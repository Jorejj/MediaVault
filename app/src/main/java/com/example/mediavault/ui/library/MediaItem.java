package com.example.mediavault.ui.library;

public class MediaItem {
    private int id;
    private String title;
    private String type;
    private String genre;
    private String status;
    private int progress;
    private int capacity;
    private String unit;
    private String coverPath;
    private float rating;
    private boolean favorite;

    public MediaItem(int id, String title, String type, String genre, String status, int progress, int capacity, String unit, String coverPath, float rating) {
        this(id, title, type, genre, status, progress, capacity, unit, coverPath, rating, false);
    }

    public MediaItem(int id, String title, String type, String genre, String status, int progress, int capacity, String unit, String coverPath, float rating, boolean favorite) {
        this.id = id;
        this.title = title;
        this.type = type;
        this.genre = genre;
        this.status = status;
        this.progress = progress;
        this.capacity = capacity;
        this.unit = unit;
        this.coverPath = coverPath;
        this.rating = rating;
        this.favorite = favorite;
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
    public int getProgress() { return progress; }
    public void setProgress(int progress) { this.progress = progress; }
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
    
    public String getSubtitle() {
        return type + " • " + status;
    }
    
    public String getRatingString() {
        return String.valueOf(rating);
    }

    public float getRatingValue() {
        return rating;
    }
}
