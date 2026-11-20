// ===== Settings ng device (naka-save sa localStorage) =====
(function () {
  const KEY = 'vk_prefs';
  const DEFAULTS = {
    // wika at boses
    lang: 'fil', ttsVoice: false,
    // tunog ng kanta (sariling kanta lang; dB)
    musicBass: 0, musicTreble: 0,
    // TV screen sa phone/tablet: 'landscape' (nakahiga) o 'auto'
    tvOrient: 'landscape',
    // pitch bar (para sa kantang may melody)
    pitchBar: 'on',
    // laktawan nang kusa ang mahabang intro ng kanta (may lyrics)
    autoSkipIntro: false,
    // interlude habang naghihintay ng kanta
    interlude: true, interludeVol: 40,
    // estilo ng numero at orasan: dot (LCD) | seg (Digital) | bold | thin (kapareho ng app)
    numFont: 'dot',
    // MIDI: 'sf' = SoundFont, 'basic' = simpleng synth
    midiEngine: 'sf', sfChoice: 'builtin', sfOwnName: '',
    // soundboard
    sbNoOverlap: true, sbVol: 80,
    // screensaver
    ssEnabled: true, ssAfter: 5, ss24h: false, ssSeconds: false, ssDate: true,
    // lyrics sa TV
    lyricSize: 'm',          // s | m | l | xl
    lyricAlign: 'center',    // center | left
    lyricLines: 2,           // 2 | 4
    hlColor: '#62e3d2',      // kulay ng highlight
    hlRandom: false,         // random na kulay bawat kanta
    outline: 1,              // 0 wala | 1 manipis | 2 makapal
    lyricShadow: true,
    countdown: 'num',        // num | dots | off
    lyricDelay: 0,           // ms; positibo = mas maaga ang lyrics
    // score
    scoreMode: 'random',     // random | mic | off
    scoreLevel: 'medium',    // easy | medium | hard
    applause: 2,             // 0 wala | 1 mahina | 2 katamtaman | 3 malakas
    // TV screen
    idleText: 'Pumili ng kanta',
    bottomText: '',
    bottomColor: '#ffd54a',
    // background
    bgMode: 'animated',      // animated | videos | plain
    scene: 'auto',           // auto | city | sea | sky
    bgChange: true,          // palitan bawat kanta
    // tunog
    sfxEnabled: true,
    sfxVolume: 80,
    keyBeep: 'mid',          // off | low | mid
    // mic (phone bilang mic)
    micVolume: 100,          // 0-150
    micEcho: 30,             // 0-100
    micReverb: 20,           // 0-100
    micClarity: 30,          // 0-100
    // wallpaper + transparency
    wallpaper: 'none',       // none | image (naka-save sa IndexedDB)
    wallDim: 45,             // dilim ng wallpaper 0-80
    glass: 0,                // transparency ng mga box 0-80
    glassBlur: true          // blur sa likod ng box
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
  // estilo ng numero/orasan sa buong page
  const applyFont = () => { try { document.documentElement.dataset.numfont = read().numFont || 'dot'; } catch (e) {} };
  applyFont(); window.addEventListener('vkprefs', applyFont);
  // Sabay-sabay na update kapag binago sa ibang tab
  window.addEventListener('storage', e => {
    if (e.key === KEY) window.dispatchEvent(new CustomEvent('vkprefs', { detail: read() }));
  });
})();

// ===== App (PWA): i-register ang service worker para mai-install =====
(function () {
  const src = (document.currentScript && document.currentScript.src) || '';
  const root = src.replace(/assets\/js\/prefs\.js.*$/, '');
  window.VK_ROOT = root;
  if ('serviceWorker' in navigator && root && location.protocol !== 'file:') {
    window.addEventListener('load', () => navigator.serviceWorker.register(root + 'sw.js', { scope: root }).catch(() => {}));
  }
  // kailangang mahuli agad ang install prompt (bago pa mag-load ang ibang script)
  window.addEventListener('beforeinstallprompt', e => { e.preventDefault(); window.vkInstallPrompt = e; window.dispatchEvent(new Event('vkinstallable')); });
})();
