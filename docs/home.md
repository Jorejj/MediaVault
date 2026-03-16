# Home (Dashboard)

The central hub providing an overview of the user's media library and activities.

## Features

### 1. Backlog Spotlight
A prominent hero card displaying the media the user is currently reading or watching.
*   **Auto-Fetching:** If cover art is missing, the app automatically triggers a background search via `MediaSearchManager` using the title and type.
*   **Image Caching:** Once a remote URL is fetched, it is downloaded locally using `ImageUtils` and saved to the app's internal storage. Future loads use the local file for instant rendering.
*   **Priority Logic:** Prioritizes `Ongoing` items, falling back to the most recent `Planning` item if none are active.
*   **Clean UI:** Removed loading spinners for a more immediate and seamless visual experience.

### 2. Binge Calculator & Quick Metrics
A redesigned analytics section featuring a balanced 3-row vertical layout.
*   **Watch Time:** Displays total hours/minutes consumed from Video-based media.
*   **Pages Read:** Aggregates progress from Books and Manga.
*   **Ongoing Titles:** Highlights currently active media with a distinctive red accent.
*   **Library Stats Card:** A unified three-column card showing total **Episodes**, **Books Read**, and **Average Rating**.

### 3. Recent Activity
A dynamic list of the 4 most recently updated items. 
*   **Automatic Sync:** Missing cover art for recent items is fetched and cached automatically in the background.
*   **Time Tracking:** Uses `getTimeAgo()` to display relative timestamps (e.g., "5m ago").

### 4. Shake to Decide
An interactive tool helping users pick their next media from the "Planning" backlog.
*   **Icon Switching:** Dynamically switches from the MediaVault logo to a shaking icon (`ic_shake`) when motion is detected.
*   **Haptic Feedback:** The device vibrates for 200ms upon successfully picking a title.
*   **Glassmorphism Dialog:** The result appears in a premium blurred dialog that uses the same glassmorphism effect as the navigation bar, with a significantly darkened background for focus.

## Code Usage Examples

### Local Image Caching
```java
// Logic used across the app to ensure fast offline loading
new Thread(() -> {
    String localPath = ImageUtils.downloadAndSaveImage(context, remoteUrl);
    if (localPath != null) {
        dbHelper.updateImagePath(mediaId, localPath);
    }
}).start();
```

### Auto-Fetching Missing Artwork
```java
// Triggered during UI binding if imagePath is null
if (imagePath == null || imagePath.isEmpty()) {
    searchManager.searchAndDownloadImage(requireContext(), id, title, type);
}
```

### Shake Detection & Vibration
```java
mShakeDetector.setOnShakeListener(count -> {
    vibrate(); // Provide haptic feedback
    handleShake(); // Pick random media
});

private void vibrate() {
    Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
    v.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE));
}
```

## Related Files
*   **Logic:** `app/src/main/java/com/example/mediavault/ui/home/HomeFragment.java`
*   **Layout:** `app/src/main/res/layout/fragment_home.xml`
*   **Image Utility:** `app/src/main/java/com/example/mediavault/ImageUtils.java`
*   **API Manager:** `app/src/main/java/com/example/mediavault/api/MediaSearchManager.java`
*   **Shake Logic:** `app/src/main/java/com/example/mediavault/ShakeFragment.java`
