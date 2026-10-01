(function(){
"use strict";

function luminance(data,index){
  return Math.round((data[index]*299+data[index+1]*587+data[index+2]*114)/1000);
}
function groupCenters(values){
  if(!values.length)return [];
  var groups=[],current=[];
  values.slice().sort(function(a,b){return a-b}).forEach(function(value){
    if(current.length&&value>current[current.length-1]+1){
      groups.push(current);
      current=[];
    }
    current.push(value);
  });
  if(current.length)groups.push(current);
  return groups.filter(function(group){return group.length<=14}).map(function(group){
    return group[Math.floor(group.length/2)];
  });
}
function fitHorizontalGrid(centers){
  if(centers.length<10)return null;
  var sorted=Array.from(new Set(centers)).sort(function(a,b){return a-b});
  var gaps=[];
  for(var i=1;i<sorted.length;i++){
    var gap=sorted[i]-sorted[i-1];
    if(gap>=7&&gap<=80)gaps.push(gap);
  }
  if(gaps.length<6)return null;

  var frequencies={};
  gaps.forEach(function(gap){frequencies[gap]=(frequencies[gap]||0)+1});
  var dominant=Object.keys(frequencies).map(Number).filter(function(gap){return gap>=10})
    .sort(function(a,b){
      function localSupport(candidate){
        return Object.keys(frequencies).reduce(function(sum,key){
          var gap=Number(key);
          return sum+(Math.abs(gap-candidate)<=2?frequencies[key]:0);
        },0);
      }
      return localSupport(b)-localSupport(a)||(frequencies[b]||0)-(frequencies[a]||0);
    })[0];
  if(!dominant)return null;
  var seeds=[];
  for(var seed=dominant-2;seed<=dominant+2;seed++){
    if(seed>=8&&seed<=80)seeds.push(seed);
  }

  // Dense photographed grids rarely scale to an integer row spacing. Using
  // only integer seeds accumulates drift across 20–30 employees and can skip
  // otherwise perfectly visible rows. Add a fractional spacing derived from
  // the locally dominant gaps, plus a full-span estimate when enough centers
  // are available.
  var localGaps=gaps.filter(function(gap){return Math.abs(gap-dominant)<=2});
  if(localGaps.length){
    var averageGap=localGaps.reduce(function(sum,gap){return sum+gap},0)/localGaps.length;
    if(averageGap>=8&&averageGap<=80)seeds.push(averageGap);
  }
  if(sorted.length>=10){
    var spanGap=(sorted[sorted.length-1]-sorted[0])/(sorted.length-1);
    if(spanGap>=8&&spanGap<=80)seeds.push(spanGap);
  }
  seeds=seeds.filter(function(value,index,array){
    return array.findIndex(function(other){return Math.abs(other-value)<.01})===index;
  });

  var best=null;
  seeds.forEach(function(spacing){
    var tolerance=Math.max(3,spacing*.30);
    sorted.forEach(function(start){
      var matches=[],predicted=start,index=0,last=sorted[sorted.length-1];
      while(predicted<=last+tolerance&&index<64){
        var nearest=null,distance=Infinity;
        sorted.forEach(function(center){
          var diff=Math.abs(center-predicted);
          if(diff<distance){distance=diff;nearest=center}
        });
        if(nearest!=null&&distance<=tolerance&&!matches.some(function(pair){
          return pair[1]===nearest;
        })){
          matches.push([index,nearest]);
        }
        predicted+=spacing;
        index++;
      }
      if(matches.length<10)return;
      var span=matches[matches.length-1][0]-matches[0][0]+1;
      if(span<10)return;
      var coverage=matches.length/span;
      var residual=matches.reduce(function(sum,pair){
        return sum+Math.abs(pair[1]-(start+pair[0]*spacing));
      },0)/matches.length/spacing;
      var score=matches.length+coverage*5+span*.08-residual*2;
      if(!best||score>best.score){
        best={spacing:spacing,matches:matches,score:score,coverage:coverage};
      }
    });
  });
  return best&&best.matches.length>=10&&best.coverage>=.58?best:null;
}
function bridgeMask(mask,maxGap){
  var index=0;
  while(index<mask.length){
    if(mask[index]){index++;continue}
    var start=index;
    while(index<mask.length&&!mask[index])index++;
    var end=index-1;
    var bounded=start>0&&index<mask.length&&mask[start-1]&&mask[index];
    if(bounded&&end-start+1<=maxGap){
      for(var fill=start;fill<=end;fill++)mask[fill]=true;
    }
  }
}
function trueRuns(mask){
  var runs=[],index=0;
  while(index<mask.length){
    while(index<mask.length&&!mask[index])index++;
    if(index>=mask.length)break;
    var start=index;
    while(index<mask.length&&mask[index])index++;
    runs.push([start,index-1]);
  }
  return runs;
}
function detectGridBounds(bitmap){
  var maxEdge=Math.max(bitmap.width,bitmap.height);
  if(bitmap.width<700||bitmap.height<500||!maxEdge)return null;

  var scale=Math.min(1,1200/maxEdge);
  var width=Math.max(1,Math.round(bitmap.width*scale));
  var height=Math.max(1,Math.round(bitmap.height*scale));
  var canvas=document.createElement("canvas");
  canvas.width=width;
  canvas.height=height;
  var context=canvas.getContext("2d",{alpha:false,willReadFrequently:true});
  if(!context)return null;

  context.fillStyle="#fff";
  context.fillRect(0,0,width,height);
  context.drawImage(bitmap,0,0,width,height);
  var data=context.getImageData(0,0,width,height).data;
  var histogram=new Uint32Array(256),samples=0;
  var step=width*height>700000?4:2;
  for(var y=0;y<height;y+=step){
    for(var x=0;x<width;x+=step){
      histogram[luminance(data,(y*width+x)*4)]++;
      samples++;
    }
  }

  var target=Math.max(1,Math.round(samples*.15));
  var cumulative=0,percentile=80;
  for(var value=0;value<256;value++){
    cumulative+=histogram[value];
    if(cumulative>=target){percentile=value;break}
  }
  var threshold=Math.max(65,Math.min(125,percentile+20));
  function isDark(x,y){
    return luminance(data,(y*width+x)*4)<=threshold;
  }

  var xStart=Math.max(0,Math.min(width-1,Math.round(width*.04)));
  var xEnd=Math.max(xStart+1,Math.min(width,Math.round(width*.96)));
  var candidateRows=[];
  for(var row=0;row<height;row++){
    var count=0;
    for(var xx=xStart;xx<xEnd;xx++)if(isDark(xx,row))count++;
    if(count/(xEnd-xStart)>=.16)candidateRows.push(row);
  }

  var centers=groupCenters(candidateRows).filter(function(row){
    return row>=2&&row<height-2;
  });
  var grid=fitHorizontalGrid(centers);
  if(!grid)return null;

  var matched=grid.matches.map(function(pair){return pair[1]})
    .filter(function(value,index,array){return array.indexOf(value)===index})
    .sort(function(a,b){return a-b});
  var spacing=grid.spacing;
  var top=Math.max(0,Math.round(matched[0]-spacing*3));
  var bottom=Math.min(height,Math.round(matched[matched.length-1]+spacing*2.2));

  var support=new Float32Array(width);
  for(var sx=0;sx<width;sx++){
    var supported=0;
    matched.forEach(function(rowY){
      var hit=false;
      for(var yy=Math.max(0,rowY-1);yy<=Math.min(height-1,rowY+1);yy++){
        if(isDark(sx,yy)){hit=true;break}
      }
      if(hit)supported++;
    });
    support[sx]=supported/matched.length;
  }

  var mask=Array.from(support,function(value){return value>=.22});
  bridgeMask(mask,Math.max(8,Math.round(spacing*1.65)));
  var runs=trueRuns(mask);
  if(!runs.length)return null;
  runs.sort(function(a,b){return (b[1]-b[0])-(a[1]-a[0])});
  var widest=runs[0];
  if(widest[1]-widest[0]<width*.45)return null;

  var side=Math.round(spacing*1.4);
  var left=Math.max(0,widest[0]-side);
  var right=Math.min(width,widest[1]+side+1);

  // Horizontal lines in photographed schedules can be fragmented at every
  // cell boundary. In that case the widest horizontal-support run can start at
  // the day grid and accidentally cut off row numbers / employee names. Recover
  // the real outer table edges from long vertical rules, matching Android.
  var verticalCandidates=[];
  var verticalHeight=Math.max(1,bottom-top);
  for(var vx=0;vx<width;vx++){
    var hits=0;
    for(var vy=top;vy<bottom;vy++){
      if(isDark(vx,vy))hits++;
    }
    if(hits/verticalHeight>=.32)verticalCandidates.push(vx);
  }
  var verticalCenters=groupCenters(verticalCandidates).filter(function(x){
    return x>=2&&x<width-2;
  });
  if(verticalCenters.length>=8){
    var maxExtension=Math.round(width*.24);
    var leftEdges=verticalCenters.filter(function(x){
      return x<=widest[0]&&widest[0]-x<=maxExtension;
    });
    var rightEdges=verticalCenters.filter(function(x){
      return x>=widest[1]&&x-widest[1]<=maxExtension;
    });
    if(leftEdges.length){
      left=Math.min(left,Math.max(0,Math.min.apply(Math,leftEdges)-side));
    }
    if(rightEdges.length){
      right=Math.max(right,Math.min(width,Math.max.apply(Math,rightEdges)+side+1));
    }
  }

  if(right-left<width*.45||bottom-top<height*.25)return null;

  var scaleX=bitmap.width/width,scaleY=bitmap.height/height;
  return {
    left:Math.max(0,Math.round(left*scaleX)),
    top:Math.max(0,Math.round(top*scaleY)),
    right:Math.min(bitmap.width,Math.round(right*scaleX)),
    bottom:Math.min(bitmap.height,Math.round(bottom*scaleY))
  };
}

function detectEmployeeRowBandsFromBitmap(bitmap,rowsPerBand){
  var bounds=detectGridBounds(bitmap);
  if(!bounds)return [];
  var boundWidth=Math.max(1,bounds.right-bounds.left);
  var boundHeight=Math.max(1,bounds.bottom-bounds.top);
  if(boundWidth<400||boundHeight<220)return [];

  var scale=Math.min(1,1200/Math.max(boundWidth,boundHeight));
  var width=Math.max(1,Math.round(boundWidth*scale));
  var height=Math.max(1,Math.round(boundHeight*scale));
  var canvas=document.createElement("canvas");
  canvas.width=width;canvas.height=height;
  var context=canvas.getContext("2d",{alpha:false,willReadFrequently:true});
  if(!context)return [];
  context.fillStyle="#fff";
  context.fillRect(0,0,width,height);
  // Geometry detection must preserve thin 1–2 px table rules. Browser image
  // smoothing can blend sub-pixel horizontal lines into the background and
  // make a dense 27-row roster look incomplete after downscaling.
  context.imageSmoothingEnabled=false;
  context.drawImage(
    bitmap,
    bounds.left,bounds.top,boundWidth,boundHeight,
    0,0,width,height
  );
  var data=context.getImageData(0,0,width,height).data;
  var histogram=new Uint32Array(256),samples=0;
  var step=width*height>700000?4:2;
  for(var sy=0;sy<height;sy+=step){
    for(var sx=0;sx<width;sx+=step){
      histogram[luminance(data,(sy*width+sx)*4)]++;
      samples++;
    }
  }
  var target=Math.max(1,Math.round(samples*.15));
  var cumulative=0,percentile=80;
  for(var value=0;value<256;value++){
    cumulative+=histogram[value];
    if(cumulative>=target){percentile=value;break}
  }
  var threshold=Math.max(65,Math.min(125,percentile+20));
  function dark(x,y){return luminance(data,(y*width+x)*4)<=threshold}

  var xStart=Math.max(0,Math.min(width-1,Math.round(width*.015)));
  var xEnd=Math.max(xStart+1,Math.min(width,Math.round(width*.985)));
  var candidateRows=[];
  for(var y=0;y<height;y++){
    var count=0;
    for(var x=xStart;x<xEnd;x++)if(dark(x,y))count++;
    if(count/(xEnd-xStart)>=.15)candidateRows.push(y);
  }
  var centers=groupCenters(candidateRows).filter(function(y){
    return y>=1&&y<height-1;
  });
  var grid=fitHorizontalGrid(centers);
  if(!grid||grid.matches.length<10)return [];

  var matches=grid.matches.slice().sort(function(a,b){return a[0]-b[0]});
  var firstIndex=matches[0][0],lastIndex=matches[matches.length-1][0];
  if(lastIndex-firstIndex<8)return [];
  var origin=matches.reduce(function(sum,pair){
    return sum+(pair[1]-pair[0]*grid.spacing);
  },0)/matches.length;

  var predicted=[];
  for(var index=firstIndex;index<=lastIndex;index++){
    var y=Math.max(0,Math.min(height,Math.round(origin+index*grid.spacing)));
    if(predicted.indexOf(y)<0)predicted.push(y);
  }
  predicted.sort(function(a,b){return a-b});
  if(predicted.length<9)return [];

  var scaleY=boundHeight/height;
  var lines=predicted.map(function(localY){
    return Math.max(
      bounds.top,
      Math.min(bounds.bottom,Math.round(bounds.top+localY*scaleY))
    );
  }).filter(function(y,index,array){return index===0||y!==array[index-1]});
  if(lines.length<9)return [];

  var headerTop=lines[0];
  var headerBottom=Math.max(headerTop+1,lines[1]);
  var safeRows=Math.max(1,Math.min(6,Number(rowsPerBand)||4));
  var padding=Math.max(2,Math.round((headerBottom-headerTop)*.18));
  var bands=[],startLine=1;
  while(startLine<lines.length-1){
    var endLine=Math.min(lines.length-1,startLine+safeRows);
    var bodyTop=Math.max(headerBottom,lines[startLine]-padding);
    var bodyBottom=Math.min(bounds.bottom,lines[endLine]+padding);
    if(bodyBottom>bodyTop+2){
      bands.push({
        left:bounds.left,right:bounds.right,
        headerTop:headerTop,headerBottom:headerBottom,
        bodyTop:bodyTop,bodyBottom:bodyBottom
      });
    }
    startLine=endLine;
  }
  return bands;
}
async function detectEmployeeRowBands(file,rowsPerBand){
  if(typeof createImageBitmap!=="function")return [];
  var bitmap;
  try{
    bitmap=await createImageBitmap(file,{imageOrientation:"from-image"});
    return detectEmployeeRowBandsFromBitmap(bitmap,rowsPerBand);
  }catch(error){
    return [];
  }finally{
    if(bitmap&&typeof bitmap.close==="function")bitmap.close();
  }
}

async function cropScheduleTable(file){
  if(typeof createImageBitmap!=="function")return file;
  var bitmap;
  try{
    bitmap=await createImageBitmap(file,{imageOrientation:"from-image"});
    var bounds=detectGridBounds(bitmap);
    if(!bounds)return file;
    var width=Math.max(1,bounds.right-bounds.left);
    var height=Math.max(1,bounds.bottom-bounds.top);
    var widthRatio=width/bitmap.width;
    var heightRatio=height/bitmap.height;
    var areaRatio=widthRatio*heightRatio;
    // Never trade completeness for a tighter crop. Weak/glared grid lines can
    // make a partial 10–15-row rectangle look valid; in that case keep the
    // whole source so later stripe/focused passes can still recover every row.
    if(widthRatio<.60||heightRatio<.45||areaRatio>=.92)return file;

    var canvas=document.createElement("canvas");
    canvas.width=width;
    canvas.height=height;
    var context=canvas.getContext("2d",{alpha:false,willReadFrequently:false});
    if(!context)return file;
    context.fillStyle="#fff";
    context.fillRect(0,0,width,height);
    context.drawImage(
      bitmap,
      bounds.left,bounds.top,width,height,
      0,0,width,height
    );
    return await new Promise(function(resolve){
      canvas.toBlob(function(blob){resolve(blob||file)},"image/png");
    });
  }catch(error){
    return file;
  }finally{
    if(bitmap&&typeof bitmap.close==="function")bitmap.close();
  }
}

window.RasporedOcrTableCrop={
  cropScheduleTable:cropScheduleTable,
  detectGridBounds:detectGridBounds,
  detectEmployeeRowBands:detectEmployeeRowBands,
  detectEmployeeRowBandsFromBitmap:detectEmployeeRowBandsFromBitmap
};
})();