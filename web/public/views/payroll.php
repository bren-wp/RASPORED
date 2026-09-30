    <section class="view" id="view-payroll" data-view="payroll">
      <div class="payroll-title-row">
        <div>
          <h1>Okvirna plaća</h1>
          <p>Procjena bruto plaće za javne zdravstvene ustanove prema službenom koeficijentu, osnovici i stvarno evidentiranim satima.</p>
        </div>
        <label class="payroll-month"><span>Mjesec</span><input type="month" id="payrollMonth" min="2026-01" max="2026-12"></label>
      </div>

      <div class="payroll-layout">
        <section class="card payroll-settings-card">
          <div class="card-head">
            <div><h2>Radno mjesto</h2><small>Javne bolnice i KBC-ovi u RH</small></div>
            <span class="payroll-badge">2026</span>
          </div>
          <label class="payroll-field">
            <span>Odaberi radno mjesto</span>
            <select id="payrollRole"></select>
          </label>
          <div class="payroll-two">
            <label class="payroll-field"><span>Koeficijent</span><input id="payrollCoefficient" type="number" min="1" max="8" step="0.01" inputmode="decimal"></label>
            <label class="payroll-field"><span>Godine staža</span><input id="payrollYears" type="number" min="0" max="60" step="1" inputmode="numeric"></label>
          </div>
          <div class="payroll-two">
            <label class="payroll-field"><span>Ručna osnovica (€)</span><input id="payrollCustomBase" type="number" min="0" max="10000" step="0.01" inputmode="decimal" placeholder="Automatski"></label>
            <label class="payroll-field"><span>Dodatak po rješenju/ugovoru (%)</span><input id="payrollExtraPercent" type="number" min="0" max="100" step="0.1" inputmode="decimal" value="0"></label>
          </div>
          <label class="setting-row payroll-switch">
            <span><b>Druga smjena 10%</b><small>Uključi samo ako se na tvoju organizaciju rada primjenjuje dodatak za rad u drugoj smjeni, u pravilu 14:00–22:00.</small></span>
            <input type="checkbox" id="payrollSecondShift">
          </label>
          <div class="payroll-role-note" id="payrollRoleNote"></div>
        </section>

        <section class="card payroll-result-card">
          <div class="payroll-estimate-head">
            <div><small>Procijenjeni bruto</small><strong id="payrollGross">0,00 €</strong><span id="payrollEvidenceHint">Na temelju evidentiranih sati.</span></div>
            <span class="metric-icon"><svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-chart"></use></svg></span>
          </div>
          <div class="payroll-result-grid">
            <div><small>Osnovica</small><b id="payrollBase">—</b></div>
            <div><small>Koeficijent</small><b id="payrollCoefResult">—</b></div>
            <div><small>Staž</small><b id="payrollSeniority">—</b></div>
            <div><small>Mjesečni fond</small><b id="payrollFund">—</b></div>
            <div><small>Osnovna bruto + staž</small><b id="payrollBasicGross">—</b></div>
            <div><small>Dodaci iz evidencije</small><b id="payrollAdditions">—</b></div>
          </div>
        </section>
      </div>

      <section class="card payroll-breakdown-card">
        <div class="card-head"><div><h2>Obračunski sati i dodaci</h2><small>Dodaci se procjenjuju iz Evidencije sati, ne iz planiranog rasporeda.</small></div></div>
        <div class="payroll-breakdown" id="payrollBreakdown"></div>
      </section>

      <section class="card payroll-legal-card">
        <h2>Kako je procjena napravljena</h2>
        <p id="payrollLegalText">Učitavanje službenih parametara…</p>
        <p><b>Važno:</b> važeći javni sustav koristi koeficijente radnih mjesta; ne postoji jedan univerzalni “bod plaće” koji bi svaka bolnica proizvoljno određivala. Ovo nije obračunska isprava niti konačan neto iznos. Porez, osobni odbitak, bolovanje, dežurstva, pripravnost, posebna rješenja i druga prava mogu promijeniti konačnu isplatu.</p>
        <div id="payrollSources" class="payroll-sources"></div>
      </section>
    </section>

