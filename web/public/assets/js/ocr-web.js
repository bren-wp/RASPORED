(function(){
"use strict";

var MONTHS={
  "SIJECANJ":1,"VELJACA":2,"OZUJAK":3,"TRAVANJ":4,"SVIBANJ":5,"LIPANJ":6,
  "SRPANJ":7,"KOLOVOZ":8,"RUJAN":9,"LISTOPAD":10,"STUDENI":11,"PROSINAC":12
};
var VALID=new Set(["D","N","GO","BO","PD","SD"]);
var workerPromise=null;

function normalize(value){
  return String(value||"").replace(/ /g," ").replace(/s+/g," ").trim();
}
function normalizeAscii(value){
  return normalize(value).toLocaleUpperCase("hr-HR")
    .normalize("NFD").replace(/[̀-ͯ]/g,"").replace(/Đ/g,"D");
}
function canonicalShift(raw){
  var value=normalize(raw).toUpperCase().replace(/[.,;:]+$/,"");
  if(value==="G0")return "GO";
  if(value==="B0")return "BO";
  return VALID.has(value)?value:null;
}
function centerX(bbox){return bbox?(Number(bbox.x0)+Number(bbox.x1))/2:NaN}
function centerY(bbox){return bbox?(Number(bbox.y0)+Number(bbox.y1))/2:NaN}
function boxHeight(bbox){return bbox?Math.max(1,Number(bbox.y1)-Number(bbox.y0)):1}
function median(values){
  if(!values.length)return 0;
  var sorted=values.slice().sort(function(a,b){return a-b});
  return sorted[Math.floor(sorted.length/2)];
}
function detectMonth(text){
  var upper=normalizeAscii(text);
  var yearFirst=upper.match(/(20d{2})s*[./-]s*(0?[1-9]|1[0-2])/);
  if(yearFirst)return {year:Number(yearFirst[1]),month:Number(yearFirst[2])};
  var monthFirst=upper.match(/(0?[1-9]|1[0-2])s*[./-]s*(20d{2})/);
  if(monthFirst)return {year:Number(monthFirst[2]),month:Number(monthFirst[1])};
  var labelled=upper.match(/(?:MJESEC|MJESECA|ZA)s*[:.-]?s*(0?[1-9]|1[0-2])s+(20d{2})/);
  if(labelled)return {year:Number(labelled[2]),month:Number(labelled[1])};
  var yearMatch=upper.match(/(20d{2})/);
  if(!yearMatch)return null;
  var monthName=Object.keys(MONTHS).find(function(name){return upper.indexOf(name)>=0});
  return monthName?{year:Number(yearMatch[1]),month:MONTHS[monthName]}:null;
}
function cleanName(text){
  return normalize(text)
    .replace(/^d{1,3}[.)]?s*/,"")
    .replace(/(?<!d)([1-9]|[12]d|3[01])s*[:.)-]?s*(?:GO|G0|BO|B0|PD|SD|D|N)(?!p{L})/giu," ")
    .replace(/d{1,2}([./-]d{1,2})?/g," ")
    .replace(/(?:GO|G0|BO|B0|PD|SD|D|N)/gi," ")
    .replace(/s+/g," ")
    .replace(/^[s|:;.,-]+|[s|:;.,-]+$/g,"");
}
function validName(name){
  if(name.length<3||(name.match(/p{L}/gu)||[]).length<3)return false;
  var normalized=normalizeAscii(name);
  return !["IME PREZIME","IME I PREZIME","DJELATNIK","ZAPOSLENIK"].includes(normalized);
}
function mergeRows(rows){
  var map=new Map();
  rows.forEach(function(row){
    var key=normalizeAscii(row.name);
    if(!key)return;
    var existing=map.get(key);
    if(!existing){map.set(key,row);return}
    map.set(key,{
      row:existing.row==null?row.row:existing.row,
      name:existing.name,
      dayShifts:Object.assign({},existing.dayShifts,row.dayShifts)
    });
  });
  return Array.from(map.values()).map(function(row){
    row.dayShifts=Object.keys(row.dayShifts).map(Number).sort(function(a,b){return a-b})
      .reduce(function(out,day){out[day]=row.dayShifts[day];return out},{});
    return row;
  });
}
function parseText(text){
  var rows=String(text||"").split(/?
/).map(normalize).filter(Boolean).map(function(line){
    var explicit={};
    Array.from(line.matchAll(/(?<!d)([1-9]|[12]d|3[01])s*[:.)-]?s*(GO|G0|BO|B0|PD|SD|D|N)(?!p{L})/giu))
      .forEach(function(match){
        var code=canonicalShift(match[2]);
        if(code)explicit[Number(match[1])]=code;
      });
    var codes=Array.from(line.matchAll(/(?<!p{L})(GO|G0|BO|B0|PD|SD|D|N)[.,;:]?(?!p{L})/giu))
      .map(function(match){return canonicalShift(match[0])}).filter(Boolean);
    if(!codes.length)return null;
    var rowMatch=line.match(/^s*(d{1,3})[.)]?s*/);
    var name=cleanName(line);
    if(!validName(name))return null;
    var dayShifts=Object.keys(explicit).length?explicit:{};
    if(!Object.keys(explicit).length)codes.forEach(function(code,index){dayShifts[index+1]=code});
    return {row:rowMatch?Number(rowMatch[1]):null,name:name,dayShifts:dayShifts};
  }).filter(Boolean);
  return mergeRows(rows);
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
function bestHeader(lines){
  var best=null;
  lines.forEach(function(line){
    var days=(line.words||[]).map(function(word){
      var raw=normalize(word.text).replace(/[.,:;]+$/,"");
      var value=Number(raw);
      return value>=1&&value<=31&&word.bbox?{day:value,x:centerX(word.bbox)}:null;
    }).filter(Boolean);
    var dedup=[];
    var seen=new Set();
    days.forEach(function(item){if(!seen.has(item.day)){seen.add(item.day);dedup.push(item)}});
    dedup.sort(function(a,b){return a.day-b.day});
    if(!best||dedup.length>best.days.length)best={line:line,days:dedup};
  });
  return best&&best.days.length>=5?best:null;
}
function dayGeometry(header){
  var centers={};
  header.days.forEach(function(item){centers[item.day]=item.x});
  var ordered=Object.keys(centers).map(Number).sort(function(a,b){return a-b});
  var diffs=[];
  for(var i=1;i<ordered.length;i++){
    var diff=Math.abs(centers[ordered[i]]-centers[ordered[i-1]]);
    if(diff>0)diffs.push(diff);
  }
  return {
    centers:centers,
    minX:Math.min.apply(null,Object.values(centers)),
    spacing:median(diffs)||32
  };
}
function parseTokenRow(tokens,geometry){
  var shifts=tokens.map(function(token){
    var code=canonicalShift(token.text);
    return code&&token.bbox?{code:code,x:centerX(token.bbox)}:null;
  }).filter(Boolean);
  if(!shifts.length)return null;
  var firstShiftX=Math.min.apply(null,shifts.map(function(item){return item.x}));
  var boundary=Math.min(firstShiftX,geometry.minX);
  var leftText=tokens.filter(function(token){
    return token.bbox&&centerX(token.bbox)<boundary&&!canonicalShift(token.text);
  }).map(function(token){return token.text}).join(" ");
  var rowMatch=leftText.match(/^s*(d{1,3})[.)]?s*/);
  var name=cleanName(leftText);
  if(!validName(name))return null;
  var dayShifts={};
  var maxDistance=Math.max(14,geometry.spacing*0.58);
  shifts.forEach(function(item){
    var nearest=null,best=Infinity;
    Object.keys(geometry.centers).forEach(function(day){
      var distance=Math.abs(geometry.centers[day]-item.x);
      if(distance<best){best=distance;nearest=Number(day)}
    });
    if(nearest&&best<=maxDistance)dayShifts[nearest]=item.code;
  });
  return Object.keys(dayShifts).length?{row:rowMatch?Number(rowMatch[1]):null,name:name,dayShifts:dayShifts}:null;
}
function parseGeometry(blocks){
  var lines=flattenLines(blocks);
  if(!lines.length)return [];
  var header=bestHeader(lines);
  if(!header)return [];
  var geometry=dayGeometry(header);
  var headerBottom=header.line.bbox?Number(header.line.bbox.y1):-Infinity;

  var rows=lines.map(function(line){
    if(!line.bbox||Number(line.bbox.y0)<=headerBottom)return null;
    return parseTokenRow(line.words||[],geometry);
  }).filter(Boolean);
  if(rows.length)return mergeRows(rows);

  // Secondary pass: Tesseract sometimes splits one schedule row into multiple
  // OCR lines. Rebuild visual rows by vertical centers before mapping columns.
  var tokens=[];
  lines.forEach(function(line){
    if(!line.bbox||Number(line.bbox.y0)<=headerBottom)return;
    (line.words||[]).forEach(function(word){
      if(word.bbox&&normalize(word.text))tokens.push(word);
    });
  });
  if(!tokens.length)return [];
  var tolerance=Math.max(8,median(tokens.map(function(token){return boxHeight(token.bbox)}))*0.70);
  var clusters=[];
  tokens.sort(function(a,b){return centerY(a.bbox)-centerY(b.bbox)}).forEach(function(token){
    var y=centerY(token.bbox),best=null,bestDistance=Infinity;
    clusters.forEach(function(cluster){
      var avg=cluster.reduce(function(sum,item){return sum+centerY(item.bbox)},0)/cluster.length;
      var distance=Math.abs(avg-y);
      if(distance<bestDistance){bestDistance=distance;best=cluster}
    });
    if(best&&bestDistance<=tolerance)best.push(token);
    else clusters.push([token]);
  });
  return mergeRows(clusters.map(function(cluster){
    return parseTokenRow(cluster.sort(function(a,b){return centerX(a.bbox)-centerX(b.bbox)}),geometry);
  }).filter(Boolean));
}
async function prepareImage(file){
  if(typeof createImageBitmap!=="function")return file;
  var bitmap;
  try{
    bitmap=await createImageBitmap(file,{imageOrientation:"from-image"});
    var largest=Math.max(bitmap.width,bitmap.height);
    var target=Math.min(largest,2600);
    var scale=target/largest;
    var canvas=document.createElement("canvas");
    canvas.width=Math.max(1,Math.round(bitmap.width*scale));
    canvas.height=Math.max(1,Math.round(bitmap.height*scale));
    var context=canvas.getContext("2d",{alpha:false,willReadFrequently:false});
    if(!context)return file;
    context.fillStyle="#fff";
    context.fillRect(0,0,canvas.width,canvas.height);
    context.filter="contrast(1.18) saturate(0.75)";
    context.drawImage(bitmap,0,0,canvas.width,canvas.height);
    context.filter="none";
    return await new Promise(function(resolve){
      canvas.toBlob(function(blob){resolve(blob||file)},"image/jpeg",0.94);
    });
  }catch(error){
    return file;
  }finally{
    if(bitmap&&typeof bitmap.close==="function")bitmap.close();
  }
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
  var source=await prepareImage(file);
  var result=await worker.recognize(source,{}, {text:true,blocks:true});
  var text=result&&result.data?result.data.text:"";
  var rows=parseGeometry(result&&result.data?result.data.blocks:null);
  if(!rows.length)rows=parseText(text);
  return {month:detectMonth(text),people:rows,rawText:text};
}
window.RasporedWebOcr={
  recognizeSchedule:recognizeSchedule,
  parseText:parseText,
  parseGeometry:parseGeometry,
  detectMonth:detectMonth
};
})();