<div align="center">

<img src="web/public/assets/brand/logo.svg" alt="RASPORED logo" width="112">

# RASPORED

### Smjene, evidencija sati i statistika — u jednoj modernoj aplikaciji.

**RASPORED** pretvara papirnate rasporede u pregledan digitalni kalendar, povezuje planirane smjene sa stvarno odrađenim vremenom i daje jasan mjesečni pregled rada na **Androidu** i kao **Web/PWA** aplikacija.

[![CI](https://github.com/bren-wp/RASPORED/actions/workflows/ci.yml/badge.svg)](https://github.com/bren-wp/RASPORED/actions/workflows/ci.yml)

**Android · Jetpack Compose · Kotlin · ML Kit OCR · Web/PWA · Playwright**

</div>

---

## Preuzimanja

Svako produkcijsko izdanje objavljuje gotove artefakte:

- **RASPORED.apk** — instalabilna Android aplikacija.
- **RASPORED-web-vX.Y.Z.zip** — Web/PWA paket spreman za upload na domenu, poddomenu ili poddirektorij.
- **SHA256SUMS-vX.Y.Z.txt** — kontrolne vrijednosti za provjeru preuzetih datoteka.

Verzija Android aplikacije i Web/PWA paketa uvijek se čita iz zajedničke datoteke <code>VERSION</code>. CI ne dopušta novo izdanje s već korištenom verzijom.

## Raspored bez tablica, papira i ručnog prepisivanja

RASPORED je napravljen za korisnika koji želi brzo vidjeti **kada radi, koju smjenu ima, koliko je stvarno odradio i kakav mu je saldo sati**.

Aplikacija spaja pet glavnih tokova u jedno sučelje:

- **Kalendar smjena** — D, N, GO i BO oznake kroz cijeli mjesec.
- **Skeniranje rasporeda** — kamera ili galerija; Android koristi on-device ML Kit OCR.
- **Evidencija sati** — ulaz, izlaz, bilješka, trajanje rada i mjesečna povijest.
- **Statistika** — dnevni/noćni sati, saldo, vikendi, blagdani i raspodjela po tjednima.
- **Android + Web/PWA** — isti vizualni identitet i ista semantika podataka na obje platforme.

## Stvarna aplikacija

> Slike ispod su **stvarni screenshotovi pokrenute Web/PWA aplikacije**, generirani automatski u GitHub Actions / Playwright QA procesu. Nisu mockupovi, renderi ni dizajnerske reference. Screenshotovi koriste izmišljene testne podatke i izmišljena imena; ne koriste stvarne osobe ni rasporede iz korisničkih fotografija.

<img src="docs/media/app-home-desktop.png" alt="RASPORED Web/PWA početna — stvarni screenshot aplikacije" width="100%">

### Mobilni prikaz

<table>
<tr>
<td width="33%" align="center"><b>Početna</b></td>
<td width="33%" align="center"><b>Skeniranje rasporeda</b></td>
<td width="33%" align="center"><b>Evidencija sati</b></td>
</tr>
<tr>
<td><img src="docs/media/app-home-mobile.png" alt="RASPORED mobilna početna"></td>
<td><img src="docs/media/app-scan-mobile.png" alt="RASPORED mobilno skeniranje"></td>
<td><img src="docs/media/app-hours-mobile.png" alt="RASPORED mobilna evidencija sati"></td>
</tr>
</table>

### Statistika rada

<img src="docs/media/app-stats-desktop.png" alt="RASPORED statistika — stvarni screenshot aplikacije" width="100%">

## Što RASPORED radi

| Funkcija | Što korisnik dobiva |
| --- | --- |
| **Mjesečni kalendar** | Brz pregled smjena po danima, vikendima i hrvatskim blagdanima. |
| **D / N / GO / BO model** | Jednostavna i konzistentna semantika smjena kroz cijelu aplikaciju. |
| **OCR na Androidu i Web/PWA** | Fotografija rasporeda → prepoznate osobe → izbor točno jedne osobe → provjera smjena → spremanje. |
| **Evidencija ulaza/izlaza** | Stvarno odrađeno vrijeme više nije isto što i planirano vrijeme. |
| **Saldo sati** | Razlika između planiranih i stvarno evidentiranih minuta. |
| **Noćni / vikend / blagdan sati** | Poseban pregled vremena odrađenog u relevantnim kategorijama. |
| **Tjedna statistika** | Vizualna raspodjela rada po tjednima u mjesecu. |
| **Dark mode** | Trajna Android postavka tamnog izgleda. |
| **Offline PWA osnova** | Web aplikacija registrira service worker i zadržava lokalno spremljene podatke. |
| **Responsive UI** | QA se provodi na 375, 390, tablet, 1440 i 1920 px viewportima. |

## Kako radi skeniranje

1. **Slikaj raspored** kamerom ili odaberi fotografiju iz galerije.
2. Android koristi **ML Kit OCR**, a Web/PWA browser OCR sloj.
3. Parser pronalazi imena i prezimena te oznake **D / N / GO / BO** po danima.
4. Ako fotografija sadrži više djelatnika, korisnik mora odabrati **točno jednu osobu**.
5. Prije spremanja moguće je ručno ispraviti prepoznate dane i smjene.
6. U kalendar se uvozi samo raspored odabrane osobe; ostali prepoznati redovi se ne spremaju.

Web OCR pri prvom korištenju može trebati internetsku vezu za učitavanje OCR modela. Spremljeni raspored i evidencija sati ostaju lokalni u pregledniku.

## Evidencija sati je odvojena od plana

Planirana smjena govori **što bi trebalo biti odrađeno**. Evidencija sati govori **što je stvarno odrađeno**.

RASPORED zato odvojeno vodi:

- vrijeme ulaza,
- vrijeme izlaza,
- trajanje rada,
- bilješku uz evidenciju,
- mjesečni zbroj,
- dnevne i noćne minute,
- vikend i blagdan minute,
- saldo u odnosu na plan.

Home i Statistika koriste stvarnu Evidenciju sati. Planirane smjene i stvarno odrađeno vrijeme vode se odvojeno.

## Brand

<table>
<tr>
<td width="50%" align="center">
<img src="web/public/assets/brand/logo.svg" alt="RASPORED glavni logo" width="150"><br>
<b>Glavni logo</b><br>
<sub>Kalendar + dan + noć</sub>
</td>
<td width="50%" align="center">
<img src="web/public/assets/brand/icon-maskable.svg" alt="RASPORED maskable PWA ikona" width="150"><br>
<b>PWA / maskable ikona</b><br>
<sub>Isti proizvodni vizualni identitet</sub>
</td>
</tr>
</table>

### Produkcijske ikonice

<img src="docs/media/icon-showcase.svg" alt="RASPORED produkcijske ikonice" width="100%">

Ikonice u aplikaciji nisu emoji ni privremeni Unicode placeholderi. Web koristi zajednički SVG sprite iz <code>web/public/assets/brand/icons.svg</code>, a Android koristi platformske vector / Compose ikone uz projektni launcher asset.

### Boje

| Token | Vrijednost | Namjena |
| --- | --- | --- |
| Midnight Navy | <code>#0B1F44</code> | navigacija, noćna smjena, brand |
| Electric Cyan | <code>#00C2FF</code> | primarna akcija, dnevna smjena |
| Teal | <code>#14B8A6</code> | slobodni dan / pozitivni statusi |
| Slate Gray | <code>#94A3B8</code> | sekundarni tekst |
| Background | <code>#F6F8FB</code> | svijetla površina aplikacije |
| Amber | <code>#F59E0B</code> | sunce / naglasci |
| Red | <code>#EF4444</code> | bolovanje / upozorenja |

## Platforme

### Android

- Kotlin
- Jetpack Compose
- Material 3
- ML Kit Text Recognition
- lokalna pohrana rasporeda i evidencije
- hrvatski fiksni i pomični blagdani
- funkcionalni dark mode
- Compose unit/lint provjere i stvarni emulator launch/navigation smoke test u CI-ju

### Web / PWA

- PHP entrypoint
- HTML + CSS + JavaScript
- responzivni layout bez framework ovisnosti u runtimeu
- PWA manifest + service worker
- lokalna pohrana rasporeda i evidencije
- Playwright funkcionalni i screenshot QA
- stvarni camera/gallery image-upload tok

## Privatnost i lokalni podaci

Trenutačni produkcijski sloj koristi lokalnu pohranu za osobni raspored i evidenciju sati:

- **Android:** aplikacijska lokalna pohrana.
- **Web/PWA:** browser local storage.
- **Android OCR:** obrada teksta preko ML Kit modela na uređaju.
- **Web OCR:** obrada fotografije odvija se u pregledniku; raspored se sprema lokalno tek nakon korisničke potvrde.

Projekt trenutačno nema potrebu predstavljati cloud račun ili centralni korisnički profil kao dovršenu funkciju.

## QA koji mora proći

Svaki ozbiljniji razvojni pass provjerava:

- PHP syntax,
- Playwright funkcionalne testove,
- screenshot capture,
- responsive Home,
- Calendar i Statistics navigaciju,
- stvarni image-upload tok,
- produkcijski Scan empty state,
- evidenciju ulaza/izlaza i perzistenciju,
- data-driven statistiku,
- Android debug APK,
- Android unit testove,
- Compose androidTest compile,
- Android lint.

### Viewporti

<code>375</code> · <code>390</code> · tablet · <code>1440</code> · <code>1920</code>

## Lokalno pokretanje Web/PWA aplikacije

<pre><code>php -S 127.0.0.1:8080 -t web/public</code></pre>

Aplikacija je dostupna na:

<pre><code>http://127.0.0.1:8080/</code></pre>

## Struktura repozitorija

<pre><code>RASPORED/
├── android/                 # Android / Jetpack Compose
├── web/                     # Web/PWA aplikacija
├── docs/
│   ├── design/              # dizajnerska pravila i mapiranje referenci
│   └── media/               # stvarni CI screenshotovi i brand media
└── .github/workflows/       # build, test i screenshot QA</code></pre>

## Produkcijski status

RASPORED je pripremljen kao **v1.0.0** aplikacija za Android i Web/PWA. Runtime ne sadrži demo raspored, fiksni razvojni datum ni hardkodirana imena korisnika. QA podaci postoje samo u automatiziranim testovima i ne ulaze u produkcijski UI.

Prije svake objave CI provjerava Android build/test/lint i Web/PWA funkcionalne, responzivne i screenshot testove.

---

<div align="center">

<img src="web/public/assets/brand/logo.svg" alt="" width="56">

**RASPORED**  
*Shift planner & evidencija sati*

</div>
