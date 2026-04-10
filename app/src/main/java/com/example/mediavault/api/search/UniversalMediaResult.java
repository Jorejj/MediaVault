package com.example.mediavault.api.search;

/**
 * Unified search result model used by provider strategies.
 */
public class UniversalMediaResult {
    private final String sourceId;
    private final String title;
    private final String author;
    private final String mediaType;
    private final String description;
    private final String imageUrl;
    private final String sourceProvider;
    private final String sourceUrl;
    private final int releaseYear;

    public UniversalMediaResult(
            String sourceId,
            String title,
            String author,
            String mediaType,
            String description,
            String imageUrl,
            String sourceProvider,
            String sourceUrl,
            int releaseYear
    ) {
        this.sourceId = sourceId;
        this.title = title;
        this.author = author;
        this.mediaType = mediaType;
        this.description = description;
        this.imageUrl = imageUrl;
        this.sourceProvider = sourceProvider;
        this.sourceUrl = sourceUrl;
        this.releaseYear = releaseYear;
    }

    public String getSourceId() {
        return sourceId;
    }

    public String getTitle() {
        return title;
    }

    public String getAuthor() {
        return author;
    }

    public String getMediaType() {
        return mediaType;
    }

    public String getDescription() {
        return description;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public String getSourceProvider() {
        return sourceProvider;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public int getReleaseYear() {
        return releaseYear;
    }
}
