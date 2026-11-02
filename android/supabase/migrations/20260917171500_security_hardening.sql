-- Applied to project <your-project-ref> on 17 Sep 2026 (migration name: security_hardening).

-- 1. Internal functions are not API endpoints. Triggers and event triggers still run (EXECUTE is checked at creation, not firing).
revoke execute on function public.rls_auto_enable() from public, anon, authenticated;
revoke execute on function public.touch_updated_at() from public, anon, authenticated;
revoke execute on function public.protect_trial_start() from public, anon, authenticated;
revoke execute on function public.limit_contributions() from public, anon, authenticated;
-- bc_normalize stays executable: match_product runs as the caller and needs it.

-- 2. Deny by default: future tables and functions in public get no API access until explicitly granted.
alter default privileges for role postgres in schema public revoke all on tables from anon, authenticated;
alter default privileges for role postgres in schema public revoke all on sequences from anon, authenticated;
alter default privileges for role postgres in schema public revoke execute on functions from public, anon, authenticated;

-- 3. RLS applies even to the table owner for every data table.
alter table public.products force row level security;
alter table public.product_contributions force row level security;

-- 4. Clients cannot rewrite audit timestamps.
create or replace function public.touch_updated_at() returns trigger
    language plpgsql set search_path = '' as $$
begin
    if tg_op = 'UPDATE' then
        new.created_at = old.created_at;
    end if;
    new.updated_at = now();
    return new;
end $$;
revoke execute on function public.touch_updated_at() from public, anon, authenticated;

drop trigger if exists profiles_touch on public.profiles;
create trigger profiles_touch before insert or update on public.profiles
    for each row execute function public.touch_updated_at();

-- Contributions: timestamp and review status are always server-controlled.
create or replace function public.limit_contributions() returns trigger
    language plpgsql security definer set search_path = '' as $$
begin
    new.created_at = now();
    new.status = 'pending';
    if (select count(*) from public.product_contributions c
        where c.user_id = new.user_id and c.created_at > now() - interval '1 day') >= 30 then
        raise exception 'Daily contribution limit reached' using errcode = 'P0001';
    end if;
    return new;
end $$;
revoke execute on function public.limit_contributions() from public, anon, authenticated;

-- 5. Cheap-query guard for the anonymous product lookup.
alter function public.match_product(text, numeric, text) set statement_timeout = '2s';
