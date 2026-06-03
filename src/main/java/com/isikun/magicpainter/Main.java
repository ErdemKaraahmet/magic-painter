package com.isikun.magicpainter;

import com.isikun.magicpainter.camera.CameraManager;
import com.isikun.magicpainter.ui.BrushManager;
import com.isikun.magicpainter.ui.OverlayPanel;
import com.isikun.magicpainter.vision.ColorTracker;

import org.bytedeco.javacv.CanvasFrame;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Point;

import javax.swing.WindowConstants;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Application entry point and core loop.
 *
 * Per-frame flow (matches the architecture diagram in the spec):
 *   read frame -> mirror -> copy + convert to HSV
 *     if colour NOT locked: draw calibration ROI; SPACE samples the colour
 *     if colour locked:
 *       mask (inRange) -> Opening -> largest contour -> centroid (cx, cy)
 *         tip over a virtual button? -> hover/dwell counting (no drawing)
 *         otherwise                  -> draw according to the active mode
 *   draw history onto the live frame -> overlay the UI -> show on screen
 *
 * Controls: SPACE = calibrate the colour, Q or ESC = quit.
 */
public class Main {

    private static final int DEVICE_INDEX = 0;
    private static final int REQUEST_WIDTH = 1280;
    private static final int REQUEST_HEIGHT = 720;

    public static void main(String[] args) {
        CameraManager camera =
                new CameraManager(DEVICE_INDEX, REQUEST_WIDTH, REQUEST_HEIGHT);
        OpenCVFrameConverter.ToMat displayConverter = new OpenCVFrameConverter.ToMat();

        CanvasFrame canvas = new CanvasFrame("Magic Air Painter");
        canvas.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        CanvasFrame maskCanvas = new CanvasFrame("Pen Detection Mask");
        maskCanvas.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        maskCanvas.setLocation(canvas.getX() + canvas.getWidth(), canvas.getY());

        AtomicBoolean calibrateRequested = new AtomicBoolean(false);
        AtomicBoolean recalibrateRequested = new AtomicBoolean(false);
        AtomicBoolean quitRequested = new AtomicBoolean(false);
        canvas.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                int code = e.getKeyCode();
                if (code == KeyEvent.VK_SPACE) {
                    calibrateRequested.set(true);
                } else if (code == KeyEvent.VK_C) {
                    recalibrateRequested.set(true);
                } else if (code == KeyEvent.VK_Q || code == KeyEvent.VK_ESCAPE) {
                    quitRequested.set(true);
                }
            }
        });

        ColorTracker tracker = new ColorTracker();
        BrushManager brush = new BrushManager();

        try {
            camera.open();
            int width = camera.getFrameWidth();
            int height = camera.getFrameHeight();
            canvas.setCanvasSize(width, height);
            maskCanvas.setCanvasSize(width, height);

            OverlayPanel overlay = new OverlayPanel(width, height, brush);

            while (!quitRequested.get() && canvas.isVisible()) {
                Mat frame = camera.grabMirrored();
                if (frame == null) {
                    break;
                }
                Mat hsv = tracker.toHsv(frame);

                // Pressing C unlocks the colour and returns to calibration mode.
                if (recalibrateRequested.getAndSet(false)) {
                    tracker.resetCalibration();
                }

                if (!tracker.isColorLocked()) {
                    if (calibrateRequested.getAndSet(false)) {
                        tracker.calibrate(hsv, frame, overlay.getCalibrationRoi());
                    }
                    // Keep the existing drawing visible while choosing a new colour.
                    brush.drawOnFrame(frame);
                    overlay.update(null, frame, false, tracker.getDrawingColorBGR());
                } else {
                    Mat mask = tracker.buildCleanMask(hsv);
                    Point tip = tracker.findTip(mask);

                    if (overlay.isOverAnyButton(tip)) {
                        // Hovering the UI: count the dwell, do not draw.
                        brush.insertBreak();
                    } else {
                        // null tip means the object is hidden -> inserts a break.
                        brush.addPoint(tip, tracker.getDrawingColorBGR());
                    }

                    brush.drawOnFrame(frame);
                    overlay.update(tip, frame, true, tracker.getDrawingColorBGR());
                    overlay.drawEraserBox(frame, tip);

                    maskCanvas.showImage(displayConverter.convert(mask));

                    if (tip != null) {
                        tip.close();
                    }
                    mask.release();
                }

                canvas.showImage(displayConverter.convert(frame));

                // Release per-frame native memory (spec note 3).
                hsv.release();
                frame.release();
            }
        } catch (Exception e) {
            System.err.println("Fatal error in main loop: " + e.getMessage());
            e.printStackTrace();
        } finally {
            camera.close();
            canvas.dispose();
            maskCanvas.dispose();
        }
    }
}