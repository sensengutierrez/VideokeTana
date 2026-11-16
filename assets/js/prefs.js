// ===== Settings ng device (naka-save sa localStorage) =====
(function () {
  const KEY = 'vk_prefs';
  const DEFAULTS = {
    ssEnabled: true,      // screensaver naka-on
    ssAfter: 5,           // minuto bago lumabas
    ss24h: false,         // 24-oras na format
    ssSeconds: false,     // ipakita ang segundo
    ssDate: true,         // ipakita ang petsa
    lyricSize: 'm'        // s | m | l (sa TV)
  };
  function read() {
    try { return Object.assign({}, DEFAULTS, JSON.parse(localStorage.getItem(KEY) || '{}')); }
    catch (e) { return Object.assign({}, DEFAULTS); }
  }
  window.VK_PREFS = {
    DEFAULTS,
    get: read,
    set(k, v) {
      const p = read(); p[k] = v;
      try { localStorage.setItem(KEY, JSON.stringify(p)); } catch (e) {}
      window.dispatchEvent(new CustomEvent('vkprefs', { detail: p }));
      return p;
    }
  };
  // Sabay-sabay na update kapag binago sa ibang tab
  window.addEventListener('storage', e => {
    if (e.key === KEY) window.dispatchEvent(new CustomEvent('vkprefs', { detail: read() }));
  });
})();
