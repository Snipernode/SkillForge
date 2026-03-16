package com.skilltree.plugin.listeners;

import com.skilltree.plugin.SkillForgePlugin;
import com.skilltree.plugin.data.PlayerData;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PlayerEventListener implements Listener {

    private final SkillForgePlugin plugin;

    public PlayerEventListener(SkillForgePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onElytraUse(org.bukkit.event.entity.EntityToggleGlideEvent event) {
        if (event.getEntity() instanceof Player player) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Elytras are no longer functional in this realm.");
        }
    }

    @EventHandler
    public void onSleep(org.bukkit.event.player.PlayerBedLeaveEvent event) {
        Player player = event.getPlayer();
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
        if (data.isNekrotic()) {
            data.setNekrosisDay(data.getNekrosisDay() + 1);
            player.sendMessage(ChatColor.DARK_RED + "The Nekrosis spreads... Day " + data.getNekrosisDay());
        }
    }

    @EventHandler
    public void onDeath(org.bukkit.event.entity.PlayerDeathEvent event) {
        if (plugin.getConfig().getBoolean("gamemodes.instant-respawn", true)) {
            return;
        }
        event.getEntity().sendMessage(ChatColor.RED + "You died. Respawn when ready.");
    }

    @EventHandler
    public void onWeaponUse(EntityDamageByEntityEvent event) {
        if (event.isCancelled()) return;
        Player player = null;
        ItemStack item = null;
        boolean projectile = false;

        if (event.getDamager() instanceof Player p) {
            player = p;
            item = player.getInventory().getItemInMainHand();
        } else if (event.getDamager() instanceof org.bukkit.entity.Projectile proj) {
            if (proj.getShooter() instanceof Player p) {
                player = p;
                item = player.getInventory().getItemInMainHand();
                projectile = true;
            }
        }

        if (player == null) return;
        if (item == null || item.getType() == Material.AIR) return;

        String skillId = getMasterySkillId(item.getType(), projectile);
        if (skillId == null) return;

        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);

        // Award mastery points on use; grant a skill point every N hits
        int points = data.getMasteryPoints(skillId) + 1;
        int pointsPerSkillPoint = 50;
        if (points >= pointsPerSkillPoint) {
            points -= pointsPerSkillPoint;
            int gained = plugin.getEvershardSystem().getSkillPointGain(data, 1);
            data.addSkillPoints(gained);
            player.sendMessage(ChatColor.GOLD + "Your mastery grows! You earned " + gained + " Skill Point" + (gained == 1 ? "" : "s") + ".");
        }
        data.setMasteryPoints(skillId, points);

        int level = data.getSkillLevel(skillId);
        if (level > 0) {
            double bonus = getMasteryDamageBonus(skillId, level);
            if (bonus > 0) {
                event.setDamage(event.getDamage() * (1.0 + bonus));
            }

            if (skillId.equals("mastery_axes")) {
                maybeDisableShield(event.getEntity(), level);
            }
        }

        // Flail self-damage logic
        if (skillId.equals("mastery_flail")) {
            int levelFlail = data.getSkillLevel("mastery_flail");
            if (levelFlail < 20) { // Low level flail users hurt themselves
                double chance = 0.2 - (levelFlail * 0.01); // 20% down to 0% at level 20
                if (Math.random() < chance) {
                    player.damage(2.0); // 1 heart of damage
                    player.sendMessage(ChatColor.RED + "You bashed your own face with flail!");
                }
            }
        }
    }

    private String getMasterySkillId(Material mat, boolean projectile) {
        String name = mat.name();
        if (projectile) {
            if (mat == Material.BOW || mat == Material.CROSSBOW) return "mastery_bows";
            return null;
        }
        if (name.contains("SWORD")) return "mastery_swords";
        if (name.contains("AXE")) return "mastery_axes";
        if (mat == Material.BOW || mat == Material.CROSSBOW) return "mastery_bows";
        if (name.contains("FIREARM")) return "mastery_firearms"; // Placeholder for firearm items
        if (name.contains("FLAIL")) return "mastery_flail";
        return null;
    }

    private double getMasteryDamageBonus(String skillId, int level) {
        double perLevel;
        if ("mastery_swords".equals(skillId)) perLevel = 0.005; // 0.5% per level
        else if ("mastery_axes".equals(skillId)) perLevel = 0.006; // 0.6% per level
        else if ("mastery_bows".equals(skillId)) perLevel = 0.004; // 0.4% per level
        else if ("mastery_firearms".equals(skillId)) perLevel = 0.005; // 0.5% per level
        else if ("mastery_flail".equals(skillId)) perLevel = 0.006; // 0.6% per level
        else perLevel = 0.0;
        double bonus = level * perLevel;
        return Math.min(bonus, 0.75); // hard cap at +75%
    }

    private void maybeDisableShield(Entity target, int level) {
        if (!(target instanceof Player tp)) return;
        ItemStack off = tp.getInventory().getItemInOffHand();
        if (off == null || off.getType() != Material.SHIELD) return;
        // Chance scales with mastery (max ~60%)
        double chance = Math.min(0.1 + (level * 0.005), 0.6);
        if (Math.random() < chance) {
            tp.setCooldown(Material.SHIELD, 60); // 3 seconds
            tp.sendMessage(ChatColor.RED + "Your shield is staggered!");
            tp.getWorld().playSound(tp.getLocation(), Sound.ITEM_SHIELD_BREAK, 1.0f, 1.0f);
        }
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
        
        // Permanent-death modes: if already dead, do not allow normal play.
        if ((data.getGamemode() == PlayerData.Gamemode.HARDCORE || data.getGamemode() == PlayerData.Gamemode.THE_END) && data.isHardcoreDead()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) return;
                if (data.getGamemode() == PlayerData.Gamemode.THE_END) {
                    player.kickPlayer(ChatColor.DARK_RED + "THE END mode death is permanent.");
                } else {
                    player.kickPlayer(ChatColor.DARK_GRAY + "YOU DIED. " + ChatColor.GRAY + "Revive via Payment ($5) or Death Realm Trials.");
                }
            }, 5L);
            return;
        }

        // Gamemode GUI is now command-driven only (/gamemodes).
        if (data.hasSelectedGamemode() && data.getKingdom() != null) {
            applyInnateSkillEffects(player);
            giveSkillItem(player);
        }
        data.setLastLoginTime(System.currentTimeMillis());
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        
        if (plugin.getStaminaSystem() != null) {
            plugin.getStaminaSystem().cleanupPlayer(player.getUniqueId());
        }
        
        // Save playtime for this session
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        long last = data.getLastLoginTime();
        if (last > 0) {
            long session = System.currentTimeMillis() - last;
            if (session > 0) data.addPlaytime(session);
            data.setLastLoginTime(0);
        }

        // Unload player (which also saves)
        plugin.getPlayerDataManager().unloadPlayer(player.getUniqueId());
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        // First, handle interactive invite prompts (chat-based)
        if (plugin.getGuildSystem().hasInvitePrompt(player.getUniqueId())) {
            event.setCancelled(true);
            String msg = event.getMessage();
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                plugin.getGuildSystem().handleInviteResponse(player, msg);
            });
            return;
        }

        String tag = plugin.getGuildSystem().getGuildTag(player.getUniqueId());
        if (tag != null && !tag.isEmpty()) {
            String fmt = event.getFormat();
            event.setFormat(tag + fmt);
        }
    }
    
    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        Entity killer = event.getEntity().getKiller();
        if (killer instanceof Player) {
            Player playerKiller = (Player) killer;
            plugin.getEvershardSystem().grantBonusESFromMobKill(playerKiller, event.getEntity());
        }
    }

    @EventHandler
    public void onPlayerExpChange(PlayerExpChangeEvent event) {
        int xpGained = event.getAmount();
        if (xpGained > 0) {
            plugin.getEvershardSystem().grantEvershardsFromXP(event.getPlayer(), xpGained);
        }
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
        PlayerData.Gamemode gamemode = data.getGamemode();
        
        if (!plugin.getConfig().getBoolean("gamemodes.enabled", true)) {
            forceInstantRespawn(player);
            return;
        }
        
        if (gamemode == PlayerData.Gamemode.CASUAL) {
            if (plugin.getConfig().getBoolean("gamemodes.casual-keep-inventory", true)) {
                event.setKeepInventory(true);
                event.getDrops().clear();
                event.setKeepLevel(true);
                event.setDroppedExp(0);
            }
            forceInstantRespawn(player);
        } else if (gamemode == PlayerData.Gamemode.ROGUELITE) {
            event.setKeepInventory(true);
            event.setKeepLevel(true);
            
            int savedES = data.getEvershards();
            int savedSP = data.getSkillPoints();
            
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    player.sendMessage("");
                    player.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "ROGUELITE DEATH");
                    player.sendMessage(ChatColor.BLUE + "Your §k ERROR §r and XP were lost.");
                    player.sendMessage(ChatColor.GREEN + "Your " + savedES + " Evershards and " + savedSP + " Skill Points remain safe...for now");
                    player.sendMessage(ChatColor.GREEN + "Your skill levels are preserved.");
                    player.sendMessage("");
                }
            }, 5L);
            forceInstantRespawn(player);
        } else if (gamemode == PlayerData.Gamemode.ROGUELIKE) {
            // ROGUELIKE: Total Wipe
            event.setKeepInventory(false); // Drops items (though they will be wiped from data anyway)
            event.setKeepLevel(false);
            
            // Logic to wipe data is handled here, but applied effectively on next join/logout
            // We wipe skills, evershards, etc. immediately to prevent duplication glitches if they somehow respawn
            data.clearAllSkills(); // Directly clear the map in PlayerData
            data.setEvershards(25);
            data.setSkillPoints(25);
            data.setKingdom("Nomad"); // Reset Kingdom
            data.setGamemode(PlayerData.Gamemode.NONE); // Reset Gamemode to force selection
            
            // Force save to ensure wipe persists even if server crashes
            plugin.getPlayerDataManager().savePlayerData(player.getUniqueId());

            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    player.sendMessage("");
                    player.sendMessage(ChatColor.DARK_RED + "" + ChatColor.BOLD + "ROGUELIKE DEATH");
                    player.sendMessage(ChatColor.GRAY + "Everything has been E R A S E D.");
                    player.sendMessage(ChatColor.GRAY + "Skill Tree G O N E.");
                    player.sendMessage(ChatColor.GRAY + "Kingdom B U R N E D.");
                    player.sendMessage(ChatColor.GRAY + "Y O U H A V E L O S T");
                    player.sendMessage(ChatColor.YELLOW + "You must START A NEW or perish trying.");
                    player.sendMessage("And Yet, you stand...");
                    player.sendMessage("This Shall be Amusing...:)");
                }
            }, 5L);
            forceInstantRespawn(player);
            
        } else if (gamemode == PlayerData.Gamemode.HARDCORE) {
            // HARDCORE: Permanent Death
            event.setKeepInventory(false);
            event.setKeepLevel(false);
            
            // Mark player as dead in data
            data.setHardcoreDead(true);
            plugin.getPlayerDataManager().savePlayerData(player.getUniqueId());

            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    player.kickPlayer(ChatColor.DARK_GRAY + "YOU DIED. " + ChatColor.GRAY + "Revive via Payment ($5) or Death Realm Trials." + ChatColor.DARK_GRAY + "FAILURE THE BANE OF MAN , THE ENTERTAINMENT OF THE DEAD." + ChatColor.BLACK + "C O N N E C T I O N R E V O K E D ");                }
            }, 5L);
        } else if (gamemode == PlayerData.Gamemode.PROTAGONIST) {
            event.setKeepInventory(false);
            event.setKeepLevel(false);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) return;
                player.sendMessage(ChatColor.AQUA + "" + ChatColor.BOLD + "PROTAGONIST DEATH");
                player.sendMessage(ChatColor.GRAY + "I Guess This is how our story ends. a tragic fate i suppose, at least for you. but fear not, they says the afterlife is weightless.");
            }, 5L);
            forceInstantRespawn(player);
        } else if (gamemode == PlayerData.Gamemode.THE_END) {
            event.setKeepInventory(false);
            event.setKeepLevel(false);

            data.setHardcoreDead(true);
            plugin.getPlayerDataManager().savePlayerData(player.getUniqueId());

            String banReason = ChatColor.DARK_RED + "§b THE END. D O N T C O M E B A C K.";
            try {
                Bukkit.getBanList(BanList.Type.NAME).addBan(player.getName(), ChatColor.stripColor(banReason), null, "SkillForge");
            } catch (Throwable ignored) {
            }
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) return;
                player.kickPlayer(banReason);
            }, 2L);
        } else {
            forceInstantRespawn(player);
        }
    }

    private void forceInstantRespawn(Player player) {
        if (!plugin.getConfig().getBoolean("gamemodes.instant-respawn", true)) return;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || !player.isDead()) return;
            try {
                player.spigot().respawn();
            } catch (Throwable ignored) {
            }
        }, 1L);
    }
    
    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        // If player is in Roguelike and data was wiped, they might technically respawn before GUI opens.
        // The onPlayerJoin check handles GUI opening.
        applyInnateSkillEffects(event.getPlayer());
        giveSkillItem(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onReadOnlyMenuClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        if (!isProtectedMenuTitle(title)) return;

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        player.setItemOnCursor(null);

        // Defensive top-slot restore (prevents occasional creative pickup ghosting).
        try {
            Inventory top = event.getView().getTopInventory();
            int raw = event.getRawSlot();
            if (raw >= 0 && raw < top.getSize()) {
                top.setItem(raw, event.getCurrentItem());
            }
        } catch (Throwable ignored) {
        }

        // Deny hotbar-swap / creative pickup edge-cases.
        if (event.getClick() == ClickType.NUMBER_KEY) {
            event.setCancelled(true);
        }
        player.updateInventory();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onReadOnlyMenuDrag(InventoryDragEvent event) {
        String title = event.getView().getTitle();
        if (!isProtectedMenuTitle(title)) return;
        event.setCancelled(true);
        if (event.getWhoClicked() instanceof Player player) {
            player.setItemOnCursor(null);
            player.updateInventory();
        }
    }

    private boolean isProtectedMenuTitle(String title) {
        if (title == null || title.isBlank()) return false;
        String plain = ChatColor.stripColor(title);
        if (plain == null) plain = title;
        String t = plain.toLowerCase(Locale.ROOT);
        if (t.contains("skillforge rpg |")) return true;
        // Legacy/fallback titles
        return t.contains("player unlocks")
                || t.contains("hall of heroes")
                || t.contains("station selection")
                || t.contains("station admin")
                || t.contains("stations list");
    }
    
    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Material blockType = event.getBlock().getType();
        boolean isCampfire = blockType == Material.CAMPFIRE || blockType == Material.SOUL_CAMPFIRE || blockType == Material.FIRE;
        
        if (!isCampfire) {
            return;
        }
        
        if (event.getBlock().getRelative(0, -1, 0).getType() != Material.NETHERRACK) {
            return;
        }
        
        Player placer = event.getPlayer();
        for (Player player : placer.getWorld().getPlayers()) {
            if (player.getLocation().distance(event.getBlock().getLocation()) <= 8.0) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.INSTANT_HEALTH, 1, 10, true, false));
                player.sendMessage(ChatColor.GOLD + "You feel warmth from campfire...");
            }
        }
    }

    /**
     * Applies innate skill effects based on config.yml definitions.
     * CHANGED: Now public so GUI can call it.
     */
    public void applyInnateSkillEffects(Player player) {
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        String skillId = data.getInnateSkillId();
        
        // 1. Clear all known innate effects first to prevent stacking
        ConfigurationSection skillsSection = plugin.getConfig().getConfigurationSection("skills.items");
        if (skillsSection != null) {
            for (String key : skillsSection.getKeys(false)) {
                String effectName = skillsSection.getString(key + ".effect");
                if (effectName != null) {
                    try {
                        // Use getByKey with NamespacedKey instead of deprecated getByName
                        PotionEffectType type = PotionEffectType.getByKey(NamespacedKey.minecraft(effectName.toLowerCase()));
                        if (type != null) {
                            player.removePotionEffect(type);
                        }
                    } catch (Exception e) {
                        // Invalid effect name in config, ignore
                    }
                }
            }
        }

        // 2. If no skill, stop here
        if (skillId == null || skillId.equalsIgnoreCase("none")) {
            return;
        }

        //3. Get specific skill config section
        ConfigurationSection skillSection = plugin.getConfig().getConfigurationSection("skills.items." + skillId);
        if (skillSection == null) {
            plugin.getLogger().warning("Unknown innate skill ID in config: " + skillId);
            return;
        }

        //4. Apply Potion Effect
        String effectName = skillSection.getString("effect");
        if (effectName != null) {
            try {
                PotionEffectType type = PotionEffectType.getByKey(NamespacedKey.minecraft(effectName.toLowerCase()));
                if (type != null) {
                    int level = data.getInnateSkillLevel();
                    int amplifier = Math.max(0, level - 1); // Level 1 = 0, Level 2 = 1
                    
                    player.addPotionEffect(new PotionEffect(type, Integer.MAX_VALUE, amplifier, false, false));
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Invalid effect type in config for " + skillId + ": " + effectName);
            }
        }
    }
    
    /**
     * Helper method to create ItemStack from config.
     * CHANGED: Now public so GUI can call it.
     */
    public void giveSkillItem(Player player) {
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        String skillId = data.getInnateSkillId();
        
        if (skillId == null || skillId.equalsIgnoreCase("none")) {
            return;
        }
        
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("skills.items." + skillId);
        if (section == null) return;

        String materialName = section.getString("material", "STONE");
        Material material;
        try {
            material = Material.valueOf(materialName.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid material in config for " + skillId + ": " + materialName);
            return;
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        
        if (meta != null) {
            // Set Name
            String displayName = section.getString("name", "Skill Item");
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', displayName));
            
            // Set Lore (replace {level})
            List<String> lore = new ArrayList<>();
            if (section.contains("lore")) {
                for (String line : section.getStringList("lore")) {
                    lore.add(ChatColor.translateAlternateColorCodes('&', line.replace("{level}", String.valueOf(data.getInnateSkillLevel()))));
                }
            }
            meta.setLore(lore);
            
            item.setItemMeta(meta);
        }
        
        // Give item to slot 0 (first hotbar slot)
        player.getInventory().setItem(0, item);
    }
}
