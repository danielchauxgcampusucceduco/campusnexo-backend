package edu.campusnexo.application.port.in;

import static java.util.Objects.requireNonNull;

/**
 * Who asks for a use case: the account name and whether it is an administrator. The infrastructure builds
 * it from the authenticated session; the application uses it for ownership rules and authorship.
 */
public record Requester(String name, boolean administrator) {
    public Requester {
        requireNonNull(name, "name");
    }

    public static Requester administrator(String name) {
        return new Requester(name, true);
    }

    public static Requester user(String name) {
        return new Requester(name, false);
    }
}
