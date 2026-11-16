# 🎤 Videoke App

Pambahay na videoke — host sa laptop/tablet (naka-HDMI/Type-C sa TV), remote sa phone, song library sa Supabase.

## Pages
| URL | Para saan |
|---|---|
| `/` | Landing page |
| `/host/` | Ang screen sa TV — player, lyrics, pila, QR code |
| `/remote/?room=XXXX` | Phone remote — song code keypad, search, YouTube, pila, controls |
| `/songbook/` | Listahan ng lahat ng kanta at song number (search, A–Z, reserve) |
| `/admin/` | Upload at manage ng song library (admin login) |

## Folder structure
```
├── index.html            Landing page
├── host/index.html       TV / host screen
├── remote/index.html     Phone remote
├── songbook/index.html   Listahan ng kanta
├── admin/index.html      Song library admin
├── assets/
│   ├── css/              style.css (shared) + isang CSS bawat page
│   └── js/
│       ├── icons.js      Line icons (SVG)
│       ├── theme.js      Light / dark mode
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
- [ ] Phase 2 — Phone bilang mic (WebRTC) + earphone mode
- [ ] Phase 3 — Duet (2 mics) + scoring
