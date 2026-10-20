INSERT INTO campus_branches (branch_id, name, city, climate_profile, avg_outside_temp_c, kwh_tariff_cop) VALUES
('SEDE-TEMPLADA', 'Campus Valle de Aburrá / Sede de demostración', 'Medellín', 'SEDE_TEMPLADA', 22.8, 940.00);

INSERT INTO buildings (building_id, branch_id, name, floors) VALUES
('BLQ-ADM-OCC', 'SEDE-TEMPLADA', 'Bloque de aulas y administración', 3);

INSERT INTO spaces (space_id, building_id, name, space_type, capacity, area_sqm) VALUES
('AULA-ADM-101', 'BLQ-ADM-OCC', 'Aula 101', 'AULA', 32, 48.00);
