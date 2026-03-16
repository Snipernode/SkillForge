package com.skilltree.plugin.managers;

import com.skilltree.plugin.SkillForgePlugin;
import com.skilltree.plugin.data.PlayerData;
import com.skilltree.plugin.systems.CrateBlockSystem;
import com.skilltree.plugin.systems.CrateRewardManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class VoucherManager {
    private final SkillForgePlugin plugin;
    private final NamespacedKey voucherKey;
    private final NamespacedKey voucherTypeKey;
    private final NamespacedKey voucherValueKey;

    public VoucherManager(SkillForgePlugin plugin) {
        this.plugin = plugin;
        this.voucherKey = new NamespacedKey(plugin, "is_voucher");
        this.voucherTypeKey = new NamespacedKey(plugin, "voucher_type");
        this.voucherValueKey = new NamespacedKey(plugin, "voucher_value");
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("vouchers.enabled", true);
    }

    public ItemStack createSpVoucher(int spAmount) {
        int amount = Math.max(1, spAmount);
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("%amount%", String.valueOf(amount));
        placeholders.put("%value%", String.valueOf(amount));
        return createVoucher("sp", String.valueOf(amount), Material.PAPER, placeholders);
    }

    public ItemStack createCrateVoucher(String rawSpec) {
        KeyGrantSpec spec = parseKeyGrant(rawSpec, getDefaultCrateKeyType(), getDefaultCrateAmount());
        if (spec == null) {
            throw new IllegalArgumentException("Invalid crate value. Use <type>, <amount>, or <type:amount>.");
        }
        if (!isValidCrateType(spec.keyType)) {
            throw new IllegalArgumentException("Unknown crate type: " + spec.keyType);
        }
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("%key_type%", spec.keyType);
        placeholders.put("%amount%", String.valueOf(spec.amount));
        placeholders.put("%value%", spec.keyType + ":" + spec.amount);
        return createVoucher("crate", spec.keyType + ":" + spec.amount, Material.TRIPWIRE_HOOK, placeholders);
    }

    public ItemStack createReferralVoucher(String rawSpec) {
        String specValue = (rawSpec == null || rawSpec.isBlank()) ? getDefaultReferralValue() : rawSpec.trim();
        if ("auto".equalsIgnoreCase(specValue)) {
            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("%key_type%", getDefaultReferralKeyType());
            placeholders.put("%amount%", "AUTO");
            placeholders.put("%value%", "auto");
            return createVoucher("referral", "auto", Material.AMETHYST_SHARD, placeholders);
        }

        KeyGrantSpec spec = parseKeyGrant(specValue, getDefaultReferralKeyType(), getDefaultReferralAmount());
        if (spec == null) {
            throw new IllegalArgumentException("Invalid referral value. Use auto, <type>, <amount>, or <type:amount>.");
        }
        if (!isValidCrateType(spec.keyType)) {
            throw new IllegalArgumentException("Unknown referral crate type: " + spec.keyType);
        }
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("%key_type%", spec.keyType);
        placeholders.put("%amount%", String.valueOf(spec.amount));
        placeholders.put("%value%", spec.keyType + ":" + spec.amount);
        return createVoucher("referral", spec.keyType + ":" + spec.amount, Material.AMETHYST_SHARD, placeholders);
    }

    public ItemStack createCouponVoucher(String code) {
        String safeCode = sanitizeValue((code == null || code.isBlank()) ? getDefaultCouponCode() : code);
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("%code%", safeCode);
        placeholders.put("%value%", safeCode);
        return createVoucher("coupon", safeCode, Material.BOOK, placeholders);
    }

    public ItemStack createRankVoucher(String rank) {
        String safeRank = sanitizeValue((rank == null || rank.isBlank()) ? getDefaultRankName() : rank);
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("%rank%", safeRank);
        placeholders.put("%value%", safeRank);
        return createVoucher("rank", safeRank, Material.NAME_TAG, placeholders);
    }

    public boolean isVoucher(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) return false;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        return pdc.has(voucherKey, PersistentDataType.BYTE);
    }

    public String getVoucherType(ItemStack item) {
        if (!isVoucher(item)) return null;
        String type = item.getItemMeta().getPersistentDataContainer().get(voucherTypeKey, PersistentDataType.STRING);
        return type != null ? type.toLowerCase(Locale.ROOT) : null;
    }

    public String getVoucherValue(ItemStack item) {
        if (!isVoucher(item)) return null;
        return item.getItemMeta().getPersistentDataContainer().get(voucherValueKey, PersistentDataType.STRING);
    }

    public RedeemResult redeem(Player player, ItemStack item) {
        if (player == null || !isVoucher(item)) {
            return RedeemResult.fail(ChatColor.RED + "That item is not a voucher.");
        }
        String type = canonicalType(getVoucherType(item));
        String value = getVoucherValue(item);
        if (type == null || value == null) {
            return RedeemResult.fail(ChatColor.RED + "Invalid voucher data.");
        }

        if (type.equals("sp")) {
            return redeemSkillPointVoucher(player, value);
        }
        if (type.equals("coupon")) {
            return redeemCouponVoucher(player, value);
        }
        if (type.equals("rank")) {
            return redeemRankVoucher(player, value);
        }
        if (type.equals("crate")) {
            return redeemCrateVoucher(player, value);
        }
        if (type.equals("referral")) {
            return redeemReferralVoucher(player, value);
        }
        return RedeemResult.fail(ChatColor.RED + "Unknown voucher type: " + type);
    }

    private RedeemResult redeemSkillPointVoucher(Player player, String value) {
        int amount;
        try {
            amount = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return RedeemResult.fail(ChatColor.RED + "Invalid SP voucher value.");
        }
        amount = Math.max(1, amount);
        PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
        data.addSkillPoints(amount);
        plugin.getPlayerDataManager().savePlayerData(player.getUniqueId());
        String msg = plugin.getConfig().getString("vouchers.messages.sp_redeemed",
                "&aRedeemed &b%amount% SP&a.");
        msg = applyPlaceholders(msg, Map.of("%amount%", String.valueOf(amount)));
        return RedeemResult.success(color(msg));
    }

    private RedeemResult redeemCouponVoucher(Player player, String code) {
        Map<String, String> placeholders = basePlaceholders(player);
        placeholders.put("%code%", code);
        placeholders.put("%value%", code);

        List<String> commands = plugin.getConfig().getStringList("vouchers.types.coupon.redeem_commands");
        if (commands != null && !commands.isEmpty()) {
            runConsoleCommands(commands, placeholders);
            String msg = plugin.getConfig().getString("vouchers.messages.coupon_redeemed",
                    "&aCoupon redeemed: &e%code%&a.");
            return RedeemResult.success(color(applyPlaceholders(msg, placeholders)));
        }

        int fallbackShards = parseIntOrDefault(code, 0);
        if (fallbackShards > 0) {
            PlayerData data = plugin.getPlayerDataManager().getPlayerData(player);
            data.addEvershards(fallbackShards);
            plugin.getPlayerDataManager().savePlayerData(player.getUniqueId());
            placeholders.put("%amount%", String.valueOf(fallbackShards));
            String msg = plugin.getConfig().getString("vouchers.messages.coupon_redeemed",
                    "&aRedeemed coupon for &e%amount%&a Evershards.");
            return RedeemResult.success(color(applyPlaceholders(msg, placeholders)));
        }

        return RedeemResult.fail(ChatColor.RED + "Coupon voucher has no redeem action configured.");
    }

    private RedeemResult redeemRankVoucher(Player player, String rank) {
        Map<String, String> placeholders = basePlaceholders(player);
        placeholders.put("%rank%", rank);
        placeholders.put("%value%", rank);

        List<String> commands = plugin.getConfig().getStringList("vouchers.types.rank.redeem_commands");
        if (commands == null || commands.isEmpty()) {
            return RedeemResult.fail(ChatColor.RED + "Rank voucher has no redeem action configured.");
        }
        runConsoleCommands(commands, placeholders);
        String msg = plugin.getConfig().getString("vouchers.messages.rank_redeemed",
                "&aRank voucher redeemed: &6%rank%&a.");
        return RedeemResult.success(color(applyPlaceholders(msg, placeholders)));
    }

    private RedeemResult redeemCrateVoucher(Player player, String rawSpec) {
        KeyGrantSpec spec = parseKeyGrant(rawSpec, getDefaultCrateKeyType(), getDefaultCrateAmount());
        if (spec == null) {
            return RedeemResult.fail(ChatColor.RED + "Invalid crate voucher value.");
        }
        if (!isValidCrateType(spec.keyType)) {
            return RedeemResult.fail(ChatColor.RED + "Unknown crate type: " + spec.keyType);
        }
        grantCrateKeys(player, spec.keyType, spec.amount, "vouchers.types.crate.give_key_items");

        Map<String, String> placeholders = basePlaceholders(player);
        placeholders.put("%key_type%", spec.keyType);
        placeholders.put("%amount%", String.valueOf(spec.amount));
        String msg = plugin.getConfig().getString("vouchers.messages.crate_redeemed",
                "&aRedeemed crate voucher: &e%amount%x %key_type% key(s)&a.");
        return RedeemResult.success(color(applyPlaceholders(msg, placeholders)));
    }

    private RedeemResult redeemReferralVoucher(Player player, String rawSpec) {
        String value = rawSpec == null ? "" : rawSpec.trim();
        if (value.isBlank()) {
            value = getDefaultReferralValue();
        }

        if ("auto".equalsIgnoreCase(value)) {
            CrateRewardManager rewardManager = plugin.getCrateRewardManager();
            if (rewardManager == null) {
                return RedeemResult.fail(ChatColor.RED + "Referral reward system is unavailable.");
            }
            long amount = rewardManager.grantBuddyKeys(player);
            String keyType = plugin.getConfig().getString("crate_rewards.buddy.key_type", getDefaultReferralKeyType());
            if (keyType == null || keyType.isBlank()) keyType = getDefaultReferralKeyType();

            Map<String, String> placeholders = basePlaceholders(player);
            placeholders.put("%key_type%", keyType.toLowerCase(Locale.ROOT));
            placeholders.put("%amount%", String.valueOf(Math.max(0L, amount)));
            String msg = plugin.getConfig().getString("vouchers.messages.referral_redeemed",
                    "&aReferral voucher redeemed: &e%amount%x %key_type% key(s)&a.");
            return RedeemResult.success(color(applyPlaceholders(msg, placeholders)));
        }

        KeyGrantSpec spec = parseKeyGrant(value, getDefaultReferralKeyType(), getDefaultReferralAmount());
        if (spec == null) {
            return RedeemResult.fail(ChatColor.RED + "Invalid referral voucher value.");
        }
        if (!isValidCrateType(spec.keyType)) {
            return RedeemResult.fail(ChatColor.RED + "Unknown referral crate type: " + spec.keyType);
        }
        grantCrateKeys(player, spec.keyType, spec.amount, "vouchers.types.referral.give_key_items");

        Map<String, String> placeholders = basePlaceholders(player);
        placeholders.put("%key_type%", spec.keyType);
        placeholders.put("%amount%", String.valueOf(spec.amount));
        String msg = plugin.getConfig().getString("vouchers.messages.referral_redeemed",
                "&aReferral voucher redeemed: &e%amount%x %key_type% key(s)&a.");
        return RedeemResult.success(color(applyPlaceholders(msg, placeholders)));
    }

    private ItemStack createVoucher(String type, String value, Material fallbackMaterial, Map<String, String> placeholders) {
        String normalizedType = type.toLowerCase(Locale.ROOT);
        Material material = getMaterial("vouchers.types." + normalizedType + ".material", fallbackMaterial);
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        String defaultName = defaultNameForType(normalizedType);
        String rawName = plugin.getConfig().getString("vouchers.types." + normalizedType + ".name", defaultName);
        meta.setDisplayName(color(applyPlaceholders(rawName, placeholders)));

        List<String> defaultLore = List.of("&7Right-click to redeem.");
        List<String> rawLore = plugin.getConfig().getStringList("vouchers.types." + normalizedType + ".lore");
        List<String> loreSource = rawLore == null || rawLore.isEmpty() ? defaultLore : rawLore;
        List<String> lore = new ArrayList<>();
        for (String line : loreSource) {
            lore.add(color(applyPlaceholders(line, placeholders)));
        }
        meta.setLore(lore);

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(voucherKey, PersistentDataType.BYTE, (byte) 1);
        pdc.set(voucherTypeKey, PersistentDataType.STRING, normalizedType);
        pdc.set(voucherValueKey, PersistentDataType.STRING, value);
        item.setItemMeta(meta);
        return item;
    }

    private String defaultNameForType(String type) {
        if ("sp".equals(type)) return "&bSP Voucher";
        if ("coupon".equals(type)) return "&dCoupon Voucher";
        if ("rank".equals(type)) return "&6Rank Voucher";
        if ("crate".equals(type)) return "&eCrate Voucher";
        if ("referral".equals(type)) return "&aReferral Voucher";
        return "&fVoucher";
    }

    private Material getMaterial(String path, Material fallback) {
        String raw = plugin.getConfig().getString(path, fallback.name());
        Material parsed = raw != null ? Material.matchMaterial(raw) : null;
        return parsed != null ? parsed : fallback;
    }

    private void runConsoleCommands(List<String> commands, Map<String, String> placeholders) {
        if (commands == null) return;
        for (String raw : commands) {
            String cmd = applyPlaceholders(raw, placeholders);
            if (cmd == null || cmd.trim().isEmpty()) continue;
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.trim());
        }
    }

    private Map<String, String> basePlaceholders(Player player) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("%player%", player.getName());
        placeholders.put("%uuid%", player.getUniqueId().toString());
        return placeholders;
    }

    private String applyPlaceholders(String raw, Map<String, String> placeholders) {
        if (raw == null) return "";
        String out = raw;
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                out = out.replace(entry.getKey(), entry.getValue());
            }
        }
        return out;
    }

    private String sanitizeValue(String value) {
        if (value == null || value.isBlank()) return "default";
        return value.replace('\n', ' ').replace('\r', ' ').trim();
    }

    public String canonicalType(String rawType) {
        if (rawType == null || rawType.isBlank()) return null;
        String t = rawType.toLowerCase(Locale.ROOT);
        if (t.equals("sp") || t.equals("skillpoint") || t.equals("skillpoints")) return "sp";
        if (t.equals("coupon") || t.equals("coupons")) return "coupon";
        if (t.equals("rank") || t.equals("ranks")) return "rank";
        if (t.equals("crate") || t.equals("crates") || t.equals("key") || t.equals("keys")) return "crate";
        if (t.equals("referral") || t.equals("referal") || t.equals("referrals") || t.equals("referals")
                || t.equals("ref") || t.equals("buddy")) return "referral";
        return null;
    }

    public int getDefaultSpAmount() {
        return Math.max(1, plugin.getConfig().getInt("vouchers.types.sp.default_amount", 5));
    }

    public String getDefaultCouponCode() {
        String out = plugin.getConfig().getString("vouchers.types.coupon.default_code", "WELCOME");
        if (out == null || out.isBlank()) return "WELCOME";
        return out.trim();
    }

    public String getDefaultRankName() {
        String out = plugin.getConfig().getString("vouchers.types.rank.default_rank", "member");
        if (out == null || out.isBlank()) return "member";
        return out.trim();
    }

    public String getDefaultCrateKeyType() {
        String out = plugin.getConfig().getString("vouchers.types.crate.default_key_type", "daily");
        if (out == null || out.isBlank()) return "daily";
        return out.trim().toLowerCase(Locale.ROOT);
    }

    public long getDefaultCrateAmount() {
        return Math.max(1L, plugin.getConfig().getLong("vouchers.types.crate.default_amount", 1L));
    }

    public String getDefaultReferralValue() {
        String out = plugin.getConfig().getString("vouchers.types.referral.default_value", "auto");
        if (out == null || out.isBlank()) return "auto";
        return out.trim();
    }

    public String getDefaultReferralKeyType() {
        String out = plugin.getConfig().getString("vouchers.types.referral.default_key_type", "buddy");
        if (out == null || out.isBlank()) return "buddy";
        return out.trim().toLowerCase(Locale.ROOT);
    }

    public long getDefaultReferralAmount() {
        return Math.max(1L, plugin.getConfig().getLong("vouchers.types.referral.default_amount", 1L));
    }

    private void grantCrateKeys(Player player, String keyType, long amount, String specificPath) {
        if (player == null || keyType == null || amount <= 0) return;
        String normalized = keyType.toLowerCase(Locale.ROOT);
        boolean defaultGiveItems = plugin.getConfig().getBoolean("crate_rewards.give_key_items", true);
        boolean giveItems = plugin.getConfig().getBoolean(specificPath, defaultGiveItems);
        CrateBlockSystem crateBlockSystem = plugin.getCrateBlockSystem();
        if (giveItems && crateBlockSystem != null) {
            crateBlockSystem.giveKeyItems(player, normalized, amount);
            return;
        }
        CrateKeyLedger ledger = plugin.getCrateKeyLedger();
        if (ledger != null) {
            ledger.addKeys(player.getUniqueId(), normalized, amount);
        }
    }

    private boolean isValidCrateType(String keyType) {
        if (keyType == null || keyType.isBlank()) return false;
        String normalized = keyType.toLowerCase(Locale.ROOT);
        CrateBlockSystem crateBlockSystem = plugin.getCrateBlockSystem();
        if (crateBlockSystem != null) {
            return crateBlockSystem.isValidCrateType(normalized);
        }
        return plugin.getConfig().isConfigurationSection("crates.types." + normalized);
    }

    private KeyGrantSpec parseKeyGrant(String rawSpec, String defaultType, long defaultAmount) {
        String type = defaultType == null || defaultType.isBlank()
                ? "daily"
                : defaultType.toLowerCase(Locale.ROOT).trim();
        long amount = Math.max(1L, defaultAmount);
        if (rawSpec == null || rawSpec.isBlank()) {
            return new KeyGrantSpec(type, amount);
        }

        String raw = rawSpec.trim();
        if (raw.contains(":")) {
            String[] parts = raw.split(":", 2);
            String left = parts[0] == null ? "" : parts[0].trim();
            String right = parts.length >= 2 && parts[1] != null ? parts[1].trim() : "";
            if (!left.isBlank()) type = left.toLowerCase(Locale.ROOT);
            if (!right.isBlank()) {
                long parsed = parseLongOrDefault(right, -1L);
                if (parsed <= 0) return null;
                amount = parsed;
            }
            return new KeyGrantSpec(type, amount);
        }

        if (isLong(raw)) {
            long parsed = parseLongOrDefault(raw, -1L);
            if (parsed <= 0) return null;
            return new KeyGrantSpec(type, parsed);
        }

        return new KeyGrantSpec(raw.toLowerCase(Locale.ROOT), amount);
    }

    private boolean isLong(String raw) {
        return parseLongOrDefault(raw, Long.MIN_VALUE) != Long.MIN_VALUE;
    }

    private long parseLongOrDefault(String raw, long fallback) {
        if (raw == null) return fallback;
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private int parseIntOrDefault(String raw, int fallback) {
        if (raw == null) return fallback;
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private String color(String raw) {
        return ChatColor.translateAlternateColorCodes('&', raw == null ? "" : raw);
    }

    public static class RedeemResult {
        private final boolean success;
        private final String message;

        private RedeemResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public static RedeemResult success(String message) {
            return new RedeemResult(true, message);
        }

        public static RedeemResult fail(String message) {
            return new RedeemResult(false, message);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }
    }

    private static final class KeyGrantSpec {
        private final String keyType;
        private final long amount;

        private KeyGrantSpec(String keyType, long amount) {
            this.keyType = keyType;
            this.amount = amount;
        }
    }
}
