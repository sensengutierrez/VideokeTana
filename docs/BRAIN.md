# VideokeTana Brain

Ito ang alaala ng project: **ano** ang nagawa, **bakit**, at **paano**. Basahin muna ito bago gumalaw ng code, at dagdagan ang Change log pagkatapos ng bawat pagbabago.

- Live: https://sensengutierrez.github.io/VideokeTana/
- Repo: sensengutierrez/VideokeTana (branch `main`, GitHub Pages)
- Backend: Supabase project "VideokeTana" (Postgres, Storage, Realtime, Edge Function)

## Mga panuntunan (hindi nagbabago)

- **Pambahay at personal muna.** Walang bayad, walang public na listahan ng kanta.
- **Walang kantang walang karapatan.** Public domain na may sariling arrangement, sariling file ng user, o YouTube embed lang (hindi dina-download). Hindi kinokopya ang laman ng ibang app (hal. SongHub); idea lang ang hinihiram, sariling disenyo.
- **Huwag sabihing gumagana kung hindi pa na-test.** Ang test dito ay sa Chrome (Playwright) na may stub ng Supabase; hiwalay na sinasabi kung hindi pa na-test sa totoong device.
- **Paunti-unting stage.** Isang grupo ng feature bawat commit, may test at screenshot.
- **Security:** admin lang ang nakakapag-upload/bura (RLS + `is_admin()`); ang YouTube API key ay nasa Supabase secret, hindi sa code.

## Paano gumagana (arkitektura)

| Bahagi | Saan | Ginagawa |
|---|---|---|
| Host (TV) | `host/`, `assets/js/host.js` | Laging bukas na screen ng videoke: background, orasan, QR, reserved list, tumutugtog ng kanta, lyrics, pitch bar, score, sound effects, mic mixer |
| Remote (phone) | `remote/`, `assets/js/remote.js` | Sumasali gamit ang QR/room code; numero, hanap, YouTube, pila, mic, soundboard |
| Songbook | `songbook/` | Listahan ng lahat ng kanta at numero |
| Library (admin) | `admin/` | Login, upload ng kanta + .lrc, YouTube add, background videos |
| Settings | `settings/` | Lahat ng setting; naka-save sa `localStorage` ng bawat device |

**Komunikasyon:** Supabase Realtime. Ang pila ay nasa table na `queue` (postgres_changes). Ang utos ng remote, estado ng TV, score at mic signaling ay broadcast sa channel na `room-<ROOM>` (events: `cmd`, `state`, `hello`, `score`, `rtc`).

**Uri ng kanta:** `audio` (MP3 + LRC), `video` (MP4), `youtube` (`yt_id`). Code ng kanta ay kusang sunod-sunod mula 10001. Ang path na nagsisimula sa `site:` ay file sa repo (`demo/`); ang iba ay nasa Storage bucket na `songs`.

## Mga desisyon at bakit

| Desisyon | Bakit |
|---|---|
| Static na web app (walang build step) sa GitHub Pages | Libre, madaling i-deploy, gumagana sa TV browser, laptop, tablet at phone. Pwedeng gawing native app balang araw. |
| Supabase | Libreng database + storage + realtime sa iisang lugar; RLS para sa admin-only na upload. |
| QR + 4 na letrang room code | Mabilis sumali ang phone nang walang account. |
| Laging-bukas na attract screen sa TV | Gusto ng user na parang totoong videoke: hindi nawawala ang main screen, may background, reserved list sa taas. |
| Sound effects at eksena ay gawa ng code (Web Audio, canvas) | Walang audio/video file na may karapatan; magaan; walang kinopya. |
| Phone bilang mic gamit ang WebRTC | Diretso phone sa TV sa iisang Wi-Fi, mababa ang delay (mga 0.1 hanggang 0.3 s). Signaling sa Supabase broadcast. Maaaring kailangan ng TURN server sa ilang router. |
| Wallpaper sa IndexedDB | Hindi ina-upload; nananatili sa device. Ganito rin ang plano para sa sariling kanta mula sa device. |
| Sariling wika (Tagalog/English) + `translate="no"` | Ginugulo ng Google Translate ng Chrome ang page (naghihiwalay ang numero, nagpapatong ang text). Kaya hinarang ito, at may sariling English na salin ang app. |
| Pitch bar na hindi alam kung saan galing ang notes | Iisang component para sa demo (.mel.json) at sa MIDI/KAR sa susunod: ibigay lang ang `[[simula, haba, midi], ...]`. |
| Pitch ay hindi pinapansin ang octave | Para pantay ang lalaki at babae; tama na kung parehong nota. |
| Responsive na host (portrait/maliit na screen) | Ginagamit din ang host sa phone/tablet na nakatayo; dati nagpapatong ang mga button sa reserved list at orasan. |

## Tuwing magre-release

Palitan ang `?v=` ng lahat ng `assets/css` at `assets/js` link sa bawat `index.html`, para makuha agad ng phone at TV ang bagong bersyon.

## Mga file na dapat kilalanin

- `assets/js/common.js`: Supabase client, `mediaUrl`, `itemInfo`, mga helper.
- `assets/js/prefs.js`: lahat ng setting at ang default nila (`VK_PREFS.get/set`).
- `assets/js/i18n.js`: diksyunaryo Tagalog→English at mga pattern para sa may numero. Kapag nagdagdag ng bagong text, idagdag din dito.
- `assets/js/sfx.js`: 14 na sound effect (`VKSfx.play/stop`), may `solo` para hindi magpatong.
- `assets/js/voice.js`: boses kapag pumindot (speechSynthesis), `VKVoice.key/act/say`.
- `assets/js/pitch.js`: pitch bar (`VKPitch.load/sample/draw/accuracy`).
- `assets/js/mic-host.js`: mixer ng mic sa TV + `pitch()` (autocorrelation, 80 hanggang 1000 Hz).
- `assets/js/mic-remote.js`: phone bilang mic.
- `supabase/schema.sql`: buong database.
- Demo songs: ginawa ng `tools/synth.py` (wala sa repo). Lumilikha ng `.mp3`, `.lrc` at `.mel.json`.

## Change log

| Bersyon | Ano | Bakit | Paano |
|---|---|---|---|
| 0.1 | Host, remote, admin, schema | Simula ng Phase 1 | Static pages + Supabase |
| 0.2 | Lumipat sa Supabase "VideokeTana", maayos na folder | Hiling ng user | Bagong project, migration |
| 0.3 | Songbook, ✕ close, light/dark, bagong login/signup | Hindi makagawa ng account; walang close | Auth modes, strength meter, friendly errors |
| 0.4 | Bagong disenyo (graphite + teal, dot-matrix na numero), Settings, screensaver | "Pambata" ang dating UI | Design tokens, icons.js, screensaver.js |
| 0.5 | QR scanner sa remote, sticky Reserve, mensahe kapag walang TV | Hindi tumutugtog sa laptop: hindi napipindot ang Reserve | BarcodeDetector + jsQR |
| 0.6 | Laging-bukas na TV screen, background na eksena, reserved list, score, countdown, sound effects, lyric style, backup/export, YouTube add | Para parang totoong videoke | scenes.js, sfx.js, Settings sections |
| 0.7 | Phone bilang mic; single-device mode; wallpaper at transparent na box | Phase 2 + hiling ng user | WebRTC; on-screen picker; IndexedDB |
| 0.8 | Wika (Tagalog/English), harang sa Chrome translate, boses kapag pumindot, 14-tunog na soundboard (volume, stop, isa-isa) | Nagpapatong/naghihiwalay ang text dahil sa Chrome translate; hiling ng user | i18n.js, voice.js, sfx.js |
| 0.9 | Pitch bar + score na may tono; responsive na host sa portrait; ang Brain na ito | Ihanda ang pitch bar bago ang MIDI; nagpapatong ang taas ng TV screen sa phone | pitch.js, `.mel.json`, `pitch()` sa mic-host, media query sa host.css |
| 0.9.1 | YouTube link sa single-device panel (Hanap) at sa Library (walang API key) | Walang paraan magdagdag ng YouTube sa isang device lang habang wala pang API key | `parseYouTubeId` + oEmbed para sa title; `hostReserve(null, {yt_id, yt_title})` |
| 0.9.2 | `?v=` sa lahat ng CSS/JS link | Lumang CSS ang nakuha ng phone (cache ng browser/GitHub Pages), kaya nagpapatong pa rin sa taas | Palitan ang `?v=` sa lahat ng HTML tuwing may release |

## Susunod

- MIDI/KAR player na may libreng SoundFont; ipapasa ang melody track sa `VKPitch.load()`.
- Sariling kanta mula sa device (IndexedDB, hindi ina-upload).
- Language/category ng kanta (OPM, English, Korean, Japanese).
- YouTube search: kailangan ang API key ng user (secret `YT_API_KEY`).
- Gawin ng user: Supabase Auth Site URL → `https://sensengutierrez.github.io/VideokeTana/admin/`.
- Hindi pa na-test sa totoong device: mic sa iba't ibang router, boses (speechSynthesis), pitch detection gamit ang totoong boses.
