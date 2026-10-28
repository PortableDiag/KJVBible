package com.portablediag.kjvbible.api;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.text.format.DateUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.portablediag.kjvbible.R;
import com.portablediag.kjvbible.ShareUtil;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;

/** Local API settings: the switch, LAN access, port, a key per caller with its tools, the log. */
public class ApiActivity extends AppCompatActivity implements LocalApi.Listener {

    private static final int LOG_SHOWN = 40;

    private ApiKeys keys;
    private MaterialSwitch apiSwitch, lanSwitch;
    private TextView status, portRow, noKeys, log;
    private LinearLayout keyList;
    private boolean binding;

    private ActivityResultLauncher<String> notifyPermission;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_api);
        keys = ApiKeys.get(this);

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.action_local_api);
        toolbar.setNavigationOnClickListener(v -> finish());

        apiSwitch = findViewById(R.id.apiSwitch);
        lanSwitch = findViewById(R.id.lanSwitch);
        status = findViewById(R.id.apiStatus);
        portRow = findViewById(R.id.portRow);
        noKeys = findViewById(R.id.noKeys);
        keyList = findViewById(R.id.keys);
        log = findViewById(R.id.log);

        // The running notification needs this on Android 13+; the API works either way.
        notifyPermission = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), granted -> { });

        apiSwitch.setOnCheckedChangeListener((b, on) -> {
            if (binding) return;
            if (on && Build.VERSION.SDK_INT >= 33 && checkSelfPermission(
                    Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
            LocalApi.setEnabled(this, on);
            render();
        });
        lanSwitch.setOnCheckedChangeListener((b, on) -> {
            if (binding) return;
            LocalApi.setLan(this, on);
            render();
        });
        portRow.setOnClickListener(v -> editPort());
        findViewById(R.id.newKey).setOnClickListener(v -> newKey());
        findViewById(R.id.clearLog).setOnClickListener(v -> {
            keys.clearLog();
            render();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        LocalApi.addListener(this);
        render();
    }

    @Override
    protected void onPause() {
        LocalApi.removeListener(this);
        super.onPause();
    }

    @Override
    public void onApiChanged() {
        runOnUiThread(this::render);
    }

    private void render() {
        PhoneApiAccess.revokeIfGone(this);
        binding = true;
        boolean on = LocalApi.enabled(this);
        apiSwitch.setChecked(on);
        lanSwitch.setChecked(LocalApi.lan(this));
        binding = false;

        int port = LocalApi.port(this);
        String failure = LocalApi.failure();
        if (failure != null && !on) status.setText(failure);
        else if (!on) status.setText(R.string.api_status_off);
        else if (!LocalApi.isRunning()) status.setText(R.string.api_status_starting);
        else status.setText(getString(LocalApi.lan(this) ? R.string.api_status_lan
                : R.string.api_status_local, port));
        portRow.setText(getString(R.string.api_port, port));

        keyList.removeAllViews();
        List<ApiKeys.Key> all = keys.all();
        noKeys.setVisibility(all.isEmpty() ? View.VISIBLE : View.GONE);
        for (ApiKeys.Key k : all) {
            View row = getLayoutInflater().inflate(R.layout.item_api_key, keyList, false);
            ((TextView) row.findViewById(R.id.keyName)).setText(k.name);
            String used = k.calls == 0 ? getString(R.string.api_never_used)
                    : getString(R.string.api_used, k.calls,
                    DateUtils.getRelativeTimeSpanString(k.lastUsed));
            ((TextView) row.findViewById(R.id.keyDetail)).setText(getString(R.string.api_key_detail,
                    k.kid, Tools.enabled(k).size(), Tools.ALL.size(), used));
            row.setOnClickListener(v -> editTools(k.id));
            keyList.addView(row);
        }

        List<ApiKeys.LogEntry> entries = keys.log();
        if (entries.isEmpty()) {
            log.setText(R.string.api_log_empty);
        } else {
            DateFormat df = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Math.min(LOG_SHOWN, entries.size()); i++) {
                ApiKeys.LogEntry e = entries.get(i);
                if (sb.length() > 0) sb.append('\n');
                sb.append(df.format(new Date(e.time))).append("  ").append(e.ok ? "✓ " : "✗ ")
                        .append(e.keyName).append("  ").append(e.tool);
                if (!e.detail.isEmpty()) sb.append("  ").append(e.detail);
            }
            log.setText(sb);
        }
    }

    private EditText field(String text, String hint, int inputType) {
        EditText input = new EditText(this);
        input.setInputType(inputType);
        input.setSingleLine(true);
        if (text != null) {
            input.setText(text);
            input.setSelection(text.length());
        }
        if (hint != null) input.setHint(hint);
        return input;
    }

    /** An EditText inside the dialog's usual side margins. */
    private View padded(View v) {
        FrameLayout frame = new FrameLayout(this);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        frame.setPadding(pad, pad / 2, pad, 0);
        frame.addView(v, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return frame;
    }

    private void editPort() {
        EditText input = field(String.valueOf(LocalApi.port(this)), null, InputType.TYPE_CLASS_NUMBER);
        new AlertDialog.Builder(this)
                .setTitle(R.string.api_port_title)
                .setView(padded(input))
                .setPositiveButton(R.string.done, (d, w) -> {
                    try {
                        LocalApi.setPort(this, Integer.parseInt(input.getText().toString().trim()));
                    } catch (NumberFormatException ignored) {
                    }
                    render();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void newKey() {
        EditText input = field(null, getString(R.string.api_key_name_hint), InputType.TYPE_CLASS_TEXT);
        new AlertDialog.Builder(this)
                .setTitle(R.string.api_key_name)
                .setView(padded(input))
                .setPositiveButton(R.string.done, (d, w) -> {
                    ApiKeys.Minted m = keys.create(input.getText().toString());
                    render();
                    showOnce(m);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showOnce(ApiKeys.Minted m) {
        TextView key = new TextView(this);
        key.setText(m.raw);
        key.setTextIsSelectable(true);
        key.setTypeface(android.graphics.Typeface.MONOSPACE);
        key.setTextSize(15);
        TextView note = new TextView(this);
        note.setText(R.string.api_key_once);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(key);
        box.addView(note);
        note.setPadding(0, (int) (12 * getResources().getDisplayMetrics().density), 0, 0);
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.api_key_created, m.key.name))
                .setView(padded(box))
                .setCancelable(false)
                .setPositiveButton(R.string.api_choose_tools, (d, w) -> editTools(m.key.id))
                .setNeutralButton(R.string.api_copy_key, null)
                .show()
                .getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                    ShareUtil.copy(this, m.raw);
                    Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show();
                });
    }

    private void editTools(long id) {
        ApiKeys.Key k = keys.byId(id);
        if (k == null) return;
        String[] labels = new String[Tools.ALL.size()];
        boolean[] checked = new boolean[labels.length];
        for (int i = 0; i < labels.length; i++) {
            Tools.Tool t = Tools.ALL.get(i);
            labels[i] = t.label + "  (" + t.name + ")";
            checked[i] = k.allows(t.name);
        }
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.api_tools_for, k.name))
                .setMultiChoiceItems(labels, checked, (d, which, on) -> {
                    keys.setTool(id, Tools.ALL.get(which).name, on);
                    render();
                })
                .setPositiveButton(R.string.done, null)
                .setNeutralButton(R.string.api_revoke, (d, w) -> confirmRevoke(id))
                .setNegativeButton(R.string.api_rename, (d, w) -> rename(id))
                .show();
    }

    private void rename(long id) {
        ApiKeys.Key k = keys.byId(id);
        if (k == null) return;
        EditText input = field(k.name, null, InputType.TYPE_CLASS_TEXT);
        new AlertDialog.Builder(this)
                .setTitle(R.string.api_rename)
                .setView(padded(input))
                .setPositiveButton(R.string.done, (d, w) -> {
                    keys.rename(id, input.getText().toString());
                    render();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void confirmRevoke(long id) {
        ApiKeys.Key k = keys.byId(id);
        if (k == null) return;
        new AlertDialog.Builder(this)
                .setMessage(getString(R.string.api_revoke_q, k.name))
                .setPositiveButton(R.string.api_revoke, (d, w) -> {
                    keys.revoke(id);
                    render();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
