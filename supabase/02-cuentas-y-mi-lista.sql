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
