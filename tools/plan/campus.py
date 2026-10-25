"""Builds the campus model of UCC Pasto from PLANOS_UCC_2025_-_B.dwg (extracted to segs.json).

For every floor: the wall drawing (for the dashboard plan) and the rooms, recovered by flood-filling the
wall raster from each room label. Room codes come from the labels ("AULA 420 A" -> 420-A); spaces without a
code get one from their floor, block and name. Capacity, seats and computers are estimated from the
room type and area (illustrative, not the official inventory).
"""
import json, math, re, sys, unicodedata
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

PX = 8
CLOSE = 7
data = json.load(open('segs.json'))
SEGS, TEXTS = data['segs'], data['texts']
WALLS = {'01', '02', '04', '05', '06', 'VIDRIO', 'Nuevo_Interior - Particiones_Pluma_Nº__4', 'Muro', 'LADRILLO 2', 'Pilar', 'COLUMNA'}
BOXES = {0: (5903, -1675, 5990, -1595), 1: (5903, -1830, 5995, -1712), 2: (5903, -1945, 5990, -1866),
         3: (5904, -2083, 5990, -2003), 4: (5704, -1675, 5795, -1598), 5: (5704, -1805, 5795, -1724)}
FLOOR_NAME = {0: 'Piso cero', 1: 'Primer piso', 2: 'Segundo piso', 3: 'Tercer piso', 4: 'Cuarto piso', 5: 'Quinto piso'}

NOT_A_LABEL = re.compile(r'PISO|M2|M²|^P-|^PT-|VINISOL|GRANITO|^N\s*[+-]|^\d|^S$|ESCALA|PLANTA ', re.I)
NOT_A_ROOM = re.compile(r'BAÑO|BANO|CIRCULACI|HALL|ASCENSOR|ASENSOR|ESCALERA|CUARTO TECNICO|BALCON|TERRAZA|VACIO|'
                        r'ASEO|JARDIN|ANDEN|PLAZOLETA|PASILLO|RAMPA|PATIO|ZONA VERDE|CUBIERTA|AZOTEA|REJILLA|DUCHA|'
                        r'VESTIER|SENDERO|Muro Verde|CARRERA|CALLE|PLANTA ELECTRICA|SUB\.? ESTACI|RESIDUOS|RECICLABLES|'
                        r'PELIGROSOS|ORDINARIOS|BICICLETAS|URKUBICI|HIDROFLO|COMPRESOR|TANQUES', re.I)

TYPE_RULES = [
    (r'INFORMATICA|INFORMÁTICA|COMPUTO|CÓMPUTO', 'SALA_COMPUTO'),
    (r'AUDITORIO', 'AUDITORIO'),
    (r'BIBLIOTECA|LECTURA|CONSULTA|HEMEROTECA|PRESTAMO', 'BIBLIOTECA'),
    (r'CAFETERIA|CAFETIN|SENSORIAL CAFE', 'CAFETERIA'),
    (r'ESTUDIO|AREA DE ESTUDIOS', 'SALA_ESTUDIO'),
    (r'AULA', 'AULA'),
    (r'LABORATORIO|LAB\.|CLINIC|SIMULACI|QUIROFANO|QUIRURG|UNIDAD|PRECLINICA|MORFOLOG|DESTREZAS|PROCEDIMIENTOS|'
     r'OSTEOTECA|DEBRIEFING|CUIDADO CRITICO|ESTERIL', 'LABORATORIO'),
    (r'BODEGA|ALMACEN|LOCKERS|TALLER|ARCHIVO|LOCAL|GIMNASIO|JUEGOS|INFRAESTRUCTURA|CORRESPOND|VIGILANTES', 'LOGISTICA'),
    (r'.', 'ADMINISTRATIVA'),
]
# m² per person, seats and computers per type (illustrative).
CAPACITY = {'AULA': 1.6, 'SALA_ESTUDIO': 2.0, 'AUDITORIO': 1.0, 'BIBLIOTECA': 2.6, 'CAFETERIA': 1.6,
            'LABORATORIO': 2.6, 'SALA_COMPUTO': 2.0, 'ADMINISTRATIVA': 7.0, 'LOGISTICA': 12.0, 'ZONA_COMUN': 3.0}
SEATED = {'AULA', 'SALA_ESTUDIO', 'AUDITORIO', 'BIBLIOTECA', 'CAFETERIA', 'LABORATORIO', 'SALA_COMPUTO'}
CODE = re.compile(r'AULA\s*(?:No\.?\s*)?(\d{3})\s*([AB])?\b', re.I)


def in_box(x, y, box):
    return box[0] <= x <= box[2] and box[1] <= y <= box[3]


def raster(box):
    x0, y0, x1, y1 = box
    im = Image.new('L', (int((x1 - x0) * PX), int((y1 - y0) * PX)), 0)
    dr = ImageDraw.Draw(im)
    for a, b, c, d, layer in SEGS:
        if layer in WALLS and (in_box(a, b, box) or in_box(c, d, box)):
            dr.line([((a - x0) * PX, (y1 - b) * PX), ((c - x0) * PX, (y1 - d) * PX)], fill=255, width=2)
    return np.array(im.filter(ImageFilter.MaxFilter(CLOSE))) > 0


def flood(mask, sx, sy, limit):
    h, w = mask.shape
    if not (0 <= sx < w and 0 <= sy < h):
        return None
    if mask[sy, sx]:
        for r in range(1, 10):
            hit = next(((x, y) for x in range(sx - r, sx + r + 1) for y in (sy - r, sy + r)
                        if 0 <= x < w and 0 <= y < h and not mask[y, x]), None)
            if hit:
                sx, sy = hit
                break
        else:
            return None
    seen = np.zeros(mask.shape, dtype=bool)
    seen[sy, sx] = True
    stack, xs, ys = [(sx, sy)], [], []
    while stack:
        x, y = stack.pop()
        xs.append(x); ys.append(y)
        if len(xs) > limit:
            return None
        for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
            if 0 <= nx < w and 0 <= ny < h and not seen[ny, nx] and not mask[ny, nx]:
                seen[ny, nx] = True
                stack.append((nx, ny))
    return np.array(xs), np.array(ys)


def hull(points):
    pts = sorted(set(points))
    if len(pts) < 3:
        return pts
    cross = lambda o, a, b: (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0])
    lower, upper = [], []
    for p in pts:
        while len(lower) >= 2 and cross(lower[-2], lower[-1], p) <= 0:
            lower.pop()
        lower.append(p)
    for p in reversed(pts):
        while len(upper) >= 2 and cross(upper[-2], upper[-1], p) <= 0:
            upper.pop()
        upper.append(p)
    return lower[:-1] + upper[:-1]


def poly_area(poly):
    return abs(sum(poly[i][0] * poly[i - 1][1] - poly[i - 1][0] * poly[i][1] for i in range(len(poly)))) / 2


def min_rect(poly):
    """Minimum-area rectangle of a convex polygon: centre, width (long side), height and angle."""
    best = None
    for i in range(len(poly)):
        (x1, y1), (x2, y2) = poly[i - 1], poly[i]
        ang = math.atan2(y2 - y1, x2 - x1)
        c, s = math.cos(-ang), math.sin(-ang)
        rx = [x * c - y * s for x, y in poly]
        ry = [x * s + y * c for x, y in poly]
        area = (max(rx) - min(rx)) * (max(ry) - min(ry))
        if best is None or area < best[0]:
            mx, my = (max(rx) + min(rx)) / 2, (max(ry) + min(ry)) / 2
            cx, cy = mx * math.cos(ang) - my * math.sin(ang), mx * math.sin(ang) + my * math.cos(ang)
            best = (area, cx, cy, max(rx) - min(rx), max(ry) - min(ry), ang)
    _, cx, cy, w, h, ang = best
    if h > w:
        w, h, ang = h, w, ang + math.pi / 2
    ang = (ang + math.pi / 2) % math.pi - math.pi / 2
    return {'cx': round(cx, 2), 'cy': round(cy, 2), 'w': round(w, 2), 'h': round(h, 2), 'angle': round(math.degrees(ang), 1)}


def nice(text):
    small = {'de', 'del', 'la', 'las', 'los', 'y', 'e', 'a', 'en', 'con'}
    words = re.sub(r'\s+', ' ', text.replace('No ', 'N.º ')).strip().lower().split(' ')
    out = [w if (i > 0 and w in small) else (w.upper() if re.fullmatch(r'[ab]|\d+[ab]?|tic|adn|rx|uci|darc|icetex|indesco|cad', w) else w.capitalize())
           for i, w in enumerate(words)]
    return ' '.join(out)


ACCENTS = {'Informatica': 'Informática', 'Clinica': 'Clínica', 'Clinicas': 'Clínicas', 'Odontologia': 'Odontología',
           'Coordinacion': 'Coordinación', 'Administracion': 'Administración', 'Ingenieria': 'Ingeniería', 'Quimica': 'Química',
           'Fisica': 'Física', 'Area': 'Área', 'Tesoreria': 'Tesorería', 'Investigacion': 'Investigación', 'Gestion': 'Gestión',
           'Morfologia': 'Morfología', 'Preclinica': 'Preclínica', 'Aplicacion': 'Aplicación', 'Critico': 'Crítico',
           'Simulacion': 'Simulación', 'Biologia': 'Biología', 'Medicas': 'Médicas', 'Salon': 'Salón'}
NAME_FIX = {
    'De Bioprocesos Laboratorio': 'Laboratorio de Bioprocesos',
    'De Historias Clínicas Sala de Consultas': 'Consulta de historias clínicas',
    'Real Lidad Laboratorio Mixta': 'Laboratorio de Realidad Mixta',
    'Microbiologia Laboratorio de': 'Laboratorio de Microbiología',
    'Informática Aula de': 'Aula de Informática',
    'Sensorial Cafe Laboratorio': 'Laboratorio Sensorial de Café',
    'Programa de Ingeniería Industrial Laboratorio de Operaciones': 'Laboratorio de Operaciones · Ing. Industrial',
    'Invesstigaciones Médicas Centro de': 'Centro de Investigaciones Médicas',
    'Y Egresados Internacion Oficina de': 'Internacionalización y Egresados',
    'Comunicaciones Oficina de': 'Oficina de Comunicaciones',
    'Sociojuridico Investigación Centro de': 'Centro de Investigación Sociojurídica',
    'Enfermeria Programa de': 'Programa de Enfermería',
    'Institucional Sub-dir. de Dllo y Financiero': 'Subdirección Administrativa y Financiera',
    'Gestión Tec Oficina de': 'Oficina de Gestión TIC',
    'Universitario Bienestar': 'Bienestar Universitario',
    'Antes la 211': 'Salón 220-B',
    'Ceras Aula 112 Laboratorio de': 'Laboratorio de Ceras',
    'Laboratorio de Yesos Aula 113': 'Laboratorio de Yesos',
    'Odontología Sala Docente': 'Sala Docente de Odontología',
    'Sala Profesores Ingeniería': 'Sala de Profesores de Ingeniería',
    'Coord.practicas Medicina': 'Coordinación de Prácticas de Medicina',
    'De Posgrado Área de Estudios': 'Área de Estudio de Posgrado',
    'Foreing Language Center': 'Foreign Language Center',
    'Auditorio Urcunina Aula 608B': 'Auditorio Urcunina',
    'Sala Juntas': 'Sala de Juntas',
    'Dir. de Sede': 'Dirección de Sede',
    'Área de Lockers Circulacion': 'Área de Lockers',
    'Sala de Profesores Enfermeria': 'Sala de Profesores de Enfermería',
    'Sala de Docentes Derecho': 'Sala de Docentes de Derecho',
    'Coordinación Preclínica Almacen': 'Coordinación Preclínica',
    'Área de Procedimientos Laboratorio Morfología': 'Laboratorio de Morfología',
    'Coord. Clínica Área de Destrezas Morfología': 'Área de Destrezas Clínicas',
}


def fix_name(name):
    name = ' '.join(ACCENTS.get(w, w) for w in name.split(' '))
    name = re.sub(r'^Laboratorio Informática (\d)', r'Laboratorio de Informática \1', name)
    return NAME_FIX.get(name, name)


def slug(text):
    t = unicodedata.normalize('NFKD', text).encode('ascii', 'ignore').decode().upper()
    return re.sub(r'[^A-Z0-9]+', '-', t).strip('-')


def seats_for(kind, area):
    if kind not in SEATED:
        return None, None
    people = max(4, int(area / CAPACITY[kind]))
    rows = max(2, round(math.sqrt(people / 1.4)))
    cols = max(2, math.ceil(people / rows))
    return rows, cols


def floor_rooms(floor):
    box = BOXES[floor]
    x0, y0, x1, y1 = box
    mask = raster(box)
    regions = {}
    for t in TEXTS:
        s = re.sub(r'\s+', ' ', t['t']).strip()
        if not s or not in_box(t['x'], t['y'], box) or t['layer'] in ('01', 'Acotación') or NOT_A_LABEL.search(s):
            continue
        if not re.search(r'[A-Za-zÁÉÍÓÚÑáéíóúñ]{3}', s):
            continue
        sx, sy = int((t['x'] - x0) * PX), int((y1 - t['y']) * PX)
        reg = flood(mask, sx, sy, limit=420 * PX * PX)
        if reg is None:
            continue
        xs, ys = reg
        key = (int(xs.min()), int(ys.min()), len(xs))
        r = regions.setdefault(key, {'xs': xs, 'ys': ys, 'labels': []})
        r['labels'].append((t['y'], t['x'], s))
    rooms = []
    for key, r in regions.items():
        frags = [s for _, _, s in sorted(r['labels'], key=lambda v: (-round(v[0] * 2), v[1]))]
        frags = list(dict.fromkeys(frags))
        text = ' '.join(frags)
        if NOT_A_ROOM.search(frags[0]) and not CODE.search(text):
            continue
        area = len(r['xs']) / (PX * PX)
        pts = list(zip((r['xs'][::2] / PX).round(2).tolist(), (r['ys'][::2] / PX).round(2).tolist()))
        poly = hull(pts)
        if area < 7 or poly_area(poly) > 1.45 * area:
            continue
        code = CODE.search(text)
        kind = next(k for pattern, k in TYPE_RULES if re.search(pattern, text, re.I))
        if area > 160 and kind not in ('AUDITORIO', 'BIBLIOTECA', 'CAFETERIA', 'LABORATORIO'):
            continue
        # The name: the most descriptive fragments, without the room code when a nicer name exists.
        name_frags = [f for f in frags if not CODE.fullmatch(f.strip()) and not re.fullmatch(r'(AULA\s*(No\.?\s*)?\d{3}\s*[AB]?)', f.strip(), re.I)]
        name = nice(' '.join(name_frags[:3])) if name_frags else None
        rooms.append({'code': code, 'text': text, 'name': name, 'kind': kind, 'area': round(area, 1),
                      'polygon': [[round(x, 2), round(y, 2)] for x, y in poly], 'rect': min_rect(poly)})
    return rooms


# Rooms drawn by hand where the walls do not close (open halls): rectangle in floor metres, y down.
MANUAL = {
    0: [('AUDITORIO-0A', 'Auditorio Bloque A', 'AUDITORIO', 'A', (73.0, 7.8, 82.2, 27.0))],
    1: [('CAFETERIA-1B', 'Cafetería', 'CAFETERIA', 'B', (5.3, 23.0, 23.9, 37.5))],
    2: [('BIBLIOTECA-2B', 'Biblioteca · sala de lectura', 'BIBLIOTECA', 'B', (5.8, 11.5, 24.1, 33.1))],
    3: [('HEMEROTECA-3B', 'Hemeroteca', 'BIBLIOTECA', 'B', (4.1, 11.5, 22.8, 25.8))],
}
# Rooms with a code on the plan keep its block letter. The rest follow the campus description: block A holds
# the classrooms, study rooms, offices and logistics; block B the library, cafeteria, labs and computer rooms.
TYPE_BLOCK = {'BIBLIOTECA': 'B', 'CAFETERIA': 'B', 'LABORATORIO': 'B', 'SALA_COMPUTO': 'B'}


def assign_blocks(rooms):
    coded = [r for r in rooms if r['code'] and r['code'].group(2)]
    for r in rooms:
        if r['code'] and r['code'].group(2):
            r['block'] = r['code'].group(2).upper()
        elif r['kind'] in TYPE_BLOCK:
            r['block'] = TYPE_BLOCK[r['kind']]
        elif r['code'] and coded:
            near = min(coded, key=lambda c: (c['rect']['cx'] - r['rect']['cx']) ** 2 + 2.5 * (c['rect']['cy'] - r['rect']['cy']) ** 2)
            r['block'] = near['code'].group(2).upper()
        else:
            r['block'] = 'A'


def walls_path(box):
    x0, y0, x1, y1 = box
    seen, parts = set(), []
    for a, b, c, d, layer in SEGS:
        if layer not in WALLS or not (in_box(a, b, box) and in_box(c, d, box)):
            continue
        if math.hypot(c - a, d - b) < 0.25:
            continue
        p = (round(a - x0, 1), round(y1 - b, 1), round(c - x0, 1), round(y1 - d, 1))
        if p in seen or (p[2], p[3], p[0], p[1]) in seen:
            continue
        seen.add(p)
        parts.append(f'M{p[0]:g} {p[1]:g}L{p[2]:g} {p[3]:g}')
    return ''.join(parts)


def main():
    floors, ids = [], set()
    # Floor 0 has no coded rooms: its block comes from the "BLOQUE A" label of the clinic.
    for floor in sorted(BOXES):
        box = BOXES[floor]
        rooms = floor_rooms(floor)
        assign_blocks(rooms)
        for code, name, kind, block, (ax, ay, bx, by) in MANUAL.get(floor, []):
            poly = [[ax, ay], [bx, ay], [bx, by], [ax, by]]
            rooms.append({'code': None, 'fixed': code, 'text': name, 'name': name, 'kind': kind, 'block': block,
                          'area': round((bx - ax) * (by - ay), 1), 'polygon': poly, 'rect': min_rect(poly)})
        out = []
        for r in rooms:
            block = r['block'] or 'A'
            if r.get('fixed'):
                code = r['fixed']
            elif r['code']:
                number = r['code'].group(1)
                code = f"{number}-{block}"
            else:
                code = f"{floor}{block}-{slug(r['name'] or r['text'])}"[:30].rstrip('-')
            base, n = code, 2
            while code in ids:
                code = f"{base[:27]}-{n}"
                n += 1
            ids.add(code)
            kind = r['kind']
            capacity = max(2, int(r['area'] / CAPACITY[kind]))
            rows, cols = seats_for(kind, r['area'])
            if kind == 'AULA':
                rows, cols = 6, 6  # campus fact (2026-10-21): every classroom has 36 desks
            if code == 'BIBLIOTECA-2B':
                capacity, rows, cols = 120, None, None  # eight tables drawn over its camera
            elif rows:
                capacity = rows * cols if kind != 'AUDITORIO' else capacity
            computers = {'SALA_COMPUTO': capacity, 'ADMINISTRATIVA': max(1, capacity), 'LABORATORIO': max(1, capacity // 4),
                         'BIBLIOTECA': max(1, capacity // 8), 'LOGISTICA': 1}.get(kind, 1)
            if r['name']:
                name = fix_name(r['name'])
            elif kind == 'AULA':
                name = f'Salón {code}'
            else:
                name = fix_name(nice(r['text']))
            out.append({'spaceId': code, 'name': name, 'type': kind, 'block': block, 'floor': floor,
                        'areaSqm': r['area'], 'capacity': capacity, 'seatRows': rows, 'seatColumns': cols,
                        'computers': computers, 'polygon': r['polygon'], 'rect': r['rect']})
        floors.append({'floor': floor, 'name': FLOOR_NAME[floor], 'width': round(box[2] - box[0], 1),
                       'height': round(box[3] - box[1], 1), 'walls': walls_path(box), 'rooms': out})
        print(floor, len(out), sum(1 for r in out if r['block'] == 'A'), 'A', file=sys.stderr)
    json.dump({'source': 'PLANOS_UCC_2025_-_B.dwg (actualización planimetría 2025-B)', 'floors': floors},
              open('campus.json', 'w'), ensure_ascii=False)


if __name__ == '__main__':
    main()
