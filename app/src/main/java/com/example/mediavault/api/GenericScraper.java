package com.example.mediavault.api;

import com.example.mediavault.AppExecutor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class GenericScraper implements MediaSource {
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    @Override
    public void getMangaPages(String url, Callback<List<String>> callback) {
        AppExecutor.getInstance().networkIO().execute(() -> {
            try {
                Document doc = Jsoup.connect(url)
                        .userAgent(USER_AGENT)
                        .timeout(15000)
                        .get();
                
                List<String> images = new ArrayList<>();
                // Common selectors for manga readers (img inside a container)
                Elements imgTags = doc.select("img[src~=(?i)\\.(png|jpe?g|webp)]");
                
                for (Element img : imgTags) {
                    String src = img.absUrl("src");
                    if (!src.isEmpty() && !src.contains("logo") && !src.contains("icon")) {
                        images.add(src);
                    }
                }

                AppExecutor.getInstance().mainThread().execute(() -> callback.onSuccess(images));
            } catch (IOException e) {
                AppExecutor.getInstance().mainThread().execute(() -> callback.onError(e));
            }
        });
    }

    @Override
    public void getVideoUrl(String url, Callback<String> callback) {
        AppExecutor.getInstance().networkIO().execute(() -> {
            try {
                Document doc = Jsoup.connect(url)
                        .userAgent(USER_AGENT)
                        .timeout(15000)
                        .get();

                // Look for common video/source tags
                Element videoSource = doc.selectFirst("video source[src~=(?i)\\.(mp4|m3u8)]");
                String videoUrl = videoSource != null ? videoSource.absUrl("src") : "";

                if (videoUrl.isEmpty()) {
                    // Fallback to searching scripts for .m3u8 or .mp4 links
                    Elements scripts = doc.select("script");
                    for (Element script : scripts) {
                        String data = script.data();
                        if (data.contains(".m3u8") || data.contains(".mp4")) {
                            // Simple regex extraction (Phase 4 will improve this)
                            java.util.regex.Matcher m = java.util.regex.Pattern.compile("(https?://[^\\s\"']+?\\.(?:m3u8|mp4))").matcher(data);
                            if (m.find()) {
                                videoUrl = m.group(1);
                                break;
                            }
                        }
                    }
                }

                final String finalUrl = videoUrl;
                AppExecutor.getInstance().mainThread().execute(() -> {
                    if (!finalUrl.isEmpty()) {
                        callback.onSuccess(finalUrl);
                    } else {
                        callback.onError(new Exception("No video source found"));
                    }
                });
            } catch (IOException e) {
                AppExecutor.getInstance().mainThread().execute(() -> callback.onError(e));
            }
        });
    }
}
