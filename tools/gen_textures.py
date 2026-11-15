#!/usr/bin/env python3
"""
Generates the AI Mobs textures.

The entity model uses the vanilla 64x64 player-skin layout, so anything this
script writes can be replaced with a normal Minecraft skin PNG.

    python3 tools/gen_textures.py
"""

import os
import struct
import zlib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ENTITY_DIR = os.path.join(ROOT, "src/main/resources/assets/aimobs/textures/entity")
ICON = os.path.join(ROOT, "src/main/resources/assets/aimobs/icon.png")


# ----------------------------------------------------------------------
# Tiny PNG writer
# ----------------------------------------------------------------------
def write_png(path, pixels):
    height = len(pixels)
    width = len(pixels[0])
    raw = bytearray()
    for row in pixels:
        raw.append(0)  # filter type 0
        for px in row:
            raw.extend(px)

    def chunk(tag, data):
        out = struct.pack(">I", len(data)) + tag + data
        return out + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
    png += chunk(b"IEND", b"")

    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(png)


# ----------------------------------------------------------------------
# Colour helpers
# ----------------------------------------------------------------------
def shade(hex_colour, factor):
    r = (hex_colour >> 16) & 0xFF
    g = (hex_colour >> 8) & 0xFF
    b = hex_colour & 0xFF
    r = max(0, min(255, int(r * factor)))
    g = max(0, min(255, int(g * factor)))
    b = max(0, min(255, int(b * factor)))
    return (r, g, b, 255)


def solid(hex_colour):
    return ((hex_colour >> 16) & 0xFF, (hex_colour >> 8) & 0xFF, hex_colour & 0xFF, 255)


class Canvas:
    def __init__(self, w, h, fill):
        self.w = w
        self.h = h
        self.px = [[fill for _ in range(w)] for _ in range(h)]

    def rect(self, x, y, w, h, colour):
        for yy in range(y, y + h):
            if 0 <= yy < self.h:
                for xx in range(x, x + w):
                    if 0 <= xx < self.w:
                        self.px[yy][xx] = colour

    def save(self, path):
        write_png(path, self.px)


# ----------------------------------------------------------------------
# Box unwrap helper (matches Minecraft's CubeListBuilder UV layout)
# ----------------------------------------------------------------------
def faces(u, v, w, h, d):
    return {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h),
        "back": (u + d + w + d, v + d, w, h),
    }


# head, body, arms and legs in the standard 64x64 skin layout
HEAD = faces(0, 0, 8, 8, 8)
BODY = faces(16, 16, 8, 12, 4)
RIGHT_ARM = faces(40, 16, 4, 12, 4)
RIGHT_LEG = faces(0, 16, 4, 12, 4)
LEFT_ARM = faces(32, 48, 4, 12, 4)
LEFT_LEG = faces(16, 48, 4, 12, 4)


def build_skin(base, dark, accent, eye):
    canvas = Canvas(64, 64, solid(base))

    # Overlay regions (unused by our model, but keeps the file a valid skin).
    for (u, v, w, h, d) in [
        (32, 0, 8, 8, 8),      # hat
        (16, 32, 8, 12, 4),    # jacket
        (40, 32, 4, 12, 4),    # right sleeve
        (48, 48, 4, 12, 4),    # left sleeve
        (0, 32, 4, 12, 4),     # right pant
        (0, 48, 4, 12, 4),     # left pant
    ]:
        for (fx, fy, fw, fh) in faces(u, v, w, h, d).values():
            canvas.rect(fx, fy, fw, fh, solid(base))

    # Slight shading on the sides of every limb so it does not look flat.
    for part in (RIGHT_ARM, LEFT_ARM, RIGHT_LEG, LEFT_LEG, BODY):
        for side in ("right", "left", "back"):
            (fx, fy, fw, fh) = part[side]
            canvas.rect(fx, fy, fw, fh, shade(base, 0.82))
        (tx, ty, tw, th) = part["top"]
        canvas.rect(tx, ty, tw, th, shade(base, 1.12))

    # ---- head ----
    (hx, hy, hw, hh) = HEAD["front"]
    canvas.rect(hx, hy, hw, hh, shade(base, 1.05))
    # visor band across the eyes
    canvas.rect(hx + 1, hy + 2, 6, 3, solid(dark))
    # eyes
    canvas.rect(hx + 2, hy + 3, 1, 1, solid(eye))
    canvas.rect(hx + 5, hy + 3, 1, 1, solid(eye))
    # mouth grille
    canvas.rect(hx + 2, hy + 6, 4, 1, solid(dark))
    # ear panels on the sides of the head
    for side in ("right", "left"):
        (sx, sy, sw, sh) = HEAD[side]
        canvas.rect(sx, sy + 2, sw, 3, solid(dark))
    (bx, by, bw, bh) = HEAD["back"]
    canvas.rect(bx + 2, by + 2, 4, 4, solid(dark))

    # ---- body: chest panel ----
    (bx, by, bw, bh) = BODY["front"]
    canvas.rect(bx + 2, by + 1, 4, 5, solid(dark))
    canvas.rect(bx + 3, by + 2, 2, 1, solid(accent))
    canvas.rect(bx + 3, by + 4, 2, 1, solid(accent))
    canvas.rect(bx + 1, by + 8, 6, 1, shade(base, 0.7))

    # ---- arms: wrist bands ----
    for part in (RIGHT_ARM, LEFT_ARM):
        (fx, fy, fw, fh) = part["front"]
        canvas.rect(fx, fy + fh - 3, fw, 2, solid(dark))
        (sx, sy, sw, sh) = part["right"]
        canvas.rect(sx, sy + sh - 3, sw, 2, solid(dark))

    # ---- legs: boots ----
    for part in (RIGHT_LEG, LEFT_LEG):
        (fx, fy, fw, fh) = part["front"]
        canvas.rect(fx, fy + fh - 2, fw, 2, solid(dark))
        (sx, sy, sw, sh) = part["right"]
        canvas.rect(sx, sy + sh - 2, sw, 2, solid(dark))
        (lx, ly, lw, lh) = part["left"]
        canvas.rect(lx, ly + lh - 2, lw, 2, solid(dark))

    return canvas


PALETTES = {
    # file            base       dark        accent      eye
    "claude": (0xD97757, 0x3A2320, 0xFFE3D3, 0xFFF3EC),
    "openai": (0x10A37F, 0x062E24, 0xC9FFEF, 0xEAFFF8),
    "gemini": (0x4285F4, 0x0B2A5B, 0xCFE4FF, 0xFFFFFF),
    "free": (0x9E9E9E, 0x2B2B2B, 0xE8E8E8, 0xFFFFFF),
}


def build_icon():
    """128x128 mod icon: a robot head on a dark rounded panel."""
    size = 128
    canvas = Canvas(size, size, (0, 0, 0, 0))

    # background panel
    for y in range(size):
        for x in range(size):
            # rounded corners
            cx = min(x, size - 1 - x)
            cy = min(y, size - 1 - y)
            if cx + cy < 10:
                continue
            t = y / (size - 1)
            r = int(18 + 12 * t)
            g = int(20 + 40 * t)
            b = int(32 + 60 * t)
            canvas.px[y][x] = (r, g, b, 255)

    # glow behind the head
    for y in range(size):
        for x in range(size):
            dx = (x - 64) / 44.0
            dy = (y - 62) / 44.0
            d = dx * dx + dy * dy
            if d < 1.0:
                px = canvas.px[y][x]
                strength = (1.0 - d) * 0.55
                canvas.px[y][x] = (
                    min(255, int(px[0] + 40 * strength)),
                    min(255, int(px[1] + 120 * strength)),
                    min(255, int(px[2] + 160 * strength)),
                    255,
                )

    # robot head
    head_x, head_y, head_w, head_h = 30, 26, 68, 56
    canvas.rect(head_x, head_y, head_w, head_h, (226, 232, 240, 255))
    canvas.rect(head_x, head_y + head_h - 6, head_w, 6, (170, 180, 196, 255))

    # antenna
    canvas.rect(62, 12, 4, 16, (140, 150, 168, 255))
    canvas.rect(56, 6, 16, 10, (79, 195, 247, 255))

    # visor
    canvas.rect(head_x + 8, head_y + 16, head_w - 16, 22, (18, 22, 30, 255))
    canvas.rect(head_x + 16, head_y + 23, 10, 8, (79, 227, 255, 255))
    canvas.rect(head_x + 42, head_y + 23, 10, 8, (79, 227, 255, 255))
    # mouth grille
    for i in range(5):
        canvas.rect(head_x + 18 + i * 7, head_y + 44, 4, 4, (140, 150, 168, 255))

    # side vents
    canvas.rect(head_x - 8, head_y + 14, 8, 26, (170, 180, 196, 255))
    canvas.rect(head_x + head_w, head_y + 14, 8, 26, (170, 180, 196, 255))

    return canvas


def main():
    for name, palette in PALETTES.items():
        canvas = build_skin(*palette)
        path = os.path.join(ENTITY_DIR, "ai_mob_%s.png" % name)
        canvas.save(path)
        print("wrote", path)

    icon = build_icon()
    icon.save(ICON)
    print("wrote", ICON)


if __name__ == "__main__":
    main()
