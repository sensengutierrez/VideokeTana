// ===== Sariling wallpaper + transparent na mga box =====
// Ang larawan ay naka-save sa IndexedDB ng device (hindi ina-upload kahit saan).
// Kailangan: prefs.js (wallpaper, wallDim, glass, glassBlur)
(function () {
  const DB = 'videoke', STORE = 'files', KEY = 'wallpaper';
  let url = null;

  function db() {
    return new Promise((res, rej) => {
      const r = indexedDB.open(DB, 1);
      r.onupgradeneeded = () => r.result.createObjectStore(STORE);
      r.onsuccess = () => res(r.result);
      r.onerror = () => rej(r.error);
    });
  }
  async function getBlob() {
    try {
      const d = await db();
      return await new Promise(res => {
        const q = d.transaction(STORE).objectStore(STORE).get(KEY);
        q.onsuccess = () => res(q.result || null);
        q.onerror = () => res(null);
      });
    } catch (e) { return null; }
  }
  async function putBlob(blob) {
    const d = await db();
    await new Promise((res, rej) => {
      const tx = d.transaction(STORE, 'readwrite');
      blob ? tx.objectStore(STORE).put(blob, KEY) : tx.objectStore(STORE).delete(KEY);
      tx.oncomplete = res; tx.onerror = () => rej(tx.error);
    });
  }

  // Paliitin ang larawan (max 1920px, JPEG) para mabilis at hindi lumaki ang storage
  function shrink(file) {
    return new Promise((res, rej) => {
      const img = new Image();
      img.onload = () => {
        const k = Math.min(1, 1920 / Math.max(img.width, img.height));
        const c = document.createElement('canvas');
        c.width = Math.round(img.width * k); c.height = Math.round(img.height * k);
        c.getContext('2d').drawImage(img, 0, 0, c.width, c.height);
        c.toBlob(b => b ? res(b) : rej(new Error('convert')), 'image/jpeg', 0.86);
        URL.revokeObjectURL(img.src);
      };
      img.onerror = () => rej(new Error('Hindi mabasa ang larawan'));
      img.src = URL.createObjectURL(file);
    });
  }

  async function apply(force) {
    const p = VK_PREFS.get(), root = document.documentElement, b = document.body;
    if (!b) return;
    // transparency ng mga box: 0 = buo, 80 = halos see-through
    const op = Math.max(15, 100 - (p.glass || 0));
    root.style.setProperty('--box-op', op + '%');
    root.style.setProperty('--glass-blur', p.glassBlur ? '14px' : '0px');
    b.classList.toggle('glass', (p.glass || 0) > 0);
    root.style.setProperty('--wall-dim', String((p.wallDim ?? 45) / 100));
    let has = false;
    if (p.wallpaper === 'image') {
      if (force === true || !url) {
        const blob = await getBlob();
        if (url) { URL.revokeObjectURL(url); url = null; }
        if (blob) url = URL.createObjectURL(blob);
      }
      if (url) { root.style.setProperty('--wall', `url("${url}")`); has = true; }
    }
    b.classList.toggle('has-wall', has);
    window.dispatchEvent(new CustomEvent('vkwall', { detail: { has, url: has ? url : null } }));
  }

  window.VKWall = {
    async set(file) {
      const blob = await shrink(file);
      await putBlob(blob);
      VK_PREFS.set('wallpaper', 'image');
      await apply(true);
      return blob;
    },
    async clear() { await putBlob(null); if (url) URL.revokeObjectURL(url); url = null; VK_PREFS.set('wallpaper', 'none'); await apply(); },
    getBlob, apply,
    url: () => url
  };

  document.addEventListener('DOMContentLoaded', apply);
  window.addEventListener('vkprefs', apply);
})();
