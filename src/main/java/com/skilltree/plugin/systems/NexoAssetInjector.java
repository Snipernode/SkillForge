package com.skilltree.plugin.systems;

import com.skilltree.plugin.SkillForgePlugin;
import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.plugin.Plugin;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

public class NexoAssetInjector {
    private static final String BUNDLE_PREFIX = "nexo/";
    private static final byte[] TRANSPARENT_HEART_PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAABAAAAAPCAYAAADtc08vAAAAEUlEQVR42mNgGAWjYBQMEwAAA88AAYyydMsAAAAASUVORK5CYII="
    );
    private final SkillForgePlugin plugin;

    public NexoAssetInjector(SkillForgePlugin plugin) {
        this.plugin = plugin;
    }

    public void injectBundledAssets(boolean logWhenSkipped) {
        if (!plugin.getConfig().getBoolean("nexo.inject.enabled", true)) {
            if (logWhenSkipped) {
                plugin.getLogger().info("Nexo asset injection disabled via config (nexo.inject.enabled=false).");
            }
            return;
        }

        Plugin nexo = Bukkit.getPluginManager().getPlugin("Nexo");
        if (nexo == null || !nexo.isEnabled()) {
            if (logWhenSkipped) {
                plugin.getLogger().info("Nexo not detected; skipping SkillForge Nexo asset injection.");
            }
            return;
        }

        boolean overwrite = plugin.getConfig().getBoolean("nexo.inject.overwrite_existing", true);
        File nexoData = nexo.getDataFolder();
        if (!nexoData.exists() && !nexoData.mkdirs()) {
            plugin.getLogger().warning("Failed to create Nexo data folder at " + nexoData.getAbsolutePath());
            return;
        }

        CopyStats stats = copyBundledTree(nexoData.toPath(), overwrite);
        importOptionalExternalTextureOverrides(nexoData.toPath(), overwrite, stats);
        applyConfiguredVanillaHeartMapping(nexoData.toPath(), overwrite, stats);
        mirrorVanillaHeartOverridesForNexoCompat(nexoData.toPath(), overwrite, stats);
        applyTransparentVanillaHeartsIfEnabled(nexoData.toPath(), overwrite, stats);
        // Always remove generic chest overrides to avoid affecting unrelated GUIs.
        // Vanilla chest background overrides are global per container type and cannot
        // be scoped to only one command/menu.
        if (plugin.getConfig().getBoolean("gui.bind.background_override.enabled", false)) {
            plugin.getLogger().warning("Ignoring gui.bind.background_override.enabled=true because generic chest overrides are global. Leaving it disabled.");
        }
        removeBindGuiBackgroundOverrideFiles(nexoData.toPath(), stats);
        plugin.getLogger().info("Nexo asset injection complete: copied=" + stats.copied + ", skipped=" + stats.skipped + ", removed=" + stats.removed + ".");

        if (stats.copied > 0 && plugin.getConfig().getBoolean("nexo.inject.auto_reload_command", false)) {
            ConsoleCommandSender console = Bukkit.getConsoleSender();
            Bukkit.dispatchCommand(console, "nexo reload all");
            plugin.getLogger().info("Executed '/nexo reload all' after asset injection.");
        }
    }

    private CopyStats copyBundledTree(Path targetRoot, boolean overwrite) {
        try {
            URI location = plugin.getClass().getProtectionDomain().getCodeSource().getLocation().toURI();
            Path runtimePath = Path.of(location);
            if (Files.isRegularFile(runtimePath)) {
                return copyFromJar(runtimePath, targetRoot, overwrite);
            }
            return copyFromFilesystem(targetRoot, overwrite);
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to resolve plugin runtime location for Nexo injection: " + ex.getMessage());
            return new CopyStats();
        }
    }

    private CopyStats copyFromJar(Path jarPath, Path targetRoot, boolean overwrite) {
        CopyStats stats = new CopyStats();
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory()) continue;
                String name = entry.getName();
                if (!name.startsWith(BUNDLE_PREFIX)) continue;

                String relative = name.substring(BUNDLE_PREFIX.length());
                if (!(relative.startsWith("items/") || relative.startsWith("pack/"))) continue;

                Path target = targetRoot.resolve(relative).normalize();
                if (!target.startsWith(targetRoot)) continue;

                if (Files.exists(target) && !overwrite) {
                    stats.skipped++;
                    continue;
                }

                Files.createDirectories(target.getParent());
                try (InputStream in = jar.getInputStream(entry)) {
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                    stats.copied++;
                }
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed copying Nexo assets from jar: " + ex.getMessage());
        }
        return stats;
    }

    private CopyStats copyFromFilesystem(Path targetRoot, boolean overwrite) {
        CopyStats stats = new CopyStats();
        Path sourceRoot = Path.of("nexo");
        if (!Files.isDirectory(sourceRoot)) {
            plugin.getLogger().warning("Nexo source folder not found in development mode at " + sourceRoot.toAbsolutePath());
            return stats;
        }

        try (Stream<Path> walk = Files.walk(sourceRoot)) {
            walk.filter(Files::isRegularFile).forEach(path -> {
                try {
                    Path rel = sourceRoot.relativize(path);
                    if (rel.getNameCount() == 0) return;
                    String root = rel.getName(0).toString();
                    if (!"items".equals(root) && !"pack".equals(root)) return;

                    Path target = targetRoot.resolve(rel).normalize();
                    if (!target.startsWith(targetRoot)) return;

                    if (Files.exists(target) && !overwrite) {
                        stats.skipped++;
                        return;
                    }

                    Files.createDirectories(target.getParent());
                    Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING);
                    stats.copied++;
                } catch (Exception ex) {
                    plugin.getLogger().warning("Failed copying Nexo file " + path + ": " + ex.getMessage());
                }
            });
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed walking local Nexo asset folder: " + ex.getMessage());
        }
        return stats;
    }

    private static class CopyStats {
        int copied;
        int skipped;
        int removed;
    }

    private void importOptionalExternalTextureOverrides(Path nexoRoot, boolean overwrite, CopyStats stats) {
        // Optional development override: if these files exist in New Imports/, they replace bundled defaults.
        // NOTE: Questbook GUI textures are intentionally not injected anymore.
        Path[] crateCandidates = new Path[]{
                Path.of("New Imports/crates sprite sheet.png"),
                Path.of("New Imports/Crates Sprite Sheet.png"),
                Path.of("New Imports/crate sprite sheet.png"),
                Path.of("New Imports/Crate sprite sheet.png"),
                Path.of("New Imports/Crate Sprite Sheet.png")
        };
        for (Path source : crateCandidates) {
            if (!Files.exists(source)) continue;
            copyOptionalExternal(
                    source,
                    nexoRoot.resolve("pack/assets/skillforge/textures/gui/crates_sprite_sheet.png").normalize(),
                    overwrite,
                    stats
            );
            break;
        }
    }

    private void copyOptionalExternal(Path source, Path target, boolean overwrite, CopyStats stats) {
        try {
            if (source == null || target == null || !Files.exists(source) || !Files.isRegularFile(source)) return;
            if (Files.exists(target) && !overwrite) {
                stats.skipped++;
                return;
            }
            Path parent = target.getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            stats.copied++;
            plugin.getLogger().info("Applied local texture override: " + source + " -> " + target.getFileName());
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed local texture override from " + source + ": " + ex.getMessage());
        }
    }

    private void applyBindGuiBackgroundOverride(Path nexoRoot, boolean overwrite, CopyStats stats) {
        if (!plugin.getConfig().getBoolean("gui.bind.background_override.enabled", true)) return;

        String relativeSource = plugin.getConfig().getString(
                "gui.bind.background_override.source",
                "pack/assets/skillforge/textures/gui/bind_background_generic54.png"
        );
        if (relativeSource == null || relativeSource.trim().isEmpty()) return;
        String fitMode = plugin.getConfig().getString("gui.bind.background_override.fit_mode", "COVER");

        Path source = nexoRoot.resolve(relativeSource).normalize();
        if (!source.startsWith(nexoRoot) || !Files.exists(source)) {
            plugin.getLogger().warning("Bind GUI background override source missing: " + source);
            return;
        }

        BufferedImage sourceImage;
        try (InputStream in = Files.newInputStream(source)) {
            sourceImage = ImageIO.read(in);
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to read bind GUI source image: " + source + " (" + ex.getMessage() + ")");
            return;
        }
        if (sourceImage == null) {
            plugin.getLogger().warning("Failed to decode bind GUI source image: " + source);
            return;
        }

        // NOTE: Vanilla can only override by container type (generic_54), not by command/title.
        // This affects every 54-slot chest-like GUI using this texture.
        Path[] targets = new Path[] {
                nexoRoot.resolve("pack/assets/minecraft/textures/gui/container/generic_54.png").normalize(),
                nexoRoot.resolve("pack/textures/gui/container/generic_54.png").normalize(),
                nexoRoot.resolve("pack/assets/minecraft/textures/gui/sprites/container/generic_54.png").normalize(),
                nexoRoot.resolve("pack/textures/gui/sprites/container/generic_54.png").normalize(),
                nexoRoot.resolve("pack/external_packs/zz_skillforge_override/assets/minecraft/textures/gui/container/generic_54.png").normalize(),
                nexoRoot.resolve("pack/external_packs/zz_skillforge_override/assets/minecraft/textures/gui/sprites/container/generic_54.png").normalize()
        };

        for (Path target : targets) {
            int[] size = target.toString().contains("/sprites/container/")
                    ? new int[] {176, 222}
                    : new int[] {256, 256};
            BufferedImage adjusted = fitImage(sourceImage, size[0], size[1], fitMode);
            if (plugin.getConfig().getBoolean("gui.bind.background_override.keep_player_inventory_visible", true)) {
                double startRatio = plugin.getConfig().getDouble("gui.bind.background_override.player_inventory_start_ratio", 0.57);
                String lowerMode = plugin.getConfig().getString("gui.bind.background_override.player_inventory_mode", "VANILLA_PANEL");
                if (lowerMode != null && lowerMode.trim().equalsIgnoreCase("TRANSPARENT")) {
                    adjusted = maskLowerAreaTransparent(adjusted, startRatio);
                } else {
                    adjusted = renderVanillaLikeLowerPanel(adjusted, startRatio);
                }
            }
            try {
                Path parent = target.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                if (Files.exists(target) && !overwrite) {
                    stats.skipped++;
                    continue;
                }
                try (OutputStream out = Files.newOutputStream(target)) {
                    ImageIO.write(adjusted, "png", out);
                }
                stats.copied++;
            } catch (Exception ex) {
                plugin.getLogger().warning("Failed writing bind GUI background override to " + target + ": " + ex.getMessage());
            }
        }
    }

    private BufferedImage maskLowerAreaTransparent(BufferedImage source, double startRatio) {
        if (source == null) return null;
        int w = source.getWidth();
        int h = source.getHeight();
        int startY = (int) Math.floor(Math.max(0.0, Math.min(1.0, startRatio)) * h);
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            g.drawImage(source, 0, 0, null);
            g.setComposite(AlphaComposite.Clear);
            g.fillRect(0, startY, w, h - startY);
        } finally {
            g.dispose();
        }
        return out;
    }

    private BufferedImage renderVanillaLikeLowerPanel(BufferedImage source, double startRatio) {
        if (source == null) return null;
        int w = source.getWidth();
        int h = source.getHeight();
        int startY = (int) Math.floor(Math.max(0.0, Math.min(1.0, startRatio)) * h);
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            g.drawImage(source, 0, 0, null);

            // Base lower panel color (vanilla-like inventory background).
            g.setColor(new Color(198, 198, 198, 255));
            g.fillRect(0, startY, w, h - startY);

            // Panel border lines for definition.
            g.setColor(new Color(60, 60, 60, 255));
            g.drawLine(0, startY, w - 1, startY);
            g.drawLine(0, h - 1, w - 1, h - 1);

            // Draw player inventory slots in vanilla-like positions.
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 9; col++) {
                    int x = sx(w, 8 + col * 18);
                    int y = sy(h, 139 + row * 18);
                    drawVanillaSlot(g, x, y, slotSize(w, h));
                }
            }
            for (int col = 0; col < 9; col++) {
                int x = sx(w, 8 + col * 18);
                int y = sy(h, 197);
                drawVanillaSlot(g, x, y, slotSize(w, h));
            }
        } finally {
            g.dispose();
        }
        return out;
    }

    private void drawVanillaSlot(Graphics2D g, int x, int y, int size) {
        int s = Math.max(10, size);
        // Outer dark frame.
        g.setColor(new Color(60, 60, 60, 255));
        g.fillRect(x, y, s, s);
        // Inner fill.
        g.setColor(new Color(139, 139, 139, 255));
        g.fillRect(x + 1, y + 1, s - 2, s - 2);
        // Top-left highlight.
        g.setColor(new Color(255, 255, 255, 180));
        g.drawLine(x + 1, y + 1, x + s - 2, y + 1);
        g.drawLine(x + 1, y + 1, x + 1, y + s - 2);
        // Bottom-right shadow.
        g.setColor(new Color(50, 50, 50, 200));
        g.drawLine(x + s - 2, y + 1, x + s - 2, y + s - 2);
        g.drawLine(x + 1, y + s - 2, x + s - 2, y + s - 2);
    }

    private int sx(int width, int vanillaX) {
        return (int) Math.round(vanillaX * (width / 176.0));
    }

    private int sy(int height, int vanillaY) {
        return (int) Math.round(vanillaY * (height / 222.0));
    }

    private int slotSize(int width, int height) {
        int sx = (int) Math.round(18 * (width / 176.0));
        int sy = (int) Math.round(18 * (height / 222.0));
        return Math.max(10, Math.min(sx, sy));
    }

    private void removeBindGuiBackgroundOverrideFiles(Path nexoRoot, CopyStats stats) {
        Path[] targets = new Path[] {
                nexoRoot.resolve("pack/assets/minecraft/textures/gui/container/generic_54.png").normalize(),
                nexoRoot.resolve("pack/textures/gui/container/generic_54.png").normalize(),
                nexoRoot.resolve("pack/assets/minecraft/textures/gui/sprites/container/generic_54.png").normalize(),
                nexoRoot.resolve("pack/textures/gui/sprites/container/generic_54.png").normalize(),
                nexoRoot.resolve("pack/external_packs/zz_skillforge_override/assets/minecraft/textures/gui/container/generic_54.png").normalize(),
                nexoRoot.resolve("pack/external_packs/zz_skillforge_override/assets/minecraft/textures/gui/sprites/container/generic_54.png").normalize()
        };

        for (Path target : targets) {
            if (!target.startsWith(nexoRoot)) continue;
            try {
                if (Files.deleteIfExists(target)) {
                    stats.removed++;
                }
            } catch (Exception ex) {
                plugin.getLogger().warning("Failed removing bind GUI override file " + target + ": " + ex.getMessage());
            }
        }
    }

    private void applyTransparentVanillaHeartsIfEnabled(Path nexoRoot, boolean overwrite, CopyStats stats) {
        boolean hideVanillaHearts = plugin.getConfig().getBoolean("hud.custom_health.hide_vanilla_hearts", false);
        if (!hideVanillaHearts) return;

        String[] names = new String[]{
                "absorbing_full.png", "absorbing_half.png",
                "blinking_full.png", "blinking_half.png",
                "container.png", "empty.png",
                "frozen_full.png", "frozen_half.png",
                "full.png", "half.png",
                "hardcore_blinking_full.png", "hardcore_blinking_half.png",
                "hardcore_full.png", "hardcore_half.png",
                "poisoned_full.png", "poisoned_half.png",
                "withered_full.png", "withered_half.png"
        };

        Path[] roots = new Path[]{
                nexoRoot.resolve("pack/assets/minecraft/textures/gui/sprites/hud/heart").normalize(),
                nexoRoot.resolve("pack/textures/gui/sprites/hud/heart").normalize(),
                // Force-last override for Nexo builds that prioritize merged external packs/default packs.
                nexoRoot.resolve("pack/external_packs/zz_skillforge_override/assets/minecraft/textures/gui/sprites/hud/heart").normalize()
        };

        for (Path root : roots) {
            try {
                Files.createDirectories(root);
            } catch (Exception ex) {
                plugin.getLogger().warning("Failed creating vanilla heart folder " + root + ": " + ex.getMessage());
                continue;
            }

            for (String name : names) {
                Path target = root.resolve(name).normalize();
                if (!target.startsWith(root)) continue;
                try {
                    if (Files.exists(target) && !overwrite) {
                        stats.skipped++;
                        continue;
                    }
                    Files.write(target, TRANSPARENT_HEART_PNG);
                    stats.copied++;
                } catch (Exception ex) {
                    plugin.getLogger().warning("Failed writing transparent heart " + target.getFileName() + ": " + ex.getMessage());
                }
            }
        }
    }

    /**
     * Some Nexo builds source texture files from pack/textures/* instead of pack/assets/*.
     * Mirror vanilla HUD heart overrides so both layouts are covered.
     */
    private void mirrorVanillaHeartOverridesForNexoCompat(Path nexoRoot, boolean overwrite, CopyStats stats) {
        Path src = nexoRoot.resolve("pack/assets/minecraft/textures/gui/sprites/hud/heart").normalize();
        Path dst = nexoRoot.resolve("pack/textures/gui/sprites/hud/heart").normalize();

        if (!Files.isDirectory(src)) return;
        try {
            Files.createDirectories(dst);
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to create Nexo compat heart folder: " + ex.getMessage());
            return;
        }

        try (Stream<Path> walk = Files.list(src)) {
            walk.filter(Files::isRegularFile).forEach(file -> {
                try {
                    Path target = dst.resolve(file.getFileName().toString()).normalize();
                    if (!target.startsWith(dst)) return;
                    if (Files.exists(target) && !overwrite) {
                        stats.skipped++;
                        return;
                    }
                    Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
                    stats.copied++;
                } catch (Exception ex) {
                    plugin.getLogger().warning("Failed to mirror Nexo heart texture " + file.getFileName() + ": " + ex.getMessage());
                }
            });
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to mirror Nexo heart overrides: " + ex.getMessage());
        }
    }

    private void applyConfiguredVanillaHeartMapping(Path nexoRoot, boolean overwrite, CopyStats stats) {
        if (!plugin.getConfig().getBoolean("hud.vanilla_heart_override.enabled", true)) return;

        double mcHeartsPerGuiHeart = plugin.getConfig().getDouble(
                "hud.vanilla_heart_override.mc_hearts_per_gui_heart",
                plugin.getConfig().getDouble("hud.custom_health.mc_hearts_per_gui_heart", 2.0)
        );
        if (mcHeartsPerGuiHeart <= 0.0) mcHeartsPerGuiHeart = 2.0;

        // Vanilla hearts only support full/half/empty frames.
        // 2.0 ratio: full->full, half->half (4 HP / heart slot).
        // 4.0 ratio: full->quarter, half->empty (legacy heavy compression).
        String mappedFull = mcHeartsPerGuiHeart >= 3.5 ? "heart_quarter.png" : "heart_full.png";
        String mappedHalf = mcHeartsPerGuiHeart >= 3.5 ? "heart_empty.png" : "heart_half.png";
        String mappedEmpty = "heart_empty.png";

        Path heartSourceRoot = nexoRoot.resolve("pack/assets/skillforge/textures/hud").normalize();
        byte[] fullBytes = readBytes(heartSourceRoot.resolve(mappedFull));
        byte[] halfBytes = readBytes(heartSourceRoot.resolve(mappedHalf));
        byte[] emptyBytes = readBytes(heartSourceRoot.resolve(mappedEmpty));
        if (fullBytes == null || halfBytes == null || emptyBytes == null) {
            plugin.getLogger().warning("Skipped vanilla heart mapping: missing source heart textures under " + heartSourceRoot);
            return;
        }

        Map<String, byte[]> mapped = new LinkedHashMap<>();
        mapped.put("full.png", fullBytes);
        mapped.put("blinking_full.png", fullBytes);
        mapped.put("hardcore_full.png", fullBytes);
        mapped.put("hardcore_blinking_full.png", fullBytes);
        mapped.put("absorbing_full.png", fullBytes);
        mapped.put("frozen_full.png", fullBytes);
        mapped.put("poisoned_full.png", fullBytes);
        mapped.put("withered_full.png", fullBytes);

        mapped.put("half.png", halfBytes);
        mapped.put("blinking_half.png", halfBytes);
        mapped.put("hardcore_half.png", halfBytes);
        mapped.put("hardcore_blinking_half.png", halfBytes);
        mapped.put("absorbing_half.png", halfBytes);
        mapped.put("frozen_half.png", halfBytes);
        mapped.put("poisoned_half.png", halfBytes);
        mapped.put("withered_half.png", halfBytes);

        mapped.put("empty.png", emptyBytes);
        mapped.put("container.png", emptyBytes);

        Path[] roots = new Path[]{
                nexoRoot.resolve("pack/assets/minecraft/textures/gui/sprites/hud/heart").normalize(),
                nexoRoot.resolve("pack/textures/gui/sprites/hud/heart").normalize(),
                nexoRoot.resolve("pack/external_packs/zz_skillforge_override/assets/minecraft/textures/gui/sprites/hud/heart").normalize()
        };

        for (Path root : roots) {
            try {
                Files.createDirectories(root);
            } catch (Exception ex) {
                plugin.getLogger().warning("Failed creating vanilla heart folder " + root + ": " + ex.getMessage());
                continue;
            }

            for (Map.Entry<String, byte[]> entry : mapped.entrySet()) {
                Path target = root.resolve(entry.getKey()).normalize();
                if (!target.startsWith(root)) continue;
                try {
                    if (Files.exists(target) && !overwrite) {
                        stats.skipped++;
                        continue;
                    }
                    Files.write(target, entry.getValue());
                    stats.copied++;
                } catch (Exception ex) {
                    plugin.getLogger().warning("Failed writing mapped heart " + target.getFileName() + ": " + ex.getMessage());
                }
            }
        }
    }

    private byte[] readBytes(Path path) {
        try {
            if (path == null || !Files.exists(path)) return null;
            return Files.readAllBytes(path);
        } catch (Exception ignored) {
            return null;
        }
    }

    private BufferedImage fitImage(BufferedImage source, int width, int height, String modeRaw) {
        if (source.getWidth() == width && source.getHeight() == height) {
            return source;
        }

        String mode = modeRaw == null ? "COVER" : modeRaw.trim().toUpperCase();
        if (mode.equals("STRETCH")) {
            BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = output.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
                g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g.drawImage(source, 0, 0, width, height, null);
            } finally {
                g.dispose();
            }
            return output;
        }
        boolean contain = mode.equals("CONTAIN");

        BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = output.createGraphics();
        try {
            g.setComposite(AlphaComposite.Clear);
            g.fillRect(0, 0, width, height);
            g.setComposite(AlphaComposite.SrcOver);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            double scaleContain = Math.min(width / (double) source.getWidth(), height / (double) source.getHeight());
            double scaleCover = Math.max(width / (double) source.getWidth(), height / (double) source.getHeight());
            double scale = contain ? scaleContain : scaleCover;
            int drawW = Math.max(1, (int) Math.round(source.getWidth() * scale));
            int drawH = Math.max(1, (int) Math.round(source.getHeight() * scale));
            int drawX = (width - drawW) / 2;
            int drawY = (height - drawH) / 2;
            g.drawImage(source, drawX, drawY, drawW, drawH, null);
        } finally {
            g.dispose();
        }
        return output;
    }
}
