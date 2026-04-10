package com.example.mediavault.api.consumet;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * Response model for Consumet API streaming links endpoint.
 * Example: GET /anime/gogoanime/watch/{episodeId}
 */
public class ConsumetStreamResponse {
    @SerializedName("headers")
    private Headers headers;

    @SerializedName("sources")
    private List<Source> sources;

    @SerializedName("download")
    private String download;

    public Headers getHeaders() {
        return headers;
    }

    public List<Source> getSources() {
        return sources;
    }

    public String getDownload() {
        return download;
    }

    public static class Headers {
        @SerializedName("Referer")
        private String referer;

        public String getReferer() {
            return referer;
        }
    }

    public static class Source {
        @SerializedName("url")
        private String url;

        @SerializedName("quality")
        private String quality;

        @SerializedName("isM3U8")
        private boolean isM3U8;

        public String getUrl() {
            return url;
        }

        public String getQuality() {
            return quality;
        }

        public boolean isM3U8() {
            return isM3U8;
        }
    }
}
