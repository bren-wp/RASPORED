import type {Page} from "@playwright/test";

export const FIXED_NOW = new Date("2026-10-16T09:00:00+02:00");

export const schedule: Record<string,string> = {
  "2026-10-01":"D","2026-10-02":"N","2026-10-03":"D",
  "2026-10-06":"D","2026-10-07":"D","2026-10-08":"GO","2026-10-09":"D","2026-10-10":"N",
  "2026-10-13":"D","2026-10-14":"N","2026-10-15":"D","2026-10-16":"D","2026-10-17":"GO",
  "2026-10-20":"D","2026-10-21":"D","2026-10-22":"N","2026-10-23":"D","2026-10-24":"D",
  "2026-10-25":"BO","2026-10-27":"BO","2026-10-28":"D","2026-10-29":"D","2026-10-30":"N","2026-10-31":"D"
};

export const scanPeople = [
  {row:4,name:"ANA HORVAT",dayShifts:{1:"D",2:"N",3:"D",6:"D",8:"GO",9:"D",13:"D",14:"N",16:"D",20:"D",22:"N",24:"D",28:"D",30:"N"}},
  {row:9,name:"LUKA BABIĆ",dayShifts:{1:"GO",4:"D",7:"N",11:"D",15:"GO",18:"N",23:"D",26:"BO",29:"D"}},
  {row:12,name:"PETRA NOVAK",dayShifts:{2:"D",5:"N",8:"D",12:"GO",16:"N",19:"D",21:"D",25:"BO",31:"D"}}
];

export async function seedApp(page: Page, options:{withScan?:boolean}={}) {
  await page.clock.install({time: FIXED_NOW});
  await page.addInitScript(({schedule,scanPeople,withScan}) => {
    localStorage.setItem("raspored.schedule",JSON.stringify(schedule));
    localStorage.setItem("raspored.profile.name","Ivana Radić");
    localStorage.setItem("raspored.colleagues.v1",JSON.stringify([
      {name:"Nikola Jurić",note:"Dnevne i noćne smjene"},
      {name:"Maja Perić",note:"Odjel B"}
    ]));
    if(withScan){
      sessionStorage.setItem("raspored.scan.v1",JSON.stringify({
        people:scanPeople,
        selected:0,
        month:{year:2026,month:10}
      }));
    }
  },{schedule,scanPeople,withScan:!!options.withScan});
}

export async function mockOcr(
  page: Page,
  options:{people?:typeof scanPeople;expectedRows?:number}={}
) {
  const people=options.people||scanPeople;
  const expectedRows=options.expectedRows||0;
  await page.route("**/assets/js/ocr-web.js", async route => {
    await route.fulfill({
      contentType:"application/javascript",
      body:`window.RasporedWebOcr={recognizeSchedule:async function(file,onProgress){if(onProgress)onProgress(.65,"recognizing");return {month:{year:2026,month:10},people:${JSON.stringify(people)},expectedRows:${expectedRows},rawText:"LISTOPAD 2026."};}};`
    });
  });
}
