package computer.helium.mobile

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.edit
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.StorageController

class MainActivity : ComponentActivity() {
    private val preferences by lazy { getSharedPreferences(PREFERENCES, MODE_PRIVATE) }
    private val tabs = mutableStateListOf<BrowserTab>()
    private val history = mutableStateListOf<BrowserEntry>()
    private val bookmarks = mutableStateListOf<BrowserEntry>()
    private var activeTabId by mutableStateOf("")
    private var searchEngineIndex by mutableIntStateOf(0)
    private val runtime: org.mozilla.geckoview.GeckoRuntime
        get() = (application as HeliumApplication).runtime

    val browserTabs: List<BrowserTab> get() = tabs
    val historyEntries: List<BrowserEntry> get() = history
    val bookmarkEntries: List<BrowserEntry> get() = bookmarks
    val searchEngine: SearchEngine get() = SEARCH_ENGINES[searchEngineIndex]
    val currentTab: BrowserTab?
        get() = tabs.firstOrNull { it.id == activeTabId } ?: tabs.firstOrNull()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        searchEngineIndex = preferences.getInt(PREF_SEARCH_ENGINE, 0)
            .coerceIn(SEARCH_ENGINES.indices)
        restoreEntries(PREF_HISTORY, history)
        restoreEntries(PREF_BOOKMARKS, bookmarks)
        restoreTabs()

        setContent {
            HeliumTheme {
                BrowserScreen(activity = this)
            }
        }
    }

    fun newTab(url: String = "") {
        val session = GeckoSession()
        val tab = BrowserTab(session = session, initialUrl = url)
        attachDelegates(tab)
        tabs.add(tab)
        activateTab(tab)
        saveTabs()
    }

    fun selectTab(tab: BrowserTab) {
        if (tab.id == activeTabId) return
        currentTab?.takeIf { it.sessionOpened }?.let {
            it.session.setFocused(false)
            it.session.setActive(false)
        }
        activateTab(tab)
        saveTabs()
    }

    fun closeTab(tab: BrowserTab) {
        if (tabs.size == 1) {
            if (tab.sessionOpened) {
                tab.session.stop()
                tab.session.close()
                tab.sessionOpened = false
            }
            tabs.remove(tab)
            activeTabId = ""
            newTab()
            return
        }

        val wasActive = tab.id == activeTabId
        val oldIndex = tabs.indexOf(tab)
        if (tab.sessionOpened) {
            tab.session.setFocused(false)
            tab.session.setActive(false)
            tab.session.close()
            tab.sessionOpened = false
        }
        tabs.remove(tab)
        if (wasActive) activateTab(tabs[oldIndex.coerceAtMost(tabs.lastIndex)])
        saveTabs()
    }

    fun navigateFromAddress(input: String) {
        val query = input.trim()
        if (query.isEmpty()) return

        val address = RustBrowserCore.resolveAddress(query, searchEngine.template)
        val tab = currentTab ?: run {
            newTab()
            currentTab ?: return
        }
        tab.url = address
        tab.title = ""
        tab.progress = 0f
        tab.loading = true
        tab.pendingNavigation = address
        saveTabs()
    }



    fun goHome() {
        currentTab?.let { tab ->
            if (tab.sessionOpened) tab.session.stop()
            tab.url = ""
            tab.title = ""
            tab.loading = false
            tab.progress = 0f
            tab.canGoBack = false
            tab.canGoForward = false
        }
        saveTabs()
    }

    fun goBack() {
        currentTab?.takeIf { it.canGoBack }?.session?.goBack()
    }

    fun goForward() {
        currentTab?.takeIf { it.canGoForward }?.session?.goForward()
    }

    fun reloadOrStop() {
        currentTab?.takeIf { it.sessionOpened }?.let { if (it.loading) it.session.stop() else it.session.reload() }
    }

    fun addBookmark() {
        val tab = currentTab ?: return
        if (tab.url.isBlank()) return
        if (bookmarks.any { it.url == tab.url }) {
            toast("This page is already bookmarked")
            return
        }
        bookmarks.add(0, BrowserEntry(tab.title.ifBlank { tab.url }, tab.url))
        persistEntries(PREF_BOOKMARKS, bookmarks)
        toast("Bookmark saved")
    }

    fun openEntry(entry: BrowserEntry) {
        navigateFromAddress(entry.url)
    }

    fun selectSearchEngine(index: Int) {
        searchEngineIndex = index.coerceIn(SEARCH_ENGINES.indices)
        preferences.edit { putInt(PREF_SEARCH_ENGINE, searchEngineIndex) }
    }

    fun sharePage() {
        val url = currentTab?.url?.takeIf(String::isNotBlank) ?: return
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
        }
        startActivity(Intent.createChooser(send, "Share page"))
    }

    fun clearBrowsingData() {
        val sessions = tabs.toList()
        sessions.forEach { tab ->
            if (tab.sessionOpened) {
                tab.session.setFocused(false)
                tab.session.setActive(false)
                tab.session.close()
                tab.sessionOpened = false
            }
        }
        tabs.clear()
        activeTabId = ""
        saveTabs()
        history.clear()
        persistEntries(PREF_HISTORY, history)
        runtime.storageController.clearData(StorageController.ClearFlags.ALL)
            .accept(
                {
                    runOnUiThread {
                        newTab()
                        toast("Browsing data cleared")
                    }
                },
                {
                    runOnUiThread {
                        newTab()
                        toast("Could not clear all site data")
                    }
                },
            )
    }

    fun aboutMessage(): String =
        "An independent Android browser inspired by Helium. Its interface is built with Jetpack Compose and its browser engine is Mozilla GeckoView. It is not affiliated with the Helium desktop project."

    override fun onPause() {
        saveTabs()
        currentTab?.takeIf { it.sessionOpened }?.session?.setFocused(false)
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        currentTab?.takeIf { it.sessionOpened && it.url.isNotBlank() }?.session?.setFocused(true)
    }

    override fun onDestroy() {
        tabs.forEach { tab ->
            if (tab.sessionOpened) {
                tab.session.setFocused(false)
                tab.session.setActive(false)
                tab.session.close()
                tab.sessionOpened = false
            }
        }
        super.onDestroy()
    }

    fun ensureSessionOpen(tab: BrowserTab) {
        if (tab.sessionOpened) return
        tab.session.open(runtime)
        tab.sessionOpened = true
    }

    private fun restoreTabs() {
        val saved = try {
            JSONArray(preferences.getString(PREF_TABS, "[]"))
        } catch (_: Exception) {
            JSONArray()
        }
        val urls = (0 until saved.length()).map(saved::optString)
        if (urls.isEmpty()) {
            newTab()
            return
        }

        urls.forEach { url ->
            val session = GeckoSession()
            val tab = BrowserTab(session = session, initialUrl = url)
            attachDelegates(tab)
            tabs.add(tab)
        }
        val selected = preferences.getInt(PREF_ACTIVE_TAB, 0).coerceIn(tabs.indices)
        activateTab(tabs[selected])
    }

    private fun activateTab(tab: BrowserTab) {
        activeTabId = tab.id
        if (tab.sessionOpened && tab.url.isNotBlank()) {
            tab.session.setActive(true)
            tab.session.setFocused(true)
        }
    }

    private fun attachDelegates(tab: BrowserTab) {
        tab.session.setProgressDelegate(object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                tab.url = url
                tab.loading = true
                tab.progress = 0f
                persistCurrentVisit(tab)
            }

            override fun onProgressChange(session: GeckoSession, progress: Int) {
                tab.progress = progress.coerceIn(0, 100) / 100f
            }

            override fun onPageStop(session: GeckoSession, success: Boolean) {
                tab.loading = false
                if (success && tab.url.startsWith("http")) recordVisit(tab)
                saveTabs()
            }
        })
        tab.session.setContentDelegate(object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String?) {
                tab.title = title.orEmpty()
            }

            override fun onCloseRequest(session: GeckoSession) {
                closeTab(tab)
            }
        })
        tab.session.setNavigationDelegate(object : GeckoSession.NavigationDelegate {
            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                tab.canGoBack = canGoBack
            }

            override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
                tab.canGoForward = canGoForward
            }

            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                perms: List<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean,
            ) {
                tab.url = url.orEmpty()
                persistCurrentVisit(tab)
                saveTabs()
            }
        })
        tab.session.setPermissionDelegate(object : GeckoSession.PermissionDelegate {
            override fun onContentPermissionRequest(
                session: GeckoSession,
                perm: GeckoSession.PermissionDelegate.ContentPermission,
            ): GeckoResult<Int> = GeckoResult.fromValue(
                GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY,
            )

            override fun onAndroidPermissionsRequest(
                session: GeckoSession,
                permissions: Array<String>?,
                callback: GeckoSession.PermissionDelegate.Callback,
            ) {
                callback.reject()
            }

            override fun onMediaPermissionRequest(
                session: GeckoSession,
                uri: String,
                video: Array<GeckoSession.PermissionDelegate.MediaSource>?,
                audio: Array<GeckoSession.PermissionDelegate.MediaSource>?,
                callback: GeckoSession.PermissionDelegate.MediaCallback,
            ) {
                callback.reject()
            }
        })
    }

    private fun recordVisit(tab: BrowserTab) {
        if (tab.url.isBlank() || tab.url == "about:blank") return
        if (history.firstOrNull()?.url == tab.url) return
        history.add(0, BrowserEntry(tab.title.ifBlank { tab.url }, tab.url))
        while (history.size > 100) history.removeAt(history.lastIndex)
        persistEntries(PREF_HISTORY, history)
    }

    private fun persistCurrentVisit(tab: BrowserTab) {
        if (tab.url.isNotBlank()) saveTabs()
    }

    private fun restoreEntries(key: String, destination: MutableList<BrowserEntry>) {
        val items = try {
            JSONArray(preferences.getString(key, "[]"))
        } catch (_: Exception) {
            JSONArray()
        }
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            val url = item.optString("url")
            if (url.isNotBlank()) destination.add(BrowserEntry(item.optString("title", url), url))
        }
    }

    private fun persistEntries(key: String, entries: List<BrowserEntry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(org.json.JSONObject().put("title", entry.title).put("url", entry.url))
        }
        preferences.edit { putString(key, array.toString()) }
    }

    private fun saveTabs() {
        val array = JSONArray()
        tabs.forEach { array.put(it.url) }
        preferences.edit {
            putString(PREF_TABS, array.toString())
            putInt(PREF_ACTIVE_TAB, tabs.indexOfFirst { it.id == activeTabId }.coerceAtLeast(0))
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val PREFERENCES = "helium_browser"
        private const val PREF_TABS = "tabs"
        private const val PREF_ACTIVE_TAB = "active_tab"
        private const val PREF_HISTORY = "history"
        private const val PREF_BOOKMARKS = "bookmarks"
        private const val PREF_SEARCH_ENGINE = "search_engine"

        val SEARCH_ENGINES = listOf(
            SearchEngine("DuckDuckGo", "https://duckduckgo.com/?q=%s"),
            SearchEngine("Kagi", "https://kagi.com/search?q=%s"),
            SearchEngine("Brave Search", "https://search.brave.com/search?q=%s"),
            SearchEngine("Startpage", "https://www.startpage.com/sp/search?query=%s"),
        )
    }
}

data class SearchEngine(val name: String, val template: String)
