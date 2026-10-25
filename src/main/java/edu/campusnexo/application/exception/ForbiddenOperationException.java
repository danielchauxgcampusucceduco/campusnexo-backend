package edu.campusnexo.application.exception;

/** The caller is authenticated but may not perform this operation on this resource (403). */
public class ForbiddenOperationException extends RuntimeException {
    private final String code;

    public ForbiddenOperationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
