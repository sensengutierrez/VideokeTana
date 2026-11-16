// ===== MIC sa TV (host): tumatanggap ng boses mula sa mga phone gamit ang WebRTC =====
// Ang signaling (offer/answer/ice) ay dumadaan sa Supabase Realtime; ang boses mismo ay diretso phone -> TV.
// Halo: bawat mic -> highpass -> clarity (presence EQ) -> gain -> bus -> dry + echo + reverb -> master -> speaker
(function () {
  const ICE = [{ urls: 'stun:stun.l.google.com:19302' }];
  let pAn = null, pBuf = null;
  let ac = null, master, bus, dry, echoDelay, echoFb, echoWet, conv, revWet;
  const peers = new Map();           // id -> { pc, name, an, buf, gain, presence, level, muted, ... }
  let sendFn = () => {}, onChange = () => {};
  let duck = 1, hotCount = 0, onFeedback = () => {};

  function ensure() {
    if (ac) return ac;
    const AC = window.AudioContext || window.webkitAudioContext;
    ac = new AC({ latencyHint: 'interactive' });
    master = ac.createGain(); master.connect(ac.destination);
    bus = ac.createGain();
    dry = ac.createGain(); bus.connect(dry).connect(master);
    echoDelay = ac.createDelay(1); echoFb = ac.createGain(); echoWet = ac.createGain();
    bus.connect(echoDelay); echoDelay.connect(echoFb).connect(echoDelay); echoDelay.connect(echoWet).connect(master);
    conv = ac.createConvolver(); conv.buffer = impulse(2.2); revWet = ac.createGain();
    bus.connect(conv).connect(revWet).connect(master);
    pAn = ac.createAnalyser(); pAn.fftSize = 2048; pBuf = new Float32Array(pAn.fftSize); bus.connect(pAn);   // para sa pitch bar
    apply();
    return ac;
  }
  function impulse(sec) {
    const len = Math.floor(ac.sampleRate * sec), b = ac.createBuffer(2, len, ac.sampleRate);
    for (let c = 0; c < 2; c++) {
      const d = b.getChannelData(c);
      for (let i = 0; i < len; i++) d[i] = (Math.random() * 2 - 1) * Math.pow(1 - i / len, 2.8);
    }
    return b;
  }
  function apply() {
    if (!ac) return;
    const p = VK_PREFS.get(), t = ac.currentTime;
    master.gain.setTargetAtTime((p.micVolume / 100) * duck, t, 0.05);
    echoDelay.delayTime.setTargetAtTime(0.16 + (p.micEcho / 100) * 0.14, t, 0.05);
    echoFb.gain.setTargetAtTime((p.micEcho / 100) * 0.45, t, 0.05);
    echoWet.gain.setTargetAtTime((p.micEcho / 100) * 0.55, t, 0.05);
    revWet.gain.setTargetAtTime((p.micReverb / 100) * 0.9, t, 0.05);
    peers.forEach(x => { if (x.presence) x.presence.gain.setTargetAtTime((p.micClarity / 100) * 9, t, 0.05); });
  }

  function create(id, name) {
    const pc = new RTCPeerConnection({ iceServers: ICE });
    const peer = { id, name: name || 'Mic', pc, iceQ: [], ready: false, level: 0, muted: false };
    pc.onicecandidate = e => { if (e.candidate) sendFn({ to: id, type: 'ice', data: e.candidate.toJSON ? e.candidate.toJSON() : e.candidate }); };
    pc.ontrack = e => {
      const stream = e.streams[0] || new MediaStream([e.track]);
      // pinakamaliit na buffer para bawas delay (Chrome)
      try { e.receiver.playoutDelayHint = 0; } catch (x) {}
      try { e.receiver.jitterBufferTarget = 0; } catch (x) {}
      // Chrome: kailangang naka-attach sa (naka-mute na) audio element para dumaloy sa Web Audio
      const el = new Audio(); el.srcObject = stream; el.muted = true; el.play().catch(() => {});
      peer.el = el;
      const src = ac.createMediaStreamSource(stream);
      const hp = ac.createBiquadFilter(); hp.type = 'highpass'; hp.frequency.value = 90;
      const pres = ac.createBiquadFilter(); pres.type = 'peaking'; pres.frequency.value = 3200; pres.Q.value = 0.9;
      const g = ac.createGain();
      const an = ac.createAnalyser(); an.fftSize = 1024;
      src.connect(hp).connect(pres).connect(g).connect(bus);
      src.connect(an);
      Object.assign(peer, { src, presence: pres, gain: g, an, buf: new Float32Array(an.fftSize) });
      g.gain.value = peer.muted ? 0 : 1;
      apply(); onChange();
    };
    pc.onconnectionstatechange = () => {
      if (pc.connectionState === 'failed' || pc.connectionState === 'closed') close(id);
      else onChange();
    };
    peers.set(id, peer);
    onChange();
    return peer;
  }
  // Mic ng mismong device (isang device lang): diretso sa halo, walang WebRTC
  function addLocal(stream, name) {
    ensure();
    if (ac.state === 'suspended') ac.resume().catch(() => {});
    close('local');
    const src = ac.createMediaStreamSource(stream);
    const hp = ac.createBiquadFilter(); hp.type = 'highpass'; hp.frequency.value = 90;
    const pres = ac.createBiquadFilter(); pres.type = 'peaking'; pres.frequency.value = 3200; pres.Q.value = 0.9;
    const g = ac.createGain();
    const an = ac.createAnalyser(); an.fftSize = 1024;
    src.connect(hp).connect(pres).connect(g).connect(bus);
    src.connect(an);
    peers.set('local', { id: 'local', name: name || 'Mic ng device', pc: null, local: stream, src, presence: pres, gain: g, an,
      buf: new Float32Array(an.fftSize), level: 0, muted: false });
    apply(); onChange();
  }
  function close(id) {
    const p = peers.get(id);
    if (!p) return;
    try { p.pc && p.pc.close(); } catch (e) {}
    try { p.local && p.local.getTracks().forEach(t => t.stop()); } catch (e) {}
    try { p.src && p.src.disconnect(); p.gain && p.gain.disconnect(); } catch (e) {}
    if (p.el) p.el.srcObject = null;
    peers.delete(id);
    onChange();
  }

  async function handle(msg) {
    ensure();
    if (ac.state === 'suspended') ac.resume().catch(() => {});
    let peer = peers.get(msg.from);
    try {
      if (msg.type === 'offer') {
        if (peer) close(msg.from);
        peer = create(msg.from, msg.name);
        await peer.pc.setRemoteDescription(msg.data);
        const ans = await peer.pc.createAnswer();
        await peer.pc.setLocalDescription(ans);
        sendFn({ to: msg.from, type: 'answer', data: { type: ans.type, sdp: peer.pc.localDescription.sdp } });
        peer.ready = true;
        for (const c of peer.iceQ) await peer.pc.addIceCandidate(c).catch(() => {});
        peer.iceQ = [];
      } else if (msg.type === 'ice' && peer) {
        if (peer.ready) await peer.pc.addIceCandidate(msg.data).catch(() => {});
        else peer.iceQ.push(msg.data);
      } else if (msg.type === 'bye') {
        close(msg.from);
      } else if (msg.type === 'mute' && peer) {
        peer.muted = !!msg.data;
        if (peer.gain) peer.gain.gain.value = peer.muted ? 0 : 1;
        onChange();
      } else if (msg.type === 'name' && peer) {
        peer.name = msg.data || peer.name; onChange();
      }
    } catch (e) { console.warn('mic signaling', e); }
  }

  // Lakas ng boses ng bawat mic (0..1) + bantay sa feedback (pag-ugong)
  function levels() {
    const out = [];
    let hot = false;
    peers.forEach(p => {
      let rms = 0;
      if (p.an) {
        p.an.getFloatTimeDomainData(p.buf);
        let s = 0; for (let i = 0; i < p.buf.length; i++) s += p.buf[i] * p.buf[i];
        rms = Math.sqrt(s / p.buf.length);
      }
      p.level = p.muted ? 0 : rms;
      if (p.level > 0.45) hot = true;
      out.push({ id: p.id, name: p.name, level: p.level, state: p.pc ? p.pc.connectionState : 'connected', muted: p.muted, live: !!p.an });
    });
    // tuloy-tuloy na sobrang lakas nang ~2s = malamang feedback -> hinaan muna
    hotCount = hot ? hotCount + 1 : Math.max(0, hotCount - 1);
    if (hotCount > 20 && duck > 0.4) { duck = 0.45; apply(); onFeedback(); hotCount = 0; setTimeout(() => { duck = 1; apply(); }, 6000); }
    return out;
  }

  // Tono ng kumakanta (Hz), 0 kapag walang malinaw na tono. Autocorrelation sa 80 hanggang 1000 Hz.
  function pitch() {
    if (!pAn || !peers.size) return 0;
    pAn.getFloatTimeDomainData(pBuf);
    const b = pBuf, N = b.length, sr = ac.sampleRate;
    let rms = 0; for (let i = 0; i < N; i++) rms += b[i] * b[i];
    rms = Math.sqrt(rms / N);
    if (rms < 0.012) return 0;
    const minL = Math.floor(sr / 1000), maxL = Math.min(Math.floor(sr / 80), N >> 1), W = N - maxL;
    let e0 = 0; for (let i = 0; i < W; i++) e0 += b[i] * b[i];
    const c = new Float32Array(maxL + 2);
    let best = -1, bestL = 0;
    for (let L = minL; L <= maxL + 1; L++) {
      let s = 0, e1 = 0;
      for (let i = 0; i < W; i++) { s += b[i] * b[i + L]; e1 += b[i + L] * b[i + L]; }
      c[L] = s / Math.sqrt(e0 * e1 + 1e-9);
    }
    // unang malakas na tuktok (iwas sa octave error)
    let top = 0; for (let L = minL; L <= maxL; L++) top = Math.max(top, c[L]);
    if (top < 0.75) return 0;
    for (let L = minL + 1; L <= maxL; L++) {
      if (c[L] > 0.9 * top && c[L] >= c[L - 1] && c[L] >= c[L + 1]) { best = c[L]; bestL = L; break; }
    }
    if (!bestL) return 0;
    const a = c[bestL - 1], m = c[bestL], z = c[bestL + 1], d = a - 2 * m + z;
    const sh = d ? 0.5 * (a - z) / d : 0;
    return sr / (bestL + sh);
  }

  window.VKMicHost = {
    pitch,
    init(send, change, feedback) {
      sendFn = send; onChange = change || onChange; onFeedback = feedback || onFeedback;
      window.addEventListener('vkprefs', apply);
    },
    unlock() { ensure(); if (ac.state === 'suspended') ac.resume().catch(() => {}); },
    handle, levels, addLocal,
    removeLocal() { close('local'); },
    hasLocal: () => peers.has('local'),
    setLocalMuted(m) { const p = peers.get('local'); if (p) { p.muted = !!m; p.gain.gain.value = m ? 0 : 1; onChange(); } },
    count: () => peers.size,
    list: () => [...peers.values()].map(p => ({ id: p.id, name: p.name, muted: p.muted, live: !!p.an })),
    closeAll() { [...peers.keys()].forEach(close); }
  };
})();
