package edu.campusnexo.domain.climate;

public enum HvacMode {
    APAGADO,
    CALEFACCION,
    ENFRIAMIENTO,
    DESHUMIDIFICACION;

    public boolean isActive() {
        return this != APAGADO;
    }
}
