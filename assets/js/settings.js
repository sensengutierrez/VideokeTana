// ===== SETTINGS PAGE =====
const $ = id => document.getElementById(id);
const DAYS = ['Linggo', 'Lunes', 'Martes', 'Miyerkules', 'Huwebes', 'Biyernes', 'Sabado'];
const MONTHS = ['Enero', 'Pebrero', 'Marso', 'Abril', 'Mayo', 'Hunyo', 'Hulyo', 'Agosto', 'Setyembre', 'Oktubre', 'Nobyembre', 'Disyembre'];

function segSet(id, val) {
  document.querySelectorAll(`#${id} button`).forEach(b => b.classList.toggle('on', b.dataset.v === String(val)));
}
function segBind(id, fn) {
  document.querySelectorAll(`#${id} button`).forEach(b => b.addEventListener('click', () => { segSet(id, b.dataset.v); fn(b.dataset.v); }));
}

// ----- Tema -----
function themeMode() { try { return localStorage.getItem('vk_theme') || 'auto'; } catch (e) { return 'auto'; } }
segSet('themeSeg', themeMode());
segBind('themeSeg', v => {
  try { v === 'auto' ? localStorage.removeItem('vk_theme') : localStorage.setItem('vk_theme', v); } catch (e) {}
  const t = v === 'auto' ? (matchMedia('(prefers-color-scheme: light)').matches ? 'light' : 'dark') : v;
  document.documentElement.dataset.theme = t;
});

// ----- Prefs -----
function render() {
  const p = VK_PREFS.get();
  segSet('lyrSeg', p.lyricSize);
  segSet('fmtSeg', p.ss24h ? '24' : '12');
  $('ssEnabled').checked = p.ssEnabled;
  $('ssSeconds').checked = p.ssSeconds;
  $('ssDate').checked = p.ssDate;
  $('ssAfter').value = String(p.ssAfter);
  $('sceneSel').value = p.scene;
  $('ssEnabled').closest('.group').classList.toggle('off', !p.ssEnabled);
  preview();
}
segBind('lyrSeg', v => VK_PREFS.set('lyricSize', v));
segBind('fmtSeg', v => { VK_PREFS.set('ss24h', v === '24'); preview(); });
$('ssEnabled').addEventListener('change', e => { VK_PREFS.set('ssEnabled', e.target.checked); render(); });
$('ssSeconds').addEventListener('change', e => { VK_PREFS.set('ssSeconds', e.target.checked); preview(); });
$('ssDate').addEventListener('change', e => { VK_PREFS.set('ssDate', e.target.checked); preview(); });
$('ssAfter').addEventListener('change', e => VK_PREFS.set('ssAfter', parseInt(e.target.value, 10)));
$('sceneSel').addEventListener('change', e => VK_PREFS.set('scene', e.target.value));
$('ssTest').addEventListener('click', () => window.vkSleepNow());

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
render();
