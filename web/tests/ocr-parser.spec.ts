import {test,expect} from "@playwright/test";

test("OCR text fallback never compresses missing calendar days", async ({page}) => {
  await page.goto("/");
  await expect(page.locator("body")).toHaveAttribute("data-app-ready","true");

  const parsed=await page.evaluate(() => (window as any).RasporedWebOcr.parseText(
    "6 ANA HORVAT 1 D 2 N 4 GO 7 PD 9 SD\n7 LUKA BABIĆ D N GO"
  ));

  expect(parsed).toHaveLength(2);
  expect(parsed[0].dayShifts).toEqual({"1":"D","2":"N","4":"GO","7":"PD","9":"SD"});
  expect(parsed[1].dayShifts).toEqual({});
});

test("OCR geometry reconstructs fragmented day header and all employee rows", async ({page}) => {
  await page.goto("/");
  await expect(page.locator("body")).toHaveAttribute("data-app-ready","true");

  const parsed=await page.evaluate(() => {
    function word(text:string,x:number,y:number,w=18,h=12){
      return {text,bbox:{x0:x,y0:y,x1:x+w,y1:y+h}};
    }
    function line(words:any[],y:number){
      return {
        text:words.map(w=>w.text).join(" "),
        bbox:{
          x0:Math.min(...words.map(w=>w.bbox.x0)),
          y0:y,
          x1:Math.max(...words.map(w=>w.bbox.x1)),
          y1:y+14
        },
        words
      };
    }

    const headerA=[] as any[];
    const headerB=[] as any[];
    for(let day=1;day<=31;day++){
      const x=320+(day-1)*24;
      (day<=15?headerA:headerB).push(word(String(day),x,day<=15?100:103,14));
    }

    const people=[
      {row:1,name:["Ana","Horvat"],y:170,codes:[[1,"D"],[2,"N"],[4,"GO"],[16,"D"],[31,"BO"]]},
      {row:2,name:["Luka","Babić"],y:205,codes:[[1,"GO"],[5,"D"],[10,"N"],[20,"PD"],[30,"SD"]]},
      {row:3,name:["Petra","Novak"],y:240,codes:[[3,"D"],[8,"N"],[12,"GO"],[18,"D"],[29,"BO"]]},
      {row:4,name:["Ivana","Radić"],y:275,codes:[[2,"N"],[7,"D"],[14,"PD"],[21,"GO"],[31,"D"]]}
    ];

    const lines:any[]=[
      line(headerA,100),
      line(headerB,103)
    ];

    for(const person of people){
      lines.push(line([
        word(String(person.row),20,person.y,12),
        word(person.name[0],55,person.y,54),
        word(person.name[1],116,person.y,62)
      ],person.y));
      lines.push(line(person.codes.map(([day,code]) =>
        word(String(code),320+(Number(day)-1)*24,person.y+3,String(code).length===1?14:22)
      ),person.y+3));
    }

    const blocks=[{paragraphs:[{lines}]}];
    return (window as any).RasporedWebOcr.parseGeometry(blocks,31);
  });

  expect(parsed).toHaveLength(4);
  expect(parsed.map((row:any)=>row.name)).toEqual(["Ana Horvat","Luka Babić","Petra Novak","Ivana Radić"]);
  expect(parsed[0].dayShifts).toEqual({"1":"D","2":"N","4":"GO","16":"D","31":"BO"});
  expect(parsed[1].dayShifts).toEqual({"1":"GO","5":"D","10":"N","20":"PD","30":"SD"});
  expect(parsed[2].dayShifts).toEqual({"3":"D","8":"N","12":"GO","18":"D","29":"BO"});
  expect(parsed[3].dayShifts).toEqual({"2":"N","7":"D","14":"PD","21":"GO","31":"D"});
});
