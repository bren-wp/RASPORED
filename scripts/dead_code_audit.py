#!/usr/bin/env python3
"""Static dead-code audit for production Kotlin, JavaScript and PHP sources.

This intentionally uses conservative heuristics:
- private Kotlin functions/properties referenced only at their declaration are errors;
- named JS/PHP functions referenced only at declaration are reported as candidates;
- Kotlin top-level classes/objects with no cross-file reference are reported for review;
- TODO/FIXME markers in production source are reported.

Framework entry points and symbols referenced by manifests/reflection are not auto-deleted.
"""
from __future__ import annotations

import argparse
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PRODUCTION_ROOTS = [
    ROOT / "android/app/src/main/java",
    ROOT / "demo/src/main/java",
    ROOT / "web/public",
]
TEXT_SUFFIXES = {".kt", ".js", ".php"}

def files_under(root: Path):
    if not root.exists():
        return []
    return [
        p for p in root.rglob("*")
        if p.is_file() and p.suffix in TEXT_SUFFIXES
    ]

files = [p for root in PRODUCTION_ROOTS for p in files_under(root)]
contents = {p: p.read_text(encoding="utf-8") for p in files}
all_text = "\n".join(contents.values())

private_kotlin = []
js_candidates = []
php_candidates = []
orphan_kotlin = []
markers = []

for path, text in contents.items():
    rel = path.relative_to(ROOT).as_posix()
    for line_no, line in enumerate(text.splitlines(), 1):
        if re.search(r"\b(?:TODO|FIXME)\b", line):
            markers.append({"path": rel, "line": line_no, "text": line.strip()})

    if path.suffix == ".kt":
        names = set(re.findall(
            r"\bprivate\s+(?:suspend\s+)?(?:fun|val|var)\s+([A-Za-z_][A-Za-z0-9_]*)",
            text,
        ))
        for name in sorted(names):
            count = len(re.findall(rf"\b{re.escape(name)}\b", text))
            if count == 1:
                private_kotlin.append({"path": rel, "symbol": name})

        top_level = set(re.findall(
            r"^(?:data\s+class|class|object)\s+([A-Z][A-Za-z0-9_]*)",
            text,
            flags=re.MULTILINE,
        ))
        for name in sorted(top_level):
            if name in {"MainActivity"}:
                continue
            count = len(re.findall(rf"\b{re.escape(name)}\b", all_text))
            if count == 1:
                orphan_kotlin.append({"path": rel, "symbol": name})

    elif path.suffix == ".js":
        names = set(re.findall(
            r"\bfunction\s+([A-Za-z_$][A-Za-z0-9_$]*)\s*\(",
            text,
        ))
        for name in sorted(names):
            count = len(re.findall(rf"\b{re.escape(name)}\b", all_text))
            if count == 1:
                js_candidates.append({"path": rel, "symbol": name})

    elif path.suffix == ".php":
        names = set(re.findall(
            r"\bfunction\s+([A-Za-z_][A-Za-z0-9_]*)\s*\(",
            text,
        ))
        for name in sorted(names):
            count = len(re.findall(rf"\b{re.escape(name)}\b", all_text))
            if count == 1:
                php_candidates.append({"path": rel, "symbol": name})

report = {
    "scanned_files": len(files),
    "dead_private_kotlin": private_kotlin,
    "orphan_kotlin_candidates": orphan_kotlin,
    "javascript_candidates": js_candidates,
    "php_candidates": php_candidates,
    "todo_fixme_markers": markers,
}

print(json.dumps(report, indent=2, ensure_ascii=False))

parser = argparse.ArgumentParser()
parser.add_argument("--strict", action="store_true")
args = parser.parse_args()

if args.strict and private_kotlin:
    raise SystemExit(
        "Dead-code audit failed: remove or use private Kotlin symbols listed above."
    )
