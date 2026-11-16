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
const REDIRECT = location.origin + location.pathname;
let mode = 'login';
let recovering = false;

const MODES = {
  login:   { title: 'Mag-login', sub: 'Para mag-upload at mag-ayos ng mga kanta sa library.', btn: 'Login', passLbl: 'Password', ac: 'current-password' },
  signup:  { title: 'Gumawa ng account', sub: 'Gamit ang email at password na madali mong maaalala.', btn: 'Gumawa ng account', passLbl: 'Password', ac: 'new-password' },
  forgot:  { title: 'I-reset ang password', sub: 'Ilagay ang email mo. Padadalhan ka ng link para gumawa ng bagong password.', btn: 'Ipadala ang link', passLbl: 'Password', ac: 'current-password' },
  newpass: { title: 'Bagong password', sub: 'Gumawa ng bagong password para sa account mo.', btn: 'I-save ang password', passLbl: 'Bagong password', ac: 'new-password' }
};

function setMode(m) {
  mode = m;
  const cfg = MODES[m];
  $('authView').dataset.mode = m;
  $('authTitle').textContent = cfg.title;
  $('authSub').textContent = cfg.sub;
  $('submitLbl').textContent = cfg.btn;
  $('passLbl').textContent = cfg.passLbl;
  $('pass').autocomplete = cfg.ac;
  document.querySelectorAll('#authTabs button').forEach(b => b.classList.toggle('on', b.dataset.mode === m));
  $('authForm').style.display = 'block';
  $('authDone').style.display = 'none';
  $('pass').value = ''; $('pass2').value = '';
  updateStrength();
  showErr('');
  document.querySelectorAll('.fl.bad').forEach(f => f.classList.remove('bad'));
  setTimeout(() => (m === 'newpass' ? $('pass') : $('email')).focus(), 50);
}

function showErr(msg, ok = false, el = $('authErr')) {
  el.textContent = msg;
  el.classList.toggle('show', !!msg);
  el.classList.toggle('ok', ok);
}

function friendly(err) {
  const m = (err && (err.message || err.msg || String(err))) || '';
  if (/invalid login credentials/i.test(m)) return 'Mali ang email o password. Subukan ulit.';
  if (/email not confirmed/i.test(m)) return 'Hindi pa naka-confirm ang email. I-check ang inbox (pati Spam) para sa confirmation link.';
  if (/already registered|already been registered|user already exists/i.test(m)) return 'May account na ang email na ito — mag-login na lang.';
  if (/rate limit|too many|security purposes/i.test(m)) return 'Masyadong maraming subok. Maghintay ng ilang minuto bago ulitin.';
  if (/password should be at least|weak password/i.test(m)) return 'Masyadong maikli o mahina ang password (min 6 na character).';
  if (/invalid email|unable to validate email/i.test(m)) return 'Hindi valid ang email address.';
  if (/signups? not allowed|signup is disabled/i.test(m)) return 'Naka-off ang pag-sign up sa ngayon.';
  if (/failed to fetch|network/i.test(m)) return 'Walang internet o hindi maabot ang server. Subukan ulit.';
  if (/same.*password|different from the old/i.test(m)) return 'Kailangang iba ang bagong password sa luma.';
  return m || 'May error. Subukan ulit.';
}

function pwScore(p) {
  let s = 0;
  if (p.length >= 6) s++;
  if (p.length >= 10) s++;
  if (/[a-z]/.test(p) && /[A-Z]/.test(p)) s++;
  if (/\d/.test(p)) s++;
  if (/[^A-Za-z0-9]/.test(p)) s++;
  return p.length < 6 ? Math.min(s, 1) : Math.min(4, s);
}
function updateStrength() {
  const p = $('pass').value;
  const sc = p ? pwScore(p) : 0;
  const lv = [
    ['0%', 'var(--danger)', 'Hindi bababa sa 6 na character'],
    ['25%', 'var(--danger)', 'Mahina'],
    ['50%', 'var(--warn)', 'Pwede na'],
    ['75%', 'var(--accent)', 'Malakas'],
    ['100%', 'var(--accent)', 'Napakalakas']
  ][p ? Math.max(1, sc) : 0];
  $('meterBar').style.width = lv[0];
  $('meterBar').style.background = lv[1];
  $('meterTxt').textContent = p && p.length < 6 ? `Kulang pa ng ${6 - p.length} character` : lv[2];
}
$('pass').addEventListener('input', updateStrength);

// Show / hide password
document.addEventListener('click', e => {
  const b = e.target.closest('[data-eye]'); if (!b) return;
  const inp = $(b.dataset.eye);
  const show = inp.type === 'password';
  inp.type = show ? 'text' : 'password';
  b.classList.toggle('on', show);
  b.innerHTML = ICON(show ? 'eyeoff' : 'eye');
});

document.querySelectorAll('#authTabs button').forEach(b => b.onclick = () => setMode(b.dataset.mode));
$('forgotBtn').onclick = () => setMode('forgot');
$('backBtn').onclick = () => setMode('login');
$('doneBack').onclick = () => setMode('login');

function done(title, msg) {
  $('doneTitle').textContent = title;
  $('doneMsg').innerHTML = msg;
  $('authForm').style.display = 'none';
  $('authDone').style.display = 'block';
}

function bad(id, msg) {
  $(id).closest('.fl').classList.add('bad');
  showErr(msg);
  $(id).focus();
  return false;
}
function validate() {
  document.querySelectorAll('.fl.bad').forEach(f => f.classList.remove('bad'));
  const email = $('email').value.trim(), p = $('pass').value, p2 = $('pass2').value;
  if (mode !== 'newpass' && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) return bad('email', 'Ilagay ang tamang email address.');
  if (mode === 'forgot') return true;
  if (!p) return bad('pass', 'Ilagay ang password.');
  if ((mode === 'signup' || mode === 'newpass') && p.length < 6) return bad('pass', 'Kailangang 6 na character o higit pa ang password.');
  if ((mode === 'signup' || mode === 'newpass') && p !== p2) return bad('pass2', 'Hindi magkapareho ang dalawang password.');
  return true;
}

$('authForm').addEventListener('submit', async e => {
  e.preventDefault();
  showErr('');
  if (!validate()) return;
  const email = $('email').value.trim(), password = $('pass').value;
  const btn = $('authSubmit');
  btn.classList.add('loading');
  try {
    if (mode === 'login') {
      const { error } = await sb.auth.signInWithPassword({ email, password });
      if (error) throw error;
      toast('Naka-login ka na', 'ok');
      refreshAuth();
    } else if (mode === 'signup') {
      const { data, error } = await sb.auth.signUp({ email, password, options: { emailRedirectTo: REDIRECT } });
      if (error) throw error;
      if (data.user && Array.isArray(data.user.identities) && data.user.identities.length === 0) {
        throw new Error('User already registered');
      }
      if (data.session) { toast('Nagawa na ang account', 'ok'); refreshAuth(); }
      else done('I-check ang email mo', `Nagpadala kami ng confirmation link sa <b>${esc(email)}</b>.<br>I-click ito, tapos bumalik dito para mag-login.<br><small>Wala sa inbox? Tingnan ang Spam/Promotions.</small>`);
    } else if (mode === 'forgot') {
      const { error } = await sb.auth.resetPasswordForEmail(email, { redirectTo: REDIRECT });
      if (error) throw error;
      done('Naipadala na!', `Kung may account ang <b>${esc(email)}</b>, makakatanggap ka ng link para mag-reset ng password.<br><small>Tingnan din ang Spam folder.</small>`);
    } else if (mode === 'newpass') {
      const { error } = await sb.auth.updateUser({ password });
      if (error) throw error;
      recovering = false;
      history.replaceState(null, '', location.pathname);
      toast('Napalitan ang password', 'ok');
      refreshAuth();
    }
  } catch (err) {
    showErr(friendly(err));
  } finally {
    btn.classList.remove('loading');
  }
});

async function refreshAuth() {
  if (recovering) { $('authView').style.display = 'flex'; $('mainView').style.display = 'none'; return; }
  const { data: { session } } = await sb.auth.getSession();
  if (!session) {
    $('authView').style.display = 'flex';
    $('mainView').style.display = 'none';
    return;
  }
  $('authView').style.display = 'none';
  $('mainView').style.display = 'block';
  $('acctEmail').textContent = session.user.email || '';
  const { data: isAdmin } = await sb.rpc('is_admin');
  $('adminArea').style.display = isAdmin ? 'block' : 'none';
  $('notAdmin').style.display = isAdmin ? 'none' : 'block';
  if (isAdmin) loadLib();
}
$('logoutBtn').onclick = async () => { await sb.auth.signOut(); setMode('login'); refreshAuth(); };
sb.auth.onAuthStateChange(ev => {
  if (ev === 'PASSWORD_RECOVERY') { recovering = true; setMode('newpass'); refreshAuth(); return; }
  if (ev === 'SIGNED_IN' || ev === 'SIGNED_OUT') refreshAuth();
});

// ----- Palitan ang password (habang naka-login) -----
function closePw() { $('pwModal').style.display = 'none'; }
$('pwBtn').onclick = () => {
  $('np1').value = ''; $('np2').value = ''; showErr('', false, $('pwErr'));
  $('pwModal').style.display = 'flex';
  setTimeout(() => $('np1').focus(), 50);
};
$('pwCancel').onclick = closePw;
$('pwModal').addEventListener('click', e => { if (e.target.id === 'pwModal') closePw(); });
$('pwForm').addEventListener('submit', async e => {
  e.preventDefault();
  const a = $('np1').value, b = $('np2').value;
  if (a.length < 6) return showErr('Kailangang 6 na character o higit pa.', false, $('pwErr'));
  if (a !== b) return showErr('Hindi magkapareho ang dalawang password.', false, $('pwErr'));
  $('pwSave').disabled = true;
  const { error } = await sb.auth.updateUser({ password: a });
  $('pwSave').disabled = false;
  if (error) return showErr(friendly(error), false, $('pwErr'));
  closePw();
  toast('Napalitan ang password', 'ok');
});
document.addEventListener('keydown', e => { if (e.key === 'Escape') closePw(); });

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
      ${p.status === 'wait' || p.status === 'err' ? `<button class="btn small icon danger" data-rm="${i}" aria-label="Alisin">${ICON('x')}</button>` : '<span></span>'}
      <div class="meta">
        <span>${ICON(p.kind === 'video' ? 'film' : 'music')}${esc(p.file.name)}, ${fmtMB(p.file.size)}${p.kind === 'audio' ? (p.lrc ? ', may lyrics' : ', <span class="warn">walang .lrc</span>') : ''}</span>
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
  p.status = 'ok'; p.msg = 'Na-upload, song no. ' + data.code;
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
  $('libStats').textContent = `${songs.length} kanta, ${fmtMB(used)} sa 1GB`;
  $('storeBar').style.width = Math.min(100, used / FREE_STORAGE * 100).toFixed(1) + '%';
  renderLib();
}
function renderLib() {
  const q = $('libQ').value.trim().toLowerCase();
  const list = songs.filter(s => !q || String(s.code).includes(q) || s.title.toLowerCase().includes(q) || (s.artist || '').toLowerCase().includes(q));
  $('libList').innerHTML = list.length ? list.map(s => `
    <div class="lrow">
      <span class="num">${s.code}</span>
      <div class="info">
        <div class="t">${esc(s.title)}</div>
        <div class="a">${ICON(s.kind === 'video' ? 'film' : 'music')}<span>${esc(s.artist || 'Walang artist')}</span>${s.kind === 'audio' ? (s.lrc_path ? '<span class="tag">May lyrics</span>' : '<span class="tag warn">Walang lyrics</span>') : ''}${s.license_note ? `<span class="tag" title="${esc(s.license_note)}">${/public domain/i.test(s.license_note) ? 'Public domain' : 'May note'}</span>` : ''}</div>
      </div>
      <div class="acts">
        ${s.kind === 'audio' ? `<button class="btn small icon" data-lrc="${s.id}" title="${s.lrc_path ? 'Palitan' : 'Idagdag'} ang lyrics (.lrc)" aria-label="Lyrics">${ICON('lyrics')}</button>` : ''}
        <button class="btn small icon" data-edit="${s.id}" title="I-edit ang title at artist" aria-label="I-edit">${ICON('edit')}</button>
        <button class="btn small icon danger" data-del="${s.id}" title="Burahin" aria-label="Burahin">${ICON('trash')}</button>
      </div>
    </div>`).join('') : `<div class="empty">${q ? 'Walang nahanap.' : 'Wala pang kanta. Magdagdag sa itaas.'}</div>`;
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
    const files = [s.media_path, s.lrc_path].filter(x => x && !isSitePath(x));
    if (files.length) await sb.storage.from('songs').remove(files);
    toast('Nabura', 'ok'); loadLib();
  }
  if (lr) { lrcTarget = songs.find(x => x.id === lr.dataset.lrc); $('lrcIn').click(); }
});
$('lrcIn').onchange = async e => {
  const f = e.target.files[0]; e.target.value = '';
  if (!f || !lrcTarget) return;
  const s = lrcTarget;
  const path = (s.lrc_path && !isSitePath(s.lrc_path)) ? s.lrc_path : `lrc/${s.id}.lrc`;
  const up = await sb.storage.from('songs').upload(path, f, { contentType: 'text/plain; charset=utf-8', upsert: true });
  if (up.error) { toast(up.error.message, 'err'); return; }
  const { error } = await sb.from('songs').update({ lrc_path: path }).eq('id', s.id);
  if (error) toast(error.message, 'err'); else { toast('Na-save ang lyrics', 'ok'); loadLib(); }
};

// Error galing sa email link (hal. expired na link)
(function () {
  const h = new URLSearchParams(location.hash.slice(1));
  const desc = h.get('error_description');
  if (desc) {
    history.replaceState(null, '', location.pathname);
    setTimeout(() => showErr(/expired|invalid/i.test(desc) ? 'Expired o hindi na valid ang link. Humingi ng bago.' : desc), 300);
  }
})();
refreshAuth();
