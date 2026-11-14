from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter
import numpy as np

W, H = 1200, 800
y, x = np.mgrid[0:H, 0:W].astype(np.float32)
nx, ny = x / W, y / H
c1 = np.array([10, 28, 26], dtype=np.float32)
c2 = np.array([4, 9, 8], dtype=np.float32)
t = np.clip(ny * .7 + nx * .3, 0, 1)[..., None]
base = c1 * (1 - t) + c2 * t
img = Image.fromarray(np.uint8(base), "RGB").convert("RGBA")

over = Image.new("RGBA", (W, H), (0, 0, 0, 0))
d = ImageDraw.Draw(over)
for cx, cy, r, col, a in [
    (270, 180, 420, (36, 231, 174), 115),
    (1020, 230, 400, (43, 166, 255), 80),
    (760, 760, 500, (146, 121, 255), 75),
    (120, 700, 350, (255, 182, 119), 35),
]:
    d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=col + (a,))
over = over.filter(ImageFilter.GaussianBlur(80))
img = Image.alpha_composite(img, over)

for cx, cy, r in [(440, 390, 145), (680, 340, 80), (790, 500, 115)]:
    layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    dd = ImageDraw.Draw(layer)
    dd.ellipse(
        (cx - r, cy - r, cx + r, cy + r),
        fill=(180, 255, 235, 38),
        outline=(255, 255, 255, 95),
        width=3,
    )
    dd.ellipse(
        (cx - r * .45, cy - r * .55, cx - r * .05, cy - r * .15),
        fill=(255, 255, 255, 95),
    )
    layer = layer.filter(ImageFilter.GaussianBlur(1.2))
    img = Image.alpha_composite(img, layer)

out = Path(r"I:\Project\ReinChat\app\src\debug\res\raw\liquid_demo.jpg")
out.parent.mkdir(parents=True, exist_ok=True)
img.convert("RGB").save(out, quality=92)
print("image created:", out)
