package com.example.mediavault.api.search.strategies;

import com.example.mediavault.api.search.MediaSearchStrategy;
import com.example.mediavault.api.search.ProviderHttpException;
import com.example.mediavault.api.search.UniversalMediaResult;
import com.example.mediavault.api.search.model.MangaDexSearchResponse;
import com.example.mediavault.api.search.service.MangaDexSearchApiService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import retrofit2.Response;
import retrofit2.Retrofit;

public class MangaDexStrategy implements MediaSearchStrategy {
    private static final String BASE_URL = "https://api.mangadex.org/";
    private static final String PROVIDER = "MangaDex";

    private final MangaDexSearchApiService apiService;

    public MangaDexStrategy() {
        this(SearchRetrofitFactory.create(BASE_URL));
    }

    MangaDexStrategy(Retrofit retrofit) {
        this.apiService = retrofit.create(MangaDexSearchApiService.class);
    }

    @Override
    public List<UniversalMediaResult> executeSearch(String query) throws IOException {
        Response<MangaDexSearchResponse> response = apiService
                .searchManga(
                        query,
                        10,
                        Arrays.asList("cover_art"),
                        Arrays.asList("safe", "suggestive", "erotica"),
                        "desc"
                )
                .execute();

        if (!response.isSuccessful()) {
            throw new ProviderHttpException(PROVIDER, response.code(), "MangaDex search failed with HTTP " + response.code());
        }

        MangaDexSearchResponse body = response.body();
        if (body == null || body.data == null || body.data.isEmpty()) {
            return new ArrayList<>();
        }

        List<UniversalMediaResult> mapped = new ArrayList<>();
        for (MangaDexSearchResponse.MangaData item : body.data) {
            if (item == null || item.attributes == null) {
                continue;
            }
            String title = pickLocalized(item.attributes.title);
            if (title == null || title.trim().isEmpty()) {
                continue;
            }
            mapped.add(new UniversalMediaResult(
                    item.id,
                    title,
                    "Manga",
                    pickLocalized(item.attributes.description),
                    null,
                    PROVIDER,
                    "https://mangadex.org/title/" + item.id,
                    item.attributes.year == null ? 0 : item.attributes.year
            ));
        }

        return mapped;
    }

    private String pickLocalized(Map<String, String> valueMap) {
        if (valueMap == null || valueMap.isEmpty()) {
            return null;
        }
        if (hasText(valueMap.get("en"))) return valueMap.get("en");
        if (hasText(valueMap.get("ja-ro"))) return valueMap.get("ja-ro");
        if (hasText(valueMap.get("ja"))) return valueMap.get("ja");
        for (String value : valueMap.values()) {
            if (hasText(value)) return value;
        }
        return null;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
