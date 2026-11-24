package computer.helium.mobile

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import android.widget.Toast
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : Activity() {
    @JvmField
    var nativeHandle: Long = 0

    private val gecko by lazy { GeckoBrowserBridge(this) }
    private var backCallback: Any? = null

    external fun nativeOnCreate(savedInstanceState: Bundle?)
    external fun nativeOnResume()
    external fun nativeOnPause()
    external fun nativeOnDestroy()
    external fun nativeOnBackPressed()
    external fun nativeOnAction(action: Int, payload: String?)
    external fun nativeOnNavigate(input: String)
    external fun nativeOnPageEvent(tabId: String, event: Int, value: String?, number: Int, flag: Boolean)
    external fun nativeOnClearDataComplete(success: Boolean)
    external fun nativeOnUpdateResult(tag: String?, url: String?, error: String?)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RustBrowserCore.ensureLoaded()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val callback = OnBackInvokedCallback { nativeOnBackPressed() }
            backCallback = callback
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                callback,
            )
        }
        nativeOnCreate(savedInstanceState)
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            (backCallback as? OnBackInvokedCallback)?.let(onBackInvokedDispatcher::unregisterOnBackInvokedCallback)
            backCallback = null
        }
        nativeOnDestroy()
        gecko.closeAll()
        super.onDestroy()
    }

    @SuppressLint("GestureBackNavigation")
    @Deprecated("Use Android system back dispatch.")
    override fun onBackPressed() {
        nativeOnBackPressed()
    }

    fun geckoView(tabId: String) = gecko.view(tabId)
    fun existingGeckoView(tabId: String) = gecko.existingView(tabId)
    fun activateGeckoTab(tabId: String?) = gecko.activate(tabId)
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
                    connection.setRequestProperty("User-Agent", "Helium-Android")
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
                result.onSuccess { nativeOnUpdateResult(it.first, it.second, null) }
                    .onFailure { nativeOnUpdateResult(null, null, it.message ?: "Update check failed") }
            }
        }.start()
    }

    fun isDarkTheme(): Boolean =
        resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

    @Suppress("DEPRECATION")
    fun installBrowserRoot(view: View) {
        val dark = isDarkTheme()
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        } else {
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }
        val lightBarFlags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        var visibility = window.decorView.systemUiVisibility and lightBarFlags.inv()
        if (!dark) visibility = visibility or lightBarFlags
        window.decorView.systemUiVisibility = visibility
        view.setOnApplyWindowInsetsListener { root, insets ->
            val (top, bottom) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                bars.top to bars.bottom
            } else {
                insets.systemWindowInsetTop to insets.systemWindowInsetBottom
            }
            root.setPadding(0, top, 0, bottom)
            insets
        }
        setContentView(view)
    }

    fun appIcon() = packageManager.getApplicationIcon(packageName)

    fun appVersion(): String = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()

    private companion object {
        const val PROJECT_REPOSITORY = "https://github.com/sajadxdp0/helium-android"
        const val LATEST_RELEASE = "$PROJECT_REPOSITORY/releases/latest"
        const val LATEST_RELEASE_API = "https://api.github.com/repos/sajadxdp0/helium-android/releases/latest"
    }
}
