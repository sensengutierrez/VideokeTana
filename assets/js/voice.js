// ===== Boses kapag pumindot (Settings > Tunog > Boses kapag pumindot) =====
// Gumagamit ng boses ng device (speechSynthesis). Walang audio file.
(function () {
  const FIL = ['zero', 'isa', 'dalawa', 'tatlo', 'apat', 'lima', 'anim', 'pito', 'walo', 'siyam'];
  const EN = ['zero', 'one', 'two', 'three', 'four', 'five', 'six', 'seven', 'eight', 'nine'];
  const WORD = {
    fil: { clr: 'Bura lahat', del: 'Bura', notfound: 'Walang kanta', reserved: 'Naka-reserve' },
    en: { clr: 'Clear', del: 'Delete', notfound: 'No song', reserved: 'Reserved' }
  };
  const ok = () => !!window.speechSynthesis && VK_PREFS.get().ttsVoice;
  function voiceFor() {
    const vs = speechSynthesis.getVoices();
    if ((window.vkLang || 'fil') === 'en') return { v: vs.find(x => /^en/i.test(x.lang)), fil: false };
    const f = vs.find(x => /^(fil|tl)/i.test(x.lang));
    return f ? { v: f, fil: true } : { v: vs.find(x => /^en/i.test(x.lang)), fil: false };
  }
  function speak(text) {
    if (!ok() || !text) return;
    speechSynthesis.cancel();
    const u = new SpeechSynthesisUtterance(text), { v } = voiceFor();
    if (v) { u.voice = v; u.lang = v.lang; }
    u.rate = 1.05;
    speechSynthesis.speak(u);
  }
  if (window.speechSynthesis) { speechSynthesis.getVoices(); speechSynthesis.onvoiceschanged = () => speechSynthesis.getVoices(); }
  window.VKVoice = {
    supported: !!window.speechSynthesis,
    say: speak,
    // isang pindot sa keypad: numero, 'clr' o 'del'
    key(k) {
      if (!ok()) return;
      const fil = voiceFor().fil, L = fil ? 'fil' : 'en';
      if (/^\d$/.test(k)) speak((fil ? FIL : EN)[+k]);
      else if (WORD[L][k]) speak(WORD[L][k]);
    },
    // mga aksyon: 'reserved', 'notfound'
    act(name, extra) {
      if (!ok()) return;
      const L = voiceFor().fil ? 'fil' : 'en';
      speak((WORD[L][name] || name) + (extra ? ', ' + extra : ''));
    }
  };
})();
