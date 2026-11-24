use crate::browser::{BrowserState, Entry, SEARCH_ENGINES};
use jni::objects::{JObject, JString, JValue};
use jni::sys::{jboolean, jint, jlong};
use jni::{errors::Result as JniResult, JNIEnv};
use serde_json::json;
use std::panic::{catch_unwind, AssertUnwindSafe};

const PREFS: &str = "helium_browser"; // Keep the existing store name so Osmium upgrades retain settings.
const PREF_TABS: &str = "tabs";
const PREF_ACTIVE_TAB: &str = "active_tab";
const PREF_HISTORY: &str = "history";
const PREF_BOOKMARKS: &str = "bookmarks";
const PREF_SEARCH_ENGINE: &str = "search_engine";
const VERSION: &str = "0.5.2";
const PROJECT_REPOSITORY: &str = "https://github.com/sajadxdp0/osmium-android";
const RELEASE_PREFIX: &str = "https://github.com/sajadxdp0/osmium-android/releases/";

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
enum Panel { Browser, Menu, Tabs, Bookmarks, History, Settings, About, Privacy }

impl Panel {
    fn as_str(self) -> &'static str {
        match self {
            Self::Browser => "browser", Self::Menu => "menu", Self::Tabs => "tabs",
            Self::Bookmarks => "bookmarks", Self::History => "history",
            Self::Settings => "settings", Self::About => "about", Self::Privacy => "privacy",
        }
    }
}

struct NativeApp {
    browser: BrowserState,
    panel: Panel,
    update_status: String,
    release_url: Option<String>,
    checking_updates: bool,
}

fn java_string<'local>(env: &mut JNIEnv<'local>, value: &str) -> JniResult<JString<'local>> {
    env.new_string(value)
}

fn read_optional_string(env: &mut JNIEnv<'_>, value: JString<'_>) -> Option<String> {
    if value.is_null() { None } else { env.get_string(&value).ok().map(|s| s.into()) }
}

fn call_void(
    env: &mut JNIEnv<'_>, object: &JObject<'_>, method: &str, signature: &str,
    args: &[JValue<'_, '_>],
) -> JniResult<()> {
    env.call_method(object, method, signature, args)?;
    Ok(())
}

fn call_string_method(
    env: &mut JNIEnv<'_>, activity: &JObject<'_>, method: &str, value: &str,
) -> JniResult<()> {
    let value = java_string(env, value)?;
    call_void(env, activity, method, "(Ljava/lang/String;)V", &[JValue::Object(value.as_ref())])
}

fn preference_store<'local>(
    env: &mut JNIEnv<'local>, activity: &JObject<'local>,
) -> JniResult<JObject<'local>> {
    let name = java_string(env, PREFS)?;
    env.call_method(
        activity, "getSharedPreferences",
        "(Ljava/lang/String;I)Landroid/content/SharedPreferences;",
        &[JValue::Object(name.as_ref()), JValue::Int(0)],
    )?.l()
}

fn preference_string(
    env: &mut JNIEnv<'_>, prefs: &JObject<'_>, key: &str, default: &str,
) -> String {
    (|| -> JniResult<String> {
        let key = java_string(env, key)?;
        let default_java = java_string(env, default)?;
        let value = env.call_method(
            prefs, "getString", "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
            &[JValue::Object(key.as_ref()), JValue::Object(default_java.as_ref())],
        )?.l()?;
        if value.is_null() { Ok(default.to_owned()) }
        else { Ok(env.get_string(&JString::from(value))?.into()) }
    })().unwrap_or_else(|_| default.to_owned())
}

fn preference_int(env: &mut JNIEnv<'_>, prefs: &JObject<'_>, key: &str, default: i32) -> i32 {
    (|| -> JniResult<i32> {
        let key = java_string(env, key)?;
        env.call_method(prefs, "getInt", "(Ljava/lang/String;I)I",
            &[JValue::Object(key.as_ref()), JValue::Int(default)])?.i()
    })().unwrap_or(default)
}

fn persist<'local>(env: &mut JNIEnv<'local>, activity: &JObject<'local>, app: &NativeApp) -> JniResult<()> {
    let prefs = preference_store(env, activity)?;
    let editor = env.call_method(&prefs, "edit", "()Landroid/content/SharedPreferences$Editor;", &[])?.l()?;
    let urls = app.browser.tabs.iter().map(|t| t.url.as_str()).collect::<Vec<_>>();
    for (key, value) in [
        (PREF_TABS, serde_json::to_string(&urls).unwrap_or_else(|_| "[]".to_owned())),
        (PREF_BOOKMARKS, serde_json::to_string(&app.browser.bookmarks).unwrap_or_else(|_| "[]".to_owned())),
        (PREF_HISTORY, serde_json::to_string(&app.browser.history).unwrap_or_else(|_| "[]".to_owned())),
    ] {
        let key = java_string(env, key)?;
        let value = java_string(env, &value)?;
        env.call_method(&editor, "putString",
            "(Ljava/lang/String;Ljava/lang/String;)Landroid/content/SharedPreferences$Editor;",
            &[JValue::Object(key.as_ref()), JValue::Object(value.as_ref())])?;
    }
    for (key, value) in [
        (PREF_ACTIVE_TAB, app.browser.active_index() as i32),
        (PREF_SEARCH_ENGINE, app.browser.search_engine as i32),
    ] {
        let key = java_string(env, key)?;
        env.call_method(&editor, "putInt", "(Ljava/lang/String;I)Landroid/content/SharedPreferences$Editor;",
            &[JValue::Object(key.as_ref()), JValue::Int(value)])?;
    }
    call_void(env, &editor, "apply", "()V", &[])
}

fn create_app<'local>(env: &mut JNIEnv<'local>, activity: &JObject<'local>) -> JniResult<()> {
    if env.get_field(activity, "nativeHandle", "J")?.j()? != 0 { return Ok(()); }
    let prefs = preference_store(env, activity)?;
    let tabs = serde_json::from_str::<Vec<String>>(&preference_string(env, &prefs, PREF_TABS, "[]")).unwrap_or_default();
    let bookmarks = serde_json::from_str::<Vec<Entry>>(&preference_string(env, &prefs, PREF_BOOKMARKS, "[]")).unwrap_or_default();
    let history = serde_json::from_str::<Vec<Entry>>(&preference_string(env, &prefs, PREF_HISTORY, "[]")).unwrap_or_default();
    let browser = BrowserState::restore(
        tabs, preference_int(env, &prefs, PREF_ACTIVE_TAB, 0).max(0) as usize,
        bookmarks, history, preference_int(env, &prefs, PREF_SEARCH_ENGINE, 0).max(0) as usize,
    );
    let pointer = Box::into_raw(Box::new(NativeApp {
        browser, panel: Panel::Browser, update_status: String::new(),
        release_url: None, checking_updates: false,
    }));
    if let Err(error) = env.set_field(activity, "nativeHandle", "J", JValue::Long(pointer as jlong)) {
        unsafe { drop(Box::from_raw(pointer)); }
        return Err(error);
    }
    Ok(())
}

unsafe fn app_mut<'a>(env: &mut JNIEnv<'_>, activity: &JObject<'_>) -> Option<&'a mut NativeApp> {
    let pointer = env.get_field(activity, "nativeHandle", "J").ok()?.j().ok()? as *mut NativeApp;
    pointer.as_mut()
}

fn snapshot(app: &NativeApp) -> String {
    let current = app.browser.current();
    let tabs = app.browser.tabs.iter().map(|tab| json!({
        "id": tab.id,
        "title": if tab.title.is_empty() { &tab.url } else { &tab.title },
        "url": tab.url, "active": tab.id == app.browser.active_id
    })).collect::<Vec<_>>();
    let entries = |list: &[Entry]| list.iter()
        .map(|e| json!({"title": e.title, "url": e.url})).collect::<Vec<_>>();
    let bookmarked = current.is_some_and(|tab| app.browser.bookmarks.iter().any(|e| e.url == tab.url));
    let engine = SEARCH_ENGINES[app.browser.search_engine];
    json!({
        "panel": app.panel.as_str(),
        "activeTabId": app.browser.active_id,
        "url": current.map_or("", |t| t.url.as_str()),
        "title": current.map_or("", |t| if t.title.is_empty() { t.url.as_str() } else { t.title.as_str() }),
        "loading": current.is_some_and(|t| t.loading),
        "progress": current.map_or(0, |t| t.progress),
        "canGoBack": current.is_some_and(|t| t.can_go_back),
        "canGoForward": current.is_some_and(|t| t.can_go_forward),
        "bookmarked": bookmarked, "tabs": tabs, "bookmarks": entries(&app.browser.bookmarks),
        "history": entries(&app.browser.history), "searchEngine": engine.name,
        "updateStatus": app.update_status, "version": VERSION
    }).to_string()
}

fn call_gecko_navigation(
    env: &mut JNIEnv<'_>, activity: &JObject<'_>, app: &NativeApp, url: &str,
) -> JniResult<()> {
    if let Some(tab) = app.browser.current() {
        let id = java_string(env, &tab.id)?;
        let url = java_string(env, url)?;
        call_void(env, activity, "activateGeckoTab", "(Ljava/lang/String;)V",
            &[JValue::Object(id.as_ref())])?;
        call_void(env, activity, "navigateGecko", "(Ljava/lang/String;Ljava/lang/String;)V",
            &[JValue::Object(id.as_ref()), JValue::Object(url.as_ref())])?;
    }
    Ok(())
}

fn ensure_gecko_tab(env: &mut JNIEnv<'_>, activity: &JObject<'_>, id: &str, url: &str) -> JniResult<()> {
    let id = java_string(env, id)?;
    let url = java_string(env, url)?;
    call_void(
        env,
        activity,
        "ensureGeckoTab",
        "(Ljava/lang/String;Ljava/lang/String;)V",
        &[JValue::Object(id.as_ref()), JValue::Object(url.as_ref())],
    )
}

fn close_tab<'local>(env: &mut JNIEnv<'local>, activity: &JObject<'local>, app: &mut NativeApp, id: &str) -> JniResult<()> {
    call_string_method(env, activity, "closeGeckoTab", id)?;
    if app.browser.close_tab(id) {
        let active = app.browser.active_id.clone();
        let url = app.browser.current().map_or("", |tab| tab.url.as_str());
        ensure_gecko_tab(env, activity, &active, url)?;
        app.panel = Panel::Browser;
        persist(env, activity, app)?;
    }
    Ok(())
}

fn invoke_action<'local>(
    env: &mut JNIEnv<'local>, activity: &JObject<'local>, app: &mut NativeApp,
    action: &str, payload: Option<&str>,
) -> JniResult<()> {
    let id = app.browser.active_id.clone();
    match action {
        "navigate" | "open_entry" => if let Some(url) = payload.and_then(|p| app.browser.navigate(p)) {
            app.panel = Panel::Browser;
            call_gecko_navigation(env, activity, app, &url)?;
            persist(env, activity, app)?;
        },
        "back" | "system_back" => {
            if action == "system_back" && app.panel != Panel::Browser { app.panel = Panel::Browser; }
            else if let Some(tab) = app.browser.current().filter(|t| t.can_go_back) {
                call_string_method(env, activity, "backGecko", &tab.id)?;
            } else if let Some(tab) = app.browser.current().filter(|t| !t.url.is_empty()) {
                call_string_method(env, activity, "stopGecko", &tab.id)?;
                app.browser.go_home();
                persist(env, activity, app)?;
            } else if app.browser.tabs.len() > 1 { close_tab(env, activity, app, &id)?; }
            else { call_void(env, activity, "finish", "()V", &[])?; }
        }
        "forward" => call_string_method(env, activity, "forwardGecko", &id)?,
        "home" => {
            call_string_method(env, activity, "stopGecko", &id)?;
            app.browser.go_home(); app.panel = Panel::Browser; persist(env, activity, app)?;
        }
        "reload" => if let Some(tab) = app.browser.current() {
            call_string_method(env, activity, if tab.loading { "stopGecko" } else { "reloadGecko" }, &tab.id)?;
        },
        "bookmark" => {
            let added = app.browser.add_bookmark();
            call_string_method(env, activity, "showToast",
                if added { "Bookmark saved" } else { "This page is already bookmarked" })?;
            if added { persist(env, activity, app)?; }
        }
        "menu" => app.panel = Panel::Menu,
        "tabs" => app.panel = Panel::Tabs,
        "new_tab" => {
            let id = app.browser.new_tab().id.clone();
            app.panel = Panel::Browser;
            call_string_method(env, activity, "activateGeckoTab", &id)?;
            persist(env, activity, app)?;
        }
        "select_tab" => if let Some(id) = payload {
            if app.browser.select_tab(id) {
                app.panel = Panel::Browser;
                let url = app.browser.current().map_or("", |tab| tab.url.as_str());
                ensure_gecko_tab(env, activity, id, url)?;
                persist(env, activity, app)?;
            }
        },
        "close_tab" => if let Some(id) = payload { close_tab(env, activity, app, id)?; },
        "bookmarks" => app.panel = Panel::Bookmarks,
        "history" => app.panel = Panel::History,
        "settings" => app.panel = Panel::Settings,
        "about" => { app.panel = Panel::About; app.update_status.clear(); app.release_url = None; }
        "privacy" => app.panel = Panel::Privacy,
        "share" => {
            let url = payload.filter(|url| !url.is_empty())
                .or_else(|| app.browser.current().map(|tab| tab.url.as_str()).filter(|url| !url.is_empty()));
            if let Some(url) = url { call_string_method(env, activity, "shareAddress", url)?; }
        }
        "clear_data" => {
            let ids = app.browser.tabs.iter().map(|t| t.id.clone()).collect::<Vec<_>>();
            for tab_id in ids { call_string_method(env, activity, "closeGeckoTab", &tab_id)?; }
            app.browser.tabs.clear(); app.browser.history.clear(); app.browser.new_tab();
            app.panel = Panel::Browser; persist(env, activity, app)?;
            call_void(env, activity, "clearGeckoData", "()V", &[])?;
        }
        "check_updates" => {
            app.checking_updates = true; app.update_status = "Checking GitHub Releases…".to_owned();
            call_void(env, activity, "checkForUpdates", "()V", &[])?;
        }
        "open_repository" => call_string_method(env, activity, "openExternal", PROJECT_REPOSITORY)?,
        "open_release" => {
            let url = payload.filter(|url|
                app.release_url.as_deref() == Some(*url) && url.starts_with(RELEASE_PREFIX))
                .or(app.release_url.as_deref())
                .unwrap_or("https://github.com/sajadxdp0/osmium-android/releases/latest");
            call_string_method(env, activity, "openExternal", url)?;
        }
        "search_engine" => if let Some(index) = payload.and_then(|name|
            SEARCH_ENGINES.iter().position(|engine| engine.name == name)) {
            app.browser.search_engine = index; persist(env, activity, app)?;
        },
        "close_panel" => app.panel = Panel::Browser,
        _ => {}
    }
    Ok(())
}

struct PageEvent {
    tab_id: String,
    kind: i32,
    value: Option<String>,
    number: i32,
    flag: bool,
}

fn page_event<'local>(
    env: &mut JNIEnv<'local>,
    activity: &JObject<'local>,
    app: &mut NativeApp,
    event: PageEvent,
) -> JniResult<()> {
    let PageEvent {
        tab_id,
        kind,
        value,
        number,
        flag,
    } = event;
    let Some(index) = app.browser.tabs.iter().position(|tab| tab.id == tab_id) else {
        return Ok(());
    };
    match kind {
        1 => {
            let tab = &mut app.browser.tabs[index];
            if let Some(url) = value {
                if url != "about:blank" || tab.url.is_empty() {
                    tab.url = url;
                }
            }
            tab.loading = true;
            tab.progress = 0;
            persist(env, activity, app)?;
        }
        2 => app.browser.tabs[index].progress = number.clamp(0, 100) as u8,
        3 => {
            app.browser.tabs[index].loading = false;
            if flag {
                app.browser.record_visit(&tab_id);
            }
            persist(env, activity, app)?;
        }
        4 => {
            if let Some(title) = value {
                app.browser.tabs[index].title = title;
                app.browser.update_saved_titles(&tab_id);
                persist(env, activity, app)?;
            }
        }
        5 => app.browser.tabs[index].can_go_back = flag,
        6 => app.browser.tabs[index].can_go_forward = flag,
        7 => {
            if let Some(url) = value {
                if url != "about:blank" || app.browser.tabs[index].url.is_empty() {
                    app.browser.tabs[index].url = url;
                    persist(env, activity, app)?;
                }
            }
        }
        8 => close_tab(env, activity, app, &tab_id)?,
        _ => {}
    }
    Ok(())
}

fn compare_versions(first: &str, second: &str) -> Option<std::cmp::Ordering> {
    let first: Vec<u64> = first.trim_start_matches('v').split('.').map(str::parse).collect::<Result<_, _>>().ok()?;
    let second: Vec<u64> = second.trim_start_matches('v').split('.').map(str::parse).collect::<Result<_, _>>().ok()?;
    for i in 0..first.len().max(second.len()) {
        match first.get(i).copied().unwrap_or(0).cmp(&second.get(i).copied().unwrap_or(0)) {
            std::cmp::Ordering::Equal => {},
            ordering => return Some(ordering),
        }
    }
    Some(std::cmp::Ordering::Equal)
}

fn update_result(app: &mut NativeApp, tag: Option<String>, url: Option<String>, error: Option<String>) {
    app.checking_updates = false;
    if let Some(error) = error {
        app.update_status = format!("Could not check for updates: {error}");
        app.release_url = None;
    } else if let (Some(tag), Some(url)) = (tag, url) {
        let latest = tag.trim_start_matches('v');
        match compare_versions(latest, VERSION) {
            Some(std::cmp::Ordering::Greater) if url.starts_with(RELEASE_PREFIX) => {
                app.update_status = format!("Version {latest} is available (installed {VERSION}).");
                app.release_url = Some(url);
            }
            Some(std::cmp::Ordering::Greater) => {
                app.update_status = "The release URL is invalid.".to_owned(); app.release_url = None;
            }
            Some(std::cmp::Ordering::Equal) => {
                app.update_status = format!("You’re up to date · {VERSION}."); app.release_url = None;
            }
            Some(std::cmp::Ordering::Less) => {
                app.update_status = format!("Installed {VERSION} is ahead of the latest published release ({latest}).");
                app.release_url = None;
            }
            None => { app.update_status = "The release version is invalid.".to_owned(); app.release_url = None; }
        }
    }
}

fn result_string<'local>(env: &mut JNIEnv<'local>, value: String) -> JString<'local> {
    env.new_string(value).unwrap_or_else(|_| JObject::null().into())
}

fn app_result<'local>(
    env: &mut JNIEnv<'local>, activity: &JObject<'local>,
    run: impl FnOnce(&mut JNIEnv<'local>, &JObject<'local>, &mut NativeApp) -> JniResult<()>,
) -> JString<'local> {
    let state = catch_unwind(AssertUnwindSafe(|| unsafe {
        let Some(app) = app_mut(env, activity) else { return "{}".to_owned(); };
        if let Err(error) = run(env, activity, app) { eprintln!("Osmium bridge error: {error}"); }
        snapshot(app)
    })).unwrap_or_else(|_| "{}".to_owned());
    result_string(env, state)
}

#[no_mangle]
pub extern "system" fn Java_com_sajadxdp_osmium_MainActivity_nativeOnCreate<'local>(
    mut env: JNIEnv<'local>, activity: JObject<'local>, _saved: JObject<'local>,
) -> JString<'local> {
    match catch_unwind(AssertUnwindSafe(|| create_app(&mut env, &activity))) {
        Ok(Ok(())) => {}
        Ok(Err(error)) => eprintln!("Osmium startup failed: {error}"),
        Err(_) => eprintln!("Osmium startup panicked"),
    }
    let state = catch_unwind(AssertUnwindSafe(|| unsafe {
        app_mut(&mut env, &activity).map(|app| snapshot(app)).unwrap_or_else(|| "{}".to_owned())
    })).unwrap_or_else(|_| "{}".to_owned());
    result_string(&mut env, state)
}

#[no_mangle]
pub extern "system" fn Java_com_sajadxdp_osmium_MainActivity_nativeOnResume(_env: JNIEnv<'_>, _activity: JObject<'_>) {}

#[no_mangle]
pub extern "system" fn Java_com_sajadxdp_osmium_MainActivity_nativeOnPause<'local>(
    mut env: JNIEnv<'local>, activity: JObject<'local>,
) {
    let _ = catch_unwind(AssertUnwindSafe(|| unsafe {
        if let Some(app) = app_mut(&mut env, &activity) {
            if let Err(error) = persist(&mut env, &activity, app) { eprintln!("Osmium persistence failed: {error}"); }
        }
    }));
}

#[no_mangle]
pub extern "system" fn Java_com_sajadxdp_osmium_MainActivity_nativeOnDestroy(
    mut env: JNIEnv<'_>, activity: JObject<'_>,
) {
    let _ = catch_unwind(AssertUnwindSafe(|| unsafe {
        if let Ok(value) = env.get_field(&activity, "nativeHandle", "J") {
            if let Ok(pointer) = value.j() {
                if pointer != 0 {
                    let _ = env.set_field(&activity, "nativeHandle", "J", JValue::Long(0));
                    drop(Box::from_raw(pointer as *mut NativeApp));
                }
            }
        }
    }));
}

#[no_mangle]
pub extern "system" fn Java_com_sajadxdp_osmium_MainActivity_nativeOnAction<'local>(
    mut env: JNIEnv<'local>, activity: JObject<'local>, action: JString<'local>, payload: JString<'local>,
) -> JString<'local> {
    let action = read_optional_string(&mut env, action).unwrap_or_default();
    let payload = read_optional_string(&mut env, payload);
    app_result(&mut env, &activity, move |env, activity, app|
        invoke_action(env, activity, app, &action, payload.as_deref()))
}

#[no_mangle]
pub extern "system" fn Java_com_sajadxdp_osmium_MainActivity_nativeOnPageEvent<'local>(
    mut env: JNIEnv<'local>, activity: JObject<'local>, tab_id: JString<'local>,
    event: jint, value: JString<'local>, number: jint, flag: jboolean,
) -> JString<'local> {
    let tab_id = read_optional_string(&mut env, tab_id).unwrap_or_default();
    let value = read_optional_string(&mut env, value);
    app_result(&mut env, &activity, move |env, activity, app| {
        page_event(
            env,
            activity,
            app,
            PageEvent {
                tab_id,
                kind: event,
                value,
                number,
                flag: flag != 0,
            },
        )
    })
}

#[no_mangle]
pub extern "system" fn Java_com_sajadxdp_osmium_MainActivity_nativeOnClearDataComplete<'local>(
    mut env: JNIEnv<'local>, activity: JObject<'local>, success: jboolean,
) -> JString<'local> {
    app_result(&mut env, &activity, move |env, activity, app| {
        app.browser.history.clear();
        app.panel = Panel::Browser;
        call_string_method(env, activity, "showToast",
            if success != 0 { "Browsing data cleared" } else { "Could not clear all site data" })?;
        persist(env, activity, app)
    })
}

#[no_mangle]
pub extern "system" fn Java_com_sajadxdp_osmium_MainActivity_nativeOnUpdateResult<'local>(
    mut env: JNIEnv<'local>, activity: JObject<'local>, tag: JString<'local>,
    url: JString<'local>, error: JString<'local>,
) -> JString<'local> {
    let tag = read_optional_string(&mut env, tag);
    let url = read_optional_string(&mut env, url);
    let error = read_optional_string(&mut env, error);
    app_result(&mut env, &activity, move |_env, _activity, app| {
        update_result(app, tag, url, error); Ok(())
    })
}

#[cfg(test)]
mod tests {
    use super::compare_versions;
    use std::cmp::Ordering;

    #[test]
    fn compares_release_versions_numerically() {
        assert_eq!(compare_versions("0.10.0", "0.9.9"), Some(Ordering::Greater));
        assert_eq!(compare_versions("0.5.1", "0.5.1"), Some(Ordering::Equal));
        assert_eq!(compare_versions("invalid", "0.5.1"), None);
    }
}
