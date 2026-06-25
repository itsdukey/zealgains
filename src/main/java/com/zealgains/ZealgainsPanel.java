package com.zealgains;

import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.Map;

public class ZealgainsPanel extends PluginPanel
{
    private final JPanel redContainer = new JPanel();
    private final JPanel blueContainer = new JPanel();

    public ZealgainsPanel(ZealgainsPlugin plugin)
    {
        super(false);
        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        JPanel layoutPanel = new JPanel();
        layoutPanel.setLayout(new BoxLayout(layoutPanel, BoxLayout.Y_AXIS));
        layoutPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        layoutPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        // Title
        JLabel title = new JLabel("Zealgains");
        title.setForeground(Color.WHITE);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        layoutPanel.add(title);
        layoutPanel.add(Box.createRigidArea(new Dimension(0, 15)));

        // Red Team Section
        redContainer.setLayout(new BoxLayout(redContainer, BoxLayout.Y_AXIS));
        redContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);
        layoutPanel.add(createTeamPanel("Red Team", redContainer, Color.RED));
        layoutPanel.add(Box.createRigidArea(new Dimension(0, 10)));

        // Blue Team Section
        blueContainer.setLayout(new BoxLayout(blueContainer, BoxLayout.Y_AXIS));
        blueContainer.setBackground(ColorScheme.DARK_GRAY_COLOR);
        layoutPanel.add(createTeamPanel("Blue Team", blueContainer, Color.CYAN));
        layoutPanel.add(Box.createRigidArea(new Dimension(0, 15)));

        // Reset Button
        JButton resetButton = new JButton("Reset Kills");
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

    // This method is called by our Plugin file every time a new kill is processed
    public void updateKills(Map<Integer, String> redKills, Map<Integer, String> blueKills)
    {
        SwingUtilities.invokeLater(() -> {
            redContainer.removeAll();
            blueContainer.removeAll();

            for (int i = 1; i <= 5; i++)
            {
                String rName = redKills.getOrDefault(i, "---");
                JLabel rLabel = new JLabel("Kill " + i + ": " + rName);
                rLabel.setForeground(Color.LIGHT_GRAY);
                redContainer.add(rLabel);

                String bName = blueKills.getOrDefault(i, "---");
                JLabel bLabel = new JLabel("Kill " + i + ": " + bName);
                bLabel.setForeground(Color.LIGHT_GRAY);
                blueContainer.add(bLabel);
            }

            revalidate();
            repaint();
        });
    }
}