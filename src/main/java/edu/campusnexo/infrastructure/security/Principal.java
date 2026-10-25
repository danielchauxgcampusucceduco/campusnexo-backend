package edu.campusnexo.infrastructure.security;

import java.time.Instant;

/** The caller of a request: a person (from a token), a sensor (from its key) or the local developer. */
public record Principal(String name, Role role, Instant expiresAt) {
    static final String REQUEST_ATTRIBUTE = Principal.class.getName();
}
