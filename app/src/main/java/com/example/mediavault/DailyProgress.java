package com.example.mediavault;

public class DailyProgress {
    public int pagesRead;
    public int episodesWatched;
    public int minutesWatched;

    public DailyProgress(int pages, int episodes, int minutes) {
        this.pagesRead = pages;
        this.episodesWatched = episodes;
        this.minutesWatched = minutes;
    }
}
