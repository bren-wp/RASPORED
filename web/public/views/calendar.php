    <section class="view is-active" id="view-calendar" data-view="calendar">
      <div class="mobile-page-title"><h1>Kalendar</h1></div>
      <section class="card mobile-calendar-card">
        <div class="calendar-mobile-head"><button class="icon-btn" id="calPrev" aria-label="Prethodni mjesec"><svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-chevron-left"></use></svg></button><h2 id="calMonthTitle">—</h2><button class="icon-btn" id="calNext" aria-label="Sljedeći mjesec"><svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-chevron-right"></use></svg></button></div>
        <div class="weekday-row"><span>Pon</span><span>Uto</span><span>Sri</span><span>Čet</span><span>Pet</span><span>Sub</span><span>Ned</span></div>
        <div class="calendar-grid calendar-grid--mobile" id="calendarGridMobile"></div>
      </section>
      <section class="card selected-day" id="selectedDayCard"></section>
      <div class="shift-legend">
        <span><i class="shift d">D</i>Dnevna<br><small>radna smjena</small></span>
        <span><i class="shift n">N</i>Noćna<br><small>radna smjena</small></span>
        <span><i class="shift go">GO</i>Godišnji odmor</span>
        <span><i class="shift bo">BO</i>Bolovanje</span>
        <span><i class="shift pd">PD</i>Plaćeni dopust</span>
        <span><i class="shift sd">SD</i>Slobodan dan</span>
      </div>
      <section class="card month-strip"><div class="card-head"><h3>Sažetak za mjesec</h3><button class="link-btn" data-route="stats">Vidi detalje ›</button></div><div class="month-strip-grid" id="monthStrip"></div></section>
    </section>

