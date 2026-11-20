// Paghahanap gamit ang boses (Web Speech API). Lalabas ang 🎤 sa loob ng bawat <input data-voice>.
// data-voice="go" + data-voice-go="<id ng button>": pipindutin din ang button pagkatapos (hal. YouTube search).
// Kapag hindi kaya ng browser (hal. Firefox), walang 🎤 na lalabas.
(function () {
  const SR = window.SpeechRecognition || window.webkitSpeechRecognition;
  if (!SR) return;
  const MIC = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="9" y="2.5" width="6" height="12" rx="3"/><path d="M5.5 11a6.5 6.5 0 0 0 13 0M12 17.5v4M8.5 21.5h7"/></svg>';
  const css = `
  .vs-wrap{position:relative;display:block;flex:1;min-width:0}
  .vs-wrap>input{width:100%;padding-right:52px!important}
  .vs-btn{position:absolute;right:6px;top:50%;transform:translateY(-50%);width:38px;height:38px;border-radius:50%;border:0;cursor:pointer;
    display:grid;place-items:center;color:#062320;background:linear-gradient(135deg,#62e3d2,#7cc4ff);box-shadow:0 4px 14px rgba(98,227,210,.35)}
  .vs-btn svg{width:19px;height:19px}
  .vs-btn:focus-visible{outline:3px solid #fff;outline-offset:2px}
  .vs-ov{position:fixed;inset:0;z-index:9999;display:grid;place-items:center;background:radial-gradient(circle at 50% 40%,rgba(18,40,44,.96),rgba(6,8,12,.97));
    color:#fff;font-family:var(--font,system-ui);text-align:center;animation:vsIn .25s ease}
  @keyframes vsIn{from{opacity:0}to{opacity:1}}
  .vs-top{opacity:.7;font-weight:600;margin-bottom:26px}
  .vs-orb{position:relative;width:220px;height:220px;margin:0 auto;display:grid;place-items:center}
  .vs-orb i{position:absolute;inset:58px;border-radius:50%;border:3px solid rgba(98,227,210,.6);animation:vsRing 1.6s linear infinite}
  .vs-orb i:nth-child(2){animation-delay:.53s}.vs-orb i:nth-child(3){animation-delay:1.06s}
  .vs-ov.talk .vs-orb i{animation-duration:.9s;border-color:rgba(124,196,255,.8)}
  @keyframes vsRing{from{transform:scale(1);opacity:.9}to{transform:scale(2.1);opacity:0}}
  .vs-core{position:relative;width:104px;height:104px;border-radius:50%;border:0;color:#062320;display:grid;place-items:center;cursor:pointer;
    background:linear-gradient(135deg,#62e3d2,#7cc4ff);box-shadow:0 0 60px rgba(98,227,210,.55)}
  .vs-core svg{width:46px;height:46px}
  .vs-bars{display:flex;gap:5px;justify-content:center;align-items:center;height:34px;margin:22px 0 16px}
  .vs-bars b{width:5px;height:6px;border-radius:3px;background:#62e3d2;transition:height .12s}
  .vs-ov.talk .vs-bars b{animation:vsBar .5s ease-in-out infinite alternate}
  .vs-bars b:nth-child(odd){animation-delay:.15s}.vs-bars b:nth-child(3n){animation-delay:.3s}
  @keyframes vsBar{from{height:6px}to{height:30px}}
  .vs-txt{font-size:18px;font-weight:700;max-width:86vw;min-height:28px}
  .vs-txt.big{font-size:26px}.vs-txt.err{color:#ff9a9a}
  .vs-hint{opacity:.6;font-size:14px;margin-top:12px}
  @media (prefers-reduced-motion:reduce){.vs-orb i,.vs-bars b{animation:none!important}}`;
  const st = document.createElement('style'); st.textContent = css; document.head.appendChild(st);
  const T = (fil, en) => (window.VK_PREFS && VK_PREFS.get().lang === 'en') ? en : fil;

  let ov = null, rec = null;
  function close() { try { rec && rec.abort(); } catch (e) {} rec = null; if (ov) { ov.remove(); ov = null; } }
  function listen(input) {
    close();
    ov = document.createElement('div'); ov.className = 'vs-ov';
    ov.innerHTML = `<div><div class="vs-top">${T('Nakikinig…', 'Listening…')}</div>
      <div class="vs-orb"><i></i><i></i><i></i><button class="vs-core" type="button" aria-label="Mic">${MIC}</button></div>
      <div class="vs-bars">${'<b></b>'.repeat(9)}</div>
      <div class="vs-txt">${T('Sabihin ang title o artist ng kanta', 'Say the song title or artist')}</div><div class="vs-hint"></div></div>`;
    document.body.appendChild(ov);
    const txt = ov.querySelector('.vs-txt'), hint = ov.querySelector('.vs-hint');
    ov.addEventListener('click', e => { if (!e.target.closest('.vs-core')) close(); });
    ov.querySelector('.vs-core').onclick = () => listen(input);
    const onKey = e => { if (e.key === 'Escape') { close(); document.removeEventListener('keydown', onKey); } };
    document.addEventListener('keydown', onKey);
    rec = new SR();
    const lang = (window.VK_PREFS && VK_PREFS.get().lang === 'en') ? (navigator.language || 'en-US') : 'fil-PH';
    rec.lang = lang; rec.interimResults = true; rec.maxAlternatives = 1;
    let got = '';
    rec.onspeechstart = () => ov && ov.classList.add('talk');
    rec.onspeechend = () => ov && ov.classList.remove('talk');
    rec.onresult = e => {
      let s = ''; let fin = false;
      for (let i = e.resultIndex; i < e.results.length; i++) { s += e.results[i][0].transcript; if (e.results[i].isFinal) fin = true; }
      got = s.trim();
      if (got) { txt.textContent = `“${got}”`; txt.className = 'vs-txt big'; }
      if (fin && got) {
        input.value = got;
        input.dispatchEvent(new Event('input', { bubbles: true }));
        if (input.dataset.voice === 'go' && input.dataset.voiceGo) { const b = document.getElementById(input.dataset.voiceGo); b && b.click(); }
        if (navigator.vibrate) navigator.vibrate(30);
        setTimeout(close, 350);
      }
    };
    rec.onerror = e => {
      if (!ov) return;
      ov.classList.remove('talk'); txt.className = 'vs-txt err';
      txt.textContent = e.error === 'not-allowed' ? T('Payagan ang mic para sa site na ito.', 'Allow the mic for this site.')
        : e.error === 'network' ? T('Kailangan ng internet para sa boses.', 'Voice search needs internet.')
        : T('Hindi narinig. Subukan ulit.', "Didn't catch that. Try again.");
      hint.textContent = T('Pindutin ang 🎤 para subukan ulit', 'Tap 🎤 to try again');
    };
    rec.onend = () => { if (ov && !got && !txt.classList.contains('err')) { txt.className = 'vs-txt err'; txt.textContent = T('Hindi narinig. Subukan ulit.', "Didn't catch that. Try again."); hint.textContent = T('Pindutin ang 🎤 para subukan ulit', 'Tap 🎤 to try again'); } };
    try { rec.start(); } catch (e) { close(); }
  }
  function attach(input) {
    if (input.dataset.vsOn) return; input.dataset.vsOn = '1';
    const w = document.createElement('span'); w.className = 'vs-wrap';
    input.parentNode.insertBefore(w, input); w.appendChild(input);
    const b = document.createElement('button'); b.type = 'button'; b.className = 'vs-btn'; b.setAttribute('aria-label', T('Hanapin gamit ang boses', 'Search by voice')); b.innerHTML = MIC;
    b.onclick = () => listen(input);
    w.appendChild(b);
  }
  const run = () => document.querySelectorAll('input[data-voice]').forEach(attach);
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', run); else run();
  window.VKVoiceSearch = { attach, listen };
})();
