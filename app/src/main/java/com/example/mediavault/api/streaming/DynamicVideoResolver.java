package com.example.mediavault.api.streaming;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;
import com.example.mediavault.api.consumet.ConsumetStreamResponse;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Dynamic streaming resolver that reads base URL from SharedPreferences.
 */
public class DynamicVideoResolver {
    private static final String TAG = "DynamicVideoResolver";
    private static final String SCRAPER_PREFS = "scraper_settings";
    private static final String KEY_ACTIVE_SCRAPER_URL = "activeScraperUrl";
    private static final String DEFAULT_SCRAPER_URL = "https://api.consumet.org/";

    private final Context appContext;
    private final OkHttpClient okHttpClient;

    public DynamicVideoResolver(Context context) {
        this.appContext = context.getApplicationContext();
        this.okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(12, TimeUnit.SECONDS)
                .readTimeout(12, TimeUnit.SECONDS)
                .writeTimeout(12, TimeUnit.SECONDS)
                .build();
    }

    /**
     * Resolves first playable m3u8/mp4 URL from a dynamic scraper endpoint.
     *
     * @param relativeStreamPath Example: movies/flixhq/watch/{episodeId}
     */
    public String resolvePlayableUrl(String relativeStreamPath) throws IOException, ProviderDeadException {
        String baseUrl = readActiveBaseUrl();
        String normalizedPath = normalizeRelativePath(relativeStreamPath);
        if (normalizedPath == null || normalizedPath.isEmpty()) {
            throw new IOException("Invalid stream path");
        }

        DynamicScraperApiService apiService;
        try {
            apiService = createApi(baseUrl);
        } catch (IllegalArgumentException invalidBaseUrl) {
            throw new IOException("Invalid scraper base URL: " + baseUrl, invalidBaseUrl);
        }
        try {
            Response<ConsumetStreamResponse> response = apiService.getStreamingLinks(normalizedPath).execute();
            if (!response.isSuccessful()) {
                throw new IOException("Streaming lookup failed with HTTP " + response.code());
            }
            ConsumetStreamResponse body = response.body();
            if (body == null) {
                throw new IOException("Streaming lookup returned empty body");
            }
            String best = pickBestSource(body.getSources());
            if (best != null) {
                return best;
            }
            if (isPlayableDirectLink(body.getDownload())) {
                return body.getDownload();
            }
            throw new IOException("No playable m3u8/mp4 links returned by scraper");
        } catch (IOException ioEx) {
            if (isUnknownHost(ioEx)) {
                Log.e(TAG, "Scraper host is unreachable: " + baseUrl, ioEx);
                throw new ProviderDeadException(
                        baseUrl,
                        "Scraper host is unreachable. Update scraper URL in settings.",
                        ioEx
                );
            }
            throw ioEx;
        }
    }

    private DynamicScraperApiService createApi(String baseUrl) {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        return retrofit.create(DynamicScraperApiService.class);
    }

    private String readActiveBaseUrl() throws IOException {
        SharedPreferences prefs = appContext.getSharedPreferences(SCRAPER_PREFS, Context.MODE_PRIVATE);
        String raw = prefs.getString(KEY_ACTIVE_SCRAPER_URL, DEFAULT_SCRAPER_URL);
        if (raw == null || raw.trim().isEmpty()) {
            return DEFAULT_SCRAPER_URL;
        }
        String normalized = raw.trim();
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "https://" + normalized;
        }
        if (!normalized.endsWith("/")) {
            normalized += "/";
        }

        Uri parsed = Uri.parse(normalized);
        String scheme = parsed.getScheme();
        String host = parsed.getHost();
        if (scheme == null || host == null ||
                (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
            throw new IOException("Invalid scraper base URL configured: " + raw);
        }

        return normalized;
    }

    private String normalizeRelativePath(String relativeStreamPath) {
        if (relativeStreamPath == null) {
            return null;
        }
        String normalized = relativeStreamPath.trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private String pickBestSource(List<ConsumetStreamResponse.Source> sources) {
        if (sources == null || sources.isEmpty()) {
            return null;
        }
        String bestM3u8 = null;
        String bestMp4 = null;
        for (ConsumetStreamResponse.Source source : sources) {
            if (source == null || source.getUrl() == null || source.getUrl().trim().isEmpty()) {
                continue;
            }
            String url = source.getUrl().trim();
            if (source.isM3U8() || url.contains(".m3u8")) {
                bestM3u8 = url;
                break;
            }
            if (url.contains(".mp4")) {
                bestMp4 = url;
            }
        }
        return bestM3u8 != null ? bestM3u8 : bestMp4;
    }

    private boolean isPlayableDirectLink(String url) {
        if (url == null || url.trim().isEmpty()) {
            return false;
        }
        String value = url.toLowerCase(Locale.ROOT);
        return value.contains(".m3u8") || value.contains(".mp4");
    }

    private boolean isUnknownHost(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof UnknownHostException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
