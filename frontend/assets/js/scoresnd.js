/* Sariling tunog ng score: mga file na pinangalanang score_61_75.mp3, score_81_90.mp3, score_100.mp3 ...
 * Naka-save sa Cache Storage ng device na ito ("vk-score"). VKScoreSnd.save(files), clear(), list(), play(score) → Promise<segundo o 0>. */
(function () {
  'use strict';
  const C = 'vk-score';
  const RE = /^score_(\d{1,3})(?:_(\d{1,3}))?\.(mp3|m4a|wav|ogg|aac)$/i;
  const range = name => { const m = RE.exec(name); if (!m) return null; const a = +m[1], b = m[2] ? +m[2] : a; return [Math.min(a, b), Math.max(a, b)]; };
  async function list() {
    try { const c = await caches.open(C); return (await c.keys()).map(r => decodeURIComponent(new URL(r.url).pathname.split('/').pop())).filter(n => range(n)); } catch (e) { return []; }
  }
  async function save(files) {
    const c = await caches.open(C); let ok = 0;
    for (const f of files) { if (!range(f.name)) continue; await c.put('/__score/' + encodeURIComponent(f.name), new Response(f, { headers: { 'Content-Type': f.type || 'audio/mpeg' } })); ok++; }
    return ok;
  }
  async function clear() { await caches.delete(C); }
  let cur = null;
  async function play(score, volume) {
    const names = await list();
    const hit = names.map(n => [n, range(n)]).filter(([, r]) => score >= r[0] && score <= r[1]).sort((a, b) => (a[1][1] - a[1][0]) - (b[1][1] - b[1][0]))[0];
    if (!hit) return 0;
    const c = await caches.open(C); const r = await c.match('/__score/' + encodeURIComponent(hit[0])); if (!r) return 0;
    const url = URL.createObjectURL(await r.blob());
    try { if (cur) cur.pause(); } catch (e) {}
    cur = new Audio(url); cur.volume = volume == null ? 1 : volume;
    await cur.play().catch(() => {});
    return new Promise(res => { cur.onloadedmetadata = () => res(Math.min(14, cur.duration || 4)); setTimeout(() => res(Math.min(14, cur.duration || 4)), 800); });
  }
  window.VKScoreSnd = { save, clear, list, play, range };
})();
