package com.portablediag.kjvbible;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.view.ActionMode;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.ViewGroup;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.slider.Slider;

import java.util.List;

public class MainActivity extends AppCompatActivity implements VerseAdapter.Listener {

    public static final String EXTRA_BOOK = "book";
    public static final String EXTRA_CHAPTER = "chapter";
    public static final String EXTRA_VERSE = "verse";

    private Bible bible;
    private Bookmarks bookmarks;
    private StudyNotes studyNotes;
    private DivineSpeech divineSpeech;
    private Prefs prefs;

    private Toolbar toolbar;
    private RecyclerView list;
    private LinearLayoutManager layout;
    private VerseAdapter adapter;
    private FloatingActionButton fabPrev, fabNext;

    private int curBook = 1, curChapter = 1;
    private ActionMode actionMode;

    private ActivityResultLauncher<Intent> navLauncher;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bible = Bible.get(this);
        bookmarks = new Bookmarks(this);
        studyNotes = StudyNotes.get(this);
        divineSpeech = DivineSpeech.get(this);
        prefs = new Prefs(this);

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setTitle("");
        toolbar.setOnClickListener(v -> openPicker());

        list = findViewById(R.id.verseList);
        layout = new LinearLayoutManager(this);
        list.setLayoutManager(layout);
        adapter = new VerseAdapter(bible, bookmarks, studyNotes, divineSpeech, this);
        adapter.setColors(
                ThemeUtil.color(this, R.attr.redLetterColor),
                ThemeUtil.color(this, R.attr.verseNumColor),
                ThemeUtil.color(this, R.attr.studyHighlightColor),
                ThemeUtil.color(this, R.attr.goldLetterColor));
        adapter.setTextSizeSp(prefs.textSizeSp());
        adapter.setStudyMode(prefs.studyMode());
        list.setAdapter(adapter);

        fabPrev = findViewById(R.id.fabPrev);
        fabNext = findViewById(R.id.fabNext);
        fabPrev.setOnClickListener(v -> prevChapter());
        fabNext.setOnClickListener(v -> nextChapter());

        list.addOnItemTouchListener(new LongPressSelector(list));

        applyWindowInsets();
        enableImmersiveReading();

        navLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Intent d = result.getData();
                        int b = d.getIntExtra(EXTRA_BOOK, curBook);
                        int c = d.getIntExtra(EXTRA_CHAPTER, curChapter);
                        int v = d.getIntExtra(EXTRA_VERSE, 0);
                        showChapter(b, c);
                        if (v > 0) scrollToVerse(v);
                    }
                });

        int restorePos = -1, restoreOff = 0;
        if (savedInstanceState != null) {
            curBook = savedInstanceState.getInt(S_BOOK, prefs.lastBook());
            curChapter = savedInstanceState.getInt(S_CHAP, prefs.lastChapter());
            restorePos = savedInstanceState.getInt(S_POS, -1);
            restoreOff = savedInstanceState.getInt(S_OFF, 0);
        } else {
            curBook = prefs.lastBook();
            curChapter = prefs.lastChapter();
        }
        showChapter(curBook, curChapter);
        if (restorePos >= 0) {
            // Restore scroll after a recreate (e.g. theme change) so the reader stays in place.
            layout.scrollToPositionWithOffset(restorePos, restoreOff);
        }
    }

    private static final String S_BOOK = "s_book";
    private static final String S_CHAP = "s_chap";
    private static final String S_POS = "s_pos";
    private static final String S_OFF = "s_off";

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt(S_BOOK, curBook);
        out.putInt(S_CHAP, curChapter);
        int pos = layout.findFirstVisibleItemPosition();
        int off = 0;
        if (pos != RecyclerView.NO_POSITION) {
            View v = layout.findViewByPosition(pos);
            if (v != null) off = v.getTop() - list.getPaddingTop();
        }
        out.putInt(S_POS, pos);
        out.putInt(S_OFF, off);
    }

    @Override
    protected void onResume() {
        super.onResume();
        adapter.refreshBookmarks();
        invalidateOptionsMenu();
    }

    /**
     * Hide the bottom system navigation bar for distraction-free reading; it reappears
     * transiently when the user swipes from the edge, then auto-hides again.
     */
    private void enableImmersiveReading() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat c =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        c.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        c.hide(WindowInsetsCompat.Type.navigationBars());
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            WindowInsetsControllerCompat c =
                    WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
            c.hide(WindowInsetsCompat.Type.navigationBars());
        }
    }

    /** Keep the floating chapter buttons and list clear of the system bars (edge-to-edge). */
    private void applyWindowInsets() {
        final int baseMargin = dp(16);
        final int listBottomBase = dp(88); // clearance for the mini FABs
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            setBottomMargin(fabPrev, baseMargin + bars.bottom);
            setBottomMargin(fabNext, baseMargin + bars.bottom);
            list.setPadding(list.getPaddingLeft(), list.getPaddingTop(),
                    list.getPaddingRight(), listBottomBase + bars.bottom);
            return insets;
        });
    }

    private void setBottomMargin(View view, int margin) {
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
        if (lp.bottomMargin != margin) {
            lp.bottomMargin = margin;
            view.setLayoutParams(lp);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void showChapter(int book, int chapter) {
        if (book < 1) book = 1;
        if (book > bible.bookCount()) book = bible.bookCount();
        int cc = bible.chapterCount(book);
        if (chapter < 1) chapter = 1;
        if (chapter > cc) chapter = cc;

        curBook = book;
        curChapter = chapter;

        String[] verses = bible.chapter(book, chapter);
        adapter.setChapter(book, chapter, verses);

        Bible.BookMeta meta = bible.book(book);
        toolbar.setTitle(meta.name + " " + chapter + "  ▾");

        layout.scrollToPositionWithOffset(0, 0);
        prefs.setLastPosition(book, chapter);

        boolean isFirst = (book == 1 && chapter == 1);
        boolean isLast = (book == bible.bookCount() && chapter == cc);
        fabPrev.setEnabled(!isFirst);
        fabPrev.setAlpha(isFirst ? 0.3f : 1f);
        fabNext.setEnabled(!isLast);
        fabNext.setAlpha(isLast ? 0.3f : 1f);

        if (actionMode != null) actionMode.finish();
        invalidateOptionsMenu(); // reflect this chapter's bookmark state on the toolbar icon
    }

    private void scrollToVerse(int verse) {
        layout.scrollToPositionWithOffset(verse - 1, 0);
        adapter.setHighlight(verse);
        handler.postDelayed(() -> adapter.setHighlight(-1), 2200);
    }

    private void nextChapter() {
        int cc = bible.chapterCount(curBook);
        if (curChapter < cc) showChapter(curBook, curChapter + 1);
        else if (curBook < bible.bookCount()) showChapter(curBook + 1, 1);
    }

    private void prevChapter() {
        if (curChapter > 1) showChapter(curBook, curChapter - 1);
        else if (curBook > 1) showChapter(curBook - 1, bible.chapterCount(curBook - 1));
    }

    private void openPicker() {
        Intent i = new Intent(this, PickerActivity.class);
        i.putExtra(EXTRA_BOOK, curBook);
        navLauncher.launch(i);
    }

    // ---- Menu ----

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem bar = menu.findItem(R.id.action_bookmarks_bar);
        if (bar != null) {
            boolean marked = bookmarks.contains(new Ref(curBook, curChapter, 0));
            bar.setIcon(marked ? R.drawable.ic_bookmark : R.drawable.ic_bookmark_border);
            bar.setTitle(marked ? R.string.action_unbookmark_chapter
                    : R.string.action_bookmark_chapter);
        }
        MenuItem study = menu.findItem(R.id.action_study_mode);
        if (study != null) study.setChecked(adapter.isStudyMode());
        return super.onPrepareOptionsMenu(menu);
    }

    private void toggleChapterBookmark() {
        Ref chapterRef = new Ref(curBook, curChapter, 0);
        String snippet = Bible.plain(bible.verse(curBook, curChapter, 1)); // first verse, for context
        boolean nowMarked = bookmarks.toggle(chapterRef, System.currentTimeMillis(), snippet);
        invalidateOptionsMenu();
        Toast.makeText(this,
                nowMarked ? R.string.chapter_bookmarked : R.string.chapter_bookmark_removed,
                Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_search) {
            navLauncher.launch(new Intent(this, SearchActivity.class));
            return true;
        } else if (id == R.id.action_bookmarks_bar) {
            toggleChapterBookmark();
            return true;
        } else if (id == R.id.action_bookmarks) {
            navLauncher.launch(new Intent(this, BookmarksActivity.class));
            return true;
        } else if (id == R.id.action_font_size) {
            showFontDialog();
            return true;
        } else if (id == R.id.action_study_mode) {
            boolean on = !adapter.isStudyMode();
            prefs.setStudyMode(on);
            adapter.setStudyMode(on);
            if (on && actionMode != null) actionMode.finish();
            invalidateOptionsMenu();
            Toast.makeText(this, on ? R.string.study_on : R.string.study_off,
                    Toast.LENGTH_SHORT).show();
            return true;
        } else if (id == R.id.action_about) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.about_title)
                    .setMessage(R.string.about_body)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return true;
        } else if (id == R.id.action_theme) {
            showThemeDialog();
            return true;
        } else if (id == R.id.action_share_chapter) {
            ShareUtil.share(this, ShareUtil.buildChapter(bible, curBook, curChapter));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showFontDialog() {
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        android.widget.LinearLayout container = new android.widget.LinearLayout(this);
        container.setOrientation(android.widget.LinearLayout.VERTICAL);
        container.setPadding(pad, pad, pad, 0);

        TextView preview = new TextView(this);
        preview.setText("For God so loved the world…");
        preview.setTextColor(ThemeUtil.color(this, R.attr.verseTextColor));
        preview.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, prefs.textSizeSp());
        container.addView(preview);

        // Slider works in integer percent steps to avoid float step-size issues.
        final Slider slider = new Slider(this);
        slider.setValueFrom(Math.round(Prefs.MIN_FONT_SCALE * 100)); // 80
        slider.setValueTo(Math.round(Prefs.MAX_FONT_SCALE * 100));   // 200
        slider.setStepSize(10f);
        slider.setValue(Math.round(prefs.fontScale() * 100));
        int sp = (int) (16 * getResources().getDisplayMetrics().density);
        slider.setPadding(0, sp, 0, 0);
        container.addView(slider);

        slider.addOnChangeListener((s, value, fromUser) -> {
            prefs.setFontScale(value / 100f);
            preview.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, prefs.textSizeSp());
            adapter.setTextSizeSp(prefs.textSizeSp());
        });

        new AlertDialog.Builder(this)
                .setTitle(R.string.font_size)
                .setView(container)
                .setPositiveButton(R.string.done, null)
                .setNeutralButton(R.string.reset, (d, w) -> {
                    prefs.setFontScale(Prefs.DEFAULT_FONT_SCALE);
                    adapter.setTextSizeSp(prefs.textSizeSp());
                })
                .show();
    }

    private void showThemeDialog() {
        final int[] modes = {
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
                AppCompatDelegate.MODE_NIGHT_NO,
                AppCompatDelegate.MODE_NIGHT_YES
        };
        String[] labels = {
                getString(R.string.theme_system),
                getString(R.string.theme_light),
                getString(R.string.theme_dark)
        };
        int current = prefs.themeMode();
        int checked = 0;
        for (int i = 0; i < modes.length; i++) if (modes[i] == current) checked = i;

        new AlertDialog.Builder(this)
                .setTitle(R.string.choose_theme)
                .setSingleChoiceItems(labels, checked, (d, which) -> {
                    prefs.setThemeMode(modes[which]);
                    AppCompatDelegate.setDefaultNightMode(modes[which]);
                    d.dismiss();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ---- Selection / contextual action mode ----

    @Override
    public void onNoteClicked(String noteId) {
        StudyNotes.Note note = studyNotes.byId(noteId);
        if (note == null) return;

        View content = getLayoutInflater().inflate(R.layout.study_sheet, null);
        TextView category = content.findViewById(R.id.noteCategory);
        TextView title = content.findViewById(R.id.noteTitle);
        TextView body = content.findViewById(R.id.noteBody);
        TextView sourcesLabel = content.findViewById(R.id.noteSourcesLabel);
        TextView sources = content.findViewById(R.id.noteSources);

        category.setText(categoryLabel(note.category));
        title.setText(note.title);
        body.setText(note.body);
        if (note.sources.isEmpty()) {
            sourcesLabel.setVisibility(View.GONE);
            sources.setVisibility(View.GONE);
        } else {
            android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder();
            for (StudyNotes.Source s : note.sources) {
                if (sb.length() > 0) sb.append('\n');
                sb.append("• ");
                int start = sb.length();
                sb.append(s.label);
                if (s.url != null && !s.url.isEmpty()) {
                    final String url = s.url;
                    sb.setSpan(new android.text.style.ClickableSpan() {
                        @Override public void onClick(@NonNull View widget) {
                            try {
                                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                            } catch (Exception ignored) {}
                        }
                    }, start, sb.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
            sources.setText(sb);
            sources.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
        }

        com.google.android.material.bottomsheet.BottomSheetDialog dialog =
                new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        dialog.setContentView(content);
        dialog.show();
    }

    private String categoryLabel(String category) {
        if ("contested".equals(category)) return getString(R.string.study_contested);
        if ("archaic".equals(category)) return getString(R.string.study_archaic);
        if (category == null || category.isEmpty()) return "";
        return Character.toUpperCase(category.charAt(0)) + category.substring(1);
    }

    private void showShareChoice(List<Ref> refs) {
        String[] options = { getString(R.string.share_as_text), getString(R.string.share_as_image) };
        new AlertDialog.Builder(this)
                .setTitle(R.string.share_how)
                .setItems(options, (d, which) -> {
                    if (which == 0) {
                        ShareUtil.share(this, ShareUtil.buildPassage(bible, refs));
                    } else {
                        showImageShare(refs);
                    }
                })
                .show();
    }

    private void showImageShare(List<Ref> refs) {
        Bitmap bmp;
        try {
            bmp = VerseImage.render(this, bible, divineSpeech, refs);
        } catch (Throwable t) {
            Toast.makeText(this, R.string.image_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        View content = getLayoutInflater().inflate(R.layout.share_image_sheet, null);
        ImageView preview = content.findViewById(R.id.preview);
        preview.setImageBitmap(bmp);

        com.google.android.material.bottomsheet.BottomSheetDialog dialog =
                new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        dialog.setContentView(content);
        content.findViewById(R.id.shareImageBtn).setOnClickListener(v -> {
            dialog.dismiss();
            shareImage(bmp, ShareUtil.buildPassage(bible, refs));
        });
        dialog.show();
    }

    private void shareImage(Bitmap bmp, String caption) {
        try {
            File dir = new File(getCacheDir(), "shared");
            dir.mkdirs();
            File file = new File(dir, "verse.png");
            try (FileOutputStream out = new FileOutputStream(file)) {
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
            }
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("image/png");
            send.putExtra(Intent.EXTRA_STREAM, uri);
            send.putExtra(Intent.EXTRA_TEXT, caption);
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(send, getString(R.string.share_via)));
        } catch (Exception e) {
            Toast.makeText(this, R.string.image_failed, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onSelectionChanged(int count) {
        if (count == 0) {
            if (actionMode != null) actionMode.finish();
            return;
        }
        if (actionMode == null) {
            actionMode = startSupportActionMode(actionCallback);
        }
        if (actionMode != null) {
            actionMode.setTitle(getString(R.string.selected_count, count));
            actionMode.invalidate(); // refresh the bookmark icon/label for the new selection
        }
    }

    private final ActionMode.Callback actionCallback = new ActionMode.Callback() {
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            mode.getMenuInflater().inflate(R.menu.menu_selection, menu);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            MenuItem bm = menu.findItem(R.id.sel_bookmark);
            boolean allBookmarked = adapter.allSelectedBookmarked();
            bm.setIcon(allBookmarked ? R.drawable.ic_bookmark : R.drawable.ic_bookmark_border);
            bm.setTitle(allBookmarked ? R.string.action_unbookmark : R.string.action_bookmark_add);
            return true;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            List<Ref> refs = adapter.selectedRefs();
            if (refs.isEmpty()) return false;
            int id = item.getItemId();
            if (id == R.id.sel_share) {
                showShareChoice(refs);
                mode.finish();
                return true;
            } else if (id == R.id.sel_copy) {
                ShareUtil.copy(MainActivity.this, ShareUtil.buildPassage(bible, refs));
                Toast.makeText(MainActivity.this, R.string.copied, Toast.LENGTH_SHORT).show();
                mode.finish();
                return true;
            } else if (id == R.id.sel_bookmark) {
                boolean allBookmarked = adapter.allSelectedBookmarked();
                long now = System.currentTimeMillis();
                if (allBookmarked) {
                    for (Ref r : refs) bookmarks.remove(r);
                    Toast.makeText(MainActivity.this, R.string.bookmark_removed,
                            Toast.LENGTH_SHORT).show();
                } else {
                    for (Ref r : refs) {
                        String snippet = Bible.plain(bible.verse(r.book, r.chapter, r.verse));
                        bookmarks.add(r, now, snippet);
                    }
                    Toast.makeText(MainActivity.this, R.string.bookmark_added,
                            Toast.LENGTH_SHORT).show();
                }
                adapter.refreshBookmarks();
                mode.invalidate();   // flip the icon to match the new state, keep selection
                return true;
            }
            return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            actionMode = null;
            adapter.clearSelection();
        }
    };

    /**
     * Selection by deliberate press-and-hold (~1s), not by tap, so scrolling never selects.
     * A quick tap only toggles a verse when a selection is already in progress.
     * Inactive in study mode (word taps handle that). Never consumes touches, so the list
     * scrolls normally.
     */
    private final class LongPressSelector implements RecyclerView.OnItemTouchListener {
        private static final long HOLD_MS = 500L;

        private final RecyclerView rv;
        private final GestureDetector detector;
        private final Handler handler = new Handler(Looper.getMainLooper());
        private Runnable pending;
        private boolean consumedByHold;

        LongPressSelector(RecyclerView rv) {
            this.rv = rv;
            // Long-press disabled; we time the hold ourselves to require a full second.
            detector = new GestureDetector(rv.getContext(),
                    new GestureDetector.SimpleOnGestureListener() {
                        @Override
                        public boolean onSingleTapUp(MotionEvent e) {
                            if (consumedByHold) return false;
                            if (!adapter.hasSelection()) return false; // tap selects only mid-selection
                            int pos = positionUnder(e);
                            if (pos != RecyclerView.NO_POSITION) adapter.toggleAt(pos);
                            return false;
                        }

                        @Override
                        public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) {
                            cancelHold(); // a scroll is not a hold
                            return false;
                        }
                    });
            detector.setIsLongpressEnabled(false);
        }

        private int positionUnder(MotionEvent e) {
            View child = rv.findChildViewUnder(e.getX(), e.getY());
            return child == null ? RecyclerView.NO_POSITION : rv.getChildAdapterPosition(child);
        }

        private void cancelHold() {
            if (pending != null) {
                handler.removeCallbacks(pending);
                pending = null;
            }
        }

        @Override
        public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
            if (adapter.isStudyMode()) return false;
            detector.onTouchEvent(e);
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    consumedByHold = false;
                    cancelHold();
                    final int pos = positionUnder(e);
                    if (pos != RecyclerView.NO_POSITION) {
                        pending = () -> {
                            consumedByHold = true;
                            rv.performHapticFeedback(
                                    android.view.HapticFeedbackConstants.LONG_PRESS);
                            adapter.toggleAt(pos);
                        };
                        handler.postDelayed(pending, HOLD_MS);
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    cancelHold();
                    break;
                default:
                    break;
            }
            return false; // never consume; RecyclerView keeps scrolling
        }

        @Override
        public void onTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
        }

        @Override
        public void onRequestDisallowInterceptTouchEvent(boolean disallow) {
            if (disallow) cancelHold();
        }
    }
}
