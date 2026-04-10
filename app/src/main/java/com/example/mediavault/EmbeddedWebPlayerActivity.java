package com.example.mediavault;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.example.mediavault.widget.ToastUtils;
import java.util.Locale;

public class EmbeddedWebPlayerActivity extends AppCompatActivity {
    public static final String EXTRA_URL = "extra_url";
    public static final String EXTRA_TITLE = "extra_title";
    public static final String EXTRA_FALLBACK_URL = "extra_fallback_url";

    private WebView webView;
    private String targetUrl;
    private String fallbackUrl;
    private String requestedTitle;
    private boolean triedFallbackReload = false;
    private boolean triedSearchRecovery = false;
    private boolean redirectedToExternal = false;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_embedded_web_player);

        targetUrl = normalizeProviderUrl(getIntent().getStringExtra(EXTRA_URL));
        fallbackUrl = normalizeProviderUrl(getIntent().getStringExtra(EXTRA_FALLBACK_URL));
        String title = getIntent().getStringExtra(EXTRA_TITLE);
        requestedTitle = sanitizeRequestedTitle(title);

        if (targetUrl == null || targetUrl.trim().isEmpty()) {
            ToastUtils.showCustomToast(this, "No provider URL available");
            finish();
            return;
        }

        TextView titleView = findViewById(R.id.text_web_player_title);
        titleView.setText(title == null || title.trim().isEmpty() ? "Web Player" : title);

        ImageButton backButton = findViewById(R.id.btn_web_player_back);
        backButton.setOnClickListener(v -> finish());

        ImageButton openExternalButton = findViewById(R.id.btn_web_player_external);
        openExternalButton.setOnClickListener(v -> openExternal(getCurrentWebUrl()));

        webView = findViewById(R.id.web_player_view);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        settings.setUserAgentString("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36");
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleProviderIntentUrl(url);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (request == null || request.getUrl() == null) {
                    return false;
                }
                return handleProviderIntentUrl(request.getUrl().toString());
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                handleSuspiciousProviderRedirect(view, url);
            }
        });
        webView.setWebChromeClient(new WebChromeClient());
        webView.loadUrl(targetUrl);
    }

    private boolean handleProviderIntentUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.trim().isEmpty()) {
            return true;
        }
        String url = normalizeProviderUrl(rawUrl);
        String lower = url.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return false;
        }
        if (lower.startsWith("intent://")) {
            try {
                Intent intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
                if (intent != null) {
                    if (intent.getPackage() != null && intent.resolveActivity(getPackageManager()) == null) {
                        String fallbackUrl = intent.getStringExtra("browser_fallback_url");
                        if (fallbackUrl != null && !fallbackUrl.trim().isEmpty()) {
                            openExternal(fallbackUrl);
                            return true;
                        }
                    }
                    startActivity(intent);
                    return true;
                }
            } catch (Exception ignored) {
            }
            String fallback = extractIntentFallback(url);
            if (fallback != null) {
                openExternal(fallback);
            } else {
                ToastUtils.showCustomToast(this, "Provider intent is not supported on this device");
            }
            return true;
        }
        if (lower.startsWith("market://")
                || lower.startsWith("vnd.youtube://")
                || lower.startsWith("tg://")
                || lower.startsWith("mailto:")
                || lower.startsWith("tel:")) {
            openExternal(url);
            return true;
        }
        return true;
    }

    private String extractIntentFallback(String intentUrl) {
        if (intentUrl == null) return null;
        int marker = intentUrl.indexOf("S.browser_fallback_url=");
        if (marker < 0) return null;
        int start = marker + "S.browser_fallback_url=".length();
        int end = intentUrl.indexOf(';', start);
        String encoded = end > start ? intentUrl.substring(start, end) : intentUrl.substring(start);
        if (encoded.isEmpty()) return null;
        try {
            return Uri.decode(encoded);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void openExternal(String url) {
        String normalized = normalizeProviderUrl(url);
        if (normalized == null || normalized.trim().isEmpty()) {
            ToastUtils.showCustomToast(this, "Invalid provider URL");
            return;
        }
        try {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(normalized.trim()));
            startActivity(browserIntent);
        } catch (ActivityNotFoundException e) {
            ToastUtils.showCustomToast(this, "No app found to open provider link");
        } catch (Exception e) {
            ToastUtils.showCustomToast(this, "Failed to open provider link");
        }
    }

    private void handleSuspiciousProviderRedirect(WebView view, String loadedUrl) {
        if (redirectedToExternal) {
            return;
        }
        String current = normalizeProviderUrl(loadedUrl);
        String title = view != null && view.getTitle() != null ? view.getTitle().toLowerCase(Locale.ROOT) : "";
        if (title.contains("just a moment")
                || title.contains("checking your browser")
                || title.contains("attention required")) {
            redirectedToExternal = true;
            ToastUtils.showCustomToast(this, "Provider bot check detected. Opening in browser.");
            openExternal(current);
            return;
        }
        if (!isLikelyHomeRedirect(targetUrl, current)) {
            return;
        }
        if (!triedFallbackReload && fallbackUrl != null && !fallbackUrl.trim().isEmpty() && !fallbackUrl.equalsIgnoreCase(current)) {
            triedFallbackReload = true;
            ToastUtils.showCustomToast(this, "Provider redirected to index. Trying fallback source...");
            webView.loadUrl(fallbackUrl);
            return;
        }
        String searchRecoveryUrl = buildSearchRecoveryUrl(current, requestedTitle);
        if (!triedSearchRecovery && searchRecoveryUrl != null && !searchRecoveryUrl.equalsIgnoreCase(current)) {
            triedSearchRecovery = true;
            ToastUtils.showCustomToast(this, "Provider redirected. Searching requested media...");
            webView.loadUrl(searchRecoveryUrl);
            return;
        }
        redirectedToExternal = true;
        ToastUtils.showCustomToast(this, "Provider redirected to home. Opening in browser.");
        openExternal(current);
    }

    private boolean isLikelyHomeRedirect(String requestedUrl, String loadedUrl) {
        if (requestedUrl == null || loadedUrl == null) {
            return false;
        }
        try {
            Uri requested = Uri.parse(requestedUrl);
            Uri loaded = Uri.parse(loadedUrl);
            String requestedHost = requested.getHost();
            String loadedHost = loaded.getHost();
            if (requestedHost == null || loadedHost == null || !requestedHost.equalsIgnoreCase(loadedHost)) {
                return false;
            }
            String requestedPath = requested.getPath() == null ? "" : requested.getPath().toLowerCase(Locale.ROOT);
            String loadedPath = loaded.getPath() == null ? "" : loaded.getPath().toLowerCase(Locale.ROOT);
            boolean requestedIsDeep = requestedPath.contains("/embed/")
                    || requestedPath.contains("/watch/")
                    || requestedPath.contains("/episode")
                    || requested.getQuery() != null;
            boolean loadedIsHome = loadedPath.isEmpty()
                    || "/".equals(loadedPath)
                    || "/home".equals(loadedPath)
                    || "/index".equals(loadedPath)
                    || "/index.php".equals(loadedPath);
            return requestedIsDeep && loadedIsHome;
        } catch (Exception ignored) {
            return false;
        }
    }

    private String getCurrentWebUrl() {
        if (webView != null && webView.getUrl() != null && !webView.getUrl().trim().isEmpty()) {
            return normalizeProviderUrl(webView.getUrl());
        }
        return targetUrl;
    }

    private String normalizeProviderUrl(String url) {
        if (url == null) {
            return null;
        }
        String normalized = url.trim();
        if (normalized.isEmpty()) {
            return normalized;
        }
        normalized = normalized.replace("https://anikai.to", "https://animekai.to");
        normalized = normalized.replace("http://anikai.to", "https://animekai.to");
        normalized = normalized.replace("http://webnovel.com", "https://webnovel.com");
        normalized = normalized.replace("http://www.webnovel.com", "https://www.webnovel.com");
        return normalized;
    }

    private String sanitizeRequestedTitle(String title) {
        if (title == null) {
            return null;
        }
        String normalized = title.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        int bulletIndex = normalized.indexOf('•');
        if (bulletIndex > 0) {
            normalized = normalized.substring(0, bulletIndex).trim();
        }
        normalized = normalized.replaceAll("(?i)\\s+s\\d+e\\d+\\s*$", "").trim();
        normalized = normalized.replaceAll("(?i)\\s+episode\\s+\\d+(?:\\.\\d+)?\\s*$", "").trim();
        normalized = normalized.replaceAll("(?i)\\s+chapter\\s+\\d+(?:\\.\\d+)?\\s*$", "").trim();
        return normalized;
    }

    private String buildSearchRecoveryUrl(String currentUrl, String title) {
        if (title == null || title.trim().isEmpty()) {
            return null;
        }
        String hostSourceUrl = firstHostUrl(currentUrl, targetUrl, fallbackUrl);
        if (hostSourceUrl == null || hostSourceUrl.trim().isEmpty()) {
            return null;
        }
        try {
            Uri current = Uri.parse(hostSourceUrl);
            String host = current.getHost();
            if (host == null || host.trim().isEmpty()) {
                return null;
            }
            String cleanHost = host.toLowerCase(Locale.ROOT);
            if (cleanHost.startsWith("www.")) {
                cleanHost = cleanHost.substring(4);
            }
            String query = Uri.encode(title.trim());
            if (cleanHost.contains("animekai.to")) return "https://animekai.to/search?keyword=" + query;
            if (cleanHost.contains("aniwatchtv.to")) return "https://aniwatchtv.to/search?keyword=" + query;
            if (cleanHost.contains("animepahe")) return "https://animepahe.pw/anime?q=" + query;
            if (cleanHost.contains("bilibili")) return "https://www.bilibili.tv/en/search-result?q=" + query;
            if (cleanHost.contains("comix.to")) return "https://comix.to/filter?keyword=" + query;
            if (cleanHost.contains("mangafire.to")) return "https://mangafire.to/filter?keyword=" + query;
            if (cleanHost.contains("weebcentral")) return "https://weebcentral.com/search?q=" + query;
            if (cleanHost.contains("nepu.to")) return "https://nepu.to/search?q=" + query;
            if (cleanHost.contains("xprime.su")) return "https://xprime.su/search?q=" + query;
            if (cleanHost.contains("cineby")) return "https://www.cineby.sc/search?q=" + query;
            if (cleanHost.contains("openchapter")) return "https://openchapter.io/?s=" + query;
            if (cleanHost.contains("novelfire")) return "https://novelfire.net/search?keyword=" + query;
            if (cleanHost.contains("wtr-lab")) return "https://wtr-lab.com/en?search=" + query;
            if (cleanHost.contains("webnovel") || cleanHost.contains("qidian")) return "https://www.webnovel.com/search?keywords=" + query;
            return "https://" + cleanHost + "/search?q=" + query;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String firstHostUrl(String first, String second, String third) {
        String[] candidates = {normalizeProviderUrl(first), normalizeProviderUrl(second), normalizeProviderUrl(third)};
        for (String candidate : candidates) {
            if (candidate == null || candidate.trim().isEmpty()) {
                continue;
            }
            try {
                Uri uri = Uri.parse(candidate);
                if (uri.getHost() != null && !uri.getHost().trim().isEmpty()) {
                    return candidate;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.loadUrl("about:blank");
            webView.clearHistory();
            webView.removeAllViews();
            webView.destroy();
        }
        super.onDestroy();
    }
}
