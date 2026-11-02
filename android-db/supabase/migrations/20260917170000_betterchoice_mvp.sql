-- BetterChoice MVP schema for project <your-project-ref> (ap-south-1).
-- Compatible with BOTH test builds: Claude's "BetterChoice India" and Codex's "BetterChoice".
-- Every table uses row-level security; the apps only use the publishable key plus the user's session.

create extension if not exists pg_trgm with schema extensions;

-- ---------------------------------------------------------------------------
-- Profiles: one row per verified user. All answers are optional.
-- Union of both apps' columns and answer options; each app validates its own rules
-- (BetterChoice India additionally requires adult_confirmed before the bubble works).
-- ---------------------------------------------------------------------------
create table public.profiles (
    user_id uuid primary key references auth.users (id) on delete cascade,
    gender text check (char_length(gender) <= 60),
    age smallint check (age between 1 and 120),
    weight_kg numeric check (weight_kg between 10 and 500 and weight_kg = round(weight_kg, 1)),
    height_cm numeric check (height_cm between 40 and 260 and height_cm = round(height_cm, 1)),
    diet text check (diet in ('Vegetarian', 'Non-vegetarian', 'Vegan')),
    goal text check (goal in ('Weight loss', 'Weight gain', 'Muscle gain', 'Improve nutritional awareness',
                              'Nutritional awareness', 'Something else')),
    other_reason text check (char_length(other_reason) <= 300),
    adult_confirmed boolean not null default false,
    trial_started_at timestamptz,
    onboarding_complete boolean not null default false,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

alter table public.profiles enable row level security;
alter table public.profiles force row level security;
revoke all on table public.profiles from public, anon, authenticated;
grant select, insert, update, delete on table public.profiles to authenticated;

create policy "Read own profile" on public.profiles
    for select to authenticated using ((select auth.uid()) = user_id);
create policy "Create own profile" on public.profiles
    for insert to authenticated with check ((select auth.uid()) = user_id);
create policy "Edit own profile" on public.profiles
    for update to authenticated using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);
create policy "Delete own profile" on public.profiles
    for delete to authenticated using ((select auth.uid()) = user_id);

create function public.touch_updated_at() returns trigger
    language plpgsql set search_path = '' as $$
begin
    new.updated_at = now();
    return new;
end $$;

create trigger profiles_touch before update on public.profiles
    for each row execute function public.touch_updated_at();

-- A trial starts once; clients cannot move it later or into the future.
create function public.protect_trial_start() returns trigger
    language plpgsql set search_path = '' as $$
begin
    if tg_op = 'UPDATE' and old.trial_started_at is not null then
        new.trial_started_at = old.trial_started_at;
    elsif new.trial_started_at is not null and new.trial_started_at > now() + interval '1 hour' then
        new.trial_started_at = now();
    end if;
    return new;
end $$;

create trigger profiles_protect_trial before insert or update on public.profiles
    for each row execute function public.protect_trial_start();

-- ---------------------------------------------------------------------------
-- Products: verified nutrition per 100 g / 100 ml. Readable by the apps, written only by admins.
-- ---------------------------------------------------------------------------
create function public.bc_normalize(value text) returns text
    language sql immutable parallel safe set search_path = '' as $$
    select btrim(regexp_replace(lower(coalesce(value, '')), '[^[:alnum:]]+', ' ', 'g'))
$$;

create table public.products (
    id uuid primary key default gen_random_uuid(),
    name text not null check (char_length(name) between 3 and 200),
    name_normalized text generated always as (public.bc_normalize(name)) stored,
    brand text,
    retailer text,
    price_inr numeric(10, 2) check (price_inr >= 0),
    energy_kcal numeric(6, 1) not null check (energy_kcal between 0 and 950),
    sugars_g numeric(5, 2) check (sugars_g between 0 and 100),
    saturated_fat_g numeric(5, 2) check (saturated_fat_g between 0 and 100),
    sodium_mg numeric(7, 1) check (sodium_mg between 0 and 40000),
    fibre_g numeric(5, 2) check (fibre_g between 0 and 100),
    protein_g numeric(5, 2) check (protein_g between 0 and 100),
    is_beverage boolean not null default false,
    source text not null default 'label',
    verified boolean not null default false,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index products_name_trgm on public.products using gin (name_normalized extensions.gin_trgm_ops);

alter table public.products enable row level security;
revoke all on table public.products from public, anon, authenticated;
grant select on table public.products to anon, authenticated;

create policy "Anyone reads verified products" on public.products
    for select to anon, authenticated using (verified);

create trigger products_touch before update on public.products
    for each row execute function public.touch_updated_at();

-- Best verified match by name similarity, preferring the same retailer and a close price.
create function public.match_product(p_name text, p_price numeric default null, p_retailer text default null)
returns table (
    product_id uuid, name text, energy_kcal numeric, sugars_g numeric, saturated_fat_g numeric,
    sodium_mg numeric, fibre_g numeric, protein_g numeric, is_beverage boolean, similarity real
)
language sql stable security invoker set search_path = '' as $$
    select p.id, p.name, p.energy_kcal, p.sugars_g, p.saturated_fat_g, p.sodium_mg, p.fibre_g, p.protein_g, p.is_beverage,
           extensions.similarity(p.name_normalized, public.bc_normalize(p_name)) as similarity
    from public.products p
    where p.verified
      and char_length(coalesce(p_name, '')) between 3 and 200
      and extensions.similarity(p.name_normalized, public.bc_normalize(p_name)) >= 0.45
    order by similarity desc,
             (p.retailer is not distinct from p_retailer) desc,
             abs(coalesce(p.price_inr, 0) - coalesce(p_price, p.price_inr, 0)) asc
    limit 1
$$;

revoke all on function public.match_product(text, numeric, text) from public;
grant execute on function public.match_product(text, numeric, text) to anon, authenticated;

-- ---------------------------------------------------------------------------
-- Contributions: "Add details" submissions, reviewed before becoming products.
-- ---------------------------------------------------------------------------
create table public.product_contributions (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
    product_name text not null check (char_length(product_name) between 3 and 200),
    retailer text not null check (retailer in ('com.grofers.customerapp', 'com.zeptoconsumerapp', 'in.swiggy.android',
        'in.swiggy.android.instamart', 'com.application.zomato', 'com.flipkart.android')),
    price_inr numeric(10, 2) check (price_inr >= 0),
    energy_kcal numeric(6, 1) not null check (energy_kcal between 0 and 950),
    sugars_g numeric(5, 2) check (sugars_g between 0 and 100),
    saturated_fat_g numeric(5, 2) check (saturated_fat_g between 0 and 100),
    sodium_mg numeric(7, 1) check (sodium_mg between 0 and 40000),
    fibre_g numeric(5, 2) check (fibre_g between 0 and 100),
    protein_g numeric(5, 2) check (protein_g between 0 and 100),
    is_beverage boolean not null default false,
    ingredients text check (char_length(ingredients) <= 2000),
    status text not null default 'pending' check (status in ('pending', 'approved', 'rejected')),
    created_at timestamptz not null default now()
);

alter table public.product_contributions enable row level security;
revoke all on table public.product_contributions from public, anon, authenticated;
grant select, insert on table public.product_contributions to authenticated;

create policy "Insert own pending contribution" on public.product_contributions
    for insert to authenticated with check ((select auth.uid()) = user_id and status = 'pending');
create policy "Read own contributions" on public.product_contributions
    for select to authenticated using ((select auth.uid()) = user_id);

create index product_contributions_user_created on public.product_contributions (user_id, created_at);

-- Abuse limit: at most 30 contributions per user per day.
create function public.limit_contributions() returns trigger
    language plpgsql security definer set search_path = '' as $$
begin
    if (select count(*) from public.product_contributions c
        where c.user_id = new.user_id and c.created_at > now() - interval '1 day') >= 30 then
        raise exception 'Daily contribution limit reached';
    end if;
    return new;
end $$;

revoke all on function public.limit_contributions() from public, anon, authenticated;

create trigger product_contributions_limit before insert on public.product_contributions
    for each row execute function public.limit_contributions();
