# BetterChoice India: database edition (v4.0.0)

The same grade engine, card and swaps as [`../android`](../android), but product facts and healthier choices come
only from the BetterChoice database in Supabase Postgres. The phone sends the product **name**, never the
shopper's profile. App id `com.betterchoice.india.db`; it installs side by side with 3.x.

**Status:** built and tested; the Supabase seed has not been loaded into a production project yet.

## How a lookup works

One read-only RPC, `bc_lookup`, tries these in order:

1. exact name or shop alias;
2. a hybrid of trigram similarity (GiST) and a `pgvector` HNSW index over a 256-dimension hashed-trigram vector.

Strict acceptance rules check coverage, extra words, variant words and category. A phone-side cache (memory LRU
plus disk, with time limits) keeps repeat lookups instant. The shopper's health filters run privately on the
phone. Full design, measurements and setup: **[DATABASE_EDITION.md](DATABASE_EDITION.md)**.

## Build and test

```bash
./gradlew testDebugUnitTest                         # includes CatalogueParityTest (seed matches catalogue)
node tools/v4_db_test.mjs                           # loads migration + seed into PGlite, checks every lookup
sh tools/v4_rebuild.sh <env-script>                 # rebuild export → seed → database test → parity → colour gaps
```

`v4_db_test.mjs` needs `@electric-sql/pglite` and `@electric-sql/pglite-pgvector`.

## Database

- `supabase/migrations/`: schema `catalogue_v4` (extensions `vector`, `pg_trgm`), public read-only functions
  `bc_lookup`, `bc_report`, `bc_catalogue_version`
- `supabase/seed/v4/`: generated seed (998 products), loadable in the SQL editor in 9 parts
- `catalogue/label_checks.json`: packs checked against their labels; shop names become lookup aliases
