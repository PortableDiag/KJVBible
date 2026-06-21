package com.portablediag.kjvbible;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.LinkedHashSet;
import java.util.Set;

/** Renders the verses of one chapter, with tap-to-select and red-letter styling. */
public class VerseAdapter extends RecyclerView.Adapter<VerseAdapter.VH> {

    public interface Listener {
        void onSelectionChanged(int count);
    }

    private final Bible bible;
    private final Bookmarks bookmarks;
    private final Listener listener;

    private int book = 1;
    private int chapter = 1;
    private String[] verses = new String[0];

    private final Set<Integer> selected = new LinkedHashSet<>(); // verse numbers (1-based)
    private int highlightVerse = -1; // transient highlight after navigation

    private float textSizeSp = Prefs.BASE_TEXT_SP;
    private int redColor;
    private int verseNumColor;

    public VerseAdapter(Bible bible, Bookmarks bookmarks, Listener listener) {
        this.bible = bible;
        this.bookmarks = bookmarks;
        this.listener = listener;
        setHasStableIds(true);
    }

    public void setColors(int redColor, int verseNumColor) {
        this.redColor = redColor;
        this.verseNumColor = verseNumColor;
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
        h.text.setText(VerseFormatter.verseLine(verseNum, verses[position], redColor, numColor));

        boolean isSelected = selected.contains(verseNum);
        h.text.setActivated(isSelected);
        h.text.setSelected(verseNum == highlightVerse && !isSelected);

        h.text.setOnClickListener(view -> toggle(verseNum));
    }

    private void toggle(int verseNum) {
        if (selected.contains(verseNum)) selected.remove(verseNum);
        else selected.add(verseNum);
        highlightVerse = -1;
        notifyItemChanged(verseNum - 1);
        listener.onSelectionChanged(selected.size());
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
