package edu.campusnexo.domain.shared;

/** Maximum number of rows a query may return; the single place where the limit range is defined. */
public record ResultLimit(int value) {
    public static final int DEFAULT = 20;
    public static final int MAX = 100;

    public ResultLimit {
        DomainChecks.requireRange(value, 1, MAX, "limit");
    }

    public static ResultLimit of(int value) {
        return new ResultLimit(value);
    }
}
