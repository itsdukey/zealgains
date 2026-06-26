package com.zealgains;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Notification;

import java.awt.Color;

@ConfigGroup("zealgains")
public interface ZealgainsConfig extends Config
{
	enum DisplayMode
	{
		SIDE_PANEL,
		OVERLAY,
		BOTH
	}

	@ConfigItem(
			keyName = "displayMode",
			name = "Display Mode",
			description = "Choose where to display the call tracker",
			position = 1
	)
	default DisplayMode displayMode()
	{
		return DisplayMode.BOTH;
	}

	@ConfigItem(
			keyName = "autoClear",
			name = "Auto-Clear on Game End",
			description = "Automatically clear the tracker when the Soul Wars game ends",
			position = 2
	)
	default boolean autoClear()
	{
		return true;
	}

	@ConfigItem(
			keyName = "hideOutsideSoulWars",
			name = "Hide Overlay Outside Game",
			description = "Only show the on-screen overlay when inside a Soul Wars game",
			position = 3
	)
	default boolean hideOutsideSoulWars()
	{
		return true;
	}

	@ConfigItem(
			keyName = "enableNotifications",
			name = "Rule Break Alerts",
			description = "Send a RuneLite notification (sound/banner) when someone breaks a call rule",
			position = 4
	)
	default Notification enableNotifications()
	{
		return Notification.ON;
	}

	@ConfigItem(
			keyName = "highlightOnFl",
			name = "Highlight if on FL",
			description = "Highlights players who are on your Friends List",
			position = 5
	)
	default boolean highlightOnFl()
	{
		return true;
	}

	@ConfigItem(
			keyName = "flHighlightColor",
			name = "FL Highlight Color",
			description = "Color to highlight players on your Friends List",
			position = 6
	)
	default Color flHighlightColor()
	{
		return Color.GREEN;
	}

	@ConfigItem(
			keyName = "pmCheckerHighlight",
			name = "PM Checker Highlight",
			description = "Highlights online friends in the Friends Chat",
			position = 7
	)
	default boolean pmCheckerHighlight()
	{
		return true;
	}

	@ConfigItem(
			keyName = "pmCheckerColor",
			name = "PM Highlight Color",
			description = "Color to highlight online friends in the Friends Chat",
			position = 8
	)
	default Color pmCheckerColor()
	{
		return Color.YELLOW;
	}

	@ConfigItem(
			keyName = "alertCrossWorld",
			name = "Alert Cross-World Calls",
			description = "Ignores and alerts you when someone makes a fake call from another world",
			position = 9
	)
	default boolean alertCrossWorld()
	{
		return true;
	}

	@ConfigItem(
			keyName = "enableBanList",
			name = "Enable Ban List Highlight",
			description = "Highlights players in the Friends Chat who are on a remote ban list",
			position = 10
	)
	default boolean enableBanList()
	{
		return true;
	}

	@ConfigItem(
			keyName = "banListUrl",
			name = "Ban List URL",
			description = "Raw URL to a plain text file containing banned names (one per line)",
			position = 11
	)
	default String banListUrl()
	{
		return "";
	}

	@ConfigItem(
			keyName = "banListColor",
			name = "Ban List Color",
			description = "Color to highlight banned players in the Friends Chat",
			position = 12
	)
	default Color banListColor()
	{
		return Color.RED;
	}

	@ConfigItem(
			keyName = "banListNotification",
			name = "Ban List Alerts",
			description = "Send a notification when a banned player joins the Friends Chat",
			position = 13
	)
	default Notification banListNotification()
	{
		return Notification.ON;
	}

	@ConfigItem(
			keyName = "dumpReminder",
			name = "5:00 Dump Reminder",
			description = "Alert when it's time to dump the winning kill (5:00 remaining, or 4:45 with 40+ members)",
			position = 14
	)
	default Notification dumpReminder()
	{
		return Notification.ON;
	}

	// ─────────────────────────────────────────────
	// PLUGIN GUIDE
	// ─────────────────────────────────────────────

	@ConfigSection(
			name = "Commands & Features Guide",
			description = "Click to expand — full reference for all commands, rules, and alerts",
			position = 15,
			closedByDefault = true
	)
	String guideSection = "guideSection";

	@ConfigItem(
			keyName = "guideCommands",
			name = "<html><body width='170'>"
					+ "<font color='#87CEEB'><b>── Commands ──</b></font><br>"
					+ "<font color='#A0A0A0'>"
					+ "<font color='#FFB347'>::zgreset</font><br>"
					+ "Clears all tracked calls and runners.<br><br>"
					+ "<font color='#FFB347'>::zgreset r or b ##</font><br>"
					+ "Resets specifically targeted calls. Remaining callers reshuffle into sequential slots and open slots are announced in chat. Supports multiple args: <font color='#FFB347'>::zgreset r2 b3, or :zgreset r34</font><br><br>"
					+ "<font color='#FFB347'>::zgsync</font><br>"
					+ "Force-refreshes the ban list immediately. 5-minute cooldown enforced.<br><br>"
					+ "<font color='#FFB347'>::zgdebug</font><br>"
					+ "Toggles debug mode. The next FC message received will print its raw character codes to chat."
					+ "</font></body></html>",
			description = "",
			position = 0,
			section = guideSection
	)
	default boolean guideCommands()
	{
		return false;
	}

	@ConfigItem(
			keyName = "guideFeatures",
			name = "<html><body width='170'>"
					+ "<font color='#87CEEB'><b>── Overlay ──</b></font><br>"
					+ "<font color='#A0A0A0'>"
					+ "R1-R5 and B1-B4 are always shown. B5 only appears after 12:00 and only if R5 has not been claimed. Runners appear at the bottom when signed up.<br><br>"
					+ "</font>"
					+ "<font color='#87CEEB'><b>── Call Rules ──</b></font><br>"
					+ "<font color='#A0A0A0'>"
					+ "Calls are done in order<br>"
					+ "Max 3 calls before 12:00.<br>"
					+ "Each player may only call for one team per game.<br>"
					+ "B5: valid only after 12:00 and if R5 not claimed. Same-tick tie: R5 wins.<br>"
					+ "</font>"
					+ "<font color='#87CEEB'><b>── Runner Callouts ──</b></font><br>"
					+ "<font color='#A0A0A0'>"
					+ "Red runner: ^r &nbsp;r^ &nbsp;&gt;r &nbsp;r&gt;<br>"
					+ "Blue runner: ^b &nbsp;b^ &nbsp;&gt;b &nbsp;b&gt;<br><br>"
					+ "</font>"
					+ "</font></body></html>",
			description = "",
			position = 1,
			section = guideSection
	)
	default boolean guideFeatures()
	{
		return false;
	}
}