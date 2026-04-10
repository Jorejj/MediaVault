package com.example.mediavault.api.providers;

import android.util.Log;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages multiple media providers with automatic fallback.
 * Tries providers in priority order until one succeeds.
 * 
 * All resolution methods are SYNCHRONOUS and MUST be called from a background thread.
 */
public class ProviderManager {
    private static final String TAG = "ProviderManager";
    
    private static ProviderManager instance;
    
    // Providers by type (ordered by priority)
    private final List<MediaProvider> animeProviders = new CopyOnWriteArrayList<>();
    private final List<MediaProvider> mangaProviders = new CopyOnWriteArrayList<>();
    private final List<MediaProvider> movieProviders = new CopyOnWriteArrayList<>();
    private final List<MediaProvider> tvProviders = new CopyOnWriteArrayList<>();
    
    // Track last successful provider for each type
    private volatile String lastSuccessfulAnimeProvider;
    private volatile String lastSuccessfulMangaProvider;
    private volatile String lastSuccessfulMovieProvider;
    private volatile String lastSuccessfulTvProvider;

    private volatile String preferredAnimeProvider;
    private volatile String preferredMangaProvider;
    private volatile String preferredMovieProvider;
    private volatile String preferredTvProvider;
    
    // Callback for UI updates
    public interface ProviderStatusCallback {
        void onProviderTrying(String providerName);
        void onProviderSuccess(String providerName);
        void onProviderFailed(String providerName, String reason);
    }
    
    private ProviderStatusCallback statusCallback;
    
    public static synchronized ProviderManager getInstance() {
        if (instance == null) {
            instance = new ProviderManager();
        }
        return instance;
    }
    
    private ProviderManager() {
        // Initialize default providers
        initializeDefaultProviders();
    }
    
    /**
     * Initialize all default providers in priority order.
     * NOTE: Consumet API is currently down (DMCA takedown), so we skip it and use fallbacks only.
     */
    private void initializeDefaultProviders() {
        // Create providers
        // ConsumetProvider consumetProvider = new ConsumetProvider(); // DISABLED: API down (DMCA)
        MangaDexProvider mangaDexProvider = new MangaDexProvider();
        JikanProvider jikanProvider = new JikanProvider();
        VidSrcProvider vidSrcProvider = new VidSrcProvider();
        AnimeFallbackProvider animeFallbackA = new AnimeFallbackProvider("AnimeFallback-AnimeKai", "https://animekai.to/search?keyword=%s", 260);
        AnimeFallbackProvider animeFallbackB = new AnimeFallbackProvider("AnimeFallback-AniwatchTV", "https://aniwatchtv.to/search?keyword=%s", 260);
        AnimeFallbackProvider animeFallbackC = new AnimeFallbackProvider("AnimeFallback-AnimePahe", "https://animepahe.pw/anime?q=%s", 260);
        AnimeFallbackProvider animeFallbackD = new AnimeFallbackProvider("AnimeFallback-BiliBili", "https://www.bilibili.tv/en/search-result?q=%s", 260);
        MangaFallbackProvider mangaFallbackA = new MangaFallbackProvider("MangaFallback-Comix", "https://comix.to/filter?keyword=%s", 500);
        MangaFallbackProvider mangaFallbackB = new MangaFallbackProvider("MangaFallback-MangaFire", "https://mangafire.to/filter?keyword=%s", 500);
        MangaFallbackProvider mangaFallbackC = new MangaFallbackProvider("MangaFallback-WeebCentral", "https://weebcentral.com/search?q=%s", 500);
        MangaFallbackProvider mangaFallbackD = new MangaFallbackProvider("MangaFallback-MangaPark", "https://mangapark.io/search?word=%s", 500);
        YouTubeSearchProvider youtubeProvider = new YouTubeSearchProvider();
        SearchEngineFallbackProvider streamFallbackNepu = new SearchEngineFallbackProvider(
                "StreamFallback-Nepu",
                "https://nepu.to/search?q=%s",
                EnumSet.of(MediaProvider.MediaType.MOVIE, MediaProvider.MediaType.TV_SHOW)
        );
        SearchEngineFallbackProvider streamFallbackXprime = new SearchEngineFallbackProvider(
                "StreamFallback-Xprime",
                "https://xprime.su/search?q=%s",
                EnumSet.of(MediaProvider.MediaType.MOVIE, MediaProvider.MediaType.TV_SHOW)
        );
        SearchEngineFallbackProvider streamFallbackCineby = new SearchEngineFallbackProvider(
                "StreamFallback-Cineby",
                "https://www.cineby.sc/search?q=%s",
                EnumSet.of(MediaProvider.MediaType.MOVIE, MediaProvider.MediaType.TV_SHOW)
        );
        
        // Register anime providers (priority order)
        // registerAnimeProvider(consumetProvider);  // DISABLED: Consumet down
        registerAnimeProvider(jikanProvider);     // Primary: Jikan metadata + external fallback handoff
        registerAnimeProvider(youtubeProvider);   // Ad-light fallback: YouTube search redirect
        registerAnimeProvider(animeFallbackA);    // Fallback: AnimeKai search redirect + generic episode table
        registerAnimeProvider(animeFallbackB);    // Fallback: AniwatchTV search redirect
        registerAnimeProvider(animeFallbackC);    // Fallback: AnimePahe search redirect
        registerAnimeProvider(animeFallbackD);    // Fallback: BiliBili search redirect
        
        // Register manga providers (priority order)
        // registerMangaProvider(consumetProvider);  // DISABLED: Consumet down
        registerMangaProvider(mangaDexProvider);  // Primary: Direct MangaDex API
        registerMangaProvider(mangaFallbackA);    // Fallback: Comix search + generic chapter table
        registerMangaProvider(mangaFallbackB);    // Fallback: MangaFire search
        registerMangaProvider(mangaFallbackC);    // Fallback: WeebCentral search
        registerMangaProvider(mangaFallbackD);    // Fallback: alternate generic chapter source
        
        // Register movie providers (priority order)
        // registerMovieProvider(consumetProvider);  // DISABLED: Consumet down
        registerMovieProvider(vidSrcProvider);    // Primary: VidSrc embeds
        registerMovieProvider(youtubeProvider);   // Ad-light fallback: YouTube search redirect
        registerMovieProvider(streamFallbackNepu);     // Fallback: Nepu search
        registerMovieProvider(streamFallbackXprime);   // Fallback: Xprime search
        registerMovieProvider(streamFallbackCineby);   // Fallback: Cineby search
        
        // Register TV providers (priority order)
        // registerTvProvider(consumetProvider);     // DISABLED: Consumet down
        registerTvProvider(vidSrcProvider);       // Primary: VidSrc embeds
        registerTvProvider(youtubeProvider);      // Ad-light fallback: YouTube search redirect
        registerTvProvider(streamFallbackNepu);   // Fallback: Nepu search
        registerTvProvider(streamFallbackXprime); // Fallback: Xprime search
        registerTvProvider(streamFallbackCineby); // Fallback: Cineby search
        
        Log.d(TAG, "Initialized providers (Consumet DISABLED) - Anime: " + animeProviders.size() + 
              ", Manga: " + mangaProviders.size() + 
              ", Movie: " + movieProviders.size() + 
              ", TV: " + tvProviders.size());
    }
    
    public void setStatusCallback(ProviderStatusCallback callback) {
        this.statusCallback = callback;
    }

    public void setPreferredProvider(MediaProvider.MediaType type, String providerName) {
        String normalized = providerName == null ? null : providerName.trim();
        if (normalized != null && normalized.isEmpty()) {
            normalized = null;
        }
        if (type == MediaProvider.MediaType.ANIME) {
            preferredAnimeProvider = normalized;
        } else if (type == MediaProvider.MediaType.MANGA) {
            preferredMangaProvider = normalized;
        } else if (type == MediaProvider.MediaType.MOVIE) {
            preferredMovieProvider = normalized;
        } else if (type == MediaProvider.MediaType.TV_SHOW) {
            preferredTvProvider = normalized;
        }
    }

    public List<String> getProviderNames(MediaProvider.MediaType type) {
        List<MediaProvider> providers;
        if (type == MediaProvider.MediaType.ANIME) {
            providers = animeProviders;
        } else if (type == MediaProvider.MediaType.MANGA) {
            providers = mangaProviders;
        } else if (type == MediaProvider.MediaType.MOVIE) {
            providers = movieProviders;
        } else if (type == MediaProvider.MediaType.TV_SHOW) {
            providers = tvProviders;
        } else {
            return new ArrayList<>();
        }

        List<String> names = new ArrayList<>();
        for (MediaProvider provider : providers) {
            String name = provider.getProviderName();
            if (name != null && !name.trim().isEmpty() && !names.contains(name)) {
                names.add(name);
            }
        }
        return names;
    }
    
    // ============= PROVIDER REGISTRATION =============
    
    public void registerAnimeProvider(MediaProvider provider) {
        if (provider.supportsMediaType(MediaProvider.MediaType.ANIME)) {
            animeProviders.add(provider);
            Log.d(TAG, "Registered anime provider: " + provider.getProviderName());
        }
    }
    
    public void registerMangaProvider(MediaProvider provider) {
        if (provider.supportsMediaType(MediaProvider.MediaType.MANGA)) {
            mangaProviders.add(provider);
            Log.d(TAG, "Registered manga provider: " + provider.getProviderName());
        }
    }
    
    public void registerMovieProvider(MediaProvider provider) {
        if (provider.supportsMediaType(MediaProvider.MediaType.MOVIE)) {
            movieProviders.add(provider);
            Log.d(TAG, "Registered movie provider: " + provider.getProviderName());
        }
    }
    
    public void registerTvProvider(MediaProvider provider) {
        if (provider.supportsMediaType(MediaProvider.MediaType.TV_SHOW)) {
            tvProviders.add(provider);
            Log.d(TAG, "Registered TV provider: " + provider.getProviderName());
        }
    }
    
    // ============= ANIME RESOLUTION =============
    
    /**
     * Resolve anime episode with automatic fallback.
     * 
     * @return ProviderResult with stream URL and provider name, or null if all failed
     */
    public ProviderResult resolveAnimeEpisode(String title, int episode) {
        Log.d(TAG, "Resolving anime: " + title + " Episode " + episode);
        
        for (MediaProvider provider : getOrderedProviders(animeProviders, preferredAnimeProvider)) {
            String providerName = provider.getProviderName();
            
            notifyTrying(providerName);
            Log.d(TAG, "Trying provider: " + providerName);
            
            try {
                String url = provider.resolveAnimeEpisode(title, episode);
                
                if (url != null && !url.isEmpty()) {
                    lastSuccessfulAnimeProvider = providerName;
                    notifySuccess(providerName);
                    Log.d(TAG, "✓ Success with provider: " + providerName);
                    return new ProviderResult(url, providerName);
                } else {
                    notifyFailed(providerName, "No URL returned");
                    Log.w(TAG, "✗ Provider returned null: " + providerName);
                }
            } catch (Exception e) {
                notifyFailed(providerName, e.getMessage());
                Log.e(TAG, "✗ Provider failed: " + providerName, e);
            }
        }
        
        Log.e(TAG, "All anime providers failed for: " + title);
        String fallbackUrl = "https://animekai.to/search?keyword=" + encodeForUrl(title);
        notifyFailed("All providers", "Using web fallback");
        return new ProviderResult(fallbackUrl, "WebFallback");
    }
    
    /**
     * Fetch anime episodes with automatic fallback.
     */
    public ProviderListResult<MediaProvider.EpisodeInfo> fetchAnimeEpisodes(String title) {
        Log.d(TAG, "Fetching anime episodes: " + title);
        
        for (MediaProvider provider : getOrderedProviders(animeProviders, preferredAnimeProvider)) {
            String providerName = provider.getProviderName();
            
            try {
                List<MediaProvider.EpisodeInfo> episodes = provider.fetchAnimeEpisodes(title);
                
                if (episodes != null && !episodes.isEmpty()) {
                    Log.d(TAG, "✓ Got " + episodes.size() + " episodes from: " + providerName);
                    return new ProviderListResult<>(episodes, providerName);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to fetch episodes from: " + providerName, e);
            }
        }
        
        // Always provide a usable table fallback so selector is never blank.
        List<MediaProvider.EpisodeInfo> fallbackEpisodes = new ArrayList<>();
        for (int i = 1; i <= 260; i++) {
            fallbackEpisodes.add(new MediaProvider.EpisodeInfo("fallback-ep-" + i, i, "Episode " + i, null));
        }
        return new ProviderListResult<>(fallbackEpisodes, "GenericFallback");
    }
    
    // ============= MANGA RESOLUTION =============
    
    /**
     * Resolve manga chapter with automatic fallback.
     */
    public ProviderListResult<String> resolveMangaChapter(String title, int chapter) {
        Log.d(TAG, "Resolving manga: " + title + " Chapter " + chapter);
        
        for (MediaProvider provider : getOrderedProviders(mangaProviders, preferredMangaProvider)) {
            String providerName = provider.getProviderName();
            
            notifyTrying(providerName);
            Log.d(TAG, "Trying provider: " + providerName);
            
            try {
                List<String> pages = provider.resolveMangaChapter(title, chapter);
                
                if (pages != null && !pages.isEmpty()) {
                    lastSuccessfulMangaProvider = providerName;
                    notifySuccess(providerName);
                    Log.d(TAG, "✓ Got " + pages.size() + " pages from: " + providerName);
                    return new ProviderListResult<>(pages, providerName);
                } else {
                    notifyFailed(providerName, "No pages returned");
                }
            } catch (Exception e) {
                notifyFailed(providerName, e.getMessage());
                Log.e(TAG, "✗ Provider failed: " + providerName, e);
            }
        }
        
        Log.e(TAG, "All manga providers failed for: " + title);
        return new ProviderListResult<>(new ArrayList<>(), "None");
    }
    
    /**
     * Fetch manga chapters with automatic fallback.
     */
    public ProviderListResult<MediaProvider.ChapterInfo> fetchMangaChapters(String title) {
        Log.d(TAG, "Fetching manga chapters: " + title);
        
        for (MediaProvider provider : getOrderedProviders(mangaProviders, preferredMangaProvider)) {
            String providerName = provider.getProviderName();
            
            try {
                List<MediaProvider.ChapterInfo> chapters = provider.fetchMangaChapters(title);
                
                if (chapters != null && !chapters.isEmpty()) {
                    Log.d(TAG, "✓ Got " + chapters.size() + " chapters from: " + providerName);
                    return new ProviderListResult<>(chapters, providerName);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to fetch chapters from: " + providerName, e);
            }
        }
        
        List<MediaProvider.ChapterInfo> fallbackChapters = new ArrayList<>();
        for (int i = 1; i <= 500; i++) {
            fallbackChapters.add(new MediaProvider.ChapterInfo("fallback-ch-" + i, i, "Chapter " + i, 0));
        }
        return new ProviderListResult<>(fallbackChapters, "GenericFallback");
    }
    
    // ============= MOVIE RESOLUTION =============
    
    /**
     * Resolve movie with automatic fallback.
     */
    public ProviderResult resolveMovie(String title, int year, String tmdbId) {
        Log.d(TAG, "Resolving movie: " + title + (year > 0 ? " (" + year + ")" : ""));
        
        for (MediaProvider provider : getOrderedProviders(movieProviders, preferredMovieProvider)) {
            String providerName = provider.getProviderName();
            
            notifyTrying(providerName);
            
            try {
                String url = provider.resolveMovie(title, year, tmdbId);
                
                if (url != null && !url.isEmpty()) {
                    lastSuccessfulMovieProvider = providerName;
                    notifySuccess(providerName);
                    Log.d(TAG, "✓ Success with provider: " + providerName);
                    return new ProviderResult(url, providerName);
                } else {
                    notifyFailed(providerName, "No URL returned");
                }
            } catch (Exception e) {
                notifyFailed(providerName, e.getMessage());
                Log.e(TAG, "✗ Provider failed: " + providerName, e);
            }
        }
        
        String fallbackUrl = "https://vidsrc.to/embed/movie/" + (tmdbId != null ? tmdbId : "") + (tmdbId == null || tmdbId.isEmpty() ? "?q=" + encodeForUrl(title) : "");
        notifyFailed("All providers", "Using web fallback");
        return new ProviderResult(fallbackUrl, "WebFallback");
    }
    
    // ============= TV SHOW RESOLUTION =============
    
    /**
     * Resolve TV episode with automatic fallback.
     */
    public ProviderResult resolveTvEpisode(String title, int season, int episode, String tmdbId) {
        Log.d(TAG, "Resolving TV: " + title + " S" + season + "E" + episode);
        
        for (MediaProvider provider : getOrderedProviders(tvProviders, preferredTvProvider)) {
            String providerName = provider.getProviderName();
            
            notifyTrying(providerName);
            
            try {
                String url = provider.resolveTvEpisode(title, season, episode, tmdbId);
                
                if (url != null && !url.isEmpty()) {
                    lastSuccessfulTvProvider = providerName;
                    notifySuccess(providerName);
                    Log.d(TAG, "✓ Success with provider: " + providerName);
                    return new ProviderResult(url, providerName);
                } else {
                    notifyFailed(providerName, "No URL returned");
                }
            } catch (Exception e) {
                notifyFailed(providerName, e.getMessage());
                Log.e(TAG, "✗ Provider failed: " + providerName, e);
            }
        }
        
        String fallbackUrl = "https://vidsrc.to/embed/tv/" + (tmdbId != null ? tmdbId : "") + "/" + season + "/" + episode;
        notifyFailed("All providers", "Using web fallback");
        return new ProviderResult(fallbackUrl, "WebFallback");
    }
    
    // ============= HELPERS =============
    
    private void notifyTrying(String providerName) {
        if (statusCallback != null) {
            statusCallback.onProviderTrying(providerName);
        }
    }
    
    private void notifySuccess(String providerName) {
        if (statusCallback != null) {
            statusCallback.onProviderSuccess(providerName);
        }
    }
    
    private void notifyFailed(String providerName, String reason) {
        if (statusCallback != null) {
            statusCallback.onProviderFailed(providerName, reason);
        }
    }
    
    public String getLastSuccessfulAnimeProvider() {
        return lastSuccessfulAnimeProvider;
    }
    
    public String getLastSuccessfulMangaProvider() {
        return lastSuccessfulMangaProvider;
    }
    
    public int getAnimeProviderCount() {
        return animeProviders.size();
    }
    
    public int getMangaProviderCount() {
        return mangaProviders.size();
    }

    private List<MediaProvider> getOrderedProviders(List<MediaProvider> providers, String preferredProviderName) {
        List<MediaProvider> ordered = new ArrayList<>(providers);
        if (preferredProviderName == null || preferredProviderName.trim().isEmpty()) {
            return ordered;
        }
        int preferredIndex = -1;
        for (int i = 0; i < ordered.size(); i++) {
            MediaProvider provider = ordered.get(i);
            if (provider != null && preferredProviderName.equalsIgnoreCase(provider.getProviderName())) {
                preferredIndex = i;
                break;
            }
        }
        if (preferredIndex > 0) {
            MediaProvider preferred = ordered.remove(preferredIndex);
            ordered.add(0, preferred);
        }
        return ordered;
    }

    private String encodeForUrl(String value) {
        try {
            return java.net.URLEncoder.encode(value, "UTF-8");
        } catch (Exception e) {
            return value.replace(" ", "+");
        }
    }
    
    // ============= RESULT CLASSES =============
    
    /**
     * Result containing a single URL and the provider that returned it
     */
    public static class ProviderResult {
        public final String url;
        public final String providerName;
        
        public ProviderResult(String url, String providerName) {
            this.url = url;
            this.providerName = providerName;
        }
    }
    
    /**
     * Result containing a list and the provider that returned it
     */
    public static class ProviderListResult<T> {
        public final List<T> items;
        public final String providerName;
        
        public ProviderListResult(List<T> items, String providerName) {
            this.items = items;
            this.providerName = providerName;
        }
    }
}
