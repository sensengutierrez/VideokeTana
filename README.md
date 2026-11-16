# 🎤 Videoke App

Pambahay na videoke. Ang TV screen ay laptop, tablet o phone (pwedeng naka-HDMI/Type-C sa TV), ang phone ay remote at mic, at ang song library ay nasa Supabase.

Basahin ang [docs/BRAIN.md](docs/BRAIN.md) para sa mga nagawa, bakit at paano.

## Folders

```
├── frontend/                 Ang website (ito ang naka-deploy sa GitHub Pages)
│   ├── index.html            Home
│   ├── host/                 TV screen: player, lyrics, pitch bar, pila, QR, mic mixer
│   ├── remote/               Phone: remote, soundboard, mic
│   ├── songbook/             Lahat ng kanta at song number
│   ├── settings/             Settings ng device
│   ├── admin/                Library (admin login): upload, YouTube, background videos
│   ├── demo/                 Demo songs (public domain, original arrangement): .mp3 + .lrc + .mel.json
│   └── assets/
│       ├── css/              style.css (shared) + isang CSS bawat page
│       └── js/               Isang JS bawat page + shared modules (tingnan ang Brain)
├── backend/
│   ├── supabase/
│   │   ├── schema.sql        Database: tables, RLS, storage, realtime
│   │   └── functions/
│   │       └── yt-search/    Edge Function para sa YouTube search (secret YT_API_KEY)
│   └── tools/
│       └── synth.py          Gumagawa ng demo songs (mp3, lrc, melody para sa pitch bar)
├── docs/BRAIN.md             Talaan ng mga nagawa, bakit at paano
└── .github/workflows/pages.yml  Nagde-deploy ng frontend/ sa GitHub Pages
```

## Deploy

GitHub Pages → Settings → Pages → **Source: GitHub Actions**. Tuwing may push sa `main` na may binago sa `frontend/`, kusang nade-deploy ang site sa https://sensengutierrez.github.io/VideokeTana/.

Habang "Deploy from a branch" pa ang setting, ililipat ka ng `index.html` at `404.html` sa ugat ng repo papunta sa `frontend/`.

## Backend

Supabase project **VideokeTana**: tables `songs`, `queue`, `admins`, `backgrounds`; storage bucket `songs` (public read, admin write, 50MB bawat file); Realtime sa `queue` at `songs`, at broadcast channel `room-XXXX` para sa remote, score at mic.

Ang `media_path` na nagsisimula sa `site:` ay file na nasa `frontend/` mismo, hindi sa Supabase storage.

## Mga kanta

Ang `frontend/demo/` ay may 10 public domain na kanta (song no. 10001 hanggang 10010). Original na arrangement ang backing track, ginawa ng `backend/tools/synth.py`.
Huwag mag-upload ng kantang may copyright nang walang lisensya. Tingnan ang `license_note` ng bawat kanta.
