# RASPORED — dizajnerske reference

Priložene slike su glavni vizualni source of truth. Ne smiju se koristiti kao bitmap UI niti kao runtime dependency.

## Mapiranje referenci

| Datoteka reference | Uloga |
|---|---|
| C60E4119-A213-46F5-AE0A-9D5149764962.jpeg | Web desktop dashboard + mobilni kalendar |
| FF22BE20-A7A8-4388-8E94-93BF0A024A01.jpeg | Android — Statistika |
| 3777D297-1936-4EAA-AEF5-DE386625B102.jpeg | Android — Skeniraj raspored / OCR pregled |
| 172645F9-50E0-4FC5-A041-EB989F643BA3.jpeg | Android — Kalendar |
| 2E3EFCF3-0DD6-4E4B-A438-CC09B6E68274.jpeg | Android — Početna |
| 7A1A1C11-CA90-4677-A5E0-95D91D0B846B.jpeg | Brand board, paleta, tipografija, ikone i više UI primjera |

## Zajednički zaključci iz svih referenci

- Navy brand header, bijele/light kartice, vrlo lagan border/shadow.
- Veliki radius kartica i kontrola; bez glassmorphisma i agresivnih gradijenata.
- Inter tipografija, snažna hijerarhija naslova i numeričkih vrijednosti.
- Mobilno: kompaktni brand header i bottom navigation s naglašenim centralnim Scan actionom.
- Desktop: lijevi sidebar, glavni kalendar i desni summary panel.
- D/N/GO/BO nikada nisu preneseni samo bojom: oznaka i tekst ostaju vidljivi.
- Layout se rekonstruira po viewportu; desktop se ne smanjuje mehanički na mobilni ekran.

## Design tokeni

```text
navy        #0B1F44
cyan        #00C2FF
teal        #14B8A6
slate       #94A3B8
background  #F6F8FB
amber       #F59E0B
red         #EF4444
```

Radijusi: 12 / 16 / 20 / 24 px.  
Kontrole: 44–52 px touch target.  
Desktop content gap: 20–24 px.  
Mobile content margin: 16 px.  
Card shadow: vrlo diskretan, hladni navy tint.

## QA pravilo

Reference se uspoređuju sa stvarnim buildom. README smije prikazivati samo screenshotove stvarne aplikacije iz `docs/screenshots/`. Reference mogu biti pohranjene u `docs/design/reference/` kada se binarni asseti dodaju u repozitorij.
