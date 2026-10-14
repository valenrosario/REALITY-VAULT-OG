-- =====================================================================
-- REALITY VAULT · "Top 10 hoy" contando las visitas a cada serie
-- Pegá este archivo en Supabase → SQL Editor → New query → Run.
-- Cada vez que alguien abre la página de un título (con o sin cuenta)
-- se suma una visita. El Top 10 cuenta personas distintas en 24 horas.
-- =====================================================================
create table if not exists public.title_views (id bigint generated always as identity primary key, title_id text not null, viewer text not null, viewed_at timestamptz not null default now());
alter table public.title_views enable row level security;
drop policy if exists "registrar visita" on public.title_views;
create policy "registrar visita" on public.title_views for insert with check (char_length(title_id) < 100 and char_length(viewer) < 100);
create or replace function public.top10() returns table (title_id text, viewers bigint) language sql stable security definer set search_path = public as $$ select v.title_id, count(distinct v.viewer) as viewers from public.title_views v where v.viewed_at > now() - interval '24 hours' group by v.title_id order by viewers desc, max(v.viewed_at) desc limit 10; $$;
grant execute on function public.top10() to anon, authenticated;
