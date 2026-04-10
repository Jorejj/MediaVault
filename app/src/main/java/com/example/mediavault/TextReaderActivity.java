package com.example.mediavault;

import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import android.text.Spanned;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.mediavault.ui.library.MediaItem;
import com.example.mediavault.widget.ToastUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class TextReaderActivity extends AppCompatActivity {
    private static final String TAG = "TextReaderActivity";
    private static final int MIN_READABLE_LENGTH = 500;

    public static final String EXTRA_URL = "extra_url";
    public static final String EXTRA_MEDIA_ID = "extra_media_id";
    public static final String EXTRA_TITLE = "extra_title";

    private MediaItem mediaItem;
    private TextView contentBody;
    private View topBar;
    private View bottomBar;
    private TextView pageIndicator;
    private ImageButton prevPageButton;
    private ImageButton nextPageButton;
    private float currentTextSize = 18f;
    private int mediaId = -1;
    private int totalCount = 0;
    private String sourceUrl;
    private String readerTitle;
    private String lastProviderLabel = "Local";
    private final List<String> pages = new ArrayList<>();
    private int currentPageIndex = 0;
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .build();

    private static class ContentResult {
        final String content;
        final String providerLabel;

        ContentResult(String content, String providerLabel) {
            this.content = content;
            this.providerLabel = providerLabel;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_text_reader);

        mediaItem = (MediaItem) getIntent().getSerializableExtra("media_item");
        mediaId = getIntent().getIntExtra(EXTRA_MEDIA_ID, -1);
        sourceUrl = getIntent().getStringExtra(EXTRA_URL);
        String explicitTitle = getIntent().getStringExtra(EXTRA_TITLE);

        contentBody = findViewById(R.id.text_reader_body);
        topBar = findViewById(R.id.text_top_bar);
        bottomBar = findViewById(R.id.text_bottom_bar);
        pageIndicator = findViewById(R.id.text_page_indicator);
        prevPageButton = findViewById(R.id.btn_text_prev_page);
        nextPageButton = findViewById(R.id.btn_text_next_page);

        setupUI(explicitTitle);
        resolveSourceAndLoad();
    }

    private void setupUI(String explicitTitle) {
        TextView titleText = findViewById(R.id.text_text_title);
        if (explicitTitle != null && !explicitTitle.trim().isEmpty()) {
            readerTitle = explicitTitle.trim();
            titleText.setText(explicitTitle);
        } else if (mediaItem != null && mediaItem.getTitle() != null) {
            readerTitle = mediaItem.getTitle().trim();
            titleText.setText(mediaItem.getTitle());
        } else {
            readerTitle = "Reader";
            titleText.setText(com.example.mediavault.R.string.auto_reader);
        }

        ImageButton backButton = findViewById(R.id.btn_text_back);
        backButton.setOnClickListener(v -> finish());

        ImageButton zoomIn = findViewById(R.id.btn_text_zoom_in);
        zoomIn.setOnClickListener(v -> adjustTextSize(2f));

        ImageButton zoomOut = findViewById(R.id.btn_text_zoom_out);
        zoomOut.setOnClickListener(v -> adjustTextSize(-2f));

        prevPageButton.setOnClickListener(v -> movePage(-1));
        nextPageButton.setOnClickListener(v -> movePage(1));

        // Toggle bars on click
        findViewById(R.id.scroll_text_content).setOnClickListener(v -> toggleBars());
        topBar.setVisibility(View.VISIBLE);
        bottomBar.setVisibility(View.VISIBLE);
    }

    private void resolveSourceAndLoad() {
        if (sourceUrl != null && !sourceUrl.trim().isEmpty()) {
            loadTextContent(sourceUrl);
            return;
        }
        if (mediaItem != null && mediaItem.getContentUri() != null && !mediaItem.getContentUri().trim().isEmpty()) {
            sourceUrl = mediaItem.getContentUri();
            totalCount = mediaItem.getTotalCount();
            loadTextContent(sourceUrl);
            return;
        }
        if (mediaId == -1) {
            ToastUtils.showCustomToast(this, "No readable source found");
            finish();
            return;
        }

        AppExecutor.getInstance().diskIO().execute(() -> {
            DatabaseHelper dbHelper = DatabaseHelper.getInstance(getApplicationContext());
            try (android.database.Cursor cursor = dbHelper.getMediaById(mediaId)) {
                if (cursor != null && cursor.moveToFirst()) {
                    sourceUrl = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_SOURCE_URL));
                    totalCount = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseHelper.COL_TOTAL_COUNT));
                    if (readerTitle == null || readerTitle.trim().isEmpty() || "Reader".equalsIgnoreCase(readerTitle)) {
                        int titleIndex = cursor.getColumnIndex(DatabaseHelper.COL_TITLE);
                        if (titleIndex >= 0) {
                            readerTitle = cursor.getString(titleIndex);
                        }
                    }
                }
            }
            String resolved = sourceUrl;
            runOnUiThread(() -> {
                if (resolved == null || resolved.trim().isEmpty()) {
                    loadTextContent(null);
                } else {
                    loadTextContent(resolved);
                }
            });
        });
    }

    private void loadTextContent(String source) {
        AppExecutor.getInstance().diskIO().execute(() -> {
            try {
                ContentResult result = readContent(source);
                String content = result == null ? null : result.content;
                if (content == null || content.trim().isEmpty()) {
                    throw new IllegalStateException("Source returned empty content");
                }
                lastProviderLabel = result.providerLabel == null ? "Unknown" : result.providerLabel;
                List<String> paginated = paginateText(content, 2200);
                AppExecutor.getInstance().mainThread().execute(() -> {
                    pages.clear();
                    pages.addAll(paginated);
                    currentPageIndex = 0;
                    showCurrentPage();
                    ToastUtils.showCustomToast(this, "Loaded via " + lastProviderLabel);
                });
            } catch (Exception e) {
                AppExecutor.getInstance().mainThread().execute(() -> {
                    contentBody.setText("Error loading content:\n" + e.getMessage());
                    pageIndicator.setText("0 / 0");
                    prevPageButton.setEnabled(false);
                    nextPageButton.setEnabled(false);
                    ToastUtils.showCustomToast(this, "Reader fallback failed. Try another source URL.");
                });
            }
        });
    }

    private ContentResult readContent(String source) throws Exception {
        if (source == null || source.trim().isEmpty()) {
            return fetchFromTitleProviders(readerTitle);
        }
        if (source.startsWith("http://") || source.startsWith("https://")) {
            return fetchRemoteContent(source);
        }
        if (source.startsWith("content://")) {
            Uri uri = Uri.parse(source);
            try (InputStream is = getContentResolver().openInputStream(uri)) {
                if (is == null) {
                    throw new IllegalStateException("Unable to open content URI");
                }
                return new ContentResult(readTextStream(is), "Device File");
            }
        }

        File file = new File(source);
        if (!file.exists()) {
            throw new IllegalStateException("Local file not found");
        }
        try (InputStream is = new FileInputStream(file)) {
            return new ContentResult(readTextStream(is), "Device File");
        }
    }

    private ContentResult fetchRemoteContent(String url) throws Exception {
        List<Exception> errors = new ArrayList<>();

        // Provider 1: direct source extraction
        try {
            String html = fetchHttpBody(url);
            String readable = htmlToReadableText(html, url);
            if (readable != null && readable.length() >= MIN_READABLE_LENGTH) {
                return new ContentResult(readable, "Source Direct");
            }
        } catch (Exception e) {
            errors.add(e);
            Log.w(TAG, "Direct provider failed for " + url, e);
        }

        // Provider 2: ad-light reader mirror (good fallback for blocked pages)
        try {
            String mirrorUrl = toReaderMirrorUrl(url);
            String mirrorText = fetchHttpBody(mirrorUrl);
            String normalized = normalizePlainText(mirrorText);
            if (normalized != null && normalized.length() >= MIN_READABLE_LENGTH) {
                return new ContentResult(normalized, "Reader Mirror");
            }
        } catch (Exception e) {
            errors.add(e);
            Log.w(TAG, "Reader mirror provider failed for " + url, e);
        }

        // Provider 3: title-based public domain fallback
        try {
            ContentResult fallback = fetchFromTitleProviders(readerTitle);
            if (fallback != null && fallback.content != null && fallback.content.length() >= MIN_READABLE_LENGTH) {
                return fallback;
            }
        } catch (Exception e) {
            errors.add(e);
            Log.w(TAG, "Title fallback provider failed", e);
        }

        if (!errors.isEmpty()) {
            throw new IllegalStateException("All providers failed: " + errors.get(errors.size() - 1).getMessage());
        }
        throw new IllegalStateException("No readable content from providers");
    }

    private String readTextStream(InputStream inputStream) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }

    private String htmlToReadableText(String html, String baseUrl) {
        if (html == null) return "";

        Document document = Jsoup.parse(html, baseUrl);
        document.select("script,style,noscript,header,footer,nav,aside,form,iframe").remove();

        String extracted = extractPrimaryArticleText(document);
        if (extracted != null && extracted.length() >= MIN_READABLE_LENGTH) {
            return normalizePlainText(extracted);
        }

        String withoutScripts = html
                .replaceAll("(?is)<script.*?>.*?</script>", " ")
                .replaceAll("(?is)<style.*?>.*?</style>", " ");
        String collapsed = withoutScripts.replaceAll("(?is)<br\\s*/?>", "\n");
        Spanned decoded = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                ? Html.fromHtml(collapsed, Html.FROM_HTML_MODE_LEGACY)
                : Html.fromHtml(collapsed);
        return normalizePlainText(decoded.toString());
    }

    private String fetchHttpBody(String url) throws Exception {
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IllegalStateException("HTTP " + response.code());
            }
            return response.body().string();
        }
    }

    private String toReaderMirrorUrl(String url) {
        if (url == null) return null;
        String trimmed = url.trim();
        if (trimmed.isEmpty()) return null;
        if (trimmed.startsWith("https://")) {
            return "https://r.jina.ai/http://" + trimmed.substring("https://".length());
        }
        if (trimmed.startsWith("http://")) {
            return "https://r.jina.ai/" + trimmed;
        }
        return "https://r.jina.ai/http://" + trimmed;
    }

    private String extractPrimaryArticleText(Document document) {
        if (document == null) return "";

        String[] selectors = new String[]{
                "article",
                "[itemprop=articleBody]",
                ".chapter-content",
                ".cha-words",
                ".entry-content",
                ".post-content",
                ".read-content",
                "#chapter-content",
                "#chapter-body",
                ".chapter",
                ".content"
        };

        String best = "";
        for (String selector : selectors) {
            Elements blocks = document.select(selector);
            for (Element block : blocks) {
                String text = block.text();
                if (text != null && text.length() > best.length()) {
                    best = text;
                }
            }
            if (best.length() >= MIN_READABLE_LENGTH) {
                return best;
            }
        }

        String body = document.body() != null ? document.body().text() : "";
        return body == null ? "" : body;
    }

    private String normalizePlainText(String text) {
        if (text == null) return "";
        return text
                .replace('\u00A0', ' ')
                .replaceAll("[\\t\\x0B\\f\\r]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .replaceAll(" {2,}", " ")
                .trim();
    }

    private ContentResult fetchFromTitleProviders(String title) throws Exception {
        if (title == null || title.trim().isEmpty() || "Reader".equalsIgnoreCase(title.trim())) {
            throw new IllegalStateException("No title available for provider search");
        }

        String encoded = URLEncoder.encode(title.trim(), StandardCharsets.UTF_8.name());
        String queryUrl = "https://gutendex.com/books?search=" + encoded;
        String payload = fetchHttpBody(queryUrl);
        JSONObject root = new JSONObject(payload);
        JSONArray results = root.optJSONArray("results");
        if (results == null || results.length() == 0) {
            throw new IllegalStateException("No public-domain source match");
        }

        for (int i = 0; i < results.length(); i++) {
            JSONObject item = results.optJSONObject(i);
            if (item == null) continue;
            JSONObject formats = item.optJSONObject("formats");
            if (formats == null) continue;

            String providerUrl = pickBestFormatUrl(formats);
            if (providerUrl == null || providerUrl.trim().isEmpty()) continue;

            try {
                String body = fetchHttpBody(providerUrl);
                String readable = looksLikeHtml(body)
                        ? htmlToReadableText(body, providerUrl)
                        : normalizePlainText(body);
                if (readable != null && readable.length() >= MIN_READABLE_LENGTH) {
                    return new ContentResult(readable, "Gutendex");
                }
            } catch (Exception ignored) {
                // Try next result.
            }
        }

        throw new IllegalStateException("No readable text from public providers");
    }

    private String pickBestFormatUrl(JSONObject formats) {
        String[] preferredKeys = new String[]{
                "text/plain; charset=utf-8",
                "text/plain",
                "text/html; charset=utf-8",
                "text/html",
                "application/xhtml+xml"
        };

        for (String key : preferredKeys) {
            String url = formats.optString(key, null);
            if (url != null && !url.trim().isEmpty() && !url.endsWith(".zip")) {
                return url;
            }
        }

        // Fallback: first textual format URL.
        Iterator<String> keys = formats.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            String lower = key.toLowerCase(Locale.US);
            if (lower.contains("text") || lower.contains("html")) {
                String url = formats.optString(key, null);
                if (url != null && !url.trim().isEmpty() && !url.endsWith(".zip")) {
                    return url;
                }
            }
        }
        return null;
    }

    private boolean looksLikeHtml(String body) {
        if (body == null) return false;
        String trimmed = body.trim().toLowerCase(Locale.US);
        return trimmed.startsWith("<!doctype html")
                || trimmed.startsWith("<html")
                || trimmed.contains("<body")
                || trimmed.contains("<p>");
    }

    private List<String> paginateText(String content, int maxCharsPerPage) {
        List<String> chunks = new ArrayList<>();
        String normalized = content == null ? "" : content.trim();
        if (normalized.isEmpty()) {
            chunks.add("No readable content available.");
            return chunks;
        }

        int cursor = 0;
        while (cursor < normalized.length()) {
            int end = Math.min(normalized.length(), cursor + maxCharsPerPage);
            if (end < normalized.length()) {
                int lastBreak = normalized.lastIndexOf(' ', end);
                if (lastBreak > cursor + 200) {
                    end = lastBreak;
                }
            }
            chunks.add(normalized.substring(cursor, end).trim());
            cursor = end;
        }
        if (chunks.isEmpty()) {
            chunks.add(normalized);
        }
        return chunks;
    }

    private void movePage(int delta) {
        if (pages.isEmpty()) {
            return;
        }
        int next = currentPageIndex + delta;
        if (next < 0 || next >= pages.size()) {
            return;
        }
        currentPageIndex = next;
        showCurrentPage();
    }

    private void showCurrentPage() {
        if (pages.isEmpty()) {
            contentBody.setText(com.example.mediavault.R.string.auto_no_readable_content_available);
            pageIndicator.setText("0 / 0");
            prevPageButton.setEnabled(false);
            nextPageButton.setEnabled(false);
            return;
        }
        contentBody.setText(pages.get(currentPageIndex));
        pageIndicator.setText(String.format(Locale.getDefault(), "%d / %d", currentPageIndex + 1, pages.size()));
        prevPageButton.setEnabled(currentPageIndex > 0);
        nextPageButton.setEnabled(currentPageIndex < pages.size() - 1);
        persistPageProgress();
    }

    private void persistPageProgress() {
        if (mediaId == -1 || pages.isEmpty()) {
            return;
        }
        int safeTotal = Math.max(totalCount, 1);
        float ratio = (currentPageIndex + 1f) / (float) pages.size();
        float mappedProgress = Math.max(1f, ratio * safeTotal);
        String status = mappedProgress >= safeTotal ? "Completed" : null;
        AppExecutor.getInstance().diskIO().execute(() ->
                DatabaseHelper.getInstance(getApplicationContext()).updateProgress(mediaId, mappedProgress, status, 0f)
        );
    }

    private void adjustTextSize(float delta) {
        currentTextSize += delta;
        if (currentTextSize < 12f) currentTextSize = 12f;
        if (currentTextSize > 36f) currentTextSize = 36f;
        contentBody.setTextSize(TypedValue.COMPLEX_UNIT_SP, currentTextSize);
    }

    private void toggleBars() {
        int visibility = (topBar.getVisibility() == View.VISIBLE) ? View.GONE : View.VISIBLE;
        topBar.setVisibility(visibility);
        bottomBar.setVisibility(visibility);
    }
}