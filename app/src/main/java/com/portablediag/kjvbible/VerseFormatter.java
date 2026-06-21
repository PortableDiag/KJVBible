package com.portablediag.kjvbible;

import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.ImageSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.SuperscriptSpan;

/** Builds styled verse text: superscript verse numbers and red-letter words of Christ. */
public final class VerseFormatter {

    private VerseFormatter() {}

    /**
     * Render a single verse as "ⁿ verse text" with the verse number small/superscript
     * and the words of Christ in {@code redColor}. If {@code bookmarkIcon} is non-null,
     * a small filled bookmark glyph is shown before the verse number.
     */
    public static CharSequence verseLine(int verseNumber, String raw,
                                         int redColor, int verseNumColor,
                                         Drawable bookmarkIcon) {
        SpannableStringBuilder sb = new SpannableStringBuilder();

        if (bookmarkIcon != null) {
            sb.append("  "); // glyph slot + trailing gap
            sb.setSpan(new ImageSpan(bookmarkIcon, ImageSpan.ALIGN_BASELINE),
                    0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        int numStart = sb.length();
        String num = String.valueOf(verseNumber);
        sb.append(num).append(' ');
        int numEnd = numStart + num.length();
        sb.setSpan(new SuperscriptSpan(), numStart, numEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new RelativeSizeSpan(0.70f), numStart, numEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), numStart, numEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new ForegroundColorSpan(verseNumColor), numStart, numEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        appendStyledBody(sb, raw, redColor);
        return sb;
    }

    /** Append the verse body, applying red-letter spans where marked. */
    public static void appendStyledBody(SpannableStringBuilder sb, String raw, int redColor) {
        int redStart = -1;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == Bible.RED_START) {
                redStart = sb.length();
            } else if (c == Bible.RED_END) {
                if (redStart >= 0 && sb.length() > redStart) {
                    sb.setSpan(new ForegroundColorSpan(redColor), redStart, sb.length(),
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                redStart = -1;
            } else {
                sb.append(c);
            }
        }
        if (redStart >= 0 && sb.length() > redStart) {
            sb.setSpan(new ForegroundColorSpan(redColor), redStart, sb.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }
}
