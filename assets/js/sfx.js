// ===== Sound effects (ginagawa ng Web Audio; walang audio file) =====
// VKSfx.unlock() sa unang tap, tapos VKSfx.play('applause' | 'drumroll' | 'horn' | 'ding' | 'fanfare', {gain, size})
(function () {
  let ac = null, master = null;
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
    src.connect(f).connect(g).connect(master);
    src.start(t, Math.random() * 1.5); src.stop(t + dur + 0.05);
  }
  function tone(t, freq, dur, gain, type = 'sine', bend = 0) {
    const o = ac.createOscillator(), g = ac.createGain();
    o.type = type; o.frequency.setValueAtTime(freq, t);
    if (bend) o.frequency.linearRampToValueAtTime(freq + bend, t + dur);
    g.gain.setValueAtTime(0, t); g.gain.linearRampToValueAtTime(gain, t + 0.02);
    g.gain.setValueAtTime(gain, t + Math.max(0.03, dur - 0.08)); g.gain.linearRampToValueAtTime(0, t + dur);
    o.connect(g).connect(master); o.start(t); o.stop(t + dur + 0.05);
  }

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
        o.connect(g).connect(master); o.start(t + k * 0.28); o.stop(t + k * 0.28 + 1.5);
      });
    },
    // maikling tagumpay na tunog para sa score
    fanfare(t) {
      [[523, 0], [659, .12], [784, .24], [1047, .36]].forEach(([f, d]) => tone(t + d, f, d === .36 ? .7 : .14, 0.09, 'triangle'));
      [[523, 659, 784]].forEach(ch => ch.forEach(f => tone(t + .36, f, .7, 0.05, 'sawtooth')));
    }
  };

  window.VKSfx = {
    unlock() { ctx(); },
    setVolume(v) { if (master) master.gain.value = Math.max(0, Math.min(1, v)) * 0.9; },
    play(name, o = {}) {
      if (!ctx() || !FX[name]) return false;
      try { FX[name](ac.currentTime + 0.03, o); return true; } catch (e) { console.warn(e); return false; }
    }
  };
})();
