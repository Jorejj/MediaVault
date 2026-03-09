package com.example.mediavault.ui.library;

public class MediaItem {
    private String title;
    private String subtitle;
    private String rating;
    private String type;
    private float ratingValue;

    public MediaItem(String title, String subtitle, String rating, String type, float ratingValue) {
        this.title = title;
        this.subtitle = subtitle;
        this.rating = rating;
        this.type = type;
        this.ratingValue = ratingValue;
    }

    public String getTitle() {
        return title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public String getRating() {
        return rating;
    }

    public String getType() {
        return type;
    }

    public float getRatingValue() {
        return ratingValue;
    }
}
