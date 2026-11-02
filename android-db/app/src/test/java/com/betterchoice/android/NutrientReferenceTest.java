package com.betterchoice.android;
import org.junit.Test;
import static org.junit.Assert.*;
public class NutrientReferenceTest {
 @Test public void indian2022AnnexuresCorrectOlderTableErrors() {
  assertEquals(13.2,NutrientReference.get("zinc").woman,.0001);
  assertEquals(150,NutrientReference.get("iodine").man,.0001);
  assertEquals(3500,NutrientReference.get("potassium").man,.0001);
  assertEquals(2,NutrientReference.get("copper").man,.0001);
  assertEquals(30,NutrientReference.get("biotin").man,.0001);
  assertEquals(1.4,NutrientReference.get("thiamine").man,.0001);
  assertEquals(25,NutrientReference.get("fibre").man,.0001);
  assertEquals(25,NutrientReference.get("fibre").woman,.0001);
  assertEquals(15,NutrientReference.get("vitamin_d").man,.0001);
 }
 /** Each row the 2026-10-01 review corrected equals the value quoted in the evidence registry. */
 @Test public void correctedRowsMatchTheirQuotedSource() {
  String[][] rows = {{"biotin","fssai.rda.biotin","fssai.rda.biotin"}, {"copper","fssai.rda.copper","fssai.rda.copper"},
   {"iodine","fssai.rda.iodine.male","fssai.rda.iodine.female"}, {"magnesium","fssai.rda.magnesium.male","fssai.rda.magnesium.female"},
   {"vitamin_b12","fssai.rda.b12.male","fssai.rda.b12.female"}, {"vitamin_k","fssai.rda.vitamin.k","fssai.rda.vitamin.k"},
   {"pantothenic","fssai.rda.pantothenic","fssai.rda.pantothenic"}, {"phosphorus","fssai.rda.phosphorus","fssai.rda.phosphorus"},
   {"potassium","fssai.rda.potassium","fssai.rda.potassium"}, {"manganese","fssai.rda.manganese","fssai.rda.manganese"},
   {"chromium","fssai.rda.chromium","fssai.rda.chromium"}, {"selenium","fssai.rda.selenium","fssai.rda.selenium"},
   {"fibre","who.fibre.min","who.fibre.min"}};
  for(String[] r:rows) {
   assertEquals(r[0],Evidence.d(r[1]).doubleValue(),NutrientReference.get(r[0]).man,1e-9);
   assertEquals(r[0],Evidence.d(r[2]).doubleValue(),NutrientReference.get(r[0]).woman,1e-9);
  }
 }
 @Test public void adequacyIsNotToxicityAndLabelReferenceIsNotRda() {
  assertEquals(NutrientReference.Kind.RDA,NutrientReference.get("protein").kind);
  assertEquals(NutrientReference.Kind.AI,NutrientReference.get("fibre").kind);
  assertEquals(NutrientReference.Kind.LABEL_REFERENCE,NutrientReference.get("added_sugar").kind);
  assertEquals(NutrientReference.Kind.INFORMATION,NutrientReference.get("total_sugar").kind);
  assertEquals(NutrientReference.Kind.UL,NutrientReference.get("vitamin_d_ul").kind);
  assertEquals(100,NutrientReference.get("vitamin_d_ul").man,.0001);
  assertTrue(NutrientReference.get("vitamin_d_ul").jurisdiction.contains("fallback"));
 }
 @Test public void rangesAreNotSexSpecificTargets() {
  assertNull(NutrientReference.get("carbohydrate").man);
  assertEquals(50,NutrientReference.get("carbohydrate").rangeLow,.001);
  assertEquals(55,NutrientReference.get("carbohydrate").rangeHigh,.001);
  assertNull(NutrientReference.get("vitamin_e").man);
  assertEquals(7.5,NutrientReference.get("vitamin_e").rangeLow,.001);
 }
 @Test public void everyRowIsAuditableAndChemicalScopeIsKept() {
  assertTrue(NutrientReference.all().size()>=30);
  for(NutrientReference.Ref r:NutrientReference.all()) {
   assertTrue(r.source.startsWith("https://"));
   for(String v:new String[]{r.unit,r.jurisdiction,r.population,r.chemicalForm,r.scope,r.published,r.reviewed,r.note}) assertFalse(r.id,v.isBlank());
  }
  assertTrue(NutrientReference.get("vitamin_d_ul").chemicalForm.contains("calcidiol"));
 }
}
