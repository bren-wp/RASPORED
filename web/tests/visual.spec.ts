import {test,expect} from "@playwright/test";
test.skip(!process.env.RASPORED_VISUAL_BASELINE,"Baseline se uključuje tek nakon ručnog pregleda stvarnih screenshotova.");
for (const route of ["home","calendar","scan","stats"]) {
  test("visual "+route,async({page})=>{
    await page.goto("/?demo=1");
    if(route!=="home") await page.locator('[data-route="'+route+'"]:visible').first().click();
    await expect(page.locator('[data-view="'+route+'"]')).toBeVisible();
    await expect(page).toHaveScreenshot(route+".png",{fullPage:true,animations:"disabled"});
  });
}
