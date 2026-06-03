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

    private static final int REQUIRED_FRAMES = 60; // ~2 seconds at 30 FPS

    /** One virtual button: a region, a label, and an action to run on dwell. */
    private static final class Button {
        final String label;
        final Rect rect;
        final Runnable action;
        int hoverFrames = 0;

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

        int roiSize = Math.max(40, frameHeight / 8);
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
    public void update(Point tip, Mat frame, boolean colorLocked) {
        if (!colorLocked) {
            drawCalibrationPrompt(frame);
            return;
        }

        for (Button b : buttons) {
            boolean hovered = tip != null && isInside(tip, b.rect);
            if (hovered) {
                b.hoverFrames++;
                if (b.hoverFrames >= REQUIRED_FRAMES) {
                    b.action.run();
                    b.hoverFrames = 0;
                }
            } else {
                b.hoverFrames = 0;
            }
            drawButton(frame, b);
        }
        drawStatus(frame);
    }

    private void drawButton(Mat frame, Button b) {
        boolean active = brush.getMode().name().equals(b.label);

        // Active mode -> green outline; everything else -> red outline.
        Scalar border = active ? new Scalar(0, 255, 0, 0) : new Scalar(0, 0, 255, 0);
        opencv_imgproc.rectangle(frame, b.rect, border, 2, opencv_imgproc.LINE_8, 0);

        Scalar textColor = new Scalar(255, 255, 255, 0);
        Point textOrg = new Point(b.rect.x() + 8, b.rect.y() + b.rect.height() / 2 + 5);
        opencv_imgproc.putText(frame, b.label, textOrg,
                opencv_imgproc.FONT_HERSHEY_SIMPLEX, 0.5, textColor, 1,
                opencv_imgproc.LINE_AA, false);

        // Circular loading arc that fills while hovering.
        if (b.hoverFrames > 0) {
            double ratio = (double) b.hoverFrames / REQUIRED_FRAMES;
            int angle = (int) (ratio * 360);
            Point center = new Point(b.rect.x() + b.rect.width() / 2,
                    b.rect.y() + b.rect.height() / 2);
            Size axes = new Size(b.rect.width() / 2 + 8, b.rect.height() / 2 + 8);
            Scalar arcColor = new Scalar(0, 255, 0, 0);
            opencv_imgproc.ellipse(frame, center, axes, 0, 0, angle, arcColor, 3,
                    opencv_imgproc.LINE_AA, 0);
            center.close();
            axes.close();
            arcColor.close();
        }

        border.close();
        textColor.close();
        textOrg.close();
    }

    private void drawCalibrationPrompt(Mat frame) {
        Scalar boxColor = new Scalar(0, 255, 255, 0);
        opencv_imgproc.rectangle(frame, calibrationRoi, boxColor, 2,
                opencv_imgproc.LINE_8, 0);

        Point textOrg = new Point(calibrationRoi.x() - 10, calibrationRoi.y() - 15);
        opencv_imgproc.putText(frame, "Hold object here and press SPACE", textOrg,
                opencv_imgproc.FONT_HERSHEY_SIMPLEX, 0.5, boxColor, 1,
                opencv_imgproc.LINE_AA, false);

        textOrg.close();
        boxColor.close();
    }

    private void drawStatus(Mat frame) {
        Scalar color = new Scalar(255, 255, 0, 0);
        Point org = new Point(20, frame.rows() - 45);
        opencv_imgproc.putText(frame, "Mode: " + brush.getMode(), org,
                opencv_imgproc.FONT_HERSHEY_SIMPLEX, 0.6, color, 2,
                opencv_imgproc.LINE_AA, false);

        Scalar hintColor = new Scalar(255, 255, 255, 0);
        Point hintOrg = new Point(20, frame.rows() - 20);
        opencv_imgproc.putText(frame, "C: new color   Q: quit", hintOrg,
                opencv_imgproc.FONT_HERSHEY_SIMPLEX, 0.5, hintColor, 1,
                opencv_imgproc.LINE_AA, false);

        org.close();
        color.close();
        hintOrg.close();
        hintColor.close();
    }

    private boolean isInside(Point p, Rect r) {
        return p.x() >= r.x() && p.x() <= (r.x() + r.width())
                && p.y() >= r.y() && p.y() <= (r.y() + r.height());
    }
}