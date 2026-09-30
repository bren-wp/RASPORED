import {test,expect} from "@playwright/test";
import {seedApp} from "./fixtures";

test.skip(!process.env.RASPORED_VISUAL_BASELINE,"Baseline se uključuje tek nakon ručnog pregleda stvarnih screenshotova.");

for (const route of ["home","calendar","scan","stats","hours","settings"]) {
  test("visual "+route,async({page})=>{
    await seedApp(page,{withScan:route==="scan"});
    await page.goto("/");
    if(route==="hours"){
      const width=page.viewportSize()?.width ?? 1440;
      if(width<=820) await page.getByRole("button",{name:/Evidentiraj ulaz\/izlaz/i}).click();
      else await page.locator('[data-route="hours"]:visible').first().click();
    }else if(route!=="home"){
      await page.locator('[data-route="'+route+'"]:visible').first().click();
    }
    await expect(page.locator('[data-view="'+route+'"]')).toBeVisible();
    await expect(page).toHaveScreenshot(route+".png",{fullPage:true,animations:"disabled"});
  });
}
