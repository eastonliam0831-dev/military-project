#!/usr/bin/env python3
"""Generates the built-in resource packs (camo vehicle skins + base decoration block textures).
Run from the project root:  python3 tools/gen_textures.py   (needs Pillow + numpy)"""
import os, json
import numpy as np
from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "resourcepacks")

def hash2(x, y, seed):
    h = (x * 374761393 + y * 668265263 + seed * 2246822519) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFF) / 65535.0

def vnoise(w, h, scale, seed):
    xs = np.arange(w) / scale; ys = np.arange(h) / scale
    xi = np.floor(xs).astype(int); yi = np.floor(ys).astype(int)
    xf = xs - xi; yf = ys - yi
    xf = xf * xf * (3 - 2 * xf); yf = yf * yf * (3 - 2 * yf)
    out = np.zeros((h, w))
    for j in range(h):
        for i in range(w):
            a = hash2(xi[i], yi[j], seed); b = hash2(xi[i] + 1, yi[j], seed)
            c = hash2(xi[i], yi[j] + 1, seed); d = hash2(xi[i] + 1, yi[j] + 1, seed)
            top = a + (b - a) * xf[i]; bot = c + (d - c) * xf[i]
            out[j, i] = top + (bot - top) * yf[j]
    return out

def fbm(w, h, scale, seed):
    return vnoise(w, h, scale, seed) * 0.6 + vnoise(w, h, scale / 2.1, seed + 7) * 0.3 + vnoise(w, h, scale / 4.3, seed + 13) * 0.1

PALETTES = {
    "woodland": [(75, 90, 42), (47, 59, 28), (107, 90, 58), (22, 25, 15)],
    "desert":   [(194, 168, 120), (168, 137, 90), (140, 112, 72), (216, 199, 154)],
    "urban":    [(138, 141, 144), (94, 97, 101), (58, 60, 63), (181, 184, 187)],
    "arctic":   [(232, 236, 239), (196, 203, 209), (154, 164, 172), (255, 255, 255)],
}

def camo(w, h, name, scale, seed=1):
    p = PALETTES[name]
    n = fbm(w, h, scale, seed); m = fbm(w, h, scale / 1.7, seed + 50)
    img = np.zeros((h, w, 3), dtype=np.uint8)
    for j in range(h):
        for i in range(w):
            v = n[j, i]; k = m[j, i]
            c = p[3] if k > 0.68 else p[2] if v > 0.58 else p[1] if v > 0.44 else p[0]
            img[j, i] = c
    return img

def solid_olive(w, h):
    n = fbm(w, h, 6, 3)
    base = np.array([75, 83, 32], dtype=float)
    img = np.clip(base[None, None, :] * (0.85 + 0.3 * n[:, :, None]), 0, 255).astype(np.uint8)
    img[::16, :, :] = (img[::16, :, :] * 0.7).astype(np.uint8)   # panel seams
    img[:, ::16, :] = (img[:, ::16, :] * 0.7).astype(np.uint8)
    return img

def solid_tan(w, h):
    n = fbm(w, h, 6, 5)
    base = np.array([190, 165, 112], dtype=float)
    img = np.clip(base[None, None, :] * (0.88 + 0.25 * n[:, :, None]), 0, 255).astype(np.uint8)
    img[::16, :, :] = (img[::16, :, :] * 0.75).astype(np.uint8)
    img[:, ::16, :] = (img[:, ::16, :] * 0.75).astype(np.uint8)
    return img

def save(img, *parts):
    path = os.path.join(ROOT, *parts)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    Image.fromarray(img).save(path)

def mcmeta(pack, desc):
    path = os.path.join(ROOT, pack, "pack.mcmeta")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump({"pack": {"description": desc, "min_format": 80, "max_format": 999}}, f, indent=2)

WOODS = ["oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "pale_oak", "bamboo"]

def vehicle_pack(pack, desc, maker):
    mcmeta(pack, desc)
    base = "assets/minecraft/textures/entity"
    save(maker(64, 32), pack, base, "minecart.png")
    for w in WOODS:
        save(maker(128, 64), pack, base, "boat", w + ".png")
        save(maker(128, 128), pack, base, "chest_boat", w + ".png")

vehicle_pack("vehicle_olive", "Military Client: Olive Drab vehicle skins (boats, minecarts)", solid_olive)
vehicle_pack("vehicle_desert", "Military Client: Desert Tan vehicle skins", solid_tan)
vehicle_pack("vehicle_woodland", "Military Client: Woodland Camo vehicle skins", lambda w, h: camo(w, h, "woodland", 8))
vehicle_pack("vehicle_urban", "Military Client: Urban Camo vehicle skins", lambda w, h: camo(w, h, "urban", 7))

# ---------------- base decoration (16x16 block textures) ----------------
pack = "base_decor"
mcmeta(pack, "Military Client: base decoration blocks (camo wool, hazard concrete, steel plate, runway)")
blk = "assets/minecraft/textures/block"
save(camo(16, 16, "woodland", 4, 11), pack, blk, "green_wool.png")
save(camo(16, 16, "desert", 5, 12), pack, blk, "brown_wool.png")
save(camo(16, 16, "urban", 4, 13), pack, blk, "gray_wool.png")
save(camo(16, 16, "arctic", 5, 14), pack, blk, "white_wool.png")

def hazard():
    img = np.zeros((16, 16, 3), dtype=np.uint8)
    for y in range(16):
        for x in range(16):
            img[y, x] = (240, 190, 20) if ((x + y) // 4) % 2 == 0 else (24, 24, 24)
    return img
save(hazard(), pack, blk, "yellow_concrete.png")

def steel(light):
    base = 150 if light else 92
    n = fbm(16, 16, 3, 21)
    img = np.clip(base + (n[:, :, None] - 0.5) * 30, 0, 255).repeat(3, axis=2).astype(np.uint8)
    img[0, :, :] = np.clip(img[0, :, :] * 0.6, 0, 255).astype(np.uint8)
    img[:, 0, :] = np.clip(img[:, 0, :] * 0.6, 0, 255).astype(np.uint8)
    img[15, :, :] = np.clip(img[15, :, :] * 1.15, 0, 255).astype(np.uint8)
    for (x, y) in [(2, 2), (13, 2), (2, 13), (13, 13)]:
        img[y, x] = (min(255, base + 50),) * 3
    return img
save(steel(False), pack, blk, "gray_concrete.png")
save(steel(True), pack, blk, "light_gray_concrete.png")

def olive_paint():
    n = fbm(16, 16, 3, 31)
    img = np.clip(np.array([78, 88, 52])[None, None, :] * (0.9 + 0.2 * n[:, :, None]), 0, 255).astype(np.uint8)
    img[7:9, 4:12] = (200, 200, 190)   # stencil bar
    return img
save(olive_paint(), pack, blk, "green_concrete.png")

def asphalt(line):
    n = np.array([[hash2(x, y, 41) for x in range(16)] for y in range(16)])
    g = (40 + n * 26).astype(np.uint8)
    img = np.stack([g, g, g], axis=2)
    if line: img[:, 7:9] = (225, 225, 215)
    return img
save(asphalt(False), pack, blk, "black_concrete.png")
save(asphalt(True), pack, blk, "white_concrete.png")
print("textures generated")
