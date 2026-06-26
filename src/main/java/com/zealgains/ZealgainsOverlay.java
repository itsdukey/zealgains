package com.zealgains;

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;
import java.util.Map;
import java.util.Set;

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
        int timeRemaining = plugin.getGameTimeRemaining();
        boolean b5Visible = timeRemaining != -1 && timeRemaining <= 720 && !rKills.containsKey(5);
        panelComponent.getChildren().add(LineComponent.builder().left("Blue Team").leftColor(Color.CYAN).build());
        for (int i = 1; i <= 5; i++)
        {
            if (i == 5 && !b5Visible) continue;
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Call " + i)
                    .right(bKills.getOrDefault(i, "-"))
                    .build());
        }

        // Runners — only shown when at least one runner has signed up
        Set<String> rRunners = plugin.getRedRunners();
        Set<String> bRunners = plugin.getBlueRunners();
        if (!rRunners.isEmpty() || !bRunners.isEmpty())
        {
            panelComponent.getChildren().add(LineComponent.builder().left("Runners").leftColor(Color.ORANGE).build());
            if (!rRunners.isEmpty())
            {
                panelComponent.getChildren().add(LineComponent.builder()
                        .left(String.join(", ", rRunners))
                        .leftColor(Color.RED)
                        .build());
            }
            if (!bRunners.isEmpty())
            {
                panelComponent.getChildren().add(LineComponent.builder()
                        .left(String.join(", ", bRunners))
                        .leftColor(Color.CYAN)
                        .build());
            }
        }

        return super.render(graphics);
    }
}
