(function(){
"use strict";

var config=null;
var bound=false;
var initialized=false;
var applying=false;
var OTHER="__manual__";
var legacyRoleMap={
  "kbc-transport-nss":"health-transport-nss",
  "kbc-transport-sss":"health-transport-sss",
  "kbc-portir":"health-portir",
  "cleaner-special":"health-cleaner-special",
  "cleaner":"health-cleaner",
  "caregiver":"health-caregiver",
  "hospital-attendant":"health-bolnicar",
  "nurse-bacc-1":"health-nurse-bacc-1",
  "nurse-bacc-2":"health-nurse-bacc-2",
  "nurse-sss-1":"health-nurse-sss-1",
  "nurse-sss-2":"health-nurse-sss-2",
  "nurse-master-special":"health-nurse-master",
  "physio-bacc-1":"health-physio-1",
  "physio-bacc-2":"health-physio-2",
  "doctor-1":"health-doctor-1",
  "doctor-2":"health-doctor-2",
  "doctor-3":"health-doctor-3",
  "doctor-specialization":"health-doctor-specialization",
  "doctor-specialist-1":"health-doctor-specialist-1",
  "doctor-specialist-2":"health-doctor-specialist-2",
  "doctor-specialist-3":"health-doctor-specialist-3"
};

function qs(id){return document.getElementById(id)}
function money(value){return new Intl.NumberFormat("hr-HR",{style:"currency",currency:"EUR",minimumFractionDigits:2,maximumFractionDigits:2}).format(Number(value)||0)}
function number(value,digits){return Number(value||0).toLocaleString("hr-HR",{minimumFractionDigits:digits||0,maximumFractionDigits:digits==null?2:digits})}
function hours(minutes){var m=Math.max(0,Math.round(minutes||0));return (m/60).toLocaleString("hr-HR",{maximumFractionDigits:2})+" h"}
function escapeHtml(value){return String(value==null?"":value).replace(/[&<>"']/g,function(ch){return {"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[ch]})}
function text(value,max){return String(value||"").trim().replace(/\s+/g," ").slice(0,max||160)}
function numeric(id,fallback,min,max){
  var value=Number(qs(id)&&String(qs(id).value).replace(",","."));
  if(!Number.isFinite(value))value=fallback;
  return Math.max(min,Math.min(max,value));
}
function monthValue(){
  var input=qs("payrollMonth");
  if(input&&/^2026-(0[1-9]|1[0-2])$/.test(input.value))return input.value;
  return "2026-09";
}
function defaultMonth(){
  var input=qs("payrollMonth");if(!input||input.value)return;
  var now=new Date(),year=now.getFullYear(),month=now.getMonth()+1;
  if(year<2026){year=2026;month=1}
  if(year>2026){year=2026;month=12}
  input.value=year+"-"+String(month).padStart(2,"0");
}
function snapshot(){return window.RasporedDataStore?window.RasporedDataStore.snapshot():{evidence:[],payroll:{}}}
function saved(){
  try{return JSON.parse(window.RasporedDataStore.get("raspored.payroll.v1")||"{}")}catch(e){return {}}
}
function regimeById(id){
  return config&&Array.isArray(config.regimes)?config.regimes.find(function(item){return item.id===id})||null:null;
}
function roleById(id){
  return config&&Array.isArray(config.roles)?config.roles.find(function(item){return item.id===id})||null:null;
}
function rolesFor(regimeId){
  return (config.roles||[]).filter(function(role){return Array.isArray(role.regimes)&&role.regimes.indexOf(regimeId)>=0});
}
function institutionsFor(sector,county){
  var all=config.institutionGroups&&config.institutionGroups[sector]||[];
  return all.filter(function(item){return item.county==="*"||item.county===county});
}
function defaultRegimeForSector(sector){
  var institution=institutionsFor(sector,qs("payrollCounty")?qs("payrollCounty").value:"")[0];
  if(institution)return regimeById(institution.regime);
  return (config.regimes||[]).find(function(item){return item.sector===sector})||regimeById("other-public");
}
function baseFor(regime,month,custom){
  if(custom>0)return custom;
  if(!regime||regime.baseType==="manual")return 0;
  var schedule=config.baseSchedules&&config.baseSchedules[regime.baseType];
  if(!Array.isArray(schedule))return 0;
  var date=month+"-01",match=schedule.find(function(item){return date>=item.from&&date<=item.to});
  return match?Number(match.amount):0;
}
function monthlyFund(year,monthIndex){
  var days=new Date(year,monthIndex+1,0).getDate(),count=0;
  for(var d=1;d<=days;d++){
    var day=new Date(year,monthIndex,d).getDay();
    if(day!==0&&day!==6)count++;
  }
  return count*8;
}
function iso(date){return date.getFullYear()+"-"+String(date.getMonth()+1).padStart(2,"0")+"-"+String(date.getDate()).padStart(2,"0")}
function addDays(date,n){var out=new Date(date);out.setDate(out.getDate()+n);return out}
function easter(year){
  var a=year%19,b=Math.floor(year/100),c=year%100,d=Math.floor(b/4),e=b%4,f=Math.floor((b+8)/25),g=Math.floor((b-f+1)/3),h=(19*a+b-d-g+15)%30,i=Math.floor(c/4),k=c%4,l=(32+2*e+2*i-h-k)%7,m=Math.floor((a+11*h+22*l)/451),mo=Math.floor((h+l-7*m+114)/31)-1,da=((h+l-7*m+114)%31)+1;
  return new Date(year,mo,da);
}
function holidayMap(year){
  var out={};function add(m,d){out[year+"-"+String(m).padStart(2,"0")+"-"+String(d).padStart(2,"0")]=true}
  [[1,1],[1,6],[5,1],[5,30],[6,22],[8,5],[8,15],[11,1],[11,18],[12,25],[12,26]].forEach(function(x){add(x[0],x[1])});
  var e=easter(year);out[iso(e)]=true;out[iso(addDays(e,1))]=true;out[iso(addDays(e,60))]=true;return out;
}
function interval(entry){
  if(!entry||!entry.date||!entry.in)return null;
  var start=entry.startedAt==null?NaN:Number(entry.startedAt);
  if(!Number.isFinite(start))start=new Date(entry.date+"T"+entry.in+":00").getTime();
  if(!Number.isFinite(start))return null;
  var end=entry.endedAt==null?NaN:Number(entry.endedAt);
  if(!Number.isFinite(end)){
    if(entry.out){
      end=new Date(entry.date+"T"+entry.out+":00").getTime();
      if(end<=start)end+=24*60*60*1000;
    }else end=Date.now();
  }
  if(!Number.isFinite(end)||end<=start)return null;
  return {start:start,end:Math.min(end,start+36*60*60*1000)};
}
function evidenceForMonth(year,monthIndex){
  var data=snapshot(),entries=Array.isArray(data.evidence)?data.evidence:[],holidays=holidayMap(year);
  var result={total:0,night:0,saturday:0,sunday:0,holiday:0,secondShift:0,shift1:0,shift2:0,shift3:0,turnus:0,duty:0,standby:0,callout:0,active:false,workedDates:{}};
  entries.forEach(function(entry){
    var span=interval(entry);if(!span)return;
    if(!entry.out&&entry.endedAt==null)result.active=true;
    for(var t=span.start;t<span.end;t+=60000){
      var d=new Date(t);
      if(d.getFullYear()!==year||d.getMonth()!==monthIndex)continue;
      result.total++;
      result.workedDates[iso(d)]=true;
      var hour=d.getHours();
      if(hour>=22||hour<6)result.night++;
      if(d.getDay()===6)result.saturday++;
      if(d.getDay()===0)result.sunday++;
      if(holidays[iso(d)])result.holiday++;
      if(hour>=14&&hour<22)result.secondShift++;
      var workType=entry.workType||"regular";
      if(workType==="shift1")result.shift1++;
      else if(workType==="shift2")result.shift2++;
      else if(workType==="shift3")result.shift3++;
      else if(workType==="turnus")result.turnus++;
      else if(workType==="duty")result.duty++;
      else if(workType==="standby")result.standby++;
      else if(workType==="callout")result.callout++;
    }
  });
  result.workedDays=Object.keys(result.workedDates).length;
  return result;
}
function currentInstitution(){
  var select=qs("payrollInstitution");if(!select)return null;
  var sector=qs("payrollSector")?qs("payrollSector").value:"";
  var county=qs("payrollCounty")?qs("payrollCounty").value:"";
  var list=institutionsFor(sector,county);
  var index=Number(select.value);
  return Number.isInteger(index)&&index>=0&&index<list.length?list[index]:null;
}
function currentRegime(){
  var institution=currentInstitution();
  if(institution)return regimeById(institution.regime);
  var sector=qs("payrollSector")?qs("payrollSector").value:"";
  return defaultRegimeForSector(sector);
}
function populateCounties(selected){
  var el=qs("payrollCounty");if(!el)return;
  el.innerHTML=(config.counties||[]).map(function(item){return '<option value="'+escapeHtml(item)+'">'+escapeHtml(item)+'</option>'}).join("");
  el.value=(config.counties||[]).indexOf(selected)>=0?selected:"Primorsko-goranska";
}
function populateResidence(selected,county){
  var el=qs("payrollResidence");if(!el)return;
  var list=(config.tax.localities||[]).slice().sort(function(a,b){
    var ac=a.county===county?0:1,bc=b.county===county?0:1;
    return ac-bc||a.name.localeCompare(b.name,"hr");
  });
  el.innerHTML=list.map(function(item){return '<option value="'+escapeHtml(item.name)+'">'+escapeHtml(item.name)+' — '+number(item.lower,1)+'% / '+number(item.higher,1)+'%</option>'}).join("")+
    '<option value="'+OTHER+'">Drugo mjesto — ručni unos poreznih stopa</option>';
  var exists=list.some(function(item){return item.name===selected});
  el.value=exists?selected:OTHER;
  var wrap=qs("payrollResidenceCustomWrap"),custom=qs("payrollResidenceCustom");
  if(wrap)wrap.hidden=exists;
  if(custom&&!exists&&selected&&selected!=="Drugo")custom.value=selected;
}
function selectedTaxLocality(){
  var name=qs("payrollResidence")?qs("payrollResidence").value:"";
  return (config.tax.localities||[]).find(function(item){return item.name===name})||null;
}
function applyTaxLocality(){
  var locality=selectedTaxLocality();
  var lower=qs("payrollTaxLower"),higher=qs("payrollTaxHigher"),note=qs("payrollTaxNote");
  var wrap=qs("payrollResidenceCustomWrap");
  if(wrap)wrap.hidden=!!locality;
  if(locality){
    if(lower)lower.value=String(locality.lower);
    if(higher)higher.value=String(locality.higher);
    if(lower)lower.readOnly=true;if(higher)higher.readOnly=true;
    if(note)note.textContent=locality.name+": "+number(locality.lower,1)+"% / "+number(locality.higher,1)+"% ("+locality.source+"). Porez se veže uz prebivalište.";
  }else{
    if(lower)lower.readOnly=false;if(higher)higher.readOnly=false;
    if(note)note.textContent="Za mjesto koje nije u verificiranom popisu ručno upiši važeću nižu i višu stopu iz odluke grada/općine.";
  }
}
function sectors(){
  var seen={};
  (config.regimes||[]).forEach(function(item){seen[item.sector]=true});
  return Object.keys(seen).filter(function(item){return item!=="Ostalo"}).concat(["Ostalo"]);
}
function populateSector(selected){
  var el=qs("payrollSector");if(!el)return;
  el.innerHTML=sectors().map(function(item){return '<option value="'+escapeHtml(item)+'">'+escapeHtml(item)+'</option>'}).join("");
  el.value=sectors().indexOf(selected)>=0?selected:"Zdravstvo";
}
function populateInstitutions(selectedName,preferredRegime){
  var el=qs("payrollInstitution");if(!el)return;
  var sector=qs("payrollSector").value,county=qs("payrollCounty").value,list=institutionsFor(sector,county);
  el.innerHTML=list.map(function(item,index){return '<option value="'+index+'">'+escapeHtml(item.name)+(item.city&&item.city!=="*"?" — "+escapeHtml(item.city):"")+'</option>'}).join("")+
    '<option value="'+OTHER+'">Druga ustanova — ručni naziv</option>';
  var found=list.findIndex(function(item){return item.name===selectedName||(preferredRegime&&item.regime===preferredRegime&&item.name.indexOf("ručni")<0)});
  el.value=found>=0?String(found):(list.length?String(0):OTHER);
  var customWrap=qs("payrollInstitutionCustomWrap"),custom=qs("payrollInstitutionCustom");
  var manual=el.value===OTHER;
  if(customWrap)customWrap.hidden=!manual;
  if(custom&&manual&&selectedName&&found<0)custom.value=selectedName;
}
function effectiveRegimeId(){
  var institution=currentInstitution();
  return institution?institution.regime:(defaultRegimeForSector(qs("payrollSector").value)||{id:"other-public"}).id;
}
function populateRoles(selectedId){
  var el=qs("payrollRole");if(!el)return;
  var regimeId=effectiveRegimeId(),list=rolesFor(regimeId);
  el.innerHTML=list.map(function(role){return '<option value="'+escapeHtml(role.id)+'">'+escapeHtml(role.label)+(role.coefficient==null?"":" — "+number(role.coefficient,2))+'</option>'}).join("");
  var mapped=legacyRoleMap[selectedId]||selectedId;
  if(list.some(function(role){return role.id===mapped}))el.value=mapped;
  else if(list.length)el.value=list[0].id;
}
function syncRoleCoefficient(force){
  var role=roleById(qs("payrollRole")?qs("payrollRole").value:"");
  if(role&&role.coefficient!=null&&(force||!qs("payrollCoefficient").value))qs("payrollCoefficient").value=Number(role.coefficient).toFixed(2);
}
function syncBaseInput(){
  var regime=currentRegime(),base=baseFor(regime,monthValue(),0),input=qs("payrollCustomBase");
  if(!input)return;
  var requiresManual=!regime||regime.baseType==="manual";
  input.required=requiresManual;
  input.placeholder=requiresManual?"Obavezno — lokalna/ustanovna osnovica":("Automatski: "+money(base));
}
function syncWorkPatternControls(){
  var regime=currentRegime(),rates=regime&&regime.additions||{},second=qs("payrollSecondShift"),turnus=qs("payrollTurnus");
  if(second){second.disabled=rates.secondShift==null;if(second.disabled)second.checked=false}
  if(turnus){turnus.disabled=rates.turnus==null;if(turnus.disabled)turnus.checked=false}
}
function persist(){
  if(applying||!window.RasporedDataStore)return;
  var institution=currentInstitution(),custom=qs("payrollInstitutionCustom"),residence=qs("payrollResidence").value;
  var customResidence=qs("payrollResidenceCustom");
  var payload={
    county:text(qs("payrollCounty").value,80),
    residence:residence===OTHER?text(customResidence&&customResidence.value||"Drugo",100):text(residence,100),
    taxLower:numeric("payrollTaxLower",20,0,50),
    taxHigher:numeric("payrollTaxHigher",30,0,50),
    sector:text(qs("payrollSector").value,100),
    institution:institution?institution.name:text(custom&&custom.value||"Druga javna ustanova",160),
    regimeId:effectiveRegimeId(),
    roleId:text(qs("payrollRole").value,80),
    coefficient:numeric("payrollCoefficient",1,0.1,10),
    yearsService:Math.trunc(numeric("payrollYears",0,0,60)),
    personalAllowance:numeric("payrollPersonalAllowance",config.tax.basicPersonalAllowance||600,0,10000),
    extraPercent:numeric("payrollExtraPercent",0,0,100),
    secondShift:!!qs("payrollSecondShift").checked,
    turnus:!!qs("payrollTurnus").checked,
    customBase:(numeric("payrollCustomBase",0,0,10000)>0)?numeric("payrollCustomBase",0,0,10000):null
  };
  window.RasporedDataStore.set("raspored.payroll.v1",JSON.stringify(payload));
}
function applySaved(){
  if(!config)return;
  applying=true;
  var p=saved();
  var county=p.county||"Primorsko-goranska";
  populateCounties(county);
  populateResidence(p.residence||"Rijeka",county);
  if(qs("payrollResidence").value===OTHER){
    if(qs("payrollResidenceCustom")&&p.residence&&p.residence!=="Drugo")qs("payrollResidenceCustom").value=p.residence;
    qs("payrollTaxLower").value=String(Number.isFinite(Number(p.taxLower))?p.taxLower:20);
    qs("payrollTaxHigher").value=String(Number.isFinite(Number(p.taxHigher))?p.taxHigher:30);
  }
  applyTaxLocality();
  populateSector(p.sector||"Zdravstvo");
  populateInstitutions(p.institution||"Klinički bolnički centar Rijeka",p.regimeId||"kbc-rijeka-2026");
  populateRoles(p.roleId||"health-transport-sss");
  var role=roleById(qs("payrollRole").value);
  qs("payrollCoefficient").value=String(Number(p.coefficient||role&&role.coefficient||1.25).toFixed(2));
  qs("payrollYears").value=String(Number.isFinite(Number(p.yearsService))?Math.trunc(Number(p.yearsService)):0);
  qs("payrollPersonalAllowance").value=String(Number.isFinite(Number(p.personalAllowance))?p.personalAllowance:(config.tax.basicPersonalAllowance||600));
  qs("payrollExtraPercent").value=String(Number(p.extraPercent)||0);
  qs("payrollSecondShift").checked=!!p.secondShift;
  qs("payrollTurnus").checked=!!p.turnus;
  qs("payrollCustomBase").value=Number(p.customBase)>0?String(Number(p.customBase).toFixed(2)):"";
  syncBaseInput();syncWorkPatternControls();
  applying=false;
}
function renderSources(){
  var el=qs("payrollSources");if(!el||!config)return;
  el.innerHTML=(config.sources||[]).map(function(source){
    return '<a href="'+escapeHtml(source.url)+'" target="_blank" rel="noopener noreferrer">'+escapeHtml(source.label)+'</a>';
  }).join("");
}
function estimateNet(gross,allowance,lower,higher){
  var pension=gross*(config.tax.pensionContribution||0.20);
  var income=Math.max(0,gross-pension);
  var taxable=Math.max(0,income-allowance);
  var threshold=Number(config.tax.monthlyThreshold)||5000;
  var tax=Math.min(taxable,threshold)*(lower/100)+Math.max(0,taxable-threshold)*(higher/100);
  return {pension:pension,income:income,taxable:taxable,tax:tax,net:Math.max(0,gross-pension-tax)};
}
function render(){
  if(!config||!qs("view-payroll"))return;
  defaultMonth();
  var parts=monthValue().split("-").map(Number),year=parts[0],monthIndex=parts[1]-1;
  var regime=currentRegime(),role=roleById(qs("payrollRole").value);
  var coefficient=numeric("payrollCoefficient",role&&role.coefficient||1,0.1,10);
  var years=Math.trunc(numeric("payrollYears",0,0,60));
  var extraPercent=numeric("payrollExtraPercent",0,0,100);
  var customBase=numeric("payrollCustomBase",0,0,10000);
  var base=baseFor(regime,monthValue(),customBase);
  var fund=monthlyFund(year,monthIndex);
  var evidence=evidenceForMonth(year,monthIndex);
  var basicGross=base*coefficient*(1+years*0.005);
  var hourly=fund>0?basicGross/fund:0;
  var rates=regime&&regime.additions||{};
  var overtime=Math.max(0,evidence.total-fund*60);
  var secondEnabled=!!qs("payrollSecondShift").checked&&rates.secondShift!=null;
  var turnusEnabled=!!qs("payrollTurnus").checked&&rates.turnus!=null;
  var turnusMinutes=turnusEnabled?evidence.turnus:0;
  var secondShiftMinutes=secondEnabled
    ?(evidence.shift2>0?evidence.shift2:(turnusMinutes>0?0:evidence.secondShift))
    :0;
  var components=[];
  function addComponent(label,minutes,rate){
    if(rate==null)return;
    components.push({label:label,minutes:minutes,rate:Number(rate),value:hourly*(minutes/60)*Number(rate)});
  }
  addComponent("Noćni rad 22:00–06:00",evidence.night,rates.night);
  addComponent("Rad subotom",evidence.saturday,rates.saturday);
  addComponent("Rad nedjeljom",evidence.sunday,rates.sunday);
  addComponent("Rad blagdanom / neradnim danom",evidence.holiday,rates.holiday);
  addComponent("Prekovremeni rad iznad mjesečnog fonda",overtime,rates.overtime);
  if(secondEnabled)addComponent("Druga smjena",secondShiftMinutes,rates.secondShift);
  if(turnusEnabled)addComponent("Rad u turnusu",turnusMinutes,rates.turnus);
  var additions=components.reduce(function(sum,item){return sum+item.value},0);
  var customAddition=basicGross*(extraPercent/100);
  var gross=basicGross+additions+customAddition;
  var lower=numeric("payrollTaxLower",20,0,50),higher=numeric("payrollTaxHigher",30,0,50);
  var allowance=numeric("payrollPersonalAllowance",config.tax.basicPersonalAllowance||600,0,10000);
  var net=estimateNet(gross,allowance,lower,higher);
  var standardDays=Math.max(1,Math.round(fund/8));
  var dayCount=evidence.workedDays||standardDays;
  var dailyGross=gross/dayCount,dailyNet=net.net/dayCount;
  var institution=currentInstitution();
  var institutionName=institution?institution.name:text(qs("payrollInstitutionCustom").value||"Druga javna ustanova",160);

  qs("payrollGross").textContent=base>0?money(gross):"Unesi osnovicu";
  qs("payrollNet").textContent=base>0?money(net.net):"—";
  qs("payrollBase").textContent=base>0?money(base):"Ručni unos";
  qs("payrollCoefResult").textContent=number(coefficient,2);
  qs("payrollSeniority").textContent=years+" god. · +"+number(years*0.5,1)+"%";
  qs("payrollFund").textContent=fund+" h";
  qs("payrollHourlyGross").textContent=base>0?money(hourly):"—";
  qs("payrollWorkedDays").textContent=evidence.workedDays?String(evidence.workedDays):(standardDays+" plan.");
  qs("payrollDailyGross").textContent=base>0?money(dailyGross):"—";
  qs("payrollDailyNet").textContent=base>0?money(dailyNet):"—";
  qs("payrollBasicGross").textContent=base>0?money(basicGross):"—";
  qs("payrollAdditions").textContent=base>0?money(additions+customAddition):"—";
  qs("payrollEvidenceHint").textContent=evidence.total
    ?("Iz "+hours(evidence.total)+" evidentiranog rada"+(evidence.active?" uključujući aktivnu evidenciju.":"."))
    :"Nema evidentiranih sati; prikazana je osnovna mjesečna procjena bez dodataka iz rada.";
  qs("payrollTaxSummary").textContent="MIO 20% · osobni odbitak "+money(allowance)+" · porez "+number(lower,1)+"% / "+number(higher,1)+"% prema prebivalištu.";

  var roleNote=qs("payrollRoleNote");
  if(roleNote){
    var note=[];
    if(regime&&regime.note)note.push(regime.note);
    if(role&&role.note)note.push(role.note);
    if(regime&&regime.baseType==="manual"&&customBase<=0)note.push("Za ovaj režim nema jedne nacionalne osnovice. Unesi osnovicu iz važećeg kolektivnog ugovora/odluke.");
    roleNote.innerHTML="<b>"+escapeHtml(institutionName)+"</b>"+
      "<span>"+escapeHtml(role?role.official:"Drugo radno mjesto")+" · koeficijent "+number(coefficient,2)+"</span>"+
      (note.length?"<small>"+escapeHtml(note.join(" "))+"</small>":"");
  }

  var rows=[{label:"Ukupno evidentirano",minutes:evidence.total,value:null,rate:null}].concat(components);
  if(evidence.shift1)rows.push({label:"1. smjena — evidentirano",minutes:evidence.shift1,rate:null,value:null});
  if(evidence.shift2)rows.push({label:"2. smjena — evidentirano",minutes:evidence.shift2,rate:null,value:null});
  if(evidence.shift3)rows.push({label:"3. smjena — evidentirano",minutes:evidence.shift3,rate:null,value:null});
  if(evidence.duty)rows.push({label:"Dežurstvo — poseban obračun",minutes:evidence.duty,rate:null,value:null});
  if(evidence.standby)rows.push({label:"Pripravnost — poseban obračun",minutes:evidence.standby,rate:null,value:null});
  if(evidence.callout)rows.push({label:"Rad po pozivu — poseban obračun",minutes:evidence.callout,rate:null,value:null});
  if(extraPercent>0)rows.push({label:"Dodatak po rješenju/ugovoru",minutes:null,rate:extraPercent/100,value:customAddition});
  rows.push({label:"Mirovinski doprinosi iz bruto procjene",minutes:null,rate:null,value:-net.pension});
  rows.push({label:"Okvirni porez na dohodak",minutes:null,rate:null,value:-net.tax});
  qs("payrollBreakdown").innerHTML=rows.map(function(item){
    var subtitle=item.minutes==null?"":hours(item.minutes);
    if(item.rate!=null)subtitle+=(subtitle?" · ":"")+"+"+number(item.rate*100,1)+"%";
    return '<div class="payroll-breakdown-row"><span><b>'+escapeHtml(item.label)+'</b><small>'+escapeHtml(subtitle||"—")+'</small></span><strong>'+(item.value==null?"—":money(item.value))+'</strong></div>';
  }).join("");

  var autoRates=[];
  [["Noć",rates.night],["Subota",rates.saturday],["Nedjelja",rates.sunday],["Blagdan",rates.holiday],["Prekovremeni",rates.overtime],["Druga smjena",rates.secondShift],["Turnus",rates.turnus]].forEach(function(pair){
    if(pair[1]!=null)autoRates.push(pair[0]+" "+number(pair[1]*100,1)+"%");
  });
  qs("payrollLegalText").textContent=(regime?regime.label:"Ručni obračun")+" — osnovna bruto plaća računa se kao osnovica × koeficijent + 0,5% za svaku navršenu godinu staža. "+
    (autoRates.length?"Automatski obračunski postoci u ovom presetu: "+autoRates.join(", ")+". ":"Dodaci nisu automatski pretpostavljeni za ovaj režim. ")+
    "Okvirni neto koristi standardni mirovinski doprinos 20%, uneseni osobni odbitak i porezne stope mjesta prebivališta. Dežurstvo, pripravnost i rad po pozivu prikazuju se kao posebni oblici rada i ne dobivaju izmišljenu stopu. Točan obračun uvijek provjeri prema ugovoru, rješenju i obračunskoj ispravi.";
}
function refreshInstitutionAndRole(preferredRole){
  populateInstitutions("",null);
  populateRoles(preferredRole||"");
  syncRoleCoefficient(true);
  syncBaseInput();syncWorkPatternControls();render();
}
function bind(){
  if(bound||!qs("view-payroll"))return;bound=true;
  qs("payrollCounty").addEventListener("change",function(){
    populateResidence(qs("payrollResidence").value,this.value);
    applyTaxLocality();
    refreshInstitutionAndRole();
    persist();
  });
  qs("payrollResidence").addEventListener("change",function(){
    if(this.value!==OTHER&&qs("payrollResidenceCustom"))qs("payrollResidenceCustom").value="";
    applyTaxLocality();persist();render();
  });
  if(qs("payrollResidenceCustom"))qs("payrollResidenceCustom").addEventListener("change",function(){persist();render()});
  ["payrollTaxLower","payrollTaxHigher"].forEach(function(id){
    qs(id).addEventListener("input",function(){render();persist()});
    qs(id).addEventListener("change",persist);
  });
  qs("payrollSector").addEventListener("change",function(){refreshInstitutionAndRole();persist()});
  qs("payrollInstitution").addEventListener("change",function(){
    var manual=this.value===OTHER;
    qs("payrollInstitutionCustomWrap").hidden=!manual;
    populateRoles("");
    syncRoleCoefficient(true);syncBaseInput();syncWorkPatternControls();persist();render();
  });
  qs("payrollInstitutionCustom").addEventListener("change",function(){persist();render()});
  qs("payrollRole").addEventListener("change",function(){syncRoleCoefficient(true);persist();render()});
  ["payrollMonth","payrollCoefficient","payrollYears","payrollCustomBase","payrollPersonalAllowance","payrollExtraPercent","payrollSecondShift","payrollTurnus"].forEach(function(id){
    var el=qs(id);if(!el)return;
    el.addEventListener("change",function(){if(id==="payrollMonth")syncBaseInput();persist();render()});
    if(el.type==="number")el.addEventListener("input",render);
  });
}
async function init(){
  if(initialized||!qs("view-payroll"))return;
  initialized=true;defaultMonth();
  try{
    var response=await fetch((document.body.dataset.base||"")+"/assets/data/payroll-public-sector-2026.json",{cache:"no-store"});
    if(!response.ok)throw new Error("Payroll parameters unavailable");
    config=await response.json();
    applySaved();bind();renderSources();render();
  }catch(error){
    qs("payrollLegalText").textContent="Službeni parametri za izračun trenutačno nisu dostupni.";
  }
}
window.RasporedPayroll={init:init,render:render};
window.addEventListener("raspored:storage-synced",function(){if(document.body.dataset.routeCurrent==="payroll")render()});
})();