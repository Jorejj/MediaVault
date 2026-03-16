# Library & Media Management

The visual grid and management interface for all saved media items, featuring robust soft-delete and automatic metadata enrichment.

## Features

### 1. Media Grid & Adaptive Cards
Displays saved media items in a responsive grid.
*   **Automatic Image Enrichment:** As the user scrolls, `MediaAdapter` detects items with missing cover art and triggers an asynchronous search via `MediaSearchManager`.
*   **Persistent Caching:** Any artwork fetched from the web is automatically downloaded to internal storage. This ensures the library loads instantly and functions offline.
*   **Unit-Aware Styling:** Cards dynamically change their accent colors (e.g., Orange for Anime, Blue for Books) based on the media type.

### 2. Recently Deleted (Soft Delete)
A safety feature that prevents accidental permanent loss of data.
*   **Trash Logic:** Deleting an item moves it to the `Recently Deleted` state. These items are excluded from all dashboard metrics and statistics.
*   **Contextual Recovery:** Restoring an item from the trash immediately returns it to the `Planning` backlog and refreshes the library view.

### 3. Smart Search & Auto-Fill
The "Add Media" screen leverages a multi-API network to simplify data entry.
*   **Comprehensive Coverage:** Queries TMDB (Movies/Series), Jikan (Anime/Manga), Google Books, TVMaze, and Open Library.
*   **Fallback Logic:** If a primary API (like TMDB) fails to provide an image, the system automatically falls back to secondary sources (like TVMaze) to ensure a complete visual record.

### 4. Custom Media Covers
*   **Manual Override:** Users can manually paste an Image URL or use the **Gallery Picker** in the edit screen to upload personal files. These are also cached locally for performance.

## Code Usage Examples

### Adapter Auto-Fetch Logic
```java
// Inside MediaAdapter.onBindViewHolder
if (item.getCoverPath() == null) {
    // Show placeholder and fetch in background
    holder.poster.setImageResource(R.color.grey_300);
    searchManager.searchAndDownloadImage(context, item.getId(), item.getTitle(), item.getType());
}
```

### Deletion and Recovery
```java
// Logic for moving items to 'Soft Delete'
dbHelper.updateProgress(item.getId(), item.getProgress(), "Recently Deleted", item.getRatingValue());

// Logic for restoring items
dbHelper.updateProgress(item.getId(), item.getProgress(), "Planning", item.getRatingValue());
```

## Related Files
*   **Fragment Logic:** `app/src/main/java/com/example/mediavault/ui/library/LibraryFragment.java`
*   **Adapter Logic:** `app/src/main/java/com/example/mediavault/ui/library/MediaAdapter.java`
*   **API Manager:** `app/src/main/java/com/example/mediavault/api/MediaSearchManager.java`
*   **Edit Screen:** `app/src/main/java/com/example/mediavault/EditMediaActivity.java`
