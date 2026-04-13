import subprocess
import asyncio
import time
import requests
import websockets
import json
import random
from datetime import datetime, timedelta
from pathlib import Path
from typing import List

"""
This script was generated with ChatGPT, using the runner.py file that I wrote myself.
I prompted it to refactor that program using a test data construction design
pattern to easily change generated json payloads, that are put in to our java program.
It needs to be tweaked a bit.

Patrick Muller
"""

# =========================
# Configuration Loader
# =========================

def load_config(path="config.json"):
    with open(path) as f:
        return json.load(f)

# =========================
# Strategy Pattern
# =========================

class CpuStrategy:
    def compute(self, index: int) -> float:
        return random.uniform(0.0, 100.0)

class LinearCpuStrategy(CpuStrategy):
    def __init__(self, base: float, variance: int):
        self.base = base
        self.variance = variance

    def compute(self, index: int) -> float:
        return self.base + (index % self.variance)

class SpikeCpuStrategy(CpuStrategy):
    def compute(self, index: int) -> float:
        return 90.0 if index % 15 == 0 else 20.0

# =========================
# Payload Builder
# =========================

class PayloadBuilder:
    def __init__(self, config: dict, cpu_strategy: CpuStrategy):
        self.config = config
        self.cpu_strategy = cpu_strategy

    def build(self, index: int, start_time: datetime):
        timestamp = (start_time + timedelta(seconds=index)).strftime("%Y-%m-%d %H:%M:%S.%f")[:-3]

        cpu_val = self.cpu_strategy.compute(index)
        core_usages = [random.uniform(0, 100) for _ in range(self.config["cpu_cores"]["count"])]

        payload = {
            "cpu": {
                "cpu_mhz": self.config["cpu"]["cpu_mhz"],
                "cpu_usage_percent": cpu_val,
                "system_responsiveness_percent": self.config["cpu"]["system_responsiveness_percent"],
                "timestamp": timestamp
            },
            "cpu_cores": [
                {
                    "core_index": i,
                    "core_mhz": self.config["cpu_cores"]["core_mhz"],
                    "core_usage_percent": core_usages[i],
                    "timestamp": timestamp
                }
                for i in range(self.config["cpu_cores"]["count"])
            ],
            "memory": {**self.config["memory"], "timestamp": timestamp},
            "disk": [
                {**d, "timestamp": timestamp}
                for d in self.config["disk"]
            ]
        }

        return payload, cpu_val, core_usages

# =========================
# Expected Output Generator
# =========================

def generate_expected_output(path: str, cpu_total: float, core_totals: List[float], count: int, config: dict):
    Path(path).parent.mkdir(parents=True, exist_ok=True)

    result = {
        "cpu": {
            "cpu_mhz": config["cpu"]["cpu_mhz"],
            "cpu_usage_percent": cpu_total / count,
            "system_responsiveness_percent": config["cpu"]["system_responsiveness_percent"],
            "timestamp": ""
        },
        "cpu_cores": [
            {
                "core_index": i,
                "core_mhz": config["cpu_cores"]["core_mhz"],
                "core_usage_percent": round(core_totals[i] / count, 2),
                "timestamp": ""
            }
            for i in range(len(core_totals))
        ]
    }

    with open(path, "w") as f:
        json.dump(result, f, indent=4)

# =========================
# Assertion Engine
# =========================

def assert_results(expected_path: str, actual: dict, tolerance=0.01):
    with open(expected_path) as f:
        expected = json.load(f)

    def approx_equal(a, b):
        return abs(a - b) <= tolerance

    assert approx_equal(expected["cpu"]["cpu_usage_percent"], actual["cpu"]["cpu_usage_percent"]), "CPU average mismatch"

    for i, core in enumerate(expected["cpu_cores"]):
        print(f"expected core usage: {core['core_usage_percent']}")
        print(f"actual core usage: {actual['cpu_cores'][i]['core_usage_percent']}")
        assert approx_equal(core["core_usage_percent"], actual["cpu_cores"][i]["core_usage_percent"]), f"Core {i} mismatch"

    print("\n✅ Aggregation test passed")

# =========================
# Test Runner
# =========================

class AggregationTestHarness:
    def __init__(self, config_path="config.json"):
        self.config = load_config(config_path)
        self.payload_count = self.config.get("payload_count", 60)
        self.api_url = self.config.get("api_url", "http://localhost:8080/api")
        self.ws_url = self.config.get("ws_url", "ws://localhost:8080/ws/metrics")
        self.jar_path = self.config.get("jar_path")

        self.builder = PayloadBuilder(
            self.config,
            LinearCpuStrategy(
                self.config["cpu"]["base_usage"],
                self.config["cpu"]["variance"]
            )
        )

    def start_backend(self):
        return subprocess.Popen(
            ["java", "-jar", self.jar_path],
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True
        )

    def wait_for_health(self, timeout=30):
        start = time.time()
        while time.time() - start < timeout:
            try:
                if requests.get(self.api_url + "/health").status_code == 200:
                    return True
            except:
                time.sleep(1)
        raise RuntimeError("Backend failed to start")

    async def run(self):
        java_process = self.start_backend()
        print("Starting backend...")

        try:
            self.wait_for_health()
            print("Backend healthy")

            async with websockets.connect(self.ws_url) as ws:
                print("WebSocket connected")

                total_cpu = 0
                core_totals = [0.0 for _ in range(self.config["cpu_cores"]["count"])]
                start_time = datetime.now()

                for i in range(self.payload_count):
                    payload, cpu_val, cores = self.builder.build(i, start_time)
                    total_cpu += cpu_val

                    for j, val in enumerate(cores):
                        core_totals[j] += val

                    await ws.send(json.dumps(payload))

                expected_path = "input/input.json"
                generate_expected_output(expected_path, total_cpu, core_totals, self.payload_count, self.config)

                # wait 5 seconds for JAR to write to output.json
                time.sleep(5)

                with open("output/output.json", 'r') as f:
                    actual = json.load(f)

                assert_results(expected_path, actual)

        finally:
            print("Shutting down backend...")
            java_process.terminate()
            java_process.wait()

# =========================
# Entry Point
# =========================

if __name__ == "__main__":
    harness = AggregationTestHarness()
    asyncio.run(harness.run())
