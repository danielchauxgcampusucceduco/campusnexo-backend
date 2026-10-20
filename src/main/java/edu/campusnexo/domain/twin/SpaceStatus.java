package edu.campusnexo.domain.twin;

/** Operational status of a space in the digital twin. */
public enum SpaceStatus {
    LIBRE,
    RESERVADO_EN_ESPERA,
    OCUPADO_OPTIMO,
    ALERTA_SUBUTILIZADO,
    ALERTA_SOBRECUPO,
    LIBERADO_POR_NO_SHOW,
    HABILITADO_POPUP_PARCIALES,
    MANTENIMIENTO
}
