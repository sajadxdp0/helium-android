package com.sajadxdp.osmium

import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.StorageController

/** Narrow JNI boundary for GeckoView's Java-only Android View and session API. */
internal class GeckoBrowserBridge(private val activity: MainActivity) {
    private val runtime get() = (activity.application as OsmiumApplication).runtime
    private val sessions = LinkedHashMap<String, GeckoSession>()
    private val views = LinkedHashMap<String, GeckoView>()
    private var activeId: String? = null

    fun view(tabId: String): GeckoView {
        views[tabId]?.let { return it }
        val session = GeckoSession()
        session.open(runtime)
        attachDelegates(tabId, session)
        val view = GeckoView(activity).apply { setSession(session) }
        views[tabId] = view
        sessions[tabId] = session
        if (activeId == null || activeId == tabId) activate(tabId)
        return view
    }

    fun navigate(tabId: String, url: String) {
        view(tabId)
        sessions[tabId]?.loadUri(url)
    }

    fun activate(tabId: String?, url: String? = null) {
        if (tabId != null && sessions[tabId] == null && !url.isNullOrBlank()) {
            val previous = activeId
            if (previous != tabId) previous?.let { sessions[it]?.apply { setFocused(false); setActive(false) } }
            activeId = tabId
            navigate(tabId, url)
            return
        }
        val previous = activeId
        if (previous == tabId) {
            sessions[tabId]?.setFocused(true)
            return
        }
        previous?.let { sessions[it]?.apply { setFocused(false); setActive(false) } }
        activeId = tabId
        tabId?.let { sessions[it]?.apply { setActive(true); setFocused(true) } }
    }

    fun goBack(tabId: String) { sessions[tabId]?.goBack() }
    fun goForward(tabId: String) { sessions[tabId]?.goForward() }
    fun reload(tabId: String) { sessions[tabId]?.reload() }
    fun stop(tabId: String) { sessions[tabId]?.stop() }

    fun close(tabId: String) {
        val session = sessions.remove(tabId) ?: return
        if (activeId == tabId) activeId = null
        session.setFocused(false)
        session.setActive(false)
        session.stop()
        views.remove(tabId)?.let { (it.parent as? android.view.ViewGroup)?.removeView(it) }
        session.close()
    }

    fun pause() { activeId?.let { sessions[it]?.setFocused(false) } }
    fun resume() { activeId?.let { sessions[it]?.setFocused(true) } }

    fun clearBrowsingData() {
        closeAll()
        runtime.storageController.clearData(StorageController.ClearFlags.ALL).accept(
            { activity.runOnUiThread { activity.onClearDataComplete(true) } },
            { activity.runOnUiThread { activity.onClearDataComplete(false) } },
        )
    }

    fun closeAll() {
        sessions.keys.toList().forEach(::close)
        activeId = null
    }

    private fun attachDelegates(tabId: String, session: GeckoSession) {
        session.setProgressDelegate(object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                activity.onPageEvent(tabId, RustBrowserCore.PAGE_START, url, 0, false)
            }

            override fun onProgressChange(session: GeckoSession, progress: Int) {
                activity.onPageEvent(tabId, RustBrowserCore.PAGE_PROGRESS, null, progress, false)
            }

            override fun onPageStop(session: GeckoSession, success: Boolean) {
                activity.onPageEvent(tabId, RustBrowserCore.PAGE_STOP, null, 0, success)
            }
        })
        session.setContentDelegate(object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String?) {
                activity.onPageEvent(tabId, RustBrowserCore.PAGE_TITLE, title, 0, false)
            }

            override fun onCloseRequest(session: GeckoSession) {
                activity.onPageEvent(tabId, RustBrowserCore.PAGE_CLOSE, null, 0, false)
            }
        })
        session.setNavigationDelegate(object : GeckoSession.NavigationDelegate {
            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                activity.onPageEvent(tabId, RustBrowserCore.PAGE_CAN_GO_BACK, null, 0, canGoBack)
            }

            override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
                activity.onPageEvent(tabId, RustBrowserCore.PAGE_CAN_GO_FORWARD, null, 0, canGoForward)
            }

            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                perms: List<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean,
            ) {
                activity.onPageEvent(tabId, RustBrowserCore.PAGE_LOCATION, url, 0, false)
            }
        })
        session.setPermissionDelegate(object : GeckoSession.PermissionDelegate {
            override fun onContentPermissionRequest(
                session: GeckoSession,
                perm: GeckoSession.PermissionDelegate.ContentPermission,
            ): GeckoResult<Int> = GeckoResult.fromValue(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY)

            override fun onAndroidPermissionsRequest(
                session: GeckoSession,
                permissions: Array<String>?,
                callback: GeckoSession.PermissionDelegate.Callback,
            ) = callback.reject()

            override fun onMediaPermissionRequest(
                session: GeckoSession,
                uri: String,
                video: Array<GeckoSession.PermissionDelegate.MediaSource>?,
                audio: Array<GeckoSession.PermissionDelegate.MediaSource>?,
                callback: GeckoSession.PermissionDelegate.MediaCallback,
            ) = callback.reject()
        })
    }
}
