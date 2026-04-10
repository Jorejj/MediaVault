package com.example.mediavault.api.providers;

import java.net.URLEncoder;

/**
 * Generic movie/tv mirror fallback provider.
 */
public class StreamMirrorProvider implements MediaProvider {
    private final String providerName;
    private final String baseUrl;

    public StreamMirrorProvider(String providerName, String baseUrl) {
        this.providerName = providerName;
        this.baseUrl = baseUrl;
    }

    @Override
    public String getProviderName() {
        return providerName;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public boolean supportsMediaType(MediaType type) {
        return type == MediaType.MOVIE || type == MediaType.TV_SHOW;
    }

    @Override
    public String resolveMovie(String title, int year, String tmdbId) {
        if (tmdbId != null && !tmdbId.isEmpty()) {
            return baseUrl + "/embed/movie/" + tmdbId;
        }
        return baseUrl + "/search?q=" + encode(title);
    }

    @Override
    public String resolveTvEpisode(String title, int season, int episode, String tmdbId) {
        if (tmdbId != null && !tmdbId.isEmpty()) {
            return baseUrl + "/embed/tv/" + tmdbId + "/" + season + "/" + episode;
        }
        return baseUrl + "/search?q=" + encode(title + " s" + season + "e" + episode);
    }

    private String encode(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, "UTF-8");
        } catch (Exception e) {
            return (value == null ? "" : value).replace(" ", "+");
        }
    }
}
