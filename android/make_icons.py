"""Génère les deux icônes de l'application (lanceur et barre d'état)."""
import os
import sys
from PIL import Image, ImageDraw

out = os.path.join(os.path.dirname(os.path.abspath(__file__)), "res", "drawable")
os.makedirs(out, exist_ok=True)


def glyph(d, s, color, width):
    # Une arche de pont entre deux piles : la « passerelle ».
    w = max(2, int(s * width))
    d.arc([s * 0.18, s * 0.30, s * 0.82, s * 0.94], 180, 360, fill=color, width=w)
    d.line([s * 0.12, s * 0.62, s * 0.88, s * 0.62], fill=color, width=w)
    for x in (0.30, 0.50, 0.70):
        d.line([s * x, s * 0.40 if x == 0.5 else s * 0.45, s * x, s * 0.62],
               fill=color, width=max(1, w // 2))


def launcher(size=192):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.ellipse([4, 4, size - 4, size - 4], fill=(0, 112, 160, 255))
    glyph(d, size, (255, 255, 255, 255), 0.07)
    img.save(os.path.join(out, "ic_launcher.png"))


def status(size=72):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    glyph(ImageDraw.Draw(img), size, (255, 255, 255, 255), 0.10)
    img.save(os.path.join(out, "ic_stat.png"))


if __name__ == "__main__":
    launcher()
    status()
    sys.exit(0)
