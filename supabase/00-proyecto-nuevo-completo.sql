-- =====================================================================
-- REALITY VAULT · estructura de la base de datos para Supabase
-- Pegá TODO este archivo en Supabase → SQL Editor → New query → Run.
-- Antes de correrlo, cambiá el email de la última sección por el tuyo.
-- =====================================================================

-- 1. Tablas -----------------------------------------------------------
create table if not exists public.titles (
  id text primary key,
  data jsonb not null,
  updated_at timestamptz not null default now()
);

create table if not exists public.banners (
  id text primary key,
  data jsonb not null,
  updated_at timestamptz not null default now()
);

create table if not exists public.site (
  id text primary key,
  data jsonb not null,
  updated_at timestamptz not null default now()
);

-- Lista de emails con permiso para usar el panel de control.
create table if not exists public.admins (
  email text primary key
);

-- 2. ¿Quién es administrador? -----------------------------------------
create or replace function public.is_admin()
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1 from public.admins
    where lower(email) = lower(coalesce(auth.jwt() ->> 'email', ''))
  );
$$;

grant execute on function public.is_admin() to anon, authenticated;

-- 3. Permisos: todos leen, solo el administrador escribe ---------------
alter table public.titles  enable row level security;
alter table public.banners enable row level security;
alter table public.site    enable row level security;
alter table public.admins  enable row level security;  -- sin políticas: nadie la lee desde la web

drop policy if exists "lectura publica" on public.titles;
drop policy if exists "solo admin escribe" on public.titles;
create policy "lectura publica"    on public.titles for select using (true);
create policy "solo admin escribe" on public.titles for all using (public.is_admin()) with check (public.is_admin());

drop policy if exists "lectura publica" on public.banners;
drop policy if exists "solo admin escribe" on public.banners;
create policy "lectura publica"    on public.banners for select using (true);
create policy "solo admin escribe" on public.banners for all using (public.is_admin()) with check (public.is_admin());

drop policy if exists "lectura publica" on public.site;
drop policy if exists "solo admin escribe" on public.site;
create policy "lectura publica"    on public.site for select using (true);
create policy "solo admin escribe" on public.site for all using (public.is_admin()) with check (public.is_admin());

-- 4. Actualización en vivo (los cambios aparecen sin recargar) ---------
do $$
begin
  begin alter publication supabase_realtime add table public.titles;  exception when others then null; end;
  begin alter publication supabase_realtime add table public.banners; exception when others then null; end;
  begin alter publication supabase_realtime add table public.site;    exception when others then null; end;
end $$;

-- 5. Carpeta de imágenes (pósters, fondos, logos) ----------------------
insert into storage.buckets (id, name, public)
values ('media', 'media', true)
on conflict (id) do update set public = true;

drop policy if exists "media lectura publica" on storage.objects;
drop policy if exists "media admin sube"      on storage.objects;
drop policy if exists "media admin edita"     on storage.objects;
drop policy if exists "media admin borra"     on storage.objects;
create policy "media lectura publica" on storage.objects for select using (bucket_id = 'media');
create policy "media admin sube"      on storage.objects for insert with check (bucket_id = 'media' and public.is_admin());
create policy "media admin edita"     on storage.objects for update using (bucket_id = 'media' and public.is_admin());
create policy "media admin borra"     on storage.objects for delete using (bucket_id = 'media' and public.is_admin());

-- 6. TU EMAIL DE ADMINISTRADOR ----------------------------------------
-- Cambiá el email por el que vas a usar para entrar al panel.
insert into public.admins (email) values ('TU_EMAIL@gmail.com')
on conflict (email) do nothing;

-- ===== 02-cuentas-y-mi-lista.sql =====
-- =====================================================================
-- REALITY VAULT · cuentas de usuarios y "Mi lista"
-- Pegá este archivo en Supabase → SQL Editor → New query → Run.
-- (Se corre una sola vez, después de schema.sql)
-- =====================================================================

create table if not exists public.user_lists (
  user_id    uuid not null references auth.users(id) on delete cascade,
  title_id   text not null,
  created_at timestamptz not null default now(),
  primary key (user_id, title_id)
);

alter table public.user_lists enable row level security;

-- Cada persona ve, agrega y quita solo lo de su propia lista.
drop policy if exists "ver mi lista"        on public.user_lists;
drop policy if exists "agregar a mi lista"  on public.user_lists;
drop policy if exists "quitar de mi lista"  on public.user_lists;
create policy "ver mi lista"       on public.user_lists for select using (auth.uid() = user_id);
create policy "agregar a mi lista" on public.user_lists for insert with check (auth.uid() = user_id);
create policy "quitar de mi lista" on public.user_lists for delete using (auth.uid() = user_id);

-- ===== 03-seguir-viendo.sql =====
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

-- ===== 04-top-10.sql =====
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

-- ===== 05-top-10-visitas.sql =====
-- =====================================================================
-- REALITY VAULT · "Top 10 hoy" contando las visitas a cada serie
-- Pegá este archivo en Supabase → SQL Editor → New query → Run.
-- Cada vez que alguien abre la página de un título (con o sin cuenta)
-- se suma una visita. El Top 10 ordena por cantidad de visitas en 24 horas.
-- =====================================================================
create table if not exists public.title_views (id bigint generated always as identity primary key, title_id text not null, viewer text not null, viewed_at timestamptz not null default now());
alter table public.title_views enable row level security;
drop policy if exists "registrar visita" on public.title_views;
create policy "registrar visita" on public.title_views for insert with check (char_length(title_id) < 100 and char_length(viewer) < 100);
create or replace function public.top10() returns table (title_id text, viewers bigint) language sql stable security definer set search_path = public as $$ select v.title_id, count(*) as viewers from public.title_views v where v.viewed_at > now() - interval '24 hours' group by v.title_id order by count(*) desc, max(v.viewed_at) desc limit 10; $$;
grant execute on function public.top10() to anon, authenticated;

-- ===== 06-activar-tele.sql =====
-- REALITY VAULT · activar la tele con el celular
-- Pegá todo en Supabase → SQL Editor → New query → Run. (Cada instrucción va en una sola línea a propósito.)
create table if not exists public.tv_codes (code text primary key, secret text not null, user_id uuid references auth.users(id) on delete cascade, created_at timestamptz not null default now(), expires_at timestamptz not null default now() + interval '15 minutes');
alter table public.tv_codes enable row level security;
create or replace function public.tv_code_create(p_code text, p_secret text) returns boolean language plpgsql security definer set search_path = public as $$ begin delete from public.tv_codes where expires_at < now() - interval '1 hour'; if char_length(p_code) <> 6 or char_length(p_secret) < 20 then return false; end if; insert into public.tv_codes (code, secret) values (upper(p_code), p_secret); return true; exception when unique_violation then return false; end; $$;
create or replace function public.tv_code_status(p_code text, p_secret text) returns text language sql security definer set search_path = public as $$ select coalesce((select case when user_id is not null then 'listo' when expires_at < now() then 'vencido' else 'esperando' end from public.tv_codes where code = upper(p_code) and secret = p_secret), 'vencido'); $$;
create or replace function public.tv_code_claim(p_code text) returns text language plpgsql security definer set search_path = public as $$ begin if auth.uid() is null then return 'sin-sesion'; end if; update public.tv_codes set user_id = auth.uid() where code = upper(replace(replace(p_code, '-', ''), ' ', '')) and user_id is null and expires_at > now(); if found then return 'ok'; end if; return 'invalido'; end; $$;
grant execute on function public.tv_code_create(text, text) to anon, authenticated;
grant execute on function public.tv_code_status(text, text) to anon, authenticated;
grant execute on function public.tv_code_claim(text) to authenticated;

-- ===== 07-posicion-de-reproduccion.sql =====
-- REALITY VAULT · guardar el minuto por el que vas (para Seguir viendo)
-- Pegá todo en Supabase → SQL Editor → New query → Run.
alter table public.watch_progress add column if not exists positions jsonb not null default '{}'::jsonb;

-- ===== 08-seguridad.sql =====
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

-- ===== 09-cerrar-sitio.sql =====
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
