# BetterChoice India: on-device edition (v3.9.0)

The Android app: the shopping bubble, label reading, the grade engine, personalisation and healthier swaps, all
running on the phone. App id `com.betterchoice.india`, Android 12+ (minSdk 31, targetSdk 37).

## Build and test

Requirements: JDK 17, Android SDK 37 (build tools 36.0.0). The Gradle wrapper pins AGP 9.3.2 / Gradle 9.5.0.

```bash
./gradlew testDebugUnitTest          # unit, evidence and decision-matrix tests (429)
./gradlew assembleDebug              # app/build/outputs/apk/debug/app-debug.apk
./gradlew lintDebug lintRelease      # lint
python tools/verify_release.py       # release checks (permissions, components, no test-only strings)
```

Without `BC_SUPABASE_URL` and `BC_SUPABASE_PUBLISHABLE_KEY` the build runs fully on-device, with sign-in off.
With them (public values only), sign-in and contributions use your own Supabase project; see
[`../docs/supabase-setup.md`](../docs/supabase-setup.md).

## Code map (`app/src/main/java/com/betterchoice/android`)

| Area | Classes |
|---|---|
| Screen and bubble | `BubbleService`, `BubbleOverlay`, `ScreenReader`, `WindowSelection`, `ForegroundPolicy`, `ProductWatcher` |
| Reading | `ProductPage`, `ProductAnalyzer`, `PackScanner`, `PackScan`, `OcrRows`, `Gtin`, `LabelEvidence` |
| Parsing | `NutritionParser`, `Nutrition`, `LabelBasis`, `PackMath`, `IngredientParser`, `InsAdditives` |
| Grading | `NutriGrade`, `BeverageGrade`, `GradeResult`, `Beverage`, `FoodCategory`, `AdditiveLimits` |
| Personalisation | `Profile`, `SafetyProfile`, `SafetyGate`, `DietRules`, `ConditionRules`, `Allergen`, `NutritionCalculator`, `DailyNeeds`, `RecommendationContext`, `Evidence` |
| Recommendation | `SwapEngine`, `SwapCatalogue`, `SwapCheck`, `GoalComparisons` |
| Card and UI | `GradeCard`, `CardFacts`, `DailyImpact`, `Highlights`, `OnboardingActivity`, `MainActivity` |
| Account (optional) | `AuthApi`, `AuthController`, `AuthConfig`, `SecureVault`, `ConsentStore` |

## Other folders

- `security/evidence/`: the regulations and guidelines every constant cites (see [`../NOTICE.md`](../NOTICE.md))
- `security/`: privacy policy, security and Play reviews, release notes
- `supabase/`: optional backend (migrations, the account-deletion edge function)
- `tools/`: catalogue builder (`curate_swaps.py`, `off_india_*.py`), calculator cross-check, release verification
- `DEVICE_TESTS.md`: manual device test protocol
