package com.moonlight.coreprotect.shadowhunter;

import com.moonlight.coreprotect.CoreProtectPlugin;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import com.moonlight.coreprotect.util.SmallCaps;

/**
 * Fase de ritual: el jugador debe completar una canalización mientras
 * se muestra una animación progresiva épica.
 */
public class ShadowHunterRitual {

    private final CoreProtectPlugin plugin;
    private final ShadowHunterManager manager;
    private final Player player;
    private final ShadowHunterNPC npc;

    private Location ritualCenter;
    private boolean active = false;
    private boolean channeling = false;
    private int channelProgress = 0;
    private static final int CHANNEL_REQUIRED = 200; // 10 segundos (200 ticks)

    private BukkitTask ritualTask;
    private BukkitTask channelTask;
    private final List<ArmorStand> runeStands = new ArrayList<>();

    // 4 pilares del ritual (N, S, E, O)
    private final Location[] pillarLocations = new Location[4];
    private boolean[] pillarActivated = new boolean[4];
    private int pillarsActivated = 0;

    // Bloques originales para restaurar al terminar
    // Map<Location, BlockData> para cada bloque colocado
    private final Map<Location, BlockData> originalBlocks = new HashMap<>();

    public ShadowHunterRitual(CoreProtectPlugin plugin, ShadowHunterManager manager, Player player, ShadowHunterNPC npc) {
        this.plugin = plugin;
        this.manager = manager;
        this.player = player;
        this.npc = npc;
    }

    public void start() {
        active = true;
        ritualCenter = player.getLocation().clone();
        ritualCenter.setY(ritualCenter.getWorld().getHighestBlockYAt(ritualCenter) + 1);

        // Posicionar NPC
        Location npcLoc = ritualCenter.clone().add(5, 0, 0);
        npcLoc.setY(npcLoc.getWorld().getHighestBlockYAt(npcLoc) + 1);
        npc.moveToAndStay(npcLoc);

        // Configurar pilares
        double pillarDist = 6.0;
        pillarLocations[0] = ritualCenter.clone().add(0, 0, -pillarDist);  // Norte
        pillarLocations[1] = ritualCenter.clone().add(0, 0, pillarDist);   // Sur
        pillarLocations[2] = ritualCenter.clone().add(pillarDist, 0, 0);   // Este
        pillarLocations[3] = ritualCenter.clone().add(-pillarDist, 0, 0);  // Oeste

        for (int i = 0; i < 4; i++) {
            pillarLocations[i].setY(ritualCenter.getWorld().getHighestBlockYAt(pillarLocations[i]) + 1);
            pillarActivated[i] = false;
        }

        // Colocar bloques físicos en los pilares (END_ROD: atravesable, emite luz, visible)
        placePillarBlocks();

        // Diálogo
        npc.say("\"Ahora... el ritual. Acércate a cada pilar de energía para activarlo.\"");
        npc.sayDelayed("\"Verás 4 pilares brillantes a tu alrededor. Acércate a cada uno.\"", 60L);

        // Crear efectos visuales de los pilares
        startRitualVisuals();

        // Iniciar detección de pilares
        startPillarDetection();
    }

    // ==================== PILARES ====================

    private void startPillarDetection() {
        ritualTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!active || !player.isOnline()) { cancel(); return; }

                for (int i = 0; i < 4; i++) {
                    if (pillarActivated[i]) continue;

                    // Verificar si el jugador está cerca del pilar
                    if (player.getLocation().distance(pillarLocations[i]) < 2.5) {
                        activatePillar(i);
                    }
                }
            }
        }.runTaskTimer(plugin, 0, 5);
    }

    private void activatePillar(int index) {
        pillarActivated[index] = true;
        pillarsActivated++;

        Location loc = pillarLocations[index];
        World world = loc.getWorld();

        // Cambiar los bloques del pilar a SOUL_LANTERN (activado)
        changePillarBlocks(index, Material.SOUL_LANTERN);

        // Efecto de activación
        Color[] colors = {Color.AQUA, Color.FUCHSIA, Color.LIME, Color.ORANGE};
        String[] names = {"§b§lAgua", "§d§lSombra", "§a§lVida", "§6§lFuego"};

        // Columna de partículas ascendente épica
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks >= 40) { cancel(); return; }
                double y = ticks * 0.3;
                world.spawnParticle(Particle.DUST, loc.clone().add(0, y, 0), 8, 0.3, 0.1, 0.3, 0,
                    new Particle.DustOptions(colors[index], 2.5f));
                world.spawnParticle(Particle.END_ROD, loc.clone().add(0, y, 0), 3, 0.1, 0.1, 0.1, 0.03);
                world.spawnParticle(Particle.SOUL_FIRE_FLAME, loc.clone().add(0, y, 0), 2, 0.2, 0.1, 0.2, 0.01);
                ticks++;
            }
        }.runTaskTimer(plugin, 0, 1);

        world.playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 0.8f + (pillarsActivated * 0.2f));
        world.playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.0f + (pillarsActivated * 0.3f));
        world.strikeLightningEffect(loc);

        player.sendMessage(SmallCaps.convert("§5§l✦ §f¡Pilar " + names[index] + " §factivado! §7(" + pillarsActivated + "/4)"));
        player.sendTitle( SmallCaps.convert("§5" + names[index] + " §fActivado"), SmallCaps.convert("§7" + pillarsActivated + "/4 pilares"), 5, 30, 10);

        // Rayo del pilar al centro con partículas más densas
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks >= 25) { cancel(); return; }
                double progress = ticks / 25.0;
                Location beam = loc.clone().add(0, 1.5, 0);
                Location center = ritualCenter.clone().add(0, 1.5, 0);

                double x = beam.getX() + (center.getX() - beam.getX()) * progress;
                double y = beam.getY() + (center.getY() - beam.getY()) * progress + Math.sin(progress * Math.PI) * 3;
                double z = beam.getZ() + (center.getZ() - beam.getZ()) * progress;

                world.spawnParticle(Particle.DUST, new Location(world, x, y, z), 5, 0.1, 0.1, 0.1, 0,
                    new Particle.DustOptions(colors[index], 2.0f));
                world.spawnParticle(Particle.END_ROD, new Location(world, x, y, z), 1, 0, 0, 0, 0.01);
                ticks++;
            }
        }.runTaskTimer(plugin, 5, 1);

        // Comentarios del NPC
        switch (pillarsActivated) {
            case 1: npc.sayDelayed("\"Bien... la energía fluye. Tres más.\"", 20L); break;
            case 2: npc.sayDelayed("\"Puedo sentirlo... el vacío se abre.\"", 20L); break;
            case 3: npc.sayDelayed("\"¡Casi! Un pilar más y comenzará la canalización.\"", 20L); break;
            case 4:
                npc.sayDelayed("\"§d¡TODOS LOS PILARES ACTIVOS! §7Ve al centro y no te muevas.\"", 20L);
                // Iniciar fase de canalización
                Bukkit.getScheduler().runTaskLater(plugin, this::startChanneling, 60L);
                if (ritualTask != null) ritualTask.cancel();
                break;
        }
    }

    // ==================== CANALIZACIÓN ====================

    private void startChanneling() {
        channeling = true;
        channelProgress = 0;

        player.sendMessage("");
        player.sendMessage(SmallCaps.convert("§5§l✦ §d§l¡CANALIZACIÓN! §fQuédate quieto en el centro."));
        player.sendMessage(SmallCaps.convert("§7No te muevas durante 10 segundos..."));
        player.sendMessage("");

        channelTask = new BukkitRunnable() {
            Location lastLoc = player.getLocation().clone();
            @Override
            public void run() {
                if (!active || !player.isOnline()) { cancel(); return; }

                // Verificar que no se ha movido
                Location currentLoc = player.getLocation();
                if (currentLoc.getBlockX() != lastLoc.getBlockX() ||
                    currentLoc.getBlockZ() != lastLoc.getBlockZ()) {
                    // Se movió: reiniciar
                    channelProgress = 0;
                    lastLoc = currentLoc.clone();
                    player.sendMessage(SmallCaps.convert("§c§l⚠ §c¡Te moviste! La canalización se reinició."));
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
                    return;
                }

                channelProgress++;
                double progress = (double) channelProgress / CHANNEL_REQUIRED;
                World world = ritualCenter.getWorld();

                // Partículas progresivas
                // Espiral convergente de los pilares
                for (int i = 0; i < 4; i++) {
                    if (!pillarActivated[i]) continue;

                    double angle = channelProgress * 0.1 + (Math.PI / 2) * i;
                    double radius = 6.0 * (1.0 - progress);

                    double x = Math.cos(angle) * radius;
                    double z = Math.sin(angle) * radius;
                    double y = 1 + progress * 3;

                    Color[] colors = {Color.AQUA, Color.FUCHSIA, Color.LIME, Color.ORANGE};
                    world.spawnParticle(Particle.DUST,
                        ritualCenter.clone().add(x, y, z), 3, 0.1, 0.1, 0.1, 0,
                        new Particle.DustOptions(colors[i], 1.5f + (float)progress));
                }

                // Círculo central que se intensifica
                if (channelProgress % 5 == 0) {
                    double circleRadius = 2.0 * (1.0 - progress * 0.5);
                    for (int i = 0; i < 8; i++) {
                        double a = (Math.PI * 2 / 8) * i + channelProgress * 0.05;
                        world.spawnParticle(Particle.END_ROD,
                            ritualCenter.clone().add(Math.cos(a) * circleRadius, 0.5 + progress * 2, Math.sin(a) * circleRadius),
                            1, 0, 0, 0, 0.01);
                    }
                }

                // Sonidos crecientes
                if (channelProgress % 20 == 0) {
                    float pitch = 0.5f + (float)progress * 1.5f;
                    world.playSound(ritualCenter, Sound.BLOCK_BEACON_AMBIENT, 0.8f, pitch);
                }

                // Barra de acción como progreso
                int bars = (int)(progress * 20);
                StringBuilder progressBar = new StringBuilder("§5§l");
                for (int i = 0; i < 20; i++) {
                    progressBar.append(i < bars ? "§d█" : "§8█");
                }
                player.sendTitle("", SmallCaps.convert(progressBar + " §f" + (int)(progress * 100) + "%"), 0, 5, 0);

                // Completado
                if (channelProgress >= CHANNEL_REQUIRED) {
                    cancel();
                    onChannelingComplete();
                }
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    private void onChannelingComplete() {
        channeling = false;
        active = false;

        World world = ritualCenter.getWorld();

        // EXPLOSIÓN FINAL ÉPICA
        new BukkitRunnable() {
            int phase = 0;
            @Override
            public void run() {
                switch (phase) {
                    case 0:
                        // Flash + explosión de partículas
                        world.spawnParticle(Particle.END_ROD, ritualCenter.clone().add(0, 2, 0), 10, 0.5, 0.5, 0.5, 0.1);
                        world.spawnParticle(Particle.END_ROD, ritualCenter.clone().add(0, 2, 0), 200, 3, 3, 3, 0.5);
                        world.playSound(ritualCenter, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.5f);
                        world.playSound(ritualCenter, Sound.BLOCK_BEACON_ACTIVATE, 2.0f, 1.0f);
                        
                        // 💥 ROMPER SOUL_LANTERNS - Explosión de los pilares
                        breakSoulLanterns();
                        break;
                    case 5:
                        // Columna de luz
                        for (int y = 0; y < 30; y++) {
                            world.spawnParticle(Particle.END_ROD, ritualCenter.clone().add(0, y, 0), 5, 0.3, 0, 0.3, 0.01);
                        }
                        world.playSound(ritualCenter, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 2.0f);
                        break;
                    case 15:
                        // Onda expansiva
                        for (int i = 0; i < 36; i++) {
                            double a = (Math.PI * 2 / 36) * i;
                            for (int r = 1; r <= 10; r++) {
                                world.spawnParticle(Particle.DUST,
                                    ritualCenter.clone().add(Math.cos(a) * r, 1, Math.sin(a) * r),
                                    1, 0, 0, 0,
                                    new Particle.DustOptions(Color.fromRGB(150, 0, 255), 2.0f));
                            }
                        }
                        world.playSound(ritualCenter, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.5f, 1.5f);
                        break;
                    case 25:
                        player.sendTitle( SmallCaps.convert("§5§l¡RITUAL COMPLETADO!"), SmallCaps.convert("§7Una presencia oscura despierta..."), 10, 60, 20);
                        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                        npc.say("\"§c¡CUIDADO! Algo se ha despertado... algo grande.\"");
                        break;
                    case 40:
                        cancel();
                        cleanupVisuals();
                        manager.onRitualCompleted(player);
                        return;
                }
                phase++;
            }
        }.runTaskTimer(plugin, 0, 2);
    }

    // ==================== SOUL LANTERNS DESTRUCTION ====================

    /**
     * Rompe todos los soul_lanterns de los pilares con efectos épicos.
     * Los soul_lanterns desaparecen y no se restauran.
     */
    private void breakSoulLanterns() {
        World world = ritualCenter.getWorld();
        Color[] colors = {Color.AQUA, Color.FUCHSIA, Color.LIME, Color.ORANGE};
        String[] names = {"Agua", "Sombra", "Vida", "Fuego"};

        for (int i = 0; i < 4; i++) {
            if (!pillarActivated[i]) continue; // Solo romper los activados

            Location base = pillarLocations[i].clone();
            
            // 💥 EXPLOSIÓN DEL PILAR
            // Efecto de explosión principal
            world.spawnParticle(Particle.EXPLOSION, base.clone().add(0, 1.5, 0), 1);
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, base.clone().add(0, 1.5, 0), 50, 2, 2, 2, 0.5);
            world.spawnParticle(Particle.END_ROD, base.clone().add(0, 1.5, 0), 100, 3, 3, 3, 0.3);
            
            // Sonidos de ruptura
            world.playSound(base, Sound.BLOCK_SHULKER_BOX_CLOSE, 1.5f, 0.8f);
            world.playSound(base, Sound.ENTITY_SHULKER_HURT, 1.0f, 1.2f);
            world.playSound(base, Sound.BLOCK_SOUL_SAND_BREAK, 1.0f, 0.6f);
            
            // Partículas de color del pilar
            for (int j = 0; j < 30; j++) {
                double offsetX = (Math.random() - 0.5) * 4;
                double offsetY = Math.random() * 3;
                double offsetZ = (Math.random() - 0.5) * 4;
                
                world.spawnParticle(Particle.DUST, 
                    base.clone().add(offsetX, offsetY, offsetZ), 2, 0.1, 0.1, 0.1, 0,
                    new Particle.DustOptions(colors[i], 2.5f));
            }
            
            // Romper los 3 bloques SOUL_LANTERN del pilar
            for (int y = 0; y < 3; y++) {
                Location blockLoc = base.clone().add(0, y, 0);
                Block block = world.getBlockAt(blockLoc);
                
                if (block.getType() == Material.SOUL_LANTERN) {
                    // 💥 Romper el bloque con efectos
                    block.breakNaturally();
                    
                    // Partículas adicionales en cada bloque roto
                    world.spawnParticle(Particle.BLOCK, blockLoc.clone().add(0.5, 0.5, 0.5), 
                        15, 0.3, 0.3, 0.3, 0.1, Bukkit.createBlockData(Material.SOUL_LANTERN));
                    
                    // Sonido de ruptura individual
                    world.playSound(blockLoc, Sound.BLOCK_GLASS_BREAK, 0.8f, 1.0f + (float)Math.random() * 0.4f);
                }
            }
            
            // Mensaje del pilar destruido
            player.sendMessage(SmallCaps.convert("§c§l💥 §fEl pilar §5" + names[i] + " §fse ha consumido en el ritual..."));
            
            // Efecto residual - partículas ascendentes
            final int colorIndex = i; // Make effectively final for lambda
            new BukkitRunnable() {
                int ticks = 0;
                @Override
                public void run() {
                    if (ticks >= 20) { cancel(); return; }
                    
                    double y = ticks * 0.2;
                    world.spawnParticle(Particle.DUST, 
                        base.clone().add(0, y, 0), 3, 0.2, 0.1, 0.2, 0,
                        new Particle.DustOptions(colors[colorIndex], 1.5f - (ticks * 0.05f)));
                    world.spawnParticle(Particle.SOUL_FIRE_FLAME, 
                        base.clone().add(0, y, 0), 1, 0.1, 0.1, 0.1, 0.01);
                    
                    ticks++;
                }
            }.runTaskTimer(plugin, 0, 1);
        }
        
        // Mensaje general
        player.sendMessage("");
        player.sendMessage(SmallCaps.convert("§c§l✦ §d§l¡LOS PILARES SE HAN CONSUMIDO!"));
        player.sendMessage(SmallCaps.convert("§7La energía del ritual ha liberado su poder..."));
        player.sendMessage("");
    }

    // ==================== BLOQUES FÍSICOS ====================

    /**
     * Coloca bloques END_ROD en cada pilar (atravesables, emiten luz, muy visibles).
     * Guarda los bloques originales para restaurarlos después.
     */
    private void placePillarBlocks() {
        World world = ritualCenter.getWorld();
        for (int i = 0; i < 4; i++) {
            Location base = pillarLocations[i].clone();
            // Columna de 3 END_RODs
            for (int y = 0; y < 3; y++) {
                Location blockLoc = base.clone().add(0, y, 0);
                Block block = world.getBlockAt(blockLoc);
                // Salvar bloque original
                originalBlocks.put(blockLoc.clone(), block.getBlockData().clone());
                // Colocar END_ROD
                block.setType(Material.END_ROD);
            }
            // Efecto de aparición
            world.spawnParticle(Particle.END_ROD, base.clone().add(0, 1.5, 0), 15, 0.3, 0.8, 0.3, 0.05);
            world.playSound(base, Sound.BLOCK_AMETHYST_CLUSTER_PLACE, 1.0f, 0.8f);
        }
    }

    /**
     * Cambia los bloques de un pilar específico (al activar → SOUL_LANTERN).
     */
    private void changePillarBlocks(int index, Material newMaterial) {
        World world = ritualCenter.getWorld();
        Location base = pillarLocations[index].clone();
        for (int y = 0; y < 3; y++) {
            Location blockLoc = base.clone().add(0, y, 0);
            Block block = world.getBlockAt(blockLoc);
            block.setType(newMaterial);
        }
    }

    /**
     * Restaura todos los bloques originales excepto los soul_lanterns rotos.
     */
    private void restoreBlocks() {
        for (Map.Entry<Location, BlockData> entry : originalBlocks.entrySet()) {
            Location loc = entry.getKey();
            Block block = loc.getBlock();
            
            // 🚫 NO restaurar si el bloque actual es SOUL_LANTERN (fue roto en el ritual)
            if (block.getType() == Material.SOUL_LANTERN) {
                continue; // Saltar este bloque - quedó destruido
            }
            
            block.setBlockData(entry.getValue());
        }
        originalBlocks.clear();
    }

    // ==================== VISUALES ====================

    private void startRitualVisuals() {
        // Partículas constantes en los pilares - MUCHO MÁS VISIBLES
        new BukkitRunnable() {
            int tick = 0;
            @Override
            public void run() {
                if (!active || !player.isOnline()) { cancel(); return; }

                World world = ritualCenter.getWorld();
                Color[] colors = {Color.AQUA, Color.FUCHSIA, Color.LIME, Color.ORANGE};

                for (int i = 0; i < 4; i++) {
                    Location loc = pillarLocations[i];

                    if (!pillarActivated[i]) {
                        // Pilar pendiente: partículas GRANDES y brillantes
                        // Columna de dust grande
                        for (int y = 0; y < 4; y++) {
                            world.spawnParticle(Particle.DUST, loc.clone().add(0, y + 0.5, 0), 3, 0.2, 0.2, 0.2, 0,
                                new Particle.DustOptions(colors[i], 2.0f));
                        }
                        // Espiral de partículas alrededor
                        double angle = tick * 0.15 + (Math.PI / 2) * i;
                        double spiralX = Math.cos(angle) * 0.8;
                        double spiralZ = Math.sin(angle) * 0.8;
                        world.spawnParticle(Particle.END_ROD, loc.clone().add(spiralX, 2, spiralZ), 1, 0, 0, 0, 0.01);
                        // Soul fire flame en la base
                        world.spawnParticle(Particle.SOUL_FIRE_FLAME, loc.clone().add(0, 0.5, 0), 2, 0.3, 0.1, 0.3, 0.01);
                        // Encantamiento flotante
                        world.spawnParticle(Particle.ENCHANT, loc.clone().add(0, 2.5, 0), 5, 0.5, 0.5, 0.5, 1.0);
                    } else {
                        // Pilar activado: columna constante intensa
                        for (int y = 0; y < 6; y++) {
                            world.spawnParticle(Particle.DUST, loc.clone().add(0, y, 0), 3, 0.1, 0, 0.1, 0,
                                new Particle.DustOptions(colors[i], 2.0f));
                        }
                        world.spawnParticle(Particle.SOUL_FIRE_FLAME, loc.clone().add(0, 3, 0), 2, 0.2, 0.3, 0.2, 0.01);
                    }
                }

                // Círculo en el centro - más visible
                for (int i = 0; i < 20; i++) {
                    double a = (Math.PI * 2 / 20) * i;
                    world.spawnParticle(Particle.DUST,
                        ritualCenter.clone().add(Math.cos(a) * 2, 0.2, Math.sin(a) * 2),
                        1, 0, 0, 0, 0,
                        new Particle.DustOptions(Color.fromRGB(120, 0, 180), 1.2f));
                }
                // Runas flotantes en el centro
                if (tick % 3 == 0) {
                    world.spawnParticle(Particle.ENCHANT, ritualCenter.clone().add(0, 1, 0), 3, 1, 0.5, 1, 0.5);
                }

                tick++;
            }
        }.runTaskTimer(plugin, 20, 5);
    }

    private void cleanupVisuals() {
        for (ArmorStand stand : runeStands) {
            if (stand != null && !stand.isDead()) stand.remove();
        }
        runeStands.clear();
    }

    // ==================== GETTERS ====================

    public boolean isActive() { return active; }
    public boolean isChanneling() { return channeling; }

    public void cleanup() {
        active = false;
        channeling = false;
        if (ritualTask != null) ritualTask.cancel();
        if (channelTask != null) channelTask.cancel();
        cleanupVisuals();
        // Restaurar bloques originales
        restoreBlocks();
    }
}
