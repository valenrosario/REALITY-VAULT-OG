-- =====================================================================
-- REALITY VAULT · "Top 10 hoy"
-- Pegá este archivo en Supabase → SQL Editor → New query → Run.
-- Devuelve los 10 títulos que más personas distintas abrieron en las
-- últimas 24 horas. Solo entrega totales: nadie ve qué miró cada usuario.
-- =====================================================================

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
