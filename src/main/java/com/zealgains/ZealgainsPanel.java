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
    private final JPanel redContainer = new JPanel();
    private final JPanel blueContainer = new JPanel();
    private final JPanel runnerContainer = new JPanel();

    public ZealgainsPanel(ZealgainsPlugin plugin)
    {
        super(false);
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
        layoutPanel.add(Box.createRigidArea(new Dimension(0, 15)));

        redContainer.setLayout(new BoxLayout(redContainer, BoxLayout.Y_AXIS));
        redContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);
        layoutPanel.add(createTeamPanel("Red Team", redContainer, Color.RED));
        layoutPanel.add(Box.createRigidArea(new Dimension(0, 10)));

        blueContainer.setLayout(new BoxLayout(blueContainer, BoxLayout.Y_AXIS));
        blueContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);
        layoutPanel.add(createTeamPanel("Blue Team", blueContainer, Color.CYAN));
        layoutPanel.add(Box.createRigidArea(new Dimension(0, 10)));

        runnerContainer.setLayout(new BoxLayout(runnerContainer, BoxLayout.Y_AXIS));
        runnerContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);
        layoutPanel.add(createTeamPanel("Runners", runnerContainer, Color.ORANGE));
        layoutPanel.add(Box.createRigidArea(new Dimension(0, 15)));

        JButton resetButton = new JButton("Reset Calls");
        resetButton.addActionListener(e -> plugin.resetKills());
        resetButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        layoutPanel.add(resetButton);

        add(layoutPanel, BorderLayout.NORTH);
    }

    private JPanel createTeamPanel(String title, JPanel container, Color color)
    {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JLabel header = new JLabel(title);
        header.setForeground(color);
        header.setBorder(new EmptyBorder(0, 0, 5, 0));

        wrapper.add(header, BorderLayout.NORTH);
        wrapper.add(container, BorderLayout.CENTER);
        return wrapper;
    }

    public void updateKills(Map<Integer, String> redKills, Map<Integer, String> blueKills)
    {
        SwingUtilities.invokeLater(() -> {
            redContainer.removeAll();
            blueContainer.removeAll();

            for (int i = 1; i <= 5; i++)
            {
                String rName = redKills.getOrDefault(i, "---");
                JLabel rLabel = new JLabel("Call " + i + ": " + rName);
                rLabel.setForeground(Color.LIGHT_GRAY);
                redContainer.add(rLabel);

                if (i <= 4 || blueKills.containsKey(5))
                {
                    String bName = blueKills.getOrDefault(i, "---");
                    JLabel bLabel = new JLabel("Call " + i + ": " + bName);
                    bLabel.setForeground(Color.LIGHT_GRAY);
                    blueContainer.add(bLabel);
                }
            }

            revalidate();
            repaint();
        });
    }

    public void updateRunners(Set<String> redRunners, Set<String> blueRunners)
    {
        SwingUtilities.invokeLater(() -> {
            runnerContainer.removeAll();

            if (redRunners.isEmpty() && blueRunners.isEmpty())
            {
                JLabel none = new JLabel("None");
                none.setForeground(Color.LIGHT_GRAY);
                runnerContainer.add(none);
            }
            else
            {
                if (!redRunners.isEmpty())
                {
                    JLabel label = new JLabel("Red: " + String.join(", ", redRunners));
                    label.setForeground(Color.RED);
                    runnerContainer.add(label);
                }
                if (!blueRunners.isEmpty())
                {
                    JLabel label = new JLabel("Blue: " + String.join(", ", blueRunners));
                    label.setForeground(Color.CYAN);
                    runnerContainer.add(label);
                }
            }

            revalidate();
            repaint();
        });
    }
}
