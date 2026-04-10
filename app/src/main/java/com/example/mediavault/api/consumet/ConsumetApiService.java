package com.example.mediavault.api.consumet;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * Retrofit API interface for Consumet API.
 * Base URL: https://api.consumet.org
 * 
 * Documentation: https://docs.consumet.org
 * Supports: Anime, Manga, Movies, TV Shows
 */
public interface ConsumetApiService {
    
    // ============= ANIME (GogoAnime) =============
    
    /**
     * Search for anime by title.
     * Example: /anime/gogoanime/one-piece
     */
    @GET("anime/gogoanime/{query}")
    Call<ConsumetSearchResponse> searchAnime(@Path("query") String query);

    /**
     * Get streaming links for a specific episode.
     * Example: /anime/gogoanime/watch/one-piece-episode-1
     */
    @GET("anime/gogoanime/watch/{episodeId}")
    Call<ConsumetStreamResponse> getStreamingLinks(@Path("episodeId") String episodeId);

    /**
     * Get anime info including episode list.
     * Example: /anime/gogoanime/info/{id}
     */
    @GET("anime/gogoanime/info/{id}")
    Call<ConsumetAnimeInfoResponse> getAnimeInfo(@Path("id") String animeId);

    /**
     * Search with pagination support.
     */
    @GET("anime/gogoanime/{query}")
    Call<ConsumetSearchResponse> searchAnimeWithPage(
            @Path("query") String query,
            @Query("page") int page
    );

    // ============= MANGA (MangaDex) =============
    
    /**
     * Search for manga by title.
     * Example: /manga/mangadex/one-piece
     */
    @GET("manga/mangadex/{query}")
    Call<ConsumetMangaSearchResponse> searchManga(@Path("query") String query);

    /**
     * Get manga info including chapters list.
     * Example: /manga/mangadex/info/{id}
     */
    @GET("manga/mangadex/info/{id}")
    Call<ConsumetMangaInfoResponse> getMangaInfo(@Path("id") String mangaId);

    /**
     * Get chapter pages (images).
     * Example: /manga/mangadex/read/{chapterId}
     */
    @GET("manga/mangadex/read/{chapterId}")
    Call<ConsumetMangaChapterResponse> getMangaChapter(@Path("chapterId") String chapterId);

    // ============= MOVIES & TV SHOWS (FlixHQ) =============
    
    /**
     * Search for movies or TV shows.
     * Example: /movies/flixhq/breaking-bad
     */
    @GET("movies/flixhq/{query}")
    Call<ConsumetMovieSearchResponse> searchMovies(@Path("query") String query);

    /**
     * Get movie/TV show info including episodes.
     * Example: /movies/flixhq/info/{id}
     */
    @GET("movies/flixhq/info/{id}")
    Call<ConsumetMovieInfoResponse> getMovieInfo(@Path("id") String mediaId);

    /**
     * Get streaming links for movie or TV episode.
     * Example: /movies/flixhq/watch/{episodeId}
     */
    @GET("movies/flixhq/watch/{episodeId}")
    Call<ConsumetStreamResponse> getMovieStreamingLinks(@Path("episodeId") String episodeId);
}
