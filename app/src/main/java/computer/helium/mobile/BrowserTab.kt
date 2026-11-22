package computer.helium.mobile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.mozilla.geckoview.GeckoSession
import java.util.UUID

class BrowserTab(
    val session: GeckoSession,
    initialUrl: String = "",
    val id: String = UUID.randomUUID().toString(),
) {
    var url by mutableStateOf(initialUrl)
    var pendingNavigation by mutableStateOf(initialUrl.takeIf(String::isNotBlank))
    var sessionOpened = false
    var title by mutableStateOf("")
    var progress by mutableFloatStateOf(0f)
    var loading by mutableStateOf(false)
    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)
}

data class BrowserEntry(val title: String, val url: String)
