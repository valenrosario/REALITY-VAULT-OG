-- REALITY VAULT · guardar el minuto por el que vas (para Seguir viendo)
-- Pegá todo en Supabase → SQL Editor → New query → Run.
alter table public.watch_progress add column if not exists positions jsonb not null default '{}'::jsonb;
