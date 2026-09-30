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
  await expect(page.locator("#scanStatus")).toContainText("Prepoznato je 3 osoba");
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

test("profile, notifications and colleagues controls work", async ({page}) => {
  await page.goto("/");
  const width=page.viewportSize()?.width ?? 1440;
  if(width>820){
    await page.locator("#notificationBtn").click();
    await expect(page.locator("#notificationPanel")).toBeVisible();
    await page.locator("#profileButton").click();
    await expect(page.locator("#profilePanel")).toBeVisible();
    await page.locator('[data-route="colleagues"]:visible').first().click();
    await expect(page.getByText("Nikola Jurić")).toBeVisible();
  }
  await page.locator('[data-route="settings"]:visible').first().click();
  await page.locator("#profileNameInput").fill("Sara Kovač");
  await page.locator("#saveProfileBtn").click();
  await expect(page.locator("#profileNameInput")).toHaveValue("Sara Kovač");
});

test("no reference-person names or development labels ship in production UI", async ({page}) => {
  await page.goto("/");
  await expect(page.locator("body")).not.toContainText("Marko Marković");
  await expect(page.locator("body")).not.toContainText("MARIO EGIMOVIĆ");
  await expect(page.locator("body")).not.toContainText("-dev");
});
