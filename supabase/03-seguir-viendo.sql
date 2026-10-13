-- =====================================================================
-- REALITY VAULT · "Seguir viendo"
-- Pegá este archivo en Supabase → SQL Editor → New query → Run.
-- Guarda, por usuario, el último capítulo que abrió de cada título.
-- =====================================================================

create table if not exists public.watch_progress (
  user_id      uuid not null references auth.users(id) on delete cascade,
  title_id     text not null,
  last_season  int,
  last_episode int,
  watched      jsonb not null default '[]'::jsonb,
  updated_at   timestamptz not null default now(),
  primary key (user_id, title_id)
);

alter table public.watch_progress enable row level security;

drop policy if exists "ver mi progreso"        on public.watch_progress;
drop policy if exists "guardar mi progreso"    on public.watch_progress;
drop policy if exists "actualizar mi progreso" on public.watch_progress;
drop policy if exists "borrar mi progreso"     on public.watch_progress;
create policy "ver mi progreso"        on public.watch_progress for select using (auth.uid() = user_id);
create policy "guardar mi progreso"    on public.watch_progress for insert with check (auth.uid() = user_id);
create policy "actualizar mi progreso" on public.watch_progress for update using (auth.uid() = user_id) with check (auth.uid() = user_id);
create policy "borrar mi progreso"     on public.watch_progress for delete using (auth.uid() = user_id);
