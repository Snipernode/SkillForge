package com.skilltree.plugin.systems;

import com.skilltree.plugin.SkillForgePlugin;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.Material;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.util.Vector;
import java.awt.Color;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;

import java.util.*;
import com.skilltree.plugin.gui.StationGUI;

public class FastTravelSystem implements Listener {
    private static final int TRAIN_SCENERY_VERSION = 2;
    private static final Material TRAIN_SCENERY_MARKER = Material.GLOWSTONE;

    private final SkillForgePlugin plugin;
    // station registry: id -> Station
    private final Map<String, Station> stations = new HashMap<>();

    // admin wand selection: player -> pos1/pos2
    private final Map<UUID, Location> wandPos1 = new HashMap<>();
    private final Map<UUID, Location> wandPos2 = new HashMap<>();

    // GUI handler (player + admin)
    private final StationGUI stationGUI;

    private final Map<UUID, TrainRide> activeRides = new HashMap<>();
    private boolean trainRealmBuilt = false;
    private Location trainRealmOrigin = null;
    private final Object trainRealmLock = new Object();
    private final Map<String, TrainSchedule> trainSchedules = new HashMap<>();
    private BukkitRunnable trainScheduleTask;
    private BukkitRunnable seatCleanupTask;
    private final Map<UUID, ArmorStand> activeSeatMounts = new HashMap<>();
    private final Map<String, Long> lastArrivalAnnouncement = new HashMap<>();
    private BufferedImage cachedPanoramaImage;

    private enum ScenicBiome {
        ISLAND,
        GRASSLAND,
        DESERT,
        SNOW,
        MOUNTAINS,
        VOLCANIC
    }

    public FastTravelSystem(SkillForgePlugin plugin) {
        this.plugin = plugin;
        // initialize GUI and register its listeners
        this.stationGUI = new StationGUI(plugin, this);
        loadStationsFromConfig();
        startTrainScheduleTask();
        startSeatCleanupTask();
    }

    // ...existing onStationInteract detection replaced to open selection GUI...
    @EventHandler
    public void onStationInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;
        Material type = event.getClickedBlock().getType();
        Player player = event.getPlayer();

        if (tryHandleTrainSeatInteract(event)) {
            return;
        }

        // detect stacked lodestone / crying obsidian as boarding
        if (type == Material.LODESTONE || type == Material.CRYING_OBSIDIAN) {
            boolean isStacked = event.getClickedBlock().getRelative(0, 1, 0).getType() == type ||
                                event.getClickedBlock().getRelative(0, -1, 0).getType() == type;
            if (isStacked) {
                // delegate to GUI
                // pass train type so GUI can reskin for C.U.M. vs B.S.B.
                String trainType = (type == Material.LODESTONE) ? "C.U.M." : "B.S.B.";
                stationGUI.openSelection(player, trainType);
                event.setCancelled(true);
                return;
            }
        }

        // handle wand left/right click to set positions (if holding station wand)
        ItemStack it = event.getItem();
        if (it != null && it.hasItemMeta() && it.getItemMeta().hasDisplayName() &&
            ChatColor.stripColor(it.getItemMeta().getDisplayName()).equalsIgnoreCase("Station Wand")) {
            event.setCancelled(true);
            UUID uid = player.getUniqueId();
            Location clicked = event.getClickedBlock() != null ? event.getClickedBlock().getLocation() : player.getLocation();
            if (event.getAction() == Action.RIGHT_CLICK_BLOCK || event.getAction() == Action.RIGHT_CLICK_AIR) {
                wandPos2.put(uid, clicked);
                player.sendMessage(ChatColor.GREEN + "Wand pos2 set: " + formatLoc(clicked));
            } else if (event.getAction() == Action.LEFT_CLICK_BLOCK || event.getAction() == Action.LEFT_CLICK_AIR) {
                wandPos1.put(uid, clicked);
                player.sendMessage(ChatColor.GREEN + "Wand pos1 set: " + formatLoc(clicked));
            }
        }
    }

    private boolean tryHandleTrainSeatInteract(PlayerInteractEvent event) {
        if (event == null) return false;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return false;
        Block clicked = event.getClickedBlock();
        if (clicked == null) return false;
        if (!isTrainSeatBlock(clicked)) return false;
        event.setCancelled(true);
        seatPlayerOnBlock(event.getPlayer(), clicked);
        return true;
    }

    private boolean isTrainSeatBlock(Block block) {
        if (block == null || trainRealmOrigin == null) return false;
        if (!isTrainRealmWorld(block.getWorld())) return false;
        if (!(block.getBlockData() instanceof Stairs)) return false;

        Material seatMat = getConfigMaterial("fasttravel.train_realm.materials.seat_base", Material.CRIMSON_STAIRS);
        if (block.getType() != seatMat) return false;

        int ox = trainRealmOrigin.getBlockX();
        int oy = trainRealmOrigin.getBlockY();
        int oz = trainRealmOrigin.getBlockZ();
        int length = plugin.getConfig().getInt("fasttravel.train_realm.car_length", 140);
        int width = plugin.getConfig().getInt("fasttravel.train_realm.car_width", 7);
        int maxX = ox + length - 1;
        int maxZ = oz + width - 1;

        int x = block.getX();
        int y = block.getY();
        int z = block.getZ();
        if (x < ox + 1 || x > maxX - 1) return false;
        if (y != oy + 1) return false;

        int seatStartX = ox + 1;
        if ((x - seatStartX) % 4 != 0) return false;

        return z == oz + 1 || z == oz + 2 || z == maxZ - 2 || z == maxZ - 1;
    }

    private void seatPlayerOnBlock(Player player, Block block) {
        if (player == null || block == null) return;
        if (player.isInsideVehicle()) {
            player.leaveVehicle();
        }
        removeSeatMount(player.getUniqueId());

        Location seatLoc = block.getLocation().add(0.5, 0.05, 0.5);
        for (Entity nearby : block.getWorld().getNearbyEntities(seatLoc, 0.25, 0.5, 0.25)) {
            if (nearby instanceof ArmorStand as && as.isMarker()) {
                if (!as.getPassengers().isEmpty()) return;
                as.remove();
            }
        }

        ArmorStand seat = block.getWorld().spawn(seatLoc, ArmorStand.class, as -> {
            as.setInvisible(true);
            as.setMarker(true);
            as.setSmall(true);
            as.setGravity(false);
            as.setInvulnerable(true);
            as.setSilent(true);
            as.setPersistent(false);
            as.setCollidable(false);
            if (block.getBlockData() instanceof Stairs stairs) {
                as.setRotation(yawForFace(stairs.getFacing()), 0f);
            }
        });

        if (!seat.addPassenger(player)) {
            seat.remove();
            return;
        }
        activeSeatMounts.put(player.getUniqueId(), seat);
    }

    private float yawForFace(BlockFace face) {
        if (face == null) return 0f;
        switch (face) {
            case NORTH: return 180f;
            case SOUTH: return 0f;
            case EAST: return -90f;
            case WEST: return 90f;
            default: return 0f;
        }
    }

    private void removeSeatMount(UUID playerId) {
        if (playerId == null) return;
        ArmorStand stand = activeSeatMounts.remove(playerId);
        if (stand == null || stand.isDead()) return;
        for (Entity passenger : new ArrayList<>(stand.getPassengers())) {
            stand.removePassenger(passenger);
        }
        stand.remove();
    }

    private void startSeatCleanupTask() {
        if (seatCleanupTask != null) {
            seatCleanupTask.cancel();
        }
        seatCleanupTask = new BukkitRunnable() {
            @Override
            public void run() {
                Iterator<Map.Entry<UUID, ArmorStand>> it = activeSeatMounts.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<UUID, ArmorStand> entry = it.next();
                    UUID playerId = entry.getKey();
                    ArmorStand stand = entry.getValue();
                    Player player = Bukkit.getPlayer(playerId);

                    boolean invalid = player == null || !player.isOnline()
                            || stand == null || stand.isDead()
                            || !stand.getPassengers().contains(player);
                    if (invalid) {
                        if (stand != null && !stand.isDead()) {
                            stand.remove();
                        }
                        it.remove();
                    }
                }
            }
        };
        seatCleanupTask.runTaskTimer(plugin, 20L, 20L);
    }

    // GUI: Station selection
    /*private void openStationSelectionGUI(Player player, Location current) {
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_AQUA + "Station Selection");
        // create station items
        int slot = 0;
        for (Station s : stations.values()) {
            if (slot >= 45) break; // reserve last row for controls
            ItemStack item = new ItemStack(Material.PAPER);
            ItemMeta m = item.getItemMeta();
            if (m != null) {
                m.setDisplayName(ChatColor.GOLD + s.name);
                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + "Travel Time: " + s.travelTime + "s");
                lore.add(ChatColor.GRAY + "ID: " + s.id);
                if (s.permission != null && !s.permission.isEmpty()) lore.add(ChatColor.RED + "Requires: " + s.permission);
                if (s.areaDefined()) lore.add(ChatColor.AQUA + "Area-protected");
                m.setLore(lore);
                item.setItemMeta(m);
            }
            inv.setItem(slot++, item);
        }

        // Admin button
        ItemStack admin = new ItemStack(Material.BEACON);
        ItemMeta am = admin.getItemMeta();
        am.setDisplayName(ChatColor.RED + "Admin Panel");
        am.setLore(Collections.singletonList(ChatColor.GRAY + "Operators only"));
        admin.setItemMeta(am);
        inv.setItem(49, admin);

        // Close button
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta cm = close.getItemMeta();
        cm.setDisplayName(ChatColor.DARK_RED + "Close");
        close.setItemMeta(cm);
        inv.setItem(53, close);

        openInventories.put(player.getUniqueId(), inv);
        player.openInventory(inv);
    }*/

    // GUI: Admin panel
    /*private void openAdminGUI(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_RED + "Station Admin");
        inv.setItem(10, makeItem(Material.GREEN_CONCRETE, ChatColor.GREEN + "Add Station Here", Collections.singletonList("Create station at your location or using wand area")));
        inv.setItem(12, makeItem(Material.STICK, ChatColor.YELLOW + "Give Wand", Collections.singletonList("Gives Station Wand (left/right click to set pos1/pos2)")));
        inv.setItem(14, makeItem(Material.PAPER, ChatColor.AQUA + "List Stations", Collections.singletonList("View or remove existing stations")));
        inv.setItem(16, makeItem(Material.BARRIER, ChatColor.RED + "Close", Collections.emptyList()));
        openInventories.put(player.getUniqueId(), inv);
        player.openInventory(inv);
    }*/

    // GUI: List stations for admin
    /*private void openAdminListGUI(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.DARK_RED + "Stations List");
        int slot = 0;
        for (Station s : stations.values()) {
            if (slot >= 54) break;
            ItemStack item = new ItemStack(Material.MAP);
            ItemMeta m = item.getItemMeta();
            if (m != null) {
                m.setDisplayName(ChatColor.GOLD + s.name);
                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.GRAY + "ID: " + s.id);
                lore.add(ChatColor.GRAY + "Travel Time: " + s.travelTime + "s");
                lore.add(ChatColor.GRAY + "Loc: " + formatLoc(s.location));
                lore.add(ChatColor.RED + "Shift-Click to remove station");
                m.setLore(lore);
                item.setItemMeta(m);
            }
            inv.setItem(slot++, item);
        }
        openInventories.put(player.getUniqueId(), inv);
        player.openInventory(inv);
    }*/

    /*@EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() == null) return;
        Player player = (Player) event.getWhoClicked();
        Inventory top = event.getView().getTopInventory();
        Inventory expected = openInventories.get(player.getUniqueId());
        if (expected == null || !expected.equals(top)) return;
        event.setCancelled(true); // prevent item moving

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        String title = event.getView().getTitle();

        if (title.contains("Station Selection")) {
            ItemMeta m = clicked.getItemMeta();
            if (m == null) return;
            String name = ChatColor.stripColor(m.getDisplayName());
            if (name.equalsIgnoreCase("Admin Panel")) {
                if (player.isOp()) openAdminGUI(player); else player.sendMessage(ChatColor.RED + "Admin access required.");
                return;
            }
            if (name.equalsIgnoreCase("Close")) { player.closeInventory(); return; }
            // station click -> extract ID from lore and start travel
            List<String> lore = m.getLore();
            if (lore != null) {
                for (String L : lore) {
                    if (L != null && L.toLowerCase().contains("id:")) {
                        String id = ChatColor.stripColor(L).split(":",2)[1].trim();
                        Station s = stations.get(id);
                        if (s == null) { player.sendMessage(ChatColor.RED + "Station missing."); return; }
                        if (s.permission != null && !s.permission.isEmpty() && !player.hasPermission(s.permission) && !player.isOp()) {
                            player.sendMessage(ChatColor.RED + "You lack permission to travel to that station.");
                            return;
                        }
                        startTravelToStation(player, s);
                        player.closeInventory();
                        return;
                    }
                }
            }
            return;
        }

        if (title.contains("Station Admin")) {
            ItemMeta m = clicked.getItemMeta();
            if (m == null) return;
            String name = ChatColor.stripColor(m.getDisplayName());
            if (name.equalsIgnoreCase("Add Station Here")) {
                // create station: prefer wand area if both pos set
                UUID uid = player.getUniqueId();
                Location loc = player.getLocation().getBlock().getLocation().add(0.5, 0, 0.5);
                Location p1 = wandPos1.get(uid);
                Location p2 = wandPos2.get(uid);
                Station s = Station.createDefault(plugin, loc, p1, p2);
                stations.put(s.id, s);
                saveStationsToConfig();
                player.sendMessage(ChatColor.GREEN + "Station added: " + s.name + " (ID: " + s.id + ")");
                return;
            }
            if (name.equalsIgnoreCase("Give Wand")) {
                ItemStack wand = new ItemStack(Material.STICK);
                ItemMeta wm = wand.getItemMeta();
                wm.setDisplayName(ChatColor.YELLOW + "Station Wand");
                wm.setLore(Arrays.asList(ChatColor.GRAY + "Left click to set pos1", ChatColor.GRAY + "Right click to set pos2"));
                wand.setItemMeta(wm);
                player.getInventory().addItem(wand);
                player.sendMessage(ChatColor.GREEN + "Station Wand given.");
                return;
            }
            if (name.equalsIgnoreCase("List Stations")) {
                openAdminListGUI(player);
                return;
            }
            if (name.equalsIgnoreCase("Close")) { player.closeInventory(); return; }
        }

        if (title.contains("Stations List")) {
            ItemMeta m = clicked.getItemMeta();
            if (m == null) return;
            List<String> lore = m.getLore();
            if (lore == null) return;
            // find id line
            String idLine = lore.stream().filter(l -> l.toLowerCase().contains("id:")).findFirst().orElse(null);
            if (idLine == null) return;
            String id = ChatColor.stripColor(idLine).split(":",2)[1].trim();
            if (event.isShiftClick()) {
                // remove station
                Station removed = stations.remove(id);
                saveStationsToConfig();
                player.sendMessage(ChatColor.RED + "Removed station: " + (removed != null ? removed.name : id));
                openAdminListGUI(player); // refresh
            } else {
                player.sendMessage(ChatColor.YELLOW + "Shift-click the station to remove it.");
            }
        }
    }*/

    /*@EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        openInventories.remove(event.getPlayer().getUniqueId());
    }*/

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uid = event.getPlayer().getUniqueId();
        wandPos1.remove(uid);
        wandPos2.remove(uid);
        removeSeatMount(uid);
        endTrainRide(uid, false);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (event == null || event.getBlock() == null) return;
        if (isTrainRealmWorld(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event == null || event.getBlock() == null) return;
        if (isTrainRealmWorld(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event == null || event.getLocation() == null) return;
        if (isTrainRealmWorld(event.getLocation().getWorld())) {
            event.setCancelled(true);
        }
    }

    // internal travel implementation (uses internal Station)
    private void startTravelToStationInternal(Player player, Station s, String trainType, int travelTimeSeconds) {
        int travelTime = Math.max(1, travelTimeSeconds);
        if (isTrainRealmEnabled()) {
            if (startTrainRealmRide(player, s, trainType, travelTime)) return;
        }
        player.sendMessage(ChatColor.GOLD + "Departing for " + s.getName() + " (" + travelTime + "s)...");
        final int ticksTotal = travelTime * 20;
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks < ticksTotal) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 40, 1, true, false));
                    int amp = (int) Math.min(255, (ticks * 255L) / Math.max(1, ticksTotal));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, amp, true, false));
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
                } else {
                    Location dest = s.getLocation().clone().add(0.5, 0, 0.5);
                    player.teleport(dest);
                    player.sendMessage(ChatColor.GREEN + "You have arrived at " + s.getName() + ".");
                    cancel();
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // public entry: travel by station id (safe API for GUIs)
    public void startTravelToStationById(Player player, String stationId) {
        startTravelToStationById(player, stationId, null);
    }

    public void startTravelToStationById(Player player, String stationId, String trainType) {
        Station s = stations.get(stationId);
        if (s == null) {
            player.sendMessage(ChatColor.RED + "Station not found: " + stationId);
            return;
        }
        if (s.isMaintenance()) {
            player.sendMessage(ChatColor.RED + "This stop is under maintenance.");
            return;
        }
        if (!canAccessStation(player, s)) {
            player.sendMessage(ChatColor.RED + "You do not have access to this station.");
            return;
        }
        int travelTime = s.getTravelTime();
        if (plugin.getConfig().getBoolean("fasttravel.dynamic_time.enabled", true)) {
            Station origin = findClosestStation(player.getLocation());
            if (origin != null && !origin.getId().equalsIgnoreCase(s.getId())) {
                travelTime = computeTravelTime(origin, s, trainType);
            }
        }
        startTravelToStationInternal(player, s, trainType, travelTime);
    }

    private boolean canAccessStation(Player player, Station s) {
        if (player == null || s == null) return false;
        if (!s.isPlayerStation()) return true;
        if (s.getOwner() != null && s.getOwner().equals(player.getUniqueId())) return true;
        return s.isPublicStation();
    }

    private int computeTravelTime(Station from, Station to, String trainType) {
        if (from == null || to == null) return 5;
        Location a = getStationCenter(from);
        Location b = getStationCenter(to);
        int crossDimTime = plugin.getConfig().getInt("fasttravel.train_schedule.cross_dimension_time", 40);
        if (a == null || b == null || a.getWorld() == null || b.getWorld() == null) return crossDimTime;
        if (!a.getWorld().equals(b.getWorld())) {
            return Math.max(1, crossDimTime);
        }
        boolean isBSB = trainType != null && trainType.equalsIgnoreCase("B.S.B.");
        double speed = plugin.getConfig().getDouble(isBSB
                ? "fasttravel.train_schedule.speed_bsb_bps"
                : "fasttravel.train_schedule.speed_cum_bps", isBSB ? 25.0 : 20.0);
        speed = Math.max(1.0, speed);
        double dist = a.distance(b);
        int computed = (int) Math.ceil(dist / speed);
        int minTime = plugin.getConfig().getInt("fasttravel.train_schedule.min_travel_time_seconds", 8);
        return Math.max(1, Math.max(computed, minTime));
    }

    private int getSecondTrainOffsetSeconds(String trainType, int direction) {
        int offsetBlocks = plugin.getConfig().getInt("fasttravel.train_schedule.second_train_offset_blocks", 120);
        if (offsetBlocks <= 0) return 0;
        String dir = plugin.getConfig().getString("fasttravel.train_schedule.second_train_direction", "RIGHT");
        boolean isSecond = "LEFT".equalsIgnoreCase(dir) ? direction < 0 : direction > 0;
        if (!isSecond) return 0;
        boolean isBSB = trainType != null && trainType.equalsIgnoreCase("B.S.B.");
        double speed = plugin.getConfig().getDouble(isBSB
                ? "fasttravel.train_schedule.speed_bsb_bps"
                : "fasttravel.train_schedule.speed_cum_bps", isBSB ? 25.0 : 20.0);
        speed = Math.max(1.0, speed);
        return (int) Math.ceil(offsetBlocks / speed);
    }

    public List<String> getRouteReport(String trainType) {
        List<String> lines = new ArrayList<>();
        List<Station> route = getOrderedStations();
        if (route.size() < 2) {
            lines.add(ChatColor.RED + "Route Calculator: need at least 2 stations.");
            return lines;
        }
        String label = trainType != null ? trainType : "Train";
        lines.add(ChatColor.GOLD + "Route Calculator — " + label);
        for (int i = 0; i < route.size(); i++) {
            Station from = route.get(i);
            Station to = route.get((i + 1) % route.size());
            Location a = getStationCenter(from);
            Location b = getStationCenter(to);
            if (a == null || b == null || a.getWorld() == null || b.getWorld() == null) {
                lines.add(ChatColor.YELLOW + from.getName() + " -> " + to.getName() + " | distance: ? | time: ?");
                continue;
            }
            boolean crossDim = !a.getWorld().equals(b.getWorld());
            int time = computeTravelTime(from, to, trainType);
            if (crossDim) {
                lines.add(ChatColor.YELLOW + from.getName() + " -> " + to.getName()
                        + ChatColor.GRAY + " | dimension jump | time: " + time + "s");
            } else {
                int dist = (int) Math.round(a.distance(b));
                lines.add(ChatColor.YELLOW + from.getName() + " -> " + to.getName()
                        + ChatColor.GRAY + " | distance: " + dist + " blocks | time: " + time + "s");
            }
        }
        return lines;
    }

    // open selection GUI for players inside the closest station area
    public Map<String, Object> openSelectionForNearestStation(Player sender, String trainType) {
        if (sender == null) return null;
        Station nearest = findClosestStation(sender.getLocation());
        if (nearest == null) return null;
        if (!canAccessStation(sender, nearest)) return null;
        int count = openSelectionForStation(nearest, trainType, sender);
        Map<String, Object> info = new HashMap<>();
        info.put("id", nearest.getId());
        info.put("name", nearest.getName());
        info.put("count", count);
        info.put("areaDefined", nearest.areaDefined());
        return info;
    }

    public Map<String, Object> openSelectionForNearestStation(Location origin, String trainType, Player fallback) {
        if (origin == null) return null;
        Station nearest = findClosestStation(origin);
        if (nearest == null) return null;
        int count = openSelectionForStation(nearest, trainType, fallback);
        Map<String, Object> info = new HashMap<>();
        info.put("id", nearest.getId());
        info.put("name", nearest.getName());
        info.put("count", count);
        info.put("areaDefined", nearest.areaDefined());
        return info;
    }

    public Map<String, Object> openSelectionForStationById(String stationId, String trainType, Player fallback) {
        if (stationId == null) return null;
        Station s = stations.get(stationId);
        if (s == null) return null;
        int count = openSelectionForStation(s, trainType, fallback);
        Map<String, Object> info = new HashMap<>();
        info.put("id", s.getId());
        info.put("name", s.getName());
        info.put("count", count);
        info.put("areaDefined", s.areaDefined());
        return info;
    }

    public String getClosestStationId(Location loc) {
        Station s = findClosestStation(loc);
        return s != null ? s.getId() : null;
    }

    public Integer estimateTravelTime(String fromId, String toId, String trainType) {
        if (fromId == null || toId == null) return null;
        Station from = stations.get(fromId);
        Station to = stations.get(toId);
        if (from == null || to == null) return null;
        return computeTravelTime(from, to, trainType);
    }

    public void handleArrivalPromptChoice(Player player, String arrivingStopId, String nextStopId, boolean continueToNext) {
        if (player == null) return;
        TrainRide ride = activeRides.get(player.getUniqueId());
        if (ride == null || ride.currentStop == null) {
            player.sendMessage(ChatColor.RED + "You are not currently on a train ride.");
            return;
        }
        if (arrivingStopId == null || !ride.currentStop.getId().equalsIgnoreCase(arrivingStopId)) {
            player.sendMessage(ChatColor.RED + "That stop prompt has expired.");
            return;
        }
        if (!continueToNext) {
            ride.continueToNext = false;
            player.sendMessage(ChatColor.GREEN + "You will disembark at " + ride.currentStop.getName() + ".");
            return;
        }
        if (ride.nextStop == null) {
            player.sendMessage(ChatColor.RED + "No further stop is available from this route.");
            ride.continueToNext = false;
            return;
        }
        if (nextStopId != null && !nextStopId.isEmpty()
                && !ride.nextStop.getId().equalsIgnoreCase(nextStopId)) {
            player.sendMessage(ChatColor.RED + "Next stop changed. Please wait for the next prompt.");
            ride.continueToNext = false;
            return;
        }
        ride.continueToNext = true;
        player.sendMessage(ChatColor.YELLOW + "You will stay on train. Next stop: " + ride.nextStop.getName() + ".");
    }

    // expose stations as simple data maps (no private Station type leak)
    public Map<String, Map<String, Object>> getStationsInfo() {
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        for (Map.Entry<String, Station> e : stations.entrySet()) {
            Station s = e.getValue();
            Map<String, Object> info = new HashMap<>();
            info.put("id", s.getId());
            info.put("name", s.getName());
            info.put("travelTime", s.getTravelTime());
            info.put("permission", s.getPermission());
            info.put("location", formatLoc(s.getLocation()));
            info.put("areaDefined", s.areaDefined());
            info.put("maintenance", s.isMaintenance());
            info.put("playerStation", s.isPlayerStation());
            info.put("public", s.isPublicStation());
            info.put("owner", s.getOwner() != null ? s.getOwner().toString() : null);
            out.put(e.getKey(), info);
        }
        return out;
    }

    public Map<String, Object> getStationInfoById(String id) {
        Station s = stations.get(id);
        if (s == null) return null;
        Map<String, Object> info = new HashMap<>();
        info.put("id", s.getId());
        info.put("name", s.getName());
        info.put("travelTime", s.getTravelTime());
        info.put("permission", s.getPermission());
        info.put("location", formatLoc(s.getLocation()));
        info.put("areaDefined", s.areaDefined());
        info.put("maintenance", s.isMaintenance());
        info.put("playerStation", s.isPlayerStation());
        info.put("public", s.isPublicStation());
        info.put("owner", s.getOwner() != null ? s.getOwner().toString() : null);
        return info;
    }

    // create station at player; returns an info map for the created station
    public Map<String, Object> createStationAtPlayer(Player player) {
        UUID uid = player.getUniqueId();
        Location loc = player.getLocation().getBlock().getLocation().add(0.5,0,0.5);
        Location p1 = wandPos1.get(uid);
        Location p2 = wandPos2.get(uid);
        Station s = Station.createDefault(plugin, loc, p1, p2);
        stations.put(s.getId(), s);
        saveStationsToConfig();
        Map<String,Object> info = new HashMap<>();
        info.put("id", s.getId());
        info.put("name", s.getName());
        return info;
    }

    public Map<String, Object> createPlayerStation(Player player, boolean isPublic) {
        if (player == null) return null;
        UUID uid = player.getUniqueId();
        int limit = getPlayerStationLimit();
        if (limit > 0 && countPlayerStations(uid) >= limit) {
            return null;
        }
        Location loc = player.getLocation().getBlock().getLocation().add(0.5, 0, 0.5);
        Location p1 = wandPos1.get(uid);
        Location p2 = wandPos2.get(uid);
        Station s = Station.createPlayerStation(plugin, uid, loc, p1, p2, isPublic);
        stations.put(s.getId(), s);
        saveStationsToConfig();
        Map<String,Object> info = new HashMap<>();
        info.put("id", s.getId());
        info.put("name", s.getName());
        info.put("playerStation", true);
        info.put("public", s.isPublicStation());
        info.put("owner", uid.toString());
        return info;
    }

    public int countPlayerStations(UUID owner) {
        if (owner == null) return 0;
        int count = 0;
        for (Station s : stations.values()) {
            if (s != null && s.isPlayerStation() && owner.equals(s.getOwner())) {
                count++;
            }
        }
        return count;
    }

    public int getPlayerStationLimit() {
        return plugin.getConfig().getInt("fasttravel.player_station_limit", 3);
    }

    // remove station by id and return basic info map for removed station (or null)
    public Map<String,Object> removeStationById(String id) {
        Station removed = stations.remove(id);
        saveStationsToConfig();
        if (removed == null) return null;
        Map<String,Object> info = new HashMap<>();
        info.put("id", removed.getId());
        info.put("name", removed.getName());
        return info;
    }

    // rename station by id and return basic info map for updated station (or null)
    public Map<String,Object> renameStationById(String id, String newName) {
        if (newName == null) return null;
        String clean = newName.trim();
        if (clean.isEmpty()) return null;
        Station s = stations.get(id);
        if (s == null) return null;
        s.setName(clean);
        saveStationsToConfig();
        Map<String,Object> info = new HashMap<>();
        info.put("id", s.getId());
        info.put("name", s.getName());
        return info;
    }

    public Map<String,Object> toggleStationMaintenance(String id) {
        Station s = stations.get(id);
        if (s == null) return null;
        s.setMaintenance(!s.isMaintenance());
        saveStationsToConfig();
        Map<String,Object> info = new HashMap<>();
        info.put("id", s.getId());
        info.put("name", s.getName());
        info.put("maintenance", s.isMaintenance());
        return info;
    }

    public Map<String,Object> toggleStationPublic(String id) {
        Station s = stations.get(id);
        if (s == null || !s.isPlayerStation()) return null;
        s.setPublicStation(!s.isPublicStation());
        saveStationsToConfig();
        Map<String,Object> info = new HashMap<>();
        info.put("id", s.getId());
        info.put("name", s.getName());
        info.put("public", s.isPublicStation());
        return info;
    }

    public Map<String,Object> setStationTravelTime(String id, int seconds) {
        Station s = stations.get(id);
        if (s == null) return null;
        s.travelTime = Math.max(1, seconds);
        saveStationsToConfig();
        Map<String,Object> info = new HashMap<>();
        info.put("id", s.getId());
        info.put("name", s.getName());
        info.put("travelTime", s.getTravelTime());
        return info;
    }

    // Helpers: create station items
    private ItemStack makeItem(Material mat, String name, List<String> lore) {
        ItemStack i = new ItemStack(mat);
        ItemMeta m = i.getItemMeta();
        if (m != null) {
            m.setDisplayName(name);
            if (lore != null) m.setLore(lore);
            i.setItemMeta(m);
        }
        return i;
    }

    private String formatLoc(Location l) {
        return l.getWorld().getName() + "@" + l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ();
    }

    private Station findClosestStation(Location loc) {
        if (loc == null) return null;
        Station best = null;
        double bestDist = Double.MAX_VALUE;
        for (Station s : stations.values()) {
            Location center = getStationCenter(s);
            if (center == null || center.getWorld() == null || loc.getWorld() == null) continue;
            if (!center.getWorld().equals(loc.getWorld())) continue;
            double dist = center.distanceSquared(loc);
            if (dist < bestDist) {
                bestDist = dist;
                best = s;
            }
        }
        return best;
    }

    private Location getStationCenter(Station s) {
        if (s == null) return null;
        if (s.areaDefined()) {
            Location a = s.getAreaPos1();
            Location b = s.getAreaPos2();
            if (a != null && b != null && a.getWorld() != null && a.getWorld().equals(b.getWorld())) {
                double cx = (a.getBlockX() + b.getBlockX()) / 2.0;
                double cy = (a.getBlockY() + b.getBlockY()) / 2.0;
                double cz = (a.getBlockZ() + b.getBlockZ()) / 2.0;
                return new Location(a.getWorld(), cx, cy, cz);
            }
        }
        return s.getLocation();
    }

    private int openSelectionForStation(Station s, String trainType, Player fallback) {
        if (s == null) return 0;
        int count = 0;
        if (s.areaDefined()) {
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                if (p == null || p.getLocation() == null) continue;
                if (isInsideArea(p.getLocation(), s.getAreaPos1(), s.getAreaPos2()) && canAccessStation(p, s)) {
                    stationGUI.openSelection(p, trainType);
                    count++;
                }
            }
            return count;
        }

        if (fallback != null) {
            stationGUI.openSelection(fallback, trainType);
            count++;
        }
        return count;
    }

    private boolean isInsideArea(Location loc, Location p1, Location p2) {
        if (loc == null || p1 == null || p2 == null) return false;
        if (loc.getWorld() == null || !loc.getWorld().equals(p1.getWorld()) || !loc.getWorld().equals(p2.getWorld())) {
            return false;
        }
        int minX = Math.min(p1.getBlockX(), p2.getBlockX());
        int maxX = Math.max(p1.getBlockX(), p2.getBlockX());
        int minY = Math.min(p1.getBlockY(), p2.getBlockY());
        int maxY = Math.max(p1.getBlockY(), p2.getBlockY());
        int minZ = Math.min(p1.getBlockZ(), p2.getBlockZ());
        int maxZ = Math.max(p1.getBlockZ(), p2.getBlockZ());

        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    private void clampPlayerToTrain(Player player, TrainRide ride) {
        if (player == null || ride == null) return;
        Location current = player.getLocation();
        if (current == null || current.getWorld() == null) return;
        if (!current.getWorld().equals(trainRealmOrigin.getWorld())) return;
        double minX = ride.originX + 1.0;
        double maxX = ride.originX + ride.length - 2.0;
        double minY = ride.originY + 1.0;
        double maxY = ride.originY + ride.height - 2.0;
        double minZ = ride.originZ + 1.0;
        double maxZ = ride.originZ + ride.width - 2.0;
        if (current.getX() < minX || current.getX() > maxX
                || current.getY() < minY || current.getY() > maxY
                || current.getZ() < minZ || current.getZ() > maxZ) {
            Location safe = new Location(current.getWorld(), ride.lastBaseX, ride.baseY, ride.baseZ, current.getYaw(), current.getPitch());
            player.teleport(safe);
        }
    }

    private void setupPanoramaScroller(TrainRide ride, String trainType, double originX, double originY, double originZ, int length, int width) {
        if (ride == null) return;
        boolean enabled = plugin.getConfig().getBoolean("fasttravel.train_realm.panorama.enabled", false);
        boolean scroll = plugin.getConfig().getBoolean("fasttravel.train_realm.panorama.scroll_enabled", false);
        if (!enabled || !scroll) {
            ride.panoramaScroll = false;
            return;
        }
        BufferedImage img = getPanoramaImage();
        if (img == null) {
            ride.panoramaScroll = false;
            return;
        }
        int panoLen = plugin.getConfig().getInt("fasttravel.train_realm.panorama.length", length);
        int panoHeight = plugin.getConfig().getInt("fasttravel.train_realm.panorama.height", 40);
        int zOffset = plugin.getConfig().getInt("fasttravel.train_realm.panorama.z_offset", 8);
        int yOffset = plugin.getConfig().getInt("fasttravel.train_realm.panorama.y_offset", 2);
        boolean isBSB = trainType != null && trainType.equalsIgnoreCase("B.S.B.");
        double scrollBps = plugin.getConfig().getDouble(isBSB
                ? "fasttravel.train_realm.panorama.scroll_bps_bsb"
                : "fasttravel.train_realm.panorama.scroll_bps_cum", isBSB ? 25.0 : 20.0);
        ride.panoramaScroll = true;
        ride.panoramaImage = img;
        ride.panoramaImageWidth = Math.max(1, img.getWidth());
        ride.panoramaImageHeight = Math.max(1, img.getHeight());
        ride.panoramaLength = Math.max(1, panoLen);
        ride.panoramaHeight = Math.max(1, panoHeight);
        ride.panoramaBaseX = (int) Math.floor(originX);
        ride.panoramaBaseY = (int) Math.floor(originY + yOffset);
        ride.panoramaZ = (int) Math.floor(originZ + width + zOffset);
        ride.panoramaScrollPerTick = Math.max(0.01, scrollBps / 20.0);
        ride.panoramaColumn = 0;
        ride.panoramaSrcX = 0;
        ride.panoramaRemainder = 0.0;
    }

    private void scrollPanorama(TrainRide ride) {
        if (ride == null || !ride.panoramaScroll || ride.panoramaImage == null) return;
        ride.panoramaRemainder += ride.panoramaScrollPerTick;
        int steps = (int) Math.floor(ride.panoramaRemainder);
        if (steps <= 0) return;
        ride.panoramaRemainder -= steps;
        for (int i = 0; i < steps; i++) {
            ride.panoramaColumn = (ride.panoramaColumn + 1) % ride.panoramaLength;
            ride.panoramaSrcX = (ride.panoramaSrcX + 1) % ride.panoramaImageWidth;
            int x = ride.panoramaBaseX + ride.panoramaColumn;
            for (int y = 0; y < ride.panoramaHeight; y++) {
                int srcY = (int) Math.floor((y / (double) ride.panoramaHeight) * ride.panoramaImageHeight);
                if (srcY >= ride.panoramaImageHeight) srcY = ride.panoramaImageHeight - 1;
                int rgb = ride.panoramaImage.getRGB(ride.panoramaSrcX, srcY);
                Material mat = closestMapMaterial(new Color(rgb, true));
                trainRealmOrigin.getWorld().getBlockAt(x, ride.panoramaBaseY + y, ride.panoramaZ).setType(mat);
            }
        }
    }

    private void setupSceneryScroller(TrainRide ride, String trainType) {
        if (ride == null) return;
        boolean enabled = plugin.getConfig().getBoolean("fasttravel.train_realm.scenery.scroll_enabled", true);
        if (!enabled) {
            ride.sceneryScroll = false;
            return;
        }
        int length = Math.max(8, ride.length);
        int depth = Math.max(8, plugin.getConfig().getInt("fasttravel.train_realm.scenery.depth", 42));
        int clearHeight = Math.max(20, plugin.getConfig().getInt("fasttravel.train_realm.scenery.clear_height", 52));
        int maxRise = Math.max(4, plugin.getConfig().getInt("fasttravel.train_realm.scenery.max_rise", 16));
        int segmentLength = Math.max(8, plugin.getConfig().getInt("fasttravel.train_realm.scenery.segment_length", 24));
        int interval = Math.max(1, plugin.getConfig().getInt("fasttravel.train_realm.scenery.scroll_interval_ticks", 2));

        boolean isBSB = trainType != null && trainType.equalsIgnoreCase("B.S.B.");
        double fallbackBps = plugin.getConfig().getDouble(isBSB
                ? "fasttravel.train_schedule.speed_bsb_bps"
                : "fasttravel.train_schedule.speed_cum_bps", isBSB ? 25.0 : 20.0);
        double scrollBps = plugin.getConfig().getDouble(isBSB
                ? "fasttravel.train_realm.scenery.scroll_bps_bsb"
                : "fasttravel.train_realm.scenery.scroll_bps_cum", fallbackBps);

        ride.sceneryScroll = true;
        ride.sceneryDepth = depth;
        ride.sceneryClearHeight = clearHeight;
        ride.sceneryMaxRise = maxRise;
        ride.scenerySegmentLength = segmentLength;
        ride.sceneryTickInterval = interval;
        ride.sceneryTickCounter = 0;
        ride.sceneryRemainder = 0.0;
        ride.sceneryScrollPerTick = Math.max(0.01, scrollBps / 20.0);
        ride.sceneryFloorY = (int) Math.floor(ride.originY) - 10;
        ride.sceneryBaseY = (int) Math.floor(ride.originY) - 4;
        ride.sceneryPositiveEdgeZ = (int) Math.floor(ride.originZ + ride.width);
        ride.sceneryNegativeEdgeZ = (int) Math.floor(ride.originZ - 1);
        ride.sceneryColumn = ride.direction >= 0 ? -1 : length;
        ride.sceneryStreamIndex = 0L;
        ride.sceneryPositiveSeedOffset = 0;
        ride.sceneryNegativeSeedOffset = 197;
    }

    private void scrollScenery(TrainRide ride) {
        if (ride == null || !ride.sceneryScroll || trainRealmOrigin == null || trainRealmOrigin.getWorld() == null) return;
        ride.sceneryTickCounter++;
        if (ride.sceneryTickCounter % ride.sceneryTickInterval != 0) {
            return;
        }

        // Accumulate blocks-to-shift at configured cadence.
        ride.sceneryRemainder += (ride.sceneryScrollPerTick * ride.sceneryTickInterval);
        int steps = (int) Math.floor(ride.sceneryRemainder);
        if (steps <= 0) return;
        ride.sceneryRemainder -= steps;

        World world = trainRealmOrigin.getWorld();
        int dir = ride.direction >= 0 ? 1 : -1;
        int ox = (int) Math.floor(ride.originX);
        int length = Math.max(8, ride.length);
        for (int i = 0; i < steps; i++) {
            ride.sceneryColumn = Math.floorMod(ride.sceneryColumn + dir, length);
            ride.sceneryStreamIndex++;
            int x = ox + ride.sceneryColumn;
            renderSceneryStripColumn(world, x, ride.sceneryPositiveEdgeZ, 1, ride.sceneryStreamIndex, ride.sceneryPositiveSeedOffset, ride);
            renderSceneryStripColumn(world, x, ride.sceneryNegativeEdgeZ, -1, ride.sceneryStreamIndex, ride.sceneryNegativeSeedOffset, ride);
        }
    }

    private void renderSceneryStripColumn(World world, int x, int edgeZ, int sideDir, long streamPos, int seedOffset, TrainRide ride) {
        if (world == null || ride == null) return;
        int depth = ride.sceneryDepth;
        int clearHeight = ride.sceneryClearHeight;
        int floorY = ride.sceneryFloorY;
        int baseY = ride.sceneryBaseY;
        int maxRise = ride.sceneryMaxRise;

        int nearZ = edgeZ + sideDir;
        int farZ = edgeZ + (sideDir * (depth + 18));
        int lowZ = Math.min(nearZ, farZ);
        int highZ = Math.max(nearZ, farZ);
        int minY = floorY - 2;
        int maxY = (int) Math.floor(ride.originY) + clearHeight;

        clearSceneryColumnVolume(world, x, minY, maxY, lowZ, highZ);

        ScenicBiome biome = biomeForStream(streamPos, ride.scenerySegmentLength, seedOffset);
        int rel = (int) Math.floorMod(streamPos + seedOffset, Integer.MAX_VALUE);
        for (int d = 1; d <= depth; d++) {
            int z = edgeZ + (d * sideDir);
            int topY = baseY + terrainHeight(rel, d, depth, biome, maxRise, seedOffset);

            if (biome == ScenicBiome.ISLAND && d <= 4) {
                fillColumn(world, x, z, floorY, baseY - 1, Material.SANDSTONE);
                world.getBlockAt(x, baseY, z).setType(Material.WATER, false);
                continue;
            }

            Material fill = fillMaterial(biome);
            fillColumn(world, x, z, floorY, topY - 1, fill);
            world.getBlockAt(x, topY, z).setType(topMaterial(biome, topY, (int) Math.floor(ride.originY)), false);
            decorateSceneryColumn(world, x, topY, z, biome, d, depth, seedOffset);
        }

        // Horizon layer for depth.
        int backdropDistance = depth + 10;
        int baseZ = edgeZ + (sideDir * backdropDistance);
        double wave = Math.sin((streamPos + seedOffset) * 0.07) * 3.5 + Math.cos((streamPos + seedOffset) * 0.19) * 2.0;
        int hill = Math.max(3, 7 + (int) Math.round(wave));
        for (int h = 0; h <= hill; h++) {
            int y = floorY + 2 + h;
            Material m = (h == hill && hill > 9) ? Material.SNOW_BLOCK : Material.DEEPSLATE;
            world.getBlockAt(x, y, baseZ).setType(m, false);
            world.getBlockAt(x, y, baseZ + sideDir).setType(m, false);
        }
    }

    private void clearSceneryColumnVolume(World world, int x, int minY, int maxY, int minZ, int maxZ) {
        if (world == null) return;
        for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                world.getBlockAt(x, y, z).setType(Material.AIR, false);
            }
        }
    }

    private ScenicBiome biomeForStream(long streamPos, int segmentLength, int seedOffset) {
        if (segmentLength <= 0) segmentLength = 24;
        long segmentIndex = Math.floorDiv(streamPos, segmentLength);
        long h = segmentIndex * 1103515245L + (seedOffset * 0x9E3779B97F4A7C15L);
        h ^= (h >>> 32);
        int idx = (int) Math.floorMod(h, ScenicBiome.values().length);
        return ScenicBiome.values()[idx];
    }

    private void applySceneryMotionBlur(Player player, TrainRide ride, int ticks) {
        if (player == null || ride == null) return;
        if (!plugin.getConfig().getBoolean("fasttravel.train_realm.scenery.motion_blur_particles", true)) return;

        int interval = Math.max(1, plugin.getConfig().getInt("fasttravel.train_realm.scenery.motion_blur_interval_ticks", 3));
        if (ticks % interval != 0) return;

        String rawParticle = plugin.getConfig().getString("fasttravel.train_realm.scenery.motion_blur_particle", "SWEEP_ATTACK");
        Particle particle = Particle.SWEEP_ATTACK;
        if (rawParticle != null && !rawParticle.isBlank()) {
            try {
                particle = Particle.valueOf(rawParticle.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                particle = Particle.SWEEP_ATTACK;
            }
        }

        int count = Math.max(1, plugin.getConfig().getInt("fasttravel.train_realm.scenery.motion_blur_count", 1));
        Location eye = player.getEyeLocation();
        double minX = ride.originX + 1.0;
        double maxX = ride.originX + ride.length - 2.0;
        double px = Math.max(minX, Math.min(maxX, eye.getX()));
        double py = Math.max(ride.originY + 1.2, Math.min(ride.originY + ride.height - 1.2, eye.getY()));
        double zNear = ride.originZ + 0.8;
        double zFar = ride.originZ + ride.width - 0.8;

        World world = player.getWorld();
        world.spawnParticle(particle, px, py, zNear, count, 0.15, 0.2, 0.05, 0.0);
        world.spawnParticle(particle, px, py, zFar, count, 0.15, 0.2, 0.05, 0.0);
    }

    private BufferedImage getPanoramaImage() {
        if (cachedPanoramaImage != null) return cachedPanoramaImage;
        String path = plugin.getConfig().getString("fasttravel.train_realm.panorama.image_path", "");
        String fallbackPath = plugin.getConfig().getString("fasttravel.train_realm.panorama.fallback_image_path", "");
        File imgFile = resolvePanoramaFile(path);
        if (imgFile == null || !imgFile.exists()) {
            imgFile = resolvePanoramaFile(fallbackPath);
        }
        if (imgFile == null || !imgFile.exists()) {
            return null;
        }
        try {
            cachedPanoramaImage = ImageIO.read(imgFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to read panorama image: " + e.getMessage());
            return null;
        }
        return cachedPanoramaImage;
    }

    private File resolvePanoramaFile(String path) {
        if (path == null || path.trim().isEmpty()) return null;
        File imgFile = new File(path);
        if (!imgFile.isAbsolute()) {
            plugin.getDataFolder().mkdirs();
            imgFile = new File(plugin.getDataFolder(), path);
        }
        return imgFile;
    }

    private void startTrainScheduleTask() {
        if (trainScheduleTask != null) {
            trainScheduleTask.cancel();
        }
        if (!plugin.getConfig().getBoolean("fasttravel.train_schedule.enabled", true)) return;
        trainScheduleTask = new BukkitRunnable() {
            @Override
            public void run() {
                tickTrainSchedules();
            }
        };
        trainScheduleTask.runTaskTimer(plugin, 20L, 20L);
    }

    private void tickTrainSchedules() {
        if (stations.isEmpty()) return;
        tickTrainSchedule("C.U.M.", -1);
        tickTrainSchedule("C.U.M.", 1);
        tickTrainSchedule("B.S.B.", -1);
        tickTrainSchedule("B.S.B.", 1);
    }

    private void tickTrainSchedule(String trainType, int direction) {
        String key = trainType + (direction < 0 ? ":L" : ":R");
        TrainSchedule schedule = trainSchedules.computeIfAbsent(key, k -> new TrainSchedule(trainType, direction));
        if (schedule.current == null || !stations.containsKey(schedule.current.getId())) {
            schedule.current = getDefaultStartStation();
            schedule.next = null;
            schedule.remainingSeconds = 0;
            schedule.offsetApplied = false;
        }
        if (schedule.current == null) return;
        if (schedule.next == null || !stations.containsKey(schedule.next.getId())) {
            schedule.next = findNextStation(schedule.current, schedule.direction);
            schedule.remainingSeconds = 0;
        }
        if (schedule.next == null) return;
        if (schedule.remainingSeconds <= 0) {
            int travel = computeTravelTime(schedule.current, schedule.next, trainType);
            if (!schedule.offsetApplied) {
                int offset = getSecondTrainOffsetSeconds(trainType, schedule.direction);
                schedule.remainingSeconds = travel + offset;
                schedule.offsetApplied = true;
            } else {
                schedule.remainingSeconds = travel;
            }
        }
        schedule.remainingSeconds--;
        if (schedule.remainingSeconds <= 0) {
            Station arrived = schedule.next;
            Station nextStop = findNextStation(arrived, schedule.direction);
            announceArrival(arrived, nextStop, trainType);
            schedule.current = arrived;
            schedule.next = nextStop;
            schedule.remainingSeconds = 0;
        }
    }

    private Station getDefaultStartStation() {
        List<Station> list = getOrderedStations();
        if (list.isEmpty()) return null;
        return list.get(0);
    }

    private Station findNextStation(Station from, int direction) {
        if (from == null) return null;
        List<Station> route = getOrderedStations();
        if (route.size() <= 1) return null;
        int index = -1;
        for (int i = 0; i < route.size(); i++) {
            if (route.get(i).getId().equalsIgnoreCase(from.getId())) {
                index = i;
                break;
            }
        }
        if (index == -1) return null;
        int dir = direction >= 0 ? 1 : -1;
        int nextIndex = (index + dir + route.size()) % route.size();
        Station next = route.get(nextIndex);
        if (!isCrossDimension(from, next)) {
            return next;
        }
        Station closestCrossDim = findClosestCrossDimensionStation(from);
        return closestCrossDim != null ? closestCrossDim : next;
    }

    private boolean isCrossDimension(Station a, Station b) {
        Location la = getStationCenter(a);
        Location lb = getStationCenter(b);
        if (la == null || lb == null || la.getWorld() == null || lb.getWorld() == null) return false;
        return !la.getWorld().equals(lb.getWorld());
    }

    private Station findClosestCrossDimensionStation(Station from) {
        Location origin = getStationCenter(from);
        if (origin == null || origin.getWorld() == null) return null;

        Station best = null;
        double bestDist = Double.MAX_VALUE;
        for (Station candidate : stations.values()) {
            if (candidate == null) continue;
            if (from.getId().equalsIgnoreCase(candidate.getId())) continue;
            Location c = getStationCenter(candidate);
            if (c == null || c.getWorld() == null) continue;
            if (c.getWorld().equals(origin.getWorld())) continue;

            // Cross-dimension next stop is chosen by nearest raw coordinates.
            double dx = origin.getX() - c.getX();
            double dy = origin.getY() - c.getY();
            double dz = origin.getZ() - c.getZ();
            double dist = (dx * dx) + (dy * dy) + (dz * dz);
            if (dist < bestDist) {
                bestDist = dist;
                best = candidate;
            }
        }
        return best;
    }

    private int resolveRideDirection(Station from, Station to) {
        if (from == null || to == null) return 1;
        if (from.getId().equalsIgnoreCase(to.getId())) return 1;
        List<Station> route = getOrderedStations();
        if (route.size() <= 1) return 1;
        int fromIndex = -1;
        int toIndex = -1;
        for (int i = 0; i < route.size(); i++) {
            String id = route.get(i).getId();
            if (id.equalsIgnoreCase(from.getId())) fromIndex = i;
            if (id.equalsIgnoreCase(to.getId())) toIndex = i;
        }
        if (fromIndex < 0 || toIndex < 0) return 1;
        int rightDistance = (toIndex - fromIndex + route.size()) % route.size();
        int leftDistance = (fromIndex - toIndex + route.size()) % route.size();
        if (rightDistance == leftDistance) return 1;
        return rightDistance < leftDistance ? 1 : -1;
    }

    private List<Station> getOrderedStations() {
        List<Station> list = new ArrayList<>(stations.values());
        String axis = plugin.getConfig().getString("fasttravel.train_schedule.axis", "X");
        boolean useZ = axis != null && axis.equalsIgnoreCase("Z");
        list.sort((a, b) -> {
            Location la = getStationCenter(a);
            Location lb = getStationCenter(b);
            String wa = la != null && la.getWorld() != null ? la.getWorld().getName() : "";
            String wb = lb != null && lb.getWorld() != null ? lb.getWorld().getName() : "";
            int worldCmp = wa.compareToIgnoreCase(wb);
            if (worldCmp != 0) return worldCmp;
            double ca = 0.0;
            double cb = 0.0;
            if (la != null) ca = useZ ? la.getZ() : la.getX();
            if (lb != null) cb = useZ ? lb.getZ() : lb.getX();
            return Double.compare(ca, cb);
        });
        return list;
    }

    private void announceArrival(Station current, Station next, String trainType) {
        if (current == null) return;
        List<Player> players = getPlayersAtStation(current);
        if (players.isEmpty()) {
            return;
        }
        boolean shouldAnnounce = shouldAnnounceArrival(current.getId());
        if (shouldAnnounce) {
            String msg = ChatColor.GOLD + "We are now arriving at " + current.getName() + ".";
            String nextMsg = next != null
                    ? ChatColor.YELLOW + "Next stop: " + next.getName() + "."
                    : ChatColor.YELLOW + "Next stop: TBD.";
            for (Player player : players) {
                player.sendMessage(msg);
                player.sendMessage(nextMsg);
            }
        }
        openSelectionForStation(current, trainType, null);
    }

    private boolean shouldAnnounceArrival(String stationId) {
        if (stationId == null) return true;
        int cooldown = plugin.getConfig().getInt("fasttravel.train_schedule.announcement_cooldown_seconds", 8);
        if (cooldown <= 0) return true;
        long now = System.currentTimeMillis();
        Long last = lastArrivalAnnouncement.get(stationId);
        if (last != null && (now - last) < (cooldown * 1000L)) {
            return false;
        }
        lastArrivalAnnouncement.put(stationId, now);
        return true;
    }

    private List<Player> getPlayersAtStation(Station station) {
        List<Player> players = new ArrayList<>();
        if (station == null) return players;
        if (station.areaDefined()) {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                if (player == null || player.getLocation() == null) continue;
                if (isInsideArea(player.getLocation(), station.getAreaPos1(), station.getAreaPos2())) {
                    if (canAccessStation(player, station)) {
                        players.add(player);
                    }
                }
            }
            return players;
        }
        int radius = plugin.getConfig().getInt("fasttravel.train_schedule.arrival_radius", 6);
        Location center = getStationCenter(station);
        if (center == null || center.getWorld() == null) return players;
        double maxDist = radius * radius;
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player == null || player.getLocation() == null) continue;
            if (!player.getWorld().equals(center.getWorld())) continue;
            if (player.getLocation().distanceSquared(center) <= maxDist) {
                if (canAccessStation(player, station)) {
                    players.add(player);
                }
            }
        }
        return players;
    }

    // persistence: load/save stations to config
    private void loadStationsFromConfig() {
        try {
            stations.clear();
            if (!plugin.getConfig().isConfigurationSection("fasttravel.stations")) return;
            for (String key : plugin.getConfig().getConfigurationSection("fasttravel.stations").getKeys(false)) {
                String path = "fasttravel.stations." + key;
                String name = plugin.getConfig().getString(path + ".name", key);
                String world = plugin.getConfig().getString(path + ".world", plugin.getServer().getWorlds().get(0).getName());
                double x = plugin.getConfig().getDouble(path + ".x", 0);
                double y = plugin.getConfig().getDouble(path + ".y", 64);
                double z = plugin.getConfig().getDouble(path + ".z", 0);
                int tt = plugin.getConfig().getInt(path + ".travelTime", 5);
                String perm = plugin.getConfig().getString(path + ".permission", "");
                boolean maintenance = plugin.getConfig().getBoolean(path + ".maintenance", false);
                boolean playerStation = plugin.getConfig().getBoolean(path + ".playerStation", false);
                boolean isPublic = plugin.getConfig().getBoolean(path + ".public", true);
                String ownerRaw = plugin.getConfig().getString(path + ".owner", null);
                Station s = new Station(key, name, new Location(Bukkit.getWorld(world), x, y, z), tt, perm);
                s.maintenance = maintenance;
                s.playerStation = playerStation;
                s.publicStation = isPublic;
                if (ownerRaw != null && !ownerRaw.isEmpty()) {
                    try { s.owner = UUID.fromString(ownerRaw); } catch (IllegalArgumentException ignored) {}
                }
                if (plugin.getConfig().contains(path + ".area.pos1")) {
                    s.areaPos1 = deserializeLoc(plugin.getConfig().getString(path + ".area.pos1"));
                }
                if (plugin.getConfig().contains(path + ".area.pos2")) {
                    s.areaPos2 = deserializeLoc(plugin.getConfig().getString(path + ".area.pos2"));
                }
                stations.put(key, s);
            }
        } catch (Exception ignored) {}
    }

    private void saveStationsToConfig() {
        try {
            plugin.getConfig().set("fasttravel.stations", null);
            for (Station s : stations.values()) {
                String path = "fasttravel.stations." + s.id;
                plugin.getConfig().set(path + ".name", s.name);
                plugin.getConfig().set(path + ".world", s.location.getWorld().getName());
                plugin.getConfig().set(path + ".x", s.location.getX());
                plugin.getConfig().set(path + ".y", s.location.getY());
                plugin.getConfig().set(path + ".z", s.location.getZ());
                plugin.getConfig().set(path + ".travelTime", s.travelTime);
                plugin.getConfig().set(path + ".permission", s.permission != null ? s.permission : "");
                plugin.getConfig().set(path + ".maintenance", s.maintenance);
                plugin.getConfig().set(path + ".playerStation", s.playerStation);
                plugin.getConfig().set(path + ".public", s.publicStation);
                plugin.getConfig().set(path + ".owner", s.owner != null ? s.owner.toString() : null);
                if (s.areaPos1 != null) plugin.getConfig().set(path + ".area.pos1", serializeLoc(s.areaPos1));
                if (s.areaPos2 != null) plugin.getConfig().set(path + ".area.pos2", serializeLoc(s.areaPos2));
            }
            plugin.saveConfig();
        } catch (Exception ignored) {}
    }

    private String serializeLoc(Location l) {
        return l.getWorld().getName() + ";" + l.getBlockX() + ";" + l.getBlockY() + ";" + l.getBlockZ();
    }
    private Location deserializeLoc(String s) {
        try {
            String[] parts = s.split(";");
            World w = Bukkit.getWorld(parts[0]);
            return new Location(w, Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
        } catch (Exception e) { return null; }
    }

    // Station data holder
    private static class Station {
        private final String id;
        private String name;
        private Location location;
        private int travelTime;
        private String permission;
        private Location areaPos1, areaPos2;
        private boolean maintenance;
        private boolean playerStation;
        private boolean publicStation;
        private UUID owner;

        Station(String id, String name, Location loc, int travelTime, String permission) {
            this.id = id;
            this.name = name;
            this.location = loc;
            this.travelTime = travelTime;
            this.permission = permission;
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public Location getLocation() { return location; }
        public int getTravelTime() { return travelTime; }
        public String getPermission() { return permission; }
        public Location getAreaPos1() { return areaPos1; }
        public Location getAreaPos2() { return areaPos2; }
        public void setName(String name) { this.name = name; }
        public boolean isMaintenance() { return maintenance; }
        public void setMaintenance(boolean maintenance) { this.maintenance = maintenance; }
        public boolean isPlayerStation() { return playerStation; }
        public boolean isPublicStation() { return publicStation; }
        public void setPublicStation(boolean value) { this.publicStation = value; }
        public UUID getOwner() { return owner; }

        public boolean areaDefined() {
            return areaPos1 != null && areaPos2 != null && areaPos1.getWorld().equals(areaPos2.getWorld());
        }

        static Station createDefault(SkillForgePlugin plugin, Location loc, Location p1, Location p2) {
            String id = "stn_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            String name = "Station_" + id.substring(4);
            Station s = new Station(id, name, loc.clone(), 5, "");
            s.maintenance = false;
            s.playerStation = false;
            s.publicStation = true;
            if (p1 != null && p2 != null && p1.getWorld().equals(p2.getWorld())) {
                s.areaPos1 = p1.clone();
                s.areaPos2 = p2.clone();
            }
            return s;
        }

        static Station createPlayerStation(SkillForgePlugin plugin, UUID owner, Location loc, Location p1, Location p2, boolean isPublic) {
            String id = "pstn_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            String name = "PlayerStation_" + id.substring(5);
            int travelTime = plugin.getConfig().getInt("fasttravel.player_station_default_time", 5);
            Station s = new Station(id, name, loc.clone(), travelTime, "");
            s.maintenance = false;
            s.playerStation = true;
            s.publicStation = isPublic;
            s.owner = owner;
            if (p1 != null && p2 != null && p1.getWorld().equals(p2.getWorld())) {
                s.areaPos1 = p1.clone();
                s.areaPos2 = p2.clone();
            }
            return s;
        }
    }

    private boolean isTrainRealmEnabled() {
        return plugin.getConfig().getBoolean("fasttravel.train_realm.enabled", true);
    }

    private boolean startTrainRealmRide(Player player, Station s, String trainType, int travelTimeSeconds) {
        if (player == null || s == null) return false;
        if (!ensureTrainRealm()) return false;

        cancelActiveRide(player.getUniqueId(), false);

        int travelTime = Math.max(1, travelTimeSeconds);
        int ticksTotal = travelTime * 20;
        double speed = getTrainSpeed(trainType);
        boolean movePlayers = plugin.getConfig().getBoolean("fasttravel.train_realm.move_players", false);
        Station originStation = findClosestStation(player.getLocation());
        int direction = resolveRideDirection(originStation, s);
        Station nextStop = findNextStation(s, direction);

        Location origin = trainRealmOrigin;
        if (origin == null || origin.getWorld() == null) return false;

        int length = (int) plugin.getConfig().getDouble("fasttravel.train_realm.car_length", 140.0);
        int width = plugin.getConfig().getInt("fasttravel.train_realm.car_width", 7);
        int height = plugin.getConfig().getInt("fasttravel.train_realm.car_height", 5);
        double originX = origin.getX();
        double originY = origin.getY();
        double originZ = origin.getZ();
        double baseZ = originZ + (width / 2.0);
        double baseY = originY + 1.0;

        TrainRide ride = new TrainRide();
        ride.original = player.getLocation().clone();
        ride.originalGameMode = player.getGameMode();
        ride.originalWalkSpeed = player.getWalkSpeed();
        ride.destination = s.getLocation().clone().add(0.5, 0, 0.5);
        ride.originX = originX;
        ride.originY = originY;
        ride.originZ = originZ;
        ride.length = length;
        ride.width = width;
        ride.height = height;
        ride.baseZ = baseZ;
        ride.baseY = baseY;
        ride.progress = 0.0;
        ride.lastBaseX = originX + 2.0;
        ride.movePlayers = movePlayers;
        ride.trainType = trainType;
        ride.direction = direction;
        ride.previousStop = originStation;
        ride.currentStop = s;
        ride.nextStop = nextStop;
        ride.remainingTicks = ticksTotal;
        ride.continueToNext = false;
        ride.promptShown = false;
        setupPanoramaScroller(ride, trainType, originX, originY, originZ, length, width);
        setupSceneryScroller(ride, trainType);

        player.setGameMode(GameMode.ADVENTURE);
        // keep movement enabled so players can walk around the car

        player.teleport(new Location(origin.getWorld(), ride.lastBaseX, baseY, baseZ, player.getLocation().getYaw(), player.getLocation().getPitch()));
        player.sendMessage(ChatColor.GRAY + "Boarding " + (trainType != null ? trainType : "Train") + "... Please wait.");

        BukkitRunnable task = new BukkitRunnable() {
            int ticks = 0;
            final int teleportInterval = Math.max(1, plugin.getConfig().getInt("fasttravel.train_realm.teleport_interval_ticks", 2));
            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    cancelActiveRide(player.getUniqueId(), true);
                    return;
                }
                if (ride.remainingTicks <= 0) {
                    if (ride.continueToNext && ride.nextStop != null) {
                        Station departed = ride.currentStop;
                        Station target = ride.nextStop;
                        if (departed != null && target != null) {
                            setRideLeg(ride, departed, target);
                            player.sendMessage(ChatColor.YELLOW + "Continuing past " + departed.getName()
                                    + ChatColor.GRAY + " -> " + ChatColor.GOLD + target.getName());
                            ticks++;
                            return;
                        }
                        ride.continueToNext = false;
                    }
                    endTrainRide(player.getUniqueId(), true);
                    cancel();
                    return;
                }
                if (shouldShowArrivalPrompt(ride)) {
                    Station current = ride.currentStop;
                    Station next = ride.nextStop;
                    int eta = Math.max(1, (int) Math.ceil(ride.remainingTicks / 20.0));
                    stationGUI.openArrivalPrompt(player,
                            current != null ? current.getId() : null,
                            current != null ? current.getName() : "Unknown",
                            next != null ? next.getId() : null,
                            next != null ? next.getName() : null,
                            eta);
                    ride.promptShown = true;
                }
                if (ride.panoramaScroll) {
                    scrollPanorama(ride);
                }
                if (ride.sceneryScroll) {
                    scrollScenery(ride);
                    applySceneryMotionBlur(player, ride, ticks);
                }
                if (ride.movePlayers) {
                    ride.progress += speed;
                    double len = Math.max(1.0, (ride.length - 4.0));
                    double baseX = ride.originX + 2.0 + (ride.progress % len);

                    if (ticks % teleportInterval == 0) {
                        Location current = player.getLocation();
                        double offsetX = current.getX() - ride.lastBaseX;
                        double offsetY = current.getY() - ride.baseY;
                        double offsetZ = current.getZ() - ride.baseZ;

                        double targetX = baseX + offsetX;
                        double targetY = ride.baseY + offsetY;
                        double targetZ = ride.baseZ + offsetZ;

                        double minX = ride.originX + 1.0;
                        double maxX = ride.originX + ride.length - 2.0;
                        double minY = ride.originY + 1.0;
                        double maxY = ride.originY + ride.height - 2.0;
                        double minZ = ride.originZ + 1.0;
                        double maxZ = ride.originZ + ride.width - 2.0;

                        targetX = Math.max(minX, Math.min(maxX, targetX));
                        targetY = Math.max(minY, Math.min(maxY, targetY));
                        targetZ = Math.max(minZ, Math.min(maxZ, targetZ));

                        ride.lastBaseX = baseX;
                        Vector velocity = player.getVelocity();
                        Location loc = new Location(origin.getWorld(), targetX, targetY, targetZ, current.getYaw(), current.getPitch());
                        player.teleport(loc);
                        player.setVelocity(velocity);
                        player.setFallDistance(0.0f);
                    }
                } else if (ticks % 20 == 0) {
                    clampPlayerToTrain(player, ride);
                }
                ride.remainingTicks--;
                ticks++;
            }
        };
        ride.task = task;
        activeRides.put(player.getUniqueId(), ride);
        task.runTaskTimer(plugin, 0L, 1L);
        return true;
    }

    private void setRideLeg(TrainRide ride, Station departed, Station target) {
        if (ride == null || departed == null || target == null) return;
        ride.previousStop = departed;
        ride.currentStop = target;
        ride.nextStop = findNextStation(target, ride.direction);
        ride.destination = target.getLocation().clone().add(0.5, 0, 0.5);
        int legTime = computeTravelTime(departed, target, ride.trainType);
        ride.remainingTicks = Math.max(20, legTime * 20);
        ride.promptShown = false;
        ride.continueToNext = false;
    }

    private boolean shouldShowArrivalPrompt(TrainRide ride) {
        if (ride == null || ride.promptShown || ride.currentStop == null) return false;
        if (!plugin.getConfig().getBoolean("fasttravel.train_realm.arrival_prompt_enabled", true)) return false;
        int seconds = Math.max(1, plugin.getConfig().getInt("fasttravel.train_realm.arrival_prompt_seconds", 8));
        int thresholdTicks = seconds * 20;
        return ride.remainingTicks <= thresholdTicks && ride.remainingTicks > 20;
    }

    private void endTrainRide(UUID uuid, boolean teleportToDestination) {
        TrainRide ride = activeRides.remove(uuid);
        if (ride == null) return;
        Player player = plugin.getServer().getPlayer(uuid);
        if (player == null) return;
        removeSeatMount(uuid);
        if (ride.task != null) {
            ride.task.cancel();
        }
        player.setWalkSpeed(ride.originalWalkSpeed);
        player.setGameMode(ride.originalGameMode);
        if (teleportToDestination && ride.destination != null) {
            player.teleport(ride.destination);
            player.sendMessage(ChatColor.GREEN + "You have arrived at your destination.");
        } else if (ride.original != null) {
            player.teleport(ride.original);
        }
    }

    private void cancelActiveRide(UUID uuid, boolean teleportBack) {
        TrainRide ride = activeRides.get(uuid);
        if (ride == null) return;
        if (ride.task != null) {
            ride.task.cancel();
        }
        if (teleportBack) {
            endTrainRide(uuid, false);
        } else {
            activeRides.remove(uuid);
        }
    }

    private double getTrainSpeed(String trainType) {
        boolean isBSB = trainType != null && trainType.equalsIgnoreCase("B.S.B.");
        double speedCum = plugin.getConfig().getDouble("fasttravel.train_realm.speed_cum", 0.35);
        double speedBsb = plugin.getConfig().getDouble("fasttravel.train_realm.speed_bsb", 0.7);
        return isBSB ? speedBsb : speedCum;
    }

    private boolean ensureTrainRealm() {
        synchronized (trainRealmLock) {
            boolean forceRebuild = plugin.getConfig().getBoolean("fasttravel.train_realm.force_rebuild", false);
            if (trainRealmBuilt && trainRealmOrigin != null && !forceRebuild) return true;
            if (forceRebuild) {
                cachedPanoramaImage = null;
            }
            String worldName = plugin.getConfig().getString("fasttravel.train_realm.world", "train_realm");
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                WorldCreator creator = new WorldCreator(worldName);
                creator.environment(World.Environment.NORMAL);
                creator.type(WorldType.FLAT);
                creator.generateStructures(false);
                world = creator.createWorld();
            }
            if (world == null) return false;
            world.setSpawnFlags(false, false);
            world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
            world.setGameRule(GameRule.DO_PATROL_SPAWNING, false);
            world.setGameRule(GameRule.DO_TRADER_SPAWNING, false);

            int baseY = plugin.getConfig().getInt("fasttravel.train_realm.base_y", 120);
            int originX = plugin.getConfig().getInt("fasttravel.train_realm.origin_x", 0);
            int originZ = plugin.getConfig().getInt("fasttravel.train_realm.origin_z", 0);
            int length = plugin.getConfig().getInt("fasttravel.train_realm.car_length", 140);
            int width = plugin.getConfig().getInt("fasttravel.train_realm.car_width", 7);
            int height = plugin.getConfig().getInt("fasttravel.train_realm.car_height", 5);
            trainRealmOrigin = new Location(world, originX, baseY, originZ);

            if (!forceRebuild && isTrainRealmBuiltMarker(world, originX, baseY, originZ)) {
                if (!isTrainRealmSceneryCurrent(world, originX, baseY, originZ)) {
                    buildScenery(world, originX, baseY, originZ, width, length);
                    placeTrainRealmSceneryMarker(world, originX, baseY, originZ);
                }
                trainRealmBuilt = true;
                return true;
            }

            buildTrainCar(world, originX, baseY, originZ, length, width, height);
            buildScenery(world, originX, baseY, originZ, width, length);

            placeTrainRealmMarker(world, originX, baseY, originZ);
            placeTrainRealmSceneryMarker(world, originX, baseY, originZ);
            trainRealmBuilt = true;
            if (forceRebuild) {
                plugin.getConfig().set("fasttravel.train_realm.force_rebuild", false);
                plugin.saveConfig();
            }
            return true;
        }
    }

    private boolean isTrainRealmWorld(World world) {
        if (world == null) return false;
        String worldName = plugin.getConfig().getString("fasttravel.train_realm.world", "train_realm");
        return world.getName().equalsIgnoreCase(worldName);
    }

    private boolean isTrainRealmBuiltMarker(World world, int x, int y, int z) {
        if (world == null) return false;
        return world.getBlockAt(x, y + 6, z).getType() == Material.SEA_LANTERN;
    }

    private void placeTrainRealmMarker(World world, int x, int y, int z) {
        if (world == null) return;
        world.getBlockAt(x, y + 6, z).setType(Material.SEA_LANTERN);
    }

    private boolean isTrainRealmSceneryCurrent(World world, int x, int y, int z) {
        if (world == null) return false;
        return world.getBlockAt(x, y + 7, z).getType() == TRAIN_SCENERY_MARKER;
    }

    private void placeTrainRealmSceneryMarker(World world, int x, int y, int z) {
        if (world == null) return;
        world.getBlockAt(x, y + 7, z).setType(TRAIN_SCENERY_MARKER);
    }

    private void buildTrainCar(World world, int ox, int oy, int oz, int length, int width, int height) {
        Material border = getConfigMaterial("fasttravel.train_realm.materials.border", Material.WAXED_COPPER_BLOCK);
        Material innerMud = getConfigMaterial("fasttravel.train_realm.materials.inner_mud", Material.MUD);
        Material innerMudstone = getConfigMaterial("fasttravel.train_realm.materials.inner_mudstone", Material.MUD_BRICKS);
        Material roof = getConfigMaterial("fasttravel.train_realm.materials.roof", Material.BLACKSTONE);
        Material floorSlabMat = getConfigMaterial("fasttravel.train_realm.materials.floor_slab", Material.BLACKSTONE_SLAB);
        Material floorPathMat = getConfigMaterial("fasttravel.train_realm.materials.floor_path_slab", Material.STONE_BRICK_SLAB);
        Material window = getConfigMaterial("fasttravel.train_realm.materials.window", Material.GLASS_PANE);
        Material stairMat = getConfigMaterial("fasttravel.train_realm.materials.corner_stairs", Material.WAXED_CUT_COPPER_STAIRS);
        Material seatMat = getConfigMaterial("fasttravel.train_realm.materials.seat_base", Material.CRIMSON_STAIRS);
        int maxX = ox + length - 1;
        int maxZ = oz + width - 1;
        int maxY = oy + height - 1;
        int centerZ = oz + (width / 2);

        for (int x = ox; x <= maxX; x++) {
            for (int z = oz; z <= maxZ; z++) {
                for (int y = oy; y <= maxY; y++) {
                    boolean isFloor = y == oy;
                    boolean isCeil = y == maxY;
                    boolean isWall = (x == ox || x == maxX || z == oz || z == maxZ);
                    if (isFloor) {
                        if (z == centerZ) {
                            setSlabTop(world, x, y, z, floorPathMat);
                        } else {
                            setSlabTop(world, x, y, z, floorSlabMat);
                        }
                    } else if (isCeil) {
                        world.getBlockAt(x, y, z).setType(roof);
                    } else if (isWall) {
                        if (isWallBorder(x, y, z, ox, oy, oz, maxX, maxY, maxZ)) {
                            world.getBlockAt(x, y, z).setType(border);
                        } else {
                            Material inner = ((x + y + z) % 2 == 0) ? innerMud : innerMudstone;
                            world.getBlockAt(x, y, z).setType(inner);
                        }
                    } else {
                        world.getBlockAt(x, y, z).setType(Material.AIR);
                    }
                }
            }
        }

        // windows on both long sides
        int windowY1 = oy + 2;
        int windowY2 = oy + 3;
        for (int x = ox + 2; x <= maxX - 2; x += 4) {
            for (int wx = x; wx <= Math.min(x + 1, maxX - 1); wx++) {
                for (int y = windowY1; y <= windowY2; y++) {
                    world.getBlockAt(wx, y, oz).setType(window);
                    world.getBlockAt(wx, y, maxZ).setType(window);
                }
            }
        }

        // back-to-back stair seats between window segments
        for (int x = ox + 1; x <= maxX - 1; x += 4) {
            if (x >= maxX) break;
            if (width >= 7) {
                placeSeat(world, seatMat, null, x, oy + 1, oz + 1, BlockFace.NORTH);
                placeSeat(world, seatMat, null, x, oy + 1, oz + 2, BlockFace.SOUTH);
                placeSeat(world, seatMat, null, x, oy + 1, maxZ - 2, BlockFace.NORTH);
                placeSeat(world, seatMat, null, x, oy + 1, maxZ - 1, BlockFace.SOUTH);
            } else {
                placeSeat(world, seatMat, null, x, oy + 1, oz + 1, BlockFace.SOUTH);
                placeSeat(world, seatMat, null, x, oy + 1, maxZ - 1, BlockFace.NORTH);
            }
        }

        // waxed cut copper stairs in the bottom two corners (inside window wall)
        placeCornerStair(world, stairMat, ox + 1, oy + 1, maxZ - 1, BlockFace.NORTH);
        placeCornerStair(world, stairMat, maxX - 1, oy + 1, maxZ - 1, BlockFace.NORTH);
    }

    private void placeSeat(World world, Material seatMat, Material backMat, int x, int y, int z, BlockFace facing) {
        if (world == null || seatMat == null) return;
        BlockData data = Bukkit.createBlockData(seatMat);
        if (data instanceof Stairs stairs) {
            stairs.setFacing(facing);
            stairs.setHalf(Stairs.Half.BOTTOM);
            world.getBlockAt(x, y, z).setBlockData(stairs, false);
        } else {
            world.getBlockAt(x, y, z).setType(seatMat);
        }
        if (backMat != null) {
            world.getBlockAt(x, y + 1, z).setType(backMat);
        }
    }

    private Material getConfigMaterial(String path, Material fallback) {
        if (path == null) return fallback;
        String raw = plugin.getConfig().getString(path, null);
        if (raw == null || raw.isEmpty()) return fallback;
        Material mat = Material.matchMaterial(raw);
        return mat != null ? mat : fallback;
    }

    private void setSlabTop(World world, int x, int y, int z, Material slabMat) {
        if (world == null || slabMat == null) return;
        if (!slabMat.name().endsWith("_SLAB")) {
            world.getBlockAt(x, y, z).setType(slabMat);
            return;
        }
        BlockData data = Bukkit.createBlockData(slabMat);
        if (data instanceof Slab slab) {
            slab.setType(Slab.Type.TOP);
            world.getBlockAt(x, y, z).setBlockData(slab, false);
        } else {
            world.getBlockAt(x, y, z).setType(slabMat);
        }
    }

    private boolean isWallBorder(int x, int y, int z, int ox, int oy, int oz, int maxX, int maxY, int maxZ) {
        boolean onZFace = (z == oz || z == maxZ);
        boolean onXFace = (x == ox || x == maxX);
        if (onZFace) {
            return y == oy + 1 || y == maxY - 1 || x == ox || x == maxX;
        }
        if (onXFace) {
            return y == oy + 1 || y == maxY - 1 || z == oz || z == maxZ;
        }
        return false;
    }

    private void placeCornerStair(World world, Material stairMat, int x, int y, int z, BlockFace face) {
        if (world == null || stairMat == null) return;
        BlockData data = Bukkit.createBlockData(stairMat);
        if (data instanceof Stairs stairs) {
            stairs.setFacing(face);
            stairs.setHalf(Stairs.Half.BOTTOM);
            world.getBlockAt(x, y, z).setBlockData(stairs, false);
        } else {
            world.getBlockAt(x, y, z).setType(stairMat);
        }
    }

    private void buildScenery(World world, int ox, int oy, int oz, int width, int length) {
        if (world == null) return;
        int positiveEdge = oz + width;
        int negativeEdge = oz - 1;
        buildScenerySide(world, ox, oy, positiveEdge, length, 1, 0);
        buildScenerySide(world, ox, oy, negativeEdge, length, -1, 197);
    }

    private void buildScenerySide(World world, int ox, int oy, int edgeZ, int length, int sideDir, int seedOffset) {
        int depth = Math.max(12, plugin.getConfig().getInt("fasttravel.train_realm.scenery.depth", 42));
        int clearHeight = Math.max(20, plugin.getConfig().getInt("fasttravel.train_realm.scenery.clear_height", 52));
        int maxRise = Math.max(6, plugin.getConfig().getInt("fasttravel.train_realm.scenery.max_rise", 16));
        int floorY = oy - 10;
        int baseY = oy - 4;

        int nearZ = edgeZ + sideDir;
        int farZ = edgeZ + (sideDir * (depth + 18));
        clearSceneryVolume(world, ox - 1, ox + length, floorY - 2, oy + clearHeight, nearZ, farZ);

        for (int x = ox; x < ox + length; x++) {
            ScenicBiome biome = biomeForColumn(x - ox, length, seedOffset);
            for (int d = 1; d <= depth; d++) {
                int z = edgeZ + (d * sideDir);
                int topY = baseY + terrainHeight(x - ox, d, depth, biome, maxRise, seedOffset);

                if (biome == ScenicBiome.ISLAND && d <= 4) {
                    fillColumn(world, x, z, floorY, baseY - 1, Material.SANDSTONE);
                    world.getBlockAt(x, baseY, z).setType(Material.WATER, false);
                    continue;
                }

                Material fill = fillMaterial(biome);
                fillColumn(world, x, z, floorY, topY - 1, fill);
                world.getBlockAt(x, topY, z).setType(topMaterial(biome, topY, oy), false);
                decorateSceneryColumn(world, x, topY, z, biome, d, depth, seedOffset);
            }
        }

        buildHorizonBackdrop(world, ox, oy, edgeZ, length, sideDir, depth, seedOffset);
        buildPanoramaScenery(world, ox, oy, edgeZ, length, sideDir, depth);
    }

    private void clearSceneryVolume(World world, int minX, int maxX, int minY, int maxY, int z1, int z2) {
        int lowZ = Math.min(z1, z2);
        int highZ = Math.max(z1, z2);
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = lowZ; z <= highZ; z++) {
                    world.getBlockAt(x, y, z).setType(Material.AIR, false);
                }
            }
        }
    }

    private void fillColumn(World world, int x, int z, int minY, int maxY, Material material) {
        if (maxY < minY) return;
        for (int y = minY; y <= maxY; y++) {
            world.getBlockAt(x, y, z).setType(material, false);
        }
    }

    private ScenicBiome biomeForColumn(int relativeX, int length, int seedOffset) {
        int segments = 6;
        int segLen = Math.max(1, length / segments);
        int idx = Math.floorMod((relativeX + seedOffset) / segLen, segments);
        return switch (idx) {
            case 0 -> ScenicBiome.ISLAND;
            case 1 -> ScenicBiome.GRASSLAND;
            case 2 -> ScenicBiome.DESERT;
            case 3 -> ScenicBiome.SNOW;
            case 4 -> ScenicBiome.MOUNTAINS;
            default -> ScenicBiome.VOLCANIC;
        };
    }

    private int terrainHeight(int relX, int d, int depth, ScenicBiome biome, int maxRise, int seedOffset) {
        double x = relX + seedOffset;
        double waveA = Math.sin(x * 0.09) * 2.0;
        double waveB = Math.cos(x * 0.23) * 1.5;
        double waveC = Math.sin((x + d) * 0.37) * 0.8;
        double distFactor = (d / (double) Math.max(1, depth));
        double rise = distFactor * (3.5 + (maxRise * 0.35));
        double raw = 1.0 + waveA + waveB + waveC + rise;

        int biomeBias = switch (biome) {
            case ISLAND -> -2;
            case GRASSLAND -> 0;
            case DESERT -> 1;
            case SNOW -> 2;
            case MOUNTAINS -> 4;
            case VOLCANIC -> 3;
        };

        int h = (int) Math.round(raw) + biomeBias;
        return Math.max(0, Math.min(maxRise, h));
    }

    private Material fillMaterial(ScenicBiome biome) {
        return switch (biome) {
            case ISLAND -> Material.SANDSTONE;
            case GRASSLAND -> Material.DIRT;
            case DESERT -> Material.SANDSTONE;
            case SNOW -> Material.STONE;
            case MOUNTAINS -> Material.STONE;
            case VOLCANIC -> Material.BLACKSTONE;
        };
    }

    private Material topMaterial(ScenicBiome biome, int topY, int oy) {
        return switch (biome) {
            case ISLAND -> Material.SAND;
            case GRASSLAND -> Material.GRASS_BLOCK;
            case DESERT -> Material.SAND;
            case SNOW -> (topY > oy + 3 ? Material.SNOW_BLOCK : Material.PACKED_ICE);
            case MOUNTAINS -> (topY > oy + 7 ? Material.SNOW_BLOCK : Material.ANDESITE);
            case VOLCANIC -> Material.BASALT;
        };
    }

    private void decorateSceneryColumn(World world, int x, int topY, int z, ScenicBiome biome, int d, int depth, int seedOffset) {
        if (d < 6 || d >= depth - 1) return;
        double noise = hashNoise(x, z, seedOffset + (biome.ordinal() * 37));

        switch (biome) {
            case GRASSLAND -> {
                if (noise > 0.992) {
                    placeSimpleTree(world, x, topY + 1, z, Material.OAK_LOG, Material.OAK_LEAVES, 3 + (Math.abs(x + z) % 2));
                } else if (noise > 0.955) {
                    world.getBlockAt(x, topY + 1, z).setType((noise > 0.975) ? Material.FERN : Material.DANDELION, false);
                }
            }
            case DESERT -> {
                if (noise > 0.986) {
                    int h = 2 + (Math.abs(x + z) % 3);
                    for (int i = 1; i <= h; i++) {
                        world.getBlockAt(x, topY + i, z).setType(Material.CACTUS, false);
                    }
                } else if (noise > 0.955) {
                    world.getBlockAt(x, topY + 1, z).setType(Material.DEAD_BUSH, false);
                }
            }
            case SNOW -> {
                if (noise > 0.988) {
                    placeSpike(world, x, topY + 1, z, Material.PACKED_ICE, 2 + (Math.abs(x * 3 + z) % 4));
                } else if (noise > 0.965) {
                    world.getBlockAt(x, topY + 1, z).setType(Material.SNOW, false);
                }
            }
            case MOUNTAINS -> {
                if (noise > 0.985) {
                    placeSpike(world, x, topY + 1, z, Material.COBBLESTONE, 2 + (Math.abs(x + z * 2) % 4));
                }
            }
            case VOLCANIC -> {
                if (noise > 0.982) {
                    placeSpike(world, x, topY + 1, z, Material.BASALT, 2 + (Math.abs(x * 5 + z) % 3));
                } else if (noise > 0.960) {
                    world.getBlockAt(x, topY + 1, z).setType(Material.MAGMA_BLOCK, false);
                }
            }
            case ISLAND -> {
                if (noise > 0.990) {
                    placeSimpleTree(world, x, topY + 1, z, Material.JUNGLE_LOG, Material.JUNGLE_LEAVES, 3);
                }
            }
        }
    }

    private void placeSimpleTree(World world, int x, int y, int z, Material trunk, Material leaves, int height) {
        for (int i = 0; i < height; i++) {
            world.getBlockAt(x, y + i, z).setType(trunk, false);
        }
        int top = y + height;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) + Math.abs(dz) > 3) continue;
                world.getBlockAt(x + dx, top, z + dz).setType(leaves, false);
                if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) {
                    world.getBlockAt(x + dx, top + 1, z + dz).setType(leaves, false);
                }
            }
        }
    }

    private void placeSpike(World world, int x, int y, int z, Material mat, int height) {
        for (int i = 0; i < height; i++) {
            world.getBlockAt(x, y + i, z).setType(mat, false);
        }
    }

    private void buildHorizonBackdrop(World world, int ox, int oy, int edgeZ, int length, int sideDir, int depth, int seedOffset) {
        int backdropDistance = depth + 10;
        int baseZ = edgeZ + (sideDir * backdropDistance);
        int floorY = oy - 8;
        for (int x = ox; x < ox + length; x++) {
            double wave = Math.sin((x + seedOffset) * 0.07) * 3.5 + Math.cos((x + seedOffset) * 0.19) * 2.0;
            int hill = Math.max(3, 7 + (int) Math.round(wave));
            for (int h = 0; h <= hill; h++) {
                int y = floorY + h;
                Material m = (h == hill && hill > 9) ? Material.SNOW_BLOCK : Material.DEEPSLATE;
                world.getBlockAt(x, y, baseZ).setType(m, false);
                world.getBlockAt(x, y, baseZ + sideDir).setType(m, false);
            }
        }
    }

    private double hashNoise(int x, int z, int seed) {
        int h = x * 73428767 ^ z * 912367 ^ seed * 19937;
        h ^= (h >>> 13);
        h *= 1274126177;
        h ^= (h >>> 16);
        return (h & 0x7fffffff) / (double) Integer.MAX_VALUE;
    }

    private boolean buildPanoramaScenery(World world, int ox, int oy, int sceneryEdgeZ, int length, int sideDir, int depth) {
        if (world == null) return false;
        boolean enabled = plugin.getConfig().getBoolean("fasttravel.train_realm.panorama.enabled", false);
        if (!enabled) return false;
        BufferedImage img = getPanoramaImage();
        if (img == null) return false;

        int panoLen = plugin.getConfig().getInt("fasttravel.train_realm.panorama.length", length);
        int panoHeight = plugin.getConfig().getInt("fasttravel.train_realm.panorama.height", 40);
        int zOffset = plugin.getConfig().getInt("fasttravel.train_realm.panorama.z_offset", 8);
        int yOffset = plugin.getConfig().getInt("fasttravel.train_realm.panorama.y_offset", 2);
        boolean scale = plugin.getConfig().getBoolean("fasttravel.train_realm.panorama.scale_to_length", true);

        int imgW = Math.max(1, img.getWidth());
        int imgH = Math.max(1, img.getHeight());
        int panoZ = sceneryEdgeZ + (sideDir * (depth + zOffset));
        int baseY = oy + yOffset;

        for (int x = 0; x < panoLen; x++) {
            int srcX;
            if (scale) {
                srcX = (int) Math.floor((x / (double) panoLen) * imgW);
                if (srcX >= imgW) srcX = imgW - 1;
            } else {
                srcX = x % imgW;
            }
            for (int y = 0; y < panoHeight; y++) {
                int srcY = (int) Math.floor((y / (double) panoHeight) * imgH);
                if (srcY >= imgH) srcY = imgH - 1;
                int rgb = img.getRGB(srcX, srcY);
                Material mat = closestMapMaterial(new Color(rgb, true));
                world.getBlockAt(ox + x, baseY + y, panoZ).setType(mat);
            }
        }
        return true;
    }

    private Material closestMapMaterial(Color color) {
        if (color == null) return Material.BLUE_CONCRETE;
        int r = color.getRed();
        int g = color.getGreen();
        int b = color.getBlue();
        PaletteColor best = null;
        int bestDist = Integer.MAX_VALUE;
        for (PaletteColor pc : PALETTE) {
            int dr = r - pc.r;
            int dg = g - pc.g;
            int db = b - pc.b;
            int dist = (dr * dr) + (dg * dg) + (db * db);
            if (dist < bestDist) {
                bestDist = dist;
                best = pc;
            }
        }
        return best != null ? best.material : Material.BLUE_CONCRETE;
    }

    private static class PaletteColor {
        private final Material material;
        private final int r;
        private final int g;
        private final int b;
        private PaletteColor(Material material, int r, int g, int b) {
            this.material = material;
            this.r = r;
            this.g = g;
            this.b = b;
        }
    }

    private static final List<PaletteColor> PALETTE = Arrays.asList(
            new PaletteColor(Material.WHITE_CONCRETE, 235, 236, 237),
            new PaletteColor(Material.LIGHT_GRAY_CONCRETE, 162, 168, 171),
            new PaletteColor(Material.GRAY_CONCRETE, 78, 82, 84),
            new PaletteColor(Material.BLACK_CONCRETE, 8, 10, 15),
            new PaletteColor(Material.BROWN_CONCRETE, 96, 59, 31),
            new PaletteColor(Material.RED_CONCRETE, 142, 33, 33),
            new PaletteColor(Material.ORANGE_CONCRETE, 224, 97, 0),
            new PaletteColor(Material.YELLOW_CONCRETE, 241, 175, 21),
            new PaletteColor(Material.LIME_CONCRETE, 94, 169, 24),
            new PaletteColor(Material.GREEN_CONCRETE, 73, 91, 36),
            new PaletteColor(Material.CYAN_CONCRETE, 21, 119, 136),
            new PaletteColor(Material.LIGHT_BLUE_CONCRETE, 36, 137, 199),
            new PaletteColor(Material.BLUE_CONCRETE, 44, 46, 143),
            new PaletteColor(Material.PURPLE_CONCRETE, 100, 32, 156),
            new PaletteColor(Material.MAGENTA_CONCRETE, 169, 48, 159),
            new PaletteColor(Material.PINK_CONCRETE, 214, 101, 143),
            new PaletteColor(Material.TERRACOTTA, 152, 94, 67),
            new PaletteColor(Material.RED_TERRACOTTA, 143, 61, 46),
            new PaletteColor(Material.ORANGE_TERRACOTTA, 161, 83, 37),
            new PaletteColor(Material.YELLOW_TERRACOTTA, 186, 133, 35),
            new PaletteColor(Material.GREEN_TERRACOTTA, 76, 83, 42),
            new PaletteColor(Material.BROWN_TERRACOTTA, 80, 50, 31),
            new PaletteColor(Material.BLUE_TERRACOTTA, 74, 59, 91),
            new PaletteColor(Material.LIGHT_BLUE_TERRACOTTA, 113, 108, 137),
            new PaletteColor(Material.BLACK_TERRACOTTA, 37, 22, 16)
    );

    private static class TrainRide {
        private Location original;
        private Location destination;
        private BukkitRunnable task;
        private GameMode originalGameMode = GameMode.SURVIVAL;
        private float originalWalkSpeed = 0.2f;
        private boolean movePlayers = false;
        private double originX;
        private double originY;
        private double originZ;
        private int length;
        private int width;
        private int height;
        private double baseZ;
        private double baseY;
        private double progress;
        private double lastBaseX;
        private boolean panoramaScroll = false;
        private BufferedImage panoramaImage;
        private int panoramaImageWidth;
        private int panoramaImageHeight;
        private int panoramaLength;
        private int panoramaHeight;
        private int panoramaBaseX;
        private int panoramaBaseY;
        private int panoramaZ;
        private int panoramaColumn;
        private int panoramaSrcX;
        private double panoramaScrollPerTick;
        private double panoramaRemainder;
        private boolean sceneryScroll = false;
        private int sceneryDepth;
        private int sceneryClearHeight;
        private int sceneryMaxRise;
        private int scenerySegmentLength;
        private int sceneryTickInterval;
        private int sceneryTickCounter;
        private double sceneryScrollPerTick;
        private double sceneryRemainder;
        private int sceneryFloorY;
        private int sceneryBaseY;
        private int sceneryPositiveEdgeZ;
        private int sceneryNegativeEdgeZ;
        private int sceneryColumn;
        private long sceneryStreamIndex;
        private int sceneryPositiveSeedOffset;
        private int sceneryNegativeSeedOffset;
        private String trainType;
        private int direction = 1;
        private Station previousStop;
        private Station currentStop;
        private Station nextStop;
        private int remainingTicks;
        private boolean promptShown;
        private boolean continueToNext;
    }

    private static class TrainSchedule {
        private final String trainType;
        private final int direction;
        private Station current;
        private Station next;
        private int remainingSeconds;
        private boolean offsetApplied;

        private TrainSchedule(String trainType, int direction) {
            this.trainType = trainType;
            this.direction = direction >= 0 ? 1 : -1;
        }
    }
}
