package edu.campusnexo.domain.catalog;

/** Kind of space. Values are kept in Spanish, as users see them. */
public enum SpaceType {
    AULA(true),
    SALA_ESTUDIO(true),
    AUDITORIO(true),
    ADMINISTRATIVA(false),
    LOGISTICA(false),
    BIBLIOTECA(true),
    CAFETERIA(true),
    LABORATORIO(true),
    SALA_COMPUTO(true),
    ZONA_COMUN(false);

    private final boolean accesibleParaEstudiantes;

    SpaceType(boolean accesibleParaEstudiantes) {
        this.accesibleParaEstudiantes = accesibleParaEstudiantes;
    }

    /** Academic and study spaces a student can use; offices, staff rooms and storage are not followed. */
    public boolean accesibleParaEstudiantes() {
        return accesibleParaEstudiantes;
    }
}
