package com.haalkhata.core.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Central helper for monetary amounts. Storage remains double, but all
 * money is rounded to 2 decimals at every write boundary so stored values
 * never drift from the ledger. A full BigDecimal migration is tracked
 * separately. */
public final class MoneyUtil {

    private MoneyUtil() {
    }

    public static double round2(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0;
        }
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    public static double max(double a, double b) {
        return Math.max(a, b);
    }

    public static double min(double a, double b) {
        return Math.min(a, b);
    }
}