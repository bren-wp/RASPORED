import {test,expect} from "@playwright/test";
import {seedApp} from "./fixtures";

test.beforeEach(async ({page}) => {
  await seedApp(page);
});

test("historical report month remains selectable", async ({page}) => {
  await page.goto("/");
  await page.locator('[data-route="settings"]:visible').first().click();
  await expect(page.locator("#reportMonth")).toHaveValue("2026-10");
  await page.locator("#reportMonth").fill("2026-08");
  await expect(page.locator("#reportMonth")).toHaveValue("2026-08");
});

test("guest storage cannot persist manager-only team schedules", async ({page}) => {
  await page.goto("/");
  const team=await page.evaluate(async () => {
    const store=(window as any).RasporedDataStore;
    store.set("raspored.team.v1",JSON.stringify([
      {name:"Ana Horvat",note:"",schedule:{"2026-10-01":"D"}}
    ]));
    await store.flush();
    return JSON.parse(store.get("raspored.team.v1")||"[]");
  });
  expect(team).toEqual([]);
});


test("legacy evidence without millisecond timestamps keeps its worked duration", async ({page}) => {
  await page.goto("/");
  await page.locator('[data-route="stats"]:visible').first().click();
  await expect(page.locator("#workedTotal")).not.toHaveText("0:00 h");
  await page.locator('[data-route="hours"]:visible').first().click();
  await expect(page.locator("#hoursHistory")).toContainText("12h 00min");
});
