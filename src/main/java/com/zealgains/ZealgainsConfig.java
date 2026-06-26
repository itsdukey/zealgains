package com.zealgains;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
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
}