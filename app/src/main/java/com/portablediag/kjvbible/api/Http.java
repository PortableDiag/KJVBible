package com.portablediag.kjvbible.api;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Just enough HTTP/1.1 to serve a local API: a request line, headers, a Content-Length body,
 * one response, close. The same shape as Lull, TextX, DialX and NotifX, in Java.
 */
public final class Http {

    private Http() {}

    /** The largest body accepted. Every tool takes a few short arguments. */
    static final int MAX_BODY = 64 * 1024;

    /** A request line longer than this is not a request, it is someone probing. */
    private static final int MAX_LINE = 8 * 1024;

    /** A parsed request. {@code path} is percent-decoded; header names are lower-cased. */
    public static final class Request {
        public final String method;
        public final String path;
        public final Map<String, String> query;
        public final Map<String, String> headers;
        public final byte[] body;

        Request(String method, String path, Map<String, String> query,
                Map<String, String> headers, byte[] body) {
            this.method = method;
            this.path = path;
            this.query = query;
            this.headers = headers;
            this.body = body;
        }

        public String header(String name) {
            return headers.get(name.toLowerCase(Locale.US));
        }

        /** The JSON body, or an empty object: a body-less POST means "with defaults". */
        public JSONObject json() throws ApiException {
            String text = new String(body, StandardCharsets.UTF_8).trim();
            if (text.isEmpty()) return new JSONObject();
            try {
                return new JSONObject(text);
            } catch (Exception e) {
                throw new ApiException(400, "body is not a JSON object: " + e.getMessage(), null);
            }
        }
    }

    /** A response held in memory; everything this API returns is small. */
    public static final class Response {
        public final int status;
        public final String contentType;
        public final byte[] body;

        Response(int status, String contentType, byte[] body) {
            this.status = status;
            this.contentType = contentType;
            this.body = body;
        }

        public String reason() {
            switch (status) {
                case 200: return "OK";
                case 400: return "Bad Request";
                case 401: return "Unauthorized";
                case 403: return "Forbidden";
                case 404: return "Not Found";
                case 405: return "Method Not Allowed";
                case 413: return "Payload Too Large";
                case 500: return "Internal Server Error";
                default: return "OK";
            }
        }

        public static Response json(JSONObject obj, int status) {
            return new Response(status, "application/json; charset=utf-8",
                    indent(obj).getBytes(StandardCharsets.UTF_8));
        }

        public static Response json(JSONObject obj) {
            return json(obj, 200);
        }

        public static Response json(JSONArray arr) {
            String s;
            try { s = arr.toString(2); } catch (Exception e) { s = arr.toString(); }
            return new Response(200, "application/json; charset=utf-8",
                    s.getBytes(StandardCharsets.UTF_8));
        }

        public static Response text(String body, String type) {
            return new Response(200, type, body.getBytes(StandardCharsets.UTF_8));
        }

        public static Response bytes(byte[] body, String type) {
            return new Response(200, type, body);
        }

        public static Response error(int status, String message, String hint) {
            JSONObject obj = new JSONObject();
            try {
                obj.put("error", message);
                if (hint != null) obj.put("hint", hint);
            } catch (Exception ignored) {
            }
            return json(obj, status);
        }

        private static String indent(JSONObject obj) {
            try { return obj.toString(2); } catch (Exception e) { return obj.toString(); }
        }
    }

    /** Anything a handler fails with that the caller should see as a status code, not a 500. */
    public static final class ApiException extends Exception {
        public final int status;
        public final String hint;

        public ApiException(int status, String message, String hint) {
            super(message);
            this.status = status;
            this.hint = hint;
        }
    }

    /**
     * Reads one request. Returns null at a clean end of stream (a port scanner that connected
     * and left); throws ApiException for something that arrived but is not a request.
     */
    public static Request parse(InputStream in) throws IOException, ApiException {
        String requestLine = readLine(in);
        if (requestLine == null || requestLine.trim().isEmpty()) return null;

        String[] parts = requestLine.split(" ");
        if (parts.length < 2) throw new ApiException(400, "malformed request line", null);
        String method = parts[0].toUpperCase(Locale.US);
        String target = parts[1];

        Map<String, String> headers = new HashMap<>();
        while (true) {
            String line = readLine(in);
            if (line == null || line.isEmpty()) break;
            int colon = line.indexOf(':');
            if (colon <= 0) continue;
            headers.put(line.substring(0, colon).trim().toLowerCase(Locale.US),
                    line.substring(colon + 1).trim());
        }

        int length = 0;
        try {
            String cl = headers.get("content-length");
            if (cl != null) length = Integer.parseInt(cl.trim());
        } catch (NumberFormatException e) {
            throw new ApiException(400, "bad Content-Length", null);
        }
        if (length > MAX_BODY) throw new ApiException(413, "body larger than " + MAX_BODY + " bytes", null);
        byte[] body = length > 0 ? readFully(in, length) : new byte[0];

        int q = target.indexOf('?');
        String rawPath = q >= 0 ? target.substring(0, q) : target;
        String rawQuery = q >= 0 ? target.substring(q + 1) : "";
        String path = decode(rawPath);
        while (path.length() > 1 && path.endsWith("/")) path = path.substring(0, path.length() - 1);
        return new Request(method, path, parseQuery(rawQuery), headers, body);
    }

    static Map<String, String> parseQuery(String raw) {
        if (raw.isEmpty()) return Collections.emptyMap();
        Map<String, String> out = new LinkedHashMap<>();
        for (String pair : raw.split("&")) {
            if (pair.isEmpty()) continue;
            int eq = pair.indexOf('=');
            String name = eq >= 0 ? pair.substring(0, eq) : pair;
            String value = eq >= 0 ? pair.substring(eq + 1) : "";
            out.put(decode(name.replace('+', ' ')), decode(value.replace('+', ' ')));
        }
        return out;
    }

    private static String decode(String raw) {
        try {
            return URLDecoder.decode(raw.replace("+", "%2B"), "UTF-8");
        } catch (Exception e) {
            return raw;
        }
    }

    /** One CRLF-terminated line as ASCII; a bare LF is accepted for hand-rolled clients. */
    private static String readLine(InputStream in) throws IOException, ApiException {
        StringBuilder sb = new StringBuilder();
        while (true) {
            int b = in.read();
            if (b == -1) return sb.length() == 0 ? null : sb.toString();
            if (b == '\n') {
                int n = sb.length();
                if (n > 0 && sb.charAt(n - 1) == '\r') sb.setLength(n - 1);
                return sb.toString();
            }
            sb.append((char) b);
            if (sb.length() > MAX_LINE) throw new ApiException(413, "header line too long", null);
        }
    }

    private static byte[] readFully(InputStream in, int length) throws IOException {
        byte[] out = new byte[length];
        int read = 0;
        while (read < length) {
            int n = in.read(out, read, length - read);
            if (n < 0) break;
            read += n;
        }
        if (read == length) return out;
        byte[] cut = new byte[read];
        System.arraycopy(out, 0, cut, 0, read);
        return cut;
    }
}
