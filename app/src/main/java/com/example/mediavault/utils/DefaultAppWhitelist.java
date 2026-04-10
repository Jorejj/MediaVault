package com.example.mediavault.utils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Utility class that provides the default whitelist of package names 
 * for the AccessibilityService to monitor.
 */
public class DefaultAppWhitelist {
    private static final Set<String> YOUTUBE_PACKAGES = new HashSet<>(Arrays.asList(
            "com.google.android.youtube",
            "com.vanced.android.youtube",
            "app.rvx.android.youtube",
            "com.google.android.apps.youtube.kids",
            "com.google.android.apps.youtube.music"
    ));

    private static final Set<String> BILIBILI_PACKAGES = new HashSet<>(Arrays.asList(
            "com.bstar.intl",
            "tv.danmaku.bili",
            "com.bilibili.app.in",
            "com.bilibili.app.blue"
    ));

    private static final Set<String> CORE_RECOMMENDED_PACKAGES = new HashSet<>(Arrays.asList(
            "com.android.chrome",
            "org.mozilla.firefox",
            "com.brave.browser",
            "app.mihon",
            "app.mihon.foss",
            "eu.kanade.tachiyomi",
            "org.koitharu.kotatsu",
            "com.qidian.Int.reader",
            "com.wattpad.android",
            "com.amazon.kindle",
            "com.crunchyroll.crunchyroid",
            "com.netflix.mediaclient",
            "org.videolan.vlc",
            "com.mxtech.videoplayer.ad",
            "com.mxtech.videoplayer.pro"
    ));

    /**
     * Returns a HashSet of package names for external readers, browsers, and media players.
     * Logically grouped for easy maintenance.
     *
     * @return HashSet of whitelisted package names.
     */
    public static HashSet<String> get() {
        HashSet<String> whitelist = new HashSet<>();

        // Browsers
        whitelist.addAll(Arrays.asList(
            "com.android.chrome",
            "org.mozilla.firefox",
            "com.brave.browser",
            "com.kiwibrowser.browser",
            "com.sec.android.app.sbrowser",
            "com.opera.browser",
            "com.duckduckgo.mobile.android"
        ));

        // Manga/Comics
        whitelist.addAll(Arrays.asList(
            "app.mihon",
            "app.mihon.foss",
            "eu.kanade.tachiyomi",
            "org.koitharu.kotatsu",
            "xyz.jmir.tachiyomi.mi",
            "com.naver.linewebtoon",
            "com.tapastic",
            "jp.co.shueisha.mangaplus",
            "com.contentsfirst.tappytoon"
        ));

        // Novels
        whitelist.addAll(Arrays.asList(
            "com.qidian.Int.reader",
            "com.flyersoft.moonreader",
            "com.flyersoft.moonreaderp",
            "com.wattpad.android",
            "com.rajarsheechatterjee.LNReader",
            "com.readera",
            "com.readera.premium",
            "com.amazon.kindle"
        ));

        // Media Players
        whitelist.addAll(Arrays.asList(
            "com.crunchyroll.crunchyroid",
            "com.netflix.mediaclient",
            "org.videolan.vlc",
            "com.mxtech.videoplayer.ad",
            "com.mxtech.videoplayer.pro"
        ));
        whitelist.addAll(YOUTUBE_PACKAGES);
        whitelist.addAll(BILIBILI_PACKAGES);

        return whitelist;
    }

    public static Set<String> getBilibiliPackages() {
        return new HashSet<>(BILIBILI_PACKAGES);
    }

    public static Set<String> getYoutubePackages() {
        return new HashSet<>(YOUTUBE_PACKAGES);
    }

    public static Set<String> getCoreRecommendedPackages() {
        HashSet<String> recommended = new HashSet<>(CORE_RECOMMENDED_PACKAGES);
        recommended.addAll(YOUTUBE_PACKAGES);
        recommended.addAll(BILIBILI_PACKAGES);
        return recommended;
    }
}
