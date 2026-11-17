// ===== Shared helpers (host / remote / admin) =====
const sb = window.supabase.createClient(VK_CONFIG.SUPABASE_URL, VK_CONFIG.SUPABASE_KEY);

// Root URL ng site (hal. https://user.github.io/VideokeTana/), mula sa lokasyon ng common.js
const SITE_ROOT = (() => {
  const src = (document.currentScript && document.currentScript.src) || location.href;
  return src.replace(/assets\/js\/common\.js.*$/, '');
})();

// "site:demo/x.mp3" = file na kasama sa website; kung hindi, nasa Supabase storage
function mediaUrl(path) {
  if (path && path.startsWith('site:')) return SITE_ROOT + path.slice(5);
  return sb.storage.from('songs').getPublicUrl(path).data.publicUrl;
}
const isSitePath = p => !!p && p.startsWith('site:');

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
  if (q.local_code) return { code: q.local_code, title: q.local_title || 'Kanta sa device', artist: '', source: 'local' };
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

// YouTube karaoke search (dumaraan sa Supabase Edge Function para nakatago ang API key)
async function ytSearchApi(q, mode = 'search') {
  const u = `${VK_CONFIG.SUPABASE_URL}/functions/v1/yt-search?mode=${mode}&q=${encodeURIComponent(q || '')}`;
  const r = await fetch(u, { headers: { apikey: VK_CONFIG.SUPABASE_KEY } });
  return r.json();
}
function ytErrorText(code) {
  return ({
    no_key: 'Hindi pa naka-set ang YouTube search. Pwede pa ring mag-paste ng YouTube link.',
    quota: 'Naubos na ang libreng YouTube search para ngayong araw. Bukas ulit, o mag-paste ng link.',
    forbidden_origin: 'Hindi pinapayagan ang site na ito na gumamit ng YouTube search.',
    network: 'Hindi maabot ang YouTube. Subukan ulit.'
  })[code] || 'Hindi gumana ang YouTube search. Subukan ulit, o mag-paste ng link.';
}
const CATEGORIES = ['OPM', 'Pop', 'Love song', 'Rock', 'Pasko', 'Pambata', 'Gospel', 'Folk', 'Classic', 'Iba pa'];

function queueSelect(room) {
  return sb.from('queue')
    .select('*, song:songs(id, code, title, artist, kind, media_path, lrc_path, yt_id)')
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
