#!/usr/bin/env python3
"""Generate a simple launcher icon PNG (blue rounded background + white circle)."""
import os, struct, zlib

def make_icon(size):
    cx = cy = size / 2.0
    radius = size * 0.42
    corner = size * 0.18
    rows = []
    for y in range(size):
        row = bytearray([0])  # filter type 0
        for x in range(size):
            in_corner = False
            if x < corner and y < corner:
                d = ((x - corner) ** 2 + (y - corner) ** 2) ** 0.5
                in_corner = d > corner
            elif x >= size - corner and y < corner:
                d = ((x - (size - corner)) ** 2 + (y - corner) ** 2) ** 0.5
                in_corner = d > corner
            elif x < corner and y >= size - corner:
                d = ((x - corner) ** 2 + (y - (size - corner)) ** 2) ** 0.5
                in_corner = d > corner
            elif x >= size - corner and y >= size - corner:
                d = ((x - (size - corner)) ** 2 + (y - (size - corner)) ** 2) ** 0.5
                in_corner = d > corner
            inside_rect = (corner <= x < size - corner) or (corner <= y < size - corner)
            bg = not (in_corner and not inside_rect)
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            circle = d <= radius
            if circle:
                row += bytes((255, 255, 255, 255))
            elif bg:
                row += bytes((33, 150, 243, 255))  # Material blue 500
            else:
                row += bytes((0, 0, 0, 0))
        rows.append(bytes(row))
    raw = b"".join(rows)

    def chunk(tag, data):
        c = struct.pack(">I", len(data)) + tag + data
        c += struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)
        return c

    ihdr = struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0)
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr) + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__))) + "/app/src/main/res"
for dpi, size in (("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)):
    d = os.path.join(BASE, f"mipmap-{dpi}")
    os.makedirs(d, exist_ok=True)
    with open(os.path.join(d, "ic_launcher.png"), "wb") as f:
        f.write(make_icon(size))
    with open(os.path.join(d, "ic_launcher_round.png"), "wb") as f:
        f.write(make_icon(size))
print("icons written")
