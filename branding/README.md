# Side Brand System

Gemeinsames Erscheinungsbild der Sidephone-App-Familie: **SideTube** (Video) und **sideplay** (Audio).
Quelle der Wahrheit: `branding/logo-spec.json` · Generator: `scripts/generate_brand_assets.py` (SVG → PNG via
`rsvg-convert`, Vorschau via PIL). Alle Produktionsassets werden aus der Spezifikation erzeugt, nie von Hand gemalt.

## Gemeinsame Identität
Ein abstrahiertes **S** aus zwei kräftigen, abgerundeten Bändern (oberer Haken links nach unten, unterer Haken rechts
nach oben). In der offenen Mitte sitzt das Funktionssymbol: **Play-Dreieck** (SideTube) oder **Audio-Welle** (sideplay).
Gleiche Form, gleiche Farben, gleiche Proportionen – nur die Funktion in der Mitte ändert sich.

```
          gemeinsames SIDE-S
                 │
        ┌────────┴────────┐
     SideTube          sideplay
        ▶               ▥ (5 Balken)
```

## Farben (kanonisch, identisch für beide Apps)
| Token | Hex | RGB |
|---|---|---|
| `brand.side.yellow` | `#FBBB1B` | 251, 187, 27 |
| `brand.side.dark` | `#090A0C` | 9, 10, 12 |
| `brand.side.light` | `#F6F4EF` | 246, 244, 239 |
Im Code: `BrandColor.yellow/.dark/.light` (`Sources/App/Brand.swift`), Asset `AccentColor` = #FBBB1B, `KidTheme.accent` verweist darauf.

## Geometrie (ViewBox 0 0 1024 1024, eingefroren 2026-08-31)
**SIDE_S_GEOMETRY IS SHARED AND IMMUTABLE**
- Top: `M 750 250 H 370 C 292 250 252 290 252 370 V 430`
- Bottom: `M 772 594 V 654 C 772 732 732 772 654 772 H 276`
- `fill=none`, `stroke-width=136`, `stroke-linecap=round`, `stroke-linejoin=round`
- Play: `M 450 416 Q 426 402 426 435 L 426 585 Q 426 618 450 604 L 590 526 Q 616 511 590 495 Z`
- Wave: fünf Balken, x-Mitten 432/472/512/552/592, Höhen 88/142/210/142/88, Breite **32** (optisch von 30 angehoben, damit
  die Welle so schwer wirkt wie das Dreieck), Eckradius 15, Mitte y = 510.
Änderungen an der S-Geometrie sind nicht vorgesehen; Symbolkorrekturen nur in `logo-spec.json`, danach neu generieren.

## Dateien
`branding/<produkt>/`: `-mark.svg` (gelb, transparent) · `-mark-dark.svg` (dunkles Signet für helle Flächen) ·
`-mark-light.svg` (helles Signet, monochrom) · `-icon-dark.svg` (**primär**: dunkle Fläche + gelbes Signet) ·
`-icon-yellow.svg` · `-icon-light.svg` · `-icon-tinted.svg` (weiß auf transparent) · PNG-Exporte `-appicon-{dark,yellow,light,tinted}-1024.png`,
`-appicon-1024.png` (= dark), `-mark-1024.png`. Vorschau: `docs/assets/side-brand-preview.png` (nicht für Produktion).

## App Icons
1024 × 1024, vollflächig, **keine** vorgerundeten Ecken (Xcode maskiert), keine Transparenz, Signet mit ~18–20 % Innenabstand
(Bounding Box x 190…834, y 170…854). Kein Text im Icon. Xcode 26: `AppIcon.appiconset` mit Default = Dark, Appearance
*dark* = Dark, Appearance *tinted* = weißes Signet (iOS färbt die Helligkeit). Icon Composer (`.icon`) wird derzeit nicht
verwendet; falls später: Layer 1 Hintergrund, Layer 2 S, Layer 3A Play / 3B Welle.

## Dark / Light / Tinted
Primär dunkel (#090A0C + #FBBB1B). Gelb-Variante für Marketing/Hardware, Light-Variante sekundär. Monochrom funktioniert,
weil das Symbol formbasiert unterscheidet (Dreieck vs. Balken) – keine Information nur über Farbe.

## Verwendung in SwiftUI
- `Image("SideTubeMark")` – Vektor-Imageset (SVG, `preserves-vector-representation`), dekorativ → `.accessibilityHidden(true)`.
- `SideWordmark(product: .tube)` – „Side" in `.primary`, „Tube" in `BrandColor.yellow`, Systemschrift Medium; nie als Bitmap.
  **Schreibweise (Produktentscheidung 2026-08-31): immer „SideTube" / „SidePlay"** – nicht kleingeschrieben.
- Zurückhaltend einsetzen (Über-Screen, PIN-Einrichtung), nicht großflächig.

## sideplay
Assets liegen bereit in `branding/sideplay/`. Integration in ein künftiges sideplay-Target: `sideplay-appicon-dark-1024.png`
+ `-tinted-` in dessen `AppIcon.appiconset` (Contents.json wie bei SideTube), `sideplay-mark.svg` als `SidePlayMark`,
`BrandColor` unverändert übernehmen, Wortmarke `SideWordmark(product: .play)`.

## Was nicht verändert werden darf
S-Pfade, Strichstärke, Farben, Proportionen, Position des Symbols; kein zweites Gelb; keine Schatten/Glows im Master;
keine YouTube-/Spotify-Anleihen; kein Text im Icon.

## Asset-Generierung
```bash
brew install librsvg            # rsvg-convert
python3 scripts/generate_brand_assets.py
cp branding/sidetube/sidetube-appicon-dark-1024.png Sources/Resources/Assets.xcassets/AppIcon.appiconset/AppIcon-Dark.png
cp branding/sidetube/sidetube-appicon-tinted-1024.png Sources/Resources/Assets.xcassets/AppIcon.appiconset/AppIcon-Tinted.png
cp branding/sidetube/sidetube-mark.svg Sources/Resources/Assets.xcassets/SideTubeMark.imageset/
```

## Android

Das Launcher-Symbol entsteht aus derselben Spezifikation, nicht als Kopie:

```bash
python3 scripts/generate_brand_assets.py --android=android/app/src/main/res
```

Geschrieben werden `ic_launcher_foreground.xml` (S-Signet in Markengelb),
`ic_launcher_background.xml` (Markenschwarz), `ic_launcher_monochrome.xml` für die
Themed Icons ab Android 13 sowie die beiden Adaptive-Icon-Definitionen. Der 1024er-Entwurf
dient direkt als `viewport` und wird auf 108 dp abgebildet; eine Verkleinerung auf 90 Prozent
um den Mittelpunkt hält das Zeichen innerhalb der sicheren Zone runder Masken.
