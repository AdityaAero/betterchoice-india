# Changelog

All notable changes to BetterChoice India. Versions are the app's `versionName`; dates are build dates (2026).

## Catalogue update (25–26 Oct)
- 998 products (from 976) after a label-checked harvest of Blinkit pack photos: peanut butters, tofu, soda,
  coconut water, vermicelli, pasta, seeds, nut snacks, makhana and frozen snacks.
- Builder: words after a negation ("No Maida", "Not from Concentrate") no longer decide a product's category.

## 4.0.0 — Database edition (16 Oct)
- New app id `com.betterchoice.india.db`, side by side with 3.x.
- Catalogue lookup moved to Supabase Postgres: exact name → alias → trigram + `pgvector` hybrid search, with
  strict acceptance rules, plus a phone-side cache (memory LRU + disk) and timeouts.
- Shopper health filters still run privately on the phone.

## 3.9.0 — Personal card (9 Oct)
- Personal "for you" lines and daily-impact figures on the card.

## 3.8.x — Three choices (8–9 Oct)
- Up to three healthier same-category swaps. 3.8.1 adds 32-bit device support.

## 3.7.x — Green-only swaps, robust reading (2 Oct)
- Swaps restricted to products the engine rates green; sturdier label reading (OCR repairs, wrapped rows).

## 3.6.0 — Open Food Facts India 2025 (1 Oct)
- Catalogue rebuilt from Open Food Facts India records with 2025-or-later label photos.

## 3.5.x — Decision tree, sourced rules (1 Oct)
- Dietitian-style decision tree: safety → physiological state → condition → goal → healthy eating.
- Calculator-first: every constant cites a document in `security/evidence/`, verified by tests.

## 3.4.0 — Comparisons (30 Sep)
- Goal-based comparisons on Zepto product pages.

## 3.3.x — Personal references, Flipkart photos (27 Sep)
- Personal nutrient references from the profile; Flipkart Minutes pack-photo reading.

## 3.2.x — Zepto and Flipkart Minutes (27 Sep)
- Zepto label scope and "View more" handling; Flipkart Minutes support and photo panels.

## 3.1.x — Biscuits and drinks (26 Sep)
- Beverage grading and juice evidence; biscuit swaps.

## 3.0.x — Healthier swaps (26 Sep)
- First same-category healthier swaps.

## 2.9.x — Food forms, shopping apps only (24–25 Sep)
- Nutrition forms for foods; the bubble limited to an allow-list of shopping apps.

## 2.8.1 — Onboarding (23 Sep)
- Private on-device profile onboarding.

## 2.1.0 — MVP (18 Sep)
- First live build: the shopping bubble and on-device grading.
