package com.portablediag.kjvbible.api;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * One key per caller (an agent, a script, PhoneAPI later), the way TextX, DialX and NotifX do it:
 *
 *  - The key is generated, shown once, and stored only as a SHA-256 hash.
 *  - Every tool is OFF for a new key. The owner switches on exactly what that caller may do;
 *    a switch takes effect on the next call, with no new key needed.
 *  - Every call is written to the audit log, allowed or refused.
 */
public final class ApiKeys {

    static final String PREFIX = "kjv_";
    /** Prefix plus six characters: enough to find the row without the secret. */
    private static final int KID_LEN = 10;
    private static final int LOG_MAX = 200;

    private static final String FILE = "local_api";
    private static final String K_KEYS = "keys";
    private static final String K_LOG = "log";

    public static final class Key {
        public final long id;
        public final String name;
        public final String kid;
        final String hash;
        public final Set<String> tools;
        public final long created;
        public final long lastUsed;
        public final int calls;

        Key(long id, String name, String kid, String hash, Set<String> tools,
            long created, long lastUsed, int calls) {
            this.id = id;
            this.name = name;
            this.kid = kid;
            this.hash = hash;
            this.tools = tools;
            this.created = created;
            this.lastUsed = lastUsed;
            this.calls = calls;
        }

        public boolean allows(String tool) {
            return tools.contains(tool);
        }
    }

    public static final class LogEntry {
        public final long time;
        public final String keyName;
        public final String tool;
        public final boolean ok;
        public final String detail;

        LogEntry(long time, String keyName, String tool, boolean ok, String detail) {
            this.time = time;
            this.keyName = keyName;
            this.tool = tool;
            this.ok = ok;
            this.detail = detail;
        }
    }

    /** A freshly minted key: the raw string exists only here, to be shown once. */
    public static final class Minted {
        public final Key key;
        public final String raw;

        Minted(Key key, String raw) {
            this.key = key;
            this.raw = raw;
        }
    }

    private static ApiKeys instance;

    private final SharedPreferences sp;
    private final List<Key> keys = new ArrayList<>();
    private final List<LogEntry> log = new ArrayList<>();

    private ApiKeys(Context ctx) {
        sp = ctx.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
        load();
    }

    public static synchronized ApiKeys get(Context ctx) {
        if (instance == null) instance = new ApiKeys(ctx);
        return instance;
    }

    // ---- keys ----

    public synchronized List<Key> all() {
        return new ArrayList<>(keys);
    }

    public synchronized Key byId(long id) {
        for (Key k : keys) if (k.id == id) return k;
        return null;
    }

    public synchronized Minted create(String name) {
        byte[] secret = new byte[32];
        new SecureRandom().nextBytes(secret);
        String raw = PREFIX + Base64.encodeToString(secret,
                Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
        long id = 1;
        for (Key k : keys) id = Math.max(id, k.id + 1);
        String label = (name == null || name.trim().isEmpty()) ? "API key" : name.trim();
        Key key = new Key(id, label, raw.substring(0, KID_LEN), sha256(raw),
                new LinkedHashSet<>(), System.currentTimeMillis(), 0, 0);
        keys.add(key);
        persist();
        return new Minted(key, raw);
    }

    public synchronized void revoke(long id) {
        for (int i = 0; i < keys.size(); i++) {
            if (keys.get(i).id == id) {
                keys.remove(i);
                break;
            }
        }
        persist();
    }

    public synchronized void setTool(long id, String tool, boolean on) {
        for (int i = 0; i < keys.size(); i++) {
            Key k = keys.get(i);
            if (k.id != id) continue;
            Set<String> tools = new LinkedHashSet<>(k.tools);
            if (on) tools.add(tool); else tools.remove(tool);
            keys.set(i, new Key(k.id, k.name, k.kid, k.hash, tools, k.created, k.lastUsed, k.calls));
        }
        persist();
    }

    public synchronized void rename(long id, String name) {
        for (int i = 0; i < keys.size(); i++) {
            Key k = keys.get(i);
            if (k.id != id || name == null || name.trim().isEmpty()) continue;
            keys.set(i, new Key(k.id, name.trim(), k.kid, k.hash, k.tools, k.created, k.lastUsed, k.calls));
        }
        persist();
    }

    /** The key row a raw key opens, compared in constant time; null if none. */
    public synchronized Key match(String raw) {
        if (raw == null || !raw.startsWith(PREFIX) || raw.length() <= KID_LEN) return null;
        String kid = raw.substring(0, KID_LEN);
        byte[] h = sha256(raw).getBytes(StandardCharsets.US_ASCII);
        for (Key k : keys) {
            if (k.kid.equals(kid) && MessageDigest.isEqual(k.hash.getBytes(StandardCharsets.US_ASCII), h)) {
                return k;
            }
        }
        return null;
    }

    // ---- audit log ----

    public synchronized void record(Key key, String tool, boolean ok, String detail) {
        long now = System.currentTimeMillis();
        for (int i = 0; i < keys.size(); i++) {
            Key k = keys.get(i);
            if (k.id == key.id) {
                keys.set(i, new Key(k.id, k.name, k.kid, k.hash, k.tools, k.created, now, k.calls + 1));
            }
        }
        log.add(new LogEntry(now, key.name, tool, ok, detail == null ? "" : detail));
        while (log.size() > LOG_MAX) log.remove(0);
        persist();
    }

    /** Newest first. */
    public synchronized List<LogEntry> log() {
        List<LogEntry> out = new ArrayList<>(log);
        Collections.reverse(out);
        return out;
    }

    public synchronized void clearLog() {
        log.clear();
        persist();
    }

    // ---- storage ----

    private void load() {
        try {
            JSONArray arr = new JSONArray(sp.getString(K_KEYS, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Set<String> tools = new LinkedHashSet<>();
                JSONArray t = o.optJSONArray("tools");
                if (t != null) for (int j = 0; j < t.length(); j++) tools.add(t.getString(j));
                keys.add(new Key(o.getLong("id"), o.getString("name"), o.getString("kid"),
                        o.getString("hash"), tools, o.optLong("created"), o.optLong("lastUsed"),
                        o.optInt("calls")));
            }
            JSONArray l = new JSONArray(sp.getString(K_LOG, "[]"));
            for (int i = 0; i < l.length(); i++) {
                JSONObject o = l.getJSONObject(i);
                log.add(new LogEntry(o.optLong("t"), o.optString("key"), o.optString("tool"),
                        o.optBoolean("ok"), o.optString("detail")));
            }
        } catch (Exception ignored) {
        }
    }

    private void persist() {
        JSONArray arr = new JSONArray();
        JSONArray l = new JSONArray();
        try {
            for (Key k : keys) {
                arr.put(new JSONObject()
                        .put("id", k.id).put("name", k.name).put("kid", k.kid).put("hash", k.hash)
                        .put("tools", new JSONArray(k.tools)).put("created", k.created)
                        .put("lastUsed", k.lastUsed).put("calls", k.calls));
            }
            for (LogEntry e : log) {
                l.put(new JSONObject().put("t", e.time).put("key", e.keyName).put("tool", e.tool)
                        .put("ok", e.ok).put("detail", e.detail));
            }
        } catch (Exception ignored) {
        }
        sp.edit().putString(K_KEYS, arr.toString()).putString(K_LOG, l.toString()).apply();
    }

    static String sha256(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
