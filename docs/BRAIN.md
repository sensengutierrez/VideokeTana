# VideokeTana Brain

Ito ang alaala ng project: **ano** ang nagawa, **bakit**, at **paano**. Basahin muna ito bago gumalaw ng code, at dagdagan ang Change log pagkatapos ng bawat pagbabago.

- Live: https://videoketana.tigomenote.com/ (dati: https://sensengutierrez.github.io/VideokeTana/, inililipat ng GitHub)
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
| Landing | `frontend/index.html` | Unang page: pakilala, Simulan, Sumali, Kunin ang app |
| App home | `frontend/app/index.html` | Display ng kanta, Buksan sa TV, Sumali sa room (room code), Kunin ang app, paano gamitin |
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
| Android app = native (Kotlin + Compose) mula app 2.0 (dati: WebView) | Gusto ng user ng totoong app na may sariling design. Iisa pa rin ang Supabase at ang protocol (broadcast cmd/state/hello/score/host, table queue), kaya nagkakausap ang app at ang web. Nag-a-update ang app nang sarili mula sa GitHub Releases (tag app-vN > versionCode). |
| Kanta sa device = IndexedDB ng TV device, numero 90001+ | Hindi ina-upload (privacy, walang gastos sa storage); ang phone ay nagre-reserve gamit ang numero na ipinapasa ng TV. |
| Pitch bar na hindi alam kung saan galing ang notes | Iisang component para sa demo (.mel.json) at sa MIDI/KAR sa susunod: ibigay lang ang `[[simula, haba, midi], ...]`. |
| Pitch ay hindi pinapansin ang octave | Para pantay ang lalaki at babae; tama na kung parehong nota. |
| Responsive na host (portrait/maliit na screen) | Ginagamit din ang host sa phone/tablet na nakatayo; dati nagpapatong ang mga button sa reserved list at orasan. |

## Tuwing magre-release

Palitan ang `?v=` ng lahat ng `assets/css` at `assets/js` link sa bawat `frontend/**/index.html`, ang `VERSION` sa `frontend/sw.js`, at ang bersyon sa footer ng home at sa Settings → Tungkol sa app, para makuha agad ng phone at TV ang bagong bersyon.

## Mga file na dapat kilalanin

- `android/`: native Android app (Kotlin + Compose). Pansariling signing key sa repo. Walang Android SDK sa workspace: GitHub Actions ang nagko-compile; tingnan ang logs ng run kapag may error.
- `frontend/sw.js`, `frontend/manifest.webmanifest`: installable web app. Palitan ang `VERSION` sa sw.js tuwing release.
- `frontend/assets/js/localsongs.js`, `frontend/mysongs/`: kanta sa device.

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
| 0.10.0 | **VideokeTana** na ang pangalan; **Android app** (phone, tablet, Android TV/Mi TV) na ginagawa ng GitHub Actions at nasa Releases; installable na web app (PWA) para sa iPhone at computer; intro sa home; **Kanta sa device** (90001+, hindi ina-upload, may sukat ng storage) | Hiling ng user: sariling app (hindi parang browser), intro, kanta mula sa device at storage, app at web na nagkakausap, app sa Smart TV | `android/` (WebView app: buong screen, mic/camera permission, file picker, full screen video, LEANBACK launcher, diretso sa host kapag TV); `.github/workflows/android.yml` → `releases/latest/download/VideokeTana.apk`; `manifest.webmanifest` + `sw.js`; `localsongs.js` (IndexedDB) + `mysongs/`; `queue.local_code`/`local_title`; ipinapasa ng TV sa phone ang listahan (`state.local`) |
| 0.11.0 | Bagong main page (landing) sa `/`: hero, larawan ng TV at phone, mga feature, wika; ang dating home ay nasa `/app/`; ang VideokeTana na logo ay papunta sa landing; ang ✕ ng bawat page ay pabalik sa `/app/` | Hiling ng user: main index na parang TigoMeNote, habang nananatili ang dating home | `frontend/index.html` + `landing.css`; `app/index.html`; manifest `start_url` at Android app ay diretso sa `/app/` (TV: `/host/`) |
| 0.11.1 | Bagong address: **videoketana.tigomenote.com** (Cloudflare CNAME → GitHub Pages) | Hiling ng user: sub-domain ng tigomenote | `CNAME`; pinayagan ang bagong origin sa `yt-search` (naka-deploy na); Android app at offline page sa bagong address (tinatanggap pa rin ang lumang link); `404.html` gumagana rin sa custom domain |
| 0.11.2 | Tunog na pang-sine sa intro: tik-tik ng umiikot na numero, riser, boom sa paglapag ng 10001, blip bawat letra ng VideokeTana, chord na may reverb | Hiling ng user (0.11.3: nilakasan nang mga 2.5x, may compressor at limiter, may suntok sa gitnang tunog para marinig sa speaker ng phone) | `VKSfx.play('intro')` (Web Audio, walang file); `intro.js` na pinagsasaluhan ng landing at app. Kapag hinarang ng browser ang tunog: "Pindutin para may tunog" na umuulit sa intro na may tunog. Sa Android app, kusang may tunog. Sumusunod sa Settings → Tunog |
| app 2.0 | **Native Android app** (Kotlin + Jetpack Compose), hindi na web sa loob: intro na may sariling tunog (laging tumutunog), home, sumali gamit QR/code, remote (numero, hanap, YouTube, pila, soundboard), songbook, settings (wika, tema, tunog), TV screen (ExoPlayer para sa sariling kanta, YouTube player, LRC lyrics, QR, score, iisang TV bawat room), **sariling update** mula sa GitHub Releases | Hiling ng user: totoong app na may sariling design at ma-update | `android/app/src/main/java/com/videoketana/app/`: `data/` (Supa REST, Realtime Phoenix websocket, Prefs, Updater), `ui/` (Theme na may DotText LCD, Synth na AudioTrack, Intro, Home, Screens), `remote/`, `tv/`. Kapareho ng web ang usapan sa Supabase. Wala pa: mic sa loob ng app (bukas muna sa web), kanta sa device |
| app 2.1 | Bagong remote: tab sa ibaba, card ng kinakanta na may Key at Tempo (−/+), Reserve / Unahin / Patugtugin ngayon, "Tunog sa TV" na sheet (bass, treble, key, tempo, lakas ng mic, echo, reverb, linaw), soundboard sheet; wallpaper at transparency; StandBy sa phone (dim na orasan, 8 kulay, pahiga o patayo); sa TV ng app: bass/treble (Equalizer), key at tempo (ExoPlayer) | Hiling ng user; inspirasyon ang feature ng ibang app pero sariling design | `cmd fx` keys: musicBass, musicTreble, musicKey, musicTempo (key/tempo: TV na app lang), `state.fx`; `ui/StandBy.kt`, `data/Wallpaper.kt` |
| app 2.2 | YouTube sa TV ng app gamit ang `frontend/yt.html` (tamang origin; inayos ang error 152 "This video is unavailable"); TV screen sa phone: pahiga at walang status bar; QR sa gitna; estilo ng numero/orasan: LCD, Digital (7-segment), Makapal, Manipis; naayos ang napiling opsyon na naging puti kapag may wallpaper | Bug report ng user (screenshot) | `tv/YtPlayer.kt` (WebView + JS bridge AndroidYT); inalis ang android-youtube-player; `DotText(style=)`; `Prefs.displayFont`, `Prefs.tvLandscape` |
| app 2.3 | **Library sa app**: login ng admin, upload ng kanta (MP3/M4A/MP4/WEBM + .lrc) mula sa phone na may checkbox ng karapatan, YouTube na kanta, listahan na may nakasulat na lisensya at lyrics, ayos ng title/artist/kategorya, palit ng .lrc, bura. Ayos sa itim na YouTube screen: buong-laki na laging WebView (alpha lang), "Kinukuha ang video..." habang hindi pa tumutugtog, retry ng play (huli: mute muna), at lipat sa susunod kapag 25s na walang tugtog o hindi ma-load ang player | Bug report ng user (itim na TV) at tanong kung bakit wala sa app ang upload at lisensya ng web | `data/Admin.kt` (Auth password grant + refresh token sa Prefs, storage upload na streaming, `rpc/is_admin`, parehong `license_note` ng web), `ui/Library.kt`; `yt.html` (origin, watchdog, `noapi`); `YtController.onError(String)` + `reload()`. RLS pa rin ang bantay. Hindi pa na-test sa device |
| app 2.4 | YouTube sa TV ng app: bass/treble mula sa remote (Equalizer sa session 0 = buong tunog ng app) at tempo (`setPlaybackRate`, 0.75x hanggang 1.25x). Key: hindi kaya sa YouTube | Hiling ng user: gandahan ang tunog ng YouTube mula sa phone | `geq` sa Tv.kt; `state.fx.ytEq` (totoo kung pumayag ang device) at `ytTempo`; `cmd('rate')` sa `yt.html`. Ang web TV ay hindi nagpapadala ng mga ito kaya naka-disable sa remote. Luma na ang session 0 sa Android: kailangang i-test sa Mi TV |
| app 2.5 | YouTube video sa TV ng app: may tunog pero itim ang video → ang WebView ay inilipat sa ilalim ng Compose (direkta sa `android.R.id.content`, index 0), at transparent ang Compose habang YouTube | Bug report ng user: may tunog, itim ang screen (2.2 hanggang 2.4) | Hula: hindi lumalabas ang video layer ng Chromium kapag ang WebView ay nasa loob ng Compose (lalo na may `alpha`/layer). `findActivity()`, `visibility` VISIBLE/INVISIBLE ayon sa mode. Kailangang kumpirmahin sa device |
| app 2.6 | Gabay na "Pagkonekta" sa Settings (Main/TV at Sumali/remote, may button); **MIDI/KAR**: upload sa Library (folder `midi/`, kind na `audio`), tumutugtog sa TV ng app gamit ang synth ng Android, lyrics mula sa loob ng file (KAR text `/` `\` `@`, o lyric event) | Hiling ng user: instructions sa koneksyon, MIDI na videoke sa sariling folder | `tv/Midi.kt` (`parseMidiLyrics`, `MidiPlayer` na MediaPlayer), `isMidiPath()`. Walang schema change (kulang ang permiso sa migration; kind `audio` + extension). Web host: hindi pa tumutugtog ng MIDI. Simple ang tunog: susunod ang SoundFont engine at MIDI settings (volume, pantay na lakas, reverb, chorus, 10-band EQ, polyphony, melody channel) |
| app 2.7 | **Walang auto-play pagbukas** ng TV: ang naiwang pila ay naghihintay ng Play/Skip/Refresh sa remote, "Simulan" sa TV, o bagong reserve. **Interlude** habang naghihintay (musikang gawa ng code: electric piano, bass, mahinang beat; bagong key/progression bawat simula; may on/off at lakas sa Settings). **BGV** sa likod ng TV (idle at audio): mga video sa table na `backgrounds` / folder `bgv/` (kapareho ng web), umiikot nang walang tunog; kung wala o "Animation" ang piniling setting, animation. Upload/bura ng BGV sa Library (tab na BGV, may checkbox ng karapatan) | Hiling ng user | `armed` + `startIds` sa Tv.kt; `tv/Interlude.kt` (AudioTrack static loop, fade); `BgvLayer` (pangalawang ExoPlayer, muted, shuffle); `Prefs.interlude/interludeVol/bgMode`; `Supa.backgrounds()`, `Admin.uploadBackground/deleteBackground`. Hindi pa na-test sa device |
| app 2.8 | **Folder sa device**: `Download/VideokeTana/` na may `MIDI/` (.mid .kar; pwedeng i-extract ang zip, hanggang 4 na sub-folder), `Kanta/` (.mp3 .mp4 + .lrc na kapareho ng pangalan), `BGV/`. Binabasa ng TV na device bawat minuto; numero 90001+ na hindi nagbabago (naka-save ang mapa ng file→numero); kasama sa `state.local` kaya nare-reserve sa remote (Numero at Hanap). **Sariling BGV**: 5 eksenang gawa ng app (Ilaw, Entablado, Bituin, Dapithapon, Retro) o Iikot bawat 2 minuto. Settings: pinagkukunan ng BGV (Lahat / Device / Library / Sarili) at eksena na may preview; "Folder sa device" na may Payagan ang access, Buong access, I-scan ulit | Hiling ng user: folder na diretsong lalagyan ng na-download na MIDI at BGV, sariling BGV, pagpili sa Settings. Sinuri ang 76 na MIDI ng user: lahat ay may lyrics (lyric event FF 05; 2 ay KAR text) | `data/LocalLib.kt`; permissions READ_MEDIA_AUDIO/VIDEO, READ/WRITE_EXTERNAL_STORAGE (lumang Android), MANAGE_EXTERNAL_STORAGE (para sa .kar); `MidiPlayer.loadFile`; `SceneCanvas` + `BG_SCENES`; `Prefs.bgScene`. Hindi pa alam ang error ng MIDI sa Mi TV; hindi pa na-test |
| app 2.9 | Songbook: "Online" at "Sa device na ito" (listahan ng folder na may basurahan na nagbubura ng file at .lrc); sa remote (Hanap): basurahan sa kantang nasa TV → `cmd localdel` (binubura ng TV ang file, nilalaktawan kung tumutugtog); "Payagan ang access" ay diretsong "Buong access" sa Android 11+ (fallback: karaniwang permission), at nagre-refresh pagbalik sa app | User: inilagay ang MIDI pero walang lumabas; gustong mabura agad ang ayaw na kanta sa listahan at sa folder. Ang online na kanta ay binubura sa Library (admin) | `LocalLib.delete()`; Lifecycle ON_RESUME sa FolderSection. Hula sa "walang lumabas": walang access (sa Android 13+, hindi nakikita ang .mid kung walang Buong access) o hindi pa nakikita dahil wala pang listahan ng device sa Songbook |

## Susunod

- Native app: mic sa phone (WebRTC) at pagtanggap ng mic sa TV screen ng app; pitch bar at background videos sa TV ng app; TV settings ng web (laki/kulay ng lyrics, score, background, marquee); kanta sa device.

- MIDI/KAR player na may libreng SoundFont; ipapasa ang melody track sa `VKPitch.load()`.
- Language/category ng kanta (OPM, English, Korean, Japanese).
- YouTube search: kailangan ang API key ng user (secret `YT_API_KEY`).
- Gawin ng user: GitHub → Settings → Pages → Source: GitHub Actions.
- Gawin ng user: Supabase Auth Site URL → `https://videoketana.tigomenote.com/admin/`.
- Hindi pa na-test sa totoong device: mic sa iba't ibang router, boses (speechSynthesis), pitch detection gamit ang totoong boses.
