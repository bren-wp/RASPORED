(function(){
"use strict";

var config=null;
var bound=false;
var initialized=false;

function qs(id){return document.getElementById(id)}
function money(value){return new Intl.NumberFormat("hr-HR",{style:"currency",currency:"EUR",minimumFractionDigits:2}).format(Number(value)||0)}
function numberHr(value,digits){return (Number(value)||0).toLocaleString("hr-HR",{minimumFractionDigits:digits||0,maximumFractionDigits:digits==null?2:digits})}
function hoursFromMinutes(minutes){var m=Math.max(0,Math.round(Number(minutes)||0));return numberHr(m/60,2)+" h"}
function escapeHtml(value){return String(value).replace(/[&<>"']/g,function(ch){return {"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[ch]})}
function finiteOrNull(value){var n=Number(value);return Number.isFinite(n)?n:null}
function inputNumber(id,fallback){var el=qs(id),n=el?finiteOrNull(el.value):null;return n==null?fallback:n}
function nullableInput(id){var el=qs(id);if(!el||String(el.value).trim()==="")return null;return finiteOrNull(el.value)}
function clamp(n,min,max){return Math.max(min,Math.min(max,Number(n)||0))}

function monthValue(){
  var input=qs("payrollMonth");
  if(input&&/^\d{4}-\d{2}$/.test(input.value))return input.value;
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
function saved(){try{return JSON.parse(window.RasporedDataStore.get("raspored.payroll.v1")||"{}")}catch(e){return {}}}
function roleById(id){return config&&Array.isArray(config.roles)?config.roles.find(function(x){return x.id===id})||config.roles[0]:null}
function institutionById(id){return config&&Array.isArray(config.institutions)?config.institutions.find(function(x){return x.id===id})||config.institutions[0]:null}
function rateProfileById(id){return config&&Array.isArray(config.rateProfiles)?config.rateProfiles.find(function(x){return x.id===id})||config.rateProfiles[0]:null}

function selectedBase(month,custom){
  if(Number(custom)>0)return Number(custom);
  if(!config||!Array.isArray(config.bases))return 0;
  var date=month+"-01";
  var hit=config.bases.find(function(item){return date>=item.from&&date<=item.to});
  return hit?Number(hit.amount):0;
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
  var start=Number(entry.startedAt);
  if(!Number.isFinite(start))start=new Date(entry.date+"T"+entry.in+":00").getTime();
  if(!Number.isFinite(start))return null;
  var end=Number(entry.endedAt);
  if(!Number.isFinite(end)){
    if(entry.out){
      end=new Date(entry.date+"T"+entry.out+":00").getTime();
      if(end<=start)end+=86400000;
    }else end=Date.now();
  }
  if(!Number.isFinite(end)||end<=start)return null;
  return {start:start,end:Math.min(end,start+36*60*60*1000)};
}
function evidenceForMonth(year,monthIndex){
  var entries=Array.isArray(snapshot().evidence)?snapshot().evidence:[],holidays=holidayMap(year);
  var r={total:0,night:0,saturday:0,sunday:0,holiday:0,secondShiftClock:0,active:false};
  entries.forEach(function(entry){
    var span=interval(entry);if(!span)return;
    if(!entry.out&&!Number.isFinite(Number(entry.endedAt)))r.active=true;
    for(var t=span.start;t<span.end;t+=60000){
      var d=new Date(t);
      if(d.getFullYear()!==year||d.getMonth()!==monthIndex)continue;
      r.total++;
      var hour=d.getHours();
      if(hour>=22||hour<6)r.night++;
      if(d.getDay()===6)r.saturday++;
      if(d.getDay()===0)r.sunday++;
      if(holidays[iso(d)])r.holiday++;
      if(hour>=14&&hour<22)r.secondShiftClock++;
    }
  });
  return r;
}

function currentPayload(){
  return {
    institutionId:qs("payrollInstitution")?qs("payrollInstitution").value:"kbc-rijeka",
    rateProfileId:qs("payrollRateProfile")?qs("payrollRateProfile").value:"national-public",
    roleId:qs("payrollRole")?qs("payrollRole").value:"kbc-transport-nss",
    coefficient:clamp(inputNumber("payrollCoefficient",1.15),1,8),
    yearsService:Math.trunc(clamp(inputNumber("payrollYears",0),0,60)),
    extraPercent:clamp(inputNumber("payrollExtraPercent",0),0,100),
    customBase:(function(){var n=nullableInput("payrollCustomBase");return n!=null&&n>0?clamp(n,0,10000):null})(),
    overtimeHours:(function(){var n=nullableInput("payrollOvertimeHours");return n==null?null:clamp(n,0,250)})(),
    turnusHours:clamp(inputNumber("payrollTurnusHours",0),0,300),
    secondShiftHours:clamp(inputNumber("payrollSecondShiftHours",0),0,300),
    grossAdjustment:clamp(inputNumber("payrollGrossAdjustment",0),-10000,10000)
  };
}
function persist(){if(window.RasporedDataStore)window.RasporedDataStore.set("raspored.payroll.v1",JSON.stringify(currentPayload()))}

function fillSelect(select,items,value,labelFn){
  if(!select)return;
  select.innerHTML=items.map(function(item){return '<option value="'+escapeHtml(item.id)+'">'+escapeHtml(labelFn(item))+'</option>'}).join("");
  if(items.some(function(item){return item.id===value}))select.value=value;
}
function applySaved(){
  if(!config)return;
  var p=saved();
  var institution=institutionById(p.institutionId||"kbc-rijeka");
  var role=roleById(p.roleId||"kbc-transport-nss");
  var profile=rateProfileById(p.rateProfileId||(institution&&institution.profileId)||"national-public");
  fillSelect(qs("payrollInstitution"),config.institutions,institution.id,function(item){return item.name+" — "+item.city});
  fillSelect(qs("payrollRateProfile"),config.rateProfiles,profile.id,function(item){return item.label});
  fillSelect(qs("payrollRole"),config.roles,role.id,function(item){return item.label});
  qs("payrollCoefficient").value=Number(p.coefficient||role.coefficient).toFixed(2);
  qs("payrollYears").value=String(Number.isFinite(Number(p.yearsService))?Math.trunc(Number(p.yearsService)):0);
  qs("payrollExtraPercent").value=String(Number(p.extraPercent)||0);
  qs("payrollCustomBase").value=Number(p.customBase)>0?Number(p.customBase).toFixed(2):"";
  qs("payrollOvertimeHours").value=p.overtimeHours!=null&&Number.isFinite(Number(p.overtimeHours))?String(Number(p.overtimeHours)):"";
  qs("payrollTurnusHours").value=Number(p.turnusHours)>0?String(Number(p.turnusHours)):"";
  qs("payrollSecondShiftHours").value=Number(p.secondShiftHours)>0?String(Number(p.secondShiftHours)):"";
  qs("payrollGrossAdjustment").value=Number(p.grossAdjustment)!==0?String(Number(p.grossAdjustment)):"";
}
function renderSources(){
  var el=qs("payrollSources");if(!el||!config)return;
  el.innerHTML=config.sources.map(function(source){
    return '<a href="'+escapeHtml(source.url)+'" target="_blank" rel="noopener noreferrer">'+escapeHtml(source.label)+'</a>';
  }).join("");
}

function calculate(){
  var parts=monthValue().split("-").map(Number),year=parts[0],monthIndex=parts[1]-1;
  var p=currentPayload(),role=roleById(p.roleId),institution=institutionById(p.institutionId),profile=rateProfileById(p.rateProfileId);
  var base=selectedBase(monthValue(),p.customBase),fund=monthlyFund(year,monthIndex),evidence=evidenceForMonth(year,monthIndex);
  var seniorityRate=p.yearsService*Number(config.nationalRules.seniorityPerYear||0.005);
  var monthlyBasicNoSeniority=base*p.coefficient;
  var monthlyBasic=monthlyBasicNoSeniority*(1+seniorityRate);
  var hourly=fund>0?monthlyBasic/fund:0;
  var autoOvertime=Math.max(0,evidence.total/60-fund);
  var overtimeHours=p.overtimeHours==null?autoOvertime:p.overtimeHours;
  var overtimeBase=hourly*overtimeHours;
  var components=[
    {key:"night",label:"Noćni rad 22:00–06:00",hours:evidence.night/60,rate:Number(profile.night)},
    {key:"saturday",label:"Rad subotom",hours:evidence.saturday/60,rate:Number(profile.saturday)},
    {key:"sunday",label:"Rad nedjeljom",hours:evidence.sunday/60,rate:Number(profile.sunday)},
    {key:"holiday",label:"Rad blagdanom / neradnim danom",hours:evidence.holiday/60,rate:Number(profile.holiday)},
    {key:"turnus",label:"Rad u turnusu",hours:p.turnusHours,rate:Number(profile.turnus)},
    {key:"secondShift",label:"Rad u drugoj smjeni",hours:p.secondShiftHours,rate:Number(profile.secondShift)},
    {key:"overtime",label:"Dodatak za prekovremeni rad",hours:overtimeHours,rate:Number(profile.overtime)}
  ];
  components.forEach(function(x){x.amount=hourly*x.hours*x.rate});
  var premiumTotal=components.reduce(function(sum,x){return sum+x.amount},0);
  var extraAmount=monthlyBasic*(p.extraPercent/100);
  var total=monthlyBasic+overtimeBase+premiumTotal+extraAmount+p.grossAdjustment;
  return {
    p:p,role:role,institution:institution,profile:profile,base:base,fund:fund,evidence:evidence,
    seniorityRate:seniorityRate,monthlyBasic:monthlyBasic,hourly:hourly,autoOvertime:autoOvertime,
    overtimeHours:overtimeHours,overtimeBase:overtimeBase,components:components,premiumTotal:premiumTotal,
    extraAmount:extraAmount,total:total
  };
}

function render(){
  if(!config||!qs("view-payroll"))return;
  defaultMonth();
  var x=calculate();
  qs("payrollGross").textContent=money(x.total);
  qs("payrollBase").textContent=money(x.base);
  qs("payrollCoefResult").textContent=numberHr(x.p.coefficient,2);
  qs("payrollSeniority").textContent=x.p.yearsService+" god. · +"+numberHr(x.seniorityRate*100,1)+"%";
  qs("payrollFund").textContent=x.fund+" h";
  qs("payrollHourly").textContent=money(x.hourly)+"/h";
  qs("payrollBasicGross").textContent=money(x.monthlyBasic);
  qs("payrollOvertimeBase").textContent=money(x.overtimeBase);
  qs("payrollAdditions").textContent=money(x.premiumTotal+x.extraAmount+x.p.grossAdjustment);

  var evidenceText=x.evidence.total
    ? "Evidentirano "+hoursFromMinutes(x.evidence.total)+(x.evidence.active?"; aktivna evidencija je uključena.":".")
    : "Nema Evidencije sati za ovaj mjesec; prikazana je osnovna mjesečna bruto procjena.";
  if(x.p.overtimeHours==null&&x.autoOvertime>0)evidenceText+=" Prekovremeni su automatski procijenjeni kao sati iznad mjesečnog fonda.";
  qs("payrollEvidenceHint").textContent=evidenceText;

  var roleNote=(x.role.note?x.role.note+" ":"")+"Koeficijent je državni koeficijent službenog radnog mjesta; svakodnevni naziv posla nije dovoljan za promjenu koeficijenta.";
  qs("payrollRoleNote").innerHTML="<b>"+escapeHtml(x.role.official)+"</b><span>Šifra "+escapeHtml(x.role.code)+" · koeficijent "+numberHr(x.p.coefficient,2)+"</span><small>"+escapeHtml(roleNote)+"</small>";

  var profileNote=x.profile.note||"";
  if(x.institution.verifyRegime)profileNote+=" Za ovu ustanovu posebno provjeri primjenjuje li se sustav plaća javnih službi na tvoje konkretno zaposlenje.";
  if(x.institution.id==="kbc-rijeka")profileNote+=" KBC Rijeka lokalne nazive radnih mjesta treba usporediti s aktualnom sistematizacijom.";
  qs("payrollProfileNote").innerHTML="<b>"+escapeHtml(x.profile.label)+"</b><small>"+escapeHtml(profileNote)+"</small>";

  var rows=[
    {label:"Osnovna mjesečna bruto + staž",caption:"Osnovica × koeficijent × (1 + staž)",amount:x.monthlyBasic},
    {label:"Osnovna vrijednost prekovremenih sati",caption:numberHr(x.overtimeHours,2)+" h × cijena sata",amount:x.overtimeBase}
  ].concat(x.components.map(function(item){
    return {label:item.label,caption:numberHr(item.hours,2)+" h · +"+numberHr(item.rate*100,0)+"%",amount:item.amount};
  }));
  if(x.p.extraPercent>0)rows.push({label:"Dodatak po rješenju / ugovoru",caption:"+"+numberHr(x.p.extraPercent,1)+"% osnovne bruto + staž",amount:x.extraAmount});
  if(x.p.grossAdjustment!==0)rows.push({label:"Korekcija / naknada bruto",caption:"Ručni iznos za GO, bolovanje ili drugu obračunsku stavku",amount:x.p.grossAdjustment});
  qs("payrollBreakdown").innerHTML=rows.map(function(item){
    return '<div class="payroll-breakdown-row"><span><b>'+escapeHtml(item.label)+'</b><small>'+escapeHtml(item.caption)+'</small></span><strong>'+money(item.amount)+'</strong></div>';
  }).join("");

  var legal="Cijena sata računa se kao osnovna plaća uvećana za dodatak za radni staž, podijeljena mjesečnim fondom sati. Staž iznosi 0,5% po navršenoj godini. TKU propisuje prekovremeni 50%, noćni 40%, drugu smjenu 10%, subotu 25%, nedjelju 50% i blagdan/neradni dan 150%; turnus 5% proizlazi iz službenog tumačenja članka 109. TKU-a kada je povoljnije. Profil KBC Rijeka odvojeno može koristiti potvrđeni obračunski noćni dodatak 50%, bez generaliziranja na druge bolnice.";
  if(x.p.turnusHours>0&&x.p.secondShiftHours>0)legal+=" Turnus i druga smjena smiju se odnositi na različite sate; aplikacija ih ne smatra kumulativnima za iste sate.";
  qs("payrollLegalText").textContent=legal;
}

function bind(){
  if(bound||!qs("view-payroll"))return;bound=true;
  ["payrollMonth","payrollCoefficient","payrollYears","payrollCustomBase","payrollExtraPercent","payrollOvertimeHours","payrollTurnusHours","payrollSecondShiftHours","payrollGrossAdjustment"].forEach(function(id){
    var el=qs(id);if(!el)return;
    el.addEventListener("change",function(){persist();render()});
    if(el.type==="number")el.addEventListener("input",render);
  });
  qs("payrollRole").addEventListener("change",function(){
    var role=roleById(this.value);if(role)qs("payrollCoefficient").value=Number(role.coefficient).toFixed(2);
    persist();render();
  });
  qs("payrollInstitution").addEventListener("change",function(){
    var institution=institutionById(this.value);
    if(institution&&institution.profileId&&rateProfileById(institution.profileId))qs("payrollRateProfile").value=institution.profileId;
    persist();render();
  });
  qs("payrollRateProfile").addEventListener("change",function(){persist();render()});
}

async function init(){
  if(initialized||!qs("view-payroll"))return;
  initialized=true;defaultMonth();
  try{
    var response=await fetch((document.body.dataset.base||"")+"/assets/data/payroll-public-health-2026.json",{cache:"no-store"});
    if(!response.ok)throw new Error("Payroll parameters unavailable");
    config=await response.json();
    applySaved();bind();renderSources();render();
  }catch(error){
    if(qs("payrollLegalText"))qs("payrollLegalText").textContent="Službeni parametri za izračun trenutačno nisu dostupni.";
  }
}
window.RasporedPayroll={init:init,render:render,calculate:function(){return config?calculate():null}};
window.addEventListener("raspored:storage-synced",function(){if(document.body.dataset.routeCurrent==="payroll")render()});
})();
