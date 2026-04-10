package com.example.mediavault.api.consumet;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.example.mediavault.api.providers.MediaProvider;
import com.example.mediavault.api.providers.ProviderManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Factory for creating media resolvers based on media type.
 * Provides a unified interface for resolving streaming/reading URLs across different media types.
 * 
 * NOW USES ProviderManager for automatic fallback between providers.
 * 
 * All methods MUST be called from background thread (AppExecutor.networkIO()).
 */
public class ResolverFactory {
    private static final String TAG = "ResolverFactory";
    private static final String PROVIDER_PREFS = "provider_preferences";
    private static final String KEY_PREF_ANIME = "preferred_provider_anime";
    private static final String KEY_PREF_MANGA = "preferred_provider_manga";
    private static final String KEY_PREF_MOVIE = "preferred_provider_movie";
    private static final String KEY_PREF_TV = "preferred_provider_tv";
    
    // Callback for resolution status updates
    private static ProviderManager.ProviderStatusCallback statusCallback;
    
    /**
     * Set a callback to receive provider status updates during resolution.
     * Call with null to clear the callback.
     */
    public static void setStatusCallback(ProviderManager.ProviderStatusCallback callback) {
        statusCallback = callback;
        ProviderManager.getInstance().setStatusCallback(callback);
    }

    public static void applyProviderPreferences(Context context) {
        if (context == null) return;
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PROVIDER_PREFS, Context.MODE_PRIVATE);
        ProviderManager manager = ProviderManager.getInstance();
        manager.setPreferredProvider(MediaProvider.MediaType.ANIME, prefs.getString(KEY_PREF_ANIME, null));
        manager.setPreferredProvider(MediaProvider.MediaType.MANGA, prefs.getString(KEY_PREF_MANGA, null));
        manager.setPreferredProvider(MediaProvider.MediaType.MOVIE, prefs.getString(KEY_PREF_MOVIE, null));
        manager.setPreferredProvider(MediaProvider.MediaType.TV_SHOW, prefs.getString(KEY_PREF_TV, null));
    }

    public static void savePreferredProvider(Context context, MediaProvider.MediaType type, String providerName) {
        if (context == null || type == null) return;
        String key = getPreferenceKey(type);
        if (key == null) return;

        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PROVIDER_PREFS, Context.MODE_PRIVATE);
        if (providerName == null || providerName.trim().isEmpty()) {
            prefs.edit().remove(key).apply();
        } else {
            prefs.edit().putString(key, providerName.trim()).apply();
        }
        ProviderManager.getInstance().setPreferredProvider(type, providerName);
    }

    public static String getPreferredProvider(Context context, MediaProvider.MediaType type) {
        if (context == null || type == null) return null;
        String key = getPreferenceKey(type);
        if (key == null) return null;
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PROVIDER_PREFS, Context.MODE_PRIVATE);
        return prefs.getString(key, null);
    }

    public static List<String> getAvailableProvidersForType(MediaProvider.MediaType type) {
        if (type == null) return new ArrayList<>();
        return ProviderManager.getInstance().getProviderNames(type);
    }

    /**
     * Applies provider priority for the current runtime session only (not persisted).
     */
    public static void setPreferredProviderForSession(MediaProvider.MediaType type, String providerName) {
        if (type == null) return;
        ProviderManager.getInstance().setPreferredProvider(type, providerName);
    }
    
    // ============= ANIME =============
    
    /**
     * Resolves streaming URL for an anime episode.
     * Uses ProviderManager for automatic fallback (Consumet → Jikan+GogoAnime).
     * 
     * MUST be called from a background thread.
     * 
     * @param animeTitle The anime title (e.g., "One Piece")
     * @param episodeNumber The episode number (e.g., 1)
     * @return Streaming URL (.m3u8 or .mp4), or null if all providers fail
     */
    public static String resolveAnime(String animeTitle, int episodeNumber) {
        if (animeTitle == null || animeTitle.trim().isEmpty()) {
            Log.e(TAG, "Cannot resolve: invalid anime title");
            return null;
        }
        
        if (episodeNumber < 1) {
            Log.e(TAG, "Cannot resolve: invalid episode number (" + episodeNumber + ")");
            return null;
        }
        
        try {
            Log.d(TAG, "Resolving anime via ProviderManager: " + animeTitle + " Episode " + episodeNumber);
            ProviderManager.ProviderResult result = ProviderManager.getInstance()
                .resolveAnimeEpisode(animeTitle, episodeNumber);
            
            if (result != null) {
                Log.d(TAG, "Resolved via " + result.providerName + ": " + result.url);
                return result.url;
            }
            
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Failed to resolve anime streaming URL", e);
            return null;
        }
    }
    
    /**
     * Resolves anime with extended result including provider name.
     * Use this when you need to display which provider succeeded.
     */
    public static ProviderManager.ProviderResult resolveAnimeWithProvider(String animeTitle, int episodeNumber) {
        if (animeTitle == null || animeTitle.trim().isEmpty() || episodeNumber < 1) {
            return null;
        }
        
        return ProviderManager.getInstance().resolveAnimeEpisode(animeTitle, episodeNumber);
    }
    
    /**
     * Resolves streaming URL using a pre-constructed episode ID.
     * Note: This only works with Consumet, no fallback.
     * 
     * @param episodeId Full episode ID (e.g., "one-piece-episode-1000")
     * @return Streaming URL, or null if resolution fails
     */
    public static String resolveAnimeByEpisodeId(String episodeId) {
        if (episodeId == null || episodeId.trim().isEmpty()) {
            Log.e(TAG, "Cannot resolve: invalid episode ID");
            return null;
        }
        
        try {
            Log.d(TAG, "Resolving episode ID: " + episodeId);
            AnimeResolver resolver = new AnimeResolver();
            return resolver.resolveByEpisodeId(episodeId);
        } catch (Exception e) {
            Log.e(TAG, "Failed to resolve episode ID", e);
            return null;
        }
    }

    /**
     * Fetches anime episodes list for selector screens.
     * Uses ProviderManager for automatic fallback.
     */
    public static List<MediaProvider.EpisodeInfo> fetchAnimeEpisodes(String animeTitle) {
        if (animeTitle == null || animeTitle.trim().isEmpty()) {
            return new ArrayList<>();
        }
        try {
            ProviderManager.ProviderListResult<MediaProvider.EpisodeInfo> result = 
                ProviderManager.getInstance().fetchAnimeEpisodes(animeTitle);
            return result.items;
        } catch (Exception e) {
            Log.e(TAG, "Failed to fetch anime episodes", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Fetches anime episodes using Consumet directly (for compatibility).
     * @deprecated Use fetchAnimeEpisodes() instead for fallback support.
     */
    @Deprecated
    public static List<ConsumetAnimeInfoResponse.Episode> fetchAnimeEpisodesConsument(String animeTitle) {
        if (animeTitle == null || animeTitle.trim().isEmpty()) {
            return new ArrayList<>();
        }
        try {
            AnimeResolver resolver = new AnimeResolver();
            return resolver.fetchEpisodes(animeTitle);
        } catch (Exception e) {
            Log.e(TAG, "Failed to fetch anime episodes", e);
            return new ArrayList<>();
        }
    }
    
    // ============= MANGA =============
    
    /**
     * Resolves page image URLs for a manga chapter.
     * Uses ProviderManager for automatic fallback (Consumet → MangaDex Direct).
     * 
     * MUST be called from a background thread.
     * 
     * @param mangaTitle The manga title (e.g., "One Piece")
     * @param chapterNumber The chapter number (e.g., 1)
     * @return List of image URLs for the chapter, or null if all providers fail
     */
    public static List<String> resolveManga(String mangaTitle, int chapterNumber) {
        if (mangaTitle == null || mangaTitle.trim().isEmpty()) {
            Log.e(TAG, "Cannot resolve: invalid manga title");
            return null;
        }
        
        if (chapterNumber < 1) {
            Log.e(TAG, "Cannot resolve: invalid chapter number (" + chapterNumber + ")");
            return null;
        }
        
        try {
            Log.d(TAG, "Resolving manga via ProviderManager: " + mangaTitle + " Chapter " + chapterNumber);
            ProviderManager.ProviderListResult<String> result = ProviderManager.getInstance()
                .resolveMangaChapter(mangaTitle, chapterNumber);
            
            if (result != null) {
                Log.d(TAG, "Resolved " + result.items.size() + " pages via " + result.providerName);
                return result.items;
            }
            
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Failed to resolve manga chapter", e);
            return null;
        }
    }

    /**
     * Fetches manga chapter list for selector screens.
     * Uses ProviderManager for automatic fallback.
     */
    public static List<MediaProvider.ChapterInfo> fetchMangaChapters(String mangaTitle) {
        if (mangaTitle == null || mangaTitle.trim().isEmpty()) {
            return new ArrayList<>();
        }
        try {
            ProviderManager.ProviderListResult<MediaProvider.ChapterInfo> result = 
                ProviderManager.getInstance().fetchMangaChapters(mangaTitle);
            return result.items;
        } catch (Exception e) {
            Log.e(TAG, "Failed to fetch manga chapters", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * Fetches manga chapters using Consumet directly (for compatibility).
     * @deprecated Use fetchMangaChapters() instead for fallback support.
     */
    @Deprecated
    public static List<ConsumetMangaInfoResponse.Chapter> fetchMangaChaptersConsumet(String mangaTitle) {
        if (mangaTitle == null || mangaTitle.trim().isEmpty()) {
            return new ArrayList<>();
        }
        try {
            MangaResolver resolver = new MangaResolver();
            return resolver.fetchChapters(mangaTitle);
        } catch (Exception e) {
            Log.e(TAG, "Failed to fetch manga chapters", e);
            return new ArrayList<>();
        }
    }
    
    // ============= MOVIES =============
    
    /**
     * Resolves streaming URL for a movie.
     * Uses ProviderManager for automatic fallback (Consumet → VidSrc).
     * 
     * MUST be called from a background thread.
     * 
     * @param movieTitle The movie title (e.g., "Inception")
     * @param year Optional release year for better matching (e.g., 2010), pass 0 to ignore
     * @return Streaming URL (.m3u8 or .mp4), or null if all providers fail
     */
    public static String resolveMovie(String movieTitle, int year) {
        return resolveMovie(movieTitle, year, null);
    }
    
    /**
     * Resolves streaming URL for a movie with TMDB ID.
     * 
     * @param movieTitle The movie title
     * @param year Optional release year (pass 0 to ignore)
     * @param tmdbId Optional TMDB ID for accurate matching
     * @return Streaming URL, or null if all providers fail
     */
    public static String resolveMovie(String movieTitle, int year, String tmdbId) {
        if (movieTitle == null || movieTitle.trim().isEmpty()) {
            Log.e(TAG, "Cannot resolve: invalid movie title");
            return null;
        }
        
        try {
            Log.d(TAG, "Resolving movie via ProviderManager: " + movieTitle + (year > 0 ? " (" + year + ")" : ""));
            ProviderManager.ProviderResult result = ProviderManager.getInstance()
                .resolveMovie(movieTitle, year, tmdbId);
            
            if (result != null) {
                Log.d(TAG, "Resolved via " + result.providerName + ": " + result.url);
                return result.url;
            }
            
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Failed to resolve movie streaming URL", e);
            return null;
        }
    }
    
    // ============= TV SHOWS =============
    
    /**
     * Resolves streaming URL for a TV show episode.
     * Uses ProviderManager for automatic fallback (Consumet → VidSrc).
     * 
     * MUST be called from a background thread.
     * 
     * @param showTitle The TV show title (e.g., "Breaking Bad")
     * @param season Season number (e.g., 1)
     * @param episode Episode number (e.g., 1)
     * @return Streaming URL (.m3u8 or .mp4), or null if all providers fail
     */
    public static String resolveTvShow(String showTitle, int season, int episode) {
        return resolveTvShow(showTitle, season, episode, null);
    }
    
    /**
     * Resolves streaming URL for a TV show episode with TMDB ID.
     * 
     * @param showTitle The TV show title
     * @param season Season number
     * @param episode Episode number
     * @param tmdbId Optional TMDB ID for accurate matching
     * @return Streaming URL, or null if all providers fail
     */
    public static String resolveTvShow(String showTitle, int season, int episode, String tmdbId) {
        if (showTitle == null || showTitle.trim().isEmpty()) {
            Log.e(TAG, "Cannot resolve: invalid TV show title");
            return null;
        }
        
        if (season < 1 || episode < 1) {
            Log.e(TAG, "Cannot resolve: invalid season/episode numbers");
            return null;
        }
        
        try {
            Log.d(TAG, "Resolving TV show via ProviderManager: " + showTitle + " S" + season + "E" + episode);
            ProviderManager.ProviderResult result = ProviderManager.getInstance()
                .resolveTvEpisode(showTitle, season, episode, tmdbId);
            
            if (result != null) {
                Log.d(TAG, "Resolved via " + result.providerName + ": " + result.url);
                return result.url;
            }
            
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Failed to resolve TV episode streaming URL", e);
            return null;
        }
    }
    
    /**
     * Get the name of the last successful provider for a given media type.
     * Useful for displaying to users.
     */
    public static String getLastSuccessfulAnimeProvider() {
        return ProviderManager.getInstance().getLastSuccessfulAnimeProvider();
    }
    
    public static String getLastSuccessfulMangaProvider() {
        return ProviderManager.getInstance().getLastSuccessfulMangaProvider();
    }

    private static String getPreferenceKey(MediaProvider.MediaType type) {
        if (type == MediaProvider.MediaType.ANIME) return KEY_PREF_ANIME;
        if (type == MediaProvider.MediaType.MANGA) return KEY_PREF_MANGA;
        if (type == MediaProvider.MediaType.MOVIE) return KEY_PREF_MOVIE;
        if (type == MediaProvider.MediaType.TV_SHOW) return KEY_PREF_TV;
        return null;
    }
}
