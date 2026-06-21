package com.portablediag.kjvbible;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.List;

/** Passage picker: choose a book (All / OT / NT tabs), then a chapter, then a verse. */
public class PickerActivity extends AppCompatActivity {

    private static final int LAST_OT_BOOK = 39;   // Malachi; NT starts at Matthew (40)

    private enum Mode { BOOKS, CHAPTERS, VERSES }

    private Bible bible;
    private Toolbar toolbar;
    private TabLayout tabs;
    private RecyclerView list;

    private Mode mode = Mode.BOOKS;
    private int selectedTab = 0;     // 0=All, 1=OT, 2=NT
    private int selectedBook = -1;
    private int selectedChapter = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_picker);
        bible = Bible.get(this);

        toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        tabs = findViewById(R.id.tabs);
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                selectedTab = tab.getPosition();
                if (mode == Mode.BOOKS) showBooks();
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        list = findViewById(R.id.list);
        showBooks();
    }

    @Override
    public void onBackPressed() {
        switch (mode) {
            case VERSES:   showChapters(selectedBook); break;
            case CHAPTERS: showBooks(); break;
            default:       super.onBackPressed();
        }
    }

    // ---- Books ----

    private void showBooks() {
        mode = Mode.BOOKS;
        selectedBook = -1;
        tabs.setVisibility(View.VISIBLE);
        toolbar.setTitle(R.string.title_select);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(new BookAdapter(buildBookItems()));
    }

    /** Header strings interleaved with BookMeta entries, per the active tab. */
    private List<Object> buildBookItems() {
        List<Object> items = new ArrayList<>();
        List<Bible.BookMeta> books = bible.getBooks();
        if (selectedTab == 1) {                 // OT
            for (Bible.BookMeta m : books) if (m.index <= LAST_OT_BOOK) items.add(m);
        } else if (selectedTab == 2) {          // NT
            for (Bible.BookMeta m : books) if (m.index > LAST_OT_BOOK) items.add(m);
        } else {                                // All, with section dividers
            items.add(getString(R.string.section_ot));
            for (Bible.BookMeta m : books) {
                if (m.index == LAST_OT_BOOK + 1) items.add(getString(R.string.section_nt));
                items.add(m);
            }
        }
        return items;
    }

    // ---- Chapters ----

    private void showChapters(int book) {
        mode = Mode.CHAPTERS;
        selectedBook = book;
        tabs.setVisibility(View.GONE);
        Bible.BookMeta meta = bible.book(book);
        toolbar.setTitle(meta.name);
        list.setLayoutManager(new GridLayoutManager(this, 5));
        list.setAdapter(new NumberAdapter(meta.chapterCount, n -> showVerses(book, n)));
    }

    // ---- Verses ----

    private void showVerses(int book, int chapter) {
        mode = Mode.VERSES;
        selectedBook = book;
        selectedChapter = chapter;
        tabs.setVisibility(View.GONE);
        Bible.BookMeta meta = bible.book(book);
        toolbar.setTitle(getString(R.string.title_select_verse, meta.name, chapter));
        list.setLayoutManager(new GridLayoutManager(this, 5));
        int verseCount = bible.verseCount(book, chapter);
        list.setAdapter(new NumberAdapter(verseCount, n -> choose(book, chapter, n)));
    }

    private void choose(int book, int chapter, int verse) {
        Intent data = new Intent();
        data.putExtra(MainActivity.EXTRA_BOOK, book);
        data.putExtra(MainActivity.EXTRA_CHAPTER, chapter);
        data.putExtra(MainActivity.EXTRA_VERSE, verse);
        setResult(RESULT_OK, data);
        finish();
    }

    // ---- Adapters ----

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_BOOK = 1;

    private class BookAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        final List<Object> items;
        BookAdapter(List<Object> items) { this.items = items; }

        @Override public int getItemViewType(int position) {
            return (items.get(position) instanceof Bible.BookMeta) ? TYPE_BOOK : TYPE_HEADER;
        }

        @NonNull @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inf = LayoutInflater.from(parent.getContext());
            if (viewType == TYPE_HEADER) {
                return new HeaderVH((TextView) inf.inflate(R.layout.item_section_header, parent, false));
            }
            return new BookVH((TextView) inf.inflate(R.layout.item_book, parent, false));
        }

        @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int position) {
            Object item = items.get(position);
            if (h instanceof HeaderVH) {
                ((HeaderVH) h).title.setText((String) item);
            } else {
                Bible.BookMeta m = (Bible.BookMeta) item;
                ((BookVH) h).name.setText(m.name);
                h.itemView.setOnClickListener(v -> showChapters(m.index));
            }
        }

        @Override public int getItemCount() { return items.size(); }
    }

    static class HeaderVH extends RecyclerView.ViewHolder {
        final TextView title;
        HeaderVH(TextView v) { super(v); title = v; }
    }

    static class BookVH extends RecyclerView.ViewHolder {
        final TextView name;
        BookVH(TextView v) { super(v); name = v; }
    }

    private interface OnNumber { void pick(int n); }

    private static class NumberAdapter extends RecyclerView.Adapter<NumberAdapter.VH> {
        final int count;
        final OnNumber onNumber;
        NumberAdapter(int count, OnNumber onNumber) {
            this.count = count;
            this.onNumber = onNumber;
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_chapter, parent, false);
            return new VH((TextView) v);
        }

        @Override public void onBindViewHolder(@NonNull VH h, int position) {
            int n = position + 1;
            h.num.setText(String.valueOf(n));
            h.num.setOnClickListener(v -> onNumber.pick(n));
        }

        @Override public int getItemCount() { return count; }

        static class VH extends RecyclerView.ViewHolder {
            final TextView num;
            VH(TextView v) { super(v); num = v; }
        }
    }
}
