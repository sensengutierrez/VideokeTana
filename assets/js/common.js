// ===== Shared helpers (host / remote / admin) =====
const sb = window.supabase.createClient(VK_CONFIG.SUPABASE_URL, VK_CONFIG.SUPABASE_KEY);

function mediaUrl(path) {
  return sb.storage.from('songs').getPublicUrl(path).data.publicUrl;
}

function esc(s) {
  return String(s ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

function toast(msg, type = 'info', ms = 2600) {
  let wrap = document.getElementById('toastWrap');
  if (!wrap) {
    wrap = document.createElement('div');
    wrap.id = 'toastWrap';
    document.body.appendChild(wrap);
  }
  const t = document.createElement('div');
  t.className = 'toast ' + type;
  t.textContent = msg;
  wrap.appendChild(t);
  setTimeout(() => t.classList.add('out'), ms);
  setTimeout(() => t.remove(), ms + 400);
}

// Accepts full YouTube URL, youtu.be link, shorts link, or raw 11-char ID
function parseYouTubeId(input) {
  const s = String(input || '').trim();
  if (/^[\w-]{11}$/.test(s)) return s;
  const m = s.match(/(?:v=|youtu\.be\/|shorts\/|embed\/|live\/)([\w-]{11})/);
  return m ? m[1] : null;
}

async function fetchYouTubeTitle(id) {
  const url = 'https://www.youtube.com/watch?v=' + id;
  const tries = [
    'https://www.youtube.com/oembed?format=json&url=' + encodeURIComponent(url),
    'https://noembed.com/embed?url=' + encodeURIComponent(url)
  ];
  for (const u of tries) {
    try {
      const r = await fetch(u);
      if (r.ok) {
        const j = await r.json();
        if (j.title) return j.title;
      }
    } catch (e) { /* try next */ }
  }
  return 'YouTube video';
}

// Display info for a queue row (library song or YouTube)
function itemInfo(q) {
  if (q.song) return { code: q.song.code, title: q.song.title, artist: q.song.artist || '', source: q.song.kind };
  return { code: 'YT', title: q.yt_title || 'YouTube video', artist: 'YouTube', source: 'youtube' };
}

const ROOM_RE = /^[A-Z0-9]{4,8}$/;
function cleanRoom(r) { return String(r || '').trim().toUpperCase(); }

function makeRoomCode() {
  const chars = 'ABCDEFGHJKMNPQRSTUVWXYZ23456789'; // walang 0/O/1/I/L para iwas lito
  let s = '';
  const arr = new Uint32Array(4);
  crypto.getRandomValues(arr);
  arr.forEach(n => s += chars[n % chars.length]);
  return s;
}

function queueSelect(room) {
  return sb.from('queue')
    .select('*, song:songs(id, code, title, artist, kind, media_path, lrc_path)')
    .eq('room', room)
    .order('sort_order', { ascending: true });
}

function sortQueue(rows) {
  // playing muna, tapos waiting ayon sa sort_order
  return [...rows].sort((a, b) => {
    if (a.status !== b.status) return a.status === 'playing' ? -1 : 1;
    return a.sort_order - b.sort_order;
  });
}
