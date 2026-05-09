package com.moonlight.coreprotect.structures;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.util.SmallCaps;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sistema de pesca con estanques configurables.
 * Los estanques se configuran con /wardstone setuppesca y ofrecen
 * loot especial basado en probabilidad.
 */
public class FishingManager implements Listener {

    private final CoreProtectPlugin plugin;
    private final Random random = new Random();
    private final List<FishingPond> ponds = new ArrayList<>();
    private final Set<UUID> discoveredPonds = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> fishCooldown = new HashMap<>();
    private static final long FISH_COOLDOWN_MS = 1000L; // 1 second between catches
    private File dataFile;
    private FileConfiguration dataConfig;

    // Loot categories with probabilities (must sum to 1.0)
    private static final double CHANCE_TRASH = 0.30;      // 30% - basura
    private static final double CHANCE_COMMON = 0.30;      // 30% - común
    private static final double CHANCE_FISH = 0.20;        // 20% - peces buenos
    private static final double CHANCE_RARE = 0.12;        // 12% - raro
    private static final double CHANCE_KEY = 0.05;         // 5%  - llaves
    private static final double CHANCE_ESSENCE = 0.025;    // 2.5% - esencias
    private static final double CHANCE_LEGENDARY = 0.005;  // 0.5% - legendario

    public static class FishingPond {
        public final String name;
        public final String world;
        public final int x1, y1, z1, x2, y2, z2;

        public FishingPond(String name, String world, int x1, int y1, int z1, int x2, int y2, int z2) {
            this.name = name;
            this.world = world;
            this.x1 = Math.min(x1, x2);
            this.y1 = Math.min(y1, y2);
            this.z1 = Math.min(z1, z2);
            this.x2 = Math.max(x1, x2);
            this.y2 = Math.max(y1, y2);
            this.z2 = Math.max(z1, z2);
        }

        public boolean contains(Location loc) {
            if (!loc.getWorld().getName().equals(world)) return false;
            int x = loc.getBlockX(), y = loc.getBlockY(), z = loc.getBlockZ();
            return x >= x1 && x <= x2 && y >= y1 && y <= y2 && z >= z1 && z <= z2;
        }

        public boolean isNear(Location loc, double dist) {
            if (!loc.getWorld().getName().equals(world)) return false;
            double cx = (x1 + x2) / 2.0, cy = (y1 + y2) / 2.0, cz = (z1 + z2) / 2.0;
            return loc.distanceSquared(new Location(loc.getWorld(), cx, cy, cz)) < dist * dist;
        }
    }

    public FishingManager(CoreProtectPlugin plugin) {
        this.plugin = plugin;
        loadData();
        startDiscoveryTask();
    }

    // ==================== POND MANAGEMENT ====================

    public void addPond(String name, String world, int x1, int y1, int z1, int x2, int y2, int z2) {
        ponds.add(new FishingPond(name, world, x1, y1, z1, x2, y2, z2));
        saveData();
    }

    public FishingPond getPondAt(Location loc) {
        for (FishingPond pond : ponds) {
            if (pond.contains(loc)) return pond;
        }
        return null;
    }

    public List<FishingPond> getPonds() {
        return Collections.unmodifiableList(ponds);
    }

    // ==================== FISHING EVENT ====================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;

        Player player = event.getPlayer();
        UUID uid = player.getUniqueId();
        Location hookLoc = event.getHook().getLocation();

        // Check if fishing in a pond
        FishingPond pond = getPondAt(hookLoc);
        if (pond == null) return;

        // Anti-spam: cooldown de 1 segundo entre pescas
        long now = System.currentTimeMillis();
        Long lastCatch = fishCooldown.get(uid);
        if (lastCatch != null && now - lastCatch < FISH_COOLDOWN_MS) {
            // Cancel silently — spam click protection
            if (event.getCaught() instanceof Item caughtItem) {
                caughtItem.remove();
            }
            event.setCancelled(true);
            return;
        }
        fishCooldown.put(uid, now);

        // Cancel default catch and give custom loot
        if (event.getCaught() instanceof Item caughtItem) {
            caughtItem.remove();
        }
        event.setCancelled(true);

        // Generate custom loot
        ItemStack loot = generateLoot(player);
        if (loot != null) {
            // Drop the item at the hook location, flying towards the player
            Item dropped = player.getWorld().dropItem(hookLoc, loot);
            org.bukkit.util.Vector dir = player.getLocation().toVector().subtract(hookLoc.toVector()).normalize().multiply(0.3);
            dropped.setVelocity(dir.add(new org.bukkit.util.Vector(0, 0.2, 0)));

            // Give XP
            player.giveExp(3 + random.nextInt(8));
        }
    }

    private ItemStack generateLoot(Player player) {
        double roll = random.nextDouble();
        double cumulative = 0;

        // Trash (30%)
        cumulative += CHANCE_TRASH;
        if (roll < cumulative) return generateTrash(player);

        // Common (30%)
        cumulative += CHANCE_COMMON;
        if (roll < cumulative) return generateCommon(player);

        // Fish (20%)
        cumulative += CHANCE_FISH;
        if (roll < cumulative) return generateFish(player);

        // Rare (12%)
        cumulative += CHANCE_RARE;
        if (roll < cumulative) return generateRare(player);

        // Key (5%)
        cumulative += CHANCE_KEY;
        if (roll < cumulative) return generateKey(player);

        // Essence (2.5%)
        cumulative += CHANCE_ESSENCE;
        if (roll < cumulative) return generateEssence(player);

        // Legendary (0.5%)
        return generateLegendary(player);
    }

    private ItemStack generateTrash(Player player) {
        ItemStack[] trashItems = {
                createNamedItem(Material.STICK, "§7§oPalo Mojado", "§8Basura del estanque"),
                createNamedItem(Material.ROTTEN_FLESH, "§7§oAlga Podrida", "§8Un poco asqueroso..."),
                createNamedItem(Material.LEATHER_BOOTS, "§7§oBota Perdida", "§8¿De quién será?"),
                createNamedItem(Material.BOWL, "§7§oCuenco Roto", "§8Inservible"),
                createNamedItem(Material.BONE, "§7§oHueso de Pez", "§8Sospechosamente grande"),
                createNamedItem(Material.STRING, "§7§oSedal Enredado", "§8Al menos es reciclable"),
                createNamedItem(Material.LILY_PAD, "§7§oNenúfar", "§8Decorativo, supongo"),
                createNamedItem(Material.INK_SAC, "§7§oTinta de Calamar", "§8Pegajosa")
        };
        ItemStack item = trashItems[random.nextInt(trashItems.length)];
        player.sendMessage(SmallCaps.convert("§7§o⟨ Basura... ⟩"));
        return item;
    }

    private ItemStack generateCommon(Player player) {
        ItemStack[] commonItems = {
                new ItemStack(Material.COD, 1 + random.nextInt(3)),
                new ItemStack(Material.SALMON, 1 + random.nextInt(3)),
                new ItemStack(Material.IRON_INGOT, 1 + random.nextInt(2)),
                new ItemStack(Material.GOLD_NUGGET, 3 + random.nextInt(5)),
                new ItemStack(Material.EXPERIENCE_BOTTLE, 2 + random.nextInt(4)),
                new ItemStack(Material.PRISMARINE_SHARD, 1 + random.nextInt(3)),
                new ItemStack(Material.CLAY_BALL, 4 + random.nextInt(8)),
                new ItemStack(Material.NAUTILUS_SHELL, 1)
        };
        ItemStack item = commonItems[random.nextInt(commonItems.length)];
        player.sendMessage(SmallCaps.convert("§a⟨ " + getItemDisplayName(item) + " ⟩"));
        return item;
    }

    private ItemStack generateFish(Player player) {
        ItemStack[] fishItems = {
                createNamedItem(Material.TROPICAL_FISH, "§b§lPez Tropical Raro", "§7Un ejemplar exótico"),
                createNamedItem(Material.PUFFERFISH, "§e§lPez Globo Dorado", "§7¡Cuidado, pica!"),
                new ItemStack(Material.COOKED_SALMON, 3 + random.nextInt(5)),
                new ItemStack(Material.COOKED_COD, 3 + random.nextInt(5)),
                createNamedItem(Material.COD, "§6§lBacalao Gigante", "§7Pesa más de lo normal"),
        };
        ItemStack item = fishItems[random.nextInt(fishItems.length)];
        player.sendMessage(SmallCaps.convert("§b⟨ ¡Buen pez! ⟩"));
        player.playSound(player.getLocation(), Sound.ENTITY_FISHING_BOBBER_SPLASH, 1.0f, 1.2f);
        return item;
    }

    private ItemStack generateRare(Player player) {
        ItemStack[] rareItems = {
                new ItemStack(Material.DIAMOND, 1),
                new ItemStack(Material.EMERALD, 2 + random.nextInt(3)),
                new ItemStack(Material.GOLDEN_APPLE, 1),
                new ItemStack(Material.ENDER_PEARL, 2),
                createNamedItem(Material.HEART_OF_THE_SEA, "§3§lCorazón del Mar", "§7Encontrado en el estanque"),
                new ItemStack(Material.LAPIS_LAZULI, 5 + random.nextInt(10)),
                new ItemStack(Material.NAME_TAG, 1),
                new ItemStack(Material.SADDLE, 1)
        };
        ItemStack item = rareItems[random.nextInt(rareItems.length)];
        player.sendMessage(SmallCaps.convert("§6§l⟨ ¡Objeto raro! ⟩"));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.5f);
        return item;
    }

    private ItemStack generateKey(Player player) {
        player.sendMessage(SmallCaps.convert("§e§l⟨ ¡¡LLAVE!! ⟩"));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.0f);

        // Dar llave via comando de crates
        String[] keyTypes = {"common", "rare", "special"};
        double roll = random.nextDouble();
        String keyType;
        if (roll < 0.5) keyType = keyTypes[0];
        else if (roll < 0.85) keyType = keyTypes[1];
        else keyType = keyTypes[2];

        Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                "excellentcrates key give " + player.getName() + " " + keyType + " 1");
        player.sendMessage(SmallCaps.convert("§e§l🗝 §fLlave §e" + keyType + " §fobtenida."));

        // Return a visual trophy item
        return createNamedItem(Material.PRISMARINE_CRYSTALS, "§e§lCristal de Suerte",
                "§7La suerte te sonríe hoy");
    }

    private ItemStack generateEssence(Player player) {
        int amount = 1 + random.nextInt(3);
        plugin.getEssenceManager().addEssences(player.getUniqueId(), amount);
        plugin.getEssenceManager().saveData();

        player.sendMessage(SmallCaps.convert("§d§l✦ §a+" + amount + " Esencias §7por pesca mágica."));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 2.0f);

        // Particles
        player.getWorld().spawnParticle(org.bukkit.Particle.END_ROD,
                player.getLocation().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0.05);

        return createNamedItem(Material.AMETHYST_SHARD, "§d§l✦ Fragmento Arcano",
                "§7Imbuido de energía mágica");
    }

    private ItemStack generateLegendary(Player player) {
        // Broadcast legendary catch
        String msg = "\n§6§l  ★ §e§l" + player.getName() + " §6§lha pescado algo §c§lLEGENDARIO§6§l ★\n";
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.sendMessage(msg);
        }

        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        player.getWorld().spawnParticle(org.bukkit.Particle.TOTEM_OF_UNDYING,
                player.getLocation().add(0, 1, 0), 50, 0.5, 1, 0.5, 0.3);

        ItemStack[] legendaryItems = {
                new ItemStack(Material.NETHERITE_SCRAP, 1 + random.nextInt(2)),
                new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 1),
                new ItemStack(Material.TOTEM_OF_UNDYING, 1),
                createNamedItem(Material.NETHER_STAR, "§c§l✦ Estrella del Abismo",
                        "§7Arrancada de las profundidades", "§8§oUn objeto de poder inconmensurable")
        };
        ItemStack item = legendaryItems[random.nextInt(legendaryItems.length)];

        // Also give 5 essences
        plugin.getEssenceManager().addEssences(player.getUniqueId(), 5);
        plugin.getEssenceManager().saveData();
        player.sendMessage(SmallCaps.convert("§d§l✦ §a+5 Esencias §7como bonus legendario."));

        // Also give a special key
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                "excellentcrates key give " + player.getName() + " legendary 1");
        player.sendMessage(SmallCaps.convert("§e§l🗝 §fLlave §elegendary §fobtenida como bonus."));

        // 5% chance de llave_espadas en pesca legendaria
        if (random.nextDouble() < 0.05) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "excellentcrates key give " + player.getName() + " llave_espadas 1");
            player.sendMessage(SmallCaps.convert("§c§l⚔ §f¡Has obtenido una §c§lLlave de Espadas§f! §7(Pesca legendaria)"));
            Bukkit.broadcastMessage(SmallCaps.convert("§c§l⚔ §e" + player.getName() + " §fha pescado una §c§lLlave de Espadas§f! §7(Increíblemente rara)"));
        }

        return item;
    }

    // ==================== ZONE DISCOVERY ====================

    private void startDiscoveryTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    for (FishingPond pond : ponds) {
                        if (pond.isNear(player.getLocation(), 20)) {
                            String key = player.getUniqueId() + ":pond:" + pond.name;
                            UUID discoveryId = UUID.nameUUIDFromBytes(key.getBytes());

                            if (!discoveredPonds.contains(discoveryId)) {
                                discoveredPonds.add(discoveryId);
                                saveData();

                                player.sendTitle(
                                        "§a§l✦ ZONA DESBLOQUEADA ✦",
                                        "§b§l⛲ Estanque: " + pond.name,
                                        10, 60, 20
                                );
                                player.sendMessage("");
                                player.sendMessage(SmallCaps.convert("§a§l✦ §fNueva zona desbloqueada: §b§lEstanque " + pond.name));
                                player.sendMessage(SmallCaps.convert("§7  ¡Pesca aquí para obtener objetos especiales!"));
                                player.sendMessage("");
                                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.2f);
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 40L, 40L);
    }

    // ==================== HELPERS ====================

    private ItemStack createNamedItem(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore.length > 0) meta.setLore(Arrays.asList(lore));
            item.setItemMeta(meta);
        }
        return item;
    }

    private String getItemDisplayName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        return item.getType().name().toLowerCase().replace('_', ' ');
    }

    // ==================== DATA PERSISTENCE ====================

    private void loadData() {
        dataFile = new File(plugin.getDataFolder(), "fishing.yml");
        if (!dataFile.exists()) {
            try {
                dataFile.getParentFile().mkdirs();
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Error creating fishing.yml: " + e.getMessage());
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);

        // Load ponds
        if (dataConfig.contains("ponds")) {
            for (String key : dataConfig.getConfigurationSection("ponds").getKeys(false)) {
                try {
                    String path = "ponds." + key;
                    String name = dataConfig.getString(path + ".name", key);
                    String world = dataConfig.getString(path + ".world");
                    int x1 = dataConfig.getInt(path + ".x1");
                    int y1 = dataConfig.getInt(path + ".y1");
                    int z1 = dataConfig.getInt(path + ".z1");
                    int x2 = dataConfig.getInt(path + ".x2");
                    int y2 = dataConfig.getInt(path + ".y2");
                    int z2 = dataConfig.getInt(path + ".z2");
                    ponds.add(new FishingPond(name, world, x1, y1, z1, x2, y2, z2));
                } catch (Exception ignored) {}
            }
        }

        // Load discoveries
        List<String> discoveries = dataConfig.getStringList("discoveries");
        for (String d : discoveries) {
            try {
                discoveredPonds.add(UUID.fromString(d));
            } catch (IllegalArgumentException ignored) {}
        }

        plugin.getLogger().info("[Fishing] Cargados " + ponds.size() + " estanques.");
    }

    public void saveData() {
        if (dataConfig == null) return;

        dataConfig.set("ponds", null);
        for (int i = 0; i < ponds.size(); i++) {
            FishingPond pond = ponds.get(i);
            String path = "ponds.pond" + i;
            dataConfig.set(path + ".name", pond.name);
            dataConfig.set(path + ".world", pond.world);
            dataConfig.set(path + ".x1", pond.x1);
            dataConfig.set(path + ".y1", pond.y1);
            dataConfig.set(path + ".z1", pond.z1);
            dataConfig.set(path + ".x2", pond.x2);
            dataConfig.set(path + ".y2", pond.y2);
            dataConfig.set(path + ".z2", pond.z2);
        }

        List<String> discoveryList = new ArrayList<>();
        for (UUID d : discoveredPonds) discoveryList.add(d.toString());
        dataConfig.set("discoveries", discoveryList);

        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Error saving fishing.yml: " + e.getMessage());
        }
    }

    public void shutdown() {
        saveData();
    }
}
