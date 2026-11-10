"""Render review-only launcher icon concepts. Does not touch Android resources."""

from pathlib import Path
from PIL import Image, ImageDraw, ImageFilter

OUT = Path(__file__).parent
S = 4
SIZE = 512 * S


def px(v):
    return round(v * S)


def base():
    im = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rounded_rectangle((px(18), px(18), px(494), px(494)), radius=px(108), fill="#0D172B")
    d.rounded_rectangle((px(20), px(20), px(492), px(492)), radius=px(106), outline="#304766", width=px(2))
    return im


def glow(im, painter, color="#3BDCF5", blur=15, opacity=100):
    mask = Image.new("L", (SIZE, SIZE), 0)
    painter(ImageDraw.Draw(mask), opacity)
    halo = Image.new("RGBA", (SIZE, SIZE), color)
    halo.putalpha(mask.filter(ImageFilter.GaussianBlur(px(blur))))
    im.alpha_composite(halo)


def finish(im, name):
    im.resize((512, 512), Image.Resampling.LANCZOS).save(OUT / name)


def ring():
    im = base()
    d = ImageDraw.Draw(im)
    box = (px(111), px(111), px(401), px(401))
    d.arc(box, 0, 359, fill="#31465F", width=px(34))
    def arc(p, value):
        p.arc(box, -90, 180, fill=value, width=px(37))
    glow(im, arc, blur=13, opacity=155)
    d = ImageDraw.Draw(im)
    arc(d, "#4CDFF5")
    d.arc(box, -90, 34, fill="#80F3FA", width=px(34))
    d.ellipse((px(195), px(195), px(317), px(317)), fill="#060C1B", outline="#2C4968", width=px(3))
    d.ellipse((px(226), px(226), px(286), px(286)), fill="#142742")
    d.ellipse((px(236), px(236), px(252), px(252)), fill="#6B95B2")
    finish(im, "a-cutout-ring.png")


def edge():
    im = base()
    d = ImageDraw.Draw(im)
    d.rounded_rectangle((px(131), px(86), px(382), px(426)), radius=px(49), fill="#111E35", outline="#415A7A", width=px(8))
    d.rounded_rectangle((px(207), px(105), px(305), px(124)), radius=px(10), fill="#050B18")
    d.rounded_rectangle((px(358), px(140), px(395), px(382)), radius=px(18), fill="#2D4966")
    def bar(p, value):
        p.rounded_rectangle((px(358), px(140), px(395), px(349)), radius=px(18), fill=value)
    glow(im, bar, blur=17, opacity=170)
    d = ImageDraw.Draw(im)
    bar(d, "#4DDEF4")
    d.ellipse((px(357), px(121), px(396), px(160)), fill="#A3F7FC")
    d.rounded_rectangle((px(174), px(261), px(184), px(345)), radius=px(5), fill="#385577")
    d.rounded_rectangle((px(174), px(303), px(184), px(345)), radius=px(5), fill="#6BDDF1")
    finish(im, "b-edge-bar.png")


def screen():
    im = base()
    d = ImageDraw.Draw(im)
    box = (px(103), px(91), px(409), px(419))
    d.rounded_rectangle(box, radius=px(81), outline="#304962", width=px(30))
    # A long continuous run around the screen, with the unfilled gap at upper left.
    points = [(256, 91), (329, 91), (360, 97), (387, 117), (404, 148),
              (409, 178), (409, 333), (403, 364), (387, 392), (357, 412),
              (329, 419), (183, 419), (151, 412), (125, 392), (107, 364),
              (103, 333), (103, 184)]
    coords = [(px(x), px(y)) for x, y in points]
    def path(p, value):
        p.line(coords, fill=value, width=px(31), joint="curve")
        for x, y in (points[0], points[-1]):
            p.ellipse((px(x-16), px(y-16), px(x+16), px(y+16)), fill=value)
    glow(im, path, blur=14, opacity=160)
    d = ImageDraw.Draw(im)
    path(d, "#49DFF0")
    d.ellipse((px(225), px(135), px(287), px(197)), fill="#050C1B", outline="#42617D", width=px(3))
    d.ellipse((px(245), px(155), px(267), px(177)), fill="#254361")
    finish(im, "c-screen-loop.png")


if __name__ == "__main__":
    ring()
    edge()
    screen()
