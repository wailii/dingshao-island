# 逐帧画眼睛：白色眼白，瞳孔是镂空（系统可能只认图标形状）
import math, os, sys, subprocess
import numpy as np
from PIL import Image

N = 96          # 输出尺寸
SS = 4          # 超采样抗锯齿
FPS = 30
OUT = sys.argv[1]

ys, xs = np.mgrid[0:N*SS, 0:N*SS].astype(np.float32)
xs = (xs + 0.5) / SS
ys = (ys + 0.5) / SS

EYES = [(26.4, 48.0, 19.6), (69.6, 49.6, 21.2)]   # 左小右大

def ss(t):  # smoothstep
    t = min(1.0, max(0.0, t)); return t * t * (3 - 2 * t)

def track(keys, t):
    """keys: [(time, value)]，分段平滑插值"""
    if t <= keys[0][0]: return keys[0][1]
    for (t0, v0), (t1, v1) in zip(keys, keys[1:]):
        if t0 <= t <= t1:
            k = ss((t - t0) / (t1 - t0)) if t1 > t0 else 1
            if isinstance(v0, tuple): return tuple(a + (b - a) * k for a, b in zip(v0, v1))
            return v0 + (v1 - v0) * k
    return keys[-1][1]

def frame(look, lid, pupil_scale=1.0, angry=0.0, bottom=1.2, shake=(0.0, 0.0), color=(255, 255, 255)):
    """look: (x,y) -1..1；lid: 上眼皮 0 睁开 1 闭上；angry: 0..1 倒八字程度；bottom: 下眼皮位置（倍半径）"""
    a = np.zeros_like(xs)
    for i, (cx, cy, r) in enumerate(EYES):
        cx += shake[0]; cy += shake[1]
        d = (xs - cx) ** 2 + (ys - cy) ** 2
        eye = d <= r * r
        # 上眼皮：一条线以上剪掉。生气时内侧低外侧高（倒八字）
        li = lid[i] if isinstance(lid, tuple) else lid   # 两只眼可以眯得不一样
        top = cy - r * 1.05 + li * r * 2.1
        tilt = angry * r * 0.85
        inner_sign = 1 if i == 0 else -1          # 左眼内侧在右边，右眼内侧在左边
        lidline = top + inner_sign * (xs - cx) / r * tilt * 0.75 + angry * r * 0.3
        eye &= ys >= lidline
        eye &= ys <= cy + r * bottom
        pr = r * 0.44 * pupil_scale
        px = cx + look[0] * (r - pr) * 0.9
        py = cy + look[1] * (r - pr) * 0.9
        pupil = (xs - px) ** 2 + (ys - py) ** 2 <= pr * pr
        a = np.maximum(a, (eye & ~pupil).astype(np.float32))
    a = a.reshape(N, SS, N, SS).mean(axis=(1, 3))
    img = np.zeros((N, N, 4), np.uint8)
    img[..., 0:3] = color
    img[..., 3] = (a * 255).round().astype(np.uint8)
    return Image.fromarray(img, "RGBA")

def face(t, size=144):
    """展开卡片右边那张红色怒脸：红底，白眼睛黑瞳孔，倒八字，整张脸在抖"""
    eyes = frame_angry_raw(t)
    bg = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    m = np.zeros((N * SS, N * SS), np.float32)
    rr = N * 0.26
    inside = ((np.abs(xs - N / 2) <= N / 2 - rr) | (np.abs(ys - N / 2) <= N / 2 - rr)) & (xs >= 0) & (xs <= N) & (ys >= 0) & (ys <= N)
    corners = np.zeros_like(inside)
    for cx in (rr, N - rr):
        for cy in (rr, N - rr):
            corners |= (xs - cx) ** 2 + (ys - cy) ** 2 <= rr * rr
    m = (inside | corners).astype(np.float32).reshape(N, SS, N, SS).mean(axis=(1, 3))
    red = np.zeros((N, N, 4), np.uint8)
    red[..., 0], red[..., 1], red[..., 2] = 255, 74, 43
    red[..., 3] = (m * 255).round().astype(np.uint8)
    k = int(N * 0.72)
    small = eyes.resize((k, k), Image.LANCZOS)
    layer = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    layer.paste(small, ((N - k) // 2, (N - k) // 2 + 6), small)
    out = Image.alpha_composite(Image.fromarray(red, "RGBA"), layer)
    return out.resize((size, size), Image.LANCZOS)

def calm(t):
    look = track([(0, (0.3, 0.15)), (0.5, (0.3, 0.15)), (0.85, (-0.9, 0.05)), (1.5, (-0.9, 0.05)),
                  (1.9, (0.9, 0.05)), (2.5, (0.9, 0.05)), (2.8, (0.3, 0.15)), (3.25, (0.3, 0.15)),
                  (3.5, (0.35, -0.65)), (3.75, (0.35, -0.65)), (4.0, (0.3, 0.15))], t)
    lid = 0.0
    for c in (2.98,):
        if c - 0.09 <= t <= c: lid = ss((t - (c - 0.09)) / 0.09)
        elif c < t <= c + 0.13: lid = 1 - ss((t - c) / 0.13)
    return frame(look, lid)

def annoyed_white(t):
    return annoyed(t, color=(255, 255, 255))

def annoyed(t, color=(255, 184, 72)):
    look = track([(0, (0.2, 0.5)), (1.0, (0.2, 0.5)), (1.35, (-0.75, 0.5)), (2.0, (-0.75, 0.5)),
                  (2.35, (0.2, 0.5)), (3.6, (0.2, 0.5))], t)
    lid = 0.42 + 0.03 * math.sin(t / 3.6 * 2 * math.pi)
    for c in (3.05,):
        if c - 0.12 <= t <= c: lid = lid + (1 - lid) * ss((t - (c - 0.12)) / 0.12)
        elif c < t <= c + 0.18: lid = 1 - (1 - 0.42) * ss((t - c) / 0.18)
    return frame(look, lid, bottom=0.95, color=color)

def angry_params(t):
    tremble = 0.09 * math.sin(t * 2 * math.pi * 11)
    look = (0.05 + tremble, 0.3 + 0.05 * math.sin(t * 2 * math.pi * 7))
    phase = t % 0.8
    sh = (2.2 * math.sin(phase * 2 * math.pi * 14) * (1 - ss(phase / 0.25)) if phase < 0.25 else 0.0, 0.0)
    pulse = 0.82 + 0.06 * math.sin(t / 2.4 * 2 * math.pi * 3)
    return look, sh, pulse

def frame_angry_raw(t):
    """白眼白 + 黑瞳孔（不镂空），给彩色的怒脸用"""
    look, sh, pulse = angry_params(t)
    white = frame(look, 0.08, pupil_scale=pulse, angry=1.0, bottom=0.88, shake=sh)
    base = frame(look, 0.08, pupil_scale=0.0001, angry=1.0, bottom=0.88, shake=sh, color=(17, 17, 17))
    # 先画整只眼（黑），再叠白色眼白（瞳孔处镂空露出黑）
    return Image.alpha_composite(base, white)

def angry_white(t):
    look, sh, pulse = angry_params(t)
    return frame(look, 0.08, pupil_scale=pulse, angry=1.0, bottom=0.88, shake=sh)

def angry(t):
    # 瞳孔缩小发抖，每 0.8 秒整双眼睛抖一下
    tremble = 0.09 * math.sin(t * 2 * math.pi * 11)
    look = (0.05 + tremble, 0.3 + 0.05 * math.sin(t * 2 * math.pi * 7))
    phase = t % 0.8
    sh = (2.2 * math.sin(phase * 2 * math.pi * 14) * (1 - ss(phase / 0.25)) if phase < 0.25 else 0.0, 0.0)
    pulse = 0.82 + 0.06 * math.sin(t / 2.4 * 2 * math.pi * 3)
    return frame(look, 0.08, pupil_scale=pulse, angry=1.0, bottom=0.88, shake=sh, color=(255, 86, 56))

def happy(t):
    """回来了：一对 ^ ^ 笑眼，上下弹"""
    bob = -3.0 * abs(math.sin(t / 1.0 * 2 * math.pi))
    a = np.zeros_like(xs)
    w = 7.0
    for (cx, cy, r) in EYES:
        cy = cy + 6 + bob
        rr = r * 0.78
        d = np.sqrt((xs - cx) ** 2 + (ys - cy) ** 2)
        ring = (np.abs(d - rr) <= w / 2) & (ys <= cy)
        for ex in (cx - rr, cx + rr):
            ring |= (xs - ex) ** 2 + (ys - cy) ** 2 <= (w / 2) ** 2
        a = np.maximum(a, ring.astype(np.float32))
    a = a.reshape(N, SS, N, SS).mean(axis=(1, 3))
    img = np.zeros((N, N, 4), np.uint8)
    img[..., 0], img[..., 1], img[..., 2] = 96, 226, 140
    img[..., 3] = (a * 255).round().astype(np.uint8)
    return Image.fromarray(img, "RGBA")

def quiz(t):
    """出题：左眼眯起来斜着打量，右眼睁圆；眼珠往右上一翻想事，再回来盯着你"""
    look = track([(0, (0.1, 0.1)), (0.6, (0.1, 0.1)), (0.9, (0.75, -0.7)), (1.7, (0.75, -0.7)),
                  (2.0, (-0.2, 0.15)), (2.6, (-0.2, 0.15)), (2.8, (0.1, 0.1)), (3.0, (0.1, 0.1))], t)
    sq = 0.36 + 0.05 * math.sin(t / 3.0 * 2 * math.pi)
    return frame(look, (sq, 0.0), bottom=1.0, color=(126, 200, 255))

def sparkle(a, cx, cy, s):
    """四角星：|dx|^.5 + |dy|^.5 <= s^.5"""
    if s <= 0.2: return a
    d = np.sqrt(np.abs(xs - cx)) + np.sqrt(np.abs(ys - cy))
    return np.maximum(a, (d <= math.sqrt(s)).astype(np.float32))

def proud(t):
    """学完一节：金色 ^ ^ 笑眼一跳一跳，旁边三颗星轮流闪"""
    bob = -3.5 * abs(math.sin(t / 0.8 * 2 * math.pi))
    a = np.zeros_like(xs)
    w = 7.0
    for (cx, cy, r) in EYES:
        cy = cy + 8 + bob
        rr = r * 0.74
        d = np.sqrt((xs - cx) ** 2 + (ys - cy) ** 2)
        ring = (np.abs(d - rr) <= w / 2) & (ys <= cy)
        for ex in (cx - rr, cx + rr):
            ring |= (xs - ex) ** 2 + (ys - cy) ** 2 <= (w / 2) ** 2
        a = np.maximum(a, ring.astype(np.float32))
    for k, (sx, sy, big) in enumerate([(10, 14, 11), (88, 12, 13), (48, 86, 9)]):
        ph = (t / 1.6 + k / 3) % 1.0
        a = sparkle(a, sx, sy, big * max(0.0, math.sin(ph * math.pi)) ** 1.5)
    a = a.reshape(N, SS, N, SS).mean(axis=(1, 3))
    img = np.zeros((N, N, 4), np.uint8)
    img[..., 0], img[..., 1], img[..., 2] = 255, 208, 64
    img[..., 3] = (a * 255).round().astype(np.uint8)
    return Image.fromarray(img, "RGBA")

def roll(t):
    """退了三回还来：半眯着眼，眼珠从下往上转一大圈翻个白眼，停一下，再慢慢眨一次"""
    if t < 1.3:
        k = ss(t / 1.3)
        ang = math.pi * 0.5 + k * 2 * math.pi          # 从正下方出发，转一整圈
        look = (0.85 * math.cos(ang) * (1 if t < 1.3 else 0), 0.85 * math.sin(ang))
        if 0.35 < k < 0.75: look = (look[0], min(look[1], -0.85 * math.sin(math.pi * (k - 0.35) / 0.4)))
    else:
        look = (0.0, 0.5)
    lid = 0.3
    if 1.75 <= t <= 2.0: lid = 0.3 + 0.7 * ss((t - 1.75) / 0.25)
    elif 2.0 < t <= 2.3: lid = 1 - 0.7 * ss((t - 2.0) / 0.3)
    return frame(look, lid, bottom=0.95, color=(255, 184, 72))

LOOPS = {"calm": (calm, 4.0), "annoyed": (annoyed, 3.6), "annoyed_white": (annoyed_white, 3.6),
         "angry": (angry, 2.4), "angry_white": (angry_white, 2.4), "face_angry": (face, 2.4), "happy": (happy, 1.0),
         "quiz": (quiz, 3.0), "proud": (proud, 1.6), "roll": (roll, 2.6)}
# 后面跟名字就只画这几个：python render_eyes.py 输出目录 quiz proud
only = set(sys.argv[2:])
for name, (fn, dur) in LOOPS.items():
    if only and name not in only: continue
    d = os.path.join(OUT, name); os.makedirs(d, exist_ok=True)
    n = round(dur * FPS)
    files = []
    for i in range(n):
        f = os.path.join(d, f"{i:03d}.png"); fn(i / FPS).save(f); files.append(f)
    out = os.path.join(OUT, f"{name if name.startswith('face') else 'eyes_' + name}_anim.webp")
    cmd = ["img2webp", "-loop", "0", "-lossless", "-d", str(round(1000 / FPS))] + files + ["-o", out]
    subprocess.run(cmd, check=True, capture_output=True)
    print(name, n, "frames ->", os.path.getsize(out), "bytes")
