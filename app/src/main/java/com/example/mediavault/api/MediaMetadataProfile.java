package com.example.mediavault.api;

import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Canonical metadata envelope used to normalize provider-specific media data.
 */
public class MediaMetadataProfile {
    private String canonicalTitle;
    private String normalizedTitle;
    private final Set<String> altTitles = new LinkedHashSet<>();
    private String providerId;
    private String providerSlug;
    private String canonicalUrl;
    private String metadataSource;
    private String mediaType;
    private String subType;
    private String language;
    private String region;
    private String status;
    private Integer releaseYear;
    private Integer totalCount;
    private String unit;
    private final Set<String> genres = new LinkedHashSet<>();
    private final Set<String> tags = new LinkedHashSet<>();
    private final Map<String, String> externalIds = new LinkedHashMap<>();
    private Float rating;
    private Float popularity;
    private String providerFeaturesJson;
    private Float metadataConfidence;
    private Integer metadataPriority;
    private String metadataUpdatedAtIso;

    public static MediaMetadataProfile create() {
        return new MediaMetadataProfile();
    }

    public MediaMetadataProfile withCanonicalTitle(String value) {
        canonicalTitle = trimOrNull(value);
        if (normalizedTitle == null) {
            normalizedTitle = normalizeTitle(value);
        }
        return this;
    }

    public MediaMetadataProfile withNormalizedTitle(String value) {
        normalizedTitle = trimOrNull(value);
        return this;
    }

    public MediaMetadataProfile addAltTitle(String value) {
        String clean = trimOrNull(value);
        if (clean != null) altTitles.add(clean);
        return this;
    }

    public MediaMetadataProfile addAltTitles(List<String> values) {
        if (values == null) return this;
        for (String value : values) addAltTitle(value);
        return this;
    }

    public MediaMetadataProfile withProviderId(String value) {
        providerId = trimOrNull(value);
        return this;
    }

    public MediaMetadataProfile withProviderSlug(String value) {
        providerSlug = trimOrNull(value);
        return this;
    }

    public MediaMetadataProfile withCanonicalUrl(String value) {
        canonicalUrl = trimOrNull(value);
        return this;
    }

    public MediaMetadataProfile withMetadataSource(String value) {
        metadataSource = trimOrNull(value);
        return this;
    }

    public MediaMetadataProfile withMediaType(String value) {
        mediaType = trimOrNull(value);
        return this;
    }

    public MediaMetadataProfile withSubType(String value) {
        subType = trimOrNull(value);
        return this;
    }

    public MediaMetadataProfile withLanguage(String value) {
        language = trimOrNull(value);
        return this;
    }

    public MediaMetadataProfile withRegion(String value) {
        region = trimOrNull(value);
        return this;
    }

    public MediaMetadataProfile withStatus(String value) {
        status = trimOrNull(value);
        return this;
    }

    public MediaMetadataProfile withReleaseYear(Integer value) {
        releaseYear = value;
        return this;
    }

    public MediaMetadataProfile withTotalCount(Integer value) {
        totalCount = value;
        return this;
    }

    public MediaMetadataProfile withUnit(String value) {
        unit = trimOrNull(value);
        return this;
    }

    public MediaMetadataProfile addGenre(String value) {
        String clean = trimOrNull(value);
        if (clean != null) genres.add(clean);
        return this;
    }

    public MediaMetadataProfile addGenres(List<String> values) {
        if (values == null) return this;
        for (String value : values) addGenre(value);
        return this;
    }

    public MediaMetadataProfile addTag(String value) {
        String clean = trimOrNull(value);
        if (clean != null) tags.add(clean);
        return this;
    }

    public MediaMetadataProfile addTags(List<String> values) {
        if (values == null) return this;
        for (String value : values) addTag(value);
        return this;
    }

    public MediaMetadataProfile addExternalId(String key, String value) {
        String cleanKey = trimOrNull(key);
        String cleanValue = trimOrNull(value);
        if (cleanKey != null && cleanValue != null) {
            externalIds.put(cleanKey, cleanValue);
        }
        return this;
    }

    public MediaMetadataProfile withRating(Float value) {
        rating = value;
        return this;
    }

    public MediaMetadataProfile withPopularity(Float value) {
        popularity = value;
        return this;
    }

    public MediaMetadataProfile withProviderFeaturesJson(String value) {
        providerFeaturesJson = trimOrNull(value);
        return this;
    }

    public MediaMetadataProfile withMetadataConfidence(Float value) {
        metadataConfidence = value;
        return this;
    }

    public MediaMetadataProfile withMetadataPriority(Integer value) {
        metadataPriority = value;
        return this;
    }

    public MediaMetadataProfile stampNow() {
        metadataUpdatedAtIso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(new Date());
        return this;
    }

    public String getCanonicalTitle() {
        return canonicalTitle;
    }

    public String getNormalizedTitle() {
        return normalizedTitle;
    }

    public String getProviderId() {
        return providerId;
    }

    public String getProviderSlug() {
        return providerSlug;
    }

    public String getCanonicalUrl() {
        return canonicalUrl;
    }

    public String getMetadataSource() {
        return metadataSource;
    }

    public String getMediaType() {
        return mediaType;
    }

    public String getSubType() {
        return subType;
    }

    public String getLanguage() {
        return language;
    }

    public String getRegion() {
        return region;
    }

    public String getStatus() {
        return status;
    }

    public Integer getReleaseYear() {
        return releaseYear;
    }

    public Integer getTotalCount() {
        return totalCount;
    }

    public String getUnit() {
        return unit;
    }

    public Float getRating() {
        return rating;
    }

    public Float getPopularity() {
        return popularity;
    }

    public String getProviderFeaturesJson() {
        return providerFeaturesJson;
    }

    public Float getMetadataConfidence() {
        return metadataConfidence;
    }

    public Integer getMetadataPriority() {
        return metadataPriority;
    }

    public String getMetadataUpdatedAtIso() {
        return metadataUpdatedAtIso;
    }

    public String getAltTitlesJson() {
        return toJsonArrayString(new ArrayList<>(altTitles));
    }

    public String getGenresJson() {
        return toJsonArrayString(new ArrayList<>(genres));
    }

    public String getTagsJson() {
        return toJsonArrayString(new ArrayList<>(tags));
    }

    public String getExternalIdsJson() {
        try {
            return new JSONObject(externalIds).toString();
        } catch (Exception ignored) {
            return "{}";
        }
    }

    public static String normalizeTitle(String title) {
        if (title == null) return null;
        String normalized = title.toLowerCase(Locale.US)
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return normalized.isEmpty() ? null : normalized;
    }

    public static List<String> splitCsv(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null) return out;
        String[] parts = raw.split(",");
        for (String part : parts) {
            String clean = trimOrNull(part);
            if (clean != null) out.add(clean);
        }
        return out;
    }

    public static String detectProviderSlugFromUrl(String url) {
        String clean = trimOrNull(url);
        if (clean == null) return null;
        String lower = clean.toLowerCase(Locale.US);
        if (lower.contains("animekai.to") || lower.contains("anikai.to")) return "animekai";
        if (lower.contains("aniwatchtv.to")) return "aniwatchtv";
        if (lower.contains("animepahe.pw")) return "animepahe";
        if (lower.contains("bilibili.tv")) return "bilibili";
        if (lower.contains("comix.to")) return "comix";
        if (lower.contains("mangafire.to")) return "mangafire";
        if (lower.contains("weebcentral.com")) return "weebcentral";
        if (lower.contains("nepu.to")) return "nepu";
        if (lower.contains("xprime.su")) return "xprime";
        if (lower.contains("cineby.sc")) return "cineby";
        if (lower.contains("openchapter.io")) return "openchapter";
        if (lower.contains("novelfire.net")) return "novelfire";
        if (lower.contains("wtr-lab.com")) return "wtr-lab";
        if (lower.contains("tmdb.org")) return "tmdb";
        if (lower.contains("googleapis.com/books") || lower.contains("books.google.com")) return "google-books";
        if (lower.contains("openlibrary.org")) return "openlibrary";
        if (lower.contains("api.jikan.moe")) return "jikan";
        return null;
    }

    public static Integer safeYear(int releaseYear) {
        return releaseYear > 0 ? releaseYear : null;
    }

    private static String trimOrNull(String value) {
        if (value == null) return null;
        String clean = value.trim();
        return clean.isEmpty() ? null : clean;
    }

    private static String toJsonArrayString(List<String> values) {
        JSONArray array = new JSONArray();
        if (values != null) {
            for (String value : values) {
                if (!TextUtils.isEmpty(value)) {
                    array.put(value);
                }
            }
        }
        return array.toString();
    }

    public static String mergeJsonArrays(String firstJson, String secondJson) {
        Set<String> merged = new LinkedHashSet<>();
        appendJsonArray(merged, firstJson);
        appendJsonArray(merged, secondJson);
        return toJsonArrayString(new ArrayList<>(merged));
    }

    private static void appendJsonArray(Set<String> out, String json) {
        if (TextUtils.isEmpty(json)) return;
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                String value = trimOrNull(array.optString(i, null));
                if (value != null) out.add(value);
            }
        } catch (JSONException ignored) {
        }
    }
}
