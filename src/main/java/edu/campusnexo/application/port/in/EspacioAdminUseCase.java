package edu.campusnexo.application.port.in;

import edu.campusnexo.domain.catalog.CampusSpace;

/**
 * The administration adjusts a room with the measurements taken on site: the pilot rooms 301-A and 303-A start
 * with illustrative ones, and the capacity or seat grid of any room can change after a refurbishment.
 */
public interface EspacioAdminUseCase {
    CampusSpace ajustar(String spaceId, Medidas medidas);

    /** {@code seatRows} and {@code seatColumns} go together: both null for a room without a seat grid. */
    record Medidas(int capacity, double areaSqm, Integer seatRows, Integer seatColumns, int computers,
                   boolean hvacInstalled) {
    }
}
