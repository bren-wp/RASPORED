# Takto — dizajnerske reference

Generirane Takto reference iz razgovora su vizualni source of truth za korisničko sučelje. Ne koriste se kao bitmap UI niti kao runtime dependency; izgled se rekonstruira stvarnim Compose/Web komponentama.

## Vizualni smjer

- Primarna tema je **tamna**: vrlo tamni navy/ink background, nešto svjetlije površine kartica i jasan bijeli tekst.
- Brand koristi kompaktni **T** znak s prijelazom **teal → plava → ljubičasta** i naziv **Takto**.
- Slogan: **„Dodirni. Označi. Radi.”**
- Kalendar je vizualno najvažniji radni ekran: velike ćelije, kratke oznake i dovoljno visok kontrast za brzo skeniranje.
- Početna služi kao pregled: pozdrav prema dobu dana, tjedni raspored, brze akcije, mjesečni sažeci i pristup kalendaru.
- Statistika koristi velike numeričke vrijednosti, jednostavne grafikone i kartice s jasnom hijerarhijom.
- Postavke grupiraju personalizaciju, izvoz, podršku i informacije o aplikaciji.
- Svijetli način ostaje dostupan, ali nije primarni Takto izgled.

## Navigacija

Mobilna primarna navigacija:

1. Početna
2. Kalendar
3. Skeniraj
4. Statistika
5. Više

Evidencija sati i okvirna plaća ostaju dostupne kao brze akcije i iz pripadajućih ekrana, bez pretrpavanja donje navigacije.

Desktop/Web koristi lijevi sidebar s istim informacijskim rasporedom.

## Kodovi rasporeda

Kod uvijek ostaje vidljiv tekstom; boja je dodatna informacija, nikad jedini signal.

| Kod | Značenje | Takto boja |
|---|---|---|
| D | Dan / dnevna smjena | plava `#147DF5` |
| N | Noć / noćna smjena | ljubičasta `#6D28D9` |
| GO | Godišnji odmor | teal/zelena `#0CC58E` |
| BO | Bolovanje | jantarna `#FFB51F` |
| PD | Plaćeni dopust | koraljno crvena `#E53648` |
| SD | Slobodan dan | slate `#20314A` |

Prazna ćelija ostaje prazna i ne smije se automatski pretvarati u SD.

## Design tokeni

- background `#07111F`
- surface `#0B1A2C`
- surface-2 `#10253D`
- border `#1B3553`
- text `#F7FAFF`
- muted `#A9B7CB`
- blue `#0A8CFF`
- purple `#8B5CF6`
- teal `#12D6A0`
- amber `#FFB51F`
- red `#FF4655`

Radijusi: 12 / 16 / 20 / 24 px.  
Touch target: najmanje 44–48 px.  
Mobile content margin: 12–16 px.  
Kartice: diskretan border, bez teškog glassmorphisma.  
Gradijenti: samo na brandu i primarnim CTA elementima.

## UX pravila

- Pozdrav se računa prema lokalnom dobu dana: **Dobro jutro / Dobar dan / Dobra večer**.
- Korisnik mora do kalendara i unosa smjene doći u najviše jednom dodiru s Početne.
- Kalendar mora ostati čitljiv na 375 px širine bez horizontalnog scrolla.
- Velike količine podataka moraju ostati skenirljive: kratke oznake, sekundarni tekst i jasni vizualni razmaci.
- Nema demo/dev teksta u produkcijskom runtimeu.
- Skeniranje, spremanje i izvoz moraju imati vidljiv status i grešku koja objašnjava sljedeći korak.

## QA pravilo

Reference se uspoređuju sa stvarnim buildom. README smije prikazivati samo screenshotove stvarne aplikacije snimljene u CI-ju ili na emulatoru, ne promotivne mockupove.
