    <section class="view" id="view-stats" data-view="stats">
      <div class="stats-title-row">
        <div><h1>Statistika</h1><button class="link-btn stats-payroll-link" data-route="payroll">Izračunaj okvirnu plaću ›</button></div>
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


