package com.zealgains;

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;
import java.util.Map;

public class ZealgainsOverlay extends OverlayPanel
{
    private final ZealgainsPlugin plugin;
    private final ZealgainsConfig config;

    @Inject
    private ZealgainsOverlay(ZealgainsPlugin plugin, ZealgainsConfig config)
    {
        super(plugin);
        this.plugin = plugin;
        this.config = config;
        setPosition(OverlayPosition.TOP_LEFT);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (config.displayMode() == ZealgainsConfig.DisplayMode.SIDE_PANEL) return null;
        if (config.hideOutsideSoulWars() && !plugin.isInSoulWarsGame()) return null;

        panelComponent.getChildren().clear();

        panelComponent.getChildren().add(TitleComponent.builder()
                .text("Zealgains")
                .color(Color.WHITE)
                .build());

        Map<Integer, String> rKills = plugin.getRedKills();
        Map<Integer, String> bKills = plugin.getBlueKills();

        // Red team calls
        panelComponent.getChildren().add(LineComponent.builder().left("Red Team").leftColor(Color.RED).build());
        for (int i = 1; i <= 5; i++)
        {
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Call " + i)
                    .right(rKills.getOrDefault(i, "-"))
                    .build());
        }

        // Blue team calls
        panelComponent.getChildren().add(LineComponent.builder().left("Blue Team").leftColor(Color.CYAN).build());
        for (int i = 1; i <= 5; i++)
        {
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Call " + i)
                    .right(bKills.getOrDefault(i, "-"))
                    .build());
        }

        // Uncalled kills summary
        StringBuilder uncalled = new StringBuilder();
        for (int i = 1; i <= 5; i++) if (!rKills.containsKey(i)) uncalled.append("R").append(i).append(" ");
        for (int i = 1; i <= 4; i++) if (!bKills.containsKey(i)) uncalled.append("B").append(i).append(" ");
        String uncalledStr = uncalled.toString().trim();
        if (!uncalledStr.isEmpty())
        {
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Uncalled")
                    .right(uncalledStr)
                    .rightColor(Color.ORANGE)
                    .build());
        }

        // B5 availability — only shown after 12:00
        int timeRemaining = plugin.getGameTimeRemaining();
        if (timeRemaining != -1 && timeRemaining <= 720)
        {
            boolean b5Available = !rKills.containsKey(5) && !bKills.containsKey(5);
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("B5")
                    .right(b5Available ? "AVAILABLE" : "LOCKED")
                    .rightColor(b5Available ? Color.GREEN : Color.RED)
                    .build());
        }

        return super.render(graphics);
    }
}
