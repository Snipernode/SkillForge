package com.skilltree.plugin.systems;

import com.skilltree.plugin.SkillForgePlugin;
import com.skilltree.plugin.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class DebateSystem {

    private final SkillForgePlugin plugin;
    private final Random random = new Random();
    private final Map<UUID, DebateProjection> projections = new HashMap<>();

    public DebateSystem(SkillForgePlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("debate.enabled", true);
    }

    public void showStatus(Player player) {
        if (player == null) return;
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
        long cooldown = getRemainingCooldownMillis(data);

        player.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "Debate Hall");
        player.sendMessage(ChatColor.GRAY + "Rating: " + ChatColor.AQUA + data.getDebateRating());
        player.sendMessage(ChatColor.GRAY + "Wins/Losses: " + ChatColor.GREEN + data.getDebateWins()
                + ChatColor.GRAY + "/" + ChatColor.RED + data.getDebateLosses());
        player.sendMessage(ChatColor.GRAY + "Current Streak: " + ChatColor.YELLOW + data.getDebateCurrentStreak()
                + ChatColor.GRAY + " | Best: " + ChatColor.GOLD + data.getDebateBestStreak());
        player.sendMessage(ChatColor.GRAY + "Reincarnation Equivalent: " + ChatColor.LIGHT_PURPLE + getReincarnationEquivalent(data));
        if (cooldown > 0L) {
            player.sendMessage(ChatColor.GRAY + "Ready again in: " + ChatColor.YELLOW + formatMillis(cooldown));
        } else {
            player.sendMessage(ChatColor.GREEN + "You are ready to debate.");
        }
        player.sendMessage(ChatColor.GRAY + "Schools: " + ChatColor.YELLOW + "logos, pathos, ethos, sophistry");
        player.sendMessage(ChatColor.GRAY + "Use " + ChatColor.YELLOW + "/debate start <school>" + ChatColor.GRAY + " to begin.");
    }

    public void startDebate(Player player, String rawSchool) {
        if (player == null) return;
        if (!isEnabled()) {
            player.sendMessage(ChatColor.RED + "Debate is disabled right now.");
            return;
        }

        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
        long cooldown = getRemainingCooldownMillis(data);
        if (cooldown > 0L) {
            player.sendMessage(ChatColor.RED + "Your thoughts still need to settle. Try again in " + ChatColor.YELLOW + formatMillis(cooldown) + ChatColor.RED + ".");
            return;
        }

        DebateSchool school = DebateSchool.fromKey(rawSchool);
        if (school == null) {
            player.sendMessage(ChatColor.YELLOW + "Choose a school: logos, pathos, ethos, sophistry");
            return;
        }

        DebatePersona persona = selectPersona(school);
        spawnProjection(player, school, persona);

        int schoolAffinity = computeAffinity(data, school);
        int totalLevels = getCurrentTotalSkillLevels(data);
        int currentStreak = data.getDebateCurrentStreak();
        int resonance = 120 + schoolAffinity + (totalLevels / 7) + (currentStreak * 18) + random.nextInt(120);
        int opposition = 170 + (data.getDebateRating() / 2) + (data.getDebateWins() * 18) + random.nextInt(160);
        int margin = resonance - opposition;
        boolean success = margin >= 0 || (schoolAffinity > opposition / 2 && random.nextDouble() < 0.24);

        int ratingGain;
        int shardReward;
        int skillPointReward = 0;

        data.setDebateLastAt(System.currentTimeMillis());

        player.sendTitle(ChatColor.GOLD + school.display, ChatColor.GRAY + school.opening, 8, 36, 12);
        player.sendMessage(ChatColor.DARK_GRAY + "--------------------------------");
        player.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "Debate School: " + ChatColor.YELLOW + school.display);
        player.sendMessage(ChatColor.GRAY + persona.displayName + ChatColor.DARK_GRAY + " (" + persona.subtitle + ")");
        player.sendMessage(ChatColor.GRAY + school.flavor);
        player.sendMessage(ChatColor.GRAY + "Your presence: " + ChatColor.AQUA + resonance
                + ChatColor.DARK_GRAY + " vs " + ChatColor.GRAY + "Opposition: " + ChatColor.RED + opposition);

        if (success) {
            ratingGain = clamp(120 + (schoolAffinity / 5) + Math.max(20, margin / 3), 120, 420);
            shardReward = 40 + (ratingGain / 6);
            data.incrementDebateWins();
            data.incrementDebateCurrentStreak();
            data.addDebateRating(ratingGain);
            data.addEvershards(shardReward);

            if (data.getDebateWins() % Math.max(1, plugin.getConfig().getInt("debate.skillpoint_every_wins", 5)) == 0) {
                skillPointReward = Math.max(1, plugin.getConfig().getInt("debate.skillpoint_reward", 1));
                data.addSkillPoints(skillPointReward);
            }

            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.08f);
            player.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "Debate Won");
            player.sendMessage(ChatColor.GRAY + school.victoryLine);
            player.sendMessage(ChatColor.GRAY + "Debate rating +" + ChatColor.LIGHT_PURPLE + ratingGain
                    + ChatColor.GRAY + " | Evershards +" + ChatColor.GOLD + shardReward);
            if (skillPointReward > 0) {
                player.sendMessage(ChatColor.GREEN + "Milestone reward: +" + skillPointReward + " Skill Point");
            }
        } else {
            ratingGain = clamp(36 + (schoolAffinity / 10) + random.nextInt(32), 36, 120);
            shardReward = 12 + (ratingGain / 8);
            data.incrementDebateLosses();
            data.resetDebateCurrentStreak();
            data.addDebateRating(ratingGain);
            data.addEvershards(shardReward);

            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.7f);
            player.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "Debate Lost");
            player.sendMessage(ChatColor.GRAY + school.defeatLine);
            player.sendMessage(ChatColor.GRAY + "You still gleaned insight: +" + ChatColor.LIGHT_PURPLE + ratingGain
                    + ChatColor.GRAY + " rating, +" + ChatColor.GOLD + shardReward + ChatColor.GRAY + " Evershards.");
        }

        player.sendMessage(ChatColor.GRAY + "Reincarnation equivalent now: " + ChatColor.LIGHT_PURPLE + getReincarnationEquivalent(data));
        player.sendMessage(ChatColor.DARK_GRAY + "--------------------------------");
    }

    public int getReincarnationEquivalent(PlayerData data) {
        if (data == null) return 0;
        return Math.max(0, data.getDebateRating() + (data.getDebateWins() * 80) + (data.getDebateBestStreak() * 120));
    }

    public long getRemainingCooldownMillis(PlayerData data) {
        if (data == null) return 0L;
        long cooldownMs = Math.max(0L, plugin.getConfig().getLong("debate.cooldown_seconds", 45L) * 1000L);
        long remaining = (data.getDebateLastAt() + cooldownMs) - System.currentTimeMillis();
        return Math.max(0L, remaining);
    }

    private int computeAffinity(PlayerData data, DebateSchool school) {
        int combat = getCategoryTotal(data, "combat");
        int mining = getCategoryTotal(data, "mining");
        int agility = getCategoryTotal(data, "agility");
        int intellect = getCategoryTotal(data, "intellect");
        int farming = getCategoryTotal(data, "farming");
        int fishing = getCategoryTotal(data, "fishing");
        int magic = getCategoryTotal(data, "magic");
        int mastery = getCategoryTotal(data, "mastery");

        return switch (school) {
            case LOGOS -> (intellect * 2) + magic + (mastery / 2);
            case PATHOS -> (combat * 2) + agility + (mastery / 2);
            case ETHOS -> (farming * 2) + fishing + (combat / 2);
            case SOPHISTRY -> (mastery * 2) + intellect + magic + (mining / 2);
        };
    }

    private int getCategoryTotal(PlayerData data, String category) {
        if (data == null) return 0;
        int total = 0;
        List<SkillTreeSystem.SkillNode> nodes = plugin.getSkillTreeSystem().getSkillsByCategory(category);
        for (SkillTreeSystem.SkillNode node : nodes) {
            total += data.getSkillLevel(node.getId());
        }
        return total;
    }

    private int getCurrentTotalSkillLevels(PlayerData data) {
        int total = 0;
        if (data == null) return total;
        for (int level : data.getAllSkillLevels().values()) {
            total += level;
        }
        return total;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private String formatMillis(long millis) {
        long totalSeconds = Math.max(0L, millis / 1000L);
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        if (minutes > 0L) return minutes + "m " + seconds + "s";
        return seconds + "s";
    }

    public List<String> schools() {
        List<String> out = new ArrayList<>();
        for (DebateSchool school : DebateSchool.values()) {
            out.add(school.key);
        }
        return out;
    }

    private DebatePersona selectPersona(DebateSchool school) {
        List<DebatePersona> pool = new ArrayList<>();
        switch (school) {
            case LOGOS -> {
                pool.add(new DebatePersona("Archivist Sel", "The Counterproof", school, Color.fromRGB(69, 121, 191), Material.WRITABLE_BOOK, "jeb_"));
                pool.add(new DebatePersona("Magister Rune", "Pattern Breaker", school, Color.fromRGB(92, 133, 176), Material.KNOWLEDGE_BOOK, "Dinnerbone"));
            }
            case PATHOS -> {
                pool.add(new DebatePersona("Vesper Kane", "Voice of Ash", school, Color.fromRGB(171, 56, 56), Material.BLAZE_POWDER, "Notch"));
                pool.add(new DebatePersona("Ember Vale", "The Crowd's Pulse", school, Color.fromRGB(198, 72, 45), Material.FIRE_CHARGE, "Technoblade"));
            }
            case ETHOS -> {
                pool.add(new DebatePersona("Warden Ilya", "Keeper of Vows", school, Color.fromRGB(84, 141, 98), Material.SHIELD, "MHF_Villager"));
                pool.add(new DebatePersona("Mother Sen", "Weight of Oaths", school, Color.fromRGB(104, 156, 112), Material.TOTEM_OF_UNDYING, "MHF_Present1"));
            }
            case SOPHISTRY -> {
                pool.add(new DebatePersona("Cipher Mock", "Smile in the Knife", school, Color.fromRGB(128, 83, 181), Material.ENDER_EYE, "MHF_Question"));
                pool.add(new DebatePersona("Lacquer Voss", "The Slantwise Tongue", school, Color.fromRGB(111, 61, 163), Material.AMETHYST_SHARD, "MHF_Blaze"));
            }
        }
        return pool.get(random.nextInt(pool.size()));
    }

    private void spawnProjection(Player player, DebateSchool school, DebatePersona persona) {
        if (player == null || school == null || persona == null) return;
        clearProjection(player.getUniqueId());

        Location eye = player.getLocation().clone();
        Vector look = eye.getDirection().clone().setY(0).normalize();
        if (look.lengthSquared() <= 0.01) {
            look = new Vector(0, 0, 1);
        }
        Location base = player.getLocation().clone().add(look.multiply(3.0));
        base.setPitch(0f);
        base.setYaw(base.getYaw() + 180f);

        ArmorStand stand = player.getWorld().spawn(base, ArmorStand.class, as -> {
            as.setVisible(true);
            as.setArms(true);
            as.setBasePlate(false);
            as.setGravity(false);
            as.setCollidable(false);
            as.setInvulnerable(true);
            as.setPersistent(false);
            as.setSilent(true);
            as.setRotation(base.getYaw() + 180f, 0f);
            as.setBodyPose(new EulerAngle(0, 0, 0));
            as.setLeftArmPose(new EulerAngle(Math.toRadians(350), 0, Math.toRadians(8)));
            as.setRightArmPose(new EulerAngle(Math.toRadians(320), 0, Math.toRadians(-10)));
        });

        EntityEquipment eq = stand.getEquipment();
        if (eq != null) {
            eq.setHelmet(createHead(persona.skinOwner));
            eq.setChestplate(coloredArmor(Material.LEATHER_CHESTPLATE, persona.color));
            eq.setLeggings(coloredArmor(Material.LEATHER_LEGGINGS, persona.color));
            eq.setBoots(coloredArmor(Material.LEATHER_BOOTS, persona.color));
            eq.setItemInMainHand(new ItemStack(persona.mainHand));
            eq.setItemInOffHand(offhandForSchool(school));
        }

        TextDisplay label = player.getWorld().spawn(base.clone().add(0, 2.25, 0), TextDisplay.class, td -> {
            td.setText(ChatColor.GOLD + persona.displayName + "\n" + ChatColor.GRAY + persona.subtitle);
            td.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
            td.setShadowed(true);
            td.setPersistent(false);
            td.setLineWidth(180);
        });

        projections.put(player.getUniqueId(), new DebateProjection(stand, label));

        long lifeTicks = Math.max(60L, plugin.getConfig().getLong("debate.projection_duration_ticks", 200L));
        new BukkitRunnable() {
            @Override
            public void run() {
                clearProjection(player.getUniqueId());
            }
        }.runTaskLater(plugin, lifeTicks);
    }

    private void clearProjection(UUID playerId) {
        DebateProjection projection = projections.remove(playerId);
        if (projection == null) return;
        if (projection.stand != null && projection.stand.isValid()) projection.stand.remove();
        if (projection.label != null && projection.label.isValid()) projection.label.remove();
    }

    private ItemStack offhandForSchool(DebateSchool school) {
        return switch (school) {
            case LOGOS -> new ItemStack(Material.PAPER);
            case PATHOS -> new ItemStack(Material.REDSTONE_TORCH);
            case ETHOS -> new ItemStack(Material.SHIELD);
            case SOPHISTRY -> new ItemStack(Material.FEATHER);
        };
    }

    private ItemStack coloredArmor(Material material, Color color) {
        ItemStack stack = new ItemStack(material);
        if (stack.getItemMeta() instanceof LeatherArmorMeta meta) {
            meta.setColor(color);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private ItemStack createHead(String owner) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        if (head.getItemMeta() instanceof SkullMeta meta && owner != null && !owner.isBlank()) {
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(owner));
            head.setItemMeta(meta);
        }
        return head;
    }

    public enum DebateSchool {
        LOGOS("logos", "Logos", "You marshal pure reason against the void.",
                "You break the chamber with proof, pattern, and precision.",
                "Reason alone was not enough; you return sharper."),
        PATHOS("pathos", "Pathos", "You turn emotion into force and force into momentum.",
                "The room bends to conviction and lived intensity.",
                "Your fire flickers, but it does not go out."),
        ETHOS("ethos", "Ethos", "You stand on reputation, duty, and moral weight.",
                "The hall yields to credibility earned the hard way.",
                "Your standing was tested; next time it will hold."),
        SOPHISTRY("sophistry", "Sophistry", "You twist framing, timing, and implication into a blade.",
                "The chamber loses track of where your argument began.",
                "You overplayed the angle; the hall saw the seams.");

        private final String key;
        private final String display;
        private final String opening;
        private final String victoryLine;
        private final String defeatLine;
        private final String flavor;

        DebateSchool(String key, String display, String opening, String victoryLine, String defeatLine) {
            this.key = key;
            this.display = display;
            this.opening = opening;
            this.victoryLine = victoryLine;
            this.defeatLine = defeatLine;
            this.flavor = opening;
        }

        public static DebateSchool fromKey(String key) {
            if (key == null || key.isBlank()) return null;
            String clean = key.toLowerCase(Locale.ROOT).trim();
            for (DebateSchool school : values()) {
                if (school.key.equals(clean)) return school;
            }
            return null;
        }
    }

    private static final class DebatePersona {
        private final String displayName;
        private final String subtitle;
        private final DebateSchool school;
        private final Color color;
        private final Material mainHand;
        private final String skinOwner;

        private DebatePersona(String displayName, String subtitle, DebateSchool school, Color color, Material mainHand, String skinOwner) {
            this.displayName = displayName;
            this.subtitle = subtitle;
            this.school = school;
            this.color = color;
            this.mainHand = mainHand;
            this.skinOwner = skinOwner;
        }
    }

    private static final class DebateProjection {
        private final ArmorStand stand;
        private final TextDisplay label;

        private DebateProjection(ArmorStand stand, TextDisplay label) {
            this.stand = stand;
            this.label = label;
        }
    }
}
