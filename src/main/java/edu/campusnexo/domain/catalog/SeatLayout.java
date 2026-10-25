package edu.campusnexo.domain.catalog;

import static edu.campusnexo.domain.shared.DomainChecks.requireRange;

/**
 * Seats (desks, chairs or workstations) of a room laid out as a grid of {@code rows} × {@code columns},
 * as the ceiling camera sees them. Used to derive one region of interest per seat.
 */
public record SeatLayout(int rows, int columns) {
    public SeatLayout {
        requireRange(rows, 1, 40, "rows");
        requireRange(columns, 1, 40, "columns");
    }

    public int seats() {
        return rows * columns;
    }
}
