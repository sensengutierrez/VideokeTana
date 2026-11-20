// ===== SONGBOOK — listahan ng lahat ng kanta =====
const $ = id => document.getElementById(id);

let songs = [];
let sortBy = 'code';
let cat = '';
let letter = '';
const ROOM = cleanRoom(localStorage.getItem('vk_last_room'));
const NAME = localStorage.getItem('vk_name') || '';

const firstLetter = s => {
  const c = (s || '').trim().charAt(0).toUpperCase();
  return /[A-Z]/.test(c) ? c : '#';
};

async function load() {
  const { data, error } = await sb.from('songs').select('id, code, title, artist, kind, category, created_at').order('code');
  if (error) { $('count').textContent = 'Error: ' + error.message; return; }
  songs = data || [];
  renderLetters();
  renderCats();
  render();
}

function renderCats() {
  const have = [...new Set(songs.map(s => s.category).filter(Boolean))].sort((x, y) => CATEGORIES.indexOf(x) - CATEGORIES.indexOf(y));
  $('catChips').innerHTML = [['', 'Lahat'], ['__new', 'Bago'], ...have.map(c => [c, c])]
    .map(([v, l]) => `<button class="chip ${v === cat ? 'on' : ''}" data-cat="${esc(v)}">${esc(l)}</button>`).join('');
}
$('catChips').addEventListener('click', e => {
  const b = e.target.closest('[data-cat]'); if (!b) return;
  cat = b.dataset.cat; renderCats(); render();
});

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
    (!cat || cat === '__new' || s.category === cat) &&
    (!q || String(s.code).includes(q) || s.title.toLowerCase().includes(q) || (s.artist || '').toLowerCase().includes(q)) &&
    (!letter || sortBy === 'code' || letterOf(s) === letter));
  list.sort((a, b) => { const x = sortKey(a), y = sortKey(b); return x < y ? -1 : x > y ? 1 : 0; });
  if (cat === '__new') list = list.sort((a, b) => (b.created_at || '').localeCompare(a.created_at || '')).slice(0, 30);

  $('count').textContent = songs.length ? (list.length === songs.length ? `${songs.length} kanta` : `${list.length} sa ${songs.length} kanta`) : 'Wala pang kanta';

  if (!songs.length) {
    $('list').innerHTML = `<div class="empty">${ICON('book')}<b>Wala pang kanta</b>
      Mag-upload sa <a href="../admin/">Library</a>. Kusang nabibigyan ng song number ang bawat kanta.</div>`;
    return;
  }
  if (!list.length) { $('list').innerHTML = '<div class="empty">Walang nahanap.</div>'; return; }

  $('list').innerHTML = list.map(s => `
    <div class="song">
      <div class="num">${s.code}</div>
      <div class="info"><div class="t">${esc(s.title)}</div><div class="a">${s.kind === 'youtube' ? '<span class="src">YouTube</span>' : ICON(s.kind === 'video' ? 'film' : 'music')}<span>${esc(s.artist || 'Walang artist')}${s.category ? ', ' + esc(s.category) : ''}</span></div></div>
      ${ROOM_RE.test(ROOM) ? `<button class="btn small" data-res="${s.id}">Reserve</button>` : '<span></span>'}
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
  else toast(`Naka-reserve sa room ${ROOM}`, 'ok');
});

if (ROOM_RE.test(ROOM)) $('roomInfo').innerHTML = `Nakakonekta sa room <b>${ROOM}</b>. Pindutin ang Reserve para ipila ang kanta.`;

// Auto-refresh kapag may bagong upload
sb.channel('songbook').on('postgres_changes', { event: '*', schema: 'public', table: 'songs' }, load).subscribe();
load();


// ---- I-download ang songbook bilang Excel (lahat ng kanta + mga kanta sa device na ito) ----
$('dlXlsx').onclick = async () => {
  const rows = [['Numero', 'Title', 'Artist', 'Kategorya', 'Uri', 'Pinagmulan']];
  const kind = k => ({ audio: 'Audio/MIDI', video: 'Video', youtube: 'YouTube' }[k] || k || '');
  songs.forEach(s => rows.push([s.code, s.title || '', s.artist || '', s.category || '', kind(s.kind), 'Online']));
  try { if (window.VKLocal) (await VKLocal.list()).forEach(s => rows.push([s.code, s.title || '', s.artist || '', '', s.kind || '', 'Sa device na ito'])); } catch (e) {}
  const blob = VKXlsx.make(rows, 'Songbook');
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = 'VideokeTana-Songbook-' + new Date().toISOString().slice(0, 10) + '.xlsx';
  document.body.appendChild(a); a.click(); a.remove();
  setTimeout(() => URL.revokeObjectURL(a.href), 4000);
  toast(`Na-download: ${rows.length - 1} na kanta`, 'ok');
};
