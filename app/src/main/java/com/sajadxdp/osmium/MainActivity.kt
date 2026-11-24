package com.sajadxdp.osmium

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.geckoview.GeckoView
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : ComponentActivity() {
    @JvmField
    var nativeHandle: Long = 0

    private val gecko by lazy { GeckoBrowserBridge(this) }
    private var uiState by mutableStateOf(OsmiumUiState())

    external fun nativeOnCreate(savedInstanceState: Bundle?): String
    external fun nativeOnResume()
    external fun nativeOnPause()
    external fun nativeOnDestroy()
    external fun nativeOnAction(action: String, payload: String?): String
    external fun nativeOnPageEvent(tabId: String, event: Int, value: String?, number: Int, flag: Boolean): String
    external fun nativeOnClearDataComplete(success: Boolean): String
    external fun nativeOnUpdateResult(tag: String?, url: String?, error: String?): String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RustBrowserCore.ensureLoaded()
        enableEdgeToEdge()
        applySnapshot(nativeOnCreate(savedInstanceState))
        if (uiState.url.isNotBlank()) gecko.activate(uiState.activeTabId, uiState.url)
        setContent {
            OsmiumApp(
                state = uiState,
                dispatch = ::dispatch,
                browserView = gecko::view,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        gecko.resume()
        nativeOnResume()
    }

    override fun onPause() {
        nativeOnPause()
        gecko.pause()
        super.onPause()
    }

    override fun onDestroy() {
        nativeOnDestroy()
        gecko.closeAll()
        super.onDestroy()
    }

    private fun dispatch(action: String, payload: String?) {
        applySnapshot(nativeOnAction(action, payload))
    }

    fun onPageEvent(tabId: String, event: Int, value: String?, number: Int, flag: Boolean) {
        applySnapshot(nativeOnPageEvent(tabId, event, value, number, flag))
    }

    fun onClearDataComplete(success: Boolean) {
        applySnapshot(nativeOnClearDataComplete(success))
    }

    private fun applySnapshot(snapshot: String) {
        if (snapshot.isBlank()) return
        val next = runCatching { parseSnapshot(snapshot) }.getOrNull() ?: return
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            uiState = next
        } else {
            runOnUiThread { uiState = next }
        }
    }

    private fun parseSnapshot(snapshot: String): OsmiumUiState {
        val root = JSONObject(snapshot)
        return OsmiumUiState(
            panel = root.optString("panel", "browser"),
            activeTabId = root.optString("activeTabId", ""),
            url = root.optString("url", ""),
            title = root.optString("title", ""),
            loading = root.optBoolean("loading"),
            progress = root.optInt("progress").coerceIn(0, 100),
            canGoBack = root.optBoolean("canGoBack"),
            canGoForward = root.optBoolean("canGoForward"),
            bookmarked = root.optBoolean("bookmarked"),
            tabs = root.optJSONArray("tabs").toTabList(),
            bookmarks = root.optJSONArray("bookmarks").toEntryList(),
            history = root.optJSONArray("history").toEntryList(),
            searchEngine = root.optString("searchEngine", "DuckDuckGo"),
            updateStatus = root.optString("updateStatus", ""),
            version = root.optString("version", appVersion()),
        )
    }

    private fun JSONArray?.toTabList(): List<OsmiumTab> = buildList {
        val array = this@toTabList ?: return@buildList
        for (index in 0 until array.length()) {
            val tab = array.optJSONObject(index) ?: continue
            add(
                OsmiumTab(
                    id = tab.optString("id"),
                    title = tab.optString("title"),
                    url = tab.optString("url"),
                    active = tab.optBoolean("active"),
                ),
            )
        }
    }

    private fun JSONArray?.toEntryList(): List<OsmiumEntry> = buildList {
        val array = this@toEntryList ?: return@buildList
        for (index in 0 until array.length()) {
            val entry = array.optJSONObject(index) ?: continue
            add(OsmiumEntry(entry.optString("title"), entry.optString("url")))
        }
    }

    fun geckoView(tabId: String): GeckoView = gecko.view(tabId)
    fun activateGeckoTab(tabId: String?) = gecko.activate(tabId)
    fun ensureGeckoTab(tabId: String, url: String) = gecko.activate(tabId, url)
    fun navigateGecko(tabId: String, url: String) = gecko.navigate(tabId, url)
    fun backGecko(tabId: String) = gecko.goBack(tabId)
    fun forwardGecko(tabId: String) = gecko.goForward(tabId)
    fun reloadGecko(tabId: String) = gecko.reload(tabId)
    fun stopGecko(tabId: String) = gecko.stop(tabId)
    fun closeGeckoTab(tabId: String) = gecko.close(tabId)
    fun clearGeckoData() = gecko.clearBrowsingData()
    fun showToast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    fun openExternal(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
    }

    fun shareAddress(url: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
        }
        startActivity(Intent.createChooser(send, "Share page"))
    }

    fun checkForUpdates() {
        Thread {
            val result = runCatching {
                val connection = URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection
                try {
                    connection.connectTimeout = 8_000
                    connection.readTimeout = 8_000
                    connection.setRequestProperty("Accept", "application/vnd.github+json")
                    connection.setRequestProperty("User-Agent", "Osmium-Android")
                    check(connection.responseCode == HttpURLConnection.HTTP_OK) {
                        "GitHub returned HTTP ${connection.responseCode}"
                    }
                    val release = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                    Triple(
                        release.getString("tag_name"),
                        release.optString("html_url").ifBlank { LATEST_RELEASE },
                        null,
                    )
                } finally {
                    connection.disconnect()
                }
            }
            runOnUiThread {
                result.onSuccess { applySnapshot(nativeOnUpdateResult(it.first, it.second, null)) }
                    .onFailure {
                        applySnapshot(nativeOnUpdateResult(null, null, it.message ?: "Update check failed"))
                    }
            }
        }.start()
    }

    fun appVersion(): String = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()

    private companion object {
        const val PROJECT_REPOSITORY = "https://github.com/sajadxdp0/osmium-android"
        const val LATEST_RELEASE = "$PROJECT_REPOSITORY/releases/latest"
        const val LATEST_RELEASE_API = "https://api.github.com/repos/sajadxdp0/osmium-android/releases/latest"
    }
}
