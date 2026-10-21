package edu.campusnexo.domain.twin;

/** Another writer stored a newer twin of the same room between our read and our write. */
public class ConcurrentTwinUpdateException extends RuntimeException {
    public ConcurrentTwinUpdateException(String spaceId) {
        super("El gemelo del espacio " + spaceId + " cambió mientras se actualizaba");
    }
}
