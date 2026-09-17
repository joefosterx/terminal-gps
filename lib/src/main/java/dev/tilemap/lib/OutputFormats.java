package dev.tilemap.lib;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.tilemap.core.Canvas;
import dev.tilemap.core.Capabilities;
import dev.tilemap.core.Capabilities.ColorDepth;
import dev.tilemap.core.Cell;
import dev.tilemap.core.Rgb;
import java.util.Objects;

/**
 * Text encodings of a {@link Canvas}. Terminal output quantizes colors to {@code caps.color()}; HTML, SVG and
 * JSON keep exact RGB, and drop all colors when the depth is {@link ColorDepth#NONE}.
 */
final class OutputFormats {
    static final String FONT_STACK = "'DejaVu Sans Mono', 'Cascadia Mono', Menlo, Consolas, monospace";
    /** SVG cell size in user units; each row's text is stretched to exactly {@code cols * CELL_W}. */
    static final int CELL_W = 8, CELL_H = 16;

    private OutputFormats() {}

    static String format(Canvas canvas, Format fmt, Capabilities caps) {
        return switch (fmt) {
            case PLAIN -> canvas.toPlain();
            case ANSI -> canvas.toAnsi(caps);
            case HTML -> html(canvas, caps.color() != ColorDepth.NONE);
            case SVG -> svg(canvas, caps.color() != ColorDepth.NONE);
            case JSON -> json(canvas, caps.color() != ColorDepth.NONE);
        };
    }

    /** A {@code <meta charset>} plus one {@code <pre>}; spans group runs of identically styled cells. */
    static String html(Canvas canvas, boolean color) {
        StringBuilder sb = new StringBuilder();
        sb.append("<meta charset=\"utf-8\">\n");
        sb.append("<pre class=\"tilemap\" style=\"font-family: ").append(FONT_STACK)
                .append("; line-height: 1; margin: 0\">");
        for (int r = 0; r < canvas.rows(); r++) {
            int c = 0;
            while (c < canvas.cols()) {
                Cell first = canvas.cell(c, r);
                String style = cssStyle(first, color);
                int end = c;
                StringBuilder run = new StringBuilder();
                while (end < canvas.cols() && cssStyle(canvas.cell(end, r), color).equals(style)) {
                    escapeXml(run, canvas.cell(end, r).codePoint());
                    end++;
                }
                if (style.isEmpty()) {
                    sb.append(run);
                } else {
                    sb.append("<span style=\"").append(style).append("\">").append(run).append("</span>");
                }
                c = end;
            }
            sb.append('\n');
        }
        sb.append("</pre>\n");
        return sb.toString();
    }

    private static String cssStyle(Cell cell, boolean color) {
        StringBuilder s = new StringBuilder();
        if (color && cell.fg() != null) s.append("color: ").append(cell.fg().toHex()).append("; ");
        if (color && cell.bg() != null) s.append("background: ").append(cell.bg().toHex()).append("; ");
        if (cell.attrs().bold()) s.append("font-weight: bold; ");
        if (cell.attrs().dim()) s.append("opacity: 0.6; ");
        return s.toString().strip();
    }

    static String svg(Canvas canvas, boolean color) {
        int width = canvas.cols() * CELL_W, height = canvas.rows() * CELL_H;
        StringBuilder sb = new StringBuilder();
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(width).append("\" height=\"").append(height)
                .append("\" viewBox=\"0 0 ").append(width).append(' ').append(height).append("\" font-family=\"")
                .append(FONT_STACK)
                .append("\" font-size=\"").append(CELL_H - 2).append("\">\n");

        if (color) {
            for (int r = 0; r < canvas.rows(); r++) {
                int c = 0;
                while (c < canvas.cols()) {
                    Rgb bg = canvas.cell(c, r).bg();
                    int end = c + 1;
                    while (end < canvas.cols() && Objects.equals(canvas.cell(end, r).bg(), bg)) end++;
                    if (bg != null) {
                        sb.append("<rect x=\"").append(c * CELL_W).append("\" y=\"").append(r * CELL_H)
                                .append("\" width=\"").append((end - c) * CELL_W).append("\" height=\"").append(CELL_H)
                                .append("\" fill=\"").append(bg.toHex()).append("\"/>\n");
                    }
                    c = end;
                }
            }
        }

        for (int r = 0; r < canvas.rows(); r++) {
            sb.append("<text x=\"0\" y=\"").append((r + 1) * CELL_H - 4).append("\" textLength=\"").append(width)
                    .append("\" lengthAdjust=\"spacingAndGlyphs\" xml:space=\"preserve\">");
            int c = 0;
            while (c < canvas.cols()) {
                Cell first = canvas.cell(c, r);
                String attrs = svgAttrs(first, color);
                int end = c;
                StringBuilder run = new StringBuilder();
                while (end < canvas.cols() && svgAttrs(canvas.cell(end, r), color).equals(attrs)) {
                    escapeXml(run, canvas.cell(end, r).codePoint());
                    end++;
                }
                sb.append("<tspan").append(attrs).append('>').append(run).append("</tspan>");
                c = end;
            }
            sb.append("</text>\n");
        }
        sb.append("</svg>\n");
        return sb.toString();
    }

    private static String svgAttrs(Cell cell, boolean color) {
        StringBuilder s = new StringBuilder();
        if (color && cell.fg() != null) s.append(" fill=\"").append(cell.fg().toHex()).append('"');
        if (cell.attrs().bold()) s.append(" font-weight=\"bold\"");
        if (cell.attrs().dim()) s.append(" opacity=\"0.6\"");
        return s.toString();
    }

    /** {@code {"cols", "rows", "cells": [{"glyph", "fg", "bg", "layer"}, ...]}}, cells row-major. */
    static String json(Canvas canvas, boolean color) {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = mapper.createObjectNode();
        root.put("cols", canvas.cols());
        root.put("rows", canvas.rows());
        ArrayNode cells = root.putArray("cells");
        for (Cell cell : canvas.cells()) {
            ObjectNode n = cells.addObject();
            n.put("glyph", Character.toString(cell.codePoint()));
            n.put("fg", color && cell.fg() != null ? cell.fg().toHex() : null);
            n.put("bg", color && cell.bg() != null ? cell.bg().toHex() : null);
            n.put("layer", cell.layer());
        }
        try {
            return mapper.writeValueAsString(root) + "\n";
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void escapeXml(StringBuilder sb, int codePoint) {
        switch (codePoint) {
            case '<' -> sb.append("&lt;");
            case '>' -> sb.append("&gt;");
            case '&' -> sb.append("&amp;");
            default -> sb.appendCodePoint(codePoint);
        }
    }
}
