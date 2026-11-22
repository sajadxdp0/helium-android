package computer.helium.mobile;

import android.net.Uri;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

final class TrackerShield {
    private static final Set<String> BLOCKED_HOSTS = new HashSet<>(Arrays.asList(
            "ad.doubleclick.net",
            "doubleclick.net",
            "googlesyndication.com",
            "google-analytics.com",
            "analytics.google.com",
            "googletagmanager.com",
            "connect.facebook.net",
            "analytics.twitter.com",
            "ads-twitter.com",
            "analytics.tiktok.com",
            "hotjar.com",
            "static.hotjar.com",
            "segment.io",
            "segment.com",
            "scorecardresearch.com",
            "chartbeat.com",
            "moatads.com",
            "taboola.com",
            "outbrain.com",
            "quantserve.com",
            "quantcount.com"
    ));

    private TrackerShield() {}

    static boolean shouldBlock(String url) {
        Uri uri = Uri.parse(url);
        String host = uri.getHost();
        if (host == null) return false;
        host = host.toLowerCase(java.util.Locale.ROOT);
        for (String blockedHost : BLOCKED_HOSTS) {
            if (host.equals(blockedHost) || host.endsWith("." + blockedHost)) {
                return true;
            }
        }
        return false;
    }
}
