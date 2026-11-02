package com.betterchoice.android;

import java.math.BigDecimal;
import java.math.MathContext;

/** Deterministic decimal arithmetic. Round only when formatting the result for display. */
final class NutrientMath {
    private static final MathContext PRECISION = MathContext.DECIMAL128;
    static boolean positive(Double value) { return value != null && Double.isFinite(value) && value > 0; }
    static boolean nonNegative(Double value) { return value != null && Double.isFinite(value) && value >= 0; }
    static Double portion(Double per100, double quantity) {
        if (!nonNegative(per100) || !positive(quantity)) return null;
        double result = BigDecimal.valueOf(per100).multiply(BigDecimal.valueOf(quantity), PRECISION)
            .divide(BigDecimal.valueOf(100), PRECISION).doubleValue();
        return Double.isFinite(result) ? result : null;
    }
    static Double percent(Double amount, double reference) {
        if (!nonNegative(amount) || !positive(reference)) return null;
        double result = BigDecimal.valueOf(amount).multiply(BigDecimal.valueOf(100), PRECISION)
            .divide(BigDecimal.valueOf(reference), PRECISION).doubleValue();
        return Double.isFinite(result) ? result : null;
    }
    static Double normalize(Double amount, double basis) { return percent(amount, basis); }
    private NutrientMath() {}
}
