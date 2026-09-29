import {test,expect} from "@playwright/test";

for (const route of ["home","calendar","scan","stats","hours"]) {
  test("capture "+route,async({page},testInfo)=>{
    await page.goto("/?demo=1");
    if(route==="hours"){
      const width=page.viewportSize()?.width ?? 1440;
      if(width<=820){
        await page.getByRole("button",{name:/Evidentiraj ulaz\/izlaz/i}).click();
      }else{
        await page.locator('[data-route="hours"]:visible').first().click();
      }
    }else if(route!=="home"){
      await page.locator('[data-route="'+route+'"]:visible').first().click();
    }
    await expect(page.locator('[data-view="'+route+'"]')).toBeVisible();
    await page.screenshot({path:testInfo.outputPath(route+".png"),fullPage:true});
  });
}
