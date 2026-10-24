import sys, numpy as np
from PIL import Image
N, SS = 96, 4
ys, xs = np.mgrid[0:N*SS, 0:N*SS].astype(np.float32); xs=(xs+.5)/SS; ys=(ys+.5)/SS
a = np.zeros_like(xs); R, off, w = 22.0, 31.0, 10.0
for sx in (-1, 1):
    for sy in (-1, 1):
        cx, cy = 48 + sx * off, 48 + sy * off
        d = np.sqrt((xs - cx) ** 2 + (ys - cy) ** 2)
        arc = (np.abs(d - R) <= w / 2) & ((xs - cx) * -sx >= 0) & ((ys - cy) * -sy >= 0)
        for ex, ey in ((cx - sx * R, cy), (cx, cy - sy * R)):
            arc |= (xs - ex) ** 2 + (ys - ey) ** 2 <= (w / 2) ** 2
        a = np.maximum(a, arc.astype(np.float32))
a = a.reshape(N, SS, N, SS).mean(axis=(1, 3))
img = np.zeros((N, N, 4), np.uint8); img[..., 0], img[..., 1], img[..., 2] = 255, 59, 43
img[..., 3] = (a * 255).round().astype(np.uint8)
Image.fromarray(img, "RGBA").save(sys.argv[1])
