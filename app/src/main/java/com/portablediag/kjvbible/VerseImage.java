package com.portablediag.kjvbible;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;

import java.util.List;

/**
 * Renders selected verses to a shareable square image that respects the current theme
 * (light/dark) and keeps the red-letter words of Christ and gold OT words of God, with
 * inline verse numbers and a full reference (Book Chapter:Verse).
 */
public final class VerseImage {

    private VerseImage() {}

    private static final int SIZE = 1080;
    private static final int PAD = 96;

    public static Bitmap render(Context ctx, Bible bible, DivineSpeech divineSpeech,
                                List<Ref> refs) {
        int bg = ThemeUtil.color(ctx, android.R.attr.colorBackground);
        int textColor = ThemeUtil.color(ctx, R.attr.verseTextColor);
        int verseNumColor = ThemeUtil.color(ctx, R.attr.verseNumColor);
        int redColor = ThemeUtil.color(ctx, R.attr.redLetterColor);
        int goldColor = ThemeUtil.color(ctx, R.attr.goldLetterColor);
        int secondary = ThemeUtil.color(ctx, R.attr.verseSecondaryColor);

        // Styled passage: inline superscript verse numbers + red-letter + gold spans.
        SpannableStringBuilder body = new SpannableStringBuilder();
        for (Ref r : refs) {
            if (body.length() > 0) body.append("  ");
            String raw = bible.verse(r.book, r.chapter, r.verse);
            int[][] gold = (r.book <= 39) ? divineSpeech.find(r.book, r.chapter, r.verse) : null;
            CharSequence line = VerseFormatter.verseLine(
                    r.verse, raw, redColor, verseNumColor,
                    null, null, 0, null, gold, goldColor);
            body.append(line);
        }

        Bitmap bmp = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        c.drawColor(bg);

        int contentW = SIZE - 2 * PAD;

        // Reference + small app mark live in a reserved footer band.
        String refLabel = ShareUtil.reference(bible, refs);
        Paint refPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        refPaint.setColor(redColor);
        refPaint.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
        refPaint.setTextSize(44);
        refPaint.setTextAlign(Paint.Align.CENTER);

        Paint markPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        markPaint.setColor(secondary);
        markPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));
        markPaint.setTextSize(28);
        markPaint.setTextAlign(Paint.Align.CENTER);
        markPaint.setLetterSpacing(0.12f);

        int footerH = 200;
        int availH = SIZE - 2 * PAD - footerH;

        // Fit the passage by shrinking the type until it fits the available height.
        TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        tp.setColor(textColor);
        tp.setTypeface(Typeface.create(Typeface.SERIF, Typeface.NORMAL));

        StaticLayout layout = null;
        float size = 60f;
        final float min = 30f;
        while (size >= min) {
            tp.setTextSize(size);
            layout = build(body, tp, contentW, Integer.MAX_VALUE);
            if (layout.getHeight() <= availH) break;
            size -= 2f;
        }
        if (layout != null && layout.getHeight() > availH) {
            // Still too tall at min size: clamp lines and ellipsize.
            tp.setTextSize(min);
            int lineH = Math.max(1, layout.getHeight() / Math.max(1, layout.getLineCount()));
            int maxLines = Math.max(1, availH / lineH);
            layout = build(body, tp, contentW, maxLines);
        }

        int blockTop = PAD + Math.max(0, (availH - layout.getHeight()) / 2);
        c.save();
        c.translate(PAD, blockTop);
        layout.draw(c);
        c.restore();

        // Accent divider above the reference.
        Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        line.setColor(redColor);
        line.setStrokeWidth(4f);
        float dividerY = SIZE - PAD - footerH + 70;
        c.drawLine(SIZE / 2f - 60, dividerY, SIZE / 2f + 60, dividerY, line);

        c.drawText(refLabel, SIZE / 2f, dividerY + 64, refPaint);
        c.drawText("KJV BIBLE", SIZE / 2f, SIZE - PAD + 4, markPaint);

        return bmp;
    }

    private static StaticLayout build(CharSequence text, TextPaint tp, int width, int maxLines) {
        StaticLayout.Builder b = StaticLayout.Builder
                .obtain(text, 0, text.length(), tp, width)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setLineSpacing(0f, 1.18f)
                .setIncludePad(false);
        if (maxLines != Integer.MAX_VALUE) {
            b.setMaxLines(maxLines).setEllipsize(TextUtils.TruncateAt.END);
        }
        return b.build();
    }
}
