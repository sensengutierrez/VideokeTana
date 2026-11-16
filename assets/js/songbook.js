// ===== SONGBOOK — listahan ng lahat ng kanta =====
const $ = id => document.getElementById(id);

let songs = [];
let sortBy = 'code';
let kind = '';
let letter = '';
const ROOM = cleanRoom(localStorage.getItem('vk_last_room'));
const NAME = localStorage.getItem('vk_name') || '';

const firstLetter = s => {
  const c = (s || '').trim().charAt(0).toUpperCase();
  return /[A-Z]/.test(c) ? c : '#';
};

async function load() {
  const { data, error } = await sb.from('songs').select('id, code, title, artist, kind').order('code');
  if (error) { $('count').textContent = 'Error: ' + error.message; return; }
  songs = data || [];
  renderLetters();
  render();
}

function sortKey(s) {
  if (sortBy === 'title') return s.title.toLowerCase();
  if (sortBy === 'artist') return (s.artist || '~').toLowerCase() + ' ' + s.title.toLowerCase();
  return s.code;
}
function letterOf(s) { return firstLetter(sortBy === 'artist' ? s.artist : s.title); }

function renderLetters() {
  const have = new Set(songs.map(letterOf));
  const all = ['', '#', ...'ABCDEFGHIJKLMNOPQRSTUVWXYZ'];
  $('letters').innerHTML = all.map(l => `<button data-l="${l}" class="${l === letter ? 'on' : ''}" ${l && !have.has(l) ? 'disabled' : ''}>${l || 'Lahat'}</button>`).join('');
  $('letters').style.display = sortBy === 'code' ? 'none' : 'flex';
}

function render() {
  const q = $('q').value.trim().toLowerCase();
  let list = songs.filter(s =>
    (!kind || s.kind === kind) &&
    (!q || String(s.code).includes(q) || s.title.toLowerCase().includes(q) || (s.artist || '').toLowerCase().includes(q)) &&
    (!letter || sortBy === 'code' || letterOf(s) === letter));
  list.sort((a, b) => { const x = sortKey(a), y = sortKey(b); return x < y ? -1 : x > y ? 1 : 0; });

  $('count').textContent = songs.length ? `${list.length} sa ${songs.length} kanta` : 'Wala pang kanta';

  if (!songs.length) {
    $('list').innerHTML = `<div class="empty"><div class="big">🎵</div><b>Wala pang kanta sa library.</b><br>
      Mag-upload muna sa <a href="../admin/">Admin page</a> — doon din awtomatikong nabibigyan ng song number ang bawat kanta.</div>`;
    return;
  }
  if (!list.length) { $('list').innerHTML = '<div class="empty">Walang nahanap.</div>'; return; }

  $('list').innerHTML = list.map(s => `
    <div class="song">
      <div class="num">${s.code}</div>
      <div class="info"><div class="t">${esc(s.title)}</div><div class="a">${esc(s.artist || '—')}</div></div>
      <span class="k" title="${s.kind}">${s.kind === 'video' ? '🎬' : '🎵'}</span>
      ${ROOM_RE.test(ROOM) ? `<button class="btn small primary" data-res="${s.id}" title="Reserve sa room ${ROOM}">🎤</button>` : ''}
    </div>`).join('');
}

// ----- Events -----
$('q').addEventListener('input', render);
$('sortSeg').addEventListener('click', e => {
  const b = e.target.closest('button'); if (!b) return;
  sortBy = b.dataset.sort; letter = '';
  document.querySelectorAll('#sortSeg button').forEach(x => x.classList.toggle('on', x === b));
  renderLetters(); render();
});
$('kindSeg').addEventListener('click', e => {
  const b = e.target.closest('button'); if (!b) return;
  kind = b.dataset.kind;
  document.querySelectorAll('#kindSeg button').forEach(x => x.classList.toggle('on', x === b));
  render();
});
$('letters').addEventListener('click', e => {
  const b = e.target.closest('button'); if (!b || b.disabled) return;
  letter = b.dataset.l;
  renderLetters(); render();
});
$('list').addEventListener('click', async e => {
  const b = e.target.closest('[data-res]'); if (!b) return;
  b.disabled = true;
  const { error } = await sb.from('queue').insert({ room: ROOM, song_id: b.dataset.res, singer: NAME || null });
  b.disabled = false;
  if (error) toast('Hindi na-reserve: ' + error.message, 'err');
  else toast(`Naka-reserve sa room ${ROOM}! 🎤`, 'ok');
});

if (ROOM_RE.test(ROOM)) $('roomInfo').innerHTML = `Room: <b style="color:var(--gold)">${ROOM}</b> · pindutin ang 🎤 para mag-reserve`;

// Auto-refresh kapag may bagong upload
sb.channel('songbook').on('postgres_changes', { event: '*', schema: 'public', table: 'songs' }, load).subscribe();
load();
