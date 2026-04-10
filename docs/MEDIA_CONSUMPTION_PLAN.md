# MediaVault - In-App Media Consumption System
## 🎯 Vision: Zero-Interaction Content Consumption

**Goal**: Create a seamless, Consumet-powered content delivery system where users can read/watch any media type without manual URL hunting or external apps.

---

## 📊 Architecture Overview

### Current State
✅ **Anime**: Consumet GogoAnime integration complete
- AnimeResolver fetches streaming links automatically
- EpisodeSelectorActivity for episode selection
- PlayerActivity plays resolved streams

### Target State
🎯 **All Media Types**: Unified auto-resolution system
- **Novels** → In-app reader with auto-chapter loading
- **Manga** → In-app image reader with auto-page loading
- **Movies** → Direct playback with auto-resolution
- **TV Shows** → Sequential episode playback with auto-resolution
- **Books** → PDF/EPUB reader (existing functionality enhanced)

---

## 🔧 Technical Architecture

### 1. Unified Resolver System

```
ResolverFactory (Enhanced)
├── resolveAnime(title, episode) ✅ DONE
├── resolveMovie(title, year)
├── resolveTvShow(title, season, episode)
├── resolveManga(title, chapter)
└── resolveNovel(title, chapter)
```

**Pattern**: Each resolver implements `MediaResolver` interface:
```java
interface MediaResolver {
    String getContentUrl(String title, String identifier); // Episode/Chapter/etc
    ContentMetadata getMetadata(String title);
    boolean isAvailable(String title);
}
```

### 2. Consumet Provider Mapping

| Media Type | Consumet Provider | Base Endpoint | Example |
|------------|-------------------|---------------|---------|
| **Anime** | GogoAnime | `/anime/gogoanime` | ✅ IMPLEMENTED |
| **Manga** | MangaDex, ComicK | `/manga/mangadex` | Search → chapters → images |
| **Movies** | FlixHQ, VidSrc | `/movies/flixhq` | Search → watch link |
| **TV Shows** | FlixHQ, VidSrc | `/movies/flixhq` | Search → season → episode |
| **Novels** | ReadLightNovel | `/light-novels` | Search → chapters → text |

### 3. Content Structure

#### Novel Chapter Response
```json
{
  "title": "Solo Leveling Chapter 1",
  "content": "Full chapter text...",
  "nextChapter": "chapter-2",
  "previousChapter": null
}
```

#### Manga Chapter Response
```json
{
  "chapterId": "chapter-1",
  "pages": [
    "https://cdn.mangadex.org/page1.jpg",
    "https://cdn.mangadex.org/page2.jpg"
  ],
  "nextChapter": "chapter-2"
}
```

#### Movie/TV Response (same as anime)
```json
{
  "sources": [
    {"url": "https://...m3u8", "quality": "1080p"}
  ]
}
```

---

## 🎨 UI/UX Design

### A. Novel Reader (`NovelReaderActivity`)

**Layout**: Full-screen immersive text reader
```
┌─────────────────────────────┐
│ [Chapter 1: Awakening]  [≡] │ ← Header (auto-hide)
├─────────────────────────────┤
│                             │
│   Full chapter text here    │
│   with adjustable font,     │
│   size, and theme.          │
│                             │
│   Swipe → for next chapter  │
│   Swipe ← for previous      │
│                             │
│   Auto-resolves chapters    │
│   on demand via Consumet    │
│                             │
└─────────────────────────────┘
```

**Features**:
- Auto-load next chapter when user reaches 90% scroll
- Chapter cache (3 chapters ahead, 1 behind)
- Font size, family, theme customization
- Reading progress auto-save
- Tap top = show menu | Tap middle = toggle controls

### B. Manga Reader (`MangaReaderActivity`)

**Layout**: Vertical scroll or page-by-page
```
┌─────────────────────────────┐
│ [Ch. 1 | Page 5/20]     [≡] │ ← Header
├─────────────────────────────┤
│                             │
│      ┌───────────────┐      │
│      │               │      │
│      │  Manga Page   │      │
│      │     Image     │      │
│      │               │      │
│      └───────────────┘      │
│                             │
│   Swipe up/down = scroll    │
│   Swipe left/right = page   │
│                             │
└─────────────────────────────┘
```

**Features**:
- Dual reading modes: Vertical scroll OR page-by-page
- Auto-fetch next chapter when reaching last page
- Image cache (5 pages ahead, 2 behind)
- Pinch-to-zoom
- Double-tap to fit width/height
- Long-press for image options

### C. Movie Player (`MoviePlayerActivity`)

**Layout**: Reuse existing `PlayerActivity` with enhancements
```
┌─────────────────────────────┐
│                             │
│    [Immersive Video]        │
│                             │
│  Auto-resolves on launch    │
│  No manual URL required     │
│                             │
└─────────────────────────────┘
```

**Flow**:
1. User taps "Watch" on a Movie
2. `MovieResolver` auto-resolves streaming URL in background
3. Shows loading spinner (2-5 seconds)
4. Directly launches PlayerActivity with URL
5. No intermediate selection screen

### D. TV Series Player (`TvSeriesPlayerActivity`)

**Layout**: Similar to anime, but with season support
```
┌─────────────────────────────┐
│ Breaking Bad - S5 E14       │
│ ─────────────────────       │
│ [Season ▼] [Episode ▼]      │
│                             │
│ [ Resolve & Play ]          │
│                             │
│ ✓ Auto-play next episode    │
│   when current ends         │
└─────────────────────────────┘
```

**Features**:
- Season and episode selection
- Auto-play next episode after 10s countdown
- "Binge mode" toggle (skip intros, auto-continue)
- Progress tracking per episode

---

## 📋 Implementation Plan

### Phase 1: Foundation (Consumet API Research)
**Goal**: Understand all available Consumet providers

**Tasks**:
1. Test Consumet manga endpoints (MangaDex, ComicK)
2. Test Consumet movie endpoints (FlixHQ, VidSrc)
3. Test Consumet TV show endpoints
4. Test Consumet novel endpoints (if available)
5. Document response structures for each
6. Create POJOs for responses

**Deliverables**:
- `ConsumetMangaResponse.java`
- `ConsumetMovieResponse.java`
- `ConsumetNovelResponse.java`
- API endpoint documentation

### Phase 2: Resolver Layer
**Goal**: Build unified resolution system

**Tasks**:
1. Create `MangaResolver.java`
   - `resolveMangaChapter(title, chapter)` → List<String> images
2. Create `MovieResolver.java`
   - `resolveMovie(title, year)` → String streamUrl
3. Create `TvShowResolver.java`
   - `resolveTvEpisode(title, season, episode)` → String streamUrl
4. Create `NovelResolver.java`
   - `resolveNovelChapter(title, chapter)` → String chapterText
5. Enhance `ResolverFactory` to handle all types

**Deliverables**:
- 4 new resolver classes
- Updated `ResolverFactory` with routing logic
- Unit tests for each resolver

### Phase 3: Novel Reader UI
**Goal**: Full-screen text reader with auto-chapter loading

**Tasks**:
1. Create `NovelReaderActivity.java`
   - Receive: `title`, `startChapter`
   - Auto-resolve chapter text via `NovelResolver`
   - Display in ScrollView with custom TextView
2. Implement swipe gestures (← previous, → next)
3. Add settings: font size, family, background color
4. Implement chapter prefetch (load chapter N+1 in background)
5. Save reading position to DB

**Deliverables**:
- `activity_novel_reader.xml`
- `NovelReaderActivity.java`
- `NovelChapterCache.java` (memory cache)

### Phase 4: Manga Reader UI
**Goal**: Image-based reader with dual reading modes

**Tasks**:
1. Create `MangaReaderActivity.java`
   - Receive: `title`, `startChapter`
   - Auto-resolve chapter pages via `MangaResolver`
   - Display in RecyclerView (vertical) or ViewPager2 (page-by-page)
2. Implement reading mode toggle
3. Add zoom/pan support (PhotoView library)
4. Implement page prefetch
5. Save reading position

**Deliverables**:
- `activity_manga_reader.xml`
- `MangaReaderActivity.java`
- `MangaPageAdapter.java`
- Image caching system (Glide integration)

### Phase 5: Movie Player Integration
**Goal**: Seamless movie playback with auto-resolution

**Tasks**:
1. Create `MoviePlayerActivity.java` (or enhance `PlayerActivity`)
   - Receive: `title`, `year`
   - Auto-resolve via `MovieResolver` in background
   - Show loading UI during resolution
   - Launch ExoPlayer with resolved URL
2. Add error handling for failed resolutions
3. Update `DescriptionActivity` to route movies correctly

**Deliverables**:
- Enhanced PlayerActivity or new MoviePlayerActivity
- Loading screen UI
- Error fallback UI

### Phase 6: TV Series Player
**Goal**: Multi-season/episode support with auto-play

**Tasks**:
1. Create `TvSeriesPlayerActivity.java`
   - Season and episode selection UI
   - Auto-resolve via `TvShowResolver`
   - Play resolved stream
2. Implement auto-play next episode
   - Detect when video ends
   - Show 10s countdown timer
   - Auto-resolve and play next episode
3. Add "Binge Mode" toggle
4. Track watched episodes in DB

**Deliverables**:
- `activity_tv_series_player.xml`
- `TvSeriesPlayerActivity.java`
- Auto-play logic with countdown UI

### Phase 7: Integration & Polish
**Goal**: Wire everything to DescriptionActivity

**Tasks**:
1. Update `DescriptionActivity.launchConsumer()`:
   - Anime → `EpisodeSelectorActivity` ✅
   - Manga → `MangaReaderActivity` (direct launch, no selection)
   - Novel → `NovelReaderActivity` (direct launch)
   - Movie → `MoviePlayerActivity` (direct launch)
   - TV Show → `TvSeriesPlayerActivity` (season/episode UI)
2. Add loading states for all media types
3. Test end-to-end flows
4. Add analytics/tracking

**Deliverables**:
- Updated routing logic in DescriptionActivity
- Comprehensive testing

---

## 🚀 User Flows (Zero Interaction)

### Novel Reading
```
User taps "Read" on Novel
    ↓
NovelReaderActivity launches
    ↓
Auto-resolves Chapter 1 (or saved progress)
    ↓
Displays text instantly
    ↓
User scrolls → reaches 90%
    ↓
Auto-prefetches Chapter 2
    ↓
User swipes right → Chapter 2 loads instantly
```

### Manga Reading
```
User taps "Read" on Manga
    ↓
MangaReaderActivity launches
    ↓
Auto-resolves Chapter 1 pages
    ↓
Displays images in vertical scroll
    ↓
User reaches last page
    ↓
Auto-loads Chapter 2
```

### Movie Watching
```
User taps "Watch" on Movie
    ↓
Shows loading: "Finding stream..."
    ↓
MovieResolver resolves URL (~3-5s)
    ↓
PlayerActivity launches
    ↓
Movie plays instantly
```

### TV Series Binging
```
User taps "Watch" on TV Show
    ↓
TvSeriesPlayerActivity loads
    ↓
Shows: Season 1, Episode 1 (or saved progress)
    ↓
User taps "Play"
    ↓
Auto-resolves episode
    ↓
Plays video
    ↓
Episode ends → Shows countdown (10s)
    ↓
Auto-resolves Episode 2
    ↓
Continues playback
```

---

## 🎯 Success Criteria

1. **Zero Manual URLs**: Users never paste or search for links
2. **Instant Consumption**: Content loads within 5 seconds
3. **Seamless Navigation**: Chapter/episode transitions are smooth
4. **Offline Resilience**: Cache handles network drops gracefully
5. **Progress Sync**: All reading/watching progress auto-saves

---

## 📦 Dependencies & Libraries

### New Dependencies Needed
```gradle
// Image loading & caching
implementation("com.github.bumptech.glide:glide:4.16.0")

// Zoom/pan for manga
implementation("com.github.chrisbanes:PhotoView:2.3.0")

// Advanced RecyclerView
implementation("androidx.recyclerview:recyclerview:1.3.2")

// ViewPager2 for page-by-page manga
implementation("androidx.viewpager2:viewpager2:1.1.0")

// Existing: ExoPlayer, Retrofit, Gson ✅
```

### Consumet API Endpoints (Estimated)
```
https://api.consumet.org/manga/mangadex/{query}
https://api.consumet.org/manga/mangadex/read/{chapterId}
https://api.consumet.org/movies/flixhq/{query}
https://api.consumet.org/movies/flixhq/watch/{mediaId}
https://api.consumet.org/light-novels/{provider}/{query}
```

---

## 🔒 Edge Cases & Error Handling

1. **Content Not Found**: Show error message, offer manual search
2. **Network Timeout**: Retry 3x, then fallback to error UI
3. **Invalid Chapter/Episode**: Disable navigation buttons
4. **API Rate Limiting**: Implement request throttling
5. **Corrupted Images**: Show placeholder, allow retry

---

## 📝 Database Schema Updates

### Add Consumption Tracking
```sql
ALTER TABLE media_table ADD COLUMN last_read_chapter INTEGER DEFAULT 0;
ALTER TABLE media_table ADD COLUMN last_watched_season INTEGER DEFAULT 1;
ALTER TABLE media_table ADD COLUMN last_watched_episode INTEGER DEFAULT 1;
ALTER TABLE media_table ADD COLUMN consumption_timestamp INTEGER;
```

### Reading/Watching History
```sql
CREATE TABLE consumption_history (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    media_id INTEGER,
    media_type TEXT,
    chapter_episode TEXT,
    timestamp INTEGER,
    FOREIGN KEY(media_id) REFERENCES media_table(id)
);
```

---

## 🎨 UI Components Reuse

- **PlayerActivity** → Reuse for Movies, TV Shows, Anime ✅
- **Glass Backgrounds** → Apply to all new readers ✅
- **Loading States** → Unified component across all media types
- **Error Screens** → Shared fallback UI

---

## 🧪 Testing Strategy

### Unit Tests
- Each Resolver: Mock Retrofit responses
- Cache: Test prefetch logic
- Navigation: Test chapter/episode transitions

### Integration Tests
- End-to-end flows for each media type
- Network failure scenarios
- Progress persistence

### Manual Tests
- Read 3 chapters sequentially (novels & manga)
- Watch 3 episodes sequentially (TV shows)
- Test offline mode with cache

---

## 📊 Performance Targets

| Metric | Target |
|--------|--------|
| Chapter/Episode Resolution | < 3 seconds |
| Page Load Time (Manga) | < 1 second |
| Next Chapter Prefetch | < 5 seconds |
| Memory Usage (Reader) | < 150 MB |
| App Launch to Consumption | < 8 seconds |

---

## 🚧 Future Enhancements

1. **Offline Downloads**: Pre-download chapters/episodes
2. **Multi-Provider Support**: Fallback to alternative sources
3. **Reading Statistics**: Track words/pages per day
4. **Custom Reading Lists**: Queue chapters across multiple novels
5. **Sync Across Devices**: Cloud-based progress sync

---

**Next Action**: Wait for user confirmation to proceed with implementation.
