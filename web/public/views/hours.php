    <section class="view" id="view-hours" data-view="hours">
      <div class="hours-title-row">
        <div>
          <h1>Evidencija sati</h1>
          <p>Automatski se računa iz rasporeda u kalendaru — bez ručnog evidentiranja ulaza i izlaza.</p>
        </div>
        <span class="hours-date" id="hoursDate">—</span>
      </div>

      <div class="hours-layout">
        <section class="card hours-current-card">
          <div class="card-head">
            <div>
              <small>Izvor evidencije</small>
              <h2>Raspored u kalendaru</h2>
            </div>
            <span class="hours-status-pill is-done">Automatski</span>
          </div>
          <div class="hours-shift-row">
            <span class="metric-icon"><svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-clock"></use></svg></span>
            <div><small>Pravilo obračuna</small><b>D / N = 12 h · J = 8 h · ostale vlastite oznake bez izmišljene satnice</b></div>
          </div>
          <div class="hours-live">
            <div><small>Ukupno</small><b id="hoursMonthTotal">0 h</b></div>
            <div><small>Noćni sati</small><b id="hoursNightTotal">0 h</b></div>
            <div><small>Vikend/blagdan</small><b id="hoursWeekendTotal">0 h</b></div>
          </div>
          <p class="hours-note">
            Evidencija prikazuje detaljan mjesečni zapis izveden iz kalendara. Statistika je odvojena i služi za zbirne pokazatelje, usporedbe i trendove.
          </p>
        </section>

        <section class="card hours-history-card">
          <div class="card-head">
            <div><h2>Dnevna evidencija</h2><small id="hoursMonthLabel">—</small></div>
          </div>
          <div class="hours-history" id="hoursHistory"></div>
        </section>
      </div>
    </section>
