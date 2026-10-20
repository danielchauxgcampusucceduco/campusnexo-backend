#!/usr/bin/env python3
"""Edge publisher for CampusNexo.

Publishes one JSON frame per interval on
``ucc/<sede>/<bloque>/<piso>/<space_id>/camara``, the topic consumed by
``YoloMqttTelemetryInboundAdapter``. For room codes such as ``201-A`` the block and floor are taken
from the code (``bloque_a``, ``piso_2``). Two modes:

* ``--source <camera index | video path>``: runs YOLO with ByteTrack (``ultralytics``) and keeps the
  classes person, backpack, laptop and book. Only aggregated detections leave the device: no image is
  published, which keeps the camera within the data-minimisation principle (Habeas Data).
* ``--synthetic``: generates plausible detections, for demos without a camera.

Room sensors (temperature, light, computers) are published by the room's Arduino on its own
``ambiente`` channel (``tools/arduino``); the values sent here are only the camera-side fallback.

Example::

    python yolo_edge_publisher.py --space-id 421-A --synthetic --people 12
"""
from __future__ import annotations

import argparse
import json
import random
import re
import time
from datetime import datetime, timezone

import paho.mqtt.client as mqtt

# COCO class ids used by YOLO models.
COCO_CLASSES = {0: "person", 24: "backpack", 63: "laptop", 73: "book"}


ROOM_CODE = re.compile(r"^(\d)(\d{2})-([A-Za-z])$")


def topic_for(args: argparse.Namespace) -> str:
    bloque, piso = args.bloque, args.piso
    match = ROOM_CODE.match(args.space_id)
    if match:
        bloque, piso = f"bloque_{match.group(3).lower()}", f"piso_{match.group(1)}"
    return f"ucc/{args.sede}/{bloque}/{piso}/{args.space_id}/camara"


def sensors(args: argparse.Namespace, people: int) -> dict:
    jitter = random.uniform(-0.15, 0.15) if args.synthetic else 0.0
    return {
        "co2_ppm": round(args.co2 + (people * 12 if args.synthetic else 0) + jitter * 40, 1),
        "temperature_c": round(args.temperature + jitter, 2),
        "outdoor_temperature_c": args.outdoor_temperature,
        "humidity_pct": args.humidity,
        "illuminance_lux": args.lux,
        "wifi_devices": max(0, int(people * 1.1)) if args.synthetic else args.wifi,
        "active_power_kw": args.power,
    }


def synthetic_detections(people: int, belongings: int) -> list[dict]:
    detections = []
    for track_id in range(people + belongings):
        cls = "person" if track_id < people else random.choice(["laptop", "backpack", "book"])
        x, y = random.uniform(0.05, 0.9), random.uniform(0.05, 0.85)
        w, h = (0.05, 0.09) if cls == "person" else (0.04, 0.03)
        detections.append({
            "track_id": track_id + 1,
            "class": cls,
            "confidence": round(random.uniform(0.6, 0.97), 2),
            "bbox": [round(x, 4), round(y, 4), round(min(1.0, x + w), 4), round(min(1.0, y + h), 4)],
        })
    return detections


def yolo_frames(args: argparse.Namespace):
    from ultralytics import YOLO  # imported lazily: optional dependency

    model = YOLO(args.model)
    source = int(args.source) if args.source.isdigit() else args.source
    for result in model.track(source=source, stream=True, persist=True, tracker="bytetrack.yaml",
                              classes=list(COCO_CLASSES), verbose=False):
        detections = []
        boxes = result.boxes
        if boxes is not None and boxes.id is not None:
            for xyxyn, cls, conf, track in zip(boxes.xyxyn.tolist(), boxes.cls.tolist(), boxes.conf.tolist(),
                                               boxes.id.tolist()):
                detections.append({
                    "track_id": int(track),
                    "class": COCO_CLASSES[int(cls)],
                    "confidence": round(float(conf), 3),
                    "bbox": [round(float(v), 4) for v in xyxyn],
                })
        yield detections


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--broker", default="localhost")
    parser.add_argument("--port", type=int, default=1883)
    parser.add_argument("--sede", default="pasto")
    parser.add_argument("--bloque", default="bloque_a", help="solo si el space_id no es un código de salón")
    parser.add_argument("--piso", default="piso_1", help="solo si el space_id no es un código de salón")
    parser.add_argument("--space-id", required=True)
    parser.add_argument("--interval", type=float, default=5.0, help="segundos entre frames publicados")
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--synthetic", action="store_true")
    mode.add_argument("--source", help="índice de cámara o ruta de video para YOLO + ByteTrack")
    parser.add_argument("--model", default="yolo11n.pt")
    parser.add_argument("--people", type=int, default=10, help="personas en modo sintético")
    parser.add_argument("--belongings", type=int, default=3, help="objetos en modo sintético")
    parser.add_argument("--co2", type=float, default=600)
    parser.add_argument("--temperature", type=float, default=22.0)
    parser.add_argument("--outdoor-temperature", type=float, default=None)
    parser.add_argument("--humidity", type=float, default=55)
    parser.add_argument("--lux", type=float, default=350)
    parser.add_argument("--wifi", type=int, default=10)
    parser.add_argument("--power", type=float, default=1.5)
    args = parser.parse_args()

    client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2, client_id=f"edge-{args.space_id}")
    client.connect(args.broker, args.port, keepalive=30)
    client.loop_start()
    topic = topic_for(args)
    frames = yolo_frames(args) if args.source else None
    last_publish = 0.0
    try:
        while True:
            detections = next(frames) if frames else synthetic_detections(args.people, args.belongings)
            if time.monotonic() - last_publish < args.interval:
                if not frames:
                    time.sleep(args.interval - (time.monotonic() - last_publish))
                continue
            people = sum(1 for d in detections if d["class"] == "person")
            payload = {
                "space_id": args.space_id,
                "timestamp": datetime.now(timezone.utc).isoformat(timespec="seconds"),
                "camera_online": True,
                "detections": detections,
                "sensors": sensors(args, people),
            }
            client.publish(topic, json.dumps(payload), qos=1)
            last_publish = time.monotonic()
            print(f"{payload['timestamp']} {topic}: {people} personas, {len(detections)} detecciones")
    except (KeyboardInterrupt, StopIteration):
        pass
    finally:
        client.loop_stop()
        client.disconnect()


if __name__ == "__main__":
    main()
