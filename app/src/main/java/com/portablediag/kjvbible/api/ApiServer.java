package com.portablediag.kjvbible.api;

import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The socket half of the API: accept a connection, read one request, write one response, close.
 * No keep-alive. The accept loop has its own thread and hands each connection to a small pool,
 * so one slow caller cannot stop the next one being served.
 */
public final class ApiServer {

    public interface Handler {
        Http.Response handle(Http.Request req) throws Http.ApiException;
    }

    /** Last touch on every response to a parsed request, errors included (PhoneAPI's proof header). */
    public interface Sealer {
        Http.Response seal(Http.Request req, Http.Response res);
    }

    private static final String TAG = "KjvApi";
    private static final int WORKERS = 4;
    private static final int SOCKET_TIMEOUT_MS = 15_000;
    private static final int BACKLOG = 16;

    private final Handler handler;
    private final Sealer sealer;
    private final String bindAddress;
    private final int port;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private ServerSocket serverSocket;
    private ExecutorService workers;

    public ApiServer(Handler handler, Sealer sealer, String bindAddress, int port) {
        this.handler = handler;
        this.sealer = sealer;
        this.bindAddress = bindAddress;
        this.port = port;
    }

    public boolean isRunning() {
        return running.get();
    }

    /** @throws IOException when the port is taken or the address cannot be bound. */
    public synchronized void start() throws IOException {
        if (running.get()) return;
        ServerSocket socket = new ServerSocket(port, BACKLOG, InetAddress.getByName(bindAddress));
        socket.setReuseAddress(true);
        serverSocket = socket;
        workers = Executors.newFixedThreadPool(WORKERS);
        running.set(true);
        Thread accept = new Thread(() -> acceptLoop(socket), "kjv-api-accept");
        accept.setDaemon(true);
        accept.start();
        Log.i(TAG, "listening on " + bindAddress + ":" + port);
    }

    public synchronized void stop() {
        if (!running.getAndSet(false)) return;
        try { if (serverSocket != null) serverSocket.close(); } catch (IOException ignored) { }
        if (workers != null) workers.shutdownNow();
        serverSocket = null;
        workers = null;
        Log.i(TAG, "stopped");
    }

    private void acceptLoop(ServerSocket socket) {
        while (running.get()) {
            Socket client;
            try {
                client = socket.accept();
            } catch (IOException e) {
                // A close() while blocked in accept() lands here; that is a stop, not a fault.
                if (running.get()) Log.w(TAG, "accept failed", e);
                return;
            }
            ExecutorService pool = workers;
            if (pool == null || pool.isShutdown()) {
                closeQuietly(client);
                return;
            }
            try {
                pool.execute(() -> serve(client));
            } catch (Exception e) {
                closeQuietly(client);
            }
        }
    }

    private void serve(Socket client) {
        try (Socket socket = client) {
            socket.setSoTimeout(SOCKET_TIMEOUT_MS);
            BufferedInputStream in = new BufferedInputStream(socket.getInputStream());
            BufferedOutputStream out = new BufferedOutputStream(socket.getOutputStream());
            Http.Response response;
            Http.Request request = null;
            try {
                request = Http.parse(in);
                if (request == null) return;
                response = handler.handle(request);
            } catch (Http.ApiException e) {
                response = Http.Response.error(e.status, e.getMessage(), e.hint);
            } catch (IOException e) {
                return;
            } catch (Exception e) {
                Log.w(TAG, "request failed", e);
                response = Http.Response.error(500, e.getClass().getSimpleName() + ": " + e.getMessage(), null);
            }
            if (request != null && sealer != null) {
                try {
                    response = sealer.seal(request, response);
                } catch (Exception e) {
                    Log.w(TAG, "seal failed", e);
                }
            }
            write(out, response);
        } catch (IOException e) {
            Log.w(TAG, "response failed", e);
        }
    }

    private static void write(OutputStream out, Http.Response r) throws IOException {
        StringBuilder head = new StringBuilder()
                .append("HTTP/1.1 ").append(r.status).append(' ').append(r.reason()).append("\r\n")
                .append("Content-Type: ").append(r.contentType).append("\r\n")
                .append("Content-Length: ").append(r.body.length).append("\r\n")
                .append("Connection: close\r\n")
                .append("Cache-Control: no-store\r\n");
        for (java.util.Map.Entry<String, String> h : r.headers.entrySet()) {
            head.append(h.getKey()).append(": ").append(h.getValue()).append("\r\n");
        }
        head.append("\r\n");
        out.write(head.toString().getBytes(StandardCharsets.US_ASCII));
        out.write(r.body);
        out.flush();
    }

    private static void closeQuietly(Socket s) {
        try { s.close(); } catch (IOException ignored) { }
    }
}
