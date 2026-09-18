package com.moonlight.coreprotect.afkzone;

import com.moonlight.coreprotect.CoreProtectPlugin;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import com.moonlight.coreprotect.util.SmallCaps;

/**
 * Gestiona la lógica de jugadores en la zona AFK:
 * - Detecta entrada/salida
 * - Muestra título con tiempo restante
 * - Da recompensas cada 5 minutos con rareza aleatoria
 * - Impide que los jugadores reciban daño o knockback en la zona
 */
public class AfkZoneListener implements Listener {

    private final CoreProtectPlugin plugin;
    private final AfkZoneManager manager;

    // UUID → segundos acumulados en la zona esta sesión
    private final Map<UUID, Integer> timeInZone = new HashMap<>();
    // UUID → segundos hasta próxima recompensa
    private final Map<UUID, Integer> cooldowns = new HashMap<>();
    // Jugadores actualmente en zona
    private final Set<UUID> inZone = new HashSet<>();

    // Scoreboard team para desactivar colisiones en la zona AFK
    private org.bukkit.scoreboard.Team afkTeam;

    public AfkZoneListener(CoreProtectPlugin plugin, AfkZoneManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        setupAfkTeam();
        startTickTask();
        startFreezeCheckTask();
    }

    private void setupAfkTeam() {
        org.bukkit.scoreboard.Scoreboard main = Bukkit.getScoreboardManager().getMainScoreboard();
        afkTeam = main.getTeam("afk_nocollision");
        if (afkTeam == null) {
            afkTeam = main.registerNewTeam("afk_nocollision");
        }
        afkTeam.setOption(org.bukkit.scoreboard.Team.Option.COLLISION_RULE,
                org.bukkit.scoreboard.Team.OptionStatus.NEVER);
        afkTeam.setCanSeeFriendlyInvisibles(false);
    }

    // Posición congelada: donde se quedó quieto
    private final Map<UUID, Location> frozenPositions = new HashMap<>();
    // Timestamp del último movimiento voluntario
    private final Map<UUID, Long> lastVoluntaryMove = new HashMap<>();
    // Si el jugador está actualmente congelado (idle)
    private final Set<UUID> frozenPlayers = new HashSet<>();
    // Tarea de teleport activo por jugador congelado
    private final Map<UUID, BukkitTask> freezeTasks = new HashMap<>();
    // Flag para permitir nuestra propia velocity de rebote (no cancelarla en PlayerVelocityEvent)
    private final Set<UUID> applyingKnockback = new HashSet<>();

    // ==========================================
    // PROTECCIÓN: NO DAÑO / NO KNOCKBACK / NO INTERACCIÓN EN ZONA AFK
    // ==========================================

    // Bloquear click derecho sobre jugador AFK (PlayerInteractAtEntityEvent)
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractAtAfk(PlayerInteractAtEntityEvent event) {
        if (!(event.getRightClicked() instanceof Player)) return;
        Player target = (Player) event.getRightClicked();
        if (frozenPlayers.contains(target.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    // Bloquear click izquierdo (ataque) sobre jugador AFK
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractEntityAfk(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Player)) return;
        Player target = (Player) event.getRightClicked();
        if (frozenPlayers.contains(target.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    // Bloquear todo daño al jugador AFK o causado por jugador AFK
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamageInAfk(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player))
            return;
        Player player = (Player) event.getEntity();
        if (inZone.contains(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    // Bloquear velocidad externa en jugadores AFK (permitir nuestra velocity de rebote)
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onVelocityInAfk(PlayerVelocityEvent event) {
        UUID uid = event.getPlayer().getUniqueId();
        if (frozenPlayers.contains(uid) && !applyingKnockback.contains(uid)) {
            event.setCancelled(true);
        }
    }

    // Bloquear daño entre entidades donde la víctima o atacante es AFK
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamageByEntityInAfk(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        // Víctima es jugador AFK
        if (event.getEntity() instanceof Player) {
            Player victim = (Player) event.getEntity();
            if (frozenPlayers.contains(victim.getUniqueId())) {
                event.setCancelled(true);
                return;
            }
        }
        // Atacante es jugador AFK (no puede atacar mientras está congelado)
        if (event.getDamager() instanceof Player) {
            Player attacker = (Player) event.getDamager();
            if (frozenPlayers.contains(attacker.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }

    // ==========================================
    // DETECCIÓN DE MOVIMIENTO — FREEZE CUANDO IDLE
    // ==========================================

    // Permitir teletransportes dentro/hacia/desde la zona AFK
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (!manager.isZoneDefined()) return;
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        Location dest = event.getTo();
        boolean destInZone = manager.isInZone(dest);
        boolean wasIn = inZone.contains(uuid);

        if (!wasIn && destInZone) {
            // Entrando a la zona por teleport — registrar sin bloquear
            inZone.add(uuid);
            lastVoluntaryMove.put(uuid, 0L);
            timeInZone.putIfAbsent(uuid, 0);
            cooldowns.putIfAbsent(uuid, AfkZoneManager.REWARD_INTERVAL_SECONDS);
            afkTeam.addEntry(player.getName());
            freezePlayer(player, dest.clone());
            sendEnterMessage(player);
            return;
        }

        if (wasIn && destInZone) {
            // Teletransporte dentro de la zona — actualizar posición congelada al destino
            if (frozenPlayers.contains(uuid)) {
                unfreezePlayer(player);
                frozenPositions.put(uuid, dest.clone());
                frozenPlayers.add(uuid);
                freezePlayer(player, dest.clone());
            }
            return;
        }

        if (wasIn && !destInZone) {
            // Saliendo de la zona por teleport — limpiar estado
            inZone.remove(uuid);
            unfreezePlayer(player);
            lastVoluntaryMove.remove(uuid);
            timeInZone.put(uuid, 0);
            cooldowns.put(uuid, AfkZoneManager.REWARD_INTERVAL_SECONDS);
            afkTeam.removeEntry(player.getName());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMove(PlayerMoveEvent event) {
        if (!manager.isZoneDefined())
            return;

        // Ignorar teleports — manejados en onTeleport
        if (event instanceof PlayerTeleportEvent)
            return;

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        boolean wasIn = inZone.contains(uuid);
        boolean nowIn = manager.isInZone(event.getTo());

        if (!wasIn && nowIn) {
            // Entró a la zona caminando — congelar inmediatamente
            inZone.add(uuid);
            lastVoluntaryMove.put(uuid, 0L);
            timeInZone.putIfAbsent(uuid, 0);
            cooldowns.putIfAbsent(uuid, AfkZoneManager.REWARD_INTERVAL_SECONDS);
            afkTeam.addEntry(player.getName());
            freezePlayer(player, event.getTo().clone());
            sendEnterMessage(player);
            return;
        }

        if (!wasIn)
            return;

        if (!nowIn) {
            // Salió de la zona
            inZone.remove(uuid);
            unfreezePlayer(player);
            lastVoluntaryMove.remove(uuid);
            timeInZone.put(uuid, 0);
            cooldowns.put(uuid, AfkZoneManager.REWARD_INTERVAL_SECONDS);
            afkTeam.removeEntry(player.getName());
            player.sendTitle("", SmallCaps.convert("§c✖ Saliste de la zona AFK §8— §cContador reiniciado"), 5, 30, 15);
            player.sendMessage(SmallCaps.convert("§8[§6AFK§8] §7Has salido de la zona AFK. §cTu progreso se ha reiniciado."));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.7f);
            return;
        }

        // Está dentro de la zona
        Location from = event.getFrom();
        Location to = event.getTo();
        boolean positionChanged = from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ();

        if (!positionChanged)
            return; // Solo giró la cabeza, permitir

        if (frozenPlayers.contains(uuid)) {
            // Está CONGELADO — solo permitir giro de cámara, no posición
            Location frozen = frozenPositions.get(uuid);
            if (frozen != null) {
                Location corrected = frozen.clone();
                corrected.setYaw(to.getYaw());
                corrected.setPitch(to.getPitch());
                event.setTo(corrected);
            }
            // Detectar si el jugador pulsa tecla de movimiento: el 'from' generado por el
            // cliente cuando pulsa W/A/S/D difiere del 'to' — si hay diferencia de posición
            // real, es el jugador intentándolo. Descongelar.
            if (frozen != null && from.distanceSquared(frozen) < 0.001) {
                // El jugador intentó moverse desde exactamente la posición congelada → descongelar
                unfreezePlayer(player);
                lastVoluntaryMove.put(uuid, System.currentTimeMillis());
                event.setTo(to);
            }
            return;
        }

        // NO está congelado (está caminando) — permitir movimiento y actualizar timer
        lastVoluntaryMove.put(uuid, System.currentTimeMillis());
    }

    // ==========================================
    // FREEZE / UNFREEZE
    // ==========================================

    private void freezePlayer(Player player, Location pos) {
        UUID uuid = player.getUniqueId();
        if (frozenPlayers.contains(uuid)) return;
        frozenPositions.put(uuid, pos.clone());
        frozenPlayers.add(uuid);
        player.setInvulnerable(true);
        player.setCollidable(false);
        // Cada 1 tick: si el AFK fue desplazado, aplicar knockback de vuelta hacia su posición
        // El knockback es visible para todos (el jugador "rebota") y es más robusto que teleport
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!frozenPlayers.contains(uuid)) return;
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline()) return;
            Location frozen = frozenPositions.get(uuid);
            if (frozen == null) return;
            Location current = p.getLocation();
            double dx = frozen.getX() - current.getX();
            double dz = frozen.getZ() - current.getZ();
            double distSq = dx * dx + dz * dz;
            if (distSq > 0.04) {
                // Aplicar velocity hacia la posición congelada (knockback de vuelta)
                double dist = Math.sqrt(distSq);
                double strength = Math.min(0.6, dist * 0.4); // fuerza proporcional, máximo 0.6
                org.bukkit.util.Vector v = new org.bukkit.util.Vector(dx / dist * strength, 0.1, dz / dist * strength);
                applyingKnockback.add(uuid);
                p.setVelocity(v);
                applyingKnockback.remove(uuid);
                // También corregir Y si se fue muy lejos
                if (Math.abs(current.getY() - frozen.getY()) > 0.5) {
                    Location snap = frozen.clone();
                    snap.setYaw(current.getYaw());
                    snap.setPitch(current.getPitch());
                    p.teleport(snap);
                }
            }
        }, 0L, 1L);
        // Cancelar tarea anterior si existía
        BukkitTask old = freezeTasks.put(uuid, task);
        if (old != null) old.cancel();
    }

    private void unfreezePlayer(Player player) {
        UUID uuid = player.getUniqueId();
        frozenPlayers.remove(uuid);
        frozenPositions.remove(uuid);
        BukkitTask task = freezeTasks.remove(uuid);
        if (task != null) task.cancel();
        player.setInvulnerable(false);
        player.setCollidable(true);
    }

    /**
     * Tarea que se ejecuta cada 10 ticks para re-congelar jugadores que dejaron de
     * moverse durante 2 segundos.
     */
    private void startFreezeCheckTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            for (UUID uuid : new HashSet<>(inZone)) {
                if (frozenPlayers.contains(uuid))
                    continue; // Ya está congelado
                Long lastMove = lastVoluntaryMove.get(uuid);
                if (lastMove != null && now - lastMove > 2000) {
                    // 2s sin moverse → re-congelar
                    Player player = Bukkit.getPlayer(uuid);
                    if (player != null && player.isOnline()) {
                        freezePlayer(player, player.getLocation().clone());
                    }
                }
            }
        }, 10L, 10L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (inZone.contains(uuid)) {
            afkTeam.removeEntry(player.getName());
        }
        inZone.remove(uuid);
        unfreezePlayer(player);
        lastVoluntaryMove.remove(uuid);
        timeInZone.remove(uuid);
        cooldowns.remove(uuid);
    }

    public boolean isPlayerInZone(Player player) {
        return inZone.contains(player.getUniqueId());
    }

    // ==========================================
    // TAREA PRINCIPAL (cada segundo)
    // ==========================================

    private void startTickTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (UUID uuid : new HashSet<>(inZone)) {
                    Player player = Bukkit.getPlayer(uuid);
                    if (player == null || !player.isOnline()) {
                        inZone.remove(uuid);
                        continue;
                    }

                    // Re-verificar que sigue en zona (puede haberse teletransportado)
                    if (!manager.isInZone(player.getLocation())) {
                        inZone.remove(uuid);
                        continue;
                    }

                    // Incrementar tiempo
                    int total = timeInZone.merge(uuid, 1, Integer::sum);
                    int cd = cooldowns.merge(uuid, -1, Integer::sum);
                    if (cd < 0)
                        cd = 0;

                    // Título cada segundo con tiempo
                    sendAfkTitle(player, total, cd);

                    // Partículas alrededor del jugador
                    spawnParticles(player, cd);

                    // Dar recompensa cuando el cooldown llega a 0
                    if (cd == 0) {
                        cooldowns.put(uuid, AfkZoneManager.REWARD_INTERVAL_SECONDS);
                        giveReward(player, total);
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    // ==========================================
    // TÍTULO AFK
    // ==========================================

    private void sendAfkTitle(Player player, int totalSeconds, int cooldownSeconds) {
        String timeSpent = formatTime(totalSeconds);
        String timeNext = formatTime(cooldownSeconds);

        // Calcular porcentaje de progreso
        double progress = 1.0 - (cooldownSeconds / (double) AfkZoneManager.REWARD_INTERVAL_SECONDS);
        String bar = buildProgressBar(progress, 12);

        String title = "§6§l☀ §e§lZONA AFK §6§l☀";
        String subtitle = "§7Tiempo: §f" + timeSpent + "  §8│  §7Próxima: §e" + timeNext + "\n" + bar;

        player.sendTitle(title, subtitle, 0, 25, 5);
    }

    private String buildProgressBar(double progress, int length) {
        int filled = (int) (progress * length);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            if (i < filled) {
                // Gradiente de color según progreso
                if (progress < 0.33)
                    sb.append("§c█");
                else if (progress < 0.66)
                    sb.append("§e█");
                else
                    sb.append("§a█");
            } else {
                sb.append("§8░");
            }
        }
        return sb.toString();
    }

    private String formatTime(int seconds) {
        int m = seconds / 60;
        int s = seconds % 60;
        if (m > 0)
            return String.format("%dm %02ds", m, s);
        return s + "s";
    }

    // ==========================================
    // PARTÍCULAS
    // ==========================================

    private void spawnParticles(Player player, int cooldown) {
        Location loc = player.getLocation().add(0, 1, 0);
        World world = player.getWorld();

        // Partículas flotantes cada 5 segundos según rareza próxima
        if (cooldown % 5 == 0) {
            world.spawnParticle(Particle.END_ROD, loc, 5, 0.3, 0.5, 0.3, 0.02);
        }

        // Flash de partículas cuando falta poco
        if (cooldown <= 10 && cooldown > 0) {
            world.spawnParticle(Particle.HAPPY_VILLAGER, loc, 3, 0.3, 0.3, 0.3, 0);
        }
    }

    // ==========================================
    // RECOMPENSAS
    // ==========================================

    private void giveReward(Player player, int totalSeconds) {
        AfkRewardRarity rarity = AfkZoneManager.rollRarity();
        AfkReward reward = AfkZoneManager.getRandomReward(rarity);

        // Animación de recompensa
        playRewardAnimation(player, rarity);

        if (reward.isKey()) {
            // Dar llave con comando de crates
            String cmd = reward.getKeyCommand().replace("{player}", player.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            // Extraer tipo de llave del comando para el mensaje
            String keyType = "Llave";
            if (cmd.contains("common"))
                keyType = "§e1x Llave Common";
            else if (cmd.contains("rare"))
                keyType = "§b1x Llave Rare";
            else if (cmd.contains("special"))
                keyType = "§61x Llave Special";
            else if (cmd.contains("legendary"))
                keyType = "§d1x Llave Legendary";
            else if (cmd.contains("common 2"))
                keyType = "§e2x Llaves Common";
            sendRewardMessages(player, rarity, keyType, null);
        } else if (reward.isMoney()) {
            double amount = reward.rollMoney();
            Economy eco = plugin.getEconomy();
            if (eco != null) {
                plugin.depositWithMultiplier(player, amount);
                String formatted = String.format("%,.0f", amount);
                sendRewardMessages(player, rarity, "§a$" + formatted, null);
            }
        } else {
            List<ItemStack> items = new ArrayList<>();
            for (String entry : reward.getItems()) {
                String[] parts = entry.split(":");
                Material mat = Material.matchMaterial(parts[0]);
                if (mat == null)
                    continue;
                int qty = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
                items.add(new ItemStack(mat, qty));
            }
            // Dar items al jugador, drop si no hay espacio
            for (ItemStack item : items) {
                Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
                for (ItemStack drop : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), drop);
                }
            }
            // Si la reward también tiene dinero
            if (reward.getMinMoney() > 0 || reward.getMaxMoney() > 0) {
                double amount = reward.rollMoney();
                Economy eco = plugin.getEconomy();
                if (eco != null) {
                    plugin.depositWithMultiplier(player, amount);
                    String formatted = String.format("%,.0f", amount);
                    sendRewardMessages(player, rarity, "§a$" + formatted + " §7+ ítems", items);
                }
            } else {
                sendRewardMessages(player, rarity, null, items);
            }
        }

        // Registrar total de tiempo
        player.sendMessage(SmallCaps.convert("§8[§6AFK§8] §7Tiempo total en zona: §e" + formatTime(totalSeconds)));
    }

    private void sendRewardMessages(Player player, AfkRewardRarity rarity, String moneyStr, List<ItemStack> items) {
        String border = rarity.getColor() + "§l━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━";

        player.sendMessage("");
        player.sendMessage(border);
        player.sendMessage(
                rarity.getColor() + "§l  " + rarity.getStars() + "  " + rarity.getDisplay() + "  " + rarity.getStars());
        player.sendMessage(SmallCaps.convert("§7  Recompensa zona AFK:"));
        if (moneyStr != null) {
            player.sendMessage(SmallCaps.convert("§f  → " + moneyStr));
        }
        if (items != null) {
            for (ItemStack item : items) {
                player.sendMessage(SmallCaps.convert("§f  → §e" + item.getAmount() + "x " + formatMaterial(item.getType())));
            }
        }
        player.sendMessage(border);
        player.sendMessage("");
    }

    private void playRewardAnimation(Player player, AfkRewardRarity rarity) {
        Location loc = player.getLocation().add(0, 1, 0);
        World world = player.getWorld();

        Sound sound;
        Particle particle;
        int count;

        switch (rarity) {
            case LEGENDARIO:
                sound = Sound.UI_TOAST_CHALLENGE_COMPLETE;
                particle = Particle.TOTEM_OF_UNDYING;
                count = 80;
                break;
            case EPICO:
                sound = Sound.ENTITY_PLAYER_LEVELUP;
                particle = Particle.WITCH;
                count = 50;
                break;
            case RARO:
                sound = Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
                particle = Particle.ENCHANT;
                count = 30;
                break;
            case POCO_COMUN:
                sound = Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
                particle = Particle.HAPPY_VILLAGER;
                count = 15;
                break;
            default:
                sound = Sound.ENTITY_ITEM_PICKUP;
                particle = Particle.CRIT;
                count = 10;
                break;
        }

        world.spawnParticle(particle, loc, count, 0.5, 0.8, 0.5, 0.1);
        player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
    }

    private void sendEnterMessage(Player player) {
        player.sendTitle( SmallCaps.convert("§6§l☀ ZONA AFK ☀"), SmallCaps.convert("§7Recibe recompensas cada §e5 minutos§7!"), 10, 50, 15);
        player.sendMessage("");
        player.sendMessage(SmallCaps.convert("§8[§6AFK§8] §f¡Bienvenido a la §6§lZona AFK§f!"));
        player.sendMessage(SmallCaps.convert("§8[§6AFK§8] §7Recibirás recompensas cada §e5 minutos§7 con rarezas:"));
        player.sendMessage(SmallCaps.convert("§8[§6AFK§8] §f" + rarityChanceLine()));
        player.sendMessage(SmallCaps.convert("§8[§6AFK§8] §a¡Estás protegido! §7Nadie puede dañarte ni moverte aquí."));
        player.sendMessage("");
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.2f);
    }

    private String rarityChanceLine() {
        return "§f55% §8│ §a25% §8│ §b13% §8│ §d5% §8│ §6§l2%";
    }

    private String formatMaterial(Material mat) {
        String name = mat.name().replace("_", " ").toLowerCase();
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
