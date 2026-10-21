# Salones desde el plano del campus

Scripts que sacaron los 107 espacios de `V12__real_rooms_from_plan.sql` y el `campusPlan.json` del frontend a partir de `PLANOS_UCC_2025_-_B.dwg` (UCC campus Pasto, actualización 2025-B).

```bash
npm install @mlightcad/libredwg-web
cp /ruta/PLANOS_UCC_2025_-_B.dwg plano.dwg
node render.mjs          # muros y textos del plano -> segs.json
pip install pillow numpy scipy shapely
python campus.py         # un polígono por salón (relleno desde cada rótulo) -> campus.json
python sql.py > V12.sql  # migración Flyway
```

- `campus.py` recorta cada piso con `BOXES` (coordenadas del dibujo), rasteriza las capas de muros a 8 px/m y rellena desde el rótulo de cada salón. Biblioteca, hemeroteca, cafetería y auditorio del piso cero se dibujan a mano porque no tienen muros cerrados en el plano.
- Bloque: los salones con código conservan la letra del plano (`421-A`, `402-B`); los que no tienen código van al bloque B si son biblioteca, cafetería, laboratorio o sala de cómputo, y al A en los demás casos.
- Pupitres, aforo y computadores no están en el plano. Los salones de clase (AULA) tienen 36 pupitres en 6 × 6 (dato del campus); los demás espacios se estiman por tipo y área.
