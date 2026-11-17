// ===== Intro sa pagbukas: umiikot na song number, "VideokeTana", at tunog na pang-sine =====
// Minsan bawat pagbukas (sessionStorage). Pindutin para laktawan.
// Kapag hinarang ng browser ang tunog (wala pang pindot sa page), may maliit na "Pindutin para may tunog":
// pagpindot, uulitin ang intro na may tunog. Sa Android app, kusa itong may tunog.
(function () {
  const el = document.getElementById('intro');
  if (!el) return;
  let seen = false; try { seen = sessionStorage.getItem('vk_intro') === '1'; } catch (e) {}
  if (seen || matchMedia('(prefers-reduced-motion: reduce)').matches) { el.remove(); return; }
  try { sessionStorage.setItem('vk_intro', '1'); } catch (e) {}
  el.hidden = false;
  const num = document.getElementById('introNum'), word = document.getElementById('introWord');
  const p = window.VK_PREFS ? VK_PREFS.get() : {};
  const soundOn = window.VKSfx && p.sfxEnabled !== false;
  let done = false, t0 = 0, withSound = false, hint = null;

  const end = () => {
    if (done) return; done = true;
    el.classList.add('out'); setTimeout(() => el.remove(), 600);
  };
  function playSound() {
    VKSfx.setVolume(Math.min(1, (p.sfxVolume == null ? 80 : p.sfxVolume) / 80));
    VKSfx.play('intro'); withSound = true;
  }
  function start() {
    t0 = performance.now(); el.classList.remove('word');
    requestAnimationFrame(function spin(now) {
      if (done) return;
      const t = now - t0;
      if (t < 900) num.textContent = String(10000 + Math.floor(Math.random() * 89999));
      else if (!el.classList.contains('word')) { num.textContent = '10001'; el.classList.add('word'); }
      word.textContent = 'VideokeTana'.slice(0, Math.max(0, Math.min(11, Math.floor((t - 900) / 70))));
      if (t > 3000) return end();
      requestAnimationFrame(spin);
    });
  }
  el.addEventListener('click', () => {
    // unang pindot habang walang tunog: ulitin ang intro na may tunog
    if (soundOn && !withSound && hint) {
      hint.remove(); hint = null;
      VKSfx.unlock(); playSound(); start();
      return;
    }
    end();
  });
  document.addEventListener('keydown', end, { once: true });

  if (!soundOn) { start(); return; }
  VKSfx.ready().then(ok => {
    if (done) return;
    if (ok) playSound();
    else {
      hint = document.createElement('button');
      hint.type = 'button'; hint.className = 'intro-sound';
      hint.textContent = 'Pindutin para may tunog';
      el.appendChild(hint);
    }
    start();
  });
})();
