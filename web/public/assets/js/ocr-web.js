(function(){
"use strict";

var MONTHS={
  "SIJEČANJ":1,"VELJAČA":2,"OŽUJAK":3,"TRAVANJ":4,"SVIBANJ":5,"LIPANJ":6,
  "SRPANJ":7,"KOLOVOZ":8,"RUJAN":9,"LISTOPAD":10,"STUDENI":11,"PROSINAC":12
};
var VALID=new Set(["D","N","GO","BO"]);
var workerPromise=null;

function normalize(value){return String(value||"").replace(/\u00a0/g," ").replace(/\s+/g," ").trim()}
function centerX(bbox){return bbox?(Number(bbox.x0)+Number(bbox.x1))/2:NaN}
function detectMonth(text){
  var upper=String(text||"").toLocaleUpperCase("hr-HR");
  var yearMatch=upper.match(/\b(20\d{2})\b/);
  if(!yearMatch)return null;
  var monthName=Object.keys(MONTHS).find(function(name){return upper.indexOf(name)>=0});
  return monthName?{year:Number(yearMatch[1]),month:MONTHS[monthName]}:null;
}
function cleanName(text){
  return normalize(text)
    .replace(/^\d{1,3}[.)]?\s*/,"")
    .replace(/\b\d{1,2}([./-]\d{1,2})?\b/g," ")
    .replace(/\b(?:GO|BO|D|N)\b/gi," ")
    .replace(/\s+/g," ")
    .replace(/^[\s|:;.,-]+|[\s|:;.,-]+$/g,"");
}
function validName(name){return name.length>=3&&(name.match(/\p{L}/gu)||[]).length>=3}
function dedupe(rows){
  var seen=new Set();
  return rows.filter(function(row){
    var key=row.name.toLocaleUpperCase("hr-HR");
    if(seen.has(key))return false;
    seen.add(key);return true;
  });
}
function parseText(text){
  var rows=String(text||"").split(/\r?\n/).map(normalize).filter(Boolean).map(function(line){
    var codes=[].concat(line.match(/(?<!\p{L})(GO|BO|D|N)(?!\p{L})/giu)||[]).map(function(code){return code.toUpperCase()}).filter(function(code){return VALID.has(code)});
    if(!codes.length)return null;
    var rowMatch=line.match(/^\s*(\d{1,3})[.)]?\s*/);
    var name=cleanName(line);
    if(!validName(name))return null;
    var dayShifts={};codes.forEach(function(code,index){dayShifts[index+1]=code});
    return {row:rowMatch?Number(rowMatch[1]):null,name:name,dayShifts:dayShifts};
  }).filter(Boolean);
  return dedupe(rows);
}
function flattenLines(blocks){
  var lines=[];
  (blocks||[]).forEach(function(block){
    (block.paragraphs||[]).forEach(function(paragraph){
      (paragraph.lines||[]).forEach(function(line){lines.push(line)});
    });
  });
  return lines;
}
function parseGeometry(blocks){
  var lines=flattenLines(blocks);
  if(!lines.length)return [];
  var header=null;
  lines.forEach(function(line){
    var days=(line.words||[]).map(function(word){
      var value=Number(normalize(word.text));
      return value>=1&&value<=31&&word.bbox?{day:value,x:centerX(word.bbox)}:null;
    }).filter(Boolean);
    if(!header||days.length>header.days.length)header={line:line,days:days};
  });
  if(!header||header.days.length<5)return [];
  var dayCenters={};header.days.forEach(function(item){dayCenters[item.day]=item.x});
  var minDayX=Math.min.apply(null,Object.values(dayCenters));
  var headerBottom=header.line.bbox?Number(header.line.bbox.y1):-Infinity;
  var rows=lines.map(function(line){
    if(!line.bbox||Number(line.bbox.y0)<=headerBottom)return null;
    var shiftWords=(line.words||[]).map(function(word){
      var code=normalize(word.text).toUpperCase();
      return VALID.has(code)&&word.bbox?{code:code,x:centerX(word.bbox)}:null;
    }).filter(Boolean);
    if(!shiftWords.length)return null;
    var firstShiftX=Math.min.apply(null,shiftWords.map(function(item){return item.x}));
    var leftText=(line.words||[]).filter(function(word){
      var x=centerX(word.bbox);
      return Number.isFinite(x)&&x<Math.min(firstShiftX,minDayX)&&!VALID.has(normalize(word.text).toUpperCase());
    }).map(function(word){return word.text}).join(" ");
    var rowMatch=leftText.match(/^\s*(\d{1,3})[.)]?\s*/);
    var name=cleanName(leftText);
    if(!validName(name))return null;
    var dayShifts={};
    shiftWords.forEach(function(item){
      var nearest=Object.keys(dayCenters).map(Number).sort(function(a,b){return Math.abs(dayCenters[a]-item.x)-Math.abs(dayCenters[b]-item.x)})[0];
      if(nearest)dayShifts[nearest]=item.code;
    });
    return Object.keys(dayShifts).length?{row:rowMatch?Number(rowMatch[1]):null,name:name,dayShifts:dayShifts}:null;
  }).filter(Boolean);
  return dedupe(rows);
}
async function getWorker(onProgress){
  if(workerPromise)return workerPromise;
  workerPromise=(async function(){
    var mod=await import("https://cdn.jsdelivr.net/npm/tesseract.js@6.0.1/dist/tesseract.esm.min.js");
    return mod.createWorker(["hrv","eng"],1,{logger:function(message){
      if(onProgress&&message&&typeof message.progress==="number")onProgress(message.progress,message.status||"");
    }});
  })();
  try{return await workerPromise}catch(error){workerPromise=null;throw error}
}
async function recognizeSchedule(file,onProgress){
  var worker=await getWorker(onProgress);
  var result=await worker.recognize(file,{}, {text:true,blocks:true});
  var text=result&&result.data?result.data.text:"";
  var rows=parseGeometry(result&&result.data?result.data.blocks:null);
  if(!rows.length)rows=parseText(text);
  return {month:detectMonth(text),people:rows,rawText:text};
}
window.RasporedWebOcr={recognizeSchedule:recognizeSchedule,parseText:parseText,detectMonth:detectMonth};
})();