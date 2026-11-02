package com.betterchoice.android;

import java.util.List;

/**
 * The grading cascade for one screen, run off the main thread:
 * 1) nutrition text on the page (including an opened "View details" section),
 * 2) the pack photo: its barcode number first, since that identifies the exact pack, then the text
 * recognised in the same image, 3) details the user typed, 4) the BetterChoice database by name and
 * price, 5) otherwise ask the user to skip or add details. Nothing is guessed from the product name.
 */
final class ProductAnalyzer {
    interface Pack { PackScan read(ProductPage page); }
    interface Database {
        Nutrition find(ProductPage page);
        /** The pack's own number: an exact match, so it is tried before name-and-price matching. */
        Nutrition byGtin(String gtin);
        /** The open food database, consulted only after ours has missed on that same number. */
        Nutrition byGtinOpenData(String gtin);
    }
    interface Memory {
        boolean skipped(String key);
        Nutrition userDetails(String key);
        default String evidence(String key, String text) { return text; }
        default String nutritionText(String key, String text) { return text; }
    }

    static GradeResult analyze(List<ScreenText> screen, int screenHeight, String retailer,
                               Pack pack, Database database, Memory memory) {
        return analyze(screen, screenHeight, retailer, pack, database, memory, null);
    }

    /** @param anchor what this same screen was already identified as, kept while the shopper scrolls */
    static GradeResult analyze(List<ScreenText> screen, int screenHeight, String retailer,
                               Pack pack, Database database, Memory memory, ProductPage anchor) {
        return analyze(screen, screenHeight, retailer, pack, database, memory, anchor, null);
    }

    /** @param photosOf the product just left for a new window, if that window may be its photo viewer */
    static GradeResult analyze(List<ScreenText> screen, int screenHeight, String retailer,
                               Pack pack, Database database, Memory memory, ProductPage anchor, ProductPage photosOf) {
        StringBuilder notes = new StringBuilder();
        ProductPage page = ProductPage.detect(screen, screenHeight, retailer, notes, anchor, photosOf);
        if (page == null) { DebugLog.diagnostic("page? " + notes); return GradeResult.neutral(GradeResult.State.NOT_PRODUCT, null); }
        DebugLog.diagnostic("page: " + notes);
        if (!page.food) return GradeResult.neutral(GradeResult.State.NOT_FOOD, page);
        String key = page.key();
        String evidence = memory == null ? page.text : memory.evidence(key, page.text);
        if (memory != null && memory.skipped(key)) return GradeResult.neutral(GradeResult.State.SKIPPED, page);

        Beverage drink = Beverage.classify(page.name, page.text, false);
        String nutritionInput = memory == null ? page.text : memory.nutritionText(key, page.text);
        Nutrition label = NutritionParser.parse(nutritionInput);
        if (BuildConfig.DEBUG) {   // what the page's figures read as, and the text they came from
            int at = nutritionInput.toLowerCase(java.util.Locale.ROOT).indexOf("nutrition");
            DebugLog.diagnostic("page figures: " + (label == null ? "none" : "kcal=" + label.energyKcal + " sugars=" + label.sugarsG
                + " sat=" + label.saturatedFatG + " sodium=" + label.sodiumMg + " basis=" + label.basisStated + " usable=" + label.usable())
                + " | " + (at < 0 ? "(no 'nutrition' in text)" : nutritionInput.substring(at, Math.min(nutritionInput.length(), at + 320))
                    .replace('\n', '|')));
        }

        // Tea and coffee bought dry are rated as the cup, not the jar, once the list shows what the
        // cup is. Otherwise a premix is rated as sold, and says so.
        boolean dryBrew = Brew.isDryBrew(page.name, Beverage.packUnit(page.name, page.text));
        if (dryBrew) {
            String ingredients = IngredientFacts.section(evidence);
            // Water quantity alone does not establish the final prepared volume or density.
            // Keep the dry label basis; a cup is not inferred from a powder/leaf ingredient list.
            // Bought dry and not made up: the dry product's own table, rated as sold, never on the
            // drink scale (salted kahwa tea bags carry 6000 mg sodium per 100 g of leaf, not of tea).
            drink = Beverage.dryProduct();
        }
        if (label != null && label.basisStated) {
            // A readable page table supplies nutrition, but a later gallery image may
            // be the only ingredient/allergen declaration. Use the watcher's bounded
            // capture budget to collect those facts without merging photo nutrients.
            if (pack != null && NutritionParser.parse(page.text) == null) {
                PackScan scan = pack.read(page);
                if (scan != null && scan.hasText()) {
                    String photoEvidence = memory == null ? scan.text : memory.evidence(key, scan.text);
                    evidence = photoEvidence + "\n" + evidence;
                }
            }
            return GradeResult.graded(label, GradeResult.Source.LABEL_TEXT, page, null,
                dryBrew ? drink : identity(page, null, label, drink), evidence);
        }
        DebugLog.diagnostic("page text has no usable nutrition table");

        // A name such as 'water' does not declare sodium or any other numeric nutrient.

        String gtin = null;
        String note = null;
        String packText = null;
        Nutrition photoUnstated = null;
        String photoUnstatedText = null;
        if (pack != null) {
            PackScan scan = pack.read(page);
            if (scan == null) scan = PackScan.EMPTY;
            String scannedEvidence = memory == null ? scan.text : memory.evidence(key, scan.text);
            if (scan.hasGtin()) {
                gtin = scan.gtin;
                DebugLog.diagnostic("barcode " + Gtin.display(gtin));
                // The pack's own label, when the same photo shows it, outranks any database record for
                // its barcode: a crowd-sourced record can be for an older recipe, or simply wrong.
                Nutrition onPack = scan.hasText() ? NutritionParser.parse(scan.text) : null;
                if (onPack != null) {
                    return GradeResult.graded(onPack, GradeResult.Source.PACK_PHOTO, page, gtin,
                        identity(page, scan.text, onPack, drink), scannedEvidence);
                }
                if (database != null) {
                    Nutrition byNumber = database.byGtin(gtin);
                    if (byNumber != null && byNumber.usable()) {
                        return GradeResult.graded(byNumber, GradeResult.Source.BARCODE, page, gtin, identity(page, scan.text, byNumber, drink),
                            scannedEvidence);
                    }
                    DebugLog.diagnostic("barcode not in our database yet");
                    // Our own table starts empty, so without this a correct scan would still find
                    // nothing. The open database is consulted live and never copied into ours.
                    Nutrition open = database.byGtinOpenData(gtin);
                    if (open != null && open.usable()) {
                        return GradeResult.graded(open, GradeResult.Source.OPEN_FOOD_FACTS, page, gtin, identity(page, scan.text, open, drink),
                            scannedEvidence);
                    }
                    note = "I read the barcode, but no database knows this pack yet.";
                }
            }
            if (scan.hasText()) {
                Nutrition fromPhoto = NutritionParser.parse(scan.text);
                // The pack photo carries the ingredient list and allergen declaration too, so it is
                // kept with the rating for the safety check rather than being read twice.
                if (fromPhoto != null && fromPhoto.basisStated) {
                    return GradeResult.graded(fromPhoto, GradeResult.Source.PACK_PHOTO, page, gtin,
                        identity(page, scan.text, fromPhoto, drink), scannedEvidence);
                }
                // Figures with no "per 100 g/ml": kept, and rated below only if nothing states a basis.
                if (fromPhoto != null && fromPhoto.usable() && photoUnstated == null) {
                    photoUnstated = fromPhoto;
                    photoUnstatedText = scan.text;
                }
                packText = scannedEvidence;
                DebugLog.diagnostic("pack text has no usable nutrition table");
                if (note == null) note = "I read the pack, but couldn't find a full nutrition panel on it.";
            } else if (scan.isEmpty()) {
                note = "I couldn't read anything from this screen.";
            }
        }
        if (memory != null) {
            Nutrition mine = memory.userDetails(key);
            if (mine != null && mine.usable()) {
                return GradeResult.graded(mine, GradeResult.Source.USER, page, gtin, identity(page, packText, mine, drink), packText);
            }
        }
        if (database != null) {
            Nutrition fromDatabase = database.find(page);
            if (fromDatabase != null && fromDatabase.usable()) {
                return GradeResult.graded(fromDatabase, GradeResult.Source.DATABASE, page, gtin,
                    identity(page, packText, fromDatabase, drink), packText);
            }
        }
        // Nothing states a basis. The figures are still the product's own: rate them as labels in
        // India give them (food per 100 g, drink per 100 ml), show them, and say plainly that the
        // label did not say whether they are per 100 g or per 100 ml.
        if (label != null && label.usable()) {
            return GradeResult.graded(label, GradeResult.Source.LABEL_TEXT, page, gtin,
                dryBrew ? drink : identity(page, packText, label, drink), packText == null ? evidence : packText + "\n" + evidence);
        }
        if (photoUnstated != null) {
            return GradeResult.graded(photoUnstated, GradeResult.Source.PACK_PHOTO, page, gtin,
                dryBrew ? drink : identity(page, photoUnstatedText, photoUnstated, drink), packText);
        }
        GradeResult unresolved = GradeResult.needsInput(page, gtin, note);
        unresolved.labelText = evidence;
        return unresolved;
    }

    /**
     * Version 4: the product's facts come only from the BetterChoice database. Its name, and the shop's own product
     * type, are read from the page and looked up once (the client caches every answer); the record is then rated by
     * the same code that rates a label read on screen. The label, the pack photo and the barcode are not read, so the
     * colour can show as soon as the product page opens, before the shopper scrolls to the label.
     */
    static GradeResult fromCatalogue(List<ScreenText> screen, int screenHeight, String retailer, CatalogueClient catalogue,
                                     Memory memory, ProductPage anchor, ProductPage photosOf, boolean userAsked) {
        StringBuilder notes = new StringBuilder();
        ProductPage page = ProductPage.detect(screen, screenHeight, retailer, notes, anchor, photosOf);
        if (page == null) { DebugLog.diagnostic("page? " + notes); return GradeResult.neutral(GradeResult.State.NOT_PRODUCT, null); }
        DebugLog.diagnostic("page: " + notes);
        if (!page.food) return GradeResult.neutral(GradeResult.State.NOT_FOOD, page);
        if (memory != null && memory.skipped(page.key())) return GradeResult.neutral(GradeResult.State.SKIPPED, page);
        long started = System.nanoTime();
        CatalogueDb.Lookup found = catalogue.lookup(page.name, page.productType(), retailer, userAsked);
        if (BuildConfig.DEBUG) {
            DebugLog.diagnostic("catalogue '" + page.name + "' type=" + page.productType() + " -> "
                + (found.failed ? "unreachable" : found.match == null ? "not listed" + (found.nearest.isEmpty() ? "" : ", nearest " + found.nearest)
                    : found.match.product.displayName() + " score=" + found.match.score + " " + found.match.method)
                + (found.fromCache ? " (cached)" : "") + " in " + (System.nanoTime() - started) / 1_000_000 + " ms");
        }
        if (found.failed) {
            GradeResult r = GradeResult.needsInput(page, null,
                "The BetterChoice database could not be reached just now. Check your connection, then tap Try again.");
            r.database = GradeResult.Database.UNREACHABLE;
            return r;
        }
        if (found.match == null) {
            GradeResult r = GradeResult.needsInput(page, null, "This product is not in the BetterChoice database yet.");
            r.database = GradeResult.Database.NOT_LISTED;
            return r;
        }
        return CatalogueDb.rate(page, found.match);
    }

    /**
     * Food or drink, decided again once the values are in hand, because a per-100-ml panel is itself
     * evidence of a drink. The page's own verdict still stands when the new reading can only say
     * "unsure": a name that says chocolate or juice outranks a pack photo that says nothing.
     */
    static Beverage identity(ProductPage page, String packText, Nutrition values, Beverage pageVerdict) {
        String text = packText == null ? page.text : packText + "\n" + page.text;
        Beverage fresh = Beverage.classify(page.name, text, values.beverage);
        boolean freshIsSure = fresh.isDrink() || fresh.definitelyFood;
        boolean pageIsSure = pageVerdict.isDrink() || pageVerdict.definitelyFood;
        return !freshIsSure && pageIsSure ? pageVerdict : fresh;
    }

    private ProductAnalyzer() {}
}
