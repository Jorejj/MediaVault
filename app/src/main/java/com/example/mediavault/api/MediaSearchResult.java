package com.example.mediavault.api;

public class MediaSearchResult {
    private String title;
    private String type;
    private String genre;
    private String author;
    private String description;
    private String imageUrl;
    private Integer capacity;
    private String unit;
    private String sourceUrl;
    private String contentType;
    private String tmdbId;
    private int releaseYear;
    private MediaMetadataProfile metadataProfile;

    public MediaSearchResult(String title, String type, String genre, String author, String description, String imageUrl, Integer capacity, String unit) {
        this(title, type, genre, author, description, imageUrl, capacity, unit, null, null, null, 0);
    }

    public MediaSearchResult(
            String title,
            String type,
            String genre,
            String author,
            String description,
            String imageUrl,
            Integer capacity,
            String unit,
            String sourceUrl,
            String contentType,
            String tmdbId,
            int releaseYear
    ) {
        this.title = title;
        this.type = type;
        this.genre = genre;
        this.author = author;
        this.description = description;
        this.imageUrl = imageUrl;
        this.capacity = capacity;
        this.unit = unit;
        this.sourceUrl = sourceUrl;
        this.contentType = contentType;
        this.tmdbId = tmdbId;
        this.releaseYear = releaseYear;
    }

    public String getTitle() { return title; }
    public String getType() { return type; }
    public String getGenre() { return genre; }
    public String getAuthor() { return author; }
    public String getDescription() { return description; }
    public String getImageUrl() { return imageUrl; }
    public Integer getCapacity() { return capacity; }
    public String getUnit() { return unit; }
    public String getSourceUrl() { return sourceUrl; }
    public String getContentType() { return contentType; }
    public String getTmdbId() { return tmdbId; }
    public int getReleaseYear() { return releaseYear; }
    public MediaMetadataProfile getMetadataProfile() { return metadataProfile; }

    public MediaSearchResult setMetadataProfile(MediaMetadataProfile metadataProfile) {
        this.metadataProfile = metadataProfile;
        return this;
    }
}
