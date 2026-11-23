package com.mama.app.share;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws an {@link OrderSummary} as a bill. The image and the PDF both call it, so they match.
 * Sizes are written for a 1080 px wide canvas and scale with the width. The drawing is a list of
 * rows, which lets the PDF start a new page between rows and never inside one.
 */
public final class OrderSummaryRenderer {
    /** The design width in px: margins and text sizes below are for this width. */
    public static final int DESIGN_WIDTH = 1080;

    private static final int INK = 0xFF212121;
    private static final int MUTED = 0xFF757575;
    private static final int RULE = 0xFFBDBDBD;

    /** A drawn row: {@code y} is the top of its content, {@code gap} the space above it. */
    private static final class Row {
        final int gap;
        final int height;
        final Painter painter;
        int y;

        Row(int gap, int height, Painter painter) {
            this.gap = gap;
            this.height = height;
            this.painter = painter;
        }
    }

    private interface Painter {
        void draw(Canvas canvas, int y);
    }

    /** A page of rows: the first row index, the row count and the y that maps to the page top. */
    public static final class Page {
        public final int firstRow;
        public final int rowCount;
        final int offset;

        Page(int firstRow, int rowCount, int offset) {
            this.firstRow = firstRow;
            this.rowCount = rowCount;
            this.offset = offset;
        }
    }

    private final OrderSummary summary;
    private final int stripeColor;
    private int laidOutWidth = -1;
    private List<Row> rows;
    private int margin;
    private int totalHeight;

    /** {@code stripeColor} is the kind color of the header stripe. */
    public OrderSummaryRenderer(OrderSummary summary, int stripeColor) {
        this.summary = summary;
        this.stripeColor = stripeColor;
    }

    /** The canvas height in px that fits the whole bill at {@code width}. */
    public int height(int width) {
        layout(width);
        return totalHeight;
    }

    /** Draws the whole bill from the canvas top. The canvas must be {@code height(width)} tall. */
    public void draw(Canvas canvas, int width) {
        layout(width);
        canvas.drawColor(Color.WHITE);
        for (Row r : rows) {
            r.painter.draw(canvas, r.y);
        }
    }

    /**
     * Splits the rows into pages of {@code pageHeight} px at {@code width}. A row that does not
     * fit under the bottom margin starts the next page.
     */
    public List<Page> paginate(int width, int pageHeight) {
        layout(width);
        List<Page> pages = new ArrayList<>();
        int first = 0;
        int offset = 0;
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            if (i > first && r.y + r.height - offset > pageHeight - margin) {
                pages.add(new Page(first, i - first, offset));
                first = i;
                offset = r.y - margin;
            }
        }
        pages.add(new Page(first, rows.size() - first, offset));
        return pages;
    }

    /** Draws one page. The caller has already scaled the canvas from {@code width} to the page. */
    public void drawPage(Canvas canvas, int width, Page page) {
        layout(width);
        canvas.drawColor(Color.WHITE);
        canvas.save();
        canvas.translate(0, -page.offset);
        for (int i = page.firstRow; i < page.firstRow + page.rowCount; i++) {
            Row r = rows.get(i);
            r.painter.draw(canvas, r.y);
        }
        canvas.restore();
    }

    // ---- layout ----

    private void layout(int width) {
        if (width == laidOutWidth) {
            return;
        }
        final float s = width / (float) DESIGN_WIDTH;
        margin = Math.round(48 * s);
        final int m = margin;
        final int cw = width - 2 * m;
        rows = new ArrayList<>();

        final TextPaint titlePaint = paint(60 * s, true, INK);
        final TextPaint kindPaint = paint(36 * s, true, stripeColor);
        final TextPaint customerPaint = paint(46 * s, true, INK);
        final TextPaint infoPaint = paint(36 * s, false, INK);
        final TextPaint rowPaint = paint(32 * s, false, INK);
        final TextPaint totalPaint = paint(42 * s, true, INK);
        final TextPaint amountPaint = paint(32 * s, false, INK);
        amountPaint.setFontFeatureSettings("tnum");
        final TextPaint totalAmountPaint = paint(42 * s, true, INK);
        totalAmountPaint.setFontFeatureSettings("tnum");
        final TextPaint headingPaint = paint(30 * s, true, MUTED);
        final TextPaint notesPaint = paint(32 * s, false, INK);
        final TextPaint footPaint = paint(30 * s, false, MUTED);
        final Paint fill = new Paint();
        fill.setAntiAlias(false);

        int cursor = m;

        // Header: kind stripe at the left, title and kind line beside it.
        final int stripeW = Math.round(14 * s);
        final int pad = Math.round(24 * s);
        final int textX = m + stripeW + pad;
        final StaticLayout title = text(summary.header, titlePaint, width - m - textX);
        final StaticLayout kind = text(summary.kindLine, kindPaint, width - m - textX);
        final int titleGap = Math.round(8 * s);
        final int headerH = title.getHeight() + titleGap + kind.getHeight();
        cursor = add(cursor, 0, headerH, (c, y) -> {
            fill.setColor(stripeColor);
            c.drawRect(m, y, m + stripeW, y + headerH, fill);
            c.save();
            c.translate(textX, y);
            title.draw(c);
            c.translate(0, title.getHeight() + titleGap);
            kind.draw(c);
            c.restore();
        });

        // Customer block.
        cursor = addText(cursor, Math.round(36 * s), summary.customer, customerPaint, m, cw);
        if (summary.phone != null) {
            cursor = addText(cursor, Math.round(8 * s), summary.phone, infoPaint, m, cw);
        }
        cursor = addText(cursor, Math.round(8 * s), summary.stageLine, infoPaint, m, cw);
        cursor = addText(cursor, Math.round(8 * s), summary.deadlineLine, infoPaint, m, cw);
        if (summary.nextVisitLine != null) {
            cursor = addText(cursor, Math.round(8 * s), summary.nextVisitLine, infoPaint, m, cw);
        }

        // Money table: labels at the left, amounts right-aligned.
        float amountW = 0;
        for (OrderSummary.MoneyRow r : summary.money) {
            amountW = Math.max(amountW, (r.total ? totalAmountPaint : amountPaint)
                    .measureText(r.amount));
        }
        final int amountCol = (int) Math.ceil(amountW);
        final int labelW = cw - amountCol - Math.round(24 * s);
        final int rowPad = Math.round(3 * s);
        final int rule = Math.max(1, Math.round(2 * s));
        boolean firstMoney = true;
        for (final OrderSummary.MoneyRow r : summary.money) {
            final TextPaint lp = r.total ? totalPaint : rowPaint;
            final TextPaint ap = r.total ? totalAmountPaint : amountPaint;
            final StaticLayout label = text(r.label, lp, labelW);
            final int top = r.total ? Math.round(18 * s) : 0;
            final int h = top + label.getHeight() + 2 * rowPad;
            int gap = firstMoney ? Math.round(44 * s) : (r.total ? Math.round(12 * s) : 0);
            firstMoney = false;
            final float base = label.getLineBaseline(0);
            final String amount = r.amount;
            final boolean total = r.total;
            cursor = add(cursor, gap, h, (c, y) -> {
                if (total) {
                    fill.setColor(INK);
                    c.drawRect(m, y, m + cw, y + rule, fill);
                }
                c.save();
                c.translate(m, y + top + rowPad);
                label.draw(c);
                c.restore();
                ap.setTextAlign(Paint.Align.RIGHT);
                c.drawText(amount, m + cw, y + top + rowPad + base, ap);
            });
        }

        // Notes, one text line per row so a page break falls between lines.
        if (summary.notes != null) {
            cursor = addText(cursor, Math.round(48 * s), summary.notesHeading, headingPaint, m, cw);
            final StaticLayout notes = text(summary.notes, notesPaint, cw);
            final String notesText = summary.notes;
            for (int i = 0; i < notes.getLineCount(); i++) {
                final int top = notes.getLineTop(i);
                final int h = notes.getLineBottom(i) - top;
                final int base = notes.getLineBaseline(i) - top;
                int start = notes.getLineStart(i);
                int end = notes.getLineEnd(i);
                while (end > start && Character.isWhitespace(notesText.charAt(end - 1))) {
                    end--;
                }
                final int s0 = start;
                final int e0 = end;
                cursor = add(cursor, i == 0 ? Math.round(8 * s) : 0, h, (c, y) ->
                        c.drawText(notesText, s0, e0, m, y + base, notesPaint));
            }
        }

        // Footer: a light rule and the date.
        final StaticLayout foot = text(summary.dateLine, footPaint, cw);
        final int footTop = Math.round(20 * s);
        cursor = add(cursor, Math.round(56 * s), footTop + foot.getHeight(), (c, y) -> {
            fill.setColor(RULE);
            c.drawRect(m, y, m + cw, y + rule, fill);
            c.save();
            c.translate(m, y + footTop);
            foot.draw(c);
            c.restore();
        });

        totalHeight = cursor + m;
        laidOutWidth = width;
    }

    private int add(int cursor, int gap, int height, Painter painter) {
        Row r = new Row(gap, height, painter);
        r.y = cursor + gap;
        rows.add(r);
        return r.y + height;
    }

    private int addText(int cursor, int gap, String text, TextPaint paint, final int x, int width) {
        final StaticLayout l = text(text, paint, width);
        return add(cursor, gap, l.getHeight(), (c, y) -> {
            c.save();
            c.translate(x, y);
            l.draw(c);
            c.restore();
        });
    }

    private static StaticLayout text(String text, TextPaint paint, int width) {
        return StaticLayout.Builder.obtain(text, 0, text.length(), paint, Math.max(1, width))
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setIncludePad(false)
                .build();
    }

    private static TextPaint paint(float size, boolean bold, int color) {
        TextPaint p = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        p.setTextSize(size);
        p.setColor(color);
        p.setTypeface(bold ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        return p;
    }
}
