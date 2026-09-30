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
          <span><i class="shift go">GO</i><small>Godišnji</small></span>
          <span><i class="shift bo">BO</i><small>Bolovanje</small></span>
          <span><i class="shift pd">PD</i><small>Plaćeni dopust</small></span>
          <span><i class="shift sd">SD</i><small>Slobodan dan</small></span>
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
        <button data-route="payroll"><svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-scale"></use></svg><b>Okvirna plaća</b><small>Bruto procjena prema koeficijentu i evidenciji</small></button>
        <button class="desktop-extra" data-route="colleagues"><svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-users"></use></svg><b>Kolege</b><small>Lokalni popis i bilješke</small></button>
        <button data-route="settings"><svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-settings"></use></svg><b>Postavke</b><small>Prilagodi aplikaciju svojim potrebama</small></button>
      </section>
    </section>

