#!/bin/sh
# Rebuilds version 4's catalogue after catalogue/label_checks.json changes (the README_V4 chain): adds the checked packs,
# exports the app engine's grades, regenerates the seed, tests the database locally (PGlite), checks seed parity and
# recounts the colours per category.
# Usage: sh tools/v4_rebuild.sh ENV_SCRIPT     (ENV_SCRIPT sets the build environment and enters this project)
set -e
. "$1"
python tools/v4_label_checked.py
# Absolute: the unit test runs from app/, where a relative ../export is this project's folder, not v4_supabase/export.
BC_DB_EXPORT_DIR="$(cd .. && pwd)/export" ./gradlew --offline -q :app:testDebugUnitTest --tests "*DbExportHarness" --rerun
python tools/v4_supabase_seed.py
node tools/v4_db_test.mjs
./gradlew --offline -q :app:testDebugUnitTest --tests "*CatalogueParityTest" --rerun
python tools/v4_colour_gaps.py
