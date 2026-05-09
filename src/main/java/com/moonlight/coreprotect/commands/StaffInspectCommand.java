package com.moonlight.coreprotect.commands;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.prestige.PrestigeManager;
import com.moonlight.coreprotect.shadowhunter.ShadowHunterManager;
import com.moonlight.coreprotect.streak.DailyStreakManager;
import com.moonlight.coreprotect.util.SmallCaps;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;

import java.util.*;
import java.util.stream.Collectors;

public class StaffInspectCommand implements CommandExecutor, TabCompleter, Listener {

    private final CoreProtectPlugin plugin;

    private static final String LIST_TITLE_BASE  = "§8§l[STAFF] §7Jugadores";
    private static final String PROFILE_PREFIX   = "§8§l[STAFF] §f";
    private static final String VAULT_PREFIX     = "§8§l[VAULT] §f";

    private final Map<UUID, UUID>    viewerTarget   = new HashMap<>();
    private final Map<UUID, Integer> listPage       = new HashMap<>();
    private final Map<UUID, String>  searchFilter   = new HashMap<>();
    private final Set<UUID>          awaitingSearch = new HashSet<>();

    public StaffInspectCommand(CoreProtectPlugin plugin) {
        this.plugin = plugin;
    }

    // ==================== COMMAND ====================

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) { sender.sendMessage("§cSolo jugadores."); return true; }
        Player staff = (Player) sender;
        if (!staff.hasPermission("wardstone.staff")) {
            staff.sendMessage(SmallCaps.convert("§c§l✖ §cNo tienes permiso."));
            return true;
        }
        if (args.length >= 1) {
            String targetName = args[0];
            OfflinePlayer op = Arrays.stream(Bukkit.getOfflinePlayers())
                    .filter(p -> p.getName() != null && p.getName().equalsIgnoreCase(targetName))
                    .findFirst().orElse(null);
            if (op == null) {
                staff.sendMessage(SmallCaps.convert("§c§l✖ §cJugador '§f" + targetName + "§c' no encontrado."));
                return true;
            }
            openProfile(staff, op.getUniqueId(), op.getName());
        } else {
            openPlayerList(staff, 0);
        }
        return true;
    }

    // ==================== PLAYER LIST ====================

    private void openPlayerList(Player staff, int page) {
        listPage.put(staff.getUniqueId(), page);

        // Must read online-player state on the main thread
        OfflinePlayer[] snapshot = Bukkit.getOfflinePlayers();
        Map<UUID, String[]> onlineSnap = new HashMap<>();
        for (OfflinePlayer op : snapshot) {
            if (op.isOnline()) {
                Player p = op.getPlayer();
                onlineSnap.put(op.getUniqueId(), new String[]{
                    String.format("%.1f", p.getHealth() / 2),
                    p.getWorld().getName()
                });
            }
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            final String filter = searchFilter.getOrDefault(staff.getUniqueId(), "").toLowerCase();
            List<OfflinePlayer> all = new ArrayList<>(Arrays.asList(snapshot));
            if (!filter.isEmpty()) {
                all.removeIf(p -> p.getName() == null || !p.getName().toLowerCase().contains(filter));
            }
            all.sort((a, b) -> {
                if (a.isOnline() != b.isOnline()) return a.isOnline() ? -1 : 1;
                String na = a.getName() != null ? a.getName() : "";
                String nb = b.getName() != null ? b.getName() : "";
                return na.compareToIgnoreCase(nb);
            });

            int perPage    = 45;
            int totalPages = Math.max(1, (int) Math.ceil((double) all.size() / perPage));
            int start      = page * perPage;
            int end        = Math.min(start + perPage, all.size());
            int total      = all.size();
            List<OfflinePlayer> pageSlice = new ArrayList<>(all.subList(start, end));

            // Economy + prestige fetched here — potentially slow for offline players
            List<Object[]> entries = new ArrayList<>();
            for (OfflinePlayer op : pageSlice) {
                UUID    uid  = op.getUniqueId();
                boolean isOn = onlineSnap.containsKey(uid);
                String  name = op.getName() != null ? op.getName() : "???";

                List<String> lore = new ArrayList<>();
                lore.add("");
                lore.add(SmallCaps.convert("§fEstado: " + (isOn ? "§aEN LÍNEA" : "§cDesconectado")));
                if (plugin.getPrestigeManager() != null)
                    lore.add(SmallCaps.convert("§fPrestigio: §d" + plugin.getPrestigeManager().getPrestige(uid)));
                if (plugin.getEconomy() != null) {
                    try { lore.add(SmallCaps.convert("§fDinero: §6$" + fmtNum(plugin.getEconomy().getBalance(op)))); }
                    catch (Exception ignored) {}
                }
                if (isOn) {
                    String[] od = onlineSnap.get(uid);
                    lore.add(SmallCaps.convert("§fHP: §c" + od[0] + "❤  §7| §fMundo: §b" + od[1]));
                }
                lore.add("");
                lore.add(SmallCaps.convert("§e§l▶ Click para inspeccionar"));

                entries.add(new Object[]{op, (isOn ? "§a§l" : "§7") + name, lore, isOn, uid, name});
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!staff.isOnline()) return;

                String title = LIST_TITLE_BASE + " §8[§7" + (page + 1) + "§8/§7" + totalPages + "§8]";
                Inventory inv = Bukkit.createInventory(null, 54, title);

                for (int idx = 0; idx < entries.size(); idx++) {
                    Object[]     e    = entries.get(idx);
                    OfflinePlayer op2 = (OfflinePlayer) e[0];
                    String       dn   = (String) e[1];
                    @SuppressWarnings("unchecked")
                    List<String> lore = (List<String>) e[2];
                    boolean      isOn = (boolean) e[3];

                    UUID         uid2  = (UUID) e[4];
                    String       name2 = (String) e[5];

                    ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
                    SkullMeta sm    = (SkullMeta) skull.getItemMeta();
                    if (isOn) sm.setOwningPlayer(op2); // skip texture fetch for offline
                    sm.setDisplayName(dn);
                    sm.setLore(lore);
                    // Store UUID + name so offline skulls are clickable
                    NamespacedKey kUuid = new NamespacedKey(plugin, "si_uuid");
                    NamespacedKey kName = new NamespacedKey(plugin, "si_name");
                    sm.getPersistentDataContainer().set(kUuid, PersistentDataType.STRING, uid2.toString());
                    sm.getPersistentDataContainer().set(kName, PersistentDataType.STRING, name2);
                    skull.setItemMeta(sm);
                    inv.setItem(idx, skull);
                }

                // No-results placeholder
                if (entries.isEmpty()) {
                    inv.setItem(22, item(Material.BARRIER, "§c§lSin resultados",
                            "§7No hay jugadores que coincidan con:",
                            "§e\"" + filter + "\""));
                }

                ItemStack gray = glass(Material.GRAY_STAINED_GLASS_PANE, " ");
                for (int s = 45; s < 54; s++) inv.setItem(s, gray);
                if (page > 0)
                    inv.setItem(45, item(Material.ARROW, "§e§l◄ §ePágina anterior", "§7Ir a página " + page));

                if (!filter.isEmpty()) {
                    inv.setItem(47, item(Material.SPYGLASS, "§b§l🔍 Búsqueda activa",
                            "§7Filtro: §e\"" + filter + "\"",
                            "§7Resultados: §f" + total,
                            "§7Click para nueva búsqueda"));
                    inv.setItem(51, item(Material.BARRIER, "§c§lX Limpiar búsqueda",
                            "§7Click para ver todos los jugadores"));
                } else {
                    inv.setItem(47, item(Material.SPYGLASS, "§b§l🔍 Buscar jugador",
                            "§7Click para filtrar por nombre"));
                }

                // === STATS GLOBALES (todos los jugadores online sumados) ===
                long globalWalkCm = 0, globalSprintCm = 0, globalSwimCm = 0, globalFlyCm = 0, globalFallCm = 0;
                int globalDemonPieces = 0;
                int playersWithDemon = 0;
                int playersWithFullDemon = 0;
                for (Player op3 : Bukkit.getOnlinePlayers()) {
                    globalWalkCm   += op3.getStatistic(org.bukkit.Statistic.WALK_ONE_CM);
                    globalSprintCm += op3.getStatistic(org.bukkit.Statistic.SPRINT_ONE_CM);
                    globalSwimCm   += op3.getStatistic(org.bukkit.Statistic.SWIM_ONE_CM);
                    globalFlyCm    += op3.getStatistic(org.bukkit.Statistic.FLY_ONE_CM);
                    globalFallCm   += op3.getStatistic(org.bukkit.Statistic.FALL_ONE_CM);
                    int dp = com.moonlight.coreprotect.kits.KitDemon.getDemonPieceCount(op3, plugin);
                    globalDemonPieces += dp;
                    if (dp > 0) playersWithDemon++;
                    if (dp >= 4) playersWithFullDemon++;
                }
                long globalTotalBlocks = (globalWalkCm + globalSprintCm + globalSwimCm + globalFlyCm + globalFallCm) / 100;

                // Errante missions global
                int globalQuestsDone = 0;
                if (plugin.getShadowHunterManager() != null) {
                    ShadowHunterManager shm2 = plugin.getShadowHunterManager();
                    for (Player op3 : Bukkit.getOnlinePlayers()) {
                        UUID u = op3.getUniqueId();
                        if (shm2.hasCompleted(u)) globalQuestsDone++;
                        if (shm2.hasCompletedQuest2(u)) globalQuestsDone++;
                        if (shm2.hasCompletedQuest3(u)) globalQuestsDone++;
                        if (shm2.hasCompletedQuest4(u)) globalQuestsDone++;
                        if (shm2.hasCompletedQuest5(u)) globalQuestsDone++;
                        if (shm2.hasCompletedQuest6(u)) globalQuestsDone++;
                        if (shm2.hasCompletedQuest7(u)) globalQuestsDone++;
                        if (shm2.hasCompletedQuest8(u)) globalQuestsDone++;
                    }
                }

                int onlineCount = Bukkit.getOnlinePlayers().size();
                inv.setItem(48, item(Material.DIAMOND_BLOCK, "§a§l📊 Stats Globales §7(online)",
                        "§7Jugadores online: §f" + onlineCount,
                        "",
                        "§b§lDistancias totales:",
                        "§f  Total: §b" + fmtNum(globalTotalBlocks) + " bloques",
                        "§f  Caminando: §7" + fmtNum(globalWalkCm / 100) + " bl",
                        "§f  Corriendo: §7" + fmtNum(globalSprintCm / 100) + " bl",
                        "§f  Volando: §7" + fmtNum(globalFlyCm / 100) + " bl",
                        "",
                        "§4§lKit Demon:",
                        "§f  Piezas totales equipadas: §4" + globalDemonPieces,
                        "§f  Jugadores con Demon: §c" + playersWithDemon,
                        "§f  Sets completos: §a" + playersWithFullDemon,
                        "",
                        "§5§lMisiones Errante:",
                        "§f  Total completadas: §a" + globalQuestsDone + " §7(entre todos)"));

                inv.setItem(49, item(Material.NETHER_STAR, "§b§l✦ Staff Inspect",
                        "§7Total de jugadores: §f" + total,
                        "§7Página actual: §f" + (page + 1) + "§7/§f" + totalPages));
                if (end < total)
                    inv.setItem(53, item(Material.ARROW, "§e§l▶ §ePágina siguiente", "§7Ir a página " + (page + 2)));

                staff.openInventory(inv);
            });
        });
    }

    // ==================== PLAYER PROFILE ====================

    private void openProfile(Player staff, UUID uid, String name) {
        viewerTarget.put(staff.getUniqueId(), uid);

        // All Player API calls MUST happen on the main thread
        OfflinePlayer op     = Bukkit.getOfflinePlayer(uid);
        Player        online = op.isOnline() ? op.getPlayer() : null;
        boolean       on     = online != null;
        String        tag    = name != null ? name : "???";

        // Snapshot online-player-only data
        final String  sWorld    = on ? online.getWorld().getName() : null;
        final int     sX        = on ? online.getLocation().getBlockX() : 0;
        final int     sY        = on ? online.getLocation().getBlockY() : 0;
        final int     sZ        = on ? online.getLocation().getBlockZ() : 0;
        final double  sHp       = on ? online.getHealth() : 0;
        final double  sMaxHp    = on ? online.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue() : 0;
        final int     sFood     = on ? online.getFoodLevel() : 0;
        final int     sLevel    = on ? online.getLevel() : 0;
        final String  sGm       = on ? online.getGameMode().name() : null;
        final int     sKills    = on ? online.getStatistic(org.bukkit.Statistic.PLAYER_KILLS) : 0;
        final int     sDeaths   = on ? online.getStatistic(org.bukkit.Statistic.DEATHS) : 0;
        final int     sMobKills = on ? online.getStatistic(org.bukkit.Statistic.MOB_KILLS) : 0;
        final long    sPlayTick = on ? online.getStatistic(org.bukkit.Statistic.PLAY_ONE_MINUTE) : 0;
        final int     sWalkCm   = on ? online.getStatistic(org.bukkit.Statistic.WALK_ONE_CM) : 0;
        final int     sSprintCm = on ? online.getStatistic(org.bukkit.Statistic.SPRINT_ONE_CM) : 0;
        final int     sSwimCm   = on ? online.getStatistic(org.bukkit.Statistic.SWIM_ONE_CM) : 0;
        final int     sFallCm   = on ? online.getStatistic(org.bukkit.Statistic.FALL_ONE_CM) : 0;
        final int     sFlyCm    = on ? online.getStatistic(org.bukkit.Statistic.FLY_ONE_CM) : 0;
        final int     sDemonPcs = on ? com.moonlight.coreprotect.kits.KitDemon.getDemonPieceCount(online, plugin) : -1;
        final int     sMaxVault = plugin.getPrivateVaultManager() == null ? 0 :
                on ? plugin.getPrivateVaultManager().getMaxVaults(online)
                   : 1 + plugin.getPrivateVaultManager().getPurchasedSlots(uid);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {

            // ── Economy (potentially slow for offline players) ──
            double money    = 0;
            if (plugin.getEconomy() != null) {
                try { money = plugin.getEconomy().getBalance(op); } catch (Exception ignored) {}
            }
            int essences = plugin.getEssenceManager() != null ? plugin.getEssenceManager().getEssences(uid) : 0;

            // ── Prestige ──
            List<String> pLore = new ArrayList<>();
            pLore.add("");
            if (plugin.getPrestigeManager() != null) {
                PrestigeManager pm = plugin.getPrestigeManager();
                PrestigeManager.PrestigeData pd = pm.getData(uid);
                int prest = pd.prestige;
                List<PrestigeManager.PrestigeMission> missions = pm.getMissionsForPrestige(prest);
                long mDone = missions.stream().filter(m -> pd.completedMissions.contains(m.id)).count();
                pLore.add(SmallCaps.convert("§fNivel: §d§l" + prest + " §8/ §770"));
                pLore.add(SmallCaps.convert("§fTítulo: " + pm.getTitle(uid)));
                pLore.add(SmallCaps.convert("§fMisiones P" + (prest + 1) + ": §a" + mDone + "§7/§f" + missions.size()));
                pLore.add(SmallCaps.convert("§fMejor racha kills: §c" + pd.bestKillstreak));
                pLore.add(SmallCaps.convert("§fKills de prestigio: §d" + pd.prestigeKills));
                pLore.add(SmallCaps.convert("§fTotal donado: §6$" + fmtNum(pd.totalDonated)));
                pLore.add(SmallCaps.convert("§fBiomas descubiertos: §9" + pd.discoveredBiomes.size()));
                if (mDone >= missions.size() && prest < 70) pLore.add(SmallCaps.convert("§a§l✔ ¡Puede prestigiar!"));
                pLore.add("");
                pLore.add(SmallCaps.convert("§e§l▶ Click para ver misiones"));
                // eco lore built here using prestige data
                final List<String> ecoLoreFinal = new ArrayList<>(Arrays.asList(
                    "",
                    SmallCaps.convert("§fDinero: §6$" + fmtNum(money)),
                    SmallCaps.convert("§fEsencias: §b" + essences),
                    "",
                    SmallCaps.convert("§7§lStats de Prestigio:"),
                    SmallCaps.convert("§f  §c⚔ Daño: " + pm.getDamageDisplay(uid)),
                    SmallCaps.convert("§f  §9🛡 Defensa: " + pm.getDefenseDisplay(uid)),
                    SmallCaps.convert("§f  §d❤ Vida: " + pm.getHealthDisplay(uid)),
                    SmallCaps.convert("§f  §a🍀 Suerte: " + pm.getLuckDisplay(uid)),
                    SmallCaps.convert("§f  §e💫 Regen: " + pm.getRegenDisplay(uid)),
                    ""
                ));
                pLore.add("");

                // ── Stats PvP ──
                List<String> pvpLore = new ArrayList<>();
                pvpLore.add("");
                if (on) {
                    double kda = sDeaths == 0 ? sKills : Math.round((double) sKills / sDeaths * 100.0) / 100.0;
                    pvpLore.add(SmallCaps.convert("§fKills PvP: §c" + sKills));
                    pvpLore.add(SmallCaps.convert("§fMuertes: §7" + sDeaths));
                    pvpLore.add(SmallCaps.convert("§fKDA: §e" + kda));
                    pvpLore.add(SmallCaps.convert("§fKills mobs: §a" + sMobKills));
                    pvpLore.add(SmallCaps.convert("§fTiempo jugado: §9" + fmtTime(sPlayTick / 20)));
                } else {
                    pvpLore.add(SmallCaps.convert("§7Stats de Bukkit requieren"));
                    pvpLore.add(SmallCaps.convert("§7que el jugador esté online."));
                }
                if (plugin.getCombatTagManager() != null && plugin.getCombatTagManager().isInCombat(uid))
                    pvpLore.add(SmallCaps.convert("§c§l⚔ EN COMBATE AHORA"));
                pvpLore.add("");

                // ── Movimiento & Distancias ──
                List<String> movLore = new ArrayList<>();
                movLore.add("");
                if (on) {
                    long totalCm = (long) sWalkCm + sSprintCm + sSwimCm + sFallCm + sFlyCm;
                    long totalBlocks = totalCm / 100;
                    movLore.add(SmallCaps.convert("§f§lBloques totales recorridos:"));
                    movLore.add(SmallCaps.convert("§b  " + fmtNum(totalBlocks) + " bloques"));
                    movLore.add("");
                    movLore.add(SmallCaps.convert("§f  Caminando: §7" + fmtNum(sWalkCm / 100.0) + " bloques"));
                    movLore.add(SmallCaps.convert("§f  Corriendo: §7" + fmtNum(sSprintCm / 100.0) + " bloques"));
                    movLore.add(SmallCaps.convert("§f  Nadando: §7" + fmtNum(sSwimCm / 100.0) + " bloques"));
                    movLore.add(SmallCaps.convert("§f  Volando: §7" + fmtNum(sFlyCm / 100.0) + " bloques"));
                    movLore.add(SmallCaps.convert("§f  Cayendo: §7" + fmtNum(sFallCm / 100.0) + " bloques"));
                } else {
                    movLore.add(SmallCaps.convert("§7Requiere que el jugador esté online."));
                }
                movLore.add("");

                // ── Kit Demon ──
                List<String> demonLore = new ArrayList<>();
                demonLore.add("");
                if (on) {
                    demonLore.add(SmallCaps.convert("§f§lPiezas del Kit Demon equipadas:"));
                    demonLore.add(SmallCaps.convert("§4  " + sDemonPcs + "§7/§f4 piezas"));
                    demonLore.add("");
                    if (sDemonPcs == 0) {
                        demonLore.add(SmallCaps.convert("§7No tiene el Kit Demon equipado."));
                    } else if (sDemonPcs < 4) {
                        demonLore.add(SmallCaps.convert("§e⚠ Set incompleto (" + sDemonPcs + "/4)"));
                    } else {
                        demonLore.add(SmallCaps.convert("§a§l✔ Set Demon COMPLETO"));
                        demonLore.add(SmallCaps.convert("§7  Todas las habilidades activas."));
                    }
                } else {
                    demonLore.add(SmallCaps.convert("§7Requiere que el jugador esté online."));
                }
                demonLore.add("");

                // ── Quests ──
                List<String> qLore = new ArrayList<>();
                qLore.add("");
                if (plugin.getShadowHunterManager() != null) {
                    ShadowHunterManager shm = plugin.getShadowHunterManager();
                    int questsDone = 0;
                    if (shm.hasCompleted(uid)) questsDone++;
                    if (shm.hasCompletedQuest2(uid)) questsDone++;
                    if (shm.hasCompletedQuest3(uid)) questsDone++;
                    if (shm.hasCompletedQuest4(uid)) questsDone++;
                    if (shm.hasCompletedQuest5(uid)) questsDone++;
                    if (shm.hasCompletedQuest6(uid)) questsDone++;
                    if (shm.hasCompletedQuest7(uid)) questsDone++;
                    if (shm.hasCompletedQuest8(uid)) questsDone++;
                    qLore.add(SmallCaps.convert("§fEstado: " + questDisplay(shm.getState(uid))));
                    qLore.add(SmallCaps.convert("§fMisiones completadas: §a" + questsDone + "§7/§f8"));
                    qLore.add("");
                    qLore.add(SmallCaps.convert((shm.hasCompleted(uid)       ? "§a✔" : "§c✖") + " §fM1: Espada del Vacío"));
                    qLore.add(SmallCaps.convert((shm.hasCompletedQuest2(uid) ? "§a✔" : "§c✖") + " §fM2: Hoja del Abismo"));
                    qLore.add(SmallCaps.convert((shm.hasCompletedQuest3(uid) ? "§a✔" : "§c✖") + " §fM3: Fractura del Vacío"));
                    qLore.add(SmallCaps.convert((shm.hasCompletedQuest4(uid) ? "§a✔" : "§c✖") + " §fM4: Elegía del Errante"));
                    qLore.add(SmallCaps.convert((shm.hasCompletedQuest5(uid) ? "§a✔" : "§c✖") + " §fM5: Último Suspiro"));
                    qLore.add(SmallCaps.convert((shm.hasCompletedQuest6(uid) ? "§a✔" : "§c✖") + " §fM6: Lágrimas de Lyra"));
                    qLore.add(SmallCaps.convert((shm.hasCompletedQuest7(uid) ? "§a✔" : "§c✖") + " §fM7: Pisada del Olvido"));
                    qLore.add(SmallCaps.convert((shm.hasCompletedQuest8(uid) ? "§a✔" : "§c✖") + " §fM8: Martillo del Cero"));
                }
                qLore.add("");

                // ── Streak ──
                List<String> stLore = new ArrayList<>();
                stLore.add("");
                if (plugin.getDailyStreakManager() != null) {
                    DailyStreakManager.StreakData sd = plugin.getDailyStreakManager().getStreak(uid);
                    stLore.add(SmallCaps.convert("§fRacha actual: §e" + sd.currentStreak + " días"));
                    stLore.add(SmallCaps.convert("§fMejor racha:  §6" + sd.bestStreak   + " días"));
                    stLore.add(SmallCaps.convert("§fDiario: " + (plugin.getDailyStreakManager().canClaimToday(uid) ? "§a¡Disponible!" : "§7Ya reclamado")));
                    if (plugin.getDailyStreakManager().isStreakBroken(uid)) stLore.add(SmallCaps.convert("§c§l⚠ RACHA ROTA"));
                }
                stLore.add("");

                // ── Bounties ──
                List<String> bLore = new ArrayList<>();
                bLore.add("");
                com.moonlight.coreprotect.bounty.BountyManager bm = plugin.getBountyManager();
                if (bm != null) {
                    bLore.add(SmallCaps.convert("§fBounty sobre él: §c$" + fmtNum(bm.getTotalBountyOnPlayer(uid)) + " §8(" + bm.getBountiesOnPlayer(uid).size() + " activos)"));
                    bLore.add(SmallCaps.convert("§fBounties cobrados: §a" + bm.getTotalClaimedBy(uid)));
                    bLore.add(SmallCaps.convert("§fGanado en bounties: §6$" + fmtNum(bm.getTotalEarnedBy(uid))));
                }
                bLore.add("");

                // ── Achievements ──
                List<String> achLore = new ArrayList<>();
                achLore.add("");
                if (plugin.getAchievementManager() != null) {
                    int done  = plugin.getAchievementManager().getAchievementCount(uid);
                    int tot   = plugin.getAchievementManager().getTotalAchievements();
                    double pct = tot > 0 ? done * 100.0 / tot : 0;
                    achLore.add(SmallCaps.convert("§fDesbloqueados: §a" + done + " §7/ §f" + tot));
                    achLore.add(SmallCaps.convert("§fProgreso: §e" + String.format("%.1f", pct) + "%"));
                    int fi = (int) (pct / 10);
                    StringBuilder bar = new StringBuilder("§a");
                    for (int b = 0; b < 10; b++) { if (b == fi) bar.append("§7"); bar.append("█"); }
                    achLore.add(bar.toString());
                }
                achLore.add("");

                // ── Head lore ──
                List<String> hl = new ArrayList<>();
                hl.add("");
                hl.add(SmallCaps.convert("§fUUID: §8" + uid));
                hl.add(SmallCaps.convert("§fEstado: " + (on ? "§aEN LÍNEA" : "§cDesconectado")));
                if (on) {
                    hl.add(SmallCaps.convert("§fMundo: §b" + sWorld));
                    hl.add(SmallCaps.convert("§fPos: §7" + sX + ", " + sY + ", " + sZ));
                    hl.add(SmallCaps.convert("§fHP: §c" + String.format("%.1f", sHp / 2) + "§7/§f" + String.format("%.1f", sMaxHp / 2) + " ❤"));
                    hl.add(SmallCaps.convert("§fHambre: §6" + sFood + "§7/§f20"));
                    hl.add(SmallCaps.convert("§fNivel XP: §a" + sLevel));
                    hl.add(SmallCaps.convert("§fModo: §e" + sGm));
                }
                hl.add("");

                // Capture finals for lambda
                final double         fMoney    = money;
                final int            fEss      = essences;
                final List<String>   fEcoLore  = ecoLoreFinal;
                final List<String>   fPLore    = pLore;
                final List<String>   fPvp      = pvpLore;
                final List<String>   fMovLore  = movLore;
                final List<String>   fDemonLore = demonLore;
                final List<String>   fQLore    = qLore;
                final List<String>   fStLore   = stLore;
                final List<String>   fBLore    = bLore;
                final List<String>   fAchLore  = achLore;
                final List<String>   fHl       = hl;

                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!staff.isOnline()) return;

                    Inventory inv = Bukkit.createInventory(null, 54, PROFILE_PREFIX + tag);

                    ItemStack bdr = glass(Material.BLACK_STAINED_GLASS_PANE, "§8");
                    for (int s = 0; s < 9;  s++) inv.setItem(s, bdr);
                    for (int s = 45; s < 54; s++) inv.setItem(s, bdr);
                    for (int row : new int[]{9, 17, 18, 26, 27, 35, 36, 44}) inv.setItem(row, bdr);

                    // Skull
                    ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                    SkullMeta hsm  = (SkullMeta) head.getItemMeta();
                    if (on) hsm.setOwningPlayer(op);
                    hsm.setDisplayName((on ? "§a§l" : "§7§l") + tag);
                    hsm.setLore(fHl);
                    head.setItemMeta(hsm);
                    inv.setItem(4, head);

                    inv.setItem(10, itemLore(Material.GOLD_INGOT,    "§6§l💰 Economía",          fEcoLore));
                    inv.setItem(11, itemLore(Material.NETHER_STAR,   "§d§l✦ Prestigio",           fPLore));
                    inv.setItem(12, itemLore(Material.DIAMOND_SWORD, "§c§l⚔ Stats PvP",            fPvp));
                    inv.setItem(13, itemLore(Material.DRAGON_EGG,    "§5§l◈ Misiones del Errante", fQLore));
                    inv.setItem(14, itemLore(Material.SUNFLOWER,     "§e§l★ Racha Diaria",         fStLore));
                    inv.setItem(15, itemLore(Material.SKELETON_SKULL,"§c§l☠ Bounties",             fBLore));
                    inv.setItem(16, itemLore(Material.BOOK,          "§6§l📖 Logros",               fAchLore));

                    // Second row: Movement & Kit info
                    inv.setItem(19, itemLore(Material.DIAMOND_BOOTS, "§b§l🏃 Movimiento & Distancias", fMovLore));
                    inv.setItem(20, itemLore(Material.NETHERITE_CHESTPLATE, "§4§l⚔ Kit Demon",     fDemonLore));

                    // PV row
                    inv.setItem(27, glass(Material.CYAN_STAINED_GLASS_PANE, "§b§lCofres Privados (PVs)"));
                    if (sMaxVault == 0) {
                        inv.setItem(31, item(Material.BARRIER, "§7Sin vaults privados", "§8Este jugador no tiene PVs"));
                    } else {
                        for (int v = 1; v <= sMaxVault && v <= 7; v++)
                            inv.setItem(27 + v, item(Material.CHEST, "§b§l[PV #" + v + "] §fCofre privado",
                                    "§7Click para §bver contenido", "§8Solo lectura"));
                    }

                    inv.setItem(49, item(Material.ARROW, "§7§l◄ §7Volver a la lista", "§7Click para volver"));
                    staff.openInventory(inv);
                });
            } else {
                // PrestigeManager null — still build minimal profile
                final double fMoney2 = money;
                final int    fEss2   = essences;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!staff.isOnline()) return;
                    Inventory inv = Bukkit.createInventory(null, 54, PROFILE_PREFIX + tag);
                    ItemStack bdr = glass(Material.BLACK_STAINED_GLASS_PANE, "§8");
                    for (int s = 0; s < 9;  s++) inv.setItem(s, bdr);
                    for (int s = 45; s < 54; s++) inv.setItem(s, bdr);
                    ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                    SkullMeta hsm  = (SkullMeta) head.getItemMeta();
                    if (on) hsm.setOwningPlayer(op);
                    hsm.setDisplayName((on ? "§a§l" : "§7§l") + tag);
                    head.setItemMeta(hsm);
                    inv.setItem(4, head);
                    inv.setItem(10, item(Material.GOLD_INGOT, "§6§l💰 Economía",
                            "§fDinero: §6$" + fmtNum(fMoney2), "§fEsencias: §b" + fEss2));
                    inv.setItem(49, item(Material.ARROW, "§7§l◄ §7Volver a la lista", "§7Click para volver"));
                    staff.openInventory(inv);
                });
            }
        });
    }

    // ==================== VAULT VIEW ====================

    private void openVault(Player staff, UUID targetUUID, String targetName, int vaultNum) {
        if (plugin.getPrivateVaultManager() == null) return;
        Inventory vault = plugin.getPrivateVaultManager().loadVault(targetUUID, vaultNum);

        int vaultSize  = vault.getSize();
        // Add a nav row below vault contents (max 54 slots total)
        int contentRows = (int) Math.ceil(vaultSize / 9.0);
        int totalRows   = Math.min(contentRows + 1, 6);
        int displaySize = totalRows * 9;
        int navStart    = (totalRows - 1) * 9; // first slot of nav row

        String title = VAULT_PREFIX + targetName + " §8#" + vaultNum;
        Inventory copy = Bukkit.createInventory(null, displaySize, title);

        // Copy vault contents (leave nav row empty of real items)
        for (int i = 0; i < Math.min(vaultSize, navStart); i++) {
            ItemStack it = vault.getItem(i);
            copy.setItem(i, it != null ? it.clone() : null);
        }

        // Nav row
        ItemStack gray = glass(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int s = navStart; s < displaySize; s++) copy.setItem(s, gray);
        copy.setItem(navStart + 4, item(Material.ARROW, "§7§l◄ §7Volver al perfil", "§7Click para volver"));

        staff.openInventory(copy);
    }

    // ==================== EVENTS ====================

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player staff = (Player) event.getWhoClicked();
        if (!staff.hasPermission("wardstone.staff")) return;

        String title = event.getView().getTitle();

        // Read-only vault
        if (title.startsWith(VAULT_PREFIX)) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            // Back button: any ARROW in the nav row
            if (slot >= 0 && event.getCurrentItem() != null
                    && event.getCurrentItem().getType() == Material.ARROW) {
                UUID targetUUID = viewerTarget.get(staff.getUniqueId());
                if (targetUUID != null) {
                    OfflinePlayer op = Bukkit.getOfflinePlayer(targetUUID);
                    String tName = op.getName() != null ? op.getName() : "???";
                    Bukkit.getScheduler().runTaskLater(plugin, () -> openProfile(staff, targetUUID, tName), 1L);
                }
            }
            return;
        }

        // ── Player list ──
        if (title.startsWith(LIST_TITLE_BASE)) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            if (slot < 0 || slot >= 54) return;

            if (slot == 45) {
                int pg = listPage.getOrDefault(staff.getUniqueId(), 0);
                if (pg > 0) Bukkit.getScheduler().runTaskLater(plugin, () -> openPlayerList(staff, pg - 1), 1L);
                return;
            }
            if (slot == 53) {
                int pg = listPage.getOrDefault(staff.getUniqueId(), 0);
                Bukkit.getScheduler().runTaskLater(plugin, () -> openPlayerList(staff, pg + 1), 1L);
                return;
            }
            if (slot == 47) {
                awaitingSearch.add(staff.getUniqueId());
                staff.closeInventory();
                staff.sendMessage(SmallCaps.convert("§b§l[STAFF] §7Escribe en el chat el nombre a buscar §8(§ccancel §8para cancelar)§7:"));
                return;
            }
            if (slot == 51) {
                searchFilter.remove(staff.getUniqueId());
                Bukkit.getScheduler().runTaskLater(plugin, () -> openPlayerList(staff, 0), 1L);
                return;
            }
            if (slot >= 45) return;

            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() != Material.PLAYER_HEAD) return;
            ItemMeta clickedMeta = clicked.getItemMeta();
            if (clickedMeta == null) return;
            NamespacedKey kUuid = new NamespacedKey(plugin, "si_uuid");
            NamespacedKey kName = new NamespacedKey(plugin, "si_name");
            String uuidStr = clickedMeta.getPersistentDataContainer().get(kUuid, PersistentDataType.STRING);
            if (uuidStr == null) return;
            UUID   targetUUID;
            try { targetUUID = UUID.fromString(uuidStr); } catch (Exception ex) { return; }
            String targetName = clickedMeta.getPersistentDataContainer().getOrDefault(kName, PersistentDataType.STRING, "???");
            Bukkit.getScheduler().runTaskLater(plugin, () -> openProfile(staff, targetUUID, targetName), 1L);
            return;
        }

        // ── Staff Missions GUI (back button) ──
        if (title.startsWith(PrestigeManager.STAFF_MISSIONS_PREFIX)) {
            event.setCancelled(true);
            if (event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.ARROW) {
                UUID targetUUID = viewerTarget.get(staff.getUniqueId());
                if (targetUUID != null) {
                    OfflinePlayer op2 = Bukkit.getOfflinePlayer(targetUUID);
                    String tName = op2.getName() != null ? op2.getName() : "???";
                    Bukkit.getScheduler().runTaskLater(plugin, () -> openProfile(staff, targetUUID, tName), 1L);
                }
            }
            return;
        }

        // ── Player profile ──
        if (title.startsWith(PROFILE_PREFIX)) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            if (slot < 0 || slot >= 54) return;

            UUID targetUUID = viewerTarget.get(staff.getUniqueId());
            if (targetUUID == null) return;
            OfflinePlayer op = Bukkit.getOfflinePlayer(targetUUID);
            String targetName = op.getName() != null ? op.getName() : "???";

            // Prestige button (slot 11) → open staff missions GUI
            if (slot == 11 && event.getCurrentItem() != null
                    && event.getCurrentItem().getType() == Material.NETHER_STAR) {
                if (plugin.getPrestigeManager() != null) {
                    Bukkit.getScheduler().runTaskLater(plugin, () ->
                            plugin.getPrestigeManager().openStaffMissionsGUI(staff, targetUUID, targetName), 1L);
                }
                return;
            }

            // Vault buttons: slots 28–34
            if (slot >= 28 && slot <= 34) {
                ItemStack it = event.getCurrentItem();
                if (it == null || it.getType() != Material.CHEST) return;
                int vaultNum = slot - 27;
                Bukkit.getScheduler().runTaskLater(plugin, () -> openVault(staff, targetUUID, targetName, vaultNum), 1L);
                return;
            }

            // Back button
            if (slot == 49) {
                int pg = listPage.getOrDefault(staff.getUniqueId(), 0);
                Bukkit.getScheduler().runTaskLater(plugin, () -> openPlayerList(staff, pg), 1L);
            }
        }
    }

    @EventHandler
    @SuppressWarnings("deprecation")
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player p = event.getPlayer();
        if (!awaitingSearch.contains(p.getUniqueId())) return;
        event.setCancelled(true);
        awaitingSearch.remove(p.getUniqueId());
        String query = event.getMessage().trim();
        if (query.equalsIgnoreCase("cancel") || query.isEmpty()) {
            searchFilter.remove(p.getUniqueId());
        } else {
            searchFilter.put(p.getUniqueId(), query.toLowerCase());
        }
        Bukkit.getScheduler().runTask(plugin, () -> openPlayerList(p, 0));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uid = event.getPlayer().getUniqueId();
        awaitingSearch.remove(uid);
        searchFilter.remove(uid);
        viewerTarget.remove(uid);
        listPage.remove(uid);
    }

    // ==================== TAB COMPLETE ====================

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("wardstone.staff")) return Collections.emptyList();
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            return Arrays.stream(Bukkit.getOfflinePlayers())
                    .filter(p -> p.getName() != null && p.getName().toLowerCase().startsWith(input))
                    .map(OfflinePlayer::getName)
                    .limit(20)
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    // ==================== UTILS ====================

    private ItemStack item(Material mat, String name, String... lore) {
        ItemStack it   = new ItemStack(mat);
        ItemMeta  meta = it.getItemMeta();
        meta.setDisplayName(SmallCaps.convert(name));
        List<String> l = new ArrayList<>();
        for (String s : lore) if (s != null && !s.isEmpty()) l.add(SmallCaps.convert(s));
        meta.setLore(l);
        it.setItemMeta(meta);
        return it;
    }

    private ItemStack itemLore(Material mat, String name, List<String> lore) {
        ItemStack it   = new ItemStack(mat);
        ItemMeta  meta = it.getItemMeta();
        meta.setDisplayName(SmallCaps.convert(name));
        meta.setLore(lore.stream().map(SmallCaps::convert).collect(Collectors.toList()));
        it.setItemMeta(meta);
        return it;
    }

    private ItemStack glass(Material mat, String name) {
        ItemStack it   = new ItemStack(mat);
        ItemMeta  meta = it.getItemMeta();
        meta.setDisplayName(SmallCaps.convert(name));
        meta.setLore(Collections.emptyList());
        it.setItemMeta(meta);
        return it;
    }

    private String fmtNum(double n) {
        if (n >= 1_000_000) return String.format("%.1fM", n / 1_000_000);
        if (n >= 1_000)     return String.format("%.1fK", n / 1_000);
        return String.format("%.0f", n);
    }

    private String fmtTime(long secs) {
        long d = secs / 86400, h = (secs % 86400) / 3600, m = (secs % 3600) / 60;
        if (d > 0) return d + "d " + h + "h";
        if (h > 0) return h + "h " + m + "m";
        return m + "m";
    }

    private String questDisplay(ShadowHunterManager.QuestState s) {
        switch (s) {
            case NONE:              return "§7Sin misión activa";
            case NPC_SPAWNED:       return "§eNPC M1 apareció";
            case DIALOGUE:          return "§eM1: Diálogo";
            case COMBAT_PHASE:      return "§cM1: Fase combate";
            case RITUAL_PHASE:      return "§5M1: Fase ritual";
            case BOSS_PHASE:        return "§4§lM1: Boss activo";
            case REWARD:            return "§aM1: Recompensa";
            case COMPLETED:         return "§aM1 completada ✔";
            case QUEST2_NPC_SPAWNED:return "§eNPC M2 apareció";
            case QUEST2_EXPLORATION:return "§bM2: Exploración";
            case QUEST2_COMBAT:     return "§cM2: Combate";
            case QUEST2_RITUAL:     return "§5M2: Ritual";
            case QUEST2_BOSS:       return "§4§lM2: Boss activo";
            case QUEST2_COMPLETED:  return "§aM2 completada ✔";
            case QUEST3_NPC_SPAWNED:return "§eNPC M3 apareció";
            case QUEST3_TRIALS:     return "§bM3: Pruebas";
            case QUEST3_ARENA:      return "§cM3: Arena";
            case QUEST3_BOSS:       return "§4§lM3: Boss activo";
            case QUEST3_POSTBOSS:   return "§5M3: Post-boss";
            case QUEST3_COMPLETED:  return "§aM3 completada ✔";
            case QUEST4_NPC_SPAWNED:return "§eNPC M4 apareció";
            case QUEST4_BOSS:       return "§4§lM4: Boss activo";
            case QUEST4_COMPLETED:  return "§aM4 completada ✔";
            case QUEST5_NPC_SPAWNED:return "§eNPC M5 apareció";
            case QUEST5_PARKOUR:    return "§bM5: Parkour";
            case QUEST5_COMPLETED:  return "§aM5 completada ✔";
            case QUEST6_NPC_SPAWNED:   return "§eNPC M6 apareció";
            case QUEST6_MEMORY_WALK:    return "§bM6: Memory Walk";
            case QUEST6_BOSS:           return "§4§lM6: Boss activo";
            case QUEST6_COMPLETED:  return "§aM6 completada ✔";
            case QUEST7_NPC_SPAWNED:return "§eNPC M7 apareció";
            case QUEST7_LABYRINTH:  return "§bM7: Laberinto";
            case QUEST7_BOSS:       return "§4§lM7: Boss activo";
            case QUEST7_COMPLETED:  return "§aM7 completada ✔";
            case QUEST8_NPC_SPAWNED:return "§eNPC M8 apareció";
            case QUEST8_BOSS:       return "§4§lM8: Boss activo";
            case QUEST8_COMPLETED:  return "§6§l🏆 TODAS COMPLETADAS";
            default:                return "§7" + s.name();
        }
    }
}
