/* Interlude ng web host: malumanay na musika habang naghihintay ng kanta (kapareho ng app: tv/Interlude.kt).
 * Gawa ng code: electric piano, bass, mahinang beat. Walang audio file, walang kinopya.
 * VKInterlude.start(volume 0..100), .stop(), .setVolume(v), VKInterlude.render() → { data: Float32Array, sr } (para sa test).
 */
(function () {
  'use strict';
  const SR = 32000;
  const PROGS = [[0, 5, 3, 4], [0, 4, 5, 3], [5, 3, 0, 4], [0, 3, 4, 3], [3, 4, 2, 5], [0, 2, 3, 4]];
  const MAJOR = [0, 2, 4, 5, 7, 9, 11];
  const hz = n => 440 * Math.pow(2, (n - 69) / 12);

  function render(seed) {
    let s = seed || (Date.now() % 2147483647);
    const rnd = () => { s = (s * 16807) % 2147483647; return s / 2147483647; };
    const ri = n => Math.floor(rnd() * n);
    const bpm = 76 + ri(18), beat = 60 / bpm, bar = beat * 4;
    const prog = PROGS[ri(PROGS.length)];
    const root = 57 + ri(8);
    const bars = 16;
    const n = Math.floor(bar * bars * SR);
    const b = new Float32Array(n);
    const add = (i, v) => { if (i >= 0 && i < n) b[i] += v; };
    function piano(t, dur, f, g) {
      const st = Math.floor(t * SR), len = Math.floor(dur * SR);
      for (let k = 0; k < len; k++) {
        const x = k / SR, env = (1 - Math.exp(-x * 60)) * Math.exp(-x * 1.6);
        const w = Math.sin(2 * Math.PI * f * x) + 0.25 * Math.sin(4 * Math.PI * f * x) * Math.exp(-x * 4) + 0.08 * Math.sin(6 * Math.PI * f * x) * Math.exp(-x * 8);
        add(st + k, w * env * g);
      }
    }
    function bass(t, dur, f, g) {
      const st = Math.floor(t * SR), len = Math.floor(dur * SR);
      for (let k = 0; k < len; k++) {
        const x = k / SR, env = (1 - Math.exp(-x * 120)) * Math.exp(-x * 2.5) * (k > len - 400 ? (len - k) / 400 : 1);
        add(st + k, Math.tanh(1.6 * Math.sin(2 * Math.PI * f * x)) * env * g);
      }
    }
    function kick(t, g) {
      const st = Math.floor(t * SR); let ph = 0;
      for (let k = 0; k < 0.25 * SR; k++) { const x = k / SR; ph += 2 * Math.PI * (45 + 90 * Math.exp(-x * 30)) / SR; add(st + k, Math.sin(ph) * Math.exp(-x * 14) * g); }
    }
    function hat(t, g) {
      const st = Math.floor(t * SR); let prev = 0;
      for (let k = 0; k < 0.05 * SR; k++) { const w = rnd() * 2 - 1, hp = w - prev; prev = w; add(st + k, hp * Math.exp(-k / SR * 70) * g); }
    }
    const note = (deg, oct) => { oct = oct || 0; const d = ((deg % 7) + 7) % 7; return root + MAJOR[d] + 12 * (oct + Math.floor(deg / 7)); };
    for (let bi = 0; bi < bars; bi++) {
      const t0 = bi * bar, deg = prog[bi % prog.length];
      const chord = [note(deg), note(deg + 2), note(deg + 4), note(deg + 6)];
      chord.forEach(m => piano(t0, beat * 1.6, hz(m), 0.055));
      chord.slice(1).forEach(m => piano(t0 + beat * 2.5, beat * 1.4, hz(m), 0.04));
      if (bi % 2 === 1) for (let k = 0; k < 3; k++) {
        const md = deg + [4, 2, 7, 6, 4][ri(5)];
        piano(t0 + beat * (k + 0.5 + ri(2) * 0.5), beat * 0.9, hz(note(md, 1)), 0.035);
      }
      bass(t0, beat * 1.5, hz(note(deg, -2)), 0.16);
      bass(t0 + beat * 2, beat * 0.9, hz(note(deg, -2)), 0.12);
      bass(t0 + beat * 3, beat * 0.9, hz(note(deg + 4, -2)), 0.11);
      if (bi >= 2) {
        kick(t0, 0.22); kick(t0 + beat * 2.5, 0.16);
        for (let h = 0; h < 8; h++) hat(t0 + h * beat / 2, h % 2 === 1 ? 0.05 : 0.025);
      }
    }
    const taps = [0.031, 0.047, 0.071, 0.113].map(d => Math.floor(d * SR));
    for (let i = n - 1; i >= 0; i--) { let r = 0; taps.forEach((d, k) => { if (i - d >= 0) r += b[i - d] * (0.22 - k * 0.04); }); b[i] += r; }
    const xf = Math.floor(0.4 * SR);
    for (let k = 0; k < xf; k++) { const a = k / xf; b[k] = b[k] * a + b[n - xf + k] * (1 - a); }
    const out = new Float32Array(n - xf);
    for (let i = 0; i < out.length; i++) out[i] = Math.tanh(b[i] * 1.4) * 0.92;
    return { data: out, sr: SR };
  }

  let ctx = null, src = null, gain = null, target = 0.4, busy = false;
  async function start(volume) {
    target = Math.max(0, Math.min(1, (volume == null ? 40 : volume) / 100));
    if (src || busy) { if (gain) gain.gain.setTargetAtTime(target, ctx.currentTime, 0.4); return; }
    busy = true;
    try {
      ctx = ctx || new (window.AudioContext || window.webkitAudioContext)();
      if (ctx.state === 'suspended') await ctx.resume().catch(() => {});
      await new Promise(r => setTimeout(r, 0));
      const { data, sr } = render();
      if (!busy) return;
      const buf = ctx.createBuffer(1, data.length, sr); buf.getChannelData(0).set(data);
      src = ctx.createBufferSource(); src.buffer = buf; src.loop = true;
      gain = ctx.createGain(); gain.gain.value = 0;
      src.connect(gain); gain.connect(ctx.destination);
      src.start();
      gain.gain.setTargetAtTime(target, ctx.currentTime, 0.8);    // dahan-dahang paglakas
    } catch (e) { console.warn('interlude', e); }
    busy = false;
  }
  function stop() {
    busy = false;
    if (!src) return;
    const s = src, g = gain; src = null; gain = null;
    try { g.gain.setTargetAtTime(0, ctx.currentTime, 0.25); setTimeout(() => { try { s.stop(); } catch (e) {} }, 1200); } catch (e) {}
  }
  function setVolume(v) { target = Math.max(0, Math.min(1, v / 100)); if (gain) gain.gain.setTargetAtTime(target, ctx.currentTime, 0.2); }

  if (typeof window !== 'undefined') window.VKInterlude = { start, stop, setVolume, render, playing: () => !!src };
})();
