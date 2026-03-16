# Settings, Architecture & Global UI

The core foundation of the MediaVault app, encompassing the design system, data layer, and external service integrations.

## Global Design System

### 1. Glassmorphism & Visual Polish
The app utilizes a refined glassmorphism aesthetic across the entire interface.
*   **Real-Time Blur:** Powered by the `BlurView` library with **RenderScript** acceleration. The navbar and premium dialogs perform live sampling of the background content.
*   **Adaptive Contrast:** Shake-to-Decide recommendation cards use a significantly darkened overlay (`#BF000000`) inside the blur layer to ensure readability while maintaining depth.
*   **Rounded Geometry:** All glass layers are clipped to match their parent containers' corner radii (typically `22dp` or `28dp`).

### 2. Standardized 9-Patch Assets
To maintain visual integrity across different screen sizes, all primary logos and iconography (e.g., `mediavault_logo.9.png`) are implemented as **9-Patch images**, ensuring zero distortion during scaling.

## Core Architecture

### 1. Centralized Media Search (`MediaSearchManager`)
A dedicated manager class that encapsulates all external API logic.
*   **Integrated Services:**
    *   **Movies/TV:** TMDB (The Movie Database)
    *   **Anime/Manga:** Jikan (MyAnimeList wrapper)
    *   **Books:** Google Books & Open Library (Fallback)
    *   **Series:** TVMaze (Secondary fallback for TV data)
*   **Intelligent Fallbacks:** Methods are designed to chain multiple API calls. If a primary search returns no image, the manager automatically queries secondary providers.

### 2. High-Performance Image Caching
MediaVault prioritizes speed by eliminating redundant network requests.
*   **Internal Storage:** Images are saved directly to `context.getFilesDir()`.
*   **Surgical Database Updates:** The `COL_IMAGE_PATH` is updated only after a successful local save, ensuring the database always points to a valid local file when possible.

## Code Usage Examples

### Safe Blur Setup
```java
// Logic used to prevent crashes on unsupported devices
try {
    blurView.setupWith(rootView)
            .setFrameClearDrawable(windowBackground)
            .setBlurAlgorithm(new RenderScriptBlur(context))
            .setBlurRadius(20f)
            .setHasFixedTransformationMatrix(true);
} catch (Throwable t) {
    blurView.setBackgroundColor(0x99000000); // Fallback to semi-transparent
}
```

### Chained API Search (Example: Books)
```java
// Chaining Google Books with OpenLibrary fallback
private void searchBookImage(int mediaId, String title) {
    googleBooks.search(title).enqueue(new Callback() {
        if (noResult) {
            searchOpenLibraryImage(mediaId, title); // Fallback call
        }
    });
}
```

## Related Files
*   **API Manager:** `app/src/main/java/com/example/mediavault/api/MediaSearchManager.java`
*   **Image Utility:** `app/src/main/java/com/example/mediavault/ImageUtils.java`
*   **Theme Engine:** `app/src/main/java/com/example/mediavault/ui/settings/SettingsFragment.java`
*   **Build Config:** `app/build.gradle.kts` (Managing RenderScript and BlurView settings)
