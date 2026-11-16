# 🎤 Videoke App

Pambahay na videoke — host sa laptop/tablet (naka-HDMI/Type-C sa TV), remote sa phone, song library sa Supabase.

## Pages
| URL | Para saan |
|---|---|
| `/` | Landing page |
| `/host/` | Ang screen sa TV — player, lyrics, pila, QR code |
| `/remote/?room=XXXX` | Phone remote — song code keypad, search, YouTube, pila, controls |
| `/songbook/` | Listahan ng lahat ng kanta at song number (search, A–Z, reserve) |
| `/settings/` | Tema, screensaver na digital clock, laki ng lyrics (naka-save sa device) |
| `/admin/` | Upload at manage ng song library (admin login) |

## Folder structure
```
├── index.html            Landing page
├── host/index.html       TV / host screen
├── remote/index.html     Phone remote
├── songbook/index.html   Listahan ng kanta
├── admin/index.html      Song library admin
├── settings/index.html   Settings + screensaver
├── demo/                 Demo songs (public domain, original arrangement): MP3 + LRC
├── assets/
│   ├── css/              style.css (shared) + isang CSS bawat page
│   └── js/
│       ├── icons.js      Line icons (SVG)
│       ├── theme.js      Light / dark mode
│       ├── prefs.js      Settings ng device
│       ├── screensaver.js Digital clock kapag idle
│       ├── mic-host.js   Tumatanggap ng boses sa TV (WebRTC + Web Audio)
│       ├── mic-remote.js Nagpapadala ng boses mula sa phone
│       ├── sfx.js        Sound effects (Web Audio, walang file)
│       ├── scenes.js     Animated backgrounds (canvas)
│       ├── config.js     Supabase URL/key, YouTube API key (optional)
│       ├── common.js     Shared helpers (Supabase client, toast, queue)
│       ├── host.js
│       ├── remote.js
│       └── admin.js
└── supabase/schema.sql   Database schema (tables, RLS, storage, realtime)
```

## Backend
Supabase project **VideokeTana** — tables `songs`, `queue`, `admins`; storage bucket `songs` (public read, admin write, 50MB/file); Realtime sa `queue` + broadcast channel `room-XXXX` para sa remote commands.

## Roadmap
- [x] Phase 1 — Host + QR + phone remote + queue + library (MP4 / MP3+LRC) + YouTube
- [x] Phase 2 — Phone bilang mic (WebRTC, experimental): echo, reverb, clarity, maraming mic sabay (duet), score mula sa mic, bantay sa feedback
- [ ] Phase 3 — Score batay sa tono (pitch), recording

## Demo songs
Ang `demo/` ay may 4 na public domain na kanta (Twinkle Twinkle Little Star, Jingle Bells, Silent Night, Auld Lang Syne), song no. 10001 hanggang 10004.
Ang backing track ay original na arrangement na ginawa gamit ang code; ang lyrics ay naka-sync na .lrc.
Sa database, `media_path` na nagsisimula sa `site:` ay nangangahulugang naka-host ang file sa website mismo, hindi sa Supabase storage.
Huwag mag-upload ng kantang may copyright nang walang lisensya. Tingnan ang `license_note` ng bawat kanta.
