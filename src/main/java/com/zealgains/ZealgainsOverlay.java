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

    private Color tint(Color c)
    {
        int pct = config.overlayOpacity();
        if (pct >= 100) return c;
        int alpha = Math.max(0, (int)(c.getAlpha() * pct / 100.0));
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (config.displayMode() == ZealgainsConfig.DisplayMode.SIDE_PANEL
                || config.displayMode() == ZealgainsConfig.DisplayMode.NONE) return null;
        if (config.hideOutsideSoulWars() && !plugin.isInSoulWarsGame()) return null;

        panelComponent.setBackgroundColor(tint(config.overlayBackgroundColor()));
        panelComponent.getChildren().clear();

        panelComponent.getChildren().add(TitleComponent.builder()
                .text("Zealgains")
                .color(tint(Color.WHITE))
                .build());

        int timeRemaining = plugin.getGameTimeRemaining();

        // Live timer and score
        if (config.showGameStatus() && timeRemaining != -1)
        {
            int mins = timeRemaining / 60;
            int secs = timeRemaining % 60;
            panelComponent.getChildren().add(LineComponent.builder()
                    .left(String.format("%d:%02d", mins, secs))
                    .leftColor(tint(config.overlayTimerColor()))
                    .right("R: " + plugin.getRedScore() + "  B: " + plugin.getBlueScore())
                    .rightColor(tint(config.overlayScoreColor()))
                    .build());
        }

        int lobbyCount = plugin.getLobbyPlayerCount();
        if (lobbyCount > 0)
        {
            if (timeRemaining != -1)
            {
                // Always show frozen player count during a game
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Players: " + lobbyCount)
                        .leftColor(tint(config.overlayLobbyCountColor()))
                        .build());
            }
            else if (config.showLobbyCount())
            {
                // Live lobby count is opt-in via Developer Options
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Lobby: " + lobbyCount)
                        .leftColor(tint(config.overlayLobbyCountColor()))
                        .build());
            }
        }

        // Fragment count — only shown during a game when the dump fragment gate is relevant
        if (config.showFragCount() && timeRemaining != -1)
        {
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Frags: " + plugin.getFragCount())
                    .leftColor(tint(config.overlayLobbyCountColor()))
                    .build());
        }

        Map<Integer, String> rKills = plugin.getRedKills();
        Map<Integer, String> bKills = plugin.getBlueKills();

        // Red team calls — R5 hidden if B5 has been claimed (mutually exclusive)
        panelComponent.getChildren().add(LineComponent.builder().left("Red Team").leftColor(tint(config.overlayRedColor())).build());
        for (int i = 1; i <= 5; i++)
        {
            if (i == 5 && bKills.containsKey(5)) continue;
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Call " + i)
                    .leftColor(tint(config.overlayCallLabelColor()))
                    .right(rKills.getOrDefault(i, "-"))
                    .rightColor(tint(config.overlayCallNameColor()))
                    .build());
        }

        // Blue team calls
        boolean inDumpPhase = (timeRemaining != -1 && timeRemaining <= 720) || config.enableCallsOutsideGame();
        boolean b5Visible = inDumpPhase && !rKills.containsKey(5);
        panelComponent.getChildren().add(LineComponent.builder().left("Blue Team").leftColor(tint(config.overlayBlueColor())).build());
        for (int i = 1; i <= 5; i++)
        {
            if (i == 5 && !b5Visible) continue;
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Call " + i)
                    .leftColor(tint(config.overlayCallLabelColor()))
                    .right(bKills.getOrDefault(i, "-"))
                    .rightColor(tint(config.overlayCallNameColor()))
                    .build());
        }

        // Runners — only shown when at least one runner has signed up
        Set<String> rRunners = plugin.getRedRunners();
        Set<String> bRunners = plugin.getBlueRunners();
        if (!rRunners.isEmpty() || !bRunners.isEmpty())
        {
            panelComponent.getChildren().add(LineComponent.builder().left("Runners").leftColor(tint(config.overlayRunnersColor())).build());
            if (!rRunners.isEmpty())
            {
                panelComponent.getChildren().add(LineComponent.builder()
                        .left(String.join(", ", rRunners))
                        .leftColor(tint(config.overlayRedColor()))
                        .build());
            }
            if (!bRunners.isEmpty())
            {
                panelComponent.getChildren().add(LineComponent.builder()
                        .left(String.join(", ", bRunners))
                        .leftColor(tint(config.overlayBlueColor()))
                        .build());
            }
        }

        return super.render(graphics);
    }
}
