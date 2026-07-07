#!/usr/bin/env python3
"""Rasterize the config-screen button icons from the provided Material Symbols SVGs.

Each SVG is rendered at 128x128 and recoloured to pure white (alpha preserved) so the
game can tint it per button state (the black-outline trick isn't needed — these are
clean vector glyphs). A sibling `<name>.png.mcmeta` enables linear filtering so the
128px art downscales smoothly to the ~14px it's drawn at.

Run:  python3 gen_icons.py   ->   writes ./out/*.png (+ .mcmeta)
Then the PNGs + mcmeta are copied into src/main/resources/.../textures/gui/icons/.
"""

import io
from pathlib import Path

import cairosvg
from PIL import Image

SIZE = 16
GEN = Path(__file__).parent
OUT = GEN / "out"

# game icon name -> provided Material Symbols SVG
MAP = {
    "add":       "add_24dp_E3E3E3_FILL0_wght400_GRAD0_opsz24.svg",
    "delete":    "close_24dp_E3E3E3_FILL0_wght400_GRAD0_opsz24.svg",
    "duplicate": "add_row_above_24dp_E3E3E3_FILL0_wght400_GRAD0_opsz24.svg",
    "reset":     "refresh_24dp_E3E3E3_FILL0_wght400_GRAD0_opsz24.svg",
    "clear":     "delete_forever_24dp_E3E3E3_FILL0_wght400_GRAD0_opsz24.svg",
    "search":    "search_24dp_E3E3E3_FILL0_wght400_GRAD0_opsz24.svg",
    "dirty":     "circle_24dp_E3E3E3_FILL1_wght400_GRAD0_opsz24.svg",
    "check":     "check_24dp_E3E3E3_FILL1_wght400_GRAD0_opsz24.svg",
}

MCMETA = '{\n  "texture": {\n    "blur": true,\n    "clamp": true\n  }\n}\n'


def render(svg_path):
    png = cairosvg.svg2png(url=str(svg_path), output_width=SIZE, output_height=SIZE)
    img = Image.open(io.BytesIO(png)).convert("RGBA")
    alpha = img.split()[3]
    white = Image.new("L", img.size, 255)
    return Image.merge("RGBA", (white, white, white, alpha))


def main():
    OUT.mkdir(exist_ok=True)
    for name, svg in MAP.items():
        src = GEN / svg
        if not src.exists():
            raise SystemExit(f"missing SVG: {src}")
        render(src).save(OUT / f"{name}.png")
        (OUT / f"{name}.png.mcmeta").write_text(MCMETA)
        print(f"wrote {name}.png (+ .mcmeta)")


if __name__ == "__main__":
    main()
