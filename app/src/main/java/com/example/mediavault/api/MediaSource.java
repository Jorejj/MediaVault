package com.example.mediavault.api;

import java.util.List;

public interface MediaSource {
    interface Callback<T> {
        void onSuccess(T result);
        void onError(Exception e);
    }

    void getMangaPages(String url, Callback<List<String>> callback);
    void getVideoUrl(String url, Callback<String> callback);
}
