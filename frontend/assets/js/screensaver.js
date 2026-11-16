// ===== SCREENSAVER: digital clock kapag hindi ginagamit ang app =====
// Kailangan: prefs.js. Opsyonal na hooks mula sa page:
//   window.vkCanSleep()   -> false kung bawal mag-screensaver ngayon (hal. may kantang tumutugtog)
//   window.vkSaverExtra() -> HTML na ipapakita sa ilalim ng orasan (hal. room code)
(function () {
  const DAYS = ['Linggo', 'Lunes', 'Martes', 'Miyerkules', 'Huwebes', 'Biyernes', 'Sabado'];
  const MONTHS = ['Enero', 'Pebrero', 'Marso', 'Abril', 'Mayo', 'Hunyo', 'Hulyo', 'Agosto', 'Setyembre', 'Oktubre', 'Nobyembre', 'Disyembre'];
  let last = Date.now();
  let wokeAt = 0;
  let shown = false;
  let el = null, tickT = null, moveT = null;

  function build() {
    el = document.createElement('div');
    el.id = 'vkSaver';
    el.setAttribute('role', 'dialog');
    el.setAttribute('aria-label', 'Screensaver. Pindutin para bumalik.');
    el.innerHTML = `<div class="ss-box"><div class="ss-time"><span class="dot ss-hm"></span><span class="ss-side"><span class="dot ss-sec"></span><span class="ss-ampm"></span></span></div>
      <div class="ss-date"></div><div class="ss-extra"></div></div><div class="ss-hint">Pindutin kahit saan para bumalik</div>`;
    document.body.appendChild(el);
  }

  function pad(n) { return String(n).padStart(2, '0'); }
  function tick() {
    if (!shown) return;
    const p = VK_PREFS.get(), d = new Date();
    let h = d.getHours();
    const ampm = h < 12 ? 'AM' : 'PM';
    if (!p.ss24h) { h = h % 12 || 12; }
    const colon = d.getSeconds() % 2 ? '<span class="ss-colon off">:</span>' : '<span class="ss-colon">:</span>';
    el.querySelector('.ss-hm').innerHTML = (p.ss24h ? pad(h) : h) + colon + pad(d.getMinutes());
    el.querySelector('.ss-sec').textContent = p.ssSeconds ? pad(d.getSeconds()) : '';
    el.querySelector('.ss-ampm').textContent = p.ss24h ? '' : ampm;
    el.querySelector('.ss-date').textContent = p.ssDate ? `${DAYS[d.getDay()]}, ${MONTHS[d.getMonth()]} ${d.getDate()}` : '';
  }
  // Ilipat-lipat ang orasan para hindi ma-"burn-in" ang screen
  function move() {
    if (!shown) return;
    const box = el.querySelector('.ss-box');
    box.style.opacity = 0;
    setTimeout(() => {
      const mx = Math.max(0, window.innerWidth - box.offsetWidth - 40);
      const my = Math.max(0, window.innerHeight - box.offsetHeight - 80);
      box.style.transform = `translate(${20 + Math.random() * mx}px, ${20 + Math.random() * my}px)`;
      box.style.opacity = 1;
    }, 700);
  }

  function show() {
    if (shown) return;
    if (!el) build();
    shown = true;
    const extra = typeof window.vkSaverExtra === 'function' ? window.vkSaverExtra() : '';
    el.querySelector('.ss-extra').innerHTML = extra || '';
    el.classList.add('on');
    tick(); tickT = setInterval(tick, 1000);
    const box = el.querySelector('.ss-box');
    box.style.transform = `translate(${Math.max(20, (innerWidth - box.offsetWidth) / 2)}px, ${Math.max(20, (innerHeight - box.offsetHeight) / 2 - 30)}px)`;
    moveT = setInterval(move, 60000);
    window.dispatchEvent(new Event('vksaver:show'));
  }
  function hide() {
    if (!shown) return;
    shown = false;
    el.classList.remove('on');
    clearInterval(tickT); clearInterval(moveT);
    last = Date.now();
    window.dispatchEvent(new Event('vksaver:hide'));
  }
  window.vkWake = hide;
  window.vkSleepNow = () => { setTimeout(show, 50); };
  window.vkSaverShown = () => shown;

  function activity(e) {
    if (shown) {
      // unang galaw ay pang-gising lang; huwag ipasa ang click sa ilalim
      if (e.type === 'pointermove' || e.type === 'mousemove') return;
      e.preventDefault(); e.stopPropagation();
      wokeAt = Date.now();
      hide();
      return;
    }
    last = Date.now();
  }
  ['pointerdown', 'keydown', 'wheel', 'touchstart'].forEach(t => window.addEventListener(t, activity, { capture: true, passive: false }));
  let mt = 0;
  window.addEventListener('pointermove', () => { const n = Date.now(); if (n - mt > 1000) { mt = n; if (!shown) last = n; } }, { passive: true });
  ['click', 'pointerup', 'mouseup', 'touchend'].forEach(t => window.addEventListener(t, e => {
    if (shown || Date.now() - wokeAt < 700) { e.preventDefault(); e.stopPropagation(); }
  }, { capture: true, passive: false }));

  setInterval(() => {
    const p = VK_PREFS.get();
    if (!p.ssEnabled || shown || document.hidden) return;
    if (typeof window.vkCanSleep === 'function' && !window.vkCanSleep()) { last = Date.now(); return; }
    if (Date.now() - last >= p.ssAfter * 60000) show();
  }, 5000);
  window.addEventListener('vkprefs', () => { if (shown) tick(); });
})();
