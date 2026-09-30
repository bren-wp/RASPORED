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