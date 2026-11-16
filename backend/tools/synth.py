import numpy as np, subprocess, os, sys
SR = 44100
NOTE = {'C':0,'C#':1,'D':2,'D#':3,'E':4,'F':5,'F#':6,'G':7,'G#':8,'A':9,'A#':10,'B':11}
def freq(n):
    name, octv = n[:-1], int(n[-1])
    midi = 12*(octv+1) + NOTE[name]
    return 440.0 * 2**((midi-69)/12)
CH = {'C':['C3','E3','G3'],'F':['F3','A3','C4'],'G':['G2','B2','D3'],'G7':['G2','B2','F3'],'D':['D3','F#3','A3'],
      'Am':['A2','C3','E3'],'Dm':['D3','F3','A3'],'Em':['E3','G3','B3']}
ROOT = {'C':'C2','F':'F2','G':'G2','G7':'G2','D':'D2','Am':'A2','Dm':'D2','Em':'E2'}

def env(n, a, d_rate, rel=0.04):
    t = np.arange(n)/SR
    e = np.minimum(1, t/a) * np.exp(-t*d_rate)
    r = int(rel*SR)
    if n > r: e[-r:] *= np.linspace(1,0,r)
    return e

def tone(f, dur, kind='lead'):
    n = int(dur*SR); t = np.arange(n)/SR
    if kind == 'lead':
        vib = 1 + 0.004*np.sin(2*np.pi*5.2*t)*np.minimum(1,t/0.3)
        ph = 2*np.pi*f*np.cumsum(vib)/SR
        w = np.sin(ph) + 0.35*np.sin(2*ph) + 0.12*np.sin(3*ph)
        return w * env(n, 0.03, 0.6, 0.06)
    if kind == 'ep':  # electric-piano-ish
        w = np.sin(2*np.pi*f*t) + 0.5*np.sin(2*np.pi*2*f*t)*np.exp(-t*6) + 0.2*np.sin(2*np.pi*3*f*t)*np.exp(-t*9)
        return w * env(n, 0.005, 1.6, 0.05)
    if kind == 'pad':
        w = sum(np.sin(2*np.pi*f*(1+d)*t) for d in (-0.003,0,0.003))/3 + 0.25*np.sin(2*np.pi*2*f*t)
        e = np.minimum(1, t/0.4) * np.minimum(1, (dur-t)/0.4).clip(0,1)
        return w * e
    if kind == 'bass':
        w = np.sin(2*np.pi*f*t) + 0.3*np.sin(2*np.pi*2*f*t)
        return w * env(n, 0.005, 2.2, 0.05)
    if kind == 'pluck':
        w = sum(np.sin(2*np.pi*f*k*t)*np.exp(-t*(3+k*2.5))/k for k in range(1,7))
        return w * env(n, 0.002, 0.8, 0.03)
    if kind == 'brass':
        vib = 1 + 0.005*np.sin(2*np.pi*5.5*t)*np.minimum(1,t/0.25)
        ph = 2*np.pi*f*np.cumsum(vib)/SR
        w = sum(np.sin(k*ph)/k**0.9 for k in range(1,8))
        a = np.minimum(1, t/0.05)
        return w * a * np.exp(-t*0.35) * 0.6 * np.minimum(1, (dur-t)/0.05).clip(0,1)
    if kind == 'bell':
        w = np.sin(2*np.pi*f*t) + 0.4*np.sin(2*np.pi*2.76*f*t)*np.exp(-t*8)
        return w * env(n, 0.002, 3.5, 0.03)

def kick(): n=int(.25*SR); t=np.arange(n)/SR; f=110*np.exp(-t*22)+45; return np.sin(2*np.pi*np.cumsum(f)/SR)*np.exp(-t*12)
def hat(): n=int(.05*SR); return np.random.default_rng(1).standard_normal(n)*np.exp(-np.arange(n)/SR*90)*0.5
def snare(): n=int(.18*SR); t=np.arange(n)/SR; return (np.random.default_rng(2).standard_normal(n)*0.6+np.sin(2*np.pi*190*t))*np.exp(-t*22)

def add(buf, x, start, gain):
    i = int(start*SR); j = min(len(buf), i+len(x))
    if j > i: buf[i:j] += x[:j-i]*gain

def render(song, out_base):
    bpm, bpb = song['bpm'], song['bpb']   # beats per minute (beat unit), beats per bar
    spb = 60/bpm
    intro_beats = song.get('intro', bpb*2)
    pickup = song.get('pickup', 0)
    # melody timeline
    t_beats = intro_beats - pickup
    lines = []
    mel = []
    for text, notes in song['lines']:
        lines.append((t_beats*spb, text))
        for n, b in notes:
            if n != 'R': mel.append((t_beats*spb, b*spb, freq(n)))
            t_beats += b
    total_beats = t_beats + bpb*2
    dur = total_beats*spb + 1.5
    buf = np.zeros(int(dur*SR)); mbuf = np.zeros_like(buf)
    # chords: list per bar (or half bars via 'chord_step')
    step = song.get('chord_step', bpb)
    chords = song['chords']
    intro_ch = song.get('intro_chords', [chords[0]]*int(intro_beats/step))
    allch = intro_ch + chords + [chords[-1]]*int(bpb*2/step)
    style = song.get('style','pop')
    for k, c in enumerate(allch):
        st = k*step*spb
        if st > dur-1: break
        cd = step*spb
        notes = CH[c]
        if style == 'pad':
            for nn in notes: add(buf, tone(freq(nn)*2, cd+0.3, 'pad'), st, 0.10)
            add(buf, tone(freq(ROOT[c])*2, cd, 'pad'), st, 0.10)
        elif style == 'folk':
            for bt in range(int(step)):
                if bt % 2 == 0: add(buf, tone(freq(ROOT[c])*2, spb*0.9, 'pluck'), st+bt*spb, 0.18)
                else:
                    for nn in notes: add(buf, tone(freq(nn)*2, spb*0.9, 'pluck'), st+bt*spb, 0.10)
            add(buf, tone(freq(ROOT[c]), cd*0.5, 'bass'), st, 0.30)
            add(buf, tone(freq(ROOT[c])*1.5, cd*0.5, 'bass'), st+cd*0.5, 0.2)
        elif style == 'hymn':
            for nn in notes: add(buf, tone(freq(nn)*2, cd+0.3, 'pad'), st, 0.09)
            add(buf, tone(freq(ROOT[c])*2, cd, 'pad'), st, 0.10)
            add(buf, tone(freq(notes[0])*4, spb*0.9, 'bell'), st, 0.05)
        else:
            # comp on each beat
            for bt in range(int(step)):
                for nn in notes: add(buf, tone(freq(nn)*2, spb*0.95, 'ep'), st+bt*spb, 0.09 if bt % 2 == 0 else 0.06)
            add(buf, tone(freq(ROOT[c]), cd*0.5, 'bass'), st, 0.32)
            add(buf, tone(freq(ROOT[c])*(1.5 if c not in ('D',) else 1.5), cd*0.5, 'bass'), st+cd*0.5, 0.22)
    if song.get('drums'):
        nb = int(total_beats)
        for b in range(int(intro_beats) - 0, nb - bpb):
            st = b*spb
            if b % bpb in (0, 2): add(buf, kick(), st, 0.45)
            if b % bpb in (1, 3): add(buf, snare(), st, 0.14)
            add(buf, hat(), st, 0.10); add(buf, hat(), st+spb/2, 0.06)
        # count-in sticks
        for b in range(int(intro_beats-bpb), int(intro_beats)):
            add(buf, hat()*2, b*spb, 0.25)
    for st, d, f in mel:
        add(mbuf, tone(f, d*0.98, song.get('lead','lead')), st, 0.22)
    mix = buf + mbuf
    # simple room reverb
    for dl, g in ((0.031, .25), (0.053, .18), (0.089, .12), (0.127, .08)):
        k = int(dl*SR); mix[k:] += mix[:-k]*g
    mix /= np.max(np.abs(mix)) * 1.12
    pcm = (mix*32767).astype(np.int16)
    wav = out_base + '.wav'
    import wave
    with wave.open(wav, 'wb') as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR); w.writeframes(pcm.tobytes())
    subprocess.run(['ffmpeg','-y','-loglevel','error','-i',wav,'-ac','1','-b:a','96k','-af','afade=t=out:st=%.2f:d=1.2' % (dur-1.4), out_base+'.mp3'], check=True)
    os.remove(wav)
    def ts(s): m=int(s//60); return f'[{m:02d}:{s-60*m:05.2f}]'
    lrc = [f"[ti:{song['title']}]", f"[ar:{song['artist']}]", "[by:Videoke demo, original arrangement]"]
    lrc += [ts(s)+txt for s, txt in lines]
    lrc.append(ts(total_beats*spb - bpb*2*spb + 0.5*spb))
    open(out_base+'.lrc','w').write('\n'.join(lrc)+'\n')
    # melody para sa pitch bar: [simula s, haba s, midi]
    import json, math
    notes = [[round(st,3), round(d*0.95,3), int(round(69+12*math.log2(f/440)))] for st, d, f in mel]
    json.dump({'v': 1, 'notes': notes}, open(out_base+'.mel.json','w'), separators=(',',':'))
    return dur

S = {}
S['twinkle-twinkle'] = dict(title='Twinkle, Twinkle, Little Star', artist='Traditional', bpm=100, bpb=4, chord_step=2, drums=True,
  lines=[
   ('Twinkle, twinkle, little star', [('C5',1),('C5',1),('G5',1),('G5',1),('A5',1),('A5',1),('G5',2)]),
   ('How I wonder what you are', [('F5',1),('F5',1),('E5',1),('E5',1),('D5',1),('D5',1),('C5',2)]),
   ('Up above the world so high', [('G5',1),('G5',1),('F5',1),('F5',1),('E5',1),('E5',1),('D5',2)]),
   ('Like a diamond in the sky', [('G5',1),('G5',1),('F5',1),('F5',1),('E5',1),('E5',1),('D5',2)]),
   ('Twinkle, twinkle, little star', [('C5',1),('C5',1),('G5',1),('G5',1),('A5',1),('A5',1),('G5',2)]),
   ('How I wonder what you are', [('F5',1),('F5',1),('E5',1),('E5',1),('D5',1),('D5',1),('C5',2)]),
  ],
  chords=['C','C','F','C','F','C','G','C','C','F','C','G','C','F','C','G','C','C','F','C','F','C','G','C'])
S['jingle-bells'] = dict(title='Jingle Bells', artist='James Lord Pierpont', bpm=132, bpb=4, drums=True,
  lines=[
   ('Jingle bells, jingle bells', [('E5',1),('E5',1),('E5',2),('E5',1),('E5',1),('E5',2)]),
   ('Jingle all the way', [('E5',1),('G5',1),('C5',1.5),('D5',.5),('E5',4)]),
   ('Oh, what fun it is to ride', [('F5',1),('F5',1),('F5',1.5),('F5',.5),('F5',1),('E5',1),('E5',1)]),
   ('in a one-horse open sleigh, hey!', [('E5',.5),('E5',.5),('E5',1),('D5',1),('D5',1),('E5',1),('D5',2),('G5',2)]),
   ('Jingle bells, jingle bells', [('E5',1),('E5',1),('E5',2),('E5',1),('E5',1),('E5',2)]),
   ('Jingle all the way', [('E5',1),('G5',1),('C5',1.5),('D5',.5),('E5',4)]),
   ('Oh, what fun it is to ride', [('F5',1),('F5',1),('F5',1.5),('F5',.5),('F5',1),('E5',1),('E5',1)]),
   ('in a one-horse open sleigh!', [('E5',.5),('E5',.5),('G5',1),('G5',1),('F5',1),('D5',1),('C5',4)]),
  ],
  chords=['C','C','C','C','F','C','D','G','C','C','C','C','F','C','G7','C'])
S['silent-night'] = dict(title='Silent Night', artist='Franz Xaver Gruber', bpm=170, bpb=6, style='pad', lead='lead', intro=12,
  lines=[
   ('Silent night, holy night', [('G4',3),('A4',1),('G4',2),('E4',6),('G4',3),('A4',1),('G4',2),('E4',6)]),
   ('All is calm, all is bright', [('D5',4),('D5',2),('B4',6),('C5',4),('C5',2),('G4',6)]),
   ('Round yon virgin mother and child', [('A4',4),('A4',2),('C5',3),('B4',1),('A4',2),('G4',3),('A4',1),('G4',2),('E4',6)]),
   ('Holy infant so tender and mild', [('A4',4),('A4',2),('C5',3),('B4',1),('A4',2),('G4',3),('A4',1),('G4',2),('E4',6)]),
   ('Sleep in heavenly peace', [('D5',4),('D5',2),('F5',3),('D5',1),('B4',2),('C5',6),('E5',6)]),
   ('Sleep in heavenly peace', [('C5',3),('G4',1),('E4',2),('G4',3),('F4',1),('D4',2),('C4',12)]),
  ],
  chords=['C','C','C','C','G','G','C','C','F','F','C','C','F','F','C','C','G','G','C','C','C','G','C','C'])
S['auld-lang-syne'] = dict(title='Auld Lang Syne', artist='Robert Burns (traditional)', bpm=84, bpb=4, pickup=1, drums=False,
  lines=[
   ('Should auld acquaintance be forgot', [('G4',1),('C5',1.5),('B4',.5),('C5',1),('E5',1),('D5',1.5),('C5',.5),('D5',1)]),
   ('and never brought to mind?', [('E5',1),('C5',1.5),('C5',.5),('E5',1),('G5',1),('A5',3)]),
   ('Should auld acquaintance be forgot', [('A5',1),('G5',1.5),('E5',.5),('E5',1),('C5',1),('D5',1.5),('C5',.5),('D5',1)]),
   ('and auld lang syne?', [('E5',1),('C5',1.5),('A4',.5),('A4',1),('G4',1),('C5',4)]),
  ],
  chords=['C','G','C','F','C','G','Am','F','C','G7','C','C'])
S['oh-susanna'] = dict(title='Oh! Susanna', artist='Stephen Foster', bpm=132, bpb=4, pickup=1, drums=True, style='folk',
  lines=[
   ('I come from Alabama with a banjo on my knee', [('C5',.5),('D5',.5),('E5',1),('G5',1),('G5',1.5),('A5',.5),('G5',1),('E5',1),('C5',1.5),('D5',.5),('E5',1),('E5',1),('D5',1),('C5',1),('D5',3)]),
   ("I'm going to Louisiana, my true love for to see", [('C5',.5),('D5',.5),('E5',1),('G5',1),('G5',1.5),('A5',.5),('G5',1),('E5',1),('C5',1.5),('D5',.5),('E5',1),('E5',1),('D5',1),('D5',1),('C5',4)]),
   ("Oh! Susanna, oh don't you cry for me", [('F5',2),('F5',2),('A5',1),('A5',2),('A5',1),('G5',1),('G5',1),('E5',1),('C5',1),('D5',3)]),
   ('For I come from Alabama with a banjo on my knee', [('C5',.5),('D5',.5),('E5',1),('G5',1),('G5',1.5),('A5',.5),('G5',1),('E5',1),('C5',1.5),('D5',.5),('E5',1),('E5',1),('D5',1),('D5',1),('C5',4)]),
  ],
  chords=['C','C','C','G','C','C','G','C','F','C','C','G','C','C','G','C'])
S['when-the-saints'] = dict(title='When the Saints Go Marching In', artist='Traditional', bpm=126, bpb=4, pickup=3, drums=True, lead='brass',
  lines=[
   ('Oh, when the saints', [('C5',1),('E5',1),('F5',1),('G5',5)]),
   ('go marching in', [('C5',1),('E5',1),('F5',1),('G5',5)]),
   ('Oh, when the saints go marching in', [('C5',1),('E5',1),('F5',1),('G5',2),('E5',2),('C5',2),('E5',2),('D5',6)]),
   ('I want to be in that number', [('E5',1),('E5',1),('D5',1),('C5',3),('C5',1),('E5',1),('G5',2),('G5',1),('F5',5)]),
   ('when the saints go marching in', [('E5',1),('F5',1),('G5',2),('E5',2),('C5',2),('D5',2),('C5',6)]),
  ],
  chords=['C','C','C','C','C','C','G','G','C','C','F','F','C','G','C','C'])
S['we-wish-you'] = dict(title='We Wish You a Merry Christmas', artist='Traditional', bpm=112, bpb=3, pickup=1, drums=False,
  lines=[
   ('We wish you a merry Christmas', [('G4',1),('C5',1),('C5',.5),('D5',.5),('C5',.5),('B4',.5),('A4',1),('A4',1)]),
   ('we wish you a merry Christmas', [('A4',1),('D5',1),('D5',.5),('E5',.5),('D5',.5),('C5',.5),('B4',1),('G4',1)]),
   ('we wish you a merry Christmas', [('G4',1),('E5',1),('E5',.5),('F5',.5),('E5',.5),('D5',.5),('C5',1),('A4',1)]),
   ('and a happy New Year!', [('G4',.5),('G4',.5),('A4',1),('D5',1),('B4',1),('C5',3)]),
   ('We wish you a merry Christmas', [('G4',1),('C5',1),('C5',.5),('D5',.5),('C5',.5),('B4',.5),('A4',1),('A4',1)]),
   ('we wish you a merry Christmas', [('A4',1),('D5',1),('D5',.5),('E5',.5),('D5',.5),('C5',.5),('B4',1),('G4',1)]),
   ('we wish you a merry Christmas', [('G4',1),('E5',1),('E5',.5),('F5',.5),('E5',.5),('D5',.5),('C5',1),('A4',1)]),
   ('and a happy New Year!', [('G4',.5),('G4',.5),('A4',1),('D5',1),('B4',1),('C5',3)]),
  ],
  chords=['C','F','D','G','Am','F','G','C','C','F','D','G','Am','F','G','C'])
S['deck-the-halls'] = dict(title='Deck the Halls', artist='Traditional', bpm=120, bpb=4, drums=True,
  lines=[
   ('Deck the halls with boughs of holly', [('G5',1.5),('F5',.5),('E5',1),('D5',1),('C5',1),('D5',1),('E5',1),('C5',1)]),
   ('fa la la la la, la la la la', [('D5',.5),('E5',.5),('F5',.5),('D5',.5),('E5',1.5),('D5',.5),('C5',1),('B4',1),('C5',2)]),
   ("'Tis the season to be jolly", [('G5',1.5),('F5',.5),('E5',1),('D5',1),('C5',1),('D5',1),('E5',1),('C5',1)]),
   ('fa la la la la, la la la la', [('D5',.5),('E5',.5),('F5',.5),('D5',.5),('E5',1.5),('D5',.5),('C5',1),('B4',1),('C5',2)]),
   ('Don we now our gay apparel', [('D5',1.5),('E5',.5),('F5',1),('D5',1),('E5',1.5),('F5',.5),('G5',1),('D5',1)]),
   ('fa la la, la la la, la la la', [('E5',.5),('F#5',.5),('G5',1),('A5',.5),('B5',.5),('C6',1),('B5',1),('A5',1),('G5',2)]),
   ('Troll the ancient Yuletide carol', [('G5',1.5),('F5',.5),('E5',1),('D5',1),('C5',1),('D5',1),('E5',1),('C5',1)]),
   ('fa la la la la, la la la la', [('A5',.5),('A5',.5),('A5',.5),('A5',.5),('G5',1.5),('F5',.5),('E5',1),('D5',1),('C5',2)]),
  ],
  chords=['C','C','G','C','C','C','G','C','G','G','D','G','C','C','F','C'])
S['row-your-boat'] = dict(title='Row, Row, Row Your Boat', artist='Traditional', bpm=200, bpb=6, drums=False, style='folk', intro=12,
  lines=[
   ('Row, row, row your boat', [('C5',3),('C5',3),('C5',2),('D5',1),('E5',3)]),
   ('gently down the stream', [('E5',2),('D5',1),('E5',2),('F5',1),('G5',6)]),
   ('Merrily, merrily, merrily, merrily', [('C6',1),('C6',1),('C6',1),('G5',1),('G5',1),('G5',1),('E5',1),('E5',1),('E5',1),('C5',1),('C5',1),('C5',1)]),
   ('life is but a dream', [('G5',2),('F5',1),('E5',2),('D5',1),('C5',6)]),
   ('Row, row, row your boat', [('C5',3),('C5',3),('C5',2),('D5',1),('E5',3)]),
   ('gently down the stream', [('E5',2),('D5',1),('E5',2),('F5',1),('G5',6)]),
   ('Merrily, merrily, merrily, merrily', [('C6',1),('C6',1),('C6',1),('G5',1),('G5',1),('G5',1),('E5',1),('E5',1),('E5',1),('C5',1),('C5',1),('C5',1)]),
   ('life is but a dream', [('G5',2),('F5',1),('E5',2),('D5',1),('C5',6)]),
  ],
  chords=['C','C','C','G','C','G','C','C','C','G','C','G','C','C'])
S['amazing-grace'] = dict(title='Amazing Grace', artist='John Newton', bpm=78, bpb=3, pickup=1, drums=False, style='hymn',
  lines=[
   ('Amazing grace, how sweet the sound', [('G4',1),('C5',2),('E5',.5),('C5',.5),('E5',2),('D5',1),('C5',2),('A4',1),('G4',2)]),
   ('that saved a wretch like me', [('G4',1),('C5',2),('E5',.5),('C5',.5),('E5',2),('D5',1),('G5',3)]),
   ('I once was lost, but now am found', [('G5',2),('E5',1),('G5',2),('E5',.5),('C5',.5),('E5',2),('D5',1),('C5',2),('A4',1)]),
   ("was blind, but now I see", [('G4',2),('G4',1),('C5',2),('E5',.5),('C5',.5),('E5',2),('D5',1),('C5',3)]),
  ],
  chords=['C','C','F','C','C','G','G','C','C','C','F','C','C','G','C'])
os.makedirs(sys.argv[1], exist_ok=True)
only = sys.argv[2].split(',') if len(sys.argv) > 2 else None
for k, s in S.items():
    if only and k not in only: continue
    d = render(s, os.path.join(sys.argv[1], k))
    print(k, round(d,1), 's')
