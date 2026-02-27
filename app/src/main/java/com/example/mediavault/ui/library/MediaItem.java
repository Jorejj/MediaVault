package com.example.mediavault.ui.library;

public class MediaItem {
    private String title;
    private String subtitle;
    private String rating;

    public MediaItem(String title, String subtitle, String rating) {
        this.title = title;
        this.subtitle = subtitle;
        this.rating = rating;
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
}
