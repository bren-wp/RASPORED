<!doctype html>
<html lang="hr" data-theme="dark">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
<meta name="theme-color" content="#07111F">
<title>Takto — raspored rada</title>
<meta name="description" content="Takto za jednostavno planiranje smjena, kalendar, evidenciju sati, statistiku i skeniranje rasporeda.">
<link rel="manifest" href="<?= htmlspecialchars(($base ?: '') . '/manifest.webmanifest', ENT_QUOTES) ?>">
<link rel="icon" href="<?= htmlspecialchars(($base ?: '') . '/assets/brand/logo.svg', ENT_QUOTES) ?>" type="image/svg+xml">
<link rel="stylesheet" href="<?= htmlspecialchars(($base ?: '') . '/assets/css/app.css', ENT_QUOTES) ?>">
</head>
<body data-base="<?= htmlspecialchars($base, ENT_QUOTES) ?>">
<div class="app-shell">
  <aside class="sidebar" aria-label="Glavna navigacija">
    <a class="brand brand--sidebar" href="#" data-route="home" aria-label="Takto početna">
      <img src="assets/brand/logo.svg" alt="" width="52" height="52">
      <span><strong>Takto</strong><small>Dodirni. Označi. Radi.</small></span>
    </a>
    <nav class="side-nav">
      <button class="nav-item" data-route="home"><svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-home"></use></svg>Početna</button>
      <button class="nav-item is-active" data-route="home"><svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-calendar"></use></svg>Kalendar</button>
      <button class="nav-item" data-route="scan"><svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-camera"></use></svg>Skeniraj</button>
      <button class="nav-item" data-route="stats"><svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-chart"></use></svg>Statistika</button>
      <button class="nav-item desktop-extra" data-route="payroll"><svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-scale"></use></svg>Plaća</button>
      <button class="nav-item desktop-extra" data-route="hours"><svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-clock"></use></svg>Evidencija sati</button>
      <button class="nav-item desktop-extra" data-route="colleagues"><svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-users"></use></svg>Kolege</button>
      <button class="nav-item" data-route="settings"><svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-settings"></use></svg>Postavke</button>
    </nav>
    <div class="side-version"><img src="assets/brand/logo.svg" alt="" width="36"><span>Takto<small>v<?= htmlspecialchars($version, ENT_QUOTES) ?></small></span></div>
  </aside>

  <main class="main">
    <header class="topbar">
      <a class="brand brand--mobile" href="#" data-route="home"><img src="assets/brand/logo.svg" alt="" width="42"><span><strong>Takto</strong><small>Dodirni. Označi. Radi.</small></span></a>
      <div class="top-actions">
        <button class="icon-btn mobile-header-action" id="mobileScanHeaderBtn" data-route="scan" aria-label="Skeniraj raspored"><svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-scan"></use></svg></button>
        <button class="icon-btn mobile-header-action" id="statsRefreshBtn" aria-label="Osvježi statistiku"><svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-refresh"></use></svg></button>
        <button class="icon-btn" id="searchBtn" aria-label="Pretraži"><svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-search"></use></svg></button>
        <button class="icon-btn notification" id="notificationBtn" aria-label="Obavijesti" aria-expanded="false"><svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-bell"></use></svg><span id="notificationDot"></span></button>
        <button class="profile-btn" id="profileButton" aria-label="Korisnički profil"><b id="profileInitials">K</b><span id="profileName">Korisnik</span><i>⌄</i></button>
      </div>
    </header>

