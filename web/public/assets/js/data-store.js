(function(){
"use strict";

var current={
  schema:4,
  revision:0,
  schedule:{},
  evidence:[],
  profile:{name:""},
  colleagues:[],
  teamMembers:[],
  settings:{theme:"dark",reducedMotion:false,notificationReadKey:""},
  scanSession:{people:[],reviewCells:[],selected:-1,month:null,expectedRows:0,incomplete:false},
  payroll:{county:"Primorsko-goranska",residence:"Rijeka",taxLower:20,taxHigher:25,sector:"Zdravstvo",institution:"Klinički bolnički centar Rijeka",regimeId:"kbc-rijeka-2026",roleId:"health-transport-sss",coefficient:1.25,yearsService:0,personalAllowance:600,extraPercent:0,secondShift:false,turnus:false,customBase:null},
  updatedAt:null
};
var writeChain=Promise.resolve();
var initialized=false;
var serverLoaded=false;
var localGeneration=0;

function clone(value){return JSON.parse(JSON.stringify(value))}
function base(){return document.body&&document.body.dataset?document.body.dataset.base||"":""}
function endpoint(){return base()+"/api/state.php"}

function normalizeScheduleCode(raw){
  if(typeof raw!=="string")return "";
  var value=raw.trim().toLocaleUpperCase("hr-HR");
  if(value==="G0")value="GO";
  else if(value==="B0")value="BO";
  return /^[\p{L}\p{N}]{1,8}$/u.test(value)?value:"";
}
function sanitizeSchedule(raw){
  var out={};
  if(!raw||typeof raw!=="object"||Array.isArray(raw))return out;
  Object.keys(raw).forEach(function(key){
    var code=normalizeScheduleCode(raw[key]);
    if(/^\d{4}-\d{2}-\d{2}$/.test(key)&&code)out[key]=code;
  });
  return out;
}
function sanitizeEvidence(raw){
  if(!Array.isArray(raw))return [];
  return raw.slice(-3000).filter(function(x){
    return x&&/^\d{4}-\d{2}-\d{2}$/.test(x.date||"")&&/^\d{2}:\d{2}$/.test(x.in||"")&&(!x.out||/^\d{2}:\d{2}$/.test(x.out));
  }).map(function(x){
    return {
      id:String(x.id||Date.now()).slice(0,80),
      date:x.date,
      in:x.in,
      out:x.out||null,
      note:typeof x.note==="string"?x.note.slice(0,500):"",
      workType:["regular","shift1","shift2","shift3","turnus","duty","standby","callout","other"].indexOf(x.workType)>=0?x.workType:"regular",
      startedAt:x.startedAt!=null&&Number.isFinite(Number(x.startedAt))?Number(x.startedAt):null,
      endedAt:x.endedAt!=null&&Number.isFinite(Number(x.endedAt))?Number(x.endedAt):null
    };
  });
}
function sanitizePeople(raw){
  if(!Array.isArray(raw))return [];
  return raw.slice(0,100).map(function(item){
    if(!item||typeof item.name!=="string")return null;
    var name=item.name.trim().replace(/\s+/g," ").slice(0,100);
    if(name.length<2)return null;
    var shifts={};
    Object.keys(item.dayShifts&&typeof item.dayShifts==="object"?item.dayShifts:{}).forEach(function(day){
      var n=Number(day),code=normalizeScheduleCode(item.dayShifts[day]);
      if(Number.isInteger(n)&&n>=1&&n<=31&&code)shifts[n]=code;
    });
    return {row:Number.isInteger(item.row)?item.row:null,name:name,dayShifts:shifts};
  }).filter(Boolean);
}
function sanitizeScanReviewCells(raw){
  if(!Array.isArray(raw))return [];
  return raw.slice(0,3100).map(function(item){
    if(!item||typeof item.employeeName!=="string")return null;
    var employeeName=item.employeeName.trim().replace(/\s+/g," ").slice(0,100);
    var day=Number(item.day);
    if(employeeName.length<2||!Number.isInteger(day)||day<1||day>31)return null;
    var employeeRow=Number(item.employeeRow);
    employeeRow=item.employeeRow!==null&&item.employeeRow!==""&&Number.isInteger(employeeRow)&&employeeRow>=1&&employeeRow<=100?employeeRow:null;
    var source=["local","ai","local+ai","manual"].indexOf(item.source)>=0?item.source:"local";
    return {
      employeeRow:employeeRow,
      employeeName:employeeName,
      day:day,
      localCode:normalizeScheduleCode(item.localCode||"")||null,
      aiCode:normalizeScheduleCode(item.aiCode||"")||null,
      selectedCode:normalizeScheduleCode(item.selectedCode||"")||null,
      source:source,
      confidence:null,
      conflict:!!item.conflict,
      manuallyConfirmed:!!item.manuallyConfirmed
    };
  }).filter(Boolean);
}
function sanitizeTeamMembers(raw){
  if(!Array.isArray(raw))return [];
  return raw.slice(0,100).map(function(item){
    if(!item||typeof item.name!=="string")return null;
    var name=item.name.trim().replace(/\s+/g," ").slice(0,100);
    if(name.length<2)return null;
    return {
      name:name,
      note:typeof item.note==="string"?item.note.trim().replace(/\s+/g," ").slice(0,120):"",
      schedule:sanitizeSchedule(item.schedule)
    };
  }).filter(Boolean);
}
function sanitize(raw){
  raw=raw&&typeof raw==="object"?raw:{};
  var profile=raw.profile&&typeof raw.profile==="object"?raw.profile:{};
  var settings=raw.settings&&typeof raw.settings==="object"?raw.settings:{};
  var scan=raw.scanSession&&typeof raw.scanSession==="object"?raw.scanSession:{};
  var payroll=raw.payroll&&typeof raw.payroll==="object"?raw.payroll:{};
  var colleagues=Array.isArray(raw.colleagues)?raw.colleagues.slice(0,30).filter(function(x){return x&&typeof x.name==="string"&&x.name.trim().length>=2}).map(function(x){return {name:x.name.trim().replace(/\s+/g," ").slice(0,80),note:typeof x.note==="string"?x.note.trim().replace(/\s+/g," ").slice(0,120):""}}):[];
  var teamMembers=sanitizeTeamMembers(raw.teamMembers);
  var month=scan.month&&Number.isInteger(scan.month.year)&&Number.isInteger(scan.month.month)&&scan.month.month>=1&&scan.month.month<=12?{year:scan.month.year,month:scan.month.month}:null;
  var people=sanitizePeople(scan.people);
  var reviewCells=sanitizeScanReviewCells(scan.reviewCells);
  var selected=Number.isInteger(scan.selected)&&scan.selected>=-1&&scan.selected<people.length?scan.selected:-1;
  var expectedRows=Number.isInteger(Number(scan.expectedRows))?Math.max(0,Math.min(100,Number(scan.expectedRows))):0;
  var incomplete=!!scan.incomplete;
  return {
    schema:4,
    revision:Number.isInteger(raw.revision)&&raw.revision>=0?raw.revision:0,
    schedule:sanitizeSchedule(raw.schedule),
    evidence:sanitizeEvidence(raw.evidence),
    profile:{name:typeof profile.name==="string"?profile.name.trim().replace(/\s+/g," ").slice(0,80):""},
    colleagues:colleagues,
    teamMembers:teamMembers,
    settings:{theme:settings.theme==="light"?"light":"dark",reducedMotion:!!settings.reducedMotion,notificationReadKey:typeof settings.notificationReadKey==="string"?settings.notificationReadKey.slice(0,120):""},
    scanSession:{people:people,reviewCells:reviewCells,selected:selected,month:month,expectedRows:expectedRows,incomplete:incomplete},
    payroll:{
      county:typeof payroll.county==="string"&&payroll.county?payroll.county.slice(0,80):"Primorsko-goranska",
      residence:typeof payroll.residence==="string"&&payroll.residence?payroll.residence.slice(0,100):"Rijeka",
      taxLower:Number.isFinite(Number(payroll.taxLower))?Math.max(0,Math.min(50,Number(payroll.taxLower))):20,
      taxHigher:Number.isFinite(Number(payroll.taxHigher))?Math.max(0,Math.min(50,Number(payroll.taxHigher))):25,
      sector:typeof payroll.sector==="string"&&payroll.sector?payroll.sector.slice(0,100):"Zdravstvo",
      institution:typeof payroll.institution==="string"&&payroll.institution?payroll.institution.slice(0,160):"Klinički bolnički centar Rijeka",
      regimeId:typeof payroll.regimeId==="string"&&payroll.regimeId?payroll.regimeId.slice(0,80):"kbc-rijeka-2026",
      roleId:typeof payroll.roleId==="string"&&payroll.roleId?payroll.roleId.slice(0,80):"health-transport-sss",
      coefficient:Number.isFinite(Number(payroll.coefficient))?Math.max(0.1,Math.min(10,Number(payroll.coefficient))):1.25,
      yearsService:Number.isFinite(Number(payroll.yearsService))?Math.max(0,Math.min(60,Math.trunc(Number(payroll.yearsService)))):0,
      personalAllowance:Number.isFinite(Number(payroll.personalAllowance))?Math.max(0,Math.min(10000,Number(payroll.personalAllowance))):600,
      extraPercent:Number.isFinite(Number(payroll.extraPercent))?Math.max(0,Math.min(100,Number(payroll.extraPercent))):0,
      secondShift:!!payroll.secondShift,
      turnus:!!payroll.turnus,
      customBase:Number.isFinite(Number(payroll.customBase))?Math.max(0,Math.min(10000,Number(payroll.customBase))):null
    },
    updatedAt:typeof raw.updatedAt==="string"?raw.updatedAt:null
  };
}
function emptyState(data){
  return !Object.keys(data.schedule).length&&!data.evidence.length&&!data.profile.name&&!data.colleagues.length&&!data.teamMembers.length&&!data.scanSession.people.length;
}
function legacyValue(key){
  try{return localStorage.getItem(key)}catch(e){return null}
}
function legacySession(key){
  try{return sessionStorage.getItem(key)}catch(e){return null}
}
function readLegacy(){
  var legacy=clone(current),found=false;
  try{
    var schedule=JSON.parse(legacyValue("raspored.schedule")||"null");
    if(schedule&&typeof schedule==="object"){legacy.schedule=schedule;found=true}
  }catch(e){}
  try{
    var evidence=JSON.parse(legacyValue("raspored.timeEntries.v1")||"null");
    if(Array.isArray(evidence)){legacy.evidence=evidence;found=true}
  }catch(e){}
  try{
    var colleagues=JSON.parse(legacyValue("raspored.colleagues.v1")||"null");
    if(Array.isArray(colleagues)){legacy.colleagues=colleagues;found=true}
  }catch(e){}
  var name=legacyValue("raspored.profile.name");if(name!==null){legacy.profile.name=name;found=true}
  var theme=legacyValue("raspored.theme");if(theme==="dark"||theme==="light"){legacy.settings.theme=theme;found=true}
  var reduced=legacyValue("raspored.reducedMotion");if(reduced!==null){legacy.settings.reducedMotion=reduced==="1";found=true}
  var notificationKey=legacyValue("raspored.notifications.readKey");if(notificationKey!==null){legacy.settings.notificationReadKey=notificationKey;found=true}
  try{
    var scan=JSON.parse(legacySession("raspored.scan.v1")||"null");
    if(scan&&typeof scan==="object"){legacy.scanSession=scan;found=true}
  }catch(e){}
  return found?sanitize(legacy):null;
}
function clearLegacy(){
  ["raspored.schedule","raspored.timeEntries.v1","raspored.colleagues.v1","raspored.profile.name","raspored.theme","raspored.reducedMotion","raspored.notifications.readKey"].forEach(function(key){try{localStorage.removeItem(key)}catch(e){}});
  try{sessionStorage.removeItem("raspored.scan.v1")}catch(e){}
}
function notifyError(){
  window.dispatchEvent(new CustomEvent("raspored:storage-error"));
}
async function writeNow(){
  if(!serverLoaded)throw new Error("Storage state was not loaded");
  var generation=localGeneration;
  var sent=clone(current);
  var response=await fetch(endpoint(),{
    method:"PUT",
    credentials:"same-origin",
    cache:"no-store",
    headers:{"Content-Type":"application/json","X-Raspored-Request":"1"},
    body:JSON.stringify({state:sent})
  });
  if(!response.ok)throw new Error("Storage write failed: "+response.status);
  var payload=await response.json();
  if(!payload||payload.ok!==true||!payload.state)throw new Error("Invalid storage response");
  var server=sanitize(payload.state);
  if(generation===localGeneration){
    current=server;
  }else{
    current.revision=server.revision;
    current.updatedAt=server.updatedAt;
  }
  window.dispatchEvent(new CustomEvent("raspored:storage-synced"));
  return current;
}
function queueWrite(){
  writeChain=writeChain.then(writeNow,writeNow).catch(function(){notifyError();return current});
  return writeChain;
}
function valueForKey(key){
  if(key==="raspored.schedule")return JSON.stringify(current.schedule);
  if(key==="raspored.timeEntries.v1")return JSON.stringify(current.evidence);
  if(key==="raspored.colleagues.v1")return JSON.stringify(current.colleagues);
  if(key==="raspored.team.v1")return JSON.stringify(current.teamMembers);
  if(key==="raspored.profile.name")return current.profile.name||null;
  if(key==="raspored.theme")return current.settings.theme||"dark";
  if(key==="raspored.reducedMotion")return current.settings.reducedMotion?"1":"0";
  if(key==="raspored.scan.v1")return JSON.stringify(current.scanSession);
  if(key==="raspored.notifications.readKey")return current.settings.notificationReadKey||null;
  if(key==="raspored.payroll.v1")return JSON.stringify(current.payroll);
  return null;
}
function setKey(key,value){
  if(!serverLoaded){notifyError();return false}
  try{
    if(key==="raspored.schedule")current.schedule=sanitizeSchedule(JSON.parse(value||"{}"));
    else if(key==="raspored.timeEntries.v1")current.evidence=sanitizeEvidence(JSON.parse(value||"[]"));
    else if(key==="raspored.colleagues.v1")current.colleagues=sanitize({colleagues:JSON.parse(value||"[]")}).colleagues;
    else if(key==="raspored.team.v1")current.teamMembers=sanitizeTeamMembers(JSON.parse(value||"[]"));
    else if(key==="raspored.profile.name")current.profile.name=String(value||"").trim().replace(/\s+/g," ").slice(0,80);
    else if(key==="raspored.theme")current.settings.theme=value==="dark"?"dark":"light";
    else if(key==="raspored.reducedMotion")current.settings.reducedMotion=value==="1";
    else if(key==="raspored.scan.v1"){
      var parsed=JSON.parse(value||"null");
      current.scanSession=sanitize({scanSession:parsed}).scanSession;
    }else if(key==="raspored.notifications.readKey")current.settings.notificationReadKey=String(value||"").slice(0,120);
    else if(key==="raspored.payroll.v1"){
      var p=JSON.parse(value||"{}");
      current.payroll=sanitize({payroll:p}).payroll;
    }else return false;
    localGeneration++;
    queueWrite();
    return true;
  }catch(e){return false}
}
function removeKey(key){
  if(!serverLoaded){notifyError();return false}
  if(key==="raspored.schedule")current.schedule={};
  else if(key==="raspored.timeEntries.v1")current.evidence=[];
  else if(key==="raspored.colleagues.v1")current.colleagues=[];
  else if(key==="raspored.team.v1")current.teamMembers=[];
  else if(key==="raspored.profile.name")current.profile.name="";
  else if(key==="raspored.theme")current.settings.theme="light";
  else if(key==="raspored.reducedMotion")current.settings.reducedMotion=false;
  else if(key==="raspored.scan.v1")current.scanSession={people:[],reviewCells:[],selected:-1,month:null,expectedRows:0,incomplete:false};
  else if(key==="raspored.notifications.readKey")current.settings.notificationReadKey="";
  else if(key==="raspored.payroll.v1")current.payroll={county:"Primorsko-goranska",residence:"Rijeka",taxLower:20,taxHigher:25,sector:"Zdravstvo",institution:"Klinički bolnički centar Rijeka",regimeId:"kbc-rijeka-2026",roleId:"health-transport-sss",coefficient:1.25,yearsService:0,personalAllowance:600,extraPercent:0,secondShift:false,turnus:false,customBase:null};
  else return false;
  localGeneration++;
  queueWrite();
  return true;
}
async function init(){
  if(initialized)return current;
  initialized=true;
  try{
    var response=await fetch(endpoint(),{credentials:"same-origin",cache:"no-store",headers:{"Accept":"application/json"}});
    if(!response.ok)throw new Error("Storage read failed: "+response.status);
    var payload=await response.json();
    if(!payload||payload.ok!==true||!payload.state)throw new Error("Invalid storage response");
    current=sanitize(payload.state);
    serverLoaded=true;
    if(emptyState(current)){
      var legacy=readLegacy();
      if(legacy){
        current=legacy;
        localGeneration++;
        await writeNow();
        clearLegacy();
      }
    }
  }catch(error){
    serverLoaded=false;
    notifyError();
  }
  return current;
}

window.RasporedDataStore={
  init:init,
  get:function(key){return valueForKey(key)},
  set:setKey,
  remove:removeKey,
  snapshot:function(){return clone(current)},
  flush:function(){return writeChain},
  reload:async function(){initialized=false;serverLoaded=false;return init()},
  isAvailable:function(){return serverLoaded}
};
})();