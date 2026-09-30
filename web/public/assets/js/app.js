(function(){
"use strict";
var months=["Siječanj","Veljača","Ožujak","Travanj","Svibanj","Lipanj","Srpanj","Kolovoz","Rujan","Listopad","Studeni","Prosinac"];
var weekdays=["Ned","Pon","Uto","Sri","Čet","Pet","Sub"];
var state={route:"home",cursor:new Date(),selected:new Date(),schedule:{},demo:new URLSearchParams(location.search).get("demo")==="1"};
state.cursor=new Date(state.cursor.getFullYear(),state.cursor.getMonth(),1);
state.selected=new Date();

function icon(name,extra){
  var base=(window.RASPORED_BASE||"")+"/assets/brand/icons.svg#icon-"+name;
  return '<svg class="ui-icon '+(extra||"")+'" aria-hidden="true"><use href="'+base+'"></use></svg>';
}

function configureProfile(){
  var saved="";
  try{saved=(localStorage.getItem("raspored.profile.name")||"").trim().slice(0,80)}catch(e){}
  var name=state.demo?"Marko Marković":saved;
  var display=name||"Korisnik";
  var parts=display.split(/\s+/).filter(Boolean);
  var initials=parts.slice(0,2).map(function(x){return x.charAt(0).toUpperCase()}).join("")||"K";
  var nameEl=document.getElementById("profileName"),initialEl=document.getElementById("profileInitials"),welcome=document.getElementById("welcomeTitle");
  if(nameEl)nameEl.textContent=display;
  if(initialEl)initialEl.textContent=initials;
  if(welcome){
    if(name&&name!=="Korisnik")welcome.textContent="Dobro došao, "+parts[0]+"!";
    else welcome.textContent="Dobro došao!";
  }
}
function loadColleagues(){
  try{
    var raw=JSON.parse(localStorage.getItem("raspored.colleagues.v1")||"[]");
    return Array.isArray(raw)?raw.filter(function(x){return x&&typeof x.name==="string"}).slice(0,30):[];
  }catch(e){return []}
}
function saveColleagues(items){localStorage.setItem("raspored.colleagues.v1",JSON.stringify(items.slice(0,30)))}
function renderColleagues(){
  var el=document.getElementById("colleaguesList");if(!el)return;
  var items=loadColleagues();
  if(!items.length){
    el.innerHTML='<div class="colleagues-empty">'+icon("users")+'<h2>Još nema dodanih kolega</h2><p>Dodaj kolegu kako bi njegov raspored bio brzo dostupan na jednom mjestu.</p></div>';
    return;
  }
  el.innerHTML=items.map(function(item,index){
    var initials=item.name.split(/\s+/).filter(Boolean).slice(0,2).map(function(x){return x.charAt(0).toUpperCase()}).join("");
    return '<article class="colleague-row"><span class="colleague-avatar">'+escapeHtml(initials||"K")+'</span><span><b>'+escapeHtml(item.name)+'</b><small>'+escapeHtml(item.note||"Raspored kolege")+'</small></span><button type="button" class="icon-btn" data-remove-colleague="'+index+'" aria-label="Ukloni '+escapeHtml(item.name)+'">×</button></article>';
  }).join("");
}
function addColleague(){
  var name=window.prompt("Ime i prezime kolege:","");
  if(!name)return;
  name=name.trim().replace(/\s+/g," ").slice(0,80);
  if(name.length<2){toast("Unesi valjano ime kolege.");return}
  var items=loadColleagues();
  if(items.some(function(x){return x.name.toLocaleLowerCase("hr-HR")===name.toLocaleLowerCase("hr-HR")})){toast("Kolega je već dodan.");return}
  items.push({name:name,note:"Lokalni kontakt"});saveColleagues(items);renderColleagues();toast("Kolega je dodan.");
}
function yearMonthKey(y,m){return y+"-"+String(m+1).padStart(2,"0")}
function renderPeriodMenu(){
  var menu=document.getElementById("statsPeriodMenu");if(!menu)return;
  var now=appNow(),current=yearMonthKey(state.cursor.getFullYear(),state.cursor.getMonth());
  menu.innerHTML="";
  for(var i=0;i<12;i++){
    var d=new Date(now.getFullYear(),now.getMonth()-i,1),key=yearMonthKey(d.getFullYear(),d.getMonth()),button=document.createElement("button");
    button.type="button";button.setAttribute("role","menuitem");button.dataset.period=key;
    button.className=key===current?"is-active":"";
    button.textContent=months[d.getMonth()]+" "+d.getFullYear()+".";
    menu.appendChild(button);
  }
}
function openSearch(){
  var q=window.prompt("Pretraži RASPORED:","");
  if(!q)return;
  var normalized=q.trim().toLocaleLowerCase("hr-HR");
  var routes=[
    ["početna","home"],["pocetna","home"],["kalendar","calendar"],["raspored","calendar"],
    ["skeniraj","scan"],["scan","scan"],["statistika","stats"],["sati","hours"],["evidencija","hours"],
    ["kolege","colleagues"],["postavke","settings"]
  ];
  var match=routes.find(function(x){return normalized.indexOf(x[0])>=0});
  if(match)route(match[1]);else toast("Nema rezultata za „"+q.trim().slice(0,40)+"“.");
}
function iso(d){return d.getFullYear()+"-"+String(d.getMonth()+1).padStart(2,"0")+"-"+String(d.getDate()).padStart(2,"0")}
function easter(y){var a=y%19,b=Math.floor(y/100),c=y%100,d=Math.floor(b/4),e=b%4,f=Math.floor((b+8)/25),g=Math.floor((b-f+1)/3),h=(19*a+b-d-g+15)%30,i=Math.floor(c/4),k=c%4,l=(32+2*e+2*i-h-k)%7,m=Math.floor((a+11*h+22*l)/451),mo=Math.floor((h+l-7*m+114)/31)-1,da=((h+l-7*m+114)%31)+1;return new Date(y,mo,da)}
function addDays(d,n){var x=new Date(d);x.setDate(x.getDate()+n);return x}
function holidays(y){var map={};function add(m,d,n){map[y+"-"+String(m).padStart(2,"0")+"-"+String(d).padStart(2,"0")]=n}add(1,1,"Nova godina");add(1,6,"Bogojavljenje");add(5,1,"Praznik rada");add(5,30,"Dan državnosti");add(6,22,"Dan antifašističke borbe");add(8,5,"Dan pobjede i domovinske zahvalnosti i Dan hrvatskih branitelja");add(8,15,"Velika Gospa");add(11,1,"Svi sveti");add(11,18,"Dan sjećanja na žrtve Domovinskog rata");add(12,25,"Božić");add(12,26,"Sveti Stjepan");var e=easter(y);map[iso(e)]="Uskrs";map[iso(addDays(e,1))]="Uskrsni ponedjeljak";map[iso(addDays(e,60))]="Tijelovo";return map}
function demoSchedule(year,month){var s={},pattern=["D","N","D",null,null,"D","D","GO","D","N",null,null,"D","GO","D","D","N",null,null,"D",null,"N",null,"D",null,null,"BO",null,null,null,null];var days=new Date(year,month+1,0).getDate();for(var i=1;i<=days;i++){var code=pattern[(i-1)%pattern.length];if(code)s[iso(new Date(year,month,i))]=code}if(year===2026&&month===9){s["2026-10-16"]="D";s["2026-10-17"]="N"}return s}
function loadSchedule(){try{state.schedule=JSON.parse(localStorage.getItem("raspored.schedule")||"{}")}catch(e){state.schedule={}}if(state.demo&&Object.keys(state.schedule).length===0){state.schedule=demoSchedule(2026,9);state.cursor=new Date(2026,9,1);state.selected=new Date(2026,9,16)}}
function save(){localStorage.setItem("raspored.schedule",JSON.stringify(state.schedule))}
function appNow(){return state.demo?new Date(2026,9,16,9,0,0):new Date()}
function loadTimeEntries(){try{var raw=JSON.parse(localStorage.getItem("raspored.timeEntries.v1")||"[]");return Array.isArray(raw)?raw.filter(function(x){return x&&/^\d{4}-\d{2}-\d{2}$/.test(x.date||"")&&/^\d{2}:\d{2}$/.test(x.in||"")}):[]}catch(e){return []}}
function saveTimeEntries(entries){localStorage.setItem("raspored.timeEntries.v1",JSON.stringify(entries.slice(-366)))}
function hhmm(d){return String(d.getHours()).padStart(2,"0")+":"+String(d.getMinutes()).padStart(2,"0")}
function durationMinutes(entry,now){
  if(!entry||!entry.in)return 0;
  function minutes(v){var p=v.split(":").map(Number);return p[0]*60+p[1]}
  var start=minutes(entry.in),end=entry.out?minutes(entry.out):(now.getHours()*60+now.getMinutes());
  if(end<start)end+=24*60;
  return Math.max(0,end-start);
}
function durationLabel(mins){return Math.floor(mins/60)+"h "+String(mins%60).padStart(2,"0")+"min"}
function currentTimeEntry(entries,dateKey){for(var i=entries.length-1;i>=0;i--){if(entries[i].date===dateKey&&!entries[i].out)return entries[i]}return null}
function latestTimeEntry(entries,dateKey){for(var i=entries.length-1;i>=0;i--){if(entries[i].date===dateKey)return entries[i]}return null}
function shiftMeta(code){return {D:{name:"Dnevna smjena",time:"07:00 – 19:00 (12h)",hours:12},N:{name:"Noćna smjena",time:"19:00 – 07:00 (12h)",hours:12},GO:{name:"Slobodan dan",time:"—",hours:0},BO:{name:"Bolovanje",time:"—",hours:0}}[code]||{name:"Slobodno",time:"—",hours:0}}
function hoursText(minutes){
  var mins=Math.max(0,Math.round(minutes||0)),h=Math.floor(mins/60),m=mins%60;
  return m===0?h+"h":h+"h "+String(m).padStart(2,"0")+"min";
}
function signedHoursText(minutes){
  var mins=Math.round(minutes||0),sign=mins>0?"+":mins<0?"-":"";
  return sign+hoursText(Math.abs(mins));
}
function largeHoursText(minutes){
  var mins=Math.max(0,Math.round(minutes||0));
  return Math.floor(mins/60)+":"+String(mins%60).padStart(2,"0")+" h";
}
function monthData(y,m){
  var out={
    worked:0,workedMinutes:0,dayMinutes:0,night:0,nightMinutes:0,otherMinutes:0,
    sat:0,sun:0,holidays:0,satMinutes:0,sunMinutes:0,holidayMinutes:0,
    go:0,bo:0,planned:0,balance:0,balanceMinutes:0,
    counts:{D:0,N:0,GO:0,BO:0},
    weeks:[{d:0,n:0,o:0},{d:0,n:0,o:0},{d:0,n:0,o:0},{d:0,n:0,o:0},{d:0,n:0,o:0}]
  };
  var hm=holidays(y),days=new Date(y,m+1,0).getDate(),day,code,date,key,wi;
  for(day=1;day<=days;day++){
    date=new Date(y,m,day);key=iso(date);code=state.schedule[key];
    if(!code)continue;
    out.counts[code]=(out.counts[code]||0)+1;
    if(code==="D"||code==="N"){
      out.planned+=12;
      if(date.getDay()===6)out.sat++;
      if(date.getDay()===0)out.sun++;
      if(hm[key])out.holidays++;
    }else if(code==="GO")out.go++;
    else if(code==="BO")out.bo++;
  }

  var prefix=y+"-"+String(m+1).padStart(2,"0")+"-";
  var entries=loadTimeEntries().filter(function(x){return x.out&&x.date.indexOf(prefix)===0});
  entries.forEach(function(entry){
    var mins=durationMinutes(entry,appNow());
    var ed=new Date(entry.date+"T12:00:00");
    var ecode=state.schedule[entry.date]||"";
    out.workedMinutes+=mins;
    if(ecode==="D")out.dayMinutes+=mins;
    else if(ecode==="N")out.nightMinutes+=mins;
    else out.otherMinutes+=mins;
    if(ed.getDay()===6)out.satMinutes+=mins;
    if(ed.getDay()===0)out.sunMinutes+=mins;
    if(hm[entry.date])out.holidayMinutes+=mins;
    wi=Math.min(4,Math.floor((ed.getDate()-1)/7));
    if(ecode==="D")out.weeks[wi].d+=mins/60;
    else if(ecode==="N")out.weeks[wi].n+=mins/60;
    else out.weeks[wi].o+=mins/60;
  });

  if(state.demo&&entries.length===0){
    out.workedMinutes=out.planned*60;
    out.dayMinutes=out.counts.D*12*60;
    out.nightMinutes=out.counts.N*12*60;
    for(day=1;day<=days;day++){
      date=new Date(y,m,day);key=iso(date);code=state.schedule[key];
      if(code!=="D"&&code!=="N")continue;
      var mins=12*60;
      if(date.getDay()===6)out.satMinutes+=mins;
      if(date.getDay()===0)out.sunMinutes+=mins;
      if(hm[key])out.holidayMinutes+=mins;
      wi=Math.min(4,Math.floor((day-1)/7));
      if(code==="D")out.weeks[wi].d+=12;
      else out.weeks[wi].n+=12;
    }
  }

  out.worked=out.workedMinutes/60;
  out.night=out.nightMinutes/60;
  out.balanceMinutes=out.workedMinutes-out.planned*60;
  out.balance=out.balanceMinutes/60;
  return out;
}
function statsForCursor(){return monthData(state.cursor.getFullYear(),state.cursor.getMonth())}
function renderCalendar(targetId){
  var el=document.getElementById(targetId);if(!el)return;
  el.innerHTML="";
  var y=state.cursor.getFullYear(),m=state.cursor.getMonth(),first=new Date(y,m,1);
  var start=(first.getDay()+6)%7,days=new Date(y,m+1,0).getDate(),prevDays=new Date(y,m,0).getDate();
  var total=Math.ceil((start+days)/7)*7,hm=holidays(y),today=iso(appNow());
  for(var idx=0;idx<total;idx++){
    var num=idx-start+1,date,inside=true;
    if(num<1){date=new Date(y,m-1,prevDays+num);inside=false}
    else if(num>days){date=new Date(y,m+1,num-days);inside=false}
    else date=new Date(y,m,num);
    var key=iso(date),code=state.schedule[key]||"",btn=document.createElement("button");
    btn.className="day-cell"+(inside?"":" outside")+(code?" "+code.toLowerCase():"")+(date.getDay()===0||date.getDay()===6?" weekend":"")+(hm[key]?" holiday":"")+(key===today?" is-today":"")+(key===iso(state.selected)?" is-selected":"");
    btn.dataset.date=key;btn.setAttribute("role","gridcell");
    btn.setAttribute("aria-label",date.toLocaleDateString("hr-HR",{weekday:"long",day:"numeric",month:"long",year:"numeric"})+(code?", "+shiftMeta(code).name:"")+(hm[key]?", "+hm[key]:""));
    btn.innerHTML='<span class="num">'+date.getDate()+'</span>'+(code?'<span class="code">'+code+'</span>':(hm[key]?'<span class="code">✣</span>':""));
    btn.addEventListener("click",function(){state.selected=new Date(this.dataset.date+"T12:00:00");renderAll()});
    el.appendChild(btn);
  }
}
function setMonthLabels(){var title=months[state.cursor.getMonth()]+" "+state.cursor.getFullYear()+".";["monthTitle","calMonthTitle"].forEach(function(id){var n=document.getElementById(id);if(n)n.textContent=title});var ml=document.getElementById("monthLabel");if(ml)ml.textContent=title;var sp=document.querySelector("#statsPeriod span");if(sp)sp.textContent=title}
function renderSummary(){
  var d=statsForCursor(),items=[
    ["sun","Odrađeno",hoursText(d.workedMinutes),""],
    ["moon","Noćni sati",hoursText(d.nightMinutes),""],
    ["scale","Saldo",signedHoursText(d.balanceMinutes),d.balanceMinutes>=0?"positive":"negative"],
    ["calendar","Subote",String(d.sat),""],
    ["calendar","Nedjelje",String(d.sun),""],
    ["holiday","Blagdani",String(d.holidays),""]
  ],el=document.getElementById("summaryGrid");
  if(el)el.innerHTML=items.map(function(x){return '<div class="summary-item '+x[3]+'"><span class="ico">'+icon(x[0])+'</span><span><small>'+x[1]+'</small><b>'+x[2]+'</b></span></div>'}).join("");
  var strip=document.getElementById("monthStrip");
  if(strip)strip.innerHTML=[
    ["Planirano",d.planned+"h"],
    ["Odrađeno",hoursText(d.workedMinutes)],
    ["Saldo",signedHoursText(d.balanceMinutes)],
    ["Noćni sati",hoursText(d.nightMinutes)]
  ].map(function(x){return '<div class="month-strip-item"><small>'+x[0]+'</small><b>'+x[1]+'</b></div>'}).join("");
}
function renderMobileHome(){
  var date=state.demo?new Date(2026,9,16):new Date(),key=iso(date),code=state.schedule[key]||null,current=shiftMeta(code),data=monthData(date.getFullYear(),date.getMonth());
  var title=document.getElementById("mobileTodayTitle");
  if(title){var dateText=date.toLocaleDateString("hr-HR",{weekday:"long",day:"2-digit",month:"2-digit",year:"numeric"});title.textContent=dateText.charAt(0).toUpperCase()+dateText.slice(1)+"."}
  var currentEl=document.getElementById("mobileCurrentShift");
  if(currentEl){currentEl.innerHTML='<div class="mobile-shift-card-head"><h2>Današnja smjena</h2>'+icon("chevron-right")+'</div><div class="mobile-shift-card-body">'+(code?'<i class="shift '+code.toLowerCase()+'">'+code+'</i>':'<i class="shift">—</i>')+'<span class="mobile-shift-copy"><b>'+current.name+'</b><small>'+current.time+'</small></span>'+(code==="D"||code==="N"?'<span class="shift-countdown">Danas</span>':'')+'</div><div class="mobile-shift-info"><div>'+icon("clock")+'<span>Radno vrijeme</span><b>'+(current.hours?current.hours+"h":"—")+'</b></div><button type="button" class="mobile-shift-info-action" data-route-dynamic="hours">'+icon("check")+'<span>Evidentiraj ulaz/izlaz</span>'+icon("chevron-right")+'</button><div>'+icon("note")+'<span>Bilješka</span>'+icon("chevron-right")+'</div></div>'}
  var next=null;
  for(var i=1;i<=62&&!next;i++){var nd=addDays(date,i),nc=state.schedule[iso(nd)];if(nc==="D"||nc==="N")next={date:nd,code:nc}}
  var nextEl=document.getElementById("mobileNextShift");
  if(nextEl){if(next){var nm=shiftMeta(next.code);nextEl.innerHTML='<div class="mobile-shift-card-head"><h2>Sljedeća smjena</h2>'+icon("chevron-right")+'</div><div class="mobile-shift-card-body"><i class="shift '+next.code.toLowerCase()+'">'+next.code+'</i><span class="mobile-shift-copy"><b>'+nm.name+'</b><small>'+nm.time+'</small></span><span class="shift-countdown">'+next.date.toLocaleDateString("hr-HR",{weekday:"short"})+'</span></div>'}else{nextEl.innerHTML='<div class="mobile-shift-card-head"><h2>Sljedeća smjena</h2>'+icon("chevron-right")+'</div><p class="empty">Nema nadolazeće smjene.</p>'}}
  var metrics=document.getElementById("mobileMetricGrid");
  if(metrics){var weekendMinutes=data.satMinutes+data.sunMinutes+data.holidayMinutes,rows=[["calendar","Ovaj mjesec",hoursText(data.workedMinutes),"Odrađeno sati",""],["chart","Saldo",signedHoursText(data.balanceMinutes),"Ukupni saldo",""],["moon","Noćni sati",hoursText(data.nightMinutes),"Ovaj mjesec","night"],["holiday","Vikendi i blagdani",hoursText(weekendMinutes),"Ovaj mjesec","weekend"]];metrics.innerHTML=rows.map(function(x){return '<div class="mobile-metric-card '+x[4]+'"><span class="metric-icon">'+icon(x[0])+'</span><span><small>'+x[1]+'</small><b>'+x[2]+'</b><small>'+x[3]+'</small></span></div>'}).join("")}
}
function nextShifts(){var list=[],start=state.demo?new Date(2026,9,16):new Date();for(var i=0;i<90&&list.length<3;i++){var d=addDays(start,i),c=state.schedule[iso(d)];if(c&&c!=="GO"&&c!=="BO")list.push([d,c])}var el=document.getElementById("nextShiftList");if(el)el.innerHTML=list.length?list.map(function(x){var m=shiftMeta(x[1]);return '<div class="next-shift"><span class="date-block">'+weekdays[x[0].getDay()].toUpperCase()+'<b>'+x[0].getDate()+'</b></span><i class="shift '+x[1].toLowerCase()+'">'+x[1]+'</i><span class="shift-copy"><b>'+m.name+'</b><small>'+m.time+'</small></span><span>›</span></div>'}).join(""):'<p class="empty">Nema nadolazećih smjena.</p>'}
function renderSelected(){var el=document.getElementById("selectedDayCard");if(!el)return;var d=state.selected,key=iso(d),hm=holidays(d.getFullYear()),code=state.schedule[key],m=shiftMeta(code);el.innerHTML='<div class="selected-day-top"><div><h2>'+((key===iso(new Date()))?"Danas":d.toLocaleDateString("hr-HR",{weekday:"long"}))+'</h2><p>'+d.toLocaleDateString("hr-HR",{weekday:"long",day:"2-digit",month:"2-digit",year:"numeric"})+'.</p></div>'+(hm[key]?'<div class="holiday-inline">▦ Blagdan<br><small>'+hm[key]+'</small></div>':'')+'</div><div class="selected-shift">'+(code?'<i class="shift '+code.toLowerCase()+'">'+code+'</i>':'<i class="shift">—</i>')+'<span><b>'+m.name+'</b><small>'+m.time+'</small></span><span>›</span></div>'}
function renderRecognition(){var el=document.getElementById("recognitionDays");if(!el)return;var y=state.cursor.getFullYear(),m=state.cursor.getMonth();el.innerHTML="";for(var i=1;i<=Math.min(10,new Date(y,m+1,0).getDate());i++){var c=state.schedule[iso(new Date(y,m,i))]||"",x=document.createElement("div");x.className="recognition-day";x.innerHTML="<b>"+String(i).padStart(2,"0")+"."+String(m+1).padStart(2,"0")+".</b><small>"+weekdays[new Date(y,m,i).getDay()].toLowerCase()+"</small>"+(c?'<i class="shift '+c.toLowerCase()+'">'+c+'</i>':'<i class="shift">slobodno</i>');el.appendChild(x)}}
function renderStats(){
  var d=statsForCursor(),worked=document.getElementById("workedTotal");
  if(worked)worked.textContent=largeHoursText(d.workedMinutes);
  var donutHours=document.getElementById("donutHours");if(donutHours)donutHours.textContent=hoursText(d.workedMinutes);
  var py=state.cursor.getFullYear(),pm=state.cursor.getMonth()-1;if(pm<0){pm=11;py--}
  var prev=monthData(py,pm),trendEl=document.getElementById("workedTrend");
  if(trendEl){
    trendEl.classList.remove("negative");
    if(prev.workedMinutes>0){
      var pct=Math.round((d.workedMinutes-prev.workedMinutes)*100/prev.workedMinutes);
      trendEl.textContent=(pct>0?"↗ +":pct<0?"↘ ":"")+pct+"% u odnosu na prethodni mjesec";
      if(pct<0)trendEl.classList.add("negative");
    }else trendEl.textContent="Nema podataka za prethodni mjesec";
  }
  var donut=document.getElementById("donut"),total=Math.max(1,d.workedMinutes),dayPct=Math.round(d.dayMinutes/total*100),nightPct=Math.round(d.nightMinutes/total*100);
  if(donut)donut.style.background="conic-gradient(#20B7EB 0 "+dayPct+"%,#27388D "+dayPct+"% "+(dayPct+nightPct)+"%,#B8D0ED "+(dayPct+nightPct)+"% 100%)";
  var cats=[
    ["#20B7EB","Dnevne smjene",hoursText(d.dayMinutes),d.counts.D+" smjena"],
    ["#27388D","Noćne smjene",hoursText(d.nightMinutes),d.counts.N+" smjena"],
    ["#B8D0ED","Subote",hoursText(d.satMinutes),d.sat+" smjene"],
    ["#FF6B61","Nedjelje",hoursText(d.sunMinutes),d.sun+" smjene"],
    ["#F59E0B","Blagdani",hoursText(d.holidayMinutes),d.holidays+" smjena"],
    ["#14B8A6","GO",d.go*8+"h",d.go+" dana"],
    ["#FB7185","BO",d.bo*8+"h",d.bo+" dana"],
    ["#BFEFFF","Saldo sati",signedHoursText(d.balanceMinutes),"Prema evidenciji"]
  ],el=document.getElementById("statsCategories");
  if(el)el.innerHTML=cats.map(function(x){return '<div class="stat-cat"><span><i style="background:'+x[0]+'"></i><b>'+x[2]+'</b></span><small>'+x[1]+' · '+x[3]+'</small></div>'}).join("");
  var max=1;d.weeks.forEach(function(w){max=Math.max(max,w.d+w.n+w.o)});
  var wb=document.getElementById("weeklyBars");
  if(wb)wb.innerHTML=d.weeks.slice(0,4).map(function(w,i){
    var total=w.d+w.n+w.o,h=Math.max(8,Math.round(total/max*130));
    return '<div class="week-bar-wrap"><b>'+hoursText(total*60)+'</b><div class="week-bar" style="height:'+h+'px"><span class="d" style="height:'+Math.round((w.d/Math.max(1,total))*100)+'%"></span><span class="n" style="height:'+Math.round((w.n/Math.max(1,total))*100)+'%"></span><span class="other" style="height:'+Math.round((w.o/Math.max(1,total))*100)+'%"></span></div><small>'+(i+1)+'. tjedan</small></div>';
  }).join("");
  var detail=document.getElementById("detailStats");
  if(detail)detail.innerHTML=[
    ["sun","Dnevne smjene",d.counts.D+" smjena",hoursText(d.dayMinutes)],
    ["holiday","Blagdani",d.holidays+" smjena",hoursText(d.holidayMinutes)],
    ["moon","Noćne smjene",d.counts.N+" smjena",hoursText(d.nightMinutes)],
    ["holiday","GO",d.go+" dana",d.go*8+"h"],
    ["calendar","Subote",d.sat+" smjene",hoursText(d.satMinutes)],
    ["check","BO",d.bo+" dana",d.bo*8+"h"],
    ["calendar","Nedjelje",d.sun+" smjene",hoursText(d.sunMinutes)],
    ["scale","Saldo sati","Prema evidenciji",signedHoursText(d.balanceMinutes)]
  ].map(function(x){return '<div class="detail-item"><i>'+icon(x[0])+'</i><span><b>'+x[1]+'</b><small>'+x[2]+'</small></span><b>'+x[3]+'</b></div>'}).join("");
}
function renderHours(){
  var root=document.getElementById("view-hours");if(!root)return;
  var now=appNow(),today=iso(now),entries=loadTimeEntries(),active=currentTimeEntry(entries,today),latest=latestTimeEntry(entries,today);
  var planned=shiftMeta(state.schedule[today]||"");
  var dateEl=document.getElementById("hoursDate");if(dateEl)dateEl.textContent=now.toLocaleDateString("hr-HR",{weekday:"long",day:"2-digit",month:"long",year:"numeric"});
  var monthLabel=document.getElementById("hoursMonthLabel");if(monthLabel)monthLabel.textContent=months[now.getMonth()]+" "+now.getFullYear()+".";
  var plannedEl=document.getElementById("hoursPlannedShift");if(plannedEl)plannedEl.textContent=(state.schedule[today]?planned.name+" · "+planned.time:"Nema planirane smjene");
  var status=document.getElementById("hoursStatus"),pill=document.getElementById("hoursStatusPill"),inEl=document.getElementById("hoursInValue"),outEl=document.getElementById("hoursOutValue"),durationEl=document.getElementById("hoursDurationValue"),note=document.getElementById("hoursNote");
  if(status)status.textContent=active?"Rad je u tijeku":latest&&latest.out?"Današnja evidencija je spremljena":"Nema evidentiranog ulaza";
  if(pill){pill.textContent=active?"U tijeku":latest&&latest.out?"Završeno":"Spremno";pill.classList.toggle("is-active",!!active);pill.classList.toggle("is-done",!!(latest&&latest.out))}
  if(inEl)inEl.textContent=latest?latest.in:"—";
  if(outEl)outEl.textContent=latest&&latest.out?latest.out:"—";
  if(durationEl)durationEl.textContent=latest?durationLabel(durationMinutes(latest,now)):"0h 00min";
  if(note&&document.activeElement!==note)note.value=latest&&latest.note?latest.note:"";
  var inBtn=document.getElementById("clockInBtn"),outBtn=document.getElementById("clockOutBtn");
  if(inBtn)inBtn.disabled=!!active;
  if(outBtn)outBtn.disabled=!active;
  var monthPrefix=now.getFullYear()+"-"+String(now.getMonth()+1).padStart(2,"0")+"-",monthEntries=entries.filter(function(x){return x.date.indexOf(monthPrefix)===0});
  var total=monthEntries.reduce(function(sum,x){return sum+(x.out?durationMinutes(x,now):0)},0),totalEl=document.getElementById("hoursMonthTotal");if(totalEl)totalEl.textContent=durationLabel(total);
  var history=document.getElementById("hoursHistory");if(history){var sorted=monthEntries.slice().reverse();history.innerHTML=sorted.length?sorted.map(function(x){var d=new Date(x.date+"T12:00:00"),mins=durationMinutes(x,now);return '<article class="hours-history-row"><span class="hours-history-date"><b>'+String(d.getDate()).padStart(2,"0")+'.'+String(d.getMonth()+1).padStart(2,"0")+'.</b><small>'+d.toLocaleDateString("hr-HR",{weekday:"short"})+'</small></span><span><b>'+x.in+' – '+(x.out||"u tijeku")+'</b><small>'+(x.note?escapeHtml(x.note):"Bez bilješke")+'</small></span><strong>'+durationLabel(mins)+'</strong></article>'}).join(""):'<div class="hours-empty">'+icon("clock")+'<p>Još nema evidentiranih sati za ovaj mjesec.</p></div>'}
}
function escapeHtml(value){return String(value).replace(/[&<>"']/g,function(ch){return {"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[ch]})}
function clockIn(){
  var entries=loadTimeEntries(),now=appNow(),today=iso(now);
  if(currentTimeEntry(entries,today)){toast("Ulaz je već evidentiran.");return}
  entries.push({id:String(Date.now()),date:today,in:hhmm(now),out:null,note:""});
  saveTimeEntries(entries);renderAll();toast("Ulaz je evidentiran.")
}
function clockOut(){
  var entries=loadTimeEntries(),now=appNow(),today=iso(now),active=currentTimeEntry(entries,today);
  if(!active){toast("Nema aktivne evidencije za izlaz.");return}
  active.out=hhmm(now);
  var note=document.getElementById("hoursNote");active.note=note?note.value.trim().slice(0,500):active.note||"";
  saveTimeEntries(entries);renderAll();toast("Izlaz je evidentiran.")
}
function saveHoursNote(){
  var entries=loadTimeEntries(),today=iso(appNow()),entry=currentTimeEntry(entries,today)||latestTimeEntry(entries,today),note=document.getElementById("hoursNote");
  if(!entry||!note)return;
  entry.note=note.value.trim().slice(0,500);saveTimeEntries(entries)
}
function handleScanFile(file){
  if(!file||!/^image\//.test(file.type)){toast("Odaberi valjanu slikovnu datoteku.");return}
  if(file.size>10*1024*1024){toast("Slika je prevelika. Najveća dopuštena veličina je 10 MB.");return}
  var preview=document.getElementById("scanPreview"),img=document.getElementById("scanPreviewImage"),status=document.getElementById("scanStatus");
  if(img.dataset.objectUrl)URL.revokeObjectURL(img.dataset.objectUrl);
  var url=URL.createObjectURL(file);img.dataset.objectUrl=url;img.src=url;preview.classList.add("has-image");
  status.classList.remove("is-success");status.classList.add("is-scanning");status.querySelector("span").textContent="Automatsko prepoznavanje...";
  setTimeout(function(){status.classList.remove("is-scanning");status.classList.add("is-success");status.querySelector("span").textContent="Fotografija je učitana i spremna za OCR obradu.";},900);
}
function route(name){
  state.route=name;document.body.dataset.routeCurrent=name;
  document.querySelectorAll(".view").forEach(function(x){x.classList.toggle("is-active",x.dataset.view===name)});
  document.querySelectorAll("[data-route]").forEach(function(x){if(x.closest(".side-nav")||x.closest(".bottom-nav"))x.classList.toggle("is-active",x.dataset.route===name)});
  if(name==="hours")renderHours();
  if(name==="colleagues")renderColleagues();
  window.scrollTo({top:0,behavior:document.body.dataset.reducedMotion==="true"?"auto":"smooth"});
}
function moveMonth(delta){state.cursor=new Date(state.cursor.getFullYear(),state.cursor.getMonth()+delta,1);state.selected=new Date(state.cursor);renderAll()}
function toast(msg){var t=document.getElementById("toast");t.textContent=msg;t.classList.add("show");setTimeout(function(){t.classList.remove("show")},2200)}
function renderAll(){setMonthLabels();renderCalendar("calendarGrid");renderCalendar("calendarGridMobile");renderSummary();renderMobileHome();nextShifts();renderSelected();renderRecognition();renderStats();renderHours();renderColleagues();renderPeriodMenu()}
function bind(){
  document.querySelectorAll("[data-route]").forEach(function(x){x.addEventListener("click",function(e){e.preventDefault();route(this.dataset.route)})});
  document.addEventListener("click",function(e){
    var target=e.target.closest("[data-route-dynamic]");if(target){e.preventDefault();route(target.dataset.routeDynamic);return}
    var remove=e.target.closest("[data-remove-colleague]");
    if(remove){
      var items=loadColleagues(),index=Number(remove.dataset.removeColleague);
      if(Number.isInteger(index)&&index>=0&&index<items.length){items.splice(index,1);saveColleagues(items);renderColleagues();toast("Kolega je uklonjen.");}
    }
  });
  ["prevMonth","calPrev"].forEach(function(id){var x=document.getElementById(id);if(x)x.addEventListener("click",function(){moveMonth(-1)})});
  ["nextMonth","calNext"].forEach(function(id){var x=document.getElementById(id);if(x)x.addEventListener("click",function(){moveMonth(1)})});
  document.getElementById("todayBtn").addEventListener("click",function(){var n=appNow();state.cursor=new Date(n.getFullYear(),n.getMonth(),1);state.selected=n;renderAll()});
  document.getElementById("themeToggle").addEventListener("change",function(){document.documentElement.dataset.theme=this.checked?"dark":"light";localStorage.setItem("raspored.theme",document.documentElement.dataset.theme)});
  var motion=document.getElementById("motionToggle");
  if(motion){
    motion.checked=localStorage.getItem("raspored.reducedMotion")==="1";
    document.body.dataset.reducedMotion=motion.checked?"true":"false";
    motion.addEventListener("change",function(){localStorage.setItem("raspored.reducedMotion",this.checked?"1":"0");document.body.dataset.reducedMotion=this.checked?"true":"false"});
  }
  document.getElementById("saveSchedule").addEventListener("click",function(){save();toast("Raspored je spremljen.")});
  document.getElementById("clockInBtn").addEventListener("click",clockIn);
  document.getElementById("clockOutBtn").addEventListener("click",clockOut);
  document.getElementById("hoursNote").addEventListener("change",saveHoursNote);
  document.getElementById("rescanBtn").addEventListener("click",function(){document.getElementById("cameraInput").click()});
  document.getElementById("rescanSecondary").addEventListener("click",function(){document.getElementById("cameraInput").click()});
  document.getElementById("galleryBtn").addEventListener("click",function(){document.getElementById("galleryInput").click()});
  ["cameraInput","galleryInput"].forEach(function(id){var input=document.getElementById(id);input.addEventListener("change",function(){if(this.files&&this.files[0])handleScanFile(this.files[0])})});
  var period=document.getElementById("statsPeriod"),menu=document.getElementById("statsPeriodMenu");
  if(period&&menu){
    period.addEventListener("click",function(e){e.stopPropagation();var opening=menu.hidden;menu.hidden=!opening;period.setAttribute("aria-expanded",opening?"true":"false")});
    menu.addEventListener("click",function(e){
      var b=e.target.closest("[data-period]");if(!b)return;
      var p=b.dataset.period.split("-").map(Number);
      state.cursor=new Date(p[0],p[1]-1,1);state.selected=new Date(state.cursor);
      menu.hidden=true;period.setAttribute("aria-expanded","false");renderAll();
    });
    document.addEventListener("click",function(e){if(!menu.hidden&&!e.target.closest(".period-picker")){menu.hidden=true;period.setAttribute("aria-expanded","false")}});
  }
  var search=document.getElementById("searchBtn");if(search)search.addEventListener("click",openSearch);
  var addColleagueBtn=document.getElementById("addColleagueBtn");if(addColleagueBtn)addColleagueBtn.addEventListener("click",addColleague);
  function connectivity(){var b=document.getElementById("connectivityBanner");b.classList.toggle("show",!navigator.onLine)}
  window.addEventListener("online",connectivity);window.addEventListener("offline",connectivity);connectivity();
  var th=localStorage.getItem("raspored.theme");if(th){document.documentElement.dataset.theme=th;document.getElementById("themeToggle").checked=th==="dark"}
}
loadSchedule();document.body.dataset.demo=state.demo?"true":"false";configureProfile();bind();document.body.dataset.routeCurrent=state.route;renderAll();setInterval(function(){if(state.route==="hours")renderHours()},60000);if("serviceWorker" in navigator){window.addEventListener("load",function(){navigator.serviceWorker.register((window.RASPORED_BASE||"")+"/sw.js").catch(function(){})})}
})();