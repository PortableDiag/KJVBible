package com.portablediag.kjvbible;

import android.graphics.drawable.Drawable;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Renders the verses of one chapter, with tap-to-select, red-letter styling, study notes. */
public class VerseAdapter extends RecyclerView.Adapter<VerseAdapter.VH> {

    public interface Listener {
        void onSelectionChanged(int count);
        void onNoteClicked(String noteId);
    }

    private final Bible bible;
    private final Bookmarks bookmarks;
    private final StudyNotes studyNotes;
    private final Listener listener;

    private int book = 1;
    private int chapter = 1;
    private String[] verses = new String[0];

    private final Set<Integer> selected = new LinkedHashSet<>(); // verse numbers (1-based)
    private int highlightVerse = -1; // transient highlight after navigation

    private float textSizeSp = Prefs.BASE_TEXT_SP;
    private int redColor;
    private int verseNumColor;
    private int studyHighlightColor;
    private boolean studyMode = false;

    private final VerseFormatter.OnNoteClick onNote = new VerseFormatter.OnNoteClick() {
        @Override public void onNote(String noteId) {
            listener.onNoteClicked(noteId);
        }
    };

    public VerseAdapter(Bible bible, Bookmarks bookmarks, StudyNotes studyNotes, Listener listener) {
        this.bible = bible;
        this.bookmarks = bookmarks;
        this.studyNotes = studyNotes;
        this.listener = listener;
        setHasStableIds(true);
    }

    public void setColors(int redColor, int verseNumColor, int studyHighlightColor) {
        this.redColor = redColor;
        this.verseNumColor = verseNumColor;
        this.studyHighlightColor = studyHighlightColor;
    }

    public void setStudyMode(boolean on) {
        if (studyMode == on) return;
        studyMode = on;
        selected.clear();         // study mode and selection are mutually exclusive
        notifyDataSetChanged();
    }

    public boolean isStudyMode() {
        return studyMode;
    }

    public void setTextSizeSp(float sp) {
        this.textSizeSp = sp;
        notifyDataSetChanged();
    }

    public void setChapter(int book, int chapter, String[] verses) {
        this.book = book;
        this.chapter = chapter;
        this.verses = verses;
        selected.clear();
        highlightVerse = -1;
        notifyDataSetChanged();
    }

    public void setHighlight(int verse) {
        this.highlightVerse = verse;
        notifyDataSetChanged();
    }

    public void clearSelection() {
        if (selected.isEmpty()) return;
        selected.clear();
        notifyDataSetChanged();
        listener.onSelectionChanged(0);
    }

    public boolean hasSelection() {
        return !selected.isEmpty();
    }

    public java.util.List<Ref> selectedRefs() {
        java.util.List<Integer> nums = new java.util.ArrayList<>(selected);
        java.util.Collections.sort(nums);
        java.util.List<Ref> refs = new java.util.ArrayList<>(nums.size());
        for (int v : nums) refs.add(new Ref(book, chapter, v));
        return refs;
    }

    public void refreshBookmarks() {
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_verse, parent, false);
        return new VH((TextView) v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        int verseNum = position + 1;
        boolean bookmarked = bookmarks.contains(new Ref(book, chapter, verseNum));
        int numColor = bookmarked ? redColor : verseNumColor;

        h.text.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, textSizeSp);

        Drawable bmIcon = null;
        if (bookmarked) {
            Drawable d = ContextCompat.getDrawable(h.text.getContext(), R.drawable.ic_bookmark);
            if (d != null) {
                d = d.mutate();
                d.setTint(redColor);
                int sz = Math.round(h.text.getTextSize() * 0.92f);
                d.setBounds(0, 0, Math.round(sz * 0.78f), sz);
                bmIcon = d;
            }
        }
        List<StudyNotes.Span> studySpans = studyMode
                ? studyNotes.find(book, chapter, verseNum, Bible.plain(verses[position]))
                : null;

        h.text.setText(VerseFormatter.verseLine(verseNum, verses[position],
                redColor, numColor, bmIcon, studySpans, studyHighlightColor, onNote));

        if (studyMode) {
            // Tap highlighted words for notes; verse selection is disabled in study mode.
            h.text.setMovementMethod(LinkMovementMethod.getInstance());
            h.text.setOnClickListener(null);
            h.text.setClickable(false);
            h.text.setActivated(false);
            h.text.setSelected(false);
        } else {
            // Selection is driven by a long-press / tap handler on the RecyclerView,
            // so the row itself stays non-clickable (no accidental select while scrolling).
            h.text.setMovementMethod(null);
            h.text.setOnClickListener(null);
            h.text.setClickable(false);
            boolean isSelected = selected.contains(verseNum);
            h.text.setActivated(isSelected);
            h.text.setSelected(verseNum == highlightVerse && !isSelected);
        }
    }

    /** Toggle selection for the verse at the given adapter position. */
    public void toggleAt(int position) {
        if (position < 0 || position >= verses.length) return;
        toggle(position + 1);
    }

    private void toggle(int verseNum) {
        if (selected.contains(verseNum)) selected.remove(verseNum);
        else selected.add(verseNum);
        boolean hadHighlight = highlightVerse != -1;
        int prevHighlight = highlightVerse;
        highlightVerse = -1;
        notifyItemChanged(verseNum - 1);
        if (hadHighlight && prevHighlight != verseNum) {
            notifyItemChanged(prevHighlight - 1); // clear the stale navigation highlight
        }
        listener.onSelectionChanged(selected.size());
    }

    /** True when there is a selection and every selected verse is already bookmarked. */
    public boolean allSelectedBookmarked() {
        if (selected.isEmpty()) return false;
        for (int v : selected) {
            if (!bookmarks.contains(new Ref(book, chapter, v))) return false;
        }
        return true;
    }

    @Override
    public long getItemId(int position) {
        return ((long) book << 24) | ((long) chapter << 12) | (position + 1);
    }

    @Override
    public int getItemCount() {
        return verses.length;
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView text;
        VH(TextView v) {
            super(v);
            this.text = v;
        }
    }
}
