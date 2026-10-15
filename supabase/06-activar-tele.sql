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
