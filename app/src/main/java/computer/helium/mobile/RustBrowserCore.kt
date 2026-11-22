package computer.helium.mobile

internal object RustBrowserCore {
    init {
        System.loadLibrary("helium_core")
    }

    external fun resolveAddress(input: String, searchTemplate: String): String
}
