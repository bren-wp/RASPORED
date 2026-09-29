import {test,expect} from "@playwright/test";
for (const route of ["home","calendar","scan","stats"]) {
  test("capture "+route,async({page},testInfo)=>{
    await page.goto("/?demo=1");
    if(route!=="home") await page.locator('[data-route="'+route+'"]').first().click();
    await expect(page.locator('[data-view="'+route+'"]')).toBeVisible();
    await page.screenshot({path:testInfo.outputPath(route+".png"),fullPage:true});
  });
}
