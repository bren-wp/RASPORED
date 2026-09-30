    <section class="view" id="view-payroll" data-view="payroll">
      <div class="payroll-title-row">
        <div>
          <h1>Okvirna plaća</h1>
          <p>Procjena za javni sektor u Hrvatskoj iz službene osnovice/koeficijenta, mjesta prebivališta i stvarne Evidencije sati. Kalendar i raspored ostaju glavni dio aplikacije.</p>
        </div>
        <label class="payroll-month"><span>Mjesec</span><input type="month" id="payrollMonth" min="2026-01" max="2026-12"></label>
      </div>

      <div class="payroll-layout">
        <section class="card payroll-settings-card">
          <div class="card-head">
            <div><h2>Ustanova i radno mjesto</h2><small>Županija → prebivalište/porez → sektor → ustanova → radno mjesto</small></div>
            <span class="payroll-badge">2026</span>
          </div>

          <div class="payroll-two">
            <label class="payroll-field">
              <span>Županija ustanove</span>
              <select id="payrollCounty"></select>
            </label>
            <label class="payroll-field">
              <span>Grad/općina prebivališta — porez</span>
              <select id="payrollResidence"></select>
            </label>
          </div>

          <label class="payroll-field payroll-custom-residence" id="payrollResidenceCustomWrap" hidden>
            <span>Grad/općina prebivališta</span>
            <input id="payrollResidenceCustom" type="text" maxlength="100" autocomplete="address-level2" placeholder="Upiši grad ili općinu">
          </label>

          <div class="payroll-two payroll-tax-row">
            <label class="payroll-field"><span>Niža porezna stopa (%)</span><input id="payrollTaxLower" type="number" min="0" max="50" step="0.1" inputmode="decimal"></label>
            <label class="payroll-field"><span>Viša porezna stopa (%)</span><input id="payrollTaxHigher" type="number" min="0" max="50" step="0.1" inputmode="decimal"></label>
          </div>
          <div class="payroll-inline-note" id="payrollTaxNote">Porez se određuje prema gradu/općini prebivališta, ne prema županiji u kojoj radiš.</div>

          <div class="payroll-two">
            <label class="payroll-field">
              <span>Sektor</span>
              <select id="payrollSector"></select>
            </label>
            <label class="payroll-field">
              <span>Ustanova / tijelo</span>
              <select id="payrollInstitution"></select>
            </label>
          </div>
          <label class="payroll-field payroll-custom-institution" id="payrollInstitutionCustomWrap" hidden>
            <span>Naziv ustanove</span>
            <input id="payrollInstitutionCustom" type="text" maxlength="160" autocomplete="organization" placeholder="Upiši naziv ustanove">
          </label>

          <label class="payroll-field">
            <span>Radno mjesto</span>
            <select id="payrollRole"></select>
          </label>

          <div class="payroll-two">
            <label class="payroll-field"><span>Koeficijent</span><input id="payrollCoefficient" type="number" min="0.1" max="10" step="0.01" inputmode="decimal"></label>
            <label class="payroll-field"><span>Godine staža</span><input id="payrollYears" type="number" min="0" max="60" step="1" inputmode="numeric"></label>
          </div>
          <div class="payroll-two">
            <label class="payroll-field"><span>Ručna osnovica (€)</span><input id="payrollCustomBase" type="number" min="0" max="10000" step="0.01" inputmode="decimal" placeholder="Automatski ako je propisana"></label>
            <label class="payroll-field"><span>Osobni odbitak (€)</span><input id="payrollPersonalAllowance" type="number" min="0" max="10000" step="0.01" inputmode="decimal" value="600"></label>
          </div>
          <label class="payroll-field"><span>Dodatak po rješenju/ugovoru (%)</span><input id="payrollExtraPercent" type="number" min="0" max="100" step="0.1" inputmode="decimal" value="0"></label>

          <div class="payroll-switches">
            <label class="setting-row payroll-switch">
              <span><b>Druga smjena</b><small>Uključi samo ako pravo postoji i rad u drugoj smjeni se primjenjuje na evidentirane sate.</small></span>
              <input type="checkbox" id="payrollSecondShift">
            </label>
            <label class="setting-row payroll-switch">
              <span><b>Turnus</b><small>Uključi samo ako je rad organiziran u turnusu i za odabrani režim postoji potvrđena stopa.</small></span>
              <input type="checkbox" id="payrollTurnus">
            </label>
          </div>

          <div class="payroll-role-note" id="payrollRoleNote"></div>
        </section>

        <section class="card payroll-result-card">
          <div class="payroll-estimate-head">
            <div><small>Procijenjeni bruto</small><strong id="payrollGross">0,00 €</strong><span id="payrollEvidenceHint">Na temelju evidentiranih sati.</span></div>
            <span class="metric-icon"><svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-chart"></use></svg></span>
          </div>
          <div class="payroll-net-box">
            <small>Okvirni neto</small>
            <strong id="payrollNet">—</strong>
            <span id="payrollTaxSummary">Neto koristi uneseni osobni odbitak i stopu prebivališta.</span>
          </div>
          <div class="payroll-result-grid">
            <div><small>Osnovica</small><b id="payrollBase">—</b></div>
            <div><small>Koeficijent</small><b id="payrollCoefResult">—</b></div>
            <div><small>Staž</small><b id="payrollSeniority">—</b></div>
            <div><small>Mjesečni fond</small><b id="payrollFund">—</b></div>
            <div><small>Bruto satnica</small><b id="payrollHourlyGross">—</b></div>
            <div><small>Radni dani iz evidencije</small><b id="payrollWorkedDays">—</b></div>
            <div><small>Prosj. bruto / radni dan</small><b id="payrollDailyGross">—</b></div>
            <div><small>Prosj. neto / radni dan</small><b id="payrollDailyNet">—</b></div>
            <div><small>Osnovna bruto + staž</small><b id="payrollBasicGross">—</b></div>
            <div><small>Dodaci iz evidencije</small><b id="payrollAdditions">—</b></div>
          </div>
          <p class="payroll-day-note">“Po radnom danu” je prosječna vrijednost plaće po evidentiranom radnom danu, a nije službena dnevnica za službeni put.</p>
        </section>
      </div>

      <section class="card payroll-breakdown-card">
        <div class="card-head"><div><h2>Obračunski sati i dodaci</h2><small>Planirani raspored i stvarno odrađeni sati ostaju odvojeni. Ovdje se koriste stvarni podaci iz Evidencije sati.</small></div></div>
        <div class="payroll-breakdown" id="payrollBreakdown"></div>
      </section>

      <section class="card payroll-legal-card">
        <h2>Točnost i izvori</h2>
        <p id="payrollLegalText">Učitavanje službenih parametara…</p>
        <p><b>Važno:</b> za javne i državne službe 2026. osnovice i službeni koeficijenti mogu se automatski postaviti kada postoji odgovarajući propis. Vrtići, lokalna uprava, dio vatrogasnih prava i druga lokalno uređena radna mjesta mogu imati vlastitu osnovicu, koeficijent ili povoljniji kolektivni ugovor; tada aplikacija traži ručni/verificirani unos umjesto izmišljanja vrijednosti.</p>
        <p>Okvirni neto nije obračunska isprava. Ne uključuje automatski sva osobna porezna prava, bolovanja, godišnji odmor po prosjeku, dežurstva, pripravnost, posebne uvjete rada, neoporezive primitke, prijevoz, obustave ili druga individualna prava.</p>
        <div id="payrollSources" class="payroll-sources"></div>
      </section>
    </section>
