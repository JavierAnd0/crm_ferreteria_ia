// Service worker: guarda la interfaz para abrirla sin internet. La API nunca se guarda en caché.
const CACHE = 'ferreteria-v2';
const ARCHIVOS = ['/', '/index.html', '/css/app.css', '/js/app.js', '/js/api.js', '/js/offline.js', '/js/vistas.js',
  '/manifest.webmanifest', '/icono.svg'];

self.addEventListener('install', e => e.waitUntil(caches.open(CACHE).then(c => c.addAll(ARCHIVOS)).then(() => self.skipWaiting())));
self.addEventListener('activate', e => e.waitUntil(
  caches.keys().then(ks => Promise.all(ks.filter(k => k !== CACHE).map(k => caches.delete(k)))).then(() => self.clients.claim())));
self.addEventListener('fetch', e => {
  const url = new URL(e.request.url);
  if (e.request.method !== 'GET' || url.pathname.startsWith('/api/') || url.origin !== location.origin) return;
  // Red primero (versión nueva si hay conexión); caché si no hay.
  e.respondWith(fetch(e.request).then(r => {
    const copia = r.clone();
    caches.open(CACHE).then(c => c.put(e.request, copia));
    return r;
  }).catch(() => caches.match(e.request).then(r => r || caches.match('/index.html'))));
});
