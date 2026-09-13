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
    // Added on top of the widest line's raw measured text width when sizing the panel each
    // frame: covers PanelComponent's own 4px-per-side border and leaves a little visual gap
    // between a line's left and right text (e.g. Compact Overlay's Red/Blue columns) instead of
    // them touching edge-to-edge.
    private static final int CONTENT_PADDING = 24;

    private final ZealgainsPlugin plugin;
    private final ZealgainsConfig config;

    // Widest line measured so far this frame (left + right text width, at the current — possibly
    // Overlay Font Size %-scaled — font). Reset at the top of render() and used at the end to
    // size the panel to fit, so the overlay's border shrinks and grows with both the font size
    // and whatever content is actually showing, instead of sitting at a fixed default width.
    private int widestContent;

    @Inject
    private ZealgainsOverlay(ZealgainsPlugin plugin, ZealgainsConfig config)
    {
        super(plugin);
        this.plugin = plugin;
        this.config = config;
        setPosition(OverlayPosition.TOP_LEFT);
        // OverlayPanel defaults every overlay to user-resizable (RuneLite's own Alt-drag corner
        // handles), which persists a separate Overlay-level preferredSize that OverlayPanel.render()
        // force-copies onto panelComponent every frame, silently undoing whatever width we compute
        // below. Since the panel now auto-fits its own content, manual resizing would only fight
        // that — turned off here, and also actively cleared in render() in case a size was already
        // stored from before this was disabled (that stale value keeps reapplying otherwise).
        setResizable(false);
    }

    private Color tint(Color c)
    {
        int pct = config.overlayOpacity();
        if (pct >= 100) return c;
        int alpha = Math.max(0, (int)(c.getAlpha() * pct / 100.0));
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
    }

    // Measures `left` (+ `right` when present) at the given FontMetrics and folds it into this
    // frame's running widest-line tally. Call once per line/title actually added to the panel —
    // text that never gets added should never influence the panel's width.
    private void trackWidth(FontMetrics fm, String left, String right)
    {
        int width = fm.stringWidth(left);
        if (right != null && !right.isEmpty())
        {
            width += fm.stringWidth(right);
        }
        widestContent = Math.max(widestContent, width);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!config.enableFragging()) return null;
        if (config.displayMode() == ZealgainsConfig.DisplayMode.SIDE_PANEL
                || config.displayMode() == ZealgainsConfig.DisplayMode.NONE) return null;
        if (config.hideOutsideSoulWars() && !plugin.isInSoulWarsGame()) return null;

        // Overlay Font Size % scales whatever font RuneLite already has active on this
        // Graphics2D rather than assuming a fixed base point size — this respects the user's
        // own RuneLite font/DPI settings instead of fighting them. Title/LineComponent both fall
        // back to graphics.getFont() when no explicit font is set on them (never done here), so
        // setting it once up front covers the title, every call/runner line, and — since this
        // runs before the FontMetrics measurement below — the panel's own dynamic width.
        int fontScale = config.overlayFontScale();
        if (fontScale != 100)
        {
            Font base = graphics.getFont();
            graphics.setFont(base.deriveFont(base.getSize2D() * fontScale / 100f));
        }

        FontMetrics fm = graphics.getFontMetrics();
        widestContent = 0;

        panelComponent.setBackgroundColor(tint(config.overlayBackgroundColor()));
        panelComponent.getChildren().clear();

        String title = "Zealgains";
        trackWidth(fm, title, null);
        panelComponent.getChildren().add(TitleComponent.builder()
                .text(title)
                .color(tint(Color.WHITE))
                .build());

        int timeRemaining = plugin.getGameTimeRemaining();

        // Live timer and score
        if (config.showGameStatus() && timeRemaining != -1)
        {
            int mins = timeRemaining / 60;
            int secs = timeRemaining % 60;
            String left = String.format("%d:%02d", mins, secs);
            String right = "R: " + plugin.getRedScore() + "  B: " + plugin.getBlueScore();
            trackWidth(fm, left, right);
            panelComponent.getChildren().add(LineComponent.builder()
                    .left(left)
                    .leftColor(tint(config.overlayTimerColor()))
                    .right(right)
                    .rightColor(tint(config.overlayScoreColor()))
                    .build());
        }

        int lobbyCount = plugin.getLobbyPlayerCount();
        if (lobbyCount > 0)
        {
            if (timeRemaining != -1)
            {
                // Always show frozen player count during a game
                String left = "Players: " + lobbyCount;
                trackWidth(fm, left, null);
                panelComponent.getChildren().add(LineComponent.builder()
                        .left(left)
                        .leftColor(tint(config.overlayLobbyCountColor()))
                        .build());
            }
            else if (config.showLobbyCount())
            {
                // Live lobby count is opt-in via Developer Options
                String left = "Lobby: " + lobbyCount;
                trackWidth(fm, left, null);
                panelComponent.getChildren().add(LineComponent.builder()
                        .left(left)
                        .leftColor(tint(config.overlayLobbyCountColor()))
                        .build());
            }
        }

        // Fragment count — only shown during a game when the dump fragment gate is relevant
        if (config.showFragCount() && timeRemaining != -1)
        {
            String left = "Frags: " + plugin.getFragCount();
            trackWidth(fm, left, null);
            panelComponent.getChildren().add(LineComponent.builder()
                    .left(left)
                    .leftColor(tint(config.overlayLobbyCountColor()))
                    .build());
        }

        Map<Integer, String> rKills = plugin.getRedKills();
        Map<Integer, String> bKills = plugin.getBlueKills();
        Set<String> rRunners = plugin.getRedRunners();
        Set<String> bRunners = plugin.getBlueRunners();

        boolean inDumpPhase = (timeRemaining != -1 && timeRemaining <= 720) || config.enableCallsOutsideGame();
        boolean b5Visible = inDumpPhase && !rKills.containsKey(5);

        if (config.compactOverlay())
        {
            renderCompactCalls(fm, rKills, bKills, b5Visible);
            renderCompactRunners(fm, rRunners, bRunners);
        }
        else
        {
            renderNormalCalls(fm, rKills, bKills, b5Visible);
            renderNormalRunners(fm, rRunners, bRunners);
        }

        // Size the panel to exactly fit this frame's content at the current font — the border
        // shrinks and grows with both Overlay Font Size % and whichever lines are actually
        // showing, instead of sitting at RuneLite's fixed default width regardless of content.
        panelComponent.setPreferredSize(new Dimension(widestContent + CONTENT_PADDING, 0));

        // Clear any stored resizable-overlay size (see the constructor comment) so
        // OverlayPanel.render() — called via super.render() below — never overwrites the width
        // we just computed with a stale persisted value.
        setPreferredSize(null);

        return super.render(graphics);
    }

    // Two stacked team lists — Red Team header + 5 rows, Blue Team header + up to 5 rows.
    // R5 hidden if B5 has been claimed (mutually exclusive); B5 only shown once b5Visible.
    private void renderNormalCalls(FontMetrics fm, Map<Integer, String> rKills, Map<Integer, String> bKills, boolean b5Visible)
    {
        trackWidth(fm, "Red Team", null);
        panelComponent.getChildren().add(LineComponent.builder().left("Red Team").leftColor(tint(config.overlayRedColor())).build());
        for (int i = 1; i <= 5; i++)
        {
            if (i == 5 && bKills.containsKey(5)) continue;
            String left = "Call " + i;
            String right = rKills.getOrDefault(i, "-");
            trackWidth(fm, left, right);
            panelComponent.getChildren().add(LineComponent.builder()
                    .left(left)
                    .leftColor(tint(config.overlayCallLabelColor()))
                    .right(right)
                    .rightColor(tint(config.overlayCallNameColor()))
                    .build());
        }

        trackWidth(fm, "Blue Team", null);
        panelComponent.getChildren().add(LineComponent.builder().left("Blue Team").leftColor(tint(config.overlayBlueColor())).build());
        for (int i = 1; i <= 5; i++)
        {
            if (i == 5 && !b5Visible) continue;
            String left = "Call " + i;
            String right = bKills.getOrDefault(i, "-");
            trackWidth(fm, left, right);
            panelComponent.getChildren().add(LineComponent.builder()
                    .left(left)
                    .leftColor(tint(config.overlayCallLabelColor()))
                    .right(right)
                    .rightColor(tint(config.overlayCallNameColor()))
                    .build());
        }
    }

    // Runners — only shown when at least one runner has signed up
    private void renderNormalRunners(FontMetrics fm, Set<String> rRunners, Set<String> bRunners)
    {
        if (rRunners.isEmpty() && bRunners.isEmpty()) return;

        trackWidth(fm, "Runners", null);
        panelComponent.getChildren().add(LineComponent.builder().left("Runners").leftColor(tint(config.overlayRunnersColor())).build());
        if (!rRunners.isEmpty())
        {
            String left = String.join(", ", rRunners);
            trackWidth(fm, left, null);
            panelComponent.getChildren().add(LineComponent.builder()
                    .left(left)
                    .leftColor(tint(config.overlayRedColor()))
                    .build());
        }
        if (!bRunners.isEmpty())
        {
            String left = String.join(", ", bRunners);
            trackWidth(fm, left, null);
            panelComponent.getChildren().add(LineComponent.builder()
                    .left(left)
                    .leftColor(tint(config.overlayBlueColor()))
                    .build());
        }
    }

    // One row per slot number, Red on the left / Blue on the right. A row is skipped entirely
    // once neither team has claimed that slot — open slots past the last claim are not shown.
    //
    // A single small "Red | Blue" header (team-colored, printed once) is enough to establish
    // which column is which team — every row after it stays in the neutral name color, same as
    // Normal mode's "Call i" / name split, instead of tinting the whole row (number + name
    // together) in a fully-saturated team color. Full-saturation red/cyan text reads fine as a
    // one-word section header but is genuinely hard to read as body text at overlay font sizes,
    // and doubling as both "which team" and "what's their name" in one color read as unfinished
    // next to Normal mode's cleaner label/name split — this matches that split instead.
    private void renderCompactCalls(FontMetrics fm, Map<Integer, String> rKills, Map<Integer, String> bKills, boolean b5Visible)
    {
        String[] lefts = new String[5];
        String[] rights = new String[5];
        int rowCount = 0;

        for (int i = 1; i <= 5; i++)
        {
            boolean rApplicable = !(i == 5 && bKills.containsKey(5));
            boolean bApplicable = i != 5 || b5Visible;

            boolean rClaimed = rApplicable && rKills.containsKey(i);
            boolean bClaimed = bApplicable && bKills.containsKey(i);
            if (!rClaimed && !bClaimed) continue;

            // Slot number always leads the left column (a shared row label, like Normal mode's
            // "Call i") so the row is identifiable even when only Blue claimed it; the team is
            // conveyed by column position under the header below, not by tinting the name itself.
            String left = i + (rClaimed ? "  " + rKills.get(i) : "");
            String right = bClaimed ? bKills.get(i) : "";
            lefts[rowCount] = left;
            rights[rowCount] = right;
            rowCount++;

            trackWidth(fm, left, right);
        }

        if (rowCount == 0) return;

        trackWidth(fm, "Red", "Blue");
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Red")
                .leftColor(tint(config.overlayRedColor()))
                .right("Blue")
                .rightColor(tint(config.overlayBlueColor()))
                .build());

        for (int i = 0; i < rowCount; i++)
        {
            panelComponent.getChildren().add(LineComponent.builder()
                    .left(lefts[i])
                    .leftColor(tint(config.overlayCallNameColor()))
                    .right(rights[i])
                    .rightColor(tint(config.overlayCallNameColor()))
                    .build());
        }
    }

    // Runners collapsed onto a single "Runners: Name (R), Name (B)" line
    private void renderCompactRunners(FontMetrics fm, Set<String> rRunners, Set<String> bRunners)
    {
        if (rRunners.isEmpty() && bRunners.isEmpty()) return;

        StringBuilder sb = new StringBuilder("Runners: ");
        boolean first = true;
        for (String name : rRunners)
        {
            if (!first) sb.append(", ");
            sb.append(name).append(" (R)");
            first = false;
        }
        for (String name : bRunners)
        {
            if (!first) sb.append(", ");
            sb.append(name).append(" (B)");
            first = false;
        }

        String left = sb.toString();
        trackWidth(fm, left, null);
        panelComponent.getChildren().add(LineComponent.builder()
                .left(left)
                .leftColor(tint(config.overlayRunnersColor()))
                .build());
    }
}
