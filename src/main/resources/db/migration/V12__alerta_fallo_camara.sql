-- A camera that stops sending frames raises its own alert (FALLO_CAMARA). V4 is already applied in production, so
-- the allowed types change here: PostgreSQL named the inline CHECK of V4 alerts_alert_type_check.
ALTER TABLE alerts DROP CONSTRAINT IF EXISTS alerts_alert_type_check;
ALTER TABLE alerts ADD CONSTRAINT alerts_alert_type_check CHECK (alert_type IN ('CO2_ALTO', 'SOBRECUPO',
    'CONSUMO_ESPACIO_VACIO', 'RESERVA_SIN_USO', 'CPU_ENCENDIDA_SIN_USUARIO', 'ACTIVO_FALTANTE', 'SENSOR_ANOMALO',
    'FALLO_CAMARA'));
