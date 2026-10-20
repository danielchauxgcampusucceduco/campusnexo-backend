package edu.campusnexo.application.catalog;

public class SpaceNotFoundException extends RuntimeException {
    public SpaceNotFoundException(String spaceId) { super("No existe el espacio: " + spaceId); }
}
