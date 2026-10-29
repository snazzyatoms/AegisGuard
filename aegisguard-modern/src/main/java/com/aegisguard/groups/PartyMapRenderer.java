package com.aegisguard.groups;

import com.aegisguard.AegisGuard;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Draws party members as skin-face cursors on a real map item — MMO-style. Terrain
 * renders underneath as usual; this renderer only paints cursors on top. Positions
 * come from {@link PartyService#positionOf} so nothing is read off another entity's
 * region thread. Faces are fetched once per player on the async executor and cached.
 */
public final class PartyMapRenderer extends MapRenderer {

    private static final int FACE = 8;          // source face size in the skin file
    private static final int CURSOR = 12;       // drawn size on the map
    private static final BufferedImage PING_ICON = pingIcon();

    private final AegisGuard plugin;
    private final PartyService parties;
    private final Map<UUID, BufferedImage> faces = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> faceLoading = new ConcurrentHashMap<>();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();

    public PartyMapRenderer(AegisGuard plugin, PartyService parties) {
        this.plugin = plugin;
        this.parties = parties;
        this.contextual(false);
    }

    /**
     * A custom renderer replaces vanilla terrain rendering for the view, so the map is
     * styled as a party radar: dark field, center crosshair, member face cursors, ping.
     */
    @Override
    public void render(MapView view, MapCanvas canvas, Player viewer) {
        paintRadarField(canvas);
        Party party = parties.partyOf(viewer.getUniqueId());
        if (party == null) return;
        int scale = view.getScale().getValue();
        int centerX = view.getCenterX();
        int centerZ = view.getCenterZ();
        Location from = viewer.getLocation();

        // Viewer marker at their own position (white diamond).
        drawCursor(canvas, projectX(from.getBlockX(), centerX, scale),
                projectZ(from.getBlockZ(), centerZ, scale), selfIcon());

        for (UUID memberId : party.getMembers()) {
            if (memberId.equals(viewer.getUniqueId()) || parties.isHidden(memberId)) continue;
            Location target = parties.positionOf(memberId);
            if (target == null || target.getWorld() == null
                    || !target.getWorld().equals(from.getWorld())) continue;
            BufferedImage face = faces.computeIfAbsent(memberId,
                    id -> requestFace(id));
            if (face == null) continue;
            int px = projectX(target.getBlockX(), centerX, scale);
            int pz = projectZ(target.getBlockZ(), centerZ, scale);
            drawCursor(canvas, px, pz, face);
        }

        Location ping = party.pingPoint();
        if (ping != null && ping.getWorld() != null && ping.getWorld().equals(from.getWorld())) {
            drawCursor(canvas, projectX(ping.getBlockX(), centerX, scale),
                    projectZ(ping.getBlockZ(), centerZ, scale), PING_ICON);
        }
    }

    private void paintRadarField(MapCanvas canvas) {
        for (int x = 0; x < 128; x++) {
            for (int y = 0; y < 128; y++) {
                canvas.setPixel(x, y, org.bukkit.map.MapPalette.DARK_GRAY);
            }
        }
        for (int i = 0; i < 128; i++) {
            canvas.setPixel(i, 64, org.bukkit.map.MapPalette.GRAY);
            canvas.setPixel(64, i, org.bukkit.map.MapPalette.GRAY);
        }
    }

    /** World X/Z → map pixel (0-127), edge-clamped like vanilla player cursors. */
    private int projectX(int worldX, int centerX, int scale) {
        return clamp((worldX - centerX) / scale + 64);
    }

    private int projectZ(int worldZ, int centerZ, int scale) {
        return clamp((worldZ - centerZ) / scale + 64);
    }

    private int clamp(int pixel) {
        return Math.max(2, Math.min(126 - CURSOR, pixel - CURSOR / 2));
    }

    private void drawCursor(MapCanvas canvas, int px, int pz, BufferedImage icon) {
        try {
            canvas.drawImage(px, pz, icon);
        } catch (Throwable ignored) { }
    }

    /** Kick an async skin fetch once per member; render skips until the face arrives. */
    private BufferedImage requestFace(UUID memberId) {
        if (faceLoading.putIfAbsent(memberId, Boolean.TRUE) != null) return null;
        plugin.scheduler().runAsync(() -> {
            try {
                BufferedImage face = fetchFace(memberId);
                if (face != null) faces.put(memberId, face);
            } catch (Throwable ignored) {
            } finally {
                faceLoading.remove(memberId);
            }
        });
        return null;
    }

    /** Downloads the member's skin and crops the face (base + hat overlay). */
    private BufferedImage fetchFace(UUID memberId) throws Exception {
        Player player = Bukkit.getPlayer(memberId);
        if (player == null) return null;
        java.net.URL skin = player.getPlayerProfile().getTextures().getSkin();
        if (skin == null) return null;
        HttpRequest request = HttpRequest.newBuilder(URI.create(skin.toString()))
                .timeout(Duration.ofSeconds(8)).GET().build();
        HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) return null;
        BufferedImage skinImage;
        try (InputStream in = response.body()) {
            skinImage = ImageIO.read(in);
        }
        if (skinImage == null) return null;
        BufferedImage face = new BufferedImage(FACE, FACE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = face.createGraphics();
        g.drawImage(skinImage.getSubimage(8, 8, FACE, FACE), 0, 0, null);      // base face
        g.drawImage(skinImage.getSubimage(40, 8, FACE, FACE), 0, 0, null);     // hat overlay
        g.dispose();
        BufferedImage scaled = new BufferedImage(CURSOR, CURSOR, BufferedImage.TYPE_INT_ARGB);
        Graphics2D s = scaled.createGraphics();
        s.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        s.drawImage(face, 0, 0, CURSOR, CURSOR, null);
        s.dispose();
        return scaled;
    }

    private static BufferedImage selfIcon() {
        BufferedImage icon = new BufferedImage(CURSOR, CURSOR, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = icon.createGraphics();
        g.setColor(java.awt.Color.WHITE);
        int[] xs = {CURSOR / 2, CURSOR - 1, CURSOR / 2, 0};
        int[] ys = {0, CURSOR / 2, CURSOR - 1, CURSOR / 2};
        g.fillPolygon(xs, ys, 4);
        g.setColor(java.awt.Color.DARK_GRAY);
        g.drawPolygon(xs, ys, 4);
        g.dispose();
        return icon;
    }

    private static BufferedImage pingIcon() {
        BufferedImage icon = new BufferedImage(CURSOR, CURSOR, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = icon.createGraphics();
        g.setColor(new java.awt.Color(220, 60, 70));
        g.fillRect(CURSOR / 2 - 1, 1, 2, CURSOR - 2);
        g.fillPolygon(new int[]{CURSOR / 2, CURSOR - 1, CURSOR / 2}, new int[]{1, 5, 5}, 3);
        g.dispose();
        return icon;
    }
}
