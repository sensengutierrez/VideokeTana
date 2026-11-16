// ===== Sound effects (ginagawa ng Web Audio; walang audio file) =====
// VKSfx.unlock() sa unang tap, tapos VKSfx.play(name, {gain, size, solo}); VKSfx.stop() para ihinto lahat.
(function () {
  let ac = null, master = null, bus = null;
  const live = new Set();   // mga tumutunog pa (para sa Stop)
  function ctx() {
    if (!ac) {
      const AC = window.AudioContext || window.webkitAudioContext;
      if (!AC) return null;
      ac = new AC();
      master = ac.createGain(); master.gain.value = 0.8; master.connect(ac.destination);
    }
    if (ac.state === 'suspended') ac.resume();
    return ac;
  }
  function noiseBuf(sec) {
    const b = ac.createBuffer(1, Math.floor(ac.sampleRate * sec), ac.sampleRate), d = b.getChannelData(0);
    for (let i = 0; i < d.length; i++) d[i] = Math.random() * 2 - 1;
    return b;
  }
  let NOISE = null;
  function noise(t, dur, gain, type = 'bandpass', freq = 2000, q = 1) {
    if (!NOISE) NOISE = noiseBuf(2);
    const src = ac.createBufferSource(); src.buffer = NOISE; src.loop = true;
    const f = ac.createBiquadFilter(); f.type = type; f.frequency.value = freq; f.Q.value = q;
    const g = ac.createGain();
    g.gain.setValueAtTime(0, t); g.gain.linearRampToValueAtTime(gain, t + 0.003);
    g.gain.exponentialRampToValueAtTime(0.0008, t + dur);
    src.connect(f).connect(g).connect(bus);
    src.start(t, Math.random() * 1.5); src.stop(t + dur + 0.05);
  }
  function tone(t, freq, dur, gain, type = 'sine', bend = 0) {
    const o = ac.createOscillator(), g = ac.createGain();
    o.type = type; o.frequency.setValueAtTime(freq, t);
    if (bend) o.frequency.linearRampToValueAtTime(freq + bend, t + dur);
    g.gain.setValueAtTime(0, t); g.gain.linearRampToValueAtTime(gain, t + 0.02);
    g.gain.setValueAtTime(gain, t + Math.max(0.03, dur - 0.08)); g.gain.linearRampToValueAtTime(0, t + dur);
    o.connect(g).connect(bus); o.start(t); o.stop(t + dur + 0.05);
  }

  // boses ng maraming tao (oscillator + formant filters): para sa wow, aww, boo, cheers
  function crowd(t, dur, f0, bend, vowel, gain, n = 9) {
    const F = { oo: [300, 870], aa: [750, 1200], uu: [320, 800], eh: [550, 1800] }[vowel];
    for (let i = 0; i < n; i++) {
      const o = ac.createOscillator(), g = ac.createGain(), at = t + Math.random() * 0.12;
      const base = f0 * (0.7 + Math.random() * 0.9);
      o.type = 'sawtooth';
      o.frequency.setValueAtTime(base, at);
      o.frequency.linearRampToValueAtTime(base * bend, at + dur * 0.8);
      const vib = ac.createOscillator(), vg = ac.createGain();
      vib.frequency.value = 4 + Math.random() * 2; vg.gain.value = base * 0.02; vib.connect(vg).connect(o.frequency);
      g.gain.setValueAtTime(0, at); g.gain.linearRampToValueAtTime(gain / n, at + 0.12);
      g.gain.setValueAtTime(gain / n, at + dur * 0.6); g.gain.linearRampToValueAtTime(0, at + dur);
      F.forEach((fr, k) => {
        const bp = ac.createBiquadFilter(); bp.type = 'bandpass'; bp.frequency.value = fr; bp.Q.value = 5;
        const fg = ac.createGain(); fg.gain.value = k ? 0.6 : 1;
        o.connect(bp).connect(fg).connect(g);
      });
      g.connect(bus); o.start(at); vib.start(at); o.stop(at + dur + 0.05); vib.stop(at + dur + 0.05);
    }
    noise(t, dur, gain * 0.15, 'bandpass', F[0] * 2, 1);
  }
  function say(text, rate = 1, pitch = 1) {
    if (!window.speechSynthesis) return;
    const u = new SpeechSynthesisUtterance(text);
    const vs = speechSynthesis.getVoices();
    const v = vs.find(x => /^fil|^tl/i.test(x.lang)) || vs.find(x => /^en/i.test(x.lang));
    if (v) u.voice = v;
    u.rate = rate; u.pitch = pitch; u.volume = Math.min(1, vol);
    speechSynthesis.speak(u);
  }
  let vol = 0.8;

  const FX = {
    // maraming maiikling palakpak + ingay ng tao
    applause(t, o) {
      const dur = 2.6 + (o.size || 1) * 1.6, n = Math.floor(120 * (o.size || 1) * dur);
      for (let i = 0; i < n; i++) {
        const at = t + Math.pow(Math.random(), 1.4) * dur;
        noise(at, 0.06 + Math.random() * 0.05, 0.05 + Math.random() * 0.07, 'bandpass', 1400 + Math.random() * 2200, 1.2);
      }
      noise(t, dur + 0.6, 0.05, 'lowpass', 900, 0.5);
    },
    drumroll(t) {
      for (let i = 0; i < 46; i++) noise(t + i * 0.042, 0.05, 0.05 + i * 0.004, 'bandpass', 1800, 0.8);
      noise(t + 1.95, 1.6, 0.35, 'highpass', 5000, 0.4);      // cymbal
      tone(t + 1.95, 70, 0.4, 0.5, 'sine', -30);               // kick
    },
    horn(t) {
      [0, 0.42, 0.84].forEach((d, k) => {
        const len = k === 2 ? 0.75 : 0.32;
        [466, 554, 698].forEach(f => tone(t + d, f, len, 0.06, 'sawtooth', -6));
      });
    },
    ding(t) {
      [1318.5, 1046.5].forEach((f, k) => {
        const o = ac.createOscillator(), g = ac.createGain();
        o.type = 'sine'; o.frequency.value = f;
        g.gain.setValueAtTime(0.25, t + k * 0.28); g.gain.exponentialRampToValueAtTime(0.001, t + k * 0.28 + 1.4);
        o.connect(g).connect(bus); o.start(t + k * 0.28); o.stop(t + k * 0.28 + 1.5);
      });
    },
    // maikling tagumpay na tunog para sa score
    fanfare(t) {
      [[523, 0], [659, .12], [784, .24], [1047, .36]].forEach(([f, d]) => tone(t + d, f, d === .36 ? .7 : .14, 0.09, 'triangle'));
      [[523, 659, 784]].forEach(ch => ch.forEach(f => tone(t + .36, f, .7, 0.05, 'sawtooth')));
    },
    cheers(t) {
      FX.applause(t, { size: 1.2 });
      crowd(t, 2.4, 260, 1.25, 'eh', 0.35, 12);
      [0.2, 0.9, 1.6].forEach(d => tone(t + d, 1900 + Math.random() * 400, 0.35, 0.05, 'sine', 700));   // sipol
    },
    wow(t) { crowd(t, 1.6, 180, 1.6, 'oo', 0.5, 12); },
    aww(t) { crowd(t, 1.8, 330, 0.62, 'aa', 0.45, 12); },
    boo(t) { crowd(t, 2.2, 120, 0.85, 'uu', 0.6, 14); },
    laugh(t) {
      for (let i = 0; i < 7; i++) crowd(t + i * 0.2, 0.16, 240 - i * 8, 0.9, 'aa', 0.45, 6);
    },
    angry(t) {
      tone(t, 110, 0.5, 0.16, 'square', -10); tone(t + 0.55, 92, 0.8, 0.16, 'square', -20);
      tone(t, 116, 0.5, 0.1, 'sawtooth'); tone(t + 0.55, 97, 0.8, 0.1, 'sawtooth');
    },
    boom(t) {
      const o = ac.createOscillator(), g = ac.createGain();
      o.type = 'sine'; o.frequency.setValueAtTime(120, t); o.frequency.exponentialRampToValueAtTime(30, t + 1.2);
      g.gain.setValueAtTime(0.9, t); g.gain.exponentialRampToValueAtTime(0.001, t + 1.6);
      o.connect(g).connect(bus); o.start(t); o.stop(t + 1.7);
      noise(t, 1.4, 0.5, 'lowpass', 400, 0.7);
    },
    omg(t) { noise(t, 0.35, 0.12, 'bandpass', 1500, 1.5); setTimeout(() => say('Oh my God!', 0.85, 1.2), 250); },
    wownaman(t) { setTimeout(() => say('Wow naman!', 0.9, 1.3), 30); FX.wow(t + 0.6); },
    galing(t) { setTimeout(() => say('Ang galing!', 0.95, 1.1), 30); FX.applause(t + 0.6, { size: 0.6 }); }
  };

  function stop() {
    live.forEach(b => { try { b.gain.cancelScheduledValues(ac.currentTime); b.gain.setTargetAtTime(0, ac.currentTime, 0.03); setTimeout(() => b.disconnect(), 200); } catch (e) {} });
    live.clear();
    if (window.speechSynthesis) speechSynthesis.cancel();
  }
  window.VKSfx = {
    NAMES: ['applause', 'cheers', 'drumroll', 'ding', 'horn', 'boom', 'wow', 'aww', 'boo', 'laugh', 'angry', 'omg', 'wownaman', 'galing'],
    unlock() { ctx(); if (window.speechSynthesis) speechSynthesis.getVoices(); },
    setVolume(v) { vol = Math.max(0, Math.min(1, v)); if (master) master.gain.value = vol * 0.9; },
    stop() { if (ac) stop(); },
    // o.solo: ihinto muna ang tumutunog bago tumugtog ang bago
    play(name, o = {}) {
      if (!ctx() || !FX[name]) return false;
      if (o.solo) stop();
      bus = ac.createGain(); bus.gain.value = o.gain == null ? 1 : o.gain; bus.connect(master);
      const b = bus; live.add(b); setTimeout(() => live.delete(b), 7000);
      try { FX[name](ac.currentTime + 0.03, o); return true; } catch (e) { console.warn(e); return false; }
    }
  };
})();
