package com.portablediag.kjvbible;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/** Lists saved bookmarks; tap to navigate, delete individually or clear all. */
public class BookmarksActivity extends AppCompatActivity {

    private Bible bible;
    private Bookmarks bookmarks;
    private TextView emptyView;
    private RecyclerView list;
    private Adapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bookmarks);
        bible = Bible.get(this);
        bookmarks = new Bookmarks(this);

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.title_bookmarks);
        toolbar.setNavigationOnClickListener(v -> finish());

        emptyView = findViewById(R.id.emptyView);
        list = findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new Adapter(bookmarks.all());
        list.setAdapter(adapter);
        updateEmpty();
    }

    private void updateEmpty() {
        boolean empty = adapter.getItemCount() == 0;
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        list.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_bookmarks, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_clear_all) {
            if (bookmarks.size() == 0) return true;
            new AlertDialog.Builder(this)
                    .setMessage(R.string.clear_bookmarks_q)
                    .setPositiveButton(R.string.clear_all, (d, w) -> {
                        bookmarks.clear();
                        adapter.setItems(bookmarks.all());
                        updateEmpty();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void open(Ref r) {
        Intent data = new Intent();
        data.putExtra(MainActivity.EXTRA_BOOK, r.book);
        data.putExtra(MainActivity.EXTRA_CHAPTER, r.chapter);
        data.putExtra(MainActivity.EXTRA_VERSE, r.verse);
        setResult(RESULT_OK, data);
        finish();
    }

    private final class Adapter extends RecyclerView.Adapter<Adapter.VH> {
        private List<Bookmarks.Entry> items;
        Adapter(List<Bookmarks.Entry> items) { this.items = items; }

        void setItems(List<Bookmarks.Entry> items) {
            this.items = items;
            notifyDataSetChanged();
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_bookmark, parent, false);
            return new VH(v);
        }

        @Override public void onBindViewHolder(@NonNull VH h, int position) {
            Bookmarks.Entry e = items.get(position);
            h.ref.setText(e.ref.label(bible));
            String snippet = e.snippet != null && !e.snippet.isEmpty()
                    ? e.snippet
                    : Bible.plain(bible.verse(e.ref.book, e.ref.chapter, e.ref.verse));
            h.text.setText(snippet);
            h.itemView.setOnClickListener(v -> open(e.ref));
            h.delete.setOnClickListener(v -> {
                bookmarks.remove(e.ref);
                setItems(bookmarks.all());
                updateEmpty();
            });
        }

        @Override public int getItemCount() { return items.size(); }

        class VH extends RecyclerView.ViewHolder {
            final TextView ref, text;
            final ImageButton delete;
            VH(View v) {
                super(v);
                ref = v.findViewById(R.id.bmRef);
                text = v.findViewById(R.id.bmText);
                delete = v.findViewById(R.id.bmDelete);
            }
        }
    }
}
