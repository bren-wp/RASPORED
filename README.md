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

- **RASPORED.apk** — uvijek instalabilni Android artefakt. Bez dodatne konfiguracije release koristi standardni debug potpis; ako je upload signing opcionalno konfiguriran, objavljuje se release-potpisani APK.
- **RASPORED.aab** — release Android App Bundle. GitHub Secrets nisu uvjet za izradu ili objavu; opcionalni upload signing koristi se samo kada su sva četiri signing podatka već konfigurirana.
- **raspored_demo.apk** — zasebna demonstracijska Android aplikacija iz korijenske mape `/demo`, s interaktivnim kalendarom i unosom oznake klikom na dan.
- **RASPORED-web-vX.Y.Z.zip** — Web/PWA paket spreman za upload na domenu, poddomenu ili poddirektorij.
- **SHA256SUMS** — SHA-256 kontrolne vrijednosti za glavni APK, AAB, `raspored_demo.apk` i Web/PWA paket; release ih provjerava prije objave.

Verzija Android aplikacije i Web/PWA paketa uvijek se čita iz zajedničke datoteke <code>VERSION</code>. CI ne dopušta novo izdanje s već korištenom verzijom.

GitHub Secrets nisu obavezni za release. Ako su sva četiri opcionalna signing secreta (`ANDROID_UPLOAD_KEYSTORE_BASE64`, `ANDROID_UPLOAD_STORE_PASSWORD`, `ANDROID_UPLOAD_KEY_ALIAS`, `ANDROID_UPLOAD_KEY_PASSWORD`) dostupna, workflow koristi trajni upload potpis. Ako nisu dostupna ili je konfiguracija nepotpuna, workflow se ne ruši: objavljuje instalabilni debug-signed APK i release AAB bez spremanja privatnog ključa u repozitorij.

## Raspored bez tablica, papira i ručnog prepisivanja

RASPORED je napravljen za korisnika koji želi brzo vidjeti **kada radi, koju smjenu ima, koliko je stvarno odradio i kakav mu je saldo sati**.

Aplikacija spaja pet glavnih tokova u jedno sučelje:

- **Kalendar smjena** — glavni početni ekran i najveći dio Android sučelja. Mjesečna 7-stupčana mreža koristi velike ćelije; klik na ćeliju otvara unos oznake baš za taj datum. U istom dijalogu moguće je odabrati D, N, GO, BO, PD ili SD, upisati vlastitu oznaku do 8 znakova ili očistiti dan. Stari blok brzog unosa i višestrukog odabira ispod kalendara uklonjen je u potpunosti.
- **Skeniranje rasporeda** — kamera ili galerija; Android koristi on-device ML Kit OCR.
- **Evidencija sati** — ulaz, izlaz, bilješka, trajanje rada i mjesečna povijest.
- **Statistika** — dnevni/noćni sati, saldo, vikendi, blagdani i raspodjela po tjednima.
- **Android + Web/PWA** — isti vizualni identitet i ista semantika podataka na obje platforme.

## Stvarna Android aplikacija

> Slike ispod su **stvarni screenshotovi pokrenute Android aplikacije** snimljeni na API 36 emulatoru tijekom GitHub Actions instrumentation QA procesa. Nisu mockupovi, renderi ni screenshotovi Web/PWA stranice. Prikazani podaci nastaju isključivo kroz testni scenarij.

<table>
<tr>
<td width="50%" align="center"><b>Mjesečni kalendar</b></td>
<td width="50%" align="center"><b>Skeniranje rasporeda</b></td>
</tr>
<tr>
<td><img src="docs/media/android-calendar.png" alt="RASPORED Android mjesečni kalendar — stvarni emulator screenshot"></td>
<td><img src="docs/media/android-scan.png" alt="RASPORED Android skeniranje rasporeda — stvarni emulator screenshot"></td>
</tr>
</table>

## Što RASPORED radi

| Funkcija | Što korisnik dobiva |
| --- | --- |
| **Mjesečni kalendar** | Početni Android ekran s velikom 7-stupčanom mrežom preko gotovo cijelog dostupnog prostora. Klik na dan otvara uređivanje te ćelije: standardna ili vlastita kratka oznaka do 8 slova/brojeva, uz mogućnost brisanja. |
| **D / N / GO / BO / PD / SD model** | Jednostavna i konzistentna semantika smjena, dopusta, bolovanja i slobodnog dana kroz cijelu aplikaciju. |
| **OCR na Androidu i Web/PWA** | Cijela fotografija rasporeda obrađuje se u više prolaza. Uz puni kadar koriste se detekcija tablice, stvarne horizontalne linije mreže, točni pojasevi redaka zaposlenika, preklapajući pojasevi, zasebni roster prolazi i fokusirani recovery tileovi. Za guste 27–31 redne tablice sustav može raditi i završni OCR **redak po redak** uz izvorno zaglavlje dana te blokira očito nepotpun uvoz kada geometrija tablice pokazuje više djelatnika nego što je OCR pouzdano pročitao. |
| **Evidencija ulaza/izlaza** | Stvarno odrađeno vrijeme više nije isto što i planirano vrijeme. |
| **Saldo sati** | Razlika između planiranih i stvarno evidentiranih minuta. |
| **Noćni / vikend / blagdan sati** | Poseban pregled vremena odrađenog u relevantnim kategorijama. |
| **Tjedna statistika** | Vizualna raspodjela rada po tjednima u mjesecu. |
| **Dark mode** | Trajna Android postavka tamnog izgleda. |
| **PWA app shell** | Web aplikacija registrira service worker za UI assete; podatkovni API ostaje network-only kako se osobni JSON ne bi spremao u cache. |
| **Okvirna plaća** | Android i Web/PWA koriste provjerljive 2026 parametre gdje postoje; lokalno uređeni i privatni sektor imaju ručni način bez izmišljanja osnovice, koeficijenta ili dodataka. |
| **Korisnički račun** | Android Postavke više nemaju ime/prezime, registraciju ni prijavu; osnovni Android rad je lokalni. Web/PWA zadržava vlastiti opcionalni račun za svoje mrežne funkcije. Postojeća Android šifrirana sesija može se samo validirati radi kompatibilnosti sa starijim instalacijama. |
| **Izvoz** | Android generira stvarni mjesečni PDF; Web/PWA podržava JSON sigurnosnu kopiju i pregled za ispis / spremanje kao PDF. |
| **Responsive UI** | QA se provodi na 375, 390, tablet, 1440 i 1920 px viewportima. |

## Kako radi skeniranje

1. **Slikaj raspored** kamerom ili odaberi fotografiju iz galerije.
2. Android koristi **ML Kit OCR**, a Web/PWA browser OCR sloj.
3. Parser traži cijelo zaglavlje 1–28/29/30/31, numerirane retke osoba i oznake **D / N / GO / BO / PD / SD**. U dokazanoj ćeliji kalendarske mreže čuva i kratke oznake specifične radnom mjestu (npr. **J, S, P1, 1, 2, 3**) bez izmišljanja njihova značenja. Kod gustih tablica koristi dodatne preklapajuće high-resolution prolaze i korekciju perspektive po retku.
4. U osobni kalendar uvozi se **točno jedna odabrana osoba**. Za lokalni OCR nije potreban račun. Ako je puni raspored gust, OCR koristi broj retka kao primarni identitet i dodatne roster prolaze za ime kako ne bi stvarao duplikate ili gubio smjene.
5. Android bez registracije može iz istog skeniranja spremiti više prepoznatih djelatnika kao **odvojene lokalne rasporede tima**. Registrirani korisnik može dodatno pokrenuti **AI provjeru cijelog rasporeda**; AI rezultat dopunjava nedostajuće podatke, a svaka nesuglasica lokalnog OCR-a i AI-ja ostaje zasebna konfliktna ćelija za ciljanu ručnu odluku. Rasporedi se nikada ne spajaju među osobama.
6. Prije spremanja moguće je ručno ispraviti svaki dan i oznaku; bez pouzdane geometrije stupaca aplikacija traži ponovno skeniranje umjesto tihog pomicanja dana ulijevo ili udesno.

Web OCR pri prvom korištenju može trebati internetsku vezu za učitavanje OCR modela. Fotografija se obrađuje u pregledniku, a potvrđeni raspored i evidencija spremaju se kroz isti-origin PHP API u per-instalacijski JSON pod `storage/data`.

### Opcionalna AI provjera rasporeda

AI provjera je **drugi, opt-in sloj**, a ne zamjena za lokalni OCR i korisnički review. Aktivira se samo za prijavljenog korisnika. Web i Android šalju odabranu fotografiju na `/api/ai-ocr.php`; backend čita `OPENAI_API_KEY` iz environmenta i koristi OpenAI Responses API s image inputom i strukturiranim JSON izlazom. Ključ se nikada ne šalje pregledniku niti se ugrađuje u APK.

Za guste tablice AI mora pokušati vratiti sve numerirane redove i točne stupce dana 1–31. Standardne oznake D/N/GO/BO/PD/SD normaliziraju se, uključujući OCR zamjene G0→GO i B0→BO, a kratke ustanovne oznake (npr. J, S, P1 ili 1/2/3) čuvaju se bez izmišljanja značenja. Lokalni OCR i AI rezultat uspoređuju se po retku i danu. Konfliktna ćelija čuva lokalnu i AI oznaku te se ne prepisuje automatski; korisnik može odabrati lokalnu vrijednost, AI vrijednost, prazno ili drugu kratku oznaku. Smart review vodi samo kroz neriješene konflikte.

Produkcijski server konfigurira `OPENAI_API_KEY` i opcionalno `OPENAI_RASPORED_MODEL`; bez ključa aplikacija nastavlja normalno raditi lokalnim OCR-om.

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

## Oznake rasporeda i Evidencija sati

RASPORED razlikuje planirani raspored od stvarne Evidencije sati. Podržane semantičke oznake rasporeda su **D** (dnevna smjena), **N** (noćna smjena), **GO** (godišnji odmor), **BO** (bolovanje), **PD** (plaćeni dopust) i **SD** (odobreni slobodan dan). **Prazna ćelija nije SD**: ostaje prazna i znači redovni slobodni dan. OCR zato nikada ne smije sam pretvarati praznu kućicu u SD niti pomaknuti kasniju oznaku na raniji datum. Kratke oznake koje ustanova koristi, a RASPORED im nema potvrđenu semantiku, čuvaju se kao vlastite oznake umjesto da se odbace ili pogrešno prevedu. OCR review zadržava točan dan/stupac i dopušta ručnu korekciju prije spremanja.

Blagdan je svojstvo datuma, a ne posebna oznaka rasporeda. Prazna ćelija na blagdan ostaje prazna i prikazuje se kao blagdan/neradni dan; aplikacija ne dodaje +150 % dodatka ako na taj datum nema stvarno odrađenog rada. Za javne službe TKU predviđa pravo na naknadu plaće kada zaposlenik ne radi zbog državnog blagdana ili neradnog dana, pa taj slučaj ne pretvaramo u BO, SD ili izmišljenu smjenu.

Evidencija sati dodatno razlikuje **redovni rad, 1./2./3. smjenu, turnus, dežurstvo, pripravnost, rad po pozivu i drugi oblik rada**. Trajanje uvijek dolazi iz stvarnog ulaza/izlaza; posebna naknada ne pretpostavlja se ako za nju nema provjerljivog pravila.

## Okvirna plaća — javni sektor i ručni način za ostale poslodavce

Android i Web/PWA imaju zaseban kalkulator **okvirne plaće**, ali kalendar, raspored i evidencija sati ostaju primarna funkcija RASPORED-a. Odabir je organiziran kao **županija ustanove → grad/općina prebivališta i porezne stope → sektor → ustanova → radno mjesto**.

Za 2026. ugrađene su službene osnovice javnih i državnih službi po razdobljima te provjerljivi koeficijenti za odabrana radna mjesta u zdravstvu, školstvu, policiji i profesionalnom vatrogastvu. Web katalog sadrži aktualni popis bolničkih zdravstvenih ustanova Ministarstva zdravstva, a posebna pravila pojedine ustanove ne primjenjuju se na druge ustanove bez provjerljivog izvora.

Vrtići, lokalna i regionalna uprava, privatni poslodavci te drugi slučajevi gdje ne postoji jedna službena državna osnovica/koeficijent koriste **ručni unos**. Privatni sektor je zato dostupan kao zaseban ručni režim: korisnik unosi poznate ugovorene parametre, a aplikacija ne izmišlja nacionalnu vrijednost. Ako radno mjesto nije u katalogu, postoji **Drugo / ručni unos**.

Procjena koristi stvarnu Evidenciju sati za noćni, subotnji, nedjeljni, blagdanski i okvirni prekovremeni rad tamo gdje je stopa za odabrani režim provjerena. Okvirni neto koristi uneseni osobni odbitak i stope grada/općine prebivališta; porez se ne veže uz županiju poslodavca. Prikaz “po radnom danu” samo je prosjek plaće po evidentiranom radnom danu i **nije službena dnevnica za službeni put**.

Kalkulator je pomoćni informativni sloj, ne obračunska isprava. Bolovanje, godišnji odmor po prosjeku, pripravnost, dežurstva, posebni uvjeti rada, neoporezivi primici, prijevoz, obustave i individualna porezna prava ne dodaju se bez odgovarajućeg podatka ili provjerljivog pravila.

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

## Demo Android aplikacija

Mapa `/demo` je zaseban Android application modul (`hr.raspored.demo`) koji demonstrira glavni kalendarski UX bez korisničkih podataka. Demo koristi veliki mjesečni kalendar i isti model **klik na dan → odaberi ili upiši oznaku**. Svaki release objavljuje ga kao **`raspored_demo.apk`** i uključuje u `SHA256SUMS`.

## Dead-code audit

Produkcijski source tree prolazi automatski audit iz `scripts/dead_code_audit.py`. CI blokira dokazano neiskorištene privatne Kotlin simbole, a JS/PHP i top-level Kotlin kandidate ispisuje za ručni pregled kako se dinamički entry pointovi ne bi brisali napamet. Trenutni cleanup uklonio je stari calendar paint/multi-select kod, `weeklyHours()`, `ProfileStore`, neiskorišteni Web helper i neiskorištene PHP wrapper funkcije. Detalji su u `docs/DEAD_CODE_AUDIT.md`.

## Platforme

### Android

- Kotlin
- Jetpack Compose
- Material 3
- Android 16 / targetSdk 36 za aktualni Google Play zahtjev
- ML Kit Text Recognition
- lokalna pohrana rasporeda i evidencije
- okvirna bruto/neto procjena uz službene javne presete i ručni način za ostale sektore
- hrvatski fiksni i pomični blagdani
- funkcionalni dark mode
- mobilna primarna navigacija: Kalendar, Evidencija, istaknuti Skeniraj, Statistika i Više
- Keystore-backed kompatibilnost za ranije povezane Android sesije uz provjeru isteka/opoziva; novi login/registracija UI je uklonjen
- stvarni mjesečni PDF izvoz rasporeda/evidencije bez profila ime/prezime u Postavkama
- debug APK + release AAB + zasebni `raspored_demo.apk` build provjera
- Compose unit/lint provjere i stvarni API 36 emulator launch/navigation smoke test u CI-ju

### Web / PWA

- tanki PHP entrypoint + zasebni viewovi, bootstrap, API i JS moduli
- HTML + CSS + JavaScript
- responzivni layout bez framework ovisnosti u runtimeu
- PWA manifest + service worker
- gostujući per-instalacijski JSON podaci u `storage/data` iza zaštićenog PHP API-ja
- opcionalna registracija/prijava ostaje Web/PWA funkcija; Android Postavke više ne nude registraciju/prijavu. Lozinke koriste `password_hash`, a Web koristi HttpOnly/SameSite session cookie.
- Web račun koristi zaseban privatni per-account JSON pod `storage/data`
- Web voditeljski profil može spremiti više djelatnika kao odvojene rasporede tima
- JSON sigurnosna kopija i mjesečni pregled za ispis / spremanje kao PDF
- jednokratna migracija starog browser storagea u JSON spremište
- okvirna bruto/neto procjena uz službene javne presete i ručni način za ostale sektore
- Playwright funkcionalni i screenshot QA
- stvarni camera/gallery image-upload tok

## Podrška

- WhatsApp: **+385 91 901 0092**
- E-mail: **info@raspored.eu**
- Developer: **Brendigo Studio** — brendigo.com

## Privatnost i podaci

- **Android:** kalendar, raspored, ML Kit OCR, evidencija, statistika, procjena plaće i PDF izvoz rade lokalno bez registracije. Postavke nemaju ime/prezime, prijavu ni registraciju. Postojeći šifrirani token iz ranijih verzija može se validirati radi kompatibilnosti, ali lokalne funkcije ne ovise o mreži.
- **Android pohrana rasporeda:** osobna povijest rasporeda primarno se čuva u SQLite bazi bez automatskog brisanja starih mjeseci. Pri nadogradnji se postojeći `SharedPreferences` raspored transakcijski prenosi u bazu, uz kompatibilni write-through mirror kako prekid nadogradnje ili povratak na stariju verziju ne bi ostavio podatke nedostupnima.
- **Android povijest evidencije:** evidencija vremena više nema automatski limit od 3000 zapisa; višegodišnja povijest ostaje sačuvana dok je korisnik sam ne ukloni.
- **Web/PWA:** gostujući način koristi per-instalacijski JSON vezan uz nasumični HttpOnly identifikator. Registrirani korisnik koristi zaseban JSON vezan uz nasumični ID računa; e-mail se ne koristi kao naziv datoteke.
- **Računi:** Web lozinke se ne spremaju u čistom tekstu; koriste PHP `password_hash` / `password_verify` i HttpOnly/SameSite session cookie. Android više nema account UI; eventualni token iz ranije verzije ostaje u Keystore-backed šifriranoj pohrani samo radi kompatibilnosti i briše se kada je istekao ili opozvan.
- **Zaštita Web spremišta:** runtime prvenstveno sprema JSON u privatni direktorij izvan document root-a; put se može eksplicitno zadati s `RASPORED_STORAGE_DIR`. `storage/.htaccess` ostaje kompatibilni fallback za Apache. Zapis ide kroz API s validacijom, sanitizacijom, ograničenjem veličine, zaključavanjem i atomskim zapisom.
- **Android OCR:** primarno lokalna obrada teksta preko ML Kit modela na uređaju. Ako prijavljeni korisnik izričito pokrene AI provjeru, fotografija se šalje preko RASPORED HTTPS backenda OpenAI Responses API-ju; OpenAI API ključ nije ugrađen u APK.
- **Web OCR:** primarno se obrađuje u pregledniku; sama fotografija ne zapisuje se u `storage/data`. Registrirani korisnik može izričito pokrenuti AI provjeru preko server-side endpointa koji čita `OPENAI_API_KEY` iz okoline, validira MIME, veličinu, dimenzije i broj piksela, primjenjuje account/IP rate limit i šalje `store:false`. Privremeni upload uklanja se prije poziva AI servisu.
- **Legacy migracija:** postojeći podaci iz starog `localStorage/sessionStorage` modela mogu se jednokratno prenijeti u JSON spremište, nakon čega se stari ključevi brišu.

Registracija nije potrebna za osnovni rad. Android više nema registraciju/prijavu u Postavkama; Web/PWA može koristiti vlastiti opcionalni račun. Nijedan API ključ ne smije biti hardkodiran u JavaScript, APK, repozitorij ili release artefakt.

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
- JSON storage API, migraciju i zaštitu zapisa,
- okvirnu plaću i perzistenciju odabranog radnog mjesta,
- Android debug APK,
- Android release AAB,
- Android unit testove,
- Compose androidTest compile,
- Android debug + release lint,
- `/demo` Android build i `raspored_demo.apk` artifact provjeru,
- repo-wide `scripts/dead_code_audit.py --strict` dead-code audit,
- API 36 emulator launch/navigation smoke test,
- zasebnu provjeru postojanja i ZIP integriteta APK/AAB/Web artefakata prije releasea.
- release workflow uvijek provjerava ZIP integritet i SHA-256; provjeru APK/AAB potpisa izvršava kada je opcionalni release signing konfiguriran.

### Viewporti

<code>375</code> · <code>390</code> · tablet · <code>1440</code> · <code>1920</code>

## Lokalno pokretanje Web/PWA aplikacije

<pre><code>php -S 127.0.0.1:8080 -t web/public</code></pre>

Aplikacija je dostupna na:

<pre><code>http://127.0.0.1:8080/</code></pre>

Kod rada iz repozitorija API zapisuje JSON u <code>web/storage/data/</code>. Produkcijski ZIP sadrži <code>storage/</code> uz javne datoteke aplikacije, pa hosting mora PHP procesu dopustiti zapis u <code>storage/data</code>, dok izravni HTTP pristup toj mapi mora ostati blokiran.

## Struktura repozitorija

<pre><code>RASPORED/
├── android/                 # Android / Jetpack Compose
├── web/                     # Web/PWA aplikacija
│   ├── public/              # entrypoint, views, API, CSS/JS i PWA asseti
│   └── storage/data/        # runtime JSON (nije dio source commita)
├── docs/
│   ├── design/              # dizajnerska pravila i mapiranje referenci
│   └── media/               # stvarni CI screenshotovi i brand media
└── .github/workflows/       # build, test i screenshot QA</code></pre>

## Produkcijski status

RASPORED je pripremljen kao **v1.0.14** aplikacija za Android i Web/PWA. Runtime ne sadrži demo raspored, fiksni razvojni datum ni hardkodirana imena korisnika. QA podaci postoje samo u automatiziranim testovima i ne ulaze u produkcijski UI.

Prije svake objave CI provjerava Android build/test/lint i Web/PWA funkcionalne, responzivne i screenshot testove.

---

<div align="center">

<img src="web/public/assets/brand/logo.svg" alt="" width="56">

**RASPORED**  
*Shift planner & evidencija sati*

</div>
