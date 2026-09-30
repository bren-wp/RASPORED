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

