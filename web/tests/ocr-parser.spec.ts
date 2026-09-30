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

test("OCR text fallback accepts table-border separators without shifting days", async ({page}) => {
  await page.goto("/");
  await expect(page.locator("body")).toHaveAttribute("data-app-ready","true");

  const parsed=await page.evaluate(() => (window as any).RasporedWebOcr.parseText(
    "6 ANA HORVAT 1 | D 2 | N 4 | GO 7 | PD 9 | SD"
  ));

  expect(parsed).toHaveLength(1);
  expect(parsed[0].dayShifts).toEqual({
    "1":"D","2":"N","4":"GO","7":"PD","9":"SD"
  });
});

test("OCR geometry preserves workplace-specific short cell labels", async ({page}) => {
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
    const header=[] as any[];
    for(let day=1;day<=31;day++)header.push(word(String(day),320+(day-1)*24,80,14));
    const employee=line([
      word("1",20,145,12),
      word("ANA",55,145,42),
      word("HORVAT",104,145,62),
      word("J",320,147,12),
      word("S",320+5*24,147,12),
      word("P1",320+12*24,147,18),
      word("3",320+20*24,147,12),
      word("GO",320+30*24,147,20)
    ],145);
    return (window as any).RasporedWebOcr.parseGeometry(
      [{paragraphs:[{lines:[line(header,80),employee]}]}],
      31
    );
  });

  expect(parsed).toHaveLength(1);
  expect(parsed[0].dayShifts).toEqual({
    "1":"J","6":"S","13":"P1","21":"3","31":"GO"
  });
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


test("OCR geometry retains a dense 30-person roster and sparse exact days", async ({page}) => {
  await page.goto("/");
  await expect(page.locator("body")).toHaveAttribute("data-app-ready","true");

  const parsed=await page.evaluate(() => {
    function word(text:string,x:number,y:number,w=18,h=11){
      return {text,bbox:{x0:x,y0:y,x1:x+w,y1:y+h}};
    }
    function line(words:any[],y:number){
      return {
        text:words.map(w=>w.text).join(" "),
        bbox:{
          x0:Math.min(...words.map(w=>w.bbox.x0)),
          y0:y,
          x1:Math.max(...words.map(w=>w.bbox.x1)),
          y1:y+13
        },
        words
      };
    }

    const header=[] as any[];
    for(let day=1;day<=31;day++)header.push(word(String(day),300+(day-1)*21,60,13));

    const lines:any[]=[line(header,60)];
    for(let row=1;row<=30;row++){
      const y=95+(row-1)*19;
      lines.push(line([
        word(String(row),14,y,14),
        word("TESTOSOBA"+row,46,y,90)
      ],y));
      const codes:[[number,string],[number,string],[number,string],[number,string],[number,string]]=[
        [1,row%2?"D":"N"],
        [5,"GO"],
        [16,row%3?"D":"PD"],
        [24,"BO"],
        [31,row%4?"N":"SD"]
      ];
      lines.push(line(codes.map(([day,code]) =>
        word(code,300+(day-1)*21,y+2,code.length===1?12:19)
      ),y+2));
    }

    const blocks=[{paragraphs:[{lines}]}];
    return (window as any).RasporedWebOcr.parseGeometry(blocks,31);
  });

  expect(parsed).toHaveLength(30);
  expect(parsed.map((row:any)=>row.row)).toEqual(Array.from({length:30},(_,i)=>i+1));
  for(const row of parsed){
    expect(row.dayShifts["2"]).toBeUndefined();
    expect(row.dayShifts["5"]).toBe("GO");
    expect(row.dayShifts["24"]).toBe("BO");
    expect(row.dayShifts["31"]).toMatch(/^(N|SD)$/);
  }
});


test("OCR geometry keeps all 27 employees and all 31 day columns in a dense monthly table", async ({page}) => {
  await page.goto("/");
  await expect(page.locator("body")).toHaveAttribute("data-app-ready","true");

  const parsed=await page.evaluate(() => {
    function word(text:string,x:number,y:number,w=14,h=10){
      return {text,bbox:{x0:x,y0:y,x1:x+w,y1:y+h}};
    }
    function line(words:any[],y:number){
      return {
        text:words.map(w=>w.text).join(" "),
        bbox:{
          x0:Math.min(...words.map(w=>w.bbox.x0)),
          y0:y,
          x1:Math.max(...words.map(w=>w.bbox.x1)),
          y1:y+12
        },
        words
      };
    }
    const lines:any[]=[];
    const header=[] as any[];
    for(let day=1;day<=31;day++)header.push(word(String(day),310+(day-1)*22,70,13));
    lines.push(line(header,70));

    const labels=["D","N","GO","BO","PD","SD","J","S","P1","1","2","3"];
    for(let row=1;row<=27;row++){
      const y=105+(row-1)*18;
      lines.push(line([
        word(String(row),12,y,14),
        word("TEST",44,y,42),
        word("OSOBA",92,y,54)
      ],y));
      const cells=[] as any[];
      for(let day=1;day<=31;day++){
        const code=labels[(row+day)%labels.length];
        cells.push(word(code,310+(day-1)*22,y+2,code.length===1?11:18));
      }
      lines.push(line(cells,y+2));
    }
    return (window as any).RasporedWebOcr.parseGeometry(
      [{paragraphs:[{lines}]}],
      31
    );
  });

  expect(parsed).toHaveLength(27);
  expect(parsed.map((row:any)=>row.row)).toEqual(Array.from({length:27},(_,i)=>i+1));
  for(const row of parsed){
    expect(Object.keys(row.dayShifts)).toHaveLength(31);
    expect(row.dayShifts["1"]).toBeTruthy();
    expect(row.dayShifts["31"]).toBeTruthy();
  }
  expect(parsed.some((row:any)=>Object.values(row.dayShifts).includes("J"))).toBe(true);
  expect(parsed.some((row:any)=>Object.values(row.dayShifts).includes("S"))).toBe(true);
});

test("OCR finalization suppresses one-off ghost people in a dense numbered roster", async ({page}) => {
  await page.goto("/");
  await expect(page.locator("body")).toHaveAttribute("data-app-ready","true");

  const rows=await page.evaluate(() => {
    const api=(window as any).RasporedWebOcr;
    const real=Array.from({length:30},(_,index) => ({
      row:index+1,
      name:"Test Osoba",
      dayShifts:{"1":index%2?"N":"D","16":"GO","31":"D"},
      supportCount:3
    }));
    const ghosts=Array.from({length:25},(_,index) => ({
      row:null,
      name:"Slucajni Tekst "+String.fromCharCode(65+(index%20)),
      dayShifts:{"2":"D"},
      supportCount:1
    }));
    return api.finalizeRows(real.concat(ghosts));
  });

  expect(rows).toHaveLength(30);
  expect(rows.map((row:any)=>row.row)).toEqual(Array.from({length:30},(_,i)=>i+1));
});


test("OCR finalization keeps very similar real names as separate people", async ({page}) => {
  await page.goto("/");
  await expect(page.locator("body")).toHaveAttribute("data-app-ready","true");

  const rows=await page.evaluate(() => {
    const api=(window as any).RasporedWebOcr;
    return api.finalizeRows([
      {row:null,name:"IVAN HORVAT",dayShifts:{"1":"D"},supportCount:3},
      {row:null,name:"IVANA HORVAT",dayShifts:{"2":"N"},supportCount:3}
    ]);
  });

  expect(rows).toHaveLength(2);
  expect(rows.map((row:any)=>row.name).sort()).toEqual(["IVAN HORVAT","IVANA HORVAT"]);
});

test("OCR finalization fills one missing dense-roster slot with repeated unnumbered employee", async ({page}) => {
  await page.goto("/");
  await expect(page.locator("body")).toHaveAttribute("data-app-ready","true");

  const rows=await page.evaluate(() => {
    const api=(window as any).RasporedWebOcr;
    const numbered=Array.from({length:30},(_,index)=>index+1)
      .filter(number=>number!==15)
      .map(number=>({
        row:number,
        name:"OSOBA BROJ",
        dayShifts:{"1":"D","31":"N"},
        supportCount:3
      }));
    return api.finalizeRows(numbered.concat([
      {row:null,name:"MAJA PERIĆ",dayShifts:{"1":"N","16":"GO","31":"D"},supportCount:4},
      {row:null,name:"NASLOV TABLICE",dayShifts:{"2":"D"},supportCount:3}
    ]));
  });

  expect(rows).toHaveLength(30);
  const unnumbered=rows.filter((row:any)=>row.row==null);
  expect(unnumbered).toHaveLength(1);
  expect(unnumbered[0].name).toBe("MAJA PERIĆ");
});
