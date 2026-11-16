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
$('joinBtn').onclick = () => {
  const r = cleanRoom($('joinRoom').value);
  if (!ROOM_RE.test(r)) { toast('Mali ang room code', 'err'); return; }
  NAME = $('joinName').value.trim().slice(0, 40);
  localStorage.setItem('vk_name', NAME);
  ROOM = r;
  history.replaceState(null, '', '?room=' + ROOM);
  start();
};

function start() {
  localStorage.setItem('vk_last_room', ROOM);
  $('joinView').style.display = 'none';
  $('mainView').style.display = 'block';
  $('roomChip').textContent = ROOM;
  renderName();
  connect();
  loadQueue();
  if (!NAME) setTimeout(askName, 400);
}

function renderName() { $('nameChip').textContent = '🙂 ' + (NAME || 'Pangalan'); }
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
    .on('broadcast', { event: 'state' }, ({ payload }) => { state = payload; renderNow(); renderQueue(); })
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
    $('nowTtl').innerHTML = '<span class="muted">Walang tumutugtog</span>';
    $('nowSub').textContent = '';
  } else {
    $('nowTtl').innerHTML = `<span class="code-badge ${n.source === 'youtube' ? 'yt' : ''}">${esc(n.code)}</span><span>${esc(n.title)}</span>`;
    $('nowSub').textContent = [n.artist, n.singer ? '🎤 ' + n.singer : ''].filter(Boolean).join(' · ');
  }
  $('volLbl').textContent = state ? '🔊 ' + state.volume : '🔊 --';
  $('btnToggle').textContent = state && state.paused ? '▶' : '⏯';
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

  if (!queue.length) { $('queueList').innerHTML = '<div class="empty">Walang nakapila. Mag-reserve na! 🎤</div>'; return; }
  let pos = 0;
  $('queueList').innerHTML = queue.map(q => {
    const i = itemInfo(q);
    const playing = q.status === 'playing';
    if (!playing) pos++;
    return `<div class="row ${playing ? 'playing' : ''}">
      <div class="pos">${playing ? '🎤' : pos}</div>
      <div class="info">
        <div class="t"><span class="code-badge ${i.source === 'youtube' ? 'yt' : ''}">${esc(i.code)}</span> ${esc(i.title)}</div>
        <div class="a">${esc(i.artist)}${q.singer ? ' · ' + esc(q.singer) : ''}${playing ? ' · <b style="color:var(--pink)">Kinakanta</b>' : ''}</div>
      </div>
      ${playing ? '' : `${pos > 1 ? `<button class="btn small" data-top="${q.id}" title="Unahin">⬆</button>` : ''}<button class="btn small danger" data-del="${q.id}" title="Alisin">✕</button>`}
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
    if (error) toast(error.message, 'err'); else { toast('Inuna sa pila ⬆', 'ok'); loadQueue(); }
  }
  if (del) {
    if (!confirm('Alisin sa pila?')) return;
    const { error } = await sb.from('queue').delete().eq('id', del.dataset.del).eq('status', 'waiting');
    if (error) toast(error.message, 'err'); else { toast('Inalis sa pila', 'ok'); loadQueue(); }
  }
});

async function reserve(row) {
  buzz();
  const { error } = await sb.from('queue').insert({ room: ROOM, singer: NAME || null, ...row });
  if (error) { toast('Hindi na-reserve: ' + error.message, 'err'); return false; }
  await loadQueue();
  const n = queue.filter(q => q.status === 'waiting').length;
  toast(`Naka-reserve! 🎤 Pang-${n} sa pila`, 'ok');
  return true;
}

// ================= TABS =================
document.querySelectorAll('.tabs button').forEach(b => b.onclick = () => {
  document.querySelectorAll('.tabs button').forEach(x => x.classList.toggle('on', x === b));
  document.querySelectorAll('.pane').forEach(p => p.classList.toggle('on', p.id === 'pane-' + b.dataset.tab));
  if (b.dataset.tab === 'search' && !$('searchList').innerHTML) doSearch();
  if (b.dataset.tab === 'queue') loadQueue();
});

// ================= CODE KEYPAD =================
function renderCode() {
  $('codeDisp').textContent = (code + '_____').slice(0, 5);
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
    else $('codePrev').innerHTML = '<span style="color:var(--red)">Walang kantang may ganitong number</span>';
    renderCode();
  } else {
    $('codePrev').innerHTML = '<span class="muted">I-type ang 5-digit song number</span>';
  }
});
$('codeReserve').onclick = async () => {
  if (!codeSong) return;
  if (await reserve({ song_id: codeSong.id })) {
    code = ''; codeSong = null; renderCode();
    $('codePrev').innerHTML = '<span class="muted">I-type ang 5-digit song number</span>';
  }
};

// ================= LIBRARY SEARCH =================
let st = null;
$('searchQ').addEventListener('input', () => { clearTimeout(st); st = setTimeout(doSearch, 300); });
async function doSearch() {
  const q = $('searchQ').value.trim().replace(/[,%()*\\]/g, ' ').trim();
  let req = sb.from('songs').select('id, code, title, artist, kind').order('title').limit(100);
  if (q) {
    if (/^\d{1,5}$/.test(q)) req = req.or(`title.ilike.%${q}%,artist.ilike.%${q}%,code.eq.${parseInt(q, 10)}`);
    else req = req.or(`title.ilike.%${q}%,artist.ilike.%${q}%`);
  }
  const { data, error } = await req;
  if (error) { toast(error.message, 'err'); return; }
  $('searchList').innerHTML = data.length ? data.map(s => `
    <div class="row">
      <span class="code-badge">${s.code}</span>
      <div class="info"><div class="t">${esc(s.title)}</div><div class="a">${esc(s.artist || '')}</div></div>
      <button class="btn small primary" data-song="${s.id}">🎤</button>
    </div>`).join('')
    : `<div class="empty">${q ? 'Walang nahanap.' : 'Wala pang kanta sa library. Mag-upload sa Admin page.'}</div>`;
}
$('searchList').addEventListener('click', e => {
  const b = e.target.closest('[data-song]');
  if (b) reserve({ song_id: b.dataset.song });
});

// ================= YOUTUBE =================
if (VK_CONFIG.YT_API_KEY) $('ytSearchBox').style.display = 'block';
$('ytReserve').onclick = async () => {
  const id = parseYouTubeId($('ytLink').value);
  if (!id) { toast('Hindi valid na YouTube link', 'err'); return; }
  $('ytReserve').disabled = true;
  const title = await fetchYouTubeTitle(id);
  if (await reserve({ yt_id: id, yt_title: title.slice(0, 200) })) $('ytLink').value = '';
  $('ytReserve').disabled = false;
};
async function ytSearch() {
  const q = $('ytQ').value.trim();
  if (!q) return;
  $('ytList').innerHTML = '<div class="empty">Hinahanap...</div>';
  try {
    const u = 'https://www.googleapis.com/youtube/v3/search?part=snippet&type=video&videoEmbeddable=true&maxResults=15'
      + '&q=' + encodeURIComponent(q + ' karaoke') + '&key=' + VK_CONFIG.YT_API_KEY;
    const r = await fetch(u); const j = await r.json();
    if (j.error) throw new Error(j.error.message);
    $('ytList').innerHTML = (j.items || []).map(it => {
      const t = it.snippet.title.replace(/&amp;/g, '&').replace(/&#39;/g, "'").replace(/&quot;/g, '"');
      return `<div class="row">
        <img src="${esc(it.snippet.thumbnails.default.url)}" alt="">
        <div class="info"><div class="t">${esc(t)}</div><div class="a">${esc(it.snippet.channelTitle)}</div></div>
        <button class="btn small primary" data-yt="${esc(it.id.videoId)}" data-title="${esc(t)}">🎤</button>
      </div>`;
    }).join('') || '<div class="empty">Walang nahanap.</div>';
  } catch (e) { $('ytList').innerHTML = `<div class="empty">Error: ${esc(e.message)}</div>`; }
}
$('ytGo').onclick = ytSearch;
$('ytQ').addEventListener('keydown', e => { if (e.key === 'Enter') ytSearch(); });
$('ytList').addEventListener('click', e => {
  const b = e.target.closest('[data-yt]');
  if (b) reserve({ yt_id: b.dataset.yt, yt_title: b.dataset.title.slice(0, 200) });
});

// ================= INIT =================
if (ROOM_RE.test(ROOM) && new URLSearchParams(location.search).get('room')) start();
else showJoin();
