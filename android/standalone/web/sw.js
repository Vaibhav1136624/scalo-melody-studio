const cacheName='scalo-studio-v1';
const files=['./','./index.html','./app.js','./demo.js','./style.css','./manifest.webmanifest','./icon-192.png','./icon-512.png'];
self.addEventListener('install',event=>event.waitUntil(caches.open(cacheName).then(cache=>cache.addAll(files)).then(()=>self.skipWaiting())));
self.addEventListener('activate',event=>event.waitUntil(self.clients.claim()));
self.addEventListener('fetch',event=>{
 if(event.request.method!=='GET'||!files.some(path=>new URL(path,self.registration.scope).href===event.request.url))return;
 event.respondWith(caches.open(cacheName).then(async cache=>(await cache.match(event.request))||fetch(event.request)));
});
