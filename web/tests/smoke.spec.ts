import {test,expect} from "@playwright/test";
import {mockOcr,scanPeople,seedApp} from "./fixtures";

test.beforeEach(async ({page}) => {
  await seedApp(page);
});

test("responsive home uses production composition", async ({page}) => {
  await page.goto("/");
  const width=page.viewportSize()?.width ?? 1440;
  if(width<=820){
    await expect(page.getByText("Današnja smjena")).toBeVisible();
    await expect(page.getByRole("button",{name:/Skeniraj raspored/i}).last()).toBeVisible();
  }else{
    await expect(page.getByRole("heading",{name:/Dobro došao, Ivana/})).toBeVisible();
    await expect(page.locator("#calendarGrid")).toBeVisible();
  }
});

test("calendar and statistics remain interactive", async ({page}) => {
  await page.goto("/");
  await page.locator('[data-route="calendar"]:visible').first().click();
  await expect(page.locator("#calendarGridMobile")).toBeVisible();
  await page.locator('[data-route="stats"]:visible').first().click();
  await expect(page.getByText("Ukupno odrađeno sati")).toBeVisible();
  if((page.viewportSize()?.width ?? 1440)<=820){
    await page.locator("#statsRefreshBtn").click();
    await expect(page.locator("#toast")).toContainText("Podaci su osvježeni.");
  }
  await page.locator("#statsPeriod").click();
  await expect(page.locator("#statsPeriodMenu")).toBeVisible();
});

test("scan performs OCR and exposes multiple invented employees", async ({page}) => {
  await mockOcr(page);
  await page.goto("/");
  await page.locator('[data-route="scan"]:visible').first().click();
  const input=page.locator("#galleryInput");
  await input.setInputFiles({
    name:"smjene.png",
    mimeType:"image/png",
    buffer:Buffer.from("89504e470d0a1a0a","hex")
  });
  await expect(page.locator("#scanStatus")).toContainText("Prepoznate su 3 osobe");
  await expect(page.locator("#scanPersonMenu [data-scan-person]")).toHaveCount(3);
  await expect(page.locator("#saveSchedule")).toBeDisabled();
});

test("scan imports only the explicitly selected employee schedule", async ({page}) => {
  await mockOcr(page);
  await page.goto("/");
  await page.locator('[data-route="scan"]:visible').first().click();
  await page.locator("#galleryInput").setInputFiles({
    name:"smjene.png",mimeType:"image/png",buffer:Buffer.from("89504e470d0a1a0a","hex")
  });
  await page.locator("#scanPersonButton").click();
  await page.locator('#scanPersonMenu [data-scan-person="1"]').click();
  await expect(page.locator("#scanPersonLabel")).toContainText("LUKA BABIĆ");
  await page.locator("#saveSchedule").click();
  await expect(page.locator('[data-view="calendar"]')).toBeVisible();
  await expect(page.locator('[data-date="2026-10-01"] .code').first()).toHaveText("GO");
  await expect(page.locator('[data-date="2026-10-04"] .code').first()).toHaveText("D");
  await expect(page.locator('[data-date="2026-10-02"] .code')).toHaveCount(0);
});

test("production scan cannot import before OCR and person selection", async ({page}) => {
  await page.goto("/");
  await page.locator('[data-route="scan"]:visible').first().click();
  await expect(page.locator("#scanEmptyState")).toBeVisible();
  await expect(page.locator("#scanPersonButton")).toBeDisabled();
  await expect(page.locator("#saveSchedule")).toBeDisabled();
});

test("recognized schedule can be corrected before import", async ({page}) => {
  await seedApp(page,{withScan:true});
  await page.goto("/");
  await page.locator('[data-route="scan"]:visible').first().click();
  await expect(page.locator("#scanMonthLabel")).toHaveText("Listopad 2026.");
  await page.locator("#scanMonthNext").click();
  await expect(page.locator("#scanMonthLabel")).toHaveText("Studeni 2026.");
  await page.locator("#scanMonthPrev").click();
  await expect(page.locator("#scanMonthLabel")).toHaveText("Listopad 2026.");
  await page.locator("#editRecognitionBtn").click();
  const first=page.locator('[data-scan-day="1"]');
  await expect(first).toBeEnabled();
  await first.click();
  await expect(first).toContainText("N");
});

test("time evidence records and persists check-in and check-out", async ({page}) => {
  await page.goto("/");
  const width=page.viewportSize()?.width ?? 1440;
  if(width<=820) await page.getByRole("button",{name:/Evidentiraj ulaz\/izlaz/i}).click();
  else await page.locator('[data-route="hours"]:visible').first().click();

  await page.getByRole("button",{name:"Evidentiraj ulaz"}).click();
  await expect(page.locator("#hoursStatus")).toContainText("Rad je u tijeku");
  await page.locator("#hoursNote").fill("Redovna smjena");
  await page.getByRole("button",{name:"Evidentiraj izlaz"}).click();
  await expect(page.locator("#hoursStatus")).toContainText("spremljena");

  await page.reload();
  if(width<=820) await page.getByRole("button",{name:/Evidentiraj ulaz\/izlaz/i}).click();
  else await page.locator('[data-route="hours"]:visible').first().click();
  await expect(page.locator("#hoursHistory")).toContainText("Redovna smjena");
});

test("time evidence stores selected work type without inventing a special-duty rate", async ({page}) => {
  await page.goto("/");
  const width=page.viewportSize()?.width ?? 1440;
  if(width<=820) await page.getByRole("button",{name:/Evidentiraj ulaz\/izlaz/i}).click();
  else await page.locator('[data-route="hours"]:visible').first().click();

  await page.locator("#hoursWorkType").selectOption("duty");
  await page.getByRole("button",{name:"Evidentiraj ulaz"}).click();
  await expect(page.locator("#hoursHistory")).toContainText("Dežurstvo");

  const stored=await page.evaluate(() => {
    const rows=JSON.parse((window as any).RasporedDataStore.get("raspored.timeEntries.v1")||"[]");
    return rows[rows.length-1];
  });
  expect(stored.workType).toBe("duty");
});

test("profile, notifications and colleagues controls work", async ({page}) => {
  await page.goto("/");
  const width=page.viewportSize()?.width ?? 1440;
  if(width>820){
    await page.locator("#notificationBtn").click();
    await expect(page.locator("#notificationPanel")).toBeVisible();
    await expect(page.locator("#notificationText")).toContainText("Sljedeća smjena");
    await page.locator("#profileButton").click();
    await expect(page.locator("#profilePanel")).toBeVisible();
    await page.locator("#searchBtn").click();
    await expect(page.locator("#searchDialog")).toBeVisible();
    await page.locator("#searchInput").fill("statistika");
    await page.locator('[data-search-route="stats"]').click();
    await expect(page.locator('[data-view="stats"]')).toBeVisible();
    await page.locator('[data-route="colleagues"]:visible').first().click();
    await expect(page.getByText("Nikola Jurić")).toBeVisible();
    await page.locator("#addColleagueBtn").click();
    await expect(page.locator("#colleagueDialog")).toBeVisible();
    await page.locator("#colleagueNameInput").fill("Tomislav Marić");
    await page.locator("#colleagueNoteInput").fill("Odjel C");
    await page.locator("#colleagueForm").getByRole("button",{name:"Spremi kolegu"}).click();
    await expect(page.getByText("Tomislav Marić")).toBeVisible();
  }
  await page.locator('[data-route="settings"]:visible').first().click();
  await page.locator("#profileNameInput").fill("Sara Kovač");
  await page.locator("#saveProfileBtn").click();
  await expect(page.locator("#profileNameInput")).toHaveValue("Sara Kovač");
});

test("Web account registration stays optional and manager import keeps employees separate", async ({page},testInfo) => {
  await mockOcr(page);
  await page.goto("/");
  await page.locator('[data-route="settings"]:visible').first().click();
  await expect(page.locator("#accountStatusBadge")).toHaveText("Gost");
  await page.locator("#registerAccountBtn").click();
  const suffix=testInfo.project.name.replace(/[^a-z0-9]+/gi,"-").toLowerCase();
  await page.locator("#registerFirstName").fill("Maja");
  await page.locator("#registerLastName").fill("Perić");
  await page.locator("#registerEmail").fill("voditelj-"+suffix+"@example.test");
  await page.locator("#registerPhone").fill("+385 91 555 0101");
  await page.locator("#registerPassword").fill("RasporedTest2026");
  await page.locator("#registerAccountType").selectOption("manager");
  await Promise.all([
    page.waitForLoadState("domcontentloaded"),
    page.locator("#registerForm").getByRole("button",{name:"Izradi račun"}).click()
  ]);
  await page.locator('[data-route="settings"]:visible').first().click();
  await expect(page.locator("#accountStatusBadge")).toHaveText("Prijavljen");
  await expect(page.locator("#accountDetails")).toContainText("Voditelj tima");

  await page.locator('[data-route="scan"]:visible').first().click();
  await page.locator("#galleryInput").setInputFiles({
    name:"tim.png",mimeType:"image/png",buffer:Buffer.from("89504e470d0a1a0a","hex")
  });
  await expect(page.locator("#saveTeamSchedules")).toBeVisible();
  await page.locator("#saveTeamSchedules").click();
  await expect(page.locator("#teamSchedulesCard")).toBeVisible();
  await expect(page.locator("#teamMembersList .team-member-row")).toHaveCount(3);
  await expect(page.locator("#teamMembersList")).toContainText("ANA HORVAT");
  await expect(page.locator("#teamMembersList")).toContainText("LUKA BABIĆ");
  const team=await page.evaluate(()=>JSON.parse((window as any).RasporedDataStore.get("raspored.team.v1")||"[]"));
  expect(team).toHaveLength(3);
  expect(Object.keys(team[0].schedule).length).toBeGreaterThan(0);
  expect(team[0].schedule).not.toEqual(team[1].schedule);
});

test("individual Web account can import only its own recognized row", async ({page},testInfo) => {
  await mockOcr(page);
  await page.goto("/");
  await page.locator('[data-route="settings"]:visible').first().click();
  await page.locator("#registerAccountBtn").click();
  const suffix=testInfo.project.name.replace(/[^a-z0-9]+/gi,"-").toLowerCase();
  await page.locator("#registerFirstName").fill("Ana");
  await page.locator("#registerLastName").fill("Horvat");
  await page.locator("#registerEmail").fill("ana-"+suffix+"@example.test");
  await page.locator("#registerPhone").fill("+385 91 555 0102");
  await page.locator("#registerPassword").fill("RasporedTest2026");
  await Promise.all([
    page.waitForLoadState("domcontentloaded"),
    page.locator("#registerForm").getByRole("button",{name:"Izradi račun"}).click()
  ]);
  await page.locator('[data-route="scan"]:visible').first().click();
  await page.locator("#galleryInput").setInputFiles({
    name:"osobni.png",mimeType:"image/png",buffer:Buffer.from("89504e470d0a1a0a","hex")
  });
  await expect(page.locator("#scanPersonLabel")).toContainText("ANA HORVAT");
  await expect(page.locator('#scanPersonMenu [data-scan-person="1"]')).toBeDisabled();
  await expect(page.locator("#saveSchedule")).toBeEnabled();
  await expect(page.locator("#saveTeamSchedules")).toBeHidden();
});

test("Web export offers JSON backup and truthful print-to-PDF action", async ({page}) => {
  await page.goto("/");
  await page.locator('[data-route="settings"]:visible').first().click();
  const downloadPromise=page.waitForEvent("download");
  await page.locator("#exportJsonBtn").click();
  const download=await downloadPromise;
  expect(download.suggestedFilename()).toMatch(/^RASPORED-backup-\d{4}-\d{2}-\d{2}\.json$/);
  await expect(page.locator("#printPdfBtn")).toHaveText("Ispis / spremi kao PDF");
});

test("no demo or development labels ship in production UI", async ({page}) => {
  await page.goto("/");
  await expect(page.locator("body")).not.toContainText("Demo korisnik");
  await expect(page.locator("body")).not.toContainText("Lorem");
  await expect(page.locator("body")).not.toContainText("-dev");
});


test("web OCR parser keeps exact day columns and normalizes common OCR errors", async ({page}) => {
  await page.goto("/");
  const parsed=await page.evaluate(() => {
    const api=(window as any).RasporedWebOcr;
    return {
      rows:api.parseText("3 IVA KOVAČ 1 D 2 N 4 G0 7 B0 9 PD 12 SD"),
      monthNamed:api.detectMonth("SIJECANJ 2027."),
      monthNumeric:api.detectMonth("2026-10")
    };
  });
  expect(parsed.rows).toHaveLength(1);
  expect(parsed.rows[0].dayShifts).toEqual({
    "1":"D","2":"N","4":"GO","7":"BO","9":"PD","12":"SD"
  });
  expect(parsed.monthNamed).toEqual({year:2027,month:1});
  expect(parsed.monthNumeric).toEqual({year:2026,month:10});
});

test("overnight time evidence can be closed after midnight", async ({page}) => {
  await page.goto("/");
  await page.clock.setFixedTime(new Date("2026-10-17T01:30:00+02:00"));
  await page.evaluate(async () => {
    (window as any).RasporedDataStore.set("raspored.timeEntries.v1",JSON.stringify([
      {id:"night-active",date:"2026-10-16",in:"19:00",out:null,note:"Noćna smjena"}
    ]));
    await (window as any).RasporedDataStore.flush();
  });
  const width=page.viewportSize()?.width ?? 1440;
  if(width<=820) await page.getByRole("button",{name:/Evidentiraj ulaz\/izlaz/i}).click();
  else await page.locator('[data-route="hours"]:visible').first().click();
  await expect(page.locator("#hoursStatus")).toContainText("Rad je u tijeku");
  await expect(page.locator("#clockOutBtn")).toBeEnabled();
  await page.locator("#clockOutBtn").click();
  await expect(page.locator("#hoursStatus")).toContainText("spremljena");
});


test("main routes have no page-level horizontal overflow or fixed-nav overlap", async ({page}) => {
  for (const route of ["home","calendar","scan","stats","payroll","hours","settings"]) {
    await page.goto("/");
    if(route==="hours"){
      const width=page.viewportSize()?.width ?? 1440;
      if(width<=820) await page.getByRole("button",{name:/Evidentiraj ulaz\/izlaz/i}).click();
      else await page.locator('[data-route="hours"]:visible').first().click();
    }else if(route==="payroll"){
      const width=page.viewportSize()?.width ?? 1440;
      if(width<=820){
        await page.locator('[data-route="stats"]:visible').first().click();
        await page.locator(".stats-payroll-link").click();
      }else{
        await page.locator('[data-route="payroll"]:visible').first().click();
      }
    }else if(route!=="home"){
      await page.locator('[data-route="'+route+'"]:visible').first().click();
    }

    const metrics=await page.evaluate(() => ({
      viewport: window.innerWidth,
      pageWidth: document.documentElement.scrollWidth,
      bodyWidth: document.body.scrollWidth,
      route: document.body.dataset.routeCurrent
    }));
    expect(metrics.pageWidth,route+" document overflow").toBeLessThanOrEqual(metrics.viewport+1);
    expect(metrics.bodyWidth,route+" body overflow").toBeLessThanOrEqual(metrics.viewport+1);

    if((page.viewportSize()?.width ?? 1440)<=820){
      const nav=page.locator(".bottom-nav");
      if(route==="scan") await expect(nav).toBeHidden();
      else await expect(nav).toBeVisible();
    }
  }
});

async function openPayroll(page:any){
  const width=page.viewportSize()?.width ?? 1440;
  if(width<=820){
    await page.locator('[data-route="stats"]:visible').first().click();
    await page.locator(".stats-payroll-link").click();
  }else{
    await page.locator('[data-route="payroll"]:visible').first().click();
  }
  await expect(page.locator('[data-view="payroll"]')).toBeVisible();
}

test("salary estimator uses verified KBC Rijeka settings and persists choices", async ({page}) => {
  await page.goto("/");
  await openPayroll(page);
  await expect(page.locator("#payrollCounty")).toHaveValue("Primorsko-goranska");
  await expect(page.locator("#payrollResidence")).toHaveValue("Rijeka");
  await expect(page.locator("#payrollTaxLower")).toHaveValue("20");
  await expect(page.locator("#payrollTaxHigher")).toHaveValue("25");
  await expect(page.locator("#payrollInstitution")).not.toHaveValue("__manual__");

  await page.locator("#payrollRole").selectOption("health-portir");
  await expect(page.locator("#payrollCoefficient")).toHaveValue("1.39");
  await page.locator("#payrollYears").fill("10");
  await page.locator("#payrollYears").blur();
  await page.locator("#payrollTurnus").check();
  await page.evaluate(async()=>{await (window as any).RasporedDataStore.flush()});

  await expect(page.locator("#payrollBase")).toContainText("1.025");
  await expect(page.locator("#payrollCoefResult")).toHaveText("1,39");
  await expect(page.locator("#payrollGross")).not.toHaveText("0,00 €");
  await expect(page.locator("#payrollNet")).not.toHaveText("—");
  await expect(page.locator("#payrollDailyGross")).not.toHaveText("—");
  await expect(page.locator("#payrollLegalText")).toContainText("Noć 50");

  await page.reload();
  await openPayroll(page);
  await expect(page.locator("#payrollRole")).toHaveValue("health-portir");
  await expect(page.locator("#payrollCoefficient")).toHaveValue("1.39");
  await expect(page.locator("#payrollYears")).toHaveValue("10");
  await expect(page.locator("#payrollTurnus")).toBeChecked();
});

test("salary estimator switches between police, fire and manual local regimes", async ({page}) => {
  await page.goto("/");
  await openPayroll(page);

  await page.locator("#payrollSector").selectOption("Policija");
  await expect(page.locator("#payrollRole")).toHaveValue("police-station");
  await expect(page.locator("#payrollCoefficient")).toHaveValue("1.70");
  await expect(page.locator("#payrollLegalText")).toContainText("Noć 50");

  await page.locator("#payrollSector").selectOption("Vatrogastvo");
  await expect(page.locator("#payrollRole")).toHaveValue("firefighter");
  await expect(page.locator("#payrollCoefficient")).toHaveValue("1.10");
  await expect(page.locator("#payrollSecondShift")).toBeDisabled();
  await expect(page.locator("#payrollTurnus")).toBeDisabled();

  await page.locator("#payrollSector").selectOption("Lokalna i regionalna uprava");
  await expect(page.locator("#payrollCustomBase")).toHaveAttribute("required","");
  await page.locator("#payrollCustomBase").fill("900");
  await page.locator("#payrollCoefficient").fill("2.10");
  await expect(page.locator("#payrollGross")).not.toHaveText("Unesi osnovicu");
});

test("salary estimator applies residence tax presets independently from institution county", async ({page}) => {
  await page.goto("/");
  await openPayroll(page);
  await page.locator("#payrollResidence").selectOption("Zagreb");
  await expect(page.locator("#payrollTaxLower")).toHaveValue("23");
  await expect(page.locator("#payrollTaxHigher")).toHaveValue("33");
  await expect(page.locator("#payrollTaxNote")).toContainText("prebivalište");
  await page.locator("#payrollResidence").selectOption("Rijeka");
  await expect(page.locator("#payrollTaxLower")).toHaveValue("20");
  await expect(page.locator("#payrollTaxHigher")).toHaveValue("25");

  await page.locator("#payrollResidence").selectOption("__manual__");
  await expect(page.locator("#payrollResidenceCustomWrap")).toBeVisible();
  await page.locator("#payrollResidenceCustom").fill("Primjer Općina");
  await page.locator("#payrollTaxLower").fill("19.5");
  await page.locator("#payrollTaxHigher").fill("29.5");
  await page.locator("#payrollResidenceCustom").blur();
  await page.evaluate(async()=>{await (window as any).RasporedDataStore.flush()});
  const stored=await page.evaluate(()=>JSON.parse((window as any).RasporedDataStore.get("raspored.payroll.v1")));
  expect(stored.residence).toBe("Primjer Općina");
  expect(stored.taxLower).toBe(19.5);
  expect(stored.taxHigher).toBe(29.5);
});

test("salary estimator exposes official sources and clearly labels approximation limits", async ({page}) => {
  await page.goto("/");
  await openPayroll(page);
  await expect(page.locator("#payrollLegalText")).toContainText("osnovna bruto plaća");
  await expect(page.locator(".payroll-legal-card")).toContainText("nije obračunska isprava");
  expect(await page.locator("#payrollSources a").count()).toBeGreaterThanOrEqual(10);
  await expect(page.locator("#payrollSources")).toContainText("NN 22/2024");
  await expect(page.locator("#payrollSources")).toContainText("Ministarstvo zdravstva");
  await expect(page.locator(".payroll-day-note")).toContainText("nije službena dnevnica");
});


test("calendar, scan help and settings controls are wired", async ({page}) => {
  await page.goto("/");
  const width=page.viewportSize()?.width ?? 1440;

  if(width>820){
    const before=await page.locator("#monthTitle").textContent();
    await page.locator("#nextMonth").click();
    await expect(page.locator("#monthTitle")).not.toHaveText(before||"");
    await page.locator("#prevMonth").click();
    await page.locator("#todayBtn").click();
  }else{
    await page.locator('[data-route="calendar"]:visible').first().click();
    const before=await page.locator("#calMonthTitle").textContent();
    await page.locator("#calNext").click();
    await expect(page.locator("#calMonthTitle")).not.toHaveText(before||"");
    await page.locator("#calPrev").click();
  }

  await page.locator('[data-route="scan"]:visible').first().click();
  await page.locator("#scanHelpBtn").click();
  await expect(page.locator("#scanHelpDialog")).toBeVisible();
  await page.locator("#scanHelpDialog").getByRole("button",{name:"Zatvori"}).click();

  if(width<=820) await page.locator(".scan-header .back-btn").click();
  else await page.locator('.side-nav [data-route="home"]').click();
  await page.locator('[data-route="settings"]:visible').first().click();
  await page.locator("#themeToggle").check();
  await expect(page.locator("html")).toHaveAttribute("data-theme","dark");
  await page.locator("#motionToggle").check();
  await expect(page.locator("body")).toHaveAttribute("data-reduced-motion","true");
  await expect(page.locator('a[href="https://wa.me/385919010092"]')).toBeVisible();
  await expect(page.locator('a[href="mailto:info@raspored.eu"]')).toBeVisible();
  await expect(page.locator('a[href="https://brendigo.com"]')).toBeVisible();
});


test("security headers are enabled", async ({page}) => {
  const response=await page.goto("/");
  expect(response).not.toBeNull();
  const headers=response!.headers();
  expect(headers["x-content-type-options"]).toBe("nosniff");
  expect(headers["x-frame-options"]).toBe("DENY");
  expect(headers["referrer-policy"]).toContain("strict-origin");
  expect(headers["permissions-policy"]).toContain("camera=(self)");
  expect(headers["content-security-policy"]).toContain("default-src 'self'");
  expect(headers["content-security-policy"]).toContain("frame-ancestors 'none'");
});


test("PWA manifest and install assets are available", async ({request}) => {
  const manifest=await request.get("/manifest.webmanifest");
  expect(manifest.ok()).toBeTruthy();
  const data=await manifest.json();
  expect(data.name).toContain("RASPORED");
  expect(data.start_url).toBe("./");
  expect(data.scope).toBe("./");
  for(const path of [
    "/assets/brand/icon-192.png",
    "/assets/brand/icon-512.png",
    "/assets/brand/icon-maskable-192.png",
    "/assets/brand/icon-maskable-512.png",
    "/sw.js"
  ]){
    const response=await request.get(path);
    expect(response.ok(),path).toBeTruthy();
  }
});


test("shift cards and chevrons open the expected destination", async ({page}) => {
  await page.goto("/");
  const width=page.viewportSize()?.width ?? 1440;
  if(width<=820){
    const note=page.locator("#mobileCurrentShift .mobile-shift-info-action").filter({hasText:"Bilješka"});
    await note.click();
    await expect(page.locator('[data-view="hours"]')).toBeVisible();
    await page.locator('[data-route="home"]:visible').first().click();

    const next=page.locator("#mobileNextShift [data-open-date]");
    if(await next.count()){
      const target=await next.getAttribute("data-open-date");
      await next.click();
      await expect(page.locator('[data-view="calendar"]')).toBeVisible();
      await expect(page.locator('[data-view="calendar"].is-active [data-date="'+target+'"].is-selected')).toHaveCount(1);
    }
  }else{
    const next=page.locator("#nextShiftList [data-open-date]").first();
    if(await next.count()){
      const target=await next.getAttribute("data-open-date");
      await next.click();
      await expect(page.locator('[data-view="calendar"]')).toBeVisible();
      await expect(page.locator('[data-view="calendar"].is-active [data-date="'+target+'"].is-selected')).toHaveCount(1);
    }
  }
});


test("server JSON storage works when browser Storage APIs are unavailable", async ({browser}) => {
  const context=await browser.newContext({viewport:{width:390,height:844}});
  const page=await context.newPage();
  const pageErrors:string[]=[];
  page.on("pageerror",error=>pageErrors.push(error.message));
  await page.addInitScript(() => {
    const blocked=()=>{throw new DOMException("Storage blocked","SecurityError")};
    Storage.prototype.getItem=blocked;
    Storage.prototype.setItem=blocked;
    Storage.prototype.removeItem=blocked;
  });
  await page.goto("/");
  await expect(page.locator('[data-view="home"]')).toBeVisible();
  await page.locator('[data-route="settings"]:visible').first().click();
  await expect(page.locator('[data-view="settings"]')).toBeVisible();
  await page.locator("#themeToggle").check();
  await page.evaluate(async()=>{await (window as any).RasporedDataStore.flush()});
  await page.reload();
  await expect(page.locator("#themeToggle")).toBeChecked();
  await expect(page.locator("html")).toHaveAttribute("data-theme","dark");
  expect(pageErrors).toEqual([]);
  await context.close();
});

test("application data is persisted through the JSON state API", async ({page}) => {
  await seedApp(page,{withScan:true});
  await page.goto("/");
  await expect(page.locator("#profileName")).toContainText("Ivana");
  const stored=await page.evaluate(async () => {
    const response=await fetch((document.body.dataset.base||"")+"/api/state.php",{cache:"no-store"});
    return response.json();
  });
  expect(stored.ok).toBe(true);
  expect(stored.state.revision).toBeGreaterThan(0);
  expect(stored.state.schedule["2026-10-16"]).toBe("D");
  expect(stored.state.evidence.length).toBeGreaterThan(0);
  expect(stored.state.profile.name).toBe("Ivana Radić");
  expect(stored.state.colleagues).toHaveLength(2);
  expect(stored.state.scanSession.people).toHaveLength(3);
});

test("state API rejects writes without the application request header", async ({page}) => {
  await page.goto("/");
  const status=await page.evaluate(async () => {
    const response=await fetch((document.body.dataset.base||"")+"/api/state.php",{
      method:"PUT",
      headers:{"Content-Type":"application/json"},
      body:JSON.stringify({state:{schedule:{}}})
    });
    return response.status;
  });
  expect(status).toBe(403);
});


test("queued JSON writes preserve edits made while an earlier PUT is in flight", async ({page}) => {
  await page.goto("/");
  await page.evaluate(async () => {
    const store=(window as any).RasporedDataStore;
    store.set("raspored.theme","dark");
    await new Promise(resolve=>setTimeout(resolve,0));
    store.set("raspored.profile.name","Ana Horvat");
    await store.flush();
  });
  const saved=await page.evaluate(async () => {
    const response=await fetch((document.body.dataset.base||"")+"/api/state.php",{cache:"no-store"});
    return (await response.json()).state;
  });
  expect(saved.settings.theme).toBe("dark");
  expect(saved.profile.name).toBe("Ana Horvat");
});

test("offline API startup rejects writes instead of overwriting server state", async ({browser}) => {
  const context=await browser.newContext({viewport:{width:390,height:844}});
  const page=await context.newPage();
  await page.route("**/api/state.php",route=>route.abort());
  await page.goto("/");
  const result=await page.evaluate(() => {
    const store=(window as any).RasporedDataStore;
    return {available:store.isAvailable(),saved:store.set("raspored.profile.name","Ne smije se spremiti")};
  });
  expect(result.available).toBe(false);
  expect(result.saved).toBe(false);
  await context.close();
});


test("dynamic assets honor a subdirectory deployment base", async ({page}) => {
  await page.goto("/");
  await page.evaluate(() => {
    document.body.dataset.base="/raspored";
    (document.getElementById("searchBtn") as HTMLElement)?.click();
  });
  await expect(page.locator("#searchDialog")).toBeVisible();
  await expect(page.locator("#searchResults use").first()).toHaveAttribute("href",/^\/raspored\/assets\/brand\/icons\.svg#icon-/);
});
