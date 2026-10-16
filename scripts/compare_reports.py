#!/usr/bin/env python3
"""Compares two simulation reports for the differences that matter.

The simulator promises that two runs with the same seed agree on every routing
decision. They cannot agree on everything: a wall-clock duration and a
throughput derived from it are measurements, not decisions, and each node's
device id comes from a freshly generated key. Those fields are listed in
`SimulationReport.volatileJsonFields()` and subtracted here.

Everything else must match, including key order. A report is only useful as a
regression artefact if a routing change shows up as a diff, and a diff is
meaningless if two identical runs disagree about where a key belongs.

Usage:
    compare_reports.py A.json B.json [--max-report N]

Exit status is 0 when the reports agree and 1 when they do not, with the first
differences printed, so this works directly as a CI step.
"""

from __future__ import annotations

import json
import sys
from typing import Any

# Mirrors SimulationReport.volatileJsonFields(). Kept as a literal rather than
# derived from the Java source so a failure here is a visible disagreement
# between two places that have to agree, not a silent reimplementation.
VOLATILE_LEAF_KEYS = frozenset({"wallClockMillis", "throughputPerSecond",
                                "deviceId", "keyFingerprint"})


def strip_volatile(value: Any) -> Any:
    """Replaces volatile leaves with a constant, preserving key order."""
    if isinstance(value, dict):
        # dict preserves insertion order in Python 3.7+, which is what makes the
        # serialised comparison below an ordering check as well as a value
        # check.
        return {
            key: ("<volatile>" if key in VOLATILE_LEAF_KEYS else strip_volatile(inner))
            for key, inner in value.items()
        }
    if isinstance(value, list):
        return [strip_volatile(item) for item in value]
    return value


def first_differences(a: Any, b: Any, out: list[str], path: str = "") -> None:
    if len(out) >= 20:
        return
    if type(a) is not type(b):
        out.append(f"{path or '<root>'}: type {type(a).__name__} vs {type(b).__name__}")
        return
    if isinstance(a, dict):
        for key in a.keys() - b.keys():
            out.append(f"{path}/{key}: only in the first report")
        for key in b.keys() - a.keys():
            out.append(f"{path}/{key}: only in the second report")
        for key in a.keys() & b.keys():
            if list(a.keys()).index(key) != list(b.keys()).index(key):
                out.append(f"{path}/{key}: key order differs")
            first_differences(a[key], b[key], out, f"{path}/{key}")
    elif isinstance(a, list):
        if len(a) != len(b):
            out.append(f"{path}: length {len(a)} vs {len(b)}")
            return
        for index, (left, right) in enumerate(zip(a, b)):
            first_differences(left, right, out, f"{path}[{index}]")
    elif a != b:
        out.append(f"{path}: {a!r} vs {b!r}")


def main() -> int:
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    if len(args) != 2:
        print(__doc__)
        return 2

    left = strip_volatile(json.loads(open(args[0], encoding="utf-8").read()))
    right = strip_volatile(json.loads(open(args[1], encoding="utf-8").read()))

    # Comparing the serialised text rather than the objects, because dict
    # equality in Python ignores key order and an order regression is exactly
    # the kind of bug this check exists to catch.
    left_text = json.dumps(left, sort_keys=False, indent=1)
    right_text = json.dumps(right, sort_keys=False, indent=1)
    if left_text == right_text:
        print("reports are reproducible: identical apart from the documented "
              "volatile fields")
        return 0

    differences: list[str] = []
    first_differences(left, right, differences)
    print("reports differ:", file=sys.stderr)
    for line in differences:
        print(f"  {line}", file=sys.stderr)
    print("\nIf the only differences are key ordering, the cause is Map.copyOf "
          "or HashMap iteration leaking into the report.", file=sys.stderr)
    return 1


if __name__ == "__main__":
    sys.exit(main())