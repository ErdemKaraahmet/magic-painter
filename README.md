# Magic Air Painter

A contactless air-drawing application for **COMP 4687 - Introduction to Computer
Vision** (Isik University, Spring 2026). The user shows any solid-colour object
to the webcam and draws in the air; the strokes appear directly on the mirrored
live video. No keyboard or mouse is needed while drawing. Pure classical computer
vision is used (no MediaPipe / YOLO / neural networks).

---

## Requirements

- JDK 17 or newer
- Maven 3.8+
- A working webcam
- Internet access on the first build only (Maven downloads the JavaCV /
  OpenCV native binaries, which are large)

The `javacv-platform` dependency bundles the correct OpenCV binaries for Windows
and macOS (Intel and Apple Silicon) automatically, so no manual `.dll` / `.dylib`
setup is required.

---

## Build and run

Quick run during development:

```bash
mvn compile exec:java
```

Build a single runnable jar:

```bash
mvn clean package
java -jar target/magic-painter-1.0.0.jar
```

---

## How to use it

1. Launch the app. A mirrored webcam window opens with a yellow square in the
   centre (the calibration ROI).
2. Hold your coloured object (pen cap, coloured card, ping-pong ball, etc.)
   inside the yellow box and press **SPACE**. The app samples the object colour
   and locks onto it.
3. Move the object in the air to draw. Four virtual buttons sit on the left:
   - **PEN** - thin line
   - **BRUSH** - thick line
   - **ERASER** - erases drawn pixels near the tip
   - **CLEAR** - wipes the whole drawing
4. To select a button, hover the object over it for about **2 seconds**
   (60 frames). A green arc fills around the button to show progress.
5. Lift / hide the object to stop a stroke. The next stroke starts cleanly with
   no connecting line (gap check).
6. Press **Q** or **ESC** to quit.

---

## Project layout

```
magic-painter/
  pom.xml
  README.md
  src/main/java/com/isikun/magicpainter/
    Main.java                 Entry point and core loop
    camera/CameraManager.java Capture, convert, horizontal mirroring
    vision/ColorTracker.java  HSV conversion, calibration, mask, morphology, moments
    vision/ShapeAnalyzer.java [Phase 2] contour-area thickness, Gaussian glow
    ui/OverlayPanel.java       Virtual buttons, calibration ROI, hover arc
    ui/BrushManager.java       Drawing history, modes, gap check, eraser
```

---

## Per-frame pipeline

```
read frame -> mirror (flip) -> copy + convert to HSV
  colour NOT locked: draw calibration ROI; SPACE samples the colour
  colour locked:
    mask (inRange) -> Opening -> largest contour -> centroid (cx, cy)
      tip over a virtual button? -> count hover/dwell, no drawing
      otherwise                  -> draw according to the active mode
draw history onto the live frame -> overlay the UI -> show on screen
```

---

## Syllabus mapping (for the jury)

| Stage in the app | CV technique | Course topic |
| --- | --- | --- |
| Stabilising tracking against lighting | BGR to HSV conversion | W4 Transformations / colour bases |
| Isolating the object | Binary thresholding via inRange | W2-3 Binary images, thresholding |
| Removing noise specks | Morphological Opening (Erosion + Dilation) | W3 Mathematical morphology |
| Finding the tip | Largest contour + image moments (Cx = M10/M00, Cy = M01/M00) | W3-4 Connected components, region properties |
| Phase 2 glow effect | Gaussian low-pass blur | W6 Spatial filtering / smoothing |

---

## Design notes

- **Mirroring is the first step.** Every captured frame is flipped horizontally
  (`flip` code 1) before any coordinate work, so the image behaves like a mirror.
- **Dynamic sizing.** The real camera resolution is read at startup; buttons and
  the ROI scale to the frame, so 640x480 and 1280x720 both work.
- **Memory management.** OpenCV `Mat` objects wrap native (C++) memory. Every
  per-frame `Mat` is released explicitly, `MatVector` uses try-with-resources, and
  the drawing history is stored as plain Java coordinates rather than native
  `Point` objects so nothing native is kept across frames.
- **Coding standard.** All comments, names, and documentation are in English and
  the codebase contains no emojis.

---

## Phase scope

**Phase 1 (implemented):** mirroring, dynamic colour calibration, 2-second
hover/dwell button selection, continuous drawing with gap check, and the Pen /
Brush / Eraser / Clear modes.

**Phase 2 (scaffolded in `ShapeAnalyzer`):** brush thickness driven by the
object's `contourArea`, and a Gaussian-blur neon glow. Wire `dynamicThickness`
into `BrushManager.addPoint` and call `applyGlow` on the frame to enable them.

---

## Troubleshooting

- **First run is slow / downloads a lot.** That is JavaCV fetching the native
  OpenCV binaries. Subsequent runs are fast.
- **Black window or camera not found.** Try a different `DEVICE_INDEX` in
  `Main.java` (0, 1, 2). On macOS, grant camera permission to your terminal / IDE
  under System Settings > Privacy & Security > Camera.
- **Tracking is jittery.** Calibrate under steady lighting on a plain background,
  and pick an object whose colour is not present elsewhere in the scene. You can
  also raise `MIN_VALID_AREA` in `ColorTracker` to ignore more noise.
- **inRange signature error on a different JavaCV version.** This project targets
  `javacv-platform:1.5.10`, where `inRange` accepts scalar bounds wrapped as
  `Mat`. If you change the version and the compiler complains, adjust the bound
  construction in `ColorTracker.buildCleanMask` accordingly.
