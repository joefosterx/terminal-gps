package dev.tilemap.android;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.OverScroller;
import dev.tilemap.core.Attrs;
import dev.tilemap.core.Capabilities.ColorDepth;
import dev.tilemap.core.Cell;
import dev.tilemap.core.Rgb;
import java.util.function.Consumer;

/**
 * A grid of equal cells painted from a {@link MapViewModel.Frame}. The view chooses the cell size and reports how
 * many cells fit; the model renders exactly that many. Gestures are turned into cell units and handed to
 * {@link GridGestures}.
 */
public final class TextGridView extends View {
    interface SizeListener {
        void onGridSize(int cols, int rows);
    }

    private static final int LIGHT_FG = 0xff202020, LIGHT_BG = 0xffffffff, DARK_FG = 0xffe0e0e0, DARK_BG = 0xff101010;
    private static final long TWO_FINGER_TAP_MILLIS = 250;
    private static final int FLING_LIMIT = 1 << 20;

    private final GlyphPainter painter = new GlyphPainter();
    private final Paint fill = new Paint();
    private final Paint cursor = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final GestureDetector gestures;
    private final ScaleGestureDetector scaler;
    private final OverScroller scroller;
    private GridGestures handler;
    private SizeListener sizeListener;
    private Consumer<Boolean> interaction;
    private boolean interacting;
    private MapViewModel.Frame frame;
    private float cellW;
    private float cellH;
    private int cols;
    private int rows;
    private long secondFingerDown = -1;
    private boolean scaledSinceSecondFinger;
    private int flingX;
    private int flingY;

    public TextGridView(Context context, AttributeSet attrs) {
        super(context, attrs);
        cursor.setStyle(Paint.Style.STROKE);
        cursor.setColor(0xffff4040);
        scroller = new OverScroller(context);
        setCellWidthDp(8);
        gestures = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                scroller.forceFinished(true);
                return true;
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                // distance is previous minus current: positive when the finger moves left/up.
                if (handler != null && handler.drag(-distanceX / cellW, -distanceY / cellH)) invalidate();
                return true;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                flingX = 0;
                flingY = 0;
                scroller.fling(0, 0, (int) velocityX, (int) velocityY, -FLING_LIMIT, FLING_LIMIT, -FLING_LIMIT, FLING_LIMIT);
                postOnAnimation(TextGridView.this::flingStep);
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (handler != null) handler.doubleTap(e.getX() / cellW, e.getY() / cellH);
                return true;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                if (handler != null) {
                    performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                    handler.longPress((int) (e.getX() / cellW), (int) (e.getY() / cellH));
                }
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                return handler != null && handler.tap((int) (e.getX() / cellW), (int) (e.getY() / cellH));
            }
        });
        scaler = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector d) {
                float factor = d.getScaleFactor();
                if (Math.abs(factor - 1) > 0.02f) scaledSinceSecondFinger = true;
                if (handler != null) {
                    handler.pinch(d.getFocusX() / cellW, d.getFocusY() / cellH, Math.log(factor) / Math.log(2));
                }
                return true;
            }
        });
        scaler.setQuickScaleEnabled(false);
    }

    void setGestures(GridGestures handler) {
        this.handler = handler;
    }

    /** Told true while a finger is down or a fling runs, false when the map settles; the model skips labels meanwhile. */
    void setInteractionListener(Consumer<Boolean> listener) {
        interaction = listener;
    }

    private void interacting(boolean now) {
        if (now == interacting) return;
        interacting = now;
        if (interaction != null) interaction.accept(now);
    }

    void setSizeListener(SizeListener listener) {
        sizeListener = listener;
        if (cols > 0) listener.onGridSize(cols, rows);
    }

    /** Cells are always 1:2 so the renderer's default cell aspect holds without calibration. */
    void setCellWidthDp(float dp) {
        float density = getResources().getDisplayMetrics().density;
        cellW = Math.max(2f, dp * density);
        cellH = cellW * 2;
        painter.setCellSize(cellW, cellH);
        recount(getWidth(), getHeight());
        invalidate();
    }

    int cols() {
        return cols;
    }

    int rows() {
        return rows;
    }

    void setFrame(MapViewModel.Frame frame) {
        this.frame = frame;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        recount(w, h);
    }

    private void recount(int w, int h) {
        int c = (int) (w / cellW), r = (int) (h / cellH);
        if (c == cols && r == rows) return;
        cols = c;
        rows = r;
        if (sizeListener != null && cols > 0 && rows > 0) sizeListener.onGridSize(cols, rows);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        MapViewModel.Frame f = frame;
        boolean dark = f == null || f.darkBackground();
        canvas.drawColor(dark ? DARK_BG : LIGHT_BG);
        if (f == null) return;
        paint(canvas, f, 0, 0, cellW, cellH, painter);
        if (f.cursorCol() >= 0) {
            cursor.setStrokeWidth(Math.max(2f, cellW * 0.2f));
            canvas.drawRect(f.cursorCol() * cellW, f.cursorRow() * cellH, (f.cursorCol() + 1) * cellW, (f.cursorRow() + 1) * cellH, cursor);
        }
    }

    /** Paints a frame's cells; shared by the screen and {@link #toBitmap}. */
    private void paint(Canvas canvas, MapViewModel.Frame f, float ox, float oy, float cw, float ch, GlyphPainter p) {
        boolean dark = f.darkBackground();
        int defaultFg = dark ? DARK_FG : LIGHT_FG, defaultBg = dark ? DARK_BG : LIGHT_BG;
        ColorDepth depth = f.depth();
        boolean colors = depth != ColorDepth.NONE;
        Cell[] cells = f.cells();
        for (int r = 0; r < f.rows(); r++) {
            float y = oy + r * ch;
            for (int c = 0; c < f.cols(); c++) {
                Cell cell = cells[r * f.cols() + c];
                float x = ox + c * cw;
                int fg = color(cell.fg(), depth, colors, defaultFg), bg = color(cell.bg(), depth, colors, defaultBg);
                Attrs attrs = cell.attrs();
                if (attrs.reverse()) {
                    int t = fg;
                    fg = bg;
                    bg = t;
                }
                if (attrs.dim()) fg = dim(fg, dark);
                if (bg != defaultBg) {
                    fill.setColor(bg);
                    canvas.drawRect(x, y, x + cw, y + ch, fill);
                }
                p.draw(canvas, cell.codePoint(), fg, attrs.bold(), x, y);
            }
        }
    }

    private static int color(Rgb c, ColorDepth depth, boolean colors, int fallback) {
        return c == null || !colors ? fallback : Palette.argb(c, depth);
    }

    private static int dim(int argb, boolean dark) {
        int target = dark ? 0x10 : 0xf0;
        int r = (Color.red(argb) + target) / 2, g = (Color.green(argb) + target) / 2, b = (Color.blue(argb) + target) / 2;
        return Color.rgb(r, g, b);
    }

    /** The current frame as an image at {@code scale} times the on-screen cell size, with a footer row of text. */
    Bitmap toBitmap(float scale, String footer) {
        MapViewModel.Frame f = frame;
        if (f == null) return null;
        float cw = cellW * scale, ch = cellH * scale;
        int width = Math.round(f.cols() * cw), height = Math.round((f.rows() + 1) * ch);
        Bitmap bitmap = Bitmap.createBitmap(Math.max(1, width), Math.max(1, height), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(f.darkBackground() ? DARK_BG : LIGHT_BG);
        GlyphPainter p = new GlyphPainter();
        p.setCellSize(cw, ch);
        paint(canvas, f, 0, 0, cw, ch, p);
        int fg = f.darkBackground() ? DARK_FG : LIGHT_FG;
        int[] cps = footer.codePoints().toArray();
        for (int i = 0; i < cps.length && i < f.cols(); i++) p.draw(canvas, cps[i], fg, false, i * cw, f.rows() * ch);
        return bitmap;
    }

    private void flingStep() {
        if (!scroller.computeScrollOffset()) {
            interacting(false);
            return;
        }
        int x = scroller.getCurrX(), y = scroller.getCurrY();
        if (handler != null && handler.drag((x - flingX) / cellW, (y - flingY) / cellH)) invalidate();
        flingX = x;
        flingY = y;
        postOnAnimation(this::flingStep);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN -> interacting(true);
            case MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.getPointerCount() == 2) {
                    secondFingerDown = event.getEventTime();
                    scaledSinceSecondFinger = false;
                }
            }
            case MotionEvent.ACTION_POINTER_UP -> {
                if (event.getPointerCount() == 2 && secondFingerDown >= 0 && !scaledSinceSecondFinger
                        && event.getEventTime() - secondFingerDown < TWO_FINGER_TAP_MILLIS && handler != null) {
                    handler.twoFingerTap();
                }
                secondFingerDown = -1;
            }
            case MotionEvent.ACTION_UP -> {
                secondFingerDown = -1;
                performClick();
            }
            case MotionEvent.ACTION_CANCEL -> secondFingerDown = -1;
            default -> { }
        }
        scaler.onTouchEvent(event);
        gestures.onTouchEvent(event);
        // After the detectors, so a fling started by this event keeps the interaction open until it stops.
        if ((event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL)
                && scroller.isFinished()) {
            interacting(false);
        }
        return true;
    }

    /** Taps are handled through the gesture detector; this keeps accessibility services informed. */
    @Override
    public boolean performClick() {
        return super.performClick();
    }
}
