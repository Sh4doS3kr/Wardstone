package com.moonlight.coreprotect.protection;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.core.ProtectedRegion;
import com.moonlight.coreprotect.effects.SoundManager;
import com.moonlight.coreprotect.gui.CoreManagementGUI;
import com.moonlight.coreprotect.gui.GUIListener;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.entity.EntityType;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.Material;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.block.Block;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.Location;
import org.bukkit.World;
import java.util.UUID;
import com.moonlight.coreprotect.util.SmallCaps;

public class ProtectionListener implements Listener {

    private final CoreProtectPlugin plugin;
    private final java.util.Map<UUID, Long> pvpZoneMessageCooldown = new java.util.HashMap<>();
    private final java.util.Map<UUID, Long> newPlayerPvpZoneCooldown = new java.util.HashMap<>();
    private final java.util.Map<UUID, Long> banKnockbackCooldown = new java.util.HashMap<>();

    public ProtectionListener(CoreProtectPlugin plugin) {
        this.plugin = plugin;
        startPvpZoneActionBar();
    }

    private void startPvpZoneActionBar() {
        org.bukkit.Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
                if (p.getWorld().getEnvironment() != org.bukkit.World.Environment.NORMAL) continue;
                Location loc = p.getLocation();
                boolean inSpawn = plugin.getProtectionManager().isSpawnProtected(loc);
                boolean inCore = plugin.getProtectionManager().isSpawnCore(loc);
                if (inSpawn && !inCore) {
                    p.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                            new net.md_5.bungee.api.chat.TextComponent("§c§l⚔ ZONA PVP §7(Spawn)"));
                }
            }
        }, 10L, 10L); // every 0.5s
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockDamage(BlockDamageEvent event) {
        boolean locked = plugin.getProtectionManager().isLocked(event.getBlock().getLocation());
        // Insta-Break logic for cores — ONLY the owner can break their own core
        Player player = event.getPlayer();
        ProtectedRegion coreRegion = plugin.getProtectionManager().getRegionByCore(event.getBlock().getLocation());
        // Auto-unlock stuck cores (region exists but location still locked)
        if (locked && coreRegion != null) {
            plugin.getProtectionManager().unlockLocation(event.getBlock().getLocation());
            locked = false;
        }
        if (locked) {
            event.setCancelled(true);
            return;
        }

        if (coreRegion != null) {
            // ALWAYS cancel the event for cores — they can only be broken via animation
            event.setCancelled(true);
            event.setInstaBreak(false);
            
            if (coreRegion.getOwner().equals(player.getUniqueId()) || player.hasPermission("coreprotect.admin")) {
                handleCoreBreak(player, coreRegion, event.getBlock());
            } else {
                plugin.getMessageManager().send(player, "protection.cannot-break");
                SoundManager.playProtectionDenied(player.getLocation());
            }
        }
    }

    // Use LOWEST priority to intercept BEFORE any other plugin or Minecraft processes the break
    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockBreakLowest(BlockBreakEvent event) {
        // Protect core blocks — must be caught at lowest priority to prevent instant-break
        // with Efficiency 10, Creative mode, etc.
        ProtectedRegion coreRegionCheck = plugin.getProtectionManager().getRegionByCore(event.getBlock().getLocation());
        if (coreRegionCheck != null) {
            event.setCancelled(true);
            event.setDropItems(false);

            // Safety: if block was somehow already broken (AIR), restore it immediately
            org.bukkit.block.Block block = event.getBlock();
            if (block.getType() == Material.AIR || block.getType() == Material.CAVE_AIR) {
                com.moonlight.coreprotect.core.CoreLevel coreLevel =
                        com.moonlight.coreprotect.core.CoreLevel.fromConfig(plugin.getConfig(), coreRegionCheck.getLevel());
                if (coreLevel != null) {
                    block.setType(coreLevel.getMaterial());
                    plugin.getLogger().warning("[CoreProtect] Restored instantly-broken core at " +
                            block.getX() + " " + block.getY() + " " + block.getZ());
                }
            }

            // If owner, trigger break via animation on next tick (avoid re-entrance issues)
            Player player = event.getPlayer();
            if (coreRegionCheck.getOwner().equals(player.getUniqueId()) || player.hasPermission("coreprotect.admin")) {
                final ProtectedRegion region = coreRegionCheck;
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (block.getType() != Material.AIR) {
                        handleCoreBreak(player, region, block);
                    }
                });
            } else {
                plugin.getMessageManager().send(player, "protection.cannot-break");
                SoundManager.playProtectionDenied(player.getLocation());
            }
            return;
        }

        if (plugin.getProtectionManager().isLocked(event.getBlock().getLocation())) {
            event.setCancelled(true);
            return;
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (plugin.getProtectionManager().isLocked(event.getBlock().getLocation())) {
            event.setCancelled(true);
            return;
        }
        Player player = event.getPlayer();

        // Double-check core protection at HIGH priority too (belt and suspenders)
        ProtectedRegion coreRegionCheck = plugin.getProtectionManager().getRegionByCore(event.getBlock().getLocation());
        if (coreRegionCheck != null) {
            event.setCancelled(true);
            event.setDropItems(false);
            return;
        }

        if (player.hasPermission("coreprotect.admin")) {
            return;
        }

        ProtectedRegion region = plugin.getProtectionManager().getRegionAt(event.getBlock().getLocation());

        // Proteger globalmente cualquier bloque dentro del Dominio (VoidArena)
        if (plugin.getShadowHunterManager() != null
                && plugin.getShadowHunterManager().isInsideAnyVoidArena(event.getBlock().getLocation())) {
            event.setCancelled(true);
            player.sendMessage(SmallCaps.convert("§c§l✖ §cNo puedes romper bloques en la Expansión de Dominio."));
            return;
        }

        if (region == null) {
            // Check if this is spawn protection
            if (plugin.getProtectionManager().isSpawnProtected(event.getBlock().getLocation()) &&
                    !player.hasPermission("wardstone.admin")) {
                org.bukkit.World.Environment env = event.getBlock().getWorld().getEnvironment();
                if (env != org.bukkit.World.Environment.THE_END) {
                    plugin.getMessageManager().send(player, "protection.cannot-break");
                    SoundManager.playProtectionDenied(player.getLocation());
                    event.setCancelled(true);
                }
            }
            return;
        }

        if (!region.canAccess(player.getUniqueId())) {
            plugin.getMessageManager().send(player, "protection.cannot-break");
            SoundManager.playProtectionDenied(player.getLocation());
            event.setCancelled(true);
        }
    }

    private void handleCoreBreak(Player player, ProtectedRegion coreRegion, org.bukkit.block.Block block) {
        if (block.getType() == Material.AIR)
            return;

        // Prevent double-trigger (from both onBlockDamage and onBlockBreakLowest)
        Location coreLoc = block.getLocation();
        if (plugin.getProtectionManager().isLocked(coreLoc)) return;
        plugin.getProtectionManager().lockLocation(coreLoc);

        Material material = block.getType();
        block.setType(Material.AIR);

        com.moonlight.coreprotect.effects.CoreAnimation animation = new com.moonlight.coreprotect.effects.CoreAnimation(
                plugin);
        animation.playBreakAnimation(block.getLocation(), material, () -> {
            if (plugin.getProtectionManager().getRegion(coreRegion.getId()) != null) {
                plugin.getProtectionManager().removeRegion(coreRegion.getId());
                plugin.getMessageManager().send(player, "protection.removed");
                if (player.isOnline()) {
                    plugin.getAchievementListener().onCoreBreak(player);
                }

                com.moonlight.coreprotect.core.CoreLevel level = com.moonlight.coreprotect.core.CoreLevel
                        .fromConfig(plugin.getConfig(), coreRegion.getLevel());
                org.bukkit.inventory.ItemStack dropItem;
                if (level != null) {
                    dropItem = level.toItemStack();
                } else {
                    dropItem = new org.bukkit.inventory.ItemStack(material);
                }

                // Store upgrades + members in the dropped item
                org.bukkit.inventory.meta.ItemMeta meta = dropItem.getItemMeta();
                if (meta != null) {
                    org.bukkit.persistence.PersistentDataContainer pdc = meta.getPersistentDataContainer();
                    org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey("coreprotect", "core_upgrades");
                    StringBuilder data = new StringBuilder();
                    data.append(coreRegion.isNoExplosion() ? "1" : "0").append(",");
                    data.append(coreRegion.isNoPvP() ? "1" : "0").append(",");
                    data.append(coreRegion.getDamageBoostLevel()).append(",");
                    data.append(coreRegion.getHealthBoostLevel()).append(",");
                    data.append(coreRegion.isNoMobSpawn() ? "1" : "0").append(",");
                    data.append(coreRegion.isAutoHeal() ? "1" : "0").append(",");
                    data.append(coreRegion.isSpeedBoost() ? "1" : "0").append(",");
                    data.append(coreRegion.isNoFallDamage() ? "1" : "0").append(",");
                    data.append(coreRegion.isAntiEnderman() ? "1" : "0").append(",");
                    data.append(coreRegion.isResourceGenerator() ? "1" : "0").append(",");
                    data.append(coreRegion.getFixedTime()).append(",");
                    data.append(coreRegion.isCoreTeleport() ? "1" : "0").append(",");
                    data.append(coreRegion.isNoHunger() ? "1" : "0").append(",");
                    data.append(coreRegion.isAntiPhantom() ? "1" : "0");
                    pdc.set(key, org.bukkit.persistence.PersistentDataType.STRING, data.toString());

                    // Store members
                    if (!coreRegion.getMembers().isEmpty()) {
                        org.bukkit.NamespacedKey membersKey = new org.bukkit.NamespacedKey("coreprotect",
                                "core_members");
                        StringBuilder membersData = new StringBuilder();
                        for (UUID member : coreRegion.getMembers()) {
                            if (membersData.length() > 0)
                                membersData.append(";");
                            membersData.append(member.toString());
                        }
                        pdc.set(membersKey, org.bukkit.persistence.PersistentDataType.STRING, membersData.toString());
                    }

                    dropItem.setItemMeta(meta);
                }

                block.getWorld().dropItemNaturally(block.getLocation(), dropItem);
            }
            plugin.getProtectionManager().unlockLocation(coreLoc);
        });
    }

    // Core placement animation is handled exclusively by CorePlaceListener — do NOT duplicate here

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("coreprotect.admin"))
            return;

        // Proteger globalmente cualquier bloque dentro del Dominio (VoidArena)
        if (plugin.getShadowHunterManager() != null
                && plugin.getShadowHunterManager().isInsideAnyVoidArena(event.getBlock().getLocation())) {
            event.setCancelled(true);
            player.sendMessage(SmallCaps.convert("§c§l✖ §cNo puedes construir en la Expansión de Dominio."));
            return;
        }

        // Check spawn protection first
        Location placeLoc = event.getBlock().getLocation();
        if (plugin.getProtectionManager().isSpawnProtected(placeLoc)) {
            boolean isBed = event.getBlockPlaced().getType().name().endsWith("_BED");
            org.bukkit.World.Environment env = event.getBlock().getWorld().getEnvironment();
            boolean isAllowedBed = isBed && (env == org.bukkit.World.Environment.NETHER || env == org.bukkit.World.Environment.THE_END);
            
            if (!isAllowedBed && env != org.bukkit.World.Environment.THE_END && !player.hasPermission("wardstone.admin")) {
                plugin.getMessageManager().send(player, "protection.cannot-build");
                SoundManager.playProtectionDenied(player.getLocation());
                event.setCancelled(true);
                return;
            }
        }
        
        // Block placement adjacent to spawn (prevents water/lava flow exploits)
        if (!player.hasPermission("wardstone.admin")) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        Location adjacentLoc = placeLoc.clone().add(dx, dy, dz);
                        if (plugin.getProtectionManager().isSpawnProtected(adjacentLoc)) {
                            org.bukkit.World.Environment env = event.getBlock().getWorld().getEnvironment();
                            if (env != org.bukkit.World.Environment.THE_END) {
                                plugin.getMessageManager().send(player, "protection.cannot-build");
                                SoundManager.playProtectionDenied(player.getLocation());
                                event.setCancelled(true);
                                return;
                            }
                        }
                    }
                }
            }
        }

        ProtectedRegion region = plugin.getProtectionManager().getRegionAt(event.getBlock().getLocation());
        if (region != null && !region.canAccess(player.getUniqueId())) {
            plugin.getMessageManager().send(player, "protection.cannot-build");
            SoundManager.playProtectionDenied(player.getLocation());
            event.setCancelled(true);
        }
    }

    // === RIGHT-CLICK CORE -> OPEN MANAGEMENT GUI ===
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null)
            return;
        Player player = event.getPlayer();

        // Check if location is locked (animation in progress)
        boolean isLocked = plugin.getProtectionManager().isLocked(event.getClickedBlock().getLocation());

        // Right-click on core -> Open Management GUI
        ProtectedRegion interactCore = plugin.getProtectionManager()
                .getRegionByCore(event.getClickedBlock().getLocation());

        // If locked but the region already exists, auto-unlock (stuck core recovery)
        if (isLocked && interactCore != null) {
            plugin.getProtectionManager().unlockLocation(event.getClickedBlock().getLocation());
            isLocked = false;
        }

        if (isLocked) {
            event.setCancelled(true);
            return;
        }
        if (interactCore != null && event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            if (interactCore.canAccess(player.getUniqueId())) {
                event.setCancelled(true);
                // Track which region this player is managing
                GUIListener.setPlayerRegion(player.getUniqueId(), interactCore.getId());
                SoundManager.playGUIOpen(player.getLocation());
                new CoreManagementGUI(plugin).open(player, interactCore);
                return;
            }
        }

        // Left-click on core -> break core (for unbreakable blocks like REINFORCED_DEEPSLATE
        // where BlockDamageEvent never fires in survival because hardness is -1)
        if (interactCore != null && event.getAction() == org.bukkit.event.block.Action.LEFT_CLICK_BLOCK) {
            event.setCancelled(true);
            if (interactCore.getOwner().equals(player.getUniqueId()) || player.hasPermission("coreprotect.admin")) {
                handleCoreBreak(player, interactCore, event.getClickedBlock());
            } else {
                plugin.getMessageManager().send(player, "protection.cannot-break");
                SoundManager.playProtectionDenied(player.getLocation());
            }
            return;
        }

        // Block crop trampling (farmland + turtle eggs)
        if (event.getAction() == org.bukkit.event.block.Action.PHYSICAL) {
            Material blockType = event.getClickedBlock().getType();
            ProtectedRegion region = plugin.getProtectionManager()
                    .getRegionAt(event.getClickedBlock().getLocation());

            // Crop trampling protection
            if (blockType == Material.FARMLAND || blockType == Material.TURTLE_EGG) {
                if (region != null) {
                    event.setCancelled(true);
                    return;
                }
            }
        }

        if (player.hasPermission("coreprotect.admin"))
            return;

        // Bloquear contenedores, aldeanos, puertas, trampillas, fence gates, palancas y botones
        Material clickedType = event.getClickedBlock().getType();
        if (!isContainer(clickedType) && !isVillager(clickedType) && !isDoorOrTrapdoor(clickedType) 
            && clickedType != Material.LEVER && !clickedType.name().endsWith("_BUTTON")) {
            return;
        }

        ProtectedRegion region = plugin.getProtectionManager().getRegionAt(event.getClickedBlock().getLocation());
        if (region == null)
            return;

        if (!region.canAccess(player.getUniqueId())) {
            plugin.getMessageManager().send(player, "protection.cannot-interact");
            SoundManager.playProtectionDenied(player.getLocation());
            event.setCancelled(true);
        }
    }

    // === PROTECCIÓN DE MARCOS DE ÍTEMS Y SOPORTES DE ARMADURAS EN ZONAS PROTEGIDAS ===
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("coreprotect.admin")) return;

        org.bukkit.entity.Entity entity = event.getRightClicked();
        if (!(entity instanceof org.bukkit.entity.ItemFrame) && !(entity instanceof org.bukkit.entity.ArmorStand)) return;

        if (plugin.getProtectionManager().isSpawnProtected(entity.getLocation())) {
            org.bukkit.World.Environment env = entity.getWorld().getEnvironment();
            if (env != org.bukkit.World.Environment.THE_END) {
                plugin.getMessageManager().send(player, "protection.cannot-interact");
                SoundManager.playProtectionDenied(player.getLocation());
                event.setCancelled(true);
            }
            return;
        }

        ProtectedRegion region = plugin.getProtectionManager().getRegionAt(entity.getLocation());
        if (region == null) return;

        if (!region.canAccess(player.getUniqueId())) {
            plugin.getMessageManager().send(player, "protection.cannot-interact");
            SoundManager.playProtectionDenied(player.getLocation());
            event.setCancelled(true);
        }
    }

    // === PROTECCIÓN DE SOPORTES DE ARMADURAS: MANIPULAR ITEMS ===
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onArmorStandManipulate(org.bukkit.event.player.PlayerArmorStandManipulateEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("coreprotect.admin")) return;

        ProtectedRegion region = plugin.getProtectionManager().getRegionAt(event.getRightClicked().getLocation());
        if (region == null) return;

        if (!region.canAccess(player.getUniqueId())) {
            plugin.getMessageManager().send(player, "protection.cannot-interact");
            SoundManager.playProtectionDenied(player.getLocation());
            event.setCancelled(true);
        }
    }

    // === EXPLOSION PROTECTION (default + enhanced with upgrade) ===
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        // Full protection in KOTH and boss arena worlds
        String worldName = event.getLocation().getWorld().getName().toLowerCase();
        if (worldName.equals("koth") || worldName.startsWith("bossarena_")) {
            event.blockList().clear();
            return;
        }
        event.blockList().removeIf(block -> {
            ProtectedRegion region = plugin.getProtectionManager().getRegionAt(block.getLocation());
            return region != null;
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(org.bukkit.event.block.BlockExplodeEvent event) {
        // Full protection in KOTH and boss arena worlds
        String worldName = event.getBlock().getWorld().getName().toLowerCase();
        if (worldName.equals("koth") || worldName.startsWith("bossarena_")) {
            event.blockList().clear();
            return;
        }
        event.blockList().removeIf(block -> {
            ProtectedRegion region = plugin.getProtectionManager().getRegionAt(block.getLocation());
            return region != null;
        });
    }

    // === PROTECCIÓN DE ENTIDADES EN ZONAS PROTEGIDAS (PvP + Mobs) ===
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        // Encontrar al atacante (directo o proyectil)
        Player attacker = null;
        if (event.getDamager() instanceof Player) {
            attacker = (Player) event.getDamager();
        } else if (event.getDamager() instanceof org.bukkit.entity.Projectile) {
            org.bukkit.entity.Projectile projectile = (org.bukkit.entity.Projectile) event.getDamager();
            if (projectile.getShooter() instanceof Player) {
                attacker = (Player) projectile.getShooter();
            }
        }

        // Si no es un jugador atacando, salir
        if (attacker == null)
            return;

        // Admins pueden hacer lo que quieran
        if (attacker.hasPermission("coreprotect.admin"))
            return;

        // === PROTECCIÓN DE MARCOS DE ÍTEMS Y ENDER CRYSTALS ===
        if (event.getEntity() instanceof org.bukkit.entity.ItemFrame || event.getEntity() instanceof org.bukkit.entity.EnderCrystal) {
            
            // Allow destroying Ender Crystals in The End (Spawn override)
            if (event.getEntity() instanceof org.bukkit.entity.EnderCrystal) {
                if (plugin.getProtectionManager().isSpawnProtected(event.getEntity().getLocation())) {
                    org.bukkit.World.Environment env = event.getEntity().getWorld().getEnvironment();
                    if (env == org.bukkit.World.Environment.THE_END || env == org.bukkit.World.Environment.NETHER) {
                        return; // Permitido
                    } else if (!attacker.hasPermission("wardstone.admin")) {
                        plugin.getMessageManager().send(attacker, "protection.cannot-interact");
                        SoundManager.playProtectionDenied(attacker.getLocation());
                        event.setCancelled(true);
                        return;
                    }
                }
            }
            
            ProtectedRegion frameRegion = plugin.getProtectionManager().getRegionAt(event.getEntity().getLocation());
            if (frameRegion != null && !frameRegion.canAccess(attacker.getUniqueId())) {
                plugin.getMessageManager().send(attacker, "protection.cannot-interact");
                SoundManager.playProtectionDenied(attacker.getLocation());
                event.setCancelled(true);
            }
            return;
        }

        // === PROTECCIÓN DE MOBS EN ZONAS PROTEGIDAS ===
        if (!(event.getEntity() instanceof Player)) {
            if (!(event.getEntity() instanceof org.bukkit.entity.LivingEntity)) return;
            org.bukkit.entity.LivingEntity entity = (org.bukkit.entity.LivingEntity) event.getEntity();

            // Permitir atacar CUALQUIER mob que pertenezca a una misión
            if (plugin.getShadowHunterManager() != null
                    && plugin.getShadowHunterManager().isMissionMob(entity.getUniqueId())) {
                return;
            }

            // Permitir atacar mobs hostiles (Monster) en cualquier zona protegida
            if (entity instanceof org.bukkit.entity.Monster) {
                return;
            }

            // Para mobs pasivos/neutrales, aplicar protección normal
            ProtectedRegion mobRegion = plugin.getProtectionManager().getRegionAt(event.getEntity().getLocation());
            if (mobRegion != null && !mobRegion.canAccess(attacker.getUniqueId())) {
                event.setCancelled(true);
                plugin.getMessageManager().send(attacker, "protection.cannot-attack-mob");
                return;
            }
            return;
        }

        // === PVP: La víctima es un jugador ===
        Player victim = (Player) event.getEntity();

        // === EXCEPCIÓN: MINIJUEGOS ===
        // La protección de nuevos jugadores no aplica en minijuegos
        if (attacker.getWorld().getName().equals("minigames") || victim.getWorld().getName().equals("minigames")) {
            return; // Skip new player protection in minigames
        }

        // === PROTECCIÓN DE NUEVOS JUGADORES: 3h sin PvP ===
        int NEW_PLAYER_TICKS = 3 * 60 * 60 * 20; // 3 horas en ticks
        int attackerPlaytime = attacker.getStatistic(org.bukkit.Statistic.PLAY_ONE_MINUTE);
        int victimPlaytime = victim.getStatistic(org.bukkit.Statistic.PLAY_ONE_MINUTE);
        if (attackerPlaytime < NEW_PLAYER_TICKS) {
            event.setCancelled(true);
            long minutesLeft = (NEW_PLAYER_TICKS - attackerPlaytime) / 20 / 60;
            long hoursLeft = minutesLeft / 60;
            long minsLeft = minutesLeft % 60;
            attacker.sendMessage(com.moonlight.coreprotect.util.SmallCaps.convert(
                    "§e§l🛡 §fTienes protección de nuevo jugador. §7No puedes hacer PvP por §e" + hoursLeft + "h " + minsLeft + "m§7."));
            victim.sendMessage(com.moonlight.coreprotect.util.SmallCaps.convert(
                    "§e§l🛡 §f" + attacker.getName() + " §7intentó atacarte pero tiene protección de nuevo jugador activa."));
            return;
        }
        if (victimPlaytime < NEW_PLAYER_TICKS) {
            event.setCancelled(true);
            long minutesLeft = (NEW_PLAYER_TICKS - victimPlaytime) / 20 / 60;
            long hoursLeft = minutesLeft / 60;
            long minsLeft = minutesLeft % 60;
            attacker.sendMessage(com.moonlight.coreprotect.util.SmallCaps.convert(
                    "§e§l🛡 §f" + victim.getName() + " §7es un jugador nuevo y tiene protección contra PvP. §7(§e" + hoursLeft + "h " + minsLeft + "m§7 restantes)"));
            victim.sendMessage(com.moonlight.coreprotect.util.SmallCaps.convert(
                    "§e§l🛡 §f" + attacker.getName() + " §7intentó atacarte. §fTienes protección de nuevo jugador §7por §e" + hoursLeft + "h " + minsLeft + "m§7."));
            return;
        }

        // Bloquear PvP completamente en El End
        if (victim.getWorld().getEnvironment() == org.bukkit.World.Environment.THE_END) {
            event.setCancelled(true);
            plugin.getMessageManager().send(attacker, "protection.cannot-interact");
            return;
        }

        // === SPAWN ZONES (prioridad máxima sobre regiones) ===
        if (victim.getWorld().getEnvironment() == org.bukkit.World.Environment.NORMAL) {
            boolean victimInCore = plugin.getProtectionManager().isSpawnCore(victim.getLocation());
            boolean attackerInCore = plugin.getProtectionManager().isSpawnCore(attacker.getLocation());
            boolean victimInSpawn = plugin.getProtectionManager().isSpawnProtected(victim.getLocation());
            boolean attackerInSpawn = plugin.getProtectionManager().isSpawnProtected(attacker.getLocation());

            // Spawn Core = zona segura, NO PVP
            if (victimInCore || attackerInCore) {
                event.setCancelled(true);
                return;
            }

            // Spawn PVP ring (fuera del core pero dentro del spawn) = PVP permitido libre
            if (victimInSpawn && attackerInSpawn) {
                return; // PVP libre en el anillo PVP
            }
        }

        // Obtener regiones de ambos jugadores
        ProtectedRegion victimRegion = plugin.getProtectionManager().getRegionAt(victim.getLocation());
        ProtectedRegion attackerRegion = plugin.getProtectionManager().getRegionAt(attacker.getLocation());

        // PRIORIDAD 1: Bloquear PvP si la víctima está en una región con Anti-PvP activado
        if (victimRegion != null && victimRegion.isNoPvP()) {
            event.setCancelled(true);
            plugin.getMessageManager().send(attacker, "upgrades.no-pvp-zone");
            return;
        }

        // PRIORIDAD 2: Bloquear PvP si el atacante está en una región con Anti-PvP activado
        if (attackerRegion != null && attackerRegion.isNoPvP()) {
            event.setCancelled(true);
            plugin.getMessageManager().send(attacker, "upgrades.no-pvp-zone");
            return;
        }

        // PRIORIDAD 3: Verificar si están en combate para permitir PvP en zonas protegidas
        boolean attackerInCombat = plugin.getCombatTagManager().isInCombat(attacker.getUniqueId());
        boolean victimInCombat = plugin.getCombatTagManager().isInCombat(victim.getUniqueId());

        if (attackerInCombat && victimInCombat) {
            // PvP permitido durante combate (si no hay mejora NoPvP)
            // Aplicar damage boost si corresponde
            if (attackerRegion != null && attackerRegion.canAccess(attacker.getUniqueId())
                    && attackerRegion.getDamageBoostLevel() > 0) {
                double multiplier = attackerRegion.getDamageBoostMultiplier();
                event.setDamage(event.getDamage() * multiplier);
            }
            return;
        }

        // PRIORIDAD 4: Bloquear PvP si la víctima está en una región protegida donde no tiene acceso
        // (esto previene que alguien ataque a un jugador en su propia base)
        if (victimRegion != null && !victimRegion.canAccess(attacker.getUniqueId())) {
            event.setCancelled(true);
            plugin.getMessageManager().send(attacker, "protection.cannot-attack-player");
            return;
        }

        // PRIORIDAD 5: Bloquear PvP si el atacante está en una región protegida donde no tiene acceso
        if (attackerRegion != null && !attackerRegion.canAccess(attacker.getUniqueId())) {
            event.setCancelled(true);
            plugin.getMessageManager().send(attacker, "protection.cannot-attack-from-here");
            return;
        }

        // === DAMAGE BOOST (atacante en su zona con mejora de daño) ===
        if (attackerRegion != null && attackerRegion.canAccess(attacker.getUniqueId())
                && attackerRegion.getDamageBoostLevel() > 0) {
            double multiplier = attackerRegion.getDamageBoostMultiplier();
            event.setDamage(event.getDamage() * multiplier);
        }
    }

    // === SPAWN CORE (zona segura): CANCEL ALL DAMAGE ===
    // Solo en la zona interna (spawnCore) se cancela todo daño.
    // La zona exterior (spawn PVP ring) permite PvP y habilidades.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDamageInSpawnZone(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player))
            return;
        Player player = (Player) event.getEntity();

        // Solo en overworld
        if (player.getWorld().getEnvironment() != org.bukkit.World.Environment.NORMAL)
            return;

        // Solo cancelar daño en la zona interna (core seguro), NO en el anillo PVP
        if (plugin.getProtectionManager().isSpawnCore(player.getLocation())) {
            event.setCancelled(true);
        }
    }

    // === FALL DAMAGE PROTECTION ===
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player))
            return;
        Player player = (Player) event.getEntity();

        ProtectedRegion region = plugin.getProtectionManager().getRegionAt(player.getLocation());
        if (region == null)
            return;

        if (!region.canAccess(player.getUniqueId()))
            return;

        // No fall damage
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL && region.isNoFallDamage()) {
            event.setCancelled(true);
        }

        // No explosion damage to members
        if (region.isNoExplosion() &&
                (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION ||
                        event.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION)) {
            event.setCancelled(true);
        }
    }

    // === VEX PROTECTION — Block vex from entering protected zones and spawn ===
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVexMove(org.bukkit.event.entity.EntityTeleportEvent event) {
        if (event.getEntityType() != EntityType.VEX) return;
        Location to = event.getTo();
        if (to == null) return;
        if (plugin.getProtectionManager().isSpawnProtected(to)
                || plugin.getProtectionManager().getRegionAt(to) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVexSpawn(CreatureSpawnEvent event) {
        if (event.getEntityType() == EntityType.VEX) {
            Location loc = event.getLocation();
            if (plugin.getProtectionManager().isSpawnProtected(loc)
                    || plugin.getProtectionManager().getRegionAt(loc) != null) {
                event.setCancelled(true);
                return;
            }
        }
    }

    // === MOB SPAWN PREVENTION + ANTI-PHANTOM ===
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        // Wandering Trader solo puede spawnear en "world"
        if (event.getEntityType() == EntityType.WANDERING_TRADER
                && !event.getEntity().getWorld().getName().equals("world")) {
            event.setCancelled(true);
            return;
        }

        ProtectedRegion region = plugin.getProtectionManager().getRegionAt(event.getLocation());
        if (region == null)
            return;

        // Anti-Phantom upgrade: block phantom spawns
        if (event.getEntityType() == EntityType.PHANTOM && region.isAntiPhantom()) {
            event.setCancelled(true);
            return;
        }

        // Anti-Mob upgrade: block hostile mob spawns
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.CUSTOM ||
                event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER_EGG) {
            return;
        }

        // Allow ShadowHunter NPC (Villager spawned by plugin)
        if (event.getEntityType() == EntityType.VILLAGER && event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.CUSTOM) {
            return;
        }

        if (event.getEntity() instanceof org.bukkit.entity.Monster && region.isNoMobSpawn()
                && event.getEntityType() != EntityType.WANDERING_TRADER) {
            event.setCancelled(true);
        }
    }

    // === ANTI-ZOMBIE IN SPAWN CORE ===
    @EventHandler(priority = EventPriority.MONITOR)
    public void onZombieInSpawnCore(org.bukkit.event.entity.EntitySpawnEvent event) {
        if (event.getEntity() instanceof org.bukkit.entity.Zombie) {
            if (plugin.getProtectionManager().isSpawnCore(event.getLocation())) {
                event.getEntity().remove();
            }
        }
    }
    
    // Scheduled task to check zombies and vex in spawn core AND hostile mobs in protected regions
    public void checkZombiesInSpawnCore() {
        for (org.bukkit.World world : org.bukkit.Bukkit.getWorlds()) {
            if (!world.getName().equals("world")) continue;
            
            for (org.bukkit.entity.Entity entity : world.getEntities()) {
                if (entity instanceof org.bukkit.entity.Zombie) {
                    if (plugin.getProtectionManager().isSpawnCore(entity.getLocation())) {
                        entity.remove();
                        continue;
                    }
                }
                // Remove vex from protected zones and spawn
                if (entity.getType() == EntityType.VEX) {
                    Location loc = entity.getLocation();
                    if (plugin.getProtectionManager().isSpawnProtected(loc)
                            || plugin.getProtectionManager().getRegionAt(loc) != null) {
                        entity.remove();
                        continue;
                    }
                }
                // Remove hostile mobs that wander into protected regions with anti-mob upgrade
                if (entity instanceof org.bukkit.entity.Monster) {
                    ProtectedRegion region = plugin.getProtectionManager().getRegionAt(entity.getLocation());
                    if (region != null && region.isNoMobSpawn()) {
                        entity.remove();
                    }
                }
            }
        }
    }

    // === PREVENT HOSTILE MOB TARGET INSIDE PROTECTED REGIONS ===
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMobTargetInProtection(org.bukkit.event.entity.EntityTargetLivingEntityEvent event) {
        if (!(event.getEntity() instanceof org.bukkit.entity.Monster)) return;
        if (!(event.getTarget() instanceof Player)) return;

        ProtectedRegion region = plugin.getProtectionManager().getRegionAt(event.getTarget().getLocation());
        if (region != null && region.isNoMobSpawn()) {
            event.setCancelled(true);
        }
    }

    // === ANTI-ENDERMAN (block pickup/place) ===
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityChangeBlock(org.bukkit.event.entity.EntityChangeBlockEvent event) {
        if (event.getEntity() instanceof org.bukkit.entity.Enderman) {
            ProtectedRegion region = plugin.getProtectionManager().getRegionAt(event.getBlock().getLocation());
            if (region != null && region.isAntiEnderman()) {
                event.setCancelled(true);
            }
        }
    }

    // === NO HUNGER ===
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFoodLevelChange(org.bukkit.event.entity.FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player))
            return;
        Player player = (Player) event.getEntity();
        if (event.getFoodLevel() < player.getFoodLevel()) {
            ProtectedRegion region = plugin.getProtectionManager().getRegionAt(player.getLocation());
            if (region != null && region.isNoHunger() && region.canAccess(player.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }

    // === BUCKET PROTECTION (lava/water placement) ===
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("coreprotect.admin"))
            return;

        // Check spawn protection first
        Location bucketLoc = event.getBlock().getLocation();
        if (plugin.getProtectionManager().isSpawnProtected(bucketLoc)) {
            org.bukkit.World.Environment env = event.getBlock().getWorld().getEnvironment();
            if (env != org.bukkit.World.Environment.THE_END) {
                plugin.getMessageManager().send(player, "protection.cannot-build");
                SoundManager.playProtectionDenied(player.getLocation());
                event.setCancelled(true);
                return;
            }
        }
        
        // Block bucket placement adjacent to spawn (prevents water/lava flow exploits)
        if (!player.hasPermission("wardstone.admin")) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        Location adjacentLoc = bucketLoc.clone().add(dx, dy, dz);
                        if (plugin.getProtectionManager().isSpawnProtected(adjacentLoc)) {
                            org.bukkit.World.Environment env = event.getBlock().getWorld().getEnvironment();
                            if (env != org.bukkit.World.Environment.THE_END) {
                                plugin.getMessageManager().send(player, "protection.cannot-build");
                                SoundManager.playProtectionDenied(player.getLocation());
                                event.setCancelled(true);
                                return;
                            }
                        }
                    }
                }
            }
        }

        ProtectedRegion region = plugin.getProtectionManager().getRegionAt(event.getBlock().getLocation());
        if (region == null)
            return;

        if (!region.canAccess(player.getUniqueId())) {
            plugin.getMessageManager().send(player, "protection.cannot-build");
            SoundManager.playProtectionDenied(player.getLocation());
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("coreprotect.admin"))
            return;

        // Check spawn protection first
        if (plugin.getProtectionManager().isSpawnProtected(event.getBlock().getLocation())) {
            org.bukkit.World.Environment env = event.getBlock().getWorld().getEnvironment();
            if (env != org.bukkit.World.Environment.THE_END) {
                plugin.getMessageManager().send(player, "protection.cannot-interact");
                SoundManager.playProtectionDenied(player.getLocation());
                event.setCancelled(true);
                return;
            }
        }

        ProtectedRegion region = plugin.getProtectionManager().getRegionAt(event.getBlock().getLocation());
        if (region == null)
            return;

        if (!region.canAccess(player.getUniqueId())) {
            plugin.getMessageManager().send(player, "protection.cannot-interact");
            SoundManager.playProtectionDenied(player.getLocation());
            event.setCancelled(true);
        }
    }

    // === LIQUID FLOW PROTECTION (prevent lava/water flowing into protected zones)
    // ===
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLiquidFlow(BlockFromToEvent event) {
        Location toLoc = event.getToBlock().getLocation();
        
        // Block liquid flowing into spawn protection
        if (plugin.getProtectionManager().isSpawnProtected(toLoc)) {
            org.bukkit.World.Environment env = toLoc.getWorld().getEnvironment();
            if (env != org.bukkit.World.Environment.THE_END) {
                event.setCancelled(true);
                return;
            }
        }
        
        ProtectedRegion fromRegion = plugin.getProtectionManager().getRegionAt(event.getBlock().getLocation());
        ProtectedRegion toRegion = plugin.getProtectionManager().getRegionAt(toLoc);

        // Block liquid flowing INTO a protected region from outside
        if (toRegion != null && fromRegion == null) {
            event.setCancelled(true);
        }
        // Block liquid flowing into a DIFFERENT protected region
        if (toRegion != null && fromRegion != null && !toRegion.getId().equals(fromRegion.getId())) {
            event.setCancelled(true);
        }
    }

    // Buff effects (speed, auto-heal, health boost) are now handled by
    // ProtectionManager.startBuffTask() repeating task to prevent flickering

    // === BLOQUEAR NUEVOS JUGADORES EN ZONAS PVP (spawn PVP ring) ===
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onNewPlayerEnterPvpZone(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return; // Solo rotación de cabeza, no movimiento
        }

        Player player = event.getPlayer();
        if (player.hasPermission("wardstone.admin")) return;
        if (player.getWorld().getEnvironment() != org.bukkit.World.Environment.NORMAL) return;

        int NEW_PLAYER_TICKS = 3 * 60 * 60 * 20; // 3 horas en ticks
        int playtime = player.getStatistic(org.bukkit.Statistic.PLAY_ONE_MINUTE);
        if (playtime >= NEW_PLAYER_TICKS) return; // No es nuevo

        Location to = event.getTo();
        boolean inSpawn = plugin.getProtectionManager().isSpawnProtected(to);
        boolean inCore = plugin.getProtectionManager().isSpawnCore(to);

        // El anillo PVP del spawn es: dentro del spawn pero fuera del core
        if (inSpawn && !inCore) {
            event.setCancelled(true);

            // Mensaje con cooldown (cada 3 segundos)
            long now = System.currentTimeMillis();
            Long lastMsg = newPlayerPvpZoneCooldown.get(player.getUniqueId());
            if (lastMsg == null || now - lastMsg > 3000) {
                newPlayerPvpZoneCooldown.put(player.getUniqueId(), now);
                long minutesLeft = (NEW_PLAYER_TICKS - playtime) / 20 / 60;
                long hoursLeft = minutesLeft / 60;
                long minsLeft = minutesLeft % 60;
                player.sendMessage(SmallCaps.convert(
                        "§e§l🛡 §fTienes protección de nuevo jugador. §7No puedes entrar a la zona PvP. §7(§e" + hoursLeft + "h " + minsLeft + "m§7 restantes)"));
            }
        }
    }

    // === BLOQUEAR TELETRANSPORTE A ZONAS PROTEGIDAS DURANTE PvP ===
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();

        // Admins pueden teletransportarse a zonas protegidas en PvP
        if (player.hasPermission("wardstone.admin")) {
            return;
        }

        // Verificar si está en combate
        if (!plugin.getCombatTagManager().isInCombat(player.getUniqueId())) {
            return;
        }

        // Permitir teletransportes de finishers, dash del arco, y ENDER PEARLS durante combate
        if (player.hasMetadata("finisher_teleport") || player.hasMetadata("bow_dash") 
                || event.getCause() == PlayerTeleportEvent.TeleportCause.ENDER_PEARL) {
            return;
        }

        // Verificar si está intentando teletransportarse a una zona protegida
        ProtectedRegion toRegion = plugin.getProtectionManager().getRegionAt(event.getTo());
        if (toRegion == null) {
            return;
        }

        // Verificar si tiene acceso a la región
        if (toRegion.canAccess(player.getUniqueId())) {
            return; // Puede teletransportarse a su propia región
        }

        // Bloquear teletransporte a zona protegida ajena durante PvP
        event.setCancelled(true);

        // Mensaje y efectos
        player.sendMessage(SmallCaps.convert("§c§l⚔ §fNo puedes teletransportarte a terrenos protegidos durante el combate PvP."));
        SoundManager.playProtectionDenied(player.getLocation());

        // Debug log
        plugin.getLogger().info(
                "[Protection] Bloqueado teletransporte a zona protegida para " + player.getName() + " en combate");
    }

    private boolean isContainer(org.bukkit.Material material) {
        switch (material) {
            case CHEST:
            case TRAPPED_CHEST:
            case BARREL:
            case SHULKER_BOX:
            case WHITE_SHULKER_BOX:
            case ORANGE_SHULKER_BOX:
            case MAGENTA_SHULKER_BOX:
            case LIGHT_BLUE_SHULKER_BOX:
            case YELLOW_SHULKER_BOX:
            case LIME_SHULKER_BOX:
            case PINK_SHULKER_BOX:
            case GRAY_SHULKER_BOX:
            case LIGHT_GRAY_SHULKER_BOX:
            case CYAN_SHULKER_BOX:
            case PURPLE_SHULKER_BOX:
            case BLUE_SHULKER_BOX:
            case BROWN_SHULKER_BOX:
            case GREEN_SHULKER_BOX:
            case RED_SHULKER_BOX:
            case BLACK_SHULKER_BOX:
            case FURNACE:
            case BLAST_FURNACE:
            case SMOKER:
            case HOPPER:
            case DISPENSER:
            case DROPPER:
            case BREWING_STAND:
                return true;
            default:
                return false;
        }
    }

    // === PISTON PROTECTION (prevent pushing/pulling into/from protected regions) ===
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block b : event.getBlocks()) {
            // Destino del bloque empujado
            Block destination = b.getRelative(event.getDirection());
            ProtectedRegion sourceRegion = plugin.getProtectionManager().getRegionAt(b.getLocation());
            ProtectedRegion destRegion = plugin.getProtectionManager().getRegionAt(destination.getLocation());

            // Bloquear si empuja bloques FUERA de una región protegida
            if (sourceRegion != null && destRegion == null) {
                event.setCancelled(true);
                fixStuckPiston(event.getBlock());
                return;
            }
            // Bloquear si empuja bloques DENTRO de una región protegida ajena
            if (destRegion != null && (sourceRegion == null || !sourceRegion.getId().equals(destRegion.getId()))) {
                event.setCancelled(true);
                fixStuckPiston(event.getBlock());
                return;
            }
        }
        // También verificar spawn protection
        for (Block b : event.getBlocks()) {
            Block destination = b.getRelative(event.getDirection());
            if (plugin.getProtectionManager().isSpawnProtected(destination.getLocation())) {
                event.setCancelled(true);
                fixStuckPiston(event.getBlock());
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block b : event.getBlocks()) {
            Block destination = b.getRelative(event.getDirection());
            ProtectedRegion sourceRegion = plugin.getProtectionManager().getRegionAt(b.getLocation());
            ProtectedRegion destRegion = plugin.getProtectionManager().getRegionAt(destination.getLocation());

            if (sourceRegion != null && destRegion == null) {
                event.setCancelled(true);
                fixStuckPiston(event.getBlock());
                return;
            }
            if (destRegion != null && (sourceRegion == null || !sourceRegion.getId().equals(destRegion.getId()))) {
                event.setCancelled(true);
                fixStuckPiston(event.getBlock());
                return;
            }
        }
        for (Block b : event.getBlocks()) {
            Block destination = b.getRelative(event.getDirection());
            if (plugin.getProtectionManager().isSpawnProtected(destination.getLocation())) {
                event.setCancelled(true);
                fixStuckPiston(event.getBlock());
                return;
            }
        }
    }

    /**
     * Fix para pistones stuck en Paper: fuerza una actualización de estado del bloque
     * en el siguiente tick para que el cliente y servidor se sincronicen.
     */
    private void fixStuckPiston(Block pistonBlock) {
        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // Forzar actualización del estado del bloque del pistón
            org.bukkit.block.data.BlockData data = pistonBlock.getBlockData();
            pistonBlock.setBlockData(data, true);

            // También actualizar el bloque adyacente (piston head si existe)
            Block adjacent = pistonBlock.getRelative(
                    ((org.bukkit.block.data.Directional) data).getFacing());
            if (adjacent.getType().name().contains("PISTON_HEAD")) {
                adjacent.setType(Material.AIR, true);
            }
        }, 1L);
    }

    private boolean isDoorOrTrapdoor(org.bukkit.Material material) {
        String name = material.name();
        return name.contains("DOOR") || name.contains("FENCE_GATE");
    }

    private boolean isVillager(org.bukkit.Material material) {
        switch (material) {
            case CARTOGRAPHY_TABLE:
            case SMITHING_TABLE:
            case FLETCHING_TABLE:
            case COMPOSTER:
            case BARREL:
            case BELL:
            case BREWING_STAND:
            case CAULDRON:
            case LOOM:
            case GRINDSTONE:
            case STONECUTTER:
                return true;
            default:
                return false;
        }
    }

    // ================= FORCING END SPAWN BYPASS & VIEW DISTANCE =================

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onForceEndSpawnPlace(BlockPlaceEvent event) {
        if (event.getBlock().getWorld().getEnvironment() == org.bukkit.World.Environment.THE_END) {
            if (plugin.getProtectionManager().isSpawnProtected(event.getBlock().getLocation())) {
                ProtectedRegion region = plugin.getProtectionManager().getRegionAt(event.getBlock().getLocation());
                if (region == null || region.canAccess(event.getPlayer().getUniqueId())) {
                    event.setCancelled(false);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onForceEndSpawnBreak(BlockBreakEvent event) {
        if (event.getBlock().getWorld().getEnvironment() == org.bukkit.World.Environment.THE_END) {
            if (plugin.getProtectionManager().isSpawnProtected(event.getBlock().getLocation())) {
                ProtectedRegion region = plugin.getProtectionManager().getRegionAt(event.getBlock().getLocation());
                if (region == null || region.canAccess(event.getPlayer().getUniqueId())) {
                    event.setCancelled(false);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onForceEndCrystalDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof org.bukkit.entity.EnderCrystal)) return;
        org.bukkit.World.Environment env = event.getEntity().getWorld().getEnvironment();
        if (env == org.bukkit.World.Environment.THE_END || env == org.bukkit.World.Environment.NETHER) {
            if (plugin.getProtectionManager().isSpawnProtected(event.getEntity().getLocation())) {
                event.setCancelled(false);
            }
            
            // Fix hit cooldown
            if (event.getDamager() instanceof Player && env == org.bukkit.World.Environment.THE_END) {
                event.setCancelled(true);
                org.bukkit.entity.EnderCrystal crystal = (org.bukkit.entity.EnderCrystal) event.getEntity();
                org.bukkit.Location loc = crystal.getLocation();
                org.bukkit.block.Block block = loc.getBlock();
                
                // Remover fuego si existe
                if (block.getType() == org.bukkit.Material.FIRE) {
                    block.setType(org.bukkit.Material.AIR);
                }
                
                crystal.remove();
                
                // Crear explosión. Intentamos usar el método que toma Entity para que cuente como cristal
                try {
                    // API de versiones recientes: createExplosion(Entity source, float power, boolean setFire, boolean breakBlocks)
                    // o loc.getWorld().createExplosion(crystal, loc.getX(), loc.getY(), loc.getZ(), 6.0f, false, true);
                    loc.getWorld().getClass().getMethod("createExplosion", org.bukkit.entity.Entity.class, double.class, double.class, double.class, float.class, boolean.class, boolean.class)
                         .invoke(loc.getWorld(), crystal, loc.getX(), loc.getY(), loc.getZ(), 6.0f, false, true);
                } catch (Exception e) {
                    // Fallback para versiones más antiguas
                    loc.getWorld().createExplosion(loc.getX(), loc.getY(), loc.getZ(), 6.0f, false, true);
                }
            }
        }
    }

    @EventHandler
    public void onWorldChangeViewDistance(org.bukkit.event.player.PlayerChangedWorldEvent event) {
        handleViewDistance(event.getPlayer());
    }

    @EventHandler
    public void onPlayerJoinViewDistance(org.bukkit.event.player.PlayerJoinEvent event) {
        handleViewDistance(event.getPlayer());
    }

    @EventHandler
    public void onRespawnViewDistance(org.bukkit.event.player.PlayerRespawnEvent event) {
        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (event.getPlayer().isOnline()) {
                handleViewDistance(event.getPlayer());
            }
        }, 10L);
    }

    public void updateAllWorldsAndPlayersViewDistance() {
        for (org.bukkit.World world : plugin.getServer().getWorlds()) {
            int distance = (world.getEnvironment() == org.bukkit.World.Environment.THE_END) ? 20 : 5;
            try {
                world.getClass().getMethod("setViewDistance", int.class).invoke(world, distance);
            } catch (Exception ignored) {}
        }
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            handleViewDistance(player);
        }
    }

    private void handleViewDistance(Player player) {
        if (player.getWorld().getEnvironment() == org.bukkit.World.Environment.THE_END) {
            try {
                // Set view distance to 20 for The End
                player.getClass().getMethod("setViewDistance", int.class).invoke(player, 20);
            } catch (Exception ignored) {}
            try {
                player.getClass().getMethod("setSendViewDistance", int.class).invoke(player, 20);
            } catch (Exception ignored) {}
        } else {
            try {
                // Set view distance to 5 for Overworld and Nether
                player.getClass().getMethod("setViewDistance", int.class).invoke(player, 5);
            } catch (Exception ignored) {}
            try {
                player.getClass().getMethod("setSendViewDistance", int.class).invoke(player, 5);
            } catch (Exception ignored) {}
        }
    }

    // === PROTECCIÓN DE CARTELES — solo jugadores con core pueden editar ===
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("coreprotect.admin")) return;

        // Check if player owns at least one core
        if (!plugin.getProtectionManager().getRegionsByOwner(player.getUniqueId()).isEmpty()) return;

        event.setCancelled(true);
        player.sendMessage("§c§l⚠ §cNecesitas tener un Core colocado para editar carteles.");
        SoundManager.playProtectionDenied(player.getLocation());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSignInteract(PlayerInteractEvent event) {
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;
        if (!(event.getClickedBlock().getState() instanceof org.bukkit.block.Sign)) return;

        Player player = event.getPlayer();
        if (player.hasPermission("coreprotect.admin")) return;

        // Check if player owns at least one core
        if (!plugin.getProtectionManager().getRegionsByOwner(player.getUniqueId()).isEmpty()) return;

        event.setCancelled(true);
        player.sendMessage("§c§l⚠ §cNecesitas tener un Core colocado para editar carteles.");
        SoundManager.playProtectionDenied(player.getLocation());
    }

    // === EXPULSAR JUGADORES BANEADOS DE PROTECCIONES ===
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBannedPlayerEnterRegion(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();

        Location to = event.getTo();
        ProtectedRegion region = plugin.getProtectionManager().getRegionAt(to);
        if (region == null) return;
        if (!region.isBanned(player.getUniqueId())) return;

        // Cancelar el movimiento y teleportar al borde
        event.setCancelled(true);
        teleportToRegionEdge(player, region);

        // Mensaje con cooldown (cada 3 segundos)
        long now = System.currentTimeMillis();
        Long lastMsg = banKnockbackCooldown.get(player.getUniqueId());
        if (lastMsg == null || now - lastMsg > 3000) {
            banKnockbackCooldown.put(player.getUniqueId(), now);
            String ownerName = org.bukkit.Bukkit.getOfflinePlayer(region.getOwner()).getName();
            player.sendMessage(SmallCaps.convert("§c§l⚠ §cEstás baneado de la protección de §f" + (ownerName != null ? ownerName : "???") + "§c. ¡No puedes entrar!"));
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 0.5f);
        }
    }

    private void teleportToRegionEdge(Player target, ProtectedRegion region) {
        Location coreLoc = region.getCoreLocation();
        if (coreLoc == null) return;
        int halfSize = region.getEffectiveSize() / 2;
        World world = coreLoc.getWorld();

        // Calcular dirección desde core hacia el jugador
        org.bukkit.util.Vector dir = target.getLocation().toVector()
                .subtract(coreLoc.toVector());
        dir.setY(0);
        double playerDist = dir.length();
        if (playerDist < 0.01) {
            dir = new org.bukkit.util.Vector(1, 0, 0);
            playerDist = 1;
        }
        dir = dir.normalize();

        // Empezar desde el borde en dirección al jugador (o desde el jugador si está fuera)
        int startDist = (int) Math.max(halfSize + 1, Math.ceil(playerDist));

        // Expandir hacia afuera hasta encontrar zona segura fuera de la región
        for (int distance = startDist; distance <= startDist + 50; distance++) {
            double testX = coreLoc.getX() + dir.getX() * distance;
            double testZ = coreLoc.getZ() + dir.getZ() * distance;

            // Encontrar Y seguro primero
            int blockX = (int) testX;
            int blockZ = (int) testZ;
            int groundY = world.getHighestBlockYAt(blockX, blockZ);
            if (groundY < coreLoc.getY() - 5) groundY = (int) coreLoc.getY();
            Location testLoc = new Location(world, testX, groundY + 1, testZ);

            // Verificar que esté fuera de la región
            if (!region.contains(testLoc)) {
                // Verificar que el suelo sea sólido y el espacio de pies esté libre
                if (testLoc.clone().subtract(0, 1, 0).getBlock().getType().isSolid()
                        && !testLoc.getBlock().getType().isSolid()) {
                    target.teleport(testLoc);
                    target.setFallDistance(0);
                    return;
                }
            }
        }

        // Fallback: dirección X positiva simple
        double fallbackX = coreLoc.getX() + halfSize + 3;
        double fallbackZ = coreLoc.getZ();
        int fallbackY = world.getHighestBlockYAt((int) fallbackX, (int) fallbackZ);
        Location fallbackLoc = new Location(world, fallbackX, fallbackY + 1, fallbackZ);
        target.teleport(fallbackLoc);
        target.setFallDistance(0);
    }
}
