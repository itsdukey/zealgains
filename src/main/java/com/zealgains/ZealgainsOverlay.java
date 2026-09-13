package com.zealgains;

import net.runelite.client.config.ConfigManager;
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
    // them touching edge-to-edge. Fixed in pixels — does NOT scale with font size, which the
    // drag-to-resize ratio math in render() has to account for explicitly (see there).
    private static final int CONTENT_PADDING = 24;

    // How often an in-progress Alt-drag is allowed to write to the config store. Every render()
    // frame where the dragged width differs from last frame would otherwise call
    // configManager.setConfiguration() dozens of times a second while the user drags — each call
    // synchronously posts a ConfigChanged event to every plugin's subscribers, which can visibly
    // stutter the client. The live-scale preview below still updates every frame regardless (so
    // dragging still feels instant); only the persisted config write is throttled.
    private static final long CONFIG_WRITE_THROTTLE_MS = 150;

    private final ZealgainsPlugin plugin;
    private final ZealgainsConfig config;
    private final ConfigManager configManager;

    // Widest line measured so far this frame (left + right text width, at the current — possibly
    // Overlay Font Size %-scaled — font). Reset at the top of render() and used at the end to
    // size the panel to fit, so the overlay's border shrinks and grows with both the font size
    // and whatever content is actually showing, instead of sitting at a fixed default width.
    private int widestContent;

    // The exact pixel width panelComponent was set to at the end of the previous frame — i.e.
    // what RuneLite's Alt-drag resize handles were actually showing on screen just now. Comparing
    // this against the Overlay-level preferredSize at the top of the next render() call is how a
    // live drag is told apart from a stale/no-op value (see the render() comment below).
    private int lastAppliedWidth = 0;

    // The font scale currently being previewed mid-drag, before (or between) config writes; -1
    // means no drag is in progress and config.overlayFontScale() is authoritative. Needed because
    // config writes are throttled (see CONFIG_WRITE_THROTTLE_MS) but rendering still has to track
    // the drag every single frame, and because each new frame's ratio must be computed against
    // the scale actually being previewed, not a stale config value from before the drag started.
    private int liveFontScale = -1;
    private long lastConfigWriteMs = 0;

    @Inject
    private ZealgainsOverlay(ZealgainsPlugin plugin, ZealgainsConfig config, ConfigManager configManager)
    {
        super(plugin);
        this.plugin = plugin;
        this.config = config;
        this.configManager = configManager;
        setPosition(OverlayPosition.TOP_LEFT);
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

        // Drag-to-resize: RuneLite's Alt-drag overlay-editing hotkey resizes this overlay by
        // writing to a separate Overlay-level preferredSize field (distinct from panelComponent's
        // own — see getPreferredSize()/setPreferredSize() here, both inherited from Overlay, not
        // the panelComponent.setPreferredSize() calls elsewhere in this file). Left alone, that
        // field would fight the content-fit sizing below every frame. Instead of ignoring it (or
        // letting it override us), a change in it is treated as "the user just dragged to width
        // X" and converted into an equivalent Overlay Size % — scaled proportionally from
        // whatever width this panel last rendered at — so a drag simply re-derives the config
        // slider instead of pinning a fixed pixel size that would immediately conflict with the
        // auto-fit logic on the very next frame. Only width matters here: this overlay's height
        // is always a pure function of line count and font size (PanelComponent hands every child
        // a forced height of 0 regardless of preferredSize.height — confirmed by decompiling the
        // client jar), so a vertical-only drag has nothing to attach to and is a no-op by design,
        // not a bug — the font (and therefore height) only ever changes via the width ratio below.
        Dimension dragged = getPreferredSize();
        if (dragged != null)
        {
            // CONTENT_PADDING is a fixed pixel amount that doesn't grow or shrink with the font,
            // so it has to be subtracted out before ratio-ing the two widths — otherwise a short
            // line of text (where the padding is a large share of the total box width) would be
            // scaled by noticeably less than the drag actually asked for. What should scale
            // 1:1 with font size is the TEXT width alone (lastAppliedWidth/dragged.width minus
            // the padding), not the padded box width.
            int textWidthOld = lastAppliedWidth - CONTENT_PADDING;
            if (lastAppliedWidth > 0 && dragged.width != lastAppliedWidth && textWidthOld > 0)
            {
                int textWidthNew = Math.max(1, dragged.width - CONTENT_PADDING);
                int baseScale = liveFontScale > 0 ? liveFontScale : config.overlayFontScale();
                double ratio = (double) textWidthNew / textWidthOld;
                int newScale = (int) Math.round(baseScale * ratio);
                newScale = Math.max(ZealgainsConfig.OVERLAY_FONT_SCALE_MIN,
                        Math.min(ZealgainsConfig.OVERLAY_FONT_SCALE_MAX, newScale));
                // Preview every frame regardless of the throttle below, so dragging still feels
                // instant; ratios on subsequent frames are computed against this live value
                // rather than a config value that may not have been written yet.
                liveFontScale = newScale;

                long now = System.currentTimeMillis();
                if (now - lastConfigWriteMs >= CONFIG_WRITE_THROTTLE_MS)
                {
                    configManager.setConfiguration("zealgains", "overlayFontScale", newScale);
                    lastConfigWriteMs = now;
                }
            }
            // Consumed — clear it so OverlayPanel.render() (invoked via super.render() below)
            // never force-copies it back onto panelComponent, undoing the fit we compute below.
            setPreferredSize(null);
        }
        else if (liveFontScale > 0)
        {
            // The Overlay-level field is null again — one frame after the last real drag
            // movement, since we always clear it ourselves and RuneLite only re-sets it while a
            // drag is actually happening. Make sure the final previewed value gets persisted even
            // if it fell inside the last throttle window, then hand control back to the config
            // value for subsequent frames (e.g. if the user edits the slider directly instead).
            configManager.setConfiguration("zealgains", "overlayFontScale", liveFontScale);
            liveFontScale = -1;
        }

        // Overlay Font Size % scales whatever font RuneLite already has active on this
        // Graphics2D rather than assuming a fixed base point size — this respects the user's
        // own RuneLite font/DPI settings instead of fighting them. Title/LineComponent both fall
        // back to graphics.getFont() when no explicit font is set on them (never done here), so
        // setting it once up front covers the title, every call/runner line, and — since this
        // runs before the FontMetrics measurement below — the panel's own dynamic width.
        int fontScale = liveFontScale > 0 ? liveFontScale : config.overlayFontScale();
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
        lastAppliedWidth = widestContent + CONTENT_PADDING;
        panelComponent.setPreferredSize(new Dimension(lastAppliedWidth, 0));

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
