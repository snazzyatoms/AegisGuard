package com.aegisguard.admin;

import com.aegisguard.AegisGuard;
import com.aegisguard.audit.AuditEntry;
import com.aegisguard.audit.AuditService;
import com.aegisguard.data.Plot;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Lightweight read-only web admin dashboard.
 *
 * <p>Runs an embedded HTTP server on a configurable host/port and exposes a
 * small JSON API over the SQL backend and audit ledger. It is disabled by
 * default and should be enabled only after configuring authentication.</p>
 *
 * <p>This is a foundation implementation. All endpoints are read-only and
 * access is gated by a bearer token. Handlers avoid touching live Bukkit state
 * directly (no {@code Bukkit.getOfflinePlayer}/{@code getWorld} lookups) so the
 * dashboard remains safe on Folia where world access must happen on the owning
 * region thread.</p>
 */
public final class WebAdminService {

    private static final int MAX_AUDIT_LIMIT = 500;

    private final AegisGuard plugin;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private HttpServer server;
    private ExecutorService executor;
    private String authToken;
    private boolean enabled;

    public WebAdminService(AegisGuard plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        shutdown();
        this.enabled = plugin.getConfig().getBoolean("web_dashboard.enabled", false);
        this.authToken = plugin.getConfig().getString("web_dashboard.auth_token", "");
        if (this.authToken == null || this.authToken.isBlank()) {
            this.enabled = false;
        }
        if (enabled) {
            start();
        }
    }

    public void start() {
        if (!enabled || !running.compareAndSet(false, true)) return;

        String host = plugin.getConfig().getString("web_dashboard.host", "127.0.0.1");
        int port = plugin.getConfig().getInt("web_dashboard.port", 8080);

        try {
            server = HttpServer.create(new InetSocketAddress(host, port), 0);
            server.createContext("/health", new AuthHandler(this::handleHealth));
            server.createContext("/plots", new AuthHandler(this::handlePlots));
            server.createContext("/audits", new AuthHandler(this::handleAudits));
            executor = Executors.newFixedThreadPool(2, r -> {
                Thread t = new Thread(r, "AegisGuard-WebAdmin");
                t.setDaemon(true);
                return t;
            });
            server.setExecutor(executor);
            server.start();
            plugin.console().info("log_web_dashboard_started",
                    "Web admin dashboard listening on {HOST}:{PORT}",
                    "HOST", host, "PORT", String.valueOf(port));
        } catch (IOException e) {
            running.set(false);
            shutdownExecutor();
            server = null;
            plugin.console().warning("log_web_dashboard_failed",
                    "Could not start web admin dashboard: {ERROR}",
                    "ERROR", e.getMessage() == null ? "" : e.getMessage());
        }
    }

    public void shutdown() {
        if (!running.compareAndSet(true, false)) return;
        if (server != null) {
            server.stop(0);
            server = null;
        }
        shutdownExecutor();
    }

    private void shutdownExecutor() {
        if (executor == null) return;
        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                plugin.getLogger().fine("Web admin executor did not stop within timeout.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        executor = null;
    }

    private void handleHealth(HttpExchange exchange) throws IOException {
        sendJson(exchange, 200,
                "{\"status\":\"ok\",\"server\":\"" + escape(plugin.getServer().getName()) + "\",\"time\":"
                        + Instant.now().getEpochSecond() + "}");
    }

    private void handlePlots(HttpExchange exchange) throws IOException {
        if (plugin.store() == null) {
            sendJson(exchange, 503, "{\"error\":\"storage unavailable\"}");
            return;
        }

        Collection<Plot> plots;
        try {
            plots = plugin.store().getAllPlots();
        } catch (Throwable t) {
            sendJson(exchange, 500, "{\"error\":\"plot store read failed\"}");
            return;
        }

        StringBuilder json = new StringBuilder("{\"plots\":[");
        int index = 0;
        for (Plot plot : plots) {
            if (plot == null || plot.getPlotId() == null) continue;
            if (index++ > 0) json.append(",");
            json.append("{");
            json.append("\"id\":\"").append(plot.getPlotId()).append("\",");
            json.append("\"name\":\"").append(escape(plot.getPlotName())).append("\",");
            json.append("\"world\":\"").append(escape(plot.getWorld())).append("\",");
            json.append("\"owner\":\"").append(escape(ownerName(plot))).append("\",");
            json.append("\"owner_uuid\":\"").append(plot.getOwner()).append("\",");
            json.append("\"x1\":").append(plot.getX1()).append(",");
            json.append("\"z1\":").append(plot.getZ1()).append(",");
            json.append("\"x2\":").append(plot.getX2()).append(",");
            json.append("\"z2\":").append(plot.getZ2()).append(",");
            json.append("\"server_zone\":").append(plot.isServerZone());
            json.append("}");
        }
        json.append("]}");
        sendJson(exchange, 200, json.toString());
    }

    private void handleAudits(HttpExchange exchange) throws IOException {
        AuditService audit = plugin.audit();
        if (audit == null) {
            sendJson(exchange, 503, "{\"error\":\"audit service unavailable\"}");
            return;
        }

        int limit = 100;
        String query = exchange.getRequestURI().getRawQuery();
        if (query != null) {
            for (String pair : query.split("&")) {
                if (pair.startsWith("limit=")) {
                    try {
                        limit = Integer.parseInt(pair.substring("limit=".length()));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        if (limit <= 0) limit = 0;
        if (limit > MAX_AUDIT_LIMIT) limit = MAX_AUDIT_LIMIT;

        List<AuditEntry> entries;
        try {
            entries = audit.recent(limit);
        } catch (Throwable t) {
            sendJson(exchange, 500, "{\"error\":\"audit read failed\"}");
            return;
        }

        StringBuilder json = new StringBuilder("{\"audits\":[");
        int index = 0;
        for (AuditEntry entry : entries) {
            if (entry == null) continue;
            if (index++ > 0) json.append(",");
            json.append("{");
            json.append("\"id\":\"").append(entry.getId()).append("\",");
            json.append("\"timestamp\":").append(entry.getTimestamp()).append(",");
            json.append("\"category\":\"").append(entry.getCategory()).append("\",");
            json.append("\"actor\":\"").append(escape(entry.getActorName())).append("\",");
            json.append("\"actor_uuid\":\"").append(entry.getActorId()).append("\",");
            json.append("\"target\":\"").append(escape(entry.getTarget())).append("\",");
            json.append("\"summary\":\"").append(escape(entry.getSummary())).append("\"");
            json.append("}");
        }
        json.append("]}");
        sendJson(exchange, 200, json.toString());
    }

    /** Uses the stored owner name on the plot so no off-thread Bukkit lookup is needed. */
    private String ownerName(Plot plot) {
        String name = plot.getOwnerName();
        if (name != null && !name.isBlank()) return name;
        return plot.getOwner() == null ? "server" : plot.getOwner().toString();
    }

    private void sendJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String escape(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /** Constant-time token comparison to avoid leaking the token via timing. */
    private boolean tokenMatches(String provided) {
        if (provided == null || authToken == null) return false;
        return MessageDigest.isEqual(
                provided.getBytes(StandardCharsets.UTF_8),
                authToken.getBytes(StandardCharsets.UTF_8));
    }

    private final class AuthHandler implements HttpHandler {
        private final HttpHandler delegate;

        AuthHandler(HttpHandler delegate) {
            this.delegate = delegate;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                    sendJson(exchange, 405, "{\"error\":\"method not allowed\"}");
                    return;
                }
                String provided = exchange.getRequestHeaders().getFirst("Authorization");
                String bearer = provided != null && provided.startsWith("Bearer ")
                        ? provided.substring("Bearer ".length())
                        : null;
                if (!tokenMatches(bearer)) {
                    sendJson(exchange, 401, "{\"error\":\"unauthorized\"}");
                    return;
                }
                delegate.handle(exchange);
            } catch (Throwable t) {
                try {
                    sendJson(exchange, 500, "{\"error\":\"internal error\"}");
                } catch (Throwable ignored) {
                }
            }
        }
    }
}
