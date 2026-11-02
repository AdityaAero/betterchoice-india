# Third-party notices

The [LICENSE](LICENSE) (all rights reserved) covers the original work in this repository. It does **not** cover
the material below, which is included under its owners' terms.

## Open Food Facts data (ODbL 1.0)

The product catalogue contains information from **Open Food Facts**, which is made available here under the
[Open Database License (ODbL) 1.0](https://opendatacommons.org/licenses/odbl/1-0/). Individual contents are under
the [Database Contents License](https://opendatacommons.org/licenses/dbcl/1-0/). Source:
https://world.openfoodfacts.org.

This covers the following files and the databases built from them:

- `android/app/src/main/assets/catalogue/swaps_v1.json`
- `android-db/app/src/main/assets/catalogue/swaps_v1.json`
- `android-db/catalogue/label_checks.json`
- `android-db/supabase/seed/v4/*.sql`

These derived databases are offered under the same ODbL 1.0 terms. A small number of records ("label drafts")
were transcribed from product-pack photos and are included in the same database under the same licence.
Nutrition figures are facts printed on product labels; trademarks and brand names belong to their owners and are
used only to identify products.

## Reference documents (`*/security/evidence/`)

These official documents are included unmodified so that every threshold the app uses can be checked against its
source. They remain the property of their publishers and are not covered by this repository's licence:

- Food Safety and Standards Authority of India (FSSAI): labelling, claims and additives regulations
- ICMR–National Institute of Nutrition: Dietary Guidelines for Indians (2024) and the Nutrient Requirements for
  Indians
- World Health Organization and FAO/WHO/UNU: guidelines and expert reports (e.g. energy requirements, sugars,
  saturated and trans fat, salt substitutes, antenatal care)
- JECFA monographs, EU Regulation (EC) No 1333/2008 and Directive 2006/52/EC annexes
- The Nutri-Score Scientific Committee's algorithm update report
- Journal articles and guideline records retrieved from Europe PMC and PubMed Central (e.g. ISSN position stand,
  KDIGO, Indian Journal of Nephrology), under their respective open-access licences

## Software dependencies

Dependencies are fetched by Gradle at build time and are not vendored:

- [Google ML Kit](https://developers.google.com/ml-kit) text recognition and barcode scanning (on-device), under the ML Kit terms
- JUnit 4 (Eclipse Public License 1.0) and Robolectric (MIT), for tests only

`android/third_party/libphonenumber-LICENSE` (Apache License 2.0) covers the country-code metadata adapted from
[libphonenumber](https://github.com/google/libphonenumber).
