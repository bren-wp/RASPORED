# RASPORED

**Shift planner & evidencija sati** — Android i Web/PWA aplikacija za rasporede smjena, evidenciju sati, statistiku i skeniranje papirnatih rasporeda.

## Status

Projekt je u aktivnoj implementaciji. Priložene dizajnerske reference su vizualni source of truth, ali **nisu** predstavljene kao screenshotovi stvarne aplikacije. Prvi release se neće objaviti dok glavni Android i Web/PWA ekrani ne prođu funkcionalni i vizualni QA.

## Struktura

- `android/` — Kotlin + Jetpack Compose Android aplikacija
- `web/` — responzivni HTML/CSS/JS/PHP/PWA
- `docs/design/DESIGN_REFERENCE.md` — mapiranje i pravila dizajnerskih referenci
- `.github/workflows/ci.yml` — web, Android i screenshot QA

## Design system

Zajednički tokeni:

- Midnight Navy: `#0B1F44`
- Electric Cyan: `#00C2FF`
- Teal: `#14B8A6`
- Slate Gray: `#94A3B8`
- Background: `#F6F8FB`
- Amber: `#F59E0B`
- Red: `#EF4444`
- Typography: Inter, uz platformski fallback dok se lokalni font asset ne doda u build

Semantika smjena je ista na obje platforme: D = cyan, N = navy/indigo, GO = teal/mint, BO = red/pink.

## Lokalno pokretanje Web/PWA

```bash
php -S 127.0.0.1:8080 -t web/public
```

Za vizualne testne podatke otvoriti `/?demo=1`. Bez `demo=1` aplikacija ne hardkodira referentne statistike.

## QA

Web screenshot testovi koriste Playwright na 375, 390, tablet, 1440 i 1920 px. Android ima Compose testnu osnovu za glavne ekrane. Baseline screenshotovi se ne prihvaćaju automatski: svaka namjerna promjena mora se pregledati prije ažuriranja baselinea.
