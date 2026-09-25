package com.aegisguard.admin;

import com.aegisguard.AegisGuard;
import com.aegisguard.data.Plot;
import com.aegisguard.data.SQLDataStore;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.Bukkit;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Lightweight read-only web admin dashboard.
 *
 * <p>Runs an embedded HTTP server on a configurable host/port and exposes a
 * small JSON API over the SQL backend and audit ledger. It is disabled by
 * default and should be enabled only after configuring authentication.</p>
 *
 * <p>This is a foundation implementation. All endpoints are read-only and
 * access is gated by a bearer token.</p>
 */
public final class WebAdminService {

    private final AegisGuard plugin;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private HttpServer server;
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
        if (!enabled || running.compareAndSet(false, true) == false) return;

        String host = plugin.getConfig().getString("web_dashboard.host", "127.0.0.1");
        int port = plugin.getConfig().getInt("web_dashboard.port", 8080);

        try {
            server = HttpServer.create(new InetSocketAddress(host, port), 0);
            server.createContext("/health", new AuthHandler(this::handleHealth));
            server.createContext("/plots", new AuthHandler(this::handlePlots));
            server.createContext("/audits", new AuthHandler(this::handleAudits));
            server.setExecutor(Executors.newFixedThreadPool(2, r -> new Thread(r, "AegisGuard-WebAdmin")));
            server.start();
            plugin.console().info("log_web_dashboard_started",
                    "Web admin dashboard listening on {HOST}:{PORT}",
                    "HOST", host, "PORT", String.valueOf(port));
        } catch (IOException e) {
            running.set(false);
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
    }

    private void handleHealth(HttpExchange exchange) throws IOException {
        sendJson(exchange, 200,
                "{\"status\":\"ok\",\"server\":\"" + escape(plugin.getServer().getName()) + "\",\"time\":"
                        + Instant.now().getEpochSecond() + "}");
    }

    private void handlePlots(HttpExchange exchange) throws IOException {
        if (!(plugin.store() instanceof SQLDataStore)) {
            sendJson(exchange, 503, "{\"error\":\"SQL storage required for web dashboard\"}");
            return;
        }

        Collection<Plot> plots = plugin.store().getAllPlots();
        StringBuilder json = new StringBuilder("{\"plots\":[");
        int index = 0;
        for (Plot plot : plots) {
            if (index++ > 0) json.append(",");
            json.append("{");
            json.append("\"id\":").append(plot.getPlotId()).append(",");
            json.append("\"name\":\"").append(escape(plot.getPlotName())).append("\",");
            json.append("\"world\":\"").append(escape(plot.getWorld())).append("\",");
            json.append("\"owner\":\"").append(escape(ownerName(plot.getOwner()))).append("\",");
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
        // Foundation: return a stub list. AuditService exposes entries; extend here.
        sendJson(exchange, 200, "{\"audits\":[],\"note\":\"Audit endpoint foundation - wire to AuditService.entries() as needed.\"}");
    }

    private String ownerName(UUID uuid) {
        if (uuid == null) return "server";
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        return name == null ? uuid.toString() : name;
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

    private final class AuthHandler implements HttpHandler {
        private final HttpHandler delegate;

        AuthHandler(HttpHandler delegate) {
            this.delegate = delegate;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"error\":\"method not allowed\"}");
                return;
            }
            String provided = exchange.getRequestHeaders().getFirst("Authorization");
            if (provided == null || !provided.equals("Bearer " + authToken)) {
                sendJson(exchange, 401, "{\"error\":\"unauthorized\"}");
                return;
            }
            delegate.handle(exchange);
        }
    }
}
