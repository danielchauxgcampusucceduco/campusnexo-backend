import json
c=json.load(open('campus.json'))
v=lambda x: 'NULL' if x is None else str(x)
q=lambda s: s.replace("'","''")
rows=[]
for f in c['floors']:
    for r in f['rooms']:
        rows.append(f"('{r['spaceId']}', 'BLOQUE-{r['block']}', '{q(r['name'])}', '{r['type']}', {r['capacity']}, {r['areaSqm']:.2f}, {r['floor']}, {v(r['seatRows'])}, {v(r['seatColumns'])}, {r['computers']})")
print("""-- Real rooms of the campus, taken from the institutional plan "PLANOS_UCC_2025_-_B.dwg" (planimetry update
-- 2025-B, floors 0 to 5). Rooms with a door code keep it (AULA 420 A -> 420-A); the rest get one from their
-- floor, block and name. Capacity, seat grid and computers are estimated from the room type and the area
-- measured on the plan: they are illustrative values for the demo, not the official inventory.

CREATE TEMPORARY TABLE old_spaces AS
    SELECT s.space_id FROM spaces s JOIN buildings b ON b.building_id = s.building_id WHERE b.branch_id = 'UCC-PASTO';
DELETE FROM study_zone_rois WHERE space_id IN (SELECT space_id FROM old_spaces);
DELETE FROM space_twin_states WHERE space_id IN (SELECT space_id FROM old_spaces);
DELETE FROM reservations WHERE space_id IN (SELECT space_id FROM old_spaces);
DELETE FROM alerts WHERE space_id IN (SELECT space_id FROM old_spaces);
DELETE FROM occupancy_estimates WHERE space_id IN (SELECT space_id FROM old_spaces);
DELETE FROM climate_recommendations WHERE space_id IN (SELECT space_id FROM old_spaces);
DELETE FROM telemetry_stream WHERE space_id IN (SELECT space_id FROM old_spaces);
DELETE FROM spaces WHERE space_id IN (SELECT space_id FROM old_spaces);
DROP TABLE old_spaces;

UPDATE buildings SET floors = 6 WHERE branch_id = 'UCC-PASTO';
ALTER TABLE spaces DROP CONSTRAINT IF EXISTS spaces_floor_check;
ALTER TABLE spaces ADD CONSTRAINT spaces_floor_check CHECK (floor BETWEEN 0 AND 10);

INSERT INTO spaces (space_id, building_id, name, space_type, capacity, area_sqm, floor, seat_rows, seat_columns, computers) VALUES""")
print(",\n".join(rows)+";")
print("""
-- The reading room of the library has tables, not a seat grid: eight table regions drawn over its camera.
INSERT INTO study_zone_rois (zone_id, space_id, label, polygon) VALUES""")
cols=[[0.06,0.26],[0.29,0.49],[0.52,0.72],[0.75,0.95]]; rws=[[0.35,0.58],[0.65,0.88]]; n=1; out=[]
for y0,y1 in rws:
    for x0,x1 in cols:
        out.append(f"('BIB2B-M{n:02d}', 'BIBLIOTECA-2B', 'Mesa {n}', '{x0:.4f} {y0:.4f};{x1:.4f} {y0:.4f};{x1:.4f} {y1:.4f};{x0:.4f} {y1:.4f}')"); n+=1
print(",\n".join(out)+";")
