package com.portablediag.kjvbible;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Lists saved bookmarks (Bible order with OT/NT separators, or order saved); tap to navigate. */
public class BookmarksActivity extends AppCompatActivity {

    private static final int LAST_OT_BOOK = 39;

    private Bible bible;
    private Bookmarks bookmarks;
    private Prefs prefs;
    private int sortMode;

    private Toolbar toolbar;
    private TextView emptyView;
    private RecyclerView list;
    private Adapter adapter;

    private ActivityResultLauncher<String> exportLauncher;
    private ActivityResultLauncher<String[]> importLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bookmarks);
        bible = Bible.get(this);
        bookmarks = new Bookmarks(this);
        prefs = new Prefs(this);
        sortMode = prefs.bookmarkSort();

        exportLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("application/json"),
                uri -> { if (uri != null) doExport(uri); });
        importLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> { if (uri != null) doImport(uri); });

        toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.title_bookmarks);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.inflateMenu(R.menu.menu_bookmarks);
        toolbar.setOnMenuItemClickListener(this::onMenuItemClick);
        updateSortTitle();

        emptyView = findViewById(R.id.emptyView);
        list = findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new Adapter(buildItems());
        list.setAdapter(adapter);
        updateEmpty();
    }

    /** Header strings (in Bible mode) interleaved with bookmark entries. */
    private List<Object> buildItems() {
        List<Object> items = new ArrayList<>();
        if (sortMode == Prefs.SORT_SAVED) {
            items.addAll(bookmarks.allInSavedOrder());
        } else {
            boolean otHeader = false, ntHeader = false;
            for (Bookmarks.Entry e : bookmarks.all()) {
                if (e.ref.book <= LAST_OT_BOOK && !otHeader) {
                    items.add(getString(R.string.section_ot));
                    otHeader = true;
                } else if (e.ref.book > LAST_OT_BOOK && !ntHeader) {
                    items.add(getString(R.string.section_nt));
                    ntHeader = true;
                }
                items.add(e);
            }
        }
        return items;
    }

    private void rebuild() {
        adapter.setItems(buildItems());
        updateEmpty();
    }

    private void updateEmpty() {
        boolean empty = bookmarks.size() == 0;
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        list.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void updateSortTitle() {
        MenuItem sort = toolbar.getMenu().findItem(R.id.action_sort);
        if (sort != null) {
            sort.setTitle(sortMode == Prefs.SORT_BIBLE
                    ? R.string.sort_bible : R.string.sort_saved);
        }
    }

    private boolean onMenuItemClick(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_sort) {
            sortMode = (sortMode == Prefs.SORT_BIBLE) ? Prefs.SORT_SAVED : Prefs.SORT_BIBLE;
            prefs.setBookmarkSort(sortMode);
            updateSortTitle();
            rebuild();
            list.scrollToPosition(0);
            String name = getString(sortMode == Prefs.SORT_BIBLE
                    ? R.string.sort_bible : R.string.sort_saved);
            Toast.makeText(this, getString(R.string.sorted_by, name), Toast.LENGTH_SHORT).show();
            return true;
        } else if (id == R.id.action_export) {
            if (bookmarks.size() == 0) {
                Toast.makeText(this, R.string.nothing_to_export, Toast.LENGTH_SHORT).show();
            } else {
                exportLauncher.launch(getString(R.string.export_filename));
            }
            return true;
        } else if (id == R.id.action_import) {
            importLauncher.launch(new String[]{"application/json", "text/*", "application/octet-stream"});
            return true;
        } else if (id == R.id.action_clear_all) {
            if (bookmarks.size() == 0) return true;
            new AlertDialog.Builder(this)
                    .setMessage(R.string.clear_bookmarks_q)
                    .setPositiveButton(R.string.clear_all, (d, w) -> {
                        bookmarks.clear();
                        rebuild();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return true;
        }
        return false;
    }

    private void doExport(Uri uri) {
        try (OutputStream os = getContentResolver().openOutputStream(uri, "wt")) {
            if (os == null) throw new Exception("no stream");
            os.write(bookmarks.exportJson(bible).getBytes(StandardCharsets.UTF_8));
            os.flush();
            Toast.makeText(this, R.string.export_done, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, R.string.export_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void doImport(Uri uri) {
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            if (is == null) throw new Exception("no stream");
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(is, StandardCharsets.UTF_8))) {
                char[] buf = new char[8192];
                int n;
                while ((n = r.read(buf)) != -1) sb.append(buf, 0, n);
            }
            int added = bookmarks.importJson(sb.toString(), bible);
            rebuild();
            if (added > 0) {
                Toast.makeText(this,
                        getResources().getQuantityString(R.plurals.import_done, added, added),
                        Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, R.string.import_none, Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, R.string.import_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void open(Ref r) {
        Intent data = new Intent();
        data.putExtra(MainActivity.EXTRA_BOOK, r.book);
        data.putExtra(MainActivity.EXTRA_CHAPTER, r.chapter);
        data.putExtra(MainActivity.EXTRA_VERSE, r.verse);
        setResult(RESULT_OK, data);
        finish();
    }

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_BOOKMARK = 1;

    private final class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private List<Object> items;
        Adapter(List<Object> items) { this.items = items; }

        void setItems(List<Object> items) {
            this.items = items;
            notifyDataSetChanged();
        }

        @Override public int getItemViewType(int position) {
            return (items.get(position) instanceof Bookmarks.Entry) ? TYPE_BOOKMARK : TYPE_HEADER;
        }

        @NonNull @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inf = LayoutInflater.from(parent.getContext());
            if (viewType == TYPE_HEADER) {
                return new HeaderVH((TextView) inf.inflate(R.layout.item_section_header, parent, false));
            }
            return new VH(inf.inflate(R.layout.item_bookmark, parent, false));
        }

        @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int position) {
            Object item = items.get(position);
            if (h instanceof HeaderVH) {
                ((HeaderVH) h).title.setText((String) item);
                return;
            }
            VH vh = (VH) h;
            Bookmarks.Entry e = (Bookmarks.Entry) item;
            vh.ref.setText(e.ref.label(bible));
            String snippet = e.snippet != null && !e.snippet.isEmpty()
                    ? e.snippet
                    : Bible.plain(bible.verse(e.ref.book, e.ref.chapter, Math.max(1, e.ref.verse)));
            vh.text.setText(snippet);
            vh.itemView.setOnClickListener(v -> open(e.ref));
            vh.delete.setOnClickListener(v -> {
                bookmarks.remove(e.ref);
                rebuild();
            });
        }

        @Override public int getItemCount() { return items.size(); }
    }

    static class HeaderVH extends RecyclerView.ViewHolder {
        final TextView title;
        HeaderVH(TextView v) { super(v); title = v; }
    }

    static class VH extends RecyclerView.ViewHolder {
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
