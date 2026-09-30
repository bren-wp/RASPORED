(function(){
"use strict";
var months=["Siječanj","Veljača","Ožujak","Travanj","Svibanj","Lipanj","Srpanj","Kolovoz","Rujan","Listopad","Studeni","Prosinac"];
var weekdays=["Ned","Pon","Uto","Sri","Čet","Pet","Sub"];
var state={route:"calendar",cursor:new Date(),selected:new Date(),schedule:{},scanPeople:[],scanSelected:-1,scanMonth:null,editRecognition:false,scanGeneration:0};
var appBound=false;
state.cursor=new Date(state.cursor.getFullYear(),state.cursor.getMonth(),1);
state.selected=new Date();

function icon(name,extra){
  var appBase=document.body&&document.body.dataset?document.body.dataset.base||"":"";
  var base=appBase+"/assets/brand/icons.svg#icon-"+name;
  return '<svg class="ui-icon '+(extra||"")+'" aria-hidden="true"><use href="'+base+'"></use></svg>';
}

function storageGet(key){
  return window.RasporedDataStore?window.RasporedDataStore.get(key):null;
}
function storageSet(key,value){
  return window.RasporedDataStore?window.RasporedDataStore.set(key,value):false;
}
function storageRemove(key){
  return window.RasporedDataStore?window.RasporedDataStore.remove(key):false;
}
function sanitizeSchedule(raw){
  var clean={};
  if(!raw||typeof raw!=="object"||Array.isArray(raw))return clean;
  Object.keys(raw).forEach(function(key){
    var code=raw[key];
    if(/^\d{4}-\d{2}-\d{2}$/.test(key)&&["D","N","GO","BO","PD","SD"].indexOf(code)>=0)clean[key]=code;
  });
  return clean;
}
function sanitizeScanPeople(items){
  if(!Array.isArray(items))return [];
  return items.slice(0,100).map(function(item){
    if(!item||typeof item.name!=="string")return null;
    var name=item.name.trim().replace(/\s+/g," ").slice(0,100);
    if(name.length<2)return null;
    var shifts={};
    var raw=item.dayShifts&&typeof item.dayShifts==="object"?item.dayShifts:{};
    Object.keys(raw).forEach(function(day){
      var number=Number(day),code=raw[day];
      if(Number.isInteger(number)&&number>=1&&number<=31&&["D","N","GO","BO","PD","SD"].indexOf(code)>=0)shifts[number]=code;
    });
    return {row:Number.isInteger(item.row)?item.row:null,name:name,dayShifts:shifts};
  }).filter(Boolean);
}
function configureProfile(){
  var saved="";
  saved=(storageGet("raspored.profile.name")||"").trim().slice(0,80);
  var name=saved;
  var display=name||"Korisnik";
  var parts=display.split(/\s+/).filter(Boolean);
  var initials=parts.slice(0,2).map(function(x){return x.charAt(0).toUpperCase()}).join("")||"K";
  var nameEl=document.getElementById("profileName"),initialEl=document.getElementById("profileInitials"),welcome=document.getElementById("welcomeTitle"),input=document.getElementById("profileNameInput");
  if(nameEl)nameEl.textContent=display;
  if(initialEl)initialEl.textContent=initials;
  if(welcome){
    if(name&&name!=="Korisnik")welcome.textContent="Dobro došao, "+parts[0]+"!";
    else welcome.textContent="Dobro došao!";
  }
  if(input&&document.activeElement!==input)input.value=name;
}
function loadColleagues(){
  try{
    var raw=JSON.parse(storageGet("raspored.colleagues.v1")||"[]");
    return Array.isArray(raw)?raw.filter(function(x){return x&&typeof x.name==="string"}).slice(0,30):[];
  }catch(e){return []}
}
function saveColleagues(items){return storageSet("raspored.colleagues.v1",JSON.stringify(items.slice(0,30)))}
function loadTeamMembers(){
  try{
    var raw=JSON.parse(storageGet("raspored.team.v1")||"[]");
    return Array.isArray(raw)?raw.filter(function(x){return x&&typeof x.name==="string"&&x.schedule&&typeof x.schedule==="object"}).slice(0,100):[];
  }catch(e){return []}
}
function saveTeamMembers(items){return storageSet("raspored.team.v1",JSON.stringify(items.slice(0,100)))}
function isManagerAccount(){return !!(window.RasporedAuth&&window.RasporedAuth.isManager&&window.RasporedAuth.isManager())}
function authSnapshot(){return window.RasporedAuth&&window.RasporedAuth.snapshot?window.RasporedAuth.snapshot():{authenticated:false,account:null}}
function normalizePersonName(value){
  return String(value||"").toLocaleUpperCase("hr-HR").normalize("NFD").replace(/[\u0300-\u036f]/g,"")
    .replace(/Đ/g,"D").replace(/[^A-Z0-9 ]/g," ").replace(/\s+/g," ").trim()
    .split(" ").filter(Boolean).sort().join(" ");
}
function individualAccountName(){
  var auth=authSnapshot();
  if(!auth.authenticated||!auth.account||auth.account.accountType==="manager")return "";
  return ((auth.account.firstName||"")+" "+(auth.account.lastName||"")).trim();
}
function syncAuthenticatedProfile(){
  var auth=authSnapshot(),input=document.getElementById("profileNameInput"),saveBtn=document.getElementById("saveProfileBtn"),help=document.getElementById("profileHelp");
  if(auth.authenticated&&auth.account){
    var name=((auth.account.firstName||"")+" "+(auth.account.lastName||"")).trim().replace(/\s+/g," ").slice(0,80);
    if(input){input.value=name;input.readOnly=true}
    if(saveBtn)saveBtn.hidden=true;
    if(help)help.textContent="Prijavljen profil koristi ime i prezime iz korisničkog računa. Promjena lokalnog imena nije dopuštena dok je račun prijavljen.";
    if(name&&window.RasporedDataStore&&window.RasporedDataStore.isAvailable&&window.RasporedDataStore.isAvailable()){
      if((storageGet("raspored.profile.name")||"")!==name)storageSet("raspored.profile.name",name);
    }
    configureProfile();
    return;
  }
  if(input)input.readOnly=false;
  if(saveBtn)saveBtn.hidden=false;
  if(help)help.textContent="Bez računa ime se sprema u ovoj instalaciji. Nakon prijave koristi se ime i prezime iz korisničkog računa.";
}
function scanPersonAllowed(person){
  var expected=individualAccountName();
  return !expected||normalizePersonName(expected)===normalizePersonName(person&&person.name);
}
function renderTeamMembers(){
  var card=document.getElementById("teamSchedulesCard"),list=document.getElementById("teamMembersList");
  if(!card||!list)return;
  var manager=isManagerAccount(),items=loadTeamMembers();
  card.hidden=!manager;
  if(!manager)return;
  if(!items.length){
    list.innerHTML='<div class="colleagues-empty">'+icon("users")+'<h3>Nema uvezenih rasporeda tima</h3><p>Skeniraj tablicu s više djelatnika i odaberi “Uvezi sve djelatnike u tim”.</p></div>';
    return;
  }
  list.innerHTML=items.map(function(item){
    var dates=Object.keys(sanitizeSchedule(item.schedule)),monthsSeen={};
    dates.forEach(function(date){monthsSeen[date.slice(0,7)]=true});
    var initials=item.name.split(/\s+/).filter(Boolean).slice(0,2).map(function(x){return x.charAt(0).toUpperCase()}).join("");
    return '<article class="team-member-row"><span class="colleague-avatar">'+escapeHtml(initials||"T")+'</span><span><b>'+escapeHtml(item.name)+'</b><small>'+dates.length+' spremljenih oznaka · '+Object.keys(monthsSeen).length+' mj.</small></span><span class="team-member-state">Odvojeni raspored</span></article>';
  }).join("");
}
function importScannedTeamSchedules(){
  if(!isManagerAccount()){toast("Uvoz cijelog tima dostupan je prijavljenom voditelju.");return}
  if(!state.scanPeople.length){toast("Najprije skeniraj raspored.");return}
  var target=scanTargetMonth(),y=target.getFullYear(),m=target.getMonth(),days=new Date(y,m+1,0).getDate();
  var members=loadTeamMembers(),byName={};
  members.forEach(function(item,index){byName[item.name.toLocaleLowerCase("hr-HR")]=index});
  var imported=0,skipped=0;
  state.scanPeople.forEach(function(person){
    var recognized=Object.keys(person.dayShifts||{}).filter(function(day){
      var n=Number(day),code=person.dayShifts[day];
      return n>=1&&n<=days&&["D","N","GO","BO","PD","SD"].indexOf(code)>=0;
    });
    if(!recognized.length){skipped++;return}
    var key=person.name.toLocaleLowerCase("hr-HR"),index=byName[key],member=index===undefined?{name:person.name,note:"",schedule:{}}:members[index];
    member.schedule=sanitizeSchedule(member.schedule);
    for(var day=1;day<=days;day++)delete member.schedule[iso(new Date(y,m,day))];
    recognized.forEach(function(day){
      var n=Number(day),code=person.dayShifts[day];
      member.schedule[iso(new Date(y,m,n))]=code;
    });
    if(index===undefined){byName[key]=members.length;members.push(member)}
    imported++;
  });
  if(!imported){toast("Nijedan raspored nema dovoljno pouzdanih oznaka dana za uvoz.");return}
  if(!saveTeamMembers(members)){toast("Rasporede tima nije moguće spremiti u storage/data.");return}
  renderTeamMembers();
  toast("Uvezeni su rasporedi za "+imported+" djelatnika"+(skipped?" · preskočeno bez oznaka: "+skipped:"")+".");
  route("colleagues");
}
function renderColleagues(){
  var el=document.getElementById("colleaguesList");if(!el)return;
  var items=loadColleagues();
  if(!items.length){
    el.innerHTML='<div class="colleagues-empty">'+icon("users")+'<h2>Još nema dodanih kolega</h2><p>Dodaj kolegu kako bi njegov raspored bio brzo dostupan na jednom mjestu.</p></div>';
    return;
  }
  el.innerHTML=items.map(function(item,index){
    var initials=item.name.split(/\s+/).filter(Boolean).slice(0,2).map(function(x){return x.charAt(0).toUpperCase()}).join("");
    return '<article class="colleague-row"><span class="colleague-avatar">'+escapeHtml(initials||"K")+'</span><span><b>'+escapeHtml(item.name)+'</b><small>'+escapeHtml(item.note||"Bez napomene")+'</small></span><button type="button" class="icon-btn" data-remove-colleague="'+index+'" aria-label="Ukloni '+escapeHtml(item.name)+'">×</button></article>';
  }).join("");
}
function addColleague(){
  var dialog=document.getElementById("colleagueDialog"),name=document.getElementById("colleagueNameInput"),note=document.getElementById("colleagueNoteInput");
  if(!dialog)return;
  if(name)name.value="";if(note)note.value="";
  dialog.showModal();
  setTimeout(function(){if(name)name.focus()},0);
}
function submitColleague(){
  var dialog=document.getElementById("colleagueDialog"),nameInput=document.getElementById("colleagueNameInput"),noteInput=document.getElementById("colleagueNoteInput");
  var name=(nameInput?nameInput.value:"").trim().replace(/\s+/g," ").slice(0,80);
  var note=(noteInput?noteInput.value:"").trim().replace(/\s+/g," ").slice(0,120);
  if(name.length<2){toast("Unesi valjano ime i prezime.");if(nameInput)nameInput.focus();return false}
  var items=loadColleagues();
  if(items.some(function(x){return x.name.toLocaleLowerCase("hr-HR")===name.toLocaleLowerCase("hr-HR")})){toast("Kolega je već dodan.");return false}
  items.push({name:name,note:note||"Bez napomene"});
  if(!saveColleagues(items)){toast("Podatke nije moguće spremiti u storage/data.");return false}
  renderColleagues();if(dialog)dialog.close();toast("Kolega je dodan.");return true;
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
var SEARCH_ROUTES=[
  {label:"Početna",route:"home",keywords:"početna pocetna naslovnica"},
  {label:"Kalendar",route:"calendar",keywords:"kalendar raspored smjene"},
  {label:"Skeniraj raspored",route:"scan",keywords:"skeniraj scan ocr fotografija uvezi"},
  {label:"Statistika",route:"stats",keywords:"statistika saldo izvještaji izvjestaji"},
  {label:"Okvirna plaća",route:"payroll",keywords:"plaća placa bruto koeficijent bod osnovica"},
  {label:"Evidencija sati",route:"hours",keywords:"sati evidencija ulaz izlaz"},
  {label:"Kolege",route:"colleagues",keywords:"kolege djelatnici osobe"},
  {label:"Postavke",route:"settings",keywords:"postavke profil tema izgled"}
];
function renderSearchResults(query){
  var list=document.getElementById("searchResults");if(!list)return;
  var q=(query||"").trim().toLocaleLowerCase("hr-HR");
  var matches=SEARCH_ROUTES.filter(function(item){return !q||item.label.toLocaleLowerCase("hr-HR").includes(q)||item.keywords.includes(q)}).slice(0,7);
  list.innerHTML=matches.map(function(item){return '<button type="button" data-search-route="'+item.route+'">'+icon(item.route==="home"?"home":item.route==="calendar"?"calendar":item.route==="scan"?"camera":item.route==="stats"?"chart":item.route==="payroll"?"scale":item.route==="hours"?"clock":item.route==="colleagues"?"users":"settings")+'<span><b>'+item.label+'</b><small>Otvori '+item.label.toLocaleLowerCase("hr-HR")+'</small></span>'+icon("chevron-right")+'</button>'}).join("")||'<p class="search-empty">Nema rezultata.</p>';
}
function openSearch(){
  var dialog=document.getElementById("searchDialog"),input=document.getElementById("searchInput");
  if(!dialog)return;
  if(input)input.value="";renderSearchResults("");dialog.showModal();
  setTimeout(function(){if(input)input.focus()},0);
}
function saveScanSession(){
  storageSet("raspored.scan.v1",JSON.stringify({
    people:state.scanPeople,
    selected:state.scanSelected,
    month:state.scanMonth
  }));
}
function loadScanSession(){
  try{
    var raw=JSON.parse(storageGet("raspored.scan.v1")||"null");
    if(raw&&Array.isArray(raw.people)){
      state.scanPeople=sanitizeScanPeople(raw.people);
      state.scanSelected=Number.isInteger(raw.selected)?raw.selected:-1;
      state.scanMonth=raw.month&&Number.isInteger(raw.month.year)&&Number.isInteger(raw.month.month)?raw.month:null;
    }
  }catch(e){}
}
function releaseScanPreview(){
  var img=document.getElementById("scanPreviewImage"),preview=document.getElementById("scanPreview");
  if(img&&img.dataset.objectUrl){
    URL.revokeObjectURL(img.dataset.objectUrl);
    delete img.dataset.objectUrl;
  }
  if(img)img.removeAttribute("src");
  if(preview)preview.classList.remove("has-image");
}
function clearScanSession(){
  state.scanGeneration++;
  state.scanPeople=[];state.scanSelected=-1;state.scanMonth=null;state.editRecognition=false;
  releaseScanPreview();
  storageRemove("raspored.scan.v1");
}
function scanTargetMonth(){
  if(state.scanMonth)return new Date(state.scanMonth.year,state.scanMonth.month-1,1);
  return new Date(state.cursor.getFullYear(),state.cursor.getMonth(),1);
}
function cycleScanCode(code){
  var order=["","D","N","GO","BO","PD","SD"],index=order.indexOf(code);
  return order[(index<0?0:index+1)%order.length];
}
function scanPerson(){
  return state.scanSelected>=0?state.scanPeople[state.scanSelected]||null:null;
}
function renderScanPersonPicker(){
  var button=document.getElementById("scanPersonButton"),label=document.getElementById("scanPersonLabel"),menu=document.getElementById("scanPersonMenu"),status=document.getElementById("recognitionStatus"),saveBtn=document.getElementById("saveSchedule"),monthLabel=document.getElementById("scanMonthLabel");
  if(!button||!label||!menu)return;
  var expectedName=individualAccountName();
  if(expectedName){
    var matched=state.scanPeople.findIndex(function(item){return scanPersonAllowed(item)});
    if(matched>=0&&(!scanPerson()||!scanPersonAllowed(scanPerson())))state.scanSelected=matched;
    if(matched<0)state.scanSelected=-1;
  }
  var person=scanPerson();
  label.textContent=person?((person.row?person.row+". ":"")+person.name):"Odaberi ime i prezime";
  button.disabled=state.scanPeople.length===0;
  menu.innerHTML=state.scanPeople.map(function(item,index){
    var allowed=scanPersonAllowed(item);
    var count=Object.keys(item.dayShifts||{}).length;
    return '<button type="button" role="option" aria-selected="'+(index===state.scanSelected?'true':'false')+'" data-scan-person="'+index+'" '+(allowed?'':'disabled')+'><b>'+(item.row?item.row+". ":"")+escapeHtml(item.name)+'</b><small>'+count+' prepoznatih dana · '+(allowed?'Samo ovaj raspored bit će uvezen':'Račun dopušta uvoz samo vlastitog rasporeda')+'</small></button>';
  }).join("");
  if(status){
    var selectedCount=person?Object.keys(person.dayShifts||{}).length:0;
    if(expectedName&&!person&&state.scanPeople.length)status.textContent="Ime s računa nije pouzdano pronađeno u skeniranom rasporedu.";
    else if(person&&!selectedCount)status.textContent="Osoba je prepoznata, ali nijedan datum nije dovoljno pouzdano mapiran. Uključi Uredi i unesi raspored prije spremanja.";
    else status.textContent=person?"✓ Odabrana 1 osoba · "+selectedCount+" dana":(state.scanPeople.length>1?"Odaberi jednu osobu":state.scanPeople.length===1?"Provjeri prepoznatu osobu":"Odaberi osobu");
  }
  if(monthLabel){
    var target=scanTargetMonth();
    monthLabel.textContent=months[target.getMonth()]+" "+target.getFullYear()+".";
  }
  if(saveBtn)saveBtn.disabled=!person||!scanPersonAllowed(person)||Object.keys(person.dayShifts||{}).length===0;
  var teamBtn=document.getElementById("saveTeamSchedules"),teamNote=document.getElementById("teamImportNote");
  var manager=isManagerAccount();
  if(teamBtn){teamBtn.hidden=!(manager&&state.scanPeople.length>0);teamBtn.disabled=state.scanPeople.length===0}
  if(teamNote)teamNote.hidden=!(manager&&state.scanPeople.length>1);
}
function selectedScanSchedule(){
  var person=scanPerson();return person&&person.dayShifts?person.dayShifts:null;
}
function importSelectedScanSchedule(){
  var person=scanPerson();
  if(!person){toast("Odaberi ime i prezime jedne osobe čiji raspored želiš uvesti.");return}
  if(!scanPersonAllowed(person)){toast("Ovaj račun može uvesti samo vlastiti raspored.");return}
  if(!Object.keys(person.dayShifts||{}).length){toast("Prije spremanja potvrdi barem jedan dan rasporeda.");return}
  var target=scanTargetMonth(),y=target.getFullYear(),m=target.getMonth(),days=new Date(y,m+1,0).getDate();
  for(var day=1;day<=days;day++)delete state.schedule[iso(new Date(y,m,day))];
  Object.keys(person.dayShifts||{}).forEach(function(day){
    var n=Number(day),code=person.dayShifts[day];
    if(n>=1&&n<=days&&["D","N","GO","BO","PD","SD"].indexOf(code)>=0)state.schedule[iso(new Date(y,m,n))]=code;
  });
  state.cursor=new Date(y,m,1);state.selected=new Date(y,m,1);
  if(!save()){loadSchedule();renderAll();toast("Raspored nije spremljen u storage/data.");return}
  clearScanSession();renderAll();toast("Uvezen je samo raspored za "+person.name+".");route("calendar");
}
function iso(d){return d.getFullYear()+"-"+String(d.getMonth()+1).padStart(2,"0")+"-"+String(d.getDate()).padStart(2,"0")}
function easter(y){var a=y%19,b=Math.floor(y/100),c=y%100,d=Math.floor(b/4),e=b%4,f=Math.floor((b+8)/25),g=Math.floor((b-f+1)/3),h=(19*a+b-d-g+15)%30,i=Math.floor(c/4),k=c%4,l=(32+2*e+2*i-h-k)%7,m=Math.floor((a+11*h+22*l)/451),mo=Math.floor((h+l-7*m+114)/31)-1,da=((h+l-7*m+114)%31)+1;return new Date(y,mo,da)}
function addDays(d,n){var x=new Date(d);x.setDate(x.getDate()+n);return x}
function holidays(y){var map={};function add(m,d,n){map[y+"-"+String(m).padStart(2,"0")+"-"+String(d).padStart(2,"0")]=n}add(1,1,"Nova godina");add(1,6,"Bogojavljenje");add(5,1,"Praznik rada");add(5,30,"Dan državnosti");add(6,22,"Dan antifašističke borbe");add(8,5,"Dan pobjede i domovinske zahvalnosti i Dan hrvatskih branitelja");add(8,15,"Velika Gospa");add(11,1,"Svi sveti");add(11,18,"Dan sjećanja na žrtve Domovinskog rata");add(12,25,"Božić");add(12,26,"Sveti Stjepan");var e=easter(y);map[iso(e)]="Uskrs";map[iso(addDays(e,1))]="Uskrsni ponedjeljak";map[iso(addDays(e,60))]="Tijelovo";return map}
function loadSchedule(){try{state.schedule=sanitizeSchedule(JSON.parse(storageGet("raspored.schedule")||"{}"))}catch(e){state.schedule={}}}
function save(){return storageSet("raspored.schedule",JSON.stringify(state.schedule))}
function appNow(){return new Date()}
function punctuatedDate(date,options){
  return date.toLocaleDateString("hr-HR",options).replace(/[.\s]+$/,"")+".";
}
function loadTimeEntries(){
  try{
    var raw=JSON.parse(storageGet("raspored.timeEntries.v1")||"[]");
    if(!Array.isArray(raw))return [];
    return raw.filter(function(x){
      return x&&/^\d{4}-\d{2}-\d{2}$/.test(x.date||"")&&/^\d{2}:\d{2}$/.test(x.in||"")&&
        (!x.out||/^\d{2}:\d{2}$/.test(x.out))&&
        (x.startedAt==null||Number.isFinite(Number(x.startedAt)))&&
        (x.endedAt==null||Number.isFinite(Number(x.endedAt)));
    }).map(function(x){
      x.note=typeof x.note==="string"?x.note.slice(0,500):"";
      return x;
    });
  }catch(e){return []}
}
function saveTimeEntries(entries){return storageSet("raspored.timeEntries.v1",JSON.stringify(entries.slice(-3000)))}
function hhmm(d){return String(d.getHours()).padStart(2,"0")+":"+String(d.getMinutes()).padStart(2,"0")}
function durationMinutes(entry,now){
  if(!entry||!entry.in)return 0;
  var startedAt=entry.startedAt==null?NaN:Number(entry.startedAt);
  var endedAt=entry.endedAt==null?NaN:Number(entry.endedAt);
  if(Number.isFinite(startedAt)){
    var finish=Number.isFinite(endedAt)?endedAt:now.getTime();
    return Math.max(0,Math.round((finish-startedAt)/60000));
  }
  function minutes(v){var p=v.split(":").map(Number);return p[0]*60+p[1]}
  var start=minutes(entry.in),end=entry.out?minutes(entry.out):(now.getHours()*60+now.getMinutes());
  if(end<start)end+=24*60;
  return Math.max(0,end-start);
}
function durationLabel(mins){return Math.floor(mins/60)+"h "+String(mins%60).padStart(2,"0")+"min"}
function activeTimeEntry(entries){
  for(var i=entries.length-1;i>=0;i--)if(!entries[i].out)return entries[i];
  return null;
}
function currentTimeEntry(entries,dateKey){for(var i=entries.length-1;i>=0;i--){if(entries[i].date===dateKey&&!entries[i].out)return entries[i]}return null}
function latestTimeEntry(entries,dateKey){for(var i=entries.length-1;i>=0;i--){if(entries[i].date===dateKey)return entries[i]}return null}
function shiftMeta(code){return {D:{name:"Dnevna smjena",time:"07:00 – 19:00 (12h)",hours:12},N:{name:"Noćna smjena",time:"19:00 – 07:00 (12h)",hours:12},GO:{name:"Godišnji odmor",time:"—",hours:0},BO:{name:"Bolovanje",time:"—",hours:0},PD:{name:"Plaćeni dopust",time:"—",hours:0},SD:{name:"Slobodan dan",time:"—",hours:0}}[code]||{name:"Slobodno",time:"—",hours:0}}
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
function completedEvidenceInterval(entry){
  if(!entry||(!entry.out&&entry.endedAt==null))return null;
  var start=entry.startedAt==null?NaN:Number(entry.startedAt);
  if(!Number.isFinite(start)&&entry.date&&entry.in)start=new Date(entry.date+"T"+entry.in+":00").getTime();
  if(!Number.isFinite(start))return null;
  var end=entry.endedAt==null?NaN:Number(entry.endedAt);
  if(!Number.isFinite(end)&&entry.out&&entry.date){
    end=new Date(entry.date+"T"+entry.out+":00").getTime();
    if(end<=start)end+=24*60*60*1000;
  }
  if(!Number.isFinite(end)||end<=start)return null;
  return {start:start,end:Math.min(end,start+36*60*60*1000)};
}
function overlapMinutes(start,end,windowStart,windowEnd){
  var clippedStart=Math.max(start,windowStart),clippedEnd=Math.min(end,windowEnd);
  return clippedEnd>clippedStart?Math.round((clippedEnd-clippedStart)/60000):0;
}
function nightMinutesForDaySegment(start,end,dayDate){
  var y=dayDate.getFullYear(),m=dayDate.getMonth(),d=dayDate.getDate();
  return overlapMinutes(start,end,new Date(y,m,d,0,0,0).getTime(),new Date(y,m,d,6,0,0).getTime())+
    overlapMinutes(start,end,new Date(y,m,d,22,0,0).getTime(),new Date(y,m,d+1,0,0,0).getTime());
}
function monthData(y,m){
  var out={
    worked:0,workedMinutes:0,dayMinutes:0,night:0,nightMinutes:0,otherMinutes:0,
    sat:0,sun:0,holidays:0,satMinutes:0,sunMinutes:0,holidayMinutes:0,weekendHolidayMinutes:0,
    go:0,bo:0,pd:0,sd:0,planned:0,balance:0,balanceMinutes:0,
    counts:{D:0,N:0,GO:0,BO:0,PD:0,SD:0},
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
    else if(code==="PD")out.pd=(out.pd||0)+1;
    else if(code==="SD")out.sd=(out.sd||0)+1;
  }

  var monthStart=new Date(y,m,1,0,0,0).getTime(),monthEnd=new Date(y,m+1,1,0,0,0).getTime();
  loadTimeEntries().forEach(function(entry){
    var span=completedEvidenceInterval(entry);
    if(!span||span.start>=monthEnd||span.end<=monthStart)return;
    var cursor=Math.max(span.start,monthStart),end=Math.min(span.end,monthEnd);
    while(cursor<end){
      var current=new Date(cursor),segmentDate=new Date(current.getFullYear(),current.getMonth(),current.getDate(),0,0,0);
      var nextDay=new Date(segmentDate.getFullYear(),segmentDate.getMonth(),segmentDate.getDate()+1,0,0,0).getTime();
      var segmentEnd=Math.min(end,nextDay);
      var mins=Math.round((segmentEnd-cursor)/60000);
      if(mins<=0){cursor=segmentEnd;continue}

      var nightMins=nightMinutesForDaySegment(cursor,segmentEnd,segmentDate);
      var dayMins=Math.max(0,mins-nightMins);
      var dateKey=iso(segmentDate);

      out.workedMinutes+=mins;
      out.dayMinutes+=dayMins;
      out.nightMinutes+=nightMins;
      if(segmentDate.getDay()===6)out.satMinutes+=mins;
      if(segmentDate.getDay()===0)out.sunMinutes+=mins;
      if(hm[dateKey])out.holidayMinutes+=mins;
      if(segmentDate.getDay()===0||segmentDate.getDay()===6||hm[dateKey])out.weekendHolidayMinutes+=mins;

      wi=Math.min(4,Math.floor((segmentDate.getDate()-1)/7));
      out.weeks[wi].d+=dayMins/60;
      out.weeks[wi].n+=nightMins/60;
      cursor=segmentEnd;
    }
  });

  out.otherMinutes=Math.max(0,out.workedMinutes-out.dayMinutes-out.nightMinutes);
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
function currentShiftFor(date){
  var now=appNow(),today=new Date(date.getFullYear(),date.getMonth(),date.getDate());
  if(now.getHours()<7){
    var previous=addDays(today,-1),previousCode=state.schedule[iso(previous)];
    if(previousCode==="N")return {date:previous,code:"N"};
  }
  var code=state.schedule[iso(today)];
  return code?{date:today,code:code}:null;
}
function shiftStatus(date,code){
  if(code!=="D"&&code!=="N")return code?"Danas":"Nema smjene";
  var now=appNow(),start=new Date(date),end=new Date(date);
  if(code==="D"){start.setHours(7,0,0,0);end.setHours(19,0,0,0)}
  else{start.setHours(19,0,0,0);end=addDays(start,1);end.setHours(7,0,0,0)}
  if(now<start){
    var mins=Math.max(0,Math.round((start-now)/60000));
    return "Za "+Math.floor(mins/60)+"h "+String(mins%60).padStart(2,"0")+"min";
  }
  if(now<end)return "U tijeku";
  return "Završeno";
}
function upcomingShiftNotification(){
  var start=appNow();
  for(var i=0;i<31;i++){
    var date=addDays(start,i),code=state.schedule[iso(date)];
    if(code==="D"||code==="N")return {date:date,code:code,key:iso(date)+"-"+code};
  }
  return null;
}
function renderNotifications(){
  var item=upcomingShiftNotification(),textEl=document.getElementById("notificationText"),dot=document.getElementById("notificationDot");
  if(!textEl||!dot)return;
  if(!item){
    textEl.textContent="Nema novih obavijesti.";
    dot.hidden=true;
    return;
  }
  var meta=shiftMeta(item.code),dateLabel=item.date.toLocaleDateString("hr-HR",{weekday:"long",day:"numeric",month:"long"});
  textEl.textContent="Sljedeća smjena: "+dateLabel+" · "+meta.name+" · "+meta.time+".";
  var readKey="";
  readKey=storageGet("raspored.notifications.readKey")||"";
  dot.hidden=readKey===item.key;
}
function markNotificationsRead(){
  var item=upcomingShiftNotification();
  if(item)storageSet("raspored.notifications.readKey",item.key);
  renderNotifications();
}
function renderMobileHome(){
  var date=appNow(),entry=currentShiftFor(date),code=entry?entry.code:null,current=shiftMeta(code),data=monthData(date.getFullYear(),date.getMonth());
  var title=document.getElementById("mobileTodayTitle");
  if(title){var dateText=punctuatedDate(date,{weekday:"long",day:"2-digit",month:"2-digit",year:"numeric"});title.textContent=dateText.charAt(0).toUpperCase()+dateText.slice(1)}
  var currentEl=document.getElementById("mobileCurrentShift");
  if(currentEl){currentEl.innerHTML='<button type="button" class="mobile-shift-card-head mobile-shift-card-head--button" data-route-dynamic="calendar"><h2>Današnja smjena</h2>'+icon("chevron-right")+'</button><div class="mobile-shift-card-body">'+(code?'<i class="shift '+code.toLowerCase()+'">'+code+'</i>':'<i class="shift">—</i>')+'<span class="mobile-shift-copy"><b>'+current.name+'</b><small>'+current.time+'</small></span>'+(code?'<span class="shift-countdown">'+shiftStatus(entry?entry.date:date,code)+'</span>':'')+'</div><div class="mobile-shift-info"><div>'+icon("clock")+'<span>Radno vrijeme</span><b>'+(current.hours?current.hours+"h":"—")+'</b></div><button type="button" class="mobile-shift-info-action" data-route-dynamic="hours">'+icon("check")+'<span>Evidentiraj ulaz/izlaz</span>'+icon("chevron-right")+'</button><button type="button" class="mobile-shift-info-action" data-route-dynamic="hours">'+icon("note")+'<span>Bilješka</span>'+icon("chevron-right")+'</button></div>'}
  var next=null;
  for(var i=1;i<=62&&!next;i++){var nd=addDays(date,i),nc=state.schedule[iso(nd)];if(nc==="D"||nc==="N")next={date:nd,code:nc}}
  var nextEl=document.getElementById("mobileNextShift");
  if(nextEl){if(next){var nm=shiftMeta(next.code);nextEl.innerHTML='<button type="button" class="mobile-shift-card-head mobile-shift-card-head--button" data-open-date="'+iso(next.date)+'"><h2>Sljedeća smjena</h2>'+icon("chevron-right")+'</button><div class="mobile-shift-card-body"><i class="shift '+next.code.toLowerCase()+'">'+next.code+'</i><span class="mobile-shift-copy"><b>'+nm.name+'</b><small>'+nm.time+'</small></span><span class="shift-countdown">'+next.date.toLocaleDateString("hr-HR",{weekday:"short"})+'</span></div>'}else{nextEl.innerHTML='<div class="mobile-shift-card-head"><h2>Sljedeća smjena</h2></div><p class="empty">Nema nadolazeće smjene.</p>'}}
  var metrics=document.getElementById("mobileMetricGrid");
  if(metrics){var weekendMinutes=data.weekendHolidayMinutes,rows=[["calendar","Ovaj mjesec",hoursText(data.workedMinutes),"Odrađeno sati",""],["chart","Saldo",signedHoursText(data.balanceMinutes),"Ukupni saldo",""],["moon","Noćni sati",hoursText(data.nightMinutes),"Ovaj mjesec","night"],["holiday","Vikendi i blagdani",hoursText(weekendMinutes),"Ovaj mjesec","weekend"]];metrics.innerHTML=rows.map(function(x){return '<div class="mobile-metric-card '+x[4]+'"><span class="metric-icon">'+icon(x[0])+'</span><span><small>'+x[1]+'</small><b>'+x[2]+'</b><small>'+x[3]+'</small></span></div>'}).join("")}
}
function nextShifts(){var list=[],start=appNow();for(var i=0;i<90&&list.length<3;i++){var d=addDays(start,i),c=state.schedule[iso(d)];if(c==="D"||c==="N")list.push([d,c])}var el=document.getElementById("nextShiftList");if(el)el.innerHTML=list.length?list.map(function(x){var m=shiftMeta(x[1]);return '<button type="button" class="next-shift next-shift--button" data-open-date="'+iso(x[0])+'"><span class="date-block">'+weekdays[x[0].getDay()].toUpperCase()+'<b>'+x[0].getDate()+'</b></span><i class="shift '+x[1].toLowerCase()+'">'+x[1]+'</i><span class="shift-copy"><b>'+m.name+'</b><small>'+m.time+'</small></span><span>›</span></button>'}).join(""):'<p class="empty">Nema nadolazećih smjena.</p>'}
function renderSelected(){
  var el=document.getElementById("selectedDayCard");if(!el)return;
  var d=state.selected,key=iso(d),hm=holidays(d.getFullYear()),code=state.schedule[key],m=shiftMeta(code);
  var codes=["D","N","GO","BO","PD","SD"];
  var editor='<div class="manual-shift-editor"><div><b>Ručno postavi oznaku</b><small>Promjena se odmah sprema i ostaje dostupna u povijesti mjeseci.</small></div><div class="manual-shift-grid">'+
    codes.map(function(item){
      return '<button type="button" class="manual-shift-btn '+(code===item?'is-selected ':'')+item.toLowerCase()+'" data-manual-shift="'+item+'" aria-pressed="'+(code===item?'true':'false')+'"><i class="shift '+item.toLowerCase()+'">'+item+'</i></button>';
    }).join('')+
    '</div><button type="button" class="link-btn manual-shift-clear" data-manual-shift="clear" '+(!code?'disabled':'')+'>Očisti oznaku</button></div>';
  el.innerHTML='<div class="selected-day-top"><div><h2>'+((key===iso(appNow()))?"Danas":d.toLocaleDateString("hr-HR",{weekday:"long"}))+'</h2><p>'+punctuatedDate(d,{weekday:"long",day:"2-digit",month:"2-digit",year:"numeric"})+'</p></div>'+(hm[key]?'<div class="holiday-inline">▦ Blagdan<br><small>'+hm[key]+'</small></div>':'')+'</div><div class="selected-shift">'+(code?'<i class="shift '+code.toLowerCase()+'">'+code+'</i>':'<i class="shift">—</i>')+'<span><b>'+m.name+'</b><small>'+m.time+'</small></span><span>›</span></div>'+editor;
}
function renderRecognition(){
  var el=document.getElementById("recognitionDays");if(!el)return;
  var target=scanTargetMonth(),y=target.getFullYear(),m=target.getMonth(),days=new Date(y,m+1,0).getDate(),selected=selectedScanSchedule();
  el.innerHTML="";
  for(var i=1;i<=days;i++){
    var code=selected?(selected[i]||""):"",x=document.createElement("button");
    x.type="button";x.className="recognition-day"+(state.editRecognition?" is-editing":"");
    x.dataset.scanDay=String(i);x.disabled=!selected||!state.editRecognition;
    x.setAttribute("aria-label",String(i)+". "+months[m]+" "+y+". "+(code?shiftMeta(code).name:"Slobodan dan"));
    x.innerHTML="<b>"+String(i).padStart(2,"0")+"."+String(m+1).padStart(2,"0")+".</b><small>"+weekdays[new Date(y,m,i).getDay()].toLowerCase()+"</small>"+(code?'<i class="shift '+code.toLowerCase()+'">'+code+'</i>':'<i class="shift">slobodno</i>');
    el.appendChild(x);
  }
  var edit=document.getElementById("editRecognitionBtn");
  if(edit){edit.disabled=!selected;edit.classList.toggle("is-active",state.editRecognition);edit.lastChild.nodeValue=state.editRecognition?" Završi uređivanje":" Uredi";}
}
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
    ["#20B7EB","Dnevni sati",hoursText(d.dayMinutes),"D raspored · "+d.counts.D+" smjena"],
    ["#27388D","Noćni sati",hoursText(d.nightMinutes),"N raspored · "+d.counts.N+" smjena"],
    ["#B8D0ED","Subote",hoursText(d.satMinutes),d.sat+" smjene"],
    ["#FF6B61","Nedjelje",hoursText(d.sunMinutes),d.sun+" smjene"],
    ["#F59E0B","Blagdani",hoursText(d.holidayMinutes),d.holidays+" smjena"],
    ["#14B8A6","GO",d.go+" dana","Godišnji odmor"],
    ["#FB7185","BO",d.bo+" dana","Bolovanje"],
    ["#F59E0B","PD",d.pd+" dana","Plaćeni dopust"],
    ["#94A3B8","SD",d.sd+" dana","Slobodan dan"],
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
    ["sun","Dnevni sati","D raspored · "+d.counts.D+" smjena",hoursText(d.dayMinutes)],
    ["holiday","Blagdani",d.holidays+" smjena",hoursText(d.holidayMinutes)],
    ["moon","Noćni sati","N raspored · "+d.counts.N+" smjena",hoursText(d.nightMinutes)],
    ["holiday","GO",d.go+" dana","Godišnji odmor"],
    ["calendar","Subote",d.sat+" smjene",hoursText(d.satMinutes)],
    ["check","BO",d.bo+" dana","Bolovanje"],
    ["calendar","PD",d.pd+" dana","Plaćeni dopust"],
    ["calendar","SD",d.sd+" dana","Slobodan dan"],
    ["calendar","Nedjelje",d.sun+" smjene",hoursText(d.sunMinutes)],
    ["scale","Saldo sati","Prema evidenciji",signedHoursText(d.balanceMinutes)]
  ].map(function(x){return '<div class="detail-item"><i>'+icon(x[0])+'</i><span><b>'+x[1]+'</b><small>'+x[2]+'</small></span><b>'+x[3]+'</b></div>'}).join("");
}
function workTypeLabel(type){
  return {
    regular:"Redovni rad",
    shift1:"1. smjena",
    shift2:"2. smjena",
    shift3:"3. smjena",
    turnus:"Turnus / 12-satni rad",
    duty:"Dežurstvo",
    standby:"Pripravnost",
    callout:"Rad po pozivu",
    other:"Drugi oblik rada"
  }[type]||"Redovni rad";
}
function normalizeWorkType(type){
  return ["regular","shift1","shift2","shift3","turnus","duty","standby","callout","other"].indexOf(type)>=0?type:"regular";
}
function renderHours(){
  var root=document.getElementById("view-hours");if(!root)return;
  var now=appNow(),today=iso(now),entries=loadTimeEntries(),active=activeTimeEntry(entries),latest=active||latestTimeEntry(entries,today);
  var currentShift=currentShiftFor(now),plannedKey=currentShift?iso(currentShift.date):today,planned=shiftMeta(state.schedule[plannedKey]||"");
  var dateEl=document.getElementById("hoursDate");if(dateEl)dateEl.textContent=now.toLocaleDateString("hr-HR",{weekday:"long",day:"2-digit",month:"long",year:"numeric"});
  var monthLabel=document.getElementById("hoursMonthLabel");if(monthLabel)monthLabel.textContent=months[now.getMonth()]+" "+now.getFullYear()+".";
  var plannedEl=document.getElementById("hoursPlannedShift");if(plannedEl)plannedEl.textContent=(state.schedule[plannedKey]?planned.name+" · "+planned.time:"Nema planirane smjene");
  var status=document.getElementById("hoursStatus"),pill=document.getElementById("hoursStatusPill"),inEl=document.getElementById("hoursInValue"),outEl=document.getElementById("hoursOutValue"),durationEl=document.getElementById("hoursDurationValue"),note=document.getElementById("hoursNote"),workType=document.getElementById("hoursWorkType");
  if(status)status.textContent=active?"Rad je u tijeku":latest&&latest.out?"Današnja evidencija je spremljena":"Nema evidentiranog ulaza";
  if(pill){pill.textContent=active?"U tijeku":latest&&latest.out?"Završeno":"Spremno";pill.classList.toggle("is-active",!!active);pill.classList.toggle("is-done",!!(latest&&latest.out))}
  if(inEl)inEl.textContent=latest?latest.in:"—";
  if(outEl)outEl.textContent=latest&&latest.out?latest.out:"—";
  if(durationEl)durationEl.textContent=latest?durationLabel(durationMinutes(latest,now)):"0h 00min";
  if(note&&document.activeElement!==note)note.value=latest&&latest.note?latest.note:"";
  if(workType&&document.activeElement!==workType)workType.value=normalizeWorkType(latest&&latest.workType);
  var inBtn=document.getElementById("clockInBtn"),outBtn=document.getElementById("clockOutBtn");
  if(inBtn)inBtn.disabled=!!active;
  if(outBtn)outBtn.disabled=!active;
  var monthPrefix=now.getFullYear()+"-"+String(now.getMonth()+1).padStart(2,"0")+"-",monthEntries=entries.filter(function(x){return x.date.indexOf(monthPrefix)===0});
  var total=monthEntries.reduce(function(sum,x){return sum+(x.out?durationMinutes(x,now):0)},0),totalEl=document.getElementById("hoursMonthTotal");if(totalEl)totalEl.textContent=durationLabel(total);
  var history=document.getElementById("hoursHistory");if(history){var sorted=monthEntries.slice().reverse();history.innerHTML=sorted.length?sorted.map(function(x){var d=new Date(x.date+"T12:00:00"),mins=durationMinutes(x,now);return '<article class="hours-history-row"><span class="hours-history-date"><b>'+String(d.getDate()).padStart(2,"0")+'.'+String(d.getMonth()+1).padStart(2,"0")+'.</b><small>'+d.toLocaleDateString("hr-HR",{weekday:"short"})+'</small></span><span><b>'+x.in+' – '+(x.out||"u tijeku")+'</b><small class="hours-work-label">'+escapeHtml(workTypeLabel(normalizeWorkType(x.workType)))+'</small><small>'+(x.note?escapeHtml(x.note):"Bez bilješke")+'</small></span><strong>'+durationLabel(mins)+'</strong></article>'}).join(""):'<div class="hours-empty">'+icon("clock")+'<p>Još nema evidentiranih sati za ovaj mjesec.</p></div>'}
}
function escapeHtml(value){return String(value).replace(/[&<>"']/g,function(ch){return {"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[ch]})}
function clockIn(){
  var entries=loadTimeEntries(),now=appNow(),today=iso(now);
  if(activeTimeEntry(entries)){toast("Ulaz je već evidentiran.");return}
  var workType=document.getElementById("hoursWorkType");
  entries.push({id:String(Date.now()),date:today,in:hhmm(now),out:null,note:"",workType:normalizeWorkType(workType&&workType.value),startedAt:now.getTime(),endedAt:null});
  if(!saveTimeEntries(entries)){toast("Ulaz nije spremljen u storage/data.");return}
  renderAll();toast("Ulaz je evidentiran.")
}
function clockOut(){
  var entries=loadTimeEntries(),now=appNow(),active=activeTimeEntry(entries);
  if(!active){toast("Nema aktivne evidencije za izlaz.");return}
  active.out=hhmm(now);active.endedAt=now.getTime();
  var note=document.getElementById("hoursNote");active.note=note?note.value.trim().slice(0,500):active.note||"";
  if(!saveTimeEntries(entries)){toast("Izlaz nije spremljen u storage/data.");return}
  renderAll();toast("Izlaz je evidentiran.")
}
function saveHoursNote(){
  var entries=loadTimeEntries(),today=iso(appNow()),entry=activeTimeEntry(entries)||latestTimeEntry(entries,today),note=document.getElementById("hoursNote");
  if(!entry||!note)return;
  entry.note=note.value.trim().slice(0,500);if(!saveTimeEntries(entries))toast("Bilješku nije moguće spremiti.")
}
function saveHoursWorkType(){
  var entries=loadTimeEntries(),today=iso(appNow()),entry=activeTimeEntry(entries)||latestTimeEntry(entries,today),select=document.getElementById("hoursWorkType");
  if(!entry||!select)return;
  entry.workType=normalizeWorkType(select.value);
  if(!saveTimeEntries(entries))toast("Vrstu rada nije moguće spremiti.");
  else renderHours();
}
async function handleScanFile(file){
  if(!file||!/^image\//.test(file.type)){toast("Odaberi valjanu slikovnu datoteku.");return}
  if(file.size>10*1024*1024){toast("Slika je prevelika. Najveća dopuštena veličina je 10 MB.");return}
  clearScanSession();
  var generation=++state.scanGeneration;
  var preview=document.getElementById("scanPreview"),img=document.getElementById("scanPreviewImage"),status=document.getElementById("scanStatus"),progress=status.querySelector(".scan-progress i");
  var url=URL.createObjectURL(file);img.dataset.objectUrl=url;img.src=url;preview.classList.add("has-image");
  renderScanPersonPicker();renderRecognition();
  status.classList.remove("is-success","is-error");status.classList.add("is-scanning");
  status.querySelector("span").textContent="Automatsko prepoznavanje rasporeda...";
  if(progress)progress.style.width="4%";
  try{
    if(!window.RasporedWebOcr||typeof window.RasporedWebOcr.recognizeSchedule!=="function")throw new Error("OCR modul nije dostupan");
    var result=await window.RasporedWebOcr.recognizeSchedule(file,function(value){
      if(generation===state.scanGeneration&&progress)progress.style.width=Math.max(4,Math.min(96,Math.round(value*100)))+"%";
    });
    if(generation!==state.scanGeneration)return;
    state.scanPeople=sanitizeScanPeople(result.people);
    state.scanMonth=result.month||null;
    state.scanSelected=state.scanPeople.length===1?0:-1;
    saveScanSession();
    status.classList.remove("is-scanning");
    if(progress)progress.style.width="100%";
    if(state.scanPeople.length){
      var monthWarning=result.month?"":" Mjesec nije pouzdano prepoznat; provjeri ga prije spremanja.";
      var recognizedDays=state.scanPeople.reduce(function(total,person){
        return total+Object.keys(person.dayShifts||{}).length;
      },0);
      var emptyRows=state.scanPeople.filter(function(person){
        return Object.keys(person.dayShifts||{}).length===0;
      }).length;
      var rowNumbers=Array.from(new Set(state.scanPeople.map(function(person){return Number(person.row)}).filter(function(value){
        return Number.isInteger(value)&&value>=1&&value<=100;
      }))).sort(function(a,b){return a-b});
      var rosterWarning="";
      if(rowNumbers.length>=5&&rowNumbers[0]<=3){
        var expectedRows=rowNumbers[rowNumbers.length-1]-rowNumbers[0]+1;
        if(expectedRows>=8&&rowNumbers.length*100<expectedRows*88){
          rosterWarning=" Upozorenje: prepoznato je "+rowNumbers.length+" od najmanje "+expectedRows+" numeriranih redaka; za potpuni uvoz ponovi fotografiju tako da cijela tablica ostane oštra.";
        }
      }
      if(recognizedDays===0){
        status.classList.add("is-error");
        status.querySelector("span").textContent="Osobe su pronađene, ali stupci dana nisu dovoljno pouzdano očitani. Ponovi fotografiju tako da se vide svi brojevi dana i cijela širina tablice."+monthWarning;
      }else{
        status.classList.add("is-success");
        var emptyWarning=emptyRows
          ?" "+emptyRows+" numeriranih redaka nema pouzdano očitanu smjenu; provjeri ih."
          :"";
        status.querySelector("span").textContent=(state.scanPeople.length===1
          ?"Prepoznata je 1 osoba i "+recognizedDays+" oznaka dana. Provjeri raspored prije spremanja."
          :"Prepoznate su "+state.scanPeople.length+" osobe i ukupno "+recognizedDays+" oznaka dana."+rosterWarning+emptyWarning+" Odaberi ime i prezime osobe čiji raspored želiš uvesti.")+monthWarning;
      }
    }else{
      status.classList.add("is-error");
      status.querySelector("span").textContent="Nije pronađena osoba s oznakama D, N, GO, BO, PD ili SD. Pokušaj s ravnijom i oštrijom fotografijom.";
    }
  }catch(error){
    if(generation!==state.scanGeneration)return;
    status.classList.remove("is-scanning");status.classList.add("is-error");
    if(progress)progress.style.width="0";
    status.querySelector("span").textContent=navigator.onLine
      ?"Prepoznavanje nije uspjelo. Pokušaj ponovno s jasnijom fotografijom."
      :"Za prvo OCR prepoznavanje potrebna je internetska veza.";
  }
  renderScanPersonPicker();renderRecognition();
}
function route(name){
  if(state.route==="scan"&&name!=="scan"){
    state.scanGeneration++;
    var scanStatus=document.getElementById("scanStatus"),scanProgress=scanStatus&&scanStatus.querySelector(".scan-progress i");
    if(scanStatus&&scanStatus.classList.contains("is-scanning")){
      scanStatus.classList.remove("is-scanning");
      scanStatus.querySelector("span").textContent="Skeniranje je prekinuto. Pokreni ga ponovno kad se vratiš.";
      if(scanProgress)scanProgress.style.width="0";
    }
  }
  state.route=name;document.body.dataset.routeCurrent=name;
  document.querySelectorAll(".view").forEach(function(x){x.classList.toggle("is-active",x.dataset.view===name)});
  var navRoute=name==="payroll"?"stats":name;
  document.querySelectorAll("[data-route]").forEach(function(x){if(x.closest(".side-nav")||x.closest(".bottom-nav"))x.classList.toggle("is-active",x.dataset.route===navRoute)});
  if(name==="stats")renderStats();
  if(name==="hours")renderHours();
  if(name==="colleagues")renderColleagues();
  if(name==="payroll"&&window.RasporedPayroll)window.RasporedPayroll.render();
  window.scrollTo({top:0,behavior:document.body.dataset.reducedMotion==="true"?"auto":"smooth"});
}
function moveMonth(delta){state.cursor=new Date(state.cursor.getFullYear(),state.cursor.getMonth()+delta,1);state.selected=new Date(state.cursor);renderAll()}
function toast(msg){var t=document.getElementById("toast");t.textContent=msg;t.classList.add("show");setTimeout(function(){t.classList.remove("show")},2200)}
function renderAll(){setMonthLabels();renderCalendar("calendarGrid");renderCalendar("calendarGridMobile");renderSummary();renderMobileHome();nextShifts();renderSelected();renderScanPersonPicker();renderRecognition();renderStats();renderHours();renderColleagues();renderTeamMembers();renderPeriodMenu();renderNotifications()}
function applyStoredAppearance(){
  var motion=document.getElementById("motionToggle");
  var reduced=storageGet("raspored.reducedMotion")==="1";
  if(motion)motion.checked=reduced;
  document.body.dataset.reducedMotion=reduced?"true":"false";
  var th=storageGet("raspored.theme");
  document.documentElement.dataset.theme=th==="dark"?"dark":"light";
  var theme=document.getElementById("themeToggle");
  if(theme)theme.checked=th==="dark";
}
function bind(){
  if(appBound)return;
  appBound=true;
  document.querySelectorAll("[data-route]").forEach(function(x){x.addEventListener("click",function(e){e.preventDefault();route(this.dataset.route)})});
  document.addEventListener("click",function(e){
    var target=e.target.closest("[data-route-dynamic]");if(target){e.preventDefault();route(target.dataset.routeDynamic);return}
    var dateTarget=e.target.closest("[data-open-date]");
    if(dateTarget){
      var date=new Date(dateTarget.dataset.openDate+"T12:00:00");
      if(!Number.isNaN(date.getTime())){state.selected=date;state.cursor=new Date(date.getFullYear(),date.getMonth(),1);renderAll();route("calendar")}
      return;
    }
    var manual=e.target.closest("[data-manual-shift]");
    if(manual){
      var value=manual.dataset.manualShift||"",key=iso(state.selected);
      if(value==="clear")delete state.schedule[key];
      else if(["D","N","GO","BO","PD","SD"].indexOf(value)>=0)state.schedule[key]=value;
      else return;
      if(!save()){loadSchedule();toast("Promjenu nije moguće spremiti u storage/data.");}
      renderAll();
      return;
    }
    var remove=e.target.closest("[data-remove-colleague]");
    if(remove){
      var items=loadColleagues(),index=Number(remove.dataset.removeColleague);
      if(Number.isInteger(index)&&index>=0&&index<items.length){items.splice(index,1);saveColleagues(items);renderColleagues();toast("Kolega je uklonjen.");}
    }
  });
  ["prevMonth","calPrev"].forEach(function(id){var x=document.getElementById(id);if(x)x.addEventListener("click",function(){moveMonth(-1)})});
  ["nextMonth","calNext"].forEach(function(id){var x=document.getElementById(id);if(x)x.addEventListener("click",function(){moveMonth(1)})});
  document.getElementById("todayBtn").addEventListener("click",function(){var n=appNow();state.cursor=new Date(n.getFullYear(),n.getMonth(),1);state.selected=n;renderAll()});
  document.getElementById("themeToggle").addEventListener("change",function(){document.documentElement.dataset.theme=this.checked?"dark":"light";if(!storageSet("raspored.theme",document.documentElement.dataset.theme))toast("Postavku izgleda nije moguće spremiti.")});
  var motion=document.getElementById("motionToggle");
  if(motion){
    motion.addEventListener("change",function(){if(!storageSet("raspored.reducedMotion",this.checked?"1":"0"))toast("Postavku animacija nije moguće spremiti.");document.body.dataset.reducedMotion=this.checked?"true":"false"});
  }
  document.getElementById("saveSchedule").addEventListener("click",importSelectedScanSchedule);
  var saveTeamSchedules=document.getElementById("saveTeamSchedules");
  if(saveTeamSchedules)saveTeamSchedules.addEventListener("click",importScannedTeamSchedules);
  window.addEventListener("raspored:auth-ready",function(){syncAuthenticatedProfile();renderScanPersonPicker();renderTeamMembers()});
  document.getElementById("clockInBtn").addEventListener("click",clockIn);
  document.getElementById("clockOutBtn").addEventListener("click",clockOut);
  document.getElementById("hoursNote").addEventListener("change",saveHoursNote);
  document.getElementById("hoursWorkType").addEventListener("change",saveHoursWorkType);
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
  var scanHelpBtn=document.getElementById("scanHelpBtn"),scanHelpDialog=document.getElementById("scanHelpDialog");
  if(scanHelpBtn&&scanHelpDialog)scanHelpBtn.addEventListener("click",function(){scanHelpDialog.showModal()});
  var statsRefreshBtn=document.getElementById("statsRefreshBtn");
  if(statsRefreshBtn)statsRefreshBtn.addEventListener("click",function(){loadSchedule();renderAll();toast("Podaci su osvježeni.")});
  var search=document.getElementById("searchBtn");if(search)search.addEventListener("click",openSearch);
  var searchDialog=document.getElementById("searchDialog"),searchInput=document.getElementById("searchInput"),searchResults=document.getElementById("searchResults");
  if(searchInput)searchInput.addEventListener("input",function(){renderSearchResults(this.value)});
  if(searchResults)searchResults.addEventListener("click",function(e){
    var button=e.target.closest("[data-search-route]");if(!button)return;
    if(searchDialog)searchDialog.close();route(button.dataset.searchRoute);
  });
  var colleagueForm=document.getElementById("colleagueForm"),colleagueDialog=document.getElementById("colleagueDialog");
  if(colleagueForm)colleagueForm.addEventListener("submit",function(e){e.preventDefault();submitColleague()});
  ["closeColleagueDialog","cancelColleagueBtn"].forEach(function(id){var button=document.getElementById(id);if(button)button.addEventListener("click",function(){if(colleagueDialog)colleagueDialog.close()})});

  var addColleagueBtn=document.getElementById("addColleagueBtn");if(addColleagueBtn)addColleagueBtn.addEventListener("click",addColleague);
  ["scanMonthPrev","scanMonthNext"].forEach(function(id){
    var button=document.getElementById(id);if(!button)return;
    button.addEventListener("click",function(){
      var target=scanTargetMonth(),delta=id==="scanMonthPrev"?-1:1;
      target=new Date(target.getFullYear(),target.getMonth()+delta,1);
      state.scanMonth={year:target.getFullYear(),month:target.getMonth()+1};
      saveScanSession();renderScanPersonPicker();renderRecognition();
    });
  });
  var scanPersonButton=document.getElementById("scanPersonButton"),scanPersonMenu=document.getElementById("scanPersonMenu");
  if(scanPersonButton&&scanPersonMenu){
    scanPersonButton.addEventListener("click",function(e){e.stopPropagation();if(this.disabled)return;var opening=scanPersonMenu.hidden;scanPersonMenu.hidden=!opening;this.setAttribute("aria-expanded",opening?"true":"false")});
    scanPersonMenu.addEventListener("click",function(e){var b=e.target.closest("[data-scan-person]");if(!b)return;state.scanSelected=Number(b.dataset.scanPerson);state.editRecognition=false;saveScanSession();scanPersonMenu.hidden=true;scanPersonButton.setAttribute("aria-expanded","false");renderScanPersonPicker();renderRecognition()});
    document.addEventListener("click",function(e){if(!scanPersonMenu.hidden&&!e.target.closest(".scan-person-picker")){scanPersonMenu.hidden=true;scanPersonButton.setAttribute("aria-expanded","false")}});
  }
  var editRecognition=document.getElementById("editRecognitionBtn"),recognitionDays=document.getElementById("recognitionDays");
  if(editRecognition){
    editRecognition.addEventListener("click",function(){
      if(!scanPerson())return;
      state.editRecognition=!state.editRecognition;renderRecognition();
    });
  }
  if(recognitionDays){
    recognitionDays.addEventListener("click",function(e){
      var button=e.target.closest("[data-scan-day]");if(!button||!state.editRecognition)return;
      var person=scanPerson(),day=Number(button.dataset.scanDay);if(!person||!day)return;
      var next=cycleScanCode(person.dayShifts[day]||"");
      if(next)person.dayShifts[day]=next;else delete person.dayShifts[day];
      saveScanSession();renderScanPersonPicker();renderRecognition();
    });
  }
  var profileInput=document.getElementById("profileNameInput"),saveProfileBtn=document.getElementById("saveProfileBtn");
  if(saveProfileBtn&&profileInput){
    saveProfileBtn.addEventListener("click",function(){
      if(authSnapshot().authenticated){toast("Ime profila dolazi iz prijavljenog korisničkog računa.");return}
      var value=profileInput.value.trim().replace(/\s+/g," ").slice(0,80);
      if(value.length>0&&value.length<2){toast("Unesi valjano ime i prezime.");return}
      var stored=value?storageSet("raspored.profile.name",value):storageRemove("raspored.profile.name");
      if(!stored){toast("Profil nije moguće spremiti u storage/data.");return}
      configureProfile();toast("Profil je spremljen.");
    });
  }
  var notificationBtn=document.getElementById("notificationBtn"),notificationPanel=document.getElementById("notificationPanel"),closeNotificationBtn=document.getElementById("closeNotificationBtn");
  function setNotificationPanel(open){if(!notificationPanel||!notificationBtn)return;notificationPanel.hidden=!open;notificationBtn.setAttribute("aria-expanded",open?"true":"false")}
  if(notificationBtn)notificationBtn.addEventListener("click",function(e){e.stopPropagation();var opening=notificationPanel?notificationPanel.hidden:false;setNotificationPanel(opening);if(opening)markNotificationsRead();if(profilePanel)profilePanel.hidden=true});
  if(closeNotificationBtn)closeNotificationBtn.addEventListener("click",function(){setNotificationPanel(false)});
  var profileButton=document.getElementById("profileButton"),profilePanel=document.getElementById("profilePanel"),closeProfileBtn=document.getElementById("closeProfileBtn");
  function setProfilePanel(open){if(!profilePanel||!profileButton)return;profilePanel.hidden=!open}
  if(profileButton)profileButton.addEventListener("click",function(e){e.stopPropagation();setProfilePanel(profilePanel?profilePanel.hidden:false);if(notificationPanel)setNotificationPanel(false)});
  if(closeProfileBtn)closeProfileBtn.addEventListener("click",function(){setProfilePanel(false)});
  document.addEventListener("click",function(e){
    if(notificationPanel&&!notificationPanel.hidden&&!e.target.closest("#notificationPanel")&&!e.target.closest("#notificationBtn"))setNotificationPanel(false);
    if(profilePanel&&!profilePanel.hidden&&!e.target.closest("#profilePanel")&&!e.target.closest("#profileButton"))setProfilePanel(false);
  });
  function connectivity(){var b=document.getElementById("connectivityBanner");b.classList.toggle("show",!navigator.onLine)}
  window.addEventListener("online",connectivity);window.addEventListener("offline",connectivity);connectivity();
}
async function initApp(){
  if(!window.RasporedDataStore){throw new Error("RASPORED data store nije učitan.");}
  bind();
  document.body.dataset.routeCurrent=state.route;
  renderAll();
  window.addEventListener("raspored:storage-error",function(){toast("Spremanje u storage/data trenutačno nije dostupno.");});
  await window.RasporedDataStore.init();
  if(window.RasporedPayroll)await window.RasporedPayroll.init();
  loadSchedule();loadScanSession();configureProfile();applyStoredAppearance();
  syncAuthenticatedProfile();
  renderAll();
  document.body.dataset.appReady=window.RasporedDataStore.isAvailable()?"true":"storage-unavailable";
  if(!window.RasporedDataStore.isAvailable())toast("storage/data nije dostupno. Podaci nisu učitani i spremanje je onemogućeno.");
  setInterval(function(){if(state.route==="hours")renderHours()},60000);
  if("serviceWorker" in navigator){window.addEventListener("load",function(){navigator.serviceWorker.register((document.body.dataset.base||"")+"/sw.js").catch(function(){})})}
}
initApp().catch(function(){
  document.body.dataset.routeCurrent=state.route;
  document.body.dataset.appReady="error";
  renderAll();
  toast("Podatkovni sloj nije dostupan. Spremanje je onemogućeno.");
});
})();