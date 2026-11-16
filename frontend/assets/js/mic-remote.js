// ===== MIC sa phone: ipinapadala ang boses sa TV gamit ang WebRTC =====
// VKMicRemote.init(sendFn, onState) ; start({clean}) ; stop() ; setMuted(bool) ; handle(msg) ; level()
// onState(state, info): off | asking | connecting | connected | reconnecting | noanswer | denied | nomic | unsupported | error
(function () {
  const ICE = [{ urls: 'stun:stun.l.google.com:19302' }];
  let myId = null;
  try { myId = sessionStorage.getItem('vk_cid'); } catch (e) {}
  if (!myId) { myId = 'm' + Math.random().toString(36).slice(2, 10); try { sessionStorage.setItem('vk_cid', myId); } catch (e) {} }

  let send = () => {}, onState = () => {};
  let pc = null, stream = null, on = false, muted = false, retryT = null, wake = null;
  let actx = null, an = null, buf = null;
  let opts = { clean: true };

  function set(s, info) { onState(s, info); }

  async function wakeOn() {
    try { if ('wakeLock' in navigator && !wake) { wake = await navigator.wakeLock.request('screen'); wake.addEventListener('release', () => { wake = null; }); } } catch (e) {}
  }
  function wakeOff() { try { wake && wake.release(); } catch (e) {} wake = null; }
  document.addEventListener('visibilitychange', () => { if (document.visibilityState === 'visible' && on) wakeOn(); });

  async function start(o) {
    opts = Object.assign(opts, o || {});
    if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia || !window.RTCPeerConnection) { set('unsupported'); return; }
    on = true; muted = false;
    set('asking');
    try {
      stream = await navigator.mediaDevices.getUserMedia({
        audio: { echoCancellation: opts.clean, noiseSuppression: opts.clean, autoGainControl: true, channelCount: 1 },
        video: false
      });
    } catch (e) {
      on = false;
      set(e.name === 'NotAllowedError' || e.name === 'SecurityError' ? 'denied' : e.name === 'NotFoundError' ? 'nomic' : 'error', e.message);
      return;
    }
    meterSetup();
    wakeOn();
    await connect();
  }

  async function connect() {
    if (!on) return;
    clearTimeout(retryT);
    if (pc) { try { pc.close(); } catch (e) {} }
    set('connecting');
    pc = new RTCPeerConnection({ iceServers: ICE });
    stream.getTracks().forEach(t => pc.addTrack(t, stream));
    pc.onicecandidate = e => { if (e.candidate) send({ to: 'host', from: myId, type: 'ice', data: e.candidate.toJSON ? e.candidate.toJSON() : e.candidate }); };
    pc.onconnectionstatechange = () => {
      if (!pc) return;
      const s = pc.connectionState;
      if (s === 'connected') { clearTimeout(retryT); set('connected'); if (muted) send({ to: 'host', from: myId, type: 'mute', data: true }); }
      else if (s === 'failed' || s === 'disconnected') { set('reconnecting'); retry(2500); }
    };
    try {
      const offer = await pc.createOffer();
      await pc.setLocalDescription(offer);
      send({ to: 'host', from: myId, type: 'offer', name: (window.vkMicName && window.vkMicName()) || 'Mic', data: { type: offer.type, sdp: pc.localDescription.sdp } });
    } catch (e) { set('error', e.message); return; }
    // kapag walang sagot ang TV
    retryT = setTimeout(() => { if (on && pc && pc.connectionState !== 'connected') { set('noanswer'); retry(4000); } }, 9000);
  }
  function retry(ms) { clearTimeout(retryT); retryT = setTimeout(() => { if (on) connect(); }, ms); }

  async function handle(msg) {
    if (!pc || !msg || msg.to !== myId) return;
    try {
      if (msg.type === 'answer') await pc.setRemoteDescription(msg.data);
      else if (msg.type === 'ice') await pc.addIceCandidate(msg.data);
    } catch (e) { /* hindi kritikal */ }
  }

  function stop() {
    on = false;
    clearTimeout(retryT);
    send({ to: 'host', from: myId, type: 'bye' });
    try { pc && pc.close(); } catch (e) {}
    pc = null;
    if (stream) stream.getTracks().forEach(t => t.stop());
    stream = null;
    try { actx && actx.close(); } catch (e) {}
    actx = null; an = null;
    wakeOff();
    set('off');
  }

  function setMuted(m) {
    muted = !!m;
    if (stream) stream.getAudioTracks().forEach(t => { t.enabled = !muted; });
    send({ to: 'host', from: myId, type: 'mute', data: muted });
  }

  function meterSetup() {
    try {
      const AC = window.AudioContext || window.webkitAudioContext;
      actx = new AC();
      an = actx.createAnalyser(); an.fftSize = 512;
      actx.createMediaStreamSource(stream).connect(an);
      buf = new Float32Array(an.fftSize);
    } catch (e) { an = null; }
  }
  function level() {
    if (!an || muted) return 0;
    an.getFloatTimeDomainData(buf);
    let s = 0; for (let i = 0; i < buf.length; i++) s += buf[i] * buf[i];
    return Math.sqrt(s / buf.length);
  }

  window.VKMicRemote = {
    id: myId,
    init(sendFn, stateFn) { send = sendFn; onState = stateFn; },
    start, stop, setMuted, handle, level,
    isOn: () => on, isMuted: () => muted,
    rename(name) { if (on) send({ to: 'host', from: myId, type: 'name', data: name }); }
  };
})();
