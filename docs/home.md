# Home (Dashboard)

The central hub providing an overview of the user's media library and activities.

## Features

### 1. Backlog Spotlight
A prominent hero card displaying the media the user is currently reading or watching.
*   **Dynamic Loading:** Features a built-in `ProgressBar` loading spinner. It uses a custom `Glide` RequestListener to automatically hide the spinner once the cover image is successfully rendered or if it fails.
*   **Priority Logic:** The spotlight automatically queries the database for the most relevant media. It strictly prioritizes items with the `Ongoing` status. If no ongoing items exist, it falls back to the most recently updated `Planning` item.
*   **Visuals:** Utilizes a frosted glass overlay with high-contrast white text to ensure readability over any colorful media cover art.

### 2. Quick Metrics Overview
Displays high-level consumption statistics extracted in real-time from the SQLite database.
*   **Watch Time:** Sums up progress for Movies, Series, and Anime (converted to Hours/Minutes).
*   **Pages Read:** Aggregates total progress for Books and Manga.
*   **Ongoing Count:** Tracks how many items are currently being consumed.
*   **Library Rating:** Shows the average user rating across all non-deleted items.

### 3. Recent Activity
A dynamic list showing the 4 most recently updated media items. It displays thumbnails, titles, and a "Time Ago" timestamp (e.g., "2h ago", "3d ago") calculated via `getTimeAgo()`.

### 4. Shake to Decide
An interactive tool helping users pick their next media.
*   **Interaction:** Uses the device's accelerometer via `ShakeDetector.java`.
*   **Logic:** Randomly selects an item specifically from the `Planning` backlog using a weighted algorithm (higher priority items have a better chance of being picked).

## Related Files
*   **Logic:** `app/src/main/java/com/example/mediavault/ui/home/HomeFragment.java`
*   **Layout:** `app/src/main/res/layout/fragment_home.xml`
*   **Shake Logic:** `app/src/main/java/com/example/mediavault/ShakeDetector.java` & `ShakeFragment.java`
