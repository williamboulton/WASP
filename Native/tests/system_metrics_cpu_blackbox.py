"""
Black-box tests for system_metrics.exe — CPU, memory, disk, and processes (stdout JSON).

Run from repo root (example):
  python tests/system_metrics_cpu_blackbox.py --exe build/system_metrics.exe

Requires: Python 3.10+.
"""

from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
from dataclasses import dataclass, field
from enum import Enum, auto
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple, Union


# ---------------------------------------------------------------------------
# Tunable pass/fail conditions (edit or construct programmatically)
# ---------------------------------------------------------------------------


@dataclass
class CpuPassConditions:
    """Thresholds for numeric fields. Adjust per machine or policy."""

    # Overall CPU nominal clock (MHz) — registry/API; must be plausible
    cpu_mhz_min: int = 1
    cpu_mhz_max: int = 10_000

    # Aggregate usage and responsiveness are percentages
    cpu_usage_percent_min: float = 0.0
    cpu_usage_percent_max: float = 100.0
    system_responsiveness_percent_min: float = 0.0
    system_responsiveness_percent_max: float = 100.0

    # Per-core MHz (same bounds as overall by default)
    core_mhz_min: int = 1
    core_mhz_max: int = 10_000
    core_usage_percent_min: float = 0.0
    core_usage_percent_max: float = 100.0

    # Timestamp: must match this pattern (full string)
    timestamp_pattern: str = r"^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3}$"


@dataclass
class MemoryPassConditions:
    """Tunable thresholds for the ``memory`` object (GlobalMemoryStatusEx + PDH page faults)."""

    # Physical RAM total must be positive on a normal Windows session
    total_bytes_min: int = 1
    total_bytes_max: int = 2**50  # loose upper bound (exabytes scale)

    # Windows memory load (dwMemoryLoad) is 0–100
    memory_usage_percent_min: int = 0
    memory_usage_percent_max: int = 100

    # Page faults/sec counter: non-negative; upper bound avoids garbage values
    page_fault_count_min: int = 0
    page_fault_count_max: int = 10**9

    # Same timestamp format as CPU (ISO-like local time from the tool)
    timestamp_pattern: str = r"^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3}$"


@dataclass
class DiskPassConditions:
    """Tunable thresholds for each ``disk[]`` entry (volumes + PDH aggregate read/write rates)."""

    total_bytes_min: int = 0
    total_bytes_max: int = 2**60

    # Per-volume free space cannot exceed total
    # Speeds are aggregate rates from PDH (bytes/sec); non-negative, loosely bounded
    read_speed_bytes_per_sec_min: int = 0
    read_speed_bytes_per_sec_max: int = 10**15
    write_speed_bytes_per_sec_min: int = 0
    write_speed_bytes_per_sec_max: int = 10**15

    timestamp_pattern: str = r"^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3}$"

    # Typical Windows display form: ``C:``, ``D:`` (letter + colon)
    drive_letter_pattern: str = r"^[A-Za-z]:$"


# Strings emitted by PriorityClassToString in system_metrics.cpp
KNOWN_PROCESS_PRIORITIES = frozenset(
    {"REALTIME", "HIGH", "ABOVE_NORMAL", "NORMAL", "BELOW_NORMAL", "IDLE", "UNKNOWN"}
)


@dataclass
class ProcessPassConditions:
    """Tunable rules for ``processes[]`` sampling checks (black-box, system-dependent)."""

    timestamp_pattern: str = r"^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3}$"

    # At least one process in the list must report each of these priority labels
    # (some systems rarely exhibit REALTIME / IDLE in the snapshot — relax the set if needed).
    required_priority_classes: frozenset[str] = field(
        default_factory=lambda: frozenset(
            {"IDLE", "BELOW_NORMAL", "NORMAL", "ABOVE_NORMAL", "HIGH", "REALTIME"}
        )
    )

    # For "one full example" scan: string fields must be non-empty when True
    require_nonempty_name: bool = True
    require_nonempty_owner: bool = True
    require_nonempty_location: bool = True

    cpu_percent_min: float = 0.0
    cpu_percent_max: float = 100.0


@dataclass
class RunOptions:
    exe: Path
    cwd: Optional[Path] = None
    timeout_sec: float = 120.0
    encoding: str = "utf-8"


# ---------------------------------------------------------------------------
# Test case IDs and result type
# ---------------------------------------------------------------------------


class CpuTestCase(Enum):
    """One logical check; each maps to a single PASS/FAIL line in the report."""

    JSON_PARSE_STDOUT = auto()
    CPU_OBJECT_PRESENT = auto()
    CPU_OVERALL_KEYS_AND_TYPES = auto()
    CPU_OVERALL_VALUE_RANGES = auto()
    CPU_CORES_LIST_PRESENT = auto()
    CPU_CORES_NONEMPTY = auto()
    CPU_CORES_KEYS_AND_TYPES = auto()
    CPU_CORES_VALUE_RANGES = auto()
    CPU_CORES_INDICES_SEQUENTIAL = auto()


class MemoryTestCase(Enum):
    """Black-box checks for ``memory`` — presence, schema, ranges, and internal consistency."""

    MEMORY_OBJECT_PRESENT = auto()
    MEMORY_KEYS_AND_TYPES = auto()
    MEMORY_VALUE_RANGES = auto()
    MEMORY_BYTES_CONSISTENCY = auto()


class DiskTestCase(Enum):
    """Black-box checks for ``disk`` — array of volume metrics + shared I/O rates."""

    DISK_ARRAY_PRESENT = auto()
    DISK_ENTRIES_KEYS_AND_TYPES = auto()
    DISK_ENTRIES_VALUE_RANGES = auto()
    DISK_DRIVE_LETTERS_WELL_FORMED = auto()
    DISK_UNIQUE_DRIVE_LETTERS = auto()


class ProcessTestCase(Enum):
    """``processes`` — array present; at-least-one full record; priority coverage across the sample."""

    PROCESSES_ARRAY_PRESENT = auto()
    PROCESS_FULL_METRICS_EXAMPLE_EXISTS = auto()
    ALL_REQUIRED_PRIORITY_CLASSES_PRESENT = auto()


CaseKind = Union[CpuTestCase, MemoryTestCase, DiskTestCase, ProcessTestCase]


@dataclass
class CaseResult:
    case: CaseKind
    passed: bool
    detail: str = ""

    @property
    def status(self) -> str:
        return "PASS" if self.passed else "FAIL"


# ---------------------------------------------------------------------------
# Helpers for reading the JSON output
# ---------------------------------------------------------------------------

_REGEX_CACHE: Dict[str, re.Pattern[str]] = {}


def _regex_fullmatch(value: str, pattern: str) -> bool:
    if pattern not in _REGEX_CACHE:
        _REGEX_CACHE[pattern] = re.compile(pattern)
    return bool(_REGEX_CACHE[pattern].match(value))


def _matches_timestamp(value: str, pattern: str) -> bool:
    return _regex_fullmatch(value, pattern)


def _is_int(x: Any) -> bool:
    return isinstance(x, int) and not isinstance(x, bool)


def _is_real(x: Any) -> bool:
    return isinstance(x, (int, float)) and not isinstance(x, bool)


# Run the .exe and return the parsed JSON dict or None, error message or None
def run_system_metrics(opts: RunOptions) -> Tuple[Optional[Dict[str, Any]], Optional[str]]:
    """Run the .exe; return (parsed JSON dict or None, error message or None)."""
    exe = opts.exe.resolve()
    if not exe.is_file():
        return None, f"Executable not found: {exe}"

    try:
        proc = subprocess.run(
            [str(exe)],
            cwd=str(opts.cwd.resolve()) if opts.cwd else None,
            capture_output=True,
            text=True,
            encoding=opts.encoding,
            errors="replace",
            timeout=opts.timeout_sec,
        )
    except subprocess.TimeoutExpired:
        return None, f"Timed out after {opts.timeout_sec}s"
    except OSError as e:
        return None, f"Failed to run exe: {e}"

    if proc.returncode != 0:
        tail = (proc.stderr or proc.stdout or "")[-500:]
        return None, f"Exit code {proc.returncode}. stderr/stdout tail: {tail!r}"

    raw = (proc.stdout or "").strip()
    if not raw:
        return None, "Empty stdout (expected JSON)"

    try:
        data = json.loads(raw)
    except json.JSONDecodeError as e:
        return None, f"Invalid JSON on stdout: {e}"

    if not isinstance(data, dict):
        return None, f"Root JSON must be an object, got {type(data).__name__}"

    return data, None


# ---------------------------------------------------------------------------
# Per-case checks (return False + message on failure)
# ---------------------------------------------------------------------------

# Check that the cpu_overall object has the required keys and types
def _check_cpu_overall_keys_and_types(
    cpu: Any,
) -> Tuple[bool, str]:
    if not isinstance(cpu, dict):
        return False, f'"cpu" must be object, got {type(cpu).__name__}'
    required = {
        "cpu_mhz": _is_int,
        "cpu_usage_percent": _is_real,
        "system_responsiveness_percent": _is_real,
        "timestamp": lambda v: isinstance(v, str),
    }
    for key, pred in required.items():
        if key not in cpu:
            return False, f'Missing "cpu"."{key}"'
        if not pred(cpu[key]):
            return False, f'Bad type/value for "cpu"."{key}": {cpu[key]!r}'
    return True, ""


# Check that the cpu_overall object has the required ranges
def _check_cpu_overall_ranges(cpu: dict, cond: CpuPassConditions) -> Tuple[bool, str]:
    mhz = cpu["cpu_mhz"]
    if not (cond.cpu_mhz_min <= mhz <= cond.cpu_mhz_max):
        return False, f"cpu_mhz {mhz} not in [{cond.cpu_mhz_min}, {cond.cpu_mhz_max}]"

    u = float(cpu["cpu_usage_percent"])
    if not (cond.cpu_usage_percent_min <= u <= cond.cpu_usage_percent_max):
        return False, (
            f"cpu_usage_percent {u} not in "
            f"[{cond.cpu_usage_percent_min}, {cond.cpu_usage_percent_max}]"
        )

    r = float(cpu["system_responsiveness_percent"])
    if not (cond.system_responsiveness_percent_min <= r <= cond.system_responsiveness_percent_max):
        return False, (
            f"system_responsiveness_percent {r} not in "
            f"[{cond.system_responsiveness_percent_min}, {cond.system_responsiveness_percent_max}]"
        )

    ts = cpu["timestamp"]
    if not _matches_timestamp(ts, cond.timestamp_pattern):
        return False, f'timestamp does not match pattern: {ts!r}'
    return True, ""

# Check that the cpu_cores array is present and not empty
def _check_cores_nonempty(cores: Any) -> Tuple[bool, str]:
    if not isinstance(cores, list):
        return False, f'"cpu_cores" must be array, got {type(cores).__name__}'
    if len(cores) == 0:
        return False, '"cpu_cores" must have at least one entry'
    return True, ""


# Check that each core entry is a dictionary with the required keys and types
def _check_each_core_keys_types(entry: Any, index: int) -> Tuple[bool, str]:
    if not isinstance(entry, dict):
        return False, f"cpu_cores[{index}] must be object"
    req = {
        "core_index": _is_int,
        "core_mhz": _is_int,
        "core_usage_percent": _is_real,
        "timestamp": lambda v: isinstance(v, str),
    }
    for key, pred in req.items():
        if key not in entry:
            return False, f'cpu_cores[{index}] missing "{key}"'
        if not pred(entry[key]):
            return False, f'cpu_cores[{index}].{key} invalid: {entry[key]!r}'
    return True, ""


# Check that each core entry has the required ranges
def _check_each_core_ranges(entry: dict, index: int, cond: CpuPassConditions) -> Tuple[bool, str]:
    mhz = entry["core_mhz"]
    if not (cond.core_mhz_min <= mhz <= cond.core_mhz_max):
        return False, f"cpu_cores[{index}].core_mhz {mhz} not in [{cond.core_mhz_min}, {cond.core_mhz_max}]"

    u = float(entry["core_usage_percent"])
    if not (cond.core_usage_percent_min <= u <= cond.core_usage_percent_max):
        return False, (
            f"cpu_cores[{index}].core_usage_percent {u} not in "
            f"[{cond.core_usage_percent_min}, {cond.core_usage_percent_max}]"
        )

    ts = entry["timestamp"]
    if not _matches_timestamp(ts, cond.timestamp_pattern):
        return False, f"cpu_cores[{index}].timestamp does not match pattern: {ts!r}"
    return True, ""


# Check that the core indices are sequential
def _check_core_indices_sequential(cores: List[dict]) -> Tuple[bool, str]:
    for i, e in enumerate(cores):
        if e.get("core_index") != i:
            return False, (
                f'Expected core_index == {i} at position {i}, got {e.get("core_index")!r}'
            )
    return True, ""

# Check that the memory object has the required keys and types
def _check_memory_keys_and_types(mem: Any) -> Tuple[bool, str]:
    if not isinstance(mem, dict):
        return False, f'"memory" must be object, got {type(mem).__name__}'
    required = {
        "total_bytes": _is_int,
        "free_bytes": _is_int,
        "used_bytes": _is_int,
        "memory_usage_percent": _is_int,
        "page_fault_count": _is_int,
        "timestamp": lambda v: isinstance(v, str),
    }
    for key, pred in required.items():
        if key not in mem:
            return False, f'Missing "memory"."{key}"'
        if not pred(mem[key]):
            return False, f'Bad type for "memory"."{key}": {mem[key]!r}'
    return True, ""

# Check that the memory object has the required ranges
def _check_memory_ranges(mem: dict, cond: MemoryPassConditions) -> Tuple[bool, str]:
    total = mem["total_bytes"]
    if not (cond.total_bytes_min <= total <= cond.total_bytes_max):
        return False, f"total_bytes {total} not in [{cond.total_bytes_min}, {cond.total_bytes_max}]"

    free = mem["free_bytes"]
    if free < 0:
        return False, f"free_bytes {free} must be >= 0"
    if free > total:
        return False, f"free_bytes {free} must be <= total_bytes {total}"

    used = mem["used_bytes"]
    if used < 0 or used > total:
        return False, f"used_bytes {used} must be in [0, total_bytes={total}]"

    pct = mem["memory_usage_percent"]
    if not (cond.memory_usage_percent_min <= pct <= cond.memory_usage_percent_max):
        return False, (
            f"memory_usage_percent {pct} not in "
            f"[{cond.memory_usage_percent_min}, {cond.memory_usage_percent_max}]"
        )

    pf = mem["page_fault_count"]
    if not (cond.page_fault_count_min <= pf <= cond.page_fault_count_max):
        return False, (
            f"page_fault_count {pf} not in "
            f"[{cond.page_fault_count_min}, {cond.page_fault_count_max}]"
        )

    ts = mem["timestamp"]
    if not _matches_timestamp(ts, cond.timestamp_pattern):
        return False, f"timestamp does not match pattern: {ts!r}"
    return True, ""

# Check that the used_bytes is consistent with the total_bytes and free_bytes
def _check_memory_bytes_consistency(mem: dict) -> Tuple[bool, str]:
    total = mem["total_bytes"]
    free = mem["free_bytes"]
    used = mem["used_bytes"]
    expected_used = total - free
    if used != expected_used:
        return False, (
            f"used_bytes {used} != total_bytes - free_bytes ({total} - {free} = {expected_used})"
        )
    return True, ""

# Check that the disk speed is a valid integer or non-negative float
def _disk_speed_ok(x: Any) -> bool:
    """PDH rates are emitted as integers in our tool; allow int or non-negative float."""
    if _is_int(x) and x >= 0:
        return True
    if isinstance(x, float) and not isinstance(x, bool) and x >= 0.0:
        return True
    return False

# Check that the disk entry has the required keys and types
def _check_disk_entry_keys_and_types(entry: Any, index: int) -> Tuple[bool, str]:
    if not isinstance(entry, dict):
        return False, f"disk[{index}] must be object"
    req = {
        "drive_letter": lambda v: isinstance(v, str) and len(v) > 0,
        "total_bytes": _is_int,
        "free_bytes": _is_int,
        "read_speed_bytes_per_sec": _disk_speed_ok,
        "write_speed_bytes_per_sec": _disk_speed_ok,
        "timestamp": lambda v: isinstance(v, str),
    }
    for key, pred in req.items():
        if key not in entry:
            return False, f'disk[{index}] missing "{key}"'
        if not pred(entry[key]):
            return False, f'disk[{index}].{key} invalid: {entry[key]!r}'
    return True, ""

# Check that the disk entry has the required ranges as specified in the DiskPassConditions class
def _check_disk_entry_ranges(entry: dict, index: int, cond: DiskPassConditions) -> Tuple[bool, str]:
    total = entry["total_bytes"]
    if not (cond.total_bytes_min <= total <= cond.total_bytes_max):
        return False, f"disk[{index}].total_bytes {total} out of range"

    free = entry["free_bytes"]
    if free < 0 or free > total:
        return False, f"disk[{index}].free_bytes {free} must be in [0, total_bytes={total}]"

    rs = entry["read_speed_bytes_per_sec"]
    ws = entry["write_speed_bytes_per_sec"]
    rs_f = float(rs) if isinstance(rs, int) else rs
    ws_f = float(ws) if isinstance(ws, int) else ws
    if not (cond.read_speed_bytes_per_sec_min <= rs_f <= cond.read_speed_bytes_per_sec_max):
        return False, f"disk[{index}].read_speed_bytes_per_sec {rs} out of range"
    if not (cond.write_speed_bytes_per_sec_min <= ws_f <= cond.write_speed_bytes_per_sec_max):
        return False, f"disk[{index}].write_speed_bytes_per_sec {ws} out of range"

    ts = entry["timestamp"]
    if not _matches_timestamp(ts, cond.timestamp_pattern):
        return False, f"disk[{index}].timestamp does not match pattern: {ts!r}"
    return True, ""

# Check that the disk drive letter is well-formed as specified in the DiskPassConditions class
def _check_disk_drive_letters_well_formed(disks: List[dict], cond: DiskPassConditions) -> Tuple[bool, str]:
    for i, e in enumerate(disks):
        letter = e.get("drive_letter", "")
        if not isinstance(letter, str):
            return False, f"disk[{i}].drive_letter not a string"
        if not _regex_fullmatch(letter, cond.drive_letter_pattern):
            return False, f"disk[{i}].drive_letter {letter!r} does not match pattern"
    return True, ""

# Check that the disk drive letters are unique
def _check_disk_unique_drive_letters(disks: List[dict]) -> Tuple[bool, str]:
    seen: set[str] = set()
    for i, e in enumerate(disks):
        letter = e.get("drive_letter")
        if not isinstance(letter, str):
            return False, f"disk[{i}].drive_letter not a string"
        key = letter.upper()
        if key in seen:
            return False, f"duplicate drive_letter {letter!r}"
        seen.add(key)
    return True, ""

# Run all the disk test cases
def run_disk_test_cases(
    data: Dict[str, Any],
    cond: DiskPassConditions,
) -> List[CaseResult]:
    """Evaluate ``disk``: fixed/removable volumes, sizes, PDH read/write rates, timestamps."""

    results: List[CaseResult] = []

    def add(case: DiskTestCase, ok: bool, detail: str = "") -> None:
        results.append(CaseResult(case=case, passed=ok, detail=detail))

    disks = data.get("disk")
    add(
        DiskTestCase.DISK_ARRAY_PRESENT,
        isinstance(disks, list),
        "" if isinstance(disks, list) else '"disk" missing or not an array',
    )
    if not isinstance(disks, list):
        for case in (
            DiskTestCase.DISK_ENTRIES_KEYS_AND_TYPES,
            DiskTestCase.DISK_ENTRIES_VALUE_RANGES,
            DiskTestCase.DISK_DRIVE_LETTERS_WELL_FORMED,
            DiskTestCase.DISK_UNIQUE_DRIVE_LETTERS,
        ):
            add(case, False, 'skipped: invalid "disk"')
        return results

    if len(disks) == 0:
        add(DiskTestCase.DISK_ENTRIES_KEYS_AND_TYPES, True, "no volumes (ok)")
        add(DiskTestCase.DISK_ENTRIES_VALUE_RANGES, True, "no volumes (ok)")
        add(DiskTestCase.DISK_DRIVE_LETTERS_WELL_FORMED, True, "no volumes (ok)")
        add(DiskTestCase.DISK_UNIQUE_DRIVE_LETTERS, True, "no volumes (ok)")
        return results

    all_types_ok = True
    first_type_err = ""
    for i, entry in enumerate(disks):
        ot, et = _check_disk_entry_keys_and_types(entry, i)
        if not ot:
            all_types_ok = False
            first_type_err = et
            break

    all_ranges_ok = True
    first_range_err = ""
    if all_types_ok:
        for i, entry in enumerate(disks):
            assert isinstance(entry, dict)
            orr, er = _check_disk_entry_ranges(entry, i, cond)
            if not orr:
                all_ranges_ok = False
                first_range_err = er
                break

    add(DiskTestCase.DISK_ENTRIES_KEYS_AND_TYPES, all_types_ok, first_type_err)
    add(
        DiskTestCase.DISK_ENTRIES_VALUE_RANGES,
        all_types_ok and all_ranges_ok,
        first_range_err if all_types_ok else "skipped: disk keys/types failed",
    )

    if all_types_ok and all(isinstance(d, dict) for d in disks):
        okl, msgl = _check_disk_drive_letters_well_formed(disks, cond)
        add(DiskTestCase.DISK_DRIVE_LETTERS_WELL_FORMED, okl, msgl)
        oku, msgu = _check_disk_unique_drive_letters(disks)
        add(DiskTestCase.DISK_UNIQUE_DRIVE_LETTERS, oku, msgu)
    else:
        add(DiskTestCase.DISK_DRIVE_LETTERS_WELL_FORMED, False, "skipped: bad disk entries")
        add(DiskTestCase.DISK_UNIQUE_DRIVE_LETTERS, False, "skipped: bad disk entries")

    return results

# Check that the process is a full metrics example as specified in the ProcessPassConditions class
def _process_is_full_metrics_example(p: dict, cond: ProcessPassConditions) -> bool:
    """
    True if this single process object has all required keys, valid types/ranges,
    and (when configured) non-empty name, owner, and exe path — i.e. one "complete" row.
    """
    keys = (
        "pid",
        "name",
        "owner",
        "priority",
        "cpu_percent",
        "cpu_time_100ns",
        "location",
        "timestamp",
    )
    for k in keys:
        if k not in p:
            return False

    pid = p["pid"]
    if not _is_int(pid) or pid < 0:
        return False

    name = p["name"]
    if not isinstance(name, str):
        return False
    if cond.require_nonempty_name and len(name.strip()) == 0:
        return False

    owner = p["owner"]
    if not isinstance(owner, str):
        return False
    if cond.require_nonempty_owner and len(owner.strip()) == 0:
        return False

    pr = p["priority"]
    if not isinstance(pr, str) or pr not in KNOWN_PROCESS_PRIORITIES:
        return False

    cpu = p["cpu_percent"]
    if not _is_real(cpu):
        return False
    cf = float(cpu)
    if not (cond.cpu_percent_min <= cf <= cond.cpu_percent_max):
        return False

    ct = p["cpu_time_100ns"]
    if not _is_int(ct) or ct < 0:
        return False

    loc = p["location"]
    if not isinstance(loc, str):
        return False
    if cond.require_nonempty_location and len(loc.strip()) == 0:
        return False

    ts = p["timestamp"]
    if not isinstance(ts, str) or not _matches_timestamp(ts, cond.timestamp_pattern):
        return False

    return True

# Run all the process test cases
def run_process_test_cases(
    data: Dict[str, Any],
    cond: ProcessPassConditions,
) -> List[CaseResult]:
    """
    Black-box process list checks:

    * At least one process row must qualify as a full metrics example (all fields present
      and passing validation — optionally requiring non-empty owner and location).
    * Across the entire sample, each priority class listed in ``required_priority_classes``
      must appear at least once (any process may supply each level).
    """

    results: List[CaseResult] = []

    def add(case: ProcessTestCase, ok: bool, detail: str = "") -> None:
        results.append(CaseResult(case=case, passed=ok, detail=detail))

    procs = data.get("processes")
    add(
        ProcessTestCase.PROCESSES_ARRAY_PRESENT,
        isinstance(procs, list),
        "" if isinstance(procs, list) else '"processes" missing or not an array',
    )
    if not isinstance(procs, list):
        add(ProcessTestCase.PROCESS_FULL_METRICS_EXAMPLE_EXISTS, False, 'skipped: invalid "processes"')
        add(ProcessTestCase.ALL_REQUIRED_PRIORITY_CLASSES_PRESENT, False, 'skipped: invalid "processes"')
        return results

    if len(procs) == 0:
        add(ProcessTestCase.PROCESS_FULL_METRICS_EXAMPLE_EXISTS, False, "empty processes list")
        add(ProcessTestCase.ALL_REQUIRED_PRIORITY_CLASSES_PRESENT, False, "empty processes list")
        return results

    has_full = False
    for entry in procs:
        if isinstance(entry, dict) and _process_is_full_metrics_example(entry, cond):
            has_full = True
            break

    add(
        ProcessTestCase.PROCESS_FULL_METRICS_EXAMPLE_EXISTS,
        has_full,
        ""
        if has_full
        else (
            "no single process had all fields valid"
            + (", with non-empty name/owner/location" if (cond.require_nonempty_name or cond.require_nonempty_owner or cond.require_nonempty_location) else "")
        ),
    )

    seen_priorities: set[str] = set()
    for entry in procs:
        if not isinstance(entry, dict):
            continue
        pr = entry.get("priority")
        if isinstance(pr, str):
            seen_priorities.add(pr)

    missing = cond.required_priority_classes - seen_priorities
    priorities_ok = len(missing) == 0
    add(
        ProcessTestCase.ALL_REQUIRED_PRIORITY_CLASSES_PRESENT,
        priorities_ok,
        ""
        if priorities_ok
        else f"no process had priority in {sorted(missing)} (seen: {sorted(seen_priorities)})",
    )

    return results


# Run all the memory test cases
def run_memory_test_cases(
    data: Dict[str, Any],
    cond: MemoryPassConditions,
) -> List[CaseResult]:
    """Evaluate memory metrics: total/free/used, load %, page faults, timestamp."""

    results: List[CaseResult] = []

    def add(case: MemoryTestCase, ok: bool, detail: str = "") -> None:
        results.append(CaseResult(case=case, passed=ok, detail=detail))

    mem = data.get("memory")
    add(
        MemoryTestCase.MEMORY_OBJECT_PRESENT,
        isinstance(mem, dict),
        "" if isinstance(mem, dict) else '"memory" missing or not an object',
    )
    if not isinstance(mem, dict):
        for case in (
            MemoryTestCase.MEMORY_KEYS_AND_TYPES,
            MemoryTestCase.MEMORY_VALUE_RANGES,
            MemoryTestCase.MEMORY_BYTES_CONSISTENCY,
        ):
            add(case, False, 'skipped: invalid or missing "memory"')
        return results

    ok, msg = _check_memory_keys_and_types(mem)
    add(MemoryTestCase.MEMORY_KEYS_AND_TYPES, ok, msg)
    if ok:
        ok2, msg2 = _check_memory_ranges(mem, cond)
        add(MemoryTestCase.MEMORY_VALUE_RANGES, ok2, msg2)
        ok3, msg3 = _check_memory_bytes_consistency(mem)
        add(MemoryTestCase.MEMORY_BYTES_CONSISTENCY, ok3, msg3)
    else:
        add(MemoryTestCase.MEMORY_VALUE_RANGES, False, "skipped: memory keys/types failed")
        add(MemoryTestCase.MEMORY_BYTES_CONSISTENCY, False, "skipped: memory keys/types failed")

    return results


# Run all the test cases
def run_cpu_test_cases(
    data: Dict[str, Any],
    cond: CpuPassConditions,
) -> List[CaseResult]:
    """Evaluate all CPU test cases against already-parsed root JSON."""

    results: List[CaseResult] = []

    def add(case: CpuTestCase, ok: bool, detail: str = "") -> None:
        results.append(CaseResult(case=case, passed=ok, detail=detail))

    # JSON parse is done by caller; we still expose a no-op style case for symmetry
    add(CpuTestCase.JSON_PARSE_STDOUT, True, "parsed OK")

    cpu = data.get("cpu")
    add(
        CpuTestCase.CPU_OBJECT_PRESENT,
        isinstance(cpu, dict),
        "" if isinstance(cpu, dict) else '"cpu" missing or not an object',
    )
    if not isinstance(cpu, dict):
        # Downstream cases need cpu
        for case in (
            CpuTestCase.CPU_OVERALL_KEYS_AND_TYPES,
            CpuTestCase.CPU_OVERALL_VALUE_RANGES,
            CpuTestCase.CPU_CORES_LIST_PRESENT,
            CpuTestCase.CPU_CORES_NONEMPTY,
            CpuTestCase.CPU_CORES_KEYS_AND_TYPES,
            CpuTestCase.CPU_CORES_VALUE_RANGES,
            CpuTestCase.CPU_CORES_INDICES_SEQUENTIAL,
        ):
            add(case, False, 'skipped: invalid or missing "cpu"')
        return results

    ok, msg = _check_cpu_overall_keys_and_types(cpu)
    add(CpuTestCase.CPU_OVERALL_KEYS_AND_TYPES, ok, msg)
    if ok:
        ok2, msg2 = _check_cpu_overall_ranges(cpu, cond)
        add(CpuTestCase.CPU_OVERALL_VALUE_RANGES, ok2, msg2)
    else:
        add(CpuTestCase.CPU_OVERALL_VALUE_RANGES, False, "skipped: cpu keys/types failed")

    cores = data.get("cpu_cores")
    add(
        CpuTestCase.CPU_CORES_LIST_PRESENT,
        isinstance(cores, list),
        "" if isinstance(cores, list) else '"cpu_cores" missing or not an array',
    )
    if not isinstance(cores, list):
        for case in (
            CpuTestCase.CPU_CORES_NONEMPTY,
            CpuTestCase.CPU_CORES_KEYS_AND_TYPES,
            CpuTestCase.CPU_CORES_VALUE_RANGES,
            CpuTestCase.CPU_CORES_INDICES_SEQUENTIAL,
        ):
            add(case, False, 'skipped: invalid "cpu_cores"')
        return results

    okn, msgn = _check_cores_nonempty(cores)
    add(CpuTestCase.CPU_CORES_NONEMPTY, okn, msgn)

    all_types_ok = True
    first_type_err = ""
    for i, entry in enumerate(cores):
        ot, et = _check_each_core_keys_types(entry, i)
        if not ot:
            all_types_ok = False
            first_type_err = et
            break

    all_ranges_ok = True
    first_range_err = ""
    if all_types_ok:
        for i, entry in enumerate(cores):
            assert isinstance(entry, dict)
            orr, er = _check_each_core_ranges(entry, i, cond)
            if not orr:
                all_ranges_ok = False
                first_range_err = er
                break

    add(CpuTestCase.CPU_CORES_KEYS_AND_TYPES, all_types_ok, first_type_err)
    add(
        CpuTestCase.CPU_CORES_VALUE_RANGES,
        all_types_ok and all_ranges_ok,
        first_range_err if all_types_ok else "skipped: core keys/types failed",
    )

    if cores and all(isinstance(c, dict) for c in cores):
        oks, msgs = _check_core_indices_sequential(cores)
        add(CpuTestCase.CPU_CORES_INDICES_SEQUENTIAL, oks, msgs)
    else:
        add(CpuTestCase.CPU_CORES_INDICES_SEQUENTIAL, False, "skipped: bad core entries")

    return results


def print_report(results: List[CaseResult], file=sys.stdout) -> None:
    for r in results:
        line = f"{r.case.name}: {r.status}"
        if r.detail:
            line += f" — {r.detail}"
        print(line, file=file)


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Black-box CPU, memory, disk, and processes JSON checks for system_metrics.exe"
    )
    parser.add_argument(
        "--exe",
        type=Path,
        default=Path("build") / "system_metrics.exe",
        help="Path to system_metrics.exe",
    )
    parser.add_argument(
        "--cwd",
        type=Path,
        default=None,
        help="Working directory for the process (default: exe directory)",
    )
    parser.add_argument(
        "--timeout",
        type=float,
        default=120.0,
        help="Seconds to wait for the exe (default 120)",
    )
    args = parser.parse_args()

    cwd = args.cwd
    if cwd is None and args.exe.parent:
        cwd = args.exe.parent

    opts = RunOptions(exe=args.exe, cwd=cwd, timeout_sec=args.timeout)
    data, err = run_system_metrics(opts)
    if err:
        print(f"ERROR: {err}", file=sys.stderr)
        return 2

    cond_cpu = CpuPassConditions()
    cond_mem = MemoryPassConditions(timestamp_pattern=cond_cpu.timestamp_pattern)
    cond_disk = DiskPassConditions(
        timestamp_pattern=cond_cpu.timestamp_pattern,
    )
    cond_proc = ProcessPassConditions(timestamp_pattern=cond_cpu.timestamp_pattern)
    results = run_cpu_test_cases(data, cond_cpu)
    results.extend(run_memory_test_cases(data, cond_mem))
    results.extend(run_disk_test_cases(data, cond_disk))
    results.extend(run_process_test_cases(data, cond_proc))
    print_report(results)

    if all(r.passed for r in results):
        return 0
    return 1


if __name__ == "__main__":
    sys.exit(main())
