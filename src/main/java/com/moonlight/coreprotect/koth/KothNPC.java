package com.moonlight.coreprotect.koth;

import com.moonlight.coreprotect.CoreProtectPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Pillager;
import org.bukkit.entity.Witch;
import org.bukkit.entity.Vindicator;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.attribute.Attribute;
import org.bukkit.metadata.MetadataValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import com.moonlight.coreprotect.util.SmallCaps;

/**
 * Sistema de NPCs para el modo KOTH Cooperativo.
 */
public class KothNPC {

    private final CoreProtectPlugin plugin;
    private final List<LivingEntity> attackers = new ArrayList<>();
    private final List<LivingEntity> capturers = new ArrayList<>();
    private final Random random = new Random();
    private Location targetZone;
    private BukkitRunnable aiTask;
    private boolean npcSpawned = false;
    private int npcCaptureProgress = 0;
    private static final int NPC_CAPTURE_GOAL = 30;
    private int waveNumber = 0;
    private int lastDifficulty = 1;

    public KothNPC(CoreProtectPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Limpia TODAS las criaturas del mundo KOTH (no jugadores).
     */
    public void killAllMobs(World kothWorld) {
        if (kothWorld == null)
            return;
        for (Entity entity : kothWorld.getEntities()) {
            if (entity instanceof Player)
                continue;
            if (entity instanceof LivingEntity) {
                entity.remove();
            }
        }
        plugin.getLogger().info("[KOTH] Todas las criaturas del mundo KOTH eliminadas.");
    }

    /**
     * Resetea el estado de spawn de NPCs (llamar al iniciar KOTH).
     */
    public void resetSpawnState() {
        npcSpawned = false;
    }

    /**
     * Devuelve si ya se spawnearon los NPCs.
     */
    public boolean hasSpawned() {
        return npcSpawned;
    }

    /**
     * Spawnea NPCs cerca de un jugador específico (el primero en entrar).
     * Dos roles: ATACANTES (van a por jugadores) y CAPTURADORES (van a la zona).
     */
    public void spawnNPCs(Location zoneCenter, int difficulty, Player firstPlayer) {
        if (npcSpawned)
            return;
        npcSpawned = true;
        this.targetZone = zoneCenter;
        this.lastDifficulty = difficulty;
        this.waveNumber = 1;
        npcCaptureProgress = 0;

        clearNPCs();

        // Menos NPCs: 4-8 atacantes + 3-5 capturadores
        int attackerCount = Math.min(3 + difficulty, 8);
        int capturerCount = Math.min(2 + difficulty, 5);

        Location playerLoc = firstPlayer.getLocation();
        int spawnedAttackers = 0;
        int spawnedCapturers = 0;

        // Spawnear ATACANTES cerca del jugador
        for (int i = 0; i < attackerCount; i++) {
            Location safeLoc = findSafeLocationNearPlayer(playerLoc);
            if (safeLoc != null) {
                LivingEntity npc = spawnAttacker(safeLoc, difficulty);
                if (npc != null) {
                    attackers.add(npc);
                    spawnedAttackers++;
                    safeLoc.getWorld().spawnParticle(Particle.SMOKE, safeLoc, 10, 0.5, 1, 0.5, 0.1);
                    safeLoc.getWorld().spawnParticle(Particle.FLAME, safeLoc, 5, 0.3, 0.5, 0.3, 0.05);
                }
            }
        }

        // Spawnear CAPTURADORES un poco más lejos, orientados a la zona
        for (int i = 0; i < capturerCount; i++) {
            Location safeLoc = findSafeLocationNearPlayer(playerLoc);
            if (safeLoc != null) {
                LivingEntity npc = spawnCapturer(safeLoc, difficulty);
                if (npc != null) {
                    capturers.add(npc);
                    spawnedCapturers++;
                    safeLoc.getWorld().spawnParticle(Particle.PORTAL, safeLoc, 15, 0.5, 1, 0.5, 0.1);
                }
            }
        }

        startNPCAI();

        playerLoc.getWorld().playSound(playerLoc, org.bukkit.Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.5f);

        Bukkit.broadcastMessage("§4§l⚔ §c§l¡OLEADA ENEMIGA! §f" + spawnedAttackers + " atacantes y " + spawnedCapturers
                + " capturadores.");
        Bukkit.broadcastMessage(
                "§7§l⚠ §7¡Mata a los §c⚔ Atacantes §7y detén a los §d🎯 Capturadores §7antes de que tomen la zona!");
    }

    /**
     * Spawnea un NPC atacante (persigue jugadores).
     */
    private LivingEntity spawnAttacker(Location loc, int difficulty) {
        EntityType[] types = { EntityType.ZOMBIE, EntityType.SKELETON, EntityType.VINDICATOR };
        EntityType type = types[random.nextInt(types.length)];
        try {
            LivingEntity npc = (LivingEntity) loc.getWorld().spawnEntity(loc, type);
            setupNPC(npc, difficulty, true);
            return npc;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Spawnea un NPC capturador (va a la zona de captura).
     */
    private LivingEntity spawnCapturer(Location loc, int difficulty) {
        // Capturadores: Pillagers y Witches (más estratégicos)
        EntityType[] types = { EntityType.PILLAGER, EntityType.WITCH };
        EntityType type = types[random.nextInt(types.length)];
        try {
            LivingEntity npc = (LivingEntity) loc.getWorld().spawnEntity(loc, type);
            setupNPC(npc, difficulty, false);
            // Más vida para capturadores - son el objetivo principal
            try {
                double extraHP = 10 + difficulty * 5;
                npc.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).setBaseValue(
                        npc.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue() + extraHP);
                npc.setHealth(npc.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue());
            } catch (Exception ignored) {
            }
            return npc;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Encuentra una ubicación segura CERCA de un jugador, A SU MISMA ALTURA Y.
     * Spawnea en superficie natural, nunca en techos o bajo tierra.
     */
    private Location findSafeLocationNearPlayer(Location playerLoc) {
        World world = playerLoc.getWorld();
        double playerY = playerLoc.getY();

        // Intentar 20 veces encontrar un buen spot
        for (int attempt = 0; attempt < 20; attempt++) {
            // Radio de 8-15 bloques del jugador (más cerca para mejor combate)
            double angle = random.nextDouble() * 2 * Math.PI;
            double dist = 8 + random.nextDouble() * 7;
            double x = playerLoc.getX() + Math.cos(angle) * dist;
            double z = playerLoc.getZ() + Math.sin(angle) * dist;

            // OBTENER LA SUPERFICIE NATURAL en esa coordenada X/Z
            int surfaceY = world.getHighestBlockYAt((int)x, (int)z) + 1;
            
            // Limitar para no spawnear demasiado alto o bajo respecto al jugador
            double targetY = Math.max(surfaceY, Math.min(playerY + 2, surfaceY));
            
            // Si la superficie está muy lejos del jugador (en un acantilado), buscar otro spot
            if (Math.abs(targetY - playerY) > 8) {
                continue; // Buscar otra ubicación
            }
            
            Location testLoc = new Location(world, x, targetY, z);
            if (isSafeSpawnLocation(testLoc)) {
                return testLoc;
            }
        }

        // Fallback: superficie cercana al jugador
        int surfaceY = world.getHighestBlockYAt(playerLoc) + 1;
        return playerLoc.clone().add(random.nextInt(6) - 3, surfaceY - playerLoc.getY(), random.nextInt(6) - 3);
    }

    /**
     * Verifica si una ubicación es segura para spawnear.
     */
    private boolean isSafeSpawnLocation(Location loc) {
        World world = loc.getWorld();

        // Suelo sólido
        Material ground = world.getBlockAt(loc.clone().subtract(0, 1, 0)).getType();
        if (!ground.isSolid() || ground == Material.WATER || ground == Material.LAVA) {
            return false;
        }

        // Espacio para el NPC (2 bloques de aire)
        Material feet = world.getBlockAt(loc).getType();
        Material head = world.getBlockAt(loc.clone().add(0, 1, 0)).getType();
        if (!feet.isAir() || !head.isAir()) {
            return false;
        }

        // No muy cerca de otros NPCs
        for (LivingEntity npc : attackers) {
            if (npc.isValid() && npc.getLocation().distance(loc) < 3) {
                return false;
            }
        }
        for (LivingEntity npc : capturers) {
            if (npc.isValid() && npc.getLocation().distance(loc) < 3) {
                return false;
            }
        }

        return true;
    }

    /**
     * Configura un NPC con equipo y efectos según la dificultad.
     * 
     * @param isAttacker true = atacante (rojo), false = capturador (morado)
     */
    private void setupNPC(LivingEntity npc, int difficulty, boolean isAttacker) {
        // Nombre con rol
        String roleName = isAttacker ? "§c⚔ " : "§d🎯 ";
        String mobName = isAttacker ? "Atacante" : "Capturador";
        npc.setCustomName(SmallCaps.convert(roleName + mobName + " §7[Nv." + difficulty + "]"));
        npc.setCustomNameVisible(true);

        // Metadata para identificar rol
        npc.setMetadata("koth_role",
                new org.bukkit.metadata.FixedMetadataValue(plugin, isAttacker ? "attacker" : "capturer"));

        // Atributos
        try {
            npc.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).setBaseValue(20 + difficulty * 10);
            npc.setHealth(npc.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue());
            npc.getAttribute(org.bukkit.attribute.Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(4 + difficulty * 2);
        } catch (Exception ignored) {
        }

        equipNPC(npc, difficulty);

        if (difficulty >= 2) {
            npc.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 999999, isAttacker ? 1 : 0));
        }
        if (difficulty >= 4) {
            npc.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 999999, 1));
        }

        // Glow para capturadores (para que los jugadores los vean)
        if (!isAttacker) {
            npc.setGlowing(true);
        }

        npc.setRemoveWhenFarAway(false);
    }

    /**
     * Equipa al NPC con armas y armadura.
     */
    private void equipNPC(LivingEntity npc, int difficulty) {
        Material weapon = Material.IRON_SWORD;
        Material armor = Material.IRON_CHESTPLATE;

        if (difficulty >= 3) {
            weapon = Material.DIAMOND_SWORD;
            armor = Material.DIAMOND_CHESTPLATE;
        }
        if (difficulty >= 5) {
            weapon = Material.NETHERITE_SWORD;
            armor = Material.NETHERITE_CHESTPLATE;
        }

        // Arma
        if (npc.getEquipment() != null) {
            npc.getEquipment().setItemInMainHand(new ItemStack(weapon));

            // Armadura parcial
            if (random.nextBoolean()) {
                npc.getEquipment().setChestplate(new ItemStack(armor));
            }
            if (random.nextBoolean() && difficulty >= 2) {
                npc.getEquipment().setHelmet(new ItemStack(armor));
            }
        }

        // Configuración especial para esqueletos (arco)
        if (npc instanceof Skeleton) {
            npc.getEquipment().setItemInMainHand(new ItemStack(Material.BOW));
            if (difficulty >= 3) {
                npc.getEquipment().setItemInMainHand(new ItemStack(Material.CROSSBOW));
            }
        }

        // Configuración especial para Pillagers
        if (npc instanceof Pillager) {
            npc.getEquipment().setItemInMainHand(new ItemStack(Material.CROSSBOW));
        }
    }

    /**
     * Inicia la IA de los NPCs. Cada segundo revisa objetivos y mueve NPCs.
     * ATACANTES: persiguen jugadores.
     * CAPTURADORES: van a la zona, si no hay jugadores cerca.
     */
    private void startNPCAI() {
        if (aiTask != null)
            aiTask.cancel();

        aiTask = new BukkitRunnable() {
            @Override
            public void run() {
                // Limpiar muertos
                attackers.removeIf(npc -> npc.isDead() || !npc.isValid());
                capturers.removeIf(npc -> npc.isDead() || !npc.isValid());

                if (attackers.isEmpty() && capturers.isEmpty()) {
                    // ¡Nueva oleada! Spawnear más NPCs
                    spawnNewWave();
                    return;
                }

                // IA ATACANTES: persiguen jugadores de forma inteligente y natural
                for (LivingEntity npc : new ArrayList<>(attackers)) {
                    if (npc.isDead() || !npc.isValid())
                        continue;

                    Player nearest = findNearestPlayer(npc);
                    if (nearest != null) {
                        Location npcLoc = npc.getLocation();
                        World w = npc.getWorld();
                        Location targetLoc = nearest.getLocation();

                        // CORRECCIÓN DE POSICIÓN: usar la Y del jugador como referencia
                        double playerY = targetLoc.getY();
                        
                        // Si el NPC está en un tejado (más de 4 bloques por encima del jugador), bajarlo
                        if (npcLoc.getY() > playerY + 4.0) {
                            Location corrected = npcLoc.clone();
                            corrected.setY(playerY);
                            if (w.getBlockAt(corrected).getType().isAir()) {
                                npc.teleport(corrected);
                                npcLoc = corrected;
                            }
                        }
                        // Si está muy por debajo del jugador, subirlo
                        else if (npcLoc.getY() < playerY - 6.0) {
                            Location corrected = npcLoc.clone();
                            corrected.setY(playerY);
                            npc.teleport(corrected);
                            npcLoc = corrected;
                        }

                        // Establecer target de combate
                        setNPCTarget(npc, nearest);

                        double dist = npcLoc.distance(targetLoc);

                        // MOVIMIENTO INTELIGENTE basado en distancia
                        if (dist > 30) {
                            // Correr hacia el jugador (movimiento rápido pero natural)
                            double dx = targetLoc.getX() - npcLoc.getX();
                            double dz = targetLoc.getZ() - npcLoc.getZ();
                            double horizDist = Math.sqrt(dx * dx + dz * dz);
                            
                            double ndx = dx / horizDist;
                            double ndz = dz / horizDist;
                            
                            // Salto inteligente solo si es necesario
                            double vy = 0.0;
                            Location nextStep = npcLoc.clone().add(ndx * 2, 0, ndz * 2);
                            if (needsJump(nextStep)) {
                                vy = 0.42; // Salto normal
                            }
                            
                            // Velocidad moderada para movimiento natural
                            npc.setVelocity(new org.bukkit.util.Vector(ndx * 0.8, vy, ndz * 0.8));
                            
                        } else if (dist > 3.0) {
                            // Movimiento táctico de combate
                            if (npc.getVelocity().lengthSquared() < 0.1) {
                                double dx = targetLoc.getX() - npcLoc.getX();
                                double dz = targetLoc.getZ() - npcLoc.getZ();
                                double horizDist = Math.sqrt(dx * dx + dz * dz);

                                double ndx = dx / horizDist;
                                double ndz = dz / horizDist;

                                // Movimiento rápido pero controlado en combate cercano
                                double vy = 0.0;
                                Location nextStep = npcLoc.clone().add(ndx * 1.5, 0, ndz * 1.5);
                                if (needsJump(nextStep)) {
                                    vy = 0.42;
                                }

                                npc.setVelocity(new org.bukkit.util.Vector(ndx * 0.6, vy, ndz * 0.6));
                            }
                        }

                        // Partículas de persecución
                        if (random.nextInt(5) == 0) {
                            w.spawnParticle(Particle.SMOKE, npc.getLocation().add(0, 0.5, 0), 1, 0.2, 0.2, 0.2, 0.02);
                        }
                    }
                }

                // IA CAPTURADORES: corren inteligentemente hacia la zona, evitando obstáculos
                for (LivingEntity npc : new ArrayList<>(capturers)) {
                    if (npc.isDead() || !npc.isValid())
                        continue;

                    if (targetZone != null) {
                        // Cancelar target de combate SIEMPRE - solo van a la zona
                        if (npc instanceof Monster) {
                            ((Monster) npc).setTarget(null);
                        }

                        Location npcLoc = npc.getLocation();
                        World w = npc.getWorld();
                        
                        // CORRECCIÓN DE POSICIÓN: usar la Y de la zona como referencia
                        double zoneY = targetZone.getY();
                        if (npcLoc.getY() > zoneY + 4.0) {
                            // En un tejado, bajar a la altura de la zona
                            Location corrected = npcLoc.clone();
                            corrected.setY(zoneY);
                            if (w.getBlockAt(corrected).getType().isAir()) {
                                npc.teleport(corrected);
                                npcLoc = corrected;
                            }
                        } else if (npcLoc.getY() < zoneY - 6.0) {
                            Location corrected = npcLoc.clone();
                            corrected.setY(zoneY);
                            npc.teleport(corrected);
                            npcLoc = corrected;
                        }

                        // Distancia a la zona (solo X/Z)
                        double dx = targetZone.getX() - npcLoc.getX();
                        double dz = targetZone.getZ() - npcLoc.getZ();
                        double distHoriz = Math.sqrt(dx * dx + dz * dz);

                        if (distHoriz > 3.0) {
                            // Normalizar dirección hacia la zona
                            double ndx = dx / distHoriz;
                            double ndz = dz / distHoriz;

                            // Velocidad base para capturadores (un poco más lenta pero constante)
                            double speed = 0.35;
                            double vy = 0.0;

                            // PATHFINDING INTELIGENTE: verificar obstáculos adelante
                            Location ahead = npcLoc.clone().add(ndx * 1.5, 0, ndz * 1.5);
                            if (!w.getBlockAt(ahead).getType().isAir()) {
                                // Hay obstáculo, intentar saltar
                                if (canJump(ahead)) {
                                    vy = 0.42; // Salto normal
                                } else {
                                    // No puede saltar, intentar rodear
                                    // Pequeña desviación lateral
                                    double perpendicularX = -ndz * 0.5;
                                    double perpendicularZ = ndx * 0.5;
                                    
                                    ndx += perpendicularX;
                                    ndz += perpendicularZ;
                                    
                                    // Renormalizar
                                    double newDist = Math.sqrt(ndx * ndx + ndz * ndz);
                                    ndx /= newDist;
                                    ndz /= newDist;
                                }
                            }

                            // Aplicar movimiento suave y constante
                            npc.setVelocity(new org.bukkit.util.Vector(ndx * speed, vy, ndz * speed));
                        }

                        // Partículas distintivas para capturadores (más visibles)
                        if (random.nextInt(4) == 0) {
                            w.spawnParticle(Particle.PORTAL, npc.getLocation().add(0, 0.8, 0), 2, 0.3, 0.3, 0.3, 0.03);
                        }
                    }
                }

                // La lógica de puntos se maneja en KothManager.mainTask
            }
        };
        aiTask.runTaskTimer(plugin, 20L, 20L);
    }

    /**
     * Spawnea una nueva oleada de NPCs cerca de un jugador aleatorio en el mundo
     * KOTH.
     */
    private void spawnNewWave() {
        if (targetZone == null || targetZone.getWorld() == null)
            return;

        World world = targetZone.getWorld();
        java.util.List<Player> players = new java.util.ArrayList<>();
        for (Player p : world.getPlayers()) {
            if (!p.isDead() && p.getGameMode() == org.bukkit.GameMode.SURVIVAL) {
                players.add(p);
            }
        }

        if (players.isEmpty())
            return; // Sin jugadores, no spawnear

        waveNumber++;
        Player targetPlayer = players.get(random.nextInt(players.size()));
        Location playerLoc = targetPlayer.getLocation();

        // Más NPCs en oleadas posteriores (escala suave)
        int attackerCount = Math.min(3 + lastDifficulty + (waveNumber / 3), 10);
        int capturerCount = Math.min(2 + lastDifficulty + (waveNumber / 4), 6);

        int spawnedA = 0, spawnedC = 0;

        for (int i = 0; i < attackerCount; i++) {
            Location safeLoc = findSafeLocationNearPlayer(playerLoc);
            if (safeLoc != null) {
                LivingEntity npc = spawnAttacker(safeLoc, lastDifficulty);
                if (npc != null) {
                    attackers.add(npc);
                    spawnedA++;
                }
            }
        }
        for (int i = 0; i < capturerCount; i++) {
            Location safeLoc = findSafeLocationNearPlayer(playerLoc);
            if (safeLoc != null) {
                LivingEntity npc = spawnCapturer(safeLoc, lastDifficulty);
                if (npc != null) {
                    capturers.add(npc);
                    spawnedC++;
                }
            }
        }

        // Sonido y anuncio
        world.playSound(targetPlayer.getLocation(), org.bukkit.Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.7f);
        Bukkit.broadcastMessage("§4§l⚔ §c§lOLEADA #" + waveNumber + "! §f" + spawnedA + " atacantes y " + spawnedC
                + " capturadores cerca de §e" + targetPlayer.getName());
    }

    /**
     * Obtiene el número de oleada actual.
     */
    public int getWaveNumber() {
        return waveNumber;
    }

    /**
     * Cuenta cuántos capturadores están dentro de la zona KOTH.
     */
    public int countCapturersInZone() {
        if (targetZone == null)
            return 0;
        int count = 0;
        for (LivingEntity npc : capturers) {
            if (npc.isValid() && !npc.isDead()) {
                double dist = npc.getLocation().distance(targetZone);
                if (dist <= 10) { // Dentro de 10 bloques del centro = en la zona
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * Obtiene el progreso de captura de los NPCs.
     */
    public int getCaptureProgress() {
        return npcCaptureProgress;
    }

    /**
     * Obtiene el objetivo de captura.
     */
    public int getCaptureGoal() {
        return NPC_CAPTURE_GOAL;
    }

    /**
     * Encuentra el jugador más cercano (sin límite de distancia).
     */
    private Player findNearestPlayer(LivingEntity npc) {
        Player nearest = null;
        double minDist = Double.MAX_VALUE;
        for (Player p : npc.getWorld().getPlayers()) {
            if (p.isDead() || p.getGameMode() != org.bukkit.GameMode.SURVIVAL)
                continue;
            double d = npc.getLocation().distance(p.getLocation());
            if (d < minDist) {
                minDist = d;
                nearest = p;
            }
        }
        return nearest;
    }

    /**
     * Establece el objetivo del NPC (para que el mob lo ataque).
     */
    private void setNPCTarget(LivingEntity npc, Player target) {
        if (npc instanceof Monster) {
            ((Monster) npc).setTarget(target);
        }
    }

    /**
     * Limpia todos los NPCs spawneados.
     */
    public void clearNPCs() {
        if (aiTask != null) {
            aiTask.cancel();
            aiTask = null;
        }
        for (LivingEntity npc : new ArrayList<>(attackers)) {
            if (npc.isValid())
                npc.remove();
        }
        for (LivingEntity npc : new ArrayList<>(capturers)) {
            if (npc.isValid())
                npc.remove();
        }
        attackers.clear();
        capturers.clear();
        npcCaptureProgress = 0;
    }

    /**
     * Verifica si quedan NPCs vivos.
     */
    public boolean hasRemainingNPCs() {
        attackers.removeIf(npc -> npc.isDead() || !npc.isValid());
        capturers.removeIf(npc -> npc.isDead() || !npc.isValid());
        return !attackers.isEmpty() || !capturers.isEmpty();
    }

    /**
     * Obtiene el número de NPCs restantes.
     */
    public int getRemainingNPCs() {
        int count = 0;
        for (LivingEntity npc : attackers) {
            if (npc.isValid() && !npc.isDead())
                count++;
        }
        for (LivingEntity npc : capturers) {
            if (npc.isValid() && !npc.isDead())
                count++;
        }
        return count;
    }

    /**
     * Obtiene el número de atacantes restantes.
     */
    public int getRemainingAttackers() {
        attackers.removeIf(npc -> npc.isDead() || !npc.isValid());
        return attackers.size();
    }

    /**
     * Obtiene el número de capturadores restantes.
     */
    public int getRemainingCapturers() {
        capturers.removeIf(npc -> npc.isDead() || !npc.isValid());
        return capturers.size();
    }

    /**
     * Verifica si el NPC necesita saltar para moverse hacia adelante.
     */
    private boolean needsJump(Location loc) {
        World world = loc.getWorld();
        Material block = world.getBlockAt(loc).getType();
        // Si hay un bloque sólido adelante, necesita saltar
        return block.isSolid() && block != Material.WATER && block != Material.LAVA;
    }

    /**
     * Verifica si el NPC puede saltar sobre el bloque (no es demasiado alto).
     */
    private boolean canJump(Location loc) {
        World world = loc.getWorld();
        // Verificar que el bloque de arriba del obstáculo es aire
        Location above = loc.clone().add(0, 1, 0);
        return world.getBlockAt(above).getType().isAir();
    }

}
