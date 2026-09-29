<?php
declare(strict_types=1);
header('Content-Type: text/html; charset=utf-8');
$base = rtrim(str_replace('\\', '/', dirname($_SERVER['SCRIPT_NAME'] ?? '/')), '/');
if ($base === '.') { $base = ''; }
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
<body>
<div class="app-shell">
  <aside class="sidebar" aria-label="Glavna navigacija">
    <a class="brand brand--sidebar" href="#" data-route="home" aria-label="RASPORED početna">
      <img src="assets/brand/logo.svg" alt="" width="52" height="52">
      <span><strong>RASPORED</strong><small>Shift planner & evidencija sati</small></span>
    </a>
    <nav class="side-nav">
      <button class="nav-item is-active" data-route="home"><span>⌂</span>Početna</button>
      <button class="nav-item" data-route="calendar"><span>▦</span>Kalendar</button>
      <button class="nav-item" data-route="scan"><span>▣</span>Skeniraj</button>
      <button class="nav-item" data-route="stats"><span>▥</span>Statistika</button>
      <button class="nav-item desktop-extra"><span>◷</span>Evidencija sati</button>
      <button class="nav-item desktop-extra"><span>♙</span>Kolege</button>
      <button class="nav-item" data-route="settings"><span>⚙</span>Postavke</button>
    </nav>
    <div class="side-version"><img src="assets/brand/logo.svg" alt="" width="36"><span>RASPORED<small>v0.1.0-dev</small></span></div>
  </aside>

  <main class="main">
    <header class="topbar">
      <a class="brand brand--mobile" href="#" data-route="home"><img src="assets/brand/logo.svg" alt="" width="42"><strong>RASPORED</strong></a>
      <div class="top-actions">
        <button class="icon-btn" id="searchBtn" aria-label="Pretraži">⌕</button>
        <button class="icon-btn notification" aria-label="Obavijesti">♢<span></span></button>
        <button class="profile-btn"><b>MM</b><span>Marko Marković</span><i>⌄</i></button>
      </div>
    </header>

    <section class="view is-active" id="view-home" data-view="home">
      <div class="page-head">
        <div><h1 id="welcomeTitle">Dobro došao!</h1><p>Ovdje možeš pregledati svoj raspored, evidenciju sati i statistiku.</p></div>
        <button class="primary-btn" data-route="scan"><span>▣</span>Uvezi / Skeniraj raspored <i>⌄</i></button>
      </div>

      <div class="dashboard-grid">
        <section class="card calendar-card">
          <div class="card-head calendar-head">
            <div class="month-arrows"><button class="icon-btn" id="prevMonth" aria-label="Prethodni mjesec">‹</button><button class="icon-btn" id="nextMonth" aria-label="Sljedeći mjesec">›</button></div>
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
        <button data-route="scan"><span>▣</span><b>Skeniraj raspored</b><small>OCR prepoznavanje iz slike ili PDF-a</small></button>
        <button><span>◷</span><b>Evidencija sati</b><small>Pregledaj odrađene sate i smjene</small></button>
        <button data-route="stats"><span>▥</span><b>Statistika</b><small>Analize, saldo i izvještaji</small></button>
        <button class="desktop-extra"><span>♙</span><b>Kolege</b><small>Pogledaj rasporede kolega</small></button>
        <button data-route="settings"><span>⚙</span><b>Postavke</b><small>Prilagodi aplikaciju svojim potrebama</small></button>
      </section>
    </section>

    <section class="view" id="view-calendar" data-view="calendar">
      <div class="mobile-page-title"><h1>Kalendar</h1></div>
      <section class="card mobile-calendar-card">
        <div class="calendar-mobile-head"><button class="icon-btn" id="calPrev">‹</button><h2 id="calMonthTitle">—</h2><button class="icon-btn" id="calNext">›</button></div>
        <div class="weekday-row"><span>Pon</span><span>Uto</span><span>Sri</span><span>Čet</span><span>Pet</span><span>Sub</span><span>Ned</span></div>
        <div class="calendar-grid calendar-grid--mobile" id="calendarGridMobile"></div>
      </section>
      <section class="card selected-day" id="selectedDayCard"></section>
      <div class="shift-legend"><span><i class="shift d">D</i>Dnevna<br><small>07:00–19:00</small></span><span><i class="shift n">N</i>Noćna<br><small>19:00–07:00</small></span><span><i class="shift go">GO</i>Slobodan dan</span><span><i class="shift bo">BO</i>Bolovanje</span></div>
      <section class="card month-strip"><div class="card-head"><h3>Sažetak za mjesec</h3><button class="link-btn" data-route="stats">Vidi detalje ›</button></div><div class="month-strip-grid" id="monthStrip"></div></section>
    </section>

    <section class="view" id="view-scan" data-view="scan">
      <div class="scan-header"><button class="back-btn" data-route="home">‹</button><div class="brand-inline"><img src="assets/brand/logo.svg" alt="" width="40"><b>RASPORED</b></div></div>
      <div class="scan-copy"><h1>Skeniraj raspored</h1><p>Slikaj raspored s papira ili učitaj fotografiju.<br>Mi ćemo automatski prepoznati podatke.</p></div>
      <section class="scan-preview" id="scanPreview">
        <div class="scan-corners" aria-hidden="true"></div>
        <div class="fake-sheet" aria-label="Pregled skeniranog rasporeda">
          <b>LISTOPAD 2026.</b>
          <div class="fake-row is-selected">6&nbsp;&nbsp;&nbsp; MARIO EGIMOVIĆ&nbsp;&nbsp;&nbsp; D&nbsp;&nbsp; N&nbsp;&nbsp; D&nbsp;&nbsp; N&nbsp;&nbsp; GO&nbsp;&nbsp; D</div>
          <div class="fake-row">7&nbsp;&nbsp;&nbsp; ADEMI DENI&nbsp;&nbsp;&nbsp; GO&nbsp;&nbsp; D&nbsp;&nbsp; N&nbsp;&nbsp; GO</div>
          <div class="fake-row">8&nbsp;&nbsp;&nbsp; VUČETA ZLATKO&nbsp;&nbsp;&nbsp; N&nbsp;&nbsp; D&nbsp;&nbsp; D</div>
        </div>
        <div class="scan-actions"><button id="rescanBtn">▣ Ponovno skeniraj</button><button id="galleryBtn">▧ Odaberi iz galerije</button></div>
      </section>
      <section class="card scan-card"><h2>Odaberi moj redak</h2><p>Provjeri je li ispravno prepoznat tvoj redak.</p><button class="select-row"><span>♙</span><b>6. MARIO EGIMOVIĆ</b><i>⌄</i></button></section>
      <section class="card scan-card"><div class="card-head"><div><h2>Provjera rasporeda</h2><p>Pregledaj prepoznate smjene i po potrebi ih ispravi.</p></div><span class="success-pill">✓ Prepoznato 31 dan</span></div><div class="recognition-days" id="recognitionDays"></div><div class="scan-edit-actions"><button>✎ Uredi</button><button id="rescanSecondary">⌗ Ponovno skeniraj</button></div></section>
      <button class="primary-btn primary-btn--full" id="saveSchedule">✓ Spremi raspored</button>
    </section>

    <section class="view" id="view-stats" data-view="stats">
      <div class="stats-title-row"><h1>Statistika</h1><button class="secondary-btn" id="statsPeriod">▦ <span>—</span>⌄</button></div>
      <section class="card stats-main-card">
        <div class="stats-hero"><div><h3>Ukupno odrađeno sati</h3><strong id="workedTotal">0:00 h</strong><p id="workedTrend">—</p></div><div class="donut" id="donut"><span><b id="donutHours">0h</b><small>ukupno</small></span></div></div>
        <div class="stats-categories" id="statsCategories"></div>
      </section>
      <section class="card chart-card"><div class="card-head"><h2>Raspodjela sati po tjednima</h2><span>›</span></div><div class="bar-chart" id="weeklyBars"></div></section>
      <section class="card detail-card"><div class="card-head"><h2>Detaljna statistika</h2><span>›</span></div><div class="detail-grid" id="detailStats"></div></section>
    </section>

    <section class="view" id="view-settings" data-view="settings">
      <div class="mobile-page-title"><h1>Postavke</h1></div>
      <section class="card settings-card">
        <h2>Izgled i pristupačnost</h2>
        <label class="setting-row"><span><b>Tamni način</b><small>Koristi navy/dark surface uz iste statusne boje.</small></span><input type="checkbox" id="themeToggle"></label>
        <label class="setting-row"><span><b>Smanjene animacije</b><small>Poštuje prefers-reduced-motion i dodatnu lokalnu postavku.</small></span><input type="checkbox" id="motionToggle"></label>
      </section>
    </section>
  </main>

  <nav class="bottom-nav" aria-label="Mobilna navigacija">
    <button class="is-active" data-route="home"><span>⌂</span><small>Početna</small></button>
    <button data-route="calendar"><span>▦</span><small>Kalendar</small></button>
    <button class="scan-nav" data-route="scan"><span>▣</span><small>Skeniraj</small></button>
    <button data-route="stats"><span>▥</span><small>Statistika</small></button>
    <button data-route="settings"><span>⚙</span><small>Postavke</small></button>
  </nav>
</div>

<div class="toast" id="toast" role="status" aria-live="polite"></div>
<script>window.RASPORED_BASE = <?= json_encode($base, JSON_UNESCAPED_SLASHES) ?>;</script>
<script src="<?= htmlspecialchars(($base ?: '') . '/assets/js/app.js', ENT_QUOTES) ?>" defer></script>
</body>
</html>
