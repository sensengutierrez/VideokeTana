// ===== Kanta sa device: magdagdag, mag-edit, magbura; storage =====
const $ = id => document.getElementById(id);
const mb = n => n >= 1e9 ? (n / 1e9).toFixed(1) + ' GB' : (n / 1e6).toFixed(n < 1e7 ? 1 : 0) + ' MB';

async function render() {
  let list = [];
  try { list = await VKLocal.list(); }
  catch (e) { $('list').innerHTML = '<div class="empty">Hindi magamit ang storage ng browser na ito.</div>'; return; }
  $('count').textContent = list.length + (list.length === 1 ? ' kanta' : ' na kanta');
  $('list').innerHTML = list.length ? list.map(s => `
    <div class="song">
      <span translate="no" class="dot">${s.code}</span>
      <div style="min-width:0"><div class="t" data-notr>${esc(s.title)}</div>
        <div class="a"><span data-notr>${esc(s.artist || '')}</span>${s.artist ? ', ' : ''}${s.kind === 'video' ? 'Video' : 'Audio'}${s.lrc ? ', may lyrics' : ''}, ${mb(s.size || 0)}</div></div>
      <div class="acts">
        <button class="btn small icon" data-edit="${s.code}" title="I-edit" aria-label="I-edit">${ICON('edit')}</button>
        <button class="btn small icon danger" data-del="${s.code}" title="Burahin" aria-label="Burahin">${ICON('trash')}</button>
      </div>
    </div>`).join('') : '<div class="empty">Wala pang kanta sa device na ito.</div>';
  const u = await VKLocal.usage();
  $('stSongs').textContent = mb(u.songs);
  $('stBar').style.width = u.quota ? Math.min(100, u.used / u.quota * 100).toFixed(1) + '%' : '0%';
  $('stFree').textContent = u.quota ? `Libre pa: ${mb(Math.max(0, u.quota - u.used))}` : '';
  $('stKeep').textContent = u.persisted ? 'Hindi kusang mabubura' : 'Pwedeng burahin ng browser kapag puno ang device';
}

async function add(files) {
  if (!files || !files.length) return;
  toast('Sine-save sa device...', 'info');
  try {
    const added = await VKLocal.addFiles(files);
    toast(added.length ? `Naidagdag ang ${added.length} kanta` : 'Walang kantang naidagdag (MP3, M4A, MP4 o WebM lang)', added.length ? 'ok' : 'err');
  } catch (e) {
    toast(/quota/i.test(e && e.name) ? 'Puno na ang storage ng device' : 'Hindi na-save: ' + (e.message || e), 'err');
  }
  render();
}
$('files').addEventListener('change', e => { add(e.target.files); e.target.value = ''; });
['dragenter', 'dragover'].forEach(ev => $('drop').addEventListener(ev, e => { e.preventDefault(); $('drop').classList.add('over'); }));
['dragleave', 'drop'].forEach(ev => $('drop').addEventListener(ev, () => $('drop').classList.remove('over')));
$('drop').addEventListener('drop', e => { e.preventDefault(); add(e.dataTransfer.files); });

$('list').addEventListener('click', async e => {
  const d = e.target.closest('[data-del]'), ed = e.target.closest('[data-edit]');
  if (d) {
    const m = await VKLocal.meta(d.dataset.del);
    if (!confirm(`Burahin ang #${m.code} "${m.title}"? Hindi na ito maibabalik.`)) return;
    await VKLocal.remove(m.code); toast('Nabura', 'ok'); render();
  }
  if (ed) {
    const m = await VKLocal.meta(ed.dataset.edit);
    const t = prompt('Title ng kanta', m.title); if (t === null) return;
    const a = prompt('Artist', m.artist || ''); if (a === null) return;
    await VKLocal.update(m.code, { title: t.trim() || m.title, artist: a.trim() });
    toast('Na-save', 'ok'); render();
  }
});
render();
