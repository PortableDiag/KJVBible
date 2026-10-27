package com.portablediag.kjvbible.api;

import android.content.Context;
import android.content.SharedPreferences;

import com.portablediag.kjvbible.BuildConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * KJV Bible's local API, in the shape TextX, DialX, NotifX and Lull share, so PhoneAPI can
 * register it as a provider unchanged:
 *
 *  - Off until the owner switches it on; a quiet notification says while it is on.
 *  - Bound to 127.0.0.1. LAN access is a separate switch, off by default.
 *  - A key per caller, shown once, kept as a hash; every tool off per key until switched on.
 *  - Every request runs the same {@link Tools#run} as an MCP call, and lands in the audit log.
 */
public final class LocalApi {

    private LocalApi() {}

    public static final String LOOPBACK = "127.0.0.1";
    /** Lull 8787, TextX 8788, DialX 8789, NotifX 8790. */
    public static final int DEFAULT_PORT = 8794;
    public static final int MIN_PORT = 1024;
    public static final int MAX_PORT = 65535;

    private static final String FILE = "local_api";
    private static final String K_ENABLED = "enabled";
    private static final String K_PORT = "port";
    private static final String K_LAN = "lan";

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    // ---- settings ----

    public static boolean enabled(Context c) {
        return sp(c).getBoolean(K_ENABLED, false);
    }

    public static void setEnabled(Context c, boolean on) {
        failure = null;
        sp(c).edit().putBoolean(K_ENABLED, on).apply();
        ApiService.sync(c);
        changed();
    }

    public static int port(Context c) {
        return Math.max(MIN_PORT, Math.min(MAX_PORT, sp(c).getInt(K_PORT, DEFAULT_PORT)));
    }

    public static void setPort(Context c, int port) {
        sp(c).edit().putInt(K_PORT, Math.max(MIN_PORT, Math.min(MAX_PORT, port))).apply();
        ApiService.restart(c);
    }

    public static boolean lan(Context c) {
        return sp(c).getBoolean(K_LAN, false);
    }

    public static void setLan(Context c, boolean on) {
        sp(c).edit().putBoolean(K_LAN, on).apply();
        ApiService.restart(c);
    }

    public static String bindAddress(Context c) {
        return lan(c) ? "0.0.0.0" : LOOPBACK;
    }

    // ---- status, for the settings screen ----

    static volatile boolean running;
    static volatile String failure;

    public static boolean isRunning() {
        return running;
    }

    /** Why the last start failed, until the switch is touched again. */
    public static String failure() {
        return failure;
    }

    /** The server could not start: turn the switch off so it is not left saying on. */
    static void failed(Context c, String reason) {
        sp(c).edit().putBoolean(K_ENABLED, false).apply();
        failure = reason;
        changed();
    }

    public interface Listener {
        void onApiChanged();
    }

    private static final List<Listener> listeners = new CopyOnWriteArrayList<>();

    public static void addListener(Listener l) {
        listeners.add(l);
    }

    public static void removeListener(Listener l) {
        listeners.remove(l);
    }

    static void changed() {
        for (Listener l : listeners) l.onApiChanged();
    }

    // ---- requests ----

    static Http.Response handle(Context ctx, Http.Request req) throws Http.ApiException {
        // A web page on this phone reaching 127.0.0.1 through DNS rebinding carries another Host.
        if (!lan(ctx)) {
            String host = req.header("host");
            if (host != null) {
                String h = host.startsWith("[") ? host.substring(1, Math.max(1, host.indexOf(']')))
                        : (host.contains(":") ? host.substring(0, host.lastIndexOf(':')) : host);
                if (!h.equals("127.0.0.1") && !h.equals("localhost") && !h.equals("::1")) {
                    return Http.Response.error(403, "Host not allowed", null);
                }
            }
        }
        if (req.method.equals("OPTIONS")) return Http.Response.error(405, "No CORS here", null);
        if (req.path.equals("/api/health")) {
            return Http.Response.json(obj("ok", true, "app", "KJV Bible", "version", BuildConfig.VERSION_NAME,
                    "package", ctx.getPackageName()));
        }

        String raw = req.header("x-api-key");
        if (raw == null) {
            String auth = req.header("authorization");
            if (auth != null && auth.startsWith("Bearer ")) raw = auth.substring(7).trim();
        }
        ApiKeys.Key key = ApiKeys.get(ctx).match(raw);
        if (key == null) {
            return Http.Response.error(401, "Missing or unknown key",
                    "Send it as X-API-Key or Authorization: Bearer. Keys are made in KJV Bible › Local API.");
        }

        String path = req.path;
        if (path.equals("/api/me")) {
            JSONArray tools = new JSONArray();
            for (Tools.Tool t : Tools.enabled(key)) tools.put(t.name);
            return Http.Response.json(obj("name", key.name, "kid", key.kid, "tools", tools));
        }
        if (path.equals("/api/tools")) {
            JSONArray arr = new JSONArray();
            for (Tools.Tool t : Tools.enabled(key)) {
                arr.put(obj("name", t.name, "description", t.description, "input_schema", schema(t)));
            }
            return Http.Response.json(arr);
        }
        if (path.equals("/api/docs")) {
            return Http.Response.text(docs(key), "text/markdown; charset=utf-8");
        }
        if (path.startsWith("/api/tools/") && req.method.equals("POST")) {
            return result(Tools.run(ctx, key, path.substring("/api/tools/".length()), req.json()));
        }
        if (path.equals("/api/read")) {
            return result(Tools.run(ctx, key, "kjv.read", obj("passage", q(req, "passage"))));
        }
        if (path.equals("/api/search")) {
            JSONObject args = obj("query", q(req, "q"));
            put(args, "testament", req.query.get("testament"));
            put(args, "book", req.query.get("book"));
            put(args, "limit", req.query.get("limit"));
            return result(Tools.run(ctx, key, "kjv.search", args));
        }
        if (path.equals("/api/image")) {
            return image(ctx, key, q(req, "passage"), req.query.get("theme"));
        }
        if (path.equals("/mcp") && req.method.equals("POST")) {
            return mcp(ctx, key, req.json());
        }
        return Http.Response.error(404, "No route " + req.method + " " + path, "GET /api/docs lists them");
    }

    /** A refused or failed tool is still a 200 with ok:false, except a switched-off tool. */
    private static Http.Response result(JSONObject r) {
        JSONObject err = r.optJSONObject("error");
        if (err != null && "not_allowed".equals(err.optString("code"))) return Http.Response.json(r, 403);
        if (err != null && "unknown_tool".equals(err.optString("code"))) return Http.Response.json(r, 404);
        return Http.Response.json(r);
    }

    /** The PNG itself, for a caller that wants bytes rather than base64. */
    private static Http.Response image(Context ctx, ApiKeys.Key key, String passage, String theme) {
        ApiKeys keys = ApiKeys.get(ctx);
        if (!key.allows("kjv.share_image")) {
            JSONObject r = Tools.run(ctx, key, "kjv.share_image", obj("passage", passage));
            return Http.Response.json(r, 403);
        }
        try {
            com.portablediag.kjvbible.Bible bible = com.portablediag.kjvbible.Bible.get(ctx);
            Tools.Image img = Tools.renderImage(ctx, bible, bible.parsePassage(passage), theme);
            keys.record(key, "kjv.share_image", true, passage + " (png)");
            return Http.Response.bytes(img.png, "image/png");
        } catch (IllegalArgumentException e) {
            keys.record(key, "kjv.share_image", false, "bad_passage: " + passage);
            return Http.Response.error(400, e.getMessage(), null);
        } catch (Tools.ToolError e) {
            keys.record(key, "kjv.share_image", false, e.code + ": " + passage);
            return Http.Response.error(400, e.getMessage(), null);
        }
    }

    /** MCP over plain JSON-RPC (stateless): initialize, tools/list, tools/call, ping. */
    private static Http.Response mcp(Context ctx, ApiKeys.Key key, JSONObject msg) {
        Object id = msg.opt("id");
        String method = msg.optString("method");
        try {
            switch (method) {
                case "initialize":
                    return rpc(id, obj("protocolVersion", "2024-11-05",
                            "serverInfo", obj("name", "KJV Bible", "version", BuildConfig.VERSION_NAME),
                            "capabilities", obj("tools", new JSONObject())));
                case "tools/list": {
                    JSONArray arr = new JSONArray();
                    for (Tools.Tool t : Tools.enabled(key)) {
                        arr.put(obj("name", t.name.replace('.', '_'), "description", t.description,
                                "inputSchema", schema(t)));
                    }
                    return rpc(id, obj("tools", arr));
                }
                case "tools/call": {
                    JSONObject p = msg.optJSONObject("params");
                    if (p == null) p = new JSONObject();
                    String name = p.optString("name").replaceFirst("^kjv_", "kjv.");
                    JSONObject args = p.optJSONObject("arguments");
                    JSONObject r = Tools.run(ctx, key, name, args == null ? new JSONObject() : args);
                    JSONArray content = new JSONArray();
                    JSONObject res = r.optJSONObject("result");
                    if (res != null && res.has("image_base64")) {
                        content.put(obj("type", "image", "data", res.getString("image_base64"),
                                "mimeType", "image/png"));
                        res.remove("image_base64");
                    }
                    content.put(obj("type", "text", "text", r.toString()));
                    return rpc(id, obj("content", content, "isError", !r.optBoolean("ok")));
                }
                case "ping":
                    return rpc(id, new JSONObject());
                default:
                    return Http.Response.json(obj("jsonrpc", "2.0", "id", id,
                            "error", obj("code", -32601, "message", "Method not found")));
            }
        } catch (Exception e) {
            return Http.Response.json(obj("jsonrpc", "2.0", "id", id,
                    "error", obj("code", -32603, "message", String.valueOf(e.getMessage()))));
        }
    }

    private static Http.Response rpc(Object id, JSONObject result) {
        return Http.Response.json(obj("jsonrpc", "2.0", "id", id, "result", result));
    }

    private static String docs(ApiKeys.Key key) {
        StringBuilder sb = new StringBuilder();
        sb.append("# KJV Bible local API\n\n");
        sb.append("Send the key as `X-API-Key` (or `Authorization: Bearer`). Each tool is switched on per key ")
                .append("by the owner in KJV Bible › Local API; a switched-off tool answers 403 and lists what is on.\n\n");
        sb.append("- `GET /api/health` (no key) · `GET /api/me` · `GET /api/tools` · `GET /api/docs`\n");
        sb.append("- `POST /api/tools/{name}` with the arguments as a JSON body → `{ok, result}` or `{ok:false, error:{code, message}}`\n");
        sb.append("- `GET /api/read?passage=` · `GET /api/search?q=&testament=&book=&limit=` · `GET /api/image?passage=&theme=` (the PNG itself)\n");
        sb.append("- `POST /mcp`: MCP over JSON-RPC (initialize, tools/list, tools/call, ping); tool names use `_` for `.`\n\n");
        sb.append("A passage is one chapter or verses in it: `John 3:16`, `John 3:16-18`, `Jhn 3:16, 18`, `Psalm 23`. ")
                .append("Text spans are `[start,end)` offsets into the verse's plain text.\n\n");
        sb.append("## Tools this key may use\n\n");
        List<Tools.Tool> on = Tools.enabled(key);
        for (Tools.Tool t : on) sb.append("- `").append(t.name).append("`: ").append(t.description).append('\n');
        if (on.isEmpty()) sb.append("None yet: switch tools on for this key in KJV Bible › Local API.\n");
        return sb.toString();
    }

    // ---- small JSON helpers ----

    private static JSONObject schema(Tools.Tool t) {
        try {
            return new JSONObject(t.schema);
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private static String q(Http.Request req, String name) {
        String v = req.query.get(name);
        return v == null ? "" : v;
    }

    private static void put(JSONObject o, String k, String v) {
        if (v == null || v.isEmpty()) return;
        try { o.put(k, v); } catch (Exception ignored) { }
    }

    static JSONObject obj(Object... kv) {
        JSONObject o = new JSONObject();
        try {
            for (int i = 0; i + 1 < kv.length; i += 2) o.put((String) kv[i], kv[i + 1] == null ? JSONObject.NULL : kv[i + 1]);
        } catch (Exception ignored) {
        }
        return o;
    }
}
