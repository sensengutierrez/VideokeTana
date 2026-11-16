const $ = id => document.getElementById(id);
const MAX = VK_CONFIG.MAX_FILE_MB * 1024 * 1024;
const FREE_STORAGE = 1024 * 1024 * 1024; // 1GB (free plan)
$('maxMb').textContent = VK_CONFIG.MAX_FILE_MB;

const VIDEO_EXT = ['mp4', 'webm', 'm4v', 'mov'];
const AUDIO_EXT = ['mp3', 'm4a', 'aac', 'ogg', 'oga', 'wav', 'opus', 'flac'];
const MIME = { mp4: 'video/mp4', m4v: 'video/mp4', webm: 'video/webm', mov: 'video/quicktime', mp3: 'audio/mpeg', m4a: 'audio/mp4', aac: 'audio/aac',
  ogg: 'audio/ogg', oga: 'audio/ogg', wav: 'audio/wav', opus: 'audio/ogg', flac: 'audio/flac' };

let pending = []; // {file, lrc, kind, ext, title, artist, status, msg}
let songs = [];

// ================= AUTH =================
async function refreshAuth() {
  const { data: { session } } = await sb.auth.getSession();
  if (!session) { $('authView').style.display = 'block'; $('mainView').style.display = 'none'; return; }
  $('authView').style.display = 'none';
  $('mainView').style.display = 'block';
  const { data: isAdmin } = await sb.rpc('is_admin');
  $('adminArea').style.display = isAdmin ? 'block' : 'none';
  $('notAdmin').style.display = isAdmin ? 'none' : 'block';
  if (isAdmin) loadLib();
}
$('loginBtn').onclick = async () => {
  const { error } = await sb.auth.signInWithPassword({ email: $('email').value.trim(), password: $('pass').value });
  if (error) toast(error.message === 'Email not confirmed' ? 'I-confirm muna ang email (check inbox)' : error.message, 'err', 4000);
  else refreshAuth();
};
$('signupBtn').onclick = async () => {
  const email = $('email').value.trim(), password = $('pass').value;
  if (!email || password.length < 6) { toast('Ilagay ang email at password (min 6)', 'err'); return; }
  const { data, error } = await sb.auth.signUp({ email, password, options: { emailRedirectTo: location.href } });
  if (error) { toast(error.message, 'err', 4000); return; }
  if (data.session) refreshAuth();
  else toast('Account ginawa! I-confirm ang email mo, tapos mag-login.', 'ok', 6000);
};
$('logoutBtn').onclick = async () => { await sb.auth.signOut(); refreshAuth(); };
sb.auth.onAuthStateChange((ev) => { if (ev === 'SIGNED_IN' || ev === 'SIGNED_OUT') refreshAuth(); });

// ================= FILE PICK =================
const extOf = n => (n.split('.').pop() || '').toLowerCase();
const baseOf = n => n.replace(/\.[^.]+$/, '');

function guessMeta(name) {
  let b = baseOf(name).replace(/[_]+/g, ' ');
  b = b.replace(/[\[(][^\])]*(karaoke|instrumental|minus ?one|lyrics|version|hd|videoke)[^\])]*[\])]/gi, '');
  b = b.replace(/\b(karaoke|videoke|minus ?one)\b/gi, '').replace(/\s{2,}/g, ' ').trim();
  const parts = b.split(/\s+-\s+/);
  if (parts.length >= 2) return { artist: parts[0].trim(), title: parts.slice(1).join(' - ').trim() };
  return { artist: '', title: b };
}

function addFiles(files) {
  const arr = [...files];
  const lrcs = arr.filter(f => extOf(f.name) === 'lrc');
  const media = arr.filter(f => VIDEO_EXT.includes(extOf(f.name)) || AUDIO_EXT.includes(extOf(f.name)));
  const skipped = arr.length - lrcs.length - media.length;
  media.forEach(f => {
    const ext = extOf(f.name);
    const kind = VIDEO_EXT.includes(ext) ? 'video' : 'audio';
    const lrc = kind === 'audio' ? lrcs.find(l => baseOf(l.name).toLowerCase() === baseOf(f.name).toLowerCase()) || null : null;
    const m = guessMeta(f.name);
    const tooBig = f.size > MAX;
    pending.push({ file: f, lrc, kind, ext, title: m.title, artist: m.artist,
      status: tooBig ? 'err' : 'wait', msg: tooBig ? `Lagpas ${VK_CONFIG.MAX_FILE_MB}MB — i-compress muna` : '' });
  });
  // .lrc na walang kapares — subukang ikabit sa pending audio na walang lrc
  lrcs.forEach(l => {
    if (pending.some(p => p.lrc === l)) return;
    const target = pending.find(p => p.kind === 'audio' && !p.lrc && p.status === 'wait' && baseOf(p.file.name).toLowerCase() === baseOf(l.name).toLowerCase());
    if (target) target.lrc = l;
  });
  if (skipped > 0) toast(`${skipped} file ang hindi suportado at nilaktawan`, 'err');
  renderPending();
}

function fmtMB(b) { return (b / 1024 / 1024).toFixed(1) + 'MB'; }

function renderPending() {
  $('upActions').style.display = pending.length ? 'flex' : 'none';
  $('upList').innerHTML = pending.map((p, i) => `
    <div class="uprow">
      <input class="field" data-i="${i}" data-f="title" value="${esc(p.title)}" placeholder="Title" ${p.status === 'ok' ? 'disabled' : ''}>
      <input class="field" data-i="${i}" data-f="artist" value="${esc(p.artist)}" placeholder="Artist" ${p.status === 'ok' ? 'disabled' : ''}>
      ${p.status === 'wait' || p.status === 'err' ? `<button class="btn small danger" data-rm="${i}">✕</button>` : '<span></span>'}
      <div class="meta">
        <span>${p.kind === 'video' ? '🎬' : '🎵'} ${esc(p.file.name)} · ${fmtMB(p.file.size)}${p.kind === 'audio' ? (p.lrc ? ' · 📝 ' + esc(p.lrc.name) : ' · <span style="color:var(--gold)">walang .lrc</span>') : ''}</span>
        <span class="${p.status === 'ok' ? 'st-ok' : p.status === 'err' ? 'st-err' : p.status === 'run' ? 'st-run' : 'muted'}">${esc(p.msg || (p.status === 'wait' ? 'Handa' : ''))}</span>
      </div>
    </div>`).join('');
}

$('upList').addEventListener('input', e => {
  const el = e.target; if (el.dataset.i === undefined) return;
  pending[el.dataset.i][el.dataset.f] = el.value;
});
$('upList').addEventListener('click', e => {
  const b = e.target.closest('[data-rm]'); if (!b) return;
  pending.splice(+b.dataset.rm, 1); renderPending();
});
$('upClear').onclick = () => { pending = pending.filter(p => p.status === 'run'); renderPending(); };

const drop = $('drop');
drop.onclick = () => $('fileIn').click();
$('fileIn').onchange = e => { addFiles(e.target.files); e.target.value = ''; };
['dragenter', 'dragover'].forEach(ev => drop.addEventListener(ev, e => { e.preventDefault(); drop.classList.add('over'); }));
['dragleave', 'drop'].forEach(ev => drop.addEventListener(ev, e => { e.preventDefault(); drop.classList.remove('over'); }));
drop.addEventListener('drop', e => addFiles(e.dataTransfer.files));

// ================= UPLOAD =================
async function uploadOne(p) {
  p.status = 'run'; p.msg = 'Ina-upload...'; renderPending();
  const id = crypto.randomUUID();
  const mediaPath = `media/${id}.${p.ext}`;
  const up = await sb.storage.from('songs').upload(mediaPath, p.file, { contentType: p.file.type || MIME[p.ext] || 'application/octet-stream', upsert: false });
  if (up.error) throw up.error;
  let lrcPath = null;
  if (p.lrc) {
    lrcPath = `lrc/${id}.lrc`;
    const ul = await sb.storage.from('songs').upload(lrcPath, p.lrc, { contentType: 'text/plain; charset=utf-8', upsert: true });
    if (ul.error) { lrcPath = null; toast('Na-upload ang kanta pero pumalya ang .lrc: ' + ul.error.message, 'err', 4000); }
  }
  const { data, error } = await sb.from('songs').insert({
    title: p.title.trim() || baseOf(p.file.name), artist: p.artist.trim() || null,
    kind: p.kind, media_path: mediaPath, lrc_path: lrcPath, size_bytes: p.file.size + (p.lrc ? p.lrc.size : 0)
  }).select('code').single();
  if (error) {
    await sb.storage.from('songs').remove([mediaPath, lrcPath].filter(Boolean));
    throw error;
  }
  p.status = 'ok'; p.msg = '✓ Song # ' + data.code;
}

$('upAll').onclick = async () => {
  const todo = pending.filter(p => p.status === 'wait' || (p.status === 'err' && p.file.size <= MAX));
  if (!todo.length) { toast('Walang i-a-upload', 'err'); return; }
  $('upAll').disabled = true;
  let ok = 0;
  for (const p of todo) {
    try { await uploadOne(p); ok++; }
    catch (e) { p.status = 'err'; p.msg = e.message || String(e); }
    renderPending();
  }
  $('upAll').disabled = false;
  toast(`${ok}/${todo.length} na-upload`, ok === todo.length ? 'ok' : 'err');
  loadLib();
};

// ================= LIBRARY =================
async function loadLib() {
  const { data, error } = await sb.from('songs').select('*').order('code', { ascending: false });
  if (error) { toast(error.message, 'err'); return; }
  songs = data || [];
  const used = songs.reduce((s, x) => s + (x.size_bytes || 0), 0);
  $('libStats').textContent = `${songs.length} kanta · ${fmtMB(used)} / 1GB`;
  $('storeBar').style.width = Math.min(100, used / FREE_STORAGE * 100).toFixed(1) + '%';
  renderLib();
}
function renderLib() {
  const q = $('libQ').value.trim().toLowerCase();
  const list = songs.filter(s => !q || String(s.code).includes(q) || s.title.toLowerCase().includes(q) || (s.artist || '').toLowerCase().includes(q));
  $('libList').innerHTML = list.length ? list.map(s => `
    <div class="lrow">
      <span class="code-badge">${s.code}</span>
      <div class="info">
        <div class="t">${esc(s.title)}</div>
        <div class="a">${esc(s.artist || '—')}<span class="tag">${s.kind === 'video' ? '🎬 video' : '🎵 audio'}</span>${s.kind === 'audio' ? `<span class="tag">${s.lrc_path ? '📝 lyrics' : '⚠ walang lyrics'}</span>` : ''}</div>
      </div>
      <div class="acts">
        ${s.kind === 'audio' ? `<button class="btn small" data-lrc="${s.id}" title="Upload/palitan ang .lrc">📝</button>` : ''}
        <button class="btn small" data-edit="${s.id}" title="I-edit">✏️</button>
        <button class="btn small danger" data-del="${s.id}" title="Burahin">🗑</button>
      </div>
    </div>`).join('') : '<div class="muted" style="padding:16px;text-align:center">Wala pang kanta.</div>';
}
$('libQ').addEventListener('input', renderLib);

let lrcTarget = null;
$('libList').addEventListener('click', async e => {
  const ed = e.target.closest('[data-edit]'), del = e.target.closest('[data-del]'), lr = e.target.closest('[data-lrc]');
  if (ed) {
    const s = songs.find(x => x.id === ed.dataset.edit);
    const title = prompt('Title:', s.title); if (title === null) return;
    const artist = prompt('Artist:', s.artist || ''); if (artist === null) return;
    const { error } = await sb.from('songs').update({ title: title.trim() || s.title, artist: artist.trim() || null }).eq('id', s.id);
    if (error) toast(error.message, 'err'); else { toast('Na-save', 'ok'); loadLib(); }
  }
  if (del) {
    const s = songs.find(x => x.id === del.dataset.del);
    if (!confirm(`Burahin ang #${s.code} "${s.title}"? Hindi na ito maibabalik.`)) return;
    const { error } = await sb.from('songs').delete().eq('id', s.id);
    if (error) { toast(error.message, 'err'); return; }
    await sb.storage.from('songs').remove([s.media_path, s.lrc_path].filter(Boolean));
    toast('Nabura', 'ok'); loadLib();
  }
  if (lr) { lrcTarget = songs.find(x => x.id === lr.dataset.lrc); $('lrcIn').click(); }
});
$('lrcIn').onchange = async e => {
  const f = e.target.files[0]; e.target.value = '';
  if (!f || !lrcTarget) return;
  const s = lrcTarget;
  const path = s.lrc_path || `lrc/${baseOf(s.media_path.split('/').pop())}.lrc`;
  const up = await sb.storage.from('songs').upload(path, f, { contentType: 'text/plain; charset=utf-8', upsert: true });
  if (up.error) { toast(up.error.message, 'err'); return; }
  const { error } = await sb.from('songs').update({ lrc_path: path }).eq('id', s.id);
  if (error) toast(error.message, 'err'); else { toast('Lyrics na-save 📝', 'ok'); loadLib(); }
};

refreshAuth();
