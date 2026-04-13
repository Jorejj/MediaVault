MediaVault: An Offline Universal Media Tracker

MediaVault is a centralized, privacy-first mobile application designed for users who want to track their entertainment consumption without relying on an internet connection. 
Built using Java and SQLite, it serves as a unified hub for managing books, movies, anime, manga, and TV series in one place.

Key Features

Multi-Type Media Management: Track diverse media formats including Books, Video, and Comics within a single database.

Dynamic UI Rendering: The interface automatically adjusts input fields based on the selected media type—for example, switching from "Pages" for books to "Duration" for movies.

Visual Progress Tracking: Monitor your journey through ongoing series or books using intuitive progress bars.

Binge Calculator (Statistics): Access a dedicated dashboard that analyzes your habits, calculating total hours watched or pages read over specific periods.

Shake-to-Decide: Stuck in a "backlog paralysis"? Physically shake your device to let the built-in accelerometer randomly pick your next title from your "Plan to Watch" list.

Advanced Filtering: Quickly navigate your library by filtering by Genre, Status (Planning, Ongoing, Completed), or Rating

## Supabase Foundation (Cloud Migration)

This project now includes Supabase foundation assets without changing existing local SQLite behavior yet.

1. Run this one-shot SQL in Supabase SQL Editor: `app/src/main/assets/supabase/setup_final.sql`
2. App config gate: `com.example.mediavault.cloud.CloudConfig`

Add to `local.properties`:

```properties
SUPABASE_URL=https://<your-project-ref>.supabase.co
SUPABASE_ANON_KEY=<your-anon-key>
SUPABASE_ENABLED=false
```

Keep `SUPABASE_ENABLED=false` until app auth + sync wiring is complete.

## Admin Website Starter

A lightweight admin starter is included at `admin/`.

1. Open `admin/index.html` in a browser.
2. Enter Supabase Project URL + anon key and save config.
3. Login with an admin user (role `admin` in `profiles`).
4. Use dashboard refresh to view profile/media/event overview.
