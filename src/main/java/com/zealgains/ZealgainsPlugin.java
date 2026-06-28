package com.zealgains;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.FriendsChatManager;
import net.runelite.api.FriendsChatMember;
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
	private Notifier notifier;

	@Inject
	private ClientThread clientThread;

	@Inject
	private OkHttpClient httpClient;

	@Inject
	private ScheduledExecutorService executor;

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

	// Pending calls: slots rejected by sequential check, held per player until prerequisite is filled
	private final Map<String, Set<Integer>> pendingRedCalls = new HashMap<>();
	private final Map<String, Set<Integer>> pendingBlueCalls = new HashMap<>();

	// Avatar dump tracking
	private int maxBlueHealth = 0, maxBlueStrength = 0;
	private int maxRedHealth = 0, maxRedStrength = 0;
	private boolean blueAvatarDumpAlerted = false, redAvatarDumpAlerted = false;
	private boolean blueEarlyDumpWarned = false, redEarlyDumpWarned = false;

	// End-of-game summary tracking
	private int lastGameRedScore = 0;
	private int lastGameBlueScore = 0;
	private int lastKnownGameSeconds = -1;

	private long lastBanListFetch = 0;
	private static final long BAN_LIST_COOLDOWN_MS = 5 * 60 * 1000L;
	// Tracks the local player's Soul Wars team; resets to 0 on any exit (idle-kick, normal end, lobby)
	private static final int VARBIT_SOUL_WARS_TEAM = 3815;

	private final Pattern callPattern = Pattern.compile("(?i)^\\s*([rb])([rb1-5]+)");
	private final Pattern runnerPattern = Pattern.compile("(?i)^(?:[>^]([rb])|([rb])[>^])");

	// Getters for overlay and panel
	public Map<Integer, String> getRedKills() { return redKills; }
	public Map<Integer, String> getBlueKills() { return blueKills; }
	public Set<String> getRedRunners() { return redRunners; }
	public Set<String> getBlueRunners() { return blueRunners; }
	public int getRedScore() { return lastGameRedScore; }
	public int getBlueScore() { return lastGameBlueScore; }

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

		if (seconds == -1) return;

		// Capture live game state so the end-of-game summary has accurate final values
		lastKnownGameSeconds = seconds;
		Widget redScoreW = client.getWidget(375, 12);
		Widget blueScoreW = client.getWidget(375, 11);
		if (redScoreW != null) lastGameRedScore = Math.max(0, parseWidgetValue(redScoreW.getText()));
		if (blueScoreW != null) lastGameBlueScore = Math.max(0, parseWidgetValue(blueScoreW.getText()));

		if (panel != null)
			panel.updateGameStatus(config.showGameStatus() ? seconds : -1, lastGameRedScore, lastGameBlueScore);

		checkAvatarDump();
	}

	// --- VARBIT HANDLER ---

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (event.getVarbitId() != VARBIT_SOUL_WARS_TEAM) return;
		if (event.getValue() != 0) return;
		if (config.autoClear())
		{
			if (config.showGameSummary()) printGameSummary();
			resetKills();
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

		// Only track calls while inside a Soul Wars game
		int secondsRemaining = getGameTimeRemaining();
		if (secondsRemaining == -1) return;

		String sender = Text.removeTags(event.getName());
		String compressedMessage = message.replaceAll("\\s+", "");

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
		if (message.contains("?") || message.contains("need") || message.contains("open") || message.contains("who"))
		{
			return;
		}

		Matcher matcher = callPattern.matcher(compressedMessage);
		if (!matcher.find()) return;

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

		String team = matcher.group(1).toLowerCase();
		String kills = matcher.group(2).replaceAll("[^1-5]", "");
		processCall(team, kills, sender, secondsRemaining);
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
			if (result.isEmpty())
			{
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Zealgains: No matching calls found to reset.", null);
			}
			else
			{
				StringBuilder reshuffleMsg = new StringBuilder();
				for (String t : affectedTeams)
					reshuffleTeam(t, reshuffleMsg);

				if (panel != null) panel.updateKills(redKills, blueKills);
				String msg = "Zealgains: Reset " + result;
				if (reshuffleMsg.length() > 0) msg += "— " + reshuffleMsg;
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", msg, null);
			}
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
		Map<String, Set<Integer>> pendingMap = team.equals("r") ? pendingRedCalls : pendingBlueCalls;
		boolean overCallAlertTriggered = false;

		for (char c : kills.toCharArray())
		{
			int killNumber = Character.getNumericValue(c);

			if (killNumber == 5 && !validateKill5(team, sender, secondsRemaining)) continue;

			// Sequential check: kill N requires kill N-1 to be claimed first
			if (killNumber > 1 && !targetMap.containsKey(killNumber - 1))
			{
				// Hold it as pending — if they correct their order later, it auto-resolves
				if (!targetMap.containsKey(killNumber))
					pendingMap.computeIfAbsent(sender, k -> new LinkedHashSet<>()).add(killNumber);
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
				// Successfully claimed — flush any pending slots this player held for this team
				if (killNumber == 5) kill5Tick = client.getTickCount();
				resolvePendingCalls(team, sender, secondsRemaining);
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
		pendingRedCalls.clear();
		pendingBlueCalls.clear();
		kill5Tick = -1;
		maxBlueHealth = 0; maxBlueStrength = 0;
		maxRedHealth = 0; maxRedStrength = 0;
		blueAvatarDumpAlerted = false; redAvatarDumpAlerted = false;
		blueEarlyDumpWarned = false; redEarlyDumpWarned = false;
		lastGameRedScore = 0; lastGameBlueScore = 0; lastKnownGameSeconds = -1;

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

		// Determine which avatar alerts to show based on team filter
		boolean showBlueAlert, showRedAlert;
		if (config.overrideCallFilter())
		{
			showBlueAlert = true;
			showRedAlert  = true;
		}
		else
		{
			// Derive team from local player's own calls; unknown = suppress all until a call is made
			String localName = client.getLocalPlayer() != null ? Text.removeTags(client.getLocalPlayer().getName()) : null;
			String localTeam = localName != null ? getSenderTeam(localName) : null;
			showBlueAlert = "r".equals(localTeam);
			showRedAlert  = "b".equals(localTeam);
		}

		// Blue avatar at full → Red team should dump
		boolean blueReady = blueHealth >= maxBlueHealth && blueStrength >= maxBlueStrength;
		if (blueReady && !blueAvatarDumpAlerted && showBlueAlert)
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
		if (redReady && !redAvatarDumpAlerted && showRedAlert)
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

	private void resolvePendingCalls(String team, String sender, int secondsRemaining)
	{
		Map<String, Set<Integer>> pendingMap = team.equals("r") ? pendingRedCalls : pendingBlueCalls;
		Map<Integer, String> targetMap = team.equals("r") ? redKills : blueKills;
		Set<Integer> pending = pendingMap.get(sender);
		if (pending == null || pending.isEmpty()) return;

		boolean resolved;
		do
		{
			resolved = false;
			Set<Integer> toRemove = new LinkedHashSet<>();
			for (int slot : pending)
			{
				if (targetMap.containsKey(slot)) { toRemove.add(slot); continue; } // taken by someone else
				if (slot > 1 && !targetMap.containsKey(slot - 1)) continue;        // prereq still missing
				if (slot == 5 && !validateKill5(team, sender, secondsRemaining)) { toRemove.add(slot); continue; }
				if (secondsRemaining > 720 && getKillsClaimedBy(sender) >= 3)      { toRemove.add(slot); break; }

				String existing = targetMap.putIfAbsent(slot, sender);
				toRemove.add(slot);
				if (existing == null)
				{
					if (slot == 5) kill5Tick = client.getTickCount();
					resolved = true;
				}
			}
			pending.removeAll(toRemove);
		}
		while (resolved && !pending.isEmpty());

		if (panel != null) panel.updateKills(redKills, blueKills);
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
