package com.zealgains;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.FriendsChatManager;
import net.runelite.api.FriendsChatMember;
import net.runelite.api.FriendsChatRank;
import net.runelite.api.GameObject;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.CommandExecuted;
import net.runelite.api.events.FriendsChatChanged;
import net.runelite.api.events.FriendsChatMemberJoined;
import net.runelite.api.events.FriendsChatMemberLeft;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.awt.Color;
import java.awt.image.BufferedImage;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@PluginDescriptor(
		name = "Zealgains",
		description = "Tracks Red/Blue kill calls in Friends Chat"
)
public class ZealgainsPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private ZealgainsConfig config;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ZealgainsOverlay overlay;

	@Inject
	private ZealgainsObeliskOverlay obeliskOverlay;

	@Inject
	private Notifier notifier;

	@Inject
	private ClientThread clientThread;

	@Inject
	private OkHttpClient httpClient;

	@Inject
	private ScheduledExecutorService executor;

	@Inject
	private ConfigManager configManager;

	private ZealgainsPanel panel;
	private NavigationButton navButton;
	private ScheduledFuture<?> banListRefreshTask;

	// Kill tracking — only accessed on the client thread
	private final Map<Integer, String> redKills = new HashMap<>();
	private final Map<Integer, String> blueKills = new HashMap<>();

	// Runner tracking (^r / ^b callouts)
	private final Set<String> redRunners = new LinkedHashSet<>();
	private final Set<String> blueRunners = new LinkedHashSet<>();

	// Thread-safe set populated from OkHttp thread
	private final Set<String> bannedPlayers = ConcurrentHashMap.newKeySet();

	private int kill5Tick = -1;
	private int cachedMajorityWorld = -1;
	private final Set<String> disconnectAlerted = new LinkedHashSet<>();

	// Soul Obelisk game object IDs (uncontrolled / blue-controlled / red-controlled)
	private static final int OBELISK_ID_NONE = 40449;
	private static final int OBELISK_ID_BLUE = 40450;
	private static final int OBELISK_ID_RED  = 40451;

	private GameObject trackedObelisk  = null;
	private boolean obeliskWarnActive  = false;
	// "r" = red team, "b" = blue team, null = unknown; set by varbit 3815 (1=blue, 2=red)
	private String varbitTeam = null;

	// Avatar dump tracking
	private static final int SOUL_FRAGMENT_ITEM_ID = 25201;
	private int maxBlueHealth = 0, maxBlueStrength = 0;
	private int maxRedHealth = 0, maxRedStrength = 0;
	private boolean blueAvatarDumpAlerted = false, redAvatarDumpAlerted = false;
	private boolean blueEarlyDumpWarned = false, redEarlyDumpWarned = false;

	// End-of-game summary tracking
	private int lastGameRedScore = 0;
	private int lastGameBlueScore = 0;
	private int lastKnownGameSeconds = -1;

	// Lobby size — tracked live from widget 434.6, frozen when lobby timer hits 0
	private int lobbyPlayerCount = 0;
	private boolean lobbyCountSet = false;
	private int prevLobbyTimerSeconds = -1;

	private long lastBanListFetch = 0;
	private static final long BAN_LIST_COOLDOWN_MS = 5 * 60 * 1000L;
	private long lastBroadcastReset = 0;
	// Tracks the local player's Soul Wars team; resets to 0 on any exit (idle-kick, normal end, lobby)
	private static final int VARBIT_SOUL_WARS_TEAM = 3815;

	private final Pattern callPattern = Pattern.compile("(?i)(?<![a-zA-Z0-9_-])([rb])([rb1-5]+)(?![a-zA-Z0-9_-])");
	private final Pattern runnerPattern = Pattern.compile("(?i)^(?:[>^]([rb])|([rb])[>^])");

	// Getters for overlay and panel
	public Map<Integer, String> getRedKills() { return redKills; }
	public Map<Integer, String> getBlueKills() { return blueKills; }
	public Set<String> getRedRunners() { return redRunners; }
	public Set<String> getBlueRunners() { return blueRunners; }
	public int getRedScore() { return lastGameRedScore; }
	public int getBlueScore() { return lastGameBlueScore; }
	public boolean isObeliskWarnActive() { return obeliskWarnActive; }
	public GameObject getTrackedObelisk() { return trackedObelisk; }
	public int getLobbyPlayerCount() { return lobbyPlayerCount; }

	public boolean hasLocalCall()
	{
		String name = client.getLocalPlayer() != null
				? Text.removeTags(client.getLocalPlayer().getName()) : null;
		if (name == null) return false;
		return redKills.containsValue(name) || blueKills.containsValue(name)
				|| redRunners.contains(name) || blueRunners.contains(name);
	}

	@Override
	protected void startUp() throws Exception
	{
		panel = new ZealgainsPanel(this);

		final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/icon.png");
		navButton = NavigationButton.builder()
				.tooltip("Zealgains")
				.icon(icon)
				.priority(5)
				.panel(panel)
				.build();

		if (config.displayMode() != ZealgainsConfig.DisplayMode.OVERLAY)
		{
			clientToolbar.addNavigation(navButton);
		}

		overlayManager.add(overlay);
		overlayManager.add(obeliskOverlay);
		resetKills();

		lastBanListFetch = System.currentTimeMillis();
		fetchBanList();

		banListRefreshTask = executor.scheduleAtFixedRate(this::fetchBanList, 30, 30, TimeUnit.MINUTES);
	}

	@Override
	protected void shutDown() throws Exception
	{
		if (banListRefreshTask != null)
		{
			banListRefreshTask.cancel(false);
			banListRefreshTask = null;
		}
		clientToolbar.removeNavigation(navButton);
		overlayManager.remove(overlay);
		overlayManager.remove(obeliskOverlay);
		resetKills();
		bannedPlayers.clear();
		cachedMajorityWorld = -1;
	}

	// --- CONFIG CHANGE LISTENER ---

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!event.getGroup().equals("zealgains")) return;

		if (event.getKey().equals("displayMode"))
		{
			clientToolbar.removeNavigation(navButton);
			if (config.displayMode() != ZealgainsConfig.DisplayMode.OVERLAY)
			{
				clientToolbar.addNavigation(navButton);
			}
		}
		else if (event.getKey().equals("banListUrl") || event.getKey().equals("enableBanList"))
		{
			fetchBanList();
		}
		else if (event.getKey().equals("overrideCallFilter") && "true".equals(event.getNewValue()))
		{
			SwingUtilities.invokeLater(() ->
			{
				int result = JOptionPane.showConfirmDialog(null,
					"<html><b>Override Call Filter</b><br><br>"
					+ "When enabled, avatar alerts fire for <b>both teams</b> regardless of which team you are on.<br><br>"
					+ "By default, alerts only fire for your enemy avatar, derived from your own call history.<br><br>"
					+ "This is a developer option — enable only if you need to monitor both avatars at once.<br><br>"
					+ "Enable Override Call Filter?</html>",
					"Developer Option — Override Call Filter",
					JOptionPane.YES_NO_OPTION,
					JOptionPane.WARNING_MESSAGE);
				if (result != JOptionPane.YES_OPTION)
				{
					configManager.setConfiguration("zealgains", "overrideCallFilter", false);
				}
			});
		}
		else if (event.getKey().equals("enableCallsOutsideGame") && "true".equals(event.getNewValue()))
		{
			SwingUtilities.invokeLater(() ->
			{
				int result = JOptionPane.showConfirmDialog(null,
					"<html><b>Enable Calls Outside Game</b><br><br>"
					+ "When enabled, the plugin tracks FC calls even when you are <b>not inside a Soul Wars game</b>.<br><br>"
					+ "Time-based rules (12:00 call cap, B5 gate) will treat the timer as <b>0:00</b> while outside a game, "
					+ "meaning all slots including B5 are available and the 3-call cap is lifted.<br><br>"
					+ "This is a developer/testing option — disable it before playing a real game.<br><br>"
					+ "Enable Calls Outside Game?</html>",
					"Developer Option — Enable Calls Outside Game",
					JOptionPane.YES_NO_OPTION,
					JOptionPane.WARNING_MESSAGE);
				if (result != JOptionPane.YES_OPTION)
				{
					configManager.setConfiguration("zealgains", "enableCallsOutsideGame", false);
				}
			});
		}
		else if (event.getKey().equals("skipFragmentCheck") && "true".equals(event.getNewValue()))
		{
			SwingUtilities.invokeLater(() ->
			{
				int result = JOptionPane.showConfirmDialog(null,
					"<html><b>Skip Fragment Count Check</b><br><br>"
					+ "By default, dump notifications are suppressed when you have <b>fewer than 16 Soul Fragments</b> in your inventory — "
					+ "the minimum needed for a dump.<br><br>"
					+ "When enabled, dump notifications will fire <b>regardless of how many fragments you have</b>.<br><br>"
					+ "This is a developer/testing option — disable it for normal play.<br><br>"
					+ "Enable Skip Fragment Count Check?</html>",
					"Developer Option — Skip Fragment Count Check",
					JOptionPane.YES_NO_OPTION,
					JOptionPane.WARNING_MESSAGE);
				if (result != JOptionPane.YES_OPTION)
				{
					configManager.setConfiguration("zealgains", "skipFragmentCheck", false);
				}
			});
		}
		else if (event.getKey().equals("alwaysShowDumpOverlay") && "true".equals(event.getNewValue()))
		{
			SwingUtilities.invokeLater(() ->
			{
				int result = JOptionPane.showConfirmDialog(null,
					"<html><b>Always Show Dump Overlay</b><br><br>"
					+ "By default the <b>DO NOT DUMP</b> obelisk overlay is hidden unless you have an active kill call, "
					+ "so runners and spectators do not see it.<br><br>"
					+ "When enabled, the overlay will show <b>regardless of whether you have a call</b>.<br><br>"
					+ "This is a developer/testing option — disable it for normal play.<br><br>"
					+ "Enable Always Show Dump Overlay?</html>",
					"Developer Option — Always Show Dump Overlay",
					JOptionPane.YES_NO_OPTION,
					JOptionPane.WARNING_MESSAGE);
				if (result != JOptionPane.YES_OPTION)
				{
					configManager.setConfiguration("zealgains", "alwaysShowDumpOverlay", false);
				}
			});
		}
	}

	// --- FC EVENT LISTENERS ---

	@Subscribe
	public void onFriendsChatChanged(FriendsChatChanged event)
	{
		// Recompute when joining or leaving an FC entirely
		cachedMajorityWorld = getMajorityWorld(client.getFriendsChatManager());
	}

	@Subscribe
	public void onFriendsChatMemberJoined(FriendsChatMemberJoined event)
	{
		cachedMajorityWorld = getMajorityWorld(client.getFriendsChatManager());

		if (!config.enableBanList() || bannedPlayers.isEmpty()) return;
		String name = cleanOsrsName(event.getMember().getName());
		if (bannedPlayers.contains(name))
		{
			String displayName = Text.removeTags(event.getMember().getName());
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
					"<col=" + colorToHex(config.alertBanColor()) + ">Zealgains: \"" + displayName + "\" was found on the banlist</col>", null);
			notifier.notify(config.banListNotification(), "\"" + displayName + "\" was found on the banlist");
		}
	}

	@Subscribe
	public void onFriendsChatMemberLeft(FriendsChatMemberLeft event)
	{
		cachedMajorityWorld = getMajorityWorld(client.getFriendsChatManager());

		if (!isInSoulWarsGame()) return;

		String leavingName = Text.removeTags(event.getMember().getName());
		String leavingStd = Text.standardize(leavingName);

		StringBuilder calls = new StringBuilder();
		for (Map.Entry<Integer, String> entry : redKills.entrySet())
		{
			if (Text.standardize(entry.getValue()).equals(leavingStd))
				calls.append("R").append(entry.getKey()).append(" ");
		}
		for (Map.Entry<Integer, String> entry : blueKills.entrySet())
		{
			if (Text.standardize(entry.getValue()).equals(leavingStd))
				calls.append("B").append(entry.getKey()).append(" ");
		}

		if (calls.length() > 0 && disconnectAlerted.add(leavingStd))
		{
			sendAlert(leavingName + " left the Friends Chat — had calls: " + calls.toString().trim());
		}
	}

	// --- DUMP REMINDER ---

	@Subscribe
	public void onGameTick(GameTick event)
	{
		int seconds = getGameTimeRemaining();

		// Lobby detection via widget 434.0 — independent of the game timer
		Widget lobbyRootW = client.getWidget(434, 0);
		boolean inLobby = lobbyRootW != null && !lobbyRootW.isHidden();

		if (inLobby)
		{
			// Track peak player count live from the lobby counter widget
			Widget countW = client.getWidget(434, 6);
			if (countW != null && !countW.isHidden() && countW.getText() != null)
			{
				String text = countW.getText().trim();
				int slash = text.indexOf('/');
				String numStr = slash >= 0 ? text.substring(0, slash).trim() : text;
				try
				{
					int parsed = Integer.parseInt(numStr);
					if (parsed > lobbyPlayerCount) lobbyPlayerCount = parsed;
				}
				catch (NumberFormatException ignored) {}
			}

			// Watch the lobby countdown (434.7): freeze count when it hits 0, detect left-behind via reset
			Widget timerW = client.getWidget(434, 7);
			if (timerW != null && !timerW.isHidden() && timerW.getText() != null)
			{
				int timerSecs = parseLobbyTimer(timerW.getText().trim());
				if (timerSecs >= 0)
				{
					if (!lobbyCountSet && timerSecs == 0)
					{
						// Timer hit 0 — game is starting, freeze the peak count
						lobbyCountSet = true;
					}
					else if (lobbyCountSet && prevLobbyTimerSeconds >= 0 && timerSecs > prevLobbyTimerSeconds + 15)
					{
						// Timer jumped up — we were left behind, a new game is forming; reset for next cycle
						lobbyPlayerCount = 0;
						lobbyCountSet = false;
					}
					prevLobbyTimerSeconds = timerSecs;
				}
			}
		}
		else
		{
			prevLobbyTimerSeconds = -1;
			// Fallback: if we entered the game without the timer hitting exactly 0, freeze whatever we have
			if (!lobbyCountSet && lobbyPlayerCount > 0) lobbyCountSet = true;
		}

		if (seconds == -1) return;

		// Capture live game state so the end-of-game summary has accurate final values
		lastKnownGameSeconds = seconds;
		Widget redScoreW = client.getWidget(375, 12);
		Widget blueScoreW = client.getWidget(375, 11);
		if (redScoreW != null) lastGameRedScore = Math.max(0, parseWidgetValue(redScoreW.getText()));
		if (blueScoreW != null) lastGameBlueScore = Math.max(0, parseWidgetValue(blueScoreW.getText()));

		if (panel != null)
			panel.updateGameStatus(config.showGameStatus() ? seconds : -1, lastGameRedScore, lastGameBlueScore);

		if (trackedObelisk == null)
		{
			scanForObelisk();
		}

		checkAvatarDump();
	}

	// --- VARBIT HANDLER ---

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (event.getVarbitId() != VARBIT_SOUL_WARS_TEAM) return;
		switch (event.getValue())
		{
			case 1: varbitTeam = "b"; break; // assigned to blue team
			case 2: varbitTeam = "r"; break; // assigned to red team
			default:
				varbitTeam = null;
				if (config.autoClear())
				{
					if (config.showGameSummary()) printGameSummary();
					resetKills();
				}
				break;
		}
	}

	// --- OBELISK TRACKING ---

	private void scanForObelisk()
	{
		Scene scene = client.getScene();
		Tile[][][] tiles = scene.getTiles();
		int plane = client.getPlane();
		for (Tile[] row : tiles[plane])
		{
			for (Tile tile : row)
			{
				if (tile == null) continue;
				for (GameObject obj : tile.getGameObjects())
				{
					if (obj == null) continue;
					int id = obj.getId();
					if (id == OBELISK_ID_NONE || id == OBELISK_ID_BLUE || id == OBELISK_ID_RED)
					{
						trackedObelisk = obj;
						return;
					}
				}
			}
		}
	}

	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned event)
	{
		int id = event.getGameObject().getId();
		if (id == OBELISK_ID_NONE || id == OBELISK_ID_BLUE || id == OBELISK_ID_RED)
		{
			trackedObelisk = event.getGameObject();
		}
	}

	@Subscribe
	public void onGameObjectDespawned(GameObjectDespawned event)
	{
		if (event.getGameObject() == trackedObelisk)
		{
			trackedObelisk = null;
		}
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		if (!config.preventOffColorDumps() || !obeliskWarnActive) return;

		String option = event.getOption().toLowerCase();
		if (!option.contains("sacrifice")) return;

		// Match by object ID, or fall back to target name if the ID isn't exposed
		int id = event.getIdentifier();
		boolean isObelisk = (id == OBELISK_ID_NONE || id == OBELISK_ID_BLUE || id == OBELISK_ID_RED)
				|| Text.removeTags(event.getTarget()).toLowerCase().contains("obelisk");

		if (isObelisk)
		{
			event.getMenuEntry().setDeprioritized(true);
		}
	}

	// --- CHAT MESSAGE HANDLER ---

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		String message = Text.removeTags(event.getMessage()).toLowerCase();

		// Auto-reset on game end — print summary first
		if (event.getType() == ChatMessageType.GAMEMESSAGE || event.getType() == ChatMessageType.SPAM)
		{
			if (message.contains("the game has ended") || message.contains("zeal token")
					|| (message.contains("you received") && message.contains("zeal")))
			{
				if (config.autoClear())
				{
					if (config.showGameSummary()) printGameSummary();
					resetKills();
				}
				return;
			}
		}

		if (event.getType() != ChatMessageType.FRIENDSCHAT) return;

		// Only track calls while inside a Soul Wars game (bypassed in dev mode)
		int secondsRemaining = getGameTimeRemaining();
		if (secondsRemaining == -1)
		{
			if (!config.enableCallsOutsideGame()) return;
			secondsRemaining = 0;
		}

		String sender = Text.removeTags(event.getName());

		// Rank broadcast reset — Captain+ only, 15-second cooldown
		if (message.trim().startsWith("!zgreset"))
		{
			FriendsChatManager fcm = client.getFriendsChatManager();
			if (fcm != null)
			{
				FriendsChatRank senderRank = FriendsChatRank.UNRANKED;
				for (FriendsChatMember member : fcm.getMembers())
				{
					if (Text.standardize(member.getName()).equals(Text.standardize(sender)))
					{
						senderRank = member.getRank();
						break;
					}
				}
				if (senderRank.getValue() >= FriendsChatRank.CAPTAIN.getValue())
				{
					long now = System.currentTimeMillis();
					if (now - lastBroadcastReset >= 15_000)
					{
						lastBroadcastReset = now;
						String argsStr = message.trim().substring("!zgreset".length()).trim();
						if (!argsStr.isEmpty())
						{
							String[] broadcastArgs = argsStr.split("\\s+");
							String result = applyTargetedReset(broadcastArgs);
							if (result != null)
							{
								client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
									"<col=" + colorToHex(config.alertCallColor()) + ">Zealgains: " + sender + " reset " + result + "</col>", null);
							}
						}
					}
				}
			}
			return;
		}

		// Runner callouts — decode <gt>/<lt> before tag-stripping so > survives
		String runnerMsg = Text.removeTags(event.getMessage().replace("<gt>", ">").replace("<lt>", "<"))
				.toLowerCase().replaceAll("\\s+", "");
		Matcher runnerMatcher = runnerPattern.matcher(runnerMsg);
		if (runnerMatcher.find())
		{
			String runnerTeam = (runnerMatcher.group(1) != null ? runnerMatcher.group(1) : runnerMatcher.group(2)).toLowerCase();
			if (runnerTeam.equals("r")) redRunners.add(sender);
			else blueRunners.add(sender);
			if (panel != null) panel.updateRunners(redRunners, blueRunners);
			return;
		}

		// Noise filter
		if (message.contains("?") || message.contains("need") || message.contains("open") || message.contains("who") || message.contains("call") || message.contains("want") || message.contains("you") || message.contains("getting") || message.contains("go get") || message.contains("grab") || message.contains("grabbing"))
		{
			return;
		}

		// Collect every call-slot mention from anywhere in the message
		Matcher matcher = callPattern.matcher(message);
		String callTeam = null;
		StringBuilder callSlots = new StringBuilder();
		while (matcher.find())
		{
			String t = matcher.group(1).toLowerCase();
			if (callTeam == null) callTeam = t;
			else if (!callTeam.equals(t)) return; // mixed teams in one message — reject
			callSlots.append(matcher.group(2));
		}
		if (callTeam == null) return;

		// Majority world check (uses cached value updated by FC member events)
		if (cachedMajorityWorld != -1 && client.getWorld() != cachedMajorityWorld) return;

		// Cross-world fake call check
		if (config.alertCrossWorld())
		{
			FriendsChatManager fcm = client.getFriendsChatManager();
			if (fcm != null)
			{
				String standardizedSender = Text.standardize(sender).replace('_', ' ');
				for (FriendsChatMember member : fcm.getMembers())
				{
					if (Text.standardize(member.getName()).replace('_', ' ').equals(standardizedSender))
					{
						if (member.getWorld() != client.getWorld())
						{
							sendAlert(sender + " called a fake call from W" + member.getWorld());
							return;
						}
						break;
					}
				}
			}
		}

		String kills = callSlots.toString().replaceAll("[^1-5]", "");
		processCall(callTeam, kills, sender, secondsRemaining);
	}

	// --- COMMANDS ---

	@Subscribe
	public void onCommandExecuted(CommandExecuted event)
	{
		if (event.getCommand().equalsIgnoreCase("zgreset"))
		{
			String[] args = event.getArguments();
			if (args == null || args.length == 0)
			{
				SwingUtilities.invokeLater(() ->
				{
					int choice = JOptionPane.showConfirmDialog(
							null,
							"Are you sure you want to reset the entire board?",
							"Zealgains — Confirm Reset",
							JOptionPane.YES_NO_OPTION,
							JOptionPane.WARNING_MESSAGE
					);
					if (choice == JOptionPane.YES_OPTION)
					{
						clientThread.invokeLater(() ->
						{
							resetKills();
							client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Zealgains: All calls reset.", null);
						});
					}
				});
				return;
			}

			String result = applyTargetedReset(args);
			if (result == null)
			{
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Zealgains: No matching calls found to reset.", null);
			}
			else
			{
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Zealgains: Reset " + result, null);
			}
		}
		else if (event.getCommand().equalsIgnoreCase("zgteam"))
		{
			int varbit = client.getVarbitValue(VARBIT_SOUL_WARS_TEAM);
			String varbitLabel = varbit == 1 ? "blue (1)" : varbit == 2 ? "red (2)" : "none (0)";

			String localName = client.getLocalPlayer() != null
					? Text.removeTags(client.getLocalPlayer().getName()) : null;
			String callTeam = localName != null ? getSenderTeam(localName) : null;

			String resolved = getLocalTeam();

			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
					"<col=ffff00>Zealgains Team Debug:</col>", null);
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
					"  Varbit 3815: " + varbitLabel, null);
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
					"  Cached varbitTeam: " + (varbitTeam != null ? varbitTeam : "null"), null);
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
					"  Call-history team: " + (callTeam != null ? callTeam : "null"), null);
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
					"  Resolved team: " + (resolved != null ? resolved : "null (alerts suppressed)"), null);
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
					"  obeliskWarnActive: " + obeliskWarnActive, null);
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
					"  trackedObelisk: " + (trackedObelisk != null ? "found" : "null"), null);
		}
		else if (event.getCommand().equalsIgnoreCase("zgsync"))
		{
			if (!config.enableBanList() || config.banListUrl().trim().isEmpty())
			{
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Zealgains: Ban list feature is disabled or URL is missing.", null);
				return;
			}
			long now = System.currentTimeMillis();
			long elapsed = now - lastBanListFetch;
			if (elapsed < BAN_LIST_COOLDOWN_MS)
			{
				long secondsLeft = (BAN_LIST_COOLDOWN_MS - elapsed) / 1000;
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Zealgains: Please wait " + secondsLeft + "s before syncing again.", null);
				return;
			}
			lastBanListFetch = now;
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Zealgains: Fetching latest ban list...", null);
			fetchBanList();
		}
	}

	// --- CALL PROCESSING ---

	private void processCall(String team, String kills, String sender, int secondsRemaining)
	{
		// Team lock: a player may only call for one team
		String lockedTeam = getSenderTeam(sender);
		if (lockedTeam != null && !lockedTeam.equals(team))
		{
			sendAlert(sender + " is locked to " + lockedTeam.toUpperCase() + " team and cannot call for " + team.toUpperCase() + ".");
			return;
		}

		Map<Integer, String> targetMap = team.equals("r") ? redKills : blueKills;
		boolean overCallAlertTriggered = false;

		for (char c : kills.toCharArray())
		{
			int killNumber = Character.getNumericValue(c);

			if (killNumber == 5 && !validateKill5(team, sender, secondsRemaining)) continue;

			// Sequential check: kill N requires kill N-1 to be claimed first — reject out-of-order calls
			if (killNumber > 1 && !targetMap.containsKey(killNumber - 1))
			{
				sendAlert(sender + " called " + team.toUpperCase() + killNumber + " out of order — " + team.toUpperCase() + (killNumber - 1) + " must be claimed first.");
				continue;
			}

			// Before 12:00, cap at 3 kills — reject and free the slot for others
			if (secondsRemaining > 720 && getKillsClaimedBy(sender) >= 3)
			{
				if (!overCallAlertTriggered)
				{
					sendAlert(sender + " called more than 3 kills before 12:00 — extra calls ignored.");
					overCallAlertTriggered = true;
				}
				continue;
			}

			String existing = targetMap.putIfAbsent(killNumber, sender);
			if (existing == null)
			{
				if (killNumber == 5) kill5Tick = client.getTickCount();
			}
			else
			{
				// Slot already taken — only alert if it's the local player making the duplicate
				alertLocalPlayerConflict(team, killNumber, sender, existing, targetMap);
			}
		}

		if (panel != null) panel.updateKills(redKills, blueKills);
	}

	private boolean validateKill5(String team, String sender, int secondsRemaining)
	{
		if (team.equals("b"))
		{
			if (secondsRemaining > 720)
			{
				sendAlert("B5 CALLED TOO EARLY BY: " + sender);
				return false;
			}
			if (redKills.containsKey(5))
			{
				if (client.getTickCount() == kill5Tick)
				{
					sendAlert("r5 and b5 called at the same time, " + redKills.get(5) + " (r5) wins the call");
				}
				return false;
			}
		}
		else
		{
			if (blueKills.containsKey(5))
			{
				if (client.getTickCount() == kill5Tick)
				{
					blueKills.remove(5);
					sendAlert("r5 and b5 called at the same time, " + sender + " (r5) wins the call");
				}
				else
				{
					sendAlert("R5 called by " + sender + " is invalid — B5 was already called by " + blueKills.get(5));
					return false;
				}
			}
		}
		return true;
	}

	private void alertLocalPlayerConflict(String team, int killNumber, String sender, String existingClaimer, Map<Integer, String> targetMap)
	{
		if (client.getLocalPlayer() == null) return;
		String localName = client.getLocalPlayer().getName();
		if (localName == null) return;
		if (!Text.standardize(sender).equals(Text.standardize(localName))) return;

		String teamLabel = team.toUpperCase();
		int nextSlot = killNumber + 1;
		while (nextSlot <= 5 && targetMap.containsKey(nextSlot)) nextSlot++;

		String suggestion = nextSlot <= 5 ? teamLabel + nextSlot : "a different kill";
		sendAlert(teamLabel + killNumber + " is already taken by " + existingClaimer + " — " + sender + ", please call " + suggestion);
	}

	// --- GAME SUMMARY ---

	private void printGameSummary()
	{
		if (redKills.isEmpty() && blueKills.isEmpty()) return;

		StringBuilder red = new StringBuilder();
		for (int i = 1; i <= 5; i++)
		{
			if (redKills.containsKey(i))
				red.append("R").append(i).append(": ").append(redKills.get(i)).append("  ");
		}

		StringBuilder blue = new StringBuilder();
		for (int i = 1; i <= 5; i++)
		{
			if (blueKills.containsKey(i))
				blue.append("B").append(i).append(": ").append(blueKills.get(i)).append("  ");
		}

		String timeStr = lastKnownGameSeconds >= 0
				? String.format("%d:%02d", lastKnownGameSeconds / 60, lastKnownGameSeconds % 60)
				: "unknown";

		// Prefer live widget read so the score is accurate even if the interface closes on the same tick as game end
		Widget redScoreW  = client.getWidget(375, 12);
		Widget blueScoreW = client.getWidget(375, 11);
		int redScore  = (redScoreW  != null) ? Math.max(0, parseWidgetValue(redScoreW.getText()))  : lastGameRedScore;
		int blueScore = (blueScoreW != null) ? Math.max(0, parseWidgetValue(blueScoreW.getText())) : lastGameBlueScore;

		String header  = "<col=" + colorToHex(config.summaryHeaderColor()) + ">=== Zealgains: Game Summary ===</col>";
		String redLine = red.length() > 0   ? "<col=" + colorToHex(config.summaryRedColor())  + ">Red Calls — "  + red.toString().trim()  + "</col>" : null;
		String blueLine = blue.length() > 0 ? "<col=" + colorToHex(config.summaryBlueColor()) + ">Blue Calls — " + blue.toString().trim() + "</col>" : null;
		String scoreLine = "<col=" + colorToHex(config.summaryScoreColor()) + ">Final Score — Red " + redScore + " / Blue " + blueScore
				+ "  -  Time remaining: " + timeStr + "</col>";

		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", header, null);
		if (redLine  != null) client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", redLine,  null);
		if (blueLine != null) client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", blueLine, null);
		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", scoreLine, null);
	}

	// --- RESET ---

	public void resetKills()
	{
		redKills.clear();
		blueKills.clear();
		redRunners.clear();
		blueRunners.clear();
		disconnectAlerted.clear();

		kill5Tick = -1;
		maxBlueHealth = 0; maxBlueStrength = 0;
		maxRedHealth = 0; maxRedStrength = 0;
		blueAvatarDumpAlerted = false; redAvatarDumpAlerted = false;
		blueEarlyDumpWarned = false; redEarlyDumpWarned = false;
		trackedObelisk = null; obeliskWarnActive = false;
		lastGameRedScore = 0; lastGameBlueScore = 0; lastKnownGameSeconds = -1;
		lobbyPlayerCount = 0; lobbyCountSet = false; prevLobbyTimerSeconds = -1;

		if (panel != null)
		{
			panel.updateKills(redKills, blueKills);
			panel.updateRunners(redRunners, blueRunners);
			panel.updateGameStatus(-1, 0, 0);
		}
	}

	// --- HELPERS ---

	private int getMajorityWorld(FriendsChatManager fcm)
	{
		if (fcm == null || fcm.getCount() == 0) return -1;

		Map<Integer, Integer> worldCounts = new HashMap<>();
		for (FriendsChatMember member : fcm.getMembers())
		{
			int world = member.getWorld();
			if (world > 0) worldCounts.merge(world, 1, Integer::sum);
		}

		return worldCounts.entrySet().stream()
				.max(Map.Entry.comparingByValue())
				.map(Map.Entry::getKey)
				.orElse(-1);
	}

	// Applies a targeted reset for the given args (e.g. ["r34", "b2"]).
	// Returns a formatted result string like "R3 R4 — B1 moved to B3" or null if nothing matched.
	private String applyTargetedReset(String[] args)
	{
		java.util.regex.Pattern resetPattern = java.util.regex.Pattern.compile("(?i)^([rb])([1-5]+)$");
		StringBuilder summary = new StringBuilder();
		Set<String> affectedTeams = new LinkedHashSet<>();
		for (String arg : args)
		{
			java.util.regex.Matcher m = resetPattern.matcher(arg.trim());
			if (!m.matches()) continue;
			String team = m.group(1).toLowerCase();
			Map<Integer, String> targetMap = team.equals("r") ? redKills : blueKills;
			for (char c : m.group(2).toCharArray())
			{
				int killNum = Character.getNumericValue(c);
				if (targetMap.remove(killNum) != null)
				{
					if (killNum == 5) kill5Tick = -1;
					summary.append(team.toUpperCase()).append(killNum).append(" ");
					affectedTeams.add(team);
				}
			}
		}
		String result = summary.toString().trim();
		if (result.isEmpty()) return null;
		StringBuilder reshuffleMsg = new StringBuilder();
		for (String t : affectedTeams) reshuffleTeam(t, reshuffleMsg);
		if (panel != null) panel.updateKills(redKills, blueKills);
		return reshuffleMsg.length() > 0 ? result + " — " + reshuffleMsg : result;
	}

	private void sendAlert(String message)
	{
		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "<col=" + colorToHex(config.alertCallColor()) + ">Zealgains Alert: " + message + "</col>", null);
		notifier.notify(config.enableNotifications(), message);
	}

	private int getKillsClaimedBy(String sender)
	{
		int total = 0;
		for (String player : redKills.values()) if (player.equals(sender)) total++;
		for (String player : blueKills.values()) if (player.equals(sender)) total++;
		return total;
	}

	private void checkAvatarDump()
	{
		Widget blueHealthW   = client.getWidget(375, 15);
		Widget blueStrengthW = client.getWidget(375, 19);
		Widget redHealthW    = client.getWidget(375, 16);
		Widget redStrengthW  = client.getWidget(375, 20);

		if (blueHealthW == null || blueStrengthW == null ||
			redHealthW == null || redStrengthW == null) return;

		int blueHealth   = parseWidgetValue(blueHealthW.getText());
		int blueStrength = parseWidgetValue(blueStrengthW.getText());
		int redHealth    = parseWidgetValue(redHealthW.getText());
		int redStrength  = parseWidgetValue(redStrengthW.getText());

		if (blueHealth < 0 || blueStrength < 0 || redHealth < 0 || redStrength < 0) return;

		// Track observed maximums so full HP can be detected without hardcoding
		if (blueHealth > maxBlueHealth)     maxBlueHealth   = blueHealth;
		if (blueStrength > maxBlueStrength) maxBlueStrength = blueStrength;
		if (redHealth > maxRedHealth)       maxRedHealth    = redHealth;
		if (redStrength > maxRedStrength)   maxRedStrength  = redStrength;

		if (maxBlueHealth == 0 || maxBlueStrength == 0 || maxRedHealth == 0 || maxRedStrength == 0) return;

		// Derive local team — varbit 3815 is authoritative; falls back to call history
		String localTeam = getLocalTeam();

		// Determine which avatar alerts to show based on team filter
		boolean showBlueAlert, showRedAlert;
		if (config.overrideCallFilter() || config.dumpAlertMode() == ZealgainsConfig.DumpAlertMode.ALL)
		{
			showBlueAlert = true;
			showRedAlert  = true;
		}
		else
		{
			// AUTO — unknown team = suppress all until a call is made
			showBlueAlert = "r".equals(localTeam);
			showRedAlert  = "b".equals(localTeam);
		}

		// Obelisk warning — true when dumping here would be wasted or harmful
		// White obelisk: never safe regardless of team
		boolean obeliskIsUncaptured = trackedObelisk != null && trackedObelisk.getId() == OBELISK_ID_NONE;
		// Opposite-color obelisk: red player on blue obelisk, or blue player on red obelisk
		boolean obeliskIsWrongColor = trackedObelisk != null && localTeam != null
				&& (("r".equals(localTeam) && trackedObelisk.getId() == OBELISK_ID_BLUE)
				||  ("b".equals(localTeam) && trackedObelisk.getId() == OBELISK_ID_RED));

		if (obeliskIsUncaptured || obeliskIsWrongColor)
		{
			obeliskWarnActive = true;
		}
		else if (localTeam == null)
		{
			obeliskWarnActive = false;
		}
		else if ("r".equals(localTeam))
		{
			obeliskWarnActive = !(blueHealth >= maxBlueHealth && blueStrength >= maxBlueStrength);
		}
		else
		{
			obeliskWarnActive = !(redHealth >= maxRedHealth && redStrength >= maxRedStrength);
		}

		// Fragment gate — suppress alert if local player doesn't have enough to dump
		boolean hasEnoughFragments = config.skipFragmentCheck() || getFragmentCount() >= 16;

		// Blue avatar at full → Red team should dump
		boolean blueReady = blueHealth >= maxBlueHealth && blueStrength >= maxBlueStrength;
		if (blueReady && !blueAvatarDumpAlerted && showBlueAlert && hasEnoughFragments)
		{
			Widget redKillsW = client.getWidget(375, 12);
			int nextRedKill = (redKillsW != null ? Math.max(0, parseWidgetValue(redKillsW.getText())) : 0) + 1;
			// Kill 5 is gated behind the dump window (5:00 / 4:45) — keep checking until it opens
			if (nextRedKill == 5 && !isDumpWindowOpen())
			{
				// If 40+ people, warn at 5:05 not to dump at 5:00 — must wait until 4:45
				if (!blueEarlyDumpWarned)
				{
					FriendsChatManager fcm = client.getFriendsChatManager();
					int timeRemaining = getGameTimeRemaining();
					if (fcm != null && fcm.getCount() >= 40 && timeRemaining != -1 && timeRemaining <= 305)
					{
						String warn = "40+ people in FC — do NOT dump at 5:00! Wait until 4:45.";
						client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "<col=" + colorToHex(config.avatarAlertColor()) + ">Zealgains: " + warn + "</col>", null);
						notifier.notify(config.avatarAlerts(), warn);
						blueEarlyDumpWarned = true;
					}
				}
			}
			else
			{
				if (nextRedKill > 1)
				{
					String msg;
					if (nextRedKill == 5)
					{
						FriendsChatManager fcm = client.getFriendsChatManager();
						String timeStr = (fcm != null && fcm.getCount() >= 40) ? "4:45" : "5:00";
						msg = "5th dump for Red at " + timeStr + " — Dump the winning kill now!";
					}
					else
					{
						msg = "Blue avatar is ready for " + ordinal(nextRedKill) + " dump!";
					}
					client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "<col=" + colorToHex(config.avatarAlertColor()) + ">Zealgains: " + msg + "</col>", null);
					notifier.notify(config.avatarAlerts(), msg);
				}
				blueAvatarDumpAlerted = true;
			}
		}
		else if (!blueReady)
		{
			blueAvatarDumpAlerted = false;
			blueEarlyDumpWarned = false;
		}

		// Red avatar at full → Blue team should dump
		boolean redReady = redHealth >= maxRedHealth && redStrength >= maxRedStrength;
		if (redReady && !redAvatarDumpAlerted && showRedAlert && hasEnoughFragments)
		{
			Widget blueKillsW = client.getWidget(375, 11);
			int nextBlueKill = (blueKillsW != null ? Math.max(0, parseWidgetValue(blueKillsW.getText())) : 0) + 1;
			// Kill 5 is gated behind the dump window (5:00 / 4:45) — keep checking until it opens
			if (nextBlueKill == 5 && !isDumpWindowOpen())
			{
				// If 40+ people, warn at 5:05 not to dump at 5:00 — must wait until 4:45
				if (!redEarlyDumpWarned)
				{
					FriendsChatManager fcm = client.getFriendsChatManager();
					int timeRemaining = getGameTimeRemaining();
					if (fcm != null && fcm.getCount() >= 40 && timeRemaining != -1 && timeRemaining <= 305)
					{
						String warn = "40+ people in FC — do NOT dump at 5:00! Wait until 4:45.";
						client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "<col=" + colorToHex(config.avatarAlertColor()) + ">Zealgains: " + warn + "</col>", null);
						notifier.notify(config.avatarAlerts(), warn);
						redEarlyDumpWarned = true;
					}
				}
			}
			else
			{
				if (nextBlueKill > 1)
				{
					String msg;
					if (nextBlueKill == 5)
					{
						FriendsChatManager fcm = client.getFriendsChatManager();
						String timeStr = (fcm != null && fcm.getCount() >= 40) ? "4:45" : "5:00";
						msg = "5th dump for Blue at " + timeStr + " — Dump the winning kill now!";
					}
					else
					{
						msg = "Red avatar is ready for " + ordinal(nextBlueKill) + " dump!";
					}
					client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "<col=" + colorToHex(config.avatarAlertColor()) + ">Zealgains: " + msg + "</col>", null);
					notifier.notify(config.avatarAlerts(), msg);
				}
				redAvatarDumpAlerted = true;
			}
		}
		else if (!redReady)
		{
			redAvatarDumpAlerted = false;
			redEarlyDumpWarned = false;
		}
	}

	private String ordinal(int n)
	{
		switch (n)
		{
			case 1:  return "1st";
			case 2:  return "2nd";
			case 3:  return "3rd";
			default: return n + "th";
		}
	}

	private String colorToHex(Color c)
	{
		return String.format("%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
	}

	// Returns "r", "b", or null. Reads varbit 3815 live (handles plugin load mid-game),
	// caches result in varbitTeam, then falls back to call history.
	private String getLocalTeam()
	{
		if (varbitTeam == null)
		{
			int v = client.getVarbitValue(VARBIT_SOUL_WARS_TEAM);
			if (v == 1) varbitTeam = "b";
			else if (v == 2) varbitTeam = "r";
		}
		if (varbitTeam != null) return varbitTeam;
		String localName = client.getLocalPlayer() != null ? Text.removeTags(client.getLocalPlayer().getName()) : null;
		return localName != null ? getSenderTeam(localName) : null;
	}

	private int getFragmentCount()
	{
		ItemContainer inv = client.getItemContainer(InventoryID.INVENTORY);
		if (inv == null) return 0;
		int count = 0;
		for (Item item : inv.getItems())
		{
			if (item != null && item.getId() == SOUL_FRAGMENT_ITEM_ID)
				count += item.getQuantity();
		}
		return count;
	}

	private boolean isDumpWindowOpen()
	{
		FriendsChatManager fcm = client.getFriendsChatManager();
		int memberCount = fcm != null ? fcm.getCount() : 0;
		int threshold = memberCount >= 40 ? 285 : 300;
		int timeRemaining = getGameTimeRemaining();
		return timeRemaining != -1 && timeRemaining <= threshold;
	}

	private int parseWidgetValue(String text)
	{
		if (text == null || text.isEmpty()) return -1;
		String cleaned = Text.removeTags(text).trim();
		StringBuilder digits = new StringBuilder();
		for (char c : cleaned.toCharArray())
		{
			if (Character.isDigit(c)) digits.append(c);
			else if (digits.length() > 0) break;
		}
		if (digits.length() == 0) return -1;
		try { return Integer.parseInt(digits.toString()); }
		catch (NumberFormatException ignored) { return -1; }
	}

	// Parses lobby countdown format "M:SS" → total seconds; returns -1 on invalid input
	private int parseLobbyTimer(String text)
	{
		if (text == null || text.isEmpty() || text.equals("-")) return -1;
		String cleaned = Text.removeTags(text).trim();
		int colon = cleaned.indexOf(':');
		try
		{
			if (colon > 0)
			{
				int mins = Integer.parseInt(cleaned.substring(0, colon).trim());
				int secs = Integer.parseInt(cleaned.substring(colon + 1).trim());
				return mins * 60 + secs;
			}
			return Integer.parseInt(cleaned);
		}
		catch (NumberFormatException ignored) { return -1; }
	}

	private void reshuffleTeam(String team, StringBuilder alertMsg)
	{
		Map<Integer, String> targetMap = team.equals("r") ? redKills : blueKills;
		String T = team.toUpperCase();

		// Collect remaining callers in slot order
		int[] oldSlots = new int[5];
		String[] callers = new String[5];
		int count = 0;
		for (int i = 1; i <= 5; i++)
		{
			String caller = targetMap.get(i);
			if (caller != null) { oldSlots[count] = i; callers[count] = caller; count++; }
		}

		if (count == 0) return;

		// Check for gaps
		boolean hasGaps = false;
		for (int i = 0; i < count; i++)
			if (oldSlots[i] != i + 1) { hasGaps = true; break; }
		if (!hasGaps) return;

		// Rebuild compacted from slot 1
		targetMap.clear();
		StringBuilder moves = new StringBuilder();
		for (int i = 0; i < count; i++)
		{
			int newSlot = i + 1;
			targetMap.put(newSlot, callers[i]);
			if (oldSlots[i] != newSlot)
			{
				if (oldSlots[i] == 5) kill5Tick = -1;
				if (moves.length() > 0) moves.append(", ");
				moves.append(T).append(oldSlots[i]).append(" moved to ").append(T).append(newSlot);
			}
		}

		// Open slots in compact form e.g. "R45"
		// Blue caps at 4 unless timer is at/below 12:00 and R5 hasn't been claimed
		int timeRemaining = getGameTimeRemaining();
		int maxSlot = (team.equals("b") && (timeRemaining == -1 || timeRemaining > 720 || redKills.containsKey(5))) ? 4 : 5;
		StringBuilder open = new StringBuilder(T);
		for (int i = count + 1; i <= maxSlot; i++) open.append(i);

		if (alertMsg.length() > 0) alertMsg.append(" | ");
		alertMsg.append(moves);
		if (open.length() > 1) alertMsg.append(", need ").append(open);
	}

	private String getSenderTeam(String sender)
	{
		for (String player : redKills.values()) if (player.equals(sender)) return "r";
		for (String player : blueKills.values()) if (player.equals(sender)) return "b";
		return null;
	}

	private String cleanOsrsName(String input)
	{
		if (input == null) return "";
		return Text.standardize(input)
				.replace('_', ' ')
				.replace('-', ' ')
				.replaceAll("[^a-z0-9 ]", "")
				.trim();
	}

	// --- WIDGET / TIMER ---

	public int getGameTimeRemaining()
	{
		Widget timeWidget = client.getWidget(375, 23);
		if (timeWidget == null || timeWidget.isHidden()) return -1;

		String rawText = timeWidget.getText();
		if (rawText == null || rawText.isEmpty()) return -1;

		String text = Text.removeTags(rawText).trim();
		if (!text.matches("\\d+:\\d{2}")) return -1;

		try
		{
			String[] parts = text.split(":");
			return (Integer.parseInt(parts[0]) * 60) + Integer.parseInt(parts[1]);
		}
		catch (NumberFormatException ignored) { return -1; }
	}

	public boolean isInSoulWarsGame()
	{
		return getGameTimeRemaining() != -1;
	}

	// --- CHAT HIGHLIGHT ---

	@Subscribe
	public void onClientTick(ClientTick event)
	{
		if (!config.pmCheckerHighlight() && !config.highlightOnFl() && !config.enableBanList()) return;
		refreshChatHighlights();
	}

	private void refreshChatHighlights()
	{
		Widget chatList = client.getWidget(InterfaceID.ChatchannelCurrent.LIST);
		if (chatList == null || chatList.isHidden()) return;

		for (Widget child : chatList.getDynamicChildren())
		{
			String rawText = child.getText();
			if (rawText == null || rawText.isEmpty()) continue;
			if (rawText.startsWith("World ") || rawText.matches("^W\\d+$")) continue;

			String cleanName = Text.removeTags(rawText).trim();
			String std = cleanOsrsName(cleanName);

			java.awt.Color colorToSet = null;

			if (config.enableBanList() && bannedPlayers.contains(std))
				colorToSet = config.banListColor();
			else if (config.pmCheckerHighlight() && client.isFriended(cleanName, true))
				colorToSet = config.pmCheckerColor();
			else if (config.highlightOnFl() && client.isFriended(cleanName, false))
				colorToSet = config.flHighlightColor();

			if (colorToSet != null)
			{
				if (rawText.contains("<col=") || rawText.contains("</col>"))
				{
					child.setText(rawText.replaceAll("(?i)<col=[^>]+>", "").replace("</col>", ""));
				}
				child.setTextColor(colorToSet.getRGB());
			}
		}
	}

	// --- BAN LIST FETCH ---

	private void fetchBanList()
	{
		if (!config.enableBanList() || config.banListUrl().trim().isEmpty())
		{
			bannedPlayers.clear();
			return;
		}

		String baseUrl = config.banListUrl().trim();
		String requestUrl = baseUrl + (baseUrl.contains("?") ? "&v=" : "?v=") + System.currentTimeMillis();

		Request request = new Request.Builder()
				.url(requestUrl)
				.cacheControl(okhttp3.CacheControl.FORCE_NETWORK)
				.build();

		httpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.error("Zealgains: Failed to fetch ban list from provided URL", e);
				clientThread.invokeLater(() ->
						client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Zealgains: Failed to fetch ban list.", null));
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException
			{
				if (response.isSuccessful() && response.body() != null)
				{
					bannedPlayers.clear();
					String body = response.body().string();
					for (String token : body.split(","))
					{
						String trimmed = token.trim();
						if (trimmed.isEmpty()) continue;
						if (trimmed.startsWith("--")) continue;
						if (trimmed.contains("(Added:")) continue;
						if (trimmed.startsWith("<--")) continue;
						String name = cleanOsrsName(trimmed);
						if (!name.isEmpty()) bannedPlayers.add(name);
					}
					log.info("Zealgains: Successfully loaded {} banned players.", bannedPlayers.size());
					int count = bannedPlayers.size();
					clientThread.invokeLater(() -> {
						client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Zealgains: Successfully loaded " + count + " banned players.", null);
						refreshChatHighlights();
					});
				}
				else
				{
					clientThread.invokeLater(() ->
							client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Zealgains: Failed to load ban list. Response not successful.", null));
				}
				response.close();
			}
		});
	}

	@Provides
	ZealgainsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ZealgainsConfig.class);
	}
}
