package edu.campusnexo.application.exception;

public class SpaceNotFoundException extends ResourceNotFoundException {
    public SpaceNotFoundException(String spaceId) {
        super("space_not_found", "No existe el espacio: " + spaceId);
    }
}
