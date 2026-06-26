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

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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

	private ZealgainsPanel panel;
	private NavigationButton navButton;

	private final Map<Integer, String> redKills = new HashMap<>();
	private final Map<Integer, String> blueKills = new HashMap<>();

	// Thread-safe set for fetching the ban list asynchronously
	private final Set<String> bannedPlayers = ConcurrentHashMap.newKeySet();

	// Tracks the exact game tick the 5th kill was claimed to resolve ties
	private int kill5Tick = -1;

	private long lastBanListFetch = 0;
	private static final long BAN_LIST_COOLDOWN_MS = 5 * 60 * 1000L;

	// Forces the call to be at the very start of the message.
	// Now allows repeated team letters in the number string (e.g., r3r4r5)
	private final Pattern callPattern = Pattern.compile("(?i)^\\s*([rb])([rb1-5]+)");

	// Getters for the Overlay UI
	public Map<Integer, String> getRedKills() { return redKills; }
	public Map<Integer, String> getBlueKills() { return blueKills; }

	@Override
	protected void startUp() throws Exception
	{
		// Set up the Sidebar Panel
		panel = new ZealgainsPanel(this);

		// Ensure you have an icon.png in src/main/resources/
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

		// Add the screen overlay
		overlayManager.add(overlay);

		resetKills();
		lastBanListFetch = System.currentTimeMillis();
		fetchBanList();
	}

	@Override
	protected void shutDown() throws Exception
	{
		clientToolbar.removeNavigation(navButton);
		overlayManager.remove(overlay);
		resetKills();
		bannedPlayers.clear();
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!event.getGroup().equals("zealgains"))
		{
			return;
		}

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
			fetchBanList(); // Refetch the list if the URL or toggle changes
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		String message = Text.removeTags(event.getMessage()).toLowerCase();

		// Auto-Reset when receiving a reward message at the end of a game
		if (event.getType() == ChatMessageType.GAMEMESSAGE || event.getType() == ChatMessageType.SPAM)
		{
			if (message.contains("the game has ended") || message.contains("zeal token") || (message.contains("you received") && message.contains("zeal")))
			{
				if (config.autoClear())
				{
					resetKills();
				}
				return;
			}
		}

		// Only parse Friends Chat messages for calls
		if (event.getType() != ChatMessageType.FRIENDSCHAT)
		{
			return;
		}

		// Only track calls when inside a Soul Wars game
		if (!isInSoulWarsGame())
		{
			return;
		}

		// Ignore questions or people asking for open spots
		if (message.contains("?") || message.contains("need") || message.contains("open") || message.contains("who"))
		{
			return;
		}

		String sender = Text.removeTags(event.getName());

		// Strip all spaces so calls like "r 3 4" or "R 1" are seamlessly processed
		String compressedMessage = message.replaceAll("\\s+", "");
		Matcher matcher = callPattern.matcher(compressedMessage);

		if (matcher.find())
		{
			FriendsChatManager fcm = client.getFriendsChatManager();
			if (fcm != null)
			{
				// Check if we are on the active clan world
				int majorityWorld = getMajorityWorld(fcm);
				if (majorityWorld != -1 && client.getWorld() != majorityWorld)
				{
					// We are not on the clan's majority world, ignore everything to prevent spam
					return;
				}

				// Check for off-world fake calls while we ARE on the majority world
				if (config.alertCrossWorld())
				{
					// Normalize underscores to spaces to ensure fair matching
					String standardizedSender = Text.standardize(sender).replace('_', ' ');
					for (FriendsChatMember member : fcm.getMembers())
					{
						if (Text.standardize(member.getName()).replace('_', ' ').equals(standardizedSender))
						{
							int senderWorld = member.getWorld();
							if (senderWorld != client.getWorld())
							{
								sendAlert(sender + " called a fake call from W" + senderWorld);
								return; // Exit early, completely ignoring the fake call
							}
							break; // Found the sender and they are on the same world, break the loop
						}
					}
				}
			}

			String team = matcher.group(1);

			// Strip out any redundant 'r' or 'b' letters that were typed between numbers
			String kills = matcher.group(2).replaceAll("[^1-5]", "");

			processCall(team, kills, sender);
		}
	}

	// Listens for typed "::" commands in the game chat
	@Subscribe
	public void onCommandExecuted(CommandExecuted event)
	{
		if (event.getCommand().equalsIgnoreCase("zgreset"))
		{
			resetKills();
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Zealgains: Kills manually reset.", null);
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

	private void processCall(String team, String kills, String sender)
	{
		// Target the correct map based on the team called
		Map<Integer, String> targetMap = team.equals("r") ? redKills : blueKills;

		boolean overCallAlertTriggered = false;

		for (char c : kills.toCharArray())
		{
			int killNumber = Character.getNumericValue(c);
			int secondsRemaining = getGameTimeRemaining();

			// KIll 5 Rules: Time gates, Mutual Exclusivity, and Same-Tick Tie Breakers
			if (killNumber == 5)
			{
				if (team.equals("b"))
				{
					if (secondsRemaining > 720)
					{
						sendAlert("B5 CALLED TOO EARLY BY: " + sender);
						continue;
					}

					// Mutual exclusivity: if Red already has 5, B5 cannot be claimed.
					if (redKills.containsKey(5))
					{
						if (client.getTickCount() == kill5Tick)
						{
							// B5 was called exactly alongside R5, but processed second. Alert operator.
							String r5Sender = redKills.get(5);
							sendAlert("r5 and b5 called at the same time, " + r5Sender + " (r5) wins the call");
						}
						continue;
					}
				}
				else if (team.equals("r"))
				{
					// Mutual exclusivity: if Blue already has 5, check for same-tick tie.
					if (blueKills.containsKey(5))
					{
						if (client.getTickCount() == kill5Tick)
						{
							// R5 overrides B5 on the exact same tick
							blueKills.remove(5);
							sendAlert("r5 and b5 called at the same time, " + sender + " (r5) wins the call");
						}
						else
						{
							// Blue got it legitimately on an earlier tick, R5 is invalid
							continue;
						}
					}
				}
			}

			// Sequential Check. If it's kill 2-5, verify the previous kill is claimed first.
			if (killNumber > 1 && !targetMap.containsKey(killNumber - 1))
			{
				// If the previous kill hasn't been called, skip this number entirely
				continue;
			}

			// If the sequential check passes, try to assign the kill
			if (targetMap.putIfAbsent(killNumber, sender) == null)
			{
				// Track the exact tick the 5th kill was claimed for tie-breaking
				if (killNumber == 5)
				{
					kill5Tick = client.getTickCount();
				}

				// The kill was successfully added. Now check if they exceeded the limit before 12:00.
				if (secondsRemaining > 720 && !overCallAlertTriggered)
				{
					if (getKillsClaimedBy(sender) > 3)
					{
						sendAlert("More than 3 calls before 12:00, please remind " + sender + " to only call up to 3 before 12:00.");
						overCallAlertTriggered = true;
					}
				}
			}
		}

		// Push fresh data to the side panel
		if (panel != null)
		{
			panel.updateKills(redKills, blueKills);
		}
	}

	public void resetKills()
	{
		redKills.clear();
		blueKills.clear();
		kill5Tick = -1;

		if (panel != null)
		{
			panel.updateKills(redKills, blueKills);
		}
	}

	// --- HELPER METHODS ---

	private int getMajorityWorld(FriendsChatManager fcm)
	{
		if (fcm == null || fcm.getCount() == 0)
		{
			return -1;
		}

		Map<Integer, Integer> worldCounts = new HashMap<>();
		for (FriendsChatMember member : fcm.getMembers())
		{
			int world = member.getWorld();
			if (world > 0)
			{
				worldCounts.put(world, worldCounts.getOrDefault(world, 0) + 1);
			}
		}

		int majorityWorld = -1;
		int maxCount = 0;

		for (Map.Entry<Integer, Integer> entry : worldCounts.entrySet())
		{
			if (entry.getValue() > maxCount)
			{
				maxCount = entry.getValue();
				majorityWorld = entry.getKey();
			}
		}

		return majorityWorld;
	}

	private void fetchBanList()
	{
		if (!config.enableBanList() || config.banListUrl().trim().isEmpty())
		{
			bannedPlayers.clear();
			return;
		}

		// Add a timestamp parameter to fully bypass GitHub's CDN caching
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

	private void sendAlert(String message)
	{
		// Add a red color tag to make it stand out in the chatbox
		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "<col=ff0000>Zealgains Alert: " + message + "</col>", null);

		// Trigger RuneLite's built-in notification system using the customizable Notification config
		notifier.notify(config.enableNotifications(), message);
	}

	private int getKillsClaimedBy(String sender)
	{
		int total = 0;
		for (String player : redKills.values())
		{
			if (player.equals(sender)) total++;
		}
		for (String player : blueKills.values())
		{
			if (player.equals(sender)) total++;
		}
		return total;
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

	// --- WIDGET TIMER LOGIC ---

	private int getGameTimeRemaining()
	{
		// 375 is the Group ID for Soul Wars Game overlay, 23 is the TIME_LEFT Child ID
		Widget timeWidget = client.getWidget(375, 23);

		// Only attempt to read if the widget exists and is visible
		if (timeWidget != null && !timeWidget.isHidden())
		{
			String rawText = timeWidget.getText();
			if (rawText != null && !rawText.isEmpty())
			{
				// Clean up the text in case it has any game color tags
				String text = Text.removeTags(rawText).trim();

				if (text.matches("\\d+:\\d{2}"))
				{
					try
					{
						String[] parts = text.split(":");
						int minutes = Integer.parseInt(parts[0]);
						int seconds = Integer.parseInt(parts[1]);
						return (minutes * 60) + seconds;
					}
					catch (NumberFormatException ignored) {}
				}
			}
		}

		return -1;
	}

	public boolean isInSoulWarsGame()
	{
		Widget timeWidget = client.getWidget(375, 23);
		return timeWidget != null && !timeWidget.isHidden();
	}

	// --- CHAT HIGHLIGHT LOGIC ---

	@Subscribe
	public void onClientTick(ClientTick event)
	{
		if (!config.pmCheckerHighlight() && !config.highlightOnFl() && !config.enableBanList())
		{
			return;
		}
		refreshChatHighlights();
	}

	private void refreshChatHighlights()
	{
		Widget chatList = client.getWidget(InterfaceID.ChatchannelCurrent.LIST);
		if (chatList == null || chatList.isHidden()) return;

		for (Widget child : chatList.getDynamicChildren())
		{
			String rawText = child.getText();

			// Skip empty widgets or World text widgets (e.g. "World 330" or "W476")
			if (rawText != null && !rawText.isEmpty() && !rawText.startsWith("World ") && !rawText.matches("^W\\d+$"))
			{
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
					// Strip hardcoded OSRS color tags (like <col=ffffff> for the local player) so our setTextColor applies
					if (rawText.contains("<col=") || rawText.contains("</col>"))
					{
						child.setText(rawText.replaceAll("(?i)<col=[^>]+>", "").replace("</col>", ""));
					}
					child.setTextColor(colorToSet.getRGB());
				}
			}
		}
	}

	@Provides
	ZealgainsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ZealgainsConfig.class);
	}
}