import {test,expect} from "@playwright/test";

test("responsive home uses platform-appropriate composition", async ({page}) => {
  await page.goto("/?demo=1");
  const width=page.viewportSize()?.width ?? 1440;
  if(width<=820){
    await expect(page.getByText("Današnja smjena")).toBeVisible();
    await expect(page.getByRole("button",{name:/Skeniraj raspored/i}).last()).toBeVisible();
  }else{
    await expect(page.getByRole("heading",{name:/Dobro došao/})).toBeVisible();
    await expect(page.locator("#calendarGrid")).toBeVisible();
  }
});

test("calendar and statistics remain interactive", async ({page}) => {
  await page.goto("/?demo=1");
  await page.locator('[data-route="calendar"]:visible').first().click();
  await expect(page.locator("#calendarGridMobile")).toBeVisible();
  await page.locator('[data-route="stats"]:visible').first().click();
  await expect(page.getByText("Ukupno odrađeno sati")).toBeVisible();
});

test("scan accepts a real image file and exposes OCR state", async ({page}) => {
  await page.goto("/?demo=1");
  await page.locator('[data-route="scan"]:visible').first().click();
  const input=page.locator("#galleryInput");
  await input.setInputFiles({
    name:"raspored.png",
    mimeType:"image/png",
    buffer:Buffer.from("89504e470d0a1a0a","hex")
  });
  await expect(page.locator("#scanStatus")).toContainText(/Automatsko prepoznavanje|Fotografija je učitana/);
});


test("time evidence records and persists check-in and check-out", async ({page}) => {
  await page.goto("/?demo=1");
  const width=page.viewportSize()?.width ?? 1440;
  if(width<=820){
    await page.getByRole("button",{name:/Evidentiraj ulaz\/izlaz/i}).click();
  }else{
    await page.locator('[data-route="hours"]:visible').first().click();
  }

  await expect(page.getByRole("heading",{name:"Evidencija sati"})).toBeVisible();
  await page.getByRole("button",{name:"Evidentiraj ulaz"}).click();
  await expect(page.locator("#hoursStatus")).toContainText("Rad je u tijeku");
  await page.locator("#hoursNote").fill("Redovna smjena");
  await page.getByRole("button",{name:"Evidentiraj izlaz"}).click();
  await expect(page.locator("#hoursStatus")).toContainText("spremljena");

  await page.reload();
  if(width<=820){
    await page.getByRole("button",{name:/Evidentiraj ulaz\/izlaz/i}).click();
  }else{
    await page.locator('[data-route="hours"]:visible').first().click();
  }
  await expect(page.locator("#hoursInValue")).toHaveText("09:00");
  await expect(page.locator("#hoursOutValue")).toHaveText("09:00");
  await expect(page.locator("#hoursHistory")).toContainText("Redovna smjena");
});


test("production scan starts from a real empty state, not the demo table", async ({page}) => {
  await page.goto("/");
  await page.locator('[data-route="scan"]:visible').first().click();
  await expect(page.locator("#scanEmptyState")).toBeVisible();
  await expect(page.locator("#fakeSheet")).toBeHidden();
});

test("production profile does not ship the reference person as a hardcoded user", async ({page}) => {
  await page.goto("/");
  await expect(page.locator("#profileName")).toHaveText("Korisnik");
  await expect(page.locator("#profileName")).not.toHaveText("Marko Marković");
});


test("statistics prefer completed time evidence over planned demo hours", async ({page}) => {
  await page.goto("/?demo=1");
  const width=page.viewportSize()?.width ?? 1440;
  if(width<=820){
    await page.getByRole("button",{name:/Evidentiraj ulaz\/izlaz/i}).click();
  }else{
    await page.locator('[data-route="hours"]:visible').first().click();
  }
  await page.getByRole("button",{name:"Evidentiraj ulaz"}).click();
  await page.getByRole("button",{name:"Evidentiraj izlaz"}).click();
  await page.locator('[data-route="stats"]:visible').first().click();
  await expect(page.locator("#workedTotal")).toHaveText("0:00 h");
  await expect(page.locator("#statsCategories")).toContainText("Saldo sati");
});
