package com.isikun.magicpainter.camera;

import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameGrabber;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.javacv.OpenCVFrameGrabber;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_core.Mat;

/**
 * Owns the webcam grabber and converts captured frames into OpenCV Mat objects.
 *
 * Design rule (spec note 1): the very first operation applied to every captured
 * frame is a horizontal flip (mirroring), so the on-screen image behaves like a
 * real mirror before any coordinate work begins.
 *
 * Design rule (spec note 2): the real resolution is read from the driver after
 * the stream starts, so the rest of the UI can scale dynamically.
 */
public class CameraManager {

    private final OpenCVFrameGrabber grabber;
    private final OpenCVFrameConverter.ToMat converter = new OpenCVFrameConverter.ToMat();

    private int frameWidth;
    private int frameHeight;

    public CameraManager(int deviceIndex, int requestedWidth, int requestedHeight) {
        this.grabber = new OpenCVFrameGrabber(deviceIndex);
        this.grabber.setImageWidth(requestedWidth);
        this.grabber.setImageHeight(requestedHeight);
    }

    /** Opens the camera stream and stores the real (driver-decided) resolution. */
    public void open() throws FrameGrabber.Exception {
        grabber.start();
        this.frameWidth = grabber.getImageWidth();
        this.frameHeight = grabber.getImageHeight();
    }

    /**
     * Grabs one frame and returns it already mirrored on the horizontal axis
     * (flip code 1). Returns null when no frame is available.
     *
     * The returned Mat is owned by the caller and must be released after use.
     */
    public Mat grabMirrored() throws FrameGrabber.Exception {
        Frame frame = grabber.grab();
        if (frame == null || frame.image == null) {
            return null;
        }
        // The Mat returned by the converter is reused internally, so we flip into
        // a fresh Mat that the caller fully owns.
        Mat captured = converter.convert(frame);
        if (captured == null) {
            return null;
        }
        Mat mirrored = new Mat();
        opencv_core.flip(captured, mirrored, 1);
        return mirrored;
    }

    public int getFrameWidth() {
        return frameWidth;
    }

    public int getFrameHeight() {
        return frameHeight;
    }

    public void close() {
        try {
            grabber.stop();
            grabber.release();
        } catch (FrameGrabber.Exception ignored) {
            // Nothing actionable can be done on a shutdown failure.
        }
    }
}
