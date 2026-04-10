package com.example.mediavault.api.providers;

import android.util.Log;

import com.example.mediavault.api.consumet.AnimeResolver;
import com.example.mediavault.api.consumet.ConsumetAnimeInfoResponse;
import com.example.mediavault.api.consumet.ConsumetMangaInfoResponse;
import com.example.mediavault.api.consumet.MangaResolver;
import com.example.mediavault.api.consumet.MovieResolver;
import com.example.mediavault.api.consumet.TvShowResolver;

import java.util.ArrayList;
import java.util.List;

/**
 * Consumet API provider implementation.
 * Wraps existing resolver classes into the MediaProvider interface.
 * 
 * Supports: Anime (GogoAnime), Manga (MangaDex), Movies/TV (FlixHQ)
 */
public class ConsumetProvider implements MediaProvider {
    private static final String TAG = "ConsumetProvider";
    private static final String PROVIDER_NAME = "Consumet";
    
    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }
    
    @Override
    public boolean isAvailable() {
        // Quick availability check - try to construct resolvers
        // If Consumet is down, actual calls will fail, which is caught by ProviderManager
        return true;
    }
    
    @Override
    public boolean supportsMediaType(MediaType type) {
        return type == MediaType.ANIME || 
               type == MediaType.MANGA || 
               type == MediaType.MOVIE || 
               type == MediaType.TV_SHOW;
    }
    
    // ============= ANIME =============
    
    @Override
    public String resolveAnimeEpisode(String title, int episode) {
        Log.d(TAG, "Resolving anime via Consumet: " + title + " Ep " + episode);
        try {
            AnimeResolver resolver = new AnimeResolver();
            return resolver.resolveStreamingUrl(title, episode);
        } catch (Exception e) {
            Log.e(TAG, "Consumet anime resolution failed", e);
            return null;
        }
    }
    
    @Override
    public List<EpisodeInfo> fetchAnimeEpisodes(String title) {
        Log.d(TAG, "Fetching anime episodes via Consumet: " + title);
        try {
            AnimeResolver resolver = new AnimeResolver();
            List<ConsumetAnimeInfoResponse.Episode> episodes = resolver.fetchEpisodes(title);
            
            if (episodes == null) {
                return new ArrayList<>();
            }
            
            List<EpisodeInfo> result = new ArrayList<>();
            for (ConsumetAnimeInfoResponse.Episode ep : episodes) {
                result.add(new EpisodeInfo(
                    ep.getId(),
                    ep.getEpisodeNumber(),
                    ep.getTitle(),
                    ep.getUrl()
                ));
            }
            return result;
        } catch (Exception e) {
            Log.e(TAG, "Consumet episode fetch failed", e);
            return new ArrayList<>();
        }
    }
    
    // ============= MANGA =============
    
    @Override
    public List<String> resolveMangaChapter(String title, int chapter) {
        Log.d(TAG, "Resolving manga via Consumet: " + title + " Ch " + chapter);
        try {
            MangaResolver resolver = new MangaResolver();
            return resolver.resolveChapterPages(title, chapter);
        } catch (Exception e) {
            Log.e(TAG, "Consumet manga resolution failed", e);
            return null;
        }
    }
    
    @Override
    public List<ChapterInfo> fetchMangaChapters(String title) {
        Log.d(TAG, "Fetching manga chapters via Consumet: " + title);
        try {
            MangaResolver resolver = new MangaResolver();
            List<ConsumetMangaInfoResponse.Chapter> chapters = resolver.fetchChapters(title);
            
            if (chapters == null) {
                return new ArrayList<>();
            }
            
            List<ChapterInfo> result = new ArrayList<>();
            for (ConsumetMangaInfoResponse.Chapter ch : chapters) {
                float chapterNum = 0;
                try {
                    chapterNum = Float.parseFloat(ch.getChapterNumber());
                } catch (NumberFormatException ignored) {}
                
                result.add(new ChapterInfo(
                    ch.getId(),
                    chapterNum,
                    ch.getTitle(),
                    ch.getPages()
                ));
            }
            return result;
        } catch (Exception e) {
            Log.e(TAG, "Consumet chapter fetch failed", e);
            return new ArrayList<>();
        }
    }
    
    // ============= MOVIE =============
    
    @Override
    public String resolveMovie(String title, int year, String tmdbId) {
        Log.d(TAG, "Resolving movie via Consumet: " + title);
        try {
            MovieResolver resolver = new MovieResolver();
            return resolver.resolveMovie(title, year);
        } catch (Exception e) {
            Log.e(TAG, "Consumet movie resolution failed", e);
            return null;
        }
    }
    
    // ============= TV SHOW =============
    
    @Override
    public String resolveTvEpisode(String title, int season, int episode, String tmdbId) {
        Log.d(TAG, "Resolving TV via Consumet: " + title + " S" + season + "E" + episode);
        try {
            TvShowResolver resolver = new TvShowResolver();
            return resolver.resolveTvEpisode(title, season, episode);
        } catch (Exception e) {
            Log.e(TAG, "Consumet TV resolution failed", e);
            return null;
        }
    }
}
