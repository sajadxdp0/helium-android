package computer.helium.mobile

import android.app.Application
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings

class HeliumApplication : Application() {
    lateinit var runtime: GeckoRuntime
        private set

    override fun onCreate() {
        super.onCreate()

        val contentBlocking = ContentBlocking.Settings.Builder()
            .antiTracking(
                ContentBlocking.AntiTracking.DEFAULT or
                    ContentBlocking.AntiTracking.CRYPTOMINING or
                    ContentBlocking.AntiTracking.FINGERPRINTING,
            )
            .cookieBehavior(ContentBlocking.CookieBehavior.ACCEPT_NON_TRACKERS)
            .build()

        runtime = GeckoRuntime.create(
            this,
            GeckoRuntimeSettings.Builder()
                .contentBlocking(contentBlocking)
                .globalPrivacyControlEnabled(true)
                .automaticFontSizeAdjustment(true)
                .build(),
        )
    }
}
