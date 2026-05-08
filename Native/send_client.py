"""WebSocket relay client for streaming system metrics to the WASP backend.

This module is responsible for:
- Periodically reading the metrics JSON file produced by the Windows service.
- Normalizing its schema to match what the Java backend expects.
- Streaming snapshots to the `/ws/metrics` WebSocket endpoint on a fixed interval.
"""

import asyncio
from datetime import datetime
import json
import os
import sys
import time

import websockets

# Works for both local/installer runs and docker compose runs.
if os.path.exists("/.dockerenv"):
    default_uri = "ws://backend:8080/ws/metrics"
else:
    default_uri = "ws://localhost:8080/ws/metrics"

URI = os.environ.get("WASP_WS_URI", default_uri)

if getattr(sys, "frozen", False):
    BASE_DIR = os.path.dirname(os.path.abspath(sys.executable))
else:
    BASE_DIR = os.path.dirname(os.path.abspath(__file__))
JSON_PATH = os.environ.get(
    "WASP_METRICS_JSON",
    os.path.join(BASE_DIR, "system_metrics_output.json"),
)
# How often to push a fresh snapshot to the backend, in seconds.
SEND_INTERVAL_SECONDS = 2.0


def normalize_timestamp_to_epoch_ms(value):
    """Convert native local timestamp strings to epoch milliseconds."""
    if isinstance(value, (int, float)):
        return int(value)
    if not isinstance(value, str):
        return value

    raw = value.strip()
    if not raw:
        return value

    # Already numeric text: keep as integer milliseconds.
    if raw.isdigit():
        n = int(raw)
        return n * 1000 if len(raw) <= 10 else n

    # Native collector format: "YYYY-MM-DD HH:MM:SS.mmm" in local machine time.
    try:
        dt = datetime.strptime(raw, "%Y-%m-%d %H:%M:%S.%f")
        return int(dt.timestamp() * 1000)
    except ValueError:
        return value


def normalize_payload_timestamps(raw):
    """Normalize timestamp fields across top-level metric sections."""
    if "cpu" in raw and isinstance(raw["cpu"], dict) and "timestamp" in raw["cpu"]:
        raw["cpu"]["timestamp"] = normalize_timestamp_to_epoch_ms(raw["cpu"]["timestamp"])

    for key in ("cpu_cores", "disk", "processes"):
        items = raw.get(key)
        if isinstance(items, list):
            for item in items:
                if isinstance(item, dict) and "timestamp" in item:
                    item["timestamp"] = normalize_timestamp_to_epoch_ms(item["timestamp"])

    if "memory" in raw and isinstance(raw["memory"], dict) and "timestamp" in raw["memory"]:
        raw["memory"]["timestamp"] = normalize_timestamp_to_epoch_ms(raw["memory"]["timestamp"])


def build_payload_from_file():
    """Return a JSON string payload built from the latest metrics file.

    This function:
    - Reads ``JSON_PATH`` from disk.
    - Copies ``core_usage_percent`` into ``cpu_usage_percent`` for each entry
      in ``cpu_cores`` so the payload matches the backend's required schema.
    - Serializes the adapted Python object back into a JSON string that can
      be sent over the WebSocket.
    """
    with open(JSON_PATH, "r") as f:
        raw = json.load(f)

    cpu_cores = raw.get("cpu_cores", [])
    for core in cpu_cores:
        if "core_usage_percent" in core and "cpu_usage_percent" not in core:
            core["cpu_usage_percent"] = core["core_usage_percent"]

    normalize_payload_timestamps(raw)

    return json.dumps(raw)


async def relay_metrics():
    """Continuously send metrics snapshots on a fixed interval.

    The loop:
    - Maintains a WebSocket connection to ``URI`` with relaxed ping timeouts.
    - Every ``SEND_INTERVAL_SECONDS``, rebuilds the payload with
      :func:`build_payload_from_file` and sends it to the backend.
    - Logs basic status and errors for observability.
    - Automatically reconnects after connection or send failures, so it can
      be run as a long‑lived background process.
    """

    while True:
        try:
            # Tune ping settings so short stalls don't immediately kill the connection.
            async with websockets.connect(
                URI,
                ping_interval=20,
                ping_timeout=20,
            ) as websocket:
                print("Connected to metrics WebSocket")

                while True:
                    try:
                        payload_str = build_payload_from_file()
                        print(
                            f"Sending metrics from '{JSON_PATH}' "
                            f"(bytes={len(payload_str)}) at {time.strftime('%H:%M:%S')}"
                        )
                        await websocket.send(payload_str)
                    except websockets.ConnectionClosed as e:
                        # Connection died mid‑send; break to outer loop to reconnect.
                        print("Connection closed while sending metrics:", e)
                        break
                    except FileNotFoundError:
                        print(f"Metrics file '{JSON_PATH}' not found; retrying...")
                    except Exception as e:
                        print("Error reading/sending metrics:", e)

                    await asyncio.sleep(SEND_INTERVAL_SECONDS)

        except Exception as e:
            print("WebSocket connection error, retrying in 5s:", e)
            await asyncio.sleep(5)


if __name__ == "__main__":
    asyncio.run(relay_metrics())
