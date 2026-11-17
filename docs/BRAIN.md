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
| Home | `frontend/index.html` | Display ng kanta, Buksan sa TV, Sumali sa room (room code), paano gamitin |
| Host (TV) | `frontend/host/`, `host.js` | Laging bukas na screen ng videoke: background, orasan, QR, reserved list, tumutugtog ng kanta, lyrics, pitch bar, score, sound effects, mic mixer |
| Remote (phone) | `frontend/remote/`, `remote.js` | Sumasali gamit ang QR/room code; numero, hanap, YouTube, pila, mic, soundboard |
| Songbook | `frontend/songbook/` | Listahan ng lahat ng kanta at numero |
| Library (admin) | `frontend/admin/` | Login, upload ng kanta + .lrc, YouTube add, background videos |
| Settings | `frontend/settings/` | Lahat ng setting; naka-save sa `localStorage` ng bawat device |

**Komunikasyon:** Supabase Realtime. Ang pila ay nasa table na `queue` (postgres_changes). Ang utos ng remote, estado ng TV, score at mic signaling ay broadcast sa channel na `room-<ROOM>` (events: `cmd`, `state`, `hello`, `score`, `rtc`).

**Uri ng kanta:** `audio` (MP3 + LRC), `video` (MP4), `youtube` (`yt_id`). Code ng kanta ay kusang sunod-sunod mula 10001. Ang path na nagsisimula sa `site:` ay file sa repo (`frontend/demo/`); ang iba ay nasa Storage bucket na `songs`.

## Mga desisyon at bakit

| Desisyon | Bakit |
|---|---|
| Hiwalay na `frontend/` at `backend/` | Malinaw kung alin ang website at alin ang database/server code. Dine-deploy ng GitHub Actions ang `frontend/` lang. |
| Static na web app (walang build step) sa GitHub Pages | Libre, madaling i-deploy, gumagana sa TV browser, laptop, tablet at phone. Pwedeng gawing native app balang araw. |
| Supabase | Libreng database + storage + realtime sa iisang lugar; RLS para sa admin-only na upload. |
| QR + 4 na letrang room code | Mabilis sumali ang phone nang walang account. |
| Laging-bukas na attract screen sa TV | Gusto ng user na parang totoong videoke: hindi nawawala ang main screen, may background, reserved list sa taas. |
| Sound effects at eksena ay gawa ng code (Web Audio, canvas) | Walang audio/video file na may karapatan; magaan; walang kinopya. |
| Phone bilang mic gamit ang WebRTC (hindi mawawala ang delay; pinakamaliit lang) | Diretso phone sa TV sa iisang Wi-Fi, mababa ang delay (mga 0.1 hanggang 0.3 s). Signaling sa Supabase broadcast. Maaaring kailangan ng TURN server sa ilang router. |
| Wallpaper sa IndexedDB | Hindi ina-upload; nananatili sa device. Ganito rin ang plano para sa sariling kanta mula sa device. |
| Sariling wika (Tagalog/English) + `translate="no"` | Ginugulo ng Google Translate ng Chrome ang page (naghihiwalay ang numero, nagpapatong ang text). Kaya hinarang ito, at may sariling English na salin ang app. |
| Pitch bar na hindi alam kung saan galing ang notes | Iisang component para sa demo (.mel.json) at sa MIDI/KAR sa susunod: ibigay lang ang `[[simula, haba, midi], ...]`. |
| Pitch ay hindi pinapansin ang octave | Para pantay ang lalaki at babae; tama na kung parehong nota. |
| Responsive na host (portrait/maliit na screen) | Ginagamit din ang host sa phone/tablet na nakatayo; dati nagpapatong ang mga button sa reserved list at orasan. |

## Tuwing magre-release

Palitan ang `?v=` ng lahat ng `assets/css` at `assets/js` link sa bawat `frontend/**/index.html`, at ang bersyon sa footer ng home at sa Settings → Tungkol sa app, para makuha agad ng phone at TV ang bagong bersyon.

## Mga file na dapat kilalanin

- `frontend/assets/js/common.js`: Supabase client, `mediaUrl`, `itemInfo`, mga helper.
- `frontend/assets/js/prefs.js`: lahat ng setting at ang default nila (`VK_PREFS.get/set`).
- `frontend/assets/js/i18n.js`: diksyunaryo Tagalog→English at mga pattern para sa may numero. Kapag nagdagdag ng bagong text, idagdag din dito.
- `frontend/assets/js/sfx.js`: 14 na sound effect (`VKSfx.play/stop`), may `solo` para hindi magpatong.
- `frontend/assets/js/voice.js`: boses kapag pumindot (speechSynthesis), `VKVoice.key/act/say`.
- `frontend/assets/js/pitch.js`: pitch bar (`VKPitch.load/sample/draw/accuracy`).
- `frontend/assets/js/mic-host.js`: mixer ng mic sa TV + `pitch()` (autocorrelation, 80 hanggang 1000 Hz).
- `frontend/assets/js/mic-remote.js`: phone bilang mic.
- `backend/supabase/schema.sql`: buong database.
- `backend/supabase/functions/yt-search/index.ts`: YouTube search (Edge Function).
- Demo songs: ginawa ng `backend/tools/synth.py` (`python3 backend/tools/synth.py <folder>`). Lumilikha ng `.mp3`, `.lrc` at `.mel.json`.

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
| 0.9.6 | Highlight ng lyrics bawat salita; kasya sa iisang linya; QR sa kanang itaas; layout para sa nakahigang phone | Sabay na nagha-highlight ang dalawang linya kapag nahati ang mahabang lyric; tinatakpan ng lyrics ang pitch bar | Bawat salita ay may sariling `--p` (`setLine`), `fitLine` na nagpapaliit ng font, media query `max-height: 520px` |
| 0.9.7 | Landscape mode sa phone/tablet: kusang nakahiga pagpindot ng Simulan, button na Landscape sa itaas, paalala kung hindi kaya ng browser, setting na Landscape/Auto | Hiling ng user; mas malaki ang lyrics at pitch bar kapag nakahiga | Fullscreen + `screen.orientation.lock('landscape')` (Android Chrome); `#rotateHint` bilang fallback (iPhone) |
| 0.9.8 | `frontend/` + `backend/` na folders, GitHub Actions deploy, bagong home page (panel na parang videoke machine, Sumali sa room gamit ang code, Paano gamitin), source ng Edge Function at synth.py sa repo | Hiling ng user: maayos na front end/back end at magandang index | `git mv`; `.github/workflows/pages.yml`; redirect na `index.html`/`404.html` habang branch deploy pa |
| 0.9.9 | Live na puntos ng tono sa pitch bar (% at tama/lahat), FAAH! sa soundboard | Ikinumpara ng user sa ibang videoke app | `pitch.js` draw; sariling sigaw gamit ang boses ng device (hindi kinopyang audio) |
| 0.9.10 | QR habang may kanta: button na may room code sa hanay ng mga button sa itaas; pindutin para lumaki sa gitna (sarado pagkalipas ng 20 s, o pindutin/Esc) | Nakaka-distract ang QR sa kanang itaas habang kumakanta | `#btnQr`, `body.qr-open` ay ginagawang malaking overlay ang `#cornerQr` |
| 0.9.11 | Mas mababang delay ng phone mic + sukat ng delay sa mic chip ng TV | Hiling ng user na walang delay | Opus 10 ms packet, FEC, walang DTX (SDP ng sagot ng TV); priority high sa sender; jitter buffer 0 (dati na); `getStats` para sa delay. Sa test sa iisang computer: mga 115 ms |
| 0.9.12 | Hindi na tinatakpan ng mga button sa itaas ang kinakanta at pila | Natatakpan ang pangalan ng kanta kapag lumabas ang mga button | `fitTopBtns()`: kapag nagpapatong, icon lang ang mga button; kung kulang pa, ibinababa ang pila (`tb-push`) |
| 0.9.14 | Kontrol ng tunog mula sa phone (Mic tab): lakas ng mic, echo, reverb, linaw, at bass/treble ng kanta; bass/treble din sa Settings | Hiling ng user; hindi mabago ang echo mula sa phone | `cmd fx` → `VK_PREFS.set` sa TV; `state.fx` pabalik sa phone. Bass/treble: Web Audio (lowshelf 120 Hz, highshelf 4 kHz) sa `aud`/`vid` na may `crossOrigin=anonymous`. Hindi kaya sa YouTube (cross-origin na player) |
| 0.9.15 | Iisang TV lang bawat room (ang luma ay naghihintay, may "Gamitin dito"); ibinabalik sa pila ang mga naipit sa "Kinakanta"; pwedeng alisin sa phone ang naipit | Naipit ang 3 kanta sa "Kinakanta" at hindi matuloy ang susunod: dalawang TV tab sa iisang room ang parehong kumukuha ng kanta | Broadcast `host` {id, since}; mas bago ang panalo; `standby` ay hindi sumasagot sa cmd/hello/rtc; `startNext` ay nagre-reset ng ibang `playing` na row |
| 0.9.16 | Nabubura na sa pila ang tapos o na-skip na kanta; pwedeng alisin sa phone ang kinakanta/naipit | Paikot-ikot sa parehong kanta at hindi nawawala kapag inalis. Sa logs: walang DELETE mula sa TV, at ang DELETE ng phone ay may `status=waiting` | `await` sa delete ng `finishCurrent` (ang supabase-js query ay hindi tumatakbo kung hindi hinihintay); tinanggal ang `.eq('status','waiting')` sa pag-alis. Aral: laging `await` ang `sb.from(...)`; hindi ito nahuli ng test stub dahil agad tumatakbo ang stub |

## Susunod

- MIDI/KAR player na may libreng SoundFont; ipapasa ang melody track sa `VKPitch.load()`.
- Sariling kanta mula sa device (IndexedDB, hindi ina-upload).
- Language/category ng kanta (OPM, English, Korean, Japanese).
- YouTube search: kailangan ang API key ng user (secret `YT_API_KEY`).
- Gawin ng user: GitHub → Settings → Pages → Source: GitHub Actions.
- Gawin ng user: Supabase Auth Site URL → `https://sensengutierrez.github.io/VideokeTana/admin/`.
- Hindi pa na-test sa totoong device: mic sa iba't ibang router, boses (speechSynthesis), pitch detection gamit ang totoong boses.
