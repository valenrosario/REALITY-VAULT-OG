#!/usr/bin/env python3
"""Genera las imágenes de la app de Roku (Roku no tiene degradés ni formas redondeadas nativas, así que van como PNG).
Uso: python3 tools/make_assets.py   (desde app-roku/)"""
from PIL import Image, ImageDraw
import math, os
OUT = 'images'
os.makedirs(OUT, exist_ok=True)
S = 4  # supersampling

def save(im, name): im.save(os.path.join(OUT, name))

def grad(w, h, horizontal, stops):
    im = Image.new('RGBA', (w, h))
    px = im.load()
    n = w if horizontal else h
    for i in range(n):
        t = i / (n - 1)
        a = stops[-1][1]
        for (p0, a0), (p1, a1) in zip(stops, stops[1:]):
            if p0 <= t <= p1:
                a = a0 + (a1 - a0) * (t - p0) / max(p1 - p0, 1e-6); break
        v = (0, 0, 0, int(round(a * 255)))
        for j in range(h if horizontal else w):
            px[(i, j) if horizontal else (j, i)] = v
    return im

# degradés (se estiran al tamaño que haga falta)
save(grad(256, 4, True, [(0, .88), (.42, .42), (.66, 0), (1, 0)]), 'grad_left.png')
save(grad(4, 256, False, [(0, 0), (.46, 0), (.78, .5), (1, 1)]), 'grad_bottom.png')
save(grad(4, 256, False, [(0, .85), (.5, .35), (1, 0)]), 'grad_top.png')
save(grad(4, 256, False, [(0, 0), (1, .62)]), 'grad_fade.png')
save(grad(4, 256, False, [(0, 1), (1, 0)]), 'grad_fadeout.png')

def ninepatch(name, r, ring=0):
    """Rectángulo redondeado (relleno blanco o solo borde) como 9-patch; se tiñe con blendColor."""
    sz = r * 2 + 2
    big = Image.new('RGBA', (sz * S, sz * S), (0, 0, 0, 0))
    d = ImageDraw.Draw(big)
    d.rounded_rectangle([0, 0, sz * S - 1, sz * S - 1], radius=r * S, fill=(255, 255, 255, 255))
    if ring:
        d.rounded_rectangle([ring * S, ring * S, sz * S - 1 - ring * S, sz * S - 1 - ring * S], radius=max(r - ring, 1) * S, fill=(0, 0, 0, 0))
    core = big.resize((sz, sz), Image.LANCZOS)
    im = Image.new('RGBA', (sz + 2, sz + 2), (0, 0, 0, 0))
    im.paste(core, (1, 1))
    for k in range(r, r + 2):  # franja central estirable
        im.putpixel((k + 1, 0), (0, 0, 0, 255)); im.putpixel((0, k + 1), (0, 0, 0, 255))
    save(im, name)

ninepatch('pill.9.png', 40)          # botones y barra: píldora
ninepatch('card.9.png', 14)          # fondo redondeado de tarjetas
ninepatch('ring.9.png', 14, ring=5)  # anillo blanco del foco

def icon(name, draw, size=96, color=(255, 255, 255, 255)):
    big = Image.new('RGBA', (size * S, size * S), (0, 0, 0, 0))
    d = ImageDraw.Draw(big)
    draw(d, size * S, color)
    save(big.resize((size, size), Image.LANCZOS), name)

def play(d, s, c): d.polygon([(s*.30, s*.18), (s*.30, s*.82), (s*.82, s*.5)], fill=c)
def heart_pts(s, k=1.0):
    pts = []
    for i in range(361):
        t = math.radians(i)
        x = 16 * math.sin(t) ** 3
        y = 13 * math.cos(t) - 5 * math.cos(2*t) - 2 * math.cos(3*t) - math.cos(4*t)
        pts.append((s/2 + x * s/38 * k, s*.46 - y * s/38 * k))
    return pts
def heart(d, s, c): d.line(heart_pts(s) + [heart_pts(s)[0]], fill=c, width=int(s*.07), joint='curve')
def heartf(d, s, c): d.polygon(heart_pts(s), fill=c)
def replay(d, s, c):
    w = int(s*.075); box = [s*.22, s*.24, s*.78, s*.80]
    d.arc(box, 40, 340, fill=c, width=w)
    d.polygon([(s*.22, s*.12), (s*.22, s*.40), (s*.46, s*.26)], fill=c)
def search(d, s, c):
    w = int(s*.08); d.ellipse([s*.18, s*.18, s*.62, s*.62], outline=c, width=w)
    d.line([(s*.57, s*.57), (s*.82, s*.82)], fill=c, width=w)
def skip(d, s, c):
    d.polygon([(s*.2, s*.22), (s*.2, s*.78), (s*.55, s*.5)], fill=c)
    d.polygon([(s*.5, s*.22), (s*.5, s*.78), (s*.85, s*.5)], fill=c)
def check(d, s, c): d.line([(s*.2, s*.55), (s*.42, s*.76), (s*.82, s*.28)], fill=c, width=int(s*.1), joint='curve')
def plus(d, s, c):
    w = int(s*.09); d.line([(s*.5, s*.2), (s*.5, s*.8)], fill=c, width=w); d.line([(s*.2, s*.5), (s*.8, s*.5)], fill=c, width=w)
for n, f in [('play', play), ('heart', heart), ('replay', replay), ('search', search), ('skip', skip), ('check', check), ('plus', plus)]:
    icon(f'ic_{n}.png', f)
icon('ic_heart_on.png', heartf, color=(245, 163, 196, 255))
icon('ic_play_dark.png', play, color=(0, 0, 0, 255))

# avatares redondos
for a in ('a1', 'a2'):
    im = Image.open(f'images/avatar_{a}.jpg').convert('RGBA').resize((128, 128), Image.LANCZOS)
    m = Image.new('L', (128 * S, 128 * S), 0); ImageDraw.Draw(m).ellipse([0, 0, 128*S-1, 128*S-1], fill=255)
    im.putalpha(m.resize((128, 128), Image.LANCZOS)); save(im, f'av_{a}.png')

# manchas de luz de la pantalla de activación
def blob(name, rgb, a):
    s = 512; im = Image.new('RGBA', (s, s)); px = im.load()
    for y in range(s):
        for x in range(s):
            r = math.hypot(x - s/2, y - s/2) / (s/2); v = max(0, 1 - r)
            px[x, y] = rgb + (int(a * 255 * v * v),)
    save(im, name)
blob('blob1.png', (90, 40, 60), .55); blob('blob2.png', (245, 163, 196), .22)

# logo + icono + splash
logo = Image.open('images/logo.png').convert('RGBA')
def on_black(w, h, lw):
    im = Image.new('RGB', (w, h), (0, 0, 0)); l = logo.resize((lw, int(lw * logo.height / logo.width)), Image.LANCZOS)
    im.paste(l, ((w - l.width)//2, (h - l.height)//2), l); return im
on_black(540, 405, 420).save(f'{OUT}/icon_fhd.png'); on_black(290, 218, 230).save(f'{OUT}/icon_hd.png')
on_black(1920, 1080, 700).save(f'{OUT}/splash_fhd.png'); on_black(1280, 720, 470).save(f'{OUT}/splash_hd.png')
EXTRA, len(os.listdir(OUT)))
