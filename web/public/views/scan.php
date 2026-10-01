    <section class="view" id="view-scan" data-view="scan">
      <div class="scan-header"><button class="back-btn" data-route="home" aria-label="Natrag"><svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-chevron-left"></use></svg></button><div class="brand-inline"><img src="assets/brand/logo.svg" alt="" width="40"><b>RASPORED</b></div></div>
      <div class="scan-copy scan-copy--with-help"><div><h1>Skeniraj raspored</h1><p>Slikaj cijelu tablicu ili učitaj fotografiju.<br>Moraju biti vidljivi svi redci osoba i svi stupci dana.</p></div><button class="icon-btn scan-help-btn" id="scanHelpBtn" aria-label="Pomoć za skeniranje">?</button></div>
      <section class="scan-preview" id="scanPreview">
        <div class="scan-corners" aria-hidden="true"></div>
        <img id="scanPreviewImage" class="scan-preview-image" alt="Odabrana fotografija rasporeda">
        <div class="scan-empty-state" id="scanEmptyState">
          <svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-scan"></use></svg>
          <b>Raspored nije učitan</b>
          <span>Skeniraj papirnati raspored ili odaberi fotografiju iz galerije.</span>
        </div>
        <div class="scan-actions">
          <button id="rescanBtn"><svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-camera"></use></svg>Ponovno skeniraj</button>
          <button id="galleryBtn"><svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-image"></use></svg>Odaberi iz galerije</button>
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
          AI provjera cijelog rasporeda
        </button>
        <p>Opcionalna druga provjera za guste tablice. Slika se šalje RASPORED poslužitelju i OpenAI API-ju samo kad ovo izričito pokreneš. API ključ ostaje isključivo na poslužitelju.</p>
      </div>
      <div class="scan-conflict-panel" id="scanConflictPanel" hidden>
        <strong id="scanConflictCount">Za provjeru: 0 nejasnih stavki</strong>
        <p>Lokalni OCR i AI se ne slažu u ovim ćelijama. Uvoz ostaje blokiran dok ne potvrdiš svaku nejasnu stavku.</p>
        <button class="secondary-btn primary-btn--full" id="scanReviewNextBtn">Pregledaj nejasne stavke</button>
      </div>
      <dialog class="app-dialog" id="scanAiConsentDialog">
        <div class="dialog-card">
          <div class="dialog-head"><div><h2>AI analiza fotografije</h2><p>Opcionalna mrežna provjera.</p></div><form method="dialog"><button class="icon-btn" aria-label="Zatvori">×</button></form></div>
          <p>Fotografija rasporeda napušta uređaj: šalje se RASPORED backendu, koji je prosljeđuje AI servisu radi analize. Lokalni OCR radi i bez AI provjere.</p>
          <div class="dialog-actions"><form method="dialog"><button class="secondary-btn">Ostani na lokalnom OCR-u</button></form><button class="primary-btn" id="scanAiConsentConfirm">Pošalji na AI provjeru</button></div>
        </div>
      </dialog>
      <dialog class="app-dialog" id="scanConflictDialog">
        <div class="dialog-card">
          <div class="dialog-head"><div><h2>Nejasna OCR stavka</h2><p id="scanConflictTitle">—</p></div><form method="dialog"><button class="icon-btn" aria-label="Zatvori">×</button></form></div>
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

