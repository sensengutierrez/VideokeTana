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
}
$('nameChip').onclick = askName;
$('roomChip').onclick = () => { if (confirm('Lumipat ng room?')) showJoin(); };

// ================= REALTIME =================
function connect() {
  if (channel) sb.removeChannel(channel);
  channel = sb.channel('room-' + ROOM, { config: { broadcast: { self: false } } })
    .on('broadcast', { event: 'state' }, ({ payload }) => { state = payload; hostSeen = true; renderNow(); renderQueue(); })
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
      ${playing ? '<span class="now-lbl">Kinakanta</span>' : `<div class="acts">${pos > 1 ? `<button class="btn small icon" data-top="${q.id}" title="Unahin" aria-label="Unahin">${ICON('up')}</button>` : ''}<button class="btn small icon danger" data-del="${q.id}" title="Alisin" aria-label="Alisin">${ICON('x')}</button></div>`}
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
  await loadQueue();
  const n = queue.filter(q => q.status === 'waiting').length;
  const playing = queue.some(q => q.status === 'playing');
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
    if (data) { codeSong = data; $('codePrev').innerHTML = `<b>${esc(data.title)}</b><span class="muted">${esc(data.artist || '')}</span>`; }
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
  $('catChips').innerHTML = [['', 'Lahat'], ['__new', 'Bago'], ...have.map(c => [c, c])]
    .map(([v, l]) => `<button class="chip ${v === cat ? 'on' : ''}" data-cat="${esc(v)}">${esc(l)}</button>`).join('');
}
$('catChips').addEventListener('click', e => {
  const b = e.target.closest('[data-cat]'); if (!b) return;
  cat = b.dataset.cat;
  document.querySelectorAll('#catChips .chip').forEach(x => x.classList.toggle('on', x === b));
  doSearch();
});
async function doSearch() {
  const q = $('searchQ').value.trim().replace(/[,%()*\\]/g, ' ').trim();
  let req = sb.from('songs').select('id, code, title, artist, kind, category, created_at');
  req = cat === '__new' ? req.order('created_at', { ascending: false }).limit(30) : req.order('title').limit(150);
  if (cat && cat !== '__new') req = req.eq('category', cat);
  if (q) {
    if (/^\d{1,5}$/.test(q)) req = req.or(`title.ilike.%${q}%,artist.ilike.%${q}%,code.eq.${parseInt(q, 10)}`);
    else req = req.or(`title.ilike.%${q}%,artist.ilike.%${q}%`);
  }
  const { data, error } = await req;
  if (error) { toast(error.message, 'err'); return; }
  $('searchList').innerHTML = data.length ? data.map(s => `
    <div class="row">
      <span class="num">${s.code}</span>
      <div class="info"><div class="t">${esc(s.title)}</div><div class="a">${s.kind === 'youtube' ? '<span class="src">YouTube</span>' : ''}${esc(s.artist || '')}${s.category ? ', ' + esc(s.category) : ''}</div></div>
      <button class="btn small" data-song="${s.id}">Reserve</button>
    </div>`).join('')
    : `<div class="empty">${q ? 'Walang nahanap sa library. Subukan sa YouTube tab.' : 'Wala pang kanta dito.'}</div>`;
}
$('searchList').addEventListener('click', e => {
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
