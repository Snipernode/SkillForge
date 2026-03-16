package com.skilltree.plugin.gui;

import com.skilltree.plugin.SkillForgePlugin;
import com.skilltree.plugin.data.PlayerData;
import com.skilltree.plugin.systems.SkillTreeSystem;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

public class UnifiedPanelGUI implements CommandExecutor, Listener {
	// filepath: g:\Coding\SkillForge\SkillForge\src\main\java\com\skilltree\plugin\gui\UnifiedPanelGUI.java
	private final SkillForgePlugin plugin;
	private Player guiPlayer;
	// NEW: hold an instance of the BindAbilityGUI
	private BindAbilityGUI bindAbilityGUI;
	// NEW: Skill upgrade inspector
	private SkillUpgradeGUI skillUpgradeGUI;
	// replace donation scheduler guard with a task handle
	private BukkitTask donationTask = null;
	private static final Random RAND = new Random();
	private static final String ADV_PANEL_TITLE = GuiStyle.title("Player Unlocks");
	private static final int SLOT_TAB_FIRST = 0;
	private static final int SLOT_TAB_LAST = 8;
	private static final int SLOT_PREV = 36;
	private static final int SLOT_STATUS = 37;
	private static final int SLOT_TAB_INFO = 40;
	private static final int SLOT_CLOSE = 41;
	private static final int SLOT_QUICK_PANELS = 42;
	private static final int SLOT_QUICK_BUY = 43;
	private static final int SLOT_NEXT = 44;
	private static final int[] SKILL_SLOTS = new int[] {
			22,
			13, 15,
			12, 14, 16,
			21, 23, 25,
			20, 24, 28, 30, 32, 34,
			11, 17, 19, 26, 27, 29, 31, 33, 35, 10, 18
	};
	private static final int[] PANEL_SLOTS = new int[] {10, 12, 14, 16, 19, 21, 23, 25};
	private final Map<UUID, PanelViewState> panelStates = new HashMap<>();
	private final Map<UUID, org.bukkit.inventory.Inventory> openPanelInventories = new HashMap<>();

	private enum PanelTab {
		COMBAT("combat", Material.IRON_SWORD, ChatColor.RED + "Combat", true),
		MINING("mining", Material.DIAMOND_PICKAXE, ChatColor.GRAY + "Mining", true),
		AGILITY("agility", Material.FEATHER, ChatColor.GREEN + "Agility", true),
		INTELLECT("intellect", Material.ENCHANTED_BOOK, ChatColor.BLUE + "Intellect", true),
		FARMING("farming", Material.WHEAT, ChatColor.YELLOW + "Farming", true),
		FISHING("fishing", Material.FISHING_ROD, ChatColor.AQUA + "Fishing", true),
		MAGIC("magic", Material.BLAZE_POWDER, ChatColor.LIGHT_PURPLE + "Magic", true),
		MASTERY("mastery", Material.NETHERITE_SWORD, ChatColor.DARK_PURPLE + "Mastery", true),
		PANELS(null, Material.LECTERN, ChatColor.GOLD + "Panels", false);

		private final String category;
		private final Material icon;
		private final String display;
		private final boolean skillTab;

		PanelTab(String category, Material icon, String display, boolean skillTab) {
			this.category = category;
			this.icon = icon;
			this.display = display;
			this.skillTab = skillTab;
		}
	}

	private static final class PanelViewState {
		private final PanelTab tab;
		private final int page;
		private final int totalPages;
		private final Map<Integer, String> slotToSkill = new HashMap<>();

		private PanelViewState(PanelTab tab, int page, int totalPages) {
			this.tab = tab;
			this.page = page;
			this.totalPages = totalPages;
		}
	}

	public UnifiedPanelGUI(SkillForgePlugin plugin) {
		this.plugin = plugin;
		this.guiPlayer = null;
		if (plugin.getCommand("skillpanel") != null) {
			plugin.getCommand("skillpanel").setExecutor(this);
		}

		// NEW: initialize and register the BindAbilityGUI listener once
		try {
			this.bindAbilityGUI = new BindAbilityGUI(plugin);
			Bukkit.getPluginManager().registerEvents(this.bindAbilityGUI, plugin);
		} catch (Throwable t) {
			this.bindAbilityGUI = null;
			plugin.getLogger().warning("BindAbilityGUI unavailable: " + t.getMessage());
		}
		try {
			this.skillUpgradeGUI = new SkillUpgradeGUI(plugin);
		} catch (Throwable t) {
			this.skillUpgradeGUI = null;
			plugin.getLogger().warning("SkillUpgradeGUI unavailable: " + t.getMessage());
		}
	}

	public UnifiedPanelGUI(SkillForgePlugin plugin, Player player) {
		this(plugin);
		this.guiPlayer = player;
	}

	public void open() {
		if (this.guiPlayer == null) return;
		openForPlayer(this.guiPlayer);
	}

	public void openForPlayer(Player player) {
		if (player == null) return;
		PanelViewState state = panelStates.get(player.getUniqueId());
		PanelTab tab = state != null ? state.tab : PanelTab.COMBAT;
		int page = state != null ? state.page : 1;
		openAdvancementPanel(player, tab, page);
	}

	private void showMainForPlayer(Player player) {
		PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
		player.sendMessage("§6§l== Adventurer's Panel ==");
		player.sendMessage("§7Evershards: §a" + data.getEvershards() + " §8| §7Skill Points: §b" + data.getSkillPoints());
		player.sendMessage("§7Commands: §e/skillpanel skills <category> [page] §8| §e/skillpanel bind <skillId>");
		player.sendMessage("§7Sanctum: §e/skillpanel tree [category]");
		player.sendMessage("§7Market: §e/skillpanel shop §8| §7Wardrobe: §e/skillpanel cosmetics");
		player.sendMessage("§7Donate: §e/skillpanel dono");
	}

	private void openAdvancementPanel(Player player, PanelTab tab, int page) {
		org.bukkit.inventory.Inventory inv = Bukkit.createInventory(null, 45, ADV_PANEL_TITLE);
		PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
		int evershards = data != null ? data.getEvershards() : 0;
		int skillPoints = data != null ? data.getSkillPoints() : 0;

		fillPanelBackground(inv);
		placeTabs(inv, player, tab);

		PanelViewState state;
		if (tab.skillTab && tab.category != null) {
			state = placeCategoryNodes(inv, player, tab, page);
		} else {
			state = placePanels(inv, player);
		}
		placeBottomBar(inv, tab, state.page, state.totalPages, evershards, skillPoints);
		panelStates.put(player.getUniqueId(), state);
		openPanelInventories.put(player.getUniqueId(), inv);
		player.openInventory(inv);
	}

	private void fillPanelBackground(org.bukkit.inventory.Inventory inv) {
		ItemStack wood = GuiStyle.pane(Material.BROWN_STAINED_GLASS_PANE, ChatColor.DARK_GRAY + " ");
		for (int slot = 0; slot < inv.getSize(); slot++) {
			if (slot <= SLOT_TAB_LAST) continue;
			if (slot >= SLOT_PREV) continue;
			inv.setItem(slot, wood.clone());
		}
	}

	private void placeTabs(org.bukkit.inventory.Inventory inv, Player player, PanelTab activeTab) {
		PanelTab[] tabs = PanelTab.values();
		for (int i = 0; i < tabs.length; i++) {
			PanelTab tab = tabs[i];
			List<String> lore = new ArrayList<>();
			if (tab.skillTab && tab.category != null) {
				int unlocked = getUnlockedInCategory(player, tab.category);
				int total = getTotalInCategory(tab.category);
				lore.add(ChatColor.GRAY + "Unlocked: " + ChatColor.GREEN + unlocked + ChatColor.GRAY + "/" + ChatColor.WHITE + total);
				lore.add(ChatColor.GRAY + "Click to view this tree.");
			} else {
				lore.add(ChatColor.GRAY + "Bind, quests, shop, cosmetics.");
			}
			if (tab == activeTab) lore.add(ChatColor.GOLD + "Selected");
			ItemStack item = makeMenuItem(tab.icon, tab.display + (tab == activeTab ? ChatColor.GOLD + " *" : ""), lore);
			if (tab == activeTab) {
				applyGlow(item);
			}
			inv.setItem(SLOT_TAB_FIRST + i, item);
		}
	}

	private PanelViewState placeCategoryNodes(org.bukkit.inventory.Inventory inv, Player player, PanelTab tab, int page) {
		List<SkillTreeSystem.SkillNode> nodes = plugin.getSkillTreeSystem().getSkillsByCategory(tab.category);
		if (nodes == null) nodes = Collections.emptyList();
		nodes = new ArrayList<>(nodes);

		int perPage = SKILL_SLOTS.length;
		int totalPages = Math.max(1, (nodes.size() + perPage - 1) / perPage);
		int safePage = Math.max(1, Math.min(page, totalPages));
		int start = (safePage - 1) * perPage;
		int end = Math.min(nodes.size(), start + perPage);

		PanelViewState state = new PanelViewState(tab, safePage, totalPages);
		PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
		Map<String, Integer> preferred = SkillTreeLayout.getCategorySlotMap(tab.category);
		Set<Integer> usedSlots = new HashSet<>();
		Map<String, Integer> skillToSlot = new HashMap<>();
		List<SkillTreeSystem.SkillNode> pageNodes = new ArrayList<>();
		for (int i = start; i < end; i++) {
			SkillTreeSystem.SkillNode node = nodes.get(i);
			if (node != null) pageNodes.add(node);
		}

		for (SkillTreeSystem.SkillNode node : pageNodes) {
			Integer mapped = preferred.get(node.getId());
			if (mapped == null || mapped < 0 || mapped >= SLOT_PREV) continue;
			if (!usedSlots.add(mapped)) continue;
			ItemStack item = createSkillNodeItem(node, data);
			inv.setItem(mapped, item);
			state.slotToSkill.put(mapped, node.getId());
			skillToSlot.put(node.getId(), mapped);
		}

		for (SkillTreeSystem.SkillNode node : pageNodes) {
			if (skillToSlot.containsKey(node.getId())) continue;
			for (int slot : SKILL_SLOTS) {
				if (!usedSlots.add(slot)) continue;
				ItemStack item = createSkillNodeItem(node, data);
				inv.setItem(slot, item);
				state.slotToSkill.put(slot, node.getId());
				skillToSlot.put(node.getId(), slot);
				break;
			}
		}

		drawTreeLinks(inv, skillToSlot);

		return state;
	}

	private PanelViewState placePanels(org.bukkit.inventory.Inventory inv, Player player) {
		PanelViewState state = new PanelViewState(PanelTab.PANELS, 1, 1);
		ItemStack[] items = new ItemStack[] {
				makeMenuItem(Material.KNOWLEDGE_BOOK, ChatColor.GREEN + "Skill Sanctum", List.of(ChatColor.GRAY + "Enter the pocket-dimension skill tree")),
				GuiIcons.icon(plugin, "bind", Material.BOOK, ChatColor.YELLOW + "Bind Skills",
						List.of(ChatColor.GRAY + "Bind unlocked active skills")),
				makeMenuItem(Material.AMETHYST_SHARD, ChatColor.LIGHT_PURPLE + "Innate Upgrade", List.of(ChatColor.GRAY + "Upgrade innate ability")),
				makeMenuItem(Material.EMERALD, ChatColor.GREEN + "Market", List.of(ChatColor.GRAY + "Open category shop")),
				GuiIcons.icon(plugin, "quest", Material.BOOK, ChatColor.AQUA + "Quest Board",
						List.of(ChatColor.GRAY + "Take and track quests")),
				makeMenuItem(Material.ELYTRA, ChatColor.LIGHT_PURPLE + "Cosmetics", List.of(ChatColor.GRAY + "Open wardrobe")),
				GuiIcons.icon(plugin, "crates", Material.CHEST, ChatColor.GOLD + "Crates",
						List.of(ChatColor.GRAY + "Open crate menu and key actions")),
				makeMenuItem(Material.EXPERIENCE_BOTTLE, ChatColor.GOLD + "Buy Skill Point", List.of(ChatColor.GRAY + "Cost: 100 Evershards"))
		};
		for (int i = 0; i < PANEL_SLOTS.length && i < items.length; i++) {
			inv.setItem(PANEL_SLOTS[i], items[i]);
		}
		inv.setItem(31, GuiIcons.icon(plugin, "skill-panel", Material.BOOK, ChatColor.GOLD + "Player Unlocks",
				List.of(ChatColor.GRAY + "Use tabs above to switch trees")));
		return state;
	}

	private void placeBottomBar(org.bukkit.inventory.Inventory inv, PanelTab tab, int page, int totalPages, int evershards, int skillPoints) {
		inv.setItem(SLOT_PREV, makeMenuItem(Material.ARROW, ChatColor.YELLOW + "Previous", List.of(ChatColor.GRAY + "Page " + Math.max(1, page - 1))));
		inv.setItem(SLOT_NEXT, makeMenuItem(Material.ARROW, ChatColor.YELLOW + "Next", List.of(ChatColor.GRAY + "Page " + Math.min(totalPages, page + 1))));
		inv.setItem(SLOT_CLOSE, makeMenuItem(Material.BARRIER, ChatColor.RED + "Close", List.of()));
		inv.setItem(SLOT_QUICK_PANELS, makeMenuItem(Material.LECTERN, ChatColor.GOLD + "Open Panels Tab", List.of(ChatColor.GRAY + "Quick switch")));
		inv.setItem(SLOT_QUICK_BUY, makeMenuItem(Material.EXPERIENCE_BOTTLE, ChatColor.GOLD + "Quick Buy SP", List.of(ChatColor.GRAY + "Cost: 100 Evershards")));
		inv.setItem(SLOT_STATUS, makeMenuItem(Material.NETHER_STAR, ChatColor.AQUA + "Status",
				List.of(ChatColor.GRAY + "Evershards: " + ChatColor.GREEN + evershards,
						ChatColor.GRAY + "Skill Points: " + ChatColor.YELLOW + skillPoints)));
		inv.setItem(SLOT_TAB_INFO, makeMenuItem(Material.BOOK, ChatColor.WHITE + "Tab: " + tab.display,
				List.of(ChatColor.GRAY + "Page " + page + "/" + totalPages)));
	}

	private ItemStack createSkillNodeItem(SkillTreeSystem.SkillNode node, PlayerData data) {
		String skillId = node.getId();
		int level = data != null ? data.getSkillLevel(skillId) : 0;
		boolean unlocked = level > 0;
		boolean maxed = level >= node.getMaxLevel();
		int tier = computeTier(level);
		Material material = resolveSkillMaterial(node, level, tier, unlocked);
		ItemStack item = new ItemStack(material);
		ItemMeta meta = item.getItemMeta();
		if (meta == null) return item;

		String prefix = maxed ? ChatColor.GOLD + "★ " : (unlocked ? ChatColor.GREEN + "● " : ChatColor.DARK_GRAY + "○ ");
		meta.setDisplayName(prefix + ChatColor.WHITE + node.getName());
		List<String> lore = new ArrayList<>();
		lore.add(ChatColor.GRAY + node.getDescription());
		lore.add("");
		lore.add(ChatColor.GRAY + "Level: " + ChatColor.WHITE + level + "/" + node.getMaxLevel());
		lore.add(ChatColor.GRAY + "Tier: " + ChatColor.YELLOW + (tier + 1) + ChatColor.GRAY + "/5");
		lore.add(ChatColor.GRAY + "Cost: " + ChatColor.YELLOW + node.getCostPerLevel() + " SP");
		appendRequirementLore(lore, node, data);
		lore.add(unlocked ? ChatColor.GREEN + "Unlocked" : ChatColor.RED + "Locked");
		if (maxed) lore.add(ChatColor.GOLD + "Max level reached");
		else lore.add(hasUnmetRequirements(node, data)
				? ChatColor.RED + "Requirements not met"
				: ChatColor.YELLOW + "Click to upgrade");
		meta.setLore(lore);

		if (unlocked) applyGlow(item, meta);
		else {
			meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
			if (meta instanceof Damageable dmg && item.getType().getMaxDurability() > 0) {
				dmg.setDamage(Math.max(0, item.getType().getMaxDurability() - 1));
			}
			item.setItemMeta(meta);
		}
		return item;
	}

	private void applyGlow(ItemStack item) {
		if (item == null) return;
		ItemMeta meta = item.getItemMeta();
		if (meta == null) return;
		applyGlow(item, meta);
	}

	private void applyGlow(ItemStack item, ItemMeta meta) {
		meta.addEnchant(Enchantment.UNBREAKING, 1, true);
		meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
		item.setItemMeta(meta);
	}

	private Material resolveSkillMaterial(SkillTreeSystem.SkillNode node, int level, int tier, boolean unlocked) {
		if (!unlocked) return Material.GRAY_DYE;
		if (node == null) return Material.BOOK;

		String id = node.getId() == null ? "" : node.getId().toLowerCase();
		String cat = node.getCategory() == null ? "" : node.getCategory().toLowerCase();
		int t = Math.max(0, Math.min(4, tier));

		if (cat.equals("fishing")) return Material.FISHING_ROD;
		if (id.contains("brick")) {
			Material[] mats = new Material[] {
					Material.BRICKS,
					Material.STONE_BRICKS,
					Material.MOSSY_STONE_BRICKS,
					Material.CRACKED_STONE_BRICKS,
					Material.CHISELED_STONE_BRICKS
			};
			return mats[t];
		}
		if (cat.equals("combat") || cat.equals("mastery")) {
			Material[] mats = new Material[] {
					Material.WOODEN_SWORD, Material.STONE_SWORD, Material.IRON_SWORD, Material.DIAMOND_SWORD, Material.NETHERITE_SWORD
			};
			return mats[t];
		}
		if (cat.equals("mining")) {
			Material[] mats = new Material[] {
					Material.WOODEN_PICKAXE, Material.STONE_PICKAXE, Material.IRON_PICKAXE, Material.DIAMOND_PICKAXE, Material.NETHERITE_PICKAXE
			};
			return mats[t];
		}
		if (cat.equals("agility")) {
			Material[] mats = new Material[] {
					Material.LEATHER_BOOTS, Material.CHAINMAIL_BOOTS, Material.IRON_BOOTS, Material.DIAMOND_BOOTS, Material.NETHERITE_BOOTS
			};
			return mats[t];
		}
		if (cat.equals("intellect")) {
			Material[] mats = new Material[] {
					Material.BOOK, Material.WRITABLE_BOOK, Material.ENCHANTED_BOOK, Material.ENDER_EYE, Material.NETHER_STAR
			};
			return mats[t];
		}
		if (cat.equals("farming")) {
			Material[] mats = new Material[] {
					Material.WOODEN_HOE, Material.STONE_HOE, Material.IRON_HOE, Material.DIAMOND_HOE, Material.NETHERITE_HOE
			};
			return mats[t];
		}
		if (cat.equals("magic")) {
			Material[] mats = new Material[] {
					Material.STICK, Material.BLAZE_ROD, Material.AMETHYST_SHARD, Material.END_ROD, Material.NETHER_STAR
			};
			return mats[t];
		}
		return Material.BOOK;
	}

	private int computeTier(int level) {
		if (level <= 0) return 0;
		return Math.max(0, Math.min(4, (level - 1) / SkillTreeSystem.TIER_LEVEL_SIZE));
	}

	private boolean hasUnmetRequirements(SkillTreeSystem.SkillNode node, PlayerData data) {
		if (node == null || data == null) return false;
		List<SkillTreeSystem.SkillRequirement> requirements = node.getRequirements();
		if (requirements == null || requirements.isEmpty()) return false;
		for (SkillTreeSystem.SkillRequirement req : requirements) {
			if (req == null) continue;
			if (data.getSkillLevel(req.getRequiredSkillId()) < req.getRequiredLevel()) return true;
		}
		return false;
	}

	private void appendRequirementLore(List<String> lore, SkillTreeSystem.SkillNode node, PlayerData data) {
		if (lore == null || node == null || data == null) return;
		List<SkillTreeSystem.SkillRequirement> requirements = node.getRequirements();
		if (requirements == null || requirements.isEmpty()) {
			lore.add(ChatColor.DARK_GREEN + "Root Skill");
			return;
		}
		lore.add(ChatColor.GRAY + "Requirements:");
		Map<String, SkillTreeSystem.SkillNode> all = plugin.getSkillTreeSystem().getAllSkillNodes();
		for (SkillTreeSystem.SkillRequirement req : requirements) {
			if (req == null) continue;
			int have = data.getSkillLevel(req.getRequiredSkillId());
			boolean met = have >= req.getRequiredLevel();
			SkillTreeSystem.SkillNode reqNode = all.get(req.getRequiredSkillId());
			String reqName = reqNode != null ? reqNode.getName() : req.getRequiredSkillId();
			lore.add((met ? ChatColor.GREEN : ChatColor.RED)
					+ "- " + reqName + " " + have + "/" + req.getRequiredLevel());
		}
	}

	private void drawTreeLinks(org.bukkit.inventory.Inventory inv, Map<String, Integer> skillToSlot) {
		if (inv == null || skillToSlot == null || skillToSlot.isEmpty()) return;
		Map<String, SkillTreeSystem.SkillNode> allNodes = plugin.getSkillTreeSystem().getAllSkillNodes();
		Set<Integer> occupied = new HashSet<>(skillToSlot.values());
		ItemStack link = GuiStyle.pane(Material.GRAY_STAINED_GLASS_PANE, ChatColor.DARK_GRAY + " ");

		for (Map.Entry<String, Integer> entry : skillToSlot.entrySet()) {
			SkillTreeSystem.SkillNode child = allNodes.get(entry.getKey());
			if (child == null || child.getRequirements().isEmpty()) continue;
			int childSlot = entry.getValue();
			for (SkillTreeSystem.SkillRequirement req : child.getRequirements()) {
				if (req == null) continue;
				Integer parentSlot = skillToSlot.get(req.getRequiredSkillId());
				if (parentSlot == null) continue;
				drawPath(inv, parentSlot, childSlot, occupied, link);
			}
		}
	}

	private void drawPath(org.bukkit.inventory.Inventory inv, int from, int to, Set<Integer> occupied, ItemStack link) {
		int r = from / 9;
		int c = from % 9;
		int tr = to / 9;
		int tc = to % 9;

		while (r != tr) {
			r += Integer.compare(tr, r);
			int slot = r * 9 + c;
			placeLink(inv, slot, occupied, link);
		}
		while (c != tc) {
			c += Integer.compare(tc, c);
			int slot = r * 9 + c;
			placeLink(inv, slot, occupied, link);
		}
	}

	private void placeLink(org.bukkit.inventory.Inventory inv, int slot, Set<Integer> occupied, ItemStack link) {
		if (slot < 0 || slot >= SLOT_PREV) return;
		if (occupied.contains(slot)) return;
		inv.setItem(slot, link);
	}

	private int getUnlockedInCategory(Player player, String category) {
		PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
		int count = 0;
		for (SkillTreeSystem.SkillNode node : plugin.getSkillTreeSystem().getSkillsByCategory(category)) {
			if (data.getSkillLevel(node.getId()) > 0) count++;
		}
		return count;
	}

	private int getTotalInCategory(String category) {
		List<SkillTreeSystem.SkillNode> nodes = plugin.getSkillTreeSystem().getSkillsByCategory(category);
		return nodes == null ? 0 : nodes.size();
	}

	private ItemStack makeMenuItem(Material mat, String name, List<String> lore) {
		ItemStack item = new ItemStack(mat);
		ItemMeta meta = item.getItemMeta();
		if (meta != null) {
			meta.setDisplayName(name);
			meta.setLore(lore == null ? List.of() : lore);
			meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
			item.setItemMeta(meta);
		}
		return item;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (!(sender instanceof Player)) {
			sender.sendMessage("This command can only be used by players.");
			return true;
		}
		Player player = (Player) sender;

		// /skillpanel bind ...
		if (args.length >= 1 && args[0].equalsIgnoreCase("bind")) {
			// allow: /skillpanel bind [page] or /skillpanel bind <skillId>
			if (args.length == 1 || (args.length == 2 && args[1].matches("\\d+"))) {
				int page = 1;
				if (args.length == 2 && args[1].matches("\\d+")) page = Integer.parseInt(args[1]);
				// prefer the GUI-backed binder if available
				if (this.bindAbilityGUI != null) {
					this.bindAbilityGUI.openForPlayer(player);
					return true;
				}
				// fallback to text pager
				showBindablePaged(player, page);
				return true;
			}
			// bind specific skill
			String skillId = args[1].toLowerCase();
			// personal generated skill binding first
			Map<String,Integer> gen = plugin.getGeneratedSkillsFor(player);
			if (gen != null && gen.containsKey(skillId)) {
				// create and give off-hand item tied to the personal skill
				ItemStack item = createSkillBindItem(skillId, "Personal: " + skillId);
				player.getInventory().setItemInOffHand(item);
				player.sendMessage("Bound personal skill " + skillId + " to off-hand.");
				return true;
			}
			// search all categories for the skillId (SkillTreeSystem)
			String[] cats = new String[] {"combat","mining","agility","intellect","farming","fishing","magic","mastery"};
			SkillTreeSystem.SkillNode found = null;
			for (String c : cats) {
				List<SkillTreeSystem.SkillNode> skills = plugin.getSkillTreeSystem().getSkillsByCategory(c);
				if (skills == null) continue;
				for (SkillTreeSystem.SkillNode s : skills) {
					if (s.getId().equalsIgnoreCase(skillId) || s.getName().equalsIgnoreCase(skillId)) {
						found = s;
						break;
					}
				}
				if (found != null) break;
			}
			// fallback: try registry lookup (reflection-backed, safe)
			LocalSkill regSkill = null;
			if (found == null) {
				regSkill = findSkillInRegistry(skillId);
			}
			if (found == null && regSkill == null) {
				player.sendMessage("Skill not found: " + skillId);
				return true;
			}
			if (found != null) {
				int playerLevel = plugin.getPlayerDataManager().getPlayerData(player).getSkillLevel(found.getId());
				if (playerLevel <= 0) {
					player.sendMessage("You have not unlocked this skill yet: " + found.getId());
					return true;
				}
				ItemStack bindItem = createSkillBindItem(found.getId(), found.getName());
				player.getInventory().setItemInOffHand(bindItem);
				player.sendMessage("Bound " + found.getName() + " to off-hand.");
				return true;
			}
			// registry-based binding
			if (regSkill != null) {
				int level = plugin.getPlayerDataManager().getPlayerData(player).getSkillLevel(regSkill.id);
				if (level <= 0) {
					player.sendMessage("You have not unlocked this skill yet: " + regSkill.id);
					return true;
				}
				ItemStack bindItem = createSkillBindItem(regSkill.id, regSkill.name != null ? regSkill.name : regSkill.id);
				player.getInventory().setItemInOffHand(bindItem);
				player.sendMessage("Bound registry skill " + (regSkill.name != null ? regSkill.name : regSkill.id) + " to off-hand.");
				return true;
			}
		}

		if (args.length == 0 || args[0].equalsIgnoreCase("main") || args[0].equalsIgnoreCase("gui")) {
			if (plugin.getSkillSanctumSystem() != null && plugin.getSkillSanctumSystem().isEnabled()) {
				plugin.getSkillSanctumSystem().open(player, "combat");
			} else if (plugin.getSkillGraphSystem() != null) {
				plugin.getSkillGraphSystem().open(player, "combat");
			} else {
				openForPlayer(player);
			}
			return true;
		}

		if (args[0].equalsIgnoreCase("tree") || args[0].equalsIgnoreCase("graph")) {
			boolean sanctumAvailable = plugin.getSkillSanctumSystem() != null && plugin.getSkillSanctumSystem().isEnabled();
			if (!sanctumAvailable && plugin.getSkillGraphSystem() == null) {
				player.sendMessage(ChatColor.RED + "Skill sanctum is unavailable right now.");
				return true;
			}
			String category = args.length >= 2 ? args[1].toLowerCase() : "combat";
			List<String> validCategories = sanctumAvailable
					? plugin.getSkillSanctumSystem().getCategories()
					: plugin.getSkillGraphSystem().getCategories();
			if (!validCategories.contains(category)) {
				player.sendMessage(ChatColor.YELLOW + "Unknown category '" + category + "'.");
				player.sendMessage(ChatColor.GRAY + "Valid: " + String.join(", ", validCategories));
				return true;
			}
			if (sanctumAvailable) {
				plugin.getSkillSanctumSystem().open(player, category);
			} else {
				plugin.getSkillGraphSystem().open(player, category);
			}
			return true;
		}

		// ...existing buysp ...
		if (args[0].equalsIgnoreCase("buysp")) {
			if (plugin.getEvershardSystem().purchaseSkillPoint(player)) {
				player.sendMessage("You purchased 1 Skill Point!");
			} else {
				player.sendMessage("Not enough Evershards!");
			}
			return true;
		}

		// cosmetics preserved
		if (args[0].equalsIgnoreCase("cosmetics")) {
			// try to open server-side Cosmetics GUI if present
			try {
				// try constructor (SkillForgePlugin, Player)
				Class<?> cls = Class.forName("com.skilltree.plugin.gui.CosmeticsGUI");
				try {
					Constructor<?> c = cls.getConstructor(SkillForgePlugin.class, org.bukkit.entity.Player.class);
					Object gui = c.newInstance(plugin, player);
					Method open = cls.getMethod("open");
					open.invoke(gui);
					return true;
				} catch (NoSuchMethodException e) {
					// try constructor (SkillForgePlugin) then open(player)
					Constructor<?> c2 = cls.getConstructor(SkillForgePlugin.class);
					Object gui = c2.newInstance(plugin);
					try {
						Method openP = cls.getMethod("open", org.bukkit.entity.Player.class);
						openP.invoke(gui, player);
						return true;
					} catch (NoSuchMethodException ex2) {
						// try open() then send player somehow (best-effort)
						Method openNoArgs = cls.getMethod("open");
						openNoArgs.invoke(gui);
						player.sendMessage("Opened cosmetics GUI (server-side).");
						return true;
					}
				}
			} catch (ClassNotFoundException cnf) {
				player.sendMessage("Cosmetics GUI not available.");
				return true;
			} catch (Throwable t) {
				player.sendMessage("Failed to open cosmetics GUI. See server log.");
				plugin.getLogger().warning("Error opening CosmeticsGUI: " + t);
				return true;
			}
		}

		// shop: simple server shop (skill points)
		if (args[0].equalsIgnoreCase("shop")) {
			if (args.length == 1) {
				player.sendMessage("== Player Shop ==");
				player.sendMessage("skillpoint - 100 Evershards (buy: /skillpanel shop buy skillpoint)");
				player.sendMessage("Use /skillpanel shop buy <item>");
				return true;
			}
			if (args.length >= 3 && args[1].equalsIgnoreCase("buy")) {
				String item = args[2].toLowerCase();
				if (item.equals("skillpoint")) {
					// reuse existing purchaseSkillPoint logic for consistency
					if (plugin.getEvershardSystem().purchaseSkillPoint(player)) {
						player.sendMessage("You bought 1 Skill Point from the shop.");
					} else {
						player.sendMessage("Not enough Evershards to buy skillpoint.");
					}
					return true;
				}
				player.sendMessage("Unknown shop item: " + item);
				return true;
			}
			player.sendMessage("Usage: /skillpanel shop  OR  /skillpanel shop buy <item>");
			return true;
		}

		// donation command + scheduler
		if (args[0].equalsIgnoreCase("dono") || args[0].equalsIgnoreCase("donate")) {
			// immediate message + schedule recurring reminders every 2 hours
			sendRandomDonationMessage(player, true);
			scheduleDonationReminders();
			player.sendMessage("Donation reminders scheduled (every 2 hours).");
			return true;
		}

		// skills listing with pagination
		if (args[0].equalsIgnoreCase("skills")) {
			if (args.length == 1) {
				player.sendMessage("Categories: combat, mining, agility, intellect, farming, fishing, magic, mastery, personal");
				player.sendMessage("View: /skillpanel skills <category> [page] (opens upgrade GUI)");
				return true;
			}
			String category = args[1].toLowerCase();
			if (category.equals("personal")) {
				Map<String, Integer> generated = plugin.getGeneratedSkillsFor(player);
				if (generated == null || generated.isEmpty()) {
					player.sendMessage("You have no personal generated skills yet.");
					return true;
				}
				player.sendMessage("Personal Skills:");
				for (Map.Entry<String, Integer> e : generated.entrySet()) {
					player.sendMessage(String.format("[%s] Level: %d", e.getKey(), e.getValue()));
				}
				return true;
			}
			int page = 1;
			if (args.length >= 3 && args[2].matches("\\d+")) page = Integer.parseInt(args[2]);
			if (this.skillUpgradeGUI != null) {
				this.skillUpgradeGUI.open(player, category, page);
				return true;
			}
			List<SkillTreeSystem.SkillNode> skills = plugin.getSkillTreeSystem().getSkillsByCategory(category);
			if (skills == null || skills.isEmpty()) {
				player.sendMessage("No skills found for category: " + category);
				return true;
			}
			showSkillsPage(player, category, skills, page);
			return true;
		}

		// existing upgrade handling
		if (args[0].equalsIgnoreCase("upgrade")) {
			if (args.length == 1 || (args.length >= 2 && args[1].equalsIgnoreCase("gui"))) {
				if (this.skillUpgradeGUI != null) {
					this.skillUpgradeGUI.open(player);
				} else {
					player.sendMessage("The upgrade panel is unavailable right now.");
				}
				return true;
			}
			if (args.length < 3) {
				player.sendMessage("Usage: /skillpanel upgrade gui OR /skillpanel upgrade <category> <skillId>");
				return true;
			}
			String category = args[1].toLowerCase();
			String skillId = args[2];

			List<SkillTreeSystem.SkillNode> skills = plugin.getSkillTreeSystem().getSkillsByCategory(category);
			if (skills == null) {
				player.sendMessage("Unknown category: " + category);
				return true;
			}

			SkillTreeSystem.SkillNode target = null;
			for (SkillTreeSystem.SkillNode s : skills) {
				if (s.getId().equalsIgnoreCase(skillId) || s.getName().equalsIgnoreCase(skillId)) {
					target = s;
					break;
				}
			}
			if (target == null) {
				player.sendMessage("Skill not found: " + skillId);
				return true;
			}

			SkillTreeSystem.UpgradeResult result = plugin.getSkillTreeSystem().tryUpgradeSkill(player, target.getId());
			if (result == SkillTreeSystem.UpgradeResult.SUCCESS) {
				player.sendMessage("Upgraded " + target.getName() + "!");
			} else {
				player.sendMessage(plugin.getSkillTreeSystem().getUpgradeFailureMessage(player, target.getId(), result));
			}
			return true;
		}

		player.sendMessage("Unknown subcommand. Usage: /skillpanel [main|tree|buysp|cosmetics|skills|bind|shop|dono|upgrade]");
		return true;
	}

	@EventHandler
	public void onPanelClick(InventoryClickEvent event) {
		if (!(event.getWhoClicked() instanceof Player player)) return;
		if (!isPanelTitle(event.getView().getTitle())) return;
		if (!isTrackedPanelInventory(player, event.getView().getTopInventory())) return;
		event.setCancelled(true);

		int raw = event.getRawSlot();
		if (raw < 0 || raw >= event.getView().getTopInventory().getSize()) return;
		ItemStack clicked = event.getCurrentItem();
		if (clicked == null || clicked.getType() == Material.AIR) return;

		PanelViewState state = panelStates.get(player.getUniqueId());
		if (state == null) {
			openForPlayer(player);
			return;
		}

		// Top tab click.
		if (raw >= SLOT_TAB_FIRST && raw <= SLOT_TAB_LAST) {
			PanelTab[] tabs = PanelTab.values();
			int idx = raw - SLOT_TAB_FIRST;
			if (idx >= 0 && idx < tabs.length) {
				openAdvancementPanel(player, tabs[idx], 1);
			}
			return;
		}

		if (raw == SLOT_CLOSE) {
			player.closeInventory();
			panelStates.remove(player.getUniqueId());
			openPanelInventories.remove(player.getUniqueId());
			return;
		}
		if (raw == SLOT_QUICK_PANELS) {
			openAdvancementPanel(player, PanelTab.PANELS, 1);
			return;
		}
		if (raw == SLOT_QUICK_BUY) {
			if (plugin.getEvershardSystem().purchaseSkillPoint(player)) {
				player.sendMessage(ChatColor.GREEN + "Purchased 1 Skill Point.");
			} else {
				player.sendMessage(ChatColor.RED + "Not enough Evershards.");
			}
			openAdvancementPanel(player, state.tab, state.page);
			return;
		}

		if (state.tab.skillTab) {
			if (raw == SLOT_PREV && state.page > 1) {
				openAdvancementPanel(player, state.tab, state.page - 1);
				return;
			}
			if (raw == SLOT_NEXT && state.page < state.totalPages) {
				openAdvancementPanel(player, state.tab, state.page + 1);
				return;
			}
			String skillId = state.slotToSkill.get(raw);
			if (skillId == null) return;
			SkillTreeSystem.SkillNode node = plugin.getSkillTreeSystem().getAllSkillNodes().get(skillId);
			if (node == null) return;
			PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
			int before = data.getSkillLevel(skillId);
			if (before >= node.getMaxLevel()) {
				player.sendMessage(ChatColor.GRAY + node.getName() + " is already maxed.");
				return;
			}
			SkillTreeSystem.UpgradeResult result = plugin.getSkillTreeSystem().tryUpgradeSkill(player, skillId);
			if (result == SkillTreeSystem.UpgradeResult.SUCCESS) {
				player.sendMessage(ChatColor.GREEN + "Unlocked/Upgraded " + node.getName() + " to level " + (before + 1) + ".");
			} else {
				player.sendMessage(ChatColor.RED + plugin.getSkillTreeSystem().getUpgradeFailureMessage(player, skillId, result));
			}
			openAdvancementPanel(player, state.tab, state.page);
			return;
		}

		// Panels tab actions.
		int panelIdx = -1;
		for (int i = 0; i < PANEL_SLOTS.length; i++) {
			if (PANEL_SLOTS[i] == raw) {
				panelIdx = i;
				break;
			}
		}
		if (panelIdx < 0) return;

		switch (panelIdx) {
			case 0 -> {
				if (plugin.getSkillSanctumSystem() != null && plugin.getSkillSanctumSystem().isEnabled()) {
					plugin.getSkillSanctumSystem().open(player, "combat");
				} else if (plugin.getSkillGraphSystem() != null) {
					plugin.getSkillGraphSystem().open(player, "combat");
				} else {
					openAllSkills(player);
				}
			}
			case 1 -> {
				if (this.bindAbilityGUI != null) this.bindAbilityGUI.openForPlayer(player);
				else player.sendMessage(ChatColor.RED + "Bind panel unavailable.");
			}
			case 2 -> {
				if (plugin.getInnateUpgradeGUI() != null) plugin.getInnateUpgradeGUI().openForPlayer(player);
				else player.sendMessage(ChatColor.RED + "Innate panel unavailable.");
			}
			case 3 -> ShopGUI.openCategories(player);
			case 4 -> {
				if (plugin.getQuestGUI() != null) plugin.getQuestGUI().open(player);
				else player.sendMessage(ChatColor.RED + "Quest panel unavailable.");
			}
			case 5 -> new CosmeticsGUI(plugin, player).open();
			case 6 -> player.performCommand("crate");
			case 7 -> {
				if (plugin.getEvershardSystem().purchaseSkillPoint(player)) {
					player.sendMessage(ChatColor.GREEN + "Purchased 1 Skill Point.");
				} else {
					player.sendMessage(ChatColor.RED + "Not enough Evershards.");
				}
				openAdvancementPanel(player, PanelTab.PANELS, 1);
			}
			default -> {
			}
		}
	}

	@EventHandler
	public void onPanelDrag(InventoryDragEvent event) {
		if (!(event.getWhoClicked() instanceof Player player)) return;
		if (!isPanelTitle(event.getView().getTitle())) return;
		if (!isTrackedPanelInventory(player, event.getView().getTopInventory())) return;
		event.setCancelled(true);
	}

	@EventHandler
	public void onPanelClose(InventoryCloseEvent event) {
		if (!(event.getPlayer() instanceof Player player)) return;
		if (!isPanelTitle(event.getView().getTitle())) return;
		org.bukkit.inventory.Inventory top = event.getView().getTopInventory();
		org.bukkit.inventory.Inventory tracked = openPanelInventories.get(player.getUniqueId());
		if (tracked != null && tracked.equals(top)) {
			panelStates.remove(player.getUniqueId());
			openPanelInventories.remove(player.getUniqueId());
		}
	}

	private boolean isPanelTitle(String title) {
		if (title == null) return false;
		String clean = ChatColor.stripColor(title);
		return clean != null && clean.contains("Player Unlocks");
	}

	private boolean isTrackedPanelInventory(Player player, org.bukkit.inventory.Inventory top) {
		if (player == null || top == null) return false;
		org.bukkit.inventory.Inventory tracked = openPanelInventories.get(player.getUniqueId());
		return tracked != null && tracked.equals(top);
	}

	private void openCategory(Player player, String category) {
		PanelTab resolved = PanelTab.COMBAT;
		for (PanelTab tab : PanelTab.values()) {
			if (tab.skillTab && tab.category != null && tab.category.equalsIgnoreCase(category)) {
				resolved = tab;
				break;
			}
		}
		openAdvancementPanel(player, resolved, 1);
	}

	private void openAllSkills(Player player) {
		if (this.skillUpgradeGUI != null) {
			this.skillUpgradeGUI.open(player);
		} else {
			player.sendMessage(ChatColor.RED + "Skill upgrade panel unavailable.");
		}
	}

	// show bindable skills paged
	private void showBindablePaged(Player player, int page) {
		String[] cats = new String[] {"combat","mining","agility","intellect","farming","fishing","magic","mastery","personal"};
		List<String> lines = new ArrayList<>();
		for (String cat : cats) {
			lines.add("Category: " + cat);
			if (cat.equals("personal")) {
				Map<String,Integer> generated = plugin.getGeneratedSkillsFor(player);
				if (generated == null || generated.isEmpty()) {
					lines.add("  (no personal skills)");
				} else {
					for (Map.Entry<String,Integer> e : generated.entrySet()) {
						lines.add(String.format("  %s (Level %d) - bind: /skillpanel bind %s", e.getKey(), e.getValue(), e.getKey()));
					}
				}
				continue;
			}
			Set<String> shown = new HashSet<>();
			List<SkillTreeSystem.SkillNode> skills = plugin.getSkillTreeSystem().getSkillsByCategory(cat);
			if (skills != null && !skills.isEmpty()) {
				for (SkillTreeSystem.SkillNode s : skills) {
					int lvl = plugin.getPlayerDataManager().getPlayerData(player).getSkillLevel(s.getId());
					String typeTag = isPassiveSkillNode(s) ? "[Passive]" : "[Active]";
					lines.add(String.format("  %s %s (Level %d) - /skillpanel bind %s", s.getId(), typeTag, lvl, s.getId()));
					shown.add(s.getId().toLowerCase());
				}
			}
			List<LocalSkill> reg = getRegistrySkillsByCategory(cat);
			if (reg != null && !reg.isEmpty()) {
				for (LocalSkill ls : reg) {
					if (ls.id == null) continue;
					if (shown.contains(ls.id.toLowerCase())) continue;
					lines.add(String.format("  %s (Registry) - /skillpanel bind %s", ls.id, ls.id));
					shown.add(ls.id.toLowerCase());
				}
			}
			if ((skills == null || skills.isEmpty()) && (reg == null || reg.isEmpty())) {
				lines.add("  (no skills)");
			}
		}
		// paginate lines
		int per = 10;
		int totalPages = Math.max(1, (lines.size() + per - 1) / per);
		page = Math.max(1, Math.min(page, totalPages));
		player.sendMessage("Bindable Skills — Page " + page + "/" + totalPages);
		int start = (page - 1) * per;
		for (int i = start; i < Math.min(lines.size(), start + per); i++) player.sendMessage(lines.get(i));
		// scrolling arrows
		String prev = page > 1 ? "« Prev (/skillpanel bind " + (page-1) + ")" : "«";
		String next = page < totalPages ? "Next (/skillpanel bind " + (page+1) + ") »" : "»";
		player.sendMessage(prev + "  |  " + next);
	}

	private void showSkillsPage(Player player, String category, List<SkillTreeSystem.SkillNode> skills, int page) {
		List<String> lines = new ArrayList<>();
		for (SkillTreeSystem.SkillNode s : skills) {
			int lvl = plugin.getPlayerDataManager().getPlayerData(player).getSkillLevel(s.getId());
			String typeTag = isPassiveSkillNode(s) ? "[Passive]" : "[Active]";
			lines.add(String.format("%s %s — Level %d/%d  Cost: %d SP", s.getId(), typeTag, lvl, s.getMaxLevel(), s.getCostPerLevel()));
		}
		int per = 8;
		int total = Math.max(1, (lines.size() + per - 1) / per);
		page = Math.max(1, Math.min(page, total));
		player.sendMessage(category.toUpperCase() + " Skills — Page " + page + "/" + total);
		int start = (page - 1) * per;
		for (int i = start; i < Math.min(lines.size(), start + per); i++) player.sendMessage(lines.get(i));
		String prev = page > 1 ? "« Prev (/skillpanel skills " + category + " " + (page-1) + ")" : "«";
		String next = page < total ? "Next (/skillpanel skills " + category + " " + (page+1) + ") »" : "»";
		player.sendMessage(prev + "  |  " + next);
	}

	// helper to create a simple ItemStack representing a bound skill
	private ItemStack createSkillBindItem(String id, String displayName) {
		Material mat = Material.BOOK;
		// simple mapping for certain skill ids -> item types (expand if needed)
		if (id.contains("undead") || id.contains("undead_defender")) mat = Material.TOTEM_OF_UNDYING;
		else if (id.contains("combat")) mat = Material.IRON_SWORD;
		else if (id.contains("mining")) mat = Material.DIAMOND_PICKAXE;
		else if (id.contains("fishing")) mat = Material.FISHING_ROD;
		else if (id.contains("farming")) mat = Material.WHEAT;
		else if (id.contains("agility")) mat = Material.FEATHER;
		else if (id.contains("intellect") || id.contains("magic")) mat = Material.ENCHANTED_BOOK;

		boolean isMeteor = id.toLowerCase().contains("meteor");
		boolean isWhirl = id.toLowerCase().contains("whirlwind") || id.toLowerCase().contains("whirl");
		if (isMeteor) mat = Material.BLAZE_POWDER;
		if (isWhirl) mat = Material.GUNPOWDER;

		ItemStack item = new ItemStack(mat);
		ItemMeta meta = item.getItemMeta();
		if (meta != null) {
			String titlePrefix = (isMeteor || isWhirl) ? "[Nerfed] " : "";
			meta.setDisplayName(titlePrefix + displayName);
			List<String> lore = new ArrayList<>();
			if (isMeteor) {
				lore.add("NERFED: Reduced damage, increased cooldown when bound.");
				lore.add("Tip: Use unbound for full power.");
			}
			if (isWhirl) {
				lore.add("NERFED: Whirlwind binding reduces knockback and radius.");
				lore.add("Tip: Upgrade the skill instead of binding for better effect.");
			}
			lore.add("Equip in off-hand for effect");
			lore.add("Skill ID: " + id);
			meta.setLore(lore);
			item.setItemMeta(meta);
		}
		return item;
	}

	// NEW: small local holder for registry-found skills
	private static class LocalSkill {
		String id;
		String name;
		Object raw;
		LocalSkill(String id, String name, Object raw) { this.id = id; this.name = name; this.raw = raw; }
	}

	// NEW: try to find a skill in the SkillRegistry via reflection (safe - no compile-time dependency)
	private LocalSkill findSkillInRegistry(String skillId) {
		try {
			Method m = plugin.getClass().getMethod("getSkillRegistry");
			Object registry = m.invoke(plugin);
			if (registry == null) return null;

			// try common single-get methods
			String[] singleNames = new String[] {"getSkill", "getSkillById", "findSkill", "get", "find"};
			for (String name : singleNames) {
				try {
					Method gm = registry.getClass().getMethod(name, String.class);
					Object s = gm.invoke(registry, skillId);
					if (s != null) {
						String id = tryGetString(s, new String[]{"getId","getIdentifier","getKey","id"});
						String nm = tryGetString(s, new String[]{"getName","getDisplayName","name"});
						return new LocalSkill(id != null ? id : skillId, nm != null ? nm : skillId, s);
					}
				} catch (NoSuchMethodException ignore) {}
			}

			// try to iterate all skills if single-get not available
			String[] allNames = new String[] {"getAllSkills","getSkills","values","all","list"};
			for (String name : allNames) {
				try {
					Method am = registry.getClass().getMethod(name);
					Object all = am.invoke(registry);
					if (all instanceof Map) {
						for (Object v : ((Map<?,?>) all).values()) {
							String id = tryGetString(v, new String[]{"getId","getIdentifier","id"});
							if (id != null && id.equalsIgnoreCase(skillId)) {
								String nm = tryGetString(v, new String[]{"getName","getDisplayName","name"});
								return new LocalSkill(id, nm, v);
							}
						}
					} else if (all instanceof Iterable) {
						for (Object v : (Iterable<?>) all) {
							String id = tryGetString(v, new String[]{"getId","getIdentifier","id"});
							if (id != null && id.equalsIgnoreCase(skillId)) {
								String nm = tryGetString(v, new String[]{"getName","getDisplayName","name"});
								return new LocalSkill(id, nm, v);
							}
						}
					}
				} catch (NoSuchMethodException ignore) {}
			}
		} catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
			// registry not present or different API; ignore silently
		}
		return null;
	}

	// NEW: attempt to collect registry skills by category (best-effort)
	private List<LocalSkill> getRegistrySkillsByCategory(String category) {
		List<LocalSkill> out = new ArrayList<>();
		try {
			Method m = plugin.getClass().getMethod("getSkillRegistry");
			Object registry = m.invoke(plugin);
			if (registry == null) return out;

			// try a method like getSkillsByCategory(String)
			try {
				Method catM = registry.getClass().getMethod("getSkillsByCategory", String.class);
				Object res = catM.invoke(registry, category);
				if (res instanceof Iterable) {
					for (Object s : (Iterable<?>) res) {
						String id = tryGetString(s, new String[]{"getId","getIdentifier","id"});
						String nm = tryGetString(s, new String[]{"getName","getDisplayName","name"});
						if (id != null) out.add(new LocalSkill(id, nm, s));
					}
					return out;
				}
			} catch (NoSuchMethodException ignore) {}

			// fallback: iterate all and filter by category property
			String[] allNames = new String[] {"getAllSkills","getSkills","values","all","list"};
			for (String name : allNames) {
				try {
					Method am = registry.getClass().getMethod(name);
					Object all = am.invoke(registry);
					if (all instanceof Map) {
						for (Object v : ((Map<?,?>) all).values()) {
							String cat = tryGetString(v, new String[]{"getCategory","category","getGroup"});
							if (cat != null && cat.equalsIgnoreCase(category)) {
								String id = tryGetString(v, new String[]{"getId","getIdentifier","id"});
								String nm = tryGetString(v, new String[]{"getName","getDisplayName","name"});
								if (id != null) out.add(new LocalSkill(id, nm, v));
							}
						}
					} else if (all instanceof Iterable) {
						for (Object v : (Iterable<?>) all) {
							String cat = tryGetString(v, new String[]{"getCategory","category","getGroup"});
							if (cat != null && cat.equalsIgnoreCase(category)) {
								String id = tryGetString(v, new String[]{"getId","getIdentifier","id"});
								String nm = tryGetString(v, new String[]{"getName","getDisplayName","name"});
								if (id != null) out.add(new LocalSkill(id, nm, v));
							}
						}
					}
				} catch (NoSuchMethodException ignore) {}
			}
		} catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
			// ignore - registry not present / different API
		}
		return out;
	}

	// small reflective helper: try multiple getter names returning String
	private String tryGetString(Object obj, String[] methodNames) {
		if (obj == null) return null;
		for (String n : methodNames) {
			try {
				Method gm = obj.getClass().getMethod(n);
				Object r = gm.invoke(obj);
				if (r instanceof String) return (String) r;
			} catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {}
			// try field fallback
			try {
				Field f = obj.getClass().getField(n);
				Object r = f.get(obj);
				if (r instanceof String) return (String) r;
			} catch (NoSuchFieldException | IllegalAccessException ignored) {}
		}
		return null;
	}

	// passive detection helpers (same as before)
	private boolean isPassiveSkillNode(SkillTreeSystem.SkillNode node) {
		if (node == null) return false;
		try {
			Method m = node.getClass().getMethod("isPassive");
			Object r = m.invoke(node);
			if (r instanceof Boolean) return (Boolean) r;
		} catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {}
		try {
			Method m = node.getClass().getMethod("getType");
			Object r = m.invoke(node);
			if (r instanceof String) return "passive".equalsIgnoreCase((String) r);
			if (r != null && r.toString().toLowerCase().contains("passive")) return true;
		} catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {}
		// fallback by id naming convention
		String id = node.getId();
		return id != null && (id.toLowerCase().startsWith("passive_") || id.toLowerCase().contains("_passive"));
	}

	private boolean isPassiveLocalSkill(LocalSkill ls) {
		if (ls == null || ls.raw == null) return false;
		Object raw = ls.raw;
		try {
			Method m = raw.getClass().getMethod("isPassive");
			Object r = m.invoke(raw);
			if (r instanceof Boolean) return (Boolean) r;
		} catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {}
		try {
			Method m = raw.getClass().getMethod("getType");
			Object r = m.invoke(raw);
			if (r instanceof String) return "passive".equalsIgnoreCase((String) r);
			if (r != null && r.toString().toLowerCase().contains("passive")) return true;
		} catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {}
		try {
			Field f = raw.getClass().getField("type");
			Object r = f.get(raw);
			if (r instanceof String) return "passive".equalsIgnoreCase((String) r);
		} catch (NoSuchFieldException | IllegalAccessException ignored) {}
		return ls.id != null && (ls.id.toLowerCase().startsWith("passive_") || ls.id.toLowerCase().contains("_passive"));
	}

	// schedule recurring donation reminders (2 hours) — now config-driven & safe
	private void scheduleDonationReminders() {
		// config: donation.enabled (boolean), donation.goal (double), donation.period-hours (long)
		boolean enabled = false;
		double goal = 25.0;
		long periodHours = 2L;
		try {
			enabled = plugin.getConfig().getBoolean("donation.enabled", false);
			goal = plugin.getConfig().getDouble("donation.goal", 25.0);
			periodHours = plugin.getConfig().getLong("donation.period-hours", 2L);
		} catch (Exception ignored) {}

		if (!enabled) {
			plugin.getLogger().info("Donation reminders are disabled in config; skipping schedule.");
			return;
		}

		// cancel previous task if present (safe on plugin reload)
		if (donationTask != null && !donationTask.isCancelled()) {
			donationTask.cancel();
			donationTask = null;
		}

		long periodTicks = Math.max(1L, periodHours) * 60L * 60L * 20L;
		// log server timezone and current time for diagnostics
		plugin.getLogger().info("Scheduling donation reminders. Server timezone: " + java.time.ZoneId.systemDefault()
			+ " | now: " + java.time.ZonedDateTime.now() + " | period-hours: " + periodHours + " | goal: $" + goal);

		// make a final copy of goal for use inside the lambda (avoids effectively-final capture error)
		final double goalFinal = goal;

		donationTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
			String[] msgs = new String[] {
				"Server upkeep reminder: help keep the server online — goal: $" + goalFinal + ".",
				"We appreciate support! Contribute toward the $" + goalFinal + " hosting goal to help upkeep the server.",
				"Enjoying the server? Consider donating. We're aiming for $" + goalFinal + " to cover hosting costs.",
				"Small donations help a lot — we're trying to reach $" + goalFinal + " to keep the server online."
			};
			String m = msgs[RAND.nextInt(msgs.length)];
			// broadcast once per run on the main server thread
			Bukkit.getServer().broadcastMessage("[Donate] " + m);
		}, 20L, periodTicks);
	}

	// send a randomized donation message to a player (immediate) — uses config goal if present
	private void sendRandomDonationMessage(Player player, boolean includeGoal) {
		double goal = 25.0;
		try { goal = plugin.getConfig().getDouble("donation.goal", 25.0); } catch (Exception ignored) {}
		String[] variants = new String[] {
			"Consider donating to help keep the server online.",
			"If you enjoy the server, donations help with hosting costs.",
			"Help us stay online — any contribution helps!"
		};
		String m = variants[RAND.nextInt(variants.length)];
		if (includeGoal) m = m + " We are aiming for $" + goal + ".";
		player.sendMessage("[Donate] " + m);
	}
}
