// Service worker ng Videoke app: para mai-install at mabilis bumukas.
// HTML: network muna (para laging bago), cache kapag offline. CSS/JS/icons: cache, tapos ina-update sa likod.
// Hindi ginagalaw ang audio/video, Supabase, YouTube at iba pang site.
const VERSION = 'vk-0.12.5';
const SHELL = ['./', './app/', './host/', './remote/', './songbook/', './settings/', './mysongs/', './manifest.webmanifest', './assets/icons/icon-192.png'];

self.addEventListener('install', e => {
  e.waitUntil(caches.open(VERSION).then(c => c.addAll(SHELL).catch(() => {})).then(() => self.skipWaiting()));
});
self.addEventListener('activate', e => {
  e.waitUntil(caches.keys().then(keys => Promise.all(keys.filter(k => k !== VERSION && k !== 'vk-sf').map(k => caches.delete(k)))).then(() => self.clients.claim()));
});
self.addEventListener('fetch', e => {
  const req = e.request, url = new URL(req.url);
  if (req.method !== 'GET' || url.origin !== location.origin) return;
  if (req.headers.has('range') || /\.(mp3|m4a|mp4|webm|ogg|wav|sf2|mid|kar)$/i.test(url.pathname)) return;
  if (req.mode === 'navigate') {
    e.respondWith(fetch(req).then(r => { const copy = r.clone(); caches.open(VERSION).then(c => c.put(req, copy)); return r; })
      .catch(() => caches.match(req).then(r => r || caches.match('./'))));
    return;
  }
  if (/\/assets\//.test(url.pathname) || /\.(webmanifest|json|lrc)$/.test(url.pathname)) {
    e.respondWith(caches.open(VERSION).then(async c => {
      const hit = await c.match(req);
      const net = fetch(req).then(r => { if (r.ok) c.put(req, r.clone()); return r; }).catch(() => hit);
      return hit || net;
    }));
  }
});
