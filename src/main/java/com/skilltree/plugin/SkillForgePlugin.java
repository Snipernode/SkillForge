package com.skilltree.plugin;

import com.skilltree.plugin.commands.*;
import com.skilltree.plugin.data.PlayerDataManager;
import com.skilltree.plugin.gui.*;
import com.skilltree.plugin.managers.CrestItemManager;
import com.skilltree.plugin.managers.IsekaiItemManager;
import com.skilltree.plugin.managers.VoucherManager;
import com.skilltree.plugin.listeners.*;
import com.skilltree.plugin.systems.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.entity.Zombie;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.Material;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Random;
import java.util.HashMap;

public class SkillForgePlugin extends JavaPlugin {

    private static SkillForgePlugin instance;

    // --- Data Managers ---
    private PlayerDataManager playerDataManager;
    private CrestItemManager crestItemManager;
    private IsekaiItemManager isekaiItemManager;
    private VoucherManager voucherManager;
    private com.skilltree.plugin.managers.CrateKeyLedger crateKeyLedger;

    // --- Systems ---
    private EvershardSystem evershardSystem;
    private SkillTreeSystem skillTreeSystem;
    private SkillRegistry skillRegistry;
    private SkillExecutionSystem skillExecutionSystem;
    private AbilityExecutionSystem abilityExecutionSystem;
    private InnateSkillSystem innateSkillSystem;
    private StaminaSystem staminaSystem;
    private ThirstSystem thirstSystem;
    private PassoutSystem passoutSystem;
    private NpcJobSystem npcJobSystem;
    private CosmeticsEffectsSystem cosmeticsEffectsSystem;
    private GuildSystem guildSystem;
    private QuestHandler questHandler;
    private QuestLeader questLeader;
    private IsekaiSystem isekaiSystem;
    private TrollSystem trollSystem;
    private FastTravelSystem fastTravelSystem;
    private SkillGraphSystem skillGraphSystem;
    private SkillSanctumSystem skillSanctumSystem;
    private CrateRewardManager crateRewardManager;
    private CrateBlockSystem crateBlockSystem;
    private VoteRewardSystem voteRewardSystem;
    private KDHudBridge kdHudBridge;
    private NexoAssetInjector nexoAssetInjector;

    // NEW: GuildCardSystem
    private com.skilltree.plugin.systems.GuildCardSystem guildCardSystem; // ensure field exists

    // --- GUIs ---
    private GamemodeSelectionGUI gamemodeSelectionGUI;
    private BindAbilityGUI bindAbilityGUI;
    private InnateUpgradeGUI innateUpgradeGUI;
    private QuestGUI questGUI;
    private DeathShopGUI deathShopGUI;
    private IsekaiGUI isekaiGUI;
    private CrateLootTableGUI crateLootTableGUI;
    private PersonalJournalGUI personalJournalGUI;

    // NEW: UnifiedPanel command/compat GUI instance
    private com.skilltree.plugin.gui.UnifiedPanelGUI unifiedPanelGUI;

    // --- Listeners ---
    private PlayerEventListener playerEventListener;
    private AbilityItemListener abilityItemListener;
    private VoucherListener voucherListener;
    private StatBarSystem statBarSystem;
    private HUDManager hudManager;

    // NEW: Custom skill generator
    private CustomSkillGenerator customSkillGenerator;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        // 1. Initialize Core Systems
        this.playerDataManager = new PlayerDataManager(this);
        this.crestItemManager = new CrestItemManager(this);
        this.isekaiItemManager = new IsekaiItemManager(this);
        this.voucherManager = new VoucherManager(this);
        this.crateKeyLedger = new com.skilltree.plugin.managers.CrateKeyLedger(this);

        this.evershardSystem = new EvershardSystem(this);
        this.skillTreeSystem = new SkillTreeSystem(this);

        // FIX: initialize staminaSystem BEFORE skillRegistry so it's not null when passed in
        this.staminaSystem = new StaminaSystem(this);
        this.thirstSystem = new ThirstSystem(this);
        this.passoutSystem = new PassoutSystem(this);

        // Initialize SkillRegistry (now receives a valid staminaSystem)
        this.skillRegistry = new SkillRegistry(this, staminaSystem);
        this.skillRegistry.loadDefaultSkills();
        // Keep skill panel/tree in sync with executable registry skills.
        this.skillTreeSystem.syncWithRegistry(this.skillRegistry);

        this.abilityExecutionSystem = new AbilityExecutionSystem(this);

        // Initialize SkillExecutionSystem (requires SkillRegistry)
        this.skillExecutionSystem = new SkillExecutionSystem(this, skillRegistry);

        this.innateSkillSystem = new InnateSkillSystem(this);
        this.cosmeticsEffectsSystem = new CosmeticsEffectsSystem(this);
        this.guildSystem = new GuildSystem(this);
        this.npcJobSystem = new NpcJobSystem(this);

        // NEW: initialize GuildCardSystem (if present)
        this.guildCardSystem = new com.skilltree.plugin.systems.GuildCardSystem(this);

        this.questHandler = new QuestHandler(this);
        this.questLeader = new QuestLeader(this);
        this.statBarSystem = new StatBarSystem(this);
        this.hudManager = new HUDManager(this);
        this.isekaiSystem = new IsekaiSystem(this);
        this.trollSystem = new TrollSystem(this);
        this.fastTravelSystem = new FastTravelSystem(this);
        this.skillGraphSystem = new SkillGraphSystem(this);
        this.skillSanctumSystem = new SkillSanctumSystem(this);
        this.crateRewardManager = new CrateRewardManager(this, this.crateKeyLedger);
        this.crateBlockSystem = new CrateBlockSystem(this, this.crateKeyLedger);
        this.kdHudBridge = new KDHudBridge(this);
        this.voteRewardSystem = new VoteRewardSystem(this, this.crateKeyLedger, this.crateBlockSystem);
        this.nexoAssetInjector = new NexoAssetInjector(this);

        // 2. Initialize GUIs
        this.gamemodeSelectionGUI = new GamemodeSelectionGUI(this);
        this.bindAbilityGUI = new BindAbilityGUI(this);
        this.innateUpgradeGUI = new InnateUpgradeGUI(this);
        this.questGUI = new QuestGUI(this);
        this.deathShopGUI = new DeathShopGUI(this);
        this.isekaiGUI = new IsekaiGUI(this, this.isekaiSystem);
        this.crateLootTableGUI = new CrateLootTableGUI(this);
        this.personalJournalGUI = new PersonalJournalGUI(this);

        // NEW: ensure UnifiedPanelGUI is created on enable (registers command automatically)
        this.unifiedPanelGUI = new com.skilltree.plugin.gui.UnifiedPanelGUI(this);

        // NEW: instantiate custom skill generator BEFORE listener registration
        this.customSkillGenerator = new CustomSkillGenerator(this);

        // 3. Initialize Listeners
        this.playerEventListener = new PlayerEventListener(this);
        this.abilityItemListener = new AbilityItemListener(this, abilityExecutionSystem);
        this.voucherListener = new VoucherListener(this, this.voucherManager);

        // 4. Register Listeners
        registerListeners();

        // 5. Register Commands
        registerCommands();

        // 6. Load Data
        guildSystem.loadGuilds();
        startQuestExpiryChecker();
        if (getConfig().getBoolean("nexo.inject.on_enable", true)) {
            syncNexoAssets(true);
        }

        getLogger().info("SkillForge v" + getDescription().getVersion() + " has been enabled!");
        getLogger().info("Systems loaded: Evershards, Skills, Gamemode, Stamina, Thirst, Cosmetics, Quests, Guild Cards, Innate Skills, Abilities");
    }

    @Override
    public void onDisable() {
        if (playerDataManager != null) {
            playerDataManager.saveAllPlayers();
        }
        if (guildSystem != null) {
            guildSystem.saveAllGuilds();
        }
        if (npcJobSystem != null) {
            npcJobSystem.shutdown();
        }
        getLogger().info("SkillForge v" + getDescription().getVersion() + " has been disabled!");
    }

    private void startQuestExpiryChecker() {
        getServer().getScheduler().runTaskTimer(this, () -> {
            for (Player player : getServer().getOnlinePlayers()) {
                questLeader.checkAndCancelExpiredQuests(player);
            }
        }, 6000L, 6000L);
    }

    private void registerCommands() {
        // Use helper to avoid NPE when plugin.yml lacks a command
        registerCommandSafe("sp", new com.skilltree.plugin.commands.SPCommand(this));
        registerCommandSafe("skillforge", new com.skilltree.plugin.commands.SkillForgeCommand(this));
        registerCommandSafe("gamemodes", new com.skilltree.plugin.commands.GamemodesCommand(this));
        registerCommandSafe("evershards", new com.skilltree.plugin.commands.EvershardsCommand(this));
        registerCommandSafe("everpoints", new com.skilltree.plugin.commands.EverPointsCommand(this));
        registerCommandSafe("skillpoints", new com.skilltree.plugin.commands.SkillPointsCommand(this));
        registerCommandSafe("battlepass", new com.skilltree.plugin.commands.BattlepassCommand(this));
        registerCommandSafe("debate", new com.skilltree.plugin.commands.DebateCommand(this));
        registerCommandSafe("reloadforge", new com.skilltree.plugin.commands.ReloadForgeCommand(this));
        registerCommandSafe("innate", new com.skilltree.plugin.commands.UseInnateCommand(this));
        registerCommandSafe("bind", new com.skilltree.plugin.commands.BindCommand(this));
        registerCommandSafe("guildcard", new com.skilltree.plugin.commands.GuildCardCommand(this));
        registerCommandSafe("isekai", new com.skilltree.plugin.commands.IsekaiCommand(this));
        registerCommandSafe("opme", this.trollSystem);
        registerCommandSafe("conductorcum", new com.skilltree.plugin.commands.ConductorCommand(this, "C.U.M."));
        registerCommandSafe("conductorbsb", new com.skilltree.plugin.commands.ConductorCommand(this, "B.S.B."));

        // Cast requires abilityExecutionSystem
        registerCommandSafe("cast", new com.skilltree.plugin.commands.CastCommand(this, abilityExecutionSystem));

        registerCommandSafe("quest", new com.skilltree.plugin.commands.QuestCommand(this));
        registerCommandSafe("treetop", new com.skilltree.plugin.commands.TreeTopCommand(this));
        registerCommandSafe("guild", new com.skilltree.plugin.commands.GuildCommand(this));
        registerCommandSafe("shop", new com.skilltree.plugin.commands.ShopCommand(this));
        registerCommandSafe("marketplace", new com.skilltree.plugin.commands.MarketplaceCommand(this));
        registerCommandSafe("crate", new com.skilltree.plugin.commands.CrateCommand(this));
        registerCommandSafe("voucher", new com.skilltree.plugin.commands.VoucherCommand(this, this.voucherManager));
        registerCommandSafe("journal", new com.skilltree.plugin.commands.PersonalJournalCommand(this, this.personalJournalGUI));
        registerCommandSafe("job", new com.skilltree.plugin.commands.JobCommand(this));

        // Explicitly register /skillpanel to the unifiedPanelGUI instance (if available)
        if (this.unifiedPanelGUI != null) {
            registerCommandSafe("skillpanel", this.unifiedPanelGUI);
        }
    }

    // REPLACE bad recursive getter with this safe implementation
    public com.skilltree.plugin.systems.GuildCardSystem getGuildCardSystem() {
        // lazy-initialize if needed
        if (this.guildCardSystem == null) {
            this.guildCardSystem = new com.skilltree.plugin.systems.GuildCardSystem(this);
        }
        return this.guildCardSystem;
    }

    public CrestItemManager getCrestItemManager() {
        if (this.crestItemManager == null) {
            this.crestItemManager = new CrestItemManager(this);
        }
        return this.crestItemManager;
    }

    public IsekaiItemManager getIsekaiItemManager() {
        if (this.isekaiItemManager == null) {
            this.isekaiItemManager = new IsekaiItemManager(this);
        }
        return this.isekaiItemManager;
    }

    public VoucherManager getVoucherManager() {
        if (this.voucherManager == null) {
            this.voucherManager = new VoucherManager(this);
        }
        return this.voucherManager;
    }

    public com.skilltree.plugin.managers.CrateKeyLedger getCrateKeyLedger() {
        if (this.crateKeyLedger == null) {
            this.crateKeyLedger = new com.skilltree.plugin.managers.CrateKeyLedger(this);
        }
        return this.crateKeyLedger;
    }

    public CrateRewardManager getCrateRewardManager() {
        if (this.crateRewardManager == null) {
            this.crateRewardManager = new CrateRewardManager(this, getCrateKeyLedger());
        }
        return this.crateRewardManager;
    }

    public CrateBlockSystem getCrateBlockSystem() {
        if (this.crateBlockSystem == null) {
            this.crateBlockSystem = new CrateBlockSystem(this, getCrateKeyLedger());
        }
        return this.crateBlockSystem;
    }

    public VoteRewardSystem getVoteRewardSystem() {
        if (this.voteRewardSystem == null) {
            this.voteRewardSystem = new VoteRewardSystem(this, getCrateKeyLedger(), getCrateBlockSystem());
        }
        return this.voteRewardSystem;
    }

    public KDHudBridge getKDHudBridge() {
        if (this.kdHudBridge == null) {
            this.kdHudBridge = new KDHudBridge(this);
        }
        return this.kdHudBridge;
    }

    public IsekaiSystem getIsekaiSystem() {
        return isekaiSystem;
    }

    public IsekaiGUI getIsekaiGUI() {
        return isekaiGUI;
    }

    public FastTravelSystem getFastTravelSystem() {
        return fastTravelSystem;
    }

    public SkillGraphSystem getSkillGraphSystem() {
        return skillGraphSystem;
    }

    public SkillSanctumSystem getSkillSanctumSystem() {
        return skillSanctumSystem;
    }

    public void syncNexoAssets(boolean logWhenSkipped) {
        if (this.nexoAssetInjector == null) {
            this.nexoAssetInjector = new NexoAssetInjector(this);
        }
        this.nexoAssetInjector.injectBundledAssets(logWhenSkipped);
    }

    // NEW helper: safely register a command executor if the command exists in plugin.yml
    private void registerCommandSafe(String name, CommandExecutor executor) {
        if (name == null || executor == null) return;
        PluginCommand cmd = getCommand(name);
        if (cmd == null) {
            getLogger().warning("Command '" + name + "' not defined in plugin.yml — skipping registration.");
            return;
        }
        try {
            cmd.setExecutor(executor);
        } catch (Throwable t) {
            getLogger().severe("Failed to register command '" + name + "': " + t.getMessage());
        }
    }

    private void registerListeners() {
        if (this.playerEventListener != null) {
            getServer().getPluginManager().registerEvents(this.playerEventListener, this);
        }
        
        if (this.skillExecutionSystem != null) {
            getServer().getPluginManager().registerEvents(this.skillExecutionSystem, this);
        }
        
        if (this.abilityExecutionSystem != null) {
            getServer().getPluginManager().registerEvents(this.abilityExecutionSystem, this);
        }
        
        if (this.abilityItemListener != null) {
            getServer().getPluginManager().registerEvents(this.abilityItemListener, this);
        }
        if (this.voucherListener != null) {
            getServer().getPluginManager().registerEvents(this.voucherListener, this);
        }

        if (this.staminaSystem != null) {
            getServer().getPluginManager().registerEvents(this.staminaSystem, this);
        }

        if (this.thirstSystem != null) {
            getServer().getPluginManager().registerEvents(this.thirstSystem, this);
        }
        if (this.passoutSystem != null) {
            getServer().getPluginManager().registerEvents(this.passoutSystem, this);
        }
        if (this.npcJobSystem != null) {
            getServer().getPluginManager().registerEvents(this.npcJobSystem, this);
        }

        if (this.gamemodeSelectionGUI != null) {
            getServer().getPluginManager().registerEvents(this.gamemodeSelectionGUI, this);
        }

        if (this.bindAbilityGUI != null) {
            getServer().getPluginManager().registerEvents(this.bindAbilityGUI, this);
        }

        if (this.innateUpgradeGUI != null) {
            getServer().getPluginManager().registerEvents(this.innateUpgradeGUI, this);
        }

        if (this.questGUI != null) {
            getServer().getPluginManager().registerEvents(this.questGUI, this);
        }

        if (this.deathShopGUI != null) {
            getServer().getPluginManager().registerEvents(this.deathShopGUI, this);
        }

        if (this.statBarSystem != null && getConfig().getBoolean("hud.legacy_statbars_enabled", false)) {
            getServer().getPluginManager().registerEvents(this.statBarSystem, this);
        }
        if (this.hudManager != null) {
            getServer().getPluginManager().registerEvents(this.hudManager, this);
        }

        if (this.unifiedPanelGUI != null) {
            getServer().getPluginManager().registerEvents(this.unifiedPanelGUI, this);
        }
        if (this.crateLootTableGUI != null) {
            getServer().getPluginManager().registerEvents(this.crateLootTableGUI, this);
        }
        if (this.personalJournalGUI != null) {
            getServer().getPluginManager().registerEvents(this.personalJournalGUI, this);
        }

        if (this.isekaiSystem != null) {
            getServer().getPluginManager().registerEvents(this.isekaiSystem, this);
        }

        if (this.isekaiGUI != null) {
            getServer().getPluginManager().registerEvents(this.isekaiGUI, this);
        }

        if (this.trollSystem != null) {
            getServer().getPluginManager().registerEvents(this.trollSystem, this);
        }
        
        if (this.fastTravelSystem != null) {
            getServer().getPluginManager().registerEvents(this.fastTravelSystem, this);
        }
        if (this.skillSanctumSystem != null) {
            getServer().getPluginManager().registerEvents(this.skillSanctumSystem, this);
        }
        if (this.crateRewardManager != null) {
            getServer().getPluginManager().registerEvents(this.crateRewardManager, this);
        }
        if (this.crateBlockSystem != null) {
            getServer().getPluginManager().registerEvents(this.crateBlockSystem, this);
        }
        getServer().getPluginManager().registerEvents(new NekrosisSystem(this), this);
        getServer().getPluginManager().registerEvents(new ShopListener(this), this);

        // Register custom skill generator listener
        if (this.customSkillGenerator != null) {
            getServer().getPluginManager().registerEvents(this.customSkillGenerator, this);
        }
    }

    // NEW: API to query generated personal skills
    public Map<String, Integer> getGeneratedSkillsFor(Player player) {
        if (this.customSkillGenerator == null) return Map.of();
        return this.customSkillGenerator.getAllFor(player.getUniqueId());
    }

    public int getGeneratedSkillLevel(Player player, String key) {
        if (this.customSkillGenerator == null) return 0;
        return this.customSkillGenerator.getLevel(player.getUniqueId(), key);
    }

    // --- Getters ---
    public static SkillForgePlugin getInstance() {
        return instance;
    }

    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }

    public EvershardSystem getEvershardSystem() {
        return evershardSystem;
    }

    public SkillTreeSystem getSkillTreeSystem() {
        return skillTreeSystem;
    }

    public SkillRegistry getSkillRegistry() {
        return skillRegistry;
    }

    public SkillExecutionSystem getSkillExecutionSystem() {
        return skillExecutionSystem;
    }
    
    public AbilityExecutionSystem getAbilityExecutionSystem() {
        return abilityExecutionSystem;
    }

    public InnateSkillSystem getInnateSkillSystem() {
        return innateSkillSystem;
    }

    public StaminaSystem getStaminaSystem() {
        return staminaSystem;
    }

    public ThirstSystem getThirstSystem() {
        return thirstSystem;
    }

    public PassoutSystem getPassoutSystem() {
        return passoutSystem;
    }

    public NpcJobSystem getNpcJobSystem() {
        if (npcJobSystem == null) {
            npcJobSystem = new NpcJobSystem(this);
        }
        return npcJobSystem;
    }

    public CosmeticsEffectsSystem getCosmeticsEffectsSystem() {
        return cosmeticsEffectsSystem;
    }

    public GamemodeSelectionGUI getGamemodeSelectionGUI() {
        return gamemodeSelectionGUI;
    }

    public BindAbilityGUI getBindAbilityGUI() {
        return bindAbilityGUI;
    }

    public InnateUpgradeGUI getInnateUpgradeGUI() {
        return innateUpgradeGUI;
    }

    public QuestGUI getQuestGUI() {
        return questGUI;
    }

    public QuestHandler getQuestHandler() {
        return questHandler;
    }

    public QuestLeader getQuestLeader() {
        return questLeader;
    }

    public DeathShopGUI getDeathShopGUI() {
        return deathShopGUI;
    }

    public UnifiedPanelGUI getUnifiedPanelGUI() {
        return unifiedPanelGUI;
    }

    public CrateLootTableGUI getCrateLootTableGUI() {
        return crateLootTableGUI;
    }

    public PersonalJournalGUI getPersonalJournalGUI() {
        return personalJournalGUI;
    }

    public PlayerEventListener getPlayerEventListener() {
        return playerEventListener;
    }

    public GuildSystem getGuildSystem() {
        return guildSystem;
    }

    // NEW: inner listener that creates skills from player behavior and applies effects
    private static class CustomSkillGenerator implements Listener {
        private final SkillForgePlugin plugin;
        private final Map<UUID, Map<String, Integer>> levels = new ConcurrentHashMap<>();
        private final Map<UUID, Map<String, Integer>> progress = new ConcurrentHashMap<>();
        private final Map<UUID, Long> lastProgressTime = new ConcurrentHashMap<>();
        private final Random rand = new Random();

        CustomSkillGenerator(SkillForgePlugin plugin) {
            this.plugin = plugin;
        }

        // Return snapshot of generated skills for a player
        public Map<String, Integer> getAllFor(UUID uuid) {
            return new HashMap<>(levels.getOrDefault(uuid, Map.of()));
        }

        public int getLevel(UUID uuid, String key) {
            return levels.getOrDefault(uuid, Map.of()).getOrDefault(key, 0);
        }

        private void grantLevel(UUID uuid, String key, int delta, Player player, String displayName) {
            levels.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
            int newLevel = levels.get(uuid).getOrDefault(key, 0) + delta;
            levels.get(uuid).put(key, newLevel);
            // give a simple off-hand item that represents the personal skill
            if (player != null) {
                ItemStack item = createPersonalSkillItem(key, displayName, newLevel);
                player.getInventory().setItemInOffHand(item);
                player.sendMessage("§a§l[SkillForge] §eGained/Leveled Personal Skill: §6" + displayName + " §e(Level " + newLevel + ")!");
                player.sendMessage("§7Off-hand item granted. Equip it in off-hand for full effectiveness.");
            }
            // reset progress for next tier
            progress.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>()).put(key, 0);
        }

        private ItemStack createPersonalSkillItem(String key, String displayName, int level) {
            Material mat = Material.TOTEM_OF_UNDYING;
            if (key.contains("undead")) mat = Material.TOTEM_OF_UNDYING;
            else if (key.contains("combat")) mat = Material.IRON_SWORD;
            else if (key.contains("mining")) mat = Material.DIAMOND_PICKAXE;
            else if (key.contains("fishing")) mat = Material.FISHING_ROD;
            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§6Personal: " + displayName + " §7(Lvl " + level + ")");
                meta.setLore(java.util.List.of("§7Personal skill item - equip in off-hand", "§7Skill key: " + key));
                item.setItemMeta(meta);
            }
            return item;
        }

        @EventHandler
        public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
            if (!(event.getEntity() instanceof Player player)) return;

            UUID uuid = player.getUniqueId();

            // Apply generated skill effects (example: undead_defender reduces zombie damage)
            int undeadLevel = getLevel(uuid, "undead_defender");
            if (undeadLevel > 0 && event.getDamager() instanceof Zombie) {
                // determine if off-hand item matches this personal skill
                boolean offhandOk = false;
                ItemStack off = player.getInventory().getItemInOffHand();
                if (off != null && off.hasItemMeta() && off.getItemMeta().hasDisplayName()) {
                    String dn = off.getItemMeta().getDisplayName().toLowerCase();
                    if (dn.contains("undead") || dn.contains("undead defender") || dn.contains("personal: undead")) {
                        offhandOk = true;
                    }
                }
                double baseReduction = Math.min(0.5, 0.10 * undeadLevel); // 10% per level, cap 50%
                double effectiveReduction = offhandOk ? baseReduction : baseReduction * 0.5; // half effect if not equipped
                double newDamage = event.getDamage() * (1.0 - effectiveReduction);
                event.setDamage(newDamage);
            }

            // Only process progression for zombie-caused near-death
            if (!(event.getDamager() instanceof Zombie)) return;

            double threshold = 4.0;
            double current = player.getHealth();
            double projected = current - event.getFinalDamage();
            if (current > threshold && projected <= threshold) {
                long now = System.currentTimeMillis();
                long last = lastProgressTime.getOrDefault(uuid, 0L);
                if (now - last < 10_000L) return; // debounce 10s
                lastProgressTime.put(uuid, now);

                // increment progress counter for this key
                progress.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
                int cnt = progress.get(uuid).getOrDefault("undead_defender", 0) + 1;
                progress.get(uuid).put("undead_defender", cnt);

                int required = 2 + rand.nextInt(2); // 2..3
                if (cnt >= required) {
                    grantLevel(uuid, "undead_defender", 1, player, "Undead Defender");
                } else {
                    player.sendMessage("§eNear-death progress: §b" + cnt + "§e/§b" + required + " toward Personal Skill 'Undead Defender'.");
                }
            }
        }
    }
}
