package com.skilltree.plugin.utils;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class ProtectionUtils {

    /**
     * Checks if a player is allowed to build/pvp/destroy blocks at a specific location.
     * 
     * @param player The player performing the action
     * @param loc The location where the action is happening
     * @return true if the area is protected (action DENIED), false otherwise
     */
    public static boolean isProtectedArea(Player player, Location loc) {
        // 1. Get the WorldGuard plugin via Bukkit's PluginManager
        Plugin wgPlugin = Bukkit.getServer().getPluginManager().getPlugin("WorldGuard");
        
        // If WorldGuard is not loaded, assume it's not protected
        if (wgPlugin == null || !(wgPlugin instanceof WorldGuardPlugin)) {
            return false;
        }

        try {
            // 2. Cast to WorldGuardPlugin to access the wrapPlayer method
            WorldGuardPlugin wg = (WorldGuardPlugin) wgPlugin;
            
            // 3. Query the regions at the specific location
            ApplicableRegionSet set = WorldGuard.getInstance().getPlatform().getRegionContainer()
                    .createQuery()
                    .getApplicableRegions(BukkitAdapter.adapt(loc));

            // 4. Check the BUILD flag state for the wrapped player
            StateFlag.State state = set.queryState(wg.wrapPlayer(player), Flags.BUILD);
            
            // 5. Return true if the state is explicitly DENY
            return state == StateFlag.State.DENY;
            
        } catch (Exception e) {
            e.printStackTrace();
            // Fallback: If WorldGuard fails, assume it's not protected to prevent breaking gameplay
            return false;
        }
    }
}
