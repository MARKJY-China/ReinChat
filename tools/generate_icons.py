from PIL import Image, ImageDraw, ImageFont, ImageFilter
import numpy as np, os

ROOT = r"I:\Project\ReinChat\app\src\main\res"
SUP = 4


def background(size):
    S = size * SUP
    yy, xx = np.mgrid[0:S, 0:S].astype(np.float32)
    nx, ny = xx / (S - 1), yy / (S - 1)
    top = np.array([11, 23, 20], dtype=np.float32)
    bottom = np.array([3, 7, 6], dtype=np.float32)
    t = np.clip(ny * 0.9 + nx * 0.25, 0, 1)[..., None]
    base = top * (1 - t) + bottom * t
    img = Image.fromarray(np.uint8(np.clip(base, 0, 255)), "RGB").convert("RGBA")
    overlay = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(overlay)

    def blob(cx, cy, r, color, alpha):
        d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=color + (alpha,))

    blob(S * 0.18, S * 0.12, S * 0.52, (116, 245, 200), 95)
    blob(S * 0.88, S * 0.33, S * 0.48, (95, 195, 238), 55)
    blob(S * 0.58, S * 0.92, S * 0.48, (166, 145, 255), 45)
    overlay = overlay.filter(ImageFilter.GaussianBlur(S * 0.12))
    img = Image.alpha_composite(img, overlay)
    yy, xx = np.mgrid[0:S, 0:S].astype(np.float32)
    cx = cy = (S - 1) / 2
    dist = np.sqrt((xx - cx) ** 2 + (yy - cy) ** 2) / (S * 0.72)
    vig = np.clip((dist - 0.45) / 0.9, 0, 1)[..., None] * 0.55
    arr = np.array(img).astype(np.float32)
    arr[..., :3] = arr[..., :3] * (1 - vig) + np.array([2, 5, 4]) * vig
    return Image.fromarray(np.uint8(np.clip(arr, 0, 255)), "RGBA")


def orb_layer(canvas_size, radius):
    S = canvas_size
    cx = cy = S / 2
    R = radius
    yy, xx = np.mgrid[0:S, 0:S].astype(np.float32)
    dx = (xx - cx) / R
    dy = (yy - cy) / R
    dist = np.sqrt(dx * dx + dy * dy)
    t = np.clip((dx * 0.34 + dy * 0.46 + 1.0) / 2.0, 0, 1)
    stops = [
        (0.00, (244, 255, 250)),
        (0.18, (166, 250, 221)),
        (0.42, (116, 230, 204)),
        (0.68, (118, 199, 213)),
        (0.86, (158, 164, 241)),
        (1.00, (186, 164, 255)),
    ]

    def color_at(v):
        v = float(np.clip(v, 0, 1))
        for i in range(len(stops) - 1):
            a, ca = stops[i]
            b, cb = stops[i + 1]
            if a <= v <= b:
                tt = (v - a) / (b - a) if b > a else 0
                return tuple(int(ca[k] + (cb[k] - ca[k]) * tt) for k in range(3))
        return stops[-1][1]

    lut_n = 256
    lut = np.array([color_at(i / (lut_n - 1)) for i in range(lut_n)], dtype=np.uint8)
    idx = np.clip((t * lut_n).astype(np.int32), 0, lut_n - 1)
    base = np.zeros((S, S, 4), dtype=np.uint8)
    base[..., :3] = lut[idx]
    alpha = np.clip((1.0 - dist) * R * 1.25 + 0.5, 0, 1)
    base[..., 3] = np.uint8(alpha * 255)
    orbimg = Image.fromarray(base, "RGBA")

    glow = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(glow)
    d.ellipse((cx - R * 0.76, cy - R * 0.78, cx - R * 0.12, cy - R * 0.16),
              fill=(255, 255, 255, 140))
    glow = glow.filter(ImageFilter.GaussianBlur(R * 0.18))
    orbimg = Image.alpha_composite(orbimg, glow)

    shade = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(shade)
    d.ellipse((cx + R * 0.10, cy + R * 0.20, cx + R * 1.05, cy + R * 1.12),
              fill=(1, 30, 24, 78))
    shade = shade.filter(ImageFilter.GaussianBlur(R * 0.16))
    orbimg = Image.alpha_composite(orbimg, shade)

    d = ImageDraw.Draw(orbimg)
    for k in range(2):
        d.ellipse((cx - R + k, cy - R + k, cx + R - k, cy + R - k),
                  outline=(255, 255, 255, 150 - k * 45),
                  width=max(2, int(R * 0.010)))

    spec = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(spec)
    d.ellipse((cx + R * 0.34, cy - R * 0.52, cx + R * 0.52, cy - R * 0.34),
              fill=(255, 255, 255, 90))
    spec = spec.filter(ImageFilter.GaussianBlur(R * 0.035))
    return Image.alpha_composite(orbimg, spec)


def draw_letter(img, S, radius):
    font_path = r"C:\Windows\Fonts\segoeuib.ttf"
    font = ImageFont.truetype(font_path, int(radius * 0.94))
    d = ImageDraw.Draw(img)
    text = "R"
    bbox = d.textbbox((0, 0), text, font=font)
    w = bbox[2] - bbox[0]
    h = bbox[3] - bbox[1]
    x = (S - w) / 2 - bbox[0]
    y = (S - h) / 2 - bbox[1] - radius * 0.03
    d.text((x, y), text, font=font, fill=(5, 32, 25, 255))
    return img


def icon(size, rounded=False, circular=False):
    S = size * SUP
    bg = background(size).resize((S, S), Image.LANCZOS)
    if circular:
        mask = Image.new("L", (S, S), 0)
        ImageDraw.Draw(mask).ellipse((0, 0, S - 1, S - 1), fill=255)
        bg.putalpha(mask)
    elif rounded:
        mask = Image.new("L", (S, S), 0)
        rad = int(S * 0.225)
        ImageDraw.Draw(mask).rounded_rectangle((0, 0, S - 1, S - 1), radius=rad, fill=255)
        bg.putalpha(mask)
    orb = orb_layer(S, S * 0.305)
    orb = draw_letter(orb, S, S * 0.305)
    bg = Image.alpha_composite(bg, orb)
    return bg.resize((size, size), Image.LANCZOS)


def adaptive_fg(size=432):
    S = size * SUP
    layer = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    aura = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(aura)
    d.ellipse((S * 0.24, S * 0.24, S * 0.76, S * 0.76), fill=(139, 243, 206, 45))
    aura = aura.filter(ImageFilter.GaussianBlur(S * 0.07))
    layer = Image.alpha_composite(layer, aura)
    orb = draw_letter(orb_layer(S, S * 0.292), S, S * 0.292)
    layer = Image.alpha_composite(layer, orb)
    return layer.resize((size, size), Image.LANCZOS)


sizes = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
for density, px in sizes.items():
    img = icon(px, rounded=True)
    img.save(os.path.join(ROOT, f"mipmap-{density}", "ic_launcher.png"), optimize=True)
    rnd = icon(px, circular=True)
    rnd.save(os.path.join(ROOT, f"mipmap-{density}", "ic_launcher_round.png"), optimize=True)

adaptive_fg(432).save(os.path.join(ROOT, "drawable-nodpi", "ic_launcher_foreground.png"), optimize=True)
background(432).resize((432, 432), Image.LANCZOS).save(
    os.path.join(ROOT, "drawable-nodpi", "ic_launcher_background.png"), optimize=True
)
icon(512).convert("RGB").save(r"C:\Users\Administrator\ReinChat-icon-preview.png", quality=95)
print("icons generated")
