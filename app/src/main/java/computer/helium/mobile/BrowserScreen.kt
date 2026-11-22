package computer.helium.mobile

import androidx.activity.compose.BackHandler
import androidx.core.net.toUri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.ui.text.style.TextOverflow
import org.mozilla.geckoview.GeckoView

private enum class BrowserSheet { TABS, MENU, BOOKMARKS, HISTORY, PRIVACY, SETTINGS, ABOUT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(activity: MainActivity) {
    var sheet by remember { mutableStateOf<BrowserSheet?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    val active = activity.currentTab

    BackHandler {
        when {
            confirmClear -> confirmClear = false
            sheet != null -> sheet = null
            active?.canGoBack == true -> activity.goBack()
            !active?.url.isNullOrBlank() -> activity.goHome()
            activity.browserTabs.size > 1 -> active?.let(activity::closeTab)
            else -> activity.finish()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
        val expanded = maxWidth >= 840.dp
        val navigationInsets = if (expanded) {
            Modifier.windowInsetsPadding(WindowInsets.navigationBars)
        } else {
            Modifier
        }
        Row(Modifier.fillMaxSize().then(navigationInsets)) {
            if (expanded) {
                BrowserRail(
                    active = active,
                    onBack = activity::goBack,
                    onForward = activity::goForward,
                    onHome = activity::goHome,
                    onTabs = { sheet = BrowserSheet.TABS },
                    onBookmarks = { sheet = BrowserSheet.BOOKMARKS },
                    onMenu = { sheet = BrowserSheet.MENU },
                )
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                BrowserHeader(
                    activity = activity,
                    active = active,
                    expanded = expanded,
                    onTabs = { sheet = BrowserSheet.TABS },
                    onPrivacy = { sheet = BrowserSheet.PRIVACY },
                )
                active?.takeIf { it.loading }?.let { tab ->
                    LinearProgressIndicator(
                        progress = { tab.progress },
                        modifier = Modifier.fillMaxWidth().height(2.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainer,
                    )
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (active == null || active.url.isBlank()) {
                        HomePanel(
                            searchEngine = activity.searchEngine.name,
                            bookmarks = activity.bookmarkEntries,
                            onFavorite = activity::navigateFromAddress,
                            onPrivacy = { sheet = BrowserSheet.PRIVACY },
                        )
                    } else {
                        GeckoPage(active)
                    }
                }
                if (!expanded) {
                    BrowserNavigationBar(
                        active = active,
                        onBack = activity::goBack,
                        onForward = activity::goForward,
                        onHome = activity::goHome,
                        onBookmark = activity::addBookmark,
                        onMenu = { sheet = BrowserSheet.MENU },
                        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
                    )
                }
            }
        }
    }
    }

    sheet?.let { visibleSheet ->
        ModalBottomSheet(
            onDismissRequest = { sheet = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            when (visibleSheet) {
                BrowserSheet.TABS -> TabsSheet(
                    tabs = activity.browserTabs,
                    activeId = active?.id,
                    onSelect = { activity.selectTab(it); sheet = null },
                    onClose = activity::closeTab,
                    onNew = { activity.newTab(); sheet = null },
                )
                BrowserSheet.MENU -> MenuSheet(
                    hasPage = !active?.url.isNullOrBlank(),
                    onAction = { action ->
                        sheet = null
                        when (action) {
                            "new" -> activity.newTab()
                            "tabs" -> sheet = BrowserSheet.TABS
                            "bookmarks" -> sheet = BrowserSheet.BOOKMARKS
                            "history" -> sheet = BrowserSheet.HISTORY
                            "privacy" -> sheet = BrowserSheet.PRIVACY
                            "settings" -> sheet = BrowserSheet.SETTINGS
                            "share" -> activity.sharePage()
                            "bookmark" -> activity.addBookmark()
                            "about" -> sheet = BrowserSheet.ABOUT
                        }
                    },
                )
                BrowserSheet.BOOKMARKS -> EntriesSheet(
                    title = "Bookmarks",
                    entries = activity.bookmarkEntries,
                    empty = "Pages you save will appear here.",
                    onOpen = { activity.openEntry(it); sheet = null },
                )
                BrowserSheet.HISTORY -> EntriesSheet(
                    title = "Recent pages",
                    entries = activity.historyEntries,
                    empty = "Pages you visit will appear here.",
                    onOpen = { activity.openEntry(it); sheet = null },
                )
                BrowserSheet.PRIVACY -> PrivacySheet(active)
                BrowserSheet.SETTINGS -> SettingsSheet(
                    activity = activity,
                    onClear = { confirmClear = true; sheet = null },
                )
                BrowserSheet.ABOUT -> AboutSheet(activity.aboutMessage())
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear browsing data?") },
            text = { Text("This clears local history, cookies, cached files, and site storage. Open tabs will close.") },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; activity.clearBrowsingData() }) {
                    Text("Clear data")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun BrowserHeader(
    activity: MainActivity,
    active: BrowserTab?,
    expanded: Boolean,
    onTabs: () -> Unit,
    onPrivacy: () -> Unit,
) {
    var address by remember(active?.id) { mutableStateOf(active?.url.orEmpty()) }
    var focused by remember(active?.id) { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val secureConnection = active?.url?.startsWith("https://", ignoreCase = true) == true
    val insecureConnection = active?.url?.startsWith("http://", ignoreCase = true) == true

    LaunchedEffect(active?.id, active?.url) {
        if (!focused) address = active?.url.orEmpty()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = if (expanded) 28.dp else 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.helium_mark),
                contentDescription = "Helium",
                modifier = Modifier.size(30.dp),
                contentScale = ContentScale.Fit,
            )
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text("helium", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    stringResource(R.string.browser_tagline),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onTabs) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Tab, contentDescription = "Open tabs")
                    Surface(
                        modifier = Modifier.align(Alignment.TopEnd).size(17.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(activity.browserTabs.size.toString(), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        OutlinedTextField(
            value = if (focused) address else active?.url.orEmpty().let(::displayAddress),
            onValueChange = { address = it },
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 980.dp)
                .heightIn(min = 54.dp)
                .onFocusChanged { focused = it.isFocused },
            singleLine = true,
            shape = RoundedCornerShape(20.dp),
            placeholder = { Text(stringResource(R.string.address_hint)) },
            textStyle = MaterialTheme.typography.bodyMedium,
            leadingIcon = {
                IconButton(onClick = onPrivacy) {
                    Icon(
                        imageVector = when {
                            secureConnection -> Icons.Default.Lock
                            insecureConnection -> Icons.Default.LockOpen
                            else -> Icons.Default.Shield
                        },
                        contentDescription = when {
                            secureConnection -> "Secure connection. Open privacy details"
                            insecureConnection -> "Connection is not secure. Open privacy details"
                            else -> "Privacy protection"
                        },
                        tint = if (insecureConnection) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                }
            },
            trailingIcon = {
                IconButton(
                    onClick = activity::reloadOrStop,
                ) {
                    Icon(
                        if (active?.loading == true) Icons.Default.Close else Icons.Default.Refresh,
                        contentDescription = if (active?.loading == true) "Stop loading" else "Reload",
                    )
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(
                onGo = {
                    activity.navigateFromAddress(address)
                    focused = false
                    keyboard?.hide()
                    focusManager.clearFocus()
                },
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.Transparent,
            ),
        )
    }
}

@Composable
private fun BrowserRail(
    active: BrowserTab?,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onHome: () -> Unit,
    onTabs: () -> Unit,
    onBookmarks: () -> Unit,
    onMenu: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .width(78.dp)
            .fillMaxHeight()
            .windowInsetsPadding(WindowInsets.statusBars),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            RailAction(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack, active?.canGoBack == true)
            RailAction(Icons.AutoMirrored.Filled.ArrowForward, "Forward", onForward, active?.canGoForward == true)
            RailAction(Icons.Default.Home, "Home", onHome)
            RailAction(Icons.Default.Tab, "Tabs", onTabs)
            RailAction(Icons.Default.BookmarkBorder, "Bookmarks", onBookmarks)
            Spacer(Modifier.weight(1f))
            RailAction(Icons.Default.MoreVert, "Menu", onMenu)
        }
    }
}

@Composable
private fun RailAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
        Text(label, fontSize = 10.sp, maxLines = 1, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
    }
}

@Composable
private fun BrowserNavigationBar(
    active: BrowserTab?,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onHome: () -> Unit,
    onBookmark: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        NavigationBarItem(
            selected = false,
            onClick = onBack,
            enabled = active?.canGoBack == true,
            icon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) },
            label = { Text("Back") },
        )
        NavigationBarItem(
            selected = false,
            onClick = onForward,
            enabled = active?.canGoForward == true,
            icon = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null) },
            label = { Text("Forward") },
        )
        NavigationBarItem(
            selected = false,
            onClick = onHome,
            icon = { Icon(Icons.Default.Home, contentDescription = null) },
            label = { Text("Home") },
        )
        NavigationBarItem(
            selected = false,
            onClick = onBookmark,
            enabled = !active?.url.isNullOrBlank(),
            icon = { Icon(Icons.Default.BookmarkBorder, contentDescription = null) },
            label = { Text("Save") },
        )
        NavigationBarItem(
            selected = false,
            onClick = onMenu,
            icon = { Icon(Icons.Default.MoreVert, contentDescription = null) },
            label = { Text("More") },
        )
    }
}

@Composable
private fun HomePanel(
    searchEngine: String,
    bookmarks: List<BrowserEntry>,
    onFavorite: (String) -> Unit,
    onPrivacy: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Column(
                modifier = Modifier.fillMaxWidth().widthIn(max = 720.dp).padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(R.drawable.helium_mark),
                        contentDescription = null,
                        modifier = Modifier.size(38.dp),
                    )
                    Text(
                        "A little more room to breathe.",
                        modifier = Modifier.padding(start = 12.dp),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "The web, at your pace.",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 42.sp,
                )
                Text(
                    "Search for something, pick up where you left off, or open a favorite.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            Text("Quick access", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (bookmarks.isEmpty()) {
                    items(FAVORITES) { favorite ->
                        FavoriteCard(favorite = favorite, onClick = { onFavorite(favorite.url) })
                    }
                } else {
                    items(bookmarks.take(8)) { bookmark ->
                        FavoriteCard(
                            favorite = Favorite(bookmark.title, displayAddress(bookmark.url), bookmark.url),
                            onClick = { onFavorite(bookmark.url) },
                        )
                    }
                }
            }
        }
        item {
            Card(
                onClick = onPrivacy,
                modifier = Modifier.fillMaxWidth().widthIn(max = 720.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(42.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text("Protection is on", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            "GeckoView blocks known trackers and limits tracking cookies while you browse.",
                            modifier = Modifier.padding(top = 4.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Privacy details", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Text(
                "Searches go to $searchEngine unless you choose another provider in settings.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private data class Favorite(val name: String, val host: String, val url: String)

private val FAVORITES = listOf(
    Favorite("Kagi", "kagi.com", "https://kagi.com/"),
    Favorite("GitHub", "github.com", "https://github.com/"),
    Favorite("Wikipedia", "wikipedia.org", "https://www.wikipedia.org/"),
)

@Composable
private fun FavoriteCard(favorite: Favorite, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.width(142.dp).height(105.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(30.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(favorite.name.first().toString(), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Column {
                Text(favorite.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(favorite.host, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun GeckoPage(tab: BrowserTab) {
    DisposableEffect(tab.id) {
        tab.session.setActive(true)
        tab.session.setFocused(true)
        onDispose {
            tab.session.setFocused(false)
            tab.session.setActive(false)
        }
    }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context -> GeckoView(context).apply { setSession(tab.session) } },
        onRelease = { view -> view.releaseSession() },
    )
}

@Composable
private fun TabsSheet(
    tabs: List<BrowserTab>,
    activeId: String?,
    onSelect: (BrowserTab) -> Unit,
    onClose: (BrowserTab) -> Unit,
    onNew: () -> Unit,
) {
    SheetColumn {
        SheetHeader("Tabs", trailing = { IconButton(onClick = onNew) { Icon(Icons.Default.Add, "New tab") } })
        if (tabs.isEmpty()) EmptyMessage("No tabs are open.")
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(tabs, key = { it.id }) { tab ->
                Card(
                    onClick = { onSelect(tab) },
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (tab.id == activeId) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainer,
                    ),
                ) {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(tab.title.ifBlank { if (tab.url.isBlank()) "New tab" else tab.url }, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                            if (tab.url.isNotBlank()) Text(displayAddress(tab.url), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { onClose(tab) }) { Icon(Icons.Default.Close, contentDescription = "Close tab") }
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuSheet(hasPage: Boolean, onAction: (String) -> Unit) {
    SheetColumn {
        SheetHeader("Browse")
        MenuAction(Icons.Default.Add, "New tab", "Start with a clean page") { onAction("new") }
        MenuAction(Icons.Default.Tab, "Your tabs", "Switch between open pages") { onAction("tabs") }
        MenuAction(Icons.Default.BookmarkBorder, "Bookmarks", "Pages you saved") { onAction("bookmarks") }
        MenuAction(Icons.Default.History, "Recent pages", "Your local browsing history") { onAction("history") }
        if (hasPage) {
            MenuAction(Icons.Default.BookmarkBorder, "Save this page", "Add the current page to bookmarks") { onAction("bookmark") }
            MenuAction(Icons.Default.Share, "Share page", "Send the current address") { onAction("share") }
        }
        MenuAction(Icons.Default.Shield, "Privacy", "Tracking protection details") { onAction("privacy") }
        MenuAction(Icons.Default.Settings, "Settings", "Search and site data") { onAction("settings") }
        MenuAction(Icons.AutoMirrored.Filled.HelpOutline, "About", "About this independent project") { onAction("about") }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun PrivacySheet(active: BrowserTab?) {
    SheetColumn {
        SheetHeader("Privacy protection")
        val connection = when {
            active?.url?.startsWith("https://", ignoreCase = true) == true -> "Secure connection"
            active?.url?.startsWith("http://", ignoreCase = true) == true -> "Connection is not secure"
            else -> "No site open"
        }
        Text(connection, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Enhanced Tracking Protection", modifier = Modifier.padding(start = 10.dp), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                }
                Text("Known ad, analytics, social, cryptomining, and fingerprinting trackers are restricted by GeckoView. Third-party tracking cookies are limited, and Global Privacy Control is sent to sites.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            "Protection is based on GeckoView’s built-in lists. It does not include Helium desktop’s uBlock Origin fork or its full filter set, and some sites may need an exception.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp, bottom = 22.dp),
        )
    }
}

@Composable
private fun SettingsSheet(activity: MainActivity, onClear: () -> Unit) {
    SheetColumn {
        SheetHeader("Settings")
        Text("Search provider", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 6.dp))
        MainActivity.SEARCH_ENGINES.forEachIndexed { index, engine ->
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { activity.selectSearchEngine(index) }.padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = activity.searchEngine == engine, onClick = { activity.selectSearchEngine(index) })
                Text(engine.name, modifier = Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyLarge)
            }
        }
        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text("Privacy", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
        Text("Tracking protection and Global Privacy Control are on. This prototype does not yet offer per-site exceptions.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(18.dp))
        Button(onClick = onClear, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Icon(Icons.Default.DeleteOutline, contentDescription = null)
            Text("Clear browsing data", modifier = Modifier.padding(start = 8.dp))
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun EntriesSheet(title: String, entries: List<BrowserEntry>, empty: String, onOpen: (BrowserEntry) -> Unit) {
    SheetColumn {
        SheetHeader(title)
        if (entries.isEmpty()) {
            EmptyMessage(empty)
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                items(entries) { entry ->
                    ListItem(
                        supportingContent = { Text(displayAddress(entry.url), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingContent = { Icon(if (title == "Bookmarks") Icons.Default.BookmarkBorder else Icons.Default.History, contentDescription = null) },
                        modifier = Modifier.clip(RoundedCornerShape(14.dp)).clickable { onOpen(entry) },
                    ) {
                        Text(entry.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutSheet(message: String) {
    SheetColumn {
        SheetHeader("About Helium for Android")
        Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun SheetColumn(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().heightIn(max = 760.dp).imePadding().padding(horizontal = 22.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content,
    )
}

@Composable
private fun SheetHeader(title: String, trailing: @Composable (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        trailing?.invoke()
    }
}

@Composable
private fun EmptyMessage(message: String) {
    Text(
        message,
        modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyLarge,
    )
}

@Composable
private fun MenuAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    detail: String,
    onClick: () -> Unit,
) {
    ListItem(
        supportingContent = { Text(detail) },
        leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick),
    ) {
        Text(title, fontWeight = FontWeight.Medium)
    }
}

private fun displayAddress(url: String): String {
    if (url.isBlank()) return ""
    return runCatching {
        UriDisplay.parse(url)
    }.getOrDefault(url.removePrefix("https://").removePrefix("http://").removePrefix("www."))
}

private object UriDisplay {
    fun parse(value: String): String {
        val uri = value.toUri()
        val host = uri.host ?: return value
        val cleanHost = host.removePrefix("www.")
        val path = uri.encodedPath.orEmpty().takeIf { it.isNotBlank() && it != "/" }.orEmpty()
        return cleanHost + path
    }
}
