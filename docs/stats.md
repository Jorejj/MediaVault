# Stats & Metrics

A comprehensive analytics screen showing detailed reading and watching habits based on the user's database.

## Features

### 1. Habit Analysis
Calculates and displays total media consumption by processing raw progress data.
*   **Video Content:** Converts progress for Movies, Series, and Anime into total minutes/hours.
*   **Reading Content:** Sums up total pages and chapters read.

### 2. Vault Composition
Visually breaks down the library to show the ratio of Books vs. Videos vs. Comics. It uses a custom logic to categorize units (Pages/Chapters vs Episodes/Minutes).

### 3. Backlog Health Tracker
A proprietary algorithm that analyzes the ratio of `Completed` items versus `Planning` and `Ongoing` items. It outputs a status (e.g., "Healthy", "Overwhelming") to help users manage their backlog.

### 4. Integrity Logic
*   **Exclusion:** **Crucially, all metric queries in `DatabaseHelper.java` are hardcoded to ignore items with the `Recently Deleted` status.** This ensures that the dashboard and stats screens reflect the user's active library and are not skewed by items in the trash.

## Related Files
*   **Logic:** `app/src/main/java/com/example/mediavault/ui/metrics/MetricsFragment.java`
*   **Layout:** `app/src/main/res/layout/fragment_metrics.xml`
*   **Queries:** `app/src/main/java/com/example/mediavault/DatabaseHelper.java` (specifically functions like `getDailyPages`, `getTotalMinutesWatched`, etc.)
