#!/usr/bin/env python3
"""Static dead-code audit for production Kotlin, JavaScript and PHP sources.

This intentionally uses conservative heuristics:
- private Kotlin functions/properties referenced only at their declaration are errors;
- named JS/PHP functions referenced only at declaration are reported as candidates;
- Kotlin top-level classes/objects with no cross-file reference are reported for review;
- TODO/FIXME markers in production source are reported;
- static Web UI IDs are checked for duplicates and dangling JavaScript bindings.

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
        function_names = set(re.findall(
            r"\bprivate\s+(?:suspend\s+)?fun\s+(?:[A-Za-z_][A-Za-z0-9_]*\.)?([A-Za-z_][A-Za-z0-9_]*)\s*\(",
            text,
        ))
        property_names = set(re.findall(
            r"\bprivate\s+(?:val|var)\s+([A-Za-z_][A-Za-z0-9_]*)",
            text,
        ))
        names = function_names | property_names
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

# UI wiring audit: catch controls that JavaScript expects but the rendered templates
# can no longer provide. Only literal/static IDs are checked to keep this conservative.
web_root = ROOT / "web/public"
php_files = list(web_root.rglob("*.php"))
js_files = list(web_root.rglob("*.js"))

html_id_locations: dict[str, list[str]] = {}
for path in php_files:
    text = path.read_text(encoding="utf-8")
    rel = path.relative_to(ROOT).as_posix()
    for match in re.finditer(r"""\bid\s*=\s*["']([A-Za-z][A-Za-z0-9_:\-\.]*)["']""", text):
        html_id_locations.setdefault(match.group(1), []).append(rel)

dynamic_web_ids: set[str] = set()
referenced_web_ids: dict[str, set[str]] = {}
for path in js_files:
    text = path.read_text(encoding="utf-8")
    rel = path.relative_to(ROOT).as_posix()

    for pattern in (
        r"""\bid\s*=\s*["']([A-Za-z][A-Za-z0-9_:\-\.]*)["']""",
        r"""\.id\s*=\s*["']([A-Za-z][A-Za-z0-9_:\-\.]*)["']""",
        r"""setAttribute\(\s*["']id["']\s*,\s*["']([A-Za-z][A-Za-z0-9_:\-\.]*)["']\s*\)""",
    ):
        dynamic_web_ids.update(re.findall(pattern, text))

    ref_patterns = (
        r"""getElementById\(\s*["']([A-Za-z][A-Za-z0-9_:\-\.]*)["']\s*\)""",
        r"""\bqs\(\s*["']([A-Za-z][A-Za-z0-9_:\-\.]*)["']\s*\)""",
        r"""querySelector(?:All)?\(\s*["']#([A-Za-z][A-Za-z0-9_:\-\.]*)["']\s*\)""",
    )
    for pattern in ref_patterns:
        for name in re.findall(pattern, text):
            referenced_web_ids.setdefault(name, set()).add(rel)

declared_web_ids = set(html_id_locations) | dynamic_web_ids
dangling_web_ids = [
    {"id": name, "referenced_from": sorted(paths)}
    for name, paths in sorted(referenced_web_ids.items())
    if name not in declared_web_ids
]
duplicate_static_html_ids = [
    {"id": name, "declared_in": locations}
    for name, locations in sorted(html_id_locations.items())
    if len(locations) > 1
]

report = {
    "scanned_files": len(files),
    "dead_private_kotlin": private_kotlin,
    "orphan_kotlin_candidates": orphan_kotlin,
    "javascript_candidates": js_candidates,
    "php_candidates": php_candidates,
    "todo_fixme_markers": markers,
    "dangling_web_id_bindings": dangling_web_ids,
    "duplicate_static_html_ids": duplicate_static_html_ids,
}

print(json.dumps(report, indent=2, ensure_ascii=False))

parser = argparse.ArgumentParser()
parser.add_argument("--strict", action="store_true")
args = parser.parse_args()

strict_failures = []
if private_kotlin:
    strict_failures.append("dead private Kotlin symbols")
if dangling_web_ids:
    strict_failures.append("dangling Web ID bindings")
if duplicate_static_html_ids:
    strict_failures.append("duplicate static HTML IDs")

if args.strict and strict_failures:
    raise SystemExit(
        "Dead-code audit failed: " + ", ".join(strict_failures) + "."
    )
