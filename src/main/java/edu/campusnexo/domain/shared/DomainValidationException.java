package edu.campusnexo.domain.shared;

/** A value or rule of the domain was violated by the data received. */
public class DomainValidationException extends RuntimeException {
    public DomainValidationException(String message) {
        super(message);
    }
}
