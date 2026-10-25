package edu.campusnexo.domain.activo;

import static edu.campusnexo.domain.shared.DomainChecks.requireRange;
import static edu.campusnexo.domain.shared.DomainChecks.requireText;

/**
 * A workstation the edge device found with the camera in the empty room: at row {@code fila} and column
 * {@code columna} of the grid it deduced (1-based, row 1 nearest the board), with the furniture it saw there.
 * {@code cpu} is the computer whose monitor showed the pairing pattern at this seat, or {@code null}.
 */
public record PuestoPropuesto(int fila, int columna, boolean monitor, boolean silla, boolean mesa, String cpu) {
    public PuestoPropuesto {
        requireRange(fila, 1, 26, "fila");
        requireRange(columna, 1, 40, "columna");
        if (cpu != null) {
            cpu = requireText(cpu, "cpu", 40).strip();
        }
    }

    /** The name staff read on the plan: A1 is the first seat of the row nearest the board. */
    public String etiqueta() {
        return (char) ('A' + fila - 1) + String.valueOf(columna);
    }

    /** Seen without chair or without table: proposed anyway, marked for review. */
    public boolean incompleto() {
        return !silla || !mesa;
    }

    public PuestoPropuesto conCpu(String codigo) {
        return new PuestoPropuesto(fila, columna, monitor, silla, mesa, codigo);
    }
}
