    <section class="view" id="view-scan" data-view="scan">
      <div class="scan-header">
        <button class="back-btn" data-route="home" aria-label="Natrag"><svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-chevron-left"></use></svg></button>
        <div class="brand-inline"><img src="assets/brand/logo.svg" alt="" width="40"><b>Takto</b></div>
      </div>

      <div class="scan-copy scan-copy--with-help">
        <div>
          <h1>Uvezi raspored</h1>
          <p>Dodaj jasnu fotografiju tablice, odaberi osobu i provjeri oznake prije spremanja.</p>
        </div>
        <button class="icon-btn scan-help-btn" id="scanHelpBtn" aria-label="Pomoć za skeniranje">?</button>
      </div>

      <div class="scan-flow-progress" aria-label="Koraci uvoza rasporeda">
        <span><b>1</b>Fotografija</span>
        <i aria-hidden="true"></i>
        <span><b>2</b>Poravnanje</span>
        <i aria-hidden="true"></i>
        <span><b>3</b>Osoba</span>
        <i aria-hidden="true"></i>
        <span><b>4</b>Provjera</span>
      </div>

      <section class="card scan-step-card scan-source-step">
        <div class="scan-step-head">
          <span class="scan-step-number">1</span>
          <div><h2>Dodaj fotografiju</h2><p>Fotografiraj cijeli raspored ili odaberi postojeću fotografiju. Tablica treba biti ravna, oštra i bez odsjaja.</p></div>
        </div>
        <div class="scan-source-actions">
          <button class="scan-source-btn scan-source-btn--camera" id="rescanBtn" type="button">
            <svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-camera"></use></svg>
            <span><b>Fotografiraj raspored</b><small>Otvori kameru</small></span>
          </button>
          <button class="scan-source-btn" id="galleryBtn" type="button">
            <svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-image"></use></svg>
            <span><b>Odaberi fotografiju</b><small>Učitaj postojeću sliku</small></span>
          </button>
        </div>
        <input class="visually-hidden" type="file" id="cameraInput" accept="image/*" capture="environment">
        <input class="visually-hidden" type="file" id="galleryInput" accept="image/*">
      </section>

      <section class="card scan-step-card scan-preview-step" id="scanPreview">
        <div class="scan-step-head">
          <span class="scan-step-number">2</span>
          <div><h2>Poravnaj i odaberi</h2><p>Provjeri orijentaciju fotografije. Za jednu osobu označi samo njezin vodoravni redak.</p></div>
        </div>
        <div class="scan-preview-frame">
          <div class="scan-corners" aria-hidden="true"></div>
          <div class="scan-image-stage" id="scanImageStage" tabindex="0" role="button" aria-label="Odaberi redak osobe na fotografiji">
            <img id="scanPreviewImage" class="scan-preview-image" alt="Odabrana fotografija rasporeda">
            <div class="scan-row-crop" id="scanRowCrop" hidden aria-hidden="true"><i></i></div>
          </div>
          <div class="scan-empty-state" id="scanEmptyState">
            <svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-scan"></use></svg>
            <b>Dodaj fotografiju u prvom koraku</b>
            <span>Nakon učitavanja ovdje ćeš poravnati i pregledati tablicu.</span>
          </div>
        </div>

        <div class="scan-transform-actions" id="scanTransformActions" hidden>
          <button type="button" class="secondary-btn" id="scanRotateLeft" aria-label="Okreni fotografiju lijevo"><svg class="ui-icon scan-rotate-left" aria-hidden="true"><use href="assets/brand/icons.svg#icon-refresh"></use></svg>Okreni lijevo</button>
          <button type="button" class="secondary-btn" id="scanRotateRight" aria-label="Okreni fotografiju desno"><svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-refresh"></use></svg>Okreni desno</button>
        </div>

        <div class="scan-mode-card">
          <label class="scan-mode-toggle">
            <input type="checkbox" id="scanSinglePersonToggle" checked>
            <span><b>Jedna osoba · preporučeno</b><small>Isključi samo kada namjerno želiš uvesti cijeli tim.</small></span>
          </label>
          <div class="scan-crop-controls" id="scanCropControls" hidden>
            <div class="scan-crop-row-nav" id="scanCropRowNav" hidden>
              <button type="button" class="secondary-btn" id="scanCropPrev" aria-label="Prethodni prepoznati redak">↑ Prethodni</button>
              <b id="scanCropRowLabel">Redak —</b>
              <button type="button" class="secondary-btn" id="scanCropNext" aria-label="Sljedeći prepoznati redak">Sljedeći ↓</button>
            </div>
            <div class="scan-crop-manual-actions">
              <button type="button" class="secondary-btn" id="scanCropUp">↑ Pomakni gore</button>
              <button type="button" class="secondary-btn" id="scanCropDown">↓ Pomakni dolje</button>
              <button type="button" class="secondary-btn" id="scanCropReset">Vrati odabir</button>
            </div>
            <details class="scan-fine-tune">
              <summary>Fino podesi rubove odabira</summary>
              <label><span>Gornji rub odabira</span><input type="range" id="scanCropTop" min="0" max="98" step="0.1" value="34"></label>
              <label><span>Donji rub odabira</span><input type="range" id="scanCropBottom" min="2" max="100" step="0.1" value="38"></label>
            </details>
            <small id="scanCropHint">Dodirni osobu ili koristi gumbe Pomakni gore / Pomakni dolje.</small>
            <button type="button" class="primary-btn scan-person-confirm" id="scanSinglePersonBtn">Prepoznaj označenu osobu</button>
          </div>
        </div>

        <div class="scan-status" id="scanStatus" role="status" aria-live="polite">
          <span>Čeka se fotografija.</span>
          <div class="scan-progress" aria-hidden="true"><i></i></div>
        </div>
      </section>

      <section class="card scan-step-card">
        <div class="scan-step-head">
          <span class="scan-step-number">3</span>
          <div><h2>Odaberi osobu i mjesec</h2><p>Takto nikada ne spaja rasporede različitih osoba. Provjeri ime prije uvoza.</p></div>
        </div>
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

      <section class="card scan-step-card scan-review-step">
        <div class="scan-step-head">
          <span class="scan-step-number">4</span>
          <div><h2>Provjeri i spremi</h2><p>Pregledaj svaku prepoznatu oznaku. Prazan dan ostaje „Nije označeno”.</p></div>
          <span class="success-pill" id="recognitionStatus">Odaberi osobu</span>
        </div>
        <div class="recognition-days" id="recognitionDays"></div>
        <div class="scan-edit-actions">
          <button id="editRecognitionBtn"><svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-edit"></use></svg>Uredi oznake</button>
          <button id="rescanSecondary"><svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-camera"></use></svg>Nova fotografija</button>
        </div>

        <div class="scan-ai-panel" id="scanAiPanel" hidden>
          <button class="secondary-btn primary-btn--full" id="scanAiVerifyBtn">
            <svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-scan"></use></svg>
            <span id="scanAiVerifyLabel">Dodatna provjera cijelog rasporeda</span>
          </button>
          <p>Opcionalna mrežna provjera za guste ili slabije čitljive tablice. Fotografija napušta uređaj samo kada to izričito potvrdiš.</p>
        </div>
        <div class="scan-conflict-panel" id="scanConflictPanel" hidden>
          <strong id="scanConflictCount">Za provjeru: 0 nejasnih stavki</strong>
          <p>Dvije provjere razlikuju se u ovim stavkama. Spremanje ostaje blokirano dok ne potvrdiš svaku nejasnu vrijednost.</p>
          <button class="secondary-btn primary-btn--full" id="scanReviewNextBtn">Pregledaj nejasne stavke</button>
        </div>

        <div class="scan-save-actions">
          <button class="primary-btn" id="saveSchedule"><svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-check"></use></svg>Spremi moj raspored</button>
          <button class="secondary-btn team-import-btn" id="saveTeamSchedules" hidden><svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-users"></use></svg>Spremi rasporede tima</button>
        </div>
        <p class="team-import-note" id="teamImportNote" hidden>Svaki djelatnik sprema se kao odvojeni raspored. Rasporedi različitih osoba nikada se ne spajaju.</p>
      </section>

      <dialog class="app-dialog" id="scanAiConsentDialog">
        <div class="dialog-card">
          <div class="dialog-head"><div><h2>Dodatna analiza fotografije</h2><p>Opcionalna mrežna provjera.</p></div><form method="dialog"><button class="icon-btn" aria-label="Zatvori">×</button></form></div>
          <p>Za ovu opcionalnu provjeru fotografija napušta uređaj i šalje se Takto poslužitelju te vanjskom servisu za analizu. Osnovno prepoznavanje radi i bez ove provjere.</p>
          <div class="dialog-actions"><form method="dialog"><button class="secondary-btn">Bez dodatne provjere</button></form><button class="primary-btn" id="scanAiConsentConfirm">Pokreni dodatnu provjeru</button></div>
        </div>
      </dialog>
      <dialog class="app-dialog" id="scanConflictDialog">
        <div class="dialog-card">
          <div class="dialog-head"><div><h2>Nejasna stavka</h2><p id="scanConflictTitle">—</p></div><form method="dialog"><button class="icon-btn" aria-label="Zatvori">×</button></form></div>
          <p id="scanConflictValues">—</p>
          <div class="scan-conflict-actions">
            <button class="primary-btn" id="scanConflictLocal">Zadrži prvo</button>
            <button class="secondary-btn" id="scanConflictAi">Odaberi dodatnu provjeru</button>
            <button class="secondary-btn" id="scanConflictEmpty">Ostavi prazno</button>
          </div>
          <label class="dialog-field"><span>Druga oznaka</span><input id="scanConflictCustom" maxlength="8" autocomplete="off" inputmode="text" aria-describedby="scanConflictCustomHelp"><small id="scanConflictCustomHelp">1–8 slova ili brojki, bez razmaka.</small></label>
          <div class="dialog-actions"><button class="primary-btn" id="scanConflictCustomApply">Primijeni oznaku</button></div>
        </div>
      </dialog>
    </section>
