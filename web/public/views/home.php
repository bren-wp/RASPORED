    <section class="view" id="view-home" data-view="home">
      <div class="mobile-home-dashboard">
        <div class="mobile-greeting">
          <p id="mobileGreeting" class="takto-greeting">Dobar dan!</p>
          <h1>Tvoj raspored.<br><span>Na prvi pogled.</span></h1>
          <small id="mobileTodayTitle">—</small>
        </div>

        <section class="card mobile-shift-card" id="mobileCurrentShift"></section>
        <section class="card mobile-shift-card mobile-shift-card--next" id="mobileNextShift"></section>

        <section class="card takto-week-card">
          <div class="takto-week-head"><strong>Ovaj tjedan</strong><button class="link-btn" data-route="calendar">Prikaži kalendar ›</button></div>
          <div class="takto-week-strip" id="mobileWeekStrip" aria-label="Raspored za ovaj tjedan"></div>
          <button class="primary-btn takto-add-shift" data-route="calendar"><span aria-hidden="true">＋</span> Dodaj ili označi dan</button>
        </section>

        <button class="primary-btn mobile-scan-cta" data-route="scan">
          <svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-camera"></use></svg><span>Skeniraj raspored</span><svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-chevron-right"></use></svg>
        </button>

        <div class="takto-home-actions" aria-label="Brze akcije">
          <button data-route="hours"><svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-clock"></use></svg><span>Evidencija</span></button>
          <button data-route="stats"><svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-chart"></use></svg><span>Statistika</span></button>
          <button data-route="payroll"><svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-scale"></use></svg><span>Plaća</span></button>
        </div>

        <div class="mobile-metric-grid" id="mobileMetricGrid"></div>

        <div class="mobile-shift-chips" aria-label="Oznake smjena">
          <span><i class="shift d">D</i><small>Dan</small></span>
          <span><i class="shift n">N</i><small>Noć</small></span>
          <span><i class="shift go">GO</i><small>Godišnji</small></span>
          <span><i class="shift bo">BO</i><small>Bolovanje</small></span>
          <span><i class="shift pd">PD</i><small>Plaćeni dopust</small></span>
          <span><i class="shift sd">SD</i><small>Slobodan dan</small></span>
        </div>
      </div>

      <div class="page-head">
        <div><h1 id="welcomeTitle">Dobro došao u Takto!</h1><p>Tvoj raspored, evidencija i statistika na jednom mjestu.</p></div>
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
        <button data-route="scan"><svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-camera"></use></svg><b>Skeniraj raspored</b><small>Prepoznaj raspored iz fotografije</small></button>
        <button data-route="hours"><svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-clock"></use></svg><b>Evidencija sati</b><small>Pregledaj odrađene sate i smjene</small></button>
        <button data-route="stats"><svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-chart"></use></svg><b>Statistika</b><small>Analize, saldo i izvještaji</small></button>
        <button data-route="payroll"><svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-scale"></use></svg><b>Okvirna plaća</b><small>Bruto procjena prema koeficijentu i evidenciji</small></button>
        <button class="desktop-extra" data-route="colleagues"><svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-users"></use></svg><b>Kolege</b><small>Lokalni popis i bilješke</small></button>
      </section>
    </section>

