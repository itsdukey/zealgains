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
		NONE,
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
			position = 2
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
			keyName = "showGameStatus",
			name = "Show Timer & Score",
			description = "Display the live game timer and current kill score in the overlay and side panel",
			position = 2,
			section = generalSection
	)
	default boolean showGameStatus()
	{
		return true;
	}

	@ConfigItem(
			keyName = "hideOutsideSoulWars",
			name = "Hide Overlay Outside Game",
			description = "Only show the on-screen overlay when inside a Soul Wars game",
			position = 3,
			section = generalSection
	)
	default boolean hideOutsideSoulWars()
	{
		return true;
	}

	@ConfigItem(
			keyName = "avatarAlerts",
			name = "Avatar Ready Alerts",
			description = "Alert when an avatar is at full health and strength. For the 5th kill, also includes the dump time (5:00 or 4:45 with 40+ members).",
			position = 4,
			section = generalSection
	)
	default Notification avatarAlerts()
	{
		return Notification.OFF;
	}

	// ─────────────────────────────────────────────
	// GENERAL SETTINGS GUIDE
	// ─────────────────────────────────────────────

	@ConfigSection(
			name = "General Settings Guide",
			description = "Commands, overlay behaviour, and alert reference",
			position = 3,
			closedByDefault = true
	)
	String generalGuideSection = "generalGuideSection";

	@ConfigItem(
			keyName = "guideGeneral",
			name = "<html><body width='170'>"
					+ "<font color='#87CEEB'><b>── Commands ──</b></font><br>"
					+ "<font color='#A0A0A0'>"
					+ "<font color='#FFB347'>::zgreset</font><br>"
					+ "Clears all tracked calls and runners.<br><br>"
					+ "<font color='#FFB347'>::zgreset r/b ##</font><br>"
					+ "Resets targeted calls. Remaining callers reshuffle and open slots are announced. Supports multiple args:<br>"
					+ "<font color='#FFB347'>::zgreset r2 b3</font> <br>or<br> <font color='#FFB347'>::zgreset r34</font><br><br>"
					+ "</font>"
					+ "<font color='#87CEEB'><b>── Overlay ──</b></font><br>"
					+ "<font color='#A0A0A0'>"
					+ "R1-R5 and B1-B4 always shown. B5 appears only after 12:00 and only if R5 is unclaimed. Runners shown at bottom when signed up.<br><br>"
					+ "Plugin only tracks calls if you are in the majority world and inside a Soul Wars game.<br><br>"
					+ "Recommended: keep overlay hidden outside of games."
					+ "</font></body></html>",
			description = "",
			position = 0,
			section = generalGuideSection
	)
	default boolean guideGeneral()
	{
		return false;
	}

	// ─────────────────────────────────────────────
	// ZG RANKS
	// ─────────────────────────────────────────────

	@ConfigSection(
			name = "ZG Ranks",
			description = "Friends Chat moderation tools — ban list, highlights, and call rule alerts",
			position = 4,
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
	// ZG RANKS GUIDE
	// ─────────────────────────────────────────────

	@ConfigSection(
			name = "ZG Ranks Guide",
			description = "Explanation of every option in ZG Ranks and the ::zgsync command",
			position = 5,
			closedByDefault = true
	)
	String ranksGuideSection = "ranksGuideSection";

	@ConfigItem(
			keyName = "guideRanks",
			name = "<html><body width='170'>"
					+ "<font color='#87CEEB'><b>── ZG Ranks Options ──</b></font><br><br>"
					+ "<font color='#A0A0A0'>"
					+ "<font color='#FFB347'>Rule Break Alerts</font><br>"
					+ "Sends a RuneLite notification when a call rule is broken <br> e.g. <br>calling for the wrong team,<br> exceeding 3 calls before 12:00,<br> or calling out of order.<br><br>"
					+ "<font color='#FFB347'>Alert Cross-World Calls</font><br>"
					+ "Detects and ignores calls from players not on the FC majority world, preventing fake calls from off-world trolls.<br><br>"
					+ "<font color='#FFB347'>Highlight if on FL</font><br>"
					+ "Colors FC members who appear on your Friends List using the FL Highlight Color.<br><br>"
					+ "<font color='#FFB347'>FL Highlight Color</font><br>"
					+ "The color used for Friends List highlights. Default: green.<br><br>"
					+ "<font color='#FFB347'>PM Checker Highlight</font><br>"
					+ "Colors FC members who are currently online and PM-able using the PM Highlight Color.<br><br>"
					+ "<font color='#FFB347'>PM Highlight Color</font><br>"
					+ "The color used for online-friend highlights. Default: yellow.<br><br>"
					+ "<font color='#FFB347'>Enable Ban List Highlight</font><br>"
					+ "Downloads a remote ban list and highlights any matching FC members in the Ban List Color.<br><br>"
					+ "<font color='#FFB347'>Ban List URL</font><br>"
					+ "Raw URL to a plain-text file of banned names, one per line. Leave blank to disable.<br><br>"
					+ "<font color='#FFB347'>Ban List Color</font><br>"
					+ "The color used to highlight banned players. Default: red.<br><br>"
					+ "<font color='#FFB347'>Ban List Alerts</font><br>"
					+ "Sends a RuneLite notification when a banned player joins the FC.<br><br>"
					+ "</font>"
					+ "<font color='#87CEEB'><b>── Command ──</b></font><br>"
					+ "<font color='#A0A0A0'>"
					+ "<font color='#FFB347'>::zgsync</font><br>"
					+ "Force-refreshes the ban list immediately. A 5-minute cooldown is enforced to prevent spam."
					+ "</font></body></html>",
			description = "",
			position = 0,
			section = ranksGuideSection
	)
	default boolean guideRanks()
	{
		return false;
	}

	// ─────────────────────────────────────────────
	// RULES GUIDE
	// ─────────────────────────────────────────────

	@ConfigSection(
			name = "Rules Guide",
			description = "Call rules, team lock rules, and runner callout formats",
			position = 1,
			closedByDefault = true
	)
	String rulesGuideSection = "rulesGuideSection";

	@ConfigItem(
			keyName = "guideRules",
			name = "<html><body width='170'>"
					+ "<font color='#A0A0A0'>For all rules / Methods please join<br></font>"
					+ "<font color='#5865F2'>discord.gg/riseabove</font><br><br>"
					+ "<font color='#87CEEB'><b>── Call Rules ──</b></font><br>"
					+ "<font color='#A0A0A0'>"
					+ "Calls are sequential — each slot requires the previous to be filled first.<br><br>"
					+ "Call order does NOT signify dump order.<br><br>"
					+ "First to mid gets dump priority, unless someone has more calls.<br><br>"
					+ "Max 3 calls before 12:00.<br><br>"
					+ "Each player may only call for one team per game.<br><br>"
					+ "B5: valid only after 12:00 and only if R5 is not claimed.<br><br>"
					+ "Same-tick R5+B5 tie: R5 wins.<br><br>"
					+ "5th dump at 5:00 remaining (4:45 with 40+ in FC).<br><br>"
					+ "In case of disputes, highest Star+ rank makes the call.<br><br>"
					+ "</font>"
					+ "<font color='#87CEEB'><b>── Runner Callouts ──</b></font><br>"
					+ "<font color='#A0A0A0'>"
					+ "Red runner: ^r &nbsp;r^ &nbsp;&gt;r &nbsp;r&gt;<br>"
					+ "Blue runner: ^b &nbsp;b^ &nbsp;&gt;b &nbsp;b&gt;<br><br>"
					+ "Runners appear at the bottom of the overlay when signed up."
					+ "</font></body></html>",
			description = "",
			position = 0,
			section = rulesGuideSection
	)
	default boolean guideRules()
	{
		return false;
	}

	// ─────────────────────────────────────────────
	// DEVELOPER OPTIONS
	// ─────────────────────────────────────────────

	@ConfigSection(
			name = "Developer Options",
			description = "Advanced overrides — not needed for normal use",
			position = 6,
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
