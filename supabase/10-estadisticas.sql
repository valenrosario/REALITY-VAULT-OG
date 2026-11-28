-- Estadísticas simples para el panel (solo el admin puede verlas).
-- Pegalo en Supabase → SQL Editor → Run. Se puede correr más de una vez.
create or replace function public.admin_stats()
returns json language plpgsql stable security definer set search_path = public, auth as $$
declare r json;
begin
  if not public.is_admin() then raise exception 'solo admin'; end if;
  select json_build_object(
    'users',        (select count(*) from auth.users),
    'users7',       (select count(*) from auth.users where created_at > now() - interval '7 days'),
    'active1',      (select count(distinct user_id) from public.watch_progress where updated_at > now() - interval '24 hours'),
    'active7',      (select count(distinct user_id) from public.watch_progress where updated_at > now() - interval '7 days'),
    'watchingNow',  (select count(distinct user_id) from public.watch_progress where updated_at > now() - interval '10 minutes'),
    'visits1',      (select count(distinct viewer) from public.title_views where viewed_at > now() - interval '24 hours'),
    'visits7',      (select count(distinct viewer) from public.title_views where viewed_at > now() - interval '7 days'),
    'daily',        (select coalesce(json_agg(json_build_object('d', d, 'n', n) order by d), '[]'::json) from (
                       select to_char(date_trunc('day', viewed_at at time zone 'America/Argentina/Buenos_Aires'), 'YYYY-MM-DD') d, count(distinct viewer) n
                       from public.title_views where viewed_at > now() - interval '14 days' group by 1) x),
    'topViews',     (select coalesce(json_agg(json_build_object('t', title_id, 'n', n) order by n desc), '[]'::json) from (
                       select title_id, count(distinct viewer) n from public.title_views where viewed_at > now() - interval '30 days'
                       group by title_id order by n desc limit 10) x),
    'topWatched',   (select coalesce(json_agg(json_build_object('t', title_id, 'n', n) order by n desc), '[]'::json) from (
                       select title_id, count(*) n from public.watch_progress where updated_at > now() - interval '30 days'
                       group by title_id order by n desc limit 10) x),
    'recent',       (select coalesce(json_agg(json_build_object('t', title_id, 's', last_season, 'e', last_episode, 'at', updated_at) order by updated_at desc), '[]'::json) from (
                       select title_id, last_season, last_episode, updated_at from public.watch_progress order by updated_at desc limit 12) x)
  ) into r;
  return r;
end $$;
revoke all on function public.admin_stats() from public, anon;
grant execute on function public.admin_stats() to authenticated;
