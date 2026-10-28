package com.portablediag.kjvbible.api;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.portablediag.kjvbible.R;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * PhoneAPI asks for a local API key ({@code com.phoneapi.action.GRANT_KEY}, provider contract
 * §3). Only PhoneAPI's release build, started for a result, gets past the first check; then the
 * owner switches on the tools the key may use, the same switches a key has in Local API. The key
 * and the proof key leave only as this activity's result.
 */
public class PhoneApiGrantActivity extends AppCompatActivity {

    private static final String TAG = "KjvApi";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setResult(RESULT_CANCELED);
        String caller = getCallingPackage();
        if (caller == null || !PhoneApi.callerTrusted(caller, PhoneApiAccess.certs(this, caller))) {
            Log.w(TAG, "phoneapi: key request refused ("
                    + (caller == null ? "not started for a result" : caller + " is not PhoneAPI's release build") + ")");
            finish();
            return;
        }
        int eventPort = getIntent().getIntExtra(PhoneApi.EXTRA_EVENT_PORT, 0);
        String eventKey = getIntent().getStringExtra(PhoneApi.EXTRA_EVENT_KEY);

        setContentView(R.layout.activity_phoneapi_grant);

        StringBuilder body = new StringBuilder(getString(R.string.phoneapi_body));
        if (!LocalApi.enabled(this)) body.append(' ').append(getString(R.string.phoneapi_turns_on));
        if (PhoneApiAccess.key(this) != null) body.append(' ').append(getString(R.string.phoneapi_replaces));
        ((TextView) findViewById(R.id.body)).setText(body);

        MaterialButton allow = findViewById(R.id.allow);
        LinearLayout list = findViewById(R.id.tools);
        List<MaterialSwitch> switches = new ArrayList<>();
        // Everything starts off, and a key that may do nothing isn't worth minting.
        Runnable sync = () -> {
            boolean any = false;
            for (MaterialSwitch s : switches) any |= s.isChecked();
            allow.setEnabled(any);
        };
        for (Tools.Tool t : Tools.ALL) {
            MaterialSwitch s = new MaterialSwitch(this);
            s.setText(t.label + "  (" + t.name + ")");
            s.setMinHeight((int) (48 * getResources().getDisplayMetrics().density));
            s.setChecked(false);
            s.setOnCheckedChangeListener((b, on) -> sync.run());
            switches.add(s);
            list.addView(s, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        }
        sync.run();

        findViewById(R.id.deny).setOnClickListener(v -> {
            Log.i(TAG, "phoneapi: key request declined");
            finish();
        });
        allow.setOnClickListener(v -> {
            Set<String> tools = new LinkedHashSet<>();
            for (int i = 0; i < switches.size(); i++) {
                if (switches.get(i).isChecked()) tools.add(Tools.ALL.get(i).name);
            }
            PhoneApiAccess.Granted g = PhoneApiAccess.create(this, tools, eventPort, eventKey);
            setResult(RESULT_OK, new Intent()
                    .putExtra("api_key", g.apiKey)
                    .putExtra("proof_key", PhoneApi.b64url(g.proofKey))
                    .putExtra("port", LocalApi.port(this)));
            finish();
        });
    }
}
