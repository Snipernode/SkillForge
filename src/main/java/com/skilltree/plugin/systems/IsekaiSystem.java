package com.skilltree.plugin.systems;

import com.skilltree.plugin.SkillForgePlugin;
import com.skilltree.plugin.data.PlayerData;
import com.skilltree.plugin.managers.IsekaiItemManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class IsekaiSystem implements Listener {

    private static final String COOLDOWN_PREFIX = "isekai:";

    private final SkillForgePlugin plugin;
    private final IsekaiItemManager itemManager;
    private final Map<String, AbilityDef> abilityDefs = new HashMap<>();
    private final Random random = new Random();
    private final Map<UUID, Long> lastGrowthAt = new ConcurrentHashMap<>();

    public IsekaiSystem(SkillForgePlugin plugin) {
        this.plugin = plugin;
        this.itemManager = plugin.getIsekaiItemManager();
        registerAbilities();
    }

    public boolean hasIsekaiItem(Player player) {
        if (player == null) return false;
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
        return data != null && data.hasIsekaiItem();
    }

    public boolean isEligible(Player player) {
        EligibilityStatus status = checkEligibility(player);
        return status.eligible;
    }

    public void sendEligibilityMessage(Player player) {
        EligibilityStatus status = checkEligibility(player);
        if (status.eligible) {
            player.sendMessage(ChatColor.GREEN + "You are eligible for reincarnation. Use /isekai.");
            return;
        }
        player.sendMessage(ChatColor.RED + "You are not yet eligible for /isekai.");
        if (status.requireCurrentMaxed) {
            player.sendMessage(ChatColor.GRAY + "Current skill total: " + ChatColor.YELLOW + status.currentTotal
                    + ChatColor.GRAY + "/" + ChatColor.YELLOW + status.maxTotal);
        }
        player.sendMessage(ChatColor.GRAY + "Skill points earned: " + ChatColor.YELLOW + status.totalPurchased
                + ChatColor.GRAY + "/" + ChatColor.YELLOW + status.requiredPurchased);
        if (status.debateEnabled) {
            player.sendMessage(ChatColor.GRAY + "Debate ascension: " + ChatColor.LIGHT_PURPLE + status.debateEquivalent
                    + ChatColor.GRAY + "/" + ChatColor.LIGHT_PURPLE + status.requiredPurchased);
        }
        player.sendMessage(ChatColor.DARK_GRAY + "Reincarnation uses the stronger of your purchased-skill progression or debate ascension.");
    }

    public void showIsekaiInfo(Player player) {
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
        if (data == null || !data.hasIsekaiItem()) {
            player.sendMessage(ChatColor.RED + "You do not have a reincarnation relic.");
            return;
        }
        IsekaiItemType type = IsekaiItemType.fromKey(data.getIsekaiItemType());
        if (type == null) {
            player.sendMessage(ChatColor.RED + "Your relic type is unknown.");
            return;
        }
        ensureAbilities(player, data);
        int level = data.getIsekaiItemLevel();
        player.sendMessage(ChatColor.GOLD + "Reincarnation Relic: " + ChatColor.YELLOW + type.getDisplayName());
        player.sendMessage(ChatColor.GRAY + "Growth Level: " + ChatColor.AQUA + level);
        List<String> abilityKeys = data.getIsekaiAbilities();
        if (abilityKeys == null || abilityKeys.isEmpty()) {
            player.sendMessage(ChatColor.DARK_GRAY + "No abilities bound.");
            return;
        }
        for (String key : abilityKeys) {
            AbilityDef def = abilityDefs.get(key);
            if (def == null) continue;
            String prefix = def.type == AbilityType.ACTIVE ? "Active" : "Passive";
            player.sendMessage(ChatColor.LIGHT_PURPLE + prefix + ": " + ChatColor.GRAY + def.name);
        }
    }

    public void grantIsekaiItem(Player player, IsekaiItemType type) {
        if (player == null || type == null) return;
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
        if (data == null) return;

        if (!isEligible(player)) {
            sendEligibilityMessage(player);
            return;
        }

        if (data.hasIsekaiItem()) {
            player.sendMessage(ChatColor.YELLOW + "Your soul already bears a relic.");
            return;
        }

        String itemId = UUID.randomUUID().toString();
        List<String> abilities = generateAbilities(player, data, type);
        data.setIsekaiItemId(itemId);
        data.setIsekaiItemType(type.getKey());
        data.setIsekaiAbilities(abilities);
        data.setIsekaiItemLevel(1);

        ItemStack item = itemManager.createItem(player.getUniqueId(), itemId, type.getMaterial(),
                buildDisplayName(player, type), 1, buildAbilityDisplay(abilities));
        giveItem(player, item);

        player.sendMessage(ChatColor.LIGHT_PURPLE + "A reincarnation relic binds to your soul.");
        player.sendMessage(ChatColor.GRAY + "Use it to awaken your unique abilities.");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
        if (data == null || !data.hasIsekaiItem()) return;

        ensureAbilities(player, data);
        restoreIfMissing(player, data);
        syncItemLore(player, data);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
        if (data == null || !data.hasIsekaiItem()) return;
        data.setIsekaiLastSeenAt(System.currentTimeMillis());
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;
        if (player.isSneaking()) return; // avoid conflict with bound abilities

        ItemStack held = player.getInventory().getItemInMainHand();
        if (!isOwnedIsekaiItem(player, held)) return;

        boolean ranged = held.getType() == Material.BOW
                || held.getType() == Material.CROSSBOW
                || held.getType() == Material.TRIDENT;

        if (ranged) {
            if (event.getAction() != org.bukkit.event.block.Action.LEFT_CLICK_AIR) return;
        } else {
            switch (event.getAction()) {
                case RIGHT_CLICK_AIR:
                case RIGHT_CLICK_BLOCK:
                    if (event.getClickedBlock() != null && event.getClickedBlock().getType().isInteractable()) {
                        return;
                    }
                    break;
                default:
                    return;
            }
        }

        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
        if (data == null) return;

        ensureAbilities(player, data);
        String activeKey = getActiveAbilityKey(data);
        if (activeKey == null) {
            player.sendMessage(ChatColor.RED + "Your relic has no active ability.");
            return;
        }

        String cooldownKey = COOLDOWN_PREFIX + activeKey;
        if (data.isAbilityOnCooldown(cooldownKey)) {
            long remaining = data.getAbilityCooldownRemaining(cooldownKey);
            player.sendMessage(ChatColor.RED + "Relic ability on cooldown for " + (remaining / 1000) + "s");
            return;
        }

        int level = data.getIsekaiItemLevel();
        executeActiveAbility(activeKey, player, level);

        AbilityDef def = abilityDefs.get(activeKey);
        long cd = def != null ? def.cooldownMillis : 20000L;
        data.setAbilityCooldown(cooldownKey, cd);
        maybeGrow(player, data);
        event.setCancelled(true);
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        Player attacker = resolveAttacker(event.getDamager());
        if (attacker != null) {
            ItemStack held = attacker.getInventory().getItemInMainHand();
            if (isOwnedIsekaiItem(attacker, held)) {
                PlayerData data = plugin.getPlayerDataManager().getPlayerData(attacker);
                if (data != null) {
                    ensureAbilities(attacker, data);
                    int level = data.getIsekaiItemLevel();
                    applyOnHitPassives(event, attacker, level, data.getIsekaiAbilities());
                    maybeGrow(attacker, data);
                }
            }
        }

        if (event.getEntity() instanceof Player defender) {
            ItemStack held = defender.getInventory().getItemInMainHand();
            if (isOwnedIsekaiItem(defender, held)) {
                PlayerData data = plugin.getPlayerDataManager().getPlayerData(defender);
                if (data != null) {
                    ensureAbilities(defender, data);
                    int level = data.getIsekaiItemLevel();
                    applyOnDamagedPassives(event, defender, level, data.getIsekaiAbilities());
                    maybeGrow(defender, data);
                }
            }
        }
    }

    private void registerAbilities() {
        registerActive("active_arcane_burst", "Arcane Burst", 20000L);
        registerActive("active_ember_wave", "Ember Wave", 24000L);
        registerActive("active_void_grasp", "Void Grasp", 26000L);
        registerActive("active_soul_dash", "Soul Dash", 18000L);
        registerActive("active_time_dilation", "Time Dilation", 30000L);

        registerPassive("passive_vampiric", "Vampiric Edge");
        registerPassive("passive_warding", "Warding Sigil");
        registerPassive("passive_precision", "Precision Focus");
        registerPassive("passive_guardbreaker", "Guardbreaker");
        registerPassive("passive_momentum", "Momentum Drive");
        registerPassive("passive_thorns", "Soul Thorns");
        registerPassive("passive_execution", "Executioner");
        registerPassive("passive_bulwark", "Bulwark Oath");
    }

    private void registerActive(String key, String name, long cooldownMillis) {
        abilityDefs.put(key, new AbilityDef(key, name, AbilityType.ACTIVE, cooldownMillis));
    }

    private void registerPassive(String key, String name) {
        abilityDefs.put(key, new AbilityDef(key, name, AbilityType.PASSIVE, 0L));
    }

    private List<String> generateAbilities(Player player, PlayerData data, IsekaiItemType type) {
        int maxAbilities = Math.min(7, plugin.getConfig().getInt("isekai.max_abilities", 7));
        Set<String> chosen = new java.util.LinkedHashSet<>();

        List<String> activePool = new ArrayList<>(type.getActivePool());
        if (activePool.isEmpty()) {
            for (AbilityDef def : abilityDefs.values()) {
                if (def.type == AbilityType.ACTIVE) {
                    activePool.add(def.key);
                }
            }
        }
        if (!activePool.isEmpty()) {
            String active = activePool.get(random.nextInt(activePool.size()));
            chosen.add(active);
        }

        String preferred = getPreferredPassive(data);
        if (preferred != null) {
            chosen.add(preferred);
        }

        List<String> passives = new ArrayList<>();
        for (AbilityDef def : abilityDefs.values()) {
            if (def.type == AbilityType.PASSIVE) {
                passives.add(def.key);
            }
        }
        Collections.shuffle(passives, random);
        for (String passive : passives) {
            if (chosen.size() >= maxAbilities) break;
            chosen.add(passive);
        }

        return new ArrayList<>(chosen);
    }

    private String getPreferredPassive(PlayerData data) {
        if (data == null) return null;
        Map<String, Integer> totals = new HashMap<>();
        for (SkillTreeSystem.SkillNode node : plugin.getSkillTreeSystem().getAllSkillNodes().values()) {
            int level = data.getSkillLevel(node.getId());
            totals.put(node.getCategory(), totals.getOrDefault(node.getCategory(), 0) + level);
        }
        String topCategory = null;
        int topValue = -1;
        for (Map.Entry<String, Integer> entry : totals.entrySet()) {
            if (entry.getValue() > topValue) {
                topCategory = entry.getKey();
                topValue = entry.getValue();
            }
        }
        if (topCategory == null) return null;
        switch (topCategory.toLowerCase()) {
            case "combat":
                return "passive_precision";
            case "agility":
                return "passive_momentum";
            case "magic":
                return "passive_bulwark";
            case "farming":
            case "fishing":
                return "passive_warding";
            case "mining":
                return "passive_guardbreaker";
            default:
                return null;
        }
    }

    private void ensureAbilities(Player player, PlayerData data) {
        List<String> existing = data.getIsekaiAbilities();
        if (existing == null || existing.isEmpty()) {
            IsekaiItemType type = IsekaiItemType.fromKey(data.getIsekaiItemType());
            if (type == null) return;
            List<String> abilities = generateAbilities(player, data, type);
            data.setIsekaiAbilities(abilities);
        }
    }

    private String getActiveAbilityKey(PlayerData data) {
        List<String> abilities = data.getIsekaiAbilities();
        if (abilities == null) return null;
        for (String key : abilities) {
            AbilityDef def = abilityDefs.get(key);
            if (def != null && def.type == AbilityType.ACTIVE) {
                return key;
            }
        }
        return null;
    }

    private void executeActiveAbility(String key, Player player, int level) {
        switch (key) {
            case "active_arcane_burst":
                arcaneBurst(player, level);
                break;
            case "active_ember_wave":
                emberWave(player, level);
                break;
            case "active_void_grasp":
                voidGrasp(player, level);
                break;
            case "active_soul_dash":
                soulDash(player, level);
                break;
            case "active_time_dilation":
                timeDilation(player, level);
                break;
            default:
                player.sendMessage(ChatColor.RED + "Your relic stirs, but nothing happens.");
        }
    }

    private void arcaneBurst(Player player, int level) {
        double radius = 3.5 + (level * 0.15);
        double damage = 4.0 + (level * 0.6);
        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (entity instanceof LivingEntity living && !(entity instanceof Player)) {
                living.damage(damage, player);
                Vector knock = living.getLocation().toVector().subtract(player.getLocation().toVector()).normalize().multiply(0.4 + level * 0.01);
                living.setVelocity(knock);
            }
        }
        player.getWorld().spawnParticle(Particle.ENCHANT, player.getLocation(), 60, 1.2, 1.2, 1.2, 0.2);
        player.playSound(player.getLocation(), Sound.ENTITY_EVOKER_CAST_SPELL, 1.0f, 1.2f);
        player.sendMessage(ChatColor.LIGHT_PURPLE + "Arcane Burst!");
    }

    private void emberWave(Player player, int level) {
        double radius = 4.0 + (level * 0.2);
        double damage = 3.0 + (level * 0.4);
        int fireTicks = 40 + (level * 4);
        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (entity instanceof LivingEntity living && !(entity instanceof Player)) {
                living.damage(damage, player);
                living.setFireTicks(fireTicks);
            }
        }
        player.getWorld().spawnParticle(Particle.FLAME, player.getLocation(), 70, 1.5, 0.6, 1.5, 0.05);
        player.playSound(player.getLocation(), Sound.ITEM_FIRECHARGE_USE, 1.0f, 0.9f);
        player.sendMessage(ChatColor.GOLD + "Ember Wave!");
    }

    private void voidGrasp(Player player, int level) {
        int range = 10 + (level / 2);
        LivingEntity living = findTargetInSight(player, range);
        if (living == null || living instanceof Player) {
            player.sendMessage(ChatColor.RED + "No target in sight.");
            return;
        }
        Vector pull = player.getLocation().toVector().subtract(living.getLocation().toVector()).normalize().multiply(0.8 + level * 0.02);
        living.setVelocity(pull);
        living.damage(2.0 + (level * 0.3), player);
        player.getWorld().spawnParticle(Particle.PORTAL, living.getLocation(), 40, 0.5, 0.5, 0.5, 0.2);
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.8f);
        player.sendMessage(ChatColor.DARK_PURPLE + "Void Grasp!");
    }

    private LivingEntity findTargetInSight(Player player, int range) {
        if (player == null) return null;
        Vector eye = player.getEyeLocation().toVector();
        Vector dir = player.getEyeLocation().getDirection().normalize();
        double bestDist = Double.MAX_VALUE;
        LivingEntity best = null;
        for (Entity entity : player.getNearbyEntities(range, range, range)) {
            if (!(entity instanceof LivingEntity living)) continue;
            if (entity instanceof Player) continue;
            if (!player.hasLineOfSight(entity)) continue;
            Vector to = entity.getLocation().toVector().subtract(eye);
            double dist = to.length();
            if (dist > range || dist <= 0.1) continue;
            double dot = dir.dot(to.normalize());
            if (dot < 0.95) continue;
            if (dist < bestDist) {
                bestDist = dist;
                best = living;
            }
        }
        return best;
    }

    private void soulDash(Player player, int level) {
        Vector dir = player.getLocation().getDirection().normalize();
        player.setVelocity(dir.multiply(1.3 + (level * 0.02)));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40 + level * 2, 0, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 30, 0, false, false));
        player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 30, 0.6, 0.2, 0.6, 0.02);
        player.playSound(player.getLocation(), Sound.ENTITY_PHANTOM_FLAP, 1.0f, 1.1f);
        player.sendMessage(ChatColor.AQUA + "Soul Dash!");
    }

    private void timeDilation(Player player, int level) {
        double radius = 5.0 + (level * 0.2);
        int duration = 60 + (level * 2);
        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (entity instanceof LivingEntity living && !(entity instanceof Player)) {
                living.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration, 1, false, false));
            }
        }
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, duration, 1, false, false));
        player.getWorld().spawnParticle(Particle.ENCHANT, player.getLocation(), 40, 1.2, 0.5, 1.2, 0.2);
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.0f);
        player.sendMessage(ChatColor.BLUE + "Time Dilation!");
    }

    private void applyOnHitPassives(EntityDamageByEntityEvent event, Player attacker, int level, List<String> abilities) {
        if (abilities == null) return;
        for (String key : abilities) {
            switch (key) {
                case "passive_precision":
                    applyPrecision(event, attacker, level);
                    break;
                case "passive_execution":
                    applyExecutioner(event, level);
                    break;
                case "passive_guardbreaker":
                    applyGuardbreaker(event, attacker, level);
                    break;
                case "passive_momentum":
                    attacker.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 50, 0, false, false));
                    attacker.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 50, 0, false, false));
                    break;
                case "passive_vampiric":
                    applyVampiric(event, attacker, level);
                    break;
                default:
                    break;
            }
        }
    }

    private void applyOnDamagedPassives(EntityDamageByEntityEvent event, Player defender, int level, List<String> abilities) {
        if (abilities == null) return;
        for (String key : abilities) {
            switch (key) {
                case "passive_warding":
                    applyWarding(event, level);
                    break;
                case "passive_thorns":
                    applyThorns(event, defender, level);
                    break;
                case "passive_bulwark":
                    defender.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 60, 0, false, false));
                    break;
                default:
                    break;
            }
        }
    }

    private void applyPrecision(EntityDamageByEntityEvent event, Player attacker, int level) {
        double chance = Math.min(0.20, 0.05 + (level * 0.003));
        if (random.nextDouble() <= chance) {
            double multiplier = Math.min(1.5, 1.25 + (level * 0.01));
            event.setDamage(event.getDamage() * multiplier);
            attacker.getWorld().spawnParticle(Particle.CRIT, attacker.getLocation().add(0, 1.0, 0), 12, 0.3, 0.3, 0.3, 0.2);
            attacker.playSound(attacker.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 1.2f);
        }
    }

    private void applyExecutioner(EntityDamageByEntityEvent event, int level) {
        if (!(event.getEntity() instanceof LivingEntity target)) return;
        double maxHealth = target.getAttribute(Attribute.MAX_HEALTH) != null
                ? target.getAttribute(Attribute.MAX_HEALTH).getValue()
                : target.getMaxHealth();
        if (target.getHealth() <= maxHealth * 0.35) {
            double bonus = Math.min(0.4, 0.15 + (level * 0.01));
            event.setDamage(event.getDamage() * (1.0 + bonus));
        }
    }

    private void applyGuardbreaker(EntityDamageByEntityEvent event, Player attacker, int level) {
        if (!(event.getEntity() instanceof Player target)) return;
        if (!target.isBlocking()) return;
        double chance = Math.min(0.30, 0.10 + (level * 0.005));
        if (random.nextDouble() <= chance) {
            target.setCooldown(Material.SHIELD, 60 + (level * 2));
            target.playSound(target.getLocation(), Sound.ITEM_SHIELD_BREAK, 1.0f, 1.0f);
            attacker.sendMessage(ChatColor.GOLD + "Guard broken!");
        }
    }

    private void applyVampiric(EntityDamageByEntityEvent event, Player attacker, int level) {
        double healRatio = Math.min(0.15, 0.02 + (level * 0.002));
        double heal = event.getDamage() * healRatio;
        double maxHealth = attacker.getAttribute(Attribute.MAX_HEALTH) != null
                ? attacker.getAttribute(Attribute.MAX_HEALTH).getValue()
                : attacker.getMaxHealth();
        attacker.setHealth(Math.min(maxHealth, attacker.getHealth() + heal));
    }

    private void applyWarding(EntityDamageByEntityEvent event, int level) {
        double reduction = Math.min(0.20, 0.04 + (level * 0.003));
        event.setDamage(event.getDamage() * (1.0 - reduction));
    }

    private void applyThorns(EntityDamageByEntityEvent event, Player defender, int level) {
        if (!(event.getDamager() instanceof LivingEntity attacker)) return;
        double ratio = Math.min(0.15, 0.05 + (level * 0.003));
        attacker.damage(event.getDamage() * ratio, defender);
    }

    private Player resolveAttacker(Entity damager) {
        if (damager instanceof Player player) return player;
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) return player;
        return null;
    }

    private void maybeGrow(Player player, PlayerData data) {
        int target = computeGrowthTarget(data);
        int current = data.getIsekaiItemLevel();
        if (current >= target) return;

        long now = System.currentTimeMillis();
        long last = lastGrowthAt.getOrDefault(player.getUniqueId(), 0L);
        long cooldownMs = plugin.getConfig().getLong("isekai.growth.gain-cooldown-ms", 60000L);
        if (now - last < cooldownMs) return;
        lastGrowthAt.put(player.getUniqueId(), now);

        int newLevel = current + 1;
        data.setIsekaiItemLevel(newLevel);
        syncItemLore(player, data);
        player.sendMessage(ChatColor.LIGHT_PURPLE + "Your relic grows stronger. Growth Level " + newLevel + ".");
    }

    private int computeGrowthTarget(PlayerData data) {
        int totalLevels = 0;
        for (int level : data.getAllSkillLevels().values()) {
            totalLevels += level;
        }
        int perTier = Math.max(1, plugin.getConfig().getInt("isekai.growth.skill-levels-per-tier", 250));
        int maxLevel = Math.max(1, plugin.getConfig().getInt("isekai.growth.max-level", 15));
        int target = 1 + (totalLevels / perTier);
        return Math.min(maxLevel, target);
    }

    private void syncItemLore(Player player, PlayerData data) {
        if (data == null || !data.hasIsekaiItem()) return;
        ItemStack item = findOwnedItem(player, data.getIsekaiItemId());
        if (item == null) return;

        IsekaiItemType type = IsekaiItemType.fromKey(data.getIsekaiItemType());
        if (type == null) return;
        List<String> displayAbilities = buildAbilityDisplay(data.getIsekaiAbilities());
        itemManager.updateItem(item, buildDisplayName(player, type), data.getIsekaiItemLevel(), displayAbilities);
    }

    private void restoreIfMissing(Player player, PlayerData data) {
        if (findOwnedItem(player, data.getIsekaiItemId()) != null) return;

        long lastSeen = data.getIsekaiLastSeenAt();
        long days = plugin.getConfig().getLong("isekai.offline_return_days", 14L);
        long requiredMs = days * 24L * 60L * 60L * 1000L;
        if (lastSeen <= 0L) return;
        if (System.currentTimeMillis() - lastSeen < requiredMs) return;

        IsekaiItemType type = IsekaiItemType.fromKey(data.getIsekaiItemType());
        if (type == null) return;

        data.setIsekaiItemLevel(1);
        ItemStack restored = itemManager.createItem(player.getUniqueId(), data.getIsekaiItemId(), type.getMaterial(),
                buildDisplayName(player, type), 1, buildAbilityDisplay(data.getIsekaiAbilities()));
        giveItem(player, restored);
        player.sendMessage(ChatColor.YELLOW + "Your relic returns at base power after long absence.");
    }

    private ItemStack findOwnedItem(Player player, String itemId) {
        if (player == null || itemId == null) return null;
        PlayerInventory inv = player.getInventory();
        for (ItemStack item : inv.getContents()) {
            if (isOwnedIsekaiItem(player, item) && itemId.equals(itemManager.getItemId(item))) {
                return item;
            }
        }
        for (ItemStack item : player.getEnderChest().getContents()) {
            if (isOwnedIsekaiItem(player, item) && itemId.equals(itemManager.getItemId(item))) {
                return item;
            }
        }
        return null;
    }

    private boolean isOwnedIsekaiItem(Player player, ItemStack item) {
        if (player == null || item == null) return false;
        if (!itemManager.isIsekaiItem(item)) return false;
        return itemManager.isOwner(item, player.getUniqueId());
    }

    private String buildDisplayName(Player player, IsekaiItemType type) {
        return player.getName() + "'s " + type.getDisplayName();
    }

    private List<String> buildAbilityDisplay(List<String> abilityKeys) {
        List<String> display = new ArrayList<>();
        if (abilityKeys == null) return display;
        for (String key : abilityKeys) {
            AbilityDef def = abilityDefs.get(key);
            if (def == null) continue;
            String prefix = def.type == AbilityType.ACTIVE ? "Active" : "Passive";
            display.add(prefix + ": " + def.name);
        }
        return display;
    }

    private void giveItem(Player player, ItemStack item) {
        if (player == null || item == null) return;
        Map<Integer, ItemStack> remaining = player.getInventory().addItem(item);
        if (!remaining.isEmpty()) {
            for (ItemStack rem : remaining.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), rem);
            }
        }
    }

    private EligibilityStatus checkEligibility(Player player) {
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
        int maxTotal = getMaxTotalSkillLevels();
        int currentTotal = getCurrentTotalSkillLevels(data);
        boolean requireCurrentMaxed = plugin.getConfig().getBoolean("isekai.require_current_maxed", true);
        int requiredPurchased = plugin.getConfig().getInt("isekai.required_total_skill_points", -1);
        if (requiredPurchased <= 0) {
            requiredPurchased = maxTotal * 2;
        }
        boolean debateEnabled = plugin.getConfig().getBoolean("isekai.debate_progress_enabled", true)
                && plugin.getDebateSystem() != null
                && plugin.getDebateSystem().isEnabled();
        int debateEquivalent = debateEnabled ? plugin.getDebateSystem().getReincarnationEquivalent(data) : 0;
        int effectivePurchased = debateEnabled ? Math.max(data.getTotalSkillsPurchased(), debateEquivalent) : data.getTotalSkillsPurchased();
        boolean meetsMax = !requireCurrentMaxed || currentTotal >= maxTotal;
        boolean meetsPurchased = effectivePurchased >= requiredPurchased;
        boolean eligible = meetsMax && meetsPurchased;
        return new EligibilityStatus(eligible, currentTotal, maxTotal, data.getTotalSkillsPurchased(), requiredPurchased,
                requireCurrentMaxed, debateEnabled, debateEquivalent, effectivePurchased);
    }

    private int getMaxTotalSkillLevels() {
        int total = 0;
        for (SkillTreeSystem.SkillNode node : plugin.getSkillTreeSystem().getAllSkillNodes().values()) {
            total += node.getMaxLevel();
        }
        return total;
    }

    private int getCurrentTotalSkillLevels(PlayerData data) {
        int total = 0;
        for (int level : data.getAllSkillLevels().values()) {
            total += level;
        }
        return total;
    }

    private static class AbilityDef {
        private final String key;
        private final String name;
        private final AbilityType type;
        private final long cooldownMillis;

        AbilityDef(String key, String name, AbilityType type, long cooldownMillis) {
            this.key = key;
            this.name = name;
            this.type = type;
            this.cooldownMillis = cooldownMillis;
        }
    }

    private enum AbilityType {
        ACTIVE,
        PASSIVE
    }

    private static class EligibilityStatus {
        private final boolean eligible;
        private final int currentTotal;
        private final int maxTotal;
        private final int totalPurchased;
        private final int requiredPurchased;
        private final boolean requireCurrentMaxed;
        private final boolean debateEnabled;
        private final int debateEquivalent;
        private final int effectivePurchased;

        EligibilityStatus(boolean eligible, int currentTotal, int maxTotal, int totalPurchased, int requiredPurchased,
                          boolean requireCurrentMaxed, boolean debateEnabled, int debateEquivalent, int effectivePurchased) {
            this.eligible = eligible;
            this.currentTotal = currentTotal;
            this.maxTotal = maxTotal;
            this.totalPurchased = totalPurchased;
            this.requiredPurchased = requiredPurchased;
            this.requireCurrentMaxed = requireCurrentMaxed;
            this.debateEnabled = debateEnabled;
            this.debateEquivalent = debateEquivalent;
            this.effectivePurchased = effectivePurchased;
        }
    }
}
