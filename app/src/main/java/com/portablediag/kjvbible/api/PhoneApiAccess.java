package com.portablediag.kjvbible.api;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.os.Build;
import android.util.Log;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * The key PhoneAPI holds, if any. It is an ordinary per-caller key named "PhoneAPI" in
 * {@link ApiKeys}, with the tools the owner switched on, so it shows in Local API › Keys like any
 * other and can be changed or revoked there. Beside it: the proof key (§2) and the two extras
 * PhoneAPI sent. The key and the proof key reach PhoneAPI only as an activity result to its
 * pinned release build, after the owner says yes on KJV Bible's own screen — never a file, a
 * broadcast or the clipboard.
 */
public final class PhoneApiAccess {

    private static final String TAG = "KjvApi";

    static final String KEY_NAME = "PhoneAPI";

    private static final String FILE = "phoneapi";
    private static final String K_KEY_ID = "key_id";
    private static final String K_PROOF = "proof";
    private static final String K_EVENT_PORT = "phoneapi_port";
    private static final String K_EVENT_KEY = "phoneapi_event_key";

    private PhoneApiAccess() {}

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    /** PhoneAPI's key row, or null until a handshake, after a revoke, or once the owner revoked it in Local API. */
    public static ApiKeys.Key key(Context c) {
        long id = sp(c).getLong(K_KEY_ID, 0);
        return id == 0 ? null : ApiKeys.get(c).byId(id);
    }

    /** The proof key, only while PhoneAPI's key still exists. */
    static byte[] proofKey(Context c) {
        if (key(c) == null) return null;
        byte[] p = PhoneApi.fromB64url(sp(c).getString(K_PROOF, ""));
        return p == null || p.length != 32 ? null : p;
    }

    /** §2: every answer to a request carrying a challenge carries the proof, while there is a proof key. */
    static Http.Response seal(Context c, Http.Request req, Http.Response res) {
        String challenge = PhoneApi.usableChallenge(req.header(PhoneApi.HEADER_CHALLENGE));
        if (challenge == null) return res;
        byte[] proof = proofKey(c);
        if (proof == null) return res;
        return res.withHeader(PhoneApi.HEADER_PROOF, PhoneApi.proof(proof, challenge));
    }

    /** What a successful handshake hands back. */
    public static final class Granted {
        public final String apiKey;
        public final byte[] proofKey;

        Granted(String apiKey, byte[] proofKey) {
            this.apiKey = apiKey;
            this.proofKey = proofKey;
        }
    }

    /**
     * The owner said yes: a new key with {@code tools} on (a second handshake replaces the
     * first), a fresh 32-byte proof key, PhoneAPI's event port and key kept as the contract asks,
     * and the local API switched on.
     */
    public static Granted create(Context c, Set<String> tools, int eventPort, String eventKey) {
        ApiKeys keys = ApiKeys.get(c);
        ApiKeys.Key old = key(c);
        if (old != null) keys.revoke(old.id);
        ApiKeys.Minted m = keys.create(KEY_NAME, tools);
        byte[] proof = new byte[32];
        new SecureRandom().nextBytes(proof);
        sp(c).edit()
                .putLong(K_KEY_ID, m.key.id)
                .putString(K_PROOF, PhoneApi.b64url(proof))
                .putInt(K_EVENT_PORT, eventPort)
                .putString(K_EVENT_KEY, eventKey == null ? "" : eventKey)
                .commit();
        if (!LocalApi.enabled(c)) LocalApi.setEnabled(c, true);
        Log.i(TAG, "phoneapi: key granted (" + tools.size() + " tools)");
        return new Granted(m.raw, proof);
    }

    /** PhoneAPI's key and proof key stop working at once; every other key is untouched. */
    public static void revoke(Context c) {
        ApiKeys.Key k = key(c);
        if (k != null) ApiKeys.get(c).revoke(k.id);
        sp(c).edit().clear().apply();
        Log.i(TAG, "phoneapi: key revoked");
    }

    /**
     * Android runs no code on uninstall, so whoever next owns the package name would inherit the
     * key. Drop it when PhoneAPI is gone or no longer signed by the pin.
     */
    public static void revokeIfGone(Context c) {
        if (sp(c).contains(K_KEY_ID) && !PhoneApi.pinMatches(certs(c, PhoneApi.PKG))) revoke(c);
    }

    /** Every SHA-256 {@code pkg} is signed with (hex); empty when it is not installed. */
    @SuppressWarnings("deprecation")
    public static List<String> certs(Context c, String pkg) {
        try {
            PackageManager pm = c.getPackageManager();
            Signature[] sigs;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                SigningInfo info = pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo;
                if (info == null) return Collections.emptyList();
                sigs = info.hasMultipleSigners() ? info.getApkContentsSigners() : info.getSigningCertificateHistory();
            } else {
                sigs = pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES).signatures;
            }
            List<String> out = new ArrayList<>();
            if (sigs == null) return out;
            for (Signature s : sigs) {
                StringBuilder hex = new StringBuilder();
                for (byte b : MessageDigest.getInstance("SHA-256").digest(s.toByteArray())) {
                    hex.append(String.format("%02x", b));
                }
                out.add(hex.toString());
            }
            return out;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
