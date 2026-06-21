package com.portablediag.kjvbible;

import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.SuperscriptSpan;

/** Builds styled verse text: superscript verse numbers and red-letter words of Christ. */
public final class VerseFormatter {

    private VerseFormatter() {}

    /**
     * Render a single verse as "ⁿ verse text" with the verse number small/superscript
     * and the words of Christ in {@code redColor}.
     */
    public static CharSequence verseLine(int verseNumber, String raw,
                                         int redColor, int verseNumColor) {
        SpannableStringBuilder sb = new SpannableStringBuilder();

        String num = String.valueOf(verseNumber);
        sb.append(num).append(' '); // thin space after the number
        sb.setSpan(new SuperscriptSpan(), 0, num.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new RelativeSizeSpan(0.70f), 0, num.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), 0, num.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new ForegroundColorSpan(verseNumColor), 0, num.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

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
