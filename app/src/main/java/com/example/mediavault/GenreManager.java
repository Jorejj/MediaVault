package com.example.mediavault;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages genre mappings for different media types.
 * Provides finalized genre categorization for Movies, Series, Anime, Manga, and Books.
 */
public class GenreManager {

    // ===== 🎬 MOVIES & SERIES (General Media) =====
    private static final String[] MOVIES_SERIES_GENRES = {
            "Action", "Adventure", "Comedy", "Drama", "Sci-Fi",
            "Fantasy", "Horror", "Thriller", "Mystery", "Romance",
            "Crime", "Documentary", "Historical", "Musical", "Western",
            "Family", "Animation"
    };

    // ===== 🌸 ANIME & 📖 MANGA =====
    private static final String[] ANIME_MANGA_CORE_GENRES = {
            "Action", "Adventure", "Comedy", "Drama", "Sci-Fi",
            "Fantasy", "Horror", "Thriller", "Mystery", "Romance"
    };

    private static final String[] ANIME_MANGA_DEMOGRAPHIC = {
            "Shounen", "Shoujo", "Seinen", "Josei"
    };

    private static final String[] ANIME_MANGA_THEMATIC = {
            "Isekai", "Slice of Life", "Mecha", "Magical Girl",
            "Psychological", "Sports", "Harem", "Iyashikei"
    };

    // ===== 📚 BOOKS =====
    private static final String[] BOOKS_CORE_GENRES = {
            "Action", "Adventure", "Comedy", "Drama", "Sci-Fi",
            "Fantasy", "Horror", "Thriller", "Mystery", "Romance"
    };

    private static final String[] BOOKS_FICTION = {
            "Literary Fiction", "Historical Fiction", "Science Fiction",
            "High Fantasy", "Low Fantasy", "Cozy Mystery", "Noir",
            "Psychological Thriller", "Legal Thriller", "Contemporary Romance",
            "Historical Romance", "Paranormal Romance", "Rom-Com",
            "Gothic Horror", "Cosmic Horror", "Splatterpunk", "Young Adult"
    };

    private static final String[] BOOKS_NONFICTION = {
            "Biography", "Autobiography", "Memoir", "History",
            "Science & Technology", "Philosophy", "Religion",
            "Self-Help", "True Crime", "Travel", "Essay"
    };

    private static final Map<String, String[]> GENRE_MAP = new HashMap<>();

    static {
        // Movies and Series share the same genres
        GENRE_MAP.put("Movie", MOVIES_SERIES_GENRES);
        GENRE_MAP.put("Series", MOVIES_SERIES_GENRES);

        // Anime has core + demographic + thematic
        GENRE_MAP.put("Anime", combineArrays(ANIME_MANGA_CORE_GENRES,
                combineArrays(ANIME_MANGA_DEMOGRAPHIC, ANIME_MANGA_THEMATIC)));

        // Manga has core + demographic + thematic
        GENRE_MAP.put("Manga", combineArrays(ANIME_MANGA_CORE_GENRES,
                combineArrays(ANIME_MANGA_DEMOGRAPHIC, ANIME_MANGA_THEMATIC)));

        // Books have core + fiction + non-fiction (user selects which applies)
        GENRE_MAP.put("Book", combineArrays(BOOKS_CORE_GENRES,
                combineArrays(BOOKS_FICTION, BOOKS_NONFICTION)));
    }

    /**
     * Get all available genres for a given media type
     */
    public static String[] getGenresForMediaType(String mediaType) {
        return GENRE_MAP.getOrDefault(mediaType, new String[0]);
    }

    /**
     * Get genres as a List for easier use with adapters
     */
    public static List<String> getGenreListForMediaType(String mediaType) {
        List<String> genres = new ArrayList<>();
        String[] genreArray = getGenresForMediaType(mediaType);
        for (String genre : genreArray) {
            genres.add(genre);
        }
        return genres;
    }

    /**
     * Check if a genre is valid for a media type
     */
    public static boolean isValidGenre(String mediaType, String genre) {
        if (genre == null || genre.isEmpty()) {
            return false;
        }
        String[] genres = getGenresForMediaType(mediaType);
        for (String g : genres) {
            if (g.equalsIgnoreCase(genre)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get genre descriptions
     */
    public static String getGenreDescription(String genre) {
        Map<String, String> descriptions = new HashMap<>();

        // Core genres
        descriptions.put("Action", "Fast-paced, stunt-heavy, and combat-focused.");
        descriptions.put("Adventure", "Epic journeys, exploration, and quests.");
        descriptions.put("Comedy", "Intended to make the audience laugh (includes Slapstick, Satire, Dark Comedy).");
        descriptions.put("Drama", "Serious, narrative-driven stories focusing on character development and emotional themes.");
        descriptions.put("Sci-Fi", "Futuristic concepts, space exploration, time travel, and advanced technology.");
        descriptions.put("Fantasy", "Magic, mythical creatures, and imaginary worlds.");
        descriptions.put("Horror", "Designed to frighten, shock, or disgust (includes Slasher, Paranormal, Psychological).");
        descriptions.put("Thriller", "Tension-building, suspenseful, and highly unpredictable.");
        descriptions.put("Mystery", "Focused on solving a crime or uncovering a secret.");
        descriptions.put("Romance", "Central focus on a love story.");

        // Media-specific genres
        descriptions.put("Crime", "Focused on criminals, the mob, or law enforcement.");
        descriptions.put("Documentary", "Non-fictional, educational, or biographical coverage of real events.");
        descriptions.put("Historical", "Set in a specific past era.");
        descriptions.put("Musical", "Characters sing songs interwoven into the narrative.");
        descriptions.put("Western", "Set in the American Old West.");
        descriptions.put("Family", "Appropriate for all ages.");
        descriptions.put("Animation", "Medium-specific (CGI, 2D, Stop-motion) but acts as a genre tag.");

        // Anime/Manga demographic
        descriptions.put("Shounen", "Action-packed, aimed at young teen males (e.g., Naruto, One Piece).");
        descriptions.put("Shoujo", "Romance and drama-focused, aimed at young teen females (e.g., Sailor Moon, Fruits Basket).");
        descriptions.put("Seinen", "Mature, complex themes, aimed at adult men (e.g., Berserk, Monster).");
        descriptions.put("Josei", "Realistic romance and slice-of-life, aimed at adult women (e.g., Nana).");

        // Anime/Manga thematic
        descriptions.put("Isekai", "\"Another world\" – protagonist is transported to or reincarnated in a fantasy world.");
        descriptions.put("Slice of Life", "Depicts everyday, mundane life with a focus on character interactions.");
        descriptions.put("Mecha", "Focuses on giant robots or machines (e.g., Gundam, Evangelion).");
        descriptions.put("Magical Girl", "Young girls transforming to use magic.");
        descriptions.put("Psychological", "Deep dives into the characters' mental states, often dark and twisted.");
        descriptions.put("Sports", "Centered around athletic competition and teamwork (e.g., Haikyuu!!).");
        descriptions.put("Harem", "One protagonist surrounded by multiple potential romantic interests.");
        descriptions.put("Iyashikei", "\"Healing\" anime; slow-paced, visually beautiful, and deeply relaxing.");

        // Book-specific
        descriptions.put("Literary Fiction", "Character-driven, focuses on prose and complex themes over plot.");
        descriptions.put("Historical Fiction", "Fictional characters set against the backdrop of real historical events.");
        descriptions.put("Science Fiction", "Hard Sci-Fi, Space Opera, Cyberpunk, Dystopian.");
        descriptions.put("High Fantasy", "Entirely invented worlds with magic systems.");
        descriptions.put("Low Fantasy", "Magical elements in the real world.");
        descriptions.put("Cozy Mystery", "Mystery with lighter tone and minimal violence.");
        descriptions.put("Noir", "Dark, cynical mystery with morally ambiguous characters.");
        descriptions.put("Psychological Thriller", "Focuses on the mind and suspense.");
        descriptions.put("Legal Thriller", "Centers on the legal system and courtroom drama.");
        descriptions.put("Contemporary Romance", "Modern-day love stories.");
        descriptions.put("Paranormal Romance", "Romance with supernatural or magical elements.");
        descriptions.put("Rom-Com", "Romance blended with comedy.");
        descriptions.put("Gothic Horror", "Dark, atmospheric horror with architectural/period elements.");
        descriptions.put("Cosmic Horror", "Horror involving cosmic/Lovecraftian themes.");
        descriptions.put("Splatterpunk", "Extreme violence and gore in horror.");
        descriptions.put("Young Adult", "Coming-of-age stories aimed at teenagers.");
        descriptions.put("Biography", "The story of a real person's life.");
        descriptions.put("Autobiography", "First-person account of the author's own life.");
        descriptions.put("Memoir", "A narrative focused on a specific period or theme in the author's life.");
        descriptions.put("History", "Factual accounts of past events.");
        descriptions.put("Science & Technology", "Educational breakdowns of scientific concepts.");
        descriptions.put("Philosophy", "Exploration of human existence, ethics, and belief systems.");
        descriptions.put("Religion", "Exploration of religious faith and belief systems.");
        descriptions.put("Self-Help", "Guides for improving one's life, habits, or career.");
        descriptions.put("True Crime", "Factual investigation of real crimes.");
        descriptions.put("Travel", "Collections of thoughts or experiences regarding specific locations.");
        descriptions.put("Essay", "Collections of thoughts or experiences on specific topics.");

        return descriptions.getOrDefault(genre, "");
    }

    /**
     * Combine multiple string arrays into one
     */
    private static String[] combineArrays(String[] array1, String[] array2) {
        String[] result = new String[array1.length + array2.length];
        System.arraycopy(array1, 0, result, 0, array1.length);
        System.arraycopy(array2, 0, result, array1.length, array2.length);
        return result;
    }
}
