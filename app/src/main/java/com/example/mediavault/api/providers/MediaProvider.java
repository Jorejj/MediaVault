package com.example.mediavault.api.providers;

import java.util.List;

/**
 * Common interface for all media streaming/reading providers.
 * Implementations must handle their own API calls and error handling.
 * 
 * All methods are SYNCHRONOUS and MUST be called from a background thread.
 */
public interface MediaProvider {
    
    /**
     * Get the provider's display name (e.g., "Consumet", "MangaDex", "VidSrc")
     */
    String getProviderName();
    
    /**
     * Check if this provider is currently available.
     * Should make a quick health check call if possible.
     * 
     * @return true if provider is reachable, false otherwise
     */
    boolean isAvailable();
    
    /**
     * Supported media types by this provider
     */
    enum MediaType {
        ANIME,
        MANGA,
        MOVIE,
        TV_SHOW,
        NOVEL
    }
    
    /**
     * Check if this provider supports a specific media type
     */
    boolean supportsMediaType(MediaType type);
    
    // ============= ANIME =============
    
    /**
     * Resolve streaming URL for an anime episode.
     * 
     * @param title Anime title
     * @param episode Episode number
     * @return Streaming URL (.m3u8/.mp4), or null if failed
     */
    default String resolveAnimeEpisode(String title, int episode) {
        return null;
    }
    
    /**
     * Fetch list of episodes for an anime.
     * 
     * @param title Anime title
     * @return List of episode info, or empty list if failed
     */
    default List<EpisodeInfo> fetchAnimeEpisodes(String title) {
        return java.util.Collections.emptyList();
    }
    
    // ============= MANGA =============
    
    /**
     * Resolve page URLs for a manga chapter.
     * 
     * @param title Manga title
     * @param chapter Chapter number
     * @return List of image URLs, or null if failed
     */
    default List<String> resolveMangaChapter(String title, int chapter) {
        return null;
    }
    
    /**
     * Fetch list of chapters for a manga.
     * 
     * @param title Manga title
     * @return List of chapter info, or empty list if failed
     */
    default List<ChapterInfo> fetchMangaChapters(String title) {
        return java.util.Collections.emptyList();
    }
    
    // ============= MOVIE =============
    
    /**
     * Resolve streaming URL for a movie.
     * 
     * @param title Movie title
     * @param year Release year (0 to ignore)
     * @param tmdbId TMDB ID if available (null to ignore)
     * @return Streaming URL or embed URL, or null if failed
     */
    default String resolveMovie(String title, int year, String tmdbId) {
        return null;
    }
    
    // ============= TV SHOW =============
    
    /**
     * Resolve streaming URL for a TV episode.
     * 
     * @param title Show title
     * @param season Season number
     * @param episode Episode number
     * @param tmdbId TMDB ID if available (null to ignore)
     * @return Streaming URL or embed URL, or null if failed
     */
    default String resolveTvEpisode(String title, int season, int episode, String tmdbId) {
        return null;
    }
    
    // ============= HELPER CLASSES =============
    
    /**
     * Basic episode information
     */
    class EpisodeInfo {
        public final String id;
        public final int number;
        public final String title;
        public final String url;
        
        public EpisodeInfo(String id, int number, String title, String url) {
            this.id = id;
            this.number = number;
            this.title = title;
            this.url = url;
        }
    }
    
    /**
     * Basic chapter information
     */
    class ChapterInfo {
        public final String id;
        public final float number;
        public final String title;
        public final int pages;
        
        public ChapterInfo(String id, float number, String title, int pages) {
            this.id = id;
            this.number = number;
            this.title = title;
            this.pages = pages;
        }
    }
}
