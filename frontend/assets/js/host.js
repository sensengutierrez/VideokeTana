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
  $('tbRoom').textContent = ROOM;
  $('remoteUrl').textContent = remoteLink();
}

// ================= VIEW =================
// ---- interlude habang naghihintay ----
let interT = null;
function interludeFor(m) {
  clearTimeout(interT);
  const p = VK_PREFS.get();
  if (m === 'idle' && started && p.interlude !== false && window.VKInterlude) {
    interT = setTimeout(() => { if (mode === 'idle' && $('splash').style.display !== 'flex') VKInterlude.start(p.interludeVol); }, 2500);
  } else if (window.VKInterlude) VKInterlude.stop();
}
function show(m) {
  mode = m;
  vid.style.display = m === 'video' ? 'block' : 'none';
  $('ytWrap').style.display = m === 'youtube' ? 'block' : 'none';
  $('audioStage').style.display = m === 'audio' ? 'block' : 'none';
  $('idle').style.display = m === 'idle' ? 'flex' : 'none';
  const playing = m !== 'idle';
  document.body.classList.toggle('playing', playing);
  if (!playing) document.body.classList.remove('qr-open');
  $('nowTag').style.display = (m === 'video' || m === 'youtube') ? 'block' : 'none'; // sa audio, nasa screen na ang title
  // ang background ay tumatakbo lang kapag hindi natatakpan ng video
  if (started) applyBg(false);
  renderNextBar();
  interludeFor(m);
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
  if (standby) return;
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

// pagkatapos ng "Stop": hindi kusang tutugtog ang naiwang pila hanggang may Play/Next o bagong reserve
let holdIds = null;
async function startNext() {
  if (starting || current || scoring || standby) return;
  if (holdIds) {
    if (queue.some(q => !holdIds.has(q.id))) holdIds = null;
    else { show('idle'); broadcastState(); return; }
  }
  const next = queue.find(q => q.status === 'playing') || queue.find(q => q.status === 'waiting');
  // mga naipit sa "Kinakanta" (hal. galing sa ibang tab na nagsara): ibalik sa pila
  const stuck = queue.filter(q => q.status === 'playing' && q !== next);
  if (stuck.length) {
    stuck.forEach(q => { q.status = 'waiting'; });
    Promise.all(stuck.map(q => sb.from('queue').update({ status: 'waiting' }).eq('id', q.id))).catch(() => {});
  }
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
    // await: ang query ng supabase-js ay hindi tumatakbo hangga't hindi hinihintay (dati, hindi nabubura ang tapos na kanta)
    const { error } = await sb.from('queue').delete().eq('id', done.id);
    if (error) toast('Hindi naalis sa pila: ' + error.message, 'err');
    if (reason === 'ended' && VK_PREFS.get().scoreMode !== 'off') await showScore(done);
  }
  if (reason === 'stop') { holdIds = new Set(queue.map(q => q.id)); show('idle'); toast('Inihinto ang kanta', 'ok', 1500); broadcastState(); return; }
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
  if (window.VKInterlude) { clearTimeout(interT); VKInterlude.stop(); }
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
      if (channel) channel.send({ type: 'broadcast', event: 'score', payload: { score: n, singer: item.singer || '', title: itemInfo(item).title } });
      const done = ms => setTimeout(() => { el.style.display = 'none'; scoring = false; res(); }, ms);
      const builtin = () => {
        if (pr.applause > 0) {
          VKSfx.play('fanfare');
          VKSfx.play('applause', { size: [0, .5, 1, 1.5][pr.applause] * (n >= 95 ? 1.2 : n >= 85 ? 1 : .7) });
        }
        done(4200);
      };
      // sariling tunog ng score (Settings → Score): kapag may file para sa score na ito
      if (pr.scoreSound === 'own' && window.VKScoreSnd) VKScoreSnd.play(n, pr.sfxVolume / 100).then(sec => sec ? done(Math.max(4200, sec * 1000 + 400)) : builtin()).catch(builtin);
      else builtin();
    })(t0);
  });
}

function showSplash(item) {
  if (window.VKInterlude) { clearTimeout(interT); VKInterlude.stop(); }
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
  if (localUrl) { setTimeout((u => () => URL.revokeObjectURL(u))(localUrl), 1000); localUrl = null; }
  try { vid.pause(); vid.removeAttribute('src'); vid.load(); } catch (e) {}
  try { aud.pause(); aud.removeAttribute('src'); aud.load(); } catch (e) {}
  try { if (ytPlayer && ytReady) ytPlayer.stopVideo(); } catch (e) {}
  lyrics = [];
  document.body.classList.remove('has-pitch');   // ang stats ay nililinis sa simula ng susunod na kanta (kailangan pa sa score)
}

let localUrl = null;

// ---- MIDI: gawing WAV gamit ang sariling synth, tapos patugtugin sa <audio> ----
async function midiSong(ab, info) {
  toast('Inihahanda ang MIDI...', 'info', 2500);
  const parsed = VKMidi.parse(ab);
  let blob = null;
  // SoundFont (GeneralUser GS, o sariling .sf2); kung pumalya, ang simpleng synth
  if (window.VKSF2 && (VK_PREFS.get().midiEngine || 'sf') === 'sf') {
    try { const sf = await VKSF2.load(); blob = await VKSF2.render(parsed, sf, {}); }
    catch (e) { console.warn('SoundFont', e); toast('Hindi magamit ang SoundFont, simpleng tunog muna', 'err', 3000); }
  }
  if (!blob) blob = await VKMidi.render(parsed);
  if (localUrl) URL.revokeObjectURL(localUrl);
  localUrl = URL.createObjectURL(blob);
  return { code: info.code, title: info.title, artist: info.artist || '', kind: 'audio', local: true, lrcText: '', midiLyrics: parsed.lyrics, midiMelody: parsed.melody };
}

// ---- file na ipinasa ng phone (remote): paisa-isang piraso sa broadcast na "file" ----
const phoneParts = new Map(), phoneFiles = new Map();
function onPhoneFile(p) {
  if (!p || !p.qid || !(p.n > 0) || p.n > 400 || !(p.i >= 0 && p.i < p.n)) return;
  let a = phoneParts.get(p.qid);
  if (!a || a.length !== p.n) { a = new Array(p.n).fill(null); phoneParts.set(p.qid, a); }
  a[p.i] = p.data;
  if (a.every(x => x !== null)) {
    phoneParts.delete(p.qid);
    const bytes = a.map(d => Uint8Array.from(atob(d), c => c.charCodeAt(0)));
    phoneFiles.set(p.qid, { blob: new Blob(bytes), ext: String(p.ext || '').toLowerCase().replace(/[^a-z0-9]/g, '') });
    for (const k of phoneFiles.keys()) if (k !== p.qid && !queue.some(q => q.id === k)) phoneFiles.delete(k);
  }
}
async function waitPhoneFile(item) {
  for (let w = 0; w < 15000; w += 500) {
    if (phoneFiles.has(item.id)) return phoneFiles.get(item.id);
    if (w % 5000 === 0 && channel) channel.send({ type: 'broadcast', event: 'need', payload: { qid: item.id } });
    await new Promise(r => setTimeout(r, 500));
    if (!current || current.id !== item.id) return null;
  }
  return phoneFiles.get(item.id) || null;
}
async function playItem(item) {
  VK_PREFS.set('musicTempo', 0);
  const i = itemInfo(item);
  $('nowTag').innerHTML = `<span class="dot">${esc(i.code)}</span><span>${esc(i.title)}</span>${item.singer ? `<span class="nt-s">${esc(item.singer)}</span>` : ''}`;
  applyVolume();

  // kanta na nasa device na ito (IndexedDB), o ipinasa ng phone (remote) sa broadcast
  if (item.local_code && !item.song) {
    const rec = window.VKLocal ? await VKLocal.get(item.local_code).catch(() => null) : null;
    if (rec) {
      if (localUrl) URL.revokeObjectURL(localUrl);
      localUrl = URL.createObjectURL(rec.blob);
      item.song = { code: rec.code, title: rec.title, artist: rec.artist, kind: rec.kind, local: true, lrcText: rec.lrc || '' };
    } else {
      const f = await waitPhoneFile(item);
      if (!f) throw new Error(`Wala sa device na ito ang kantang ${item.local_code}. Kung nasa phone, buksan ang remote ng phone na nag-reserve.`);
      const [artist, title] = (item.local_title || '').includes(' - ') ? item.local_title.split(' - ', 2) : ['', item.local_title || ('Kanta ' + item.local_code)];
      if (VKMidi.isMidi('x.' + f.ext)) item.song = await midiSong(await f.blob.arrayBuffer(), { code: item.local_code, title, artist });
      else {
        if (localUrl) URL.revokeObjectURL(localUrl);
        localUrl = URL.createObjectURL(f.blob);
        item.song = { code: item.local_code, title, artist, kind: /^(mp4|webm|mkv|3gp)$/.test(f.ext) ? 'video' : 'audio', local: true, lrcText: '' };
      }
    }
  }
  // MIDI na nasa Library (storage)
  if (item.song && item.song.kind === 'audio' && !item.song.local && VKMidi.isMidi(item.song.media_path)) {
    const r = await fetch(mediaUrl(item.song.media_path));
    if (!r.ok) throw new Error('Hindi ma-download ang MIDI');
    item.song = await midiSong(await r.arrayBuffer(), item.song);
  }
  const src = s => s.local ? localUrl : mediaUrl(s.media_path);
  if (item.song && item.song.kind === 'video') {
    show('video');
    vid.src = src(item.song);
    await vid.play();
  } else if (item.song && item.song.kind === 'audio') {
    show('audio');
    $('aT').textContent = item.song.title;
    $('aA').textContent = item.song.artist || '';
    $('aS').textContent = item.singer ? 'Kumakanta: ' + item.singer : '';
    $('lyCur').textContent = ''; lineText = null; $('lyNext').textContent = '';
    lyrics = []; lyricOffset = 0;
    if (item.song.midiLyrics) lyrics = item.song.midiLyrics;
    else if (item.song.lrcText) parseLrc(item.song.lrcText);
    else if (item.song.lrc_path) {
      try {
        const r = await fetch(mediaUrl(item.song.lrc_path) + '?v=' + Date.now());
        parseLrc(await r.text());
      } catch (e) { console.warn('LRC load failed', e); }
    }
    $('noLrc').style.display = lyrics.length ? 'none' : 'block';
    loadMelody(item.song);
    aud.src = src(item.song);
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
      onStateChange: e => { if (e.data === YT.PlayerState.ENDED && mode === 'youtube') finishCurrent('ended'); if (e.data === YT.PlayerState.PLAYING) applyTempo(); },
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
  if (song.midiMelody) { if (VKPitch.load(song.midiMelody)) document.body.classList.add('has-pitch'); return; }
  if (song.local) return;
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
// pitch bar: maaaring i-off kahit habang kumakanta (Settings o remote)
let pitchWas = false;
window.addEventListener('vkprefs', () => {
  const off = VK_PREFS.get().pitchBar === 'off';
  if (off && document.body.classList.contains('has-pitch')) { pitchWas = true; document.body.classList.remove('has-pitch'); }
  else if (!off && pitchWas && VKPitch.has && VKPitch.has()) { document.body.classList.add('has-pitch'); pitchWas = false; }
});
// laktawan ang mahabang intro: diretso sa 3 segundo bago ang unang linya ng lyrics
function skipIntro() {
  if (mode !== 'audio' || !lyrics.length) return false;
  const first = lyrics.find(l => l.text);
  if (!first) return false;
  const target = Math.max(0, first.t - lyricOffset - 3);
  if (aud.currentTime < target - 1) { aud.currentTime = target; toast('Nilaktawan ang intro', 'ok', 1500); return true; }
  return false;
}
aud.addEventListener('playing', () => { if (VK_PREFS.get().autoSkipIntro && aud.currentTime < 1) setTimeout(skipIntro, 400); });
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
    case 'toggle': if (!current && holdIds) { holdIds = null; startNext(); } else setPaused(!paused); break;
    case 'pause': setPaused(true); break;
    case 'play': if (!current && holdIds) { holdIds = null; startNext(); } else setPaused(false); break;
    case 'skip': if (current) finishCurrent('skip'); else { holdIds = null; startNext(); } break;
    case 'stop': if (current) finishCurrent('stop'); break;
    case 'sfx': playSfx(value); return;
    case 'restart': restart(); break;
    case 'skipintro': skipIntro(); break;
    case 'volup': volume = Math.min(100, volume + 10); applyVolume(); flash($('volBadge'), 'Volume ' + volume); break;
    case 'voldown': volume = Math.max(0, volume - 10); applyVolume(); flash($('volBadge'), 'Volume ' + volume); break;
    case 'setvol': volume = Math.max(0, Math.min(100, parseInt(value, 10) || 0)); applyVolume(); flash($('volBadge'), 'Volume ' + volume); break;
    case 'refresh': loadQueue(); break;
    case 'scene': nextScene(); break;
    case 'font': if (['dot', 'seg', 'bold', 'thin'].includes(value)) VK_PREFS.set('numFont', value); break;
    case 'fx': {   // mula sa phone: echo, reverb, lakas ng mic, bass, treble
      const k = value && value.key, r = FX_KEYS[k];
      // interlude at background ng TV (mula sa "Tunog sa TV" ng app)
      if (k === 'interlude') { VK_PREFS.set('interlude', +value.value === 1); interludeFor(mode); break; }
      if (k === 'interludeVol') { const v = Math.max(0, Math.min(100, Math.round(+value.value || 0))); VK_PREFS.set('interludeVol', v); if (window.VKInterlude) VKInterlude.setVolume(v); break; }
      if (k === 'pitchBar') { VK_PREFS.set('pitchBar', +value.value === 1 ? 'on' : 'off'); break; }
      if (k === 'autoSkipIntro') { VK_PREFS.set('autoSkipIntro', +value.value === 1); break; }
      if (k === 'bgVideo') { const v = +value.value; VK_PREFS.set('bgMode', v === 1 ? 'videos' : v === 2 ? 'mix' : 'animated'); if (started) applyBg(true); break; }
      if (!r) break;
      VK_PREFS.set(k, Math.max(r[0], Math.min(r[1], Math.round(+value.value || 0))));
      if (k === 'musicTempo') applyTempo();
      clearTimeout(handleCmd.fxT); handleCmd.fxT = setTimeout(broadcastState, 300);
      break;
    }
  }
  broadcastState();
}

// ---- Bass at treble ng kanta (sariling MP3/MP4 lang; hindi kaya sa YouTube) ----
let eqAC = null, eqBass = null, eqTreble = null;
[aud, vid].forEach(el => { el.crossOrigin = 'anonymous'; });   // kailangan para madaanan ng Web Audio
function eqInit() {
  if (eqAC) { if (eqAC.state === 'suspended') eqAC.resume().catch(() => {}); return; }
  try {
    const AC = window.AudioContext || window.webkitAudioContext;
    eqAC = new AC({ latencyHint: 'playback' });
    eqBass = eqAC.createBiquadFilter(); eqBass.type = 'lowshelf'; eqBass.frequency.value = 120;
    eqTreble = eqAC.createBiquadFilter(); eqTreble.type = 'highshelf'; eqTreble.frequency.value = 4000;
    eqBass.connect(eqTreble).connect(eqAC.destination);
    [aud, vid].forEach(el => eqAC.createMediaElementSource(el).connect(eqBass));
    eqApply();
  } catch (e) { console.warn('EQ', e); eqAC = null; }
}
function eqApply() {
  if (!eqAC) return;
  const p = VK_PREFS.get(), t = eqAC.currentTime;
  eqBass.gain.setTargetAtTime(Math.max(-12, Math.min(12, +p.musicBass || 0)), t, 0.05);
  eqTreble.gain.setTargetAtTime(Math.max(-12, Math.min(12, +p.musicTreble || 0)), t, 0.05);
}
window.addEventListener('vkprefs', eqApply);
const FX_KEYS = { micVolume: [0, 150], micEcho: [0, 100], micReverb: [0, 100], micClarity: [0, 100], musicBass: [-12, 12], musicTreble: [-12, 12], musicTempo: [-5, 5] };
// tempo ng kanta: 5% bawat hakbang (−25% hanggang +25%), hindi nagbabago ang tono; bumabalik sa 0 bawat kanta
function applyTempo() {
  const r = 1 + 0.05 * (+VK_PREFS.get().musicTempo || 0);
  [aud, vid].forEach(el => { try { el.preservesPitch = true; el.playbackRate = r; } catch (e) {} });
  try { if (ytPlayer && ytReady) ytPlayer.setPlaybackRate(r); } catch (e) {}
}
['playing', 'loadedmetadata'].forEach(ev => { aud.addEventListener(ev, applyTempo); vid.addEventListener(ev, applyTempo); });
function fxState() {
  const p = VK_PREFS.get(), o = {};
  Object.keys(FX_KEYS).forEach(k => { o[k] = +p[k] || 0; });
  o.eq = !!eqAC && (mode === 'audio' || mode === 'video');
  o.ytTempo = true; o.pitchBar = p.pitchBar === 'off' ? 0 : 1; o.skipIntro = 1;
  o.interlude = p.interlude === false ? 0 : 1; o.interludeVol = p.interludeVol == null ? 40 : +p.interludeVol; o.bgVideo = p.bgMode === 'videos' ? 1 : p.bgMode === 'mix' ? 2 : 0;
  return o;
}

// listahan ng kanta sa device na ito, para makapag-reserve ang phone gamit ang numero
let localCatalog = [];
async function refreshLocalCatalog() {
  try { localCatalog = window.VKLocal ? (await VKLocal.list()).map(s => ({ code: s.code, title: s.title, artist: s.artist })) : []; } catch (e) { localCatalog = []; }
}
refreshLocalCatalog();
window.addEventListener('focus', () => refreshLocalCatalog().then(() => { if (!standby) broadcastState(); }));

function broadcastState() {
  if (!channel) return;
  const now = current ? { ...itemInfo(current), singer: current.singer || '', id: current.id } : null;
  channel.send({ type: 'broadcast', event: 'state', payload: { now, paused, volume, room: ROOM, fx: fxState(), local: localCatalog } });
}

// ================= REALTIME =================
// ---- Iisang TV lang bawat room ----
// Kapag may dalawang bukas na TV page sa iisang room (hal. dalawang tab), pareho silang kumukuha ng kanta sa pila
// at naiipit ang mga kanta sa "Kinakanta". Ang pinakabagong binuksang TV ang gagamitin; ang luma ay naghihintay.
const HOST_ID = Math.random().toString(36).slice(2, 10);
let hostSince = 0, standby = false;
function announceHost() {
  if (channel && started && !standby) channel.send({ type: 'broadcast', event: 'host', payload: { id: HOST_ID, since: hostSince } });
}
function onOtherHost(p) {
  if (!p || p.id === HOST_ID || !started || standby) return;
  if (p.since > hostSince) goStandby(); else announceHost();
}
function goStandby() {
  standby = true; if (window.VKInterlude) VKInterlude.stop();
  stopMedia(); current = null; starting = false;
  try { VKMicHost.closeAll(); } catch (e) {}
  show('idle');
  $('standby').hidden = false;
}
function takeOver() {
  standby = false; hostSince = Date.now();
  $('standby').hidden = true;
  announceHost(); loadQueue(); broadcastState();
}
setInterval(announceHost, 10000);

function connect() {
  if (channel) sb.removeChannel(channel);
  channel = sb.channel('room-' + ROOM, { config: { broadcast: { self: false } } })
    .on('broadcast', { event: 'cmd' }, ({ payload }) => { if (!standby) handleCmd(payload.action, payload.value); })
    .on('broadcast', { event: 'hello' }, () => { if (!standby) broadcastState(); })
    .on('broadcast', { event: 'file' }, ({ payload }) => { if (!standby) onPhoneFile(payload); })
    .on('broadcast', { event: 'host' }, ({ payload }) => onOtherHost(payload))
    .on('broadcast', { event: 'rtc' }, ({ payload }) => { if (!standby && payload && payload.to === 'host') VKMicHost.handle(payload); })
    .on('postgres_changes', { event: '*', schema: 'public', table: 'queue' }, () => loadQueueSoon())
    .subscribe(status => {
      if (status === 'SUBSCRIBED') { loadQueue(); if (!standby) broadcastState(); announceHost(); }
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
$('takeOver').onclick = takeOver;

// QR habang may kanta: nakatago, lumalaki kapag pinindot ang QR button
let qrTimer = null;
function toggleQr(open = !document.body.classList.contains('qr-open')) {
  document.body.classList.toggle('qr-open', open);
  clearTimeout(qrTimer);
  if (open) qrTimer = setTimeout(() => toggleQr(false), 20000);
}
$('btnQr').onclick = e => { e.stopPropagation(); toggleQr(); };
$('cornerQr').onclick = () => toggleQr(false);
document.addEventListener('keydown', e => { if (e.key === 'Escape' && document.body.classList.contains('qr-open')) toggleQr(false); });

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
  location.href = '../app/';
};
// Ipakita ang mga button kapag gumalaw ang mouse o tinap ang screen, tapos itago ulit
let uiTimer = null;
// Huwag takpan ng mga button ang pila/kinakanta sa itaas: icon lang muna, at kung kulang pa, ibaba ang pila
function fitTopBtns() {
  const b = document.body;
  b.classList.remove('tb-compact', 'tb-push');
  const btnLeft = () => $('topBtns').getBoundingClientRect().left - 8;
  const usedRight = () => Math.max(0, ...['nowTag', 'nextBar', 'micBar'].map(id => {
    const el = $(id); if (!el || !el.offsetParent) return 0;
    if (!el.textContent.trim()) return 0;
    const r = document.createRange(); r.selectNodeContents(el);   // dulo ng mismong laman, hindi ng buong kahon
    return r.getBoundingClientRect().right;
  }));
  if (usedRight() <= btnLeft()) return;
  b.classList.add('tb-compact');
  if (usedRight() > btnLeft()) b.classList.add('tb-push');
}
['mousemove', 'touchstart', 'click'].forEach(ev => document.addEventListener(ev, () => {
  document.body.classList.add('ui');
  if (Date.now() - (fitTopBtns.at || 0) > 250) { fitTopBtns.at = Date.now(); fitTopBtns(); }
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

// LCD sa gate: umiikot na numero na lumalapag sa 10001
(function () {
  const el = $('gateNum'); if (!el || matchMedia('(prefers-reduced-motion: reduce)').matches) return;
  let n = 0; const t = setInterval(() => {
    n++;
    el.textContent = n < 22 ? String(10000 + Math.floor(Math.random() * 89999)).slice(0, 5) : '10001';
    if (n >= 22) clearInterval(t);
  }, 60);
})();
$('gate').onclick = () => {
  if (started) { // na-block ang autoplay — ituloy ang kasalukuyang kanta
    $('gate').style.display = 'none';
    if (mode === 'video') vid.play().catch(() => {});
    if (mode === 'audio') aud.play().catch(() => {});
    return;
  }
  eqInit();
  started = true; hostSince = Date.now(); setTimeout(announceHost, 1500);
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
  wow: 'Wow', aww: 'Aww', boo: 'Boo', laugh: 'Tawa', angry: 'Galit', omg: 'OMG', faah: 'FAAH!', wownaman: 'Wow naman', galing: 'Ang galing' };
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
  $('micBar').innerHTML = list.map(m => `<span class="mic-chip ${m.muted ? 'muted' : ''}" data-mic="${esc(m.id)}">${ICON('mic')}<span>${esc(m.name)}</span><i class="lv"><b></b></i><small class="dl" translate="no"></small></span>`).join('');
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
  VKMicHost.list().forEach(m => {
    const d = document.querySelector(`[data-mic="${CSS.escape(m.id)}"] .dl`);
    if (d) d.textContent = m.delay && m.id !== 'local' ? m.delay + ' ms' : '';
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
// BGV: kung malayo ang hugis ng video sa screen, buo ang video at malabong kopya sa likod (hindi naputol, walang itim na gilid)
const bgvBlur = document.getElementById('bgvBlur');
function bgvFit() {
  if (!bgvBlur) return;
  const va = bgv.videoWidth && bgv.videoHeight ? bgv.videoWidth / bgv.videoHeight : 0;
  const sa = innerWidth / innerHeight;
  const fit = va > 0 && Math.abs(Math.log(va / sa)) > 0.35 && bgv.style.display !== 'none';
  bgv.classList.toggle('fit', fit);
  if (fit) {
    if (bgvBlur.src !== bgv.src) bgvBlur.src = bgv.src;
    bgvBlur.style.display = 'block';
    try { bgvBlur.currentTime = bgv.currentTime; } catch (e) {}
    if (!bgv.paused) bgvBlur.play().catch(() => {});
  } else { bgvBlur.style.display = 'none'; bgvBlur.pause(); }
}
bgv.addEventListener('loadedmetadata', bgvFit);
bgv.addEventListener('play', () => { if (bgv.classList.contains('fit')) bgvBlur.play().catch(() => {}); });
bgv.addEventListener('pause', () => bgvBlur && bgvBlur.pause());
addEventListener('resize', bgvFit);
// "mix": salitan ang video at animation bawat 90 segundo
let mixVideo = true;
setInterval(() => { if (VK_PREFS.get().bgMode === 'mix' && started) { mixVideo = !mixVideo; applyBg(true); } }, 90000);
async function applyBg(change) {
  const p0 = VK_PREFS.get();
  const p = p0.bgMode === 'mix' ? Object.assign({}, p0, { bgMode: mixVideo ? 'videos' : 'animated' }) : p0;
  const covered = mode === 'video' || mode === 'youtube';
  const wall = $('wallImg');
  if (p.bgMode === 'image' && window.VKWall && VKWall.url()) {
    if (window.VKScenes) VKScenes.pause();
    bgv.pause(); bgv.style.display = 'none'; bgvFit();
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
      bgv.style.display = 'block'; bgvFit();
      covered ? bgv.pause() : bgv.play().catch(() => {});
      return;
    }
  }
  bgv.pause(); bgv.style.display = 'none'; bgvFit();
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
  let row = yt ? { room: ROOM, yt_id: yt.yt_id, yt_title: yt.yt_title, singer: HOST_NAME() } : { room: ROOM, song_id: songId, singer: HOST_NAME() };
  if (typeof songId === 'string' && songId.startsWith('local:')) {
    const m = await VKLocal.meta(songId.slice(6));
    row = { room: ROOM, local_code: m.code, local_title: (m.artist ? m.artist + ' - ' : '') + m.title, singer: HOST_NAME() };
  }
  const { error } = await sb.from('queue').insert(row);
  if (error) { toast('Hindi na-reserve: ' + error.message, 'err'); return false; }
  if (window.VKVoice) VKVoice.act('reserved');
  toast(current ? 'Naka-reserve' : 'Naka-reserve. Magsisimula na.', 'ok');
  loadQueue();
  return true;
}
async function findSong(code) {
  if (+code >= 90000) {                       // kanta sa device na ito
    const m = window.VKLocal ? await VKLocal.meta(code).catch(() => null) : null;
    return m ? { id: 'local:' + m.code, code: m.code, title: m.title, artist: m.artist } : null;
  }
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
  const [{ data }, locals] = await Promise.all([req, window.VKLocal ? VKLocal.list().catch(() => []) : []]);
  const ql = q.toLowerCase();
  const mine = locals.filter(s => !q || String(s.code).includes(q) || (s.title + ' ' + (s.artist || '')).toLowerCase().includes(ql))
    .map(s => ({ id: 'local:' + s.code, code: s.code, title: s.title, artist: s.artist, category: 'Kanta sa device na ito' }));
  $('sList').innerHTML = [...mine, ...(data || [])].map(s => `<div class="s-row"><span class="dot">${s.code}</span><div><b>${esc(s.title)}</b><small>${esc(s.artist || '')}${s.category ? ', ' + esc(s.category) : ''}</small></div><button class="btn small" data-sid="${s.id}">Reserve</button></div>`).join('')
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
  if (del) { await sb.from('queue').delete().eq('id', del.dataset.sdel); loadQueue(); }
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
