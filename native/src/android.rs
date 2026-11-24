use crate::browser::{BrowserState, Entry, SEARCH_ENGINES};
use jni::objects::{GlobalRef, JObject, JString, JValue};
use jni::sys::{jboolean, jint, jlong};
use jni::{errors::Result as JniResult, JNIEnv};
use std::panic::{catch_unwind, AssertUnwindSafe};

const ACTION_BACK: i32 = 1;
const ACTION_FORWARD: i32 = 2;
const ACTION_HOME: i32 = 3;
const ACTION_RELOAD: i32 = 4;
const ACTION_BOOKMARK: i32 = 5;
const ACTION_MENU: i32 = 6;
const ACTION_TABS: i32 = 7;
const ACTION_NEW_TAB: i32 = 8;
const ACTION_SELECT_TAB: i32 = 9;
const ACTION_CLOSE_TAB: i32 = 10;
const ACTION_OPEN_ENTRY: i32 = 11;
const ACTION_BOOKMARKS: i32 = 12;
const ACTION_HISTORY: i32 = 13;
const ACTION_SETTINGS: i32 = 14;
const ACTION_ABOUT: i32 = 15;
const ACTION_PRIVACY: i32 = 16;
const ACTION_SHARE: i32 = 17;
const ACTION_CLEAR_DATA: i32 = 18;
const ACTION_CHECK_UPDATES: i32 = 19;
const ACTION_OPEN_REPOSITORY: i32 = 20;
const ACTION_OPEN_RELEASE: i32 = 21;
const ACTION_SEARCH_ENGINE: i32 = 22;
const ACTION_CLOSE_PANEL: i32 = 23;

const PREFS: &str = "helium_browser";
const PREF_TABS: &str = "tabs";
const PREF_ACTIVE_TAB: &str = "active_tab";
const PREF_HISTORY: &str = "history";
const PREF_BOOKMARKS: &str = "bookmarks";
const PREF_SEARCH_ENGINE: &str = "search_engine";
const PROJECT_REPOSITORY: &str = "https://github.com/sajadxdp0/helium-android";

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
enum Panel {
    Browser,
    Menu,
    Tabs,
    Bookmarks,
    History,
    Settings,
    About,
    Privacy,
}

struct NativeApp {
    browser: BrowserState,
    panel: Panel,
    root: GlobalRef,
    content: GlobalRef,
    address: GlobalRef,
    progress: GlobalRef,
    tab_count: GlobalRef,
    reload: GlobalRef,
    back: GlobalRef,
    forward: GlobalRef,
    bookmark: GlobalRef,
    update_status: String,
    release_url: Option<String>,
    checking_updates: bool,
    dark: bool,
    density: f32,
}

struct ShellViews {
    root: GlobalRef,
    content: GlobalRef,
    address: GlobalRef,
    progress: GlobalRef,
    tab_count: GlobalRef,
    reload: GlobalRef,
    back: GlobalRef,
    forward: GlobalRef,
    bookmark: GlobalRef,
}

#[derive(Clone, Copy)]
struct Palette {
    background: i32,
    surface: i32,
    card: i32,
    foreground: i32,
    secondary: i32,
    accent: i32,
}

impl Palette {
    fn new(dark: bool) -> Self {
        if dark {
            Self {
                background: 0xFF11121B_u32 as i32,
                surface: 0xFF171822_u32 as i32,
                card: 0xFF1C1D29_u32 as i32,
                foreground: 0xFFF4F4FA_u32 as i32,
                secondary: 0xFFBFC0CE_u32 as i32,
                accent: 0xFFBAC3FF_u32 as i32,
            }
        } else {
            Self {
                background: 0xFFF8F8FC_u32 as i32,
                surface: 0xFFF1F2F8_u32 as i32,
                card: 0xFFEDEEF5_u32 as i32,
                foreground: 0xFF171821_u32 as i32,
                secondary: 0xFF5C5F70_u32 as i32,
                accent: 0xFF4152B8_u32 as i32,
            }
        }
    }
}

fn jni_object<'local>(
    env: &mut JNIEnv<'local>,
    class: &str,
    signature: &str,
    args: &[JValue<'local, '_>],
) -> JniResult<JObject<'local>> {
    env.new_object(class, signature, args)
}

fn call_object<'local>(
    env: &mut JNIEnv<'local>,
    object: &JObject<'local>,
    method: &str,
    signature: &str,
    args: &[JValue<'local, '_>],
) -> JniResult<JObject<'local>> {
    env.call_method(object, method, signature, args)?.l()
}

fn call_void(
    env: &mut JNIEnv<'_>,
    object: &JObject<'_>,
    method: &str,
    signature: &str,
    args: &[JValue<'_, '_>],
) -> JniResult<()> {
    env.call_method(object, method, signature, args)?;
    Ok(())
}

fn java_string<'local>(env: &mut JNIEnv<'local>, value: &str) -> JniResult<JString<'local>> {
    env.new_string(value)
}

fn read_java_string(env: &mut JNIEnv<'_>, value: JObject<'_>) -> String {
    if value.is_null() {
        return String::new();
    }
    env.get_string(&JString::from(value))
        .map(|value| value.into())
        .unwrap_or_default()
}

fn dp(app: &NativeApp, value: f32) -> i32 {
    (value * app.density).round() as i32
}

fn new_view<'local>(
    env: &mut JNIEnv<'local>,
    class: &str,
    activity: &JObject<'local>,
) -> JniResult<JObject<'local>> {
    jni_object(
        env,
        class,
        "(Landroid/content/Context;)V",
        &[JValue::Object(activity)],
    )
}

fn new_linear<'local>(
    env: &mut JNIEnv<'local>,
    activity: &JObject<'local>,
    orientation: i32,
) -> JniResult<JObject<'local>> {
    let view = new_view(env, "android/widget/LinearLayout", activity)?;
    call_void(
        env,
        &view,
        "setOrientation",
        "(I)V",
        &[JValue::Int(orientation)],
    )?;
    Ok(view)
}

fn add_linear<'local>(
    env: &mut JNIEnv<'local>,
    parent: &JObject<'local>,
    child: &JObject<'local>,
    width: i32,
    height: i32,
    weight: f32,
) -> JniResult<()> {
    let params = jni_object(
        env,
        "android/widget/LinearLayout$LayoutParams",
        "(IIF)V",
        &[
            JValue::Int(width),
            JValue::Int(height),
            JValue::Float(weight),
        ],
    )?;
    call_void(
        env,
        parent,
        "addView",
        "(Landroid/view/View;Landroid/view/ViewGroup$LayoutParams;)V",
        &[JValue::Object(child), JValue::Object(&params)],
    )
}

fn add_frame<'local>(
    env: &mut JNIEnv<'local>,
    parent: &JObject<'local>,
    child: &JObject<'local>,
    width: i32,
    height: i32,
) -> JniResult<()> {
    let params = jni_object(
        env,
        "android/widget/FrameLayout$LayoutParams",
        "(II)V",
        &[JValue::Int(width), JValue::Int(height)],
    )?;
    call_void(
        env,
        parent,
        "addView",
        "(Landroid/view/View;Landroid/view/ViewGroup$LayoutParams;)V",
        &[JValue::Object(child), JValue::Object(&params)],
    )
}

fn set_text<'local>(env: &mut JNIEnv<'local>, view: &JObject<'local>, text: &str) -> JniResult<()> {
    let value = java_string(env, text)?;
    call_void(
        env,
        view,
        "setText",
        "(Ljava/lang/CharSequence;)V",
        &[JValue::Object(value.as_ref())],
    )
}

fn configure_text<'local>(
    env: &mut JNIEnv<'local>,
    view: &JObject<'local>,
    text: &str,
    size_sp: f32,
    color: i32,
    padding: i32,
) -> JniResult<()> {
    set_text(env, view, text)?;
    call_void(env, view, "setTextSize", "(F)V", &[JValue::Float(size_sp)])?;
    call_void(env, view, "setTextColor", "(I)V", &[JValue::Int(color)])?;
    call_void(
        env,
        view,
        "setPadding",
        "(IIII)V",
        &[
            JValue::Int(padding),
            JValue::Int(padding),
            JValue::Int(padding),
            JValue::Int(padding),
        ],
    )?;
    Ok(())
}

fn set_click<'local>(
    env: &mut JNIEnv<'local>,
    activity: &JObject<'local>,
    view: &JObject<'local>,
    action: i32,
    payload: Option<&str>,
) -> JniResult<()> {
    let payload = match payload {
        Some(value) => java_string(env, value)?.into(),
        None => JObject::null(),
    };
    let listener = jni_object(
        env,
        "computer/helium/mobile/RustClickListener",
        "(Lcomputer/helium/mobile/MainActivity;ILjava/lang/String;)V",
        &[
            JValue::Object(activity),
            JValue::Int(action),
            JValue::Object(&payload),
        ],
    )?;
    call_void(
        env,
        view,
        "setOnClickListener",
        "(Landroid/view/View$OnClickListener;)V",
        &[JValue::Object(&listener)],
    )
}

fn new_text<'local>(
    env: &mut JNIEnv<'local>,
    activity: &JObject<'local>,
    app: &NativeApp,
    text: &str,
    size: f32,
    color: i32,
) -> JniResult<JObject<'local>> {
    let view = new_view(env, "android/widget/TextView", activity)?;
    configure_text(env, &view, text, size, color, dp(app, 4.0))?;
    Ok(view)
}

fn new_button<'local>(
    env: &mut JNIEnv<'local>,
    activity: &JObject<'local>,
    dark: bool,
    density: f32,
    text: &str,
    action: i32,
    payload: Option<&str>,
) -> JniResult<JObject<'local>> {
    let view = new_view(env, "android/widget/Button", activity)?;
    let value = java_string(env, text)?;
    let palette = Palette::new(dark);
    configure_text(
        env,
        &view,
        text,
        14.0,
        palette.foreground,
        (8.0 * density).round() as i32,
    )?;
    let tint = env
        .call_static_method(
            "android/content/res/ColorStateList",
            "valueOf",
            "(I)Landroid/content/res/ColorStateList;",
            &[JValue::Int(palette.card)],
        )?
        .l()?;
    call_void(
        env,
        &view,
        "setBackgroundTintList",
        "(Landroid/content/res/ColorStateList;)V",
        &[JValue::Object(&tint)],
    )?;
    call_void(env, &view, "setAllCaps", "(Z)V", &[JValue::Bool(0)])?;
    call_void(
        env,
        &view,
        "setMinHeight",
        "(I)V",
        &[JValue::Int((48.0 * density).round() as i32)],
    )?;
    call_void(
        env,
        &view,
        "setContentDescription",
        "(Ljava/lang/CharSequence;)V",
        &[JValue::Object(value.as_ref())],
    )?;
    set_click(env, activity, &view, action, payload)?;
    Ok(view)
}

fn new_address_editor<'local>(
    env: &mut JNIEnv<'local>,
    activity: &JObject<'local>,
    dark: bool,
    density: f32,
) -> JniResult<JObject<'local>> {
    let view = new_view(env, "android/widget/EditText", activity)?;
    call_void(env, &view, "setSingleLine", "(Z)V", &[JValue::Bool(1)])?;
    call_void(
        env,
        &view,
        "setSelectAllOnFocus",
        "(Z)V",
        &[JValue::Bool(1)],
    )?;
    call_void(env, &view, "setImeOptions", "(I)V", &[JValue::Int(2)])?;
    call_void(env, &view, "setInputType", "(I)V", &[JValue::Int(1 | 0x10)])?;
    let hint = java_string(env, "Search or enter address")?;
    call_void(
        env,
        &view,
        "setHint",
        "(Ljava/lang/CharSequence;)V",
        &[JValue::Object(hint.as_ref())],
    )?;
    call_void(env, &view, "setTextSize", "(F)V", &[JValue::Float(15.0)])?;
    let palette = Palette::new(dark);
    call_void(
        env,
        &view,
        "setTextColor",
        "(I)V",
        &[JValue::Int(palette.foreground)],
    )?;
    call_void(
        env,
        &view,
        "setHintTextColor",
        "(I)V",
        &[JValue::Int(palette.secondary)],
    )?;
    let face_name = java_string(env, "monospace")?;
    let typeface = env
        .call_static_method(
            "android/graphics/Typeface",
            "create",
            "(Ljava/lang/String;I)Landroid/graphics/Typeface;",
            &[JValue::Object(face_name.as_ref()), JValue::Int(0)],
        )?
        .l()?;
    call_void(
        env,
        &view,
        "setTypeface",
        "(Landroid/graphics/Typeface;)V",
        &[JValue::Object(&typeface)],
    )?;
    let padding = (8.0 * density).round() as i32;
    call_void(
        env,
        &view,
        "setPadding",
        "(IIII)V",
        &[
            JValue::Int((12.0 * density).round() as i32),
            JValue::Int(padding),
            JValue::Int((12.0 * density).round() as i32),
            JValue::Int(padding),
        ],
    )?;
    let listener = jni_object(
        env,
        "computer/helium/mobile/RustEditorListener",
        "(Lcomputer/helium/mobile/MainActivity;Landroid/widget/TextView;)V",
        &[JValue::Object(activity), JValue::Object(&view)],
    )?;
    call_void(
        env,
        &view,
        "setOnEditorActionListener",
        "(Landroid/widget/TextView$OnEditorActionListener;)V",
        &[JValue::Object(&listener)],
    )?;
    Ok(view)
}

fn new_scroll_panel<'local>(
    env: &mut JNIEnv<'local>,
    activity: &JObject<'local>,
    app: &NativeApp,
) -> JniResult<(JObject<'local>, JObject<'local>)> {
    let scroll = new_view(env, "android/widget/ScrollView", activity)?;
    call_void(env, &scroll, "setFillViewport", "(Z)V", &[JValue::Bool(1)])?;
    let column = new_linear(env, activity, 1)?;
    call_void(
        &mut *env,
        &column,
        "setPadding",
        "(IIII)V",
        &[
            JValue::Int(dp(app, 20.0)),
            JValue::Int(dp(app, 20.0)),
            JValue::Int(dp(app, 20.0)),
            JValue::Int(dp(app, 24.0)),
        ],
    )?;
    add_frame(env, &scroll, &column, -1, -2)?;
    Ok((scroll, column))
}

fn append_text<'local>(
    env: &mut JNIEnv<'local>,
    activity: &JObject<'local>,
    app: &NativeApp,
    column: &JObject<'local>,
    text: &str,
    size: f32,
    color: i32,
) -> JniResult<()> {
    let view = new_text(env, activity, app, text, size, color)?;
    add_linear(env, column, &view, -1, -2, 0.0)
}

fn append_button<'local>(
    env: &mut JNIEnv<'local>,
    activity: &JObject<'local>,
    app: &NativeApp,
    column: &JObject<'local>,
    text: &str,
    action: i32,
    payload: Option<&str>,
) -> JniResult<()> {
    let button = new_button(env, activity, app.dark, app.density, text, action, payload)?;
    add_linear(env, column, &button, -1, -2, 0.0)
}

fn append_entry_button<'local>(
    env: &mut JNIEnv<'local>,
    activity: &JObject<'local>,
    app: &NativeApp,
    column: &JObject<'local>,
    entry: &Entry,
) -> JniResult<()> {
    let label = format!("{}  ·  {}", entry.title, display_address(&entry.url));
    let button = new_button(
        env,
        activity,
        app.dark,
        app.density,
        &label,
        ACTION_OPEN_ENTRY,
        Some(&entry.url),
    )?;
    call_void(env, &button, "setGravity", "(I)V", &[JValue::Int(19)])?;
    call_void(env, &button, "setSingleLine", "(Z)V", &[JValue::Bool(1)])?;
    let ellipsize = env
        .get_static_field(
            "android/text/TextUtils$TruncateAt",
            "END",
            "Landroid/text/TextUtils$TruncateAt;",
        )?
        .l()?;
    call_void(
        env,
        &button,
        "setEllipsize",
        "(Landroid/text/TextUtils$TruncateAt;)V",
        &[JValue::Object(&ellipsize)],
    )?;
    let horizontal_padding = (16.0 * app.density).round() as i32;
    let vertical_padding = (8.0 * app.density).round() as i32;
    call_void(
        env,
        &button,
        "setPadding",
        "(IIII)V",
        &[
            JValue::Int(horizontal_padding),
            JValue::Int(vertical_padding),
            JValue::Int(horizontal_padding),
            JValue::Int(vertical_padding),
        ],
    )?;
    add_linear(env, column, &button, -1, -2, 0.0)
}

fn create_shell<'local>(
    env: &mut JNIEnv<'local>,
    activity: &JObject<'local>,
    dark: bool,
    density: f32,
) -> JniResult<ShellViews> {
    let palette = Palette::new(dark);
    let root = new_linear(env, activity, 1)?;
    call_void(
        env,
        &root,
        "setBackgroundColor",
        "(I)V",
        &[JValue::Int(palette.background)],
    )?;

    let brand = new_linear(env, activity, 0)?;
    call_void(env, &brand, "setGravity", "(I)V", &[JValue::Int(16)])?;
    call_void(
        env,
        &brand,
        "setPadding",
        "(IIII)V",
        &[
            JValue::Int((16.0 * density) as i32),
            JValue::Int((6.0 * density) as i32),
            JValue::Int((16.0 * density) as i32),
            JValue::Int((2.0 * density) as i32),
        ],
    )?;
    let logo = new_view(env, "android/widget/ImageView", activity)?;
    let drawable = call_object(
        env,
        activity,
        "appIcon",
        "()Landroid/graphics/drawable/Drawable;",
        &[],
    )?;
    call_void(
        env,
        &logo,
        "setImageDrawable",
        "(Landroid/graphics/drawable/Drawable;)V",
        &[JValue::Object(&drawable)],
    )?;
    let logo_size = (36.0 * density).round() as i32;
    add_linear(env, &brand, &logo, logo_size, logo_size, 0.0)?;

    let brand_text = new_view(env, "android/widget/TextView", activity)?;
    set_text(env, &brand_text, "Helium  ·  A calmer way to browse")?;
    call_void(
        env,
        &brand_text,
        "setTextSize",
        "(F)V",
        &[JValue::Float(17.0)],
    )?;
    call_void(
        env,
        &brand_text,
        "setTextColor",
        "(I)V",
        &[JValue::Int(palette.accent)],
    )?;
    call_void(env, &brand_text, "setGravity", "(I)V", &[JValue::Int(16)])?;
    call_void(
        env,
        &brand_text,
        "setPadding",
        "(IIII)V",
        &[
            JValue::Int((10.0 * density) as i32),
            JValue::Int(0),
            JValue::Int(0),
            JValue::Int(0),
        ],
    )?;
    add_linear(env, &brand, &brand_text, 0, -2, 1.0)?;
    add_linear(env, &root, &brand, -1, -2, 0.0)?;

    let address_row = new_linear(env, activity, 0)?;
    call_void(env, &address_row, "setGravity", "(I)V", &[JValue::Int(16)])?;
    call_void(
        env,
        &address_row,
        "setPadding",
        "(IIII)V",
        &[
            JValue::Int((10.0 * density) as i32),
            JValue::Int((5.0 * density) as i32),
            JValue::Int((10.0 * density) as i32),
            JValue::Int((8.0 * density) as i32),
        ],
    )?;
    let address = new_address_editor(env, activity, dark, density)?;
    add_linear(env, &address_row, &address, 0, (52.0 * density) as i32, 1.0)?;
    let reload = new_button(env, activity, dark, density, "↻", ACTION_RELOAD, None)?;
    call_void(env, &reload, "setTextSize", "(F)V", &[JValue::Float(18.0)])?;
    add_linear(
        env,
        &address_row,
        &reload,
        (56.0 * density) as i32,
        (52.0 * density) as i32,
        0.0,
    )?;
    let tab_count = new_button(env, activity, dark, density, "Tabs 1", ACTION_TABS, None)?;
    add_linear(
        env,
        &address_row,
        &tab_count,
        (78.0 * density) as i32,
        (52.0 * density) as i32,
        0.0,
    )?;
    add_linear(env, &root, &address_row, -1, -2, 0.0)?;

    let progress = new_view(env, "android/widget/ProgressBar", activity)?;
    call_void(
        env,
        &progress,
        "setIndeterminate",
        "(Z)V",
        &[JValue::Bool(0)],
    )?;
    call_void(env, &progress, "setMax", "(I)V", &[JValue::Int(100)])?;
    call_void(env, &progress, "setVisibility", "(I)V", &[JValue::Int(8)])?;
    add_linear(env, &root, &progress, -1, (2.0 * density) as i32, 0.0)?;

    let content = new_view(env, "android/widget/FrameLayout", activity)?;
    call_void(
        env,
        &content,
        "setBackgroundColor",
        "(I)V",
        &[JValue::Int(palette.background)],
    )?;
    add_linear(env, &root, &content, -1, 0, 1.0)?;

    let bottom = new_linear(env, activity, 0)?;
    call_void(env, &bottom, "setGravity", "(I)V", &[JValue::Int(17)])?;
    call_void(
        env,
        &bottom,
        "setBackgroundColor",
        "(I)V",
        &[JValue::Int(palette.surface)],
    )?;
    let back = new_button(env, activity, dark, density, "Back", ACTION_BACK, None)?;
    let forward = new_button(
        env,
        activity,
        dark,
        density,
        "Forward",
        ACTION_FORWARD,
        None,
    )?;
    let home = new_button(env, activity, dark, density, "Home", ACTION_HOME, None)?;
    let bookmark = new_button(env, activity, dark, density, "Save", ACTION_BOOKMARK, None)?;
    let menu = new_button(env, activity, dark, density, "More", ACTION_MENU, None)?;
    for button in [&back, &forward, &home, &bookmark, &menu] {
        add_linear(env, &bottom, button, 0, (58.0 * density) as i32, 1.0)?;
    }
    add_linear(env, &root, &bottom, -1, -2, 0.0)?;

    let root_ref = env.new_global_ref(&root)?;
    let content_ref = env.new_global_ref(&content)?;
    let address_ref = env.new_global_ref(&address)?;
    let progress_ref = env.new_global_ref(&progress)?;
    let count_ref = env.new_global_ref(&tab_count)?;
    let reload_ref = env.new_global_ref(&reload)?;
    let back_ref = env.new_global_ref(&back)?;
    let forward_ref = env.new_global_ref(&forward)?;
    let bookmark_ref = env.new_global_ref(&bookmark)?;
    Ok(ShellViews {
        root: root_ref,
        content: content_ref,
        address: address_ref,
        progress: progress_ref,
        tab_count: count_ref,
        reload: reload_ref,
        back: back_ref,
        forward: forward_ref,
        bookmark: bookmark_ref,
    })
}

fn density<'local>(env: &mut JNIEnv<'local>, activity: &JObject<'local>) -> f32 {
    let result = (|| -> JniResult<f32> {
        let resources = call_object(
            env,
            activity,
            "getResources",
            "()Landroid/content/res/Resources;",
            &[],
        )?;
        let metrics = call_object(
            env,
            &resources,
            "getDisplayMetrics",
            "()Landroid/util/DisplayMetrics;",
            &[],
        )?;
        env.get_field(&metrics, "density", "F")?.f()
    })();
    result.unwrap_or(1.0).max(1.0)
}

fn preference_store<'local>(
    env: &mut JNIEnv<'local>,
    activity: &JObject<'local>,
) -> JniResult<JObject<'local>> {
    let name = java_string(env, PREFS)?;
    call_object(
        env,
        activity,
        "getSharedPreferences",
        "(Ljava/lang/String;I)Landroid/content/SharedPreferences;",
        &[JValue::Object(name.as_ref()), JValue::Int(0)],
    )
}

fn preference_string<'local>(
    env: &mut JNIEnv<'local>,
    prefs: &JObject<'local>,
    key: &str,
    default: &str,
) -> String {
    let result = (|| -> JniResult<String> {
        let key = java_string(env, key)?;
        let default = java_string(env, default)?;
        let value = call_object(
            env,
            prefs,
            "getString",
            "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",
            &[
                JValue::Object(key.as_ref()),
                JValue::Object(default.as_ref()),
            ],
        )?;
        Ok(read_java_string(env, value))
    })();
    result.unwrap_or_else(|_| default.to_owned())
}

fn preference_int(env: &mut JNIEnv<'_>, prefs: &JObject<'_>, key: &str, default: i32) -> i32 {
    let result = (|| -> JniResult<i32> {
        let key = java_string(env, key)?;
        env.call_method(
            prefs,
            "getInt",
            "(Ljava/lang/String;I)I",
            &[JValue::Object(key.as_ref()), JValue::Int(default)],
        )?
        .i()
    })();
    result.unwrap_or(default)
}

fn persist<'local>(
    env: &mut JNIEnv<'local>,
    activity: &JObject<'local>,
    app: &NativeApp,
) -> JniResult<()> {
    let prefs = preference_store(env, activity)?;
    let editor = call_object(
        env,
        &prefs,
        "edit",
        "()Landroid/content/SharedPreferences$Editor;",
        &[],
    )?;
    let urls: Vec<&str> = app
        .browser
        .tabs
        .iter()
        .map(|tab| tab.url.as_str())
        .collect();
    for (key, value) in [
        (
            PREF_TABS,
            serde_json::to_string(&urls).unwrap_or_else(|_| "[]".to_owned()),
        ),
        (
            PREF_BOOKMARKS,
            serde_json::to_string(&app.browser.bookmarks).unwrap_or_else(|_| "[]".to_owned()),
        ),
        (
            PREF_HISTORY,
            serde_json::to_string(&app.browser.history).unwrap_or_else(|_| "[]".to_owned()),
        ),
    ] {
        let key = java_string(env, key)?;
        let value = java_string(env, &value)?;
        env.call_method(
            &editor,
            "putString",
            "(Ljava/lang/String;Ljava/lang/String;)Landroid/content/SharedPreferences$Editor;",
            &[JValue::Object(key.as_ref()), JValue::Object(value.as_ref())],
        )?;
    }
    let active_key = java_string(env, PREF_ACTIVE_TAB)?;
    env.call_method(
        &editor,
        "putInt",
        "(Ljava/lang/String;I)Landroid/content/SharedPreferences$Editor;",
        &[
            JValue::Object(active_key.as_ref()),
            JValue::Int(app.browser.active_index() as i32),
        ],
    )?;
    let search_key = java_string(env, PREF_SEARCH_ENGINE)?;
    env.call_method(
        &editor,
        "putInt",
        "(Ljava/lang/String;I)Landroid/content/SharedPreferences$Editor;",
        &[
            JValue::Object(search_key.as_ref()),
            JValue::Int(app.browser.search_engine as i32),
        ],
    )?;
    call_void(env, &editor, "apply", "()V", &[])
}

fn create_app<'local>(env: &mut JNIEnv<'local>, activity: &JObject<'local>) -> JniResult<()> {
    if env.get_field(activity, "nativeHandle", "J")?.j()? != 0 {
        return Ok(());
    }
    let prefs = preference_store(env, activity)?;
    let tabs_json = preference_string(env, &prefs, PREF_TABS, "[]");
    let tabs = serde_json::from_str::<Vec<String>>(&tabs_json).unwrap_or_default();
    let bookmarks_json = preference_string(env, &prefs, PREF_BOOKMARKS, "[]");
    let bookmarks = serde_json::from_str::<Vec<Entry>>(&bookmarks_json).unwrap_or_default();
    let history_json = preference_string(env, &prefs, PREF_HISTORY, "[]");
    let history = serde_json::from_str::<Vec<Entry>>(&history_json).unwrap_or_default();
    let active_index = preference_int(env, &prefs, PREF_ACTIVE_TAB, 0).max(0) as usize;
    let search_engine = preference_int(env, &prefs, PREF_SEARCH_ENGINE, 0).max(0) as usize;
    let dark = env.call_method(activity, "isDarkTheme", "()Z", &[])?.z()?;
    let density = density(env, activity);
    let browser = BrowserState::restore(tabs, active_index, bookmarks, history, search_engine);
    let views = create_shell(env, activity, dark, density)?;
    let app = NativeApp {
        browser,
        panel: Panel::Browser,
        root: views.root,
        content: views.content,
        address: views.address,
        progress: views.progress,
        tab_count: views.tab_count,
        reload: views.reload,
        back: views.back,
        forward: views.forward,
        bookmark: views.bookmark,
        update_status: String::new(),
        release_url: None,
        checking_updates: false,
        dark,
        density,
    };
    let pointer = Box::into_raw(Box::new(app));
    env.set_field(
        activity,
        "nativeHandle",
        "J",
        JValue::Long(pointer as jlong),
    )?;
    let app = unsafe { &mut *pointer };
    env.call_method(
        activity,
        "installBrowserRoot",
        "(Landroid/view/View;)V",
        &[JValue::Object(app.root.as_obj())],
    )?;
    if let Some(tab) = app.browser.current() {
        if !tab.url.is_empty() {
            let id = java_string(env, &tab.id)?;
            let url = java_string(env, &tab.url)?;
            env.call_method(
                activity,
                "navigateGecko",
                "(Ljava/lang/String;Ljava/lang/String;)V",
                &[JValue::Object(id.as_ref()), JValue::Object(url.as_ref())],
            )?;
        }
    }
    render(env, activity, app)?;
    Ok(())
}

unsafe fn app_mut<'a>(env: &mut JNIEnv<'_>, activity: &JObject<'_>) -> Option<&'a mut NativeApp> {
    let pointer = env
        .get_field(activity, "nativeHandle", "J")
        .ok()?
        .j()
        .ok()? as *mut NativeApp;
    if pointer.is_null() {
        None
    } else {
        Some(&mut *pointer)
    }
}

fn with_app<'local, F>(env: &mut JNIEnv<'local>, activity: &JObject<'local>, handler: F)
where
    F: FnOnce(&mut JNIEnv<'local>, &JObject<'local>, &mut NativeApp) -> JniResult<()>,
{
    let result = unsafe { app_mut(env, activity).map(|app| handler(env, activity, app)) };
    if let Some(Err(error)) = result {
        eprintln!("Helium Rust UI bridge error: {error}");
    }
}

fn render<'local>(
    env: &mut JNIEnv<'local>,
    activity: &JObject<'local>,
    app: &mut NativeApp,
) -> JniResult<()> {
    refresh_header(env, app)?;
    render_content(env, activity, app)
}

fn refresh_header(env: &mut JNIEnv<'_>, app: &NativeApp) -> JniResult<()> {
    let current = app.browser.current();
    let has_address = current.is_some_and(|tab| !tab.url.is_empty() && tab.url != "about:blank");
    let focused = env
        .call_method(app.address.as_obj(), "hasFocus", "()Z", &[])?
        .z()?;
    if !focused {
        let text = current
            .filter(|tab| !tab.url.is_empty() && tab.url != "about:blank")
            .map(|tab| display_address(&tab.url))
            .unwrap_or_default();
        set_text(env, app.address.as_obj(), &text)?;
    }
    let count = format!("Tabs {}", app.browser.tabs.len());
    set_text(env, app.tab_count.as_obj(), &count)?;
    let loading = current.is_some_and(|tab| tab.loading);
    set_text(env, app.reload.as_obj(), if loading { "Stop" } else { "↻" })?;
    let progress = current.map_or(0, |tab| i32::from(tab.progress));
    call_void(
        env,
        app.progress.as_obj(),
        "setProgress",
        "(I)V",
        &[JValue::Int(progress)],
    )?;
    call_void(
        env,
        app.progress.as_obj(),
        "setVisibility",
        "(I)V",
        &[JValue::Int(if loading { 0 } else { 8 })],
    )?;
    call_void(
        env,
        app.back.as_obj(),
        "setEnabled",
        "(Z)V",
        &[JValue::Bool(
            current.is_some_and(|tab| tab.can_go_back) as u8
        )],
    )?;
    call_void(
        env,
        app.forward.as_obj(),
        "setEnabled",
        "(Z)V",
        &[JValue::Bool(
            current.is_some_and(|tab| tab.can_go_forward) as u8
        )],
    )?;
    call_void(
        env,
        app.bookmark.as_obj(),
        "setEnabled",
        "(Z)V",
        &[JValue::Bool(has_address as u8)],
    )?;
    Ok(())
}

fn render_content<'local>(
    env: &mut JNIEnv<'local>,
    activity: &JObject<'local>,
    app: &mut NativeApp,
) -> JniResult<()> {
    if app.panel == Panel::Browser {
        if let Some(tab) = app
            .browser
            .current()
            .filter(|tab| !tab.url.is_empty() && tab.url != "about:blank")
        {
            let id = java_string(env, &tab.id)?;
            let view = call_object(
                env,
                activity,
                "existingGeckoView",
                "(Ljava/lang/String;)Lorg/mozilla/geckoview/GeckoView;",
                &[JValue::Object(id.as_ref())],
            )?;
            if !view.is_null() {
                let parent =
                    call_object(env, &view, "getParent", "()Landroid/view/ViewParent;", &[])?;
                if !parent.is_null() && env.is_same_object(&parent, app.content.as_obj())? {
                    return Ok(());
                }
                if !parent.is_null() {
                    call_void(
                        env,
                        &parent,
                        "removeView",
                        "(Landroid/view/View;)V",
                        &[JValue::Object(&view)],
                    )?;
                }
                call_void(env, app.content.as_obj(), "removeAllViews", "()V", &[])?;
                return add_frame(env, app.content.as_obj(), &view, -1, -1);
            }

            call_void(env, app.content.as_obj(), "removeAllViews", "()V", &[])?;
            let view = call_object(
                env,
                activity,
                "geckoView",
                "(Ljava/lang/String;)Lorg/mozilla/geckoview/GeckoView;",
                &[JValue::Object(id.as_ref())],
            )?;
            let url = java_string(env, &tab.url)?;
            call_void(
                env,
                activity,
                "navigateGecko",
                "(Ljava/lang/String;Ljava/lang/String;)V",
                &[JValue::Object(id.as_ref()), JValue::Object(url.as_ref())],
            )?;
            return add_frame(env, app.content.as_obj(), &view, -1, -1);
        }
        call_void(env, app.content.as_obj(), "removeAllViews", "()V", &[])?;
        return render_home(env, activity, app);
    }
    call_void(env, app.content.as_obj(), "removeAllViews", "()V", &[])?;
    render_panel(env, activity, app)
}

fn render_home<'local>(
    env: &mut JNIEnv<'local>,
    activity: &JObject<'local>,
    app: &NativeApp,
) -> JniResult<()> {
    let palette = Palette::new(app.dark);
    let (scroll, column) = new_scroll_panel(env, activity, app)?;
    append_text(
        env,
        activity,
        app,
        &column,
        "The web, at your pace.",
        30.0,
        palette.foreground,
    )?;
    append_text(
        env,
        activity,
        app,
        &column,
        if app.browser.history.is_empty() && app.browser.bookmarks.is_empty() {
            "Search or enter a website above to get started."
        } else {
            "Pick up where you left off."
        },
        16.0,
        palette.secondary,
    )?;
    if !app.browser.bookmarks.is_empty() {
        append_text(
            env,
            activity,
            app,
            &column,
            "Saved sites",
            18.0,
            palette.foreground,
        )?;
        for entry in app.browser.bookmarks.iter().take(8) {
            append_entry_button(env, activity, app, &column, entry)?;
        }
    }
    if !app.browser.history.is_empty() {
        append_text(
            env,
            activity,
            app,
            &column,
            "Recent pages",
            18.0,
            palette.foreground,
        )?;
        for entry in app.browser.history.iter().take(5) {
            append_entry_button(env, activity, app, &column, entry)?;
        }
    }
    append_button(
        env,
        activity,
        app,
        &column,
        "Protection is on · GeckoView tracking protection",
        ACTION_PRIVACY,
        None,
    )?;
    append_text(
        env,
        activity,
        app,
        &column,
        &format!(
            "Search provider: {}",
            SEARCH_ENGINES[app.browser.search_engine].name
        ),
        13.0,
        palette.secondary,
    )?;
    add_frame(env, app.content.as_obj(), &scroll, -1, -1)
}

fn render_panel<'local>(
    env: &mut JNIEnv<'local>,
    activity: &JObject<'local>,
    app: &NativeApp,
) -> JniResult<()> {
    let palette = Palette::new(app.dark);
    let (scroll, column) = new_scroll_panel(env, activity, app)?;
    let title = match app.panel {
        Panel::Menu => "Browse",
        Panel::Tabs => "Your tabs",
        Panel::Bookmarks => "Bookmarks",
        Panel::History => "Recent pages",
        Panel::Settings => "Settings",
        Panel::About => "About Helium for Android",
        Panel::Privacy => "Privacy protection",
        Panel::Browser => "",
    };
    append_text(env, activity, app, &column, title, 24.0, palette.foreground)?;
    append_button(
        env,
        activity,
        app,
        &column,
        "Back to browser",
        ACTION_CLOSE_PANEL,
        None,
    )?;
    match app.panel {
        Panel::Menu => {
            append_button(
                env,
                activity,
                app,
                &column,
                "＋  New tab",
                ACTION_NEW_TAB,
                None,
            )?;
            append_button(
                env,
                activity,
                app,
                &column,
                "▣  Your tabs",
                ACTION_TABS,
                None,
            )?;
            append_button(
                env,
                activity,
                app,
                &column,
                "☆  Bookmarks",
                ACTION_BOOKMARKS,
                None,
            )?;
            append_button(
                env,
                activity,
                app,
                &column,
                "◷  Recent pages",
                ACTION_HISTORY,
                None,
            )?;
            if app.browser.current().is_some_and(|tab| !tab.url.is_empty()) {
                append_button(
                    env,
                    activity,
                    app,
                    &column,
                    "Save this page",
                    ACTION_BOOKMARK,
                    None,
                )?;
                append_button(
                    env,
                    activity,
                    app,
                    &column,
                    "Share page",
                    ACTION_SHARE,
                    None,
                )?;
            }
            append_button(
                env,
                activity,
                app,
                &column,
                "Privacy protection",
                ACTION_PRIVACY,
                None,
            )?;
            append_button(
                env,
                activity,
                app,
                &column,
                "Settings",
                ACTION_SETTINGS,
                None,
            )?;
            append_button(env, activity, app, &column, "About", ACTION_ABOUT, None)?;
        }
        Panel::Tabs => {
            append_button(
                env,
                activity,
                app,
                &column,
                "＋  New tab",
                ACTION_NEW_TAB,
                None,
            )?;
            for tab in &app.browser.tabs {
                let label = if tab.title.is_empty() {
                    if tab.url.is_empty() {
                        "New tab".to_owned()
                    } else {
                        display_address(&tab.url)
                    }
                } else {
                    tab.title.clone()
                };
                append_button(
                    env,
                    activity,
                    app,
                    &column,
                    &format!("{}\n{}", label, display_address(&tab.url)),
                    ACTION_SELECT_TAB,
                    Some(&tab.id),
                )?;
                append_button(
                    env,
                    activity,
                    app,
                    &column,
                    "Close tab",
                    ACTION_CLOSE_TAB,
                    Some(&tab.id),
                )?;
            }
        }
        Panel::Bookmarks | Panel::History => {
            let entries = if app.panel == Panel::Bookmarks {
                &app.browser.bookmarks
            } else {
                &app.browser.history
            };
            if entries.is_empty() {
                append_text(
                    env,
                    activity,
                    app,
                    &column,
                    if app.panel == Panel::Bookmarks {
                        "Pages you save will appear here."
                    } else {
                        "Pages you visit will appear here."
                    },
                    16.0,
                    palette.secondary,
                )?;
            } else {
                for entry in entries {
                    append_button(
                        env,
                        activity,
                        app,
                        &column,
                        &format!("{}\n{}", entry.title, display_address(&entry.url)),
                        ACTION_OPEN_ENTRY,
                        Some(&entry.url),
                    )?;
                }
            }
        }
        Panel::Settings => {
            append_text(
                env,
                activity,
                app,
                &column,
                "Search provider",
                17.0,
                palette.foreground,
            )?;
            for (index, engine) in SEARCH_ENGINES.iter().enumerate() {
                append_button(
                    env,
                    activity,
                    app,
                    &column,
                    &format!(
                        "{}{}",
                        if app.browser.search_engine == index {
                            "●  "
                        } else {
                            "○  "
                        },
                        engine.name
                    ),
                    ACTION_SEARCH_ENGINE,
                    Some(&index.to_string()),
                )?;
            }
            append_text(env, activity, app, &column, "Tracking protection and Global Privacy Control are on. Site permissions are denied by default.", 14.0, palette.secondary)?;
            append_button(
                env,
                activity,
                app,
                &column,
                "Clear browsing data",
                ACTION_CLEAR_DATA,
                None,
            )?;
        }
        Panel::About => {
            append_text(env, activity, app, &column, "An independent native Android browser. App state and UI are written in Rust; GeckoView provides the web engine through a small Kotlin Android adapter.", 16.0, palette.secondary)?;
            let version = call_object(env, activity, "appVersion", "()Ljava/lang/String;", &[])?;
            let version = read_java_string(env, version);
            append_text(
                env,
                activity,
                app,
                &column,
                &format!("Version {version}"),
                14.0,
                palette.secondary,
            )?;
            append_button(
                env,
                activity,
                app,
                &column,
                "GitHub repository",
                ACTION_OPEN_REPOSITORY,
                None,
            )?;
            if let Some(url) = &app.release_url {
                append_button(
                    env,
                    activity,
                    app,
                    &column,
                    "Download update",
                    ACTION_OPEN_RELEASE,
                    Some(url),
                )?;
            } else {
                append_button(
                    env,
                    activity,
                    app,
                    &column,
                    if app.checking_updates {
                        "Checking…"
                    } else {
                        "Check for updates"
                    },
                    ACTION_CHECK_UPDATES,
                    None,
                )?;
            }
            if !app.update_status.is_empty() {
                append_text(
                    env,
                    activity,
                    app,
                    &column,
                    &app.update_status,
                    14.0,
                    palette.secondary,
                )?;
            }
        }
        Panel::Privacy => {
            append_text(
                env,
                activity,
                app,
                &column,
                "Enhanced Tracking Protection",
                18.0,
                palette.foreground,
            )?;
            append_text(env, activity, app, &column, "Known ad, analytics, social, cryptomining, and fingerprinting trackers are restricted by GeckoView. Third-party tracking cookies are limited, and Global Privacy Control is sent to sites.", 16.0, palette.secondary)?;
            append_text(env, activity, app, &column, "Protection uses GeckoView’s built-in lists. It does not include Helium desktop’s filter set; some sites may need an exception.", 14.0, palette.secondary)?;
        }
        Panel::Browser => {}
    }
    add_frame(env, app.content.as_obj(), &scroll, -1, -1)
}

fn display_address(url: &str) -> String {
    url.strip_prefix("https://")
        .or_else(|| url.strip_prefix("http://"))
        .unwrap_or(url)
        .strip_prefix("www.")
        .unwrap_or_else(|| {
            url.strip_prefix("https://")
                .or_else(|| url.strip_prefix("http://"))
                .unwrap_or(url)
        })
        .split(['?', '#'])
        .next()
        .unwrap_or(url)
        .to_owned()
}

fn save_and_render<'local>(
    env: &mut JNIEnv<'local>,
    activity: &JObject<'local>,
    app: &mut NativeApp,
) -> JniResult<()> {
    persist(env, activity, app)?;
    render(env, activity, app)
}

fn navigate<'local>(
    env: &mut JNIEnv<'local>,
    activity: &JObject<'local>,
    app: &mut NativeApp,
    input: &str,
) -> JniResult<()> {
    let Some(url) = app.browser.navigate(input) else {
        return Ok(());
    };
    let Some(tab) = app.browser.current() else {
        return Ok(());
    };
    let tab_id = java_string(env, &tab.id)?;
    let url = java_string(env, &url)?;
    call_void(
        env,
        activity,
        "activateGeckoTab",
        "(Ljava/lang/String;)V",
        &[JValue::Object(tab_id.as_ref())],
    )?;
    call_void(
        env,
        activity,
        "navigateGecko",
        "(Ljava/lang/String;Ljava/lang/String;)V",
        &[
            JValue::Object(tab_id.as_ref()),
            JValue::Object(url.as_ref()),
        ],
    )?;
    app.panel = Panel::Browser;
    save_and_render(env, activity, app)
}

fn handle_action<'local>(
    env: &mut JNIEnv<'local>,
    activity: &JObject<'local>,
    app: &mut NativeApp,
    action: i32,
    payload: Option<String>,
) -> JniResult<()> {
    let current_id = app.browser.current().map(|tab| tab.id.clone());
    match action {
        ACTION_BACK => {
            if app.panel != Panel::Browser {
                app.panel = Panel::Browser;
            } else if let Some(tab) = app.browser.current().filter(|tab| tab.can_go_back) {
                let id = java_string(env, &tab.id)?;
                call_void(
                    env,
                    activity,
                    "backGecko",
                    "(Ljava/lang/String;)V",
                    &[JValue::Object(id.as_ref())],
                )?;
            } else if let Some(tab) = app.browser.current().filter(|tab| !tab.url.is_empty()) {
                let id = java_string(env, &tab.id)?;
                call_void(
                    env,
                    activity,
                    "stopGecko",
                    "(Ljava/lang/String;)V",
                    &[JValue::Object(id.as_ref())],
                )?;
                app.browser.go_home();
            } else if app.browser.tabs.len() > 1 {
                if let Some(id) = current_id.as_deref() {
                    close_tab(env, activity, app, id)?;
                }
            } else {
                call_void(env, activity, "finish", "()V", &[])?;
            }
        }
        ACTION_FORWARD => {
            if let Some(id) = current_id.as_deref() {
                let id = java_string(env, id)?;
                call_void(
                    env,
                    activity,
                    "forwardGecko",
                    "(Ljava/lang/String;)V",
                    &[JValue::Object(id.as_ref())],
                )?;
            }
        }
        ACTION_HOME => {
            if let Some(id) = current_id.as_deref() {
                let id = java_string(env, id)?;
                call_void(
                    env,
                    activity,
                    "stopGecko",
                    "(Ljava/lang/String;)V",
                    &[JValue::Object(id.as_ref())],
                )?;
            }
            app.browser.go_home();
            app.panel = Panel::Browser;
        }
        ACTION_RELOAD => {
            if let Some(tab) = app.browser.current() {
                let id = java_string(env, &tab.id)?;
                call_void(
                    env,
                    activity,
                    if tab.loading {
                        "stopGecko"
                    } else {
                        "reloadGecko"
                    },
                    "(Ljava/lang/String;)V",
                    &[JValue::Object(id.as_ref())],
                )?;
            }
        }
        ACTION_BOOKMARK => {
            let added = app.browser.add_bookmark();
            let message = if added {
                "Bookmark saved"
            } else {
                "This page is already bookmarked"
            };
            let message = java_string(env, message)?;
            call_void(
                env,
                activity,
                "showToast",
                "(Ljava/lang/String;)V",
                &[JValue::Object(message.as_ref())],
            )?;
            if added {
                persist(env, activity, app)?;
            }
        }
        ACTION_MENU => app.panel = Panel::Menu,
        ACTION_TABS => app.panel = Panel::Tabs,
        ACTION_NEW_TAB => {
            app.browser.new_tab();
            app.panel = Panel::Browser;
            let id = app.browser.active_id.clone();
            let id = java_string(env, &id)?;
            call_void(
                env,
                activity,
                "activateGeckoTab",
                "(Ljava/lang/String;)V",
                &[JValue::Object(id.as_ref())],
            )?;
            persist(env, activity, app)?;
        }
        ACTION_SELECT_TAB => {
            if let Some(id) = payload.as_deref() {
                if app.browser.select_tab(id) {
                    app.panel = Panel::Browser;
                    let id = java_string(env, id)?;
                    call_void(
                        env,
                        activity,
                        "activateGeckoTab",
                        "(Ljava/lang/String;)V",
                        &[JValue::Object(id.as_ref())],
                    )?;
                    persist(env, activity, app)?;
                }
            }
        }
        ACTION_CLOSE_TAB => {
            if let Some(id) = payload.as_deref() {
                close_tab(env, activity, app, id)?;
            }
        }
        ACTION_OPEN_ENTRY => {
            if let Some(url) = payload.as_deref() {
                navigate(env, activity, app, url)?;
            }
        }
        ACTION_BOOKMARKS => app.panel = Panel::Bookmarks,
        ACTION_HISTORY => app.panel = Panel::History,
        ACTION_SETTINGS => app.panel = Panel::Settings,
        ACTION_ABOUT => {
            app.panel = Panel::About;
            app.update_status.clear();
            app.release_url = None;
        }
        ACTION_PRIVACY => app.panel = Panel::Privacy,
        ACTION_SHARE => {
            if let Some(tab) = app.browser.current().filter(|tab| !tab.url.is_empty()) {
                let url = java_string(env, &tab.url)?;
                call_void(
                    env,
                    activity,
                    "shareAddress",
                    "(Ljava/lang/String;)V",
                    &[JValue::Object(url.as_ref())],
                )?;
            }
        }
        ACTION_CLEAR_DATA => {
            let ids: Vec<String> = app.browser.tabs.iter().map(|tab| tab.id.clone()).collect();
            for id in ids {
                let id = java_string(env, &id)?;
                call_void(
                    env,
                    activity,
                    "closeGeckoTab",
                    "(Ljava/lang/String;)V",
                    &[JValue::Object(id.as_ref())],
                )?;
            }
            app.browser.tabs.clear();
            app.browser.history.clear();
            app.browser.new_tab();
            app.panel = Panel::Browser;
            persist(env, activity, app)?;
            call_void(env, activity, "clearGeckoData", "()V", &[])?;
        }
        ACTION_CHECK_UPDATES => {
            app.checking_updates = true;
            app.update_status = "Checking GitHub Releases…".to_owned();
            call_void(env, activity, "checkForUpdates", "()V", &[])?;
        }
        ACTION_OPEN_REPOSITORY => {
            let url = java_string(env, PROJECT_REPOSITORY)?;
            call_void(
                env,
                activity,
                "openExternal",
                "(Ljava/lang/String;)V",
                &[JValue::Object(url.as_ref())],
            )?;
        }
        ACTION_OPEN_RELEASE => {
            if let Some(url) = payload.as_deref() {
                if url.starts_with("https://github.com/sajadxdp0/helium-android/releases/") {
                    let url = java_string(env, url)?;
                    call_void(
                        env,
                        activity,
                        "openExternal",
                        "(Ljava/lang/String;)V",
                        &[JValue::Object(url.as_ref())],
                    )?;
                }
            }
        }
        ACTION_SEARCH_ENGINE => {
            if let Some(index) = payload
                .as_deref()
                .and_then(|value| value.parse::<usize>().ok())
            {
                app.browser.search_engine = index.min(SEARCH_ENGINES.len() - 1);
                persist(env, activity, app)?;
            }
        }
        ACTION_CLOSE_PANEL => app.panel = Panel::Browser,
        _ => {}
    }
    render(env, activity, app)
}

fn close_tab<'local>(
    env: &mut JNIEnv<'local>,
    activity: &JObject<'local>,
    app: &mut NativeApp,
    id: &str,
) -> JniResult<()> {
    let id_java = java_string(env, id)?;
    call_void(
        env,
        activity,
        "closeGeckoTab",
        "(Ljava/lang/String;)V",
        &[JValue::Object(id_java.as_ref())],
    )?;
    app.browser.close_tab(id);
    let active = app.browser.active_id.clone();
    let active = java_string(env, &active)?;
    call_void(
        env,
        activity,
        "activateGeckoTab",
        "(Ljava/lang/String;)V",
        &[JValue::Object(active.as_ref())],
    )?;
    app.panel = Panel::Browser;
    persist(env, activity, app)
}

fn back_pressed<'local>(
    env: &mut JNIEnv<'local>,
    activity: &JObject<'local>,
    app: &mut NativeApp,
) -> JniResult<()> {
    handle_action(env, activity, app, ACTION_BACK, None)
}

struct PageEvent {
    tab_id: String,
    kind: i32,
    value: Option<String>,
    progress: i32,
    flag: bool,
}

fn page_event<'local>(
    env: &mut JNIEnv<'local>,
    activity: &JObject<'local>,
    app: &mut NativeApp,
    event: PageEvent,
) -> JniResult<()> {
    let tab_id = event.tab_id.as_str();
    let Some(index) = app.browser.tabs.iter().position(|tab| tab.id == tab_id) else {
        return Ok(());
    };
    let active = app.browser.active_id == tab_id;
    match event.kind {
        1 => {
            let tab = &mut app.browser.tabs[index];
            if let Some(url) = event.value {
                if url != "about:blank" || tab.url.is_empty() {
                    tab.url = url;
                }
            }
            tab.loading = true;
            tab.progress = 0;
            persist(env, activity, app)?;
        }
        2 => app.browser.tabs[index].progress = event.progress.clamp(0, 100) as u8,
        3 => {
            app.browser.tabs[index].loading = false;
            if event.flag {
                app.browser.record_visit(tab_id);
            }
            persist(env, activity, app)?;
        }
        4 => {
            if let Some(title) = event.value {
                app.browser.tabs[index].title = title;
                app.browser.update_saved_titles(tab_id);
                persist(env, activity, app)?;
            }
        }
        5 => app.browser.tabs[index].can_go_back = event.flag,
        6 => app.browser.tabs[index].can_go_forward = event.flag,
        7 => {
            if let Some(url) = event.value {
                if url != "about:blank" || app.browser.tabs[index].url.is_empty() {
                    app.browser.tabs[index].url = url;
                    persist(env, activity, app)?;
                }
            }
        }
        8 => close_tab(env, activity, app, tab_id)?,
        _ => {}
    }
    if event.kind == 8 || (active && event.kind == 1 && app.panel == Panel::Browser) {
        render(env, activity, app)?;
    } else if active {
        refresh_header(env, app)?;
    }
    Ok(())
}

fn update_result<'local>(
    env: &mut JNIEnv<'local>,
    activity: &JObject<'local>,
    app: &mut NativeApp,
    tag: Option<String>,
    url: Option<String>,
    error: Option<String>,
) -> JniResult<()> {
    app.checking_updates = false;
    if let Some(error) = error {
        app.update_status = format!("Could not check for updates: {error}");
        app.release_url = None;
    } else if let (Some(tag), Some(url)) = (tag, url) {
        let latest = tag.trim_start_matches('v');
        let current_obj = call_object(env, activity, "appVersion", "()Ljava/lang/String;", &[])?;
        let current = read_java_string(env, current_obj);
        match compare_versions(latest, &current) {
            Some(std::cmp::Ordering::Greater) => {
                app.update_status = format!("Version {latest} is available (installed {current}).");
                app.release_url = Some(url);
            }
            Some(std::cmp::Ordering::Equal) => {
                app.update_status = format!("You’re up to date · {current}.");
                app.release_url = None;
            }
            Some(std::cmp::Ordering::Less) => {
                app.update_status = format!(
                    "Installed {current} is ahead of the latest published release ({latest})."
                );
                app.release_url = None;
            }
            None => {
                app.update_status = "The release version is invalid.".to_owned();
                app.release_url = None;
            }
        }
    }
    render(env, activity, app)
}

fn compare_versions(first: &str, second: &str) -> Option<std::cmp::Ordering> {
    let first: Vec<u64> = first
        .split('.')
        .map(str::parse)
        .collect::<Result<_, _>>()
        .ok()?;
    let second: Vec<u64> = second
        .split('.')
        .map(str::parse)
        .collect::<Result<_, _>>()
        .ok()?;
    for index in 0..first.len().max(second.len()) {
        match first
            .get(index)
            .copied()
            .unwrap_or(0)
            .cmp(&second.get(index).copied().unwrap_or(0))
        {
            std::cmp::Ordering::Equal => continue,
            ordering => return Some(ordering),
        }
    }
    Some(std::cmp::Ordering::Equal)
}

fn read_optional_string(env: &mut JNIEnv<'_>, value: JString<'_>) -> Option<String> {
    if value.is_null() {
        None
    } else {
        env.get_string(&value).ok().map(|value| value.into())
    }
}

fn dispatch<'local>(
    env: &mut JNIEnv<'local>,
    activity: &JObject<'local>,
    handler: impl FnOnce(&mut JNIEnv<'local>, &JObject<'local>, &mut NativeApp) -> JniResult<()>,
) {
    let _ = catch_unwind(AssertUnwindSafe(|| with_app(env, activity, handler)));
}

#[no_mangle]
pub extern "system" fn Java_computer_helium_mobile_MainActivity_nativeOnCreate<'local>(
    mut env: JNIEnv<'local>,
    activity: JObject<'local>,
    _saved: JObject<'local>,
) {
    let _ = catch_unwind(AssertUnwindSafe(|| {
        if let Err(error) = create_app(&mut env, &activity) {
            eprintln!("Helium native startup failed: {error}");
        }
    }));
}

#[no_mangle]
pub extern "system" fn Java_computer_helium_mobile_MainActivity_nativeOnResume<'local>(
    mut env: JNIEnv<'local>,
    activity: JObject<'local>,
) {
    dispatch(&mut env, &activity, |env, activity, app| {
        render(env, activity, app)
    });
}

#[no_mangle]
pub extern "system" fn Java_computer_helium_mobile_MainActivity_nativeOnPause<'local>(
    mut env: JNIEnv<'local>,
    activity: JObject<'local>,
) {
    dispatch(&mut env, &activity, |env, activity, app| {
        persist(env, activity, app)
    });
}

#[no_mangle]
pub extern "system" fn Java_computer_helium_mobile_MainActivity_nativeOnDestroy<'local>(
    mut env: JNIEnv<'local>,
    activity: JObject<'local>,
) {
    let _ = catch_unwind(AssertUnwindSafe(|| unsafe {
        if let Ok(value) = env.get_field(&activity, "nativeHandle", "J") {
            if let Ok(pointer) = value.j() {
                if pointer != 0 {
                    env.set_field(&activity, "nativeHandle", "J", JValue::Long(0))
                        .ok();
                    drop(Box::from_raw(pointer as *mut NativeApp));
                }
            }
        }
    }));
}

#[no_mangle]
pub extern "system" fn Java_computer_helium_mobile_MainActivity_nativeOnBackPressed<'local>(
    mut env: JNIEnv<'local>,
    activity: JObject<'local>,
) {
    dispatch(&mut env, &activity, back_pressed);
}

#[no_mangle]
pub extern "system" fn Java_computer_helium_mobile_MainActivity_nativeOnAction<'local>(
    mut env: JNIEnv<'local>,
    activity: JObject<'local>,
    action: jint,
    payload: JString<'local>,
) {
    let payload = read_optional_string(&mut env, payload);
    dispatch(&mut env, &activity, move |env, activity, app| {
        handle_action(env, activity, app, action, payload)
    });
}

#[no_mangle]
pub extern "system" fn Java_computer_helium_mobile_MainActivity_nativeOnNavigate<'local>(
    mut env: JNIEnv<'local>,
    activity: JObject<'local>,
    input: JString<'local>,
) {
    let input = read_optional_string(&mut env, input).unwrap_or_default();
    dispatch(&mut env, &activity, move |env, activity, app| {
        navigate(env, activity, app, &input)
    });
}

#[no_mangle]
pub extern "system" fn Java_computer_helium_mobile_MainActivity_nativeOnPageEvent<'local>(
    mut env: JNIEnv<'local>,
    activity: JObject<'local>,
    tab_id: JString<'local>,
    event: jint,
    value: JString<'local>,
    number: jint,
    flag: jboolean,
) {
    let tab_id = read_optional_string(&mut env, tab_id).unwrap_or_default();
    let value = read_optional_string(&mut env, value);
    dispatch(&mut env, &activity, move |env, activity, app| {
        page_event(
            env,
            activity,
            app,
            PageEvent {
                tab_id,
                kind: event,
                value,
                progress: number,
                flag: flag != 0,
            },
        )
    });
}

#[no_mangle]
pub extern "system" fn Java_computer_helium_mobile_MainActivity_nativeOnClearDataComplete<
    'local,
>(
    mut env: JNIEnv<'local>,
    activity: JObject<'local>,
    success: jboolean,
) {
    dispatch(&mut env, &activity, move |env, activity, app| {
        app.browser.history.clear();
        app.panel = Panel::Browser;
        let toast = if success != 0 {
            "Browsing data cleared"
        } else {
            "Could not clear all site data"
        };
        let toast = java_string(env, toast)?;
        call_void(
            env,
            activity,
            "showToast",
            "(Ljava/lang/String;)V",
            &[JValue::Object(toast.as_ref())],
        )?;
        persist(env, activity, app)?;
        render(env, activity, app)
    });
}

#[no_mangle]
pub extern "system" fn Java_computer_helium_mobile_MainActivity_nativeOnUpdateResult<'local>(
    mut env: JNIEnv<'local>,
    activity: JObject<'local>,
    tag: JString<'local>,
    url: JString<'local>,
    error: JString<'local>,
) {
    let tag = read_optional_string(&mut env, tag);
    let url = read_optional_string(&mut env, url);
    let error = read_optional_string(&mut env, error);
    dispatch(&mut env, &activity, move |env, activity, app| {
        update_result(env, activity, app, tag, url, error)
    });
}

#[cfg(test)]
mod tests {
    use super::compare_versions;
    use std::cmp::Ordering;

    #[test]
    fn compares_release_versions_numerically() {
        assert_eq!(compare_versions("0.10.0", "0.9.9"), Some(Ordering::Greater));
        assert_eq!(compare_versions("0.3.5", "0.3.5"), Some(Ordering::Equal));
        assert_eq!(compare_versions("invalid", "0.3.5"), None);
    }
}
