package com.betterchoice.android;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=31)
public class PersonalisationRegressionTest {
 private Profile profile(String goal, String buys) { Profile p=new Profile(); p.answers[Profile.GOAL]=goal; p.answers[Profile.BUYS]=buys; return p; }
 private Nutrition values(double energy,double protein) { Nutrition n=new Nutrition(); n.energyKcal=energy; n.proteinG=protein; n.sugarsG=1.0; n.saturatedFatG=1.0; n.sodiumMg=30.0; n.fibreG=5.0; n.basisStated=true; return n; }
 private SwapCatalogue catalogue() throws Exception {
  return SwapCatalogue.parse("{\"version\":\"test\",\"groups\":[{\"id\":\"breakfast_cereals\",\"name\":\"Cereals\",\"branch\":\"food\",\"match\":[\"cereal\"],\"focus\":[\"energy\",\"sugars\",\"saturated_fat\",\"sodium\"]}],\"products\":["
   + candidate("light","Light Brand",200,12)+","+candidate("dense","Dense Brand",450,18)+","+candidate("protein","Protein Brand",300,30)+"]}");
 }
 private String candidate(String name,String brand,int kcal,int protein) {
  return "{\"id\":\""+name+"\",\"brand\":\""+brand+"\",\"name\":\""+name+" cereal\",\"group\":\"breakfast_cereals\",\"unit\":\"g\",\"pack\":400,\"status\":\"verified\",\"ingredients\":\"oats\",\"per100\":{\"energy_kcal\":"+kcal+",\"protein_g\":"+protein+",\"sugars_g\":1,\"saturated_fat_g\":1,\"sodium_mg\":30,\"fibre_g\":5}}";
 }
 private SwapEngine.Outcome recommend(Profile p) throws Exception {
  Nutrition n=values(300,12); n.servingSize=30.0;
  return SwapEngine.recommend(catalogue(),"Original cereal",n,NutriGrade.Band.A,'A',false,SafetyProfile.unknown(),Occasion.SNACK,false,p);
 }
 @Test public void lossGainAndMuscleProduceDifferentComparisonsWithoutRecolouring() throws Exception {
  SwapEngine.Outcome loss=recommend(profile("Weight loss","breakfast"));
  assertEquals(1,loss.swaps.size()); assertEquals("light",loss.swaps.get(0).product.id);
  SwapEngine.Outcome gain=recommend(profile("Weight gain","breakfast"));
  assertEquals(1,gain.swaps.size()); assertEquals("dense",gain.swaps.get(0).product.id);
  assertTrue(gain.swaps.get(0).why.contains("more energy"));
  SwapEngine.Outcome muscle=recommend(profile("Muscle gain","breakfast"));
  assertEquals(1,muscle.swaps.size()); assertEquals("protein",muscle.swaps.get(0).product.id);
  assertTrue(muscle.swaps.get(0).why.contains("30 g"));
 }
 @Test public void onlySelectedFoodCategoriesReceiveComparisonCards() throws Exception {
  assertTrue(recommend(profile("Muscle gain","drinks")).swaps.isEmpty());
  assertTrue(GoalComparisons.allowsGroup(profile("Weight loss",""),"breakfast_cereals"));
  assertFalse(GoalComparisons.allowsGroup(profile("Weight loss","chocolate"),"salty_snacks"));
 }
 @Test public void noAdviceForUnselectedGoalsAndClinicalFlagsNeverCancelled() {
  Profile p=profile("Muscle gain","breakfast");
  String text=String.join(" ",GoalComparisons.explain(p,values(300,12)));
  assertFalse(text.contains("weight-loss")); assertFalse(text.contains("weight-gain"));
  p.answers[Profile.HEALTH]="kidney"; assertNull(GoalComparisons.focus(p));
 }
 @Test public void guestAndAccountProfilesUseSameSelectionRules() throws Exception {
  Profile guest=profile("Weight loss","breakfast");
  Profile account=Profile.fromJson(guest.syncJson()).withLocalAnswers(guest);
  assertEquals(recommend(guest).swaps.get(0).product.id,recommend(account).swaps.get(0).product.id);
  assertEquals(LearnCards.selected(guest).get(0).tag,LearnCards.selected(account).get(0).tag);
 }
 @Test public void serverReloadPreservesLocalPreferencesWithoutUploadingHealth() {
  Profile local=profile("Weight loss","chocolate"); local.answers[Profile.HEALTH]="milk"; local.answers[Profile.HEALTH_OTHER]="Kiwi";
  Profile remote=profile("Muscle gain",""); Profile merged=remote.withLocalAnswers(local);
  assertEquals("Muscle gain",merged.answers[Profile.GOAL]); assertEquals("chocolate",merged.answers[Profile.BUYS]);
  assertEquals("milk",merged.answers[Profile.HEALTH]); assertEquals("Kiwi",merged.answers[Profile.HEALTH_OTHER]);
  assertFalse(merged.syncJson().has("health_other")); assertFalse(merged.syncJson().has("buys"));
 }
 @Test public void quakerInlineAmountsDoNotBecomeRecommendedRailValuesOrAllergens() {
  String details="Ingredients: Jumbo Rolled Oats. May contain wheat and barley.\nNutrition Information\nEnergy (kcal) 407, Protein (g) 11.8, Total Fat (g) 9.5, Carbohydrate (g) 68.5, Fibre (g) 10, Sodium (mg) 9.5\nPackaging Type\nPouch";
  Nutrition n=NutritionParser.parse(details); assertNull("Sugar and saturated fat are absent; do not create a grade",n);
  Allergen.Scan declared=Allergen.scan(IngredientFacts.section(details),details);
  assertFalse(declared.mentions(Allergen.TREE_NUT));
 }
 @Test public void chocolateMathUsesStatedBasisAndNeverTotalsAsAdded() {
  String data="Per 100 g\nEnergy (kcal) 531, Protein (g) 7.9, Carbohydrate (g) 60.4, Total Sugars (g) 57, Added Sugars (g) 47.4, Total Fat (g) 29, Saturated Fat (g) 19.6, Sodium (mg) 129";
  Nutrition n=NutritionParser.parse(data); assertNotNull(n); assertEquals(60.4,n.carbohydrateG,.0001);
  assertEquals(9.5748,NutrientMath.portion(n.addedSugarsG,20.2),.0000001);
  assertEquals(19.1496,NutrientMath.percent(NutrientMath.portion(n.addedSugarsG,20.2),50.0),.0000001);
  GradeResult unstated=GradeResult.graded(NutritionParser.parse(data.replace("Per 100 g\n","")),GradeResult.Source.LABEL_TEXT,null);
  assertEquals(GradeResult.State.GRADED,unstated.state); assertFalse(unstated.nutrition.basisStated);
  assertTrue(unstated.caveats.toString().contains("per 100 g or per 100 ml"));
 }
 @Test public void recommendationsDoNotSupplyCurrentProductsNutrition() {
  List<ScreenText> s=List.of(new ScreenText("Classic Potato Chips",300,false),new ScreenText("?19",340,false),new ScreenText("Highlights",500,false),new ScreenText("Ingredients: Potato, oil, salt",600,false),new ScreenText("Similar Products",900,false),new ScreenText("Per 100 g Energy 553 kcal Sugars 0.6 g Saturated Fat 15.8 g Sodium 500 mg",1000,false));
  ProductPage p=ProductPage.detect(s,2400,"com.zeptoconsumerapp"); assertNotNull(p); assertFalse(p.text.contains("553"));
 }
 @Test public void searchSuggestionsCannotReuseAnAnchoredProduct() {
  List<ScreenText> s=List.of(new ScreenText("Ingredients deals",300,false),new ScreenText("Show all results for",500,false));
  assertNull(ProductPage.detect(s,2400,"com.zeptoconsumerapp",null,ProductPage.of("Coffee",123.0,"com.zeptoconsumerapp")));
 }
 @Test public void scalingIncludesAllDeclaredMacrosAndRejectsNonfiniteAmounts() {
  Nutrition n=values(100,3); n.addedSugarsG=4.0; n.totalFatG=2.0; n.transFatG=.1; n.carbohydrateG=10.0;
  Nutrition scaled=PackMath.toPer100(n,25); assertEquals(16,scaled.addedSugarsG,.001); assertEquals(40,scaled.carbohydrateG,.001);
  assertEquals(8,scaled.totalFatG,.001); assertEquals(.4,scaled.transFatG,.001);
  assertThrows(IllegalArgumentException.class,()->PackMath.toPer100(n,Double.NaN));
  n.servingSize=Double.POSITIVE_INFINITY; assertNull(PackMath.servingSize(n));
 }
 @Test public void unknownAdverseNutrientCannotYieldGreenAndNewEvidenceRefreshesNeutral() {
  Nutrition n=values(10,1); n.sodiumMg=null;
  GradeResult r=GradeResult.graded(n,GradeResult.Source.LABEL_TEXT,null,null,Beverage.NOT_A_DRINK,"Ingredients: oats");
  assertEquals(GradeResult.State.NEEDS_INPUT,r.state); assertNull(r.band); assertTrue(r.labelText.contains("oats"));
  GradeResult other=GradeResult.needsInput(null,null,r.note); other.labelText="Ingredients: milk"; assertFalse(r.sameAs(other));
 }
 @Test public void milkPerPackPhotoHeaderNormalisesItsDeclared200Ml() {
  Nutrition n=NutritionParser.parse("Nutrition Information\nServing size 200 ml\nAmounts per pack\nEnergy 116 kcal\nTotal Fat 6 g\nSaturated Fat 4 g\nTrans Fat 0 g\nTotal Sugars 9.6 g\nAdded Sugars 0 g\nCarbohydrate 9.6 g\nProtein 6 g\nSodium 74 mg\nNet quantity 200 ml");
  assertNotNull(n); assertTrue(n.basisStated); assertEquals(58,n.energyKcal,.00001); assertEquals(37,n.sodiumMg,.00001);
  assertEquals(0,n.addedSugarsG,.00001); assertEquals(4.8,n.carbohydrateG,.00001);
 }
 @Test public void perPackIsNotConfusedWithOneOfItsMultipleServings() {
  Nutrition n=NutritionParser.parse("Serving size 100 ml\nNet quantity 300 ml\nNutrition Information\nAmounts per pack\nEnergy 180 kcal\nTotal Sugars 12 g\nTotal Fat 0 g\nSodium 90 mg");
  assertNotNull(n); assertEquals(60,n.energyKcal,.00001); assertEquals(4,n.sugarsG,.00001); assertEquals(30,n.sodiumMg,.00001);
 }
 @Test public void ambiguousPackMetadataAndMinorProfilesDoNotInventAmounts() {
  assertNull(NutritionParser.netQuantity("Net quantity: 1 pc (48 g or 52.9 g)"));
  assertNull(NutritionParser.netQuantityUnit("Net quantity: 1 pc (48 g or 52.9 g)"));
  Profile p=profile("Muscle gain",""); p.adultConfirmed=true; p.answers[Profile.AGE]="12"; p.answers[Profile.WEIGHT]="50";
  assertTrue(Double.isNaN(DailyNeeds.forProfile(p).proteinG));
 }
 @Test public void changingAccountClearsDeviceOnlyPreferences() throws Exception {
  AccountStore store=TestAccounts.onboarded(); Profile p=profile("Weight loss","chocolate"); p.answers[Profile.HEALTH]="milk"; store.profile(p);
  org.json.JSONObject response=new org.json.JSONObject().put("access_token","test-access").put("refresh_token","test-refresh").put("expires_in",3600)
   .put("user",new org.json.JSONObject().put("id","aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee").put("email_confirmed_at","2026-09-27T10:00:00Z"));
  store.session(response); assertTrue(store.profile().buyCodes().isEmpty()); assertTrue(store.profile().healthCodes().isEmpty()); assertFalse(store.profileLoaded());
 }
}
