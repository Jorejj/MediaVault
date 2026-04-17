-- MediaVault Supabase RLS policies (v1)
-- Run after schema_v1.sql

alter table public.profiles enable row level security;
alter table public.media_library enable row level security;
alter table public.progress_log enable row level security;
alter table public.daily_metrics enable row level security;
alter table public.media_metadata enable row level security;
alter table public.user_media_events enable row level security;
alter table public.user_feature_vectors enable row level security;

-- Helper to determine admin role from profile.
create or replace function public.is_admin()
returns boolean
language sql
stable
as $$
  select exists (
    select 1 from public.profiles p
    where p.id = auth.uid() and p.role = 'admin'
  );
$$;

-- profiles
drop policy if exists profiles_select_self_or_admin on public.profiles;
create policy profiles_select_self_or_admin
on public.profiles for select
using (id = auth.uid() or public.is_admin());

drop policy if exists profiles_insert_self on public.profiles;
create policy profiles_insert_self
on public.profiles for insert
with check (id = auth.uid());

drop policy if exists profiles_update_self_or_admin on public.profiles;
create policy profiles_update_self_or_admin
on public.profiles for update
using (id = auth.uid() or public.is_admin())
with check (id = auth.uid() or public.is_admin());

-- user-owned tables
drop policy if exists media_library_rw_self_or_admin on public.media_library;
create policy media_library_rw_self_or_admin
on public.media_library
for all
using (user_id = auth.uid() or public.is_admin())
with check (user_id = auth.uid() or public.is_admin());

drop policy if exists progress_log_rw_self_or_admin on public.progress_log;
create policy progress_log_rw_self_or_admin
on public.progress_log
for all
using (user_id = auth.uid() or public.is_admin())
with check (user_id = auth.uid() or public.is_admin());

drop policy if exists daily_metrics_rw_self_or_admin on public.daily_metrics;
create policy daily_metrics_rw_self_or_admin
on public.daily_metrics
for all
using (user_id = auth.uid() or public.is_admin())
with check (user_id = auth.uid() or public.is_admin());

drop policy if exists user_media_events_rw_self_or_admin on public.user_media_events;
create policy user_media_events_rw_self_or_admin
on public.user_media_events
for all
using (user_id = auth.uid() or public.is_admin())
with check (user_id = auth.uid() or public.is_admin());

drop policy if exists user_feature_vectors_rw_self_or_admin on public.user_feature_vectors;
create policy user_feature_vectors_rw_self_or_admin
on public.user_feature_vectors
for all
using (user_id = auth.uid() or public.is_admin())
with check (user_id = auth.uid() or public.is_admin());

-- media_metadata inherits ownership from media row
drop policy if exists media_metadata_rw_self_or_admin on public.media_metadata;
create policy media_metadata_rw_self_or_admin
on public.media_metadata
for all
using (
  exists (
    select 1
    from public.media_library ml
    where ml.id = media_metadata.media_id
      and (ml.user_id = auth.uid() or public.is_admin())
  )
)
with check (
  exists (
    select 1
    from public.media_library ml
    where ml.id = media_metadata.media_id
      and (ml.user_id = auth.uid() or public.is_admin())
  )
);
