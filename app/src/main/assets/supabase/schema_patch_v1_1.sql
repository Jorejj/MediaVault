-- Run this if schema_v1.sql was already applied before local_media_id was added.

alter table public.media_library
  add column if not exists local_media_id integer;

create unique index if not exists uq_media_library_user_local_media_id
  on public.media_library(user_id, local_media_id);
