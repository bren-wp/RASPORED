import {test,expect} from "@playwright/test";
import {seedApp} from "./fixtures";

test.beforeEach(async ({page}) => {
  await seedApp(page);
});

async function openReady(page:any){
  await page.goto("/");
  await expect(page.locator("body")).toHaveAttribute("data-app-ready","true");
}

test("historical report month remains selectable", async ({page}) => {
  await openReady(page);
  await page.locator('[data-route="settings"]:visible').first().click();
  await expect(page.locator("#reportMonth")).toHaveValue("2026-10");
  await page.locator("#reportMonth").fill("2026-08");
  await expect(page.locator("#reportMonth")).toHaveValue("2026-08");
});

test("guest storage cannot persist manager-only team schedules", async ({page}) => {
  await openReady(page);
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


test("schedule storage and state API normalize OCR zero variants", async ({page}) => {
  await openReady(page);
  const result=await page.evaluate(async () => {
    const store=(window as any).RasporedDataStore;
    store.set("raspored.schedule",JSON.stringify({
      "2026-10-01":"g0",
      "2026-10-02":"B0"
    }));
    await store.flush();
    const client=JSON.parse(store.get("raspored.schedule")||"{}");

    const response=await fetch((document.body.dataset.base||"")+"/api/state.php",{
      method:"PUT",
      credentials:"same-origin",
      cache:"no-store",
      headers:{
        "Content-Type":"application/json",
        "X-Raspored-Request":"1"
      },
      body:JSON.stringify({
        patch:true,
        state:{
          schedule:{
            "2026-10-03":"G0",
            "2026-10-04":"b0"
          }
        }
      })
    });
    const payload=await response.json();
    return {status:response.status,client:client,server:payload.state&&payload.state.schedule};
  });

  expect(result.status).toBe(200);
  expect(result.client["2026-10-01"]).toBe("GO");
  expect(result.client["2026-10-02"]).toBe("BO");
  expect(result.server["2026-10-03"]).toBe("GO");
  expect(result.server["2026-10-04"]).toBe("BO");
});


test("legacy evidence without millisecond timestamps keeps its worked duration", async ({page}) => {
  await openReady(page);
  const width=page.viewportSize()?.width ?? 1440;
  await page.locator('[data-route="stats"]:visible').first().click();
  await expect(page.locator("#workedTotal")).not.toHaveText("0:00 h");
  if(width<=820){
    await page.locator('[data-route="home"]:visible').first().click();
    await page.getByRole("button",{name:/Evidentiraj ulaz\/izlaz/i}).click();
  }else{
    await page.locator('[data-route="hours"]:visible').first().click();
  }
  await expect(page.locator("#hoursHistory")).toContainText("12h 00min");
});


test("statistics split overnight evidence at month and night boundaries", async ({page}) => {
  await openReady(page);
  await page.evaluate(async () => {
    const store=(window as any).RasporedDataStore;
    const start=new Date(2026,8,30,22,0,0).getTime();
    const end=new Date(2026,9,1,7,0,0).getTime();
    store.set("raspored.timeEntries.v1",JSON.stringify([
      {id:"cross-month",date:"2026-09-30",in:"22:00",out:"07:00",note:"",workType:"shift3",startedAt:start,endedAt:end}
    ]));
    await store.flush();
  });
  await page.locator('[data-route="stats"]:visible').first().click();
  await expect(page.locator("#workedTotal")).toHaveText("7:00 h");
  await expect(page.locator("#statsCategories")).toContainText("Noćni sati");
  await expect(page.locator("#statsCategories")).toContainText("6h");
  await expect(page.locator("#statsCategories")).toContainText("Dnevni sati");
  await expect(page.locator("#statsCategories")).toContainText("1h");
});
