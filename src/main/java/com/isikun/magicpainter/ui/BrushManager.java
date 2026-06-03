package com.isikun.magicpainter.ui;

import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Point;
import org.bytedeco.opencv.opencv_core.Scalar;

import java.util.ArrayList;
import java.util.List;

/**
 * Stores the drawing history and replays it on every live frame.
 *
 * Memory note (spec note 3): the history is stored as plain Java coordinates,
 * NOT as native OpenCV Point objects. Native pointers must not be kept across
 * frames (the garbage collector and JavaCV deallocators would fight over them),
 * so we only build short-lived Point/Scalar objects while drawing and close them
 * immediately.
 *
 * A "gap marker" is inserted whenever the pen is lifted or the mode changes, so
 * two unrelated segments are never joined by a stray line.
 *                                          -> Continuous drawing + gap safety check
 */
public class BrushManager {

    /** Phase 1 drawing modes. */
    public enum Mode { PEN, BRUSH, ERASER }
    
    /** A single recorded sample. Intentionally plain data, not an OpenCV Point. */
    private static final class StrokePoint {
        final int x;
        final int y;
        final boolean isBreak;
        final double[] colorBgr; // null on break / eraser markers
        final int thickness;

        StrokePoint(int x, int y, boolean isBreak, double[] colorBgr, int thickness) {
            this.x = x;
            this.y = y;
            this.isBreak = isBreak;
            this.colorBgr = colorBgr;
            this.thickness = thickness;
        }
    }

    private static final int PEN_THICKNESS = 3;
    private static final int BRUSH_THICKNESS = 14;
    private static final int ERASER_RADIUS = 50;

    private final List<StrokePoint> history = new ArrayList<>();
    private Mode mode = Mode.PEN;

    public void setMode(Mode newMode) {
        this.mode = newMode;
        // A mode switch must not connect the new stroke to the previous one.
        insertBreak();
    }

    public Mode getMode() {
        return mode;
    }

    /** Records a break marker (pen lifted, object hidden, or mode changed). */
    public void insertBreak() {
        if (history.isEmpty() || !history.get(history.size() - 1).isBreak) {
            history.add(new StrokePoint(-1, -1, true, null, 0));
        }
    }

    /**
     * Adds a sample at the given tip using the active mode and colour. A null tip
     * is interpreted as the pen being lifted and inserts a break. In ERASER mode
     * the nearby history is removed instead of adding ink.
     */
    public void addPoint(Point tip, Scalar drawingColorBgr) {
        if (tip == null) {
            insertBreak();
            return;
        }
        int x = tip.x();
        int y = tip.y();

        if (mode == Mode.ERASER) {
            eraseNear(x, y);
            return;
        }

        double[] color = {
                drawingColorBgr.get(0),
                drawingColorBgr.get(1),
                drawingColorBgr.get(2)
        };
        int thickness = (mode == Mode.BRUSH) ? BRUSH_THICKNESS : PEN_THICKNESS;
        history.add(new StrokePoint(x, y, false, color, thickness));
    }

    /** Removes drawn points within the eraser radius and splits the stroke there. */
    private void eraseNear(int x, int y) {
        List<StrokePoint> kept = new ArrayList<>(history.size());
        for (StrokePoint p : history) {
            if (p.isBreak) {
                kept.add(p);
                continue;
            }
            int dx = p.x - x;
            int dy = p.y - y;
            if (dx * dx + dy * dy > ERASER_RADIUS * ERASER_RADIUS) {
                kept.add(p);
            } else {
                kept.add(new StrokePoint(-1, -1, true, null, 0));
            }
        }
        history.clear();
        history.addAll(kept);
    }

    /** Wipes the whole drawing. */
    public void clear() {
        history.clear();
    }

    /**
     * Replays the entire history onto the frame. Consecutive non-break points are
     * joined; a break on either endpoint skips that segment (the gap check).
     */
    public void drawOnFrame(Mat frame) {
        Point p1 = new Point();
        Point p2 = new Point();
        try {
            for (int i = 1; i < history.size(); i++) {
                StrokePoint a = history.get(i - 1);
                StrokePoint b = history.get(i);
                if (a.isBreak || b.isBreak) {
                    continue; // never connect across a break
                }
                p1.x(a.x).y(a.y);
                p2.x(b.x).y(b.y);
                Scalar color = new Scalar(b.colorBgr[0], b.colorBgr[1], b.colorBgr[2], 0);
                opencv_imgproc.line(frame, p1, p2, color, b.thickness,
                        opencv_imgproc.LINE_AA, 0);
                color.close();
            }
        } finally {
            p1.close();
            p2.close();
        }
    }
}
