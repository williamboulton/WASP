import subprocess
import asyncio
import time
import requests
import websockets
import json
import random
from datetime import datetime, timedelta
from pathlib import Path
from typing import List, Dict

"""
This script was generated with ChatGPT, using the runner.py file that I wrote myself.
I prompted it to refactor that program using a test data construction design
pattern to easily change generated json payloads, that are put in to our java program.
I tweaked it a bit to send multiple payloads with realistic data, and compare the results
from the java program to the results python generated.

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
    def compute(self, index: int, timestamp: str) -> dict:
        raise NotImplementedError

class LinearCpuStrategy(CpuStrategy):
    def __init__(self, base: float, variance: int, cpu_mhz: float = 4800.0):
        self.base = base
        self.variance = variance
        self.cpu_mhz = cpu_mhz

    def compute(self, index: int, timestamp: str) -> dict:
        usage = self.base + (index % self.variance)
        usage = max(0.0, min(100.0, usage))

        return {
            "cpu_mhz": round(self.cpu_mhz + random.uniform(-100, 100), 2),
            "cpu_usage_percent": round(usage, 2),
            "system_responsiveness_percent": round(99.5 + random.uniform(-0.5, 0.4), 2),
            "timestamp": timestamp
        }

class SpikeCpuStrategy(CpuStrategy):
    def __init__(self, base_usage=20.0, spike_usage=90.0, spike_every=15, cpu_mhz=4800.0):
        self.base_usage = base_usage
        self.spike_usage = spike_usage
        self.spike_every = spike_every
        self.cpu_mhz = cpu_mhz

    def compute(self, index: int, timestamp: str) -> dict:
        is_spike = index % self.spike_every == 0

        if is_spike:
            usage = self.spike_usage + random.uniform(-3, 3)
            responsiveness = random.uniform(70.0, 90.0)  # degraded under load
        else:
            usage = self.base_usage + random.uniform(-5, 5)
            responsiveness = random.uniform(95.0, 100.0)

        usage = max(0.0, min(100.0, usage))

        return {
            "cpu_mhz": round(self.cpu_mhz + random.uniform(-150, 150), 2),
            "cpu_usage_percent": round(usage, 2),
            "system_responsiveness_percent": round(responsiveness, 2),
            "timestamp": timestamp
        }

# =========================
# Memory Strategy
# =========================

class MemoryStrategy:
    def compute(self, index: int) -> dict:
        raise NotImplementedError


class StaticMemoryStrategy(MemoryStrategy):
    def __init__(self, config_memory: dict):
        self.config = config_memory

    def compute(self, index: int) -> dict:
        return self.config


class RandomMemoryStrategy(MemoryStrategy):
    def __init__(self, total_bytes: float):
        self.total_bytes = total_bytes

    def compute(self, index: int) -> dict:
        # match the way java is calculating longs for bytes
        used = int(random.uniform(0.3, 0.9) * self.total_bytes)
        free = int(self.total_bytes - used)
        percent = (used / self.total_bytes) * 100

        return {
            "used_bytes": used,
            "total_bytes": self.total_bytes,
            "free_bytes": free,
            "memory_usage_percent": percent,
            "page_fault_count": int(random.uniform(1000, 10000))
        }


class SpikeMemoryStrategy(MemoryStrategy):
    def __init__(self, total_bytes: float):
        self.total_bytes = total_bytes

    def compute(self, index: int) -> dict:
        if index % 20 == 0:
            used = 0.95 * self.total_bytes
        else:
            used = 0.5 * self.total_bytes

        free = self.total_bytes - used
        percent = (used / self.total_bytes) * 100

        return {
            "used_bytes": used,
            "total_bytes": self.total_bytes,
            "free_bytes": free,
            "memory_usage_percent": percent,
            "page_fault_count": 5000.0 if index % 20 == 0 else 2000.0
        }

# =========================
# Payload Builder
# =========================

class PayloadBuilder:
    def __init__(self, config: dict, cpu_strategy: CpuStrategy, memory_strategy: MemoryStrategy):
        self.config = config
        self.cpu_strategy = cpu_strategy
        self.memory_strategy = memory_strategy

    def build(self, index: int, start_time: datetime):
        timestamp = (start_time + timedelta(seconds=index)).strftime("%Y-%m-%d %H:%M:%S.%f")[:-3]

        cpu_data = self.cpu_strategy.compute(index, timestamp)
        core_usages = [random.uniform(0, 100) for _ in range(self.config["cpu_cores"]["count"])]
        memory_data = self.memory_strategy.compute(index)

        payload = {
            "cpu": cpu_data,
            "cpu_cores": [
                {
                    "core_index": i,
                    "core_mhz": self.config["cpu_cores"]["core_mhz"],
                    "core_usage_percent": core_usages[i],
                    "timestamp": timestamp
                }
                for i in range(self.config["cpu_cores"]["count"])
            ],
            "memory": {
                **memory_data,
                "timestamp": timestamp
            },
            "disk": [
                {**d, "timestamp": timestamp}
                for d in self.config["disk"]
            ]
        }

        return payload, cpu_data, core_usages

# =========================
# Expected Output Generator
# =========================

def generate_expected_output(path: str, cpu_totals: dict, core_totals: List[float], memory_totals: Dict,
                             count: int, config: dict):
    Path(path).parent.mkdir(parents=True, exist_ok=True)

    result = {
        "cpu": cpu_totals,
        "cpu_cores": [
            {
                "core_index": i,
                "core_mhz": config["cpu_cores"]["core_mhz"],
                "core_usage_percent": round(core_totals[i] / count, 2),
                "timestamp": ""
            }
            for i in range(len(core_totals))
        ],
        "memory": memory_totals
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

    expected_cpu = expected["cpu"]
    actual_cpu = actual["cpu"]

    for key in ["cpu_usage_percent", "system_responsiveness_percent", "cpu_mhz"]:
        assert approx_equal(expected_cpu[key], actual_cpu[key]), f"CPU {key} mismatch"

    for i, core in enumerate(expected["cpu_cores"]):
        assert approx_equal(
            core["core_usage_percent"],
            actual["cpu_cores"][i]["core_usage_percent"]
        ), f"Core {i} mismatch"

    expected_mem = expected["memory"]
    actual_mem = actual["memory"]

    for key in ["used_bytes", "total_bytes", "free_bytes", "memory_usage_percent", "page_fault_count"]:
        assert approx_equal(expected_mem[key], actual_mem[key]), f"Memory {key} mismatch"

    print(f"\n✅ Aggregation test passed for {expected_path}")
# =========================
# Test Runner
# =========================

class AggregationTestHarness:
    def __init__(self, config_path="config.json"):
        self.config = load_config(config_path)
        self.payload_count = self.config.get("payload_count", 60)
        self.window_size = self.config.get("aggregation_window_size", 60)
        self.api_url = self.config.get("api_url", "http://localhost:8080/api")
        self.ws_url = self.config.get("ws_url", "ws://localhost:8080/ws/metrics")
        self.jar_path = self.config.get("jar_path")
        self.output_file = Path("output/output.json")
        self.expected_dir = Path("input")
        self.actual_dir = Path("output")

        self.builder = PayloadBuilder(
            self.config,
            SpikeCpuStrategy(
                self.config["cpu"]["base_usage"],
                self.config["cpu"]["variance"]
            ),
            RandomMemoryStrategy(
                self.config["memory"]["total_bytes"]
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

    def _new_window_totals(self):
        return {
            "total_cpu_usage": 0.0,
            "total_cpu_responsiveness": 0.0,
            "total_cpu_mhz": 0.0,
            "core_totals": [0.0 for _ in range(self.config["cpu_cores"]["count"])],
            "total_mem_used": 0.0,
            "total_mem_total": 0.0,
            "total_mem_free": 0.0,
            "total_mem_percent": 0.0,
            "total_page_faults": 0.0,
            "count": 0
        }

    def _update_window_totals(self, totals: dict, cpu_data: dict, cores: list, mem: dict):
        totals["total_cpu_usage"] += cpu_data["cpu_usage_percent"]
        totals["total_cpu_responsiveness"] += cpu_data["system_responsiveness_percent"]
        totals["total_cpu_mhz"] += cpu_data["cpu_mhz"]

        totals["total_mem_used"] += mem["used_bytes"]
        totals["total_mem_total"] += mem["total_bytes"]
        totals["total_mem_free"] += mem["free_bytes"]
        totals["total_mem_percent"] += mem["memory_usage_percent"]
        totals["total_page_faults"] += mem["page_fault_count"]

        for j, val in enumerate(cores):
            totals["core_totals"][j] += val

        totals["count"] += 1

    def _build_expected_totals(self, totals: dict):
        count = totals["count"]

        cpu_totals = {
            "cpu_usage_percent": round(totals["total_cpu_usage"] / count, 2),
            "system_responsiveness_percent": round(totals["total_cpu_responsiveness"] / count, 2),
            "cpu_mhz": round(totals["total_cpu_mhz"] / count, 2),
            "timestamp": ""
        }

        memory_totals = {
            "used_bytes": round(totals["total_mem_used"] / count, 2),
            "total_bytes": round(totals["total_mem_total"] / count, 2),
            "free_bytes": round(totals["total_mem_free"] / count, 2),
            "memory_usage_percent": round(totals["total_mem_percent"] / count, 2),
            "page_fault_count": round(totals["total_page_faults"] / count, 2),
            "timestamp": ""
        }

        return cpu_totals, memory_totals

    def _wait_for_output_update(self, previous_mtime=None, timeout=15):
        start = time.time()
        while time.time() - start < timeout:
            if self.output_file.exists():
                current_mtime = self.output_file.stat().st_mtime
                if previous_mtime is None or current_mtime > previous_mtime:
                    return current_mtime
            time.sleep(0.25)

        raise TimeoutError(f"Timed out waiting for backend to update {self.output_file}")

    def _wait_for_complete_json(self, previous_mtime=None, timeout=15, settle_time=0.2):
        start = time.time()
        latest_mtime = previous_mtime

        while time.time() - start < timeout:
            if self.output_file.exists():
                current_mtime = self.output_file.stat().st_mtime

                if previous_mtime is None or current_mtime > previous_mtime:
                    latest_mtime = current_mtime

                    # small settle delay so we don't read during truncate/rewrite
                    time.sleep(settle_time)

                    try:
                        with open(self.output_file, "r", encoding="utf-8") as f:
                            content = f.read().strip()

                        if not content:
                            time.sleep(0.1)
                            continue

                        data = json.loads(content)
                        return data, latest_mtime

                    except json.JSONDecodeError:
                        time.sleep(0.1)
                        continue

            time.sleep(0.1)

        raise TimeoutError(f"Timed out waiting for complete JSON in {self.output_file}")

    async def run(self):
        java_process = self.start_backend()
        print("Starting backend...")

        try:
            self.wait_for_health()
            print("Backend healthy")

            self.expected_dir.mkdir(parents=True, exist_ok=True)
            self.actual_dir.mkdir(parents=True, exist_ok=True)

            last_output_mtime = self.output_file.stat().st_mtime if self.output_file.exists() else None
            totals = self._new_window_totals()
            window_index = 1

            async with websockets.connect(self.ws_url) as ws:
                print("WebSocket connected")

                start_time = datetime.now()

                for i in range(self.payload_count):
                    payload, cpu_data, cores = self.builder.build(i, start_time)
                    mem = payload["memory"]

                    self._update_window_totals(totals, cpu_data, cores, mem)

                    await ws.send(json.dumps(payload))

                    if totals["count"] == self.window_size:
                        cpu_totals, memory_totals = self._build_expected_totals(totals)

                        expected_path = self.expected_dir / f"expected_minute_{window_index}.json"
                        actual_snapshot_path = self.actual_dir / f"actual_minute_{window_index}.json"

                        generate_expected_output(
                            str(expected_path),
                            cpu_totals,
                            totals["core_totals"],
                            memory_totals,
                            totals["count"],
                            self.config
                        )

                        actual, last_output_mtime = self._wait_for_complete_json(last_output_mtime)

                        with open(actual_snapshot_path, "w") as f:
                            json.dump(actual, f, indent=4)

                        assert_results(str(expected_path), actual, tolerance=0.02)
                        print(f"✅ Window {window_index} passed")

                        window_index += 1
                        totals = self._new_window_totals()

                if totals["count"] > 0:
                    print(f"⚠️ Ignoring trailing partial window of {totals['count']} payloads")

        except Exception as e:
            print(e)

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
