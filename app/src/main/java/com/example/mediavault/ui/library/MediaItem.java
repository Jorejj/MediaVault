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

    public MediaItem(int id, String title, String type, String genre, String status, int progress, int capacity, String unit, String coverPath, float rating) {
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
    }

    public int getId() { return id; }
    public String getTitle() { return title; }
    public String getType() { return type; }
    public String getGenre() { return genre; }
    public String getStatus() { return status; }
    public int getProgress() { return progress; }
    public int getCapacity() { return capacity; }
    public String getUnit() { return unit; }
    public String getCoverPath() { return coverPath; }
    public float getRating() { return rating; }
    
    // For backward compatibility with MediaAdapter if needed
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
