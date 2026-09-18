package com.moonlight.coreprotect.prestige;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.util.SmallCaps;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.*;

/**
 * Weekly rotating missions for P30+ players.
 * 5 missions change every Monday. Same missions for everyone each week.
 * Tracks progress using Bukkit statistics with baselines.
 */
public class PrestigeWeeklyManager implements Listener {

    private final CoreProtectPlugin plugin;
    private final File dataFile;
    private FileConfiguration data;

    public static final String GUI_TITLE = "§8✦ §b§lMisiones Semanales §8✦";
    private static final int REQUIRED_PRESTIGE = 30;

    // Per-player weekly data
    private final Map<UUID, WeeklyPlayerData> playerData = new HashMap<>();

    // Current week missions (generated from seed, same for all)
    private List<WeeklyMission> currentMissions;
    private int currentWeekId;

    // Mission templates
    private static final List<MissionTemplate> TEMPLATES = List.of(
        new MissionTemplate("mine", "Minar %s bloques", Material.DIAMOND_PICKAXE, 3000, 5000),
        new MissionTemplate("kill_mob", "Matar %s mobs", Material.DIAMOND_SWORD, 150, 400),
        new MissionTemplate("pvp_kill", "Eliminar %s jugadores", Material.GOLDEN_SWORD, 10, 30),
        new MissionTemplate("mine_diamond", "Minar %s diamantes", Material.DIAMOND, 20, 60),
        new MissionTemplate("enchant", "Encantar %s items", Material.ENCHANTING_TABLE, 10, 30),
        new MissionTemplate("fish", "Pescar %s peces", Material.FISHING_ROD, 30, 80),
        new MissionTemplate("damage_dealt", "Hacer %s de daño", Material.IRON_SWORD, 1500, 4000),
        new MissionTemplate("eat", "Comer %s alimentos", Material.GOLDEN_APPLE, 80, 200),
        new MissionTemplate("walk", "Caminar %s bloques", Material.LEATHER_BOOTS, 8000, 20000),
        new MissionTemplate("sprint", "Correr %s bloques", Material.IRON_BOOTS, 4000, 12000),
        new MissionTemplate("swim", "Nadar %s bloques", Material.COD, 500, 2000),
        new MissionTemplate("craft", "Craftear %s items de diamante/netherita", Material.CRAFTING_TABLE, 8, 25),
        new MissionTemplate("brew", "Preparar %s pociones", Material.BREWING_STAND, 15, 40),
        new MissionTemplate("trade", "Comerciar %s veces con aldeanos", Material.EMERALD, 20, 60),
        new MissionTemplate("mine_netherite", "Minar %s Ancient Debris", Material.ANCIENT_DEBRIS, 3, 10)
    );

    public PrestigeWeeklyManager(CoreProtectPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "weekly_missions.yml");
        this.data = YamlConfiguration.loadConfiguration(dataFile);
        generateCurrentWeekMissions();
        loadPlayerData();

        // Periodic check every 60 seconds for P30+ players
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            PrestigeManager mgr = plugin.getPrestigeManager();
            if (mgr == null) return;
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (mgr.getData(p.getUniqueId()).prestige >= REQUIRED_PRESTIGE) {
                    checkAndComplete(p);
                }
            }
        }, 1200L, 1200L);
    }

    // ═══════════════════════════════════════
    // MISSION GENERATION
    // ═══════════════════════════════════════

    private int getWeekId() {
        LocalDate now = LocalDate.now();
        return now.getYear() * 100 + now.get(WeekFields.ISO.weekOfYear());
    }

    private void generateCurrentWeekMissions() {
        currentWeekId = getWeekId();
        Random weekRand = new Random(currentWeekId);

        // Shuffle templates and pick 5
        List<MissionTemplate> shuffled = new ArrayList<>(TEMPLATES);
        Collections.shuffle(shuffled, weekRand);

        currentMissions = new ArrayList<>();
        for (int i = 0; i < 5 && i < shuffled.size(); i++) {
            MissionTemplate t = shuffled.get(i);
            // Random target between min and max, rounded to nice numbers
            int target = t.minTarget + weekRand.nextInt(t.maxTarget - t.minTarget + 1);
            target = ((target + 4) / 5) * 5; // round to nearest 5
            String name = String.format(t.nameFormat, formatNumber(target));
            currentMissions.add(new WeeklyMission(i, t.statType, name, target, t.icon));
        }
    }

    // ═══════════════════════════════════════
    // PLAYER DATA
    // ═══════════════════════════════════════

    private void loadPlayerData() {
        if (data.contains("players")) {
            for (String uuidStr : data.getConfigurationSection("players").getKeys(false)) {
                UUID uuid = UUID.fromString(uuidStr);
                String path = "players." + uuidStr;
                int weekId = data.getInt(path + ".weekId", 0);
                WeeklyPlayerData wpd = new WeeklyPlayerData();
                wpd.weekId = weekId;

                if (data.contains(path + ".baselines")) {
                    for (String key : data.getConfigurationSection(path + ".baselines").getKeys(false)) {
                        wpd.baselines.put(key, data.getLong(path + ".baselines." + key));
                    }
                }
                if (data.contains(path + ".completed")) {
                    wpd.completed.addAll(data.getIntegerList(path + ".completed"));
                }
                playerData.put(uuid, wpd);
            }
        }
    }

    public void saveData() {
        for (Map.Entry<UUID, WeeklyPlayerData> e : playerData.entrySet()) {
            String path = "players." + e.getKey().toString();
            WeeklyPlayerData wpd = e.getValue();
            data.set(path + ".weekId", wpd.weekId);
            for (Map.Entry<String, Long> b : wpd.baselines.entrySet()) {
                data.set(path + ".baselines." + b.getKey(), b.getValue());
            }
            data.set(path + ".completed", new ArrayList<>(wpd.completed));
        }
        try { data.save(dataFile); } catch (IOException ex) { ex.printStackTrace(); }
    }

    private WeeklyPlayerData getOrInit(Player player) {
        UUID uid = player.getUniqueId();
        WeeklyPlayerData wpd = playerData.computeIfAbsent(uid, k -> new WeeklyPlayerData());

        // Check if week changed
        if (wpd.weekId != currentWeekId) {
            // New week — check if global missions need refresh
            if (currentWeekId != getWeekId()) {
                generateCurrentWeekMissions();
            }
            wpd.weekId = currentWeekId;
            wpd.baselines.clear();
            wpd.completed.clear();

            // Store current stat values as baselines
            PrestigeManager mgr = plugin.getPrestigeManager();
            if (mgr != null) {
                for (WeeklyMission m : currentMissions) {
                    wpd.baselines.put(m.statType, mgr.getMissionProgress(player.getUniqueId(), m.statType));
                }
            }
        }
        return wpd;
    }

    // ═══════════════════════════════════════
    // PROGRESS CHECKING
    // ═══════════════════════════════════════

    private long getProgress(Player player, WeeklyMission mission, WeeklyPlayerData wpd) {
        PrestigeManager mgr = plugin.getPrestigeManager();
        if (mgr == null) return 0;
        long current = mgr.getMissionProgress(player.getUniqueId(), mission.statType);
        long baseline = wpd.baselines.getOrDefault(mission.statType, current);
        return Math.max(0, current - baseline);
    }

    public void checkAndComplete(Player player) {
        PrestigeManager mgr = plugin.getPrestigeManager();
        if (mgr == null) return;
        if (mgr.getData(player.getUniqueId()).prestige < REQUIRED_PRESTIGE) return;

        WeeklyPlayerData wpd = getOrInit(player);
        for (WeeklyMission m : currentMissions) {
            if (wpd.completed.contains(m.index)) continue;
            long progress = getProgress(player, m, wpd);
            if (progress >= m.target) {
                wpd.completed.add(m.index);
                player.sendMessage(SmallCaps.convert("§b§l✦ §fMisión semanal completada: §e" + m.name));
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.5f);

                // Check if all 5 completed
                if (wpd.completed.size() == currentMissions.size()) {
                    giveWeeklyReward(player);
                }
            }
        }
        saveData();
    }

    private void giveWeeklyReward(Player player) {
        String pName = player.getName();
        // Reward: essences + special key + money
        if (plugin.getEssenceManager() != null) plugin.getEssenceManager().addEssences(player.getUniqueId(), 150);
        if (plugin.getEconomy() != null) plugin.getEconomy().depositPlayer(player, 25000);
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "excellentcrates key give " + pName + " legendary 2");

        player.sendMessage("");
        player.sendMessage(SmallCaps.convert("§b§l★ §e§l¡MISIONES SEMANALES COMPLETADAS! §b§l★"));
        player.sendMessage(SmallCaps.convert("§f  +150 Esencias, $25,000, 2x Llave Legendary"));
        player.sendMessage("");
        Bukkit.broadcastMessage(SmallCaps.convert("§b§l★ §e" + pName + " §fha completado todas las §bmisiones semanales§f!"));
    }

    // ═══════════════════════════════════════
    // GUI
    // ═══════════════════════════════════════

    public void openGUI(Player player) {
        PrestigeManager mgr = plugin.getPrestigeManager();
        if (mgr == null) return;
        int prestige = mgr.getData(player.getUniqueId()).prestige;

        if (prestige < REQUIRED_PRESTIGE) {
            player.sendMessage(SmallCaps.convert("§c§l✦ §fNecesitas §cPrestigio " + REQUIRED_PRESTIGE + " §fpara acceder a misiones semanales."));
            return;
        }

        WeeklyPlayerData wpd = getOrInit(player);

        Inventory inv = Bukkit.createInventory(null, 27, GUI_TITLE);

        // Borders
        ItemStack border = createItem(Material.BLACK_STAINED_GLASS_PANE, "§r");
        for (int i = 0; i < 9; i++) inv.setItem(i, border);
        for (int i = 18; i < 27; i++) inv.setItem(i, border);

        // Title
        int completed = wpd.completed.size();
        int total = currentMissions.size();
        ItemStack title = createItem(Material.CLOCK, "§b§l📅 Misiones Semanales §7(" + completed + "/" + total + ")");
        ItemMeta titleMeta = title.getItemMeta();
        titleMeta.setLore(Arrays.asList(
                "",
                SmallCaps.convert("§7Misiones nuevas cada lunes"),
                SmallCaps.convert("§7Requiere Prestigio " + REQUIRED_PRESTIGE + "+"),
                "",
                SmallCaps.convert("§eRecompensa por completar todas:"),
                SmallCaps.convert("§f  150 Esencias + $25,000 + 2x Llave Legendary"),
                ""
        ));
        title.setItemMeta(titleMeta);
        inv.setItem(4, title);

        // Missions (slots 11-15)
        int[] missionSlots = {11, 12, 13, 14, 15};
        for (int i = 0; i < currentMissions.size() && i < missionSlots.length; i++) {
            WeeklyMission m = currentMissions.get(i);
            boolean done = wpd.completed.contains(m.index);
            long progress = getProgress(player, m, wpd);
            long target = m.target;

            Material mat = done ? Material.LIME_STAINED_GLASS_PANE : m.icon;
            String prefix = done ? "§a§l✓ " : "§e";
            int pct = (int) Math.min(100, (progress * 100) / target);

            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(prefix + m.name);
            List<String> lore = new ArrayList<>();
            lore.add("");
            if (done) {
                lore.add(SmallCaps.convert("§a✓ Completada"));
            } else {
                String bar = buildProgressBar(pct);
                lore.add(SmallCaps.convert("§fProgreso: §e" + formatNumber(progress) + "§7/§e" + formatNumber(target)));
                lore.add(bar + " §e" + pct + "%");
            }
            lore.add("");
            meta.setLore(lore);
            item.setItemMeta(meta);
            inv.setItem(missionSlots[i], item);
        }

        // Back button
        inv.setItem(22, createItem(Material.ARROW, "§e§l◀ Volver a Prestigios"));

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.5f, 1.2f);
    }

    private String buildProgressBar(int pct) {
        int filled = pct / 5; // 20 segments
        int empty = 20 - filled;
        StringBuilder sb = new StringBuilder("§a");
        for (int i = 0; i < filled; i++) sb.append("|");
        sb.append("§7");
        for (int i = 0; i < empty; i++) sb.append("|");
        return sb.toString();
    }

    // ═══════════════════════════════════════
    // EVENT HANDLERS
    // ═══════════════════════════════════════

    @EventHandler(priority = EventPriority.HIGH)
    public void onClickWeekly(InventoryClickEvent event) {
        if (!event.getView().getTitle().equals(GUI_TITLE)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // Back button
        if (event.getRawSlot() == 22 && event.getCurrentItem() != null
                && event.getCurrentItem().getType() == Material.ARROW) {
            plugin.getPrestigeManager().openMainGUI(player, 0);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDragWeekly(InventoryDragEvent event) {
        if (event.getView().getTitle().equals(GUI_TITLE)) {
            event.setCancelled(true);
        }
    }

    // ═══════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════

    private ItemStack createItem(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }

    private String formatNumber(long n) {
        if (n >= 1_000_000) return String.format("%.1fM", n / 1_000_000.0);
        if (n >= 1_000) return String.format("%.1fK", n / 1_000.0);
        return String.valueOf(n);
    }

    // ═══════════════════════════════════════
    // INNER CLASSES
    // ═══════════════════════════════════════

    private static class WeeklyPlayerData {
        int weekId = 0;
        Map<String, Long> baselines = new HashMap<>();
        Set<Integer> completed = new HashSet<>();
    }

    private static class WeeklyMission {
        int index;
        String statType;
        String name;
        long target;
        Material icon;
        WeeklyMission(int index, String statType, String name, long target, Material icon) {
            this.index = index; this.statType = statType; this.name = name;
            this.target = target; this.icon = icon;
        }
    }

    private record MissionTemplate(String statType, String nameFormat, Material icon, int minTarget, int maxTarget) {}
}
