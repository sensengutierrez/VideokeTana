// ===== Pitch bar (gabay sa tono) =====
// Para sa kantang may melody data: demo song (.mel.json) ngayon, MIDI/KAR sa susunod.
// Format ng notes: [[simula_seg, haba_seg, midi], ...]  (midi 60 = C4)
// Hindi alam ng pitch bar kung saan galing ang notes, kaya pareho ang gamit nito sa demo, MIDI o anumang sariling kanta.
(function () {
  const AHEAD = 4, BEHIND = 1.2;            // segundo na kita sa unahan at likod ng playhead
  let notes = [], lo = 60, hi = 72, cv, g, W = 0, H = 0, trail = [];
  const stat = { n: 0, hit: 0 };

  // ilang semitone ang layo, hindi pinapansin ang octave (lalaki at babae pareho ang tsansa)
  function semis(userMidi, target) {
    let d = (userMidi - target) % 12;
    if (d > 6) d -= 12; if (d < -6) d += 12;
    return d;
  }
  const hzToMidi = hz => 69 + 12 * Math.log2(hz / 440);

  function size() {
    if (!cv) return;
    const r = cv.getBoundingClientRect(), k = Math.min(2, window.devicePixelRatio || 1);
    W = Math.round(r.width * k); H = Math.round(r.height * k);
    if (cv.width !== W) cv.width = W; if (cv.height !== H) cv.height = H;
  }
  const yOf = m => H * 0.88 - (m - lo) / Math.max(1, hi - lo) * H * 0.76;

  window.VKPitch = {
    init(canvas) { cv = canvas; g = cv.getContext('2d'); window.addEventListener('resize', size); },
    load(list) {
      notes = (list || []).filter(n => n && n.length >= 3).sort((a, b) => a[0] - b[0]);
      if (notes.length) { lo = Math.min(...notes.map(n => n[2])) - 2; hi = Math.max(...notes.map(n => n[2])) + 2; }
      trail = []; stat.n = 0; stat.hit = 0;
      size();
      return notes.length > 0;
    },
    clear() { notes = []; trail = []; stat.n = 0; stat.hit = 0; },
    has: () => notes.length > 0,
    noteAt(t) { for (const n of notes) { if (n[0] > t) break; if (t < n[0] + n[1]) return n; } return null; },
    // tawagin nang madalas habang kumakanta: t = oras sa kanta, hz = tono ng mic (0 = walang boses)
    sample(t, hz) {
      const n = this.noteAt(t);
      let hit = false, um = null;
      if (hz > 0) {
        um = hzToMidi(hz);
        if (n) { const d = semis(um, n[2]); um = n[2] + d; hit = Math.abs(d) <= 1; stat.n++; if (hit) stat.hit++; }
        else um = lo + ((um - lo) % 12 + 12) % 12;          // ilagay sa loob ng range
      }
      trail.push({ t, m: um, hit });
      while (trail.length && trail[0].t < t - BEHIND - 0.2) trail.shift();
      return hit;
    },
    // ratio ng tamang tono (0..1), o null kung kulang ang data
    accuracy: () => stat.n >= 30 ? stat.hit / stat.n : null,
    draw(t) {
      if (!g) return;
      if (!W) size();
      g.clearRect(0, 0, W, H);
      if (!notes.length) return;
      const px = W * 0.22, sc = (W - px) / AHEAD;   // pixel bawat segundo
      const xOf = s => px + (s - t) * sc;
      const bh = Math.max(6, H * 0.075);
      // guhit ng bawat note
      for (const n of notes) {
        const x0 = xOf(n[0]), x1 = xOf(n[0] + n[1]);
        if (x1 < 0) continue; if (x0 > W) break;
        const y = yOf(n[2]), active = t >= n[0] && t < n[0] + n[1];
        g.fillStyle = active ? 'rgba(98,227,210,.95)' : (t > n[0] + n[1] ? 'rgba(255,255,255,.18)' : 'rgba(255,255,255,.42)');
        const r = bh / 2, w = Math.max(bh, x1 - x0);
        g.beginPath(); g.moveTo(x0 + r, y - r); g.arcTo(x0 + w, y - r, x0 + w, y + r, r); g.arcTo(x0 + w, y + r, x0, y + r, r);
        g.arcTo(x0, y + r, x0, y - r, r); g.arcTo(x0, y - r, x0 + w, y - r, r); g.fill();
      }
      // playhead
      g.fillStyle = 'rgba(255,255,255,.55)'; g.fillRect(px - 1, H * 0.06, 2, H * 0.88);
      // tono ng kumakanta
      for (const p of trail) {
        if (p.m == null) continue;
        const x = xOf(p.t); if (x < 0 || x > px + 2) continue;
        g.fillStyle = p.hit ? '#ffd54a' : 'rgba(255,120,90,.85)';
        g.beginPath(); g.arc(x, yOf(p.m), bh * 0.42, 0, Math.PI * 2); g.fill();
      }
    }
  };
})();
