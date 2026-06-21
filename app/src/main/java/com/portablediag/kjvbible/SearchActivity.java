package com.portablediag.kjvbible;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Full-text search across the whole Bible, on a background thread. */
public class SearchActivity extends AppCompatActivity {

    private static final int MAX_RESULTS = 800;

    private Bible bible;
    private TextView statusLine;
    private ResultsAdapter adapter;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile int searchToken = 0;

    private int redColor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);
        bible = Bible.get(this);
        redColor = ThemeUtil.color(this, R.attr.redLetterColor);

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        statusLine = findViewById(R.id.statusLine);

        RecyclerView results = findViewById(R.id.results);
        results.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ResultsAdapter();
        results.setAdapter(adapter);

        TextInputEditText input = findViewById(R.id.searchInput);
        input.requestFocus();
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                debounce(s.toString());
            }
        });
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                runSearch(v.getText().toString());
                return true;
            }
            return false;
        });
    }

    private void debounce(String query) {
        final int token = ++searchToken;
        main.postDelayed(() -> {
            if (token == searchToken) runSearch(query);
        }, 250);
    }

    private void runSearch(String rawQuery) {
        final String query = rawQuery.trim().toLowerCase(Locale.US);
        final int token = ++searchToken;
        if (query.length() < 2) {
            adapter.setItems(new ArrayList<>());
            statusLine.setText(R.string.search_prompt);
            return;
        }
        statusLine.setText(R.string.searching);
        executor.execute(() -> {
            List<Result> found = new ArrayList<>();
            boolean truncated = false;
            outer:
            for (int b = 1; b <= bible.bookCount(); b++) {
                int chapters = bible.chapterCount(b);
                for (int c = 1; c <= chapters; c++) {
                    if (token != searchToken) return; // superseded
                    String[] verses = bible.chapter(b, c);
                    for (int v = 0; v < verses.length; v++) {
                        String plain = Bible.plain(verses[v]);
                        if (plain.toLowerCase(Locale.US).contains(query)) {
                            found.add(new Result(new Ref(b, c, v + 1), plain));
                            if (found.size() >= MAX_RESULTS) { truncated = true; break outer; }
                        }
                    }
                }
            }
            final boolean trunc = truncated;
            main.post(() -> {
                if (token != searchToken) return;
                adapter.setQuery(query);
                adapter.setItems(found);
                if (found.isEmpty()) {
                    statusLine.setText(R.string.no_results);
                } else {
                    String n = getResources().getQuantityString(
                            R.plurals.result_count, found.size(), found.size());
                    statusLine.setText(trunc ? n + " (showing first " + MAX_RESULTS + ")" : n);
                }
            });
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        searchToken++;                      // invalidate any in-flight results
        main.removeCallbacksAndMessages(null);
        executor.shutdownNow();
    }

    private void open(Ref r) {
        Intent data = new Intent();
        data.putExtra(MainActivity.EXTRA_BOOK, r.book);
        data.putExtra(MainActivity.EXTRA_CHAPTER, r.chapter);
        data.putExtra(MainActivity.EXTRA_VERSE, r.verse);
        setResult(RESULT_OK, data);
        finish();
    }

    private static final class Result {
        final Ref ref;
        final String text;
        Result(Ref ref, String text) { this.ref = ref; this.text = text; }
    }

    private final class ResultsAdapter extends RecyclerView.Adapter<ResultsAdapter.VH> {
        private List<Result> items = new ArrayList<>();
        private String query = "";

        void setItems(List<Result> list) {
            this.items = list;
            notifyDataSetChanged();
        }
        void setQuery(String q) { this.query = q; }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_result, parent, false);
            return new VH(v);
        }

        @Override public void onBindViewHolder(@NonNull VH h, int position) {
            Result r = items.get(position);
            h.ref.setText(r.ref.label(bible));
            h.text.setText(highlight(r.text, query));
            h.itemView.setOnClickListener(v -> open(r.ref));
        }

        @Override public int getItemCount() { return items.size(); }

        SpannableString highlight(String text, String q) {
            SpannableString ss = new SpannableString(text);
            if (q.isEmpty()) return ss;
            String lower = text.toLowerCase(Locale.US);
            int idx = lower.indexOf(q), from = 0;
            while (idx >= 0) {
                ss.setSpan(new ForegroundColorSpan(redColor), idx, idx + q.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                ss.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), idx, idx + q.length(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                from = idx + q.length();
                idx = lower.indexOf(q, from);
            }
            return ss;
        }

        class VH extends RecyclerView.ViewHolder {
            final TextView ref, text;
            VH(View v) {
                super(v);
                ref = v.findViewById(R.id.resultRef);
                text = v.findViewById(R.id.resultText);
            }
        }
    }
}
