-- BetterChoice version 4 (15 Oct 2026): the catalogue lives in the database and is found by the name the shop shows.
--
--   * catalogue_v4.products   the listed products in 31 categories (Open Food Facts India, labels from 2025, ODbL), each with
--                             its record as the app reads it, its report from the app's own engine, and its better
--                             candidates (computed by the app's engine, best first).
--   * name matching           exact name or curated alias first; then a hybrid of pg_trgm trigram similarity and a
--                             pgvector cosine search (HNSW) over a hashed character-trigram embedding computed inside
--                             Postgres: no model server and no cold start, so the lookup stays in single-digit ms.
--   * public.bc_lookup(...)   the only way in: one call returns the product, its report, its category and its better
--                             candidates, so the phone needs one round trip per product (and caches the answer).
--
-- Nothing from earlier migrations is changed or dropped: version 3 keeps working against the same project.

create schema if not exists extensions;
create extension if not exists vector with schema extensions;
create extension if not exists pg_trgm with schema extensions;

create schema if not exists catalogue_v4;
revoke all on schema catalogue_v4 from public;

-- A shop title or a catalogue name, written one way: lower case, no apostrophes, no pack sizes ("200 g", "1 L",
-- "Pack of 2"), only letters, digits and "&", single spaces. A Zepto title's claims after "|" are dropped when the part
-- before it is a name in its own right ("Lay's Classic Salted | Crunchy Potato Chips").
create or replace function catalogue_v4.norm(t text) returns text
language sql immutable parallel safe set search_path = ''
as $$
  select btrim(regexp_replace(regexp_replace(regexp_replace(regexp_replace(
           lower(translate(case when strpos(coalesce(t, ''), '|') > 0 and length(btrim(split_part(t, '|', 1))) >= 8
                                then split_part(t, '|', 1) else coalesce(t, '') end, '’''`', '')),
           '\mpack of \d+\M', ' ', 'g'),
           '\m\d+(\.\d+)?\s*(g|gm|gms|gram|grams|kg|kgs|ml|l|ltr|litre|litres|pc|pcs|piece|pieces)\M', ' ', 'g'),
           '[^a-z0-9&]+', ' ', 'g'),
           '\s+', ' ', 'g'))
$$;

-- The words that say what a product is: the normalised name's words, without numbers, single letters and the words
-- every title uses ("pack", "new", "crunchy", "flavour"...). Used to check that a match is the same product.
create or replace function catalogue_v4.words(t text) returns text[]
language sql immutable parallel safe set search_path = ''
as $$
  select coalesce(array_agg(distinct w), '{}')
  from regexp_split_to_table(catalogue_v4.norm(t), ' ') as w
  where length(w) >= 3 and w !~ '^[0-9]+$'
    and w <> all (array['the', 'and', 'with', 'for', 'from', 'new', 'pack', 'packet', 'pouch', 'box', 'jar', 'bottle', 'can',
                        'tin', 'combo', 'original', 'classic', 'premium', 'flavour', 'flavor', 'flavoured', 'flavored', 'rich',
                        'made', 'fresh', 'natural', 'pure', 'healthy', 'tasty', 'crunchy', 'crispy', 'delicious', 'snack',
                        'snacks', 'ready', 'family', 'value', 'mini', 'big', 'small', 'large', 'medium', 'jumbo', 'bar',
                        'bars', 'pcs', 'india', 'indian', 'best', 'quality', 'premix', 'refill'])
$$;

-- The share of the words in a that b also has, a spelling slip allowed (trigram similarity of 0.45 or more for words
-- of four letters or more): "waffers" is "wafers", "diary" is "dairy".
create or replace function catalogue_v4.covered(a text[], b text[]) returns float8
language sql immutable parallel safe set search_path = ''
as $$
  select case when cardinality(a) = 0 then 1.0 else (
    select count(*)::float8 / cardinality(a)
    from unnest(a) as x(w)
    where w = any(b) or (length(w) >= 4 and exists (
      select 1 from unnest(b) as y(u) where length(u) >= 4 and extensions.similarity(w, u) >= 0.45))) end
$$;

-- The name as a vector: character trigrams (and whole words, weighted twice) hashed into 256 dimensions and
-- L2-normalised, so cosine similarity measures shared spelling. Deterministic and computed in the database.
create or replace function catalogue_v4.embed(k text) returns extensions.vector
language plpgsql immutable parallel safe set search_path = ''
as $$
declare
  dims constant int := 256;
  v float8[] := array_fill(0::float8, array[dims]);
  s text := ' ' || coalesce(k, '') || ' ';
  w text;
  i int;
  h int;
  n float8;
begin
  for i in 1 .. greatest(length(s) - 2, 0) loop
    h := ((hashtext(substr(s, i, 3))::bigint % dims) + dims) % dims;
    v[h + 1] := v[h + 1] + 1;
  end loop;
  foreach w in array regexp_split_to_array(btrim(coalesce(k, '')), '\s+') loop
    if length(w) > 1 then
      h := ((hashtext('w:' || w)::bigint % dims) + dims) % dims;
      v[h + 1] := v[h + 1] + 2;
    end if;
  end loop;
  select sqrt(sum(x * x)) into n from unnest(v) as u(x);
  if n is not null and n > 0 then
    for i in 1 .. dims loop v[i] := v[i] / n; end loop;
  end if;
  return v::extensions.vector;
end
$$;

create table if not exists catalogue_v4.meta (
  singleton boolean primary key default true check (singleton),
  version text not null,
  products int not null,
  built_at timestamptz not null default now()
);

create table if not exists catalogue_v4.groups (
  id text primary key,
  ord int not null,
  doc jsonb not null
);

-- The category a shop title names, by the catalogue's own words, in the catalogue's order: exactly how the app places
-- the product on screen (SwapCatalogue.groupFor). Null when the title names none.
create or replace function catalogue_v4.group_of(t text) returns text
language sql stable parallel safe set search_path = ''
as $$
  select g.id from catalogue_v4.groups g
  where exists (select 1 from jsonb_array_elements_text(g.doc -> 'match') m
                where (' ' || regexp_replace(lower(coalesce(t, '')), '[^a-z0-9&]+', ' ', 'g') || ' ')
                      like ('% ' || btrim(regexp_replace(lower(m), '[^a-z0-9&]+', ' ', 'g')) || ' %'))
    and not exists (select 1 from jsonb_array_elements_text(g.doc -> 'exclude') x
                where (' ' || regexp_replace(lower(coalesce(t, '')), '[^a-z0-9&]+', ' ', 'g') || ' ')
                      like ('% ' || btrim(regexp_replace(lower(x), '[^a-z0-9&]+', ' ', 'g')) || ' %'))
  order by g.ord
  limit 1
$$;

create table if not exists catalogue_v4.products (
  gtin text primary key check (gtin ~ '^[0-9]{8,14}$'),
  id text not null unique,
  group_id text not null references catalogue_v4.groups (id),
  display_name text not null,
  brand text,
  name_key text generated always as (catalogue_v4.norm(display_name)) stored,
  brand_key text generated always as (split_part(catalogue_v4.norm(coalesce(brand, '')), ' ', 1)) stored,
  name_vec extensions.vector(256) generated always as (catalogue_v4.embed(catalogue_v4.norm(display_name))) stored,
  name_words text[] generated always as (catalogue_v4.words(display_name)) stored,
  brand_words text[] generated always as (catalogue_v4.words(brand)) stored,
  status text not null default 'draft' check (status in ('draft', 'verified')),
  doc jsonb not null,
  report jsonb not null,
  alternatives text[] not null default '{}'
);

create index if not exists products_name_key on catalogue_v4.products (name_key);
create index if not exists products_name_trgm on catalogue_v4.products using gist (name_key extensions.gist_trgm_ops);
create index if not exists products_name_vec on catalogue_v4.products using hnsw (name_vec extensions.vector_cosine_ops);

-- Shop titles confirmed for a product (by a nutritionist, or a label check: catalogue/label_checks.json): an exact hit,
-- ahead of any similarity.
create table if not exists catalogue_v4.aliases (
  alias_key text primary key,
  gtin text not null references catalogue_v4.products (gtin) on delete cascade
);

alter table catalogue_v4.meta enable row level security;
alter table catalogue_v4.groups enable row level security;
alter table catalogue_v4.products enable row level security;
alter table catalogue_v4.aliases enable row level security;

-- The lookup. One call per product the shopper opens; the phone caches the answer.
--   p_name      the product name as the shop shows it
--   p_type      the shop's own "Product Type", when the page shows one (kept for the cache key and future use)
--   p_retailer  the shop's package name (never stored)
-- A wrong product is worse than none, so a similar name is not enough. A match must also
--   * score 0.62 or more, and lead the runner-up (another name) by 0.04;
--   * say everything the listed product says: 85% of the words of its name (beyond its brand) appear in the title;
--   * say little else: at most one distinctive word of the title is not in the listed name or brand, and never a word
--     that changes what is in the pack ("protein", "zero", "diet", "lite", "sugar free", "toned"...): "Country
--     Delight 40g Protein Taaza Paneer" is not "Country Delight Taaza Paneer";
--   * name what the listed product says: at least one word beyond the brand (a record named "Maggi 280g" or just
--     "britannia" is never matched);
--   * not name another category: the category the shop's own product type (or else the title) names, if any, is the
--     listed product's category.
-- A miss returns the nearest names, for diagnostics only.
create or replace function public.bc_lookup(p_name text, p_type text default null, p_retailer text default null)
returns jsonb
language plpgsql stable security definer set search_path = ''
as $$
declare
  q text := catalogue_v4.norm(p_name);
  qw text[];
  qg text;
  qv extensions.vector;
  variants constant text[] := array['protein', 'zero', 'diet', 'lite', 'light', 'sugarfree', 'free', 'unsweetened', 'sweetened',
                                    'low', 'high', 'keto', 'fibre', 'fiber', 'whole', 'multigrain', 'toned', 'skimmed', 'skim',
                                    'double', 'plus', 'max', 'slim', 'gold', 'lactose', 'vegan', 'eggless', 'baked', 'fried'];
  ver text;
  best_gtin text;
  best_key text;
  best_score float8;
  second_score float8;
  method text := 'exact';
  near jsonb := '[]'::jsonb;
begin
  select m.version into ver from catalogue_v4.meta m;
  if q is null or length(q) < 3 then
    return jsonb_build_object('v', ver, 'match', null, 'nearest', near);
  end if;

  select a.gtin into best_gtin from catalogue_v4.aliases a where a.alias_key = q;
  if best_gtin is null then
    select p.gtin into best_gtin from catalogue_v4.products p where p.name_key = q order by p.gtin limit 1;
  end if;

  if best_gtin is null then
    method := 'hybrid';
    qw := catalogue_v4.words(p_name);
    qg := coalesce(catalogue_v4.group_of(catalogue_v4.norm(p_type)), catalogue_v4.group_of(catalogue_v4.norm(p_name)));
    qv := catalogue_v4.embed(q);
    with cand as (
      (select p.gtin from catalogue_v4.products p order by p.name_key operator(extensions.<->) q limit 15)
      union
      (select p.gtin from catalogue_v4.products p order by p.name_vec operator(extensions.<=>) qv limit 15)
    ), scored as (
      select p.gtin, p.name_key, p.display_name, p.group_id,
             0.30 * extensions.similarity(p.name_key, q)
           + 0.40 * greatest(extensions.word_similarity(p.name_key, q), extensions.word_similarity(q, p.name_key))
           + 0.30 * (1 - (p.name_vec operator(extensions.<=>) qv))
           - case when length(p.brand_key) >= 3 and strpos(q, p.brand_key) = 0 then 0.12 else 0 end as score,
             catalogue_v4.covered(array(select unnest(p.name_words) except select unnest(p.brand_words)), qw) as says_all,
             (select count(*) from unnest(qw) as t(w)
               where not (catalogue_v4.covered(array[w], p.name_words || p.brand_words) > 0)) as extra,
             (select count(*) from unnest(qw) as t(w)
               where w = any(variants) and not (catalogue_v4.covered(array[w], p.name_words || p.brand_words) > 0)) as variant,
             cardinality(array(select unnest(p.name_words) except select unnest(p.brand_words))) as own_words
      from cand join catalogue_v4.products p on p.gtin = cand.gtin
    ), eligible as (
      select * from scored
      where says_all >= 0.85 and extra <= 1 and variant = 0 and own_words >= 1 and (qg is null or qg = group_id)
    ), ranked as (
      select e.*, row_number() over (order by e.score desc, e.gtin) as r from eligible e
    )
    select (select gtin from ranked where r = 1), (select name_key from ranked where r = 1),
           (select score from ranked where r = 1),
           (select max(score) from ranked where r > 1 and name_key <> (select name_key from ranked where r = 1)),
           coalesce((select jsonb_agg(jsonb_build_object('name', display_name, 'score', round(score::numeric, 3))
                                      order by score desc) from (select * from scored order by score desc limit 3) top), '[]'::jsonb)
      into best_gtin, best_key, best_score, second_score, near;
    if best_gtin is null or best_score < 0.62 or (second_score is not null and best_score - second_score < 0.04) then
      return jsonb_build_object('v', ver, 'match', null, 'nearest', near);
    end if;
  else
    best_score := 1;
  end if;

  return (
    select jsonb_build_object(
      'v', ver,
      'match', jsonb_build_object('gtin', p.gtin, 'name', p.display_name, 'score', round(best_score::numeric, 3), 'method', method),
      'product', p.doc,
      'report', p.report,
      'group', g.doc,
      'alternatives', coalesce((
        select jsonb_agg(a.doc order by x.ord)
        from unnest(p.alternatives) with ordinality as x (gtin, ord)
        join catalogue_v4.products a on a.gtin = x.gtin), '[]'::jsonb))
    from catalogue_v4.products p
    join catalogue_v4.groups g on g.id = p.group_id
    where p.gtin = best_gtin);
end
$$;

-- The catalogue version the phone's cache belongs to.
create or replace function public.bc_catalogue_version()
returns text language sql stable security definer set search_path = ''
as $$ select m.version from catalogue_v4.meta m $$;

-- The database's own report of one product, by barcode, for any client (the same lines the app's card shows).
create or replace function public.bc_report(p_gtin text)
returns jsonb language sql stable security definer set search_path = ''
as $$
  select jsonb_build_object('gtin', p.gtin, 'name', p.display_name, 'status', p.status, 'report', p.report)
  from catalogue_v4.products p where p.gtin = p_gtin
$$;

revoke all on function public.bc_lookup(text, text, text) from public;
revoke all on function public.bc_catalogue_version() from public;
revoke all on function public.bc_report(text) from public;
grant execute on function public.bc_lookup(text, text, text) to anon, authenticated;
grant execute on function public.bc_catalogue_version() to anon, authenticated;
grant execute on function public.bc_report(text) to anon, authenticated;
