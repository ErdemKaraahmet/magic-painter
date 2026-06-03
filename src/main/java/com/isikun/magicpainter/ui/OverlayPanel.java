package com.isikun.magicpainter.ui;

import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Point;
import org.bytedeco.opencv.opencv_core.Rect;
import org.bytedeco.opencv.opencv_core.Scalar;
import org.bytedeco.opencv.opencv_core.Size;

/**
 * Renders the virtual control panel and implements hover/dwell selection: keeping
 * the tip on a button for REQUIRED_FRAMES (about 2 seconds at 30 FPS) activates
 * it. A circular progress arc fills around the button as visual feedback.
 *
 * All button rectangles and the calibration ROI are scaled to the real frame size
 * (spec note 2), so the layout adapts to 640x480 or 1280x720 automatically.
 */
public class OverlayPanel {

    private static final int REQUIRED_FRAMES = 30; // ~1 second at 30 FPS
    private static final int MAX_MISSED_FRAMES = 5; // allow 5 frames of jitter

    /** One virtual button: a region, a label, and an action to run on dwell. */
    private static final class Button {
        final String label;
        final Rect rect;
        final Runnable action;
        int hoverFrames = 0;
        int missedFrames = 0;

        Button(String label, Rect rect, Runnable action) {
            this.label = label;
            this.rect = rect;
            this.action = action;
        }
    }

    private final Button[] buttons;
    private final Rect calibrationRoi;
    private final BrushManager brush;

    public OverlayPanel(int frameWidth, int frameHeight, BrushManager brush) {
        this.brush = brush;

        int buttonWidth = Math.max(90, frameWidth / 8);
        int buttonHeight = Math.max(45, frameHeight / 10);
        int margin = Math.max(12, buttonHeight / 3);
        int x = margin;

        this.buttons = new Button[] {
                new Button("PEN",
                        new Rect(x, margin, buttonWidth, buttonHeight),
                        () -> brush.setMode(BrushManager.Mode.PEN)),
                new Button("BRUSH",
                        new Rect(x, margin * 2 + buttonHeight, buttonWidth, buttonHeight),
                        () -> brush.setMode(BrushManager.Mode.BRUSH)),
                new Button("ERASER",
                        new Rect(x, margin * 3 + buttonHeight * 2, buttonWidth, buttonHeight),
                        () -> brush.setMode(BrushManager.Mode.ERASER)),
                new Button("CLEAR",
                        new Rect(x, margin * 4 + buttonHeight * 3, buttonWidth, buttonHeight),
                        brush::clear)
        };

        int roiSize = Math.max(30, frameHeight / 12);
        this.calibrationRoi = new Rect(
                frameWidth / 2 - roiSize / 2,
                frameHeight / 2 - roiSize / 2,
                roiSize, roiSize);
    }

    public Rect getCalibrationRoi() {
        return calibrationRoi;
    }

    /** True when the tip is inside any virtual button (used to suppress drawing). */
    public boolean isOverAnyButton(Point tip) {
        if (tip == null) {
            return false;
        }
        for (Button b : buttons) {
            if (isInside(tip, b.rect)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Updates hover state for the current tip and draws all UI elements. Pass a
     * null tip when no object is detected.
     */
    public void update(Point tip, Mat frame, boolean colorLocked, Scalar currentObjColor) {
        if (!colorLocked) {
            drawCalibrationPrompt(frame);
            return;
        }

        for (Button b : buttons) {
            boolean hovered = tip != null && isInside(tip, b.rect);
            if (hovered) {
                b.hoverFrames++;
                b.missedFrames = 0;
                if (b.hoverFrames >= REQUIRED_FRAMES) {
                    b.action.run();
                    b.hoverFrames = 0;
                }
            } else if (b.hoverFrames > 0) {
                // Grace period: allow small interruptions without resetting.
                b.missedFrames++;
                if (b.missedFrames > MAX_MISSED_FRAMES) {
                    b.hoverFrames = 0;
                    b.missedFrames = 0;
                }
            } else {
                b.hoverFrames = 0;
                b.missedFrames = 0;
            }
            drawButton(frame, b, currentObjColor);
        }
        drawStatus(frame);
    }

    private void drawButton(Mat frame, Button b, Scalar objColor) {
        boolean active = brush.getMode().name().equals(b.label);
        double ratio = (double) b.hoverFrames / REQUIRED_FRAMES;

        // 1. Draw the filled background from left to right
        if (active) {
            // Fully filled for active button
            opencv_imgproc.rectangle(frame, b.rect, objColor, -1, opencv_imgproc.LINE_8, 0);
        } else if (b.hoverFrames > 0) {
            // Progress fill from left to right
            int progressWidth = (int) (b.rect.width() * ratio);
            Rect fillRect = new Rect(b.rect.x(), b.rect.y(), progressWidth, b.rect.height());
            opencv_imgproc.rectangle(frame, fillRect, objColor, -1, opencv_imgproc.LINE_8, 0);
            fillRect.close();
        }

        // 2. Draw the static black border
        Scalar borderColor = new Scalar(0, 0, 0, 0);
        opencv_imgproc.rectangle(frame, b.rect, borderColor, 2, opencv_imgproc.LINE_8, 0);

        // 3. Draw the text with HSV complement if the background is significantly filled
        Scalar textColor;
        if (active || ratio > 0.5) {
            textColor = getHsvComplement(objColor);
        } else {
            textColor = new Scalar(0, 0, 0, 0); // Black text initially
        }

        Point textOrg = new Point(b.rect.x() + 8, b.rect.y() + b.rect.height() / 2 + 5);
        opencv_imgproc.putText(frame, b.label, textOrg,
                opencv_imgproc.FONT_HERSHEY_SIMPLEX, 0.5, textColor, 1,
                opencv_imgproc.LINE_AA, false);

        borderColor.close();
        textColor.close();
        textOrg.close();
        // DO NOT close objColor here; it's owned by ColorTracker.
    }

    /**
     * Calculates the complementary color by shifting Hue by 180 degrees in HSV space.
     * Also adjusts Value for guaranteed contrast against the original color.
     */
    private Scalar getHsvComplement(Scalar bgr) {
        // Create a 1x1 Mat to perform the conversion
        Mat bgrMat = new Mat(1, 1, org.bytedeco.opencv.global.opencv_core.CV_8UC3, bgr);
        Mat hsvMat = new Mat();
        opencv_imgproc.cvtColor(bgrMat, hsvMat, opencv_imgproc.COLOR_BGR2HSV);

        // Access HSV values. H in OpenCV is 0-179.
        byte[] hsvData = new byte[3];
        hsvMat.data().get(hsvData);
        int h = hsvData[0] & 0xFF;
        int s = hsvData[1] & 0xFF;
        int v = hsvData[2] & 0xFF;

        // Shift Hue by 180 degrees (90 units in OpenCV)
        h = (h + 90) % 180;
        
        // Boost/Invert Value for contrast: if dark, make text bright; if bright, make text dark.
        v = (v > 128) ? 40 : 255;
        // Keep Saturation high for the complementary effect to be visible
        s = Math.max(s, 150);

        hsvData[0] = (byte) h;
        hsvData[1] = (byte) s;
        hsvData[2] = (byte) v;
        hsvMat.data().put(hsvData);

        Mat resBgrMat = new Mat();
        opencv_imgproc.cvtColor(hsvMat, resBgrMat, opencv_imgproc.COLOR_HSV2BGR);

        byte[] resBgrData = new byte[3];
        resBgrMat.data().get(resBgrData);
        Scalar result = new Scalar(resBgrData[0] & 0xFF, resBgrData[1] & 0xFF, resBgrData[2] & 0xFF, 0);

        bgrMat.release();
        hsvMat.release();
        resBgrMat.release();

        return result;
    }

    private void drawCalibrationPrompt(Mat frame) {
        Scalar boxColor = new Scalar(0, 0, 0, 0); // Black calibration box
        opencv_imgproc.rectangle(frame, calibrationRoi, boxColor, 2,
                opencv_imgproc.LINE_8, 0);

        Point textOrg = new Point(calibrationRoi.x() - 100, calibrationRoi.y() - 15);
        opencv_imgproc.putText(frame, "Hold object in box and press SPACE", textOrg,
                opencv_imgproc.FONT_HERSHEY_SIMPLEX, 0.5, boxColor, 1,
                opencv_imgproc.LINE_AA, false);

        textOrg.close();
        boxColor.close();
    }

    private void drawStatus(Mat frame) {
        Scalar color = new Scalar(0, 0, 0, 0); // Black status text
        Point org = new Point(20, frame.rows() - 45);
        opencv_imgproc.putText(frame, "Mode: " + brush.getMode() + " (Hover buttons to select)", org,
                opencv_imgproc.FONT_HERSHEY_SIMPLEX, 0.5, color, 1,
                opencv_imgproc.LINE_AA, false);

        Scalar hintColor = new Scalar(0, 0, 0, 0); // Black hints
        Point hintOrg = new Point(20, frame.rows() - 20);
        opencv_imgproc.putText(frame, "C: new color   S: save   Q: quit", hintOrg,
                opencv_imgproc.FONT_HERSHEY_SIMPLEX, 0.5, hintColor, 1,
                opencv_imgproc.LINE_AA, false);

        org.close();
        color.close();
        hintOrg.close();
        hintColor.close();
    }

    /** Draws a visual box around the eraser tip to show the area of effect. */
    public void drawEraserBox(Mat frame, Point tip) {
        if (tip == null || brush.getMode() != BrushManager.Mode.ERASER) {
            return;
        }
        int radius = 50; // Matches ERASER_RADIUS in BrushManager
        Rect eraserRect = new Rect(tip.x() - radius, tip.y() - radius, radius * 2, radius * 2);
        Scalar color = new Scalar(255, 255, 255, 0); // White box
        opencv_imgproc.rectangle(frame, eraserRect, color, 1, opencv_imgproc.LINE_AA, 0);
        
        eraserRect.close();
        color.close();
    }

    private boolean isInside(Point p, Rect r) {
        return p.x() >= r.x() && p.x() <= (r.x() + r.width())
                && p.y() >= r.y() && p.y() <= (r.y() + r.height());
    }
}