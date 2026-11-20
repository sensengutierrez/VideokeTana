/* VideokeTana SoundFont (SF2) engine para sa web.
 * VKSF2.parse(ArrayBuffer) → soundfont
 * VKSF2.render(midiParsed, sf, opts) → Promise<Blob> (stereo WAV)
 * Ang render ay paisa-isang block (hindi buong kanta sa memory nang sabay-sabay ang mga voice), may limiter sa dulo.
 * Kapareho ng algorithm ng Android (tv/Sf2.kt) para pareho ang tunog.
 */
(function () {
  'use strict';

  // ---------------- basa ng SF2 ----------------
  function parse(ab) {
    const dv = new DataView(ab);
    const str = (o, n) => { let s = ''; for (let i = 0; i < n; i++) { const c = dv.getUint8(o + i); if (!c) break; s += String.fromCharCode(c); } return s; };
    if (str(0, 4) !== 'RIFF' || str(8, 4) !== 'sfbk') throw new Error('Hindi SoundFont (.sf2) ang file');
    const chunks = {};
    let smplOff = 0, smplLen = 0;
    (function walk(o, end) {
      while (o + 8 <= end) {
        const id = str(o, 4), len = dv.getUint32(o + 4, true);
        if (id === 'LIST') walk(o + 12, o + 8 + len);
        else if (id === 'smpl') { smplOff = o + 8; smplLen = len; }
        else chunks[id] = { o: o + 8, len };
        o += 8 + len + (len & 1);
      }
    })(12, ab.byteLength);
    const need = ['phdr', 'pbag', 'pgen', 'inst', 'ibag', 'igen', 'shdr'];
    need.forEach(k => { if (!chunks[k]) throw new Error('Sirang SoundFont: walang ' + k); });
    const recs = (k, size, fn) => { const c = chunks[k], out = []; for (let o = c.o; o + size <= c.o + c.len; o += size) out.push(fn(o)); return out; };
    const phdr = recs('phdr', 38, o => ({ name: str(o, 20), preset: dv.getUint16(o + 20, true), bank: dv.getUint16(o + 22, true), bag: dv.getUint16(o + 24, true) }));
    const pbag = recs('pbag', 4, o => dv.getUint16(o, true));
    const pgen = recs('pgen', 4, o => [dv.getUint16(o, true), dv.getInt16(o + 2, true), dv.getUint8(o + 2), dv.getUint8(o + 3)]);
    const inst = recs('inst', 22, o => ({ name: str(o, 20), bag: dv.getUint16(o + 20, true) }));
    const ibag = recs('ibag', 4, o => dv.getUint16(o, true));
    const igen = recs('igen', 4, o => [dv.getUint16(o, true), dv.getInt16(o + 2, true), dv.getUint8(o + 2), dv.getUint8(o + 3)]);
    const shdr = recs('shdr', 46, o => ({ name: str(o, 20), start: dv.getUint32(o + 20, true), end: dv.getUint32(o + 24, true), ls: dv.getUint32(o + 28, true), le: dv.getUint32(o + 32, true),
      rate: dv.getUint32(o + 36, true), pitch: dv.getUint8(o + 40), corr: dv.getInt8(o + 41), type: dv.getUint16(o + 44, true) }));
    // mga zone: { gens: Map(op → value), lo, hi, vlo, vhi, ref (instrument o sample) }
    function zones(bags, gens, i0, i1, refOp) {
      const out = []; let global = null;
      for (let b = i0; b < i1; b++) {
        const g = {}; let lo = 0, hi = 127, vlo = 0, vhi = 127, ref = -1;
        for (let k = bags[b]; k < bags[b + 1]; k++) {
          const [op, amt, l, h] = gens[k];
          if (op === 43) { lo = l; hi = h; } else if (op === 44) { vlo = l; vhi = h; }
          else if (op === refOp) ref = amt & 0xffff;
          else g[op] = amt;
        }
        const z = { g, lo, hi, vlo, vhi, ref };
        if (ref < 0) { if (b === i0) global = z; } else out.push(z);
      }
      return { global, zones: out };
    }
    const instruments = inst.slice(0, -1).map((x, i) => zones(ibag, igen, x.bag, inst[i + 1].bag, 53));
    const presets = {};
    phdr.slice(0, -1).forEach((x, i) => { presets[x.bank * 128 + x.preset] = zones(pbag, pgen, x.bag, phdr[i + 1].bag, 41); });
    const samples = new Int16Array(ab.slice(smplOff, smplOff + (smplLen & ~1)));
    return { presets, instruments, shdr, samples, name: str(chunks.INAM ? chunks.INAM.o : 0, chunks.INAM ? Math.min(64, chunks.INAM.len) : 0) };
  }

  // ---------------- paghahanap ng tunog para sa isang nota ----------------
  const ADDITIVE_SKIP = new Set([0, 1, 2, 3, 4, 12, 45, 50, 41, 53, 54, 57, 58, 46, 47, 43, 44]);
  function regions(sf, bank, prog, key, vel, drum) {
    let p = sf.presets[(drum ? 128 : bank) * 128 + prog] || sf.presets[(drum ? 128 : 0) * 128 + prog] || (drum ? sf.presets[128 * 128] : null) || sf.presets[0];
    if (!p) return [];
    const out = [];
    for (const pz of p.zones) {
      if (key < pz.lo || key > pz.hi || vel < pz.vlo || vel > pz.vhi) continue;
      const ins = sf.instruments[pz.ref]; if (!ins) continue;
      for (const iz of ins.zones) {
        if (key < iz.lo || key > iz.hi || vel < iz.vlo || vel > iz.vhi) continue;
        const g = Object.assign({}, ins.global ? ins.global.g : {}, iz.g);
        const add = Object.assign({}, p.global ? p.global.g : {}, pz.g);
        for (const k in add) if (!ADDITIVE_SKIP.has(+k)) g[k] = (g[k] || 0) + add[k];
        out.push({ g, s: sf.shdr[iz.ref] });
      }
    }
    return out;
  }

  const tc = (v, d) => Math.pow(2, (v === undefined ? d : v) / 1200);     // timecents → segundo
  const gv = (g, k, d) => (g[k] === undefined ? d : g[k]);

  // ---------------- voice ----------------
  // Mga generator ng SF2 na ginagamit: sample/loop offsets, tuning, volume envelope, modulation envelope (→ filter at pitch),
  // vibrato LFO (→ pitch), modulation LFO (→ pitch, filter, lakas), resonant lowpass filter, pan, reverb/chorus send, exclusive class.
  const cents = c => Math.pow(2, c / 1200);
  function makeVoice(sf, nt, r, SR, opts) {
    const g = r.g, s = r.s;
    const start = s.start + gv(g, 0, 0) + 32768 * gv(g, 4, 0);
    const end = s.end + gv(g, 1, 0) + 32768 * gv(g, 12, 0);
    const ls = s.ls + gv(g, 2, 0) + 32768 * gv(g, 45, 0);
    const le = s.le + gv(g, 3, 0) + 32768 * gv(g, 50, 0);
    const lim = sf.samples.length - 3;
    const mode = gv(g, 54, 0) & 3;
    const root = gv(g, 58, -1) >= 0 ? g[58] : s.pitch;
    const key = nt.midi + (nt.ch === 9 ? 0 : (opts.transpose || 0));
    const basePitch = (key - root) * gv(g, 56, 100) / 100 + gv(g, 51, 0) + gv(g, 52, 0) / 100 + s.corr / 100;
    const rate = s.rate / SR;
    if (!(rate > 0) || start < 1 || start >= lim) return null;
    const endC = Math.min(lim, Math.max(start + 2, end));
    // lakas: attenuation ×0.4 (gaya ng karamihan ng player), velocity (concave), channel
    const att = gv(g, 48, 0) * 0.4;
    const vel = (nt.v || 100) / 127;
    const amp = Math.pow(10, -att / 200) * vel * vel * opts.master;
    const pz = Math.max(-500, Math.min(500, gv(g, 17, 0) + ((nt.pan === undefined ? 64 : nt.pan) - 64) / 64 * 500));
    const pa = (pz + 500) / 1000 * Math.PI / 2;
    // filter: cutoff (absolute cents) + velocity (malambot na tugtog = mas mapurol), resonance (cB)
    let fc = gv(g, 8, 13500);
    const filtered = fc < 13400 || gv(g, 11, 0) !== 0 || gv(g, 10, 0) !== 0;
    if (filtered) fc += -2400 * (1 - vel) * 0.5;
    const q = Math.max(0, gv(g, 9, 0)) / 10;               // dB
    const kn = nt.midi;
    return {
      pos: start, end: endC, ls: Math.max(start, ls), le: Math.min(endC, Math.max(ls + 2, le)), loop: (mode === 1 || mode === 3) && le > ls + 2, mode,
      rate, basePitch, L: amp * Math.cos(pa), R: amp * Math.sin(pa),
      rvb: Math.min(1, (nt.rvb === undefined ? 0.3 : nt.rvb) + gv(g, 16, 0) / 1000), cho: Math.min(1, (nt.cho || 0) + gv(g, 15, 0) / 1000),
      filtered, fc, q: Math.pow(10, q / 20) * 0.7071, x1: 0, x2: 0, y1: 0, y2: 0,
      // volume envelope
      delay: tc(g[33], -12000), attack: tc(g[34], -12000), hold: tc(gv(g, 35, -12000) + gv(g, 39, 0) * (60 - kn), -12000),
      decay: tc(gv(g, 36, -12000) + gv(g, 40, 0) * (60 - kn), -12000), susDb: Math.max(0, Math.min(1440, gv(g, 37, 0))) / 10,
      release: Math.min(opts.maxRelease, tc(g[38], -12000)),
      // modulation envelope
      mDelay: tc(g[25], -12000), mAttack: tc(g[26], -12000), mHold: tc(gv(g, 27, -12000) + gv(g, 31, 0) * (60 - kn), -12000),
      mDecay: tc(gv(g, 28, -12000) + gv(g, 32, 0) * (60 - kn), -12000), mSus: 1 - Math.max(0, Math.min(1000, gv(g, 29, 0))) / 1000,
      mRelease: tc(g[30], -12000), mToFc: gv(g, 11, 0), mToPitch: gv(g, 7, 0), mLevel: 0, mRelLevel: 0,
      // LFO
      vDelay: tc(g[23], -12000), vFreq: 8.176 * cents(gv(g, 24, 0)), vToPitch: gv(g, 6, 0),          // vibrato LFO
      lDelay: tc(g[21], -12000), lFreq: 8.176 * cents(gv(g, 22, 0)), lToPitch: gv(g, 5, 0), lToFc: gv(g, 10, 0), lToVol: gv(g, 13, 0),   // modulation LFO
      t0: nt.t, off: nt.t + nt.dur, done: false, relStart: -1, relDb: 0, curDb: 0, excl: gv(g, 57, 0), ch: nt.ch
    };
  }

  // volume envelope (0..1) sa oras na x mula sa simula ng nota
  function envAt(v, x) {
    if (v.relStart >= 0) {
      const rx = x - v.relStart;
      if (rx < 0) return Math.pow(10, -Math.max(0, v.curDb) / 20);
      const db = Math.max(0, v.relDb) + 100 * rx / Math.max(0.001, v.release);
      if (db >= 96) { v.done = true; return 0; }
      return Math.pow(10, -db / 20);
    }
    if (x < v.delay) return 0;
    x -= v.delay;
    if (x < v.attack) { v.curDb = 0; return x / v.attack; }
    x -= v.attack;
    const db = x < v.hold ? 0 : Math.min(v.susDb, 100 * (x - v.hold) / Math.max(0.001, v.decay));
    v.curDb = db;
    return Math.pow(10, -db / 20);
  }
  // modulation envelope (0..1)
  function modAt(v, x) {
    if (v.relStart >= 0) { const rx = Math.max(0, x - v.relStart); return v.mRelLevel * Math.max(0, 1 - rx / Math.max(0.001, v.mRelease)); }
    if (x < v.mDelay) return 0;
    x -= v.mDelay;
    let lv;
    if (x < v.mAttack) lv = x / v.mAttack;
    else { x -= v.mAttack; lv = x < v.mHold ? 1 : Math.max(v.mSus, 1 - (1 - v.mSus) * (x - v.mHold) / Math.max(0.001, v.mDecay)); }
    v.mLevel = lv;
    return lv;
  }
  const valAt = (arr, t) => { if (!arr) return 0; let lo = 0, hi = arr.length - 1; while (lo < hi) { const m = (lo + hi + 1) >> 1; if (arr[m][0] <= t) lo = m; else hi = m - 1; } return arr[lo][1]; };

  async function render(mp, sf, opts) {
    opts = Object.assign({ SR: 44100, master: 1.3, maxRelease: 3, reverb: 1, chorus: 1, transpose: 0, onProgress: null }, opts || {});
    const SR = opts.SR, BLK = 64;
    const dur = Math.min(mp.duration + 2, 20 * 60);
    const total = Math.ceil(dur * SR);
    const out = new Int16Array(total * 2);
    const notes = mp.notes.slice().sort((a, b) => a.t - b.t);
    const ctl = mp.ctl || {};
    const data = sf.samples;
    const active = [];
    let ni = 0;
    const chunk = SR;
    const mixL = new Float32Array(chunk), mixR = new Float32Array(chunk), rvIn = new Float32Array(chunk), chL = new Float32Array(chunk), chR = new Float32Array(chunk);
    const fx = makeFx(SR);
    for (let c0 = 0; c0 < total; c0 += chunk) {
      const n = Math.min(chunk, total - c0);
      mixL.fill(0); mixR.fill(0); rvIn.fill(0); chL.fill(0); chR.fill(0);
      const tEnd = (c0 + n) / SR;
      while (ni < notes.length && notes[ni].t < tEnd) {
        const nt = notes[ni++];
        for (const r of regions(sf, nt.bank || 0, nt.prog || 0, nt.midi, nt.v || 100, nt.ch === 9)) {
          if (!r.s) continue;
          const v = makeVoice(sf, nt, r, SR, opts);
          if (!v) continue;
          if (v.excl) for (const a of active) if (a.excl === v.excl && a.ch === v.ch && a.relStart < 0 && a.t0 < v.t0) { a.relStart = Math.max(0, v.t0 - a.t0); a.relDb = a.curDb; a.mRelLevel = a.mLevel; a.release = Math.min(a.release, 0.04); }
          active.push(v);
        }
      }
      for (let ai = active.length - 1; ai >= 0; ai--) {
        const v = active[ai];
        let k = Math.max(0, Math.floor(v.t0 * SR) - c0);
        if (k >= n) continue;
        let pos = v.pos;
        while (k < n && !v.done) {
          const tAbs = (c0 + k) / SR, x = tAbs - v.t0;
          if (v.relStart < 0 && tAbs >= v.off) { v.relStart = x; v.relDb = v.curDb; v.mRelLevel = v.mLevel; }
          let e = envAt(v, x);
          const me = modAt(v, x);
          // LFO
          const vib = x > v.vDelay ? Math.sin(2 * Math.PI * v.vFreq * (x - v.vDelay)) : 0;
          const lfo = x > v.lDelay ? Math.sin(2 * Math.PI * v.lFreq * (x - v.lDelay)) : 0;
          const modW = valAt(ctl.mod && ctl.mod[v.ch], tAbs);
          const pitchC = v.basePitch * 100 + valAt(ctl.bend && ctl.bend[v.ch], tAbs) * 100 + me * v.mToPitch + vib * (v.vToPitch + modW * 50) + lfo * v.lToPitch;
          const step = v.rate * cents(pitchC);
          if (v.lToVol) e *= Math.pow(10, -(lfo * v.lToVol) / 200);
          const cg = (ctl.gain && ctl.gain[v.ch]) ? valAt(ctl.gain[v.ch], tAbs) : 0.8;
          const gl = v.L * e * cg, gr = v.R * e * cg;
          // biquad lowpass (kinukuwenta bawat block)
          let b0 = 1, b1 = 0, b2 = 0, a1 = 0, a2 = 0, filt = false;
          if (v.filtered) {
            const fHz = Math.min(SR * 0.45, 8.176 * cents(v.fc + me * v.mToFc + lfo * v.lToFc));
            if (fHz < SR * 0.44) {
              const w = 2 * Math.PI * fHz / SR, al = Math.sin(w) / (2 * v.q), cw = Math.cos(w), a0 = 1 + al;
              b1 = (1 - cw) / a0; b0 = b1 / 2; b2 = b0; a1 = -2 * cw / a0; a2 = (1 - al) / a0; filt = true;
            }
          }
          const kEnd = Math.min(n, k + BLK);
          for (; k < kEnd; k++) {
            const ip = pos | 0;
            if (ip + 2 >= v.end) { v.done = true; break; }
            // 4-point (Hermite) interpolation: mas malinis kaysa linear, lalo na sa mataas na nota
            const fr = pos - ip;
            const xm1 = data[ip - 1], x0 = data[ip], x1 = data[ip + 1], x2 = data[ip + 2];
            const c1 = 0.5 * (x1 - xm1), c2 = xm1 - 2.5 * x0 + 2 * x1 - 0.5 * x2, c3 = 0.5 * (x2 - xm1) + 1.5 * (x0 - x1);
            let smp = (((c3 * fr + c2) * fr + c1) * fr + x0) / 32768;
            if (filt) { const y = b0 * smp + b1 * v.x1 + b2 * v.x2 - a1 * v.y1 - a2 * v.y2; v.x2 = v.x1; v.x1 = smp; v.y2 = v.y1; v.y1 = y; smp = y; }
            const l = smp * gl, r = smp * gr;
            mixL[k] += l; mixR[k] += r;
            rvIn[k] += (l + r) * v.rvb; chL[k] += l * v.cho; chR[k] += r * v.cho;
            pos += step;
            if (v.loop && !(v.mode === 3 && v.relStart >= 0) && pos >= v.le) pos -= (v.le - v.ls);
          }
        }
        v.pos = pos;
        if (v.done) active.splice(ai, 1);
      }
      fx.process(mixL, mixR, rvIn, chL, chR, n, opts);
      for (let k = 0; k < n; k++) {
        out[(c0 + k) * 2] = softClip(mixL[k]) * 32767; out[(c0 + k) * 2 + 1] = softClip(mixR[k]) * 32767;
      }
      if (opts.onProgress) opts.onProgress(Math.min(1, (c0 + n) / total));
      await new Promise(r => setTimeout(r, 0));
    }
    return wav16(out, SR, 2);
  }
  // malambot na limiter: tuwid hanggang 0.7, tapos pakurba (hindi "lata" na pumapalo)
  const softClip = x => { const a = Math.abs(x); if (a <= 0.7) return x; return Math.sign(x) * (0.7 + 0.3 * Math.tanh((a - 0.7) / 0.3)); };

  // ---------------- effects: Freeverb (stereo) + chorus ----------------
  function makeFx(SR) {
    const sc = SR / 44100;
    const combL = [1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617].map(d => ({ b: new Float32Array(Math.round(d * sc)), i: 0, f: 0 }));
    const combR = [1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617].map(d => ({ b: new Float32Array(Math.round((d + 23) * sc)), i: 0, f: 0 }));
    const apL = [556, 441, 341, 225].map(d => ({ b: new Float32Array(Math.round(d * sc)), i: 0 }));
    const apR = [556, 441, 341, 225].map(d => ({ b: new Float32Array(Math.round((d + 23) * sc)), i: 0 }));
    const room = 0.84, damp = 0.25, wet = 0.32;
    const cLen = Math.round(0.05 * SR), cbL = new Float32Array(cLen), cbR = new Float32Array(cLen); let ci = 0, ph = 0;
    function comb(c, x) { const y = c.b[c.i]; c.f = y * (1 - damp) + c.f * damp; c.b[c.i] = x + c.f * room; if (++c.i >= c.b.length) c.i = 0; return y; }
    function ap(a, x) { const bo = a.b[a.i]; const y = -x + bo; a.b[a.i] = x + bo * 0.5; if (++a.i >= a.b.length) a.i = 0; return y; }
    return {
      process(L, R, rv, chL, chR, n, opts) {
        const rw = wet * opts.reverb, cw = 0.5 * opts.chorus;
        for (let k = 0; k < n; k++) {
          const inp = rv[k] * 0.015 * 2;
          let ol = 0, or = 0;
          for (let j = 0; j < 8; j++) { ol += comb(combL[j], inp); or += comb(combR[j], inp); }
          for (let j = 0; j < 4; j++) { ol = ap(apL[j], ol); or = ap(apR[j], or); }
          // chorus: dalawang delay na dahan-dahang gumagalaw (kabaligtaran ang L at R)
          cbL[ci] = chL[k]; cbR[ci] = chR[k];
          const m = Math.sin(ph) * 0.004 * SR, base = 0.018 * SR;
          const rl = (ci - (base + m) + cLen) % cLen, rr = (ci - (base - m) + cLen) % cLen;
          const il = rl | 0, ir = rr | 0, fl = rl - il, fr = rr - ir;
          const cl = cbL[il] * (1 - fl) + cbL[(il + 1) % cLen] * fl, cr = cbR[ir] * (1 - fr) + cbR[(ir + 1) % cLen] * fr;
          ph += 2 * Math.PI * 0.45 / SR; if (++ci >= cLen) ci = 0;
          L[k] += ol * rw + cl * cw; R[k] += or * rw + cr * cw;
        }
      }
    };
  }

  function wav16(pcm, sr, ch) {
    const n = pcm.length, ab = new ArrayBuffer(44 + n * 2), v = new DataView(ab);
    const w = (o, s) => { for (let i = 0; i < s.length; i++) v.setUint8(o + i, s.charCodeAt(i)); };
    w(0, 'RIFF'); v.setUint32(4, 36 + n * 2, true); w(8, 'WAVE'); w(12, 'fmt '); v.setUint32(16, 16, true);
    v.setUint16(20, 1, true); v.setUint16(22, ch, true); v.setUint32(24, sr, true); v.setUint32(28, sr * ch * 2, true); v.setUint16(32, ch * 2, true); v.setUint16(34, 16, true);
    w(36, 'data'); v.setUint32(40, n * 2, true);
    new Int16Array(ab, 44).set(pcm);
    return new Blob([ab], { type: 'audio/wav' });
  }

  // ---------------- pag-load (naka-cache sa browser) ----------------
  // Kasama: GeneralUser GS (nasa site: /sf/). Sariling .sf2: naka-save sa Cache Storage ng device na ito ("own").
  const OWN_KEY = '/__vk-own.sf2';
  let loading = null, loadedKey = '';
  function choice() { try { return (window.VK_PREFS && VK_PREFS.get().sfChoice) || 'builtin'; } catch (e) { return 'builtin'; } }
  async function load() {
    const key = choice();
    if (loading && loadedKey === key) return loading;
    loadedKey = key;
    loading = (async () => {
      let ab = null;
      const c = await caches.open('vk-sf').catch(() => null);
      if (key === 'own' && c) { const r = await c.match(OWN_KEY); if (r) ab = await r.arrayBuffer(); }
      if (!ab) {
        const u = new URL('../sf/GeneralUser-GS.sf2', document.baseURI).href;
        let res = c ? await c.match(u) : null;
        if (!res) { res = await fetch(u); if (!res.ok) throw new Error('Hindi ma-download ang SoundFont (HTTP ' + res.status + ')'); if (c) await c.put(u, res.clone()); }
        ab = await res.arrayBuffer();
      }
      return parse(ab);
    })();
    loading.catch(() => { loading = null; });
    return loading;
  }
  async function saveOwn(file) {
    const ab = await file.arrayBuffer();
    parse(ab);                                  // susuriin muna kung tunay na SF2
    const c = await caches.open('vk-sf');
    await c.put(OWN_KEY, new Response(ab, { headers: { 'Content-Type': 'application/octet-stream' } }));
    if (window.VK_PREFS) { VK_PREFS.set('sfOwnName', file.name); VK_PREFS.set('sfChoice', 'own'); }
    loading = null;
  }
  async function clearOwn() {
    const c = await caches.open('vk-sf'); await c.delete(OWN_KEY);
    if (window.VK_PREFS) { VK_PREFS.set('sfOwnName', ''); VK_PREFS.set('sfChoice', 'builtin'); }
    loading = null;
  }
  // maikling tugtog para subukan: piano, strings, bass at drums
  function testSong() {
    const notes = [], add = (t, dur, midi, ch, prog, v) => notes.push({ t, dur, midi, ch, prog, v: v || 100, cg: 0.8, bank: 0, pan: 64, bend: 0 });
    [[60, 64, 67], [57, 60, 64], [53, 57, 60], [55, 59, 62]].forEach((c, i) => {
      const t = i * 1.4;
      c.forEach(m => add(t, 1.3, m, 0, 0)); c.forEach(m => add(t, 1.4, m + 12, 1, 48, 60));
      add(t, 0.6, c[0] - 24, 2, 33); add(t + 0.7, 0.6, c[0] - 24, 2, 33);
      add(t, 0.2, 36, 9, 0); add(t + 0.7, 0.2, 38, 9, 0);
      for (let h = 0; h < 4; h++) add(t + h * 0.35, 0.1, 42, 9, 0, 70);
    });
    add(5.6, 1.2, 72, 0, 0); add(5.6, 0.2, 49, 9, 0);
    return { notes: notes.sort((a, b) => a.t - b.t), lyrics: [], melody: [], duration: 7 };
  }
  async function test() {
    const sf = await load();
    const blob = await render(testSong(), sf, {});
    const a = new Audio(URL.createObjectURL(blob)); await a.play();
    return Object.keys(sf.presets).length;
  }

  window.VKSF2 = { parse, render, load, regions, saveOwn, clearOwn, test };
})();
