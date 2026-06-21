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

import java.util.List;

/** Two-step passage picker: choose a book, then a chapter. */
public class PickerActivity extends AppCompatActivity {

    private Bible bible;
    private Toolbar toolbar;
    private RecyclerView list;
    private int selectedBook = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_picker);
        bible = Bible.get(this);

        toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        list = findViewById(R.id.list);
        showBooks();
    }

    @Override
    public void onBackPressed() {
        if (selectedBook != -1) {
            showBooks();
        } else {
            super.onBackPressed();
        }
    }

    private void showBooks() {
        selectedBook = -1;
        toolbar.setTitle(R.string.title_select);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(new BookAdapter(bible.getBooks()));
    }

    private void showChapters(int book) {
        selectedBook = book;
        Bible.BookMeta meta = bible.book(book);
        toolbar.setTitle(meta.name);
        GridLayoutManager glm = new GridLayoutManager(this, 5);
        list.setLayoutManager(glm);
        list.setAdapter(new ChapterAdapter(meta.chapterCount));
    }

    private void choose(int book, int chapter) {
        Intent data = new Intent();
        data.putExtra(MainActivity.EXTRA_BOOK, book);
        data.putExtra(MainActivity.EXTRA_CHAPTER, chapter);
        setResult(RESULT_OK, data);
        finish();
    }

    private class BookAdapter extends RecyclerView.Adapter<BookAdapter.VH> {
        final List<Bible.BookMeta> books;
        BookAdapter(List<Bible.BookMeta> b) { this.books = b; }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_book, parent, false);
            return new VH((TextView) v);
        }

        @Override public void onBindViewHolder(@NonNull VH h, int position) {
            Bible.BookMeta m = books.get(position);
            h.name.setText(m.name);
            h.name.setOnClickListener(v -> showChapters(m.index));
        }

        @Override public int getItemCount() { return books.size(); }

        class VH extends RecyclerView.ViewHolder {
            final TextView name;
            VH(TextView v) { super(v); name = v; }
        }
    }

    private class ChapterAdapter extends RecyclerView.Adapter<ChapterAdapter.VH> {
        final int count;
        ChapterAdapter(int count) { this.count = count; }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_chapter, parent, false);
            return new VH((TextView) v);
        }

        @Override public void onBindViewHolder(@NonNull VH h, int position) {
            int chapter = position + 1;
            h.num.setText(String.valueOf(chapter));
            h.num.setOnClickListener(v -> choose(selectedBook, chapter));
        }

        @Override public int getItemCount() { return count; }

        class VH extends RecyclerView.ViewHolder {
            final TextView num;
            VH(TextView v) { super(v); num = v; }
        }
    }
}
