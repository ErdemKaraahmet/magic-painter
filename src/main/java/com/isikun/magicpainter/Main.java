package com.isikun.magicpainter;

import com.isikun.magicpainter.camera.CameraManager;
import com.isikun.magicpainter.ui.BrushManager;
import com.isikun.magicpainter.ui.OverlayPanel;
import com.isikun.magicpainter.vision.ColorTracker;
import com.isikun.magicpainter.vision.ShapeAnalyzer;

import org.bytedeco.javacv.CanvasFrame;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Point;
import org.bytedeco.opencv.opencv_core.Scalar;

import javax.swing.WindowConstants;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
 * Controls: SPACE = calibrate the colour, Q or ESC = quit, S = save screenshot.
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
        AtomicBoolean saveRequested = new AtomicBoolean(false);
        canvas.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                int code = e.getKeyCode();
                if (code == KeyEvent.VK_SPACE) {
                    calibrateRequested.set(true);
                } else if (code == KeyEvent.VK_C) {
                    recalibrateRequested.set(true);
                } else if (code == KeyEvent.VK_S) {
                    saveRequested.set(true);
                } else if (code == KeyEvent.VK_Q || code == KeyEvent.VK_ESCAPE) {
                    quitRequested.set(true);
                }
            }
        });

        ColorTracker tracker = new ColorTracker();
        BrushManager brush = new BrushManager();
        ShapeAnalyzer analyzer = new ShapeAnalyzer();

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
                    // 1. Draw non-glowing lines directly on the frame
                    brush.drawOnFrame(frame, false);

                    // 2. Draw glowing lines on a separate layer, blur it, and add it
                    Mat glowLayer = new Mat(frame.size(), frame.type(), new Scalar(0, 0, 0, 0));
                    brush.drawOnFrame(glowLayer, true);
                    if (org.bytedeco.opencv.global.opencv_core.countNonZero(glowLayer.reshape(1)) > 0) {
                        analyzer.applyGlow(glowLayer, 15);
                        org.bytedeco.opencv.global.opencv_core.addWeighted(frame, 1.0, glowLayer, 1.5, 0, frame);
                    }
                    glowLayer.release();

                    overlay.update(null, frame, false, tracker.getDrawingColorBGR());
                } else {
                    Mat mask = tracker.buildCleanMask(hsv);
                    Point tip = tracker.findTip(mask);

                    if (overlay.isOverAnyButton(tip)) {
                        // Hovering the UI: count the dwell, do not draw.
                        brush.insertBreak();
                    } else {
                        int thickness = 0; // Use default for the mode
                        if (brush.getMode() == BrushManager.Mode.BRUSH) {
                            // Phase 2: Dynamic thickness based on contour area for BRUSH only.
                            thickness = analyzer.dynamicThickness(tracker.getLastMaxArea());
                        }
                        // null tip means the object is hidden -> inserts a break.
                        brush.addPoint(tip, tracker.getDrawingColorBGR(), thickness);
                    }

                    // 1. Draw non-glowing lines directly on the frame
                    brush.drawOnFrame(frame, false);

                    // 2. Draw glowing lines on a separate layer, blur it, and add it
                    Mat glowLayer = new Mat(frame.size(), frame.type(), new Scalar(0, 0, 0, 0));
                    brush.drawOnFrame(glowLayer, true);
                    
                    // Check if anything was actually drawn on the glow layer
                    if (org.bytedeco.opencv.global.opencv_core.countNonZero(glowLayer.reshape(1)) > 0) {
                        analyzer.applyGlow(glowLayer, 15);
                        org.bytedeco.opencv.global.opencv_core.addWeighted(frame, 1.0, glowLayer, 1.5, 0, frame);
                    }
                    glowLayer.release();
                    
                    overlay.update(tip, frame, true, tracker.getDrawingColorBGR());
                    overlay.drawEraserBox(frame, tip);

                    maskCanvas.showImage(displayConverter.convert(mask));

                    if (tip != null) {
                        tip.close();
                    }
                    mask.release();
                }

                if (saveRequested.getAndSet(false)) {
                    saveScreenshot(frame);
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

    private static void saveScreenshot(Mat frame) {
        File outputsDir = new File("outputs");
        if (!outputsDir.exists()) {
            outputsDir.mkdirs();
        }

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filename = "outputs/screenshot_" + timestamp + ".png";
        
        if (opencv_imgcodecs.imwrite(filename, frame)) {
            System.out.println("Screenshot saved to: " + filename);
        } else {
            System.err.println("Failed to save screenshot to: " + filename);
        }
    }
}