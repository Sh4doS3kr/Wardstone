package com.moonlight.coreprotect.shadowhunter;

import com.moonlight.coreprotect.CoreProtectPlugin;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;
import com.moonlight.coreprotect.util.SmallCaps;

/**
 * Listener para las habilidades de Fractura del Vacío (pico).
 */
public class VoidPickaxeListener implements Listener {

    private final CoreProtectPlugin plugin;
    private final Map<UUID, Long> vetaCooldown = new HashMap<>();
    private final Map<UUID, Long> ojoCooldown = new HashMap<>();

    private static final long VETA_CD = 4000;
    private static final long OJO_CD = 25000;

    // Minerales que detecta Ojo del Vacío
    private static final Set<Material> VALUABLE_ORES = new HashSet<>(Arrays.asList(
            Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE,
            Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE,
            Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE,
            Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE,
            Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE,
            Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE,
            Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE,
            Material.ANCIENT_DEBRIS, Material.NETHER_GOLD_ORE,
            Material.NETHER_QUARTZ_ORE));

    public VoidPickaxeListener(CoreProtectPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;

        ItemStack item = player.getInventory().getItemInMainHand();
        if (!VoidPickaxe.isVoidPickaxe(item, plugin))
            return;

        // Bloquear habilidades de pico en KOTH (excepto VALE_TODO) y Boss
        com.moonlight.coreprotect.bossarena.BossArenaManager bossManager = plugin.getBossArenaManager();
        boolean inBoss = bossManager != null && bossManager.isInAnyArena(player.getLocation());
        if (inBoss) {
            player.sendMessage(SmallCaps.convert("§c§l✖ §cNo puedes usar habilidades de este pico aquí."));
            return;
        }
        if (player.getWorld().getName().equals("koth")) {
            com.moonlight.coreprotect.koth.KothManager km = plugin.getKothManager();
            if (km == null || !km.isActive() || km.getCurrentMode() != com.moonlight.coreprotect.koth.KothMode.VALE_TODO) {
                player.sendMessage(SmallCaps.convert("§c§l✖ §cNo puedes usar habilidades de este pico en el mundo KOTH."));
                return;
            }
        }
        
        // Bloquear en spawn, zona AFK y cualquier zona protegida (excepto en combate PvP)
        if (plugin.getProtectionManager().isSpawnCore(player.getLocation())) {
            if (!plugin.getCombatTagManager().isInCombat(player.getUniqueId())) {
                player.sendMessage(SmallCaps.convert("§c§l✖ §cNo puedes usar habilidades en la zona protegida del spawn."));
                return;
            }
        }
        com.moonlight.coreprotect.core.ProtectedRegion region = plugin.getProtectionManager().getRegionAt(player.getLocation());
        if (region != null && !region.canAccess(player.getUniqueId())) {
            player.sendMessage(SmallCaps.convert("§c§l✖ §cNo puedes usar habilidades en la zona protegida de otro jugador."));
            return;
        }
        if (plugin.getAfkZoneManager() != null && plugin.getAfkZoneManager().isInZone(player.getLocation())) {
            player.sendMessage(SmallCaps.convert("§c§l✖ §cNo puedes usar habilidades en la zona AFK."));
            return;
        }

        // No activar si el bloque es interactivo
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
            Material type = event.getClickedBlock().getType();
            if (type.isInteractable() && !player.isSneaking()) {
                return;
            }
            event.setCancelled(true);
        }

        if (player.isSneaking()) {
            useOjoDelVacio(player);
        } else {
            useVetaSombria(player);
        }
    }

    // ==================== VETA SOMBRÍA ====================

    private void useVetaSombria(Player player) {
        UUID uid = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long lastUse = vetaCooldown.get(uid);
        if (lastUse != null) {
            long remaining = VETA_CD - (now - lastUse);
            if (remaining > 0) {
                player.sendTitle("",
                        "§8§lVeta Sombría §7- Cooldown: §e" + String.format("%.1f", remaining / 1000.0) + "s", 0, 20,
                        5);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.5f);
                return;
            }
        }
        vetaCooldown.put(uid, now);

        // Determinar bloque objetivo
        Block target = player.getTargetBlockExact(5);
        if (target == null || target.getType().isAir()) {
            player.sendMessage(SmallCaps.convert("§7Apunta a un bloque para minar 3x3."));
            vetaCooldown.remove(uid);
            return;
        }

        player.sendTitle("", SmallCaps.convert("§8§l⛏ Veta Sombría"), 0, 20, 5);
        player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_DIG, 0.8f, 1.5f);

        Location center = target.getLocation();
        World world = center.getWorld();

        // Recopilar bloques a minar (filtrados)
        java.util.List<Block> toBreak = new java.util.ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    Block b = world.getBlockAt(center.getBlockX() + dx, center.getBlockY() + dy,
                            center.getBlockZ() + dz);
                    if (b.getType().isAir() || b.getType() == Material.BEDROCK
                            || b.getType() == Material.BARRIER
                            || b.getType() == Material.OBSIDIAN)
                        continue;
                    if (b.getType().getHardness() < 0)
                        continue; // Irrompibles
                    // No romper bloques en zonas protegidas ni spawn
                    if (plugin.getProtectionManager().isSpawnProtected(b.getLocation()))
                        continue;
                    if (plugin.getProtectionManager().getRegionAt(b.getLocation()) != null
                            && !plugin.getProtectionManager().canBuild(player.getUniqueId(), b.getLocation()))
                        continue;
                    toBreak.add(b);
                }
            }
        }

        // Marcar al jugador como usando habilidad (para bypass de anticheat)
        org.bukkit.NamespacedKey abilityKey = new org.bukkit.NamespacedKey(plugin, "voidpickaxe_ability");
        player.getPersistentDataContainer().set(abilityKey,
                org.bukkit.persistence.PersistentDataType.LONG, System.currentTimeMillis());

        // Repartir la rotura en lotes de ~25 bloques por tick para evitar anticheat
        int batchSize = 25;
        int totalBatches = (int) Math.ceil(toBreak.size() / (double) batchSize);
        final int[] totalMined = {0};

        for (int batch = 0; batch < totalBatches; batch++) {
            int startIdx = batch * batchSize;
            int endIdx = Math.min(startIdx + batchSize, toBreak.size());
            java.util.List<Block> batchBlocks = toBreak.subList(startIdx, endIdx);
            final boolean isLastBatch = (batch == totalBatches - 1);

            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) return;
                ItemStack tool = player.getInventory().getItemInMainHand();
                for (Block b : batchBlocks) {
                    if (b.getType().isAir()) continue;
                    BlockBreakEvent breakEvent = new BlockBreakEvent(b, player);
                    plugin.getServer().getPluginManager().callEvent(breakEvent);
                    if (!breakEvent.isCancelled()) {
                        b.breakNaturally(tool);
                        totalMined[0]++;
                    }
                }
                if (isLastBatch) {
                    // Quitar marca de habilidad
                    player.getPersistentDataContainer().remove(abilityKey);
                    player.sendMessage(SmallCaps.convert("§8§l⛏ §7Veta Sombría: §f" + totalMined[0] + " §7bloques minados."));
                }
            }, batch); // 1 tick de separación entre lotes
        }

        // Efecto visual inmediato
        world.spawnParticle(Particle.DUST, center.clone().add(0.5, 0.5, 0.5), 30, 1, 1, 1, 0,
                new Particle.DustOptions(Color.fromRGB(20, 20, 20), 2.0f));
        world.spawnParticle(Particle.SMOKE, center.clone().add(0.5, 0.5, 0.5), 20, 1, 1, 1, 0.05);
        world.playSound(center, Sound.BLOCK_ANCIENT_DEBRIS_BREAK, 1.0f, 0.8f);
    }

    // ==================== OJO DEL VACÍO ====================

    /**
     * Obtiene el ChatColor asociado a un tipo de mineral.
     */
    private org.bukkit.ChatColor getOreGlowColor(Material mat) {
        String name = mat.name();
        if (name.contains("DIAMOND")) return org.bukkit.ChatColor.AQUA;
        if (name.contains("EMERALD")) return org.bukkit.ChatColor.GREEN;
        if (mat == Material.ANCIENT_DEBRIS) return org.bukkit.ChatColor.DARK_RED;
        if (name.contains("GOLD")) return org.bukkit.ChatColor.YELLOW;
        if (name.contains("IRON")) return org.bukkit.ChatColor.WHITE;
        if (name.contains("LAPIS")) return org.bukkit.ChatColor.BLUE;
        if (name.contains("REDSTONE")) return org.bukkit.ChatColor.RED;
        if (name.contains("COPPER")) return org.bukkit.ChatColor.GOLD;
        if (name.contains("QUARTZ")) return org.bukkit.ChatColor.WHITE;
        return org.bukkit.ChatColor.WHITE;
    }

    /**
     * Obtiene el color de partícula asociado a un tipo de mineral.
     */
    private Particle.DustOptions getOreDustColor(Material mat) {
        String name = mat.name();
        if (name.contains("DIAMOND")) return new Particle.DustOptions(Color.fromRGB(80, 220, 255), 2.0f);
        if (name.contains("EMERALD")) return new Particle.DustOptions(Color.fromRGB(0, 200, 50), 2.0f);
        if (mat == Material.ANCIENT_DEBRIS) return new Particle.DustOptions(Color.fromRGB(130, 70, 50), 2.5f);
        if (name.contains("GOLD")) return new Particle.DustOptions(Color.fromRGB(255, 200, 0), 1.5f);
        if (name.contains("IRON")) return new Particle.DustOptions(Color.fromRGB(220, 220, 220), 1.5f);
        if (name.contains("LAPIS")) return new Particle.DustOptions(Color.fromRGB(30, 50, 200), 1.8f);
        if (name.contains("REDSTONE")) return new Particle.DustOptions(Color.fromRGB(200, 0, 0), 1.8f);
        if (name.contains("COPPER")) return new Particle.DustOptions(Color.fromRGB(200, 120, 50), 1.5f);
        if (name.contains("QUARTZ")) return new Particle.DustOptions(Color.fromRGB(230, 225, 215), 1.5f);
        return null;
    }

    private void useOjoDelVacio(Player player) {
        UUID uid = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long lastUse = ojoCooldown.get(uid);
        if (lastUse != null) {
            long remaining = OJO_CD - (now - lastUse);
            if (remaining > 0) {
                player.sendTitle("",
                        "§b§lOjo del Vacío §7- Cooldown: §e" + String.format("%.1f", remaining / 1000.0) + "s", 0, 20,
                        5);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.5f);
                return;
            }
        }
        ojoCooldown.put(uid, now);

        player.sendTitle("", SmallCaps.convert("§b§l👁 Ojo del Vacío"), 0, 20, 5);
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.5f);
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.8f);

        Location center = player.getLocation();
        World world = center.getWorld();
        int radius = 8;

        // Escanear minerales en radio
        List<Location> oreLocs = new ArrayList<>();
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block b = world.getBlockAt(center.getBlockX() + x, center.getBlockY() + y,
                            center.getBlockZ() + z);
                    if (VALUABLE_ORES.contains(b.getType())) {
                        oreLocs.add(b.getLocation());
                    }
                }
            }
        }

        if (oreLocs.isEmpty()) {
            player.sendMessage(SmallCaps.convert("§b§l👁 §7No se detectaron minerales valiosos cerca."));
            return;
        }

        player.sendMessage(SmallCaps.convert("§b§l👁 §7Detectados §e" + oreLocs.size() + " §7minerales valiosos. Visión otorgada."));
        player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.NIGHT_VISION, 200,
                0, false, false, false));

        // Crear teams temporales en el scoreboard para colores de glow
        org.bukkit.scoreboard.Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        String teamPrefix = "ojo_" + uid.toString().substring(0, 4) + "_";
        Map<org.bukkit.ChatColor, org.bukkit.scoreboard.Team> colorTeams = new HashMap<>();

        List<org.bukkit.entity.Shulker> glowEntities = new ArrayList<>();

        for (Location loc : oreLocs) {
            Material oreType = loc.getBlock().getType();
            org.bukkit.ChatColor glowColor = getOreGlowColor(oreType);

            // Obtener o crear team para este color
            org.bukkit.scoreboard.Team team = colorTeams.get(glowColor);
            if (team == null) {
                String teamName = teamPrefix + glowColor.name().toLowerCase();
                // Truncar a 16 chars max (límite de Bukkit)
                if (teamName.length() > 16) teamName = teamName.substring(0, 16);
                // Eliminar team viejo si existe
                org.bukkit.scoreboard.Team existing = board.getTeam(teamName);
                if (existing != null) existing.unregister();
                team = board.registerNewTeam(teamName);
                team.setColor(glowColor);
                team.setOption(org.bukkit.scoreboard.Team.Option.COLLISION_RULE,
                        org.bukkit.scoreboard.Team.OptionStatus.NEVER);
                colorTeams.put(glowColor, team);
            }

            org.bukkit.scoreboard.Team finalTeam = team;
            org.bukkit.entity.Shulker shulker = world.spawn(loc.clone().add(0.5, 0, 0.5),
                    org.bukkit.entity.Shulker.class, s -> {
                        s.setInvisible(true);
                        s.setGlowing(true);
                        s.setAI(false);
                        s.setGravity(false);
                        s.setInvulnerable(true);
                        s.setPersistent(false);
                        s.setSilent(true);
                    });
            finalTeam.addEntry(shulker.getUniqueId().toString());
            glowEntities.add(shulker);
        }

        // Partículas + cleanup después de 10s
        final Map<org.bukkit.ChatColor, org.bukkit.scoreboard.Team> teamsToClean = colorTeams;
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= 200 || !player.isOnline()) { // 10 seconds
                    for (org.bukkit.entity.Shulker s : glowEntities) {
                        if (!s.isDead()) s.remove();
                    }
                    // Limpiar teams temporales
                    for (org.bukkit.scoreboard.Team t : teamsToClean.values()) {
                        try { t.unregister(); } catch (Exception ignored) {}
                    }
                    cancel();
                    return;
                }

                // Partículas del color del mineral
                if (ticks % 10 == 0) {
                    for (Location loc : oreLocs) {
                        if (loc.distance(player.getLocation()) > 25) continue;
                        Particle.DustOptions dust = getOreDustColor(loc.getBlock().getType());
                        if (dust != null) {
                            world.spawnParticle(Particle.DUST, loc.clone().add(0.5, 0.5, 0.5),
                                    5, 0.3, 0.3, 0.3, 0, dust);
                        }
                    }
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    // ==================== RESONANCIA (PASIVA) ====================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();

        if (!VoidPickaxe.isVoidPickaxe(tool, plugin))
            return;

        // 5% de chance de doble drops SOLO en minerales básicos
        if (Math.random() < 0.05) {
            Block block = event.getBlock();
            Material blockType = block.getType();
            
            // SOLO permitir duplicación en minerales básicos
            boolean isBasicOre = VALUABLE_ORES.contains(blockType) && 
                !blockType.name().contains("ANCIENT") && 
                !blockType.name().contains("DEBRIS");
            
            if (!isBasicOre) {
                return; // No duplicar nada que no sea mineral básico
            }
            
            Collection<ItemStack> drops = block.getDrops(tool);
            if (!drops.isEmpty()) {
                for (ItemStack drop : drops) {
                    // Give directly to inventory to avoid stat duplication
                    ItemStack bonus = drop.clone();
                    java.util.HashMap<Integer, ItemStack> overflow = player.getInventory().addItem(bonus);
                    // If inventory full, drop remaining but that's unavoidable
                    for (ItemStack leftover : overflow.values()) {
                        block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), leftover);
                    }
                }

                player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f, 1.5f);

                block.getWorld().spawnParticle(Particle.DUST,
                        block.getLocation().add(0.5, 0.5, 0.5), 10, 0.3, 0.3, 0.3, 0,
                        new Particle.DustOptions(Color.fromRGB(120, 0, 200), 1.2f));
            }
        }
    }

    // ==================== PARTÍCULAS AMBIENTALES ====================

    public void tickAmbientParticles() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            ItemStack item = player.getInventory().getItemInMainHand();
            if (VoidPickaxe.isVoidPickaxe(item, plugin)) {
                // Pasiva Haste II y Speed I
                player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        org.bukkit.potion.PotionEffectType.HASTE, 40, 1, false, false, false));
                player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SPEED, 40,
                        0, false, false, false));
                Location loc = player.getLocation().add(0, 1.2, 0);

                loc.getWorld().spawnParticle(Particle.DUST,
                        loc.clone().add((Math.random() - 0.5) * 0.6, (Math.random() - 0.5) * 0.4,
                                (Math.random() - 0.5) * 0.6),
                        1, 0, 0, 0, 0,
                        new Particle.DustOptions(Color.fromRGB(30, 30, 40), 0.5f));

                if (Math.random() < 0.08) {
                    loc.getWorld().spawnParticle(Particle.ASH, loc, 3, 0.3, 0.3, 0.3, 0);
                }
            }
        }
    }
}
