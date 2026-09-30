<?php
declare(strict_types=1);
header('Content-Type: text/html; charset=utf-8');
header('X-Content-Type-Options: nosniff');
header('Referrer-Policy: strict-origin-when-cross-origin');
header('Permissions-Policy: camera=(self), microphone=(), geolocation=()');
header('X-Frame-Options: DENY');
header("Content-Security-Policy: default-src 'self'; base-uri 'self'; object-src 'none'; frame-ancestors 'none'; form-action 'self'; img-src 'self' data: blob:; style-src 'self' 'unsafe-inline'; script-src 'self' https://cdn.jsdelivr.net 'wasm-unsafe-eval' 'unsafe-eval'; connect-src 'self' https://cdn.jsdelivr.net https://tessdata.projectnaptha.com; worker-src 'self' blob: https://cdn.jsdelivr.net; font-src 'self' data:");
$base = rtrim(str_replace('\\', '/', dirname($_SERVER['SCRIPT_NAME'] ?? '/')), '/');
if ($base === '.') { $base = ''; }
$version = trim((string) @file_get_contents(__DIR__ . '/version.txt'));
if (!preg_match('/^\d+\.\d+\.\d+$/', $version)) { $version = '1.0.1'; }
?>
<!doctype html>
<html lang="hr" data-theme="light">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
<meta name="theme-color" content="#0B1F44">
<title>RASPORED — Shift planner & evidencija sati</title>
<meta name="description" content="Planiranje smjena, evidencija sati, statistika i skeniranje rasporeda.">
<link rel="manifest" href="<?= htmlspecialchars(($base ?: '') . '/manifest.webmanifest', ENT_QUOTES) ?>">
<link rel="icon" href="<?= htmlspecialchars(($base ?: '') . '/assets/brand/logo.svg', ENT_QUOTES) ?>" type="image/svg+xml">
<link rel="stylesheet" href="<?= htmlspecialchars(($base ?: '') . '/assets/css/app.css', ENT_QUOTES) ?>">
</head>
<body data-base="<?= htmlspecialchars($base, ENT_QUOTES) ?>">
<div class="app-shell">
  <aside class="sidebar" aria-label="Glavna navigacija">
    <a class="brand brand--sidebar" href="#" data-route="home" aria-label="RASPORED početna">
      <img src="assets/brand/logo.svg" alt="" width="52" height="52">
      <span><strong>RASPORED</strong><small>Shift planner & evidencija sati</small></span>
    </a>
    <nav class="side-nav">
      <button class="nav-item is-active" data-route="home"><svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-home"></use></svg>Početna</button>
      <button class="nav-item" data-route="calendar"><svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-calendar"></use></svg>Kalendar</button>
      <button class="nav-item" data-route="scan"><svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-camera"></use></svg>Skeniraj</button>
      <button class="nav-item" data-route="stats"><svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-chart"></use></svg>Statistika</button>
      <button class="nav-item desktop-extra" data-route="hours"><svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-clock"></use></svg>Evidencija sati</button>
      <button class="nav-item desktop-extra" data-route="colleagues"><svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-users"></use></svg>Kolege</button>
      <button class="nav-item" data-route="settings"><svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-settings"></use></svg>Postavke</button>
    </nav>
    <div class="side-version"><img src="assets/brand/logo.svg" alt="" width="36"><span>RASPORED<small>v<?= htmlspecialchars($version, ENT_QUOTES) ?></small></span></div>
  </aside>

  <main class="main">
    <header class="topbar">
      <a class="brand brand--mobile" href="#" data-route="home"><img src="assets/brand/logo.svg" alt="" width="42"><span><strong>RASPORED</strong><small>Shift planner & evidencija sati</small></span></a>
      <div class="top-actions">
        <button class="icon-btn mobile-header-action" id="mobileScanHeaderBtn" data-route="scan" aria-label="Skeniraj raspored"><svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-scan"></use></svg></button>
        <button class="icon-btn mobile-header-action" id="statsRefreshBtn" aria-label="Osvježi statistiku"><svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-refresh"></use></svg></button>
        <button class="icon-btn" id="searchBtn" aria-label="Pretraži"><svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-search"></use></svg></button>
        <button class="icon-btn notification" id="notificationBtn" aria-label="Obavijesti" aria-expanded="false"><svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-bell"></use></svg><span id="notificationDot"></span></button>
        <button class="profile-btn" id="profileButton" aria-label="Korisnički profil"><b id="profileInitials">K</b><span id="profileName">Korisnik</span><i>⌄</i></button>
      </div>
    </header>

    <section class="view is-active" id="view-home" data-view="home">
      <div class="mobile-home-dashboard">
        <div class="mobile-greeting">
          <h1 id="mobileTodayTitle">—</h1>
          <p id="mobileGreeting">Dobar dan! 👋</p>
        </div>

        <section class="card mobile-shift-card" id="mobileCurrentShift"></section>
        <section class="card mobile-shift-card mobile-shift-card--next" id="mobileNextShift"></section>

        <div class="mobile-shift-chips" aria-label="Oznake smjena">
          <span><i class="shift d">D</i><small>Dnevna</small></span>
          <span><i class="shift n">N</i><small>Noćna</small></span>
          <span><i class="shift go">GO</i><small>Slobodan</small></span>
          <span><i class="shift bo">BO</i><small>Bolovanje</small></span>
        </div>

        <div class="mobile-metric-grid" id="mobileMetricGrid"></div>

        <button class="primary-btn mobile-scan-cta" data-route="scan">
          <svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-camera"></use></svg><span>Skeniraj raspored</span><svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-chevron-right"></use></svg>
        </button>
      </div>

      <div class="page-head">
        <div><h1 id="welcomeTitle">Dobro došao!</h1><p>Ovdje možeš pregledati svoj raspored, evidenciju sati i statistiku.</p></div>
        <button class="primary-btn" data-route="scan"><svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-camera"></use></svg>Uvezi / Skeniraj raspored <svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-chevron-down"></use></svg></button>
      </div>

      <div class="dashboard-grid">
        <section class="card calendar-card">
          <div class="card-head calendar-head">
            <div class="month-arrows"><button class="icon-btn" id="prevMonth" aria-label="Prethodni mjesec"><svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-chevron-left"></use></svg></button><button class="icon-btn" id="nextMonth" aria-label="Sljedeći mjesec"><svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-chevron-right"></use></svg></button></div>
            <h2 id="monthTitle">—</h2>
            <button class="secondary-btn" id="todayBtn">Danas</button>
          </div>
          <div class="weekday-row" aria-hidden="true"><span>Pon</span><span>Uto</span><span>Sri</span><span>Čet</span><span>Pet</span><span>Sub</span><span>Ned</span></div>
          <div class="calendar-grid" id="calendarGrid" role="grid" aria-label="Mjesečni raspored"></div>
        </section>

        <aside class="summary-column">
          <section class="card month-summary">
            <div class="card-head"><h3>Ovaj mjesec</h3><small id="monthLabel">—</small></div>
            <div class="summary-grid" id="summaryGrid"></div>
          </section>
          <section class="card next-shifts">
            <div class="card-head"><h3>Sljedeće smjene</h3><button class="link-btn" data-route="calendar">Vidi sve</button></div>
            <div id="nextShiftList"></div>
          </section>
        </aside>
      </div>

      <section class="quick-actions" aria-label="Brze akcije">
        <button data-route="scan"><svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-camera"></use></svg><b>Skeniraj raspored</b><small>OCR prepoznavanje iz fotografije</small></button>
        <button data-route="hours"><svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-clock"></use></svg><b>Evidencija sati</b><small>Pregledaj odrađene sate i smjene</small></button>
        <button data-route="stats"><svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-chart"></use></svg><b>Statistika</b><small>Analize, saldo i izvještaji</small></button>
        <button class="desktop-extra" data-route="colleagues"><svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-users"></use></svg><b>Kolege</b><small>Lokalni popis i bilješke</small></button>
        <button data-route="settings"><svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-settings"></use></svg><b>Postavke</b><small>Prilagodi aplikaciju svojim potrebama</small></button>
      </section>
    </section>

    <section class="view" id="view-calendar" data-view="calendar">
      <div class="mobile-page-title"><h1>Kalendar</h1></div>
      <section class="card mobile-calendar-card">
        <div class="calendar-mobile-head"><button class="icon-btn" id="calPrev" aria-label="Prethodni mjesec"><svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-chevron-left"></use></svg></button><h2 id="calMonthTitle">—</h2><button class="icon-btn" id="calNext" aria-label="Sljedeći mjesec"><svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-chevron-right"></use></svg></button></div>
        <div class="weekday-row"><span>Pon</span><span>Uto</span><span>Sri</span><span>Čet</span><span>Pet</span><span>Sub</span><span>Ned</span></div>
        <div class="calendar-grid calendar-grid--mobile" id="calendarGridMobile"></div>
      </section>
      <section class="card selected-day" id="selectedDayCard"></section>
      <div class="shift-legend"><span><i class="shift d">D</i>Dnevna<br><small>07:00–19:00</small></span><span><i class="shift n">N</i>Noćna<br><small>19:00–07:00</small></span><span><i class="shift go">GO</i>Slobodan dan</span><span><i class="shift bo">BO</i>Bolovanje</span></div>
      <section class="card month-strip"><div class="card-head"><h3>Sažetak za mjesec</h3><button class="link-btn" data-route="stats">Vidi detalje ›</button></div><div class="month-strip-grid" id="monthStrip"></div></section>
    </section>

    <section class="view" id="view-scan" data-view="scan">
      <div class="scan-header"><button class="back-btn" data-route="home" aria-label="Natrag"><svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-chevron-left"></use></svg></button><div class="brand-inline"><img src="assets/brand/logo.svg" alt="" width="40"><b>RASPORED</b></div></div>
      <div class="scan-copy scan-copy--with-help"><div><h1>Skeniraj raspored</h1><p>Slikaj raspored s papira ili učitaj fotografiju.<br>Mi ćemo automatski prepoznati podatke.</p></div><button class="icon-btn scan-help-btn" id="scanHelpBtn" aria-label="Pomoć za skeniranje">?</button></div>
      <section class="scan-preview" id="scanPreview">
        <div class="scan-corners" aria-hidden="true"></div>
        <img id="scanPreviewImage" class="scan-preview-image" alt="Odabrana fotografija rasporeda">
        <div class="scan-empty-state" id="scanEmptyState">
          <svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-scan"></use></svg>
          <b>Raspored nije učitan</b>
          <span>Skeniraj papirnati raspored ili odaberi fotografiju iz galerije.</span>
        </div>
        <div class="scan-actions">
          <button id="rescanBtn"><svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-camera"></use></svg>Ponovno skeniraj</button>
          <button id="galleryBtn"><svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-image"></use></svg>Odaberi iz galerije</button>
        </div>
        <div class="scan-status" id="scanStatus" role="status" aria-live="polite">
          <span>Spremno za učitavanje rasporeda.</span>
          <div class="scan-progress" aria-hidden="true"><i></i></div>
        </div>
        <input class="visually-hidden" type="file" id="cameraInput" accept="image/*" capture="environment">
        <input class="visually-hidden" type="file" id="galleryInput" accept="image/*">
      </section>
      <section class="card scan-card">
        <h2>Odaberi osobu</h2>
        <p>Ako raspored sadrži više djelatnika, odaberi samo ime i prezime osobe čiji raspored želiš uvesti.</p>
        <div class="scan-person-picker">
          <button class="select-row" id="scanPersonButton" aria-haspopup="listbox" aria-expanded="false">
            <svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-user"></use></svg>
            <b id="scanPersonLabel">Odaberi ime i prezime</b>
            <svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-chevron-down"></use></svg>
          </button>
          <div class="scan-person-menu" id="scanPersonMenu" role="listbox" hidden></div>
        </div>
        <div class="scan-month-field">
          <span>Mjesec rasporeda</span>
          <div class="scan-month-stepper" aria-label="Mjesec rasporeda">
            <button type="button" class="icon-btn" id="scanMonthPrev" aria-label="Prethodni mjesec"><svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-chevron-left"></use></svg></button>
            <b id="scanMonthLabel">—</b>
            <button type="button" class="icon-btn" id="scanMonthNext" aria-label="Sljedeći mjesec"><svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-chevron-right"></use></svg></button>
          </div>
        </div>
      </section>
      <section class="card scan-card"><div class="card-head"><div><h2>Provjera rasporeda</h2><p>Pregledaj prepoznate smjene i po potrebi ih ispravi.</p></div><span class="success-pill" id="recognitionStatus">Odaberi osobu</span></div><div class="recognition-days" id="recognitionDays"></div><div class="scan-edit-actions"><button id="editRecognitionBtn"><svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-edit"></use></svg>Uredi</button><button id="rescanSecondary"><svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-scan"></use></svg>Ponovno skeniraj</button></div></section>
      <button class="primary-btn primary-btn--full" id="saveSchedule"><svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-check"></use></svg>Spremi raspored</button>
    </section>

    <section class="view" id="view-stats" data-view="stats">
      <div class="stats-title-row">
        <h1>Statistika</h1>
        <div class="period-picker">
          <button class="secondary-btn" id="statsPeriod" aria-haspopup="menu" aria-expanded="false">
            <svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-calendar"></use></svg><span>—</span><svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-chevron-down"></use></svg>
          </button>
          <div class="period-menu" id="statsPeriodMenu" role="menu" hidden></div>
        </div>
      </div>
      <section class="card stats-main-card">
        <div class="stats-hero"><div><h3>Ukupno odrađeno sati</h3><strong id="workedTotal">0:00 h</strong><p id="workedTrend">—</p></div><div class="donut" id="donut"><span><b id="donutHours">0h</b><small>ukupno</small></span></div></div>
        <div class="stats-categories" id="statsCategories"></div>
      </section>
      <section class="card chart-card"><div class="card-head"><h2>Raspodjela sati po tjednima</h2><span>›</span></div><div class="bar-chart" id="weeklyBars"></div></section>
      <section class="card detail-card"><div class="card-head"><h2>Detaljna statistika</h2><span>›</span></div><div class="detail-grid" id="detailStats"></div></section>
    </section>


    <section class="view" id="view-hours" data-view="hours">
      <div class="hours-title-row">
        <div>
          <h1>Evidencija sati</h1>
          <p>Bilježi stvarni ulaz i izlaz te prati odrađeno vrijeme.</p>
        </div>
        <span class="hours-date" id="hoursDate">—</span>
      </div>

      <div class="hours-layout">
        <section class="card hours-current-card">
          <div class="card-head">
            <div>
              <small>Današnji status</small>
              <h2 id="hoursStatus">Nema evidentiranog ulaza</h2>
            </div>
            <span class="hours-status-pill" id="hoursStatusPill">Spremno</span>
          </div>
          <div class="hours-shift-row">
            <span class="metric-icon"><svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-clock"></use></svg></span>
            <div><small>Planirana smjena</small><b id="hoursPlannedShift">—</b></div>
          </div>
          <div class="hours-live">
            <div><small>Ulaz</small><b id="hoursInValue">—</b></div>
            <div><small>Izlaz</small><b id="hoursOutValue">—</b></div>
            <div><small>Odrađeno</small><b id="hoursDurationValue">0h 00min</b></div>
          </div>
          <div class="hours-actions">
            <button class="primary-btn" id="clockInBtn"><svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-check"></use></svg>Evidentiraj ulaz</button>
            <button class="secondary-btn" id="clockOutBtn"><svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-clock"></use></svg>Evidentiraj izlaz</button>
          </div>
          <label class="hours-note">
            <span>Bilješka</span>
            <textarea id="hoursNote" rows="3" maxlength="500" placeholder="Dodaj kratku bilješku uz evidenciju, po potrebi."></textarea>
          </label>
        </section>

        <section class="card hours-history-card">
          <div class="card-head">
            <div><h2>Ovaj mjesec</h2><small id="hoursMonthLabel">—</small></div>
            <strong id="hoursMonthTotal">0h 00min</strong>
          </div>
          <div class="hours-history" id="hoursHistory"></div>
        </section>
      </div>
    </section>

    <section class="view" id="view-colleagues" data-view="colleagues">
      <div class="colleagues-title-row">
        <div><h1>Kolege</h1><p>Dodaj kolege i brzo provjeri njihove planirane smjene.</p></div>
        <button class="primary-btn" id="addColleagueBtn"><svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-users"></use></svg>Dodaj kolegu</button>
      </div>
      <section class="card colleagues-card">
        <div class="colleagues-list" id="colleaguesList"></div>
      </section>
    </section>

    <section class="view" id="view-settings" data-view="settings">
      <div class="mobile-page-title"><h1>Postavke</h1></div>
      <section class="card settings-card">
        <h2>Profil</h2>
        <label class="setting-field"><span><b>Ime i prezime</b><small>Koristi se samo za prikaz u ovoj instalaciji aplikacije.</small></span><input type="text" id="profileNameInput" maxlength="80" autocomplete="name" placeholder="Unesi ime i prezime"></label>
        <button class="secondary-btn settings-save" id="saveProfileBtn">Spremi profil</button>
      </section>
      <section class="card settings-card">
        <h2>Izgled i pristupačnost</h2>
        <label class="setting-row"><span><b>Tamni način</b><small>Koristi navy/dark surface uz iste statusne boje.</small></span><input type="checkbox" id="themeToggle"></label>
        <label class="setting-row"><span><b>Smanjene animacije</b><small>Poštuje prefers-reduced-motion i dodatnu lokalnu postavku.</small></span><input type="checkbox" id="motionToggle"></label>
      </section>
    </section>
  </main>

  <nav class="bottom-nav" aria-label="Mobilna navigacija">
    <button class="is-active" data-route="home"><svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-home"></use></svg><small>Početna</small></button>
    <button data-route="calendar"><svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-calendar"></use></svg><small>Kalendar</small></button>
    <button class="scan-nav" data-route="scan"><svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-camera"></use></svg><small>Skeniraj</small></button>
    <button data-route="stats"><svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-chart"></use></svg><small>Statistika</small></button>
    <button data-route="settings"><svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-settings"></use></svg><small>Postavke</small></button>
  </nav>
</div>

<div class="floating-panel notification-panel" id="notificationPanel" hidden>
  <div class="floating-panel-head"><b>Obavijesti</b><button class="icon-btn" id="closeNotificationBtn" aria-label="Zatvori">×</button></div>
  <p id="notificationText">Nema novih obavijesti.</p>
</div>
<div class="floating-panel profile-panel" id="profilePanel" hidden>
  <div class="floating-panel-head"><b>Profil</b><button class="icon-btn" id="closeProfileBtn" aria-label="Zatvori">×</button></div>
  <button class="panel-action" data-route="settings">Uredi profil i postavke</button>
</div>


<dialog class="app-dialog" id="scanHelpDialog">
  <form class="dialog-card" method="dialog">
    <div class="dialog-head">
      <div><h2>Kako dobiti dobar rezultat</h2><p>Fotografija rasporeda treba biti jasna i ravna.</p></div>
      <button class="icon-btn" value="cancel" aria-label="Zatvori">×</button>
    </div>
    <div class="scan-help-list">
      <p><b>1.</b> Obuhvati cijelu tablicu i zaglavlje s brojevima dana.</p>
      <p><b>2.</b> Izbjegni sjene, odsjaj i zamućenje.</p>
      <p><b>3.</b> Ako je na rasporedu više osoba, nakon prepoznavanja odaberi samo jedno ime i prezime.</p>
      <p><b>4.</b> Provjeri D, N, GO i BO oznake prije spremanja.</p>
    </div>
  </form>
</dialog>

<dialog class="app-dialog" id="searchDialog">
  <form class="dialog-card" method="dialog">
    <div class="dialog-head">
      <div><h2>Pretraži RASPORED</h2><p>Brzo otvori željeni dio aplikacije.</p></div>
      <button class="icon-btn" value="cancel" aria-label="Zatvori">×</button>
    </div>
    <label class="dialog-field">
      <span>Pretraživanje</span>
      <input id="searchInput" type="search" autocomplete="off" placeholder="Npr. kalendar, statistika, evidencija sati">
    </label>
    <div class="search-results" id="searchResults"></div>
  </form>
</dialog>

<dialog class="app-dialog" id="colleagueDialog">
  <form class="dialog-card" id="colleagueForm">
    <div class="dialog-head">
      <div><h2>Dodaj kolegu</h2><p>Spremi ime i kratku napomenu lokalno u ovoj instalaciji.</p></div>
      <button class="icon-btn" type="button" id="closeColleagueDialog" aria-label="Zatvori">×</button>
    </div>
    <label class="dialog-field"><span>Ime i prezime</span><input id="colleagueNameInput" maxlength="80" autocomplete="name" required></label>
    <label class="dialog-field"><span>Napomena</span><input id="colleagueNoteInput" maxlength="120" placeholder="Npr. Odjel B"></label>
    <div class="dialog-actions">
      <button class="secondary-btn" type="button" id="cancelColleagueBtn">Odustani</button>
      <button class="primary-btn" type="submit">Spremi kolegu</button>
    </div>
  </form>
</dialog>

<div class="connectivity-banner" id="connectivityBanner" role="status" aria-live="polite">Nema internetske veze. Spremljeni raspored ostaje dostupan.</div>
<div class="toast" id="toast" role="status" aria-live="polite"></div>
<script src="<?= htmlspecialchars(($base ?: '') . '/assets/js/ocr-web.js', ENT_QUOTES) ?>" defer></script>
<script src="<?= htmlspecialchars(($base ?: '') . '/assets/js/app.js', ENT_QUOTES) ?>" defer></script>
</body>
</html>
