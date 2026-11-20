/* VideokeTana MIDI para sa web host.
 * VKMidi.parse(ArrayBuffer) → { notes, lyrics, melody, duration }
 * VKMidi.render(parsed) → Promise<Blob> (WAV), ginagawa ng sariling synth (Web Audio, walang SoundFont file)
 * Ang WAV ay pinapatugtog sa <audio> ng host, kaya gumagana ang pause, volume, bass/treble at lyrics.
 */
(function () {
  'use strict';

  function parse(buf) {
    const b = new Uint8Array(buf);
    let p = 0;
    const u8 = () => b[p++];
    const u16 = () => (u8() << 8) | u8();
    const u32 = () => ((u16() << 16) >>> 0) + u16();
    const vlq = () => { let v = 0, c; do { c = u8(); v = (v << 7) | (c & 0x7f); } while (c & 0x80); return v; };
    const str = (a, n) => { let s = ''; for (let i = 0; i < n; i++) s += String.fromCharCode(b[a + i]); return s; };
    if (str(0, 4) !== 'MThd') throw new Error('Hindi MIDI file');
    p = 4; const hl = u32(); const st0 = p; u16(); const ntr = u16(); const div = u16(); p = st0 + hl;
    if (div & 0x8000) throw new Error('Hindi suportadong MIDI (SMPTE)');
    const tempos = [[0, 500000]];
    const ev = [];   // {tick, type:'on'|'off'|'prog'|'cc'|'text'|'lyric', ...}
    for (let tr = 0; tr < ntr && p + 8 <= b.length; tr++) {
      const id = str(p, 4); p += 4; const len = u32(); const end = Math.min(p + len, b.length);
      if (id !== 'MTrk') { p = end; continue; }
      let tick = 0, status = 0;
      while (p < end) {
        tick += vlq();
        let s = u8();
        if (s < 0x80) { p--; s = status; } else if (s < 0xf0) status = s;
        if (s === 0xff) {
          const type = u8(); const l = vlq(); const a = p; p += l;
          if (type === 0x51 && l === 3) tempos.push([tick, (b[a] << 16) | (b[a + 1] << 8) | b[a + 2]]);
          else if (type === 0x01 || type === 0x05) ev.push({ tick, k: type === 5 ? 'lyric' : 'text', text: str(a, l) });
          else if (type === 0x2f) p = end;
        } else if (s === 0xf0 || s === 0xf7) { const l = vlq(); p += l; }   // (huwag "p += vlq()": nababasa ang lumang p bago gumalaw ang vlq)
        else {
          const hi = s & 0xf0, ch = s & 0x0f;
          const d1 = u8(); const d2 = (hi === 0xc0 || hi === 0xd0) ? 0 : u8();
          if (hi === 0x90 && d2 > 0) ev.push({ tick, k: 'on', ch, n: d1, v: d2 });
          else if (hi === 0x80 || (hi === 0x90 && d2 === 0)) ev.push({ tick, k: 'off', ch, n: d1 });
          else if (hi === 0xc0) ev.push({ tick, k: 'prog', ch, v: d1 });
          else if (hi === 0xb0) ev.push({ tick, k: 'cc', ch, c: d1, v: d2 });
          else if (hi === 0xe0) ev.push({ tick, k: 'bend', ch, v: ((d2 << 7) | d1) - 8192 });
        }
      }
      p = end;
    }
    tempos.sort((x, y) => x[0] - y[0]);
    const sec = t => { let s = 0, lt = 0, us = 500000; for (const [tt, u] of tempos) { if (tt > t) break; s += (tt - lt) * us / 1e6 / div; lt = tt; us = u; } return s + (t - lt) * us / 1e6 / div; };
    ev.sort((x, y) => x.tick - y.tick || (x.k === 'off' ? -1 : y.k === 'off' ? 1 : 0));

    // mga nota na may haba; sustain pedal (CC64) ay nagpapahaba
    const prog = new Array(16).fill(0), vol = new Array(16).fill(100), expr = new Array(16).fill(127), sus = new Array(16).fill(false);
    const bank = new Array(16).fill(0), pan = new Array(16).fill(64), bend = new Array(16).fill(0);
    const open = {}, held = new Array(16).fill(null).map(() => []);
    const notes = [];
    const close = (o, t) => { o.dur = Math.max(0.05, t - o.t); };
    for (const e of ev) {
      const t = sec(e.tick);
      if (e.k === 'prog') prog[e.ch] = e.v;
      else if (e.k === 'bend') bend[e.ch] = e.v / 8192 * 2;
      else if (e.k === 'cc') {
        if (e.c === 7) vol[e.ch] = e.v; else if (e.c === 11) expr[e.ch] = e.v;
        else if (e.c === 0) bank[e.ch] = e.v; else if (e.c === 10) pan[e.ch] = e.v;
        else if (e.c === 121) { vol[e.ch] = 100; expr[e.ch] = 127; bend[e.ch] = 0; }
        else if (e.c === 64) { const on = e.v >= 64; if (sus[e.ch] && !on) { held[e.ch].forEach(o => close(o, t)); held[e.ch] = []; } sus[e.ch] = on; }
      } else if (e.k === 'on') {
        const key = e.ch * 128 + e.n;
        if (open[key]) { close(open[key], t); }
        const o = { t, dur: 0.5, midi: e.n, ch: e.ch, vel: e.v / 127 * (vol[e.ch] / 127) * (expr[e.ch] / 127), prog: prog[e.ch],
          v: e.v, cg: (vol[e.ch] / 127) * (expr[e.ch] / 127), bank: bank[e.ch], pan: pan[e.ch], bend: bend[e.ch] };
        notes.push(o); open[key] = o;
      } else if (e.k === 'off') {
        const key = e.ch * 128 + e.n; const o = open[key];
        if (o) { delete open[key]; if (sus[e.ch]) held[e.ch].push(o); else close(o, t); }
      }
    }
    Object.values(open).forEach(o => close(o, o.t + 1));
    held.forEach(h => h.forEach(o => close(o, o.t + 1)));

    // lyrics: lyric event (FF 05) o KAR text (FF 01: "/" bagong linya, "\" talata, "@" header)
    const useLyric = ev.some(e => e.k === 'lyric' && e.text.trim());
    const lyr = ev.filter(e => (useLyric ? e.k === 'lyric' : e.k === 'text') && !e.text.startsWith('@'));
    const lines = []; const sylls = [];
    let cur = '', curT = -1;
    const flush = () => { const s = cur.trim(); if (s && /[\p{L}\p{N}]/u.test(s)) lines.push({ t: curT, text: s }); cur = ''; curT = -1; };
    for (const e of lyr) {
      let t = e.text;
      if (t.startsWith('/') || t.startsWith('\\')) { flush(); t = t.slice(1); }
      const brk = /[\r\n]$/.test(t);
      if (/^[\r\n]/.test(t)) flush();
      t = t.replace(/[\r\n]/g, '').replace(/#/g, '');
      if (t) { const s = sec(e.tick); if (curT < 0) curT = s; cur += t; if (/[\p{L}\p{N}]/u.test(t)) sylls.push(s); }
      if (brk) flush();
    }
    flush();

    // melody para sa pitch bar: ang channel na pinakamaraming nota na kasabay ng mga pantig ng lyrics
    let melody = [];
    if (sylls.length > 8) {
      const best = new Array(16).fill(0);
      const byCh = {};
      notes.forEach(n => { if (n.ch !== 9) (byCh[n.ch] = byCh[n.ch] || []).push(n); });
      Object.keys(byCh).forEach(ch => {
        const starts = byCh[ch].map(n => n.t);
        let j = 0, hit = 0;
        for (const s of sylls) { while (j < starts.length && starts[j] < s - 0.08) j++; if (j < starts.length && Math.abs(starts[j] - s) <= 0.08) hit++; }
        best[ch] = hit;
      });
      const ch = best.indexOf(Math.max(...best));
      if (best[ch] > sylls.length * 0.4) {
        // isang nota bawat oras (pinakamataas kung may chord)
        const ns = byCh[ch].slice().sort((a, b) => a.t - b.t || b.midi - a.midi);
        let lastT = -1;
        ns.forEach(n => { if (Math.abs(n.t - lastT) > 0.02) { melody.push([n.t, n.dur, n.midi]); lastT = n.t; } });
      }
    }
    const duration = notes.reduce((m, n) => Math.max(m, n.t + n.dur), 0) + 2;
    return { notes, lyrics: lines, melody, duration };
  }

  // ---------------- synth (sariling mixing sa JS: mabilis, walang Web Audio node bawat nota) ----------------
  const hz = m => 440 * Math.pow(2, (m - 69) / 12);
  function family(prog, ch) {
    if (ch === 9) return 'drum';
    if (prog < 8) return 'piano';
    if (prog < 16) return 'bell';
    if (prog < 24) return 'organ';
    if (prog < 32) return 'guitar';
    if (prog < 40) return 'bass';
    if (prog < 56) return 'strings';
    if (prog < 64) return 'brass';
    if (prog < 80) return 'reed';
    if (prog < 88) return 'lead';
    if (prog < 96) return 'pad';
    return 'piano';
  }
  const SR = 32000, TL = 2048;
  function table(harm) {        // harm: [[n, amp], ...]
    const t = new Float32Array(TL);
    for (const [n, a] of harm) for (let i = 0; i < TL; i++) t[i] += a * Math.sin(2 * Math.PI * n * i / TL);
    let m = 0; for (let i = 0; i < TL; i++) m = Math.max(m, Math.abs(t[i]));
    for (let i = 0; i < TL; i++) t[i] /= m || 1;
    return t;
  }
  const H = (k, f) => Array.from({ length: k }, (_, i) => [i + 1, f(i + 1)]);
  const TABLES = {
    piano:   [table(H(10, n => 1 / Math.pow(n, 1.6))), table(H(3, n => 1 / Math.pow(n, 1.6)))],
    bell:    [table([[1, 1], [2, 0.4], [3, 0.25], [5, 0.12]]), table([[1, 1], [2, 0.3]])],
    organ:   [table([[1, 1], [2, 0.8], [3, 0.5], [4, 0.45], [6, 0.3], [8, 0.25]]), table([[1, 1], [2, 0.6]])],
    guitar:  [table(H(12, n => 1 / n * (n % 2 ? 1 : 0.7))), table(H(4, n => 1 / n))],
    bass:    [table([[1, 1], [2, 0.35], [3, 0.12], [4, 0.05]]), table([[1, 1], [2, 0.3]])],
    strings: [table(H(14, n => 1 / n)), table(H(4, n => 1 / n))],
    brass:   [table(H(10, n => 1 / Math.pow(n, 0.9))), table(H(4, n => 1 / n))],
    reed:    [table(H(9, n => n % 2 ? 1 / n : 0.15 / n)), table([[1, 1], [3, 0.3]])],
    lead:    [table(H(11, n => n % 2 ? 1 / n : 0)), table([[1, 1], [3, 0.33]])],
    pad:     [table(H(8, n => 1 / n)), table(H(3, n => 1 / n))]
  };
  // a = attack (s), d = bilis ng paghina (s), s = sustain (0..1), r = release (s), g = lakas
  const ENV = {
    piano:   { a: 0.004, d: 0.8, s: 0.0, r: 0.25, g: 0.5 },
    bell:    { a: 0.002, d: 1.0, s: 0.0, r: 0.5,  g: 0.4 },
    organ:   { a: 0.01,  d: 5,   s: 0.85, r: 0.06, g: 0.22 },
    guitar:  { a: 0.003, d: 0.6, s: 0.1, r: 0.15, g: 0.3 },
    bass:    { a: 0.006, d: 1.2, s: 0.55, r: 0.08, g: 0.7 },
    strings: { a: 0.15,  d: 6,   s: 0.85, r: 0.35, g: 0.17 },
    brass:   { a: 0.04,  d: 1.5, s: 0.7, r: 0.12, g: 0.2 },
    reed:    { a: 0.03,  d: 2.5, s: 0.75, r: 0.1, g: 0.2 },
    lead:    { a: 0.01,  d: 2,   s: 0.7, r: 0.1,  g: 0.16 },
    pad:     { a: 0.35,  d: 6,   s: 0.8, r: 0.6,  g: 0.14 }
  };

  function voice(mix, nt, len) {
    const fam = family(nt.prog, nt.ch);
    const E = ENV[fam] || ENV.piano;
    const f = hz(nt.midi);
    const tab = (TABLES[fam] || TABLES.piano)[f > 900 ? 1 : 0];
    const v = Math.min(1, nt.vel) * E.g;
    const s0 = Math.floor(nt.t * SR);
    const on = Math.max(1, Math.floor(nt.dur * SR));
    const total = Math.min(on + Math.floor(E.r * 3 * SR), len - s0);
    const att = Math.max(1, Math.floor(E.a * SR));
    const dk = Math.exp(-1 / (E.d * SR)), rk = Math.exp(-1 / (E.r * SR));
    const inc = f * TL / SR;
    // pad/strings: pangalawang boses na bahagyang naka-detune para mas malapad
    const det = (fam === 'pad' || fam === 'strings') ? inc * 1.004 : 0;
    let ph = Math.random() * TL, ph2 = Math.random() * TL, dec = 1, lvl = 0, rel = 1;
    for (let k = 0; k < total; k++) {
      if (k < att) lvl = k / att;
      else { dec *= dk; lvl = E.s + (1 - E.s) * dec; }
      if (k >= on) rel *= rk;
      let x = tab[ph | 0];
      if (det) { x = (x + tab[ph2 | 0]) * 0.5; ph2 += det; if (ph2 >= TL) ph2 -= TL; }
      mix[s0 + k] += x * lvl * rel * v;
      ph += inc; if (ph >= TL) ph -= TL;
      if (rel < 0.0005) break;
    }
  }

  function drum(mix, n, t, v, len) {
    const s0 = Math.floor(t * SR);
    const put = (k, x) => { if (s0 + k < len) mix[s0 + k] += x; };
    if (n === 35 || n === 36) {          // kick
      let ph = 0;
      for (let k = 0; k < 0.3 * SR; k++) { const x = k / SR; ph += 2 * Math.PI * (42 + 100 * Math.exp(-x * 28)) / SR; put(k, Math.sin(ph) * Math.exp(-x * 11) * v * 0.9); }
      return;
    }
    if (n >= 41 && n <= 50 && n !== 42 && n !== 44 && n !== 46 && n !== 49) {   // toms
      const base = 70 + (n - 41) * 16; let ph = 0;
      for (let k = 0; k < 0.35 * SR; k++) { const x = k / SR; ph += 2 * Math.PI * base * (1 + 0.6 * Math.exp(-x * 20)) / SR; put(k, Math.sin(ph) * Math.exp(-x * 9) * v * 0.6); }
      return;
    }
    let dec = 0.08, g = 0.25, hp = true, tone = 0;
    if (n === 38 || n === 40 || n === 37 || n === 39) { dec = 0.16; g = 0.5; hp = false; tone = 190; }
    else if (n === 42 || n === 44) { dec = 0.04; g = 0.22; }
    else if (n === 46) { dec = 0.25; g = 0.18; }
    else if (n === 49 || n === 57 || n === 52 || n === 55) { dec = 1.0; g = 0.2; }
    else if (n === 51 || n === 59 || n === 53) { dec = 0.45; g = 0.13; }
    let prev = 0, ph = 0;
    const L = Math.floor(dec * 4 * SR);
    for (let k = 0; k < L; k++) {
      const x = k / SR, w = Math.random() * 2 - 1;
      let y = hp ? w - prev : (w + prev) * 0.5; prev = w;
      let o = y * g;
      if (tone) { ph += 2 * Math.PI * tone / SR; o += Math.sin(ph) * 0.35 * Math.exp(-x * 25); }
      put(k, o * Math.exp(-x / dec) * v);
    }
  }

  // malumanay na reverb (Schroeder: 4 comb + 2 allpass), hinahalo nang kaunti
  function reverb(mix) {
    const n = mix.length, wet = new Float32Array(n);
    [1557, 1617, 1491, 1422].forEach(d => {
      d = Math.floor(d * SR / 44100); const buf = new Float32Array(d); let i = 0;
      for (let k = 0; k < n; k++) { const y = buf[i]; buf[i] = mix[k] + y * 0.78; wet[k] += y * 0.25; if (++i >= d) i = 0; }
    });
    [225, 556].forEach(d => {
      d = Math.floor(d * SR / 44100); const buf = new Float32Array(d); let i = 0;
      for (let k = 0; k < n; k++) { const b = buf[i]; const y = -wet[k] + b; buf[i] = wet[k] + b * 0.5; wet[k] = y; if (++i >= d) i = 0; }
    });
    for (let k = 0; k < n; k++) mix[k] += wet[k] * 0.22;
  }

  const tick = () => new Promise(r => setTimeout(r, 0));
  async function render(parsed, onProgress) {
    const dur = Math.min(parsed.duration, 20 * 60);
    const len = Math.ceil(dur * SR);
    const mix = new Float32Array(len);
    const notes = parsed.notes.filter(n => n.t < dur);
    for (let i = 0; i < notes.length; i++) {
      const nt = notes[i];
      if (nt.ch === 9) drum(mix, nt.midi, nt.t, Math.min(1, nt.vel), len); else voice(mix, nt, len);
      if (i % 400 === 399) { if (onProgress) onProgress(i / notes.length); await tick(); }
    }
    reverb(mix);
    // lakas: i-normalize ayon sa RMS, tapos malambot na limiter
    let sum = 0; for (let k = 0; k < len; k += 4) sum += mix[k] * mix[k];
    const rms = Math.sqrt(sum / (len / 4)) || 1e-3;
    const gain = 0.22 / rms;
    for (let k = 0; k < len; k++) mix[k] = Math.tanh(mix[k] * gain);
    return wav(mix, SR);
  }

  function wav(data, sr) {
    const n = data.length; const ab = new ArrayBuffer(44 + n * 2); const v = new DataView(ab);
    const w = (o, s) => { for (let i = 0; i < s.length; i++) v.setUint8(o + i, s.charCodeAt(i)); };
    w(0, 'RIFF'); v.setUint32(4, 36 + n * 2, true); w(8, 'WAVE'); w(12, 'fmt '); v.setUint32(16, 16, true);
    v.setUint16(20, 1, true); v.setUint16(22, 1, true); v.setUint32(24, sr, true); v.setUint32(28, sr * 2, true); v.setUint16(32, 2, true); v.setUint16(34, 16, true);
    w(36, 'data'); v.setUint32(40, n * 2, true);
    for (let i = 0; i < n; i++) { const s = Math.max(-1, Math.min(1, data[i])); v.setInt16(44 + i * 2, s * 32767, true); }
    return new Blob([ab], { type: 'audio/wav' });
  }

  const isMidi = name => /\.(mid|midi|kar)$/i.test(name || '');
  window.VKMidi = { parse, render, isMidi };
})();
