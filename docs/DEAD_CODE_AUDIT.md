# Dead-code audit

RASPORED uses a conservative static audit across production Kotlin, JavaScript and PHP sources.

## What is checked

- private Kotlin functions/properties that appear only at their declaration;
- top-level Kotlin classes/objects without another source reference (reported for manual review);
- named JavaScript and PHP functions referenced only once (reported as candidates);
- production `TODO` / `FIXME` markers;
- the standalone `/demo` module is included in the same audit.

The audit does **not** automatically delete framework entry points or symbols that may be reached through Android manifests, reflection, PHP routing, DOM data attributes or external requests.

## Release rule

CI runs:

```bash
python3 scripts/dead_code_audit.py --strict
```

A release is blocked when a private Kotlin symbol is provably unreferenced. JS/PHP and top-level Kotlin candidates are printed for manual review so dynamic code is not deleted incorrectly.

## Current cleanup

The calendar redesign removed the obsolete quick-paint/multi-select flow and the dead `weeklyHours()` helper. Profile name, registration and login UI were removed from Android Settings. Further candidates found by CI are reviewed and removed before release.
