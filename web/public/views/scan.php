    <section class="view" id="view-scan" data-view="scan">
      <div class="scan-header"><button class="back-btn" data-route="home" aria-label="Natrag"><svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-chevron-left"></use></svg></button><div class="brand-inline"><img src="assets/brand/logo.svg" alt="" width="40"><b>Takto</b></div></div>
      <div class="scan-copy scan-copy--with-help"><div><h1>Skeniraj raspored</h1><p>Slikaj cijelu tablicu ili učitaj fotografiju.<br>Moraju biti vidljivi svi redci osoba i svi stupci dana.</p></div><button class="icon-btn scan-help-btn" id="scanHelpBtn" aria-label="Pomoć za skeniranje">?</button></div>
      <section class="scan-preview" id="scanPreview">
        <div class="scan-corners" aria-hidden="true"></div>
        <div class="scan-image-stage" id="scanImageStage" tabindex="0" role="button" aria-label="Odaberi redak osobe na fotografiji">
          <img id="scanPreviewImage" class="scan-preview-image" alt="Odabrana fotografija rasporeda">
          <div class="scan-row-crop" id="scanRowCrop" hidden aria-hidden="true"><i></i></div>
        </div>
        <div class="scan-empty-state" id="scanEmptyState">
          <svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-scan"></use></svg>
          <b>Raspored nije učitan</b>
          <span>Skeniraj papirnati raspored ili odaberi fotografiju iz galerije.</span>
        </div>
        <div class="scan-actions">
          <button id="rescanBtn"><svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-camera"></use></svg>Ponovno skeniraj</button>
          <button id="galleryBtn"><svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-image"></use></svg>Odaberi iz galerije</button>
        </div>
        <div class="scan-single-mode">
          <label><input type="checkbox" id="scanSinglePersonToggle"><span><b>Samo jedna osoba</b><small>Označi vodoravni redak: ime i prezime + svi dani</small></span></label>
          <div class="scan-crop-controls" id="scanCropControls" hidden>
            <span>Gornja granica</span><input type="range" id="scanCropTop" min="0" max="98" step="0.1" value="34">
            <span>Donja granica</span><input type="range" id="scanCropBottom" min="2" max="100" step="0.1" value="38">
            <div class="scan-crop-row-nav" id="scanCropRowNav" hidden>
              <button type="button" class="secondary-btn" id="scanCropPrev" aria-label="Prethodni prepoznati redak">↑ Prethodni</button>
              <b id="scanCropRowLabel">Redak —</b>
              <button type="button" class="secondary-btn" id="scanCropNext" aria-label="Sljedeći prepoznati redak">Sljedeći ↓</button>
            </div>
            <small id="scanCropHint">Dodirni osobu na fotografiji za brzo postavljanje plavog pojasa.</small>
            <button type="button" class="secondary-btn" id="scanSinglePersonBtn">Skeniraj označenu osobu</button>
          </div>
        </div>
        <div class="scan-status" id="scanStatus" role="status" aria-live="polite">
          <span>Spremno za učitavanje rasporeda.</span>
          <div class="scan-progress" aria-hidden="true"><i></i></div>
        </div>
        <input class="visually-hidden" type="file" id="cameraInput" accept="image/*" capture="environment">
        <input class="visually-hidden" type="file" id="galleryInput" accept="image/*">
      </section>
      <section class="card scan-card">
        <h2>Odaberi osobu</h2>
        <p>Ako raspored sadrži više djelatnika, odaberi samo ime i prezime osobe čiji raspored želiš uvesti.</p>
        <div class="scan-person-picker">
          <button class="select-row" id="scanPersonButton" aria-haspopup="listbox" aria-expanded="false">
            <svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-user"></use></svg>
            <b id="scanPersonLabel">Odaberi ime i prezime</b>
            <svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-chevron-down"></use></svg>
          </button>
          <div class="scan-person-menu" id="scanPersonMenu" role="listbox" hidden></div>
        </div>
        <div class="scan-month-field">
          <span>Mjesec rasporeda</span>
          <div class="scan-month-stepper" aria-label="Mjesec rasporeda">
            <button type="button" class="icon-btn" id="scanMonthPrev" aria-label="Prethodni mjesec"><svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-chevron-left"></use></svg></button>
            <b id="scanMonthLabel">—</b>
            <button type="button" class="icon-btn" id="scanMonthNext" aria-label="Sljedeći mjesec"><svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-chevron-right"></use></svg></button>
          </div>
        </div>
      </section>
      <section class="card scan-card"><div class="card-head"><div><h2>Provjera rasporeda</h2><p>Pregledaj prepoznate smjene i po potrebi ih ispravi.</p></div><span class="success-pill" id="recognitionStatus">Odaberi osobu</span></div><div class="recognition-days" id="recognitionDays"></div><div class="scan-edit-actions"><button id="editRecognitionBtn"><svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-edit"></use></svg>Uredi</button><button id="rescanSecondary"><svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-scan"></use></svg>Ponovno skeniraj</button></div></section>
      <div class="scan-ai-panel" id="scanAiPanel" hidden>
        <button class="secondary-btn primary-btn--full" id="scanAiVerifyBtn">
          <svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-scan"></use></svg>
          <span id="scanAiVerifyLabel">AI provjera cijelog rasporeda</span>
        </button>
        <p>Opcionalna dodatna provjera za guste tablice. Fotografija napušta uređaj samo kada to izričito pokreneš.</p>
      </div>
      <div class="scan-conflict-panel" id="scanConflictPanel" hidden>
        <strong id="scanConflictCount">Za provjeru: 0 nejasnih stavki</strong>
        <p>Dvije provjere razlikuju se u ovim stavkama. Uvoz ostaje blokiran dok ne potvrdiš svaku nejasnu vrijednost.</p>
        <button class="secondary-btn primary-btn--full" id="scanReviewNextBtn">Pregledaj nejasne stavke</button>
      </div>
      <dialog class="app-dialog" id="scanAiConsentDialog">
        <div class="dialog-card">
          <div class="dialog-head"><div><h2>AI analiza fotografije</h2><p>Opcionalna mrežna provjera.</p></div><form method="dialog"><button class="icon-btn" aria-label="Zatvori">×</button></form></div>
          <p>Za ovu opcionalnu provjeru fotografija napušta uređaj i šalje se Takto poslužitelju te vanjskom servisu za analizu. Lokalno prepoznavanje radi i bez ove provjere.</p>
          <div class="dialog-actions"><form method="dialog"><button class="secondary-btn">Ostani na lokalnom prepoznavanju</button></form><button class="primary-btn" id="scanAiConsentConfirm">Pokreni dodatnu provjeru</button></div>
        </div>
      </dialog>
      <dialog class="app-dialog" id="scanConflictDialog">
        <div class="dialog-card">
          <div class="dialog-head"><div><h2>Nejasna stavka</h2><p id="scanConflictTitle">—</p></div><form method="dialog"><button class="icon-btn" aria-label="Zatvori">×</button></form></div>
          <p id="scanConflictValues">—</p>
          <div class="scan-conflict-actions">
            <button class="primary-btn" id="scanConflictLocal">Zadrži lokalno</button>
            <button class="secondary-btn" id="scanConflictAi">Odaberi AI</button>
            <button class="secondary-btn" id="scanConflictEmpty">Ostavi prazno</button>
          </div>
          <label class="dialog-field"><span>Druga oznaka</span><input id="scanConflictCustom" maxlength="8" autocomplete="off" inputmode="text" aria-describedby="scanConflictCustomHelp"><small id="scanConflictCustomHelp">1–8 slova ili brojki, bez razmaka.</small></label>
          <div class="dialog-actions"><button class="primary-btn" id="scanConflictCustomApply">Primijeni oznaku</button></div>
        </div>
      </dialog>
      <button class="primary-btn primary-btn--full" id="saveSchedule"><svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-check"></use></svg>Spremi raspored</button>
      <button class="secondary-btn primary-btn--full team-import-btn" id="saveTeamSchedules" hidden><svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-users"></use></svg>Uvezi sve djelatnike u tim</button>
      <p class="team-import-note" id="teamImportNote" hidden>Možeš spremiti sve pouzdano prepoznate djelatnike kao odvojene rasporede tima i bez korisničkog računa. Rasporedi se nikada ne spajaju među osobama.</p>
    </section>

