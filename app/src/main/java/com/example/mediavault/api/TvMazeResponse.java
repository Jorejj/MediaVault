package com.example.mediavault.api;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class TvMazeResponse {
    @SerializedName("show")
    private TvShow show;

    public TvShow getShow() {
        return show;
    }

    public static class TvShow {
        @SerializedName("name")
        private String name;

        @SerializedName("genres")
        private List<String> genres;

        @SerializedName("summary")
        private String summary;

        @SerializedName("image")
        private TvMazeImage image;

        public String getName() {
            return name;
        }

        public List<String> getGenres() {
            return genres;
        }

        public String getSummary() {
            return summary;
        }

        public TvMazeImage getImage() {
            return image;
        }
    }

    public static class TvMazeImage {
        @SerializedName("original")
        private String original;

        public String getOriginal() {
            return original;
        }
    }
}
