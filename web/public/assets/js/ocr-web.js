(function(){
"use strict";

var MONTHS={
  "SIJECANJ":1,"VELJACA":2,"OZUJAK":3,"TRAVANJ":4,"SVIBANJ":5,"LIPANJ":6,
  "SRPANJ":7,"KOLOVOZ":8,"RUJAN":9,"LISTOPAD":10,"STUDENI":11,"PROSINAC":12
};
var VALID=new Set(["D","N","GO","BO","PD","SD"]);
var workerPromise=null;
var recognitionChain=Promise.resolve();
var activeProgressCallback=null;

function normalize(value){
  return String(value||"").replace(/\u00a0/g," ").replace(/\s+/g," ").trim();
}
function normalizeAscii(value){
  return normalize(value).toLocaleUpperCase("hr-HR")
    .normalize("NFD").replace(/[\u0300-\u036f]/g,"").replace(/Đ/g,"D");
}
function canonicalShift(raw){
  var value=normalize(raw).toUpperCase().replace(/[.,;:|]+$/,"");
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
  var yearFirst=upper.match(/\b(20\d{2})[ \t]*[./-][ \t]*(0?[1-9]|1[0-2])\b/);
  if(yearFirst)return {year:Number(yearFirst[1]),month:Number(yearFirst[2])};
  var monthFirst=upper.match(/\b(0?[1-9]|1[0-2])[ \t]*[./-][ \t]*(20\d{2})\b/);
  if(monthFirst)return {year:Number(monthFirst[2]),month:Number(monthFirst[1])};
  var labelled=upper.match(/\b(?:MJESEC|MJESECA|ZA)\s*[:.-]?\s*(0?[1-9]|1[0-2])\s+(20\d{2})\b/);
  if(labelled)return {year:Number(labelled[2]),month:Number(labelled[1])};
  var yearMatch=upper.match(/\b(20\d{2})\b/);
  if(!yearMatch)return null;
  var monthName=Object.keys(MONTHS).find(function(name){
    return new RegExp("(?:^|\\s)"+name+"(?:\\s|[.,;:/-]|$)").test(upper);
  });
  return monthName?{year:Number(yearMatch[1]),month:MONTHS[monthName]}:null;
}
function daysInMonth(month){
  if(!month||!month.year||!month.month)return 31;
  return new Date(month.year,month.month,0).getDate();
}
function cleanName(text){
  return normalize(text)
    .replace(/^\d{1,3}[.)]?\s*/,"")
    .replace(/(?<!\d)([1-9]|[12]\d|3[01])\s*[:.)-]?\s*(?:GO|G0|BO|B0|PD|SD|D|N)(?!\p{L})/giu," ")
    .replace(/\b\d{1,2}([./-]\d{1,2})?\b/g," ")
    .replace(/\b(?:GO|G0|BO|B0|PD|SD|D|N)\b/gi," ")
    .replace(/\s+/g," ")
    .replace(/^[\s|:;.,-]+|[\s|:;.,-]+$/g,"");
}
function validName(name){
  if(name.length<3||(name.match(/\p{L}/gu)||[]).length<3)return false;
  var normalized=normalizeAscii(name);
  return !["IME PREZIME","IME I PREZIME","DJELATNIK","ZAPOSLENIK","RADNIK"].includes(normalized);
}
function sortedShiftMap(raw){
  return Object.keys(raw||{}).map(Number).filter(function(day){return day>=1&&day<=31})
    .sort(function(a,b){return a-b})
    .reduce(function(out,day){out[day]=raw[day];return out},{});
}
function nameFingerprint(value){
  return normalizeAscii(value).replace(/[^A-Z0-9ČĆŽŠĐ]/g,"");
}
function mergeRows(rows){
  var merged=[];
  (rows||[]).forEach(function(row){
    if(!row||!validName(row.name||""))return;
    var key=nameFingerprint(row.name);
    var index=merged.findIndex(function(existing){
      var sameRow=row.row!=null&&existing.row!=null&&Number(row.row)===Number(existing.row);
      var conflictingRows=row.row!=null&&existing.row!=null&&Number(row.row)!==Number(existing.row);
      var sameName=nameFingerprint(existing.name)===key;
      return sameRow||(sameName&&!conflictingRows);
    });
    if(index<0){
      merged.push({row:row.row==null?null:Number(row.row),name:row.name,dayShifts:sortedShiftMap(row.dayShifts||{})});
      return;
    }
    var existing=merged[index];
    merged[index]={
      row:existing.row==null?row.row:existing.row,
      name:existing.name.length>=row.name.length?existing.name:row.name,
      dayShifts:sortedShiftMap(Object.assign({},row.dayShifts||{},existing.dayShifts||{}))
    };
  });
  return merged.sort(function(a,b){
    var ar=a.row==null?9999:a.row,br=b.row==null?9999:b.row;
    return ar-br||a.name.localeCompare(b.name,"hr");
  });
}
function parseText(text){
  var rows=String(text||"").split(/\r?\n/).map(normalize).filter(Boolean).map(function(line){
    var explicit={};
    Array.from(line.matchAll(/(?<!\d)([1-9]|[12]\d|3[01])\s*[:.)-]?\s*(GO|G0|BO|B0|PD|SD|D|N)(?!\p{L})/giu))
      .forEach(function(match){
        var code=canonicalShift(match[2]);
        if(code)explicit[Number(match[1])]=code;
      });
    var hasCode=Array.from(line.matchAll(/(?<!\p{L})(GO|G0|BO|B0|PD|SD|D|N)[.,;:|]?(?!\p{L})/giu))
      .some(function(match){return !!canonicalShift(match[0])});
    if(!hasCode)return null;
    var rowMatch=line.match(/^\s*(\d{1,3})[.)]?\s*/);
    var name=cleanName(line);
    if(!validName(name))return null;
    // Tekst bez geometrije nije dovoljan za sigurno određivanje praznih stupaca.
    // Spremamo samo eksplicitne parove "dan + oznaka" i ne komprimiramo dane ulijevo.
    return {row:rowMatch?Number(rowMatch[1]):null,name:name,dayShifts:explicit};
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
function allWords(lines){
  var words=[];
  (lines||[]).forEach(function(line){
    (line.words||[]).forEach(function(word){
      if(word&&word.bbox&&normalize(word.text))words.push(word);
    });
  });
  return words;
}
function dayNumber(text,maxDay){
  var raw=normalize(text).replace(/^[|]+|[.,:;|]+$/g,"");
  if(!/^\d{1,2}$/.test(raw))return null;
  var value=Number(raw);
  return value>=1&&value<=maxDay?value:null;
}
function longestIncreasingChain(items){
  if(!items.length)return [];
  var sorted=items.slice().sort(function(a,b){return a.x-b.x||a.day-b.day});
  var length=sorted.map(function(){return 1});
  var previous=sorted.map(function(){return -1});
  for(var right=0;right<sorted.length;right++){
    for(var left=0;left<right;left++){
      if(sorted[left].day<sorted[right].day&&sorted[left].x<sorted[right].x&&length[left]+1>length[right]){
        length[right]=length[left]+1;previous[right]=left;
      }
    }
  }
  var best=0;
  for(var i=1;i<length.length;i++)if(length[i]>length[best])best=i;
  var out=[];
  for(var index=best;index>=0;index=previous[index]){
    out.push(sorted[index]);
    if(previous[index]<0)break;
  }
  return out.reverse();
}
function inferDayCenters(observed,maxDay){
  var days=Object.keys(observed).map(Number).filter(function(day){return day>=1&&day<=maxDay}).sort(function(a,b){return a-b});
  if(days.length<2)return null;
  var slopes=[];
  for(var i=1;i<days.length;i++){
    var deltaDay=days[i]-days[i-1],deltaX=observed[days[i]]-observed[days[i-1]];
    if(deltaDay>0&&deltaX>0)slopes.push(deltaX/deltaDay);
  }
  var spacing=median(slopes);
  if(!spacing||spacing<3)return null;
  var centers={};
  for(var day=1;day<=maxDay;day++){
    if(observed[day]!=null){centers[day]=observed[day];continue}
    var lower=null,upper=null;
    for(var li=days.length-1;li>=0;li--)if(days[li]<day){lower=days[li];break}
    for(var ui=0;ui<days.length;ui++)if(days[ui]>day){upper=days[ui];break}
    if(lower!=null&&upper!=null){
      var ratio=(day-lower)/(upper-lower);
      centers[day]=observed[lower]+(observed[upper]-observed[lower])*ratio;
    }else if(lower!=null)centers[day]=observed[lower]+(day-lower)*spacing;
    else centers[day]=observed[upper]-(upper-day)*spacing;
  }
  return {centers:centers,spacing:spacing};
}
function findHeader(lines,maxDay){
  var words=allWords(lines);
  var candidates=words.map(function(word){
    var day=dayNumber(word.text,maxDay);
    return day==null?null:{day:day,x:centerX(word.bbox),y:centerY(word.bbox),word:word};
  }).filter(Boolean);
  if(candidates.length<2)return null;
  var medianHeight=median(candidates.map(function(item){return boxHeight(item.word.bbox)}))||12;
  var tolerance=Math.max(12,medianHeight*2.2),best=null;
  candidates.forEach(function(anchor){
    var band=candidates.filter(function(item){return Math.abs(item.y-anchor.y)<=tolerance});
    var chain=longestIncreasingChain(band);
    if(chain.length<2)return;
    var span=chain[chain.length-1].x-chain[0].x;
    if(span<Math.max(18,medianHeight*1.6))return;
    if(!best||chain.length>best.length||(chain.length===best.length&&span>best.span)){
      best={items:chain,length:chain.length,span:span};
    }
  });
  if(!best)return null;
  var grouped={};
  best.items.forEach(function(item){
    if(!grouped[item.day])grouped[item.day]=[];
    grouped[item.day].push(item.x);
  });
  var observed={};
  Object.keys(grouped).forEach(function(day){observed[day]=median(grouped[day])});
  var inferred=inferDayCenters(observed,maxDay);
  if(!inferred)return null;
  return {
    centers:inferred.centers,
    spacing:inferred.spacing,
    minX:Math.min.apply(null,Object.values(inferred.centers)),
    bottom:Math.max.apply(null,best.items.map(function(item){return Number(item.word.bbox.y1)}))+Math.max(2,Math.round(medianHeight*.35)),
    observedDays:Object.keys(observed).map(Number)
  };
}
function inferDayCentersFromShiftXs(rawXs,maxDay,absoluteAnchors){
  absoluteAnchors=absoluteAnchors||{};
  if(maxDay<28||maxDay>31||!Array.isArray(rawXs)||rawXs.length<12)return null;
  var sorted=rawXs.slice().map(Number).filter(Number.isFinite).sort(function(a,b){return a-b});
  if(sorted.length<12)return null;
  var span=sorted[sorted.length-1]-sorted[0];
  if(!(span>0))return null;

  var tolerance=Math.max(3,span/(maxDay*5.5));
  var clusters=[];
  sorted.forEach(function(x){
    var current=clusters[clusters.length-1];
    var mean=current&&current.length
      ?current.reduce(function(sum,v){return sum+v},0)/current.length
      :null;
    if(current&&mean!=null&&Math.abs(x-mean)<=tolerance)current.push(x);
    else clusters.push([x]);
  });

  var supported=clusters.filter(function(cluster){return cluster.length>=2});
  var gridClusters=supported.length>=Math.max(10,Math.floor(maxDay/3))?supported:clusters;
  var observed=gridClusters.map(function(cluster){
    return cluster.reduce(function(sum,v){return sum+v},0)/cluster.length;
  });
  if(observed.length<Math.max(12,Math.floor(maxDay/2)))return null;

  var candidates=[];
  for(var leading=0;leading<=3;leading++){
    for(var trailing=0;trailing<=3;trailing++){
      var firstDay=1+leading,lastDay=maxDay-trailing,daySpan=lastDay-firstDay;
      if(daySpan<=0)continue;
      var slope=(observed[observed.length-1]-observed[0])/daySpan;
      if(!(slope>=3))continue;
      var intercept=observed[0]-(firstDay-1)*slope;
      var assigned=observed.map(function(x){
        return Math.max(1,Math.min(maxDay,Math.round((x-intercept)/slope)+1));
      });
      var strictlyIncreasing=true;
      for(var i=1;i<assigned.length;i++)if(assigned[i]<=assigned[i-1]){strictlyIncreasing=false;break}
      if(!strictlyIncreasing)continue;
      var residual=observed.reduce(function(sum,x,index){
        return sum+Math.abs(x-(intercept+(assigned[index]-1)*slope));
      },0)/observed.length/slope;
      var anchorErrors=[];
      Object.keys(absoluteAnchors).forEach(function(dayKey){
        var day=Number(dayKey);
        if(!(day>=1&&day<=maxDay))return;
        (absoluteAnchors[dayKey]||[]).forEach(function(x){
          anchorErrors.push(Math.abs(x-(intercept+(day-1)*slope))/slope);
        });
      });
      var anchorResidual=anchorErrors.length
        ?anchorErrors.reduce(function(sum,v){return sum+v},0)/anchorErrors.length
        :0;
      residual+=(leading+trailing)*.025+anchorResidual*2.5;
      candidates.push({
        firstDay:firstDay,lastDay:lastDay,slope:slope,intercept:intercept,
        residual:residual,anchorResidual:anchorResidual,assigned:assigned
      });
    }
  }
  if(!candidates.length)return null;
  candidates.sort(function(a,b){return a.residual-b.residual});
  var best=candidates[0];
  if(best.residual>.30)return null;
  var hasAnchors=Object.keys(absoluteAnchors).some(function(day){
    return Array.isArray(absoluteAnchors[day])&&absoluteAnchors[day].length;
  });
  if(hasAnchors&&best.anchorResidual>.35)return null;
  var second=candidates[1];
  if(!hasAnchors&&second&&Math.abs(second.residual-best.residual)<.012&&
    (second.firstDay!==best.firstDay||second.lastDay!==best.lastDay))return null;
  var unique=Array.from(new Set(best.assigned));
  if(unique.length<Math.max(12,Math.floor(maxDay/2)))return null;
  if(Math.max.apply(null,unique)-Math.min.apply(null,unique)<maxDay-7)return null;

  var centers={};
  for(var day=1;day<=maxDay;day++)centers[day]=best.intercept+(day-1)*best.slope;
  return {centers:centers,spacing:best.slope};
}
function inferHeaderFromShiftColumns(lines,maxDay){
  var words=allWords(lines);
  var shiftWords=words.filter(function(word){return !!canonicalShift(word.text)});
  if(shiftWords.length<12)return null;
  var minShiftY=Math.min.apply(null,shiftWords.map(function(word){return centerY(word.bbox)}));
  var medianHeight=median(shiftWords.map(function(word){return boxHeight(word.bbox)}))||12;
  var shiftXs=shiftWords.map(function(word){return centerX(word.bbox)});
  var minShiftX=Math.min.apply(null,shiftXs),maxShiftX=Math.max.apply(null,shiftXs);
  var roughSpacing=maxDay>1?(maxShiftX-minShiftX)/(maxDay-1):0;
  var anchors={};
  words.forEach(function(word){
    var day=dayNumber(word.text,maxDay);
    if(day==null)return;
    var x=centerX(word.bbox),y=centerY(word.bbox);
    var inHeaderBand=y<=minShiftY+Math.max(10,medianHeight*1.25);
    var inGridBand=x>=minShiftX-roughSpacing*3&&x<=maxShiftX+roughSpacing*3;
    if(inHeaderBand&&inGridBand){
      if(!anchors[day])anchors[day]=[];
      anchors[day].push(x);
    }
  });
  var inferred=inferDayCentersFromShiftXs(
    shiftXs,
    maxDay,
    anchors
  );
  if(!inferred)return null;
  return {
    centers:inferred.centers,
    spacing:inferred.spacing,
    minX:Math.min.apply(null,Object.values(inferred.centers)),
    bottom:-Infinity,
    observedDays:[]
  };
}
function clusterByY(tokens,tolerance){
  var clusters=[];
  tokens.slice().sort(function(a,b){return centerY(a.bbox)-centerY(b.bbox)}).forEach(function(token){
    var y=centerY(token.bbox),best=null,bestDistance=Infinity;
    clusters.forEach(function(cluster){
      var avg=cluster.reduce(function(sum,item){return sum+centerY(item.bbox)},0)/cluster.length;
      var distance=Math.abs(avg-y);
      if(distance<bestDistance){bestDistance=distance;best=cluster}
    });
    if(best&&bestDistance<=tolerance)best.push(token);
    else clusters.push([token]);
  });
  return clusters;
}
function bestRowAlignment(xs,geometry){
  var centers=Object.values(geometry.centers||{}).map(Number).filter(Number.isFinite);
  var pivot=centers.length?centers.reduce(function(sum,v){return sum+v},0)/centers.length:0;
  if(xs.length<4||!(geometry.spacing>0))return {scale:1,offset:0,pivot:pivot};
  var best={scale:1,offset:0,pivot:pivot},bestScore=Infinity;
  for(var scaleStep=-6;scaleStep<=6;scaleStep++){
    var scale=1+scaleStep*.01;
    for(var offsetStep=-6;offsetStep<=6;offsetStep++){
      var offset=geometry.spacing*offsetStep*.05;
      var residual=xs.reduce(function(sum,x){
        var adjusted=pivot+(x-pivot)*scale+offset;
        var nearest=Math.min.apply(null,centers.map(function(center){return Math.abs(center-adjusted)}));
        return sum+nearest;
      },0)/xs.length/geometry.spacing;
      var penalty=Math.abs(scale-1)*.10+Math.abs(offset)/geometry.spacing*.015;
      var score=residual+penalty;
      if(score<bestScore){
        bestScore=score;
        best={scale:scale,offset:offset,pivot:pivot};
      }
    }
  }
  return bestScore<=.30?best:{scale:1,offset:0,pivot:pivot};
}
function mapShiftTokens(tokens,geometry){
  var dayShifts={},maxDistance=Math.max(12,geometry.spacing*.52);
  var shiftTokens=tokens.filter(function(token){
    return token&&token.bbox&&canonicalShift(token.text);
  });
  var alignment=bestRowAlignment(
    shiftTokens.map(function(token){return centerX(token.bbox)}),
    geometry
  );
  shiftTokens.forEach(function(token){
    var code=canonicalShift(token.text);
    var rawX=centerX(token.bbox);
    var x=alignment.pivot+(rawX-alignment.pivot)*alignment.scale+alignment.offset;
    var nearest=null,best=Infinity;
    Object.keys(geometry.centers).forEach(function(day){
      var distance=Math.abs(geometry.centers[day]-x);
      if(distance<best){best=distance;nearest=Number(day)}
    });
    if(nearest&&best<=maxDistance)dayShifts[nearest]=code;
  });
  return dayShifts;
}
function parseTokenRow(tokens,geometry){
  var shifts=tokens.filter(function(token){return token.bbox&&canonicalShift(token.text)});
  if(!shifts.length)return null;
  var firstShiftX=Math.min.apply(null,shifts.map(function(item){return centerX(item.bbox)}));
  var boundary=Math.min(firstShiftX,geometry.minX);
  var leftText=tokens.filter(function(token){
    return token.bbox&&centerX(token.bbox)<boundary&&!canonicalShift(token.text);
  }).sort(function(a,b){return centerX(a.bbox)-centerX(b.bbox)})
    .map(function(token){return token.text}).join(" ");
  var rowMatch=leftText.match(/^\s*(\d{1,3})[.)]?\s*/);
  var name=cleanName(leftText);
  if(!validName(name))return null;
  var dayShifts=mapShiftTokens(shifts,geometry);
  return {row:rowMatch?Number(rowMatch[1]):null,name:name,dayShifts:dayShifts};
}
function findRowAnchors(tokens,geometry,tolerance){
  var boundary=geometry.minX-geometry.spacing*.30;
  var left=tokens.filter(function(token){
    return token.bbox&&centerX(token.bbox)<boundary&&!canonicalShift(token.text);
  });
  return clusterByY(left,Math.max(7,tolerance*.9)).map(function(cluster){
    var sorted=cluster.slice().sort(function(a,b){return centerX(a.bbox)-centerX(b.bbox)});
    var text=sorted.map(function(token){return token.text}).join(" ");
    var rowMatch=text.match(/^\s*(\d{1,3})[.)]?\s*/);
    var name=cleanName(text);
    if(!validName(name))return null;
    return {
      row:rowMatch?Number(rowMatch[1]):null,
      name:name,
      y:cluster.reduce(function(sum,item){return sum+centerY(item.bbox)},0)/cluster.length
    };
  }).filter(Boolean).sort(function(a,b){return a.y-b.y});
}
function anchoredRows(tokens,geometry,tolerance){
  var anchors=findRowAnchors(tokens,geometry,tolerance);
  return anchors.map(function(anchor,index){
    var previous=anchors[index-1],next=anchors[index+1];
    var lower=previous?(previous.y+anchor.y)/2:anchor.y-tolerance*1.6;
    var upper=next?(next.y+anchor.y)/2:anchor.y+tolerance*1.6;
    var rowTokens=tokens.filter(function(token){
      var y=centerY(token.bbox);return y>=lower&&y<upper;
    });
    return {
      row:anchor.row,
      name:anchor.name,
      dayShifts:mapShiftTokens(rowTokens,geometry)
    };
  }).filter(function(row){
    return row.row!=null||Object.keys(row.dayShifts).length>0;
  });
}
function parseGeometry(blocks,maxDay){
  var lines=flattenLines(blocks);
  if(!lines.length)return [];
  var resolvedMaxDay=maxDay||31;
  var header=findHeader(lines,resolvedMaxDay)||inferHeaderFromShiftColumns(lines,resolvedMaxDay);
  if(!header)return [];
  var linesBelow=lines.filter(function(line){
    return line.bbox&&centerY(line.bbox)>header.bottom;
  });
  var tokens=allWords(linesBelow);
  if(!tokens.length)return [];
  var tolerance=Math.max(8,(median(tokens.map(function(token){return boxHeight(token.bbox)}))||12)*.85);
  var lineRows=linesBelow.map(function(line){return parseTokenRow(line.words||[],header)}).filter(Boolean);
  var clusterRows=clusterByY(tokens,tolerance).map(function(cluster){
    return parseTokenRow(cluster,header);
  }).filter(Boolean);
  var rows=anchoredRows(tokens,header,tolerance);
  return mergeRows(rows.concat(clusterRows,lineRows));
}
async function prepareImage(file,strong){
  if(typeof createImageBitmap!=="function")return file;
  var bitmap;
  try{
    bitmap=await createImageBitmap(file,{imageOrientation:"from-image"});
    var largest=Math.max(bitmap.width,bitmap.height);
    var longEdgeScale=4096/largest;
    var pixelScale=Math.sqrt(10000000/(bitmap.width*bitmap.height));
    var scale=Math.min(1,longEdgeScale,pixelScale);
    var canvas=document.createElement("canvas");
    canvas.width=Math.max(1,Math.round(bitmap.width*scale));
    canvas.height=Math.max(1,Math.round(bitmap.height*scale));
    var context=canvas.getContext("2d",{alpha:false,willReadFrequently:false});
    if(!context)return file;
    context.fillStyle="#fff";
    context.fillRect(0,0,canvas.width,canvas.height);
    context.filter=strong?"grayscale(1) contrast(1.34)":"contrast(1.15) saturate(.85)";
    context.drawImage(bitmap,0,0,canvas.width,canvas.height);
    context.filter="none";
    return await new Promise(function(resolve){
      canvas.toBlob(function(blob){resolve(blob||file)},"image/jpeg",strong ? .96 : .95);
    });
  }catch(error){
    return file;
  }finally{
    if(bitmap&&typeof bitmap.close==="function")bitmap.close();
  }
}
async function prepareStripe(file,startRatio,endRatio){
  if(typeof createImageBitmap!=="function")return file;
  var bitmap;
  try{
    bitmap=await createImageBitmap(file,{imageOrientation:"from-image"});
    var top=Math.max(0,Math.floor(bitmap.height*startRatio));
    var bottom=Math.min(bitmap.height,Math.ceil(bitmap.height*endRatio));
    var cropHeight=Math.max(1,bottom-top);
    var pixelScale=Math.sqrt(6500000/(bitmap.width*cropHeight));
    var edgeScale=5600/bitmap.width;
    var scale=Math.max(.18,Math.min(1.65,pixelScale,edgeScale));
    var canvas=document.createElement("canvas");
    canvas.width=Math.max(1,Math.round(bitmap.width*scale));
    canvas.height=Math.max(1,Math.round(cropHeight*scale));
    var context=canvas.getContext("2d",{alpha:false,willReadFrequently:false});
    if(!context)return file;
    context.fillStyle="#fff";
    context.fillRect(0,0,canvas.width,canvas.height);
    context.filter="grayscale(1) contrast(1.34)";
    context.drawImage(
      bitmap,
      0,top,bitmap.width,cropHeight,
      0,0,canvas.width,canvas.height
    );
    context.filter="none";
    return await new Promise(function(resolve){
      canvas.toBlob(function(blob){resolve(blob||file)},"image/jpeg",.96);
    });
  }catch(error){
    return file;
  }finally{
    if(bitmap&&typeof bitmap.close==="function")bitmap.close();
  }
}
async function getWorker(){
  if(workerPromise)return workerPromise;
  workerPromise=(async function(){
    var mod=await import("https://cdn.jsdelivr.net/npm/tesseract.js@6.0.1/dist/tesseract.esm.min.js");
    return mod.createWorker(["hrv","eng"],1,{logger:function(message){
      if(activeProgressCallback&&message&&typeof message.progress==="number"){
        activeProgressCallback(message.progress,message.status||"");
      }
    }});
  })();
  try{return await workerPromise}catch(error){workerPromise=null;throw error}
}
function parsedResult(result,monthHint){
  var text=result&&result.data?result.data.text:"";
  var month=detectMonth(text)||monthHint||null;
  var geometryRows=parseGeometry(result&&result.data?result.data.blocks:null,daysInMonth(month));
  var textRows=parseText(text);
  return {month:month,people:mergeRows(geometryRows.concat(textRows)),rawText:text};
}
function sparseResult(parsed){
  var rows=parsed&&Array.isArray(parsed.people)?parsed.people:[];
  if(!rows.length)return true;
  var mapped=rows.reduce(function(sum,row){return sum+Object.keys(row.dayShifts||{}).length},0);
  var expected=Math.min(daysInMonth(parsed.month),12);
  return mapped<Math.max(18,rows.length*expected);
}
function mergeRecognized(first,second){
  return {
    month:first.month||second.month,
    people:mergeRows((first.people||[]).concat(second.people||[])),
    rawText:(second.rawText||"").length>(first.rawText||"").length?second.rawText:first.rawText
  };
}
async function recognizeScheduleNow(file,onProgress){
  activeProgressCallback=onProgress||null;
  try{
    var worker=await getWorker();
    var source=await prepareImage(file,false);
    var first=parsedResult(await worker.recognize(source,{}, {text:true,blocks:true}));
    var needsDeep=sparseResult(first)||(first.people||[]).length<16;
    if(!needsDeep)return first;

    if(onProgress)onProgress(.78,"recovery");
    var recoverySource=await prepareImage(file,true);
    var second=parsedResult(
      await worker.recognize(recoverySource,{}, {text:true,blocks:true}),
      first.month
    );
    var merged=mergeRecognized(first,second);
    if(!sparseResult(merged)&&(merged.people||[]).length>=16)return merged;

    var stripes=[[0,.58],[.42,1]];
    for(var i=0;i<stripes.length;i++){
      if(onProgress)onProgress(.88+i*.05,"table-stripe-"+(i+1));
      var stripeSource=await prepareStripe(file,stripes[i][0],stripes[i][1]);
      var stripe=parsedResult(
        await worker.recognize(stripeSource,{}, {text:true,blocks:true}),
        merged.month
      );
      merged=mergeRecognized(merged,stripe);
    }
    return merged;
  }finally{
    activeProgressCallback=null;
  }
}
function recognizeSchedule(file,onProgress){
  recognitionChain=recognitionChain.catch(function(){}).then(function(){
    return recognizeScheduleNow(file,onProgress);
  });
  return recognitionChain;
}
window.RasporedWebOcr={
  recognizeSchedule:recognizeSchedule,
  parseText:parseText,
  parseGeometry:parseGeometry,
  detectMonth:detectMonth,
  inferDayCenters:inferDayCenters,
  inferDayCentersFromShiftXs:inferDayCentersFromShiftXs
};
})();