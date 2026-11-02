"""Generates the Android icons.

The launcher icon is resized from icons/callbridge-source.png (the shared
project icon, in the Sailfish OS shape). The status-bar icon is a small white
bridge drawn here, since Android requires a white silhouette for it.
"""
import os
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "res", "drawable")
SRC = os.path.join(HERE, "..", "icons", "callbridge-source.png")
os.makedirs(OUT, exist_ok=True)


def status(size=72):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    w = max(2, int(size * 0.10))
    white = (255, 255, 255, 255)
    d.arc([size * 0.18, size * 0.30, size * 0.82, size * 0.94], 180, 360, fill=white, width=w)
    d.line([size * 0.12, size * 0.62, size * 0.88, size * 0.62], fill=white, width=w)
    for x in (0.30, 0.50, 0.70):
        top = 0.40 if x == 0.5 else 0.45
        d.line([size * x, size * top, size * x, size * 0.62], fill=white, width=max(1, w // 2))
    img.save(os.path.join(OUT, "ic_stat.png"))


if __name__ == "__main__":
    Image.open(SRC).resize((192, 192), Image.LANCZOS).save(os.path.join(OUT, "ic_launcher.png"))
    status()
