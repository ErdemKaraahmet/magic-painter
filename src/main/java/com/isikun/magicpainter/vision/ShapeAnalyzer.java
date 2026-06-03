package com.isikun.magicpainter.vision;

import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Size;

/**
 * Phase 2 helpers.
 *
 *  - dynamicThickness: maps the tracked object's contour area to a brush width,
 *    so moving the object closer to the camera (a larger area) draws thicker
 *    lines.                                             -> Region properties (W3-4)
 *  - applyGlow: a low-pass Gaussian blur used to fake a neon / light-saber glow.
 *                                                       -> Spatial filtering (W6)
 *
 * This class is ready to wire into BrushManager when Phase 2 is activated.
 */
public class ShapeAnalyzer {

    private static final int MIN_THICKNESS = 3;
    private static final int MAX_THICKNESS = 40;

    /** Maps a contour area to a clamped line thickness. */
    public int dynamicThickness(double contourArea) {
        // sqrt keeps the response proportional to the object's diameter rather
        // than its area, which feels more natural to the user.
        int thickness = (int) (Math.sqrt(contourArea) / 6.0);
        return Math.max(MIN_THICKNESS, Math.min(MAX_THICKNESS, thickness));
    }

    /** Applies an in-place Gaussian low-pass blur to create a glow effect. */
    public void applyGlow(Mat frame, int kernelSize) {
        int k = (kernelSize % 2 == 0) ? kernelSize + 1 : kernelSize; // must be odd
        Size kernel = new Size(k, k);
        opencv_imgproc.GaussianBlur(frame, frame, kernel, 0);
        kernel.close();
    }
}
