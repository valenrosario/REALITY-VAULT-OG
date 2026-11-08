-- REALITY VAULT · ajustes de seguridad (opcional, recomendado antes de abrir a usuarios)
-- Pegalo en Supabase → SQL Editor → New query → Run.

-- 1) Top 10 de la SEMANA (últimos 7 días) y por PERSONAS distintas por título (antes era solo
--    de hoy y contaba cada visita, así que alguien podía inflar un título recargando la página).
create or replace function public.top10() returns table (title_id text, viewers bigint)
language sql stable security definer set search_path = public as $$
  select v.title_id, count(distinct v.viewer) as viewers
  from public.title_views v
  where v.viewed_at > now() - interval '7 days'
  group by v.title_id
  order by count(distinct v.viewer) desc, max(v.viewed_at) desc
  limit 10;
$$;
grant execute on function public.top10() to anon, authenticated;

-- 2) Limpieza: las visitas de más de 8 días ya no sirven para nada.
delete from public.title_views where viewed_at < now() - interval '8 days';
