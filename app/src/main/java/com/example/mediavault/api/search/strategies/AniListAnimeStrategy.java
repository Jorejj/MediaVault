package com.example.mediavault.api.search.strategies;

import com.example.mediavault.api.search.MediaSearchStrategy;
import com.example.mediavault.api.search.ProviderHttpException;
import com.example.mediavault.api.search.UniversalMediaResult;
import com.example.mediavault.api.search.model.AniListSearchResponse;
import com.example.mediavault.api.search.service.AniListSearchApiService;
import com.google.gson.Gson;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Response;
import retrofit2.Retrofit;

public class AniListAnimeStrategy implements MediaSearchStrategy {
    private static final String BASE_URL = "https://graphql.anilist.co/";
    private static final String PROVIDER = "AniList";
    private static final String SEARCH_QUERY =
            "query ($search: String) { " +
                    "Page(page: 1, perPage: 10) { " +
                    "media(search: $search, type: ANIME) { " +
                    "id title { romaji english native } description(asHtml: false) coverImage { large } startDate { year } " +
                    "} } }";

    private final AniListSearchApiService apiService;
    private final Gson gson = new Gson();

    public AniListAnimeStrategy() {
        this(SearchRetrofitFactory.create(BASE_URL));
    }

    AniListAnimeStrategy(Retrofit retrofit) {
        this.apiService = retrofit.create(AniListSearchApiService.class);
    }

    @Override
    public List<UniversalMediaResult> executeSearch(String query) throws IOException {
        Map<String, String> variables = new HashMap<>();
        variables.put("search", query);
        String variablesJson = gson.toJson(variables);

        Response<AniListSearchResponse> response = apiService
                .searchAnime(SEARCH_QUERY, variablesJson)
                .execute();

        if (!response.isSuccessful()) {
            throw new ProviderHttpException(PROVIDER, response.code(), "AniList search failed with HTTP " + response.code());
        }

        AniListSearchResponse body = response.body();
        if (body == null || body.data == null || body.data.page == null || body.data.page.media == null) {
            return new ArrayList<>();
        }

        List<UniversalMediaResult> mapped = new ArrayList<>();
        for (AniListSearchResponse.Media media : body.data.page.media) {
            if (media == null || media.title == null) {
                continue;
            }
            String title = coalesce(media.title.english, media.title.romaji, media.title.nativeTitle);
            if (title == null || title.trim().isEmpty()) {
                continue;
            }
            mapped.add(new UniversalMediaResult(
                    String.valueOf(media.id),
                    title,
                    "Anime",
                    media.description,
                    media.coverImage == null ? null : media.coverImage.large,
                    PROVIDER,
                    "https://anilist.co/anime/" + media.id,
                    media.startDate == null ? 0 : media.startDate.year
            ));
        }
        return mapped;
    }

    private String coalesce(String first, String second, String third) {
        if (first != null && !first.trim().isEmpty()) return first;
        if (second != null && !second.trim().isEmpty()) return second;
        if (third != null && !third.trim().isEmpty()) return third;
        return null;
    }
}
