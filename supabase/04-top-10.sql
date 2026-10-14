-- =====================================================================
-- REALITY VAULT · "Top 10 hoy"
-- Pegá este archivo en Supabase → SQL Editor → New query → Run.
-- Devuelve los 10 títulos que más personas distintas abrieron en las
-- últimas 24 horas. Solo entrega totales: nadie ve qué miró cada usuario.
-- =====================================================================

-- 1. Tabla de "Seguir viendo" (se crea solo si todavía no existe)
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

-- 2. Función del Top 10
create or replace function public.top10()
returns table (title_id text, viewers bigint)
language sql
stable
security definer
set search_path = public
as $$
  select wp.title_id, count(distinct wp.user_id) as viewers
  from public.watch_progress wp
  where wp.updated_at > now() - interval '24 hours'
  group by wp.title_id
  order by viewers desc, max(wp.updated_at) desc
  limit 10;
$$;

grant execute on function public.top10() to anon, authenticated;
