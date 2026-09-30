(function(){
"use strict";

var current={authenticated:false,account:null};
function qs(id){return document.getElementById(id)}
function base(){return document.body&&document.body.dataset?document.body.dataset.base||"":""}
function endpoint(){return base()+"/api/auth.php"}
function fullName(account){return account?((account.firstName||"")+" "+(account.lastName||"")).trim():""}
function initials(value){
  var parts=String(value||"K").trim().split(/\s+/).filter(Boolean);
  return (parts[0]&&parts[0][0]||"K")+(parts[1]&&parts[1][0]||"");
}
function setError(id,message){
  var el=qs(id);if(!el)return;
  el.textContent=message||"";
  el.hidden=!message;
}
function accountTypeLabel(type){return type==="manager"?"Voditelj tima":"Djelatnik / osobni raspored"}
function render(){
  var badge=qs("accountStatusBadge"),status=qs("accountStatusText"),details=qs("accountDetails");
  var login=qs("loginAccountBtn"),register=qs("registerAccountBtn"),logout=qs("logoutAccountBtn");
  if(current.authenticated&&current.account){
    var name=fullName(current.account);
    if(badge){badge.textContent="Prijavljen";badge.classList.add("is-authenticated")}
    if(status)status.textContent=name+" · "+current.account.email;
    if(details){
      details.hidden=false;
      details.replaceChildren();
      var rows=[
        ["Profil",accountTypeLabel(current.account.accountType)],
        ["Telefon",current.account.phone||"—"],
        ["Spremanje","Račun · storage/data JSON"]
      ];
      rows.forEach(function(row){
        var div=document.createElement("div");
        var small=document.createElement("small");small.textContent=row[0];
        var strong=document.createElement("strong");strong.textContent=row[1];
        div.append(small,strong);details.appendChild(div);
      });
    }
    if(login)login.hidden=true;if(register)register.hidden=true;if(logout)logout.hidden=false;
    document.body.dataset.authenticated="true";
    document.body.dataset.accountType=current.account.accountType||"individual";
    var profileName=qs("profileName"),profileInitials=qs("profileInitials");
    if(profileName&&name)profileName.textContent=name;
    if(profileInitials&&name)profileInitials.textContent=initials(name).toUpperCase();
  }else{
    if(badge){badge.textContent="Gost";badge.classList.remove("is-authenticated")}
    if(status)status.textContent="Osnovne funkcije rade bez registracije.";
    if(details){details.hidden=true;details.replaceChildren()}
    if(login)login.hidden=false;if(register)register.hidden=false;if(logout)logout.hidden=true;
    document.body.dataset.authenticated="false";
    document.body.dataset.accountType="guest";
  }
  window.dispatchEvent(new CustomEvent("raspored:auth-ready",{detail:{authenticated:current.authenticated,account:current.account}}));
}
async function refresh(){
  try{
    var response=await fetch(endpoint(),{credentials:"same-origin",cache:"no-store",headers:{"Accept":"application/json"}});
    if(!response.ok)throw new Error("auth status");
    var payload=await response.json();
    current={authenticated:payload.authenticated===true,account:payload.account||null};
  }catch(error){
    current={authenticated:false,account:null};
    var status=qs("accountStatusText");
    if(status&&!navigator.onLine)status.textContent="Račun nije dostupan bez internetske veze. Lokalni raspored i dalje radi.";
  }
  render();
  return current;
}
async function post(payload){
  var response=await fetch(endpoint(),{
    method:"POST",
    credentials:"same-origin",
    cache:"no-store",
    headers:{"Content-Type":"application/json","X-Raspored-Request":"1"},
    body:JSON.stringify(payload)
  });
  var data={};
  try{data=await response.json()}catch(error){}
  if(!response.ok||data.ok!==true)throw new Error(data.error||"Zahtjev nije uspio.");
  return data;
}
function openDialog(id){
  var dialog=qs(id);if(dialog&&typeof dialog.showModal==="function")dialog.showModal();
}
function closeAuthDialogs(){
  ["loginDialog","registerDialog"].forEach(function(id){
    var dialog=qs(id);if(dialog&&dialog.open)dialog.close();
  });
}
function bind(){
  var loginBtn=qs("loginAccountBtn"),registerBtn=qs("registerAccountBtn"),logoutBtn=qs("logoutAccountBtn");
  if(loginBtn)loginBtn.addEventListener("click",function(){setError("loginError","");openDialog("loginDialog")});
  if(registerBtn)registerBtn.addEventListener("click",function(){setError("registerError","");openDialog("registerDialog")});
  document.querySelectorAll("[data-close-auth]").forEach(function(button){button.addEventListener("click",closeAuthDialogs)});

  var loginForm=qs("loginForm");
  if(loginForm)loginForm.addEventListener("submit",async function(event){
    event.preventDefault();setError("loginError","");
    var submit=loginForm.querySelector('button[type="submit"]');if(submit)submit.disabled=true;
    try{
      await post({action:"login",email:qs("loginEmail").value,password:qs("loginPassword").value});
      location.reload();
    }catch(error){
      setError("loginError",error&&error.message?error.message:"Prijava nije uspjela.");
    }finally{if(submit)submit.disabled=false}
  });

  var registerForm=qs("registerForm");
  if(registerForm)registerForm.addEventListener("submit",async function(event){
    event.preventDefault();setError("registerError","");
    var submit=registerForm.querySelector('button[type="submit"]');if(submit)submit.disabled=true;
    try{
      await post({
        action:"register",
        firstName:qs("registerFirstName").value,
        lastName:qs("registerLastName").value,
        email:qs("registerEmail").value,
        phone:qs("registerPhone").value,
        password:qs("registerPassword").value,
        accountType:qs("registerAccountType").value
      });
      location.reload();
    }catch(error){
      setError("registerError",error&&error.message?error.message:"Registracija nije uspjela.");
    }finally{if(submit)submit.disabled=false}
  });

  if(logoutBtn)logoutBtn.addEventListener("click",async function(){
    logoutBtn.disabled=true;
    try{await post({action:"logout"});location.reload()}
    catch(error){logoutBtn.disabled=false;window.dispatchEvent(new CustomEvent("raspored:storage-error"))}
  });
}
window.RasporedAuth={
  refresh:refresh,
  snapshot:function(){return JSON.parse(JSON.stringify(current))},
  isAuthenticated:function(){return current.authenticated===true},
  isManager:function(){return current.authenticated===true&&current.account&&current.account.accountType==="manager"}
};
document.addEventListener("DOMContentLoaded",function(){bind();refresh()},{once:true});
})();