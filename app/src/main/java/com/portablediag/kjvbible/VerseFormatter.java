package com.portablediag.kjvbible;

import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.ImageSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.SuperscriptSpan;
import android.view.View;

import androidx.annotation.NonNull;

import java.util.List;

/** Builds styled verse text: superscript verse numbers, red-letter words, study highlights. */
public final class VerseFormatter {

    private VerseFormatter() {}

    public interface OnNoteClick {
        void onNote(String noteId);
    }

    /**
     * Render a single verse. {@code studySpans} (may be null/empty) add a subtle background
     * highlight and a tap target over annotated words, in plain-body coordinates.
     */
    public static CharSequence verseLine(int verseNumber, String raw,
                                         int redColor, int verseNumColor,
                                         Drawable bookmarkIcon,
                                         List<StudyNotes.Span> studySpans,
                                         int studyHighlightColor,
                                         OnNoteClick onNote) {
        SpannableStringBuilder sb = new SpannableStringBuilder();

        if (bookmarkIcon != null) {
            sb.append("  ");
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

        int bodyStart = sb.length();
        appendStyledBody(sb, raw, redColor);

        if (studySpans != null && !studySpans.isEmpty()) {
            int bodyLen = sb.length() - bodyStart;
            for (StudyNotes.Span s : studySpans) {
                int a = bodyStart + s.start;
                int b = bodyStart + s.end;
                if (s.start < 0 || s.end > bodyLen || a >= b) continue;
                sb.setSpan(new BackgroundColorSpan(studyHighlightColor), a, b,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                sb.setSpan(new NoteClickSpan(s.noteId, onNote), a, b,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
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

    /** Clickable span that opens a study note; keeps the word's own color (no underline). */
    private static final class NoteClickSpan extends ClickableSpan {
        private final String noteId;
        private final OnNoteClick onNote;

        NoteClickSpan(String noteId, OnNoteClick onNote) {
            this.noteId = noteId;
            this.onNote = onNote;
        }

        @Override
        public void onClick(@NonNull View widget) {
            if (onNote != null) onNote.onNote(noteId);
        }

        @Override
        public void updateDrawState(@NonNull android.text.TextPaint ds) {
            // Intentionally do not call super: no underline, no link color.
            // The subtle background highlight is the only visual cue.
        }
    }
}
