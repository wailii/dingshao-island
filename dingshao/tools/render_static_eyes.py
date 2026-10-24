# 生成单色静态眼睛（矢量）：别家手机的状态栏图标多半会被染成单色，瞳孔用镂空
import math, sys, os
OUT = sys.argv[1]
EYES = [(6.6, 12.0, 5.0), (17.4, 12.4, 5.4)]

def circle_poly(cx, cy, r, n=56):
    return [(cx + r * math.cos(2 * math.pi * k / n), cy + r * math.sin(2 * math.pi * k / n)) for k in range(n)]

def clip(poly, a, b, c):
    """保留 a*x + b*y + c >= 0 的那一侧（Sutherland–Hodgman）"""
    out = []
    for i in range(len(poly)):
        p, q = poly[i], poly[(i + 1) % len(poly)]
        fp, fq = a * p[0] + b * p[1] + c, a * q[0] + b * q[1] + c
        if fp >= 0: out.append(p)
        if (fp >= 0) != (fq >= 0):
            t = fp / (fp - fq)
            out.append((p[0] + t * (q[0] - p[0]), p[1] + t * (q[1] - p[1])))
    return out

def path(poly):
    return "M" + " L".join(f"{x:.2f},{y:.2f}" for x, y in poly) + " Z"

def circ(cx, cy, r):
    return f"M{cx - r:.2f},{cy:.2f}a{r},{r} 0,1 0,{2 * r:.2f},0a{r},{r} 0,1 0,{-2 * r:.2f},0z"

def vec(body):
    return f'''<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
{body}
</vector>
'''

def fill(d):
    return f'''    <path
        android:fillColor="#FFFFFFFF"
        android:fillType="evenOdd"
        android:pathData="{d}" />'''

def stroke(d, w=2.2):
    return f'''    <path
        android:strokeColor="#FFFFFFFF"
        android:strokeWidth="{w}"
        android:strokeLineCap="round"
        android:pathData="{d}" />'''

# 不耐烦：上眼皮压掉四成，瞳孔往下看
annoyed = []
for (cx, cy, r) in EYES:
    lid = cy - r * 0.15
    annoyed.append(path(clip(circle_poly(cx, cy, r), 0, 1, -lid)) + " " + circ(cx + 0.8, cy + 1.9, 1.7))
# 生气：倒八字，内侧低外侧高
angry = []
for i, (cx, cy, r) in enumerate(EYES):
    inner = 1 if i == 0 else -1
    x0, y0 = cx - r, cy - r * (0.95 if inner == 1 else 0.05)
    x1, y1 = cx + r, cy - r * (0.05 if inner == 1 else 0.95)
    # 直线 (x0,y0)-(x1,y1) 下方：叉积 >= 0
    a, b = -(y1 - y0), (x1 - x0)
    c = -(a * x0 + b * y0)
    angry.append(path(clip(circle_poly(cx, cy, r), a, b, c)) + " " + circ(cx + 0.4 * inner, cy + 1.7, 1.6))
open(os.path.join(OUT, "ic_eyes_annoyed.xml"), "w").write(vec(fill(" ".join(annoyed))))
open(os.path.join(OUT, "ic_eyes_angry.xml"), "w").write(vec(fill(" ".join(angry))))
open(os.path.join(OUT, "ic_eyes_happy.xml"), "w").write(vec(stroke("M2.9,13.8 Q6.6,8.4 10.3,13.8") + "\n" + stroke("M13.5,14.2 Q17.4,8.4 21.3,14.2")))
print("ok")
