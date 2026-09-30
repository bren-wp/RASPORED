    <section class="view" id="view-payroll" data-view="payroll">
      <div class="payroll-title-row">
        <div>
          <h1>Okvirna plaća</h1>
          <p>Procjena bruto plaće za bolničke zdravstvene ustanove prema službenom radnom mjestu, osnovici, stažu i stvarnoj Evidenciji sati.</p>
        </div>
        <label class="payroll-month"><span>Mjesec</span><input type="month" id="payrollMonth" min="2026-01" max="2026-12"></label>
      </div>

      <div class="payroll-layout">
        <section class="card payroll-settings-card">
          <div class="card-head">
            <div><h2>Ustanova i radno mjesto</h2><small>Popis ustanova: Ministarstvo zdravstva RH</small></div>
            <span class="payroll-badge">2026</span>
          </div>

          <label class="payroll-field">
            <span>Bolnica / ustanova</span>
            <select id="payrollInstitution"></select>
          </label>

          <label class="payroll-field">
            <span>Obračunski profil dodataka</span>
            <select id="payrollRateProfile"></select>
          </label>
          <div class="payroll-profile-note" id="payrollProfileNote"></div>

          <label class="payroll-field">
            <span>Odaberi radno mjesto</span>
            <select id="payrollRole"></select>
          </label>

          <div class="payroll-two">
            <label class="payroll-field"><span>Koeficijent</span><input id="payrollCoefficient" type="number" min="1" max="8" step="0.01" inputmode="decimal"></label>
            <label class="payroll-field"><span>Godine staža</span><input id="payrollYears" type="number" min="0" max="60" step="1" inputmode="numeric"></label>
          </div>

          <div class="payroll-two">
            <label class="payroll-field"><span>Ručna osnovica (€)</span><input id="payrollCustomBase" type="number" min="0" max="10000" step="0.01" inputmode="decimal" placeholder="Automatski iz NN 11/2026"></label>
            <label class="payroll-field"><span>Dodatak po rješenju/ugovoru (%)</span><input id="payrollExtraPercent" type="number" min="0" max="100" step="0.1" inputmode="decimal" value="0"></label>
          </div>

          <div class="payroll-advanced">
            <div class="payroll-advanced-head">
              <b>Napredni obračunski sati</b>
              <small>Prazno polje znači automatsku procjenu gdje je to moguće. Turnus i druga smjena ne računaju se automatski na iste sate.</small>
            </div>
            <div class="payroll-two">
              <label class="payroll-field"><span>Prekovremeni sati</span><input id="payrollOvertimeHours" type="number" min="0" max="250" step="0.25" inputmode="decimal" placeholder="Automatski"></label>
              <label class="payroll-field"><span>Sati turnusa 5%</span><input id="payrollTurnusHours" type="number" min="0" max="300" step="0.25" inputmode="decimal" placeholder="0"></label>
            </div>
            <div class="payroll-two">
              <label class="payroll-field"><span>Sati druge smjene 10%</span><input id="payrollSecondShiftHours" type="number" min="0" max="300" step="0.25" inputmode="decimal" placeholder="0"></label>
              <label class="payroll-field"><span>Korekcija / naknada bruto (€)</span><input id="payrollGrossAdjustment" type="number" min="-10000" max="10000" step="0.01" inputmode="decimal" placeholder="0"></label>
            </div>
            <small class="payroll-help">Korekcija služi za stavke koje nije moguće pouzdano izvesti samo iz ulaza/izlaza, npr. razliku naknade godišnjeg odmora po tromjesečnom prosjeku, bolovanje ili pojedinačnu bruto korekciju.</small>
          </div>

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
            <div><small>Cijena sata + staž</small><b id="payrollHourly">—</b></div>
            <div><small>Osnovna bruto + staž</small><b id="payrollBasicGross">—</b></div>
            <div><small>Osnovica prekovremenih</small><b id="payrollOvertimeBase">—</b></div>
            <div><small>Dodaci i korekcije</small><b id="payrollAdditions">—</b></div>
          </div>
        </section>
      </div>

      <section class="card payroll-breakdown-card">
        <div class="card-head">
          <div>
            <h2>Obračunski sati i dodaci</h2>
            <small>Noć, subota, nedjelja i blagdan dolaze iz Evidencije sati. Prekovremeni se može ručno ispraviti, a turnus i druga smjena unose se zasebno radi točnosti.</small>
          </div>
        </div>
        <div class="payroll-breakdown" id="payrollBreakdown"></div>
      </section>

      <section class="card payroll-legal-card">
        <h2>Kako je procjena napravljena</h2>
        <p id="payrollLegalText">Učitavanje službenih parametara…</p>
        <p><b>Važno:</b> za javne službe isti službeni naziv radnog mjesta ima isti koeficijent prema državnoj Uredbi; bolnica ne dobiva svoj zaseban “bod”. Razlike nastaju kada ustanova isti svakodnevni naziv posla mapira na drugo službeno radno mjesto, zbog organizacije rada ili zbog dodatnih prava. Odabir ustanove zato ne mijenja koeficijent bez provjerljive osnove.</p>
        <p><b>Granice procjene:</b> ovo nije obračunska isprava niti konačan neto iznos. Godišnji odmor, bolovanje, dežurstvo, pripravnost, posebna rješenja, porezni osobni odbitak i druga individualna prava mogu promijeniti konačnu isplatu.</p>
        <div id="payrollSources" class="payroll-sources"></div>
      </section>
    </section>

