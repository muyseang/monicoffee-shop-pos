"""Simple illustrated product images for the demo menu (seed_demo).

Drawn with Pillow so the repo carries no binary image files and no photo
licensing questions. Each image is a flat illustration — hot cup, iced glass,
teacup or pastry — in its category's colours, with the item name underneath.
"""

import io

from PIL import Image, ImageDraw, ImageFont

SIZE = 600
SCALE = 2  # draw at 2x, then downscale, for smooth (anti-aliased) edges

# kind -> (background, accent, drink/food colour)
PALETTES = {
    "hot": ("#F3E9DC", "#1B6B45", "#7B4A2D"),
    "iced": ("#E3F1EA", "#1B6B45", "#A0673F"),
    "tea": ("#EEF3E2", "#1B6B45", "#8DAA4B"),
    "pastry": ("#FBEBD8", "#B5651D", "#E0A458"),
}


def render(name, kind, colour=None):
    """PNG bytes of a 600x600 illustration for `name` in the `kind` style.

    `colour` overrides the drink/food colour (e.g. dark brown for a chocolate
    cake), so items of the same kind don't all look identical."""
    background, accent, fill = PALETTES[kind]
    fill = colour or fill
    s = SIZE * SCALE
    image = Image.new("RGB", (s, s), background)
    draw = ImageDraw.Draw(image)

    # Shapes use fractions of the canvas, so they don't depend on SCALE.
    def point(x, y):
        return (round(x * s), round(y * s))

    def box(x0, y0, x1, y1):
        return [*point(x0, y0), *point(x1, y1)]

    draw.ellipse(box(0.16, 0.08, 0.84, 0.76), fill="#FFFFFF")  # soft backdrop
    shape = {"hot": _hot_cup, "iced": _iced_glass, "tea": _teacup, "pastry": _pastry}[kind]
    shape(draw, box, point, accent, fill)

    font = _font(round(0.075 * s))
    label = name if draw.textlength(name, font=font) < 0.9 * s else name[:22] + "…"
    width = draw.textlength(label, font=font)
    draw.text(((s - width) / 2, 0.82 * s), label, font=font, fill="#1E1E1E")

    buffer = io.BytesIO()
    image.resize((SIZE, SIZE), Image.LANCZOS).save(buffer, "PNG", optimize=True)
    return buffer.getvalue()


def _font(size):
    try:
        return ImageFont.load_default(size=size)  # FreeType default font, Pillow >= 10.1
    except (TypeError, OSError):
        return ImageFont.load_default()


def _hot_cup(draw, box, point, accent, fill):
    draw.ellipse(box(0.20, 0.58, 0.80, 0.70), fill="#D9CBB8")  # saucer
    draw.rounded_rectangle(box(0.30, 0.34, 0.66, 0.64), radius=60, fill=accent)  # cup body
    draw.ellipse(box(0.60, 0.40, 0.76, 0.56), outline=accent, width=26)  # handle
    draw.ellipse(box(0.30, 0.29, 0.66, 0.39), fill=fill)  # coffee surface
    draw.ellipse(box(0.43, 0.31, 0.53, 0.37), fill="#F3E9DC")  # latte-art dot
    for x in (0.40, 0.48, 0.56):  # steam
        draw.arc(box(x - 0.03, 0.14, x + 0.03, 0.27), 250, 110, fill="#B9A890", width=14)


def _iced_glass(draw, box, point, accent, fill):
    draw.polygon(
        [point(0.33, 0.24), point(0.67, 0.24),
         point(0.62, 0.68), point(0.38, 0.68)],
        fill="#DDEFF5",
    )  # glass
    draw.polygon(
        [point(0.345, 0.36), point(0.655, 0.36),
         point(0.615, 0.66), point(0.385, 0.66)],
        fill=fill,
    )  # drink
    for x0, y0 in ((0.39, 0.38), (0.50, 0.42), (0.44, 0.49)):  # ice cubes
        draw.rounded_rectangle(box(x0, y0, x0 + 0.09, y0 + 0.08), radius=14, fill="#F4FBFD")
    draw.line(box(0.56, 0.12, 0.52, 0.40), fill=accent, width=22)  # straw


def _teacup(draw, box, point, accent, fill):
    draw.ellipse(box(0.22, 0.60, 0.78, 0.70), fill="#D7DEC8")  # saucer
    draw.chord(box(0.27, 0.30, 0.71, 0.70), 0, 180, fill=accent)  # bowl
    draw.ellipse(box(0.27, 0.44, 0.71, 0.54), fill=fill)  # tea surface
    draw.ellipse(box(0.66, 0.48, 0.80, 0.60), outline=accent, width=22)  # handle
    draw.line(box(0.42, 0.20, 0.42, 0.47), fill="#9C9C9C", width=8)  # tea-bag string
    draw.rectangle(box(0.38, 0.16, 0.46, 0.23), fill="#F2C14E")  # tag


def _pastry(draw, box, point, accent, fill):
    draw.ellipse(box(0.18, 0.56, 0.82, 0.70), fill="#FFFFFF", outline="#E8D9C5", width=10)  # plate
    draw.polygon(
        [point(0.28, 0.58), point(0.72, 0.58),
         point(0.64, 0.32), point(0.36, 0.32)],
        fill=fill,
    )  # slice
    draw.rectangle(box(0.31, 0.44, 0.69, 0.48), fill="#FFF4E0")  # cream layer
    draw.ellipse(box(0.46, 0.22, 0.54, 0.31), fill=accent)  # cherry
