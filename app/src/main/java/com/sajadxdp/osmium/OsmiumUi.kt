package com.sajadxdp.osmium

import android.content.res.Configuration
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogProperties
import org.mozilla.geckoview.GeckoView

private val OsmiumRed = Color(0xFFB32632)
private val OsmiumDarkRed = Color(0xFFFFB3B8)
private val OsmiumDarkSurface = Color(0xFF1B1919)

private val OsmiumLightScheme = lightColorScheme(
    primary = OsmiumRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDADB),
    onPrimaryContainer = Color(0xFF410009),
    secondary = Color(0xFF625B5B),
    tertiary = Color(0xFF765A2D),
    surface = Color(0xFFFFF8F7),
    surfaceContainer = Color(0xFFF5ECEB),
    background = Color(0xFFFFF8F7),
)
private val OsmiumDarkScheme = darkColorScheme(
    primary = OsmiumDarkRed,
    onPrimary = Color(0xFF680014),
    primaryContainer = Color(0xFF8F1727),
    onPrimaryContainer = Color(0xFFFFDADB),
    secondary = Color(0xFFD0C3C2),
    tertiary = Color(0xFFE3C28D),
    surface = OsmiumDarkSurface,
    surfaceContainer = Color(0xFF292525),
    background = OsmiumDarkSurface,
)

data class OsmiumUiState(
    val panel: String = "browser",
    val activeTabId: String = "",
    val url: String = "",
    val title: String = "",
    val loading: Boolean = false,
    val progress: Int = 0,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val bookmarked: Boolean = false,
    val tabs: List<OsmiumTab> = emptyList(),
    val bookmarks: List<OsmiumEntry> = emptyList(),
    val history: List<OsmiumEntry> = emptyList(),
    val searchEngine: String = "DuckDuckGo",
    val updateStatus: String = "",
    val version: String = "0.5.0",
)

data class OsmiumTab(val id: String, val title: String, val url: String, val active: Boolean)
data class OsmiumEntry(val title: String, val url: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OsmiumApp(
    state: OsmiumUiState,
    dispatch: (String, String?) -> Unit,
    browserView: (String) -> GeckoView,
) {
    val dark = LocalConfiguration.current.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    val context = LocalContext.current
    val dynamicScheme = remember(dark, context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else null
    }
    val colorScheme = when {
        dynamicScheme != null -> dynamicScheme.copy(primary = if (dark) OsmiumDarkRed else OsmiumRed)
        dark -> OsmiumDarkScheme
        else -> OsmiumLightScheme
    }
    var clearConfirm by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val uiDispatch: (String, String?) -> Unit = { action, payload ->
        if (action == "clear_data") clearConfirm = true else dispatch(action, payload)
    }
    androidx.activity.compose.BackHandler {
        dispatch("system_back", null)
    }
    var searchText by remember(state.url, state.panel) { mutableStateOf(state.url) }
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val onSearch: (String) -> Unit = { raw ->
        val input = raw.trim()
        if (input.isNotEmpty()) {
            keyboard?.hide()
            focusManager.clearFocus()
            dispatch("navigate", input)
        }
    }

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        // Expressive motion and larger geometry give the browser a softer native rhythm.
        motionScheme = androidx.compose.material3.MotionScheme.expressive(),
        shapes = MaterialTheme.shapes.copy(
            extraSmall = RoundedCornerShape(12.dp),
            small = RoundedCornerShape(18.dp),
            medium = RoundedCornerShape(24.dp),
            large = RoundedCornerShape(30.dp),
            extraLarge = RoundedCornerShape(36.dp),
        ),
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        ) { insets ->
            Column(
                Modifier.fillMaxSize()
                    .padding(insets)
                    .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
                    .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
            ) {
                AnimatedContent(
                    targetState = state.panel,
                    label = "browser panel transition",
                    modifier = Modifier.weight(1f),
                ) { panel ->
                    when (panel) {
                        "browser" -> BrowserContent(
                            state = state,
                            searchText = searchText,
                            onSearchText = { searchText = it },
                            onSearch = onSearch,
                            dispatch = uiDispatch,
                            browserView = browserView,
                            openMenu = { menuOpen = true },
                        )
                        "tabs" -> TabsPanel(state, uiDispatch)
                        "bookmarks" -> EntriesPanel("Bookmarks", "Your saved places", state.bookmarks, Icons.Default.Bookmark, uiDispatch)
                        "history" -> EntriesPanel("History", "Pages you have visited", state.history, Icons.Default.History, uiDispatch, action = "history")
                        "settings" -> SettingsPanel(state, uiDispatch)
                        "about" -> AboutPanel(state, uiDispatch)
                        "privacy" -> PrivacyPanel(uiDispatch)
                        else -> HomeContent(state, uiDispatch)
                    }
                }
                AnimatedVisibility(
                    visible = state.panel == "browser",
                    enter = slideInVertically { it / 2 } + fadeIn(),
                    exit = slideOutVertically { it / 2 } + fadeOut(),
                ) {
                    BottomBar(state, uiDispatch)
                }
            }
        }

        if (menuOpen) {
            ModalBottomSheet(
                onDismissRequest = { menuOpen = false },
                sheetState = sheetState,
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                MenuSheet(
                    state = state,
                    dismiss = { menuOpen = false },
                    dispatch = { action, payload -> menuOpen = false; uiDispatch(action, payload) },
                    confirmClear = { menuOpen = false; clearConfirm = true },
                )
            }
        }
        if (clearConfirm) {
            AlertDialog(
                onDismissRequest = { clearConfirm = false },
                icon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                title = { Text("Clear browsing data?") },
                text = { Text("This removes your local browsing history and stored site data. Your bookmarks and open tabs stay safe.") },
                confirmButton = {
                    Button(onClick = { clearConfirm = false; dispatch("clear_data", null) }) { Text("Clear data") }
                },
                dismissButton = { TextButton(onClick = { clearConfirm = false }) { Text("Cancel") } },
                properties = DialogProperties(usePlatformDefaultWidth = false),
            )
        }
    }
}

@Composable
private fun BrowserContent(
    state: OsmiumUiState,
    searchText: String,
    onSearchText: (String) -> Unit,
    onSearch: (String) -> Unit,
    dispatch: (String, String?) -> Unit,
    browserView: (String) -> GeckoView,
    openMenu: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        UrlBar(
            state = state,
            text = searchText,
            onText = onSearchText,
            onSearch = onSearch,
            dispatch = dispatch,
            openMenu = openMenu,
        )
        AnimatedVisibility(state.loading) {
            LinearProgressIndicator(
                progress = { (state.progress.coerceIn(0, 100) / 100f) },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.Transparent,
            )
        }
        if (state.url.isBlank()) {
            HomeContent(state, dispatch)
        } else if (state.activeTabId.isNotEmpty()) {
            keyTab(state.activeTabId) {
                AndroidView(
                    factory = { browserView(state.activeTabId) },
                    modifier = Modifier.fillMaxSize(),
                    update = { },
                )
            }
        } else {
            HomeContent(state, dispatch)
        }
    }
}

@Composable
private fun keyTab(tabId: String, content: @Composable () -> Unit) {
    androidx.compose.runtime.key(tabId) { content() }
}

@Composable
private fun UrlBar(
    state: OsmiumUiState,
    text: String,
    onText: (String) -> Unit,
    onSearch: (String) -> Unit,
    dispatch: (String, String?) -> Unit,
    openMenu: () -> Unit,
) {
    val addressState = rememberTextFieldState(initialText = text)
    LaunchedEffect(text) {
        if (addressState.text.toString() != text) {
            addressState.setTextAndPlaceCursorAtEnd(text)
        }
    }
    LaunchedEffect(addressState) {
        snapshotFlow { addressState.text.toString() }.collect { onText(it) }
    }
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp, top = 9.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.weight(1f).height(54.dp),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 1.dp,
        ) {
            Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (state.url.startsWith("https://")) Icons.Default.Lock else Icons.Default.Search,
                    contentDescription = if (state.url.startsWith("https://")) "Secure connection" else "Search or enter address",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(19.dp),
                )
                Spacer(Modifier.width(10.dp))
                BasicTextField(
                    state = addressState,
                    modifier = Modifier.weight(1f),
                    lineLimits = TextFieldLineLimits.SingleLine,
                    textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontFamily = FontFamily.Monospace),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    onKeyboardAction = { onSearch(addressState.text.toString()) },
                    decorator = { inner ->
                        Box {
                            if (addressState.text.isEmpty()) Text("Search or enter address", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                            inner()
                        }
                    },
                )
                if (text.isNotEmpty()) {
                    IconButton(onClick = { onText("") }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Default.Close, "Clear address", modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        IconButton(onClick = openMenu, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Default.MoreVert, contentDescription = "More browser options")
        }
    }
}

@Composable
private fun HomeContent(state: OsmiumUiState, dispatch: (String, String?) -> Unit) {
    val favorites = state.bookmarks.take(4)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 26.dp, bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(30.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OsmiumBrandMark(44.dp, contentDescription = "Osmium")
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Osmium", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("Private by design", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Favorites", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    if (favorites.isNotEmpty()) {
                        TextButton(onClick = { dispatch("bookmarks", null) }) { Text("See all") }
                    }
                }
                if (favorites.isEmpty()) {
                    Text(
                        "Pages you save will appear here.",
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Spacer(Modifier.height(8.dp))
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().height(98.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(favorites, key = { it.url }) { entry ->
                            FavoriteShortcut(
                                entry = entry,
                                modifier = Modifier.width(76.dp),
                                onClick = { dispatch("open_entry", entry.url) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoriteShortcut(entry: OsmiumEntry, modifier: Modifier, onClick: () -> Unit) {
    val label = entry.title.takeUnless { it.isBlank() || it.startsWith("http://") || it.startsWith("https://") }
        ?: entry.url.removePrefix("https://").removePrefix("http://").substringBefore('/')
    Column(
        modifier = modifier.clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(48.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    label.firstOrNull()?.uppercase() ?: "•",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun OsmiumBrandMark(
    size: Dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    Surface(
        modifier = modifier.size(size),
        shape = RoundedCornerShape(size * 0.26f),
        color = Color(0xFFC6283D),
    ) {
        Image(
            painter = painterResource(R.drawable.ic_osmium_mark),
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun BottomBar(state: OsmiumUiState, dispatch: (String, String?) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { dispatch("back", null) }, enabled = state.canGoBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
            }
            IconButton(onClick = { dispatch("forward", null) }, enabled = state.canGoForward, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, "Forward")
            }
            FilledTonalIconButton(onClick = { dispatch("home", null) }, modifier = Modifier.size(50.dp)) {
                Icon(Icons.Default.Home, "Home")
            }
            IconButton(onClick = { dispatch("tabs", null) }, modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Tab, "Tabs")
                    Surface(color = MaterialTheme.colorScheme.primary, shape = CircleShape, modifier = Modifier.align(Alignment.TopEnd).size(16.dp)) {
                        Box(contentAlignment = Alignment.Center) { Text(state.tabs.size.toString(), color = MaterialTheme.colorScheme.onPrimary, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                    }
                }
            }
            IconButton(onClick = { dispatch("new_tab", null) }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.Add, "New tab")
            }
        }
    }
}

@Composable
private fun TabsPanel(state: OsmiumUiState, dispatch: (String, String?) -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        PanelHeader("Your tabs", "${state.tabs.size} open", onBack = { dispatch("close_panel", null) }, trailing = {
            FilledTonalIconButton(onClick = { dispatch("new_tab", null) }) { Icon(Icons.Default.Add, "New tab") }
        })
        if (state.tabs.isEmpty()) EmptyState(Icons.Default.Tab, "Nothing open yet", "Open a new tab to get started.")
        else LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(state.tabs, key = { it.id }) { tab ->
                Card(
                    onClick = { dispatch("select_tab", tab.id) },
                    shape = RoundedCornerShape(26.dp),
                    colors = CardDefaults.cardColors(containerColor = if (tab.active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 15.dp, bottom = 15.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(12.dp), color = if (tab.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface, modifier = Modifier.size(42.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                if (tab.active) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.onPrimary)
                                else Text((tab.title.firstOrNull() ?: '•').uppercase(), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.width(13.dp))
                        Column(Modifier.weight(1f)) {
                            Text(tab.title.ifBlank { "New tab" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(tab.url.ifBlank { "Ready when you are" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = { dispatch("close_tab", tab.id) }, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.Close, "Close ${tab.title.ifBlank { "tab" }}") }
                    }
                }
            }
        }
    }
}

@Composable
private fun EntriesPanel(
    title: String,
    subtitle: String,
    entries: List<OsmiumEntry>,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    dispatch: (String, String?) -> Unit,
    action: String = "bookmarks",
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        PanelHeader(title, subtitle, onBack = { dispatch("close_panel", null) }, trailing = {
            if (action == "history") IconButton(onClick = { dispatch("clear_data", null) }) { Icon(Icons.Default.DeleteOutline, "Clear browsing data") }
        })
        if (entries.isEmpty()) EmptyState(icon, "Nothing here yet", if (action == "history") "Pages you visit will appear here." else "Bookmark a page to keep it close.")
        else LazyColumn(contentPadding = PaddingValues(bottom = 26.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            items(entries) { entry ->
                EntryRow(entry, icon, onClick = { dispatch("open_entry", entry.url) }, trailing = {
                    IconButton(onClick = { dispatch("share", entry.url) }) { Icon(Icons.Default.Share, "Share ${entry.title}") }
                })
            }
        }
    }
}

@Composable
private fun EntryRow(entry: OsmiumEntry, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, trailing: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick).padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.size(44.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.title.ifBlank { entry.url }, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(entry.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        trailing()
    }
}

@Composable
private fun SettingsPanel(state: OsmiumUiState, dispatch: (String, String?) -> Unit) {
    val engines = listOf("DuckDuckGo", "Kagi", "Brave Search", "Startpage")
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        PanelHeader("Settings", "Make the browser yours", onBack = { dispatch("close_panel", null) })
        LazyColumn(contentPadding = PaddingValues(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                SectionLabel("SEARCH")
                Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Search engine", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text("Choose your default", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        engines.forEach { engine ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { dispatch("search_engine", engine) }.padding(horizontal = 10.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(engine, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                                if (state.searchEngine == engine) Icon(Icons.Default.Check, "Selected", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
            item {
                SectionLabel("BROWSER")
                SettingTile(Icons.Default.Shield, "Privacy", "Protection and browsing data") { dispatch("privacy", null) }
                SettingTile(Icons.Default.History, "Clear browsing data", "Remove local history and site data") { dispatch("clear_data", null) }
                SettingTile(Icons.Default.Update, "Check for updates", state.updateStatus.ifBlank { "See what's new" }) { dispatch("check_updates", null) }
            }
            item {
                SectionLabel("ABOUT")
                SettingTile(Icons.Default.Info, "About Osmium", "Version ${state.version}") { dispatch("about", null) }
            }
        }
    }
}

@Composable
private fun AboutPanel(state: OsmiumUiState, dispatch: (String, String?) -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        PanelHeader("About Osmium", "A calmer way to browse", onBack = { dispatch("close_panel", null) })
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(32.dp)) {
                    Column(Modifier.fillMaxWidth().padding(25.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        OsmiumBrandMark(74.dp)
                        Spacer(Modifier.height(16.dp))
                        Text("Osmium", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text("Version ${state.version}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item { SettingTile(Icons.Default.Update, "Updates", state.updateStatus.ifBlank { "Check for the latest release" }) { dispatch("check_updates", null) } }
            item { SettingTile(Icons.AutoMirrored.Filled.Article, "Release notes", "See the latest changes") { dispatch("open_release", null) } }
            item { SettingTile(Icons.Default.Code, "Project repository", "Source and community") { dispatch("open_repository", null) } }
            item { SettingTile(Icons.Default.Shield, "Privacy", "Learn how your data stays yours") { dispatch("privacy", null) } }
        }
    }
}

@Composable
private fun PrivacyPanel(dispatch: (String, String?) -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        PanelHeader("Privacy", "Your browsing stays yours", onBack = { dispatch("close_panel", null) })
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(28.dp)) {
                    Column(Modifier.fillMaxWidth().padding(22.dp)) {
                        Icon(Icons.Default.Shield, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.height(13.dp))
                        Text("Private by design", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        Text("Your history and bookmarks are stored on this device. You choose what to save and when to clear it.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item { SettingTile(Icons.Default.Bookmark, "Bookmarks", "Saved pages stay on your device") { dispatch("bookmarks", null) } }
            item { SettingTile(Icons.Default.DeleteOutline, "Clear browsing data", "Remove history and site data") { dispatch("clear_data", null) } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MenuSheet(
    state: OsmiumUiState,
    dismiss: () -> Unit,
    dispatch: (String, String?) -> Unit,
    confirmClear: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().imePadding().padding(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
        Text("Osmium", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(state.title.ifBlank { state.url.ifBlank { "Ready to explore" } }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MenuAction(Icons.Default.BookmarkBorder, "Bookmark", Modifier.weight(1f)) { dispatch("bookmark", null) }
            MenuAction(Icons.Default.Share, "Share", Modifier.weight(1f)) { dispatch("share", state.url) }
            MenuAction(Icons.Default.Refresh, "Reload", Modifier.weight(1f)) { dispatch("reload", null) }
        }
        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
        Spacer(Modifier.height(6.dp))
        MenuRow(Icons.Default.Tab, "Tabs", state.tabs.size.toString()) { dispatch("tabs", null) }
        MenuRow(Icons.Default.Bookmark, "Bookmarks") { dispatch("bookmarks", null) }
        MenuRow(Icons.Default.History, "History") { dispatch("history", null) }
        MenuRow(Icons.Default.Settings, "Settings") { dispatch("settings", null) }
        MenuRow(Icons.Default.Shield, "Privacy") { dispatch("privacy", null) }
        MenuRow(Icons.Default.DeleteOutline, "Clear browsing data") { confirmClear() }
        MenuRow(Icons.Default.Info, "About Osmium") { dispatch("about", null) }
        Spacer(Modifier.height(4.dp))
        TextButton(onClick = dismiss, modifier = Modifier.align(Alignment.End)) { Text("Done") }
    }
}

@Composable
private fun MenuAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    FilledTonalButton(onClick = onClick, modifier = modifier.height(62.dp), shape = RoundedCornerShape(20.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Icon(icon, null, modifier = Modifier.size(19.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

@Composable
private fun MenuRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, value: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(15.dp))
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        if (value != null) Text(value, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PanelHeader(title: String, subtitle: String, onBack: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing()
    }
}

@Composable
private fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = CircleShape, modifier = Modifier.size(76.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp)) }
            }
            Spacer(Modifier.height(17.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(5.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SectionLabel(label: String) {
    Text(label, Modifier.padding(start = 6.dp, top = 8.dp, bottom = 6.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, letterSpacing = 1.4.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun SettingTile(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, detail: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).clickable(onClick = onClick).background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.size(42.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) }
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(17.dp))
    }
}

