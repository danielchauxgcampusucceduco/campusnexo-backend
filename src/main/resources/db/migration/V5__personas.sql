-- Teachers and students the administration registers so bookings and check-ins name who is responsible. Only the
-- institutional code and the name: no photos, no biometrics, no contact data (Ley 1581 de 2012).
CREATE TABLE personas (
    codigo VARCHAR(32) PRIMARY KEY,
    tipo VARCHAR(16) NOT NULL CHECK (tipo IN ('DOCENTE', 'ESTUDIANTE')),
    nombre VARCHAR(120) NOT NULL,
    adscripcion VARCHAR(120) NOT NULL
);
CREATE INDEX idx_personas_tipo_nombre ON personas (tipo, nombre);
