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
        // Hide if the user only wants the Side Panel
        if (config.displayMode() == ZealgainsConfig.DisplayMode.SIDE_PANEL)
        {
            return null;
        }

        // Hide if outside of Soul Wars and the config option is enabled
        if (config.hideOutsideSoulWars() && !plugin.isInSoulWarsGame())
        {
            return null;
        }

        panelComponent.getChildren().clear();

        panelComponent.getChildren().add(TitleComponent.builder()
                .text("Zealgains")
                .color(Color.WHITE)
                .build());

        Map<Integer, String> rKills = plugin.getRedKills();
        Map<Integer, String> bKills = plugin.getBlueKills();

        panelComponent.getChildren().add(LineComponent.builder().left("Red Team").leftColor(Color.RED).build());
        for (int i = 1; i <= 5; i++)
        {
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Kill " + i)
                    .right(rKills.getOrDefault(i, "-"))
                    .build());
        }

        panelComponent.getChildren().add(LineComponent.builder().left("Blue Team").leftColor(Color.CYAN).build());
        for (int i = 1; i <= 5; i++)
        {
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Kill " + i)
                    .right(bKills.getOrDefault(i, "-"))
                    .build());
        }

        return super.render(graphics);
    }
}