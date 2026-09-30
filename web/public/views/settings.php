    <section class="view" id="view-settings" data-view="settings">
      <div class="mobile-page-title"><h1>Postavke</h1></div>
      <section class="card settings-card account-card">
        <div class="card-head">
          <div>
            <h2>Korisnički račun</h2>
            <small id="accountStatusText">Provjera statusa računa…</small>
          </div>
          <span class="account-status-badge" id="accountStatusBadge">Gost</span>
        </div>
        <p class="settings-help">Kalendar, raspored smjena i evidencija sati rade i bez registracije. Račun omogućuje pristup istim podacima nakon ponovne prijave i otključava voditeljske opcije kada je odabran profil voditelja.</p>
        <div class="account-actions">
          <button class="secondary-btn" id="loginAccountBtn" type="button">Prijava</button>
          <button class="primary-btn" id="registerAccountBtn" type="button">Registracija</button>
          <button class="secondary-btn" id="logoutAccountBtn" type="button" hidden>Odjava</button>
        </div>
        <div class="account-details" id="accountDetails" hidden></div>
      </section>
      <section class="card settings-card">
        <h2>Profil</h2>
        <label class="setting-field"><span><b>Ime i prezime</b><small>Koristi se samo za prikaz u ovoj instalaciji aplikacije.</small></span><input type="text" id="profileNameInput" maxlength="80" autocomplete="name" placeholder="Unesi ime i prezime"></label>
        <button class="secondary-btn settings-save" id="saveProfileBtn">Spremi profil</button>
      </section>
      <section class="card settings-card">
        <h2>Izvoz podataka</h2>
        <p class="settings-help">Preuzmi sigurnosnu kopiju u JSON-u ili otvori mjesečni izvještaj za ispis / spremanje kao PDF u pregledniku.</p>
        <div class="account-actions">
          <button class="secondary-btn" id="exportJsonBtn" type="button">Preuzmi JSON</button>
          <button class="primary-btn" id="printPdfBtn" type="button">Ispis / spremi kao PDF</button>
        </div>
      </section>
      <section class="card settings-card">
        <h2>Izgled i pristupačnost</h2>
        <label class="setting-row"><span><b>Tamni način</b><small>Koristi navy/dark surface uz iste statusne boje.</small></span><input type="checkbox" id="themeToggle"></label>
        <label class="setting-row"><span><b>Smanjene animacije</b><small>Poštuje prefers-reduced-motion i dodatnu lokalnu postavku.</small></span><input type="checkbox" id="motionToggle"></label>
      </section>
      <section class="card settings-card support-card">
        <h2>Podrška</h2>
        <p class="settings-help">Za pomoć s aplikacijom, rasporedom ili prijavom greške.</p>
        <div class="support-links">
          <a href="https://wa.me/385919010092" target="_blank" rel="noopener noreferrer" class="support-link" aria-label="WhatsApp podrška +385 91 901 0092">
            <span><b>WhatsApp</b><small>+385 91 901 0092</small></span><span aria-hidden="true">›</span>
          </a>
          <a href="mailto:info@raspored.eu" class="support-link">
            <span><b>E-mail</b><small>info@raspored.eu</small></span><span aria-hidden="true">›</span>
          </a>
          <a href="https://brendigo.com" target="_blank" rel="noopener noreferrer" class="support-link">
            <span><b>Developer — Brendigo Studio</b><small>brendigo.com</small></span><span aria-hidden="true">›</span>
          </a>
        </div>
      </section>
    </section>
  </main>

