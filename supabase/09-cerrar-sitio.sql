-- =====================================================================
-- REALITY VAULT · interruptor "sitio cerrado" (Admin → Sitio → Acceso)
-- Correr una sola vez en Supabase → SQL Editor.
--
-- Sin esto, el cierre funciona igual en la web y en las apps nuevas (muestran
-- "No disponible por ahora"). Con esto, además, la base deja de entregar el
-- catálogo a quien no sea admin mientras esté cerrado: sirve para las apps
-- viejas que todavía no se actualizaron y para que nadie lo lea por afuera.
-- =====================================================================
create or replace function public.site_open()
returns boolean language sql stable security definer set search_path = public as $$
  select coalesce((select (data->>'closed')::boolean from public.site where id = 'config'), false) is not true;
$$;
grant execute on function public.site_open() to anon, authenticated;

drop policy if exists "lectura publica" on public.titles;
create policy "lectura publica" on public.titles  for select using (public.site_open() or public.is_admin());

drop policy if exists "lectura publica" on public.banners;
create policy "lectura publica" on public.banners for select using (public.site_open() or public.is_admin());
-- La tabla "site" sigue siendo pública: ahí está el propio interruptor y el logo.
