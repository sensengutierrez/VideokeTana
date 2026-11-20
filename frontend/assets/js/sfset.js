// Settings: SoundFont (pagsubok at sariling .sf2)
(function () {
  const $ = id => document.getElementById(id);
  if (!$('sfTest')) return;
  const info = () => { const n = VK_PREFS.get().sfOwnName; $('sfInfo').textContent = n ? 'Sarili: ' + n : 'GeneralUser GS (kasama, libre, mga 300 instrumento).'; };
  info(); window.addEventListener('vkprefs', info);
  $('sfTest').onclick = async () => {
    $('sfStatus').textContent = 'Inihahanda (ang unang beses ay nagda-download ng 32 MB)...';
    try { const n = await VKSF2.test(); $('sfStatus').textContent = 'Gumagana ang SoundFont: ' + n + ' na tunog. Naririnig mo ba?'; }
    catch (e) { $('sfStatus').textContent = 'Hindi gumana: ' + e.message; }
  };
  $('sfFile').onchange = async e => {
    const f = e.target.files[0]; e.target.value = ''; if (!f) return;
    $('sfStatus').textContent = 'Sinusuri ang ' + f.name + '...';
    try { await VKSF2.saveOwn(f); $('sfStatus').textContent = 'Na-save. Ito na ang gagamitin sa MIDI.'; }
    catch (err) { $('sfStatus').textContent = 'Hindi magamit: ' + err.message; }
  };
  $('sfClear').onclick = async () => { await VKSF2.clearOwn(); $('sfStatus').textContent = 'Inalis. GeneralUser GS na ulit.'; };
})();
