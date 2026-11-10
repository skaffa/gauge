"""Generates the Gauge icon as 32x32 pixel art, in the Ghost Mason / Ortho icon style.

Style: dark rounded-square badge (#10161C) with 4-px stepped corners, soft radial glow, a block
character with a cute face, cream 4-point sparkles. Subject: a redstone-red cube under a dial whose
arc runs through the 0-15 power colours Gauge uses for dust, with a needle near full power.

Usage: python tools/make_icon.py  -> media/icon.png (512), media/icon-128.png (128)
"""
import math
import os
import sys

from PIL import Image

N = 32
BG = (16, 22, 28, 255)
GLOW = (60, 26, 24)
TOP = (255, 150, 140, 255)
RIM = (255, 210, 200, 255)
LEFT = (230, 60, 50, 255)
RIGHT = (170, 34, 30, 255)
EDGE = (110, 18, 18, 255)
EYE = (40, 8, 8, 255)
EYE_HI = (255, 235, 230, 255)
OFF = (112, 112, 112)
NEEDLE = (255, 236, 140, 255)
HUB = (255, 250, 220, 255)
SPARK = (255, 236, 140)


def power_color(p):
    """Inspector.powerColor: grey at 0, dark to bright red as it rises."""
    if p <= 0:
        return OFF + (255,)
    return (255, 0x20 + p * 9, 0x20 + p * 6, 255)


def blend(dst, src):
    """Alpha-composite src (r,g,b,a) over dst (r,g,b,a)."""
    sa = src[3] / 255
    da = dst[3] / 255
    oa = sa + da * (1 - sa)
    if oa == 0:
        return (0, 0, 0, 0)
    c = tuple(round((src[i] * sa + dst[i] * da * (1 - sa)) / oa) for i in range(3))
    return c + (round(oa * 255),)


def corner_cut(x, y):
    """Stepped rounded corners: 4,3,2,1 transparent pixels on rows 0..3 (mirrored)."""
    steps = [4, 3, 2, 1]
    for yy, xx in ((y, x), (N - 1 - y, x), (y, N - 1 - x), (N - 1 - y, N - 1 - x)):
        if yy < 4 and xx < steps[yy]:
            return True
    return False



def main(out_dir):
    px = [[(0, 0, 0, 0)] * N for _ in range(N)]

    def put(x, y, c):
        if 0 <= x < N and 0 <= y < N and not corner_cut(x, y):
            px[y][x] = blend(px[y][x], c) if c[3] < 255 else c

    for y in range(N):
        for x in range(N):
            if corner_cut(x, y):
                continue
            d = math.hypot(x + 0.5 - 16, y + 0.5 - 15) / 15
            t = max(0.0, 1 - d) ** 1.6
            px[y][x] = tuple(round(BG[i] + (GLOW[i] - BG[i]) * t) for i in range(3)) + (255,)

    # dial: upper half ring, dark red at power 0 (left) to bright red at 15 (right)
    cx, cy, r0, r1 = 16, 17, 10.6, 13.4
    lo, hi = (92, 26, 26), (255, 84, 60)
    for y in range(N):
        for x in range(N):
            fx, fy = x + 0.5 - cx, y + 0.5 - cy
            d = math.hypot(fx, fy)
            if r0 <= d <= r1 and fy < 0:
                t = (180 - math.degrees(math.atan2(-fy, fx))) / 180
                c = tuple(round(lo[i] + (hi[i] - lo[i]) * t) for i in range(3))
                if d > r1 - 0.9:
                    c = tuple(round(v * 0.72) for v in c)  # outer shade
                put(x, y, c + (255,))

    # isometric cube with a face (sits in the dial)
    xc, yc, W, H, F = 16, 15, 7, 3.5, 7
    for y in range(N):
        for x in range(N):
            fx, fy = x + 0.5, y + 0.5
            if abs(fx - xc) / W + abs(fy - yc) / H <= 1.0:
                put(x, y, TOP)
                continue
            if xc - W <= fx < xc:
                te = yc + (fx - (xc - W)) / 2
                if te <= fy < te + F:
                    put(x, y, LEFT)
            elif xc <= fx < xc + W:
                te = yc + ((xc + W) - fx) / 2
                if te <= fy < te + F:
                    put(x, y, RIGHT)
    for x in range(xc - W, xc + W):
        fx = x + 0.5
        top = yc + ((fx - (xc - W)) if fx < xc else ((xc + W) - fx)) / 2
        put(x, math.floor(top - 0.01), RIM)
    for y in range(int(yc + H), int(yc + H + F)):
        put(16, y, EDGE)
    for ex, ey in ((11, 19), (13, 20)):
        for dy in range(3):
            put(ex, ey + dy, EYE)
        put(ex, ey, EYE_HI)

    # needle from the cube top toward power ~13
    ang = math.radians(180 - 13.5 / 16 * 180)
    for i in range(22):
        t = 3 + i * 0.4
        put(int(cx + 0.5 + math.cos(ang) * t), int(cy - 4 + 0.5 - math.sin(ang) * t * 0.95), NEEDLE)
    put(16, 13, HUB)
    put(15, 13, HUB)

    def sparkle(x, y, strong=True):
        put(x, y, SPARK + (255,))
        a = 200 if strong else 140
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            put(x + dx, y + dy, SPARK + (a,))

    sparkle(4, 27, strong=False)
    sparkle(27, 27)

    img = Image.new("RGBA", (N, N))
    img.putdata([c for row in px for c in row])
    os.makedirs(out_dir, exist_ok=True)
    img.resize((512, 512), Image.NEAREST).save(os.path.join(out_dir, "icon.png"))
    img.resize((128, 128), Image.NEAREST).save(os.path.join(out_dir, "icon-128.png"))
    print("wrote", out_dir)


if __name__ == "__main__":
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    main(sys.argv[1] if len(sys.argv) > 1 else os.path.join(root, "media"))
