#!/usr/bin/env python3
"""Erzeugt alle Side-Brand-Assets deterministisch aus branding/logo-spec.json.

SVG (Master, flach) -> PNG (rsvg-convert) -> Vorschau (PIL). Keine KI, keine externen Dienste.
Aufruf: python3 scripts/generate_brand_assets.py [--check]
"""
import json, os, subprocess, sys, shutil
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SPEC = json.load(open(os.path.join(ROOT, "branding", "logo-spec.json")))
C = SPEC["colors"]; ST = SPEC["stroke"]; S = SPEC["side_s"]

def side_s(color):
    attrs = f'fill="none" stroke="{color}" stroke-width="{ST["width"]}" stroke-linecap="{ST["linecap"]}" stroke-linejoin="{ST["linejoin"]}"'
    return f'  <path id="side-s-top" d="{S["top"]}" {attrs}/>\n  <path id="side-s-bottom" d="{S["bottom"]}" {attrs}/>\n'

def play(color):
    return f'  <path id="symbol-play" d="{SPEC["symbols"]["play"]["path"]}" fill="{color}"/>\n'

def wave(color):
    w = SPEC["symbols"]["wave"]; out = ""
    for cx, h in zip(w["centers_x"], w["heights"]):
        x = cx - w["bar_width"] / 2; y = w["center_y"] - h / 2
        out += f'  <rect id="symbol-wave-{cx}" x="{x:g}" y="{y:g}" width="{w["bar_width"]}" height="{h}" rx="{w["corner_radius"]}" fill="{color}"/>\n'
    return out

SYMBOLS = {"play": play, "wave": wave}

def svg(product, fg, bg=None):
    sym = SPEC["products"][product]["symbol"]
    body = (f'  <rect id="background" width="1024" height="1024" fill="{bg}"/>\n' if bg else "") + side_s(fg) + SYMBOLS[sym](fg)
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{SPEC["viewBox"]}" width="1024" height="1024" role="img" aria-label="{product}">\n'
            f'  <!-- Side brand: SIDE_S_GEOMETRY IS SHARED AND IMMUTABLE. {product} = S + {sym}. Generated from branding/logo-spec.json -->\n'
            + body + '</svg>\n')

def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f: f.write(text)

def png(svg_path, png_path, size=1024):
    subprocess.run(["rsvg-convert", "-w", str(size), "-h", str(size), "-o", png_path, svg_path], check=True)

def main():
    check = "--check" in sys.argv
    if shutil.which("rsvg-convert") is None:
        sys.exit("rsvg-convert fehlt (brew install librsvg)")
    made = []
    for product in SPEC["products"]:
        d = os.path.join(ROOT, "branding", product)
        variants = {
            f"{product}-mark.svg": svg(product, C["yellow"]),                    # gelbes Signet, transparent
            f"{product}-mark-dark.svg": svg(product, C["dark"]),                 # dunkles Signet fuer helle Flaechen
            f"{product}-mark-light.svg": svg(product, C["light"]),               # helles Signet fuer dunkle Flaechen (monochrom)
            f"{product}-icon-dark.svg": svg(product, C["yellow"], C["dark"]),    # PRIMAER
            f"{product}-icon-yellow.svg": svg(product, C["dark"], C["yellow"]),
            f"{product}-icon-light.svg": svg(product, C["yellow"], C["light"]),
        }
        for name, text in variants.items():
            write(os.path.join(d, name), text); made.append(name)
        for variant in ("dark", "yellow", "light"):
            png(os.path.join(d, f"{product}-icon-{variant}.svg"), os.path.join(d, f"{product}-appicon-{variant}-1024.png"))
        shutil.copy(os.path.join(d, f"{product}-appicon-dark-1024.png"), os.path.join(d, f"{product}-appicon-1024.png"))
        # Tinted/monochrom: weisses Signet auf Transparent (iOS 18 "tinted" nutzt nur die Helligkeit)
        write(os.path.join(d, f"{product}-icon-tinted.svg"), svg(product, "#FFFFFF"))
        png(os.path.join(d, f"{product}-icon-tinted.svg"), os.path.join(d, f"{product}-appicon-tinted-1024.png"))
        png(os.path.join(d, f"{product}-mark.svg"), os.path.join(d, f"{product}-mark-1024.png"))
    preview()
    print("erzeugt:", len(made), "SVGs + PNG-Exporte + docs/assets/side-brand-preview.png")

def preview():
    from PIL import Image, ImageDraw, ImageFont
    def load(p, size): return Image.open(os.path.join(ROOT, "branding", p)).convert("RGBA").resize((size, size), Image.LANCZOS)
    W, H = 1400, 1220
    img = Image.new("RGB", (W, H), (30, 30, 34)); d = ImageDraw.Draw(img)
    try:
        font = ImageFont.truetype("/System/Library/Fonts/SFNS.ttf", 40); small = ImageFont.truetype("/System/Library/Fonts/SFNS.ttf", 22)
    except Exception:
        font = small = ImageFont.load_default()
    d.text((40, 30), "Side brand preview – sidetube | sideplay (nicht fuer Produktion)", fill=(230, 230, 230), font=small)
    y = 80
    for variant in ("dark", "yellow", "light"):
        d.text((40, y + 90), variant, fill=(200, 200, 200), font=small)
        for i, product in enumerate(("sidetube", "sideplay")):
            img.paste(load(f"{product}/{product}-appicon-{variant}-1024.png", 220), (200 + i * 300, y))
        y += 260
    d.text((40, y + 60), "mark", fill=(200, 200, 200), font=small)
    for i, product in enumerate(("sidetube", "sideplay")):
        m = load(f"{product}/{product}-mark-1024.png", 160); img.paste(m, (230 + i * 300, y), m)
        d.text((225 + i * 300, y + 170), "Side", fill=(246, 244, 239), font=font)
        d.text((225 + i * 300 + 92, y + 170), product[4:].capitalize(), fill=(251, 187, 27), font=font)
    y += 250
    d.text((40, y + 20), "16 / 32 / 64 / 128 px", fill=(200, 200, 200), font=small)
    x = 300
    for size in (16, 32, 64, 128):
        for i, product in enumerate(("sidetube", "sideplay")):
            img.paste(load(f"{product}/{product}-appicon-dark-1024.png", size), (x + i * (size + 24), y + 64 - size // 2))
        x += 2 * size + 90
    os.makedirs(os.path.join(ROOT, "docs", "assets"), exist_ok=True)
    img.save(os.path.join(ROOT, "docs", "assets", "side-brand-preview.png"))

if __name__ == "__main__":
    main()
