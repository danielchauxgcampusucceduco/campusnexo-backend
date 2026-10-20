package edu.campusnexo.domain.shared;

import java.math.BigDecimal;

/** Centralized invariant checks so every model reports violations the same way. */
public final class DomainChecks {
    private DomainChecks() {
    }

    public static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new DomainValidationException(field + " es obligatorio");
        }
        return value;
    }

    public static String requireText(String value, String field, int maxLength) {
        requireText(value, field);
        if (value.length() > maxLength) {
            throw new DomainValidationException(field + " admite máximo " + maxLength + " caracteres");
        }
        return value;
    }

    public static <T> T requireNonNull(T value, String field) {
        if (value == null) {
            throw new DomainValidationException(field + " es obligatorio");
        }
        return value;
    }

    public static double requireRange(double value, double min, double max, String field) {
        if (!Double.isFinite(value) || value < min || value > max) {
            throw new DomainValidationException(field + " debe estar entre " + format(min) + " y " + format(max));
        }
        return value;
    }

    public static int requireRange(int value, int min, int max, String field) {
        if (value < min || value > max) {
            throw new DomainValidationException(field + " debe estar entre " + min + " y " + max);
        }
        return value;
    }

    public static void require(boolean condition, String message) {
        if (!condition) {
            throw new DomainValidationException(message);
        }
    }

    private static String format(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }
}
