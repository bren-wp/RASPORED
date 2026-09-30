(function(){
"use strict";

function snapshot(){
  return window.RasporedDataStore&&window.RasporedDataStore.snapshot
    ?window.RasporedDataStore.snapshot()
    :null;
}
function pad(value){return String(value).padStart(2,"0")}
function isoMonth(date){return date.getFullYear()+"-"+pad(date.getMonth()+1)}
function safeText(value){return String(value==null?"":value)}
function validMonth(value){return /^\d{4}-(0[1-9]|1[0-2])$/.test(String(value||""))}
function selectedMonth(){
  var input=document.getElementById("reportMonth");
  return input&&validMonth(input.value)?input.value:isoMonth(new Date());
}
function monthParts(month){
  var parts=month.split("-").map(Number);
  return {year:parts[0],month:parts[1]};
}
function monthBounds(month){
  var parts=monthParts(month);
  return {
    start:new Date(parts.year,parts.month-1,1).getTime(),
    end:new Date(parts.year,parts.month,1).getTime()
  };
}
function entryInterval(entry){
  if(!entry)return null;
  var start=Number(entry.startedAt);
  if(!Number.isFinite(start)&&entry.date&&entry.in){
    start=new Date(String(entry.date)+"T"+String(entry.in)+":00").getTime();
  }
  if(!Number.isFinite(start))return null;
  var end=Number(entry.endedAt);
  if(!Number.isFinite(end)&&entry.out&&entry.date){
    end=new Date(String(entry.date)+"T"+String(entry.out)+":00").getTime();
    if(end<=start)end+=24*60*60*1000;
  }
  if(!Number.isFinite(end))end=Date.now();
  end=Math.min(end,start+36*60*60*1000);
  return end>start?{start:start,end:end}:null;
}
function clippedInterval(entry,start,end){
  var span=entryInterval(entry);
  if(!span)return null;
  var clippedStart=Math.max(span.start,start);
  var clippedEnd=Math.min(span.end,end);
  return clippedEnd>clippedStart?{start:clippedStart,end:clippedEnd}:null;
}
function durationMinutesInMonth(entry,month){
  var bounds=monthBounds(month),span=clippedInterval(entry,bounds.start,bounds.end);
  return span?Math.max(0,Math.round((span.end-span.start)/60000)):0;
}
function downloadJson(){
  var data=snapshot();
  if(!data)return;
  var clean=JSON.parse(JSON.stringify(data));
  if(clean.scanSession)clean.scanSession={people:[],selected:-1,month:null};
  var blob=new Blob([JSON.stringify(clean,null,2)+"\n"],{type:"application/json;charset=utf-8"});
  var url=URL.createObjectURL(blob),a=document.createElement("a");
  a.href=url;
  a.download="RASPORED-backup-"+new Date().toISOString().slice(0,10)+".json";
  document.body.appendChild(a);a.click();a.remove();
  setTimeout(function(){URL.revokeObjectURL(url)},0);
}
function labelCode(code){
  return {D:"D · dnevna smjena",N:"N · noćna smjena",GO:"GO · godišnji odmor",BO:"BO · bolovanje",PD:"PD · plaćeni dopust",SD:"SD · slobodan dan"}[code]||"—";
}
function workType(type){
  return {regular:"Redovni rad",shift1:"1. smjena",shift2:"2. smjena",shift3:"3. smjena",turnus:"Turnus",duty:"Dežurstvo",standby:"Pripravnost",callout:"Rad po pozivu",other:"Drugo"}[type]||"Redovni rad";
}
function hhmm(timestamp){
  var d=new Date(timestamp);
  return pad(d.getHours())+":"+pad(d.getMinutes());
}
function removeReport(){
  var old=document.getElementById("printReport");if(old)old.remove();
  document.body.classList.remove("is-printing-report");
}
function printReport(){
  var data=snapshot();if(!data)return;
  removeReport();

  var month=selectedMonth(),parts=monthParts(month),bounds=monthBounds(month);
  var monthDate=new Date(parts.year,parts.month-1,1),section=document.createElement("section");
  section.id="printReport";section.className="print-report";

  var title=document.createElement("h1");title.textContent="RASPORED";
  var sub=document.createElement("p");
  sub.textContent="Mjesečni izvještaj · "+monthDate.toLocaleDateString("hr-HR",{month:"long",year:"numeric"});
  section.append(title,sub);

  if(data.profile&&data.profile.name){
    var profile=document.createElement("p");
    profile.textContent="Profil: "+safeText(data.profile.name);
    section.appendChild(profile);
  }

  var evidence=(data.evidence||[]).filter(function(entry){
    return clippedInterval(entry,bounds.start,bounds.end)!==null;
  });
  var worked=evidence.reduce(function(sum,entry){
    return sum+durationMinutesInMonth(entry,month);
  },0);
  var summary=document.createElement("p");
  summary.textContent="Odrađeno u odabranom mjesecu: "+Math.floor(worked/60)+"h "+pad(worked%60)+"min";
  section.appendChild(summary);

  var table=document.createElement("table"),thead=document.createElement("thead"),tr=document.createElement("tr");
  ["Datum","Raspored","Evidencija rada"].forEach(function(label){
    var th=document.createElement("th");th.textContent=label;tr.appendChild(th);
  });
  thead.appendChild(tr);table.appendChild(thead);

  var tbody=document.createElement("tbody");
  var days=new Date(parts.year,parts.month,0).getDate();
  for(var day=1;day<=days;day++){
    var date=month+"-"+pad(day),row=document.createElement("tr");
    var dayStart=new Date(parts.year,parts.month-1,day).getTime();
    var dayEnd=new Date(parts.year,parts.month-1,day+1).getTime();

    var dateCell=document.createElement("td");
    dateCell.textContent=pad(day)+"."+pad(parts.month)+"."+parts.year+".";
    var scheduleCell=document.createElement("td");
    scheduleCell.textContent=labelCode(data.schedule&&data.schedule[date]);
    var evidenceCell=document.createElement("td");

    var rows=evidence.map(function(entry){
      var span=clippedInterval(entry,dayStart,dayEnd);
      if(!span)return null;
      var suffix=(!entry.endedAt&&!entry.out)?" · u tijeku":"";
      return hhmm(span.start)+"–"+hhmm(span.end)+" · "+workType(entry.workType)+suffix;
    }).filter(Boolean);
    evidenceCell.textContent=rows.length?rows.join(" | "):"—";
    row.append(dateCell,scheduleCell,evidenceCell);tbody.appendChild(row);
  }
  table.appendChild(tbody);section.appendChild(table);

  var note=document.createElement("p");
  note.className="print-report-note";
  note.textContent="Izvještaj je informativan i temelji se na podacima spremljenima u aplikaciji RASPORED. Noćna evidencija koja prelazi granicu dana ili mjeseca razdvaja se prema stvarnom vremenu.";
  section.appendChild(note);

  document.body.appendChild(section);document.body.classList.add("is-printing-report");
  window.addEventListener("afterprint",removeReport,{once:true});
  setTimeout(function(){window.print()},50);
}
function bind(){
  var json=document.getElementById("exportJsonBtn"),pdf=document.getElementById("printPdfBtn"),month=document.getElementById("reportMonth");
  if(month&&!month.value)month.value=isoMonth(new Date());
  if(json)json.addEventListener("click",downloadJson);
  if(pdf)pdf.addEventListener("click",printReport);
}
document.addEventListener("DOMContentLoaded",bind,{once:true});
})();