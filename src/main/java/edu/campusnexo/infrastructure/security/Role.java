package edu.campusnexo.infrastructure.security;

/**
 * Who may do what. People get VIEWER, OPERATOR or ADMIN (each includes the one before); edge cameras and
 * room boards authenticate as SENSOR with their own keys and may only push readings.
 */
public enum Role {
    VIEWER, OPERATOR, ADMIN, SENSOR;

    boolean satisfies(Access access) {
        return switch (access) {
            case PUBLIC -> true;
            case VIEWER -> this == VIEWER || this == OPERATOR || this == ADMIN;
            case OPERATOR -> this == OPERATOR || this == ADMIN;
            case ADMIN -> this == ADMIN;
            case SENSOR -> this == SENSOR || this == ADMIN;
        };
    }

    /** What an endpoint requires. */
    public enum Access {
        PUBLIC, VIEWER, OPERATOR, ADMIN, SENSOR
    }
}
