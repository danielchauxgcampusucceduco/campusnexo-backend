-- Occupancy estimates asked to the AI service (or its local fallback) and the alerts raised by the observers.
CREATE TABLE occupancy_estimates (
    estimate_id UUID PRIMARY KEY,
    space_id VARCHAR(32) NOT NULL REFERENCES spaces (space_id),
    estimated_at TIMESTAMPTZ NOT NULL,
    occupancy_estimate INTEGER NOT NULL CHECK (occupancy_estimate >= 0),
    confidence_pct DOUBLE PRECISION NOT NULL CHECK (confidence_pct BETWEEN 0 AND 100),
    camera_available BOOLEAN NOT NULL,
    is_estimate BOOLEAN NOT NULL,
    source VARCHAR(24) NOT NULL CHECK (source IN ('SERVICIO_IA', 'RESPALDO_LOCAL')),
    note TEXT NOT NULL
);
CREATE INDEX idx_occupancy_space_time ON occupancy_estimates (space_id, estimated_at DESC);

CREATE TABLE alerts (
    alert_id UUID PRIMARY KEY,
    space_id VARCHAR(32) NOT NULL REFERENCES spaces (space_id),
    alert_type VARCHAR(32) NOT NULL CHECK (alert_type IN ('CO2_ALTO', 'SOBRECUPO', 'CONSUMO_ESPACIO_VACIO',
        'RESERVA_SIN_USO', 'CPU_ENCENDIDA_SIN_USUARIO', 'ACTIVO_FALTANTE', 'SENSOR_ANOMALO')),
    severity VARCHAR(16) NOT NULL CHECK (severity IN ('INFORMATIVA', 'ADVERTENCIA', 'CRITICA')),
    detected_at TIMESTAMPTZ NOT NULL,
    message TEXT NOT NULL,
    measured_value DOUBLE PRECISION NOT NULL,
    threshold_value DOUBLE PRECISION NOT NULL
);
CREATE INDEX idx_alerts_time ON alerts (detected_at DESC);
CREATE INDEX idx_alerts_space_type_time ON alerts (space_id, alert_type, detected_at DESC);
