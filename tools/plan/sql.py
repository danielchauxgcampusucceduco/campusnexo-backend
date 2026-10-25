"""Genera la semilla Flyway del campus a partir de campus.json (salida de campus.py).

Uso: python sql.py [campus.json] > ../../src/main/resources/db/migration/V9__salones_ucc_pasto.sql
"""
import json
import sys

# Supuesto del prototipo, por confirmar con mantenimiento: estos tipos de espacio tienen equipo de clima.
CON_CLIMA = {"AULA", "LABORATORIO", "SALA_COMPUTO", "AUDITORIO"}
# Dato del campus (2026-10-21): toda aula tiene 36 pupitres en 6 filas de 6.
PUPITRES_AULA = (6, 6)

CABECERA = """-- The UCC campus in Pasto (Calle 18 # 47-150, Torobajo) and its 107 real rooms, taken from the institutional plan
-- "PLANOS_UCC_2025_-_B.dwg" (planimetry update 2025-B, floors 0 to 5) by tools/plan. Rooms with a door code keep it
-- (AULA 420 A -> 420-A); the rest get one from their floor, block and name. Every classroom has 36 desks in 6 x 6
-- (campus fact); capacity, seats and computers of the other rooms are estimated from type and area, and which rooms
-- have a climate unit is a prototype assumption: both must be confirmed with the campus before real use.
INSERT INTO campus_branches (branch_id, name, city, climate_profile, avg_outside_temp_c, kwh_tariff_cop) VALUES
('UCC-PASTO', 'Universidad Cooperativa de Colombia · Campus Pasto', 'Pasto', 'SEDE_ANDINA', 13.5, 865.00);

INSERT INTO buildings (building_id, branch_id, name, floors) VALUES
('BLOQUE-A', 'UCC-PASTO', 'Bloque A', 6),
('BLOQUE-B', 'UCC-PASTO', 'Bloque B', 6);

INSERT INTO spaces (space_id, building_id, name, space_type, capacity, area_sqm, floor, seat_rows, seat_columns,
                    computers, hvac_installed) VALUES"""

MESAS_BIBLIOTECA = """
-- The reading room of the library has tables, not a seat grid: eight table regions drawn over its camera.
INSERT INTO study_zone_rois (zone_id, space_id, label, polygon) VALUES"""


def valor(x):
    return "NULL" if x is None else str(x)


def texto(s):
    return s.replace("'", "''")


def fila(salon):
    filas, columnas, aforo = salon["seatRows"], salon["seatColumns"], salon["capacity"]
    if salon["type"] == "AULA":
        filas, columnas = PUPITRES_AULA
        aforo = filas * columnas
    clima = "TRUE" if salon["type"] in CON_CLIMA else "FALSE"
    return (f"('{salon['spaceId']}', 'BLOQUE-{salon['block']}', '{texto(salon['name'])}', '{salon['type']}', {aforo}, "
            f"{salon['areaSqm']:.2f}, {salon['floor']}, {valor(filas)}, {valor(columnas)}, {salon['computers']}, {clima})")


def mesas():
    columnas = [[0.06, 0.26], [0.29, 0.49], [0.52, 0.72], [0.75, 0.95]]
    filas = [[0.35, 0.58], [0.65, 0.88]]
    salida, n = [], 1
    for y0, y1 in filas:
        for x0, x1 in columnas:
            salida.append(f"('BIB2B-M{n:02d}', 'BIBLIOTECA-2B', 'Mesa {n}', "
                          f"'{x0:.4f} {y0:.4f};{x1:.4f} {y0:.4f};{x1:.4f} {y1:.4f};{x0:.4f} {y1:.4f}')")
            n += 1
    return salida


def main(ruta="campus.json"):
    # Flyway lee UTF-8; en Windows la consola usaría su página de códigos y CRLF.
    sys.stdout.reconfigure(encoding="utf-8", newline="\n")
    with open(ruta, encoding="utf-8") as archivo:
        campus = json.load(archivo)
    filas = [fila(salon) for piso in campus["floors"] for salon in piso["rooms"]]
    print(CABECERA)
    print(",\n".join(filas) + ";")
    print(MESAS_BIBLIOTECA)
    print(",\n".join(mesas()) + ";")


if __name__ == "__main__":
    main(*sys.argv[1:])
