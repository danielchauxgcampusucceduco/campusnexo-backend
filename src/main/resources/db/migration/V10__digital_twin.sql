-- Desk regions drawn over each study-area camera, in normalized image coordinates ("x y;x y;...").
CREATE TABLE study_zone_rois (
    zone_id VARCHAR(32) PRIMARY KEY,
    space_id VARCHAR(32) NOT NULL REFERENCES spaces (space_id),
    label VARCHAR(60) NOT NULL,
    polygon TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_rois_space ON study_zone_rois (space_id);

-- Latest digital-twin state per space. The document holds the full state, including the Kalman
-- and thermal-controller traces; status and HVAC state are columns for SQL reporting.
CREATE TABLE space_twin_states (
    space_id VARCHAR(32) PRIMARY KEY REFERENCES spaces (space_id),
    status VARCHAR(32) NOT NULL CHECK (status IN ('LIBRE', 'RESERVADO_EN_ESPERA', 'OCUPADO_OPTIMO',
        'ALERTA_SUBUTILIZADO', 'ALERTA_SOBRECUPO', 'LIBERADO_POR_NO_SHOW', 'HABILITADO_POPUP_PARCIALES',
        'MANTENIMIENTO')),
    hvac_state VARCHAR(32) NOT NULL CHECK (hvac_state IN ('APAGADO', 'PRE_ENFRIAMIENTO_IA', 'CONFORT_ACTIVO',
        'ECO_SETBACK_ENTRE_CLASES', 'CORTE_INERCIA_TERMICA', 'BLOQUEO_AHORRO_VACIO', 'VENTILACION_CO2_ACTIVA',
        'OVERRIDE_MANUAL_DOCENTE')),
    updated_at TIMESTAMPTZ NOT NULL,
    document TEXT NOT NULL
);

-- Demo layout of the library's second floor: 2 rows × 4 desks seen by one ceiling camera.
INSERT INTO study_zone_rois (zone_id, space_id, label, polygon) VALUES
('BIB2-M01', 'BIB-PISO-2', 'Mesa 1', '0.0600 0.3500;0.2600 0.3500;0.2600 0.5800;0.0600 0.5800'),
('BIB2-M02', 'BIB-PISO-2', 'Mesa 2', '0.2900 0.3500;0.4900 0.3500;0.4900 0.5800;0.2900 0.5800'),
('BIB2-M03', 'BIB-PISO-2', 'Mesa 3', '0.5200 0.3500;0.7200 0.3500;0.7200 0.5800;0.5200 0.5800'),
('BIB2-M04', 'BIB-PISO-2', 'Mesa 4', '0.7500 0.3500;0.9500 0.3500;0.9500 0.5800;0.7500 0.5800'),
('BIB2-M05', 'BIB-PISO-2', 'Mesa 5', '0.0600 0.6500;0.2600 0.6500;0.2600 0.8800;0.0600 0.8800'),
('BIB2-M06', 'BIB-PISO-2', 'Mesa 6', '0.2900 0.6500;0.4900 0.6500;0.4900 0.8800;0.2900 0.8800'),
('BIB2-M07', 'BIB-PISO-2', 'Mesa 7', '0.5200 0.6500;0.7200 0.6500;0.7200 0.8800;0.5200 0.8800'),
('BIB2-M08', 'BIB-PISO-2', 'Mesa 8', '0.7500 0.6500;0.9500 0.6500;0.9500 0.8800;0.7500 0.8800');
