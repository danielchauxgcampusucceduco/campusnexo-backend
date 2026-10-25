package edu.campusnexo.application.exception;

/** A requested resource does not exist. {@link #code()} is the stable error code for clients. */
public class ResourceNotFoundException extends RuntimeException {
    private final String code;

    public ResourceNotFoundException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
