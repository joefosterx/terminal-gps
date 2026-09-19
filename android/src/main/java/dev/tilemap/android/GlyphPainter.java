package dev.tilemap.android;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;

/**
 * Draws one cell's glyph. Braille, box-drawing, block and sextant glyphs are shapes from {@link GlyphTable}, so
 * they look the same on every device and meet exactly at cell edges; everything else is text in the monospace face.
 */
final class GlyphPainter {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint boldText = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private float w = 1;
    private float h = 2;
    private float baseline;
    private float advance;
    private final char[] chars = new char[2];

    GlyphPainter() {
        text.setTypeface(Typeface.MONOSPACE);
        boldText.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
    }

    /** Sizes the text so that one glyph advance fits the cell width. */
    void setCellSize(float cellW, float cellH) {
        w = cellW;
        h = cellH;
        text.setTextSize(cellH * 0.82f);
        float adv = text.measureText("M");
        if (adv > cellW * 0.98f) text.setTextSize(text.getTextSize() * cellW * 0.98f / adv);
        boldText.setTextSize(text.getTextSize());
        advance = text.measureText("M");
        Paint.FontMetrics fm = text.getFontMetrics();
        baseline = (cellH - (fm.ascent + fm.descent)) / 2;
    }

    void draw(Canvas c, int cp, int color, boolean bold, float x, float y) {
        fill.setColor(color);
        switch (GlyphTable.kind(cp)) {
            case BRAILLE -> braille(c, GlyphTable.brailleMask(cp), x, y);
            case BOX -> box(c, GlyphTable.boxArms(cp), x, y);
            case QUADRANT -> grid(c, GlyphTable.quadrantMask(cp), 2, x, y);
            case SEXTANT -> grid(c, GlyphTable.sextantMask(cp), 3, x, y);
            case SHADE -> {
                fill.setAlpha(64 * GlyphTable.shadeLevel(cp));
                c.drawRect(x, y, x + w, y + h, fill);
            }
            case TEXT -> {
                if (cp == ' ') return;
                Paint p = bold ? boldText : text;
                p.setColor(color);
                int n = Character.toChars(cp, chars, 0);
                c.drawText(chars, 0, n, x + (w - advance) / 2, y + baseline, p);
            }
        }
    }

    private void braille(Canvas c, int mask, float x, float y) {
        float dx = w / 2, dy = h / 4, r = Math.min(dx, dy) * 0.36f;
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 2; col++) {
                if ((mask & (1 << (row * 2 + col))) != 0) c.drawCircle(x + (col + 0.5f) * dx, y + (row + 0.5f) * dy, r, fill);
            }
        }
    }

    private void grid(Canvas c, int mask, int rows, float x, float y) {
        float dx = w / 2, dy = h / rows;
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < 2; col++) {
                if ((mask & (1 << (row * 2 + col))) != 0) {
                    c.drawRect(x + col * dx, y + row * dy, x + (col + 1) * dx, y + (row + 1) * dy, fill);
                }
            }
        }
    }

    /** Arms run from the cell center to its edge so neighbors meet; heavy is wider, double is two light lines. */
    private void box(Canvas c, int arms, float x, float y) {
        float light = Math.max(1f, w * 0.14f), heavy = w * 0.34f, offset = w * 0.16f;
        float cx = x + w / 2, cy = y + h / 2;
        for (int d = 0; d < 4; d++) {
            int weight = GlyphTable.arm(arms, d);
            if (weight == GlyphTable.NONE) continue;
            if (weight == GlyphTable.DOUBLE) {
                arm(c, d, x, y, cx, cy, light, -offset);
                arm(c, d, x, y, cx, cy, light, offset);
            } else {
                arm(c, d, x, y, cx, cy, weight == GlyphTable.HEAVY ? heavy : light, 0);
            }
        }
        // A double corner or tee: fill the center square so the two rails of each arm join up.
        int count = 0;
        boolean anyDouble = false;
        for (int d = 0; d < 4; d++) {
            int weight = GlyphTable.arm(arms, d);
            if (weight != GlyphTable.NONE) count++;
            anyDouble |= weight == GlyphTable.DOUBLE;
        }
        if (anyDouble && count >= 2 && count < 4) {
            float t = light / 2;
            c.drawRect(cx - offset - t, cy - offset - t, cx + offset + t, cy + offset + t, fill);
        }
    }

    /** One arm: {@code d} is 0 N, 1 E, 2 S, 3 W; {@code shift} moves it sideways for double lines. */
    private void arm(Canvas c, int d, float x, float y, float cx, float cy, float t, float shift) {
        float half = t / 2;
        switch (d) {
            case 0 -> c.drawRect(cx + shift - half, y, cx + shift + half, cy + half + Math.abs(shift), fill);
            case 2 -> c.drawRect(cx + shift - half, cy - half - Math.abs(shift), cx + shift + half, y + h, fill);
            case 1 -> c.drawRect(cx - half - Math.abs(shift), cy + shift - half, x + w, cy + shift + half, fill);
            case 3 -> c.drawRect(x, cy + shift - half, cx + half + Math.abs(shift), cy + shift + half, fill);
            default -> throw new IllegalArgumentException("direction " + d);
        }
    }
}
