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

<dialog class="app-dialog" id="loginDialog">
  <form class="dialog-card" id="loginForm">
    <div class="dialog-head">
      <div><h2>Prijava</h2><p>Pristupi svom RASPORED računu i spremljenim podacima.</p></div>
      <button class="icon-btn" type="button" data-close-auth aria-label="Zatvori">×</button>
    </div>
    <label class="dialog-field"><span>E-mail</span><input id="loginEmail" type="email" maxlength="160" autocomplete="email" required></label>
    <label class="dialog-field"><span>Lozinka</span><input id="loginPassword" type="password" minlength="10" maxlength="200" autocomplete="current-password" required></label>
    <p class="auth-error" id="loginError" role="alert" hidden></p>
    <div class="dialog-actions">
      <button class="secondary-btn" type="button" data-close-auth>Odustani</button>
      <button class="primary-btn" type="submit">Prijavi se</button>
    </div>
  </form>
</dialog>

<dialog class="app-dialog" id="registerDialog">
  <form class="dialog-card" id="registerForm">
    <div class="dialog-head">
      <div><h2>Registracija</h2><p>Račun čuva podatke u zaštićenom <code>storage/data</code> spremištu ove instalacije.</p></div>
      <button class="icon-btn" type="button" data-close-auth aria-label="Zatvori">×</button>
    </div>
    <div class="auth-grid">
      <label class="dialog-field"><span>Ime</span><input id="registerFirstName" maxlength="60" autocomplete="given-name" required></label>
      <label class="dialog-field"><span>Prezime</span><input id="registerLastName" maxlength="60" autocomplete="family-name" required></label>
    </div>
    <label class="dialog-field"><span>E-mail</span><input id="registerEmail" type="email" maxlength="160" autocomplete="email" required></label>
    <label class="dialog-field"><span>Broj telefona</span><input id="registerPhone" type="tel" maxlength="30" autocomplete="tel" required></label>
    <label class="dialog-field"><span>Lozinka</span><input id="registerPassword" type="password" minlength="10" maxlength="200" autocomplete="new-password" required><small>Najmanje 10 znakova, jedno slovo i jedan broj.</small></label>
    <label class="dialog-field"><span>Vrsta profila</span>
      <select id="registerAccountType">
        <option value="individual">Djelatnik / osobni raspored</option>
        <option value="manager">Voditelj tima / više djelatnika</option>
      </select>
    </label>
    <p class="auth-error" id="registerError" role="alert" hidden></p>
    <div class="dialog-actions">
      <button class="secondary-btn" type="button" data-close-auth>Odustani</button>
      <button class="primary-btn" type="submit">Izradi račun</button>
    </div>
  </form>
</dialog>

<dialog class="app-dialog" id="colleagueDialog">
  <form class="dialog-card" id="colleagueForm">
    <div class="dialog-head">
      <div><h2>Dodaj kolegu</h2><p>Spremi ime i kratku napomenu u ovu instalaciju RASPORED-a.</p></div>
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
<script src="<?= htmlspecialchars(($base ?: '') . '/assets/js/data-store.js', ENT_QUOTES) ?>" defer></script>
<script src="<?= htmlspecialchars(($base ?: '') . '/assets/js/ocr-web.js', ENT_QUOTES) ?>" defer></script>
<script src="<?= htmlspecialchars(($base ?: '') . '/assets/js/payroll.js', ENT_QUOTES) ?>" defer></script>
<script src="<?= htmlspecialchars(($base ?: '') . '/assets/js/auth.js', ENT_QUOTES) ?>" defer></script>
<script src="<?= htmlspecialchars(($base ?: '') . '/assets/js/app.js', ENT_QUOTES) ?>" defer></script>
</body>
</html>
