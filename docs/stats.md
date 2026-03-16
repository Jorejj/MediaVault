# Stats & Metrics

A comprehensive analytics engine that transforms raw media data into actionable habit insights.

## Features

### 1. Binge Calculator & Habit Analysis
The Binge Calculator (found on the Home Dashboard and Stats screen) provides a real-time summary of media consumption.
*   **Watch Time:** Calculated by summing total minutes from Anime, Movies, and Series. Minutes are dynamically formatted into "X hours Y mins" for readability.
*   **Pages Read:** Aggregates progress from Book and Manga units.
*   **Vault Composition:** A breakdown showing the distribution of media types across your library.

### 2. Expanded Dashboard Metrics
The home dashboard features an enhanced statistics card with three critical data points:
*   **Episodes:** Total count of individual episodes watched.
*   **Books Read:** Total count of media items with type `Book` and status `Completed`.
*   **Avg Rating:** The average score across your entire active library (weighted equally).

### 3. Backlog Health Tracker
An algorithmic assessment of your library's balance.
*   **Healthy:** High ratio of `Completed` to `Ongoing` items.
*   **Overwhelming:** Large number of `Ongoing` or `Planning` items with low completion rates.

### 4. Data Integrity (Exclusion Logic)
*   **Trash Protection:** Every statistical query in `DatabaseHelper.java` explicitly excludes items where the status is `Recently Deleted`. This ensures that your metrics accurately reflect your current goals and aren't skewed by discarded entries.

## Code Usage Examples

### Formatting Duration
```java
// Converts raw minutes into a human-readable string
private String formatDuration(int totalMinutes) {
    int hours = totalMinutes / 60;
    int mins = totalMinutes % 60;
    return hours + " hours " + mins + " mins";
}
```

### Type-Specific Completion Queries
```java
// SQL logic to count completed books specifically
public int getCompletedCountByType(String type) {
    String query = "SELECT COUNT(*) FROM media_library WHERE status = 'Completed' AND media_type = ?";
    // returns the integer result
}
```

### Aggregating Consumption
```java
// Sums up watch time from all relevant media categories
public int getTotalMinutesWatched() {
    String query = "SELECT SUM(current_progress) FROM media_library WHERE unit = 'Minutes' OR unit = 'Episodes'";
    // Note: Episode conversion usually happens at the fragment level or via metadata
}
```

## Related Files
*   **Logic:** `app/src/main/java/com/example/mediavault/ui/metrics/MetricsFragment.java`
*   **Dashboard Logic:** `app/src/main/java/com/example/mediavault/ui/home/HomeFragment.java`
*   **SQL Queries:** `app/src/main/java/com/example/mediavault/DatabaseHelper.java`
