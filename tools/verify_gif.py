#!/usr/bin/env python3
"""验证 AnimatedGifEncoder 输出的 GIF：用 PIL 解码，与预期合成图逐像素对比。"""
import sys
from PIL import Image

def expected_frame(w, h, f):
    """与 Kotlin 测试相同公式的合成图（ARGB）。"""
    px = [[(0, 0, 0, 0)] * w for _ in range(h)]
    seed = 42 + f * 1000
    for y in range(h):
        for x in range(w):
            if x < 30 and y < 30:
                px[y][x] = (0, 0, 0, 0)  # 透明
                continue
            r = (x * 2 + y) & 0xff
            g = (y * 2) & 0xff
            b = (255 - x) & 0xff
            bx = (10 + f * 30) % 112
            block = bx <= x <= bx + 20 and 40 <= y <= 60
            seed = (seed * 1103515245 + 12345) & 0x7fffffff
            noise = (seed >> 16) & 0x0f
            cr = 255 if block else (r + noise) & 0xff
            cg = 0 if block else (g + noise) & 0xff
            cb = 0 if block else (b + noise) & 0xff
            px[y][x] = (cr, cg, cb, 255)
    return px

def main(path):
    img = Image.open(path)
    n = getattr(img, "n_frames", 1)
    w, h = img.size
    print(f"size={w}x{h} frames={n} format={img.format}")
    assert w == 112 and h == 112, "尺寸错误"
    assert n == 3, f"帧数错误: {n}"
    durations = []
    for i in range(n):
        img.seek(i)
        durations.append(img.info.get("duration", -1))
    print("durations(ms):", durations)
    assert all(d >= 50 for d in durations), "帧延迟异常"

    # 逐帧像素对比（GIF 量化后允许误差）
    max_err = 0.0
    total_err = 0.0
    px_total = 0
    transparent_ok = True
    for i in range(n):
        img.seek(i)
        rgba = img.convert("RGBA")
        exp = expected_frame(w, h, i)
        for y in range(h):
            for x in range(w):
                r, g, b, a = rgba.getpixel((x, y))
                er, eg, eb, ea = exp[y][x]
                if ea == 0:
                    if a != 0:
                        transparent_ok = False
                        print(f"  [帧{i}] 透明区({x},{y}) 不透明: a={a}")
                    continue
                err = (abs(r - er) + abs(g - eg) + abs(b - eb)) / 3.0
                total_err += err
                max_err = max(max_err, err)
                px_total += 1
    avg_err = total_err / max(px_total, 1)
    print(f"平均通道误差={avg_err:.2f} 最大误差={max_err:.1f} 透明区校验={'OK' if transparent_ok else 'FAIL'}")
    assert transparent_ok, "透明区域未保持透明"
    assert avg_err < 6.0, f"平均误差过大: {avg_err}"
    assert max_err < 40.0, f"最大误差过大: {max_err}"
    print("PASS: GIF 解码与像素对比全部通过")

if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "app/build/test-output/roundtrip.gif")
