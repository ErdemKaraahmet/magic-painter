package com.isikun.magicpainter.vision;

import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.MatVector;
import org.bytedeco.opencv.opencv_core.Moments;
import org.bytedeco.opencv.opencv_core.Point;
import org.bytedeco.opencv.opencv_core.Rect;
import org.bytedeco.opencv.opencv_core.Scalar;
import org.bytedeco.opencv.opencv_core.Size;

/**
 * Core computer-vision pipeline.
 *
 * Responsibilities and syllabus mapping:
 *  - Convert BGR frames to HSV, which is far more robust against lighting
 *    changes (shadows, glare, room lights).  -> Colour space conversion (W4)
 *  - Sample a colour from a calibration ROI and derive HSV lower/upper bounds.
 *  - Build a binary mask with inRange.                  -> Thresholding (W2-3)
 *  - Clean the mask with a morphological Opening
 *    (Erosion then Dilation).                           -> Morphology (W3)
 *  - Find the largest contour and compute its centroid
 *    from image moments: Cx = M10/M00, Cy = M01/M00.    -> Region properties (W3-4)
 */
public class ColorTracker {

    /** Contours smaller than this (in pixels) are treated as noise. */
    private static final int MIN_VALID_AREA = 300;

    /** Side length of the square structuring element used for Opening. */
    private static final int MORPH_KERNEL_SIZE = 5;

    /** HSV tolerances applied around the sampled mean colour. */
    private static final double H_TOL = 10;
    private static final double S_TOL = 40;
    private static final double V_TOL = 40;

    /** Smoothing factor for the drawing tip coordinates (0.0 to 1.0). Lower is smoother. */
    private static final double SMOOTHING_FACTOR = 0.3;

    private Scalar lowerBound;
    private Scalar upperBound;

    /** Average BGR colour of the calibrated object; used as the drawing colour. */
    private Scalar drawingColorBGR = new Scalar(0, 0, 0, 0);

    private boolean colorLocked = false;

    private double smoothedX = -1;
    private double smoothedY = -1;

    /** Converts a BGR frame into a new HSV Mat. The caller must release it. */
    public Mat toHsv(Mat bgrFrame) {
        Mat hsv = new Mat();
        opencv_imgproc.cvtColor(bgrFrame, hsv, opencv_imgproc.COLOR_BGR2HSV);
        return hsv;
    }

    /**
     * Samples the mean colour inside the ROI and locks the HSV bounds plus the
     * average BGR colour used for drawing.
     */
    public void calibrate(Mat hsvFrame, Mat bgrFrame, Rect roi) {
        Mat roiHsv = new Mat(hsvFrame, roi);
        Mat roiBgr = new Mat(bgrFrame, roi);

        Scalar meanHsv = opencv_core.mean(roiHsv);
        this.drawingColorBGR = opencv_core.mean(roiBgr);

        double h = meanHsv.get(0);
        double s = meanHsv.get(1);
        double v = meanHsv.get(2);

        this.lowerBound = new Scalar(
                Math.max(0, h - H_TOL),
                Math.max(50, s - S_TOL),
                Math.max(50, v - V_TOL),
                0);
        this.upperBound = new Scalar(
                Math.min(180, h + H_TOL),
                Math.min(255, s + S_TOL),
                Math.min(255, v + V_TOL),
                0);

        this.colorLocked = true;

        roiHsv.release();
        roiBgr.release();
    }

    /**
     * Builds the binary mask for the locked colour and cleans it with an Opening.
     * The caller must release the returned mask.
     */
    public Mat buildCleanMask(Mat hsvFrame) {
        Mat mask = new Mat();

        // Color Masking
        Mat lower = new Mat(lowerBound);
        Mat upper = new Mat(upperBound);
        opencv_core.inRange(hsvFrame, lower, upper, mask);
        lower.release();
        upper.release();

        // Morphological Cleaning
        Mat kernel = opencv_imgproc.getStructuringElement(
                opencv_imgproc.MORPH_RECT,
                new Size(MORPH_KERNEL_SIZE, MORPH_KERNEL_SIZE));
        opencv_imgproc.morphologyEx(mask, mask, opencv_imgproc.MORPH_OPEN, kernel);
        kernel.release();

        return mask;
    }

    /**
     * Finds the largest contour in the mask and returns its centroid as a Point,
     * or null when no contour is big enough. Applies Exponential Moving Average (EMA)
     * for smoothing.
     */
    public Point findTip(Mat mask) {
        Point tip = null;
        try (MatVector contours = new MatVector()) {
            opencv_imgproc.findContours(mask, contours,
                    opencv_imgproc.RETR_EXTERNAL, opencv_imgproc.CHAIN_APPROX_SIMPLE);

            double maxArea = 0;
            Mat largest = null;
            for (long i = 0; i < contours.size(); i++) {
                Mat c = contours.get(i);
                double area = opencv_imgproc.contourArea(c);
                if (area > maxArea) {
                    maxArea = area;
                    largest = c;
                }
            }

            if (largest != null && maxArea > MIN_VALID_AREA) {
                Moments mu = opencv_imgproc.moments(largest);
                double m00 = mu.m00();
                if (m00 != 0) {
                    int rawX = (int) (mu.m10() / m00);
                    int rawY = (int) (mu.m01() / m00);

                    // Apply Exponential Moving Average (EMA) for smoothing
                    if (smoothedX < 0) {
                        smoothedX = rawX;
                        smoothedY = rawY;
                    } else {
                        smoothedX = (rawX * SMOOTHING_FACTOR) + (smoothedX * (1.0 - SMOOTHING_FACTOR));
                        smoothedY = (rawY * SMOOTHING_FACTOR) + (smoothedY * (1.0 - SMOOTHING_FACTOR));
                    }
                    tip = new Point((int) smoothedX, (int) smoothedY);
                }
            } else {
                // Reset smoothing when object is lost
                smoothedX = -1;
                smoothedY = -1;
            }
        }
        return tip;
    }

    public boolean isColorLocked() {
        return colorLocked;
    }

    public Scalar getDrawingColorBGR() {
        return drawingColorBGR;
    }

    /**
     * Unlocks the colour so the app returns to calibration mode.
     */
    public void resetCalibration() {
        this.colorLocked = false;
        this.smoothedX = -1;
        this.smoothedY = -1;
    }
}