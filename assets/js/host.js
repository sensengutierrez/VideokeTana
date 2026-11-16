// ================= STATE =================
const $ = id => document.getElementById(id);
const params = new URLSearchParams(location.search);
let ROOM = cleanRoom(params.get('room') || localStorage.getItem('vk_room'));
if (!ROOM_RE.test(ROOM)) ROOM = makeRoomCode();
localStorage.setItem('vk_room', ROOM);

let queue = [];
let current = null;       // queue row na tumutugtog
let mode = 'idle';        // idle | video | audio | youtube
let started = false;
let starting = false;
let paused = false;
let volume = parseInt(localStorage.getItem('vk_vol') || '80', 10);
let lyrics = [];          // [{t, text}]
let lyricOffset = 0;
let ytPlayer = null, ytReady = false, ytReadyWaiters = [];
let channel = null;

const vid = $('vid'), aud = $('aud');

// ================= QR =================
function remoteLink() {
  return new URL('../remote/?room=' + ROOM, location.href).href;
}
function renderQr() {
  const qr = qrcode(0, 'M');
  qr.addData(remoteLink());
  qr.make();
  const svg = qr.createSvgTag({ cellSize: 4, margin: 0, scalable: true });
  $('qrBig').innerHTML = svg;
  $('qrSmall').innerHTML = svg;
  $('roomCode').textContent = ROOM;
  $('roomSmall').textContent = ROOM;
  $('remoteUrl').textContent = remoteLink();
}

// ================= VIEW =================
function show(m) {
  mode = m;
  vid.style.display = m === 'video' ? 'block' : 'none';
  $('ytWrap').style.display = m === 'youtube' ? 'block' : 'none';
  $('audioStage').style.display = m === 'audio' ? 'block' : 'none';
  $('idle').style.display = m === 'idle' ? 'flex' : 'none';
  const playing = m !== 'idle';
  $('cornerQr').style.display = playing ? 'flex' : 'none';
  $('nowTag').style.display = playing ? 'block' : 'none';
  renderNextBar();
}

function renderNextBar() {
  const waiting = queue.filter(q => q.status === 'waiting' && (!current || q.id !== current.id));
  const bar = $('nextBar');
  if (mode === 'idle' || !waiting.length) { bar.style.display = 'none'; }
  else {
    const n = waiting[0], i = itemInfo(n);
    bar.innerHTML = `<b>SUSUNOD:</b> ${esc(i.code)} · ${esc(i.title)}${n.singer ? ' — ' + esc(n.singer) : ''}`
      + (waiting.length > 1 ? ` <span class="muted">(+${waiting.length - 1} pa)</span>` : '');
    bar.style.display = 'block';
  }
  $('idleHint').textContent = waiting.length && mode === 'idle'
    ? 'Naghahanda ng susunod na kanta...'
    : 'I-scan ang QR gamit ang phone camera para mag-reserve ng kanta.';
}

function flash(el, text, ms = 1400) {
  el.textContent = text;
  el.style.display = 'block';
  clearTimeout(el._t);
  el._t = setTimeout(() => el.style.display = 'none', ms);
}

// ================= QUEUE =================
async function loadQueue() {
  const { data, error } = await queueSelect(ROOM);
  if (error) { toast('Error sa pila: ' + error.message, 'err'); return; }
  queue = sortQueue(data || []);
  // kung na-delete ang current mula sa remote (hal. inalis), ituloy sa susunod
  if (current && !queue.find(q => q.id === current.id)) {
    stopMedia();
    current = null;
  }
  renderNextBar();
  if (started && !current) startNext();
}

let loadTimer = null;
function loadQueueSoon() { clearTimeout(loadTimer); loadTimer = setTimeout(loadQueue, 150); }

async function startNext() {
  if (starting || current) return;
  const next = queue.find(q => q.status === 'playing') || queue.find(q => q.status === 'waiting');
  if (!next) { show('idle'); broadcastState(); return; }
  starting = true;
  current = next;
  if (next.status !== 'playing') {
    await sb.from('queue').update({ status: 'playing' }).eq('id', next.id);
    next.status = 'playing';
  }
  await showSplash(next);
  starting = false;
  if (current !== next) { startNext(); return; } // na-skip habang splash
  try { await playItem(next); }
  catch (e) {
    console.error(e);
    if (e.name === 'NotAllowedError') { $('gateBtn').textContent = '▶ Tap para ituloy'; $('gate').style.display = 'flex'; }
    else failCurrent('Hindi ma-play: ' + (e.message || e));
  }
  broadcastState();
}

function failCurrent(msg, ms = 1500) {
  const id = current && current.id;
  toast(msg, 'err', 3500);
  setTimeout(() => { if (current && current.id === id) finishCurrent(); }, ms);
}

async function finishCurrent() {
  const done = current;
  stopMedia();
  current = null;
  paused = false;
  $('pauseBadge').style.display = 'none';
  if (done) {
    queue = queue.filter(q => q.id !== done.id);
    await sb.from('queue').delete().eq('id', done.id);
  }
  startNext();
}

function showSplash(item) {
  return new Promise(res => {
    const i = itemInfo(item);
    $('spNum').textContent = i.code;
    $('spT').textContent = i.title;
    $('spA').textContent = i.artist;
    $('spS').textContent = item.singer ? '🎤 ' + item.singer : '';
    $('splash').style.display = 'flex';
    setTimeout(() => { $('splash').style.display = 'none'; res(); }, 3000);
  });
}

// ================= PLAYBACK =================
function stopMedia() {
  try { vid.pause(); vid.removeAttribute('src'); vid.load(); } catch (e) {}
  try { aud.pause(); aud.removeAttribute('src'); aud.load(); } catch (e) {}
  try { if (ytPlayer && ytReady) ytPlayer.stopVideo(); } catch (e) {}
  lyrics = [];
}

async function playItem(item) {
  const i = itemInfo(item);
  $('nowTag').innerHTML = `<span class="code-badge ${i.source === 'youtube' ? 'yt' : ''}">${esc(i.code)}</span> ${esc(i.title)}${item.singer ? ' · 🎤 ' + esc(item.singer) : ''}`;
  applyVolume();

  if (item.song && item.song.kind === 'video') {
    show('video');
    vid.src = mediaUrl(item.song.media_path);
    await vid.play();
  } else if (item.song && item.song.kind === 'audio') {
    show('audio');
    $('aT').textContent = item.song.title;
    $('aA').textContent = item.song.artist || '';
    $('lyCur').textContent = ''; $('lyNext').textContent = '';
    lyrics = []; lyricOffset = 0;
    if (item.song.lrc_path) {
      try {
        const r = await fetch(mediaUrl(item.song.lrc_path) + '?v=' + Date.now());
        parseLrc(await r.text());
      } catch (e) { console.warn('LRC load failed', e); }
    }
    $('noLrc').style.display = lyrics.length ? 'none' : 'block';
    aud.src = mediaUrl(item.song.media_path);
    await aud.play();
  } else if (item.yt_id) {
    show('youtube');
    await ensureYT();
    ytPlayer.loadVideoById(item.yt_id);
  } else {
    throw new Error('Walang media');
  }
}

vid.addEventListener('ended', finishCurrent);
aud.addEventListener('ended', finishCurrent);
vid.addEventListener('error', () => { if (current && mode === 'video' && vid.getAttribute('src')) failCurrent('Error sa video file'); });
aud.addEventListener('error', () => { if (current && mode === 'audio' && aud.getAttribute('src')) failCurrent('Error sa audio file'); });

// ---- YouTube ----
window.onYouTubeIframeAPIReady = () => {
  ytPlayer = new YT.Player('ytPlayer', {
    width: '100%', height: '100%',
    playerVars: { autoplay: 1, controls: 0, rel: 0, modestbranding: 1, playsinline: 1, iv_load_policy: 3, fs: 0 },
    events: {
      onReady: () => { ytReady = true; applyVolume(); ytReadyWaiters.forEach(f => f()); ytReadyWaiters = []; },
      onStateChange: e => { if (e.data === YT.PlayerState.ENDED && mode === 'youtube') finishCurrent(); },
      onError: () => {
        if (mode !== 'youtube') return;
        failCurrent('Ayaw ipa-play ng YouTube ang video na ito sa labas ng YouTube. Lilipat sa susunod...', 2500);
      }
    }
  });
};
function loadYTApi() {
  if (window.YT && window.YT.Player) { if (!ytPlayer) onYouTubeIframeAPIReady(); return; }
  const s = document.createElement('script');
  s.src = 'https://www.youtube.com/iframe_api';
  document.head.appendChild(s);
}
function ensureYT() {
  if (ytReady) return Promise.resolve();
  return new Promise((res, rej) => {
    ytReadyWaiters.push(res);
    setTimeout(() => rej(new Error('Hindi ma-load ang YouTube player (internet?)')), 15000);
  });
}

// ---- Lyrics (LRC) ----
function parseLrc(text) {
  const out = [];
  text.split(/\r?\n/).forEach(line => {
    const off = line.match(/^\[offset:\s*([+-]?\d+)\]/i);
    if (off) { lyricOffset = parseInt(off[1], 10) / 1000; return; }
    const tags = [...line.matchAll(/\[(\d{1,3}):(\d{1,2}(?:[.:]\d{1,3})?)\]/g)];
    if (!tags.length) return;
    const words = line.replace(/\[[^\]]*\]/g, '').replace(/<[^>]*>/g, '').trim();
    tags.forEach(m => out.push({ t: parseInt(m[1], 10) * 60 + parseFloat(m[2].replace(':', '.')), text: words }));
  });
  lyrics = out.sort((a, b) => a.t - b.t);
}

function lyricTick() {
  requestAnimationFrame(lyricTick);
  if (mode !== 'audio' || !lyrics.length) return;
  const t = aud.currentTime + lyricOffset;
  let idx = -1;
  for (let k = 0; k < lyrics.length; k++) { if (lyrics[k].t <= t) idx = k; else break; }
  const cur = $('lyCur'), nxt = $('lyNext');
  if (idx < 0) {
    const first = lyrics[0];
    const left = first.t - t;
    cur.style.setProperty('--p', '0%');
    cur.textContent = left <= 4 ? '● '.repeat(Math.max(1, Math.ceil(left))).trim() : '♪ ♪ ♪';
    nxt.textContent = first.text;
    return;
  }
  const line = lyrics[idx];
  const nextT = idx + 1 < lyrics.length ? lyrics[idx + 1].t : line.t + 5;
  const dur = Math.min(nextT - line.t, Math.max(2, line.text.length * 0.18) + 1);
  const p = Math.max(0, Math.min(1, (t - line.t) / dur));
  if (cur.textContent !== (line.text || '♪')) cur.textContent = line.text || '♪';
  cur.style.setProperty('--p', (p * 100).toFixed(1) + '%');
  const upcoming = lyrics.slice(idx + 1).find(l => l.text);
  nxt.textContent = upcoming ? upcoming.text : '';
}
requestAnimationFrame(lyricTick);

// ================= CONTROLS =================
function applyVolume() {
  vid.volume = volume / 100;
  aud.volume = volume / 100;
  try { if (ytPlayer && ytReady) ytPlayer.setVolume(volume); } catch (e) {}
  localStorage.setItem('vk_vol', volume);
}

function setPaused(p) {
  if (!current) return;
  paused = p;
  if (mode === 'video') p ? vid.pause() : vid.play();
  if (mode === 'audio') p ? aud.pause() : aud.play();
  if (mode === 'youtube' && ytReady) p ? ytPlayer.pauseVideo() : ytPlayer.playVideo();
  $('pauseBadge').style.display = p ? 'flex' : 'none';
}

function restart() {
  if (mode === 'video') vid.currentTime = 0;
  if (mode === 'audio') aud.currentTime = 0;
  if (mode === 'youtube' && ytReady) ytPlayer.seekTo(0, true);
  setPaused(false);
}

function handleCmd(action, value) {
  switch (action) {
    case 'toggle': setPaused(!paused); break;
    case 'pause': setPaused(true); break;
    case 'play': setPaused(false); break;
    case 'skip': if (current) finishCurrent(); break;
    case 'restart': restart(); break;
    case 'volup': volume = Math.min(100, volume + 10); applyVolume(); flash($('volBadge'), '🔊 ' + volume); break;
    case 'voldown': volume = Math.max(0, volume - 10); applyVolume(); flash($('volBadge'), '🔉 ' + volume); break;
    case 'setvol': volume = Math.max(0, Math.min(100, parseInt(value, 10) || 0)); applyVolume(); flash($('volBadge'), '🔊 ' + volume); break;
    case 'refresh': loadQueue(); break;
  }
  broadcastState();
}

function broadcastState() {
  if (!channel) return;
  const now = current ? { ...itemInfo(current), singer: current.singer || '', id: current.id } : null;
  channel.send({ type: 'broadcast', event: 'state', payload: { now, paused, volume, room: ROOM } });
}

// ================= REALTIME =================
function connect() {
  if (channel) sb.removeChannel(channel);
  channel = sb.channel('room-' + ROOM, { config: { broadcast: { self: false } } })
    .on('broadcast', { event: 'cmd' }, ({ payload }) => handleCmd(payload.action, payload.value))
    .on('broadcast', { event: 'hello' }, () => broadcastState())
    .on('postgres_changes', { event: '*', schema: 'public', table: 'queue' }, () => loadQueueSoon())
    .subscribe(status => {
      if (status === 'SUBSCRIBED') { loadQueue(); broadcastState(); }
      if (status === 'CHANNEL_ERROR' || status === 'TIMED_OUT') toast('Nawala ang koneksyon, kumokonekta ulit...', 'err');
    });
}

// Backup polling kung sakaling ma-miss ang realtime event
setInterval(() => { if (started) loadQueue(); }, 15000);

// ================= UI EVENTS =================
function toggleFs() {
  if (document.fullscreenElement) document.exitFullscreen();
  else document.documentElement.requestFullscreen().catch(() => {});
}
$('btnFs').onclick = toggleFs;
$('btnClose').onclick = () => {
  if (!confirm('Isara ang host? Hihinto ang kanta.')) return;
  if (document.fullscreenElement) document.exitFullscreen().catch(() => {});
  location.href = '../';
};
// Ipakita ang mga button kapag gumalaw ang mouse o tinap ang screen, tapos itago ulit
let uiTimer = null;
['mousemove', 'touchstart', 'click'].forEach(ev => document.addEventListener(ev, () => {
  document.body.classList.add('ui');
  clearTimeout(uiTimer);
  uiTimer = setTimeout(() => document.body.classList.remove('ui'), 3000);
}, { passive: true }));
$('btnSkip').onclick = () => handleCmd('skip');
$('btnNewRoom').onclick = () => {
  if (!confirm('Gumawa ng bagong room code? Kailangang i-scan ulit ng mga phone ang bagong QR.')) return;
  ROOM = makeRoomCode();
  localStorage.setItem('vk_room', ROOM);
  history.replaceState(null, '', location.pathname);
  stopMedia(); current = null; queue = [];
  renderQr(); show('idle'); connect();
};
document.addEventListener('keydown', e => {
  if (!started) return;
  if (e.key === ' ') { e.preventDefault(); handleCmd('toggle'); }
  else if (e.key === 'n' || e.key === 'N' || e.key === 'ArrowRight') handleCmd('skip');
  else if (e.key === 'ArrowUp') handleCmd('volup');
  else if (e.key === 'ArrowDown') handleCmd('voldown');
  else if (e.key === 'f' || e.key === 'F') toggleFs();
  else if (e.key === 'r' || e.key === 'R') handleCmd('restart');
});

$('gate').onclick = () => {
  if (started) { // na-block ang autoplay — ituloy ang kasalukuyang kanta
    $('gate').style.display = 'none';
    if (mode === 'video') vid.play().catch(() => {});
    if (mode === 'audio') aud.play().catch(() => {});
    return;
  }
  started = true;
  $('gate').style.display = 'none';
  // i-unlock ang audio para sa susunod na autoplay
  [vid, aud].forEach(el => { el.muted = true; el.play().catch(() => {}); el.pause(); el.muted = false; });
  loadYTApi();
  loadQueue();
};

// ================= INIT =================
renderQr();
show('idle');
connect();
