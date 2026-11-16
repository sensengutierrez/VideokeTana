-- =====================================================
-- VIDEOKE APP — Supabase schema (Phase 1)
-- Naka-apply na ito sa project na VideokeTana.
-- Gamitin lang kung gagawa ulit ng bagong Supabase project.
-- =====================================================

-- ---------- SONGS (library) ----------
create sequence public.song_code_seq start 10001;

create table public.songs (
  id uuid primary key default gen_random_uuid(),
  code int not null unique default nextval('public.song_code_seq'),
  title text not null,
  artist text,
  kind text not null check (kind in ('video','audio')),
  media_path text not null,
  lrc_path text,
  size_bytes bigint,
  created_at timestamptz not null default now()
);
alter sequence public.song_code_seq owned by public.songs.code;
create index songs_title_idx on public.songs (lower(title));
create index songs_artist_idx on public.songs (lower(artist));

-- ---------- QUEUE (pila per room) ----------
create table public.queue (
  id uuid primary key default gen_random_uuid(),
  room text not null check (room ~ '^[A-Z0-9]{4,8}$'),
  song_id uuid references public.songs(id) on delete cascade,
  yt_id text,
  yt_title text,
  singer text check (char_length(singer) <= 40),
  sort_order double precision not null default (extract(epoch from now()) * 1000),
  status text not null default 'waiting' check (status in ('waiting','playing')),
  created_at timestamptz not null default now(),
  check (song_id is not null or yt_id is not null)
);
create index queue_room_idx on public.queue (room, status, sort_order);
create index queue_song_idx on public.queue (song_id);

-- ---------- ADMINS (unang nag-sign up = admin) ----------
create table public.admins (
  user_id uuid primary key references auth.users(id) on delete cascade,
  created_at timestamptz not null default now()
);

create or replace function public.is_admin()
returns boolean language sql stable security invoker set search_path = ''
as $$ select exists (select 1 from public.admins where user_id = (select auth.uid())) $$;

create or replace function public.handle_first_admin()
returns trigger language plpgsql security definer set search_path = ''
as $$
begin
  if not exists (select 1 from public.admins) then
    insert into public.admins (user_id) values (new.id);
  end if;
  return new;
end $$;

create trigger on_auth_user_created_first_admin
  after insert on auth.users
  for each row execute function public.handle_first_admin();

revoke execute on function public.handle_first_admin() from public, anon, authenticated;
revoke execute on function public.is_admin() from public, anon;
grant execute on function public.is_admin() to authenticated;

-- ---------- RLS ----------
alter table public.songs enable row level security;
alter table public.queue enable row level security;
alter table public.admins enable row level security;

create policy "songs readable by all" on public.songs for select to anon, authenticated using (true);
create policy "songs insert admin" on public.songs for insert to authenticated with check ((select public.is_admin()));
create policy "songs update admin" on public.songs for update to authenticated using ((select public.is_admin())) with check ((select public.is_admin()));
create policy "songs delete admin" on public.songs for delete to authenticated using ((select public.is_admin()));

create policy "queue read" on public.queue for select to anon, authenticated using (true);
create policy "queue insert" on public.queue for insert to anon, authenticated with check (true);
create policy "queue update" on public.queue for update to anon, authenticated using (true) with check (true);
create policy "queue delete" on public.queue for delete to anon, authenticated using (true);

create policy "admins read self" on public.admins for select to authenticated using (user_id = (select auth.uid()));

grant select on public.songs to anon, authenticated;
grant insert, update, delete on public.songs to authenticated;
grant usage on sequence public.song_code_seq to authenticated;
grant select, insert, update, delete on public.queue to anon, authenticated;
grant select on public.admins to authenticated;

-- ---------- REALTIME ----------
alter publication supabase_realtime add table public.queue;
alter publication supabase_realtime add table public.songs;

-- ---------- STORAGE (public read, admin write, 50MB/file) ----------
insert into storage.buckets (id, name, public, file_size_limit)
values ('songs', 'songs', true, 52428800)
on conflict (id) do nothing;

create policy "songs bucket admin select" on storage.objects for select to authenticated
  using (bucket_id = 'songs' and (select public.is_admin()));
create policy "songs bucket admin insert" on storage.objects for insert to authenticated
  with check (bucket_id = 'songs' and (select public.is_admin()));
create policy "songs bucket admin update" on storage.objects for update to authenticated
  using (bucket_id = 'songs' and (select public.is_admin()));
create policy "songs bucket admin delete" on storage.objects for delete to authenticated
  using (bucket_id = 'songs' and (select public.is_admin()));

-- ---------- v0.5: kategorya + YouTube na kanta sa library ----------
alter table public.songs add column if not exists license_note text;
alter table public.songs add column if not exists category text;
alter table public.songs add column if not exists yt_id text;
alter table public.songs drop constraint if exists songs_kind_check;
alter table public.songs add constraint songs_kind_check check (kind in ('video','audio','youtube'));
alter table public.songs alter column media_path drop not null;
alter table public.songs add constraint songs_media_check check ((kind = 'youtube' and yt_id is not null) or (kind <> 'youtube' and media_path is not null));
create index if not exists songs_category_idx on public.songs (category);
create index if not exists songs_created_idx on public.songs (created_at desc);

-- ---------- v0.6: background videos ----------
create table if not exists public.backgrounds (
  id uuid primary key default gen_random_uuid(),
  title text not null,
  path text not null,
  size_bytes bigint,
  created_at timestamptz not null default now()
);
alter table public.backgrounds enable row level security;
create policy "bg read all" on public.backgrounds for select to anon, authenticated using (true);
create policy "bg insert admin" on public.backgrounds for insert to authenticated with check ((select public.is_admin()));
create policy "bg update admin" on public.backgrounds for update to authenticated using ((select public.is_admin())) with check ((select public.is_admin()));
create policy "bg delete admin" on public.backgrounds for delete to authenticated using ((select public.is_admin()));
grant select on public.backgrounds to anon, authenticated;
grant insert, update, delete on public.backgrounds to authenticated;
