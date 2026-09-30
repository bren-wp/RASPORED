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
function durationMinutes(entry){
  if(!entry||!entry.startedAt||!entry.endedAt)return 0;
  return Math.max(0,Math.round((Number(entry.endedAt)-Number(entry.startedAt))/60000));
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
function removeReport(){
  var old=document.getElementById("printReport");if(old)old.remove();
  document.body.classList.remove("is-printing-report");
}
function printReport(){
  var data=snapshot();if(!data)return;
  removeReport();
  var now=new Date(),month=isoMonth(now),section=document.createElement("section");
  section.id="printReport";section.className="print-report";
  var title=document.createElement("h1");title.textContent="RASPORED";
  var sub=document.createElement("p");sub.textContent="Mjesečni izvještaj · "+now.toLocaleDateString("hr-HR",{month:"long",year:"numeric"});
  section.append(title,sub);
  if(data.profile&&data.profile.name){
    var profile=document.createElement("p");profile.textContent="Profil: "+safeText(data.profile.name);section.appendChild(profile);
  }
  var evidence=(data.evidence||[]).filter(function(entry){return String(entry.date||"").slice(0,7)===month});
  var worked=evidence.reduce(function(sum,entry){return sum+durationMinutes(entry)},0);
  var summary=document.createElement("p");summary.textContent="Odrađeno: "+Math.floor(worked/60)+"h "+pad(worked%60)+"min";section.appendChild(summary);
  var table=document.createElement("table"),thead=document.createElement("thead"),tr=document.createElement("tr");
  ["Datum","Raspored","Evidencija rada"].forEach(function(label){var th=document.createElement("th");th.textContent=label;tr.appendChild(th)});
  thead.appendChild(tr);table.appendChild(thead);
  var tbody=document.createElement("tbody");
  var days=new Date(now.getFullYear(),now.getMonth()+1,0).getDate();
  for(var day=1;day<=days;day++){
    var date=month+"-"+pad(day),row=document.createElement("tr");
    var dateCell=document.createElement("td");dateCell.textContent=pad(day)+"."+pad(now.getMonth()+1)+"."+now.getFullYear()+". ";
    var scheduleCell=document.createElement("td");scheduleCell.textContent=labelCode(data.schedule&&data.schedule[date]);
    var evidenceCell=document.createElement("td");
    var rows=evidence.filter(function(entry){return entry.date===date});
    evidenceCell.textContent=rows.length?rows.map(function(entry){return safeText(entry.in)+"–"+safeText(entry.out||"u tijeku")+" · "+workType(entry.workType)}).join(" | "):"—";
    row.append(dateCell,scheduleCell,evidenceCell);tbody.appendChild(row);
  }
  table.appendChild(tbody);section.appendChild(table);
  var note=document.createElement("p");note.className="print-report-note";note.textContent="Izvještaj je informativan i temelji se na podacima spremljenima u aplikaciji RASPORED.";section.appendChild(note);
  document.body.appendChild(section);document.body.classList.add("is-printing-report");
  window.addEventListener("afterprint",removeReport,{once:true});
  setTimeout(function(){window.print()},50);
}
function bind(){
  var json=document.getElementById("exportJsonBtn"),pdf=document.getElementById("printPdfBtn");
  if(json)json.addEventListener("click",downloadJson);
  if(pdf)pdf.addEventListener("click",printReport);
}
document.addEventListener("DOMContentLoaded",bind,{once:true});
})();