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

Web screenshot testovi koriste Playwright na 375, 390, tablet, 1440 i 1920 px, uz funkcionalne responsive smoke testove i provjeru stvarnog image-upload toka na Scan ekranu. Android CI sada provjerava debug build, kompilaciju Compose androidTestova i lint. Referentni demo podaci uključeni su samo u Android debug buildu; release put koristi stvarni datum i ne hardkodira referentni mjesec. Baseline screenshotovi se ne prihvaćaju automatski: svaka namjerna promjena mora se pregledati prije ažuriranja baselinea.


## Trenutačni UI pass

Mobilni Web/PWA početni ekran više nije smanjeni desktop kalendar: ima zasebnu kompoziciju s današnjom smjenom, sljedećom smjenom, D/N/GO/BO oznakama, četiri mjesečne metrike i glavnim Scan CTA-om kao na mobilnoj referenci. Web koristi zajednički SVG icon set umjesto zamjenskih Unicode znakova. Scan ekran prihvaća stvarnu fotografiju s kamere ili galerije, validira vrstu i veličinu datoteke te prikazuje loading/success OCR stanje bez lažnog tvrdjenja da je backend OCR već dovršen.


## Trenutačni funkcionalni sloj

- Android Scan koristi kameru ili galeriju, on-device ML Kit OCR, odabir prepoznatog retka te korekciju D/N/GO/BO oznaka prije spremanja.
- Android lokalno sprema raspored po datumu; Početna, Kalendar i Statistika koriste spremljene podatke.
- Android kalendar podržava promjenu mjeseca, odabir dana i hrvatske fiksne/pomične blagdane.
- Android Statistika računa period, trend, donut i tjedne vrijednosti iz rasporeda umjesto fiksnih referentnih brojki.
- Android i Web/PWA imaju trajnu evidenciju ulaza/izlaza, bilješke i trajanja rada.
- Android tamni način i postavka smanjenih animacija trajno se spremaju.
- Web Scan prikazuje i validira stvarnu odabranu fotografiju; učitana fotografija se ne predstavlja kao završen OCR rezultat.

Prije prvog releasea ostaju dodatni pixel-precision i screenshot-regression prolazi te završna produkcijska Web OCR integracija.
