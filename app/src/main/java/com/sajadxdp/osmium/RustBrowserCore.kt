package com.sajadxdp.osmium

internal object RustBrowserCore {
    const val PAGE_START = 1
    const val PAGE_PROGRESS = 2
    const val PAGE_STOP = 3
    const val PAGE_TITLE = 4
    const val PAGE_CAN_GO_BACK = 5
    const val PAGE_CAN_GO_FORWARD = 6
    const val PAGE_LOCATION = 7
    const val PAGE_CLOSE = 8

    init {
        System.loadLibrary("osmium_core")
    }

    fun ensureLoaded() = Unit
}
