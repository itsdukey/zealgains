package com.zealgains;

import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.LinkBrowser;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Map;
import java.util.Set;

public class ZealgainsPanel extends PluginPanel
{
    private final ZealgainsPlugin plugin;
    private final ZealgainsConfig config;

    private final JPanel redContainer = new JPanel();
    private final JPanel blueContainer = new JPanel();
    private final JPanel runnerContainer = new JPanel();
    private final JPanel runnerWrapper;
    private final JPanel statusPanel = new JPanel();
    private final JLabel timerLabel = new JLabel();
    private final JLabel scoreLabel = new JLabel();
    private final JLabel playersLabel = new JLabel();
    private final JLabel redHeader = new JLabel("Red Team");
    private final JLabel blueHeader = new JLabel("Blue Team");
    private final JLabel runnerHeader = new JLabel("Runners");

    public ZealgainsPanel(ZealgainsPlugin plugin, ZealgainsConfig config)
    {
        super(false);
        this.plugin = plugin;
        this.config = config;

        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        JPanel layoutPanel = new JPanel();
        layoutPanel.setLayout(new BoxLayout(layoutPanel, BoxLayout.Y_AXIS));
        layoutPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        layoutPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JLabel title = new JLabel("Zealgains");
        title.setForeground(Color.WHITE);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        layoutPanel.add(title);
        layoutPanel.add(Box.createRigidArea(new Dimension(0, 6)));

        JLabel discordLink = new JLabel("discord.gg/riseabove");
        discordLink.setForeground(new Color(0x5865F2));
        discordLink.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        discordLink.setAlignmentX(Component.CENTER_ALIGNMENT);
        discordLink.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                LinkBrowser.browse("https://discord.gg/riseabove");
            }

            @Override
            public void mouseEntered(MouseEvent e)
            {
                discordLink.setText("<html><u>discord.gg/riseabove</u></html>");
            }

            @Override
            public void mouseExited(MouseEvent e)
            {
                discordLink.setText("discord.gg/riseabove");
            }
        });
        layoutPanel.add(discordLink);
        layoutPanel.add(Box.createRigidArea(new Dimension(0, 10)));

        // Timer, score, and player count
        statusPanel.setLayout(new BoxLayout(statusPanel, BoxLayout.Y_AXIS));
        statusPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        statusPanel.setVisible(false);

        timerLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        scoreLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        playersLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        statusPanel.add(timerLabel);
        statusPanel.add(scoreLabel);
        statusPanel.add(playersLabel);
        layoutPanel.add(statusPanel);
        layoutPanel.add(Box.createRigidArea(new Dimension(0, 10)));

        redContainer.setLayout(new BoxLayout(redContainer, BoxLayout.Y_AXIS));
        redContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);
        layoutPanel.add(createTeamSection(redHeader, redContainer));
        layoutPanel.add(Box.createRigidArea(new Dimension(0, 10)));

        blueContainer.setLayout(new BoxLayout(blueContainer, BoxLayout.Y_AXIS));
        blueContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);
        layoutPanel.add(createTeamSection(blueHeader, blueContainer));
        layoutPanel.add(Box.createRigidArea(new Dimension(0, 10)));

        runnerContainer.setLayout(new BoxLayout(runnerContainer, BoxLayout.Y_AXIS));
        runnerContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);
        runnerWrapper = createTeamSection(runnerHeader, runnerContainer);
        runnerWrapper.setVisible(false);
        layoutPanel.add(runnerWrapper);
        layoutPanel.add(Box.createRigidArea(new Dimension(0, 15)));

        JButton resetButton = new JButton("Reset Calls");
        resetButton.addActionListener(e ->
        {
            int result = JOptionPane.showConfirmDialog(
                this,
                "Clear all tracked calls and runners?",
                "Reset Calls",
                JOptionPane.YES_NO_OPTION
            );
            if (result == JOptionPane.YES_OPTION)
            {
                plugin.resetKills();
            }
        });
        resetButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        layoutPanel.add(resetButton);

        add(layoutPanel, BorderLayout.NORTH);
    }

    private JPanel createTeamSection(JLabel header, JPanel container)
    {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
        header.setBorder(new EmptyBorder(0, 0, 5, 0));
        wrapper.add(header, BorderLayout.NORTH);
        wrapper.add(container, BorderLayout.CENTER);
        return wrapper;
    }

    public void updateKills(Map<Integer, String> redKills, Map<Integer, String> blueKills)
    {
        int timeRemaining = plugin.getGameTimeRemaining(); // must be captured on the client thread
        SwingUtilities.invokeLater(() ->
        {
            redHeader.setForeground(config.overlayRedColor());
            blueHeader.setForeground(config.overlayBlueColor());

            redContainer.removeAll();
            blueContainer.removeAll();

            boolean inDumpPhase = (timeRemaining != -1 && timeRemaining <= 720) || config.enableCallsOutsideGame();
            boolean b5Visible = inDumpPhase && !redKills.containsKey(5);

            // R5 hidden when B5 is claimed
            for (int i = 1; i <= 5; i++)
            {
                if (i == 5 && blueKills.containsKey(5)) continue;
                JLabel label = new JLabel("Call " + i + ": " + redKills.getOrDefault(i, "-"));
                label.setForeground(config.overlayCallNameColor());
                redContainer.add(label);
            }

            // B5 only visible in dump phase when R5 is unclaimed
            for (int i = 1; i <= 5; i++)
            {
                if (i == 5 && !b5Visible) continue;
                JLabel label = new JLabel("Call " + i + ": " + blueKills.getOrDefault(i, "-"));
                label.setForeground(config.overlayCallNameColor());
                blueContainer.add(label);
            }

            revalidate();
            repaint();
        });
    }

    public void updateGameStatus(int seconds, int redScore, int blueScore)
    {
        int timeRemaining = plugin.getGameTimeRemaining(); // must be captured on the client thread
        int lobbyCount = plugin.getLobbyPlayerCount();
        SwingUtilities.invokeLater(() ->
        {
            boolean inGame = timeRemaining != -1;
            boolean showStatus = seconds >= 0;

            timerLabel.setForeground(config.overlayTimerColor());
            scoreLabel.setForeground(config.overlayScoreColor());
            playersLabel.setForeground(config.overlayLobbyCountColor());

            timerLabel.setVisible(showStatus && inGame);
            scoreLabel.setVisible(showStatus && inGame);

            if (showStatus && inGame)
            {
                timerLabel.setText(String.format("%d:%02d", seconds / 60, seconds % 60));
                scoreLabel.setText("Red: " + redScore + "  /  Blue: " + blueScore);
            }

            // Players: ## always shown during game; Lobby: ## opt-in outside game
            if (inGame && lobbyCount > 0)
            {
                playersLabel.setText("Players: " + lobbyCount);
                playersLabel.setVisible(true);
            }
            else if (!inGame && lobbyCount > 0 && config.showLobbyCount())
            {
                playersLabel.setText("Lobby: " + lobbyCount);
                playersLabel.setVisible(true);
            }
            else
            {
                playersLabel.setVisible(false);
            }

            statusPanel.setVisible(timerLabel.isVisible() || scoreLabel.isVisible() || playersLabel.isVisible());

            revalidate();
            repaint();
        });
    }

    public void updateRunners(Set<String> redRunners, Set<String> blueRunners)
    {
        SwingUtilities.invokeLater(() ->
        {
            runnerHeader.setForeground(config.overlayRunnersColor());
            runnerContainer.removeAll();

            boolean hasRunners = !redRunners.isEmpty() || !blueRunners.isEmpty();
            runnerWrapper.setVisible(hasRunners);

            if (!redRunners.isEmpty())
            {
                JLabel label = new JLabel("Red: " + String.join(", ", redRunners));
                label.setForeground(config.overlayRedColor());
                runnerContainer.add(label);
            }
            if (!blueRunners.isEmpty())
            {
                JLabel label = new JLabel("Blue: " + String.join(", ", blueRunners));
                label.setForeground(config.overlayBlueColor());
                runnerContainer.add(label);
            }

            revalidate();
            repaint();
        });
    }
}
