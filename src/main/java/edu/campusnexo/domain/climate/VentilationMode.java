package edu.campusnexo.domain.climate;

public enum VentilationMode {
    NINGUNA,
    NATURAL,
    MECANICA;

    public boolean isActive() {
        return this != NINGUNA;
    }
}
