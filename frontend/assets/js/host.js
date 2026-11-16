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
  document.body.classList.toggle('playing', playing);
  $('cornerQr').style.display = playing ? 'flex' : 'none';
  $('nowTag').style.display = (m === 'video' || m === 'youtube') ? 'block' : 'none'; // sa audio, nasa screen na ang title
  // ang background ay tumatakbo lang kapag hindi natatakpan ng video
  if (started) applyBg(false);
  renderNextBar();
}

// Listahan ng naka-reserve sa itaas ng screen, parang tunay na videoke
function renderNextBar() {
  const waiting = queue.filter(q => q.status === 'waiting' && (!current || q.id !== current.id));
  const bar = $('nextBar');
  if (!waiting.length) {
    bar.innerHTML = `<span class="rs-empty">${current ? 'Walang kasunod. Mag-reserve gamit ang phone.' : 'Walang naka-reserve'}</span>`;
  } else {
    const max = 5;
    bar.innerHTML = `<span class="rs-lbl">Reserved<b>${waiting.length}</b></span>` + waiting.slice(0, max).map((q, k) => {
      const i = itemInfo(q);
      return `<span class="rs ${k === 0 ? 'first' : ''}"><span class="dot">${esc(i.code)}</span><span>${esc(i.title)}</span></span>`;
    }).join('') + (waiting.length > max ? `<span class="rs-more">+${waiting.length - max}</span>` : '');
  }
  $('idleHint').textContent = waiting.length && mode === 'idle'
    ? 'Inihahanda ang susunod na kanta'
    : 'I-scan ang QR code gamit ang camera ng phone para mag-reserve ng kanta.';
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
  renderSoloQueue();
  if (started && !current) startNext();
}

let loadTimer = null;
function loadQueueSoon() { clearTimeout(loadTimer); loadTimer = setTimeout(loadQueue, 150); }

async function startNext() {
  if (starting || current || scoring) return;
  const next = queue.find(q => q.status === 'playing') || queue.find(q => q.status === 'waiting');
  if (!next) { show('idle'); broadcastState(); return; }
  starting = true;
  current = next;
  if (window.vkWake) window.vkWake();
  if (VK_PREFS.get().bgChange) applyBg(true);
  pickSongColor();
  if (next.status !== 'playing') {
    await sb.from('queue').update({ status: 'playing' }).eq('id', next.id);
    next.status = 'playing';
  }
  await showSplash(next);
  starting = false;
  if (current !== next) { startNext(); return; } // na-skip habang splash
  resetMicStat(); VKPitch.clear();
  try { await playItem(next); }
  catch (e) {
    console.error(e);
    if (e.name === 'NotAllowedError') { $('gateBtn').querySelector('span').textContent = 'Ituloy'; $('gate').style.display = 'flex'; }
    else failCurrent('Hindi ma-play: ' + (e.message || e));
  }
  broadcastState();
}

function failCurrent(msg, ms = 1500) {
  const id = current && current.id;
  toast(msg, 'err', 3500);
  setTimeout(() => { if (current && current.id === id) finishCurrent('error'); }, ms);
}

// reason: 'ended' (natapos ang kanta), 'skip', 'error'
let scoring = false;
async function finishCurrent(reason = 'skip') {
  const done = current;
  stopMedia();
  current = null;
  paused = false;
  $('pauseBadge').style.display = 'none';
  if (done) {
    queue = queue.filter(q => q.id !== done.id);
    sb.from('queue').delete().eq('id', done.id);
    if (reason === 'ended' && VK_PREFS.get().scoreMode !== 'off') await showScore(done);
  }
  startNext();
}

// ----- Score (pang-aliw; hindi pa sinusukat ang boses) -----
function pickScore() {
  // mas mataas ang score sa Madali, mas mahirap makakuha ng mataas sa Mahirap
  const lvl = VK_PREFS.get().scoreLevel;
  const [lo, hi, bias] = lvl === 'easy' ? [82, 100, .6] : lvl === 'hard' ? [55, 99, 1.6] : [70, 100, 1];
  return Math.round(hi - Math.pow(Math.random(), 1 / bias) * (hi - lo));
}
function scoreMsg(n) {
  return n === 100 ? 'Perfect! Pang-concert ang boses!' : n >= 95 ? 'Ang galing! Isa pa!' : n >= 88 ? 'Ang ganda ng boses!'
    : n >= 80 ? 'Magaling! Palakpakan natin!' : 'Pwede na! Kanta pa ulit!';
}
function showScore(item) {
  return new Promise(res => {
    scoring = true;
    const prS = VK_PREFS.get();
    const fromMic = prS.scoreMode === 'mic' ? micScore() : null;
    const n = fromMic !== null ? fromMic : pickScore(), el = $('scoreScreen');
    $('scNote').textContent = prS.scoreMode === 'mic'
      ? (fromMic !== null ? (VKPitch.accuracy() !== null ? 'Batay sa tono at pagkanta habang may lyrics' : 'Batay sa pagkanta habang may lyrics') : 'Walang mic na nakakonekta, kaya random ang score')
      : '';
    $('scNum').textContent = '0';
    $('scMsg').textContent = '';
    $('scWho').textContent = item.singer ? item.singer : '';
    el.style.display = 'flex';
    if (started) applyBg(false);
    const pr = VK_PREFS.get();
    VKSfx.setVolume(pr.sfxVolume / 100);
    if (pr.applause > 0) VKSfx.play('drumroll');
    const t0 = performance.now(), dur = 1900;
    (function count(now) {
      const k = Math.min(1, (now - t0) / dur);
      $('scNum').textContent = k < 1 ? Math.floor(Math.random() * 100) : n;
      if (k < 1) return requestAnimationFrame(count);
      $('scMsg').textContent = scoreMsg(n);
      if (pr.applause > 0) {
        VKSfx.play('fanfare');
        VKSfx.play('applause', { size: [0, .5, 1, 1.5][pr.applause] * (n >= 95 ? 1.2 : n >= 85 ? 1 : .7) });
      }
      if (channel) channel.send({ type: 'broadcast', event: 'score', payload: { score: n, singer: item.singer || '', title: itemInfo(item).title } });
      setTimeout(() => { el.style.display = 'none'; scoring = false; res(); }, 4200);
    })(t0);
  });
}

function showSplash(item) {
  return new Promise(res => {
    const i = itemInfo(item);
    $('spNum').textContent = i.code;
    $('spT').textContent = i.title;
    $('spA').textContent = i.artist;
    $('spS').textContent = item.singer ? 'Kumakanta: ' + item.singer : '';
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
  document.body.classList.remove('has-pitch');   // ang stats ay nililinis sa simula ng susunod na kanta (kailangan pa sa score)
}

async function playItem(item) {
  const i = itemInfo(item);
  $('nowTag').innerHTML = `<span class="dot">${esc(i.code)}</span><span>${esc(i.title)}</span>${item.singer ? `<span class="nt-s">${esc(item.singer)}</span>` : ''}`;
  applyVolume();

  if (item.song && item.song.kind === 'video') {
    show('video');
    vid.src = mediaUrl(item.song.media_path);
    await vid.play();
  } else if (item.song && item.song.kind === 'audio') {
    show('audio');
    $('aT').textContent = item.song.title;
    $('aA').textContent = item.song.artist || '';
    $('aS').textContent = item.singer ? 'Kumakanta: ' + item.singer : '';
    $('lyCur').textContent = ''; lineText = null; $('lyNext').textContent = '';
    lyrics = []; lyricOffset = 0;
    if (item.song.lrc_path) {
      try {
        const r = await fetch(mediaUrl(item.song.lrc_path) + '?v=' + Date.now());
        parseLrc(await r.text());
      } catch (e) { console.warn('LRC load failed', e); }
    }
    $('noLrc').style.display = lyrics.length ? 'none' : 'block';
    loadMelody(item.song);
    aud.src = mediaUrl(item.song.media_path);
    await aud.play();
  } else if (item.yt_id || (item.song && item.song.kind === 'youtube')) {
    show('youtube');
    await ensureYT();
    ytPlayer.loadVideoById(item.yt_id || item.song.yt_id);
  } else {
    throw new Error('Walang media');
  }
}

vid.addEventListener('ended', () => finishCurrent('ended'));
aud.addEventListener('ended', () => finishCurrent('ended'));
vid.addEventListener('error', () => { if (current && mode === 'video' && vid.getAttribute('src')) failCurrent('Error sa video file'); });
aud.addEventListener('error', () => { if (current && mode === 'audio' && aud.getAttribute('src')) failCurrent('Error sa audio file'); });

// ---- YouTube ----
window.onYouTubeIframeAPIReady = () => {
  ytPlayer = new YT.Player('ytPlayer', {
    width: '100%', height: '100%',
    playerVars: { autoplay: 1, controls: 0, rel: 0, modestbranding: 1, playsinline: 1, iv_load_policy: 3, fs: 0 },
    events: {
      onReady: () => { ytReady = true; applyVolume(); ytReadyWaiters.forEach(f => f()); ytReadyWaiters = []; },
      onStateChange: e => { if (e.data === YT.PlayerState.ENDED && mode === 'youtube') finishCurrent('ended'); },
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

// ---- Pitch bar: melody ng kanta (demo .mel.json ngayon; MIDI sa susunod) ----
const songTime = () => aud.currentTime + lyricOffset + (VK_PREFS.get().lyricDelay || 0) / 1000;
async function loadMelody(song) {
  VKPitch.clear(); document.body.classList.remove('has-pitch');
  if (VK_PREFS.get().pitchBar === 'off') return;
  const path = song.mel_path || (song.media_path || '').replace(/\.(mp3|m4a|ogg|wav)$/i, '.mel.json');
  if (!/\.mel\.json$/.test(path)) return;
  try {
    const r = await fetch(mediaUrl(path));
    if (!r.ok) return;
    const j = await r.json();
    if (VKPitch.load(j.notes)) document.body.classList.add('has-pitch');
  } catch (e) { /* walang melody: walang pitch bar */ }
}
VKPitch.init($('pitchBar'));
requestAnimationFrame(pitchTick);
function pitchTick() {
  requestAnimationFrame(pitchTick);
  if (mode === 'audio' && VKPitch.has()) VKPitch.draw(songTime());
}
setInterval(() => {
  if (mode !== 'audio' || paused || scoring || !VKPitch.has()) return;
  VKPitch.sample(songTime(), VKMicHost.pitch());
}, 50);

function lyricTick() {
  requestAnimationFrame(lyricTick);
  if (mode !== 'audio' || !lyrics.length) return;
  const t = aud.currentTime + lyricOffset + (VK_PREFS.get().lyricDelay || 0) / 1000;
  let idx = -1;
  for (let k = 0; k < lyrics.length; k++) { if (lyrics[k].t <= t) idx = k; else break; }
  const cur = $('lyCur'), nxt = $('lyNext'), cue = $('lyCue');
  // countdown na 3-2-1 bago pumasok ang boses
  const cd = VK_PREFS.get().countdown;
  const cueText = (left, label) => {
    if (cd === 'off') return '';
    if (left > 3.2) return label;
    const n = Math.max(1, Math.ceil(left));
    return cd === 'dots' ? '● '.repeat(n).trim() : String(n);
  };
  if (idx < 0) {
    const first = lyrics[0];
    const left = first.t - t;
    cur.style.setProperty('--p', '0%');
    cur.textContent = ''; lineText = null;
    const c = cd === 'off' ? '' : left <= 3.2 ? cueText(left) : (left > 6 ? `Intro, ${Math.ceil(left)}` : '');
    if (cue.textContent !== c) cue.textContent = c;
    setNext(0);
    return;
  }
  const line = lyrics[idx];
  const nextT = idx + 1 < lyrics.length ? lyrics[idx + 1].t : line.t + 5;
  const dur = Math.min(nextT - line.t, Math.max(2, line.text.length * 0.18) + 1);
  const p = Math.max(0, Math.min(1, (t - line.t) / dur));
  // Interlude: mahabang pagitan na walang kakantahin
  const gap = nextT - t, sung = t > line.t + dur + 0.8;
  let c = '';
  if (cd !== 'off' && idx + 1 < lyrics.length && sung && nextT - line.t - dur > 5) c = gap <= 3.2 ? cueText(gap) : `Interlude, ${Math.ceil(gap)}`;
  if (cue.textContent !== c) cue.textContent = c;
  if (!line.text && idx + 1 < lyrics.length && gap > 3.2) { cur.textContent = ''; lineText = null; setNext(idx + 1); return; }
  setLine(line.text || '♪', p);
  setNext(idx + 1);
}
// Paliitin ang font para kasya sa iisang linya (parang videoke); kapag sobrang haba, saka lang hahatiin
function fitLine(el) {
  el.style.fontSize = ''; el.style.whiteSpace = 'nowrap';
  const box = $('lyrics').clientWidth, w = el.scrollWidth;
  if (!box || !w || w <= box) return;
  const r = box / w * 0.97;
  if (r >= 0.45) el.style.fontSize = (parseFloat(getComputedStyle(el).fontSize) * r).toFixed(1) + 'px';
  else el.style.whiteSpace = '';
}
// Linyang kinakanta: hinahati sa salita para sunod-sunod ang highlight kahit maging 2 linya sa screen
let lineText = null, lineWords = [];
function setLine(text, p) {
  const cur = $('lyCur');
  if (text !== lineText) {
    lineText = text;
    const parts = text.split(/(\s+)/).filter(Boolean);
    let pos = 0;
    cur.innerHTML = '';
    lineWords = [];
    parts.forEach(w => {
      if (/^\s+$/.test(w)) { cur.appendChild(document.createTextNode(' ')); pos += 1; return; }
      const s = document.createElement('span'); s.className = 'w'; s.textContent = w;
      s._a = pos; s._b = pos + w.length; pos += w.length;
      cur.appendChild(s); lineWords.push(s);
    });
    lineWords.total = Math.max(1, pos);
    fitLine(cur);
  }
  const at = p * lineWords.total;
  for (const s of lineWords) {
    const v = Math.max(0, Math.min(1, (at - s._a) / (s._b - s._a)));
    const css = (v * 100).toFixed(1) + '%';
    if (s._p !== css) { s._p = css; s.style.setProperty('--p', css); }
  }
}
// mga susunod na linya (1 o 3, depende sa Settings)
let lastNextKey = '';
function setNext(from) {
  const n = VK_PREFS.get().lyricLines >= 4 ? 3 : 1;
  const up = lyrics.slice(from).filter(l => l.text).slice(0, n).map(l => l.text);
  const key = from + '|' + n;
  if (key === lastNextKey) return;
  lastNextKey = key;
  $('lyNext').innerHTML = up.map(t => `<div>${esc(t)}</div>`).join('');
  $('lyNext').querySelectorAll('div').forEach(fitLine);
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
    case 'skip': if (current) finishCurrent('skip'); break;
    case 'sfx': playSfx(value); return;
    case 'restart': restart(); break;
    case 'volup': volume = Math.min(100, volume + 10); applyVolume(); flash($('volBadge'), 'Volume ' + volume); break;
    case 'voldown': volume = Math.max(0, volume - 10); applyVolume(); flash($('volBadge'), 'Volume ' + volume); break;
    case 'setvol': volume = Math.max(0, Math.min(100, parseInt(value, 10) || 0)); applyVolume(); flash($('volBadge'), 'Volume ' + volume); break;
    case 'refresh': loadQueue(); break;
    case 'scene': nextScene(); break;
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
    .on('broadcast', { event: 'rtc' }, ({ payload }) => { if (payload && payload.to === 'host') VKMicHost.handle(payload); })
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

// ----- Landscape sa phone/tablet -----
// Sa Android Chrome: fullscreen + lock sa landscape. Kapag hindi kaya (hal. iPhone), may paalala na i-rotate ang phone.
const isTouch = matchMedia('(pointer: coarse)').matches;
const isPortrait = () => matchMedia('(orientation: portrait)').matches;
async function goLandscape() {
  try {
    if (!document.fullscreenElement && document.documentElement.requestFullscreen) await document.documentElement.requestFullscreen({ navigationUI: 'hide' });
    if (screen.orientation && screen.orientation.lock) await screen.orientation.lock('landscape');
  } catch (e) { /* hindi suportado: ang paalala na lang */ }
  setTimeout(checkRotate, 400);
}
function checkRotate() {
  const want = isTouch && started && VK_PREFS.get().tvOrient === 'landscape';
  $('rotateHint').hidden = !(want && isPortrait());
}
$('btnRotate').onclick = () => {
  if (VK_PREFS.get().tvOrient === 'landscape' && !isPortrait()) {   // nakahiga na: ibalik sa auto
    VK_PREFS.set('tvOrient', 'auto');
    try { screen.orientation.unlock(); } catch (e) {}
    toast('Auto na ang screen', 'ok');
  } else { VK_PREFS.set('tvOrient', 'landscape'); goLandscape(); }
  checkRotate();
};
$('rotGo').onclick = goLandscape;
$('rotStay').onclick = () => { VK_PREFS.set('tvOrient', 'auto'); checkRotate(); };
matchMedia('(orientation: portrait)').addEventListener('change', checkRotate);
if (!isTouch) $('btnRotate').hidden = true;
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
  if (!started || e.target.closest('input, textarea, select')) return;
  if (e.key === ' ') { e.preventDefault(); handleCmd('toggle'); }
  else if (e.key === 'n' || e.key === 'N' || e.key === 'ArrowRight') handleCmd('skip');
  else if (e.key === 'ArrowUp') handleCmd('volup');
  else if (e.key === 'ArrowDown') handleCmd('voldown');
  else if (e.key === 'f' || e.key === 'F') toggleFs();
  else if (e.key === 'r' || e.key === 'R') handleCmd('restart');
  else if (e.key === 'b' || e.key === 'B') nextScene();
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
  if (isTouch && VK_PREFS.get().tvOrient === 'landscape') goLandscape();
  keepAwake();
  VKSfx.unlock();
  VKMicHost.unlock();
  const sc = VK_PREFS.get().scene;
  VKScenes.start($('scene'), sc === 'auto' ? VKScenes.NAMES[Math.floor(Math.random() * VKScenes.NAMES.length)] : sc);
  applyBg(false);
  // i-unlock ang audio para sa susunod na autoplay
  [vid, aud].forEach(el => { el.muted = true; el.play().catch(() => {}); el.pause(); el.muted = false; });
  loadYTApi();
  loadQueue();
};

// ================= SCREENSAVER + SETTINGS =================
// Screensaver lang kapag walang kanta; may QR at room code para makasali pa rin
// Ang host ay may sariling "attract screen" (background + orasan + QR), kaya hindi na kailangan ang itim na screensaver
window.vkCanSleep = () => false;
window.vkSaverExtra = () => `<div class="ss-qr">${$('qrBig').innerHTML}</div><div>I-scan para mag-reserve<br>Room <b>${ROOM}</b></div>`;
const HL_COLORS = ['#62e3d2', '#ffd54a', '#ff7ab6', '#7cc4ff', '#9be15d', '#ffa552', '#c49bff'];
let songColor = null;
function pickSongColor() {
  const p = VK_PREFS.get();
  songColor = p.hlRandom ? HL_COLORS[Math.floor(Math.random() * HL_COLORS.length)] : null;
  applyPrefs();
}
function applyPrefs() {
  const p = VK_PREFS.get(), b = document.body;
  b.classList.remove('lyr-s', 'lyr-m', 'lyr-l', 'lyr-xl');
  b.classList.add('lyr-' + p.lyricSize);
  b.classList.toggle('lyr-left', p.lyricAlign === 'left');
  b.classList.toggle('lyr-4', p.lyricLines >= 4);
  const ly = $('lyrics');
  ly.style.setProperty('--hl', songColor || p.hlColor);
  const o = [0, 2, 4][p.outline] || 0;
  const f = [];
  if (o) f.push(`drop-shadow(${o / 2}px 0 0 #000)`, `drop-shadow(-${o / 2}px 0 0 #000)`, `drop-shadow(0 ${o / 2}px 0 #000)`, `drop-shadow(0 -${o / 2}px 0 #000)`);
  if (p.lyricShadow) f.push('drop-shadow(0 4px 10px rgba(0,0,0,.65))');
  ly.style.setProperty('--lyFilter', f.length ? f.join(' ') : 'none');
  lastNextKey = '';
  // teksto sa TV
  $('idleTitle').textContent = p.idleText || '';
  $('idleTitle').style.display = p.idleText ? '' : 'none';
  const mq = $('marquee'), txt = (p.bottomText || '').trim();
  b.classList.toggle('has-marquee', !!txt);
  if (txt && $('marqueeText').textContent !== txt) {
    $('marqueeText').textContent = txt;
    $('marqueeText').style.animationDuration = Math.max(12, txt.length * 0.32) + 's';
  }
  mq.style.setProperty('--mq', p.bottomColor || '#ffd54a');
  if (started) applyBg(false);
}
window.addEventListener('vkprefs', applyPrefs);
applyPrefs();

// Huwag hayaang mag-sleep ang screen ng TV/laptop habang bukas ang host
let wakeLock = null;
async function keepAwake() {
  try { if ('wakeLock' in navigator && !wakeLock) { wakeLock = await navigator.wakeLock.request('screen'); wakeLock.addEventListener('release', () => wakeLock = null); } } catch (e) {}
}
document.addEventListener('visibilitychange', () => { if (document.visibilityState === 'visible' && started) keepAwake(); });

// ================= SOUND EFFECTS mula sa remote =================
const SFX_LABEL = { applause: 'Palakpak', cheers: 'Cheers', drumroll: 'Drum roll', ding: 'Ding', horn: 'Busina', boom: 'Boom',
  wow: 'Wow', aww: 'Aww', boo: 'Boo', laugh: 'Tawa', angry: 'Galit', omg: 'OMG', wownaman: 'Wow naman', galing: 'Ang galing' };
let lastSfx = 0;
function playSfx(v) {
  const p = VK_PREFS.get();
  if (!p.sfxEnabled) return;
  const o = typeof v === 'object' && v ? v : { name: v };
  if (o.name === 'stop') { VKSfx.stop(); return; }
  if (!SFX_LABEL[o.name] || Date.now() - lastSfx < 350) return;
  lastSfx = Date.now();
  VKSfx.setVolume(p.sfxVolume / 100);
  const gain = o.vol == null ? 1 : Math.max(0, Math.min(1, +o.vol));
  VKSfx.play(o.name, { size: 1, gain, solo: o.solo == null ? p.sbNoOverlap : !!o.solo });
  const n = $('sfxNote');
  n.textContent = SFX_LABEL[o.name] + (o.from ? ` mula kay ${o.from}` : '');
  n.classList.remove('show'); void n.offsetWidth; n.classList.add('show');
}

// ================= MIC (phone bilang mic) =================
VKMicHost.init(
  msg => { if (channel) channel.send({ type: 'broadcast', event: 'rtc', payload: { ...msg, from: 'host' } }); },
  renderMics,
  () => toast('Umuugong ang speaker. Hininaan muna ang mic. Ilayo ang phone sa speaker o hinaan ang mic sa Settings.', 'err', 5000)
);
function renderMics() {
  const list = VKMicHost.list();
  $('micBar').innerHTML = list.map(m => `<span class="mic-chip ${m.muted ? 'muted' : ''}" data-mic="${esc(m.id)}">${ICON('mic')}<span>${esc(m.name)}</span><i class="lv"><b></b></i></span>`).join('');
}
// lugar ng linya ng lyrics sa oras na t (para sa score mula sa mic)
function inLyricLine(t) {
  if (!lyrics.length) return true;
  let idx = -1;
  for (let k = 0; k < lyrics.length; k++) { if (lyrics[k].t <= t) idx = k; else break; }
  if (idx < 0) return false;
  const line = lyrics[idx];
  if (!line.text) return false;
  const nextT = idx + 1 < lyrics.length ? lyrics[idx + 1].t : line.t + 5;
  const dur = Math.min(nextT - line.t, Math.max(2, line.text.length * 0.18) + 1);
  return t <= line.t + dur + 0.3;
}
let micStat = null;
function resetMicStat() { micStat = { inLine: 0, voiced: 0, mics: 0 }; }
function micScore() {
  if (!micStat || micStat.mics === 0 || micStat.inLine < 50) return null;
  let r = Math.min(1, micStat.voiced / micStat.inLine * 1.1);
  const acc = VKPitch.accuracy();                 // kapag may pitch bar, kasama na ang tono
  if (acc !== null) r = 0.35 * r + 0.65 * Math.min(1, acc * 1.25);
  const lvl = VK_PREFS.get().scoreLevel;
  const n = lvl === 'easy' ? 62 + 38 * r : lvl === 'hard' ? 40 + 60 * Math.pow(r, 1.4) : 52 + 48 * r;
  return Math.max(40, Math.min(100, Math.round(n)));
}
setInterval(() => {
  const lv = VKMicHost.levels();
  lv.forEach(m => {
    const el = document.querySelector(`[data-mic="${CSS.escape(m.id)}"] .lv b`);
    if (el) el.style.width = Math.min(100, m.level * 400) + '%';
  });
  if (!current || !micStat || paused || mode === 'idle' || scoring) return;
  const live = lv.filter(m => m.live && !m.muted);
  micStat.mics = Math.max(micStat.mics, live.length);
  if (!live.length) return;
  const voiced = live.some(m => m.level > 0.025);
  const inLine = mode === 'audio' ? inLyricLine(aud.currentTime + lyricOffset + (VK_PREFS.get().lyricDelay || 0) / 1000) : true;
  if (inLine) { micStat.inLine++; if (voiced) micStat.voiced++; }
}, 100);

// ================= ORASAN + BACKGROUND =================
const DAYS = ['Linggo', 'Lunes', 'Martes', 'Miyerkules', 'Huwebes', 'Biyernes', 'Sabado'];
const MONTHS = ['Enero', 'Pebrero', 'Marso', 'Abril', 'Mayo', 'Hunyo', 'Hulyo', 'Agosto', 'Setyembre', 'Oktubre', 'Nobyembre', 'Disyembre'];
function tickClock() {
  const p = VK_PREFS.get(), d = new Date();
  let h = d.getHours();
  const ampm = p.ss24h ? '' : (h < 12 ? 'AM' : 'PM');
  if (!p.ss24h) h = h % 12 || 12;
  const hm = (p.ss24h ? String(h).padStart(2, '0') : h) + ':' + String(d.getMinutes()).padStart(2, '0');
  $('tbClock').textContent = hm; $('tbAmpm').textContent = ampm;
  $('idleClock').textContent = hm; $('idleAmpm').textContent = ampm;
  $('idleDate').textContent = `${DAYS[d.getDay()]}, ${MONTHS[d.getMonth()]} ${d.getDate()}`;
}
setInterval(tickClock, 1000); tickClock();
// ----- Background: animated / sariling video / plain -----
const bgv = $('bgv');
let bgList = null, bgPick = null;
async function loadBgList() {
  const { data } = await sb.from('backgrounds').select('id, title, path');
  bgList = data || [];
}
async function applyBg(change) {
  const p = VK_PREFS.get();
  const covered = mode === 'video' || mode === 'youtube';
  const wall = $('wallImg');
  if (p.bgMode === 'image' && window.VKWall && VKWall.url()) {
    if (window.VKScenes) VKScenes.pause();
    bgv.pause(); bgv.style.display = 'none';
    $('scene').style.visibility = 'hidden';
    document.body.classList.remove('bg-plain');
    wall.style.backgroundImage = `url("${VKWall.url()}")`;
    wall.style.display = 'block';
    return;
  }
  wall.style.display = 'none';
  if (p.bgMode === 'videos') {
    if (!bgList) await loadBgList();
    if (bgList.length) {
      if (window.VKScenes) VKScenes.pause();
      $('scene').style.visibility = 'hidden';
      document.body.classList.remove('bg-plain');
      if (change || !bgPick) {
        const choices = bgList.length > 1 ? bgList.filter(b => !bgPick || b.id !== bgPick.id) : bgList;
        bgPick = choices[Math.floor(Math.random() * choices.length)];
        bgv.src = mediaUrl(bgPick.path);
      }
      bgv.style.display = 'block';
      covered ? bgv.pause() : bgv.play().catch(() => {});
      return;
    }
  }
  bgv.pause(); bgv.style.display = 'none';
  if (p.bgMode === 'plain') {
    if (window.VKScenes) VKScenes.pause();
    $('scene').style.visibility = 'hidden';
    document.body.classList.add('bg-plain');
    return;
  }
  document.body.classList.remove('bg-plain');
  $('scene').style.visibility = 'visible';
  if (!window.VKScenes) return;
  if (change && p.scene === 'auto') VKScenes.next();
  covered ? VKScenes.pause() : VKScenes.resume();
}
function nextScene() {
  const p = VK_PREFS.get();
  if (p.bgMode === 'videos') { applyBg(true); return; }
  if (p.bgMode === 'plain') { VK_PREFS.set('bgMode', 'animated'); return; }
  if (!window.VKScenes) return;
  VKScenes.next();
  if (p.scene !== 'auto') VK_PREFS.set('scene', VKScenes.current());
}
$('btnScene').onclick = nextScene;
window.addEventListener('vkwall', () => { if (started) applyBg(false); });
window.addEventListener('vkprefs', () => {
  const sc = VK_PREFS.get().scene;
  if (window.VKScenes && sc !== 'auto' && sc !== VKScenes.current()) VKScenes.set(sc);
  tickClock();
});

// ================= ISANG DEVICE LANG (solo mode) =================
const HOST_NAME = () => localStorage.getItem('vk_name') || null;
async function hostReserve(songId, yt) {
  const row = yt ? { room: ROOM, yt_id: yt.yt_id, yt_title: yt.yt_title, singer: HOST_NAME() } : { room: ROOM, song_id: songId, singer: HOST_NAME() };
  const { error } = await sb.from('queue').insert(row);
  if (error) { toast('Hindi na-reserve: ' + error.message, 'err'); return false; }
  if (window.VKVoice) VKVoice.act('reserved');
  toast(current ? 'Naka-reserve' : 'Naka-reserve. Magsisimula na.', 'ok');
  loadQueue();
  return true;
}
async function findSong(code) {
  const { data } = await sb.from('songs').select('id, code, title, artist').eq('code', code).maybeSingle();
  return data;
}

// --- panel ---
function openSolo(tab) {
  $('solo').hidden = false;
  document.body.classList.add('solo-open');
  if (tab) soloTab(tab);
  renderSoloQueue();
}
function closeSolo() { $('solo').hidden = true; document.body.classList.remove('solo-open'); }
function soloTab(t) {
  document.querySelectorAll('.solo-tabs button').forEach(b => b.classList.toggle('on', b.dataset.st === t));
  document.querySelectorAll('.solo-pane').forEach(p => p.classList.toggle('on', p.id === 'sp-' + t));
  if (t === 'search' && !$('sList').innerHTML) soloSearch();
  if (t === 'queue') renderSoloQueue();
}
$('soloBtn').onclick = () => openSolo('code');
$('soloClose').onclick = closeSolo;
document.querySelectorAll('.solo-tabs button').forEach(b => b.onclick = () => soloTab(b.dataset.st));
document.querySelectorAll('.solo-ctrls button').forEach(b => b.onclick = () => handleCmd(b.dataset.cmd));

// keypad sa panel
let sCode = '', sSong = null;
function renderSCode() {
  $('sCode').innerHTML = esc(sCode) + `<span class="ghost">${'8'.repeat(5 - sCode.length)}</span>`;
  $('sRes').disabled = !sSong;
  $('sRes').innerHTML = sSong ? `Reserve <span class="dot">${sSong.code}</span>` : 'Reserve';
}
$('sPad').addEventListener('click', async e => {
  const b = e.target.closest('button'); if (!b) return;
  const k = b.dataset.k;
  if (window.VKVoice) VKVoice.key(k || b.textContent.trim());
  if (k === 'clr') sCode = ''; else if (k === 'del') sCode = sCode.slice(0, -1); else if (sCode.length < 5) sCode += b.textContent;
  sSong = null; renderSCode();
  $('sPrev').textContent = 'I-type ang song number';
  if (sCode.length === 5) {
    const typed = sCode, s = await findSong(parseInt(sCode, 10));
    if (typed !== sCode) return;
    sSong = s;
    if (window.VKVoice) s ? VKVoice.say(s.title) : VKVoice.act('notfound');
    $('sPrev').innerHTML = s ? `<b>${esc(s.title)}</b> ${esc(s.artist || '')}` : '<span class="err">Walang kanta sa numerong ito</span>';
    renderSCode();
  }
});
$('sRes').onclick = async () => { if (sSong && await hostReserve(sSong.id)) { sCode = ''; sSong = null; renderSCode(); $('sPrev').textContent = 'I-type ang song number'; } };

// hanap
let sqT = null;
$('sQ').addEventListener('input', () => { clearTimeout(sqT); sqT = setTimeout(soloSearch, 300); });
async function soloSearch() {
  const raw = $('sQ').value.trim();
  if (/youtu/i.test(raw)) {                       // naka-paste na YouTube link
    const id = parseYouTubeId(raw);
    if (!id) { $('sList').innerHTML = '<p class="s-empty">Hindi valid na YouTube link</p>'; return; }
    const title = (await fetchYouTubeTitle(id).catch(() => null)) || 'YouTube video';
    $('sList').innerHTML = `<div class="s-row"><span class="dot">YT</span><div><b data-notr>${esc(title)}</b><small>YouTube</small></div><button class="btn small" data-yt="${esc(id)}" data-title="${esc(title)}">Reserve</button></div>`;
    return;
  }
  const q = $('sQ').value.trim().replace(/[,%()*\\]/g, ' ').trim();
  let req = sb.from('songs').select('id, code, title, artist, category').order('title').limit(100);
  if (q) req = /^\d{1,5}$/.test(q) ? req.or(`title.ilike.%${q}%,artist.ilike.%${q}%,code.eq.${parseInt(q, 10)}`) : req.or(`title.ilike.%${q}%,artist.ilike.%${q}%`);
  const { data } = await req;
  $('sList').innerHTML = (data || []).map(s => `<div class="s-row"><span class="dot">${s.code}</span><div><b>${esc(s.title)}</b><small>${esc(s.artist || '')}${s.category ? ', ' + esc(s.category) : ''}</small></div><button class="btn small" data-sid="${s.id}">Reserve</button></div>`).join('')
    || '<p class="s-empty">Walang nahanap.</p>';
}
$('sList').addEventListener('click', async e => {
  const b = e.target.closest('[data-sid]'); if (b) { hostReserve(b.dataset.sid); return; }
  const y = e.target.closest('[data-yt]');
  if (y && await hostReserve(null, { yt_id: y.dataset.yt, yt_title: y.dataset.title.slice(0, 200) })) $('sQ').value = '';
});

// pila
function renderSoloQueue() {
  if ($('solo').hidden) return;
  const rows = queue.map((q, k) => {
    const i = itemInfo(q), playing = q.status === 'playing';
    return `<div class="s-row ${playing ? 'playing' : ''}"><span class="dot">${esc(i.code)}</span><div><b>${esc(i.title)}</b><small>${playing ? 'Kinakanta' : (q.singer ? esc(q.singer) : '')}</small></div>
      ${playing ? '' : `<button class="btn small icon" data-sup="${q.id}" aria-label="Unahin">${ICON('up')}</button><button class="btn small icon danger" data-sdel="${q.id}" aria-label="Alisin">${ICON('x')}</button>`}</div>`;
  });
  $('sQueue').innerHTML = rows.join('') || '<p class="s-empty">Walang nakapila.</p>';
}
$('sQueue').addEventListener('click', async e => {
  const up = e.target.closest('[data-sup]'), del = e.target.closest('[data-sdel]');
  if (up) {
    const min = Math.min(...queue.filter(q => q.status === 'waiting').map(q => q.sort_order));
    await sb.from('queue').update({ sort_order: min - 1 }).eq('id', up.dataset.sup); loadQueue();
  }
  if (del) { await sb.from('queue').delete().eq('id', del.dataset.sdel).eq('status', 'waiting'); loadQueue(); }
});

// mic ng device na ito
let sMicRAF = 0;
$('sMicBtn').onclick = async () => {
  if (VKMicHost.hasLocal()) {
    VKMicHost.removeLocal(); cancelAnimationFrame(sMicRAF);
    $('sMicBtn').classList.remove('on'); $('sMicStatus').textContent = 'Gamitin ang mic ng device na ito'; $('sMicMute').hidden = true; $('sMicMeter').style.width = '0';
    return;
  }
  try {
    const st = await navigator.mediaDevices.getUserMedia({ audio: { echoCancellation: true, noiseSuppression: true, autoGainControl: true }, video: false });
    VKMicHost.addLocal(st, HOST_NAME() || 'Mic');
    $('sMicBtn').classList.add('on'); $('sMicStatus').textContent = 'Naka-on ang mic. Pindutin ulit para patayin.'; $('sMicMute').hidden = false;
    (function loop() { sMicRAF = requestAnimationFrame(loop); const l = VKMicHost.levels().find(m => m.id === 'local'); $('sMicMeter').style.width = Math.min(100, (l ? l.level : 0) * 450) + '%'; })();
  } catch (e) {
    toast(e.name === 'NotAllowedError' ? 'Hindi pinayagan ang mic. Payagan ito sa settings ng browser.' : 'Walang mic na nakita sa device na ito.', 'err', 4500);
  }
};
$('sMicMute').onclick = () => {
  const m = $('sMicMute').textContent === 'I-mute';
  VKMicHost.setLocalMuted(m); $('sMicMute').textContent = m ? 'I-unmute' : 'I-mute';
};

// --- direktang pag-type ng numero gamit ang keyboard (laptop) ---
let ceCode = '', ceSong = null, ceT = null;
function showCE() {
  $('codeEntry').classList.toggle('on', ceCode.length > 0);
  $('ceNum').innerHTML = esc(ceCode) + `<span class="ghost">${'8'.repeat(5 - ceCode.length)}</span>`;
  clearTimeout(ceT);
  ceT = setTimeout(() => { ceCode = ''; ceSong = null; showCE(); }, 8000);
}
async function ceKey(e) {
  if (!started || e.target.closest('input, textarea, select')) return false;
  if (/^[0-9]$/.test(e.key)) {
    if (ceCode.length >= 5) ceCode = '';
    if (window.VKVoice) VKVoice.key(e.key);
    ceCode += e.key; ceSong = null; $('ceTitle').textContent = '';
    showCE();
    if (ceCode.length === 5) {
      const typed = ceCode, s = await findSong(parseInt(ceCode, 10));
      if (typed !== ceCode) return true;
      ceSong = s;
      if (window.VKVoice) s ? VKVoice.say(s.title) : VKVoice.act('notfound');
      $('ceTitle').textContent = s ? `${s.title}${s.artist ? ', ' + s.artist : ''}` : 'Walang kanta sa numerong ito';
    }
    return true;
  }
  if (!ceCode) return false;
  if (e.key === 'Backspace') { ceCode = ceCode.slice(0, -1); ceSong = null; $('ceTitle').textContent = ''; showCE(); return true; }
  if (e.key === 'Escape') { ceCode = ''; ceSong = null; showCE(); return true; }
  if (e.key === 'Enter') {
    if (ceSong && await hostReserve(ceSong.id)) { ceCode = ''; ceSong = null; showCE(); }
    return true;
  }
  return false;
}
document.addEventListener('keydown', async e => { if (await ceKey(e)) e.preventDefault(); }, true);

// ================= INIT =================
renderQr();
show('idle');
connect();
