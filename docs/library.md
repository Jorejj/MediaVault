# Library & Media Management

The visual grid and management interface for all saved media items, featuring robust soft-delete capabilities.

## Features

### 1. Media Grid & Cards
Displays saved media items in a responsive grid.
*   **Alignment:** All internal card elements (Title, Metadata, Star Rating, and Progress) are **left-aligned** for a consistent and clean reading experience.
*   **Anchored Progress:** The progress section (Bar + Text) is pinned to the **absolute bottom** of the card using layout weights. This ensures that progress bars across different cards always form a straight horizontal line, regardless of title length.
*   **Rating Pill:** A centered-text star rating pill using `rating_pill_bg.xml`.

### 2. Recently Deleted (Soft Delete)
A safety feature that prevents accidental permanent loss of data.
*   **Logic:** When a user clicks "Delete" on a media item, its status is updated to `Recently Deleted` in the database.
*   **Filtering:** A dedicated **"Recently Deleted" chip filter** at the top allows users to view these items.
*   **Recovery:** Items in this view can be restored back to the `Planning` status or **Permanently Deleted** via the 3-dots menu.

### 3. Media Card Options (3-Dots Menu)
A contextual `PopupMenu` located in the top-right corner of every media poster.
*   **File:** Managed in `MediaAdapter.java` via the `btn_more_options` click listener.

### 4. Search Online (API Integration)
Integrated into the "Add Media" flow.
*   **Search Online:** Replaces the generic "Search API" label. It queries TMDB, Jikan, and Google Books.
*   **Smart Population:** When an item is selected from search results, it auto-fills the manual entry form and hides the `Image URL` field to maintain a streamlined UI.

### 5. Edit Media & Gallery Picker
*   **Image Upload:** In `EditMediaActivity`, the Image URL field features a **Gallery Icon** (`endIconMode="custom"`). Clicking it launches the system image picker, allowing users to upload local images as media covers.

## Related Files
*   **Fragment Logic:** `app/src/main/java/com/example/mediavault/ui/library/LibraryFragment.java`
*   **Adapter Logic:** `app/src/main/java/com/example/mediavault/ui/library/MediaAdapter.java`
*   **Item Layout:** `app/src/main/res/layout/item_media_card.xml`
*   **Add Logic:** `app/src/main/java/com/main/AddMediaActivity.java`
*   **Edit Logic:** `app/src/main/java/com/main/EditMediaActivity.java`
