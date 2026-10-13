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
