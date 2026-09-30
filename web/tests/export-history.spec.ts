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
