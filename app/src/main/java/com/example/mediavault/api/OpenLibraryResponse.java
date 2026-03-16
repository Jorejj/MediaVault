package com.example.mediavault.api;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class OpenLibraryResponse {
    @SerializedName("docs")
    private List<Doc> docs;

    public List<Doc> getDocs() {
        return docs;
    }

    public static class Doc {
        @SerializedName("title")
        private String title;

        @SerializedName("author_name")
        private List<String> authorName;

        @SerializedName("cover_i")
        private Integer coverI;

        @SerializedName("subject")
        private List<String> subject;

        public String getTitle() {
            return title;
        }

        public List<String> getAuthorName() {
            return authorName;
        }

        public Integer getCoverI() {
            return coverI;
        }

        public List<String> getSubject() {
            return subject;
        }

        public String getCoverUrl() {
            if (coverI != null) {
                return "https://covers.openlibrary.org/b/id/" + coverI + "-L.jpg";
            }
            return null;
        }
    }
}
