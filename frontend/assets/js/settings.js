// ===== SETTINGS PAGE =====
// Ang bawat control na may data-pref ay kusang naka-bind sa VK_PREFS.
const $ = id => document.getElementById(id);
const DAYS = ['Linggo', 'Lunes', 'Martes', 'Miyerkules', 'Huwebes', 'Biyernes', 'Sabado'];
const MONTHS = ['Enero', 'Pebrero', 'Marso', 'Abril', 'Mayo', 'Hunyo', 'Hulyo', 'Agosto', 'Setyembre', 'Oktubre', 'Nobyembre', 'Disyembre'];
const COLORS = {
  hlColor: ['#62e3d2', '#ffd54a', '#ff7ab6', '#7cc4ff', '#9be15d', '#ffa552', '#c49bff', '#ffffff'],
  bottomColor: ['#ffd54a', '#ffffff', '#62e3d2', '#ff7ab6', '#7cc4ff', '#9be15d', '#ffa552']
};

function cast(el, v) {
  if (el.hasAttribute('data-num')) return Number(v);
  if (el.hasAttribute('data-bool')) return v === 'true';
  return v;
}

// ----- Tema (hiwalay sa VK_PREFS) -----
function themeMode() { try { return localStorage.getItem('vk_theme') || 'auto'; } catch (e) { return 'auto'; } }
document.querySelectorAll('[data-seg="theme"] button').forEach(b => b.addEventListener('click', () => {
  const v = b.dataset.v;
  try { v === 'auto' ? localStorage.removeItem('vk_theme') : localStorage.setItem('vk_theme', v); } catch (e) {}
  document.documentElement.dataset.theme = v === 'auto' ? (matchMedia('(prefers-color-scheme: light)').matches ? 'light' : 'dark') : v;
  render();
}));

// ----- Swatches -----
document.querySelectorAll('.swatches').forEach(w => {
  w.innerHTML = COLORS[w.dataset.pref].map(c => `<button type="button" class="sw" data-v="${c}" style="--c:${c}" aria-label="Kulay ${c}"></button>`).join('');
});

// ----- Binding -----
document.querySelectorAll('[data-pref]').forEach(el => {
  const key = el.dataset.pref;
  if (el.classList.contains('seg') || el.classList.contains('swatches')) {
    el.addEventListener('click', e => {
      const b = e.target.closest('button[data-v]'); if (!b) return;
      VK_PREFS.set(key, cast(el, b.dataset.v)); render();
    });
  } else if (el.type === 'checkbox') {
    el.addEventListener('change', () => { VK_PREFS.set(key, el.checked); render(); });
  } else if (el.type === 'range') {
    el.addEventListener('input', () => { if (key === 'lyricDelay') delayLabel(el.value); showVal(key, el.value); });
    el.addEventListener('change', () => { VK_PREFS.set(key, Number(el.value)); render(); });
  } else if (el.tagName === 'SELECT') {
    el.addEventListener('change', () => { VK_PREFS.set(key, cast(el, el.value)); render(); });
  } else {
    let t = null;
    el.addEventListener('input', () => { clearTimeout(t); t = setTimeout(() => VK_PREFS.set(key, el.value.trim()), 400); });
  }
});

function render() {
  const p = VK_PREFS.get();
  document.querySelectorAll('[data-seg="theme"] button').forEach(b => b.classList.toggle('on', b.dataset.v === themeMode()));
  document.querySelectorAll('[data-pref]').forEach(el => {
    const v = p[el.dataset.pref];
    if (el.classList.contains('seg') || el.classList.contains('swatches')) {
      el.querySelectorAll('button[data-v]').forEach(b => b.classList.toggle('on', b.dataset.v === String(v)));
    } else if (el.type === 'checkbox') el.checked = !!v;
    else if (document.activeElement !== el) el.value = v;
  });
  $('ssGroup').classList.toggle('off', !p.ssEnabled);
  document.querySelectorAll('.dep-score').forEach(e => e.classList.toggle('dim', p.scoreMode === 'off'));
  document.querySelectorAll('.dep-anim').forEach(e => e.classList.toggle('dim', p.bgMode !== 'animated'));
  document.querySelectorAll('.dep-wall').forEach(e => e.classList.toggle('dim', p.wallpaper !== 'image'));
  document.querySelectorAll('.dep-glass').forEach(e => e.classList.toggle('dim', !p.glass));
  delayLabel(p.lyricDelay);
  document.querySelectorAll('[data-show]').forEach(e => showVal(e.dataset.show, p[e.dataset.show]));
  lyricPreview(p);
  preview();
}

function showVal(key, v) {
  const e = document.querySelector(`[data-show="${key}"]`);
  if (e && /^music/.test(key)) { e.textContent = (+v > 0 ? '+' : '') + v + ' dB'; return; }
  if (e) e.textContent = key === 'glass' ? (+v === 0 ? 'Buo (walang transparency)' : v + '% see-through') : v + '%';
}

// ----- Wallpaper -----
function wallThumb(url) {
  const t = $('wallThumb');
  t.style.backgroundImage = url ? `url("${url}")` : '';
  t.classList.toggle('has', !!url);
  $('wallClear').disabled = !url;
}
window.addEventListener('vkwall', e => wallThumb(e.detail.url));
$('wallPick').addEventListener('click', () => $('wallIn').click());
$('wallIn').addEventListener('change', async e => {
  const f = e.target.files[0]; e.target.value = '';
  if (!f) return;
  try { await VKWall.set(f); toast('Naitakda ang wallpaper', 'ok'); render(); }
  catch (err) { toast('Hindi magamit ang larawang ito. Pumili ng iba.', 'err', 4000); }
});
$('wallClear').addEventListener('click', async () => { await VKWall.clear(); toast('Inalis ang wallpaper', 'ok'); render(); });
function delayLabel(v) {
  v = +v;
  $('lyricDelayVal').textContent = v === 0 ? 'Walang adjust' : `${Math.abs(v / 1000).toFixed(1)} segundo ${v > 0 ? 'mas maaga' : 'mas huli'}`;
}

// Preview ng lyrics ayon sa settings
function lyricPreview(p) {
  const box = $('lyPreview');
  box.style.textAlign = p.lyricAlign === 'left' ? 'left' : 'center';
  const sizes = { s: 20, m: 26, l: 31, xl: 36 };
  $('lpCur').style.fontSize = sizes[p.lyricSize] + 'px';
  box.style.setProperty('--hl', p.hlColor);
  const o = [0, 1.5, 3][p.outline] || 0;
  const f = [];
  if (o) f.push(`drop-shadow(${o}px 0 0 #000)`, `drop-shadow(-${o}px 0 0 #000)`, `drop-shadow(0 ${o}px 0 #000)`, `drop-shadow(0 -${o}px 0 #000)`);
  if (p.lyricShadow) f.push('drop-shadow(0 3px 6px rgba(0,0,0,.7))');
  box.style.setProperty('--f', f.join(' ') || 'none');
  $('lpNext').classList.toggle('four', p.lyricLines >= 4);
  $('lpCue').textContent = p.countdown === 'off' ? '' : p.countdown === 'dots' ? '● ● ●' : '3';
}

// ----- Screensaver preview -----
function preview() {
  const p = VK_PREFS.get(), d = new Date();
  let h = d.getHours();
  const ampm = h < 12 ? 'AM' : 'PM';
  if (!p.ss24h) h = h % 12 || 12;
  const mm = String(d.getMinutes()).padStart(2, '0'), ss = String(d.getSeconds()).padStart(2, '0');
  $('pvTime').textContent = (p.ss24h ? String(h).padStart(2, '0') : h) + ':' + mm + (p.ssSeconds ? ':' + ss : '');
  $('pvAmpm').textContent = p.ss24h ? '' : ampm;
  $('pvDate').textContent = p.ssDate ? `${DAYS[d.getDay()]}, ${MONTHS[d.getMonth()]} ${d.getDate()}` : '';
}
setInterval(preview, 1000);
$('ssTest').addEventListener('click', () => window.vkSleepNow());

// ----- Data -----
function download(name, text, type) {
  const blob = new Blob([text], { type });
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob); a.download = name;
  document.body.appendChild(a); a.click(); a.remove();
  setTimeout(() => URL.revokeObjectURL(a.href), 2000);
}
const stamp = () => new Date().toISOString().slice(0, 10);

$('exportCsv').addEventListener('click', async () => {
  const { data, error } = await sb.from('songs').select('code, title, artist, category, kind, created_at').order('code');
  if (error) { toast('Hindi ma-export: ' + error.message, 'err'); return; }
  const q = v => `"${String(v ?? '').replace(/"/g, '""')}"`;
  const kind = k => ({ audio: 'MP3', video: 'Video', youtube: 'YouTube' })[k] || k;
  const rows = [['Song no.', 'Title', 'Artist', 'Kategorya', 'Uri', 'Idinagdag'].map(q).join(',')]
    .concat(data.map(s => [s.code, s.title, s.artist, s.category, kind(s.kind), (s.created_at || '').slice(0, 10)].map(q).join(',')));
  download(`videoke-kanta-${stamp()}.csv`, '﻿' + rows.join('\r\n'), 'text/csv;charset=utf-8');
  toast(`Na-export ang ${data.length} kanta`, 'ok');
});

const LSKEYS = ['vk_favs', 'vk_hist', 'vk_name', 'vk_theme', 'vk_vol'];
$('backupBtn').addEventListener('click', () => {
  const out = { app: 'videoke', version: 1, saved: new Date().toISOString(), prefs: VK_PREFS.get(), data: {} };
  LSKEYS.forEach(k => { const v = localStorage.getItem(k); if (v !== null) out.data[k] = v; });
  download(`videoke-backup-${stamp()}.json`, JSON.stringify(out, null, 2), 'application/json');
  toast('Na-download ang backup', 'ok');
});
$('restoreBtn').addEventListener('click', () => $('restoreIn').click());
$('restoreIn').addEventListener('change', async e => {
  const f = e.target.files[0]; e.target.value = '';
  if (!f) return;
  try {
    const j = JSON.parse(await f.text());
    if (j.app !== 'videoke' || !j.prefs) throw new Error('bad');
    Object.entries(j.prefs).forEach(([k, v]) => { if (k in VK_PREFS.DEFAULTS) VK_PREFS.set(k, v); });
    Object.entries(j.data || {}).forEach(([k, v]) => { if (LSKEYS.includes(k)) localStorage.setItem(k, v); });
    toast('Na-restore ang settings', 'ok');
    setTimeout(() => location.reload(), 600);
  } catch (err) { toast('Hindi ito valid na backup file ng Videoke.', 'err', 4000); }
});
$('resetBtn').addEventListener('click', () => {
  if (!confirm('Ibalik sa default ang lahat ng settings sa device na ito?')) return;
  try { localStorage.removeItem('vk_prefs'); } catch (e) {}
  window.dispatchEvent(new CustomEvent('vkprefs', { detail: VK_PREFS.get() }));
  toast('Naibalik sa default', 'ok');
  render();
});

render();

// palit wika: i-reload para maisalin ang buong page
document.querySelectorAll('[data-pref="lang"] button').forEach(b => b.addEventListener('click', () => {
  if (b.dataset.v !== window.vkLang) setTimeout(() => location.reload(), 150);
}));
