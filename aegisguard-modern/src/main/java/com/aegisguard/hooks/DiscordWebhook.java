package com.aegisguard.hooks;

import com.aegisguard.AegisGuard;
import org.bukkit.Bukkit;

import javax.net.ssl.HttpsURLConnection;
import java.io.OutputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.awt.Color;

/**
 * DiscordWebhook
 * - Lightweight, zero-dependency class to send Embeds to Discord.
 * - Runs asynchronously to prevent server lag.
 */
public class DiscordWebhook {

    private final AegisGuard plugin;
    private final String url;

    public DiscordWebhook(AegisGuard plugin) {
        this.plugin = plugin;
        this.url = plugin.cfg().raw().getString("hooks.discord.webhook_url", "");
    }

    public boolean isEnabled() {
        return plugin.cfg().raw().getBoolean("hooks.discord.enabled", false) 
               && url != null 
               && url.startsWith("http");
    }

    /**
     * Sends an Embed to the configured Webhook URL asynchronously.
     */
    public void send(EmbedObject embed) {
        if (!isEnabled()) return;

        plugin.runGlobalAsync(() -> {
            try {
                String json = "{\"embeds\": [" + embed.toJson() + "]}";
                performRequest(json);
            } catch (Exception e) {
                plugin.getLogger().warning("[Discord] Failed to send webhook: " + e.getMessage());
            }
        });
    }

    /**
     * Sends a plain text message asynchronously.
     */
    public void send(String content) {
        if (!isEnabled()) return;

        plugin.runGlobalAsync(() -> {
            try {
                String json = "{\"content\": \"" + escape(content) + "\"}";
                performRequest(json);
            } catch (Exception e) {
                plugin.getLogger().warning("[Discord] Failed to send webhook: " + e.getMessage());
            }
        });
    }

    private void performRequest(String jsonPayload) throws Exception {
        URL urlObj = new URL(this.url);
        HttpsURLConnection connection = (HttpsURLConnection) urlObj.openConnection();
        connection.addRequestProperty("Content-Type", "application/json");
        connection.addRequestProperty("User-Agent", "AegisGuard-Plugin");
        connection.setDoOutput(true);
        connection.setRequestMethod("POST");

        try (OutputStream stream = connection.getOutputStream()) {
            stream.write(jsonPayload.getBytes());
            stream.flush();
        }

        connection.getInputStream().close();
        connection.disconnect();
    }

    private static String escape(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\b", "\\b")
                  .replace("\f", "\\f")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }

    // --- EMBED BUILDER ---

    public static class EmbedObject {
        private String title;
        private String description;
        private String url;
        private int color;
        private final List<Field> fields = new ArrayList<>();
        private Footer footer;
        private Thumbnail thumbnail;

        public EmbedObject setTitle(String title) { this.title = title; return this; }
        public EmbedObject setDescription(String description) { this.description = description; return this; }
        public EmbedObject setUrl(String url) { this.url = url; return this; }
        
        public EmbedObject setColor(Color color) {
            if (color != null) this.color = color.getRGB() & 0xFFFFFF; // Strip alpha
            return this;
        }
        
        public EmbedObject setColor(int r, int g, int b) {
            this.color = ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
            return this;
        }

        public EmbedObject addField(String name, String value, boolean inline) {
            this.fields.add(new Field(name, value, inline));
            return this;
        }

        public EmbedObject setFooter(String text, String iconUrl) {
            this.footer = new Footer(text, iconUrl);
            return this;
        }
        
        public EmbedObject setThumbnail(String url) {
            this.thumbnail = new Thumbnail(url);
            return this;
        }

        public String toJson() {
            StringBuilder builder = new StringBuilder();
            builder.append("{");
            
            if (title != null) builder.append("\"title\": \"").append(escape(title)).append("\",");
            if (description != null) builder.append("\"description\": \"").append(escape(description)).append("\",");
            if (url != null) builder.append("\"url\": \"").append(escape(url)).append("\",");
            if (color != 0) builder.append("\"color\": ").append(color).append(",");
            
            if (thumbnail != null) {
                builder.append("\"thumbnail\": {\"url\": \"").append(escape(thumbnail.url)).append("\"},");
            }

            if (footer != null) {
                builder.append("\"footer\": {");
                builder.append("\"text\": \"").append(escape(footer.text)).append("\",");
                builder.append("\"icon_url\": \"").append(escape(footer.iconUrl)).append("\"");
                builder.append("},");
            }

            if (!fields.isEmpty()) {
                builder.append("\"fields\": [");
                for (int i = 0; i < fields.size(); i++) {
                    Field f = fields.get(i);
                    builder.append("{");
                    builder.append("\"name\": \"").append(escape(f.name)).append("\",");
                    builder.append("\"value\": \"").append(escape(f.value)).append("\",");
                    builder.append("\"inline\": ").append(f.inline);
                    builder.append("}");
                    if (i < fields.size() - 1) builder.append(",");
                }
                builder.append("],");
            }

            // Remove trailing comma if exists
            if (builder.charAt(builder.length() - 1) == ',') {
                builder.deleteCharAt(builder.length() - 1);
            }

            builder.append("}");
            return builder.toString();
        }

        private static class Field {
            String name, value;
            boolean inline;
            public Field(String name, String value, boolean inline) {
                this.name = name; this.value = value; this.inline = inline;
            }
        }

        private static class Footer {
            String text, iconUrl;
            public Footer(String text, String iconUrl) { this.text = text; this.iconUrl = iconUrl; }
        }
        
        private static class Thumbnail {
            String url;
            public Thumbnail(String url) { this.url = url; }
        }
    }
}
