package com.moonlight.coreprotect.koth;

import com.moonlight.coreprotect.CoreProtectPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.entity.EnderPearl;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerBucketEmptyEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import com.moonlight.coreprotect.util.SmallCaps;

/**
 * Listener de eventos para el mundo KOTH con límites de mapa y telarañas.
 */
public class KothListener implements Listener {

    private final CoreProtectPlugin plugin;
    private final Map<UUID, Long> enderpearlCooldowns = new HashMap<>();
    private static final long ENDERPEARL_COOLDOWN = 20000; // 20 segundos

    public KothListener(CoreProtectPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Límites del mapa dinámicos con cristales rojos.
     */
    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!player.getWorld().getName().equals(KothWorld.getWorldName()))
            return;

        KothManager manager = plugin.getKothManager();
        if (!manager.isActive())
            return;

        Location to = event.getTo();
        if (to == null)
            return;

        // Si intenta salir, cancelar
        if (!manager.isInsideMap(to)) {
            event.setCancelled(true);
            player.sendMessage(SmallCaps.convert("§c§l✖ §cHas alcanzado el límite del campo de batalla."));
            return;
        }

        // Mostrar cristales rojos dinámicos
        showDynamicBarriers(player, to);
    }

    /**
     * Envía bloques falsos de cristal rojo solo al usuario que está cerca del
     * borde.
     */
    private void showDynamicBarriers(Player player, Location loc) {
        KothManager manager = plugin.getKothManager();
        int x1 = manager.getMapX1();
        int z1 = manager.getMapZ1();
        int x2 = manager.getMapX2();
        int z2 = manager.getMapZ2();

        int px = loc.getBlockX();
        int pz = loc.getBlockZ();
        int py = loc.getBlockY();

        // Verificar distancias a los 4 bordes (Aumentado a 6 bloques de detección)
        if (Math.abs(px - x1) < 6)
            spawnWall(player, x1, pz, py, true);
        if (Math.abs(px - x2) < 6)
            spawnWall(player, x2, pz, py, true);
        if (Math.abs(pz - z1) < 6)
            spawnWall(player, px, z1, py, false);
        if (Math.abs(pz - z2) < 6)
            spawnWall(player, px, z2, py, false);
    }

    private void spawnWall(Player player, int x, int pz, int py, boolean isXWall) {
        // Pared más grande: 5 bloques de ancho y 5 de alto
        for (int offset = -2; offset <= 2; offset++) {
            for (int y = -1; y <= 3; y++) {
                Location barrier = isXWall ? new Location(player.getWorld(), x, py + y, pz + offset)
                        : new Location(player.getWorld(), x + offset, py + y, pz);

                player.sendBlockChange(barrier, Material.RED_STAINED_GLASS.createBlockData());

                // Los quitamos tras 3 segundos
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) {
                        player.sendBlockChange(barrier, barrier.getBlock().getBlockData());
                    }
                }, 60L);
            }
        }
    }

    /**
     * Bloquea TODOS los placements durante el KOTH.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!event.getBlock().getWorld().getName().equals(KothWorld.getWorldName()))
            return;
        if (event.getPlayer().isOp())
            return;

        KothManager manager = plugin.getKothManager();
        if (!manager.isActive())
            return;

        // Bloquear TODOS los placements durante el KOTH
        event.setCancelled(true);
        event.getPlayer().sendMessage(SmallCaps.convert("§c§l✖ §cNo puedes colocar bloques durante el KOTH."));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!event.getBlock().getWorld().getName().equals(KothWorld.getWorldName()))
            return;
        if (event.getPlayer().isOp())
            return;

        KothManager manager = plugin.getKothManager();
        if (!manager.isActive())
            return;

        // Bloquear romper CUALQUIER bloque durante el KOTH
        event.setCancelled(true);
        event.getPlayer().sendMessage(SmallCaps.convert("§c§l✖ §cNo puedes romper bloques durante el KOTH."));
    }

    /**
     * Puntos por kills.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        if (!victim.getWorld().getName().equals(KothWorld.getWorldName()))
            return;

        Player killer = victim.getKiller();
        if (killer == null)
            return;

        KothManager manager = plugin.getKothManager();
        if (!manager.isActive())
            return;

        int killPoints = plugin.getConfig().getInt("koth.points.per-kill", 5);
        manager.addPoints(killer, killPoints);
        manager.addKill(killer);

        killer.sendMessage(SmallCaps.convert("§a§l+§a" + killPoints + " puntos §7(kill a §f" + victim.getName() + "§7)"));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (!event.getPlayer().getWorld().getName().equals(KothWorld.getWorldName()))
            return;

        org.bukkit.Location spawnLoc = plugin.getKothManager().getKothSpawn();
        if (spawnLoc != null) {
            event.setRespawnLocation(spawnLoc);

            // También proteger al reaparecer (darle 15s de gracia)
            plugin.getKothManager().getEntryTimestamps().put(event.getPlayer().getUniqueId(),
                    System.currentTimeMillis());
        }
        
        // Reaplicar efecto de oscuridad en KOTH nocturno
        Player player = event.getPlayer();
        if (plugin.getKothManager().getCurrentMode() == com.moonlight.coreprotect.koth.KothMode.NOCTURNO) {
            org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && player.getWorld().getName().equals(KothWorld.getWorldName())) {
                    player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        org.bukkit.potion.PotionEffectType.BLINDNESS, 999999, 0, false, false));
                }
            }, 2L);
        }
    }

    /**
     * Desequipar elytras automáticamente en KOTH clásico.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!player.getWorld().getName().equals(KothWorld.getWorldName()))
            return;
        
        KothManager manager = plugin.getKothManager();
        if (!manager.isActive())
            return;
        
        // Solo en modo CLÁSICO
        if (manager.getCurrentMode() != KothMode.CLASICO)
            return;
        
        // Verificar si tiene elytra equipada
        ItemStack chest = player.getInventory().getChestplate();
        if (chest != null && chest.getType() == Material.ELYTRA) {
            player.getInventory().setChestplate(null);
            player.getInventory().addItem(chest);
            player.sendMessage(SmallCaps.convert("§c§l✖ §cNo puedes usar elytra en KOTH Clásico."));
        }
    }
    
    /**
     * Prevenir equipar elytras en KOTH clásico.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player))
            return;
        
        Player player = (Player) event.getWhoClicked();
        if (!player.getWorld().getName().equals(KothWorld.getWorldName()))
            return;
        
        KothManager manager = plugin.getKothManager();
        if (!manager.isActive())
            return;
        
        // Solo en modo CLÁSICO
        if (manager.getCurrentMode() != KothMode.CLASICO)
            return;
        
        ItemStack item = event.getCurrentItem();
        if (item != null && item.getType() == Material.ELYTRA) {
            // Si está intentando equipar la elytra en el slot de pecho
            if (event.getSlot() == 38) { // Slot 38 es el pecho en el inventario del jugador
                event.setCancelled(true);
                player.sendMessage(SmallCaps.convert("§c§l✖ §cNo puedes equipar elytra en KOTH Clásico."));
            }
        }
        
        // También verificar si está haciendo shift-click con elytra
        if (event.isShiftClick() && item != null && item.getType() == Material.ELYTRA) {
            event.setCancelled(true);
            player.sendMessage(SmallCaps.convert("§c§l✖ §cNo puedes equipar elytra en KOTH Clásico."));
        }
    }
    
    /**
     * Bloquear uso de cargas de viento en KOTH clásico.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onWindChargeUse(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!player.getWorld().getName().equals(KothWorld.getWorldName()))
            return;
        
        KothManager manager = plugin.getKothManager();
        if (!manager.isActive())
            return;
        
        // Solo en modo CLÁSICO
        if (manager.getCurrentMode() != KothMode.CLASICO)
            return;
        
        ItemStack item = event.getItem();
        if (item != null && item.getType() == Material.WIND_CHARGE) {
            event.setCancelled(true);
            player.sendMessage(SmallCaps.convert("§c§l✖ §cNo puedes usar cargas de viento en KOTH Clásico."));
        }
    }

    /**
     * Cooldown de enderpearls (20s) solo en KOTH clásico.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEnderpearlThrow(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof EnderPearl))
            return;
        if (!(event.getEntity().getShooter() instanceof Player))
            return;
        
        Player player = (Player) event.getEntity().getShooter();
        if (!player.getWorld().getName().equals(KothWorld.getWorldName()))
            return;
        
        KothManager manager = plugin.getKothManager();
        if (!manager.isActive())
            return;
        
        // Solo aplicar cooldown en modo CLÁSICO
        if (manager.getCurrentMode() != KothMode.CLASICO)
            return;
        
        UUID uid = player.getUniqueId();
        long now = System.currentTimeMillis();
        
        if (enderpearlCooldowns.containsKey(uid)) {
            long lastUse = enderpearlCooldowns.get(uid);
            long remaining = ENDERPEARL_COOLDOWN - (now - lastUse);
            
            if (remaining > 0) {
                event.setCancelled(true);
                player.sendMessage(SmallCaps.convert("§c§l✖ §cEnderpearl en cooldown: §e" + (remaining / 1000) + "s"));
                return;
            }
        }
        
        enderpearlCooldowns.put(uid, now);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        KothManager manager = plugin.getKothManager();

        if (player.getWorld().getName().equals(KothWorld.getWorldName())) {
            manager.addPlayerToBossBar(player);
        } else if (event.getFrom().getName().equals(KothWorld.getWorldName())) {
            manager.getScoreboard().removeScoreboard(player);
        }
    }

    /**
     * Protección de spawn (Anti-PvP en 3x3x3).
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player))
            return;
        Player player = (Player) event.getEntity();
        if (!player.getWorld().getName().equals(KothWorld.getWorldName()))
            return;

        KothManager manager = plugin.getKothManager();
        if (manager.isProtectedAtSpawn(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPvP(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player))
            return;
        Player victim = (Player) event.getEntity();
        if (!victim.getWorld().getName().equals(KothWorld.getWorldName()))
            return;

        KothManager manager = plugin.getKothManager();

        // Si la víctima o el atacante están en el spawn PROTEGIDO, cancelar PvP
        boolean victimProtected = manager.isProtectedAtSpawn(victim);
        boolean attackerProtected = false;

        if (event.getDamager() instanceof Player) {
            attackerProtected = manager.isProtectedAtSpawn((Player) event.getDamager());
        }

        if (victimProtected || attackerProtected) {
            event.setCancelled(true);
            if (event.getDamager() instanceof Player) {
                event.getDamager().sendMessage(SmallCaps.convert("§c§l✖ §cNo puedes pelear en la zona de spawn."));
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        KothManager manager = plugin.getKothManager();
        if (manager.isActive()) {
            manager.addPlayerToBossBar(event.getPlayer());
        }
        // Expulsar jugadores que reconectan en el mundo KOTH cuando no deberían
        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                if (event.getPlayer().isOnline()) {
                    plugin.getKothManager().kickIfNotAllowed(event.getPlayer());
                }
            }
        }.runTaskLater(plugin, 5L);
    }

    /**
     * Bloquea elytras en modo CLASICO.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGlide(EntityToggleGlideEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        if (!player.getWorld().getName().equals(KothWorld.getWorldName())) return;

        KothManager manager = plugin.getKothManager();
        if (!manager.isActive()) return;

        // Solo bloquear si intenta ACTIVAR el glide (no al desactivar)
        if (!event.isGliding()) return;

        // En VALE_TODO se permite todo
        if (manager.getCurrentMode() == KothMode.VALE_TODO) return;

        // En CLASICO bloquear elytras
        if (manager.getCurrentMode() == KothMode.CLASICO) {
            event.setCancelled(true);
            player.sendMessage(SmallCaps.convert("§c§l✖ §cLas elytras están §lprohibidas §cen el modo Clásico."));
        }
    }

    /**
     * Bloquea el uso del mazo (MACE) en modo CLASICO.
     * Detecta cuando un jugador ataca con un mazo equipado.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMaceAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) return;
        Player attacker = (Player) event.getDamager();
        if (!attacker.getWorld().getName().equals(KothWorld.getWorldName())) return;

        KothManager manager = plugin.getKothManager();
        if (!manager.isActive()) return;

        // En VALE_TODO se permite todo
        if (manager.getCurrentMode() == KothMode.VALE_TODO) return;

        // En CLASICO bloquear mazo
        if (manager.getCurrentMode() == KothMode.CLASICO) {
            ItemStack hand = attacker.getInventory().getItemInMainHand();
            if (hand != null && hand.getType().name().equals("MACE")) {
                event.setCancelled(true);
                attacker.sendMessage(SmallCaps.convert("§c§l✖ §cEl mazo está §lprohibido §cen el modo Clásico."));
            }
        }
    }

    /**
     * Bloquea el uso de cubos de agua/lava durante el KOTH.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (!event.getPlayer().getWorld().getName().equals(KothWorld.getWorldName()))
            return;
        if (event.getPlayer().isOp())
            return;

        KothManager manager = plugin.getKothManager();
        if (!manager.isActive())
            return;

        Material bucket = event.getBucket();
        if (bucket == Material.WATER_BUCKET || bucket == Material.LAVA_BUCKET
                || bucket == Material.POWDER_SNOW_BUCKET) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(SmallCaps.convert("§c§l✖ §cNo puedes colocar líquidos durante el KOTH."));
        }
    }

    /**
     * Quita elytras del chestplate en modo CLASICO al entrar al KOTH.
     * Previene que jugadores entren con elytras puestas.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMoveWithElytra(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!player.getWorld().getName().equals(KothWorld.getWorldName())) return;

        KothManager manager = plugin.getKothManager();
        if (!manager.isActive()) return;
        if (manager.getCurrentMode() == KothMode.VALE_TODO) return;

        // En CLASICO: si está planeando con elytra, forzar aterrizaje
        if (manager.getCurrentMode() == KothMode.CLASICO && player.isGliding()) {
            player.setGliding(false);
        }
    }
}
