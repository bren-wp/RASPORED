const CACHE="raspored-v1.0.25";
const ASSETS=[
  "./",
  "./version.txt",
  "./assets/css/app.css",
  "./assets/js/data-store.js",
  "./assets/js/app.js",
  "./assets/js/ocr-table.js",
  "./assets/js/ocr-web.js",
  "./assets/js/payroll.js",
  "./assets/js/auth.js",
  "./assets/js/export.js",
  "./assets/data/payroll-public-sector-2026.json",
  "./assets/brand/logo.svg",
  "./assets/brand/icon-maskable.svg",
  "./assets/brand/icons.svg",
  "./manifest.webmanifest"
];

self.addEventListener("install",event=>{
  event.waitUntil(
    caches.open(CACHE)
      .then(cache=>cache.addAll(ASSETS))
      .then(()=>self.skipWaiting())
  );
});

self.addEventListener("activate",event=>{
  event.waitUntil(
    caches.keys()
      .then(keys=>Promise.all(keys.filter(key=>key!==CACHE).map(key=>caches.delete(key))))
      .then(()=>self.clients.claim())
  );
});

self.addEventListener("fetch",event=>{
  if(event.request.method!=="GET")return;
  const url=new URL(event.request.url);
  if(url.origin!==self.location.origin)return;
  if(url.pathname.includes("/api/"))return;

  if(event.request.mode==="navigate"){
    event.respondWith(
      fetch(event.request)
        .then(response=>{
          if(response.ok)caches.open(CACHE).then(cache=>cache.put("./",response.clone()));
          return response;
        })
        .catch(()=>caches.match("./"))
    );
    return;
  }

  event.respondWith(
    caches.match(event.request).then(cached=>{
      const network=fetch(event.request)
        .then(response=>{
          if(response.ok){
            const clone=response.clone();
            caches.open(CACHE).then(cache=>cache.put(event.request,clone));
          }
          return response;
        })
        .catch(()=>cached||new Response("",{status:503,statusText:"Offline"}));
      return cached||network;
    })
  );
});
