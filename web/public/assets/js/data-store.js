(function(){
"use strict";

var current={
  schema:1,
  revision:0,
  schedule:{},
  evidence:[],
  profile:{name:""},
  colleagues:[],
  settings:{theme:"light",reducedMotion:false},
  scanSession:{people:[],selected:-1,month:null},
  updatedAt:null
};
var writeChain=Promise.resolve();
var initialized=false;

function clone(value){return JSON.parse(JSON.stringify(value))}
function base(){return document.body&&document.body.dataset?document.body.dataset.base||"":""}
function endpoint(){return base()+"/api/state.php"}

function sanitizeSchedule(raw){
  var out={};
  if(!raw||typeof raw!=="object"||Array.isArray(raw))return out;
  Object.keys(raw).forEach(function(key){
    var code=raw[key];
    if(/^\d{4}-\d{2}-\d{2}$/.test(key)&&["D","N","GO","BO"].indexOf(code)>=0)out[key]=code;
  });
  return out;
}
function sanitizeEvidence(raw){
  if(!Array.isArray(raw))return [];
  return raw.slice(-366).filter(function(x){
    return x&&/^\d{4}-\d{2}-\d{2}$/.test(x.date||"")&&/^\d{2}:\d{2}$/.test(x.in||"")&&(!x.out||/^\d{2}:\d{2}$/.test(x.out));
  }).map(function(x){
    return {
      id:String(x.id||Date.now()).slice(0,80),
      date:x.date,
      in:x.in,
      out:x.out||null,
      note:typeof x.note==="string"?x.note.slice(0,500):"",
      startedAt:Number.isFinite(Number(x.startedAt))?Number(x.startedAt):null,
      endedAt:Number.isFinite(Number(x.endedAt))?Number(x.endedAt):null
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
      var n=Number(day),code=item.dayShifts[day];
      if(Number.isInteger(n)&&n>=1&&n<=31&&["D","N","GO","BO"].indexOf(code)>=0)shifts[n]=code;
    });
    return {row:Number.isInteger(item.row)?item.row:null,name:name,dayShifts:shifts};
  }).filter(Boolean);
}
function sanitize(raw){
  raw=raw&&typeof raw==="object"?raw:{};
  var profile=raw.profile&&typeof raw.profile==="object"?raw.profile:{};
  var settings=raw.settings&&typeof raw.settings==="object"?raw.settings:{};
  var scan=raw.scanSession&&typeof raw.scanSession==="object"?raw.scanSession:{};
  var colleagues=Array.isArray(raw.colleagues)?raw.colleagues.slice(0,30).filter(function(x){return x&&typeof x.name==="string"&&x.name.trim().length>=2}).map(function(x){return {name:x.name.trim().replace(/\s+/g," ").slice(0,80),note:typeof x.note==="string"?x.note.trim().replace(/\s+/g," ").slice(0,120):""}}):[];
  var month=scan.month&&Number.isInteger(scan.month.year)&&Number.isInteger(scan.month.month)&&scan.month.month>=1&&scan.month.month<=12?{year:scan.month.year,month:scan.month.month}:null;
  var people=sanitizePeople(scan.people);
  var selected=Number.isInteger(scan.selected)&&scan.selected>=-1&&scan.selected<people.length?scan.selected:-1;
  return {
    schema:1,
    revision:Number.isInteger(raw.revision)&&raw.revision>=0?raw.revision:0,
    schedule:sanitizeSchedule(raw.schedule),
    evidence:sanitizeEvidence(raw.evidence),
    profile:{name:typeof profile.name==="string"?profile.name.trim().replace(/\s+/g," ").slice(0,80):""},
    colleagues:colleagues,
    settings:{theme:settings.theme==="dark"?"dark":"light",reducedMotion:!!settings.reducedMotion},
    scanSession:{people:people,selected:selected,month:month},
    updatedAt:typeof raw.updatedAt==="string"?raw.updatedAt:null
  };
}
function emptyState(data){
  return data.revision===0&&!Object.keys(data.schedule).length&&!data.evidence.length&&!data.profile.name&&!data.colleagues.length&&!data.scanSession.people.length;
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
  try{
    var scan=JSON.parse(legacySession("raspored.scan.v1")||"null");
    if(scan&&typeof scan==="object"){legacy.scanSession=scan;found=true}
  }catch(e){}
  return found?sanitize(legacy):null;
}
function clearLegacy(){
  ["raspored.schedule","raspored.timeEntries.v1","raspored.colleagues.v1","raspored.profile.name","raspored.theme","raspored.reducedMotion"].forEach(function(key){try{localStorage.removeItem(key)}catch(e){}});
  try{sessionStorage.removeItem("raspored.scan.v1")}catch(e){}
}
function notifyError(){
  window.dispatchEvent(new CustomEvent("raspored:storage-error"));
}
async function writeNow(){
  var response=await fetch(endpoint(),{
    method:"PUT",
    credentials:"same-origin",
    cache:"no-store",
    headers:{"Content-Type":"application/json","X-Raspored-Request":"1"},
    body:JSON.stringify({state:current})
  });
  if(!response.ok)throw new Error("Storage write failed: "+response.status);
  var payload=await response.json();
  if(!payload||payload.ok!==true||!payload.state)throw new Error("Invalid storage response");
  current=sanitize(payload.state);
  window.dispatchEvent(new CustomEvent("raspored:storage-synced"));
  return current;
}
function queueWrite(){
  writeChain=writeChain.then(writeNow,writeNow).catch(function(error){notifyError();throw error});
  return writeChain;
}
function valueForKey(key){
  if(key==="raspored.schedule")return JSON.stringify(current.schedule);
  if(key==="raspored.timeEntries.v1")return JSON.stringify(current.evidence);
  if(key==="raspored.colleagues.v1")return JSON.stringify(current.colleagues);
  if(key==="raspored.profile.name")return current.profile.name||null;
  if(key==="raspored.theme")return current.settings.theme||"light";
  if(key==="raspored.reducedMotion")return current.settings.reducedMotion?"1":"0";
  if(key==="raspored.scan.v1")return JSON.stringify(current.scanSession);
  return null;
}
function setKey(key,value){
  try{
    if(key==="raspored.schedule")current.schedule=sanitizeSchedule(JSON.parse(value||"{}"));
    else if(key==="raspored.timeEntries.v1")current.evidence=sanitizeEvidence(JSON.parse(value||"[]"));
    else if(key==="raspored.colleagues.v1")current.colleagues=sanitize({colleagues:JSON.parse(value||"[]")}).colleagues;
    else if(key==="raspored.profile.name")current.profile.name=String(value||"").trim().replace(/\s+/g," ").slice(0,80);
    else if(key==="raspored.theme")current.settings.theme=value==="dark"?"dark":"light";
    else if(key==="raspored.reducedMotion")current.settings.reducedMotion=value==="1";
    else if(key==="raspored.scan.v1"){
      var parsed=JSON.parse(value||"null");
      current.scanSession=sanitize({scanSession:parsed}).scanSession;
    }else return false;
    queueWrite();
    return true;
  }catch(e){return false}
}
function removeKey(key){
  if(key==="raspored.schedule")current.schedule={};
  else if(key==="raspored.timeEntries.v1")current.evidence=[];
  else if(key==="raspored.colleagues.v1")current.colleagues=[];
  else if(key==="raspored.profile.name")current.profile.name="";
  else if(key==="raspored.theme")current.settings.theme="light";
  else if(key==="raspored.reducedMotion")current.settings.reducedMotion=false;
  else if(key==="raspored.scan.v1")current.scanSession={people:[],selected:-1,month:null};
  else return false;
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
    if(emptyState(current)){
      var legacy=readLegacy();
      if(legacy){
        current=legacy;
        await writeNow();
        clearLegacy();
      }
    }
  }catch(error){
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
  reload:async function(){initialized=false;return init()}
};
})();