import {test,expect} from "@playwright/test";
import {mockOcr,seedApp} from "./fixtures";

function syntheticScheduleSvg(): Buffer {
  const rows=[
    ["4","ANA HORVAT","D","N","D","","GO","D"],
    ["9","LUKA BABIĆ","GO","","","D","N",""],
    ["12","PETRA NOVAK","","D","N","GO","","D"]
  ];
  const body=rows.map((row,index)=>{
    const y=98+index*36;
    const cells=row.map((cell,col)=>{
      const x=18+[0,36,210,258,306,354,402,450][col];
      const width=col===2?174:48;
      return '<rect x="'+x+'" y="'+(y-24)+'" width="'+width+'" height="36" fill="white" stroke="#94A3B8"/>'+
        '<text x="'+(x+8)+'" y="'+y+'" font-family="Arial" font-size="14" fill="#0B1F44">'+cell+'</text>';
    }).join("");
    return cells;
  }).join("");
  const svg='<svg xmlns="http://www.w3.org/2000/svg" width="520" height="240" viewBox="0 0 520 240">'+
    '<rect width="520" height="240" fill="#f2f2f2"/>'+
    '<text x="360" y="28" font-family="Arial" font-size="18" font-weight="700" fill="#111">LISTOPAD 2026.</text>'+
    '<text x="18" y="62" font-family="Arial" font-size="12" font-weight="700">Rb</text>'+
    '<text x="62" y="62" font-family="Arial" font-size="12" font-weight="700">IME I PREZIME</text>'+
    '<text x="222" y="62" font-family="Arial" font-size="12">1</text><text x="270" y="62" font-family="Arial" font-size="12">2</text>'+
    '<text x="318" y="62" font-family="Arial" font-size="12">3</text><text x="366" y="62" font-family="Arial" font-size="12">4</text>'+
    '<text x="414" y="62" font-family="Arial" font-size="12">5</text><text x="462" y="62" font-family="Arial" font-size="12">6</text>'+
    body+'</svg>';
  return Buffer.from(svg);
}

for (const route of ["home","calendar","scan","stats","hours","settings"]) {
  test("capture "+route,async({page},testInfo)=>{
    if(route==="scan") await mockOcr(page);
    await seedApp(page);
    await page.goto("/");

    if(route==="hours"){
      const width=page.viewportSize()?.width ?? 1440;
      if(width<=820) await page.getByRole("button",{name:/Evidentiraj ulaz\/izlaz/i}).click();
      else await page.locator('[data-route="hours"]:visible').first().click();
    }else if(route!=="home"){
      await page.locator('[data-route="'+route+'"]:visible').first().click();
    }

    if(route==="scan"){
      await page.locator("#galleryInput").setInputFiles({
        name:"raspored-izmisljeni.svg",
        mimeType:"image/svg+xml",
        buffer:syntheticScheduleSvg()
      });
      await expect(page.locator("#scanStatus")).toContainText("Prepoznate su 3 osobe");
      await page.locator("#scanPersonButton").click();
      await page.locator('#scanPersonMenu [data-scan-person="0"]').click();
      await expect(page.locator("#scanPersonLabel")).toContainText("ANA HORVAT");
    }

    await expect(page.locator('[data-view="'+route+'"]')).toBeVisible();
    const width=page.viewportSize()?.width ?? 1440;
    await page.screenshot({
      path:testInfo.outputPath(route+".png"),
      fullPage:width>820
    });
  });
}
