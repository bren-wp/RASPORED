(function(){
"use strict";

var config=null;
var bound=false;
var initialized=false;

function qs(id){return document.getElementById(id)}
function money(value){return new Intl.NumberFormat("hr-HR",{style:"currency",currency:"EUR",minimumFractionDigits:2}).format(Number(value)||0)}
function hours(minutes){var m=Math.max(0,Math.round(minutes||0));return (m/60).toLocaleString("hr-HR",{maximumFractionDigits:2})+" h"}
function escapeHtml(value){return String(value).replace(/[&<>"']/g,function(ch){return {"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[ch]})}
function monthValue(){
  var input=qs("payrollMonth");
  if(input&&/^\d{4}-\d{2}$/.test(input.value))return input.value;
  var now=new Date();
  return now.getFullYear()+"-"+String(now.getMonth()+1).padStart(2,"0");
}
function defaultMonth(){
  var input=qs("payrollMonth");if(!input||input.value)return;
  var now=new Date(),year=now.getFullYear(),month=now.getMonth()+1;
  if(year<2026){year=2026;month=1}
  if(year>2026){year=2026;month=12}
  input.value=year+"-"+String(month).padStart(2,"0");
}
function store(){
  return window.RasporedDataStore?window.RasporedDataStore.snapshot():{evidence:[],payroll:{}};
}
function saved(){
  try{return JSON.parse(window.RasporedDataStore.get("raspored.payroll.v1")||"{}")}catch(e){return {}}
}
function persist(){
  if(!window.RasporedDataStore)return;
  var payload={
    roleId:qs("payrollRole")?qs("payrollRole").value:"kbc-transport-nss",
    coefficient:Math.max(1,Math.min(8,Number(qs("payrollCoefficient")&&qs("payrollCoefficient").value)||1.15)),
    yearsService:Math.max(0,Math.min(60,Math.trunc(Number(qs("payrollYears")&&qs("payrollYears").value)||0))),
    extraPercent:Math.max(0,Math.min(100,Number(qs("payrollExtraPercent")&&qs("payrollExtraPercent").value)||0)),
    secondShift:!!(qs("payrollSecondShift")&&qs("payrollSecondShift").checked),
    customBase:(Number(qs("payrollCustomBase")&&qs("payrollCustomBase").value)>0)?Number(qs("payrollCustomBase").value):null
  };
  window.RasporedDataStore.set("raspored.payroll.v1",JSON.stringify(payload));
}
function roleById(id){
  return config&&Array.isArray(config.roles)?config.roles.find(function(role){return role.id===id})||config.roles[0]:null;
}
function selectedBase(month,custom){
  if(Number(custom)>0)return Number(custom);
  if(!config||!Array.isArray(config.bases))return 0;
  var date=month+"-01";
  var candidates=config.bases.filter(function(item){
    return date>=item.from&&(!item.to||date<=item.to);
  });
  return candidates.length?Number(candidates[candidates.length-1].amount):0;
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
  if(!Number.isFinite(start)){
    start=new Date(entry.date+"T"+entry.in+":00").getTime();
  }
  if(!Number.isFinite(start))return null;
  var end=Number(entry.endedAt);
  if(!Number.isFinite(end)){
    if(entry.out){
      end=new Date(entry.date+"T"+entry.out+":00").getTime();
      if(end<=start)end+=24*60*60*1000;
    }else{
      end=Date.now();
    }
  }
  if(!Number.isFinite(end)||end<=start)return null;
  return {start:start,end:Math.min(end,start+36*60*60*1000)};
}
function evidenceForMonth(year,monthIndex){
  var snapshot=store(),entries=Array.isArray(snapshot.evidence)?snapshot.evidence:[],holidays=holidayMap(year);
  var result={total:0,night:0,saturday:0,sunday:0,holiday:0,secondShift:0,active:false};
  entries.forEach(function(entry){
    var span=interval(entry);if(!span)return;
    if(!entry.out&&!Number.isFinite(Number(entry.endedAt)))result.active=true;
    for(var t=span.start;t<span.end;t+=60000){
      var d=new Date(t);
      if(d.getFullYear()!==year||d.getMonth()!==monthIndex)continue;
      result.total++;
      var hour=d.getHours();
      if(hour>=22||hour<6)result.night++;
      if(d.getDay()===6)result.saturday++;
      if(d.getDay()===0)result.sunday++;
      if(holidays[iso(d)])result.holiday++;
      if(hour>=14&&hour<22)result.secondShift++;
    }
  });
  return result;
}
function applySaved(){
  if(!config)return;
  var p=saved(),role=roleById(p.roleId||"kbc-transport-nss");
  var select=qs("payrollRole");
  if(select){
    select.innerHTML=config.roles.map(function(item){return '<option value="'+escapeHtml(item.id)+'">'+escapeHtml(item.label)+'</option>'}).join("");
    select.value=role?role.id:config.roles[0].id;
  }
  if(qs("payrollCoefficient"))qs("payrollCoefficient").value=String(Number(p.coefficient||role.coefficient).toFixed(2));
  if(qs("payrollYears"))qs("payrollYears").value=String(Number.isFinite(Number(p.yearsService))?Math.trunc(Number(p.yearsService)):0);
  if(qs("payrollExtraPercent"))qs("payrollExtraPercent").value=String(Number(p.extraPercent)||0);
  if(qs("payrollSecondShift"))qs("payrollSecondShift").checked=!!p.secondShift;
  if(qs("payrollCustomBase"))qs("payrollCustomBase").value=Number(p.customBase)>0?String(Number(p.customBase).toFixed(2)):"";
}
function renderSources(){
  var el=qs("payrollSources");if(!el||!config)return;
  el.innerHTML=config.sources.map(function(source){
    return '<a href="'+escapeHtml(source.url)+'" target="_blank" rel="noopener noreferrer">'+escapeHtml(source.label)+'</a>';
  }).join("");
}
function render(){
  if(!config||!qs("view-payroll"))return;
  defaultMonth();
  var month=monthValue().split("-").map(Number),year=month[0],monthIndex=month[1]-1;
  var role=roleById(qs("payrollRole")?qs("payrollRole").value:"");
  var coefficient=Math.max(1,Math.min(8,Number(qs("payrollCoefficient")&&qs("payrollCoefficient").value)||Number(role&&role.coefficient)||1));
  var years=Math.max(0,Math.min(60,Math.trunc(Number(qs("payrollYears")&&qs("payrollYears").value)||0)));
  var extraPercent=Math.max(0,Math.min(100,Number(qs("payrollExtraPercent")&&qs("payrollExtraPercent").value)||0));
  var customBase=Number(qs("payrollCustomBase")&&qs("payrollCustomBase").value)||0;
  var base=selectedBase(monthValue(),customBase);
  var fund=monthlyFund(year,monthIndex);
  var evidence=evidenceForMonth(year,monthIndex);
  var basicWithoutSeniority=base*coefficient;
  var basicGross=basicWithoutSeniority*(1+years*config.additions.seniorityPerYear);
  var hourly=fund>0?basicGross/fund:0;
  var overtime=Math.max(0,evidence.total-fund*60);
  var components=[
    {label:"Noćni rad 22:00–06:00",minutes:evidence.night,rate:config.additions.night},
    {label:"Rad subotom",minutes:evidence.saturday,rate:config.additions.saturday},
    {label:"Rad nedjeljom",minutes:evidence.sunday,rate:config.additions.sunday},
    {label:"Rad blagdanom / neradnim danom",minutes:evidence.holiday,rate:config.additions.holiday},
    {label:"Prekovremeni rad iznad mjesečnog fonda",minutes:overtime,rate:config.additions.overtime}
  ];
  if(qs("payrollSecondShift")&&qs("payrollSecondShift").checked)components.push({label:"Druga smjena 14:00–22:00",minutes:evidence.secondShift,rate:config.additions.secondShift});
  var additions=components.reduce(function(sum,item){return sum+hourly*(item.minutes/60)*item.rate},0);
  var customAddition=basicGross*(extraPercent/100);
  var estimate=basicGross+additions+customAddition;

  if(qs("payrollGross"))qs("payrollGross").textContent=money(estimate);
  if(qs("payrollBase"))qs("payrollBase").textContent=money(base);
  if(qs("payrollCoefResult"))qs("payrollCoefResult").textContent=coefficient.toFixed(2).replace(".",",");
  if(qs("payrollSeniority"))qs("payrollSeniority").textContent=years+" god. · +"+(years*0.5).toLocaleString("hr-HR",{maximumFractionDigits:1})+"%";
  if(qs("payrollFund"))qs("payrollFund").textContent=fund+" h";
  if(qs("payrollBasicGross"))qs("payrollBasicGross").textContent=money(basicGross);
  if(qs("payrollAdditions"))qs("payrollAdditions").textContent=money(additions+customAddition);
  if(qs("payrollEvidenceHint"))qs("payrollEvidenceHint").textContent=evidence.total?("Iz "+hours(evidence.total)+" evidentiranog rada"+(evidence.active?" uključujući aktivnu evidenciju.":". ")):"Nema evidentiranih sati za odabrani mjesec; prikazana je osnovna bruto procjena.";
  if(qs("payrollRoleNote")){
    var note=(role&&role.note)?role.note:"Koeficijent je preuzet iz službene Uredbe; provjeri službeni naziv svog radnog mjesta na ugovoru/rješenju.";
    qs("payrollRoleNote").innerHTML="<b>"+escapeHtml(role?role.official:"Radno mjesto")+"</b><span>Šifra "+escapeHtml(role?role.code:"—")+" · koeficijent "+coefficient.toFixed(2).replace(".",",")+"</span><small>"+escapeHtml(note)+"</small>";
  }
  if(qs("payrollBreakdown")){
    var rows=[{label:"Ukupno evidentirano",minutes:evidence.total,rate:null}].concat(components);
    if(extraPercent>0)rows.push({label:"Dodatak po rješenju/ugovoru",minutes:null,rate:extraPercent/100,value:customAddition});
    qs("payrollBreakdown").innerHTML=rows.map(function(item){
      var value=item.value!=null?item.value:(item.rate==null?null:hourly*((item.minutes||0)/60)*item.rate);
      return '<div class="payroll-breakdown-row"><span><b>'+escapeHtml(item.label)+'</b><small>'+(item.minutes==null?"Osnovna bruto baza":hours(item.minutes))+(item.rate==null?"":" · +"+Math.round(item.rate*100)+"%")+'</small></span><strong>'+(value==null?"—":money(value))+'</strong></div>';
    }).join("");
  }
  if(qs("payrollLegalText"))qs("payrollLegalText").textContent="Osnovna bruto plaća računa se kao osnovica × koeficijent, uz +0,5% za svaku navršenu godinu staža. Cijena sata temelji se na mjesečnom fondu, a noćni, subotnji, nedjeljni, blagdanski i prekovremeni dodaci procjenjuju se iz stvarne Evidencije sati. Posebne dodatke koji ovise o konkretnom rješenju ili radnom mjestu aplikacija ne pretpostavlja automatski. Važeći javni sustav koristi koeficijent radnog mjesta, a ne jedinstveni bod koji pojedina bolnica proizvoljno određuje.";
}
function bind(){
  if(bound||!qs("view-payroll"))return;bound=true;
  ["payrollMonth","payrollCoefficient","payrollYears","payrollCustomBase","payrollExtraPercent","payrollSecondShift"].forEach(function(id){
    var el=qs(id);if(!el)return;
    el.addEventListener("change",function(){persist();render()});
    if(el.type==="number")el.addEventListener("input",render);
  });
  var role=qs("payrollRole");
  if(role)role.addEventListener("change",function(){
    var selected=roleById(this.value);
    if(selected&&qs("payrollCoefficient"))qs("payrollCoefficient").value=Number(selected.coefficient).toFixed(2);
    persist();render();
  });
}
async function init(){
  if(initialized||!qs("view-payroll"))return;
  initialized=true;
  defaultMonth();
  try{
    var response=await fetch((document.body.dataset.base||"")+"/assets/data/payroll-public-health-2026.json",{cache:"no-store"});
    if(!response.ok)throw new Error("Payroll parameters unavailable");
    config=await response.json();
    applySaved();bind();renderSources();render();
  }catch(error){
    if(qs("payrollLegalText"))qs("payrollLegalText").textContent="Službeni parametri za izračun trenutačno nisu dostupni.";
  }
}
window.RasporedPayroll={init:init,render:render};
window.addEventListener("raspored:storage-synced",function(){if(document.body.dataset.routeCurrent==="payroll")render()});
})();