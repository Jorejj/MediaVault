-- MediaVault Supabase foundation schema (v1)
-- Run in Supabase SQL Editor

create extension if not exists pgcrypto;

-- Auth users live in auth.users; this profile stores app-level user data.
create table if not exists public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  email text unique,
  display_name text,
  avatar_url text,
  role text not null default 'user' check (role in ('user', 'admin')),
  -- Signals for homepage ranking/recommendation strategy.
  preferred_types text[] not null default '{}',
  preferred_genres text[] not null default '{}',
  preferred_units text[] not null default '{}',
  avg_session_minutes numeric not null default 0,
  binge_score numeric not null default 0,
  completion_rate numeric not null default 0,
  recency_bias numeric not null default 0,
  diversity_bias numeric not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table if not exists public.media_library (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  api_id text,
  title text not null,
  description text,
  creator text,
  media_type text not null,
  genre text,
  image_path text,
  current_progress numeric not null default 0,
  previous_progress numeric not null default 0,
  total_count integer not null check (total_count > 0),
  capacity_unit text not null check (capacity_unit in ('Pages', 'Episodes', 'Minutes', 'Chapters')),
  runtime text,
  status text not null default 'Planning' check (status in ('Ongoing', 'Completed', 'Planning', 'Dropped', 'Recently Deleted')),
  user_rating numeric not null default 0 check (user_rating >= 0 and user_rating <= 5),
  personal_review text,
  memory_journal text,
  finish_mood text,
  priority_level text not null default 'Medium' check (priority_level in ('High', 'Medium', 'Low')),
  date_added timestamptz not null default now(),
  last_updated timestamptz not null default now(),
  is_favorite boolean not null default false,
  source_url text,
  content_type text,
  current_season integer not null default 1 check (current_season >= 1),
  current_episode integer not null default 1 check (current_episode >= 1),
  -- Feature columns for recommendation algorithms.
  watch_time_minutes numeric not null default 0,
  completion_ratio numeric not null default 0,
  engagement_score numeric not null default 0,
  skip_count integer not null default 0,
  replay_count integer not null default 0,
  last_consumed_at timestamptz,
  unique (user_id, lower(title), media_type)
);

create index if not exists idx_media_library_user_status on public.media_library(user_id, status);
create index if not exists idx_media_library_user_type on public.media_library(user_id, media_type);
create index if not exists idx_media_library_user_updated on public.media_library(user_id, last_updated desc);

create table if not exists public.progress_log (
  id bigint generated always as identity primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  media_id uuid not null references public.media_library(id) on delete cascade,
  progress_added numeric not null,
  log_date date not null default current_date
);

create index if not exists idx_progress_log_user_date on public.progress_log(user_id, log_date desc);

create table if not exists public.daily_metrics (
  id bigint generated always as identity primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  date text not null,
  chapters_read integer not null default 0,
  episodes_watched integer not null default 0,
  minutes_watched integer not null default 0,
  goal_met boolean not null default false,
  unique (user_id, date)
);

create table if not exists public.media_metadata (
  media_id uuid primary key references public.media_library(id) on delete cascade,
  canonical_title text,
  normalized_title text,
  alt_titles_json jsonb,
  provider_id text,
  provider_slug text,
  canonical_url text,
  metadata_source text,
  metadata_media_type text,
  metadata_sub_type text,
  metadata_language text,
  metadata_region text,
  metadata_status text,
  metadata_release_year integer,
  metadata_total_count integer,
  metadata_unit text,
  genres_json jsonb,
  tags_json jsonb,
  external_ids_json jsonb,
  metadata_rating numeric,
  metadata_popularity numeric,
  provider_features_json jsonb,
  metadata_confidence numeric default 0,
  metadata_priority integer default 0,
  metadata_updated_at timestamptz not null default now()
);

-- Event stream for homepage recommendation and "FYP-like" ranking.
create table if not exists public.user_media_events (
  id bigint generated always as identity primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  media_id uuid references public.media_library(id) on delete set null,
  event_type text not null check (event_type in (
    'impression', 'open', 'play_start', 'progress', 'complete', 'favorite', 'unfavorite', 'skip', 'search_click'
  )),
  event_value numeric not null default 0,
  source_surface text,
  device_platform text default 'android',
  created_at timestamptz not null default now()
);

create index if not exists idx_user_media_events_user_created on public.user_media_events(user_id, created_at desc);
create index if not exists idx_user_media_events_user_type on public.user_media_events(user_id, event_type);

-- Materialized-style profile features (can be recomputed by cron/edge function).
create table if not exists public.user_feature_vectors (
  user_id uuid primary key references auth.users(id) on delete cascade,
  top_genres jsonb not null default '[]'::jsonb,
  top_types jsonb not null default '[]'::jsonb,
  active_hours jsonb not null default '[]'::jsonb,
  completion_distribution jsonb not null default '{}'::jsonb,
  freshness_preference numeric not null default 0,
  novelty_preference numeric not null default 0,
  quality_preference numeric not null default 0,
  updated_at timestamptz not null default now()
);

-- Trigger helper
create or replace function public.touch_updated_at()
returns trigger
language plpgsql
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

drop trigger if exists trg_profiles_updated_at on public.profiles;
create trigger trg_profiles_updated_at
before update on public.profiles
for each row execute function public.touch_updated_at();
