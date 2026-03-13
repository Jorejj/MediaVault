package com.example.mediavault.api;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class TvDetailResponse {

    @SerializedName("created_by")
    private List<Creator> createdBy;

    @SerializedName("number_of_episodes")
    private Integer numberOfEpisodes;

    @SerializedName("genres")
    private List<Genre> genres;

    public List<Creator> getCreatedBy() {
        return createdBy;
    }

    public Integer getNumberOfEpisodes() {
        return numberOfEpisodes;
    }

    public List<Genre> getGenres() {
        return genres;
    }

    public static class Creator {
        @SerializedName("name")
        private String name;

        public String getName() {
            return name;
        }
    }

    public static class Genre {
        @SerializedName("name")
        private String name;

        public String getName() {
            return name;
        }
    }

    /**
     * Helper method to return a clean, comma-separated string 
     * of creators (e.g., "Vince Gilligan, Peter Gould")
     */
    public String getDisplayCreators() {
        if (createdBy == null || createdBy.isEmpty()) {
            return "Unknown Creator";
        }
        
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < createdBy.size(); i++) {
            sb.append(createdBy.get(i).getName());
            if (i < createdBy.size() - 1) {
                sb.append(", ");
            }
        }
        return sb.toString();
    }
}
