const $ = id => document.getElementById(id);
const buzz = () => { try { navigator.vibrate && navigator.vibrate(12); } catch (e) {} };

let ROOM = cleanRoom(new URLSearchParams(location.search).get('room') || localStorage.getItem('vk_last_room'));
let NAME = localStorage.getItem('vk_name') || '';
let channel = null;
let queue = [];
let state = null;
let code = '';
let codeSong = null;

// ================= JOIN =================
function showJoin() {
  $('mainView').style.display = 'none';
  $('joinView').style.display = 'block';
  $('joinRoom').value = ROOM_RE.test(ROOM) ? ROOM : '';
  $('joinName').value = NAME;
}
function joinRoom(r) {
  NAME = $('joinName').value.trim().slice(0, 40) || NAME;
  localStorage.setItem('vk_name', NAME);
  ROOM = r;
  history.replaceState(null, '', '?room=' + ROOM);
  start();
}
$('joinBtn').onclick = () => {
  const r = cleanRoom($('joinRoom').value);
  if (!ROOM_RE.test(r)) { toast('Ilagay ang 4 na letra o numero na room code na nasa TV', 'err'); $('joinRoom').focus(); return; }
  joinRoom(r);
};
$('joinRoom').addEventListener('keydown', e => { if (e.key === 'Enter') $('joinName').focus(); });
$('joinName').addEventListener('keydown', e => { if (e.key === 'Enter') $('joinBtn').click(); });

// ================= QR SCANNER =================
let scanStream = null, scanTimer = null, detector = null;
const scanCanvas = document.createElement('canvas');
const scanCtx = scanCanvas.getContext('2d', { willReadFrequently: true });

function roomFromText(t) {
  try { const r = cleanRoom(new URL(t).searchParams.get('room')); if (ROOM_RE.test(r)) return r; } catch (e) {}
  const r = cleanRoom(t);
  return ROOM_RE.test(r) ? r : null;
}
function loadJsQR() {
  return new Promise(res => {
    if (window.jsQR) return res();
    const sc = document.createElement('script');
    sc.src = 'https://cdn.jsdelivr.net/npm/jsqr@1.4.0/dist/jsQR.js';
    sc.onload = res; sc.onerror = res;
    document.head.appendChild(sc);
  });
}
async function startScan() {
  if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
    toast('Walang access sa camera ang browser na ito. I-type na lang ang room code.', 'err', 4500); return;
  }
  $('scanMsg').textContent = 'Binubuksan ang camera';
  $('scanner').hidden = false;
  try {
    scanStream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' }, audio: false });
  } catch (e) {
    stopScan();
    toast(e.name === 'NotAllowedError'
      ? 'Hindi pinayagan ang camera. Payagan ito sa settings ng browser, o i-type ang room code.'
      : 'Hindi mabuksan ang camera. I-type na lang ang room code.', 'err', 5000);
    return;
  }
  const v = $('scanVid');
  v.srcObject = scanStream;
  try { await v.play(); } catch (e) {}
  if ('BarcodeDetector' in window) {
    try { detector = new BarcodeDetector({ formats: ['qr_code'] }); } catch (e) { detector = null; }
  }
  if (!detector) await loadJsQR();
  $('scanMsg').textContent = 'Itapat ang camera sa QR code na nasa TV';
  scanLoop();
}
async function scanLoop() {
  if (!scanStream) return;
  const v = $('scanVid');
  let text = null;
  if (v.readyState >= 2) {
    try {
      if (detector) {
        const codes = await detector.detect(v);
        if (codes.length) text = codes[0].rawValue;
      } else if (window.jsQR) {
        const k = Math.min(1, 640 / v.videoWidth);
        scanCanvas.width = v.videoWidth * k; scanCanvas.height = v.videoHeight * k;
        scanCtx.drawImage(v, 0, 0, scanCanvas.width, scanCanvas.height);
        const img = scanCtx.getImageData(0, 0, scanCanvas.width, scanCanvas.height);
        const r = jsQR(img.data, img.width, img.height, { inversionAttempts: 'dontInvert' });
        if (r) text = r.data;
      }
    } catch (e) {}
  }
  if (text) {
    const r = roomFromText(text);
    if (r) { buzz(); stopScan(); toast('Nakasali sa room ' + r, 'ok'); joinRoom(r); return; }
    $('scanMsg').textContent = 'Hindi ito ang QR code ng videoke. Itapat sa QR na nasa TV.';
  }
  scanTimer = setTimeout(scanLoop, 180);
}
function stopScan() {
  clearTimeout(scanTimer);
  if (scanStream) { scanStream.getTracks().forEach(t => t.stop()); scanStream = null; }
  $('scanVid').srcObject = null;
  $('scanner').hidden = true;
}
$('scanBtn').onclick = startScan;
$('scanClose').onclick = stopScan;
document.addEventListener('visibilitychange', () => { if (document.hidden && scanStream) stopScan(); });

let hostSeen = false, hostTimer = null;
function start() {
  localStorage.setItem('vk_last_room', ROOM);
  hostSeen = false; state = null; renderNow();
  clearTimeout(hostTimer);
  hostTimer = setTimeout(() => { if (!hostSeen) renderNow(); }, 4000);
  $('joinView').style.display = 'none';
  $('mainView').style.display = 'block';
  $('roomChip').textContent = ROOM;
  renderName();
  connect();
  loadQueue();
  if (!NAME) setTimeout(askName, 400);
}

function renderName() {
  $('nameChip').innerHTML = ICON('user') + (NAME ? `<span>Kumakanta bilang <b>${esc(NAME)}</b>. Palitan</span>` : '<span>Ilagay ang pangalan mo</span>');
}
function askName() {
  const n = prompt('Pangalan mo? (lalabas sa TV kapag ikaw na ang kakanta)', NAME);
  if (n === null) return;
  NAME = n.trim().slice(0, 40);
  localStorage.setItem('vk_name', NAME);
  renderName();
  VKMicRemote.rename(NAME || 'Mic');
}
$('nameChip').onclick = askName;
$('roomChip').onclick = () => { if (confirm('Lumipat ng room?')) showJoin(); };

// ================= REALTIME =================
function connect() {
  if (channel) sb.removeChannel(channel);
  channel = sb.channel('room-' + ROOM, { config: { broadcast: { self: false } } })
    .on('broadcast', { event: 'state' }, ({ payload }) => { state = payload; hostSeen = true; renderNow(); renderQueue(); renderFx(); })
    .on('broadcast', { event: 'rtc' }, ({ payload }) => VKMicRemote.handle(payload))
    .on('broadcast', { event: 'score' }, ({ payload }) => {
      toast(`Score${payload.singer ? ' ni ' + payload.singer : ''}: ${payload.score}`, 'ok', 4000);
      try { navigator.vibrate && navigator.vibrate([20, 60, 20]); } catch (e) {}
    })
    .on('postgres_changes', { event: '*', schema: 'public', table: 'queue' }, () => loadQueueSoon())
    .subscribe(status => {
      $('conn').classList.toggle('ok', status === 'SUBSCRIBED');
      if (status === 'SUBSCRIBED') channel.send({ type: 'broadcast', event: 'hello', payload: {} });
    });
}
document.addEventListener('visibilitychange', () => {
  if (document.visibilityState === 'visible' && ROOM_RE.test(ROOM) && $('mainView').style.display !== 'none') {
    loadQueue();
    if (channel) channel.send({ type: 'broadcast', event: 'hello', payload: {} });
  }
});

function sendCmd(action, value) {
  if (!channel) return;
  buzz();
  if (!hostSeen) { toast(`Hindi makita ang TV sa room ${ROOM}. Buksan ang Host at pindutin ang Simulan.`, 'err', 4500); return; }
  if (['toggle', 'skip', 'restart'].includes(action) && !(state && state.now)) {
    toast('Walang tumutugtog. Mag-reserve muna ng kanta.', 'err'); return;
  }
  channel.send({ type: 'broadcast', event: 'cmd', payload: { action, value } });
}
document.querySelectorAll('.ctrls button').forEach(b => b.onclick = () => {
  const c = b.dataset.cmd;
  if (c === 'skip' && state && state.now && !confirm('I-skip ang kanta?')) return;
  sendCmd(c);
});

// ----- Tunog sa TV mula sa phone: mic effects at bass/treble ng kanta -----
const fxDrag = new Set();
function fxLabel(k, v) { return /^music/.test(k) ? (v > 0 ? '+' : '') + v + ' dB' : v + (k === 'micVolume' ? '%' : ''); }
document.querySelectorAll('[data-fx]').forEach(inp => {
  const k = inp.dataset.fx, out = inp.nextElementSibling;
  let t = null;
  const send = () => channel && channel.send({ type: 'broadcast', event: 'cmd', payload: { action: 'fx', value: { key: k, value: +inp.value } } });
  inp.addEventListener('input', () => {
    fxDrag.add(k); out.textContent = fxLabel(k, +inp.value);
    clearTimeout(t); t = setTimeout(send, 120);
  });
  inp.addEventListener('change', () => { send(); setTimeout(() => fxDrag.delete(k), 800); });
});
function renderFx() {
  const fx = state && state.fx;
  $('fxBox').classList.toggle('off', !fx);
  if (!fx) return;
  document.querySelectorAll('[data-fx]').forEach(inp => {
    const k = inp.dataset.fx;
    if (!fxDrag.has(k) && fx[k] != null) { inp.value = fx[k]; inp.nextElementSibling.textContent = fxLabel(k, fx[k]); }
  });
  const yt = state.now && state.now.source === 'youtube';
  document.querySelectorAll('.fx-row.eq input').forEach(i => { i.disabled = !fx.eq; });
  $('eqNote').hidden = !yt;
}

// ----- Sound effects / soundboard sa TV -----
let sfxAt = 0;
const sbPrefs = () => { const p = VK_PREFS.get(); return { vol: p.sbVol == null ? 80 : p.sbVol, solo: p.sbNoOverlap }; };
$('sbVol').value = sbPrefs().vol;
$('sbSolo').checked = sbPrefs().solo;
$('sbVol').addEventListener('change', () => VK_PREFS.set('sbVol', +$('sbVol').value));
$('sbSolo').addEventListener('change', () => VK_PREFS.set('sbNoOverlap', $('sbSolo').checked));
$('sbMore').addEventListener('click', () => {
  const open = $('sb').hidden; $('sb').hidden = !open; $('sbMore').setAttribute('aria-expanded', open);
  $('sbMore').classList.toggle('on', open);
});
function onSfx(e) {
  const b = e.target.closest('[data-sfx]'); if (!b) return;
  if (!hostSeen) { toast(`Hindi makita ang TV sa room ${ROOM}.`, 'err'); return; }
  const name = b.dataset.sfx;
  if (name !== 'stop' && Date.now() - sfxAt < 400) return;
  sfxAt = Date.now(); buzz();
  const sp = sbPrefs();
  channel.send({ type: 'broadcast', event: 'cmd', payload: { action: 'sfx', value: { name, from: NAME || '', vol: sp.vol / 100, solo: sp.solo } } });
  b.classList.add('hit'); setTimeout(() => b.classList.remove('hit'), 400);
}
$('sfxRow').addEventListener('click', onSfx);
$('sb').addEventListener('click', onSfx);

// ----- Beep kapag nagta-type ng numero (Settings: Beep sa pag-type) -----
let beepCtx = null;
function keyBeep(hi) {
  const lvl = VK_PREFS.get().keyBeep;
  if (lvl === 'off') return;
  try {
    beepCtx = beepCtx || new (window.AudioContext || window.webkitAudioContext)();
    const t = beepCtx.currentTime, o = beepCtx.createOscillator(), g = beepCtx.createGain();
    o.type = 'square'; o.frequency.value = hi ? 1320 : 990;
    g.gain.setValueAtTime(lvl === 'low' ? .03 : .07, t); g.gain.exponentialRampToValueAtTime(.0005, t + .07);
    o.connect(g).connect(beepCtx.destination); o.start(t); o.stop(t + .08);
  } catch (e) {}
}

// ================= MIC =================
window.vkMicName = () => NAME || 'Mic';
const MIC_TEXT = {
  off: ['Gawing mic ang phone na ito', 'Maririnig ang boses mo sa speaker ng TV.'],
  asking: ['Humihingi ng pahintulot', 'Pindutin ang "Allow" para magamit ang mic.'],
  connecting: ['Kumokonekta sa TV', 'Sandali lang.'],
  connected: ['Naka-on ang mic', 'Kumanta na! Naririnig ka na sa TV.'],
  reconnecting: ['Kumokonekta ulit', 'Naputol ang koneksyon. Sinusubukan ulit.'],
  noanswer: ['Hindi sumasagot ang TV', 'Buksan ang Host sa laptop at pindutin ang Simulan. Dapat nasa iisang Wi-Fi.'],
  denied: ['Hindi pinayagan ang mic', 'Payagan ang microphone sa settings ng browser, tapos subukan ulit.'],
  nomic: ['Walang nakitang mic', 'Hindi makita ang microphone ng phone na ito.'],
  unsupported: ['Hindi kaya ng browser na ito', 'Gamitin ang Chrome o Safari na bago.'],
  error: ['May error sa mic', 'Subukan ulit.']
};
let micState = 'off', micRAF = 0;
VKMicRemote.init(
  msg => { if (channel) channel.send({ type: 'broadcast', event: 'rtc', payload: msg }); },
  (s, info) => {
    micState = s;
    const t = MIC_TEXT[s] || MIC_TEXT.error;
    $('micStatus').textContent = t[0];
    $('micSub').textContent = s === 'error' && info ? `${t[1]} (${info})` : t[1];
    const active = ['asking', 'connecting', 'connected', 'reconnecting', 'noanswer'].includes(s);
    $('micBtn').classList.toggle('on', s === 'connected');
    $('micBtn').classList.toggle('busy', ['asking', 'connecting', 'reconnecting', 'noanswer'].includes(s));
    $('micActs').hidden = !active;
    $('micDot').classList.toggle('on', s === 'connected');
    $('micClean').disabled = active;
    if (s === 'connected') toast('Naka-on ang mic', 'ok');
    cancelAnimationFrame(micRAF);
    if (active) meterLoop(); else $('micMeter').style.width = '0%';
  }
);
function meterLoop() {
  micRAF = requestAnimationFrame(meterLoop);
  $('micMeter').style.width = Math.min(100, VKMicRemote.level() * 450) + '%';
}
$('micBtn').onclick = () => {
  buzz();
  if (VKMicRemote.isOn()) return;
  if (!hostSeen) toast(`Hindi pa makita ang TV sa room ${ROOM}, pero susubukan pa rin.`, 'err', 3500);
  VKMicRemote.start({ clean: $('micClean').checked });
};
$('micStop').onclick = () => { buzz(); VKMicRemote.stop(); $('micMute').textContent = 'I-mute'; };
$('micMute').onclick = () => {
  buzz();
  const m = !VKMicRemote.isMuted();
  VKMicRemote.setMuted(m);
  $('micMute').textContent = m ? 'I-unmute' : 'I-mute';
  $('micStatus').textContent = m ? 'Naka-mute ang mic' : MIC_TEXT[micState][0];
};
window.addEventListener('pagehide', () => { if (VKMicRemote.isOn()) VKMicRemote.stop(); });

function renderNow() {
  const n = state && state.now;
  if (!n) {
    $('nowTtl').innerHTML = '<span class="idle">Walang tumutugtog</span>';
    $('nowSub').textContent = hostSeen ? 'Mag-reserve ng kanta para magsimula.'
      : `Hinahanap ang TV sa room ${ROOM}. Siguraduhing bukas ang Host at napindot ang Simulan.`;
  } else {
    $('nowTtl').innerHTML = (n.source === 'youtube' ? '<span class="yt">YouTube</span>' : `<span class="dot">${esc(n.code)}</span>`) + `<span class="t">${esc(n.title)}</span>`;
    $('nowSub').textContent = [n.artist, n.singer ? 'Kumakanta: ' + n.singer : ''].filter(Boolean).join(', ');
  }
  $('volLbl').textContent = state ? 'Volume ' + state.volume : '';
  $('btnToggle').innerHTML = ICON(state && state.paused ? 'play' : (state && state.now ? 'pause' : 'playpause'));
}

// ================= QUEUE =================
async function loadQueue() {
  const { data, error } = await queueSelect(ROOM);
  if (error) { toast('Error: ' + error.message, 'err'); return; }
  queue = sortQueue(data || []);
  renderQueue();
}
let qt = null;
function loadQueueSoon() { clearTimeout(qt); qt = setTimeout(loadQueue, 200); }

function renderQueue() {
  const waiting = queue.filter(q => q.status === 'waiting');
  const b = $('qBadge');
  b.style.display = waiting.length ? 'inline-block' : 'none';
  b.textContent = waiting.length;

  if (!queue.length) { $('queueList').innerHTML = '<div class="empty">Walang nakapila. Mag-reserve ng kanta sa Numero o Hanap.</div>'; return; }
  let pos = 0;
  $('queueList').innerHTML = queue.map(q => {
    const i = itemInfo(q);
    const playing = q.status === 'playing';
    if (!playing) pos++;
    return `<div class="row ${playing ? 'playing' : ''}">
      <div class="pos">${playing ? ICON('mic') : pos}</div>
      <div class="info">
        <div class="t">${esc(i.title)}</div>
        <div class="a">${i.source === 'youtube' ? 'YouTube' : esc(i.code)}${i.artist && i.source !== 'youtube' ? ', ' + esc(i.artist) : ''}${q.singer ? '. Kakanta: ' + esc(q.singer) : ''}</div>
      </div>
      ${playing ? `<div class="acts"><span class="now-lbl">${state && state.now && state.now.id === q.id ? 'Kinakanta' : 'Naipit'}</span><button class="btn small icon danger" data-del="${q.id}" title="Alisin" aria-label="Alisin">${ICON('x')}</button></div>` : `<div class="acts">${pos > 1 ? `<button class="btn small icon" data-top="${q.id}" title="Unahin" aria-label="Unahin">${ICON('up')}</button>` : ''}<button class="btn small icon danger" data-del="${q.id}" title="Alisin" aria-label="Alisin">${ICON('x')}</button></div>`}
    </div>`;
  }).join('');
}

$('queueList').addEventListener('click', async e => {
  const top = e.target.closest('[data-top]'), del = e.target.closest('[data-del]');
  if (top) {
    buzz();
    const waiting = queue.filter(q => q.status === 'waiting');
    const min = Math.min(...waiting.map(q => q.sort_order));
    const { error } = await sb.from('queue').update({ sort_order: min - 1 }).eq('id', top.dataset.top);
    if (error) toast(error.message, 'err'); else { toast('Inuna sa pila', 'ok'); loadQueue(); }
  }
  if (del) {
    if (!confirm('Alisin sa pila?')) return;
    const { error } = await sb.from('queue').delete().eq('id', del.dataset.del).eq('status', 'waiting');
    if (error) toast(error.message, 'err'); else { toast('Inalis sa pila', 'ok'); loadQueue(); }
  }
});

let reserving = false;
async function reserve(row) {
  if (reserving) return false;
  reserving = true;
  buzz();
  try { return await doReserve(row); } finally { reserving = false; }
}
async function doReserve(row) {
  const { error } = await sb.from('queue').insert({ room: ROOM, singer: NAME || null, ...row });
  if (error) { toast('Hindi na-reserve: ' + error.message, 'err'); return false; }
  addHistory(row);
  await loadQueue();
  const n = queue.filter(q => q.status === 'waiting').length;
  const playing = queue.some(q => q.status === 'playing');
  if (window.VKVoice) VKVoice.act('reserved');
  toast(!playing && n <= 1 ? 'Naka-reserve. Magsisimula na sa TV.' : `Naka-reserve. Pang-${n} ka sa pila.`, 'ok');
  if (!hostSeen) toast(`Naka-pila na, pero hindi pa makita ang TV sa room ${ROOM}.`, 'err', 4500);
  return true;
}

// ================= TABS =================
document.querySelectorAll('.tabs button').forEach(b => b.onclick = () => {
  document.querySelectorAll('.tabs button').forEach(x => x.classList.toggle('on', x === b));
  document.querySelectorAll('.pane').forEach(p => p.classList.toggle('on', p.id === 'pane-' + b.dataset.tab));
  if (b.dataset.tab === 'search' && !$('searchList').innerHTML) { loadCats(); doSearch(); }
  if (b.dataset.tab === 'yt') openYtTab();
  if (b.dataset.tab === 'queue') loadQueue();
});

// ================= CODE KEYPAD =================
function renderCode() {
  $('codeReserve').innerHTML = codeSong ? `Reserve <span class="dot">${codeSong.code}</span>` : 'Reserve';
  $('codeDisp').innerHTML = esc(code) + `<span class="ghost">${'8'.repeat(5 - code.length)}</span>`;
  $('codeReserve').disabled = !codeSong;
}
$('pad').addEventListener('click', async e => {
  const b = e.target.closest('button'); if (!b) return;
  buzz();
  const k = b.dataset.k;
  keyBeep(!!k);
  if (window.VKVoice) VKVoice.key(k || b.textContent.trim());
  if (k === 'clr') code = '';
  else if (k === 'del') code = code.slice(0, -1);
  else if (code.length < 5) code += b.textContent;
  codeSong = null;
  renderCode();
  if (code.length === 5) {
    $('codePrev').innerHTML = '<span class="muted">Hinahanap...</span>';
    const typed = code;
    const { data } = await sb.from('songs').select('id, code, title, artist').eq('code', parseInt(code, 10)).maybeSingle();
    if (typed !== code) return;
    if (window.VKVoice) data ? VKVoice.say(data.title) : VKVoice.act('notfound');
    if (data) { codeSong = data; songCache.set(data.id, data); $('codePrev').innerHTML = `<b>${esc(data.title)}</b><span class="muted">${esc(data.artist || '')}</span>`; }
    else $('codePrev').innerHTML = '<span class="err">Walang kanta sa numerong ito</span>';
    renderCode();
  } else {
    $('codePrev').innerHTML = '<span class="muted">I-type ang song number</span>';
  }
});
$('codeReserve').onclick = async () => {
  if (!codeSong) return;
  if (await reserve({ song_id: codeSong.id })) {
    code = ''; codeSong = null; renderCode();
    $('codePrev').innerHTML = '<span class="muted">I-type ang song number</span>';
  }
};

// ================= LIBRARY SEARCH =================
let st = null, cat = '';
$('searchQ').addEventListener('input', () => { clearTimeout(st); st = setTimeout(doSearch, 300); });
async function loadCats() {
  const { data } = await sb.from('songs').select('category').not('category', 'is', null).limit(1000);
  const have = [...new Set((data || []).map(r => r.category))].sort((x, y) => CATEGORIES.indexOf(x) - CATEGORIES.indexOf(y));
  $('catChips').innerHTML = [['', 'Lahat'], ['__fav', 'Paborito'], ['__hist', 'Kamakailan'], ['__new', 'Bago'], ...have.map(c => [c, c])]
    .map(([v, l]) => `<button class="chip ${v === cat ? 'on' : ''}" data-cat="${esc(v)}">${esc(l)}</button>`).join('');
}
$('catChips').addEventListener('click', e => {
  const b = e.target.closest('[data-cat]'); if (!b) return;
  cat = b.dataset.cat;
  document.querySelectorAll('#catChips .chip').forEach(x => x.classList.toggle('on', x === b));
  doSearch();
});
// ----- Paborito at Kamakailan (naka-save sa phone na ito) -----
const LS = (k, d) => { try { return JSON.parse(localStorage.getItem(k) || 'null') || d; } catch (e) { return d; } };
const favs = () => LS('vk_favs', []);
const hist = () => LS('vk_hist', []);
function saveLS(k, v) { try { localStorage.setItem(k, JSON.stringify(v)); } catch (e) {} }
const songCache = new Map();
function addHistory(row) {
  let h = hist();
  const s = row.song_id ? songCache.get(row.song_id) : null;
  const item = row.song_id
    ? { id: row.song_id, code: s ? s.code : '', title: s ? s.title : 'Kanta', artist: s ? s.artist : '', kind: s ? s.kind : '' }
    : { yt: row.yt_id, title: row.yt_title || 'YouTube video', artist: 'YouTube' };
  h = [item, ...h.filter(x => (x.id || x.yt) !== (item.id || item.yt))].slice(0, 40);
  saveLS('vk_hist', h);
}
function toggleFav(s) {
  let f = favs();
  const has = f.some(x => x.id === s.id);
  f = has ? f.filter(x => x.id !== s.id) : [{ id: s.id, code: s.code, title: s.title, artist: s.artist, kind: s.kind }, ...f].slice(0, 100);
  saveLS('vk_favs', f);
  toast(has ? 'Inalis sa Paborito' : 'Naidagdag sa Paborito', 'ok');
  return !has;
}
function songRow(s) {
  const isFav = s.id && favs().some(x => x.id === s.id);
  return `<div class="row">
      <span class="num">${s.code || (s.yt ? 'YT' : '')}</span>
      <div class="info"><div class="t">${esc(s.title)}</div><div class="a">${s.kind === 'youtube' || s.yt ? '<span class="src">YouTube</span>' : ''}${esc(s.yt ? '' : (s.artist || ''))}${s.category ? ', ' + esc(s.category) : ''}</div></div>
      ${s.id ? `<button class="btn small icon fav ${isFav ? 'on' : ''}" data-fav="${s.id}" aria-label="Paborito" title="Paborito">${ICON('star')}</button>` : ''}
      ${s.id ? `<button class="btn small" data-song="${s.id}">Reserve</button>` : `<button class="btn small" data-yt="${esc(s.yt)}" data-title="${esc(s.title)}">Reserve</button>`}
    </div>`;
}

async function doSearch() {
  const q = $('searchQ').value.trim().replace(/[,%()*\\]/g, ' ').trim();
  if (cat === '__fav' || cat === '__hist') {
    const src = cat === '__fav' ? favs() : hist();
    const ql = q.toLowerCase();
    const list = src.filter(s => !ql || (s.title || '').toLowerCase().includes(ql) || (s.artist || '').toLowerCase().includes(ql) || String(s.code || '').includes(ql));
    $('searchList').innerHTML = list.length ? list.map(songRow).join('')
      : `<div class="empty">${cat === '__fav' ? 'Wala pang Paborito. Pindutin ang bituin sa tabi ng kanta.' : 'Wala ka pang nire-reserve sa phone na ito.'}</div>`;
    return;
  }
  let req = sb.from('songs').select('id, code, title, artist, kind, category, created_at');
  req = cat === '__new' ? req.order('created_at', { ascending: false }).limit(30) : req.order('title').limit(150);
  if (cat && cat !== '__new') req = req.eq('category', cat);
  if (q) {
    if (/^\d{1,5}$/.test(q)) req = req.or(`title.ilike.%${q}%,artist.ilike.%${q}%,code.eq.${parseInt(q, 10)}`);
    else req = req.or(`title.ilike.%${q}%,artist.ilike.%${q}%`);
  }
  const { data, error } = await req;
  if (error) { toast(error.message, 'err'); return; }
  data.forEach(s => songCache.set(s.id, s));
  $('searchList').innerHTML = data.length ? data.map(songRow).join('')
    : `<div class="empty">${q ? 'Walang nahanap sa library. Subukan sa YouTube tab.' : 'Wala pang kanta dito.'}</div>`;
}
$('searchList').addEventListener('click', e => {
  const f = e.target.closest('[data-fav]');
  if (f) {
    const s = songCache.get(f.dataset.fav) || favs().find(x => x.id === f.dataset.fav) || hist().find(x => x.id === f.dataset.fav);
    if (s) { const on = toggleFav(s); f.classList.toggle('on', on); if (cat === '__fav' && !on) doSearch(); }
    return;
  }
  const y = e.target.closest('[data-yt]');
  if (y) { reserve({ yt_id: y.dataset.yt, yt_title: y.dataset.title.slice(0, 200) }); return; }
  const b = e.target.closest('[data-song]');
  if (b) reserve({ song_id: b.dataset.song });
});

// ================= YOUTUBE =================
$('ytReserve').onclick = async () => {
  const id = parseYouTubeId($('ytLink').value);
  if (!id) { toast('Hindi valid na YouTube link', 'err'); return; }
  $('ytReserve').disabled = true;
  const title = await fetchYouTubeTitle(id);
  if (await reserve({ yt_id: id, yt_title: title.slice(0, 200) })) $('ytLink').value = '';
  $('ytReserve').disabled = false;
};
let ytLoaded = false;
function ytRows(items) {
  return items.map(it => `<div class="row">
      <img src="${esc(it.thumb)}" alt="" loading="lazy">
      <div class="info"><div class="t">${esc(it.title)}</div><div class="a">${esc(it.channel)}</div></div>
      <button class="btn small" data-yt="${esc(it.id)}" data-title="${esc(it.title)}">Reserve</button>
    </div>`).join('');
}
async function ytSearch(mode) {
  const q = $('ytQ').value.trim();
  if (mode !== 'latest' && !q) return;
  $('ytHead').textContent = mode === 'latest' ? 'Bago at sikat ngayon' : `Resulta para sa "${q}"`;
  $('ytList').innerHTML = '<div class="empty">Hinahanap</div>';
  try {
    const j = await ytSearchApi(q, mode);
    if (j.error) {
      if (j.error === 'no_key') { $('ytSearchBox').style.display = 'none'; $('ytNoKey').style.display = 'block'; return; }
      $('ytList').innerHTML = `<div class="empty">${esc(ytErrorText(j.error))}</div>`; return;
    }
    $('ytList').innerHTML = ytRows(j.items || []) || '<div class="empty">Walang nahanap. Subukan ang ibang salita.</div>';
  } catch (e) { $('ytList').innerHTML = `<div class="empty">${esc(ytErrorText('network'))}</div>`; }
}
$('ytGo').onclick = () => ytSearch('search');
$('ytLatest').onclick = () => { $('ytQ').value = ''; ytSearch('latest'); };
$('ytQ').addEventListener('keydown', e => { if (e.key === 'Enter') ytSearch('search'); });
$('ytList').addEventListener('click', e => {
  const b = e.target.closest('[data-yt]');
  if (b) reserve({ yt_id: b.dataset.yt, yt_title: b.dataset.title.slice(0, 200) });
});
function openYtTab() { if (!ytLoaded) { ytLoaded = true; ytSearch('latest'); } }

// ================= INIT =================
if (ROOM_RE.test(ROOM) && new URLSearchParams(location.search).get('room')) start();
else showJoin();
