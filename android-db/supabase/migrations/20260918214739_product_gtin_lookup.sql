-- The number printed under the barcode identifies one exact pack, so it is the strongest match we
-- have. Stored right-padded to 14 digits so EAN-8, UPC-12, EAN-13 and GTIN-14 share one key.
-- The check digit is enforced here as well as in the app: a misread number must never become a row.
-- Applied to the live project on 2026-09-18 as migration product_gtin_lookup.

create or replace function public.bc_gtin_check(value text)
returns boolean
language sql immutable parallel safe
set search_path to ''
as $$
    select case
        when value is null then true
        when value !~ '^[0-9]{14}$' then false
        else (
            select (10 - (sum(
                (substr(value, 14 - i, 1))::int * case when i % 2 = 1 then 3 else 1 end
            ) % 10)) % 10 = substr(value, 14, 1)::int
            from generate_series(1, 13) as i
        )
    end
$$;

comment on function public.bc_gtin_check(text) is
    'GS1 check digit over a 14-digit GTIN: weights 3 and 1 from the right. Null passes (unknown).';

alter table public.products
    add column if not exists gtin text,
    add constraint products_gtin_shape check (gtin is null or gtin ~ '^[0-9]{14}$'),
    add constraint products_gtin_checksum check (public.bc_gtin_check(gtin));

comment on column public.products.gtin is
    'GTIN-14 from the pack barcode. A GS1 prefix such as 890 shows who issued the number, not the country of origin.';

-- One verified row per pack. Unverified duplicates are allowed until a moderator picks the winner.
create unique index if not exists products_gtin_verified_key
    on public.products (gtin) where gtin is not null and verified;

alter table public.product_contributions
    add column if not exists gtin text,
    add constraint contributions_gtin_shape check (gtin is null or gtin ~ '^[0-9]{14}$'),
    add constraint contributions_gtin_checksum check (public.bc_gtin_check(gtin));

comment on column public.product_contributions.gtin is
    'Barcode number read from the pack when the shopper submitted the label.';

-- Exact lookup: no fuzzy matching, so no chance of grading the wrong pack size.
create or replace function public.match_by_gtin(p_gtin text)
returns table(product_id uuid, name text, energy_kcal numeric, sugars_g numeric,
              saturated_fat_g numeric, sodium_mg numeric, fibre_g numeric, protein_g numeric,
              is_beverage boolean, gtin text)
language sql stable
set search_path to ''
set statement_timeout to '2s'
as $$
    select p.id, p.name, p.energy_kcal, p.sugars_g, p.saturated_fat_g, p.sodium_mg,
           p.fibre_g, p.protein_g, p.is_beverage, p.gtin
    from public.products p
    where p.verified
      and p.gtin is not null
      and p.gtin = p_gtin
      and p_gtin ~ '^[0-9]{14}$'
      and public.bc_gtin_check(p_gtin)
    limit 1
$$;

comment on function public.match_by_gtin(text) is
    'Reads one verified product by its pack barcode. Anonymous callers may read verified rows only.';

revoke all on function public.match_by_gtin(text) from public;
grant execute on function public.match_by_gtin(text) to anon, authenticated;
revoke all on function public.bc_gtin_check(text) from public;
grant execute on function public.bc_gtin_check(text) to anon, authenticated;
