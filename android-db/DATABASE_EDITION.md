# BetterChoice version 4: database only (Supabase)

Version 4 rates products and picks better choices only from the BetterChoice database in Supabase. It does not read
the label, the pack photo or the barcode. The phone reads the product's **name** (and the shop's own "Product Type",
when the page shows one), asks the database once, and rates the record that comes back. The report and the three
choices use the same code as version 3.

| | Version 3 (kept as is) | Version 4 (this folder) |
|---|---|---|
| Folder | `android/` | `android-db/` |
| App id / label | `com.betterchoice.india` / "BetterChoice India" | `com.betterchoice.india.db` / "BetterChoice DB" |
| Version | 3.9.0 (vc 53) | 4.0.0-supabase (vc 400) |
| Product facts | label text on screen → pack photo (OCR) → barcode → Open Food Facts | the BetterChoice database only |
| Better choices | bundled catalogue (`swaps_v1.json`, in the APK) | the database's answer (the catalogue is **not** in the APK) |
| Screenshots | yes, of the shopping app only | none (the Accessibility service cannot take them) |

The two apps can be installed side by side. Each has its own consent, sign-in callback (`<app id>://auth-callback`)
and Accessibility entry.

## How a lookup works

```
product page opens
  → phone reads: name, shop's product type, shop                        (nothing else leaves the phone)
  → cache: memory (256 names) → disk (24 h for a hit, 6 h for "not listed")
  → POST /rest/v1/rpc/bc_lookup  (publishable key, one request, keep-alive, gzip)
      1. exact: alias or normalised name                                 (index lookup, ~1 ms)
      2. hybrid: top 15 by trigram (GiST) ∪ top 15 by vector (HNSW, cosine)
         score = 0.30·similarity + 0.40·word similarity + 0.30·(1 − cosine) − 0.12 if the brand is missing
         accept only if all of these hold:
           score ≥ 0.62 and 0.04 ahead of the next name
           the title covers ≥ 85% of the record's own words (a slip of spelling allowed)
           at most 1 extra word in the title
           no extra "variant" word (protein, zero, diet, lite, sugar-free, toned…)
           the shop's category agrees with the record's
  → answer: record + its category + the category's better candidates (in catalogue order)
  → phone: rates the record (same code as v3), then applies the shopper's allergies, diet, conditions and goals to
           the candidates and shows three choices, green first
```

The vector is a 256-dimension hashed character-trigram embedding computed inside Postgres
(`catalogue_v4.embed`). The seed, the lookup and the generated columns all use the same function, so no model server
is involved and nothing can drift between them.

**Privacy.** The database never receives the shopper's identity or health answers. It returns every candidate that
is better for *some* goal (the union over the 12 goal sets). The phone then removes and orders candidates for this
shopper. The tables are closed to the public key (RLS on, no policies). Only `bc_lookup`, `bc_report` and
`bc_catalogue_version` can be called.

**When nothing matches.** If the name is not listed, the card says "Not in our database yet" and the bubble stays
neutral. A wrong product is worse than no answer, so a near miss is never shown as this product. If the database
can't be reached, the card offers "Try again".

## Measured

Local Postgres 18 (PGlite, WebAssembly, so a real server is faster), `tools/v4_db_test.mjs`, results in
`export/db_test.json` (measured on the first 973 products):

| Shop wording of the 973 products | Right | Wrong | None |
|---|---|---|---|
| Exact name | 973 | 0 | 0 |
| Claims and pack size added | 965 | 0 | 8 |
| Lower case, no punctuation | 960 | 0 | 13 |
| One typo | 426 | 9 | 538 |
| "Name By Brand" (781) | 728 | 1 | 52 |
| Brand left out (595) | 519 | 18* | 58 |

\* Mostly names shared by several records (e.g. three "Whole Wheat Bread" records), where any of them is a fair answer.

- **Products not in the catalogue (307):** 301 answered "not listed", 5 matched a same-named record, and 1 matched a close variant (a Greek yoghurt *smoothie* matched Greek *yogurt*).
- **Real Zepto titles from the recordings:** 11 of 94 matched, all correct. The other 83 aren't in the 973, so **coverage, not matching, is the main limit today.**
- **Database time, across runs:** exact match p50 0.8–1.2 ms (p95 ≤ 2.5 ms), hybrid p50 7–12 ms (p95 10–17 ms), miss p50 7–11 ms.
- **Answer size:** a product's candidate list has median 6, p95 35 and max 64 products.
- **On the phone, a product seen in the last 24 h** is answered from cache with no request.
- **New product:** one network round trip, which on Indian mobile networks will dominate. The wait is capped at 2.5 s. An answer that arrives later is still cached for the next look.

## Applying it to Supabase

Project `<your-project-ref>` (Mumbai, Postgres 17):

1. **Applied on 16 Oct 2026** through the Supabase connector, as migration `v4_catalogue_vector`: `betterchoice-android/supabase/migrations/20261015120000_v4_catalogue_vector.sql`. This creates schema `catalogue_v4` (pgvector 0.8.2 enabled), the tables, the indexes and the three public functions. It only adds objects. The security advisor lists two intended things: closed tables (RLS on, no policies) and three public read-only functions. It also lists one older setting not related to v4: leaked-password protection is off in Auth.
2. **Pending.** Run `betterchoice-android/supabase/seed/v4/v4_catalogue_001.sql` … `_009.sql` in the SQL editor (Dashboard → SQL → New query), one at a time and in order. Each file is one transaction. Alternatively, run `v4_catalogue_all.sql` (1.6 MB) with `psql`.

   The seed is too big to send through the connector. A temporary public loader function was blocked by Claude Code's safety check, which is right to block anything that lets the public key write.
3. Check:
   ```sql
   select public.bc_catalogue_version();
   select public.bc_lookup('Amul Masti Spiced Buttermilk', null, 'com.zeptoconsumerapp') -> 'match';
   ```

If you use magic links, add `com.betterchoice.india.db://auth-callback` to Auth → URL Configuration → Redirect
URLs. The one-time code sign-in needs nothing extra.

## Changing the catalogue

```
1. edit/regenerate app/src/main/assets/catalogue/swaps_v1.json (tools/off_india_catalogue.py)
   packs checked on a label: catalogue/label_checks.json, then python tools/v4_label_checked.py
2. BC_DB_EXPORT_DIR=<absolute path of v4_supabase/export> ./gradlew :app:testDebugUnitTest --tests "*DbExportHarness" --rerun
   (the test runs from app/, so a relative path lands in the wrong folder; --rerun because the variable is not a task input)
3. python tools/v4_supabase_seed.py           → supabase/seed/v4/*.sql (new version stamp; label-check shop names as aliases)
4. node tools/v4_db_test.mjs                  → checks the database against the engine
   node tools/v4_lookup.mjs titles.txt        → answers for shop titles, one per line
5. run the seed files in Supabase
```
`sh tools/v4_rebuild.sh <env script>` runs steps 1 (label checks) to 4, then `CatalogueParityTest` and
`tools/v4_colour_gaps.py` (green and yellow per category, by the app's engine).

**Label checks** (`catalogue/label_checks.json`) record packs a person has compared with their open-data record. They
are evidence, never a new source of figures.
- **Shop names** become database aliases. For example, Blinkit's "Kellogg's 0% Added Sugar Muesli" is the listed "Kellogg's Muesli".
- **A pack declaring 0 g saturated fat per serving with a %RDA** lets the builder keep the record's 0, but only when the colour is the same anywhere up to the %RDA bound (`curate_swaps.declared_zero_settles`; FSSAI's 22 g reference, `Evidence fssai.label.saturated.fat`).

On 16 Oct 2026 this added The Baker's Dozen Multigrain Protein Chips (8906076133258, salty snacks, yellow), so the list
has 974 products.

Phones drop their cached answers as soon as they see the new version stamp. `CatalogueParityTest` fails if the
seed is older than the engine.

## Build and test

```
source <scratchpad>/bc4_env.sh     # reuses version 3's .tools (JDK, SDK, offline Gradle cache) read-only
./gradlew --offline :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:assembleDebug :app:assembleRelease
python tools/verify_release.py
```

Tests added for version 4:

- `CatalogueClientTest` covers the cache:
  - memory, disk, and the 24 h / 6 h lifetimes;
  - dropping everything on a new version;
  - failures are never cached, with a 20 s pause before retrying, which "Try again" skips;
  - two screens asking at once share one request;
  - the 2.5 s cap, and caching an answer that arrives late;
  - deletion on start and when consent is withdrawn;
  - only the name, type and shop are sent.
- `CatalogueParityTest` runs every product × 10 shoppers (diets, allergies, conditions, BMI, goals). It checks the following against version 3's engine over the whole catalogue:
  - the database answer gives **the same three choices, reasons and "left out" counts**;
  - the rating is the record's, whatever the shop calls it;
  - the seed is current.
- `tools/v4_db_test.mjs` runs the migration and seed in a real Postgres. It checks matching, closed tables and latency.

## Known limits

- **Coverage.** Only the 974 listed products (Open Food Facts India) can be rated, so most Zepto items will say "Not in our database yet". Adding products is a database change (above); no app update is needed.
- **Drafts.** All 974 records are drafts (not yet checked by the nutritionist), so the card says so. As in version 3, a release build offers only checked products as choices, so it shows **no choices** until records are marked `verified`. The debug build shows draft choices.
- **What the database decides.** The category comes from the database record, not from the page. That is usually more accurate, but it means a wrong match carries a wrong category. The ⓘ details name the matched record, so a wrong match is easy to spot.
