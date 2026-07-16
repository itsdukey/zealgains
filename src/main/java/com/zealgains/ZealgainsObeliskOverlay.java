package com.zealgains;

import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.Perspective;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

import javax.inject.Inject;
import java.awt.*;

public class ZealgainsObeliskOverlay extends Overlay
{
    private static final Color WARN_FILL   = new Color(255, 0, 0, 60);
    private static final Color WARN_BORDER = Color.RED;
    private static final Font  WARN_FONT   = new Font("Arial", Font.BOLD, 14);

    // World-coordinate bounding box of the Soul Obelisk tile square (SW 2205,2910 → NE 2208,2913).
    // Used as an absolute position lock so the polygon can never render anywhere else on the map.
    private static final int OBELISK_MIN_X = 2205;
    private static final int OBELISK_MAX_X = 2208;
    private static final int OBELISK_MIN_Y = 2910;
    private static final int OBELISK_MAX_Y = 2913;

    private final ZealgainsPlugin plugin;
    private final ZealgainsConfig config;
    private final Client client;

    @Inject
    ZealgainsObeliskOverlay(ZealgainsPlugin plugin, ZealgainsConfig config, Client client)
    {
        this.plugin = plugin;
        this.config = config;
        this.client = client;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!config.highlightObelisk() || !plugin.isObeliskWarnActive()) return null;
        if (config.dumpOverlayFilter() == ZealgainsConfig.DumpOverlayFilter.SMART_FILTER
                && !config.alwaysShowDumpOverlay() && !plugin.isLocalPlayerInGame()) return null;

        GameObject obelisk = plugin.getTrackedObelisk();
        if (obelisk == null) return null;

        LocalPoint lp = obelisk.getLocalLocation();
        if (lp == null) return null;

        // Absolute position lock: only render if the obelisk's template world coordinates fall
        // within the known Soul Obelisk tile square. fromLocalInstance handles instanced regions
        // by mapping scene coordinates back to the template region coordinates.
        WorldPoint wp = WorldPoint.fromLocalInstance(client, lp);
        if (wp.getX() < OBELISK_MIN_X || wp.getX() > OBELISK_MAX_X
                || wp.getY() < OBELISK_MIN_Y || wp.getY() > OBELISK_MAX_Y) return null;

        // Hard tile-distance cap: reject if the obelisk is more than 15 tiles away in scene
        // space. This prevents perspective projection from producing a valid-looking polygon at
        // a completely wrong canvas position when the obelisk is at the scene edge.
        LocalPoint playerLp = client.getLocalPlayer() != null
                ? client.getLocalPlayer().getLocalLocation() : null;
        if (playerLp == null) return null;
        int distTiles = Math.max(Math.abs(lp.getX() - playerLp.getX()),
                                 Math.abs(lp.getY() - playerLp.getY()))
                        / Perspective.LOCAL_TILE_SIZE;
        if (distTiles > 15) return null;

        // Secondary guard: localToCanvas returns null when the tile center is outside the view
        // frustum — catch any remaining off-screen case the distance check didn't eliminate.
        net.runelite.api.Point tileCenter = Perspective.localToCanvas(client, lp, client.getPlane());
        if (tileCenter == null) return null;

        // Use a 3-tile area poly to cover the large obelisk footprint
        Polygon poly = Perspective.getCanvasTileAreaPoly(client, lp, 3);
        if (poly == null) return null;

        // Validate polygon position: all vertices must be within 200px of the projected tile center.
        // A 3×3 tile area is at most ~150px across at any zoom level, so any vertex further away
        // than 200px means the polygon has wrapped to the wrong canvas position.
        for (int i = 0; i < poly.npoints; i++)
        {
            if (Math.abs(poly.xpoints[i] - tileCenter.getX()) > 200
                    || Math.abs(poly.ypoints[i] - tileCenter.getY()) > 200) return null;
        }

        // Red fill + border
        graphics.setColor(WARN_FILL);
        graphics.fillPolygon(poly);
        graphics.setStroke(new BasicStroke(2));
        graphics.setColor(WARN_BORDER);
        graphics.drawPolygon(poly);

        // "DO NOT DUMP" text centered over the obelisk, with black shadow for readability
        String text = "DO NOT DUMP";
        graphics.setFont(WARN_FONT);
        FontMetrics fm = graphics.getFontMetrics();
        int x = (int) poly.getBounds().getCenterX() - fm.stringWidth(text) / 2;
        int y = (int) poly.getBounds().getCenterY() + fm.getAscent() / 2;
        graphics.setColor(Color.BLACK);
        graphics.drawString(text, x + 1, y + 1);
        graphics.setColor(Color.RED);
        graphics.drawString(text, x, y);

        return null;
    }
}
