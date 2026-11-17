// ===== Wika ng app: Tagalog (default) o English =====
// Isinusulat ang app sa Tagalog; kapag English ang pinili, isinasalin ang mga teksto sa screen gamit ang diksyunaryo sa ibaba.
// Hindi isinasalin ang lyrics, title ng kanta at mga isinulat ng user (may data-notr).
// Pinipigilan din nito ang Google Translate ng Chrome (na sumisira sa mga numero).
(function () {
  const D = {
    // ---- pangkalahatan ----
    'Isara': 'Close', 'Settings': 'Settings', 'Library': 'Library', 'Remote': 'Remote', 'Songbook': 'Songbook',
    'Reserve': 'Reserve', 'Numero': 'Number', 'Hanap': 'Search', 'Pila': 'Queue', 'Mic': 'Mic', 'YouTube': 'YouTube',
    'Hanapin': 'Search', 'Alisin': 'Remove', 'Burahin': 'Delete', 'Burahin lahat': 'Clear all', 'Kanselahin': 'Cancel',
    'I-save': 'Save', 'Na-save': 'Saved', 'Nabura': 'Deleted', 'Idagdag': 'Add', 'Lahat': 'All', 'Bago': 'New',
    'Paborito': 'Favorites', 'Kamakailan': 'Recent', 'Kategorya': 'Category', 'Title': 'Title', 'Artist': 'Artist',
    'Wala': 'None', 'Walang nahanap.': 'Nothing found.', 'Walang nahanap. Subukan ang ibang salita.': 'Nothing found. Try other words.',
    'Hinahanap': 'Searching', 'Naglo-load': 'Loading', 'Song no.': 'Song no.', 'Room': 'Room', 'Room code': 'Room code',
    'Walang artist': 'No artist', 'Wala pang kanta': 'No songs yet', 'Wala pang kanta.': 'No songs yet.',
    'Ulitin': 'Replay', 'Ulitin mula simula': 'Replay from the start', 'Hinaan': 'Volume down', 'Lakasan': 'Volume up',
    'Play o pause': 'Play or pause', 'Laktawan': 'Skip', 'Laktawan (N)': 'Skip (N)', 'Fullscreen': 'Fullscreen', 'Fullscreen (F)': 'Fullscreen (F)',
    'Background': 'Background', 'Palitan ang background (B)': 'Change background (B)', 'Bagong room': 'New room', 'Bagong room code': 'New room code',
    'Lumipat sa dark mode': 'Switch to dark mode', 'Lumipat sa light mode': 'Switch to light mode',
    'Pindutin kahit saan para bumalik': 'Tap anywhere to go back', 'Screensaver. Pindutin para bumalik.': 'Screensaver. Tap to go back.',
    'Mga section': 'Sections', 'Ayusin ayon sa': 'Sort by', 'Uri': 'Type',

    // ---- home ----
    'Pumili ng kanta, i-type ang numero, kumanta.': 'Pick a song, type its number, sing.',
    'Buksan sa TV': 'Open on TV', 'Sa laptop o tablet na naka-HDMI. Dito tutugtog ang kanta.': 'On the laptop or tablet connected by HDMI. Songs play here.',
    'Mag-reserve at kontrolin ang kanta gamit ang phone.': 'Reserve and control songs with your phone.',
    'Lahat ng kanta at ang song number nila.': 'All songs and their song numbers.',
    'Mag-upload at mag-ayos ng kanta. Para sa admin.': 'Upload and organize songs. For the admin.',
    'Tema, screensaver na orasan at laki ng lyrics.': 'Theme, screensaver clock and lyric size.',
    'Song number display': 'Song number display',

    // ---- host / TV ----
    'Handa na ang TV': 'The TV is ready', 'Simulan': 'Start', 'Ituloy': 'Continue',
    'Pindutin para payagan ng browser na tumunog ang kanta. Pagkatapos, F para fullscreen.': 'Tap so the browser allows sound. Then press F for fullscreen.',
    'Pumili ng kanta': 'Pick a song', 'Walang lyrics para sa kantang ito': 'No lyrics for this song',
    'I-scan ang QR code gamit ang camera ng phone para mag-reserve ng kanta.': "Scan the QR code with your phone's camera to reserve a song.",
    'Inihahanda ang susunod na kanta': 'Getting the next song ready', 'Score': 'Score', 'Naka-pause': 'Paused',
    'Walang naka-reserve': 'No reservations', 'Walang kasunod. Mag-reserve gamit ang phone.': 'Nothing next. Reserve with your phone.',
    'Reserved': 'Reserved', 'Susunod': 'Next', 'Enter para i-reserve, Esc para kanselahin': 'Enter to reserve, Esc to cancel',
    'I-type ang song number': 'Type the song number', 'Walang kanta sa numerong ito': 'No song with this number',
    'Gamitin ang mic ng device na ito': 'Use this device\'s mic', 'Mic ng device na ito': "This device's mic",
    'Para sa isang device lang. Gumamit ng earphones na may mic o external na mic para hindi umugong ang speaker.': 'For single-device use. Use earphones with a mic or an external mic so the speaker does not howl.',
    'Naka-on ang mic. Pindutin ulit para patayin.': 'Mic is on. Tap again to turn it off.',
    'Hindi pinayagan ang mic. Payagan ito sa settings ng browser.': 'Mic access was blocked. Allow it in the browser settings.',
    'Walang mic na nakita sa device na ito.': 'No mic found on this device.',
    'Naka-reserve': 'Reserved', 'Naka-reserve. Magsisimula na.': 'Reserved. Starting now.',
    'Perfect! Pang-concert ang boses!': 'Perfect! A concert-ready voice!', 'Ang galing! Isa pa!': 'Amazing! One more!',
    'Ang ganda ng boses!': 'What a beautiful voice!', 'Magaling! Palakpakan natin!': 'Great job! Let\'s give a hand!', 'Pwede na! Kanta pa ulit!': 'Not bad! Sing again!',
    'Batay sa pagkanta habang may lyrics': 'Based on singing while lyrics are shown',
    'Walang mic na nakakonekta, kaya random ang score': 'No mic connected, so the score is random',
    'Error sa video file': 'Video file error', 'Error sa audio file': 'Audio file error', 'Walang media': 'No media',
    'Ayaw ipa-play ng YouTube ang video na ito sa labas ng YouTube. Lilipat sa susunod...': 'YouTube does not allow this video to play outside YouTube. Moving to the next song...',
    'Hindi ma-load ang YouTube player (internet?)': 'Could not load the YouTube player (internet?)',
    'Nawala ang koneksyon, kumokonekta ulit...': 'Connection lost, reconnecting...',
    'Isara ang host? Hihinto ang kanta.': 'Close the host? The song will stop.',
    'Gumawa ng bagong room code? Kailangang i-scan ulit ng mga phone ang bagong QR.': 'Create a new room code? Phones will need to scan the new QR code.',
    'Umuugong ang speaker. Hininaan muna ang mic. Ilayo ang phone sa speaker o hinaan ang mic sa Settings.': 'The speaker is howling. Mic volume was lowered. Move the phone away from the speaker or lower the mic in Settings.',
    'I-scan para mag-reserve': 'Scan to reserve', 'I-scan para sumali': 'Scan to join',
    'Palakpak': 'Applause', 'Drum roll': 'Drum roll', 'Busina': 'Horn', 'Ding': 'Ding',

    // ---- remote ----
    'Sumali sa kantahan': 'Join the singing', 'I-scan ang QR code na nasa TV, o i-type ang room code na nakalagay doon.': 'Scan the QR code on the TV, or type the room code shown there.',
    'I-scan ang QR code': 'Scan the QR code', 'o i-type ang room code': 'or type the room code', 'Pangalan mo': 'Your name',
    'Lalabas sa TV kapag ikaw na ang kakanta': 'Shown on the TV when it is your turn', 'Sumali': 'Join', 'Binubuksan ang camera': 'Opening the camera',
    'Isara ang scanner': 'Close scanner', 'Koneksyon': 'Connection', 'Lumipat ng room': 'Change room', 'Lumipat ng room?': 'Change room?',
    'Kinakanta ngayon': 'Now singing', 'Walang tumutugtog': 'Nothing is playing', 'Mag-reserve ng kanta para magsimula.': 'Reserve a song to start.',
    'Sound effects sa TV': 'Sound effects on the TV', 'Pangalan': 'Name', 'Ilagay ang pangalan mo': 'Enter your name',
    'Kumakanta bilang': 'Singing as', '. Palitan': '. Change',
    'Pangalan mo? (lalabas sa TV kapag ikaw na ang kakanta)': 'Your name? (shown on the TV when it is your turn)',
    'Hanapin ang title, artist o numero': 'Search title, artist or number', 'Hanapin ang kahit anong kanta': 'Search for any song',
    'Bago at sikat ngayon': 'New and popular now', 'Ipakita ang bago': 'Show new', 'O i-paste ang YouTube link:': 'Or paste a YouTube link:',
    'Hindi pa naka-set ang YouTube search. Pwede pa ring mag-paste ng YouTube link sa ibaba.': 'YouTube search is not set up yet. You can still paste a YouTube link below.',
    'Hindi pa naka-set ang YouTube search. Pwede pa ring mag-paste ng YouTube link.': 'YouTube search is not set up yet. You can still paste a YouTube link.',
    'Naubos na ang libreng YouTube search para ngayong araw. Bukas ulit, o mag-paste ng link.': 'The free YouTube searches for today are used up. Try tomorrow, or paste a link.',
    'Hindi pinapayagan ang site na ito na gumamit ng YouTube search.': 'This site is not allowed to use YouTube search.',
    'Hindi maabot ang YouTube. Subukan ulit.': 'Cannot reach YouTube. Try again.',
    'Hindi gumana ang YouTube search. Subukan ulit, o mag-paste ng link.': 'YouTube search failed. Try again, or paste a link.',
    'Hindi valid na YouTube link': 'Not a valid YouTube link',
    'Walang nakapila. Mag-reserve ng kanta sa Numero o Hanap.': 'The queue is empty. Reserve a song from Number or Search.',
    'Kinakanta': 'Singing', 'Unahin': 'Move to top', 'Inuna sa pila': 'Moved to the top', 'Alisin sa pila?': 'Remove from the queue?', 'Inalis sa pila': 'Removed from the queue',
    'Naka-reserve. Magsisimula na sa TV.': 'Reserved. Starting on the TV.', 'Walang tumutugtog. Mag-reserve muna ng kanta.': 'Nothing is playing. Reserve a song first.',
    'I-skip ang kanta?': 'Skip this song?', 'Inalis sa Paborito': 'Removed from Favorites', 'Naidagdag sa Paborito': 'Added to Favorites',
    'Wala pang Paborito. Pindutin ang bituin sa tabi ng kanta.': 'No favorites yet. Tap the star next to a song.',
    'Wala ka pang nire-reserve sa phone na ito.': 'You have not reserved anything on this phone yet.',
    'Walang nahanap sa library. Subukan sa YouTube tab.': 'Not found in the library. Try the YouTube tab.', 'Wala pang kanta dito.': 'No songs here yet.',
    'Ilagay ang 4 na letra o numero na room code na nasa TV': 'Enter the 4-character room code shown on the TV',
    'Walang access sa camera ang browser na ito. I-type na lang ang room code.': 'This browser cannot use the camera. Type the room code instead.',
    'Hindi pinayagan ang camera. Payagan ito sa settings ng browser, o i-type ang room code.': 'Camera access was blocked. Allow it in the browser settings, or type the room code.',
    'Hindi mabuksan ang camera. I-type na lang ang room code.': 'Could not open the camera. Type the room code instead.',
    'Itapat ang camera sa QR code na nasa TV': 'Point the camera at the QR code on the TV',
    'Hindi ito ang QR code ng videoke. Itapat sa QR na nasa TV.': 'This is not the videoke QR code. Point at the QR code on the TV.',
    // mic sa phone
    'Gawing mic ang phone na ito': 'Use this phone as a mic', 'Gawing mic ang phone': 'Use the phone as a mic',
    'Maririnig ang boses mo sa speaker ng TV.': 'Your voice will play on the TV speaker.', 'Itigil ang mic': 'Stop the mic',
    'I-mute': 'Mute', 'I-unmute': 'Unmute', 'Linisin ang boses': 'Clean up the voice',
    'Binabawasan ang ingay sa paligid at ang echo. I-off kung gusto mo ng mas natural na tunog.': 'Reduces background noise and echo. Turn off for a more natural sound.',
    'Hawakan ang phone malapit sa bibig, parang tunay na mic.': 'Hold the phone close to your mouth, like a real mic.',
    'Ilayo sa speaker ng TV. Kapag umugong, hinaan ang mic o ang TV.': 'Keep away from the TV speaker. If it howls, lower the mic or the TV.',
    'Dapat nasa iisang Wi-Fi ang phone at ang laptop o TV.': 'The phone and the laptop or TV must be on the same Wi-Fi.',
    'Huwag i-lock ang phone habang kumakanta, para hindi maputol ang mic.': 'Do not lock the phone while singing, so the mic does not cut off.',
    'Kung may earphones na may mic, iyon ang gagamitin.': 'If you have earphones with a mic, that mic will be used.',
    'May kaunting delay (mga 0.1 hanggang 0.3 segundo) dahil dumadaan sa Wi-Fi.': 'There is a small delay (about 0.1 to 0.3 seconds) because audio goes over Wi-Fi.',
    'Humihingi ng pahintulot': 'Asking for permission', 'Pindutin ang "Allow" para magamit ang mic.': 'Tap "Allow" to use the mic.',
    'Kumokonekta sa TV': 'Connecting to the TV', 'Sandali lang.': 'Just a moment.', 'Naka-on ang mic': 'Mic is on',
    'Kumanta na! Naririnig ka na sa TV.': 'Start singing! The TV can hear you.', 'Kumokonekta ulit': 'Reconnecting',
    'Naputol ang koneksyon. Sinusubukan ulit.': 'Connection dropped. Trying again.', 'Hindi sumasagot ang TV': 'The TV is not answering',
    'Buksan ang Host sa laptop at pindutin ang Simulan. Dapat nasa iisang Wi-Fi.': 'Open the Host on the laptop and press Start. Both must be on the same Wi-Fi.',
    'Hindi pinayagan ang mic': 'Mic access blocked', 'Payagan ang microphone sa settings ng browser, tapos subukan ulit.': 'Allow the microphone in the browser settings, then try again.',
    'Walang nakitang mic': 'No mic found', 'Hindi makita ang microphone ng phone na ito.': "This phone's microphone could not be found.",
    'Hindi kaya ng browser na ito': 'This browser cannot do this', 'Gamitin ang Chrome o Safari na bago.': 'Use an up-to-date Chrome or Safari.',
    'May error sa mic': 'Mic error', 'Subukan ulit.': 'Try again.', 'Naka-mute ang mic': 'Mic is muted',

    // ---- songbook ----
    'Numero ': 'Number ', 'Video': 'Video', 'Audio': 'Audio',
    'Mag-upload sa': 'Upload in', '. Kusang nabibigyan ng song number ang bawat kanta.': '. Each song gets a song number automatically.',

    // ---- settings ----
    'Naka-save ang settings sa device na ito. Ang settings ng TV (lyrics, score, background) ay i-set sa laptop o tablet na naka-konekta sa TV.': 'Settings are saved on this device. Set the TV settings (lyrics, score, background) on the laptop or tablet connected to the TV.',
    'Itsura': 'Appearance', 'Lyrics': 'Lyrics', 'TV screen': 'TV screen', 'Tunog': 'Sound', 'Screensaver': 'Screensaver', 'Data': 'Data',
    'Wika': 'Language', 'Wika ng app': 'App language', 'Ang wikang gagamitin sa lahat ng screen ng app sa device na ito.': 'The language used on every screen of the app on this device.',
    'Tema': 'Theme', 'Ang Auto ay sumusunod sa setting ng device mo.': 'Auto follows your device setting.', 'Dark': 'Dark', 'Light': 'Light', 'Auto': 'Auto',
    'Wallpaper': 'Wallpaper', 'Sariling larawan bilang background ng app sa device na ito. Hindi ito ina-upload kahit saan.': 'Your own picture as the app background on this device. It is never uploaded.',
    'Walang wallpaper': 'No wallpaper', 'Pumili ng larawan': 'Choose a picture', 'Dilim ng wallpaper': 'Wallpaper darkness',
    'Mas madilim, mas madaling basahin ang mga letra.': 'Darker makes text easier to read.', 'Maliwanag': 'Bright', 'Madilim': 'Dark',
    'Transparency ng mga box': 'Box transparency', 'Gawing see-through ang mga box at button para makita ang wallpaper o background.': 'Make boxes and buttons see-through to show the wallpaper or background.',
    'Buo': 'Solid', 'See-through': 'See-through', 'Buo (walang transparency)': 'Solid (no transparency)',
    'Blur sa likod ng box': 'Blur behind boxes', 'Pinalalabo ang nasa likod para mabasa pa rin ang laman ng box.': 'Blurs what is behind so box content stays readable.',
    'Naitakda ang wallpaper': 'Wallpaper set', 'Hindi magamit ang larawang ito. Pumili ng iba.': 'This picture cannot be used. Choose another.', 'Inalis ang wallpaper': 'Wallpaper removed',
    'Lyrics sa TV': 'Lyrics on the TV', 'Ito ang linyang kinakanta': 'This is the line being sung', 'Ito ang susunod na linya': 'This is the next line',
    'Pangatlong linya': 'Third line', 'Pang-apat na linya': 'Fourth line', 'Laki': 'Size', 'Maliit': 'Small', 'Katamtaman': 'Medium', 'Malaki': 'Large', 'Pinakamalaki': 'Extra large',
    'Posisyon': 'Position', 'Gitna': 'Center', 'Kaliwa': 'Left', 'Ilang linya ang kita': 'Lines shown', 'Ang 4 na linya ay mas madaling basahin nang maaga.': 'Four lines make it easier to read ahead.',
    '2 linya': '2 lines', '4 na linya': '4 lines', 'Kulay ng highlight': 'Highlight color', 'Ang kulay ng bahaging nakanta na.': 'The color of the part already sung.',
    'Ibang kulay bawat kanta': 'Different color per song', 'Pipili ng random na kulay ng highlight sa bawat kanta.': 'Picks a random highlight color for each song.',
    'Outline ng letra': 'Text outline', 'Manipis': 'Thin', 'Makapal': 'Thick', 'Anino ng letra': 'Text shadow', 'Para mabasa kahit maliwanag ang background.': 'Keeps text readable on bright backgrounds.',
    'Countdown bago kumanta': 'Countdown before singing', 'Lalabas din ang "Interlude" kapag mahaba ang tugtog na walang kanta.': '"Interlude" also appears during long instrumental parts.',
    'Tuldok': 'Dots', 'Timing ng lyrics': 'Lyric timing', 'Kung nauuna o nahuhuli ang lyrics sa kanta sa TV mo, iusog dito.': 'If the lyrics run ahead of or behind the song on your TV, adjust here.',
    'Mas huli': 'Later', 'Mas maaga': 'Earlier', 'Walang adjust': 'No adjustment',
    'Score pagkatapos ng kanta': 'Score after each song',
    'Ang "Mula sa mic" ay batay sa kung kumakanta ka habang may lyrics; hindi pa nito sinusukat ang tono. Kapag walang mic, random ang score.': '"From mic" is based on whether you sing while lyrics are shown; it does not measure pitch yet. Without a mic the score is random.',
    'Mula sa mic': 'From mic', 'Random': 'Random', 'Hirap': 'Difficulty', 'Sa Madali, laging mataas ang score. Sa Mahirap, bihira ang 90 pataas.': 'Easy gives high scores. On Hard, 90 and above is rare.',
    'Madali': 'Easy', 'Mahirap': 'Hard', 'Palakpak at fanfare': 'Applause and fanfare', 'Mahina': 'Soft', 'Malakas': 'Loud',
    'Teksto kapag walang kanta': 'Text when no song is playing', 'Lalabas sa itaas ng orasan habang naghihintay ng kanta.': 'Shown above the clock while waiting for a song.',
    'Tumatakbong teksto sa ibaba': 'Scrolling text at the bottom', 'Pang-okasyon, hal. "Happy birthday, Lola!" Iwanang blangko kung ayaw.': 'For occasions, e.g. "Happy birthday, Grandma!" Leave empty to turn off.',
    'Kulay ng tumatakbong teksto': 'Scrolling text color', 'Background sa TV': 'Background on the TV', 'Uri ng background': 'Background type',
    'Ang "Video" ay ang mga video loop na in-upload mo sa Library. Ang "Wallpaper" ay ang larawang pinili mo sa Itsura, dahan-dahang gumagalaw.': '"Video" uses the video loops you uploaded to the Library. "Wallpaper" uses the picture you chose under Appearance, moving slowly.',
    'Animated': 'Animated', 'Plain': 'Plain', 'Animated na eksena': 'Animated scene', 'Palit-palit': 'Rotate',
    'Lungsod sa gabi': 'City at night', 'Dagat sa paglubog ng araw': 'Sunset sea', 'Langit na may bituin': 'Starry sky',
    'Palitan bawat kanta': 'Change with every song', 'Kapag naka-off, iisang background lang hanggang palitan mo.': 'When off, the same background stays until you change it.',
    'Mic (phone bilang mic)': 'Mic (phone as mic)', 'Lakas ng mic': 'Mic volume', 'Para sa lahat ng phone na naka-mic. Hinaan kapag umuugong ang speaker.': 'For all phones used as mics. Lower it if the speaker howls.',
    'Echo': 'Echo', 'Ang paulit-ulit na tunog ng boses, gaya ng sa videoke machine.': 'The repeating voice effect, like on a videoke machine.', 'Marami': 'More',
    'Reverb': 'Reverb', 'Parang kumakanta sa malaking kwarto.': 'Like singing in a big room.', 'Linaw ng boses': 'Voice clarity',
    'Pinapalakas ang bahagi ng boses na nagpapalinaw ng salita.': 'Boosts the part of the voice that makes words clear.', 'Natural': 'Natural', 'Malinaw': 'Clear',
    'Para gamitin: buksan ang Remote sa phone, pumunta sa Mic tab, at pindutin ang malaking mic button. I-set ang mga ito sa laptop o tablet na naka-TV.': 'To use: open the Remote on the phone, go to the Mic tab and press the big mic button. Set these on the laptop or tablet connected to the TV.',
    'Sound effects mula sa phone': 'Sound effects from phones', 'Palakpak, drum roll, busina at ding na pinipindot sa Remote.': 'Applause, drum roll, horn and other sounds pressed on the Remote.',
    'Lakas ng sound effects at palakpak': 'Sound effects and applause volume', 'Beep sa pag-type ng numero': 'Beep when typing numbers', 'Sa phone na ginagamit bilang remote.': 'On the phone used as a remote.',
    'Gamitin ang screensaver': 'Use the screensaver', 'Orasan sa phone at ibang page kapag walang gumagamit. Sa TV, laging may orasan at background kapag walang kanta.': 'A clock on phones and other pages when idle. The TV always shows a clock and background when no song is playing.',
    'Lalabas pagkatapos ng': 'Show after', '1 minuto': '1 minute', '2 minuto': '2 minutes', '5 minuto': '5 minutes', '10 minuto': '10 minutes', '15 minuto': '15 minutes', '30 minuto': '30 minutes',
    'Format ng oras': 'Time format', '12-oras': '12-hour', '24-oras': '24-hour', 'Ipakita ang segundo': 'Show seconds', 'Ipakita ang petsa': 'Show date', 'Subukan ang screensaver': 'Try the screensaver',
    'Data at backup': 'Data and backup', 'I-export ang listahan ng kanta': 'Export the song list', 'CSV file na nabubuksan sa Excel at Google Sheets.': 'A CSV file that opens in Excel and Google Sheets.',
    'I-backup ang settings': 'Back up settings', 'Kasama ang Paborito, Kamakailan at pangalan mo sa device na ito.': 'Includes Favorites, Recent and your name on this device.',
    'I-restore mula sa backup': 'Restore from backup', 'Pumili ng .json na backup file.': 'Choose a .json backup file.', 'Ibalik sa default': 'Reset to defaults',
    'Ibabalik ang lahat ng settings sa simula. Hindi kasama ang Paborito.': 'Returns all settings to their defaults. Favorites are kept.',
    'Tungkol sa app': 'About the app', 'Bersyon 0.9.8. Pambahay na videoke para sa TV, laptop, tablet at phone.': 'Version 0.9.8. A home videoke for TV, laptop, tablet and phone.', 'Mga demo song': 'Demo songs',
    'Ang 10001 hanggang 10010 ay public domain na kanta na may original na arrangement.': 'Songs 10001 to 10010 are public domain songs with original arrangements.',
    '© 2026 VideokeTana. Lahat ng karapatan ay nakalaan.': '© 2026 VideokeTana. All rights reserved.',
    'Hal. Maligayang kaarawan, Lola Nena!': 'E.g. Happy birthday, Grandma Nena!',
    'Na-download ang backup': 'Backup downloaded', 'Na-restore ang settings': 'Settings restored', 'Hindi ito valid na backup file ng Videoke.': 'This is not a valid Videoke backup file.',
    'Ibalik sa default ang lahat ng settings sa device na ito?': 'Reset all settings on this device to defaults?', 'Naibalik sa default': 'Reset to defaults',
    'Lakas': 'Volume', 'Isa-isa lang': 'One at a time', 'Stop': 'Stop', 'Tawa': 'Laugh', 'Galit': 'Angry', 'Ang galing': 'Amazing', 'Cheers': 'Cheers',
    'Sa soundboard, isang tunog lang sa isang pagkakataon.': 'On the soundboard, only one sound at a time.',
    'Ang soundboard sa Remote: palakpak, cheers, wow, boo, tawa at iba pa.': 'The soundboard on the Remote: applause, cheers, wow, boo, laugh and more.',
    'Pitch bar': 'Pitch bar', 'On': 'On', 'Off': 'Off',
    'Gabay sa tono sa itaas ng lyrics: ang mga guhit ay ang tamang nota, ang bilog ay ang boses mo mula sa mic. Lalabas lang sa kantang may melody, gaya ng mga demo song at ng MIDI sa susunod. Kapag naka-on at may mic, kasama na ang tono sa score.': 'A pitch guide above the lyrics: the bars are the correct notes and the dot is your voice from the mic. It only shows for songs with melody data, like the demo songs and MIDI later. When on with a mic, pitch counts toward the score.',
    'Ang "Mula sa mic" ay batay sa kung kumakanta ka habang may lyrics. Sa kantang may pitch bar, sinusukat na rin ang tono. Kapag walang mic, random ang score.': '"From mic" is based on whether you sing while lyrics are shown. On songs with a pitch bar, pitch is measured too. Without a mic the score is random.',
    'Batay sa tono at pagkanta habang may lyrics': 'Based on pitch and singing while lyrics are shown',
    'Title, artist, numero o YouTube link': 'Title, artist, number or YouTube link', 'Title, artist o i-paste ang YouTube link': 'Title, artist or paste a YouTube link', 'YT': 'YT',
    'Gawing landscape': 'Switch to landscape', 'Mas malaki ang lyrics at pitch bar kapag nakahiga ang phone o tablet.': 'Lyrics and the pitch bar are bigger when the phone or tablet is sideways.',
    'Hayaang nakatayo': 'Keep it upright', 'Auto na ang screen': 'Screen is on auto', 'Landscape (L)': 'Landscape (L)', 'Landscape': 'Landscape',
    'Screen kapag phone o tablet ang TV': 'Screen when a phone or tablet is the TV',
    'Sa "Landscape", kusang nakahiga ang screen pagpindot ng Simulan. Kapag hindi kaya ng browser (hal. iPhone), may paalala na i-rotate ang phone.': 'With "Landscape", the screen turns sideways when you press Start. If the browser cannot do it (e.g. iPhone), you will be reminded to rotate the phone.',
    'Bago sa library': 'New in the library', 'Sumali sa room': 'Join a room',
    'Sa laptop, tablet o phone na tutugtog ng kanta. Pwede ring dito na mismo kumanta kahit iisang device lang.': 'On the laptop, tablet or phone that will play the songs. You can also sing right here on a single device.',
    'I-type ang room code na nasa TV para gawing remote at mic ang phone.': 'Type the room code shown on the TV to use your phone as a remote and mic.',
    'Paano gamitin': 'How to use', 'I-scan ang QR': 'Scan the QR', 'I-type ang numero': 'Type the number',
    'Sa device na nakakonekta sa TV, pindutin ang Simulan.': 'On the device connected to the TV, press Start.',
    'Sa phone, i-scan ang QR o i-type ang room code.': 'On your phone, scan the QR or type the room code.',
    'Hanapin ang kanta sa Songbook, i-type ang numero, Reserve.': 'Find a song in the Songbook, type its number, Reserve.',
    'Lahat ng kanta at numero': 'All songs and numbers', 'Magdagdag ng kanta': 'Add songs', 'Wika, tema, lyrics, tunog': 'Language, theme, lyrics, sound',
    'VideokeTana 0.10, pambahay na videoke': 'VideokeTana 0.10, a home videoke',
    'QR code ng room': 'Room QR code', 'I-scan para sumali. Pindutin para isara.': 'Scan to join. Tap to close.',
    'Tunog ng kanta at mic': 'Song sound and mic', 'Bass ng kanta': 'Song bass', 'Treble ng kanta': 'Song treble',
    'Para sa sariling kanta (MP3/MP4) lang. Hindi mababago ang tunog ng YouTube na kanta.': 'For your own songs (MP3/MP4) only. YouTube songs cannot be changed.',
    'Mas malinaw na tunog sa itaas, gaya ng cymbals at boses ng kanta.': 'Brighter high sounds, like cymbals and the singer\'s voice.',
    'Boses sa TV': 'Voice on the TV', 'Kanta': 'Song', 'Linaw': 'Clarity', 'Bass': 'Bass', 'Treble': 'Treble',
    'Hindi mababago ang bass at treble ng YouTube na kanta. Sa sariling kanta lang ito gumagana.': 'Bass and treble cannot be changed for YouTube songs. They only work on your own songs.',
    'May ibang TV na gumagamit ng room na ito': 'Another TV is using this room', 'Gamitin dito': 'Use it here', 'Naipit': 'Stuck',
    'Binuksan ang room na ito sa ibang tab o device, kaya doon na tutugtog ang mga kanta. Isara ang tab na ito, o gamitin ulit dito.': 'This room was opened in another tab or device, so songs will play there. Close this tab, or use it here again.',
    'VideokeTana app': 'VideokeTana app', 'Sariling app para sa phone, tablet at Smart TV. Walang address bar, buong screen.': 'Our own app for phone, tablet and Smart TV. No address bar, full screen.',
    'I-download': 'Download', 'I-install': 'Install', 'Sa Safari: pindutin ang Share, tapos "Add to Home Screen".': 'In Safari: tap Share, then "Add to Home Screen".',
    'Kanta sa device': 'Songs on this device', 'Sariling file, hindi ina-upload': 'Your own files, never uploaded', 'Gamit na storage ng mga kanta': 'Storage used by songs',
    'Magdagdag ng kanta mula sa device': 'Add songs from this device',
    'MP3, M4A, MP4 o WebM, at .lrc para sa lyrics na kapareho ang pangalan ng file. Hindi ina-upload: dito lang sa device na ito naka-save, at dito lang ito tutugtog.': 'MP3, M4A, MP4 or WebM, plus an .lrc with the same file name for lyrics. Nothing is uploaded: songs are saved and played only on this device.',
    'Hindi kusang mabubura': 'Will not be cleared automatically', 'Pwedeng burahin ng browser kapag puno ang device': 'The browser may clear it if the device runs out of space',
    'Sine-save sa device...': 'Saving to this device...', 'Wala pang kanta sa device na ito.': 'No songs on this device yet.', 'Puno na ang storage ng device': 'The device storage is full',
    'Walang kantang naidagdag (MP3, M4A, MP4 o WebM lang)': 'No songs added (MP3, M4A, MP4 or WebM only)', 'Hindi magamit ang storage ng browser na ito.': 'This browser storage cannot be used.',
    'Title ng kanta': 'Song title', 'I-edit': 'Edit', 'Kanta sa device na ito': 'Song on this device',
    'Boses kapag pumindot': 'Voice when tapping', 'Binabasa nang malakas ang numerong pinipindot at ang mga aksyon, gaya ng "Naka-reserve".': 'Reads out the numbers you press and actions like "Reserved".',
    'Soundboard': 'Soundboard', 'Hindi magpapatong-patong': 'No overlap', 'Isang tunog lang sa isang pagkakataon.': 'Only one sound at a time.',

    // ---- admin ----
    'Mag-login': 'Log in', 'Para mag-upload at mag-ayos ng mga kanta sa library.': 'To upload and organize songs in the library.', 'Login': 'Log in',
    'Gumawa ng account': 'Create account', 'Email': 'Email', 'Password': 'Password', 'Hindi bababa sa 6 na character': 'At least 6 characters',
    'Ulitin ang password': 'Repeat password', 'Nakalimutan ang password?': 'Forgot password?', 'Bumalik sa login': 'Back to log in',
    'I-check ang email mo': 'Check your email', 'Hindi kailangan ng account para kumanta. Buksan lang ang Remote sa phone.': 'You do not need an account to sing. Just open the Remote on a phone.',
    'Walang permiso ang account na ito na mag-upload.': 'This account is not allowed to upload.', 'Ang admin account lang ang pwedeng magdagdag o magbura ng kanta.': 'Only the admin account can add or delete songs.',
    'Magdagdag ng kanta': 'Add songs', 'Pumili ng mga file': 'Choose files',
    'o i-drag dito. Video na MP4 o WebM, o audio na MP3 o M4A kasama ang .lrc para sa lyrics. Hanggang': 'or drag here. MP4 or WebM video, or MP3 or M4A audio with an .lrc file for lyrics. Up to',
    'MB bawat file.': 'MB per file.', 'Pangalanan ang file na': 'Name the file', 'para kusang mapunan. Ang .lrc na kapareho ang pangalan ay kusang naikakabit.': 'to fill in details automatically. An .lrc with the same name is attached automatically.',
    'Alisin lahat': 'Remove all', 'I-upload lahat': 'Upload all', 'Magdagdag mula sa YouTube': 'Add from YouTube',
    'Para sa mga bago at OPM na kanta. Naka-embed lang ang video mula sa YouTube at hindi dina-download. Bawat idadagdag ay may sariling song number.': 'For new and OPM songs. The YouTube video is only embedded, never downloaded. Each added song gets its own song number.',
    'Ipakita ang bago at sikat': 'Show new and popular', 'Background videos': 'Background videos', 'Mag-upload ng video': 'Upload video',
    'Mga video loop na lalabas sa likod ng lyrics kapag "Sariling video" ang background sa Settings. Gumamit ng sariling kuha o video na may lisensyang pwedeng gamitin. MP4 o WebM, hanggang 50MB.': 'Video loops shown behind the lyrics when the background is set to "Video" in Settings. Use your own footage or licensed video. MP4 or WebM, up to 50MB.',
    'Mga kanta': 'Songs', 'Palitan ang password': 'Change password', 'Bagong password': 'New password', 'Ulitin ang bagong password': 'Repeat new password',
    'I-edit ang kanta': 'Edit song', 'ikaw@email.com': 'you@email.com', 'Ang password mo': 'Your password', 'Ipakita ang password': 'Show password', 'Ipakita': 'Show',
    'I-type ulit': 'Type again', 'Logout': 'Log out', 'Title o artist ng kanta': 'Song title or artist', 'Gamit na storage': 'Storage used',
    'Gamit ang email at password na madali mong maaalala.': 'Use an email and a password you can remember.', 'I-reset ang password': 'Reset password',
    'Ilagay ang email mo. Padadalhan ka ng link para gumawa ng bagong password.': 'Enter your email. You will get a link to create a new password.', 'Ipadala ang link': 'Send link',
    'Gumawa ng bagong password para sa account mo.': 'Create a new password for your account.', 'I-save ang password': 'Save password',
    'Mali ang email o password. Subukan ulit.': 'Wrong email or password. Try again.', 'Hindi pa naka-confirm ang email. I-check ang inbox (pati Spam) para sa confirmation link.': 'The email is not confirmed yet. Check your inbox (and Spam) for the confirmation link.',
    'May account na ang email na ito — mag-login na lang.': 'This email already has an account. Log in instead.', 'Masyadong maraming subok. Maghintay ng ilang minuto bago ulitin.': 'Too many attempts. Wait a few minutes before trying again.',
    'Masyadong maikli o mahina ang password (min 6 na character).': 'The password is too short or weak (min 6 characters).', 'Hindi valid ang email address.': 'Not a valid email address.',
    'Naka-off ang pag-sign up sa ngayon.': 'Sign-ups are turned off for now.', 'Walang internet o hindi maabot ang server. Subukan ulit.': 'No internet or the server cannot be reached. Try again.',
    'Kailangang iba ang bagong password sa luma.': 'The new password must be different from the old one.', 'Pwede na': 'Okay', 'Napakalakas': 'Very strong',
    'Ilagay ang tamang email address.': 'Enter a valid email address.', 'Ilagay ang password.': 'Enter the password.', 'Kailangang 6 na character o higit pa ang password.': 'The password must be at least 6 characters.',
    'Hindi magkapareho ang dalawang password.': 'The two passwords do not match.', 'Naka-login ka na': 'You are logged in', 'Nagawa na ang account': 'Account created',
    'Naipadala na!': 'Sent!', 'Napalitan ang password': 'Password changed', 'Kailangang 6 na character o higit pa.': 'Must be at least 6 characters.',
    'I-edit ang title at artist': 'Edit title and artist', 'Wala pang kanta. Magdagdag sa itaas.': 'No songs yet. Add some above.', 'Wala pang background video.': 'No background videos yet.',
    'Idagdag sa library': 'Add to library', 'Idagdag mula sa YouTube': 'Add from YouTube', 'Walang kategorya': 'No category', 'Ilagay ang title ng kanta.': 'Enter the song title.',
    'May lyrics': 'Has lyrics', 'Walang lyrics': 'No lyrics', 'Public domain': 'Public domain', 'Handa': 'Ready', 'Ina-upload...': 'Uploading...', 'Walang i-a-upload': 'Nothing to upload',
    'walang .lrc': 'no .lrc', 'Na-save ang lyrics': 'Lyrics saved', 'Expired o hindi na valid ang link. Humingi ng bago.': 'The link expired or is no longer valid. Request a new one.',
    'Iba pa': 'Other', 'Pasko': 'Christmas', 'Pambata': 'Kids', 'Gospel': 'Gospel', 'Folk': 'Folk', 'Classic': 'Classic', 'Love song': 'Love song', 'Rock': 'Rock', 'Pop': 'Pop', 'OPM': 'OPM'
  };

  const DAYS = { Linggo: 'Sunday', Lunes: 'Monday', Martes: 'Tuesday', Miyerkules: 'Wednesday', Huwebes: 'Thursday', Biyernes: 'Friday', Sabado: 'Saturday' };
  const MONTHS = { Enero: 'January', Pebrero: 'February', Marso: 'March', Abril: 'April', Mayo: 'May', Hunyo: 'June', Hulyo: 'July', Agosto: 'August', Setyembre: 'September', Oktubre: 'October', Nobyembre: 'November', Disyembre: 'December' };

  // mga tekstong may numero o pangalan sa loob
  const P = [
    [/^(Linggo|Lunes|Martes|Miyerkules|Huwebes|Biyernes|Sabado), (\w+) (\d+)$/, (m, d, mo, n) => `${DAYS[d]}, ${MONTHS[mo] || mo} ${n}`],
    [/^Naka-reserve\. Pang-(\d+) ka sa pila\.$/, (m, n) => `Reserved. You are number ${n} in the queue.`],
    [/^Naka-reserve sa room (\w+)$/, (m, r) => `Reserved in room ${r}`],
    [/^Nakasali sa room (\w+)$/, (m, r) => `Joined room ${r}`],
    [/^Naka-pila na, pero hindi pa makita ang TV sa room (\w+)\.$/, (m, r) => `Queued, but the TV in room ${r} cannot be found yet.`],
    [/^Hindi makita ang TV sa room (\w+)\. Buksan ang Host at pindutin ang Simulan\.$/, (m, r) => `Cannot find the TV in room ${r}. Open the Host and press Start.`],
    [/^Hindi makita ang TV sa room (\w+)\.$/, (m, r) => `Cannot find the TV in room ${r}.`],
    [/^Hindi pa makita ang TV sa room (\w+), pero susubukan pa rin\.$/, (m, r) => `The TV in room ${r} is not visible yet, but trying anyway.`],
    [/^Hinahanap ang TV sa room (\w+)\. Siguraduhing bukas ang Host at napindot ang Simulan\.$/, (m, r) => `Looking for the TV in room ${r}. Make sure the Host is open and Start was pressed.`],
    [/^Nakakonekta sa room$/, () => 'Connected to room'],
    [/^\. Pindutin ang Reserve para ipila ang kanta\.$/, () => '. Press Reserve to queue a song.'],
    [/^Resulta para sa "(.*)"$/, (m, q) => `Results for "${q}"`],
    [/^(\d+) kanta$/, (m, n) => `${n} song${n === '1' ? '' : 's'}`],
    [/^(\d+) na kanta$/, (m, n) => `${n} songs`],
    [/^(\d+) sa (\d+) kanta$/, (m, a, b) => `${a} of ${b} songs`],
    [/^(\d+) kanta, (.+) sa 1GB$/, (m, n, s) => `${n} songs, ${s} of 1GB`],
    [/^(\d+) video, (.+)$/, (m, n, s) => `${n} video${n === '1' ? '' : 's'}, ${s}`],
    [/^Na-export ang (\d+) kanta$/, (m, n) => `Exported ${n} songs`],
    [/^Score ni (.+): (\d+)$/, (m, w, n) => `${w}'s score: ${n}`],
    [/^Kumakanta: (.+)$/, (m, w) => `Singing: ${w}`],
    [/^(.*), Kumakanta: (.+)$/, (m, a, w) => `${a}, singing: ${w}`],
    [/^Kakanta: (.+)$/, (m, w) => `Singer: ${w}`],
    [/^(Palakpak|Drum roll|Busina|Ding|Cheers|Boo|Wow|OMG|Boom|Tawa|Aww|Galit|Wow naman|Ang galing|FAAH!)( mula kay (.+))?$/, (m, s, x, w) => `${SFX_EN[s] || s}${w ? ' from ' + w : ''}`],
    [/^I-edit ang (\d+)$/, (m, n) => `Edit ${n}`],
    [/^Naidagdag\. Song no\. (\d+)$/, (m, n) => `Added. Song no. ${n}`],
    [/^Na-upload, song no\. (\d+)$/, (m, n) => `Uploaded, song no. ${n}`],
    [/^(\d+)\/(\d+) na-upload$/, (m, a, b) => `${a}/${b} uploaded`],
    [/^Ina-upload ang (.+)$/, (m, f) => `Uploading ${f}`],
    [/^(.+): lagpas (\d+)MB$/, (m, f, n) => `${f}: over ${n}MB`],
    [/^Lagpas (\d+)MB — i-compress muna$/, (m, n) => `Over ${n}MB. Compress it first.`],
    [/^(\d+) file ang hindi suportado at nilaktawan$/, (m, n) => `${n} unsupported file(s) skipped`],
    [/^Kulang pa ng (\d+) character$/, (m, n) => `${n} more character(s) needed`],
    [/^([\d.]+) segundo (mas maaga|mas huli)$/, (m, n, w) => `${n} seconds ${w === 'mas maaga' ? 'earlier' : 'later'}`],
    [/^Nasa library, (\d+)$/, (m, n) => `In library, ${n}`],
    [/^Burahin ang #(\d+) "(.+)"\? Hindi na ito maibabalik\.$/, (m, n, t) => `Delete #${n} "${t}"? This cannot be undone.`],
    [/^Burahin ang background na "(.+)"\?$/, (m, t) => `Delete the background "${t}"?`],
    [/^Hindi na-reserve: (.+)$/, (m, e) => `Could not reserve: ${e}`],
    [/^Hindi ma-play: (.+)$/, (m, e) => `Cannot play: ${e}`],
    [/^Hindi ma-export: (.+)$/, (m, e) => `Could not export: ${e}`],
    [/^Hindi naalis sa pila: (.+)$/, (m, e) => `Could not remove from the queue: ${e}`],
    [/^Naidagdag ang (\d+) kanta$/, (m, n) => `Added ${n} song${n === '1' ? '' : 's'}`],
    [/^Libre pa: (.+)$/, (m, x) => `Free: ${x}`],
    [/^Wala sa device na ito ang kantang (\d+)$/, (m, n) => `Song ${n} is not on this device`],
    [/^Hindi na-save: (.+)$/, (m, e) => `Could not save: ${e}`],
    [/^(.*), may lyrics, (.+)$/, (m, a, b) => `${a}, with lyrics, ${b}`],
    [/^Error sa pila: (.+)$/, (m, e) => `Queue error: ${e}`],
    [/^Intro, (\d+)$/, (m, n) => `Intro, ${n}`],
    [/^(\d+)% see-through$/, (m, n) => `${n}% see-through`],
    [/^Volume (\d+)$/, (m, n) => `Volume ${n}`],
    [/^Lumipat ng room\?$/, () => 'Change room?'],
    [/^(\d+) minuto$/, (m, n) => `${n} minute${n === '1' ? '' : 's'}`]
  ];
  const SFX_EN = { Palakpak: 'Applause', Busina: 'Horn', Tawa: 'Laugh', Galit: 'Angry', 'Ang galing': 'Amazing' };

  function getLang() {
    try { return (JSON.parse(localStorage.getItem('vk_prefs') || '{}').lang) || 'fil'; } catch (e) { return 'fil'; }
  }
  const LANG = getLang();
  document.documentElement.lang = LANG === 'en' ? 'en' : 'fil';
  document.documentElement.setAttribute('translate', 'no');   // huwag isalin ng Chrome

  function tr(s) {
    if (LANG !== 'en' || !s) return s;
    const m = /^(\s*)([\s\S]*?)(\s*)$/.exec(s);
    const core = m[2];
    if (!core || !/[A-Za-z]/.test(core)) return s;
    let out = D[core];
    if (out === undefined) {
      for (const [re, fn] of P) { const r = core.match(re); if (r) { out = fn(...r); break; } }
    }
    return out === undefined ? s : m[1] + out + m[3];
  }
  window.vkT = tr;
  window.vkLang = LANG;
  if (LANG !== 'en') return;

  const ATTRS = ['placeholder', 'title', 'aria-label', 'data-hint'];
  const skip = el => !el || el.closest('[data-notr], script, style, textarea');
  function walk(root) {
    if (root.nodeType === 3) { if (!skip(root.parentElement)) { const t = tr(root.data); if (t !== root.data) root.data = t; } return; }
    if (root.nodeType !== 1 || skip(root)) return;
    ATTRS.forEach(a => { if (root.hasAttribute(a)) { const v = root.getAttribute(a), t = tr(v); if (t !== v) root.setAttribute(a, t); } });
    const tw = document.createTreeWalker(root, NodeFilter.SHOW_TEXT | NodeFilter.SHOW_ELEMENT);
    let n;
    while ((n = tw.nextNode())) {
      if (n.nodeType === 3) { if (!skip(n.parentElement)) { const t = tr(n.data); if (t !== n.data) n.data = t; } }
      else ATTRS.forEach(a => { if (n.hasAttribute(a) && !skip(n)) { const v = n.getAttribute(a), t = tr(v); if (t !== v) n.setAttribute(a, t); } });
    }
  }
  const mo = new MutationObserver(list => {
    for (const r of list) {
      if (r.type === 'childList') r.addedNodes.forEach(walk);
      else if (r.type === 'characterData') { const t = tr(r.target.data); if (t !== r.target.data && !skip(r.target.parentElement)) r.target.data = t; }
      else if (r.type === 'attributes' && !skip(r.target)) { const v = r.target.getAttribute(r.attributeName), t = tr(v); if (v && t !== v) r.target.setAttribute(r.attributeName, t); }
    }
  });
  document.addEventListener('DOMContentLoaded', () => {
    document.title = tr(document.title);
    walk(document.body);
    mo.observe(document.body, { childList: true, subtree: true, characterData: true, attributes: true, attributeFilter: ATTRS });
  });
  // mga dialog box
  const oc = window.confirm.bind(window), op = window.prompt.bind(window);
  window.confirm = msg => oc(tr(msg));
  window.prompt = (msg, def) => op(tr(msg), def);
})();
