    <section class="view" id="view-colleagues" data-view="colleagues">
      <div class="colleagues-title-row">
        <div><h1>Kolege</h1><p>Lokalni popis kolega dostupan je svima. Voditeljski račun dodatno može uvesti odvojene rasporede više djelatnika iz jednog skeniranja.</p></div>
        <button class="primary-btn" id="addColleagueBtn"><svg class="ui-icon" aria-hidden="true"><use href="assets/brand/icons.svg#icon-users"></use></svg>Dodaj kolegu</button>
      </div>
      <section class="card colleagues-card">
        <div class="card-head team-list-head"><div><h2>Popis kolega</h2><small>Ime i napomena bez cloud/team tvrdnji.</small></div></div>
        <div class="colleagues-list" id="colleaguesList"></div>
      </section>
      <section class="card colleagues-card team-card" id="teamSchedulesCard" hidden>
        <div class="card-head"><div><h2>Rasporedi tima</h2><small>Voditeljski profil · svaki djelatnik ostaje zaseban.</small></div><span class="account-status-badge is-authenticated">Voditelj</span></div>
        <div class="team-members-list" id="teamMembersList"></div>
      </section>
    </section>

