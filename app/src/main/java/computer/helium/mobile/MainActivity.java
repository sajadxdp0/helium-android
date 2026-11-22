package computer.helium.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.SafeBrowsingResponse;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;

public final class MainActivity extends Activity {
    private static final String PREFS = "helium_browser";
    private static final String PREF_HISTORY = "history";
    private static final String PREF_BOOKMARKS = "bookmarks";
    private static final String PREF_TABS = "tabs";
    private static final String PREF_ACTIVE_TAB = "active_tab";
    private static final String PREF_BLOCK_THIRD_PARTY = "block_third_party_cookies";
    private static final String PREF_SEARCH_ENGINE = "search_engine";

    private static final int COLOR_BACKGROUND = Color.rgb(11, 16, 32);
    private static final int COLOR_SURFACE = Color.rgb(21, 30, 48);
    private static final int COLOR_SURFACE_RAISED = Color.rgb(28, 39, 61);
    private static final int COLOR_ACCENT = Color.rgb(120, 136, 255);
    private static final int COLOR_TEXT = Color.rgb(244, 246, 255);
    private static final int COLOR_MUTED = Color.rgb(157, 168, 192);
    private static final int COLOR_LINE = Color.rgb(47, 59, 83);

    private final ArrayList<BrowserTab> tabs = new ArrayList<>();
    private SharedPreferences preferences;
    private LinearLayout root;
    private LinearLayout addressContainer;
    private EditText addressField;
    private ImageButton privacyButton;
    private ImageButton reloadButton;
    private ImageButton tabsButton;
    private TextView tabsCount;
    private ProgressBar pageProgress;
    private FrameLayout pageContainer;
    private int activeTab = 0;
    private boolean isEditingAddress = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        configureSystemBars();
        restoreSession();
        buildBrowserShell();
        displayActiveTab();
    }

    private void configureSystemBars() {
        Window window = getWindow();
        window.setStatusBarColor(COLOR_BACKGROUND);
        window.setNavigationBarColor(COLOR_BACKGROUND);
        window.getDecorView().setSystemUiVisibility(0);
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false);
        } else {
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }

    private void restoreSession() {
        String serialized = preferences.getString(PREF_TABS, "");
        try {
            JSONArray savedTabs = new JSONArray(serialized);
            for (int i = 0; i < savedTabs.length(); i++) {
                String url = savedTabs.optString(i, "");
                BrowserTab tab = new BrowserTab();
                tab.url = url;
                tabs.add(tab);
            }
        } catch (JSONException ignored) {
            // A malformed saved session is treated like an empty one.
        }
        if (tabs.isEmpty()) tabs.add(new BrowserTab());
        activeTab = Math.max(0, Math.min(preferences.getInt(PREF_ACTIVE_TAB, 0), tabs.size() - 1));
    }

    private void buildBrowserShell() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(COLOR_BACKGROUND);
        root.setFocusableInTouchMode(true);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            int top = insets.getSystemWindowInsetTop();
            int bottom = insets.getSystemWindowInsetBottom();
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(
                        android.view.WindowInsets.Type.systemBars()
                                | android.view.WindowInsets.Type.displayCutout());
                top = bars.top;
                bottom = bars.bottom;
            }
            view.setPadding(0, top, 0, bottom);
            return insets;
        });

        root.addView(buildBrandBar(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(55)));
        root.addView(buildAddressBar(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(55)));
        pageProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        pageProgress.setMax(100);
        pageProgress.setProgressTintList(android.content.res.ColorStateList.valueOf(COLOR_ACCENT));
        pageProgress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(COLOR_BACKGROUND));
        pageProgress.setVisibility(View.GONE);
        root.addView(pageProgress, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(2)));

        pageContainer = new FrameLayout(this);
        pageContainer.setBackgroundColor(COLOR_BACKGROUND);
        root.addView(pageContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        root.addView(buildBottomBar(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(64)));
        setContentView(root);
    }

    private View buildBrandBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(18), dp(4), dp(14), dp(2));

        ImageView mark = new ImageView(this);
        mark.setImageResource(R.drawable.helium_mark);
        mark.setContentDescription(getString(R.string.app_name));
        bar.addView(mark, new LinearLayout.LayoutParams(dp(32), dp(32)));

        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.VERTICAL);
        brand.setPadding(dp(10), 0, 0, 0);
        TextView name = text("helium", 19, COLOR_TEXT, true);
        TextView tagline = text(getString(R.string.browser_tagline), 10, COLOR_MUTED, false);
        brand.addView(name);
        brand.addView(tagline);
        bar.addView(brand, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        FrameLayout tabsControl = new FrameLayout(this);
        tabsControl.setContentDescription(getString(R.string.tabs));
        tabsControl.setBackground(round(COLOR_SURFACE, dp(16)));
        tabsButton = iconButton(R.drawable.ic_tabs, getString(R.string.tabs));
        tabsButton.setOnClickListener(view -> showTabs());
        tabsControl.addView(tabsButton, new FrameLayout.LayoutParams(dp(42), dp(38), Gravity.CENTER));
        tabsCount = text("1", 10, COLOR_TEXT, true);
        tabsCount.setGravity(Gravity.CENTER);
        tabsCount.setBackground(round(COLOR_ACCENT, dp(8)));
        FrameLayout.LayoutParams badge = new FrameLayout.LayoutParams(dp(17), dp(17), Gravity.TOP | Gravity.END);
        badge.setMargins(0, dp(2), dp(2), 0);
        tabsControl.addView(tabsCount, badge);
        tabsControl.setOnClickListener(view -> showTabs());
        bar.addView(tabsControl, new LinearLayout.LayoutParams(dp(42), dp(38)));
        return bar;
    }

    private View buildAddressBar() {
        LinearLayout outer = new LinearLayout(this);
        outer.setPadding(dp(16), dp(3), dp(16), dp(7));
        addressContainer = new LinearLayout(this);
        addressContainer.setGravity(Gravity.CENTER_VERTICAL);
        addressContainer.setPadding(dp(12), 0, dp(5), 0);
        addressContainer.setBackground(round(COLOR_SURFACE, dp(15)));

        privacyButton = iconButton(R.drawable.ic_shield, getString(R.string.privacy_controls));
        privacyButton.setColorFilter(COLOR_ACCENT);
        privacyButton.setOnClickListener(view -> showPrivacyControls());
        addressContainer.addView(privacyButton, new LinearLayout.LayoutParams(dp(34), dp(40)));

        addressField = new EditText(this);
        addressField.setSingleLine(true);
        addressField.setTextSize(14);
        addressField.setTextColor(COLOR_TEXT);
        addressField.setHintTextColor(COLOR_MUTED);
        addressField.setHint(getString(R.string.address_hint));
        addressField.setSelectAllOnFocus(true);
        addressField.setImeOptions(EditorInfo.IME_ACTION_GO);
        addressField.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        addressField.setBackgroundColor(Color.TRANSPARENT);
        addressField.setPadding(dp(7), 0, dp(4), 0);
        addressField.setOnFocusChangeListener((view, hasFocus) -> isEditingAddress = hasFocus);
        addressField.setOnEditorActionListener((view, actionId, event) -> {
            boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_UP;
            if (actionId == EditorInfo.IME_ACTION_GO || enter) {
                submitAddress();
                return true;
            }
            return false;
        });
        addressContainer.addView(addressField, new LinearLayout.LayoutParams(
                0, dp(42), 1));

        reloadButton = iconButton(R.drawable.ic_refresh, getString(R.string.reload));
        reloadButton.setOnClickListener(view -> reloadOrStop());
        addressContainer.addView(reloadButton, new LinearLayout.LayoutParams(dp(38), dp(40)));
        outer.addView(addressContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42)));
        return outer;
    }

    private View buildBottomBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER);
        bar.setPadding(dp(7), dp(5), dp(7), dp(4));
        bar.setBackgroundColor(COLOR_BACKGROUND);
        bar.addView(navItem(R.drawable.ic_back, getString(R.string.back), this::goBack), weightedButton());
        bar.addView(navItem(R.drawable.ic_forward, getString(R.string.forward), this::goForward), weightedButton());
        bar.addView(navItem(R.drawable.ic_home, getString(R.string.home), this::goHome), weightedButton());
        bar.addView(navItem(R.drawable.ic_bookmark, getString(R.string.bookmarks), this::showBookmarks), weightedButton());
        bar.addView(navItem(R.drawable.ic_more, getString(R.string.menu), this::showMenu), weightedButton());
        return bar;
    }

    private LinearLayout navItem(int icon, String label, Runnable action) {
        LinearLayout item = new LinearLayout(this);
        item.setGravity(Gravity.CENTER);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setBackground(selectableBackground());
        item.setContentDescription(label);
        ImageView image = new ImageView(this);
        image.setImageResource(icon);
        image.setColorFilter(COLOR_MUTED);
        item.addView(image, new LinearLayout.LayoutParams(dp(21), dp(21)));
        TextView caption = text(label, 10, COLOR_MUTED, false);
        caption.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams captionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        captionParams.topMargin = dp(2);
        item.addView(caption, captionParams);
        item.setOnClickListener(view -> action.run());
        return item;
    }

    private LinearLayout.LayoutParams weightedButton() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
    }

    private void displayActiveTab() {
        if (pageContainer == null || tabs.isEmpty()) return;
        activeTab = Math.min(activeTab, tabs.size() - 1);
        BrowserTab tab = currentTab();
        pageContainer.removeAllViews();
        if (tab.url == null || tab.url.isEmpty()) {
            pageContainer.addView(buildHomePage(), new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            pageProgress.setVisibility(View.GONE);
        } else {
            WebView webView = ensureWebView(tab);
            if (webView.getUrl() == null || !tab.url.equals(webView.getUrl())) {
                webView.loadUrl(tab.url);
            }
            if (webView.getParent() instanceof ViewGroup) {
                ((ViewGroup) webView.getParent()).removeView(webView);
            }
            pageContainer.addView(webView, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            pageProgress.setVisibility(View.VISIBLE);
        }
        updateChrome();
    }

    private View buildHomePage() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(23), dp(28), dp(23), dp(24));

        ImageView mark = new ImageView(this);
        mark.setImageResource(R.drawable.helium_mark);
        mark.setContentDescription(getString(R.string.app_name));
        content.addView(mark, new LinearLayout.LayoutParams(dp(54), dp(54)));

        TextView headline = text(getString(R.string.home_title), 30, COLOR_TEXT, true);
        headline.setLetterSpacing(-0.035f);
        LinearLayout.LayoutParams headlineParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        headlineParams.topMargin = dp(20);
        content.addView(headline, headlineParams);

        TextView body = text(getString(R.string.home_body), 15, COLOR_MUTED, false);
        body.setLineSpacing(dp(3), 1.03f);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bodyParams.topMargin = dp(9);
        content.addView(body, bodyParams);

        LinearLayout quickHeader = new LinearLayout(this);
        quickHeader.setGravity(Gravity.CENTER_VERTICAL);
        quickHeaderParams(quickHeader);
        TextView quickTitle = text(getString(R.string.home_section), 10, COLOR_MUTED, true);
        quickTitle.setLetterSpacing(0.13f);
        quickHeader.addView(quickTitle, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView searchHint = text(getString(R.string.search_action), 12, COLOR_ACCENT, true);
        quickHeader.addView(searchHint);
        quickHeader.setOnClickListener(view -> focusAddress());
        content.addView(quickHeader);

        LinearLayout favorites = new LinearLayout(this);
        favorites.setOrientation(LinearLayout.HORIZONTAL);
        String[] names = {
                getString(R.string.favorite_kagi),
                getString(R.string.favorite_github),
                getString(R.string.favorite_wikipedia)
        };
        String[] urls = {
                "https://kagi.com/",
                "https://github.com/",
                "https://www.wikipedia.org/"
        };
        for (int i = 0; i < names.length; i++) {
            final String url = urls[i];
            LinearLayout card = favoriteCard(names[i], url);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(84), 1);
            if (i > 0) params.leftMargin = dp(9);
            favorites.addView(card, params);
        }
        content.addView(favorites);

        LinearLayout privacyCard = new LinearLayout(this);
        privacyCard.setOrientation(LinearLayout.VERTICAL);
        privacyCard.setPadding(dp(16), dp(15), dp(16), dp(15));
        privacyCard.setBackground(round(COLOR_SURFACE, dp(14)));
        LinearLayout.LayoutParams privacyParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        privacyParams.topMargin = dp(24);
        content.addView(privacyCard, privacyParams);

        LinearLayout privacyTitleRow = new LinearLayout(this);
        privacyTitleRow.setGravity(Gravity.CENTER_VERTICAL);
        ImageView shield = new ImageView(this);
        shield.setImageResource(R.drawable.ic_shield);
        shield.setColorFilter(COLOR_ACCENT);
        privacyTitleRow.addView(shield, new LinearLayout.LayoutParams(dp(20), dp(20)));
        TextView privacyTitle = text(getString(R.string.home_privacy_title), 14, COLOR_TEXT, true);
        LinearLayout.LayoutParams privacyTitleParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        privacyTitleParams.leftMargin = dp(10);
        privacyTitleRow.addView(privacyTitle, privacyTitleParams);
        privacyCard.addView(privacyTitleRow);

        TextView privacyBody = text(getString(R.string.home_privacy_body), 12, COLOR_MUTED, false);
        privacyBody.setLineSpacing(dp(2), 1.02f);
        LinearLayout.LayoutParams privacyBodyParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        privacyBodyParams.topMargin = dp(9);
        privacyCard.addView(privacyBody, privacyBodyParams);
        TextView detail = text(getString(R.string.home_privacy_detail), 12, COLOR_ACCENT, true);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        detailParams.topMargin = dp(12);
        privacyCard.addView(detail, detailParams);
        privacyCard.setOnClickListener(view -> showPrivacyControls());

        content.addView(spacer(dp(14)));
        TextView footer = text(getString(R.string.private_search_note), 11, COLOR_MUTED, false);
        content.addView(footer);
        scroll.addView(content);
        return scroll;
    }

    private LinearLayout favoriteCard(String title, String url) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(12), dp(10), dp(8), dp(10));
        card.setBackground(round(COLOR_SURFACE, dp(12)));
        TextView initial = text(title.substring(0, 1), 17, COLOR_ACCENT, true);
        card.addView(initial);
        TextView name = text(title, 12, COLOR_TEXT, true);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nameParams.topMargin = dp(7);
        card.addView(name, nameParams);
        TextView host = text(Uri.parse(url).getHost(), 9, COLOR_MUTED, false);
        card.addView(host);
        card.setContentDescription(title + ", " + url);
        card.setOnClickListener(view -> navigate(url));
        return card;
    }

    private WebView ensureWebView(BrowserTab tab) {
        if (tab.webView != null) return tab.webView;
        WebView webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSafeBrowsingEnabled(true);
        settings.setSaveFormData(false);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(webView,
                !preferences.getBoolean(PREF_BLOCK_THIRD_PARTY, true));

        webView.setBackgroundColor(Color.WHITE);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String scheme = request.getUrl().getScheme();
                if ("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme)) {
                    return false;
                }
                if ("mailto".equalsIgnoreCase(scheme) || "tel".equalsIgnoreCase(scheme)) {
                    openExternal(request.getUrl());
                }
                return true;
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (!request.isForMainFrame() && TrackerShield.shouldBlock(request.getUrl().toString())) {
                    tab.blockedRequests++;
                    runOnUiThread(() -> {
                        if (currentTab() == tab) updateChrome();
                    });
                    return new WebResourceResponse("text/plain", "UTF-8",
                            new ByteArrayInputStream(new byte[0]));
                }
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                tab.url = url;
                if (currentTab() == tab) updateChrome();
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                tab.url = url;
                tab.title = view.getTitle() == null ? "" : view.getTitle();
                if (!url.startsWith("https://helium.local")) addHistory(tab.title, url);
                if (currentTab() == tab) updateChrome();
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this,
                            getString(R.string.page_load_error), Toast.LENGTH_SHORT).show());
                }
            }

            @Override
            public void onSafeBrowsingHit(WebView view, WebResourceRequest request,
                                          int threatType, SafeBrowsingResponse callback) {
                callback.backToSafety(true);
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int progress) {
                if (currentTab() == tab) {
                    pageProgress.setProgress(progress);
                    pageProgress.setVisibility(progress >= 100 ? View.GONE : View.VISIBLE);
                    reloadButton.setImageResource(progress >= 100 ? R.drawable.ic_refresh : R.drawable.ic_close);
                    reloadButton.setContentDescription(getString(progress >= 100
                            ? R.string.reload : R.string.stop_loading));
                }
            }

            @Override
            public void onGeolocationPermissionsShowPrompt(String origin,
                                                            GeolocationPermissions.Callback callback) {
                callback.invoke(origin, false, false);
            }

            @Override
            public void onPermissionRequest(android.webkit.PermissionRequest request) {
                request.deny();
            }
        });
        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) ->
                startDownload(url, userAgent, contentDisposition, mimeType));
        tab.webView = webView;
        return webView;
    }

    private void openExternal(Uri uri) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (android.content.ActivityNotFoundException ignored) {
            Toast.makeText(this, getString(R.string.invalid_address), Toast.LENGTH_SHORT).show();
        }
    }

    private void startDownload(String url, String userAgent, String contentDisposition, String mimeType) {
        try {
            String filename = android.webkit.URLUtil.guessFileName(url, contentDisposition, mimeType)
                    .replaceAll("[^A-Za-z0-9._-]", "_");
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setMimeType(mimeType);
            request.addRequestHeader("User-Agent", userAgent);
            request.setTitle(filename);
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename);
            DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            if (manager == null) throw new IllegalStateException("Download service unavailable");
            manager.enqueue(request);
            Toast.makeText(this, getString(R.string.download_started), Toast.LENGTH_SHORT).show();
        } catch (RuntimeException exception) {
            Toast.makeText(this, getString(R.string.download_failed), Toast.LENGTH_SHORT).show();
        }
    }

    private void navigate(String url) {
        if (url == null || url.trim().isEmpty()) {
            Toast.makeText(this, getString(R.string.invalid_address), Toast.LENGTH_SHORT).show();
            return;
        }
        BrowserTab tab = currentTab();
        tab.url = url;
        tab.blockedRequests = 0;
        WebView webView = ensureWebView(tab);
        if (webView.getParent() instanceof ViewGroup) {
            ((ViewGroup) webView.getParent()).removeView(webView);
        }
        pageContainer.removeAllViews();
        pageContainer.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        pageProgress.setProgress(0);
        pageProgress.setVisibility(View.VISIBLE);
        webView.loadUrl(url);
        hideKeyboard();
        updateChrome();
    }

    private void submitAddress() {
        String input = addressField.getText().toString().trim();
        if (input.isEmpty()) {
            Toast.makeText(this, getString(R.string.invalid_address), Toast.LENGTH_SHORT).show();
            return;
        }
        String target = resolveAddress(input);
        addressField.clearFocus();
        isEditingAddress = false;
        navigate(target);
    }

    private String resolveAddress(String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        if (lower.startsWith("https://") || lower.startsWith("http://")) return input;
        if (input.matches("(?i)^(localhost|([a-z0-9-]+\\.)+[a-z]{2,})(:\\d+)?(/.*)?$")) {
            return "https://" + input;
        }
        String[] templates = getResources().getStringArray(R.array.search_engine_templates);
        int engine = Math.max(0, Math.min(preferences.getInt(PREF_SEARCH_ENGINE, 0), templates.length - 1));
        String query = URLEncoder.encode(input, StandardCharsets.UTF_8);
        return templates[engine].replace("%s", query);
    }

    private void hideKeyboard() {
        View focused = getCurrentFocus();
        if (focused != null) {
            ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE))
                    .hideSoftInputFromWindow(focused.getWindowToken(), 0);
        }
    }

    private void focusAddress() {
        addressField.requestFocus();
        addressField.selectAll();
        ((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE))
                .showSoftInput(addressField, InputMethodManager.SHOW_IMPLICIT);
    }

    private void reloadOrStop() {
        BrowserTab tab = currentTab();
        if (tab.webView == null) {
            focusAddress();
            return;
        }
        if (tab.webView.getProgress() < 100) tab.webView.stopLoading();
        else tab.webView.reload();
    }

    private void goBack() {
        BrowserTab tab = currentTab();
        if (tab.webView != null && tab.webView.canGoBack()) tab.webView.goBack();
        else goHome();
    }

    private void goForward() {
        BrowserTab tab = currentTab();
        if (tab.webView != null && tab.webView.canGoForward()) tab.webView.goForward();
    }

    private void goHome() {
        BrowserTab tab = currentTab();
        if (tab.webView != null) {
            tab.webView.stopLoading();
            tab.webView.onPause();
        }
        tab.url = "";
        tab.title = "";
        tab.blockedRequests = 0;
        displayActiveTab();
    }

    private void createTab() {
        tabs.add(new BrowserTab());
        activeTab = tabs.size() - 1;
        displayActiveTab();
        saveSession();
    }

    private void selectTab(int index) {
        if (index < 0 || index >= tabs.size()) return;
        BrowserTab old = currentTab();
        if (old.webView != null) old.webView.onPause();
        activeTab = index;
        BrowserTab selected = currentTab();
        if (selected.webView != null) selected.webView.onResume();
        displayActiveTab();
        saveSession();
    }

    private void closeActiveTab() {
        BrowserTab removed = currentTab();
        if (removed.webView != null) {
            if (removed.webView.getParent() instanceof ViewGroup) {
                ((ViewGroup) removed.webView.getParent()).removeView(removed.webView);
            }
            removed.webView.stopLoading();
            removed.webView.destroy();
        }
        tabs.remove(activeTab);
        if (tabs.isEmpty()) tabs.add(new BrowserTab());
        activeTab = Math.max(0, Math.min(activeTab, tabs.size() - 1));
        displayActiveTab();
        saveSession();
    }

    private void showTabs() {
        ArrayList<String> labels = new ArrayList<>();
        labels.add(getString(R.string.new_tab_choice));
        for (BrowserTab tab : tabs) {
            String title = tab.title == null || tab.title.isEmpty()
                    ? getString(R.string.home_label) : tab.title;
            String address = tab.url == null || tab.url.isEmpty() ? "" : "\n" + tab.url;
            labels.add(title + address);
        }
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.choose_tab))
                .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                    if (which == 0) createTab();
                    else selectTab(which - 1);
                })
                .setNeutralButton(getString(R.string.close_tab), (dialog, which) -> closeActiveTab())
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    private void showMenu() {
        String[] menu = {
                getString(R.string.add_bookmark),
                getString(R.string.bookmarks),
                getString(R.string.history),
                getString(R.string.settings),
                getString(R.string.clear_data),
                getString(R.string.share_page),
                getString(R.string.about)
        };
        new AlertDialog.Builder(this)
                .setItems(menu, (dialog, which) -> {
                    switch (which) {
                        case 0: addBookmark(); break;
                        case 1: showBookmarks(); break;
                        case 2: showHistory(); break;
                        case 3: showSettings(); break;
                        case 4: confirmClearData(); break;
                        case 5: sharePage(); break;
                        case 6: showAbout(); break;
                    }
                })
                .show();
    }

    private void showSettings() {
        String[] options = {
                getString(R.string.privacy_controls),
                getString(R.string.search_engine),
                getString(R.string.bookmarks),
                getString(R.string.history),
                getString(R.string.clear_data),
                getString(R.string.about)
        };
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.settings))
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0: showPrivacyControls(); break;
                        case 1: chooseSearchEngine(); break;
                        case 2: showBookmarks(); break;
                        case 3: showHistory(); break;
                        case 4: confirmClearData(); break;
                        case 5: showAbout(); break;
                    }
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    private void showPrivacyControls() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22), dp(10), dp(22), dp(8));

        TextView count = text(getString(R.string.blocked_request_count, currentTab().blockedRequests),
                15, COLOR_TEXT, true);
        content.addView(count);

        TextView filterDetails = text(getString(R.string.tracker_filter_detail), 13, COLOR_MUTED, false);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        detailParams.topMargin = dp(7);
        content.addView(filterDetails, detailParams);

        Switch cookiesToggle = new Switch(this);
        cookiesToggle.setText(getString(R.string.third_party_cookies));
        cookiesToggle.setTextColor(COLOR_TEXT);
        cookiesToggle.setTextSize(14);
        cookiesToggle.setChecked(preferences.getBoolean(PREF_BLOCK_THIRD_PARTY, true));
        cookiesToggle.setPadding(0, dp(16), 0, dp(2));
        content.addView(cookiesToggle);
        TextView cookieDetail = text(getString(R.string.third_party_cookies_detail), 12, COLOR_MUTED, false);
        content.addView(cookieDetail);
        cookiesToggle.setOnCheckedChangeListener((button, checked) -> {
            preferences.edit().putBoolean(PREF_BLOCK_THIRD_PARTY, checked).apply();
            CookieManager cookies = CookieManager.getInstance();
            for (BrowserTab tab : tabs) {
                if (tab.webView != null) cookies.setAcceptThirdPartyCookies(tab.webView, !checked);
            }
        });

        TextView safeBrowsing = text(getString(R.string.safe_browsing), 14, COLOR_TEXT, true);
        LinearLayout.LayoutParams safeTitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        safeTitleParams.topMargin = dp(18);
        content.addView(safeBrowsing, safeTitleParams);
        TextView safeDetail = text(getString(R.string.safe_browsing_detail), 12, COLOR_MUTED, false);
        content.addView(safeDetail);

        TextView scopeNote = text(getString(R.string.shield_scope_note), 12, COLOR_MUTED, false);
        scopeNote.setLineSpacing(dp(2), 1.02f);
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        noteParams.topMargin = dp(17);
        content.addView(scopeNote, noteParams);
        scroll.addView(content);

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.privacy_controls))
                .setView(scroll)
                .setPositiveButton(getString(android.R.string.ok), null)
                .show();
    }

    private void chooseSearchEngine() {
        String[] names = getResources().getStringArray(R.array.search_engine_names);
        int selected = Math.max(0, Math.min(preferences.getInt(PREF_SEARCH_ENGINE, 0), names.length - 1));
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.choose_search_engine))
                .setSingleChoiceItems(names, selected, (dialog, which) -> {
                    preferences.edit().putInt(PREF_SEARCH_ENGINE, which).apply();
                    dialog.dismiss();
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    private void addBookmark() {
        BrowserTab tab = currentTab();
        if (tab.url == null || tab.url.isEmpty()) {
            Toast.makeText(this, getString(R.string.invalid_address), Toast.LENGTH_SHORT).show();
            return;
        }
        ArrayList<JSONObject> bookmarks = readEntries(PREF_BOOKMARKS);
        for (JSONObject item : bookmarks) {
            if (tab.url.equals(item.optString("url"))) {
                Toast.makeText(this, getString(R.string.bookmark_added), Toast.LENGTH_SHORT).show();
                return;
            }
        }
        bookmarks.add(0, entry(tab.title, tab.url));
        writeEntries(PREF_BOOKMARKS, bookmarks);
        Toast.makeText(this, getString(R.string.bookmark_added), Toast.LENGTH_SHORT).show();
    }

    private void showBookmarks() {
        showEntryList(PREF_BOOKMARKS, getString(R.string.bookmarks), getString(R.string.no_bookmarks));
    }

    private void showHistory() {
        showEntryList(PREF_HISTORY, getString(R.string.history), getString(R.string.no_history));
    }

    private void showEntryList(String key, String title, String emptyMessage) {
        ArrayList<JSONObject> entries = readEntries(key);
        if (entries.isEmpty()) {
            new AlertDialog.Builder(this).setTitle(title).setMessage(emptyMessage)
                    .setPositiveButton(getString(android.R.string.ok), null).show();
            return;
        }
        String[] labels = new String[entries.size()];
        for (int i = 0; i < entries.size(); i++) {
            JSONObject entry = entries.get(i);
            String label = entry.optString("title", "");
            if (label.isEmpty()) label = entry.optString("url", "");
            labels[i] = label + "\n" + entry.optString("url", "");
        }
        new AlertDialog.Builder(this).setTitle(title).setItems(labels, (dialog, which) ->
                navigate(entries.get(which).optString("url", "")))
                .setNegativeButton(getString(android.R.string.cancel), null).show();
    }

    private void addHistory(String title, String url) {
        ArrayList<JSONObject> entries = readEntries(PREF_HISTORY);
        if (!entries.isEmpty() && url.equals(entries.get(0).optString("url"))) return;
        entries.add(0, entry(title, url));
        while (entries.size() > 60) entries.remove(entries.size() - 1);
        writeEntries(PREF_HISTORY, entries);
    }

    private JSONObject entry(String title, String url) {
        JSONObject value = new JSONObject();
        try {
            value.put("title", title == null ? "" : title);
            value.put("url", url == null ? "" : url);
        } catch (JSONException ignored) {
            // JSONObject only rejects invalid JSON values; both fields are strings.
        }
        return value;
    }

    private ArrayList<JSONObject> readEntries(String key) {
        ArrayList<JSONObject> entries = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(preferences.getString(key, "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item != null) entries.add(item);
            }
        } catch (JSONException ignored) {
            // Ignore a damaged local list and let the next write replace it.
        }
        return entries;
    }

    private void writeEntries(String key, ArrayList<JSONObject> entries) {
        JSONArray array = new JSONArray();
        for (JSONObject entry : entries) array.put(entry);
        preferences.edit().putString(key, array.toString()).apply();
    }

    private void confirmClearData() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.clear_data_title))
                .setMessage(getString(R.string.clear_data_message))
                .setNegativeButton(getString(R.string.cancel), null)
                .setPositiveButton(getString(R.string.clear), (dialog, which) -> clearBrowsingData())
                .show();
    }

    private void clearBrowsingData() {
        preferences.edit().remove(PREF_HISTORY).apply();
        for (BrowserTab tab : tabs) {
            tab.blockedRequests = 0;
            if (tab.webView != null) {
                tab.webView.clearCache(true);
                tab.webView.clearHistory();
                tab.webView.clearFormData();
            }
        }
        CookieManager cookies = CookieManager.getInstance();
        cookies.removeAllCookies(value -> cookies.flush());
        WebStorage.getInstance().deleteAllData();
        updateChrome();
        Toast.makeText(this, getString(R.string.data_cleared), Toast.LENGTH_SHORT).show();
    }

    private void sharePage() {
        String url = currentTab().url;
        if (url == null || url.isEmpty()) return;
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, url);
        startActivity(Intent.createChooser(intent, getString(R.string.share_page)));
    }

    private void showAbout() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.about_title))
                .setMessage(getString(R.string.about_message))
                .setPositiveButton(getString(android.R.string.ok), null)
                .show();
    }

    private void updateChrome() {
        if (tabsButton == null || currentTab() == null) return;
        tabsCount.setText(String.valueOf(tabs.size()));
        BrowserTab tab = currentTab();
        if (!isEditingAddress) {
            String address = tab.url == null ? "" : tab.url;
            addressField.setText(address);
            addressField.setSelection(address.length());
        }
        boolean secure = tab.url != null && tab.url.startsWith("https://");
        privacyButton.setColorFilter(tab.url == null || tab.url.isEmpty()
                ? COLOR_ACCENT : (secure ? COLOR_ACCENT : COLOR_MUTED));
        privacyButton.setContentDescription(getString(tab.url == null || tab.url.isEmpty()
                ? R.string.privacy_controls : (secure ? R.string.secure_site : R.string.insecure_site)));
        if (tab.webView == null) {
            reloadButton.setImageResource(R.drawable.ic_refresh);
            reloadButton.setContentDescription(getString(R.string.reload));
            pageProgress.setVisibility(View.GONE);
        }
    }

    private void saveSession() {
        JSONArray saved = new JSONArray();
        for (BrowserTab tab : tabs) saved.put(tab.url == null ? "" : tab.url);
        preferences.edit().putString(PREF_TABS, saved.toString())
                .putInt(PREF_ACTIVE_TAB, activeTab).apply();
    }

    private BrowserTab currentTab() {
        if (tabs.isEmpty()) return null;
        activeTab = Math.max(0, Math.min(activeTab, tabs.size() - 1));
        return tabs.get(activeTab);
    }

    private void quickHeaderParams(LinearLayout header) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        params.topMargin = dp(30);
        header.setLayoutParams(params);
    }

    @Override
    protected void onPause() {
        BrowserTab tab = currentTab();
        if (tab != null && tab.webView != null) tab.webView.onPause();
        saveSession();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        BrowserTab tab = currentTab();
        if (tab != null && tab.webView != null) tab.webView.onResume();
    }

    @Override
    protected void onDestroy() {
        for (BrowserTab tab : tabs) {
            if (tab.webView != null) {
                if (tab.webView.getParent() instanceof ViewGroup) {
                    ((ViewGroup) tab.webView.getParent()).removeView(tab.webView);
                }
                tab.webView.stopLoading();
                tab.webView.destroy();
                tab.webView = null;
            }
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        BrowserTab tab = currentTab();
        if (tab.webView != null && tab.webView.canGoBack()) {
            tab.webView.goBack();
        } else if (tab.url != null && !tab.url.isEmpty()) {
            goHome();
        } else if (tabs.size() > 1) {
            closeActiveTab();
        } else {
            super.onBackPressed();
        }
    }

    private ImageButton iconButton(int resource, String description) {
        ImageButton button = new ImageButton(this);
        button.setImageResource(resource);
        button.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        button.setColorFilter(COLOR_MUTED);
        button.setBackground(selectableBackground());
        button.setContentDescription(description);
        return button;
    }

    private TextView text(String value, float sizeSp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setIncludeFontPadding(false);
        return view;
    }

    private View spacer(int height) {
        View view = new View(this);
        view.setLayoutParams(new LinearLayout.LayoutParams(1, height));
        return view;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private android.graphics.drawable.Drawable selectableBackground() {
        android.util.TypedValue value = new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, value, true);
        return getDrawable(value.resourceId);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
