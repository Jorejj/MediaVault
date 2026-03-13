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

    public MediaSearchResult(String title, String type, String genre, String author, String description, String imageUrl, Integer capacity, String unit) {
        this.title = title;
        this.type = type;
        this.genre = genre;
        this.author = author;
        this.description = description;
        this.imageUrl = imageUrl;
        this.capacity = capacity;
        this.unit = unit;
    }

    public String getTitle() { return title; }
    public String getType() { return type; }
    public String getGenre() { return genre; }
    public String getAuthor() { return author; }
    public String getDescription() { return description; }
    public String getImageUrl() { return imageUrl; }
    public Integer getCapacity() { return capacity; }
    public String getUnit() { return unit; }
}