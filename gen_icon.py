from PIL import Image, ImageDraw

SIZE = 432


def bezier(points, steps=120):
    pts = []
    n = len(points) - 1
    for i in range(steps + 1):
        t = i / steps
        px = py = 0.0
        for k, (x, y) in enumerate(points):
            b = comb(n, k) * (t ** k) * ((1 - t) ** (n - k))
            px += x * b
            py += y * b
        pts.append((px, py))
    return pts


def comb(n, k):
    import math
    return math.comb(n, k)


def teardrop_polygon(w, h, inset):
    # 与模块背景同款：左边平、右边鼓成水滴
    left = inset
    right = w - inset
    top = inset
    bottom = h - inset
    cy = (top + bottom) / 2
    r = (bottom - top) / 2
    tip = right
    pts = [(left, top)]
    pts += bezier([(left, top), (left + (tip - left) * 0.55, top),
                   (tip, cy - r * 0.55), (tip, cy)])
    pts += bezier([(tip, cy), (tip, cy + r * 0.55),
                   (left + (tip - left) * 0.55, bottom), (left, bottom)])
    pts.append((left, bottom))
    return pts


def gradient(size, c1, c2):
    img = Image.new('RGB', (size, size))
    d = ImageDraw.Draw(img)
    for y in range(size):
        t = y / max(1, size - 1)
        color = tuple(int(c1[i] + (c2[i] - c1[i]) * t) for i in range(3))
        d.line([(0, y), (size, y)], fill=color)
    return img


def render(mono=False):
    S = SIZE
    img = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    inset = S * 0.18
    poly = teardrop_polygon(S, S, inset)
    mask = Image.new('L', (S, S), 0)
    ImageDraw.Draw(mask).polygon(poly, fill=255)

    if mono:
        layer = Image.new('RGBA', (S, S), (255, 255, 255, 255))
    else:
        grad = gradient(S, (255, 122, 168), (138, 92, 255)).convert('RGBA')
        layer = Image.new('RGBA', (S, S), (0, 0, 0, 0))
        layer.paste(grad, (0, 0), mask)
        # 顶部高光
        hl = Image.new('L', (S, S), 0)
        hd = ImageDraw.Draw(hl)
        hd.ellipse([inset - 20, inset - 40, S - inset + 10, S * 0.55], fill=60)
        white = Image.new('RGBA', (S, S), (255, 255, 255, 255))
        layer.paste(white, (0, 0), Image.composite(hl, Image.new('L', (S, S), 0), mask))
        layer.putalpha(mask)

    d = ImageDraw.Draw(layer)
    # 向右的箭头
    cx, cy = S * 0.52, S * 0.5
    w = S * 0.20
    h = S * 0.17
    width = int(S * 0.045)
    d.line([(cx - w / 2, cy - h), (cx + w / 2, cy), (cx - w / 2, cy + h)],
           fill=(255, 255, 255, 255) if not mono else (255, 255, 255, 255),
           width=width, joint='curve')
    return layer


base = 'app/src/main/res'
for scale, folder in [(1.0, 'mipmap-mdpi'), (1.5, 'mipmap-hdpi'), (2.0, 'mipmap-xhdpi'),
                      (3.0, 'mipmap-xxhdpi'), (4.0, 'mipmap-xxxhdpi')]:
    size = int(SIZE * scale / 4 * 1.0) if False else int(108 * scale)
    for mono, name in [(False, 'ic_launcher_foreground'), (True, 'ic_launcher_monochrome')]:
        img = render(mono).resize((size, size), Image.LANCZOS)
        img.save(f'{base}/{folder}/{name}.png')
print('icons written')
