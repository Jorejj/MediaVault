package com.example.mediavault.api.search.strategies;

import com.example.mediavault.api.search.MediaSearchStrategy;
import com.example.mediavault.api.search.ProviderHttpException;
import com.example.mediavault.api.search.UniversalMediaResult;
import com.example.mediavault.api.search.model.GoogleBooksSearchResponse;
import com.example.mediavault.api.search.service.GoogleBooksSearchApiService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import retrofit2.Response;
import retrofit2.Retrofit;

public class GoogleBooksStrategy implements MediaSearchStrategy {
    private static final String BASE_URL = "https://www.googleapis.com/books/v1/";
    private static final String PROVIDER = "GoogleBooks";

    private final GoogleBooksSearchApiService apiService;

    public GoogleBooksStrategy() {
        this(SearchRetrofitFactory.create(BASE_URL));
    }

    GoogleBooksStrategy(Retrofit retrofit) {
        this.apiService = retrofit.create(GoogleBooksSearchApiService.class);
    }

    @Override
    public List<UniversalMediaResult> executeSearch(String query) throws IOException {
        Response<GoogleBooksSearchResponse> response = apiService
                .searchBooks(query, 10, "books", "relevance", "en")
                .execute();

        if (!response.isSuccessful()) {
            throw new ProviderHttpException(PROVIDER, response.code(), "Google Books search failed with HTTP " + response.code());
        }

        GoogleBooksSearchResponse body = response.body();
        if (body == null || body.items == null || body.items.isEmpty()) {
            return new ArrayList<>();
        }

        List<UniversalMediaResult> mapped = new ArrayList<>();
        for (GoogleBooksSearchResponse.Item item : body.items) {
            if (item == null || item.volumeInfo == null || item.volumeInfo.title == null || item.volumeInfo.title.trim().isEmpty()) {
                continue;
            }
            mapped.add(new UniversalMediaResult(
                    item.id,
                    item.volumeInfo.title,
                    "Book",
                    item.volumeInfo.description,
                    normalizeThumbnail(item.volumeInfo.imageLinks),
                    PROVIDER,
                    item.volumeInfo.infoLink,
                    parseYear(item.volumeInfo.publishedDate)
            ));
        }
        return mapped;
    }

    private String normalizeThumbnail(GoogleBooksSearchResponse.ImageLinks imageLinks) {
        if (imageLinks == null || imageLinks.thumbnail == null || imageLinks.thumbnail.trim().isEmpty()) {
            return null;
        }
        return imageLinks.thumbnail.replace("http://", "https://");
    }

    private int parseYear(String publishedDate) {
        if (publishedDate == null || publishedDate.length() < 4) {
            return 0;
        }
        try {
            return Integer.parseInt(publishedDate.substring(0, 4));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
