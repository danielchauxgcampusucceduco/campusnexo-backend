#!/usr/bin/env python3
"""Pruebas de sistema con una cuenta ADMIN, contra el sistema desplegado (compose local o Render).

Recorre lo que la administración hace en la piloto y dice PASA o FALLA en cada paso:

 1. inicia sesión como ADMIN y el backend lo reconoce;
 2. el catálogo tiene los 107 salones del plano más los 2 pilotos;
 3. registra un docente y un estudiante;
 4. registra los puestos de 301-A con la cámara: descubrimiento, emparejamiento por patrón y confirmación;
 5. un frame con el profesor adelante y estudiantes al fondo de 303-A se reconoce como CLASE_EN_CURSO;
 6. un computador encendido con 301-A vacío levanta CPU_ENCENDIDA_SIN_USUARIO;
 7. una petición de administración con un X-Forwarded-For falso no pasa (obligatorio con --exige-red-admin,
    que es como se corre contra Render; sin la opción, en local sin red configurada el paso no aplica);
 8. el historial del gemelo (MongoDB) devuelve filas.

Solo biblioteca estándar. Credenciales por variables de entorno, nunca en la línea de comandos:
CAMPUSNEXO_ADMIN_USER, CAMPUSNEXO_ADMIN_PASSWORD y CAMPUSNEXO_SENSOR_KEY.

    python tools/e2e/e2e_admin.py --backend http://localhost:8080
    python tools/e2e/e2e_admin.py --backend https://<backend>.onrender.com --exige-red-admin
"""
from __future__ import annotations

import argparse
from datetime import datetime, timedelta, timezone
import json
import os
import sys
import urllib.error
import urllib.request


class Api:
    def __init__(self, base: str):
        self.base = base.rstrip("/")
        self.token = None

    def call(self, method: str, path: str, body=None, sensor_key: str | None = None, headers: dict | None = None):
        cabeceras = {"Content-Type": "application/json", **(headers or {})}
        if sensor_key:
            cabeceras["X-API-Key"] = sensor_key
        elif self.token:
            cabeceras["Authorization"] = f"Bearer {self.token}"
        datos = None if body is None else json.dumps(body).encode("utf-8")
        solicitud = urllib.request.Request(self.base + path, data=datos, method=method, headers=cabeceras)
        try:
            with urllib.request.urlopen(solicitud, timeout=20) as respuesta:
                texto = respuesta.read().decode("utf-8")
                return respuesta.status, _json(texto)
        except urllib.error.HTTPError as error:
            return error.code, _json(error.read().decode("utf-8", errors="replace"))


def _json(texto: str):
    try:
        return json.loads(texto) if texto else None
    except json.JSONDecodeError:
        return texto


def persona(x: float, y: float, track: int) -> dict:
    return {"track_id": track, "class": "person", "confidence": 0.9, "bbox": [x - 0.02, y - 0.04, x + 0.02, y + 0.04]}


def frame(space_id: str, personas: list[dict]) -> dict:
    return {"space_id": space_id, "timestamp": datetime.now(timezone.utc).isoformat(timespec="seconds"),
            "camera_online": True, "emulated": True, "detections": personas,
            "sensors": {"co2_ppm": 650, "temperature_c": 18.0, "humidity_pct": 55, "illuminance_lux": 400}}


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--backend", default="http://localhost:8080")
    parser.add_argument("--exige-red-admin", action="store_true",
                        help="el paso 7 falla si la administración acepta un X-Forwarded-For falso")
    args = parser.parse_args(argv)
    sys.stdout.reconfigure(encoding="utf-8")
    usuario, clave = os.environ.get("CAMPUSNEXO_ADMIN_USER"), os.environ.get("CAMPUSNEXO_ADMIN_PASSWORD")
    sensor = os.environ.get("CAMPUSNEXO_SENSOR_KEY")
    if not (usuario and clave and sensor):
        raise SystemExit("Define CAMPUSNEXO_ADMIN_USER, CAMPUSNEXO_ADMIN_PASSWORD y CAMPUSNEXO_SENSOR_KEY")
    api = Api(args.backend)
    resultados: list[tuple[str, bool, str]] = []

    def paso(nombre: str, ok: bool, detalle: str = "") -> bool:
        resultados.append((nombre, ok, detalle))
        print(f"{'PASA ' if ok else 'FALLA'} {nombre}{' · ' + detalle if detalle else ''}")
        return ok

    estado, cuerpo = api.call("POST", "/api/v1/auth/login", {"username": usuario, "password": clave})
    api.token = cuerpo.get("token") if isinstance(cuerpo, dict) else None
    _, sesion = api.call("GET", "/api/v1/auth/session")
    if not paso("1. sesión ADMIN", estado == 200 and isinstance(sesion, dict) and sesion.get("role") == "ADMIN",
                f"HTTP {estado}"):
        return 1

    _, espacios = api.call("GET", "/api/v1/spaces")
    paso("2. catálogo 107 + 2 pilotos", isinstance(espacios, list) and len(espacios) == 109,
         f"{len(espacios) if isinstance(espacios, list) else espacios} espacios")

    d, _ = api.call("POST", "/api/v1/admin/personas", {"tipo": "DOCENTE", "codigo": "E2E-D-1", "nombre": "Docente E2E",
                                                        "adscripcion": "Pruebas"})
    e, _ = api.call("POST", "/api/v1/admin/personas", {"tipo": "ESTUDIANTE", "codigo": "E2E-E-1",
                                                        "nombre": "Estudiante E2E", "adscripcion": "Pruebas"})
    paso("3. registrar docente y estudiante", d == 200 and e == 200, f"HTTP {d} / {e}")

    _, puestos = api.call("GET", "/api/v1/admin/spaces/301-A/puestos")
    if isinstance(puestos, list) and puestos:
        paso("4. puestos de 301-A", True, f"ya registrados: {len(puestos)}")
    else:
        for n in range(1, 31):
            api.call("POST", "/api/v1/workstations/heartbeats", {"cpu": f"PC-301A-{n:02d}", "spaceId": "301-A"},
                     sensor_key=sensor)
        vistos = [{"fila": f, "columna": c, "monitor": True, "silla": True, "mesa": True}
                  for f in range(1, 6) for c in range(1, 7)]
        api.call("POST", "/api/v1/workstations/discovery", {"spaceId": "301-A", "emulado": True, "puestos": vistos},
                 sensor_key=sensor)
        _, plan = api.call("POST", "/api/v1/admin/spaces/301-A/emparejamiento")
        inicio = datetime.fromisoformat(plan["inicio"].replace("Z", "+00:00"))
        ranura_s = _segundos(plan["ranura"])
        for i, cpu in enumerate(plan["cpus"]):
            numero = int(cpu.rsplit("-", 1)[1])
            api.call("POST", "/api/v1/workstations/pairing", {
                "spaceId": "301-A", "fila": (numero - 1) // 6 + 1, "columna": (numero - 1) % 6 + 1,
                "at": (inicio + timedelta(seconds=(i + 0.5) * ranura_s)).isoformat()},
                sensor_key=sensor)
        estado, altas = api.call("POST", "/api/v1/admin/spaces/301-A/propuesta/confirmar", {})
        paso("4. registro automático de 301-A", estado == 200 and isinstance(altas, list) and len(altas) == 30,
             f"HTTP {estado}, {len(altas) if isinstance(altas, list) else altas} puestos")

    clase = [persona(0.5, 0.15, 1)] + [persona(0.05 + 0.15 * (c + 0.5), 0.30 + 0.65 / 6 * 5.5, 10 + c) for c in range(6)]
    estado, gemelo = api.call("POST", "/api/v1/twin/telemetry", frame("303-A", clase), sensor_key=sensor)
    escenarios = [e["escenario"] for e in gemelo.get("escenarios", [])] if isinstance(gemelo, dict) else []
    paso("5. profesor adelante y estudiantes al fondo", "CLASE_EN_CURSO" in escenarios
         and gemelo.get("teachersPresent") == 1, f"{escenarios}")

    api.call("POST", "/api/v1/twin/telemetry", frame("301-A", []), sensor_key=sensor)
    estado, latido = api.call("POST", "/api/v1/workstations/heartbeats", {"cpu": "PC-301A-05"}, sensor_key=sensor)
    _, alertas = api.call("GET", "/api/v1/alerts?spaceId=301-A")
    tipos = {a.get("type") for a in alertas} if isinstance(alertas, list) else set()
    paso("6. CPU encendida con el salón vacío", "CPU_ENCENDIDA_SIN_USUARIO" in tipos, f"latido HTTP {estado}")

    estado, cuerpo = api.call("GET", "/api/v1/admin/personas", headers={"X-Forwarded-For": "198.51.100.9"})
    if estado == 403 and isinstance(cuerpo, dict) and cuerpo.get("code") == "admin_network_required":
        paso("7. X-Forwarded-For falso no abre la administración", True)
    else:
        no_aplica = estado == 200 and not args.exige_red_admin
        paso("7. X-Forwarded-For falso no abre la administración", no_aplica,
             "sin red de administración configurada: no aplica" if no_aplica else f"HTTP {estado}")

    estado, csv = api.call("GET", "/api/v1/admin/spaces/303-A/historial.csv")
    filas = csv.strip().splitlines() if isinstance(csv, str) else []
    paso("8. historial del gemelo en MongoDB", estado == 200 and len(filas) > 1, f"HTTP {estado}, {len(filas) - 1} filas")

    fallidos = [nombre for nombre, ok, _ in resultados if not ok]
    print(f"\n{len(resultados) - len(fallidos)}/{len(resultados)} pasos correctos")
    return 1 if fallidos else 0


def _segundos(duracion) -> float:
    """Segundos de la ranura tal como la serializa el backend: número de segundos o ISO-8601 (PT5S)."""
    if isinstance(duracion, (int, float)):
        return float(duracion)
    if isinstance(duracion, str) and duracion.startswith("PT") and duracion.endswith("S"):
        return float(duracion[2:-1])
    raise ValueError(f"Duración de ranura desconocida: {duracion!r}")


if __name__ == "__main__":
    sys.exit(main())
