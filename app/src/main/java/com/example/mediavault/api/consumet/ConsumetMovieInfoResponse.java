package com.example.mediavault.api.consumet;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * Response model for Consumet movie/TV info endpoint.
 * Example: GET /movies/flixhq/info/{id}
 */
public class ConsumetMovieInfoResponse {
    @SerializedName("id")
    private String id;

    @SerializedName("title")
    private String title;

    @SerializedName("image")
    private String image;

    @SerializedName("description")
    private String description;

    @SerializedName("type")
    private String type; // "Movie" or "TV Series"

    @SerializedName("releaseDate")
    private String releaseDate;

    @SerializedName("episodes")
    private List<Episode> episodes;

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getImage() {
        return image;
    }

    public String getDescription() {
        return description;
    }

    public String getType() {
        return type;
    }

    public String getReleaseDate() {
        return releaseDate;
    }

    public List<Episode> getEpisodes() {
        return episodes;
    }

    public static class Episode {
        @SerializedName("id")
        private String id;

        @SerializedName("title")
        private String title;

        @SerializedName("number")
        private int number;

        @SerializedName("season")
        private int season;

        @SerializedName("url")
        private String url;

        public String getId() {
            return id;
        }

        public String getTitle() {
            return title;
        }

        public int getNumber() {
            return number;
        }

        public int getSeason() {
            return season;
        }

        public String getUrl() {
            return url;
        }
    }
}
