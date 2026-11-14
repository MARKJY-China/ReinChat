from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

OUT = Path(r"I:\Project\ReinChat\server\web")
WHITE = (255, 255, 255, 255)
GREEN = (11, 138, 85, 255)
FONT = r"C:\Windows\Fonts\georgiab.ttf"

def render(size: int) -> Image.Image:
    image = Image.new("RGBA", (size, size), WHITE)
    draw = ImageDraw.Draw(image)
    font = ImageFont.truetype(FONT, int(size * 0.76))
    box = draw.textbbox((0, 0), "R", font=font)
    width = box[2] - box[0]
    height = box[3] - box[1]
    x = (size - width) / 2 - box[0]
    y = (size - height) / 2 - box[1] - size * 0.015
    draw.text((x, y), "R", font=font, fill=GREEN)
    return image

for size, name in [(512, "icon-512.png"), (192, "icon-192.png"), (180, "apple-touch-icon.png")]:
    render(size).save(OUT / name, "PNG", optimize=True)
render(64).save(OUT / "favicon.ico", sizes=[(16, 16), (32, 32), (48, 48), (64, 64)])
(OUT / "icon.svg").write_text(
    """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512">
<rect width="512" height="512" fill="#ffffff"/>
<text x="256" y="400" text-anchor="middle" font-family="Georgia, 'Times New Roman', serif" font-weight="700" font-style="italic" font-size="390" fill="#0b8a55">R</text>
</svg>""",
    encoding="utf-8",
)
print("icons written")
