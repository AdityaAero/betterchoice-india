-- Swap catalogue drafts from Open Food Facts India, labels evidenced from 1 Jan 2025 (record created or pack
-- photographed), for a certified nutritionist to review. Not yet applied: no Supabase connection in the session
-- that wrote it. Apply in the SQL editor (or `supabase db push`), then load supabase/seed/catalogue_off_india_*.sql.
--
-- Licence: Open Food Facts data is under the Open Database Licence (ODbL 1.0). Credit "Open Food Facts
-- contributors" wherever it is shown; a public copy of this derived table must be offered under the ODbL.
-- Nothing here is personal data.
--
-- Access: clients (anon, authenticated) read approved rows only. Every write goes through the dashboard or the
-- service role, so a nutritionist's approval cannot be made from the app.

create table public.catalogue_products (
    gtin text primary key
        check (gtin ~ '^[0-9]{8,14}$' and public.bc_gtin_check(lpad(gtin, 14, '0'))),
    brand text not null check (char_length(brand) between 1 and 120),
    name text not null check (char_length(name) between 2 and 200),
    swap_group text,
    grouped_by text,
    fssai_category smallint check (fssai_category between 1 and 16),
    infant_food boolean not null default false,
    is_beverage boolean not null default false,
    energy_kcal numeric(6, 1) check (energy_kcal between 0 and 950),
    sugars_g numeric(6, 2) check (sugars_g between 0 and 100),
    added_sugars_g numeric(6, 2) check (added_sugars_g between 0 and 100),
    saturated_fat_g numeric(6, 2) check (saturated_fat_g between 0 and 100),
    total_fat_g numeric(6, 2) check (total_fat_g between 0 and 100),
    trans_fat_g numeric(6, 2) check (trans_fat_g between 0 and 100),
    sodium_mg numeric(8, 1) check (sodium_mg between 0 and 40000),
    protein_g numeric(6, 2) check (protein_g between 0 and 100),
    fibre_g numeric(6, 2) check (fibre_g between 0 and 100),
    carbohydrate_g numeric(6, 2) check (carbohydrate_g between 0 and 100),
    serving_g numeric(7, 1) check (serving_g > 0),
    pack_size numeric(9, 1) check (pack_size > 0),
    pack_unit text check (pack_unit in ('g', 'ml')),
    ingredients text,
    contains text[] not null default '{}',
    may_contain text[] not null default '{}',
    veg text check (veg in ('vegan', 'vegetarian', 'non-vegetarian')),
    halal boolean not null default false,
    nutri_score_letter text check (nutri_score_letter in ('A', 'B', 'C', 'D', 'E')),
    nutri_score_score smallint,
    band text check (band in ('A', 'B', 'C')),
    label_date date not null check (label_date >= date '2025-01-01'),
    off_created date,
    status text not null default 'draft' check (status in ('draft', 'approved', 'rejected')),
    reviewed_by text,
    reviewed_at timestamptz,
    review_note text,
    source text not null default 'Open Food Facts',
    source_url text not null check (source_url like 'https://world.openfoodfacts.org/product/%'),
    licence text not null default 'ODbL 1.0',
    imported_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

comment on table public.catalogue_products is
    'Open Food Facts India products (labels from 2025), graded with Nutri-Score 2023 by the app''s rules; drafts until a nutritionist approves. ODbL.';
comment on column public.catalogue_products.label_date is
    'Newest evidence of the current label: the latest pack photo, else when the Open Food Facts record was created.';

create index catalogue_products_group on public.catalogue_products (swap_group, status);
create index catalogue_products_category on public.catalogue_products (fssai_category);

create table public.catalogue_recommendations (
    swap_group text not null,
    profile_choice text not null check (char_length(profile_choice) between 3 and 40),
    rank smallint not null check (rank between 1 and 50),
    gtin text not null references public.catalogue_products (gtin) on delete cascade,
    qualifies_for smallint not null check (qualifies_for >= 1),
    originals smallint not null check (originals >= qualifies_for),
    why text,
    status text not null default 'draft' check (status in ('draft', 'approved', 'rejected')),
    reviewed_by text,
    reviewed_at timestamptz,
    review_note text,
    updated_at timestamptz not null default now(),
    primary key (swap_group, profile_choice, gtin)
);

comment on table public.catalogue_recommendations is
    'For each swap group and single profile choice (diet, goal, allergy or condition), the alternatives the app''s engine accepts for the group''s red and amber products, best first.';

alter table public.catalogue_products enable row level security;
alter table public.catalogue_products force row level security;
alter table public.catalogue_recommendations enable row level security;
alter table public.catalogue_recommendations force row level security;

revoke all on table public.catalogue_products from public, anon, authenticated;
revoke all on table public.catalogue_recommendations from public, anon, authenticated;
grant select on table public.catalogue_products to anon, authenticated;
grant select on table public.catalogue_recommendations to anon, authenticated;

create policy "Anyone reads approved catalogue products" on public.catalogue_products
    for select to anon, authenticated using (status = 'approved');
create policy "Anyone reads approved recommendations" on public.catalogue_recommendations
    for select to anon, authenticated using (status = 'approved');

create trigger catalogue_products_touch before update on public.catalogue_products
    for each row execute function public.touch_updated_at();
create trigger catalogue_recommendations_touch before update on public.catalogue_recommendations
    for each row execute function public.touch_updated_at();
