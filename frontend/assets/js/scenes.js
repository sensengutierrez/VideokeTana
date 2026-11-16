// ===== Background videos (animated, ginuguhit sa canvas) =====
// VKScenes.start(canvas) / set(name) / next() / pause() / resume()
// Mga eksena: city (gabi sa lungsod), sea (paglubog ng araw sa dagat), sky (bituin at aurora)
(function () {
  const W = 960, H = 540;              // internal na resolution, pinapalaki ng CSS
  let cv, ctx, raf = 0, running = false, t0 = 0, scene = null, name = 'city';
  const reduce = matchMedia('(prefers-reduced-motion: reduce)').matches;
  const rnd = (a, b) => a + Math.random() * (b - a);

  // ---------- CITY: bokeh lights + skyline ----------
  function makeCity() {
    const colors = ['255,190,110', '255,140,90', '120,200,255', '255,230,160', '200,140,255', '98,227,210'];
    const sprites = colors.map(c => {
      const s = document.createElement('canvas'); s.width = s.height = 128;
      const g = s.getContext('2d'), gr = g.createRadialGradient(64, 64, 0, 64, 64, 64);
      gr.addColorStop(0, `rgba(${c},.9)`); gr.addColorStop(.55, `rgba(${c},.35)`); gr.addColorStop(1, `rgba(${c},0)`);
      g.fillStyle = gr; g.fillRect(0, 0, 128, 128); return s;
    });
    const dots = Array.from({ length: 46 }, () => ({
      x: rnd(0, W), y: rnd(0, H * .8), r: rnd(18, 70), s: sprites[Math.floor(rnd(0, sprites.length))],
      vx: rnd(-6, 6), vy: rnd(-3, 3), ph: rnd(0, 6.28), sp: rnd(.3, .9)
    }));
    // skyline (isang beses lang iguguhit)
    const sky = document.createElement('canvas'); sky.width = W; sky.height = H;
    const g = sky.getContext('2d');
    let x = 0;
    while (x < W) {
      const bw = rnd(28, 70), bh = rnd(70, 230);
      g.fillStyle = '#07090f'; g.fillRect(x, H - bh, bw, bh);
      for (let wy = H - bh + 8; wy < H - 6; wy += 9) for (let wx = x + 5; wx < x + bw - 5; wx += 8)
        if (Math.random() < .28) { g.fillStyle = `rgba(255,${Math.floor(rnd(190, 230))},${Math.floor(rnd(120, 170))},${rnd(.35, .8)})`; g.fillRect(wx, wy, 3, 4); }
      x += bw + rnd(0, 6);
    }
    return (t) => {
      const bg = ctx.createLinearGradient(0, 0, 0, H);
      bg.addColorStop(0, '#0b1026'); bg.addColorStop(.6, '#1a1235'); bg.addColorStop(1, '#2a1426');
      ctx.fillStyle = bg; ctx.fillRect(0, 0, W, H);
      ctx.globalCompositeOperation = 'lighter';
      for (const d of dots) {
        d.x += d.vx * .016; d.y += d.vy * .016;
        if (d.x < -80) d.x = W + 80; if (d.x > W + 80) d.x = -80;
        if (d.y < -80) d.y = H * .8; if (d.y > H * .85) d.y = -60;
        ctx.globalAlpha = .35 + .3 * Math.sin(t * d.sp + d.ph);
        ctx.drawImage(d.s, d.x - d.r, d.y - d.r, d.r * 2, d.r * 2);
      }
      ctx.globalAlpha = 1; ctx.globalCompositeOperation = 'source-over';
      ctx.drawImage(sky, 0, Math.sin(t * .05) * 3);
    };
  }

  // ---------- SEA: sunset over the ocean ----------
  function makeSea() {
    const HZ = H * .52;
    return (t) => {
      const sky = ctx.createLinearGradient(0, 0, 0, HZ);
      sky.addColorStop(0, '#1b1d4a'); sky.addColorStop(.55, '#a4486b'); sky.addColorStop(1, '#f6a25a');
      ctx.fillStyle = sky; ctx.fillRect(0, 0, W, HZ);
      // araw
      const sx = W * .5, sy = HZ - 18 + Math.sin(t * .05) * 4;
      const glow = ctx.createRadialGradient(sx, sy, 0, sx, sy, 220);
      glow.addColorStop(0, 'rgba(255,230,170,.9)'); glow.addColorStop(.18, 'rgba(255,190,120,.55)'); glow.addColorStop(1, 'rgba(255,150,100,0)');
      ctx.fillStyle = glow; ctx.fillRect(0, 0, W, HZ);
      ctx.fillStyle = '#ffe6b0'; ctx.beginPath(); ctx.arc(sx, sy, 42, 0, 6.283); ctx.fill();
      // dagat
      const sea = ctx.createLinearGradient(0, HZ, 0, H);
      sea.addColorStop(0, '#6b3a5c'); sea.addColorStop(.4, '#2b2347'); sea.addColorStop(1, '#0d1024');
      ctx.fillStyle = sea; ctx.fillRect(0, HZ, W, H - HZ);
      // alon (mga linya)
      for (let i = 0; i < 46; i++) {
        const p = i / 46, y = HZ + 4 + p * p * (H - HZ);
        ctx.strokeStyle = `rgba(255,200,170,${.05 + .1 * (1 - p)})`;
        ctx.lineWidth = 1 + p * 2;
        ctx.beginPath();
        for (let x = 0; x <= W; x += 16) {
          const yy = y + Math.sin(x * (.02 - p * .012) + t * (0.6 + p) + i) * (1 + p * 5);
          x ? ctx.lineTo(x, yy) : ctx.moveTo(x, yy);
        }
        ctx.stroke();
      }
      // repleksyon ng araw
      ctx.globalCompositeOperation = 'lighter';
      for (let i = 0; i < 60; i++) {
        const p = i / 60, y = HZ + 3 + p * (H - HZ) * .9;
        const w = (30 + p * 160) * (.5 + .5 * Math.sin(t * 2.3 + i * 1.7));
        ctx.fillStyle = `rgba(255,210,150,${.35 * (1 - p)})`;
        ctx.fillRect(sx - w / 2 + Math.sin(t + i) * 8 * p, y, w, 1.5 + p * 2);
      }
      ctx.globalCompositeOperation = 'source-over';
    };
  }

  // ---------- SKY: stars + aurora + mountains ----------
  function makeSky() {
    const stars = Array.from({ length: 260 }, () => ({ x: rnd(0, W), y: rnd(0, H * .75), r: rnd(.4, 1.6), ph: rnd(0, 6.28), sp: rnd(.5, 2) }));
    const mount = document.createElement('canvas'); mount.width = W; mount.height = H;
    const g = mount.getContext('2d');
    [['#0d1424', H * .72, 90], ['#070b16', H * .82, 60]].forEach(([c, base, amp]) => {
      g.fillStyle = c; g.beginPath(); g.moveTo(0, H);
      let y = base;
      for (let x = 0; x <= W; x += 12) { y = Math.min(H, Math.max(base - amp, y + rnd(-14, 14))); g.lineTo(x, y); }
      g.lineTo(W, H); g.closePath(); g.fill();
    });
    return (t) => {
      const bg = ctx.createLinearGradient(0, 0, 0, H);
      bg.addColorStop(0, '#03060f'); bg.addColorStop(.7, '#0a1730'); bg.addColorStop(1, '#10213a');
      ctx.fillStyle = bg; ctx.fillRect(0, 0, W, H);
      for (const s of stars) {
        ctx.globalAlpha = .4 + .6 * Math.abs(Math.sin(t * s.sp * .5 + s.ph));
        ctx.fillStyle = '#fff'; ctx.fillRect(s.x, s.y, s.r, s.r);
      }
      ctx.globalAlpha = 1;
      ctx.globalCompositeOperation = 'lighter';
      const bands = [['98,227,210', 150, .9], ['120,140,255', 120, 1.3], ['180,110,255', 95, .7]];
      bands.forEach(([c, base, f], k) => {
        for (let x = 0; x < W; x += 6) {
          const y = base + Math.sin(x * .006 * f + t * .25 + k) * 40 + Math.sin(x * .017 + t * .4) * 14;
          const h = 90 + Math.sin(x * .01 + t * .3 + k) * 40;
          const gr = ctx.createLinearGradient(0, y - h, 0, y + 10);
          gr.addColorStop(0, `rgba(${c},0)`); gr.addColorStop(.8, `rgba(${c},.10)`); gr.addColorStop(1, `rgba(${c},0)`);
          ctx.fillStyle = gr; ctx.fillRect(x, y - h, 6, h + 10);
        }
      });
      ctx.globalCompositeOperation = 'source-over';
      ctx.drawImage(mount, 0, 0);
    };
  }

  const MAKERS = { city: makeCity, sea: makeSea, sky: makeSky };
  const NAMES = Object.keys(MAKERS);

  function frame(ts) {
    if (!running) return;
    raf = requestAnimationFrame(frame);
    const t = (ts - t0) / 1000 * (reduce ? .25 : 1);
    try { scene(t); } catch (e) { console.warn(e); }
  }

  window.VKScenes = {
    NAMES,
    start(canvas, initial) {
      cv = canvas; cv.width = W; cv.height = H; ctx = cv.getContext('2d');
      this.set(initial || 'city');
      this.resume();
    },
    set(n) {
      name = MAKERS[n] ? n : NAMES[0];
      scene = MAKERS[name]();
      if (cv) { cv.classList.remove('fade'); void cv.offsetWidth; cv.classList.add('fade'); }
    },
    next() { this.set(NAMES[(NAMES.indexOf(name) + 1) % NAMES.length]); },
    current() { return name; },
    pause() { running = false; cancelAnimationFrame(raf); },
    resume() { if (running || !cv) return; running = true; t0 = performance.now() - 1000; raf = requestAnimationFrame(frame); }
  };
})();
