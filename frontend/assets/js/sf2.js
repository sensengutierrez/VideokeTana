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
  function makeVoice(sf, nt, r, SR, opts) {
    const g = r.g, s = r.s;
    const start = s.start + gv(g, 0, 0) + 32768 * gv(g, 4, 0);
    const end = s.end + gv(g, 1, 0) + 32768 * gv(g, 12, 0);
    const ls = s.ls + gv(g, 2, 0) + 32768 * gv(g, 45, 0);
    const le = s.le + gv(g, 3, 0) + 32768 * gv(g, 50, 0);
    const mode = gv(g, 54, 0) & 3;
    const root = gv(g, 58, -1) >= 0 ? g[58] : s.pitch;
    const scale = gv(g, 56, 100);
    const semis = (nt.midi - root) * scale / 100 + gv(g, 51, 0) + gv(g, 52, 0) / 100 + s.corr / 100 + (nt.bend || 0);
    const step = Math.pow(2, semis / 12) * s.rate / SR;
    // lakas: attenuation (cB, ×0.4 gaya ng karamihan ng player), velocity at channel (concave)
    const att = gv(g, 48, 0) * 0.4;
    const vel = (nt.v || 100) / 127;
    let amp = Math.pow(10, -att / 200) * vel * vel * Math.max(0.0001, nt.cg === undefined ? 0.8 : nt.cg) * opts.master;
    // pan: zone + channel
    const pz = Math.max(-500, Math.min(500, gv(g, 17, 0) + ((nt.pan === undefined ? 64 : nt.pan) - 64) / 64 * 500));
    const pa = (pz + 500) / 1000 * Math.PI / 2;
    const fc = gv(g, 8, 13500);
    const cut = fc < 13400 ? Math.min(0.99, 1 - Math.exp(-2 * Math.PI * 8.176 * Math.pow(2, fc / 1200) / SR)) : 1;
    const key = nt.midi;
    const hold = tc(gv(g, 35, -12000) + gv(g, 39, 0) * (60 - key), -12000);
    const decay = tc(gv(g, 36, -12000) + gv(g, 40, 0) * (60 - key), -12000);
    const sus = Math.max(0, Math.min(1440, gv(g, 37, 0)));
    const lim = sf.samples.length - 2;
    if (!(step > 0 && step < 64) || start < 0 || start >= lim) return null;
    const endC = Math.min(lim, Math.max(start + 1, end));
    return {
      pos: start, start, end: endC, ls: Math.max(start, ls), le: Math.min(endC, Math.max(ls + 2, le)), loop: (mode === 1 || mode === 3) && le > ls + 2, mode,
      step, L: amp * Math.cos(pa), R: amp * Math.sin(pa), cut, lp: 0,
      delay: tc(g[33], -12000), attack: tc(g[34], -12000), hold, decay, susDb: sus / 10, release: Math.min(opts.maxRelease, tc(g[38], -12000)),
      t0: nt.t, off: nt.t + nt.dur, done: false, relStart: -1, relDb: 0, excl: gv(g, 57, 0), ch: nt.ch
    };
  }

  // antas ng envelope (0..1) sa oras na x mula sa simula ng nota
  function envAt(v, x) {
    if (v.relStart >= 0) {
      const rx = x - v.relStart;
      if (rx < 0) return Math.pow(10, -Math.max(0, v.curDb || 0) / 20);      // hindi pa oras ng release (exclusive class sa hinaharap)
      const db = Math.max(0, v.relDb) + 100 * rx / Math.max(0.001, v.release);
      if (db >= 96) { v.done = true; return 0; }
      return Math.pow(10, -db / 20);
    }
    let db;
    if (x < v.delay) return 0;
    x -= v.delay;
    if (x < v.attack) return x / v.attack;
    x -= v.attack;
    if (x < v.hold) db = 0;
    else { x -= v.hold; db = Math.min(v.susDb, 100 * x / Math.max(0.001, v.decay)); }
    v.curDb = db;
    return Math.pow(10, -db / 20);
  }

  async function render(mp, sf, opts) {
    opts = Object.assign({ SR: 44100, master: 1.0, maxRelease: 2.5, reverb: 0.18, onProgress: null }, opts || {});
    const SR = opts.SR, BLK = 32;
    const dur = Math.min(mp.duration + 1.5, 20 * 60);
    const total = Math.ceil(dur * SR);
    const out = new Int16Array(total * 2);
    const notes = mp.notes.slice().sort((a, b) => a.t - b.t);
    const data = sf.samples;
    const active = [];
    let ni = 0;
    const chunk = SR * 2;                     // 2 segundo bawat ikot
    const mixL = new Float32Array(chunk), mixR = new Float32Array(chunk);
    // reverb (Schroeder, stereo na bahagyang magkaiba)
    const combs = [1557, 1617, 1491, 1422, 1277, 1356].map((d, i) => ({ buf: new Float32Array(Math.floor(d * SR / 44100) + (i % 2) * 23), i: 0, side: i % 2 }));
    const aps = [225, 556, 441].map(d => ({ buf: new Float32Array(Math.floor(d * SR / 44100)), i: 0 }));
    for (let c0 = 0; c0 < total; c0 += chunk) {
      const n = Math.min(chunk, total - c0);
      mixL.fill(0); mixR.fill(0);
      const tEnd = (c0 + n) / SR;
      // mga bagong nota sa bahaging ito
      while (ni < notes.length && notes[ni].t < tEnd) {
        const nt = notes[ni++];
        const rs = regions(sf, nt.bank || 0, nt.prog || 0, nt.midi, nt.v || 100, nt.ch === 9);
        for (const r of rs) {
          if (!r.s) continue;
          const v = makeVoice(sf, nt, r, SR, opts);
          if (!v) continue;
          // exclusive class (hal. bukas/saradong hi-hat): pinapatay ang luma
          if (v.excl) for (const a of active) if (a.excl === v.excl && a.ch === v.ch && a.relStart < 0) { a.relStart = Math.max(0, nt.t - a.t0); a.relDb = a.curDb || 0; a.release = Math.min(a.release, 0.05); }
          active.push(v);
        }
      }
      for (let ai = active.length - 1; ai >= 0; ai--) {
        const v = active[ai];
        let k = Math.max(0, Math.floor(v.t0 * SR) - c0);
        if (k >= n) continue;
        let pos = v.pos, lp = v.lp;
        while (k < n && !v.done) {
          const x = (c0 + k) / SR - v.t0;
          if (v.relStart < 0 && c0 / SR + k / SR >= v.off) { v.relStart = x; v.relDb = v.curDb || 0; }
          const e = envAt(v, x);
          const kEnd = Math.min(n, k + BLK);
          const gl = v.L * e, gr = v.R * e;
          for (; k < kEnd; k++) {
            const ip = pos | 0;
            if (ip + 1 >= v.end) { v.done = true; break; }
            const fr = pos - ip;
            let smp = (data[ip] + (data[ip + 1] - data[ip]) * fr) / 32768;
            if (v.cut < 1) { lp += (smp - lp) * v.cut; smp = lp; }
            if (smp !== smp) { v.done = true; break; }
            mixL[k] += smp * gl; mixR[k] += smp * gr;
            pos += v.step;
            if (v.loop && !(v.mode === 3 && v.relStart >= 0) && pos >= v.le) pos -= (v.le - v.ls);
          }
          if (e === 0 && x > v.delay + 0.01 && v.relStart >= 0) v.done = true;
        }
        v.pos = pos; v.lp = lp;
        if (v.done) active.splice(ai, 1);
      }
      // reverb + limiter
      for (let k = 0; k < n; k++) {
        const inp = (mixL[k] + mixR[k]) * 0.5;
        let wl = 0, wr = 0;
        for (const cb of combs) { const y = cb.buf[cb.i]; cb.buf[cb.i] = inp + y * 0.8; if (++cb.i >= cb.buf.length) cb.i = 0; if (cb.side) wr += y; else wl += y; }
        for (const ap of aps) { const b = ap.buf[ap.i]; const y = -wl + b; ap.buf[ap.i] = wl + b * 0.5; wl = y; if (++ap.i >= ap.buf.length) ap.i = 0; }
        const l = Math.tanh(mixL[k] + wl * opts.reverb * 0.3), r = Math.tanh(mixR[k] + wr * opts.reverb * 0.3);
        out[(c0 + k) * 2] = l * 32767; out[(c0 + k) * 2 + 1] = r * 32767;
      }
      if (opts.onProgress) opts.onProgress(Math.min(1, (c0 + n) / total));
      await new Promise(r => setTimeout(r, 0));
    }
    return wav16(out, SR, 2);
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
  const DEFAULT_URL = (window.VK_SITE || '') + '/sf/GeneralUser-GS.sf2';
  let loading = null;
  async function load(url) {
    if (loading) return loading;
    loading = (async () => {
      let ab = null;
      try {
        if (window.VKLocal && VKLocal.getSoundfont) { const own = await VKLocal.getSoundfont(); if (own) ab = await own.arrayBuffer(); }
      } catch (e) { }
      if (!ab) {
        const u = url || new URL('../sf/GeneralUser-GS.sf2', document.baseURI).href;
        let res = null;
        try { const c = await caches.open('vk-sf'); res = await c.match(u); if (!res) { res = await fetch(u); if (res.ok) await c.put(u, res.clone()); } } catch (e) { res = await fetch(u); }
        if (!res || !res.ok) throw new Error('Hindi ma-download ang SoundFont');
        ab = await res.arrayBuffer();
      }
      return parse(ab);
    })();
    loading.catch(() => { loading = null; });
    return loading;
  }

  window.VKSF2 = { parse, render, load, regions, DEFAULT_URL };
})();
