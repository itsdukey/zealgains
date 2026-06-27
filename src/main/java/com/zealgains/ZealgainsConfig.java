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

	// ─────────────────────────────────────────────
	// GENERAL SETTINGS
	// ─────────────────────────────────────────────

	@ConfigSection(
			name = "General Settings",
			description = "Display and in-game alert options",
			position = 1
	)
	String generalSection = "generalSection";

	@ConfigItem(
			keyName = "displayMode",
			name = "Display Mode",
			description = "Choose where to display the call tracker",
			position = 0,
			section = generalSection
	)
	default DisplayMode displayMode()
	{
		return DisplayMode.OVERLAY;
	}

	@ConfigItem(
			keyName = "autoClear",
			name = "Auto-Clear on Game End",
			description = "Automatically clear the tracker when the Soul Wars game ends",
			position = 1,
			section = generalSection
	)
	default boolean autoClear()
	{
		return true;
	}

	@ConfigItem(
			keyName = "hideOutsideSoulWars",
			name = "Hide Overlay Outside Game",
			description = "Only show the on-screen overlay when inside a Soul Wars game",
			position = 2,
			section = generalSection
	)
	default boolean hideOutsideSoulWars()
	{
		return true;
	}

	@ConfigItem(
			keyName = "dumpReminder",
			name = "5:00 Dump Reminder",
			description = "Alert when it's time to dump the winning kill (5:00 remaining, or 4:45 with 40+ members)",
			position = 3,
			section = generalSection
	)
	default Notification dumpReminder()
	{
		return Notification.OFF;
	}

	@ConfigItem(
			keyName = "avatarAlerts",
			name = "Avatar Ready Alerts",
			description = "Alert when an avatar is at full health and strength, and show which dump is next",
			position = 4,
			section = generalSection
	)
	default Notification avatarAlerts()
	{
		return Notification.OFF;
	}

	// ─────────────────────────────────────────────
	// ZG RANKS
	// ─────────────────────────────────────────────

	@ConfigSection(
			name = "ZG Ranks",
			description = "Friends Chat moderation tools — ban list, highlights, and call rule alerts",
			position = 3,
			closedByDefault = true
	)
	String ranksSection = "ranksSection";

	@ConfigItem(
			keyName = "enableNotifications",
			name = "Rule Break Alerts",
			description = "Send a RuneLite notification (sound/banner) when someone breaks a call rule",
			position = 0,
			section = ranksSection
	)
	default Notification enableNotifications()
	{
		return Notification.OFF;
	}

	@ConfigItem(
			keyName = "alertCrossWorld",
			name = "Alert Cross-World Calls",
			description = "Ignores and alerts you when someone makes a fake call from another world",
			position = 1,
			section = ranksSection
	)
	default boolean alertCrossWorld()
	{
		return true;
	}

	@ConfigItem(
			keyName = "highlightOnFl",
			name = "Highlight if on FL",
			description = "Highlights players who are on your Friends List",
			position = 2,
			section = ranksSection
	)
	default boolean highlightOnFl()
	{
		return true;
	}

	@ConfigItem(
			keyName = "flHighlightColor",
			name = "FL Highlight Color",
			description = "Color to highlight players on your Friends List",
			position = 3,
			section = ranksSection
	)
	default Color flHighlightColor()
	{
		return Color.GREEN;
	}

	@ConfigItem(
			keyName = "pmCheckerHighlight",
			name = "PM Checker Highlight",
			description = "Highlights online friends in the Friends Chat",
			position = 4,
			section = ranksSection
	)
	default boolean pmCheckerHighlight()
	{
		return true;
	}

	@ConfigItem(
			keyName = "pmCheckerColor",
			name = "PM Highlight Color",
			description = "Color to highlight online friends in the Friends Chat",
			position = 5,
			section = ranksSection
	)
	default Color pmCheckerColor()
	{
		return Color.YELLOW;
	}

	@ConfigItem(
			keyName = "enableBanList",
			name = "Enable Ban List Highlight",
			description = "Highlights players in the Friends Chat who are on a remote ban list",
			position = 6,
			section = ranksSection
	)
	default boolean enableBanList()
	{
		return true;
	}

	@ConfigItem(
			keyName = "banListUrl",
			name = "Ban List URL",
			description = "Raw URL to a plain text file containing banned names (one per line)",
			position = 7,
			section = ranksSection
	)
	default String banListUrl()
	{
		return "";
	}

	@ConfigItem(
			keyName = "banListColor",
			name = "Ban List Color",
			description = "Color to highlight banned players in the Friends Chat",
			position = 8,
			section = ranksSection
	)
	default Color banListColor()
	{
		return Color.RED;
	}

	@ConfigItem(
			keyName = "banListNotification",
			name = "Ban List Alerts",
			description = "Send a notification when a banned player joins the Friends Chat",
			position = 9,
			section = ranksSection
	)
	default Notification banListNotification()
	{
		return Notification.OFF;
	}

	// ─────────────────────────────────────────────
	// PLUGIN GUIDE
	// ─────────────────────────────────────────────

	@ConfigSection(
			name = "Commands & Features Guide",
			description = "Click to expand — full reference for all commands, rules, and alerts",
			position = 2,
			closedByDefault = true
	)
	String guideSection = "guideSection";

	@ConfigItem(
			keyName = "guideCommands",
			name = "<html><body width='170'>"
					+ "<font color='#A0A0A0'>For all rules / Methods please join </font>"
					+ "<font color='#5865F2'>discord.gg/riseabove</font><br><br>"
					+ "<font color='#87CEEB'><b>── Commands ──</b></font><br>"
					+ "<font color='#A0A0A0'>"
					+ "<font color='#FFB347'>::zgreset</font><br>"
					+ "Clears all tracked calls and runners.<br><br>"
					+ "<font color='#FFB347'>::zgreset r</font> or <font color='#FFB347'> b ##</font><br>"
					+ "Resets specifically targeted calls. Remaining callers reshuffle into sequential slots and open slots are announced in chat. Supports multiple args: <br> <font color='#FFB347'>::zgreset r2 b3, <br>or<br> :zgreset r34</font><br><br>"
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
					+ "plugin only works if you are in majority world of zealgains, and in a soul wars game.<br><br>"
					+ "recommend keeping overlay hidden if outside of games<br><br>"
					+ "</font>"
					+ "<font color='#87CEEB'><b>── Call Rules ──</b></font><br>"
					+ "<font color='#A0A0A0'>"
					+ "Calls are done in order<br><br>"
					+ "Call Order DOES NOT signify Dump Order<br><br>"
					+ "First to Mid gets dump priority, UNLESS someone has more calls<br><br>"
					+ "Max 3 calls before 12:00.<br><br>"
					+ "Each player may only call for one team per game.<br><br>"
					+ "B5: valid only after 12:00 and if R5 not claimed. <br><br>Same-tick tie: R5 wins.<br><br>"
					+ "R5 or B5 Dump at 5:00 on timer, unless 40+ people in game, then it becomes 4:45 on timer<br><br>"
					+ "In case of disputes, Highest Star+ rank will make the call<br><br>"
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

	// ─────────────────────────────────────────────
	// DEVELOPER OPTIONS
	// ─────────────────────────────────────────────

	@ConfigSection(
			name = "Developer Options",
			description = "Advanced overrides — not needed for normal use",
			position = 4,
			closedByDefault = true
	)
	String devSection = "devSection";

	@ConfigItem(
			keyName = "overrideCallFilter",
			name = "Override Call Filter",
			description = "<html>When <b>off</b> (default), avatar alerts are filtered to your team only — "
					+ "derived from your own calls. Alerts are suppressed until you have called.<br><br>"
					+ "When <b>on</b>, alerts fire for both teams regardless of which team you are on.</html>",
			position = 0,
			section = devSection
	)
	default boolean overrideCallFilter()
	{
		return false;
	}
}
