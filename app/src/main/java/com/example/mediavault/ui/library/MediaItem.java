package com.example.mediavault.ui.library;

public class MediaItem {
    private int id;
    private String title;
    private String type;
    private String genre;
    private String status;
    private float progress;
    private float previousProgress;
    private int capacity;
    private String unit;
    private String coverPath;
    private float rating;
    private boolean favorite;
    private String sourceUrl;
    private String contentType;

    public MediaItem(int id, String title, String type, String genre, String status, float progress, int capacity, String unit, String coverPath, float rating) {
        this(id, title, type, genre, status, progress, progress, capacity, unit, coverPath, rating, false, null, null);
    }

    public MediaItem(int id, String title, String type, String genre, String status, float progress, float previousProgress, int capacity, String unit, String coverPath, float rating, boolean favorite, String sourceUrl, String contentType) {
        this.id = id;
        this.title = title;
        this.type = type;
        this.genre = genre;
        this.status = status;
        this.progress = progress;
        this.previousProgress = previousProgress;
        this.capacity = capacity;
        this.unit = unit;
        this.coverPath = coverPath;
        this.rating = rating;
        this.favorite = favorite;
        this.sourceUrl = sourceUrl;
        this.contentType = contentType;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getGenre() { return genre; }
    public void setGenre(String genre) { this.genre = genre; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public float getProgress() { return progress; }
    public void setProgress(float progress) { this.progress = progress; }
    public float getPreviousProgress() { return previousProgress; }
    public void setPreviousProgress(float previousProgress) { this.previousProgress = previousProgress; }
    public int getCapacity() { return capacity; }
    public void setCapacity(int capacity) { this.capacity = capacity; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public String getCoverPath() { return coverPath; }
    public void setCoverPath(String coverPath) { this.coverPath = coverPath; }
    public float getRating() { return rating; }
    public void setRating(float rating) { this.rating = rating; }
    public boolean isFavorite() { return favorite; }
    public void setFavorite(boolean favorite) { this.favorite = favorite; }
    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    
    public String getContentUri() {
        return sourceUrl;
    }
    
    public String getSubtitle() {
        return type + " • " + status;
    }
    
    public String getRatingString() {
        return String.valueOf(rating);
    }

    public float getRatingValue() {
        return rating;
    }

    public float getCurrentProgress() {
        return progress;
    }

    public int getTotalCount() {
        return capacity;
    }
}
