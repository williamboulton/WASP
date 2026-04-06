import enum
import subprocess
import asyncio
import time
from typing import final
import requests
import websockets
import json
import random
from datetime import datetime, timedelta
from pathlib import Path
from typing import List

"""
This python script is designed to generate dummy payloads
for the aggregator service inside of the wasp-backend java
application, send it through via websockets and see if the values
are aggregated correctly.
"""

# --- Configuration ---
JAR_PATH = "../../wasp-backend/target/wasp-backend-0.0.1-SNAPSHOT.jar"
INPUT_PATH = "input/input.json"
API_URL = "http://localhost:8080/api"  # Update to your endpoint
WS_URL = "ws://localhost:8080/ws/metrics"  # Update to your endpoint
PAYLOAD_COUNT = 60


def generate_payload(index, start_time):
    """Generates a single JSON payload with predictable variance."""
    timestamp = (start_time + timedelta(seconds=index)).strftime("%Y-%m-%d %H:%M:%S.%f")[:-3]
    cpu_val = 20.0 + (index % 10)  # Cycles between 20.0 and 29.0
    random_core_usage = [random.uniform(0.0, 100.0) for _ in range(16)]

    return {
        "cpu": {
            "cpu_mhz": 4800,
            "cpu_usage_percent": cpu_val,
            "system_responsiveness_percent": 99.0,
            "timestamp": timestamp
        },
        "cpu_cores": [
            {
            "core_index": 0,
            "core_mhz": 4700,
            "core_usage_percent": random_core_usage[0],
            "timestamp": "2026-02-26 02:39:30.960"
            },
            {
            "core_index": 1,
            "core_mhz": 4700,
            "core_usage_percent": random_core_usage[1],
            "timestamp": "2026-02-26 02:39:30.960"
            },
            {
            "core_index": 2,
            "core_mhz": 4700,
            "core_usage_percent": random_core_usage[2],
            "timestamp": "2026-02-26 02:39:30.960"
            },
            {
            "core_index": 3,
            "core_mhz": 4700,
            "core_usage_percent": random_core_usage[3],
            "timestamp": "2026-02-26 02:39:30.960"
            },
            {
            "core_index": 4,
            "core_mhz": 4700,
            "core_usage_percent": random_core_usage[4],
            "timestamp": "2026-02-26 02:39:30.960"
            },
            {
            "core_index": 5,
            "core_mhz": 4700,
            "core_usage_percent": random_core_usage[5],
            "timestamp": "2026-02-26 02:39:30.960"
            },
            {
            "core_index": 6,
            "core_mhz": 4700,
            "core_usage_percent": random_core_usage[6],
            "timestamp": "2026-02-26 02:39:30.960"
            },
            {
            "core_index": 7,
            "core_mhz": 4700,
            "core_usage_percent": random_core_usage[7],
            "timestamp": "2026-02-26 02:39:30.960"
            },
            {
            "core_index": 8,
            "core_mhz": 4700,
            "core_usage_percent": random_core_usage[8],
            "timestamp": "2026-02-26 02:39:30.960"
            },
            {
            "core_index": 9,
            "core_mhz": 4700,
            "core_usage_percent": random_core_usage[9],
            "timestamp": "2026-02-26 02:39:30.960"
            },
            {
            "core_index": 10,
            "core_mhz": 4700,
            "core_usage_percent": random_core_usage[10],
            "timestamp": "2026-02-26 02:39:30.960"
            },
            {
            "core_index": 11,
            "core_mhz": 4700,
            "core_usage_percent": random_core_usage[11],
            "timestamp": "2026-02-26 02:39:30.960"
            },
            {
            "core_index": 12,
            "core_mhz": 4700,
            "core_usage_percent": random_core_usage[12],
            "timestamp": "2026-02-26 02:39:30.960"
            },
            {
            "core_index": 13,
            "core_mhz": 4700,
            "core_usage_percent": random_core_usage[13],
            "timestamp": "2026-02-26 02:39:30.960"
            },
            {
            "core_index": 14,
            "core_mhz": 4700,
            "core_usage_percent": random_core_usage[14],
            "timestamp": "2026-02-26 02:39:30.960"
            },
            {
            "core_index": 15,
            "core_mhz": 4700,
            "core_usage_percent": random_core_usage[15],
            "timestamp": "2026-02-26 02:39:30.960"
            }
        ],
    "memory": {
        "total_bytes": 33346146304,
        "free_bytes": 18831712256,
        "used_bytes": 14514434048,
        "memory_usage_percent": 43,
        "page_fault_count": 7562,
        "timestamp": "2026-02-26 02:39:30.960"
    },

    "disk": [
        {
        "drive_letter": "C:",
        "total_bytes": 1999392206848,
        "free_bytes": 1012803956736,
        "read_speed_bytes_per_sec": 5331,
        "write_speed_bytes_per_sec": 45314,
        "timestamp": "2026-02-26 02:39:30.960"
        },
        {
        "drive_letter": "D:",
        "total_bytes": 1000196800512,
        "free_bytes": 452141776896,
        "read_speed_bytes_per_sec": 5331,
        "write_speed_bytes_per_sec": 45314,
        "timestamp": "2026-02-26 02:39:30.960"
        },
        {
        "drive_letter": "E:",
        "total_bytes": 5000924233728,
        "free_bytes": 228360768716,
        "read_speed_bytes_per_sec": 5331,
        "write_speed_bytes_per_sec": 45314,
        "timestamp": "2026-02-26 02:39:30.960"
        }
    ]}, cpu_val, random_core_usage

"""
This function generates the expected output we want our backend to produce
in the input/input.json file
"""
def generate_output(cpu_total, core_usage_total: List[float]):
    # Create all missing parent directories
    file_path = Path(INPUT_PATH)
    file_path.parent.mkdir(parents=True, exist_ok=True)
    print(f"file path {file_path}")

    final_json = {}

    # open file to write our black box inputs
    with open(INPUT_PATH, 'w') as f:
        cpu = {
            "cpu_mhz" : 4800,
            "cpu_usage_percent": cpu_total / PAYLOAD_COUNT,
            "system_responsiveness_percent": 99.9,
            "timestamp": ""
        }

        cpu_cores = [ {
                "core_index" : i,
                "core_mhz" : 4700,
                "core_usage_percent": round(core_usage_total[i] / PAYLOAD_COUNT, 2),
                "timestamp" : ""
            }
                for i in range(16)
            ]

        final_json["cpu"] = cpu
        final_json["cpu_cores"] = cpu_cores
        json.dump(final_json, f, indent=4)



async def run_test():
    # 1. Start the Java Application
    print(f"--- Starting Java Aggregator: {JAR_PATH} ---")
    java_process = subprocess.Popen(
        ["java", "-jar", JAR_PATH],
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True
    )

    # 2. Wait for Spring Boot to be healthy
    print("Waiting for server to start...")
    timeout = 30
    start = time.time()
    while time.time() - start < timeout:
        try:
            if requests.get(API_URL + "/health").status_code == 200:
                print("Backend detected and healthy")
                break
        except:
            time.sleep(1)


    async with websockets.connect(WS_URL) as ws:
        print("connected to web socket")

        try:
            # 3. Generate and Send 60 Payloads
            print(f"Sending {PAYLOAD_COUNT} payloads...")
            total_cpu = 0
            cpu_core_usage = [0.0 for _ in range(16)]
            start_time = datetime.now()

            for i in range(PAYLOAD_COUNT):
                payload, val, cores = generate_payload(i, start_time)
                total_cpu += val
                for j, core_pct in enumerate(cores):
                    cpu_core_usage[j] += core_pct

                await ws.send(json.dumps(payload))


            generate_output(total_cpu, cpu_core_usage)


        finally:
            # 5. Cleanup: Kill the Java process
            print("Shutting down Java application...")
            print("Printing java app stdout before shutdown: \n")
            print("***" * 10)
            print("\n" * 5)
            for line in java_process.stdout:
                print(line, end="")
            java_process.terminate()
            java_process.wait()

if __name__ == "__main__":
    asyncio.run(run_test())
