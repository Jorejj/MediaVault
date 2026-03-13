package com.example.mediavault.api;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class MovieDetailResponse {

    @SerializedName("runtime")
    private Integer runtime;

    @SerializedName("genres")
    private List<Genre> genres;

    @SerializedName("credits")
    private Credits credits;

    public Integer getRuntime() {
        return runtime;
    }

    public List<Genre> getGenres() {
        return genres;
    }

    public Credits getCredits() {
        return credits;
    }

    public static class Genre {
        @SerializedName("name")
        private String name;

        public String getName() {
            return name;
        }
    }

    public static class Credits {
        @SerializedName("crew")
        private List<CrewMember> crew;

        public List<CrewMember> getCrew() {
            return crew;
        }
    }

    public static class CrewMember {
        @SerializedName("name")
        private String name;

        @SerializedName("job")
        private String job;

        public String getName() {
            return name;
        }

        public String getJob() {
            return job;
        }
    }

    /**
     * Helper method to find the Director's name from the crew list.
     */
    public String getDirector() {
        if (credits != null && credits.getCrew() != null) {
            for (CrewMember member : credits.getCrew()) {
                if ("Director".equalsIgnoreCase(member.getJob())) {
                    return member.getName();
                }
            }
        }
        return "Unknown Director";
    }
}
