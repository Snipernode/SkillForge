package com.skilltree.plugin.gui;

import com.skilltree.plugin.SkillForgePlugin;
import com.skilltree.plugin.systems.FastTravelSystem;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.EventPriority;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class StationGUI implements Listener {
	// filepath: g:\Coding\SkillForge\SkillForge\src\main\java\com\skilltree\plugin\gui\StationGUI.java
	private final SkillForgePlugin plugin;
	private final FastTravelSystem owner;
	private final Map<UUID, Inventory> openInventories = new HashMap<>();
	private final Map<UUID, String> pendingRename = new ConcurrentHashMap<>();
	private final Map<UUID, String> pendingRenameContext = new ConcurrentHashMap<>();
	private final Map<UUID, String> optionsStation = new ConcurrentHashMap<>();
	private final Map<UUID, String> optionsContext = new ConcurrentHashMap<>();
	private final Map<UUID, String> pendingDelete = new ConcurrentHashMap<>();
	private final Map<UUID, String> lastTrainType = new ConcurrentHashMap<>();
	private final Map<UUID, String> arrivalPromptCurrent = new ConcurrentHashMap<>();
	private final Map<UUID, String> arrivalPromptNext = new ConcurrentHashMap<>();
	private final Map<UUID, Boolean> selectionViewOnly = new ConcurrentHashMap<>();
	private static final int MAX_STATION_NAME_LENGTH = 32;
	private static final int MAX_TRAVEL_TIME = 600;
	private static final String CONTEXT_ADMIN = "ADMIN";
	private static final String CONTEXT_PLAYER = "PLAYER";

	public StationGUI(SkillForgePlugin plugin, FastTravelSystem owner) {
		this.plugin = plugin;
		this.owner = owner;
		// register listener
		plugin.getServer().getPluginManager().registerEvents(this, plugin);
	}

	// open station selection (player view) - trainType controls visual style ("C.U.M." or "B.S.B.")
	public void openSelection(Player player, String trainType) {
		lastTrainType.put(player.getUniqueId(), trainType);
		boolean isBSB = trainType != null && trainType.equalsIgnoreCase("B.S.B.");
		String baseTitle = "Station Selection" + (trainType != null ? " — " + trainType : "");
		String title = GuiStyle.title(baseTitle);
		Inventory inv = Bukkit.createInventory(null, 54, title);
		int slot = 0;
		boolean useDynamic = plugin.getConfig().getBoolean("fasttravel.dynamic_time.enabled", true);
		String originId = useDynamic ? owner.getClosestStationId(player.getLocation()) : null;
		Map<String, Map<String,Object>> infos = owner.getStationsInfo();
		for (Map<String,Object> si : infos.values()) {
			if (slot >= 45) break;
			if (!canViewStation(player, si)) continue;
			String name = (String) si.getOrDefault("name", "Unknown");
			String id = (String) si.getOrDefault("id", "");
			Object tt = si.getOrDefault("travelTime", 5);
			if (useDynamic && originId != null && !originId.equalsIgnoreCase(id)) {
				Integer estimate = owner.estimateTravelTime(originId, id, trainType);
				if (estimate != null) {
					tt = estimate;
				}
			}
			String perm = (String) si.getOrDefault("permission", "");
			boolean area = Boolean.TRUE.equals(si.get("areaDefined"));
			boolean maintenance = Boolean.TRUE.equals(si.get("maintenance"));
			boolean playerStation = Boolean.TRUE.equals(si.get("playerStation"));
			boolean isPublic = !playerStation || Boolean.TRUE.equals(si.get("public"));
			boolean isOwner = isOwner(player, si);
			Material entryMat = isBSB ? Material.RED_DYE : Material.PAPER; // harsher reskin for B.S.B.
			List<String> lore = new ArrayList<>();
			if (maintenance) {
				entryMat = Material.BARRIER;
				lore.add(ChatColor.RED + "This stop is under maintenance.");
				lore.add(ChatColor.GRAY + "Check back later.");
				lore.add(ChatColor.DARK_GRAY + "ID: " + id);
				inv.setItem(slot++, makeItem(entryMat, ChatColor.DARK_RED + "Stop Under Maintenance", lore));
			} else {
				lore.add(ChatColor.GRAY + "Travel Time: " + tt + "s");
				lore.add(ChatColor.GRAY + "ID: " + id);
				if (perm != null && !perm.isEmpty()) lore.add(ChatColor.RED + "Requires: " + perm);
				if (area) lore.add(ChatColor.AQUA + "Area-protected");
				if (playerStation) {
					lore.add(ChatColor.LIGHT_PURPLE + "Player Station");
					lore.add(isPublic ? ChatColor.GREEN + "Visibility: Public" : ChatColor.RED + "Visibility: Private");
					if (isOwner) lore.add(ChatColor.GRAY + "Owner: You");
				}
				if (isBSB) {
					// extra warning / harsher feel for B.S.B.
					lore.add("");
					lore.add(ChatColor.DARK_RED + "Warning: B.S.B. travel is harsher — expect intense effects.");
				}
				inv.setItem(slot++, makeItem(entryMat, ChatColor.GOLD + name, lore));
			}
		}

		// Info + controls
		ItemStack info = makeItem(Material.MAP, ChatColor.AQUA + "Caravan Ledger",
				Arrays.asList(ChatColor.GRAY + "Click a station to begin travel.",
							  ChatColor.GRAY + "Permissions may apply."));
		inv.setItem(45, info);
		boolean viewOnly = Boolean.TRUE.equals(selectionViewOnly.get(player.getUniqueId()));
		inv.setItem(46, makeItem(viewOnly ? Material.LIME_DYE : Material.LIME_CONCRETE,
				ChatColor.GREEN + "Get On The Train",
				Collections.singletonList(viewOnly
						? ChatColor.GRAY + "Click to switch to boarding mode."
						: ChatColor.GREEN + "Selected: station clicks will board.")));
		int limit = owner.getPlayerStationLimit();
		int count = owner.countPlayerStations(player.getUniqueId());
		String limitText = limit > 0 ? String.valueOf(limit) : "Unlimited";
		inv.setItem(47, makeItem(Material.WRITABLE_BOOK, ChatColor.LIGHT_PURPLE + "Player Stations",
				Arrays.asList(ChatColor.GRAY + "Manage your personal stations.",
							  ChatColor.GRAY + "Used: " + count + "/" + limitText)));
		inv.setItem(48, makeItem(viewOnly ? Material.GRAY_CONCRETE : Material.GRAY_DYE,
				ChatColor.YELLOW + "Nah, I'm Just Viewing",
				Collections.singletonList(viewOnly
						? ChatColor.YELLOW + "Selected: station clicks only preview."
						: ChatColor.GRAY + "Click to switch to view mode.")));

		// Admin button shows train type and uses same slot
		String adminLabel = (trainType != null ? trainType + " " : "") + "Admin Panel";
		inv.setItem(49, makeItem(Material.BEACON, ChatColor.RED + adminLabel, Collections.singletonList(ChatColor.GRAY + "Operators only")));
		inv.setItem(53, makeItem(Material.BARRIER, ChatColor.DARK_RED + "Close", null));
		GuiStyle.fillEmpty(inv, GuiStyle.fillerPane());
		openInventories.put(player.getUniqueId(), inv);
		player.openInventory(inv);
	}

	// open admin panel
	public void openAdmin(Player player) {
		Inventory inv = Bukkit.createInventory(null, 27, GuiStyle.title("Station Admin"));
		inv.setItem(10, makeItem(Material.GREEN_CONCRETE, ChatColor.GREEN + "Add Station Here", Collections.singletonList("Create station at your location (uses wand area if set)")));
		inv.setItem(12, makeItem(Material.STICK, ChatColor.YELLOW + "Give Wand", Collections.singletonList("Gives Station Wand (left/right click to set pos1/pos2)")));
		inv.setItem(14, makeItem(Material.PAPER, ChatColor.AQUA + "List Stations",
				Arrays.asList("View or manage existing stations", "Click a station to edit options")));
		inv.setItem(22, makeItem(Material.COMPASS, ChatColor.GOLD + "Route Calculator",
				Arrays.asList("Shows distance + time between stops", "Uses the current axis order")));
		inv.setItem(16, makeItem(Material.BARRIER, ChatColor.RED + "Close", null));
		inv.setItem(4, GuiStyle.item(Material.BOOK, ChatColor.RED + "" + ChatColor.BOLD + "Admin Tools",
				Arrays.asList(ChatColor.GRAY + "Manage station entries and areas.")));
		GuiStyle.fillBorder(inv, GuiStyle.fillerPane());
		GuiStyle.fillEmpty(inv, GuiStyle.fillerPane());
		openInventories.put(player.getUniqueId(), inv);
		player.openInventory(inv);
	}

	// admin list
	private void openAdminList(Player player) {
		Inventory inv = Bukkit.createInventory(null, 54, GuiStyle.title("Stations List"));
		int slot = 0;
		Map<String, Map<String, Object>> infos = owner.getStationsInfo();
		for (Map.Entry<String, Map<String, Object>> entry : infos.entrySet()) {
			if (slot >= 54) break;
			Map<String, Object> si = entry.getValue();
			String id = (String) si.getOrDefault("id", entry.getKey());
			String name = (String) si.getOrDefault("name", id);
			Object tt = si.getOrDefault("travelTime", 5);
			String loc = (String) si.getOrDefault("location", "(unknown)");
			boolean maintenance = Boolean.TRUE.equals(si.get("maintenance"));
			inv.setItem(slot++, makeItem(Material.MAP, ChatColor.GOLD + name,
					Arrays.asList(ChatColor.GRAY + "ID: " + id,
								  ChatColor.GRAY + "Travel Time: " + tt + "s",
								  ChatColor.GRAY + "Loc: " + loc,
								  (maintenance ? ChatColor.RED + "Status: Under Maintenance" : ChatColor.GREEN + "Status: Active"),
								  ChatColor.YELLOW + "Click to manage station")));
		}
		inv.setItem(49, GuiStyle.item(Material.BOOK, ChatColor.RED + "" + ChatColor.BOLD + "Admin List",
				Arrays.asList(ChatColor.GRAY + "Click a station to manage.")));
		inv.setItem(53, GuiStyle.item(Material.ARROW, ChatColor.YELLOW + "Back",
				Collections.singletonList(ChatColor.GRAY + "Return to admin panel.")));
		GuiStyle.fillEmpty(inv, GuiStyle.fillerPane());
		openInventories.put(player.getUniqueId(), inv);
		player.openInventory(inv);
	}

	public void openArrivalPrompt(Player player, String arrivingStationId, String arrivingStationName,
								  String nextStationId, String nextStationName, int etaSeconds) {
		if (player == null) return;
		UUID uid = player.getUniqueId();
		arrivalPromptCurrent.put(uid, arrivingStationId);
		if (nextStationId != null && !nextStationId.isEmpty()) {
			arrivalPromptNext.put(uid, nextStationId);
		} else {
			arrivalPromptNext.remove(uid);
		}

		Inventory inv = Bukkit.createInventory(null, 27, GuiStyle.title("Arrival Prompt"));
		String safeArriving = arrivingStationName != null && !arrivingStationName.isEmpty() ? arrivingStationName : "Unknown";
		String safeNext = nextStationName != null && !nextStationName.isEmpty() ? nextStationName : "None";
		int eta = Math.max(1, etaSeconds);

		inv.setItem(4, GuiStyle.item(Material.MAP, ChatColor.GOLD + "Approaching " + safeArriving,
				Arrays.asList(ChatColor.GRAY + "ETA: " + eta + "s",
						ChatColor.GRAY + "Would you like to stop here?")));

		inv.setItem(11, GuiStyle.item(Material.LIME_WOOL, ChatColor.GREEN + "Stop Here",
				Arrays.asList(ChatColor.GRAY + "Disembark at " + safeArriving + ".",
						ChatColor.GRAY + "This is your selected stop.")));

		if (nextStationId != null && !nextStationId.isEmpty()) {
			inv.setItem(15, GuiStyle.item(Material.MINECART, ChatColor.YELLOW + "Stay On Train",
					Arrays.asList(ChatColor.GRAY + "Continue past " + safeArriving + ".",
							ChatColor.GRAY + "Closest next stop: " + safeNext)));
		} else {
			inv.setItem(15, GuiStyle.item(Material.GRAY_DYE, ChatColor.DARK_GRAY + "No Next Stop",
					Collections.singletonList(ChatColor.GRAY + "This route has no further stops.")));
		}

		inv.setItem(26, GuiStyle.item(Material.BARRIER, ChatColor.RED + "Close",
				Collections.singletonList(ChatColor.GRAY + "Keep current stop selection.")));
		GuiStyle.fillEmpty(inv, GuiStyle.fillerPane());
		openInventories.put(uid, inv);
		player.openInventory(inv);
	}

	@SuppressWarnings("null")
    @EventHandler
	public void onInventoryClick(InventoryClickEvent event) {
		if (event.getWhoClicked() == null) return;
		Player player = (Player) event.getWhoClicked();
		Inventory top = event.getView().getTopInventory();
		// detect our GUIs by title (avoid relying on Inventory object equality)
		String title = event.getView().getTitle();
		boolean isOurGui = title != null && (title.contains("Station Selection") || title.contains("Station Admin")
				|| title.contains("Stations List") || title.contains("Station Options")
				|| title.contains("Player Stations") || title.contains("Arrival Prompt"));
		if (!isOurGui) return;
		// always cancel to prevent taking/moving GUI items
		event.setCancelled(true);
		// restore top-inventory slot and clear cursor to prevent pickup (fixes creative-mode grabs)
		try {
			int raw = event.getRawSlot();
			if (raw >= 0 && raw < top.getSize()) {
				ItemStack curr = event.getCurrentItem();
				top.setItem(raw, curr);
				player.setItemOnCursor(null);
			}
		} catch (Throwable ignored) {}

		ItemStack clicked = event.getCurrentItem();
		if (clicked == null || clicked.getType() == Material.AIR) return;
		// title already retrieved above
		ItemMeta meta = clicked.getItemMeta();
		String display = meta != null && meta.hasDisplayName() ? ChatColor.stripColor(meta.getDisplayName()) : "";

		if (title.contains("Arrival Prompt")) {
			UUID uid = player.getUniqueId();
			String currentId = arrivalPromptCurrent.get(uid);
			String nextId = arrivalPromptNext.get(uid);
			if (display.equalsIgnoreCase("Stop Here")) {
				owner.handleArrivalPromptChoice(player, currentId, nextId, false);
				player.closeInventory();
				return;
			}
			if (display.equalsIgnoreCase("Stay On Train")) {
				owner.handleArrivalPromptChoice(player, currentId, nextId, true);
				player.closeInventory();
				return;
			}
			if (display.equalsIgnoreCase("Close")) {
				player.closeInventory();
				return;
			}
			return;
		}

		if (title.contains("Station Selection")) {
			// tolerant admin detection: accept display names that contain "admin panel" (train-type prefix possible)
			String dlow = display == null ? "" : display.toLowerCase();
			if (dlow.contains("admin panel") || clicked.getType() == Material.BEACON) {
				if (player.isOp()) {
					openAdmin(player);
				} else {
					player.sendMessage(ChatColor.RED + "Admin access required.");
				}
				return;
			}
			if (display.equalsIgnoreCase("Player Stations")) {
				openPlayerStations(player);
				return;
			}
			if (display.equalsIgnoreCase("Get On The Train")) {
				selectionViewOnly.put(player.getUniqueId(), false);
				openSelection(player, lastTrainType.get(player.getUniqueId()));
				return;
			}
			if (display.equalsIgnoreCase("Nah, I'm Just Viewing")) {
				selectionViewOnly.put(player.getUniqueId(), true);
				openSelection(player, lastTrainType.get(player.getUniqueId()));
				return;
			}
			if (display.equalsIgnoreCase("Close")) { player.closeInventory(); return; }
			String id = extractIdFromLore(meta);
			if (id == null) return;
			Map<String,Object> si = owner.getStationInfoById(id);
			if (si == null) { player.sendMessage(ChatColor.RED + "Station missing."); return; }
			boolean viewOnly = Boolean.TRUE.equals(selectionViewOnly.get(player.getUniqueId()));
			if (viewOnly) {
				sendStationPreview(player, si, lastTrainType.get(player.getUniqueId()));
				return;
			}
			boolean maintenance = Boolean.TRUE.equals(si.get("maintenance"));
			if (maintenance) {
				player.sendMessage(ChatColor.RED + "This stop is under maintenance.");
				return;
			}
			String perm = (String) si.getOrDefault("permission", "");
			if (perm != null && !perm.isEmpty() && !player.hasPermission(perm) && !player.isOp()) {
				player.sendMessage(ChatColor.RED + "You lack permission to travel to that station.");
				return;
			}
			String trainType = lastTrainType.get(player.getUniqueId());
			owner.startTravelToStationById(player, id, trainType);
			player.closeInventory();
			return;
		}

		if (title.contains("Station Admin")) {
			if (display.equalsIgnoreCase("Add Station Here")) {
				Map<String,Object> created = owner.createStationAtPlayer(player);
				player.sendMessage(ChatColor.GREEN + "Station added: " + created.getOrDefault("name","(unknown)") + " (ID: " + created.getOrDefault("id","?") + ")");
				return;
			}
			if (display.equalsIgnoreCase("Give Wand")) {
				ItemStack wand = new ItemStack(Material.STICK);
				ItemMeta wm = wand.getItemMeta();
				wm.setDisplayName(ChatColor.YELLOW + "Station Wand");
				wm.setLore(Arrays.asList(ChatColor.GRAY + "Left click to set pos1", ChatColor.GRAY + "Right click to set pos2"));
				wand.setItemMeta(wm);
				player.getInventory().addItem(wand);
				player.sendMessage(ChatColor.GREEN + "Station Wand given.");
				return;
			}
			if (display.equalsIgnoreCase("Route Calculator")) {
				for (String line : owner.getRouteReport("C.U.M.")) {
					player.sendMessage(line);
				}
				for (String line : owner.getRouteReport("B.S.B.")) {
					player.sendMessage(line);
				}
				return;
			}
			if (display.equalsIgnoreCase("List Stations")) {
				openAdminList(player);
				return;
			}
			if (display.equalsIgnoreCase("Close")) { player.closeInventory(); return; }
		}

		if (title.contains("Stations List")) {
			if (!player.isOp()) { player.sendMessage(ChatColor.RED + "Admin access required."); return; }
			if (display.equalsIgnoreCase("Back")) {
				openAdmin(player);
				return;
			}
			String id = extractIdFromLore(meta);
			if (id == null) return;
			openStationOptions(player, id, CONTEXT_ADMIN);
			return;
		}

		if (title.contains("Player Stations")) {
			if (display.equalsIgnoreCase("Back")) {
				openSelection(player, lastTrainType.get(player.getUniqueId()));
				return;
			}
			if (display.equalsIgnoreCase("Create Public Station")) {
				handleCreatePlayerStation(player, true);
				return;
			}
			if (display.equalsIgnoreCase("Create Private Station")) {
				handleCreatePlayerStation(player, false);
				return;
			}
			String id = extractIdFromLore(meta);
			if (id == null) return;
			openStationOptions(player, id, CONTEXT_PLAYER);
			return;
		}

		if (title.contains("Station Options")) {
			UUID uid = player.getUniqueId();
			String stationId = optionsStation.get(uid);
			if (stationId == null) return;
			Map<String, Object> info = owner.getStationInfoById(stationId);
			if (info == null) {
				player.sendMessage(ChatColor.RED + "Station not found.");
				returnToContext(player, optionsContext.get(uid));
				return;
			}
			boolean playerStation = Boolean.TRUE.equals(info.get("playerStation"));
			boolean isOwner = isOwner(player, info);
			boolean canManage = player.isOp() || (playerStation && isOwner);
			boolean canTogglePublic = playerStation && (player.isOp() || isOwner);
			if (display.equalsIgnoreCase("Back")) {
				pendingDelete.remove(uid);
				returnToContext(player, optionsContext.get(uid));
				return;
			}
			if (display.equalsIgnoreCase("Rename Station")) {
				if (!canManage) {
					player.sendMessage(ChatColor.RED + "You cannot rename this station.");
					return;
				}
				pendingRename.put(uid, stationId);
				pendingRenameContext.put(uid, optionsContext.get(uid));
				player.closeInventory();
				player.sendMessage(ChatColor.YELLOW + "Type a new name in chat for station " + stationId + " (max " + MAX_STATION_NAME_LENGTH + " chars).");
				player.sendMessage(ChatColor.GRAY + "Type 'cancel' to abort.");
				return;
			}
			Integer delta = parseTimeDelta(display);
			if (delta != null) {
				if (!canManage) {
					player.sendMessage(ChatColor.RED + "You cannot edit travel time for this station.");
					return;
				}
				adjustTravelTime(player, stationId, delta, optionsContext.get(uid));
				return;
			}
			if (display.toLowerCase().contains("maintenance")) {
				if (!player.isOp()) {
					player.sendMessage(ChatColor.RED + "Admin access required.");
					return;
				}
				owner.toggleStationMaintenance(stationId);
				openStationOptions(player, stationId, optionsContext.get(uid));
				return;
			}
			if (display.toLowerCase().contains("visibility")) {
				if (!canTogglePublic) {
					player.sendMessage(ChatColor.RED + "You cannot change visibility for this station.");
					return;
				}
				owner.toggleStationPublic(stationId);
				openStationOptions(player, stationId, optionsContext.get(uid));
				return;
			}
			if (display.equalsIgnoreCase("Remove Station")) {
				if (!canManage) {
					player.sendMessage(ChatColor.RED + "You cannot remove this station.");
					return;
				}
				pendingDelete.put(uid, stationId);
				openStationOptions(player, stationId, optionsContext.get(uid));
				return;
			}
			if (display.equalsIgnoreCase("Confirm Remove")) {
				if (!canManage) {
					player.sendMessage(ChatColor.RED + "You cannot remove this station.");
					return;
				}
				pendingDelete.remove(uid);
				Map<String,Object> removed = owner.removeStationById(stationId);
				player.sendMessage(ChatColor.RED + "Removed station: " + (removed != null ? removed.getOrDefault("name", stationId) : stationId));
				returnToContext(player, optionsContext.get(uid));
				return;
			}
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPlayerChat(AsyncPlayerChatEvent event) {
		Player player = event.getPlayer();
		String id = pendingRename.get(player.getUniqueId());
		if (id == null) return;
		event.setCancelled(true);
		String msg = event.getMessage() != null ? event.getMessage().trim() : "";
		if (msg.equalsIgnoreCase("cancel")) {
			pendingRename.remove(player.getUniqueId());
			pendingRenameContext.remove(player.getUniqueId());
			player.sendMessage(ChatColor.YELLOW + "Station rename cancelled.");
			return;
		}
		String clean = ChatColor.stripColor(msg);
		if (clean.isEmpty()) {
			player.sendMessage(ChatColor.RED + "Name cannot be empty. Type a new name or 'cancel'.");
			return;
		}
		if (clean.length() > MAX_STATION_NAME_LENGTH) {
			player.sendMessage(ChatColor.RED + "Name too long. Max " + MAX_STATION_NAME_LENGTH + " characters.");
			return;
		}
		pendingRename.remove(player.getUniqueId());
		String context = pendingRenameContext.remove(player.getUniqueId());
		plugin.getServer().getScheduler().runTask(plugin, () -> {
			Map<String,Object> updated = owner.renameStationById(id, clean);
			if (updated == null) {
				player.sendMessage(ChatColor.RED + "Station not found: " + id);
				return;
			}
			player.sendMessage(ChatColor.GREEN + "Station renamed to: " + updated.getOrDefault("name", clean));
			returnToContext(player, context);
		});
	}

	@EventHandler
	public void onInventoryClose(InventoryCloseEvent event) {
		UUID uid = event.getPlayer().getUniqueId();
		openInventories.remove(uid);
		arrivalPromptCurrent.remove(uid);
		arrivalPromptNext.remove(uid);
		String title = event.getView().getTitle();
		if (title != null && title.contains("Station Options")) {
			// Defer cleanup to avoid wiping context when we refresh the options GUI.
			plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
				Player player = plugin.getServer().getPlayer(uid);
				if (player == null) {
					pendingDelete.remove(uid);
					optionsStation.remove(uid);
					optionsContext.remove(uid);
					return;
				}
				String currentTitle = player.getOpenInventory().getTitle();
				if (currentTitle == null || !currentTitle.contains("Station Options")) {
					pendingDelete.remove(uid);
					optionsStation.remove(uid);
					optionsContext.remove(uid);
				}
			}, 1L);
		}
	}

	@EventHandler
	public void onPlayerQuit(PlayerQuitEvent event) {
		openInventories.remove(event.getPlayer().getUniqueId());
		pendingRename.remove(event.getPlayer().getUniqueId());
		pendingRenameContext.remove(event.getPlayer().getUniqueId());
		optionsStation.remove(event.getPlayer().getUniqueId());
		optionsContext.remove(event.getPlayer().getUniqueId());
		pendingDelete.remove(event.getPlayer().getUniqueId());
		lastTrainType.remove(event.getPlayer().getUniqueId());
		arrivalPromptCurrent.remove(event.getPlayer().getUniqueId());
		arrivalPromptNext.remove(event.getPlayer().getUniqueId());
		selectionViewOnly.remove(event.getPlayer().getUniqueId());
	}

	// helpers
	private ItemStack makeItem(Material mat, String name, List<String> lore) {
		ItemStack i = new ItemStack(mat);
		ItemMeta m = i.getItemMeta();
		if (m != null) {
			m.setDisplayName(name);
			if (lore != null) {
				List<String> clean = new ArrayList<>();
				for (String s : lore) if (s != null) clean.add(s);
				m.setLore(clean);
			}
			i.setItemMeta(m);
		}
		return i;
	}

	private String formatLoc(Location l) {
		return l.getWorld().getName() + "@" + l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ();
	}

	private boolean isOwner(Player player, Map<String, Object> info) {
		if (player == null || info == null) return false;
		Object ownerRaw = info.get("owner");
		if (ownerRaw == null) return false;
		return ownerRaw.toString().equalsIgnoreCase(player.getUniqueId().toString());
	}

	private boolean canViewStation(Player player, Map<String, Object> info) {
		if (info == null) return false;
		boolean playerStation = Boolean.TRUE.equals(info.get("playerStation"));
		if (!playerStation) return true;
		if (player != null && player.isOp()) return true;
		if (isOwner(player, info)) return true;
		return Boolean.TRUE.equals(info.get("public"));
	}

	private String extractIdFromLore(ItemMeta meta) {
		if (meta == null || !meta.hasLore()) return null;
		for (String line : meta.getLore()) {
			if (line == null) continue;
			String lower = ChatColor.stripColor(line).toLowerCase();
			if (lower.contains("id:")) {
				return ChatColor.stripColor(line).split(":", 2)[1].trim();
			}
		}
		return null;
	}

	private void openPlayerStations(Player player) {
		Inventory inv = Bukkit.createInventory(null, 54, GuiStyle.title("Player Stations"));
		int slot = 0;
		Map<String, Map<String, Object>> infos = owner.getStationsInfo();
		for (Map<String, Object> si : infos.values()) {
			if (slot >= 45) break;
			if (!Boolean.TRUE.equals(si.get("playerStation"))) continue;
			if (!isOwner(player, si) && !(player != null && player.isOp())) continue;
			String name = (String) si.getOrDefault("name", "Unknown");
			String id = (String) si.getOrDefault("id", "");
			Object tt = si.getOrDefault("travelTime", 5);
			boolean isPublic = Boolean.TRUE.equals(si.get("public"));
			ItemStack item = makeItem(Material.WRITABLE_BOOK, ChatColor.GOLD + name,
					Arrays.asList(ChatColor.GRAY + "ID: " + id,
								  ChatColor.GRAY + "Travel Time: " + tt + "s",
								  isPublic ? ChatColor.GREEN + "Visibility: Public" : ChatColor.RED + "Visibility: Private",
								  ChatColor.YELLOW + "Click to manage station"));
			inv.setItem(slot++, item);
		}
		int limit = owner.getPlayerStationLimit();
		int count = owner.countPlayerStations(player.getUniqueId());
		String limitText = limit > 0 ? String.valueOf(limit) : "Unlimited";
		inv.setItem(45, GuiStyle.item(Material.BOOK, ChatColor.LIGHT_PURPLE + "Player Stations",
				Arrays.asList(ChatColor.GRAY + "Create up to " + limitText + " stations.",
							  ChatColor.GRAY + "Used: " + count + "/" + limitText)));
		inv.setItem(47, makeItem(Material.EMERALD_BLOCK, ChatColor.GREEN + "Create Public Station",
				Collections.singletonList(ChatColor.GRAY + "Visible to everyone.")));
		inv.setItem(51, makeItem(Material.REDSTONE_BLOCK, ChatColor.RED + "Create Private Station",
				Collections.singletonList(ChatColor.GRAY + "Only you can see/use it.")));
		inv.setItem(53, GuiStyle.item(Material.ARROW, ChatColor.YELLOW + "Back",
				Collections.singletonList(ChatColor.GRAY + "Return to station selection.")));
		GuiStyle.fillEmpty(inv, GuiStyle.fillerPane());
		openInventories.put(player.getUniqueId(), inv);
		player.openInventory(inv);
	}

	private void handleCreatePlayerStation(Player player, boolean isPublic) {
		int limit = owner.getPlayerStationLimit();
		int count = owner.countPlayerStations(player.getUniqueId());
		if (limit > 0 && count >= limit) {
			player.sendMessage(ChatColor.RED + "You have reached the player station limit (" + limit + ").");
			return;
		}
		Map<String,Object> created = owner.createPlayerStation(player, isPublic);
		if (created == null) {
			player.sendMessage(ChatColor.RED + "Unable to create a player station.");
			return;
		}
		player.sendMessage(ChatColor.GREEN + "Player station created: " + created.getOrDefault("name","(unknown)") + " (ID: " + created.getOrDefault("id","?") + ")");
		openPlayerStations(player);
	}

	private void openStationOptions(Player player, String stationId, String context) {
		Map<String,Object> info = owner.getStationInfoById(stationId);
		if (info == null) {
			player.sendMessage(ChatColor.RED + "Station not found.");
			return;
		}
		optionsStation.put(player.getUniqueId(), stationId);
		optionsContext.put(player.getUniqueId(), context);
		Inventory inv = Bukkit.createInventory(null, 27, GuiStyle.title("Station Options"));
		String name = String.valueOf(info.getOrDefault("name", stationId));
		boolean playerStation = Boolean.TRUE.equals(info.get("playerStation"));
		boolean isPublic = !playerStation || Boolean.TRUE.equals(info.get("public"));
		boolean maintenance = Boolean.TRUE.equals(info.get("maintenance"));
		Object tt = info.getOrDefault("travelTime", 5);
		boolean isOwner = isOwner(player, info);
		boolean canManage = player.isOp() || (playerStation && isOwner);
		boolean canTogglePublic = playerStation && (player.isOp() || isOwner);
		boolean canMaintenance = player.isOp();
		boolean confirmDelete = pendingDelete.containsKey(player.getUniqueId())
				&& stationId.equals(pendingDelete.get(player.getUniqueId()));

		inv.setItem(4, GuiStyle.item(Material.MAP, ChatColor.GOLD + name,
				Arrays.asList(ChatColor.GRAY + "ID: " + stationId,
							  ChatColor.GRAY + "Travel Time: " + tt + "s",
							  playerStation ? (isPublic ? ChatColor.GREEN + "Visibility: Public" : ChatColor.RED + "Visibility: Private")
										: ChatColor.GRAY + "System Station",
							  maintenance ? ChatColor.RED + "Status: Under Maintenance" : ChatColor.GREEN + "Status: Active")));

		inv.setItem(10, GuiStyle.item(Material.NAME_TAG, ChatColor.YELLOW + "Rename Station",
				Collections.singletonList(canManage ? ChatColor.GRAY + "Click to rename." : ChatColor.RED + "No access.")));

		inv.setItem(12, GuiStyle.item(Material.CLOCK, ChatColor.AQUA + "Time -10s",
				Collections.singletonList(canManage ? ChatColor.GRAY + "Decrease travel time." : ChatColor.RED + "No access.")));
		inv.setItem(13, GuiStyle.item(Material.CLOCK, ChatColor.AQUA + "Time -5s",
				Collections.singletonList(canManage ? ChatColor.GRAY + "Decrease travel time." : ChatColor.RED + "No access.")));
		inv.setItem(14, GuiStyle.item(Material.CLOCK, ChatColor.AQUA + "Time -1s",
				Collections.singletonList(canManage ? ChatColor.GRAY + "Decrease travel time." : ChatColor.RED + "No access.")));
		inv.setItem(15, GuiStyle.item(Material.CLOCK, ChatColor.AQUA + "Time +1s",
				Collections.singletonList(canManage ? ChatColor.GRAY + "Increase travel time." : ChatColor.RED + "No access.")));
		inv.setItem(16, GuiStyle.item(Material.CLOCK, ChatColor.AQUA + "Time +5s",
				Collections.singletonList(canManage ? ChatColor.GRAY + "Increase travel time." : ChatColor.RED + "No access.")));
		inv.setItem(17, GuiStyle.item(Material.CLOCK, ChatColor.AQUA + "Time +10s",
				Collections.singletonList(canManage ? ChatColor.GRAY + "Increase travel time." : ChatColor.RED + "No access.")));

		if (playerStation) {
			String visibility = isPublic ? "Visibility: Public" : "Visibility: Private";
			inv.setItem(20, GuiStyle.item(Material.ENDER_EYE, ChatColor.LIGHT_PURPLE + visibility,
					Collections.singletonList(canTogglePublic ? ChatColor.GRAY + "Click to toggle." : ChatColor.RED + "No access.")));
		} else {
			inv.setItem(20, GuiStyle.item(Material.ENDER_EYE, ChatColor.DARK_GRAY + "Visibility: System",
					Collections.singletonList(ChatColor.GRAY + "Fixed by admins.")));
		}

		inv.setItem(22, GuiStyle.item(Material.BELL, maintenance ? ChatColor.RED + "Maintenance: ON" : ChatColor.GREEN + "Maintenance: OFF",
				Collections.singletonList(canMaintenance ? ChatColor.GRAY + "Click to toggle." : ChatColor.RED + "Admin only.")));

		if (confirmDelete) {
			inv.setItem(24, GuiStyle.item(Material.RED_WOOL, ChatColor.DARK_RED + "Confirm Remove",
					Arrays.asList(ChatColor.RED + "This cannot be undone.", ChatColor.GRAY + "Click to confirm.")));
		} else {
			inv.setItem(24, GuiStyle.item(Material.BARRIER, ChatColor.RED + "Remove Station",
					Collections.singletonList(canManage ? ChatColor.GRAY + "Click to remove." : ChatColor.RED + "No access.")));
		}
		inv.setItem(26, GuiStyle.item(Material.ARROW, ChatColor.YELLOW + "Back",
				Collections.singletonList(ChatColor.GRAY + "Return to previous list.")));
		GuiStyle.fillEmpty(inv, GuiStyle.fillerPane());
		openInventories.put(player.getUniqueId(), inv);
		player.openInventory(inv);
	}

	private Integer parseTimeDelta(String display) {
		if (display == null) return null;
		String lower = display.toLowerCase(Locale.ROOT);
		if (!lower.contains("time")) return null;
		if (lower.contains("-10")) return -10;
		if (lower.contains("-5")) return -5;
		if (lower.contains("-1")) return -1;
		if (lower.contains("+1")) return 1;
		if (lower.contains("+5")) return 5;
		if (lower.contains("+10")) return 10;
		return null;
	}

	private void adjustTravelTime(Player player, String stationId, int delta, String context) {
		Map<String,Object> info = owner.getStationInfoById(stationId);
		if (info == null) {
			player.sendMessage(ChatColor.RED + "Station not found.");
			return;
		}
		int current = ((Number) info.getOrDefault("travelTime", 5)).intValue();
		int next = Math.max(1, Math.min(MAX_TRAVEL_TIME, current + delta));
		Map<String,Object> updated = owner.setStationTravelTime(stationId, next);
		if (updated == null) {
			player.sendMessage(ChatColor.RED + "Station not found.");
			return;
		}
		player.sendMessage(ChatColor.GREEN + "Travel time set to " + next + "s.");
		openStationOptions(player, stationId, context);
	}

	private void returnToContext(Player player, String context) {
		if (CONTEXT_PLAYER.equalsIgnoreCase(context)) {
			openPlayerStations(player);
		} else if (CONTEXT_ADMIN.equalsIgnoreCase(context)) {
			openAdminList(player);
		} else {
			openSelection(player, lastTrainType.get(player.getUniqueId()));
		}
	}

	private void sendStationPreview(Player player, Map<String, Object> info, String trainType) {
		if (player == null || info == null) return;
		String id = String.valueOf(info.getOrDefault("id", ""));
		String name = String.valueOf(info.getOrDefault("name", "Unknown"));
		int tt = 5;
		Object raw = info.get("travelTime");
		if (raw instanceof Number) {
			tt = ((Number) raw).intValue();
		}
		boolean useDynamic = plugin.getConfig().getBoolean("fasttravel.dynamic_time.enabled", true);
		if (useDynamic && id != null && !id.isEmpty()) {
			String originId = owner.getClosestStationId(player.getLocation());
			if (originId != null && !originId.equalsIgnoreCase(id)) {
				Integer estimate = owner.estimateTravelTime(originId, id, trainType);
				if (estimate != null) tt = estimate;
			}
		}
		boolean maintenance = Boolean.TRUE.equals(info.get("maintenance"));
		String perm = String.valueOf(info.getOrDefault("permission", ""));
		player.sendMessage(ChatColor.AQUA + "Viewing stop: " + ChatColor.GOLD + name
				+ ChatColor.GRAY + " (ETA: " + tt + "s)");
		if (maintenance) {
			player.sendMessage(ChatColor.RED + "Status: Under Maintenance");
		}
		if (perm != null && !perm.isEmpty()) {
			player.sendMessage(ChatColor.GRAY + "Requires permission: " + perm);
		}
		player.sendMessage(ChatColor.YELLOW + "Switch to " + ChatColor.GREEN + "Get On The Train"
				+ ChatColor.YELLOW + " to board.");
	}
}
