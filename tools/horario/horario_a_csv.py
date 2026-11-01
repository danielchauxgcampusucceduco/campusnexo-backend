"""Convierte el horario de Ingeniería de Software (xlsx) en las sesiones que usa la demo del backend.

    python -I tools/horario/horario_a_csv.py <horario.xlsx> src/main/resources/demo/horario-isw-2620.csv

Solo biblioteca estándar. No copia los nombres de los docentes.

Cada hoja ISW es un grupo: filas de una hora y, por día, una columna AULA y otra con el curso; las celdas combinadas
dan la duración. Los nombres de curso se toman de la tabla de cursos de la hoja (con tildes). Las clases sin salón
reciben un salón del mismo horario que esté libre a esa hora; las que no ocurren en el campus (práctica en empresas)
y los cursos de la tabla sin franja en la grilla se informan y no se emulan.
"""
import html
import json
import re
import sys
import unicodedata
import zipfile
from difflib import SequenceMatcher

DIAS = ["LUNES", "MARTES", "MIERCOLES", "JUEVES", "VIERNES"]
# Columns of the grid: (AULA, CURSO) per day.
COLUMNAS = {"C": ("C", "D"), "E": ("E", "F"), "G": ("G", "H"), "I": ("I", "J"), "K": ("K", "L")}
SALAS = {"1": "308-A", "2": "309-A", "3": "310-A", "6": "301-A"}
# Where a session moves when the timetable puts two groups in the same room: the computer room of floor 5.
SALAS_DE_RESPALDO = ["5A-AULA-DE-INFORMATICA"]
# The grid writes some courses with another name than the course table.
SINONIMOS = {"ingles": "idioma extranjero"}


def sin_tildes(texto):
    return "".join(c for c in unicodedata.normalize("NFD", texto) if unicodedata.category(c) != "Mn").lower()


def salon(texto):
    t = sin_tildes(texto).strip()
    m = re.match(r"sala (?:de )?informatica (\d)", t)
    if m:
        return SALAS[m.group(1)]
    m = re.match(r"(\d{3}) ?([ab])$", t)
    if m:
        return f"{m.group(1)}-{m.group(2).upper()}"
    return None


def hora(etiqueta):
    """Start hour (24 h) and the hours the row stands for, from '7:00 am a 8:00 am' or '14:00 - 16:00'."""
    t = etiqueta.lower().replace(".", "")
    m = re.match(r"(\d{1,2}):00\s*-\s*(\d{1,2}):00", t)
    if m:
        return int(m.group(1)), int(m.group(2)) - int(m.group(1))
    m = re.match(r"(\d{1,2}):00\s*(am|pm|m)", t)
    h = int(m.group(1))
    if m.group(2) == "pm" and h != 12:
        h += 12
    return h, 1


def leer(ruta):
    z = zipfile.ZipFile(ruta)
    compartidas = [html.unescape("".join(re.findall(r"<t[^>]*>(.*?)</t>", si, flags=re.S)))
                   for si in re.findall(r"<si>(.*?)</si>", z.read("xl/sharedStrings.xml").decode("utf-8"), flags=re.S)]
    libro = z.read("xl/workbook.xml").decode()
    relaciones = dict(re.findall(r'Id="([^"]+)"[^>]*Target="([^"]+)"', z.read("xl/_rels/workbook.xml.rels").decode()))
    for nombre, rid in re.findall(r'<sheet [^>]*name="([^"]+)"[^>]*r:id="([^"]+)"', libro):
        x = z.read("xl/" + relaciones[rid].replace("xl/", "").lstrip("/")).decode("utf-8")
        celdas = {}
        for ref, attrs, cuerpo in re.findall(r'<c r="([A-Z]+\d+)"([^>]*?)(?:/>|>(.*?)</c>)', x, flags=re.S):
            v = re.search(r"<v>(.*?)</v>", cuerpo or "", flags=re.S)
            if v is None:
                continue
            valor = compartidas[int(v.group(1))] if 't="s"' in attrs else v.group(1)
            valor = html.unescape(valor).replace("\n", " ").strip()
            if valor:
                celdas[ref] = valor
        combinadas = {}
        for rango in re.findall(r'<mergeCell ref="([A-Z]+)(\d+):([A-Z]+)(\d+)"', x):
            combinadas[f"{rango[0]}{rango[1]}"] = int(rango[3]) - int(rango[1]) + 1
        yield html.unescape(nombre).strip(), celdas, combinadas


TILDES = {"Programacion": "Programación", "programacion": "programación", "Gestion": "Gestión",
          "Ingenieria": "Ingeniería", "Informatica": "Informática", "interpretacion": "interpretación",
          "Movil": "Móvil"}


def limpio(curso):
    """Display name: the accents the sheet omits, a space before a parenthesis and none inside it."""
    for sin, con in TILDES.items():
        curso = re.sub(rf"\b{sin}\b", con, curso)
        curso = re.sub(r"(\S)\(", r"\1 (", curso)
    curso = re.sub(r"\(\s+", "(", re.sub(r"\s+\)", ")", curso))
    return re.sub(r"\s+", " ", curso).strip()


def base(curso):
    texto = sin_tildes(curso).replace("(", " (").split(" (")[0]
    for viejo, nuevo in SINONIMOS.items():
        texto = texto.replace(viejo, nuevo)
    texto = re.sub(r"\b1\b", "i", texto)
    return re.sub(r"\s+", " ", re.sub(r"\b(de|del|la|el|para|a|y)\b", " ", texto)).strip()


def curso_oficial(nombre, cursos):
    """The course of the table the grid cell names: the closest name, if it is close enough."""
    puntaje, mejor = max(((SequenceMatcher(None, base(nombre), base(c)).ratio(), c) for c in cursos), default=(0, None))
    return mejor if puntaje >= 0.72 else None


def grupo(nombre, celdas, combinadas):
    if not nombre.upper().startswith("ISW"):
        return None
    filas = {int(re.sub(r"\D", "", r)): v for r, v in celdas.items() if r.startswith("B") and re.search(r"\d+$", r)}
    encabezado = next(f for f, v in filas.items() if v.upper() == "HORAS")
    tabla = next(f for f, v in filas.items() if v.upper().startswith("NO DE CLASE"))
    # The group comes from the sheet name: some headers repeat another group (the 6B sheet says 6A).
    codigo = re.sub(r"[^0-9AB]", "", nombre.upper().replace("ISW", ""))
    if codigo.isdigit():
        codigo = re.sub(r"\s+", "", next(v for f, v in filas.items() if f < encabezado and re.match(r"^\d\s?[AB]$", v)))
    estudiantes = int(next(v for r, v in celdas.items() if r.startswith("H") and celdas.get("G" + r[1:]) == "Estudiantes"))
    cursos = [celdas[f"C{f}"] for f in range(tabla + 1, tabla + 15) if f"C{f}" in celdas]
    sesiones = []
    for f in range(encabezado + 1, tabla):
        if f"B{f}" not in celdas:
            continue
        inicio, horas_fila = hora(celdas[f"B{f}"])
        for d, (col_aula, col_curso) in enumerate(COLUMNAS.values()):
            texto = celdas.get(f"{col_curso}{f}", "").strip(" /")
            if not texto or texto == "/":
                continue
            filas_clase = max(combinadas.get(f"{col_curso}{f}", 1), combinadas.get(f"{col_aula}{f}", 1))
            fin = inicio + (filas_clase if horas_fila == 1 else horas_fila)
            # Notes written in the cell ("Ingles 1 / 2:00 a 5:00 pm", ".../ 8:00 - 11:00 AM") correct the time.
            nota = re.search(r"(\d{1,2}):00\s*(?:a|-)\s*(\d{1,2}):00\s*(am|pm)?", texto.lower())
            if nota:
                a, b = int(nota.group(1)), int(nota.group(2))
                if nota.group(3) == "pm" or (nota.group(3) is None and a < 7):
                    a, b = a + 12 if a < 12 else a, b + 12 if b < 12 else b
                inicio_nota, fin_nota = a, b
            else:
                inicio_nota, fin_nota = inicio, fin
            nombre_curso = re.split(r"\s+/|\s+\d{1,2}:00", texto)[0].strip()
            oficial = curso_oficial(nombre_curso, cursos)
            aula = celdas.get(f"{col_aula}{f}", "")
            sesiones.append({"grupo": codigo, "estudiantes": estudiantes,
                             "curso": oficial or nombre_curso,
                             "detalle": nombre_curso if oficial and "(" in nombre_curso else None,
                             "dia": DIAS[d], "inicio": inicio_nota, "fin": fin_nota,
                             "salon": salon(aula) if aula else None, "aula": aula})
    sin_franja = [c for c in cursos if not any(s["curso"] == c for s in sesiones)]
    return codigo, sesiones, sin_franja


def main():
    todas, informe = [], []
    for nombre, celdas, combinadas in leer(sys.argv[1]):
        resultado = grupo(nombre, celdas, combinadas)
        if resultado is None:
            continue
        codigo, sesiones, sin_franja = resultado
        todas += sesiones
        if sin_franja:
            informe.append(f"{codigo}: sin franja en la grilla, no se emulan: {', '.join(sin_franja)}")
    fuera = [s for s in todas if s["aula"] and s["salon"] is None]
    for s in fuera:
        informe.append(f"{s['grupo']} {s['dia']} {s['inicio']}-{s['fin']} {s['curso']}: «{s['aula']}» no es un salón del campus, no se emula")
    todas = [s for s in todas if not (s["aula"] and s["salon"] is None)]
    salones = sorted({s["salon"] for s in todas if s["salon"]})
    aulas = [r for r in salones if r not in SALAS.values()]

    def ocupado(r, dia, a, b, sesiones):
        return any(s["salon"] == r and s["dia"] == dia and s["inicio"] < b and a < s["fin"] for s in sesiones)

    for s in todas:
        if s["salon"] is None:
            libre = next(r for r in aulas if not ocupado(r, s["dia"], s["inicio"], s["fin"], todas))
            s["salon"], s["asignado"] = libre, True
            informe.append(f"{s['grupo']} {s['dia']} {s['inicio']}-{s['fin']} {s['curso']}: sin salón en el horario, asignado {libre}")
        else:
            s["asignado"] = False
        del s["aula"]
    # Two groups in the same room at the same time: the second one moves to a free room of the same kind.
    for i, t in enumerate(todas):
        otras = todas[:i] + todas[i + 1:]
        if not ocupado(t["salon"], t["dia"], t["inicio"], t["fin"], [o for o in todas[:i]]):
            continue
        computo = t["salon"] in SALAS.values()
        opciones = (list(SALAS.values()) + SALAS_DE_RESPALDO) if computo else aulas
        libre = next(r for r in opciones if not ocupado(r, t["dia"], t["inicio"], t["fin"], otras))
        informe.append(f"CRUCE en {t['salon']} {t['dia']} {t['inicio']}-{t['fin']}: {t['grupo']} {t['curso']} pasa a {libre}")
        t["salon"], t["asignado"] = libre, True
    # One spelling per course: the variants of the same name take the most frequent one.
    variantes = {}
    for s in todas:
        s["curso"] = limpio(s["curso"])
        variantes.setdefault(base(s["curso"]).removesuffix(" i").strip(), []).append(s["curso"])
    for s in todas:
        formas = variantes[base(s["curso"]).removesuffix(" i").strip()]
        s["curso"] = max(sorted(set(formas)), key=lambda f: (formas.count(f), f[:1].isupper(), len(f)))
        if s["detalle"]:
            s["detalle"] = limpio(s["detalle"])
            if base(s["detalle"]) == base(s["curso"]):
                s["detalle"] = None
    todas.sort(key=lambda s: (DIAS.index(s["dia"]), s["inicio"], s["salon"]))
    filas = ["# Ingeniería de Software 07ISW, periodo 2620 (HORARIO 2620 ING SOFTWARE 07ISW - Version Actualizada.xlsx).",
             "# Generado por tools/horario/horario_a_csv.py. Sin nombres de docentes. asignado=si: la clase no traía salón o",
             "# cruzaba con otra en el mismo salón, y la demo le asignó uno libre.",
             "grupo;estudiantes;curso;dia;inicio;fin;salon;asignado"]
    for s in todas:
        filas.append(";".join([s["grupo"], str(s["estudiantes"]), s["curso"], s["dia"], str(s["inicio"]), str(s["fin"]),
                               s["salon"], "si" if s["asignado"] else "no"]))
    with open(sys.argv[2], "w", encoding="utf-8", newline="\n") as salida:
        salida.write("\n".join(filas) + "\n")
    print(f"{len(todas)} sesiones en {len(salones)} salones: {', '.join(salones)}")
    print("\n".join(informe))


main()
