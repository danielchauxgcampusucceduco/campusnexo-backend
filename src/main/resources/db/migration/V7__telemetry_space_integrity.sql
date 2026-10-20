-- V1 created telemetry before the catalog existed, so it never referenced spaces.
-- NOT VALID enforces the key for new rows without failing on legacy rows; run
-- "ALTER TABLE telemetry_stream VALIDATE CONSTRAINT fk_telemetry_space" after cleaning them.
ALTER TABLE telemetry_stream
    ADD CONSTRAINT fk_telemetry_space FOREIGN KEY (space_id) REFERENCES spaces (space_id) NOT VALID;
