// ===== Kanta sa device (hindi ina-upload) =====
// Naka-save sa IndexedDB ng device na ito. Numero 90001 pataas para hindi kapareho ng library (10001+).
// Ang device na may kanta (ang TV) lang ang makakapagpatugtog nito; ang phone ay nagre-reserve gamit ang numero.
(function () {
  const DB = 'videoke-songs', STORE = 'songs', FIRST = 90001;
  let dbp = null;
  function db() {
    if (dbp) return dbp;
    dbp = new Promise((res, rej) => {
      if (!window.indexedDB) return rej(new Error('Walang IndexedDB'));
      const r = indexedDB.open(DB, 1);
      r.onupgradeneeded = () => r.result.createObjectStore(STORE, { keyPath: 'code' });
      r.onsuccess = () => res(r.result);
      r.onerror = () => rej(r.error);
    });
    return dbp;
  }
  async function tx(mode, fn) {
    const d = await db();
    return new Promise((res, rej) => {
      const t = d.transaction(STORE, mode), s = t.objectStore(STORE);
      let out; const r = fn(s);
      if (r) r.onsuccess = () => { out = r.result; };
      t.oncomplete = () => res(out);
      t.onerror = t.onabort = () => rej(t.error);
    });
  }
  // "Artist - Title.mp3" o "Title.mp3"
  function parseName(name) {
    const base = name.replace(/\.[^.]+$/, '').replace(/[_]+/g, ' ').trim();
    const m = base.match(/^(.+?)\s+-\s+(.+)$/);
    return m ? { artist: m[1].trim(), title: m[2].trim() } : { artist: '', title: base };
  }
  const isMedia = f => /^(audio|video)\//.test(f.type) || /\.(mp3|m4a|aac|ogg|wav|flac|mp4|webm|mkv)$/i.test(f.name);
  const isVideo = f => /^video\//.test(f.type) || /\.(mp4|webm|mkv)$/i.test(f.name);

  const api = {
    FIRST,
    async list() {
      const all = await tx('readonly', s => s.getAll());
      return (all || []).map(({ blob, ...meta }) => meta).sort((a, b) => a.code - b.code);
    },
    get(code) { return tx('readonly', s => s.get(+code)); },
    async meta(code) { const r = await api.get(code); if (!r) return null; const { blob, ...m } = r; return m; },
    remove(code) { return tx('readwrite', s => s.delete(+code)); },
    async update(code, patch) {
      const r = await api.get(code); if (!r) return;
      Object.assign(r, patch);
      return tx('readwrite', s => s.put(r));
    },
    // magdagdag ng mga file; ang .lrc na kapareho ang pangalan ay ikakabit bilang lyrics
    async addFiles(files) {
      files = [...files];
      const lrcs = new Map(files.filter(f => /\.lrc$/i.test(f.name)).map(f => [f.name.replace(/\.lrc$/i, '').toLowerCase(), f]));
      const media = files.filter(isMedia);
      const have = await api.list();
      let next = Math.max(FIRST - 1, ...have.map(s => s.code)) + 1;
      const added = [];
      for (const f of media) {
        if (next > 99999) break;
        const key = f.name.replace(/\.[^.]+$/, '').toLowerCase();
        const l = lrcs.get(key);
        const rec = {
          code: next++, ...parseName(f.name), kind: isVideo(f) ? 'video' : 'audio',
          type: f.type || '', size: f.size, added: Date.now(), file: f.name,
          lrc: l ? await l.text() : '', blob: f
        };
        await tx('readwrite', s => s.put(rec));
        added.push({ code: rec.code, title: rec.title });
      }
      // kapag ang .lrc lang ang idinagdag: ikabit sa kantang kapareho ang file name
      for (const [key, l] of lrcs) {
        if (media.some(f => f.name.replace(/\.[^.]+$/, '').toLowerCase() === key)) continue;
        const hit = have.find(s => (s.file || '').replace(/\.[^.]+$/, '').toLowerCase() === key);
        if (hit) await api.update(hit.code, { lrc: await l.text() });
      }
      if (navigator.storage && navigator.storage.persist) navigator.storage.persist().catch(() => {});
      return added;
    },
    async usage() {
      const list = await api.list();
      const songs = list.reduce((n, s) => n + (s.size || 0), 0);
      let quota = 0, used = 0, persisted = false;
      try { const e = await navigator.storage.estimate(); quota = e.quota || 0; used = e.usage || 0; } catch (e) {}
      try { persisted = await navigator.storage.persisted(); } catch (e) {}
      return { count: list.length, songs, used, quota, persisted };
    }
  };
  window.VKLocal = api;
})();
