# Settings, Architecture & Global UI

The core foundation of the MediaVault app, encompassing the design system, database, and system-wide utilities.

## Global Design System

### 1. Glassmorphism & Real-Time Blur
The app utilizes a consistent "frosted glass" aesthetic across all components.
*   **Ambient Mesh:** A global background (`ambient_mesh_bg.xml`) consisting of colorful, blurred orbs provides the backdrop for the transparency to work.
*   **BlurView Integration:** The bottom navigation bar uses the `BlurView` library (`version-1.6.6`) to perform real-time background blurring of the content scrolling beneath it.
*   **Oval Navigation:** The navbar is styled as a floating pill (`72dp` height, `36dp` corner radius).

### 2. Custom Glassmorphism Toasts
Standard Android toasts have been replaced with a premium custom implementation.
*   **Style:** Features a semi-transparent, blurred dark background with a red brand logo and white text.
*   **Implementation:** Located in `ToastUtils.java`. It dynamically sets up a `BlurView` within the toast layout at runtime to ensure the blur matches the background behind the toast.

## Core Architecture

### 1. Database (SQLite)
A robust local database managing all persistence.
*   **Schema:** Table `media_library` stores extensive metadata including completion moods, personal journals, and priority levels.
*   **Constraints:** Includes `CHECK` constraints to ensure data integrity for statuses, units, and ratings.
*   **Triggers:** Features an `AFTER UPDATE` trigger to automatically refresh the `last_updated` timestamp whenever an item is modified.

### 2. Theme Engine
A system-wide toggle in `SettingsFragment` allows switching between Light and Dark modes.
*   **Implementation:** Persisted via `SharedPreferences`. The app uses theme attributes (`?attr/colorTextPrimary`, etc.) and mode-specific drawables (`drawable-night/`) to ensure the glassmorphism gradients remain legible in both modes.

## Related Files
*   **Custom Toast Utility:** `app/src/main/java/com/example/mediavault/widget/ToastUtils.java`
*   **Toast Layout:** `app/src/main/res/layout/layout_custom_toast.xml`
*   **Database Helper:** `app/src/main/java/com/example/mediavault/DatabaseHelper.java`
*   **Global Layout:** `app/src/main/res/layout/activity_main.xml`
*   **Settings Logic:** `app/src/main/java/com/example/mediavault/ui/settings/SettingsFragment.java`
*   **Build Config:** `app/build.gradle.kts` (Managing BlurView and Retrofit dependencies)
