package com.zealgains;

import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Notification;
import net.runelite.client.config.Range;

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

	enum DumpAlertMode
	{
		AUTO,
		ALL
	}

	// ─────────────────────────────────────────────
	// TOP-LEVEL NOTICE (no section — appears above all sections)
	// ─────────────────────────────────────────────

	@ConfigItem(
			keyName = "topNotice",
			name = "<html><body width='170'>"
					+ "<font color='#FFA500'><b>Before configuring, please read:</b><br>"
					+ "Rules Guide · General Settings Guide · Overlay Usage</font>"
					+ "</body></html>",
			description = "",
			position = 0
	)
	default boolean topNotice()
	{
		return false;
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
			keyName = "showGameSummary",
			name = "End-of-Game Summary",
			description = "Print a call and score summary to chat when the game ends (requires Auto-Clear)",
			position = 2,
			section = generalSection
	)
	default boolean showGameSummary()
	{
		return true;
	}

	@ConfigItem(
			keyName = "showGameStatus",
			name = "Show Timer & Score",
			description = "Display the live game timer and current kill score in the overlay and side panel",
			position = 3,
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
			position = 4,
			section = generalSection
	)
	default boolean hideOutsideSoulWars()
	{
		return true;
	}

	@ConfigItem(
			keyName = "avatarAlerts",
			name = "Dump Alerts",
			description = "<html>Alerts when an avatar is at full HP and you have at least <b>16 Soul Fragments</b> — the minimum for a dump.<br><br>"
					+ "Kill 1 suppressed. Kill 5 fires only when the dump window opens (5:00, or 4:45 with 40+ in FC).</html>",
			position = 5,
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
			position = 4,
			closedByDefault = true
	)
	String generalGuideSection = "generalGuideSection";

	@ConfigItem(
			keyName = "guideGeneral",
			name = "<html><body width='170'>"
					+ "<font color='#87CEEB'><b>── General Settings Options ──</b></font><br><br>"
					+ "<font color='#A0A0A0'>"
					+ "<font color='#FFB347'>Display Mode</font><br>"
					+ "Controls where the call tracker appears.<br>"
					+ "None — hidden entirely.<br>"
					+ "Side Panel — RuneLite nav bar only.<br>"
					+ "Overlay — on-screen overlay only.<br>"
					+ "Both — overlay and side panel.<br><br>"
					+ "<font color='#FFB347'>Auto-Clear on Game End</font><br>"
					+ "Automatically clears all tracked calls and runners when a Soul Wars game ends.<br><br>"
					+ "<font color='#FFB347'>End-of-Game Summary</font><br>"
					+ "Prints a caller assignment, final score, and time remaining to chat at game end. Requires Auto-Clear to be enabled.<br><br>"
					+ "<font color='#FFB347'>Show Timer &amp; Score</font><br>"
					+ "Displays the live game countdown and current kill score in the overlay and side panel.<br><br>"
					+ "<font color='#FFB347'>Hide Overlay Outside Game</font><br>"
					+ "Hides the on-screen overlay when you are not inside a Soul Wars game. Recommended to keep this on.<br><br>"
					+ "<font color='#FFB347'>Dump Alerts</font><br>"
					+ "Sends a chat message and optional RuneLite notification when an avatar reaches full HP and strength and you have at least 16 Soul Fragments. "
					+ "Kill 1 is suppressed. Kill 5 fires only when the dump window opens (5:00, or 4:45 with 40+ in FC).<br><br>"
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
	// OVERLAY USAGE
	// ─────────────────────────────────────────────

	@ConfigSection(
			name = "Overlay Usage",
			description = "How to read the on-screen call tracker overlay",
			position = 5,
			closedByDefault = true
	)
	String overlayGuideSection = "overlayGuideSection";

	@ConfigItem(
			keyName = "guideOverlay",
			name = "<html><body width='170'>"
					+ "<font color='#A0A0A0'>"
					+ "R1-R5 and B1-B4 are always shown. B5 appears only after 12:00 and only if R5 is unclaimed.<br><br>"
					+ "Runners are shown at the bottom of the overlay when signed up.<br><br>"
					+ "The plugin only tracks calls if you are on the FC majority world and inside a Soul Wars game.<br><br>"
					+ "Recommended: enable Hide Overlay Outside Game to keep your screen clean between games."
					+ "</font></body></html>",
			description = "",
			position = 0,
			section = overlayGuideSection
	)
	default boolean guideOverlay()
	{
		return false;
	}

	// ─────────────────────────────────────────────
	// CHAT COMMANDS
	// ─────────────────────────────────────────────

	@ConfigSection(
			name = "Chat Commands",
			description = "In-game chat commands for the Zealgains plugin",
			position = 6,
			closedByDefault = true
	)
	String commandsGuideSection = "commandsGuideSection";

	@ConfigItem(
			keyName = "guideCommands",
			name = "<html><body width='170'>"
					+ "<font color='#87CEEB'><b>── Local Commands (you only) ──</b></font><br><br>"
					+ "<font color='#A0A0A0'>"
					+ "<font color='#FFB347'>::zgreset</font><br>"
					+ "Resets the entire board locally. A confirmation dialog will appear before anything is cleared.<br><br>"
					+ "<font color='#FFB347'>::zgreset r/b ##</font><br>"
					+ "Resets targeted calls locally. Remaining callers reshuffle and open slots are announced.<br>"
					+ "Supports multiple args:<br>"
					+ "<font color='#FFB347'>::zgreset r2 b3</font> <br>or<br> <font color='#FFB347'>::zgreset r34</font><br><br>"
					+ "</font>"
					+ "<font color='#87CEEB'><b>── Rank Broadcast (Captain+ only) ──</b></font><br><br>"
					+ "<font color='#A0A0A0'>"
					+ "<font color='#FFB347'>!zgreset r/b ##</font><br>"
					+ "Sends a targeted reset to FC chat. All plugin users in the FC silently apply the reset and their overlays update instantly.<br><br>"
					+ "Only works if you are ranked Captain or above (Captain, General, Owner). Lower ranks are ignored.<br><br>"
					+ "15-second cooldown is enforced to prevent accidental spam from multiple ranks firing at once.<br><br>"
					+ "Supports multiple args:<br>"
					+ "<font color='#FFB347'>!zgreset r2 b3</font> <br>or<br> <font color='#FFB347'>!zgreset r34</font>"
					+ "</font></body></html>",
			description = "",
			position = 0,
			section = commandsGuideSection
	)
	default boolean guideCommands()
	{
		return false;
	}

	// ─────────────────────────────────────────────
	// ZG RANKS SETTINGS
	// ─────────────────────────────────────────────

	@ConfigSection(
			name = "ZG Ranks Settings",
			description = "Friends Chat moderation tools — ban list, highlights, and call rule alerts",
			position = 7,
			closedByDefault = true
	)
	String ranksSection = "ranksSection";

	@ConfigItem(
			keyName = "ranksNotice",
			name = "<html><body width='170'>"
					+ "<font color='#A0A0A0'>ZG Ranks Settings and below are intended for "
					+ "<b>ZG Star Ranks</b> only.</font>"
					+ "</body></html>",
			description = "",
			position = -1,
			section = ranksSection
	)
	default boolean ranksNotice()
	{
		return false;
	}

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
			description = "Explanation of every option in ZG Ranks Settings",
			position = 8,
			closedByDefault = true
	)
	String ranksGuideSection = "ranksGuideSection";

	@ConfigItem(
			keyName = "guideRanks",
			name = "<html><body width='170'>"
					+ "<font color='#87CEEB'><b>── Rank Commands ──</b></font><br><br>"
					+ "<font color='#A0A0A0'>"
					+ "<font color='#FFB347'>!zgreset r/b ##</font><br>"
					+ "Broadcasts a targeted reset to all plugin users in the FC. Only fires if you are Captain or above. 15-second cooldown prevents accidental spam.<br><br>"
					+ "Supports multiple args:<br>"
					+ "<font color='#FFB347'>!zgreset r2 b3</font> <br>or<br> <font color='#FFB347'>!zgreset r34</font><br><br>"
					+ "<font color='#FFB347'>::zgsync</font><br>"
					+ "Force-refreshes the ban list immediately from the configured URL.<br><br>"
					+ "The plugin automatically refreshes the list every <b>30 minutes</b> on its own — you only need this command when you have just made a change to the AKL.<br><br>"
					+ "A <b>5-minute cooldown</b> is enforced to prevent API spam. If you run ::zgsync too soon it will tell you how many seconds remain.<br><br>"
					+ "<font color='#FF6666'>Only run ::zgsync after waiting at least 5 minutes following any addition or removal from the AKL, to ensure the updated list has propagated before fetching.</font><br><br>"
					+ "</font>"
					+ "<font color='#87CEEB'><b>── ZG Ranks Options ──</b></font><br><br>"
					+ "<font color='#A0A0A0'>"
					+ "<font color='#FFB347'>Rule Break Alerts</font><br>"
					+ "Sends a RuneLite notification when a call rule is broken, e.g. exceeding 3 calls before 12:00, calling out of order, or trying to call for both teams in the same game. A player's first call locks them to that team — the plugin has no way of knowing their actual in-game team.<br><br>"
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
					+ "Calls are in order — each slot requires the previous to be filled first.<br><br>"
					+ "Calling b2 before b1 is <b>invalid</b> and will be rejected.<br><br>"
					+ "Call order does NOT signify dump order.<br><br>"
					+ "Max 3 calls before 12:00 on the timer.<br><br>"
					+ "Each player may only call for one team per game — first call locks your team.<br><br>"
					+ "B5 is valid only after 12:00 and only if R5 is unclaimed. Same-tick tie: R5 wins.<br><br>"
					+ "First to mid gets dump priority, unless someone else has more calls.<br><br>"
					+ "In case of disputes, the highest Star+ rank makes the call.<br><br>"
					+ "</font>"
					+ "<font color='#87CEEB'><b>── How to Frag ──</b></font><br>"
					+ "<font color='#A0A0A0'>"
					+ "<font color='#FF6666'>Watch 'How to Frag' by Gods Account on YouTube for a full visual walkthrough.</font><br><br>"
					+ "<font color='#FFB347'>Setup</font><br>"
					+ "Install the <b>Soul Wars</b> plugin by <b>Lucid Poro</b> from the Plugin Hub.<br><br>"
					+ "<font color='#FFB347'>Benefits of Fragging</font><br>"
					+ "Earn <b>max zeal</b> — 60 zeal on a win, 40 on a loss.<br>"
					+ "Help maintain consistency and continuity of games for the FC.<br>"
					+ "Earn a rank of <b>Corporal</b> or <b>Sergeant</b> in the Zeal-Gains chat.<br><br>"
					+ "<font color='#FFB347'>Calling your kills</font><br>"
					+ "At game start, call how many kills you want to cover. <b>Call order at the start matters</b> — dump order does not.<br><br>"
					+ "<font color='#FFB347'>Frag amounts</font><br>"
					+ "1 kill called &nbsp;→ <b>32 frags</b><br>"
					+ "2 kills called → <b>16 frags</b> each<br>"
					+ "3 kills called → <b>16 frags</b> each<br>"
					+ "<i>16 frags minimum per dump</i><br><br>"
					+ "<font color='#FFB347'>Blue Victory</font><br>"
					+ "If Red does not fill all 5 kills by <b>12:00</b>, Blue can win with <b>b5</b>.<br><br>"
					+ "<font color='#FFB347'>Correcting calls</font><br>"
					+ "If someone takes a slot you planned, move to the next open one. "
					+ "Example: you call r1r2, someone takes r1 first — change your call to r3. <i>your r2 will count so long as no other r2 happened prior to your call.</i><br><br>"
					+ "</font>"
					+ "<font color='#87CEEB'><b>── Dumping Rules ──</b></font><br>"
					+ "<font color='#A0A0A0'>"
					+ "<b>Capture the obelisk</b> before dumping — <b>do not</b> dump frags off-color.<br><br>"
					+ "Only dump when the enemy Avatar is at <b>100% HP and strength</b>.<br><br>"
					+ "<b>Do not</b> overdump — dumping the same kill as another fragger wastes it.<br><br>"
					+ "<b>Do not dump more kills than you called.</b><br><br>"
					+ "<b>Do not</b> dump the winning kill before <b>5:00</b> remaining.<br>"
					+ "With <b>40+ people</b> in FC, wait until <b>4:45</b> instead.<br><br>"
					+ "</font>"
					+ "<font color='#87CEEB'><b>── Valid Callouts ──</b></font><br>"
					+ "<font color='#A0A0A0'>"
					+ "Callouts can appear anywhere in your message. Any of the formats below are recognised:<br><br>"
					+ "<font color='#FFB347'>Single kill</font><br>"
					+ "r1 &nbsp; r2 &nbsp; r3 &nbsp; r4 &nbsp; r5<br>"
					+ "b1 &nbsp; b2 &nbsp; b3 &nbsp; b4 &nbsp; b5*<br><br>"
					+ "<font color='#FFB347'>Multiple kills — compact</font><br>"
					+ "r12 &nbsp; r123 &nbsp; r1234 &nbsp; r12345<br>"
					+ "b12 &nbsp; b123 &nbsp; b1234<br><br>"
					+ "<font color='#FFB347'>Multiple kills — spaced</font><br>"
					+ "r1r2 &nbsp; r1r2r3 &nbsp; r1r2r3r4<br>"
					+ "b1b2 &nbsp; b1b2b3 &nbsp; b1b2b3b4<br><br>"
					+ "<font color='#FFB347'>Mixed spacing</font><br>"
					+ "r1 r2 r3 &nbsp; or &nbsp; r1 r2r3 etc.<br><br>"
					+ "<font color='#A0A0A0'>*b5 is only valid after 12:00 and only if R5 is unclaimed.</font><br><br>"
					+ "<font color='#FF6666'>Invalid examples</font><br>"
					+ "r2 (before r1 is claimed)<br>"
					+ "b2 (before b1 is claimed)<br>"
					+ "r1 b2 (mixed teams in one message)<br>"
					+ "Messages containing: need, open, who, call, want, you, getting, go get, grab — are always ignored regardless of content."
					+ "</font><br><br>"
					+ "<font color='#87CEEB'><b>── Frag Runners ──</b></font><br>"
					+ "<font color='#A0A0A0'>"
					+ "<font color='#FFB347'>Who they are</font><br>"
					+ "Frag Runners are lower-level players who can't efficiently cover a kill solo, but want to assist and earn max zeal (60 zeal on Red, 40 on Blue).<br><br>"
					+ "<font color='#FFB347'>What they do</font><br>"
					+ "Runners help deliver fragments to the obelisk for the main fragger covering their calls, and speed up middle obelisk captures to keep the game flowing.<br><br>"
					+ "<font color='#FFB347'>How to sign up</font><br>"
					+ "Call out in FC to signal you are a runner:<br>"
					+ "^r &nbsp;r^ &nbsp;&gt;r &nbsp;r&gt; — Red frag runner<br>"
					+ "^b &nbsp;b^ &nbsp;&gt;b &nbsp;b&gt; — Blue frag runner<br><br>"
					+ "<font color='#FFB347'>What to do in-game</font><br>"
					+ "Follow the taxi at the start for graveyard and middle captures.<br>"
					+ "After the enemy graveyard is captured, return to mid to receive frags and dump them.<br>"
					+ "In some cases you may be asked to meet the fragger at ghosts to collect frags.<br><br>"
					+ "<font color='#FFB347'>Important notes</font><br>"
					+ "Not every fragger uses runners — if no one opts in to use you, stay with the taxi.<br>"
					+ "After covering kills, stay in middle and help with captures to keep the game smooth.<br>"
					+ "All standard rules apply to runners as well.<br>"
					+ "Make sure <b>Accept Aid</b> is turned ON. If you are an iron, let the fragger know so they can kill the souls and leave fragments on the ground for you to pick up.<br><br>"
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
	// ALERT COLORS
	// ─────────────────────────────────────────────

	@ConfigSection(
			name = "Color Options",
			description = "Customize colors for alerts, overlays, and summaries — all support opacity",
			position = 3,
			closedByDefault = true
	)
	String alertColorsSection = "alertColorsSection";

	// ── Alert Colors ──

	@Alpha
	@ConfigItem(
			keyName = "alertCallColor",
			name = "Rule Break Alert",
			description = "Color of rule-break alerts (bad calls, cross-world calls, disconnect notices)",
			position = 0,
			section = alertColorsSection
	)
	default Color alertCallColor()
	{
		return new Color(0xFF, 0x00, 0x00, 0xFF);
	}

	@Alpha
	@ConfigItem(
			keyName = "alertBanColor",
			name = "Ban List Alert",
			description = "Color of the chat message when a banned player is detected",
			position = 1,
			section = alertColorsSection
	)
	default Color alertBanColor()
	{
		return new Color(0xFF, 0x00, 0x00, 0xFF);
	}

	@Alpha
	@ConfigItem(
			keyName = "avatarAlertColor",
			name = "Avatar Alert",
			description = "Color of avatar-ready dump messages and the 4:45 warning",
			position = 2,
			section = alertColorsSection
	)
	default Color avatarAlertColor()
	{
		return new Color(0xFF, 0x99, 0x00, 0xFF);
	}

	@Alpha
	@ConfigItem(
			keyName = "summaryHeaderColor",
			name = "Summary Header",
			description = "Color of the === Zealgains: Game Summary === header line",
			position = 3,
			section = alertColorsSection
	)
	default Color summaryHeaderColor()
	{
		return new Color(0xFF, 0x99, 0x00, 0xFF);
	}

	@Alpha
	@ConfigItem(
			keyName = "summaryRedColor",
			name = "Summary Red Calls",
			description = "Color of the Red Calls line in the end-of-game summary",
			position = 4,
			section = alertColorsSection
	)
	default Color summaryRedColor()
	{
		return new Color(0xCC, 0x22, 0x22, 0xFF);
	}

	@Alpha
	@ConfigItem(
			keyName = "summaryBlueColor",
			name = "Summary Blue Calls",
			description = "Color of the Blue Calls line in the end-of-game summary",
			position = 5,
			section = alertColorsSection
	)
	default Color summaryBlueColor()
	{
		return new Color(0x22, 0x77, 0xCC, 0xFF);
	}

	@Alpha
	@ConfigItem(
			keyName = "summaryScoreColor",
			name = "Summary Score",
			description = "Color of the Final Score / Time remaining line in the end-of-game summary",
			position = 6,
			section = alertColorsSection
	)
	default Color summaryScoreColor()
	{
		return new Color(0xFF, 0x99, 0x00, 0xFF);
	}

	// ── Overlay Colors ──

	@Alpha
	@ConfigItem(
			keyName = "overlayRedColor",
			name = "Overlay Red Team",
			description = "Color of the Red Team label and red runner names in the overlay",
			position = 7,
			section = alertColorsSection
	)
	default Color overlayRedColor()
	{
		return new Color(0xFF, 0x00, 0x00, 0xFF);
	}

	@Alpha
	@ConfigItem(
			keyName = "overlayBlueColor",
			name = "Overlay Blue Team",
			description = "Color of the Blue Team label and blue runner names in the overlay",
			position = 8,
			section = alertColorsSection
	)
	default Color overlayBlueColor()
	{
		return new Color(0x00, 0xFF, 0xFF, 0xFF);
	}

	@Alpha
	@ConfigItem(
			keyName = "overlayRunnersColor",
			name = "Overlay Runners",
			description = "Color of the Runners section header in the overlay",
			position = 9,
			section = alertColorsSection
	)
	default Color overlayRunnersColor()
	{
		return new Color(0xFF, 0x80, 0x00, 0xFF);
	}

	@Alpha
	@ConfigItem(
			keyName = "overlayTimerColor",
			name = "Overlay Timer",
			description = "Color of the live game timer in the overlay",
			position = 10,
			section = alertColorsSection
	)
	default Color overlayTimerColor()
	{
		return new Color(0xFF, 0xFF, 0x00, 0xFF);
	}

	@Alpha
	@ConfigItem(
			keyName = "overlayScoreColor",
			name = "Overlay Score",
			description = "Color of the kill score display in the overlay",
			position = 11,
			section = alertColorsSection
	)
	default Color overlayScoreColor()
	{
		return new Color(0xFF, 0xFF, 0xFF, 0xFF);
	}

	@Alpha
	@ConfigItem(
			keyName = "overlayLobbyCountColor",
			name = "Overlay Lobby Count",
			description = "Color of the FC member count line in the overlay",
			position = 12,
			section = alertColorsSection
	)
	default Color overlayLobbyCountColor()
	{
		return new Color(0xAA, 0xAA, 0xAA, 0xFF);
	}

	@Alpha
	@ConfigItem(
			keyName = "overlayBackgroundColor",
			name = "Overlay Background",
			description = "Background color and opacity of the overlay panel",
			position = 13,
			section = alertColorsSection
	)
	default Color overlayBackgroundColor()
	{
		return new Color(0x1A, 0x1A, 0x1A, 0x1A);
	}

	@Alpha
	@ConfigItem(
			keyName = "overlayCallLabelColor",
			name = "Overlay Call Label",
			description = "Color of the 'Call 1', 'Call 2' etc. labels in the overlay",
			position = 14,
			section = alertColorsSection
	)
	default Color overlayCallLabelColor()
	{
		return new Color(0xFF, 0xFF, 0xFF, 0xFF);
	}

	@Alpha
	@ConfigItem(
			keyName = "overlayCallNameColor",
			name = "Overlay Caller Name",
			description = "Color of the player name shown next to each call slot",
			position = 15,
			section = alertColorsSection
	)
	default Color overlayCallNameColor()
	{
		return new Color(0xFF, 0xFF, 0xFF, 0xFF);
	}

	@Range(min = 0, max = 100)
	@ConfigItem(
			keyName = "overlayOpacity",
			name = "Overlay Global Opacity %",
			description = "Scales the opacity of every overlay element at once. 100 = fully opaque, 0 = invisible.",
			position = 16,
			section = alertColorsSection
	)
	default int overlayOpacity()
	{
		return 100;
	}

	// ─────────────────────────────────────────────
	// DEVELOPER OPTIONS
	// ─────────────────────────────────────────────

	@ConfigSection(
			name = "Developer Options",
			description = "Advanced overrides — not needed for normal use",
			position = 9,
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

	@ConfigItem(
			keyName = "enableCallsOutsideGame",
			name = "Enable Calls Outside Game",
			description = "<html>When <b>on</b>, the plugin processes FC calls even when you are not inside a Soul Wars game. "
					+ "Useful for testing the overlay and call logic outside of a live game.<br><br>"
					+ "Time-based rules (12:00 cap, B5 gate) treat the timer as 0:00 while outside a game.</html>",
			position = 1,
			section = devSection
	)
	default boolean enableCallsOutsideGame()
	{
		return false;
	}

	@ConfigItem(
			keyName = "skipFragmentCheck",
			name = "Skip Fragment Count Check",
			description = "<html>When <b>on</b>, dump notifications fire even if you have fewer than 16 Soul Fragments in your inventory.<br><br>"
					+ "By default the plugin suppresses alerts when you do not have enough fragments to cover a dump (minimum 16). "
					+ "Enable this to always receive dump notifications regardless of inventory.</html>",
			position = 2,
			section = devSection
	)
	default boolean skipFragmentCheck()
	{
		return false;
	}

	@ConfigItem(
			keyName = "highlightObelisk",
			name = "Do Not Dump Warning",
			description = "<html><b>Smart obelisk warning</b> — highlights the Soul Obelisk in red and shows <b>DO NOT DUMP</b> over it when dumping would be wasted.<br><br>"
					+ "Triggers automatically in three situations:<br>"
					+ "• Obelisk is <b>white</b> (uncontrolled) — fragments go nowhere<br>"
					+ "• Obelisk is the <b>wrong color</b> for your team<br>"
					+ "• Obelisk is your team's color but the <b>avatar isn't at full HP+strength</b><br><br>"
					+ "Only shown to players with an active kill call or runner signup — spectators do not see it.<br><br>"
					+ "Pair with <b>Prevent Dumps When Not Ready</b> to also move Sacrifice off left-click.</html>",
			position = 7,
			section = generalSection
	)
	default boolean highlightObelisk()
	{
		return true;
	}

	@ConfigItem(
			keyName = "preventOffColorDumps",
			name = "Prevent Dumps When Not Ready",
			description = "<html>Deprioritizes <b>Sacrifice-Fragments</b> on the Soul Obelisk when dumping would be wasted — "
					+ "making <b>Walk Here</b> the default left-click. Right-click Sacrifice still works.<br><br>"
					+ "Fires in the same three situations as the obelisk highlight: white obelisk, wrong team color, or avatar not at full HP+strength.</html>",
			position = 8,
			section = generalSection
	)
	default boolean preventOffColorDumps()
	{
		return false;
	}

	@ConfigItem(
			keyName = "dumpAlertMode",
			name = "Dump Alert Team Filter",
			description = "<html><b>Auto</b> (default) — only alerts for the avatar your team should dump, derived from your own call history.<br><br>"
					+ "<b>All</b> — alerts fire for both avatars regardless of which team you are on.</html>",
			position = 3,
			section = devSection
	)
	default DumpAlertMode dumpAlertMode()
	{
		return DumpAlertMode.AUTO;
	}

	@ConfigItem(
			keyName = "alwaysShowDumpOverlay",
			name = "Always Show Dump Overlay",
			description = "<html>When <b>on</b>, the <b>DO NOT DUMP</b> obelisk overlay shows even if you have no active calls or runner signup.<br><br>"
					+ "By default the overlay is hidden unless you have a kill call or are a registered runner, "
					+ "so spectators do not see it. Enable this to show it regardless.</html>",
			position = 4,
			section = devSection
	)
	default boolean alwaysShowDumpOverlay()
	{
		return false;
	}

	@ConfigItem(
			keyName = "showLobbyCount",
			name = "Show Lobby Player Count",
			description = "<html>Shows the number of players in the lobby on the overlay.<br><br>"
					+ "Updates live while waiting for a game to start, then freezes at the game-start count for the duration of the game.<br><br>"
					+ "Color is configurable under <b>Color Options → Overlay Lobby Count</b>.</html>",
			position = 5,
			section = devSection
	)
	default boolean showLobbyCount()
	{
		return false;
	}
}
