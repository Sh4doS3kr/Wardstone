package com.moonlight.coreprotect.shadowhunter;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.core.ProtectedRegion;
import org.bukkit.*;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import com.moonlight.coreprotect.util.SmallCaps;

/**
 * NPC "El Errante" que persigue al jugador, inicia diálogo y acompaña durante
 * la misión.
 */
public class ShadowHunterNPC {

    private final CoreProtectPlugin plugin;
    private final ShadowHunterManager manager;
    private final Player player;

    private Villager villager;
    private ArmorStand nameTag;
    private BukkitTask followTask;
    private BukkitTask particleTask;
    private boolean inDialogue = false;
    private boolean dialogueOptionsShown = false;
    private int dialogueStep = 0;
    private boolean isQuest2 = false;
    private BukkitTask dialogueTask;

    // Posición donde se ubica durante combate/ritual/boss
    private Location stationaryLoc;

    public ShadowHunterNPC(CoreProtectPlugin plugin, ShadowHunterManager manager, Player player) {
        this.plugin = plugin;
        this.manager = manager;
        this.player = player;
    }

    // ==================== SPAWN ====================

    private Location findSafeNPCSpawnLocation(Location playerLoc) {
        World world = playerLoc.getWorld();

        // Primero intentar enfrente del jugador (3-5 bloques)
        org.bukkit.util.Vector lookDir = playerLoc.getDirection().setY(0).normalize();
        for (int dist = 3; dist <= 5; dist++) {
            double x = playerLoc.getX() + lookDir.getX() * dist;
            double z = playerLoc.getZ() + lookDir.getZ() * dist;
            Location testLoc = new Location(world, x, playerLoc.getY(), z);
            int groundY = world.getHighestBlockYAt(testLoc);
            testLoc.setY(groundY + 1);
            if (isSafeNPCSpawnLocation(testLoc)) {
                return testLoc;
            }
        }

        // Intentar varias ubicaciones en círculo alrededor del jugador (3-8 bloques)
        for (int radius = 3; radius <= 8; radius += 2) {
            for (int angle = 0; angle < 360; angle += 30) {
                double radians = Math.toRadians(angle);
                double x = playerLoc.getX() + Math.cos(radians) * radius;
                double z = playerLoc.getZ() + Math.sin(radians) * radius;

                // Encontrar Y seguro
                Location testLoc = new Location(world, x, playerLoc.getY(), z);
                int groundY = world.getHighestBlockYAt(testLoc);
                testLoc.setY(groundY + 1); // Spawnear 1 bloque arriba del suelo

                // Validar que sea una ubicación segura
                if (isSafeNPCSpawnLocation(testLoc)) {
                    return testLoc;
                }

                // También probar 2 bloques arriba
                testLoc.setY(groundY + 2);
                if (isSafeNPCSpawnLocation(testLoc)) {
                    return testLoc;
                }
            }
        }

        return null; // No se encontró ubicación segura
    }

    private boolean isSafeNPCSpawnLocation(Location loc) {
        World world = loc.getWorld();

        // No verificar zonas protegidas (El usuario quiere que el errante acceda a
        // zonas protegidas)

        // Verificar que no esté en agua/lava
        org.bukkit.Material blockBelow = world.getBlockAt(loc.clone().subtract(0, 1, 0)).getType();
        org.bukkit.Material blockAt = world.getBlockAt(loc).getType();
        org.bukkit.Material blockAbove = world.getBlockAt(loc.clone().add(0, 1, 0)).getType();

        // No spawnear en agua, lava, o dentro de bloques sólidos
        if (blockBelow == org.bukkit.Material.WATER || blockBelow == org.bukkit.Material.LAVA)
            return false;
        if (blockAt.isSolid())
            return false;
        if (blockAbove.isSolid())
            return false;

        return true;
    }

    public void spawn() {
        if (villager != null)
            return;
        if (!player.isOnline())
            return;

        Location playerLoc = player.getLocation();

        // Solo spawnear en el mundo "world" (no en minijuegos, nether, end, etc)
        if (!playerLoc.getWorld().getName().equals("world")) {
            plugin.getLogger().info("[ShadowHunter] El Errante no aparece en " + playerLoc.getWorld().getName()
                    + " para " + player.getName());
            return;
        }
        
        // Bloquear spawn en mundo de minijuegos
        if (playerLoc.getWorld().getName().equals("minigames")) {
            plugin.getLogger().info("[ShadowHunter] El Errante bloqueado en mundo de minijuegos para " + player.getName());
            return;
        }

        // Encontrar ubicación segura; si no hay espacio, spawnear literalmente encima o
        // enfrente del jugador
        Location spawnLoc = findSafeNPCSpawnLocation(playerLoc);
        if (spawnLoc == null) {
            spawnLoc = playerLoc.clone().add(playerLoc.getDirection().normalize().multiply(1.5));
            if (spawnLoc.getBlock().getType().isSolid()) {
                spawnLoc = playerLoc.clone(); // Fallback para túneles 1x2
            }
        }

        // Efecto de aparición
        World world = spawnLoc.getWorld();
        world.spawnParticle(Particle.SMOKE, spawnLoc, 40, 1, 2, 1, 0.05);
        world.spawnParticle(Particle.PORTAL, spawnLoc, 60, 1, 2, 1, 0.5);
        world.playSound(spawnLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.5f);

        // Crear Villager
        villager = (Villager) world.spawnEntity(spawnLoc, EntityType.VILLAGER);
        villager.setProfession(Villager.Profession.NITWIT);
        villager.setCustomName(SmallCaps.convert(isQuest10 ? "§5§l✦ El Errante §7§o[Última Vez]" : isQuest9 ? "§e§l☆ El Centinela" : isQuest8 ? "§b§l❄ El Heraldo" : isQuest7 ? "§4§l☠ La Sombra Sin Nombre" : isQuest6 ? "§b§l♡ Lyra" : isQuest5 ? "§3§l◈ El Eco" : "§5§l✦ El Errante"));
        villager.setCustomNameVisible(true);
        villager.setAI(true);
        villager.setInvulnerable(true);
        villager.setSilent(true);
        villager.setCollidable(false);
        villager.setRemoveWhenFarAway(false);
        villager.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.WATER_BREATHING,
                99999, 0, false, false));
        // Tag para proteger de plugins de limpieza de entidades
        villager.addScoreboardTag("errante_npc");
        villager.addScoreboardTag("wardstone_mission_mob");
        // Persistir: no permitir que desaparezca
        villager.setPersistent(true);

        plugin.getLogger().info("[ShadowHunter] Errante spawneado en " + spawnLoc.getBlockX() + ", " 
                + spawnLoc.getBlockY() + ", " + spawnLoc.getBlockZ() + " para " + player.getName());

        // Equipar con ropa oscura
        equipNPC();

        // Notificar al jugador
        player.sendMessage("");
        if (isQuest10) {
            player.sendMessage(SmallCaps.convert("§5§l✦ §7Una presencia familiar y... debilitada aparece."));
            player.sendMessage(SmallCaps.convert("§7§o*El Errante está aquí. Pero algo está mal. Se tambalea.*"));
            player.sendMessage(SmallCaps.convert("§7§o*Su cuerpo parpadea entre luz y oscuridad.*"));
        } else if (isQuest9) {
            player.sendMessage(SmallCaps.convert("§e§l☆ §6Una luz cálida y cegadora inunda el lugar..."));
            player.sendMessage(SmallCaps.convert("§e§o*Una figura radiante se materializa. Su presencia irradia calma... y poder.*"));
        } else if (isQuest8) {
            player.sendMessage(SmallCaps.convert("§b§l❄ §3El aire se congela. La temperatura baja bruscamente..."));
            player.sendMessage(SmallCaps.convert("§3§o*Una figura cubierta de escarcha emerge de la nada. No es humana.*"));
        } else if (isQuest7) {
            player.sendMessage(SmallCaps.convert("§4§l☠ §8Una presencia OSCURA y ANTIGUA aparece... El aire se congela."));
            player.sendMessage(SmallCaps.convert("§8§o*No es el Errante. No es Lyra. Es algo que existía antes que todo.*"));
        } else if (isQuest6) {
            player.sendMessage(SmallCaps.convert("§b§l♡ §7Una figura translúcida y triste aparece cerca de ti..."));
            player.sendMessage(SmallCaps.convert("§7§o*No es el Errante... es alguien más joven. Una chica.*"));
        } else if (isQuest5) {
            player.sendMessage(SmallCaps.convert("§3§l◈ §7Una figura borrosa y silenciosa aparece cerca de ti..."));
        } else {
            player.sendMessage(SmallCaps.convert("§5§l✦ §7Una presencia extraña aparece cerca de ti..."));
        }
        player.sendMessage("");
        player.playSound(player.getLocation(),
                isQuest10 ? Sound.BLOCK_AMETHYST_BLOCK_RESONATE : isQuest9 ? Sound.BLOCK_BEACON_ACTIVATE : isQuest8 ? Sound.ENTITY_PLAYER_HURT_FREEZE : isQuest7 ? Sound.ENTITY_WARDEN_EMERGE : isQuest6 ? Sound.ENTITY_ALLAY_DEATH : isQuest5 ? Sound.ENTITY_ALLAY_AMBIENT_WITH_ITEM : Sound.AMBIENT_CAVE,
                1.0f, isQuest10 ? 0.3f : isQuest9 ? 1.5f : isQuest8 ? 0.5f : isQuest7 ? 0.3f : isQuest6 ? 0.6f : isQuest5 ? 1.2f : 0.5f);

        // Iniciar seguimiento
        startFollowing();
        startParticles();

        // Iniciar timer de despawn (1 minuto)
        startDespawnTimer();
    }

    private void equipNPC() {
        if (villager == null)
            return;

        // Colores según misión: quest6 = azul claro/blanco (Lyra), quest5 = blanco/cian (El Eco), resto = negro/morado (El Errante)
        Color hColor = isQuest10 ? Color.fromRGB(80, 40, 100) : isQuest9 ? Color.fromRGB(255, 240, 180) : isQuest8 ? Color.fromRGB(200, 240, 255) : isQuest6 ? Color.fromRGB(180, 220, 255) : isQuest5 ? Color.fromRGB(200, 230, 245) : Color.fromRGB(30, 0, 50);
        Color cColor = isQuest10 ? Color.fromRGB(60, 30, 80) : isQuest9 ? Color.fromRGB(255, 230, 150) : isQuest8 ? Color.fromRGB(180, 230, 255) : isQuest6 ? Color.fromRGB(160, 210, 250) : isQuest5 ? Color.fromRGB(170, 215, 240) : Color.fromRGB(20, 0, 40);
        Color lColor = isQuest10 ? Color.fromRGB(50, 20, 70) : isQuest9 ? Color.fromRGB(255, 220, 120) : isQuest8 ? Color.fromRGB(160, 220, 250) : isQuest6 ? Color.fromRGB(140, 200, 245) : isQuest5 ? Color.fromRGB(150, 205, 235) : Color.fromRGB(15, 0, 30);
        Color bColor = isQuest10 ? Color.fromRGB(40, 15, 60) : isQuest9 ? Color.fromRGB(255, 215, 100) : isQuest8 ? Color.fromRGB(140, 210, 245) : isQuest6 ? Color.fromRGB(120, 190, 240) : isQuest5 ? Color.fromRGB(130, 195, 230) : Color.fromRGB(10, 0, 20);

        ItemStack helmet = new ItemStack(Material.LEATHER_HELMET);
        LeatherArmorMeta hMeta = (LeatherArmorMeta) helmet.getItemMeta();
        hMeta.setColor(hColor);
        helmet.setItemMeta(hMeta);

        ItemStack chest = new ItemStack(Material.LEATHER_CHESTPLATE);
        LeatherArmorMeta cMeta = (LeatherArmorMeta) chest.getItemMeta();
        cMeta.setColor(cColor);
        chest.setItemMeta(cMeta);

        ItemStack legs = new ItemStack(Material.LEATHER_LEGGINGS);
        LeatherArmorMeta lMeta = (LeatherArmorMeta) legs.getItemMeta();
        lMeta.setColor(lColor);
        legs.setItemMeta(lMeta);

        ItemStack boots = new ItemStack(Material.LEATHER_BOOTS);
        LeatherArmorMeta bMeta = (LeatherArmorMeta) boots.getItemMeta();
        bMeta.setColor(bColor);
        boots.setItemMeta(bMeta);

        villager.getEquipment().setHelmet(helmet);
        villager.getEquipment().setChestplate(chest);
        villager.getEquipment().setLeggings(legs);
        villager.getEquipment().setBoots(boots);
    }

    // ==================== FOLLOW ====================

    private Location lastNpcPos = null;
    private int stuckTicks = 0;

    private void startFollowing() {
        followTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (villager == null || villager.isDead() || !player.isOnline()) {
                    cancel();
                    return;
                }
                if (inDialogue)
                    return;

                Location npcLoc = villager.getLocation();
                Location playerLoc = player.getLocation();

                // Solo seguir si están en el mismo mundo "world"
                if (!npcLoc.getWorld().equals(playerLoc.getWorld()) || !playerLoc.getWorld().getName().equals("world")) {
                    dramaticTeleport(playerLoc);
                    return;
                }

                double distance = npcLoc.distance(playerLoc);

                // Si está cerca (< 3.5 bloques), iniciar diálogo
                if (distance < 3.5) {
                    startDialogue();
                    return;
                }

                // Detectar si está atascado (tolera muy poco ahora)
                if (lastNpcPos != null && npcLoc.distanceSquared(lastNpcPos) < 0.25) {
                    stuckTicks++;
                } else {
                    stuckTicks = 0;
                }
                lastNpcPos = npcLoc.clone();

                // TP Rápido: >25 bloques de distancia o atascado >20 ticks (1 segundo)
                if (distance > 25 || stuckTicks > 20) {
                    stuckTicks = 0;
                    dramaticTeleport(playerLoc);
                    return;
                }

                // === PATHFINDING NATURAL (AI activada) ===
                boolean inWater = villager.isInWater();
                if (inWater) {
                    // Nadar activamente hacia el jugador en agua
                    Vector dir = playerLoc.toVector().subtract(npcLoc.toVector()).normalize();
                    double speed = distance > 10 ? 0.35 : 0.25;
                    // Ajustar Y: bajar si el jugador está más abajo, subir si está más arriba
                    double dy = playerLoc.getY() - npcLoc.getY();
                    double vy = Math.max(-0.3, Math.min(0.3, dy * 0.15));
                    villager.setVelocity(new Vector(dir.getX() * speed, vy, dir.getZ() * speed));
                } else {
                    try {
                        // Intentar usar Paper Pathfinder API para caminar naturalmente
                        Object pathfinder = villager.getClass().getMethod("getPathfinder").invoke(villager);
                        java.lang.reflect.Method moveToMethod = pathfinder.getClass().getMethod("moveTo",
                                Location.class);
                        moveToMethod.invoke(pathfinder, playerLoc);
                    } catch (Exception e) {
                        // Fallback: usar velocity con AI habilitada (las piernas se animan)
                        Vector dir = playerLoc.toVector().subtract(npcLoc.toVector()).normalize();
                        double speed = distance > 15 ? 0.4 : (distance > 8 ? 0.3 : 0.2);
                        double vy = villager.getVelocity().getY();
                        Location ahead = npcLoc.clone().add(dir.clone().multiply(1.0));
                        ahead.setY(npcLoc.getBlockY());
                        if (ahead.getBlock().getType().isSolid())
                            vy = 0.42;
                        villager.setVelocity(new Vector(dir.getX() * speed, vy, dir.getZ() * speed));
                    }
                }

                // Mirar al jugador
                Location lookAt = npcLoc.clone();
                Vector toPlayer = playerLoc.toVector().subtract(npcLoc.toVector()).normalize();
                lookAt.setDirection(toPlayer);
                villager.setRotation(lookAt.getYaw(), lookAt.getPitch());
            }
        }.runTaskTimer(plugin, 0, 5); // Cada 5 ticks (0.25s)
    }

    /**
     * Rompe un bloque con efecto visual (partículas de rotura) y lo restaura
     * después de 30s.
     */
    private void mineBlock(org.bukkit.block.Block block) {
        if (block.getType() == Material.AIR || block.getType() == Material.BEDROCK)
            return;

        Material originalType = block.getType();
        org.bukkit.block.data.BlockData originalData = block.getBlockData().clone();
        Location loc = block.getLocation().add(0.5, 0.5, 0.5);

        // Efecto de rotura
        block.getWorld().spawnParticle(Particle.BLOCK, loc, 15, 0.3, 0.3, 0.3, 0.1, originalData);
        block.getWorld().playSound(loc, Sound.BLOCK_STONE_BREAK, 0.6f, 1.0f);

        // Romper el bloque
        block.setType(Material.AIR);

        // Restaurar el bloque después de 30 segundos
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (block.getType() == Material.AIR) {
                block.setBlockData(originalData);
            }
        }, 20L * 30);
    }

    /**
     * Verifica si un bloque es rompible por el NPC (no bedrock, no obsidiana, etc.)
     */
    private boolean isBreakable(Material mat) {
        if (mat == Material.AIR || mat == Material.CAVE_AIR || mat == Material.VOID_AIR)
            return false;
        if (mat == Material.BEDROCK)
            return false;
        if (mat == Material.OBSIDIAN || mat == Material.CRYING_OBSIDIAN)
            return false;
        if (mat == Material.REINFORCED_DEEPSLATE)
            return false;
        if (mat == Material.BARRIER)
            return false;
        if (mat == Material.END_PORTAL || mat == Material.END_PORTAL_FRAME)
            return false;
        if (mat == Material.NETHER_PORTAL)
            return false;
        if (mat == Material.COMMAND_BLOCK || mat == Material.CHAIN_COMMAND_BLOCK
                || mat == Material.REPEATING_COMMAND_BLOCK)
            return false;
        // No romper cofres, camas, etc. importantes
        if (mat.name().contains("CHEST") || mat.name().contains("SHULKER"))
            return false;
        return true;
    }

    /**
     * Teletransporte dramático con efectos cuando el NPC no puede llegar.
     */
    private void dramaticTeleport(Location playerLoc) {
        if (villager == null || villager.isDead())
            return;

        Location oldLoc = villager.getLocation();
        World world = oldLoc.getWorld();

        // Efecto de desaparición
        if (world != null) {
            world.spawnParticle(Particle.SMOKE, oldLoc.clone().add(0, 1, 0), 25, 0.4, 0.8, 0.4, 0.05);
            world.spawnParticle(Particle.PORTAL, oldLoc.clone().add(0, 1, 0), 30, 0.3, 0.6, 0.3, 0.5);
            world.playSound(oldLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 0.6f);
        }

        // Encontrar posición a 2-3 bloques del jugador (para no ahogarse underground)
        double angle = Math.random() * Math.PI * 2;
        double dist = 2 + Math.random() * 2;
        Location tpLoc = playerLoc.clone().add(Math.cos(angle) * dist, 0, Math.sin(angle) * dist);

        // Si el bloque no es aire, spawnear exactamente donde está el jugador (seguro
        // si está túnelando)
        if (tpLoc.getBlock().getType().isSolid() || tpLoc.clone().add(0, 1, 0).getBlock().getType().isSolid()) {
            tpLoc = playerLoc.clone().add(playerLoc.getDirection().normalize().multiply(1));
            if (tpLoc.getBlock().getType().isSolid()) {
                tpLoc = playerLoc.clone();
            }
        }

        // Darle slow falling si el jugador está volando
        if (tpLoc.getBlock().getRelative(org.bukkit.block.BlockFace.DOWN).getType().isAir()) {
            villager.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SLOW_FALLING,
                    100, 0, false, false));
        }

        // Teletransportar
        tpLoc.setDirection(playerLoc.toVector().subtract(tpLoc.toVector()));
        villager.teleport(tpLoc);

        // Efecto de aparición
        Location finalLoc = tpLoc;
        World tpWorld = tpLoc.getWorld();
        if (tpWorld != null) {
            tpWorld.spawnParticle(Particle.PORTAL, finalLoc.clone().add(0, 1, 0), 40, 0.5, 1, 0.5, 0.5);
            tpWorld.spawnParticle(Particle.SOUL, finalLoc.clone().add(0, 1.5, 0), 10, 0.3, 0.5, 0.3, 0.02);
            tpWorld.playSound(finalLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 0.8f);
        }

        if (player.isOnline()) {
            player.sendMessage(SmallCaps.convert("§5§l✦ §7El Errante aparece de las sombras cerca de ti..."));
        }
    }

    private void startParticles() {
        particleTask = new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (villager == null || villager.isDead()) {
                    cancel();
                    return;
                }

                Location loc = villager.getLocation().add(0, 1.5, 0);

                // Partículas de soul trail
                loc.getWorld().spawnParticle(Particle.SOUL, loc, 2, 0.3, 0.5, 0.3, 0.01);

                // Partículas de encantamiento ocasionales
                if (ticks % 10 == 0) {
                    loc.getWorld().spawnParticle(Particle.ENCHANT, loc, 5, 0.5, 0.5, 0.5, 0.5);
                }

                // Aura oscura
                if (ticks % 20 == 0) {
                    loc.getWorld().spawnParticle(Particle.DUST,
                            loc, 8, 0.6, 0.8, 0.6, 0,
                            new Particle.DustOptions(Color.fromRGB(80, 0, 120), 1.2f));
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0, 3);
    }

    // ==================== DIÁLOGO ====================

    public void startDialogue() {
        if (inDialogue)
            return;
        inDialogue = true;
        dialogueStep = 0;

        // Cancelar timer de despawn al iniciar diálogo (especialmente importante para Quest 4 que es largo)
        cancelDespawnTimer();

        // Detener movimiento permanentemente
        if (followTask != null) {
            followTask.cancel();
            followTask = null;
        }

        // Desactivar IA del villager para que no se mueva por cuenta propia
        villager.setAI(false);
        villager.setCollidable(false);

        // Mirar al jugador y quedarse quieto
        Location npcLoc = villager.getLocation();
        npcLoc.setDirection(player.getLocation().toVector().subtract(npcLoc.toVector()));
        villager.teleport(npcLoc);

        // Iniciar tarea para mantenerlo mirando al jugador sin moverse
        new BukkitRunnable() {
            @Override
            public void run() {
                if (villager == null || villager.isDead() || !player.isOnline()) {
                    cancel();
                    return;
                }
                // Solo rotar para mirar al jugador, sin moverse
                Location vLoc = villager.getLocation();
                Vector toPlayer = player.getLocation().toVector().subtract(vLoc.toVector()).normalize();
                vLoc.setDirection(toPlayer);
                villager.setRotation(vLoc.getYaw(), vLoc.getPitch());
            }
        }.runTaskTimer(plugin, 0, 10);

        manager.getPlugin().getLogger().info("[ShadowHunter] Diálogo iniciado con " + player.getName());

        // Efecto de diálogo
        player.getWorld().spawnParticle(Particle.ENCHANT, villager.getLocation().add(0, 2, 0), 30, 0.5, 0.5, 0.5, 1);
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8f, 1.2f);

        // Diálogo tipo máquina de escribir
        if (isQuest10) {
            sendTypewriterDialogueQuest10();
        } else if (isQuest9) {
            sendTypewriterDialogueQuest9();
        } else if (isQuest8) {
            sendTypewriterDialogueQuest8();
        } else if (isQuest7) {
            sendTypewriterDialogueQuest7();
        } else if (isQuest6) {
            sendTypewriterDialogueQuest6();
        } else if (isQuest5) {
            sendTypewriterDialogueQuest5();
        } else if (isQuest4) {
            sendTypewriterDialogueQuest4();
        } else if (isQuest3) {
            sendTypewriterDialogueQuest3();
        } else if (isQuest2) {
            sendTypewriterDialogueQuest2();
        } else {
            sendTypewriterDialogue();
        }
    }

    private boolean isQuest5 = false;
    private boolean isQuest6 = false;
    private boolean isQuest7 = false;

    public void setQuest5(boolean quest5) {
        this.isQuest5 = quest5;
    }

    public boolean isQuest5() {
        return isQuest5;
    }

    public void setQuest6(boolean quest6) {
        this.isQuest6 = quest6;
    }

    public boolean isQuest6() {
        return isQuest6;
    }

    public void setQuest2(boolean quest2) {
        this.isQuest2 = quest2;
    }

    public boolean isQuest2() {
        return isQuest2;
    }

    private boolean isQuest3 = false;

    public void setQuest3(boolean quest3) {
        this.isQuest3 = quest3;
    }

    public boolean isQuest3() {
        return isQuest3;
    }

    private boolean isQuest4 = false;

    public void setQuest4(boolean quest4) {
        this.isQuest4 = quest4;
    }

    public boolean isQuest4() {
        return isQuest4;
    }

    public void setQuest7(boolean quest7) {
        this.isQuest7 = quest7;
    }

    public boolean isQuest7() {
        return isQuest7;
    }

    private boolean isQuest8 = false;
    private boolean isQuest9 = false;

    public void setQuest8(boolean quest8) {
        this.isQuest8 = quest8;
    }

    public boolean isQuest8() {
        return isQuest8;
    }

    public void setQuest9(boolean quest9) {
        this.isQuest9 = quest9;
    }

    public boolean isQuest9() {
        return isQuest9;
    }

    private boolean isQuest10 = false;

    public void setQuest10(boolean quest10) {
        this.isQuest10 = quest10;
    }

    public boolean isQuest10() {
        return isQuest10;
    }

    // ==================== MISIÓN 4: DIÁLOGO TRISTE Y LENTO ====================

    private void sendTypewriterDialogueQuest4() {
        // Diálogo largo, triste, lento. No se puede saltar.
        String[][] dialogues = {
                { "§5§l✦ El Errante §7susurra con voz quebrada:",
                        "§f\"...Nos volvemos a ver. Por última vez.\"" },
                { "§5§l✦ El Errante §7baja la mirada:",
                        "§7§o*Sus manos tiemblan. Algo ha cambiado en él.*" },
                { "§5§l✦ El Errante:",
                        "§f\"Hay algo que nunca te conté...\"" },
                { "§5§l✦ El Errante:",
                        "§f\"Yo no siempre fui... esto.\"" },
                { "§5§l✦ El Errante §7se toca el pecho:",
                        "§f\"Fui un jugador. Como tú. Tenía un nombre. Una vida.\"" },
                { "§5§l✦ El Errante:",
                        "§f\"Construía cosas hermosas. Tenía amigos. Un hogar.\"" },
                { "§5§l✦ El Errante §7cierra los ojos:",
                        "§7§o*Una lágrima de partículas moradas cae de su rostro.*" },
                { "§5§l✦ El Errante:",
                        "§f\"Pero encontré algo que no debía. El Vacío.\"" },
                { "§5§l✦ El Errante:",
                        "§f\"Me prometió poder. Conocimiento. La verdad del mundo.\"" },
                { "§5§l✦ El Errante §7aprieta los puños:",
                        "§c\"Y me lo quitó todo. Mi nombre. Mis recuerdos. Mi cuerpo.\"" },
                { "§5§l✦ El Errante:",
                        "§f\"Cada misión que te di... era un pedazo de mí que se iba.\"" },
                { "§5§l✦ El Errante:",
                        "§f\"La espada. El hacha. El pico. Eran fragmentos de mi alma.\"" },
                { "§5§l✦ El Errante §7tiembla:",
                        "§c\"Ya casi no queda nada de mí...\"" },
                { "§5§l✦ El Errante:",
                        "§f\"Pero hay un último boss. Un fantasma de quien fui.\"" },
                { "§5§l✦ El Errante:",
                        "§f\"Está atrapado en dolor. En un bucle eterno. Sufriendo.\"" },
                { "§5§l✦ El Errante §7te mira a los ojos:",
                        "§d\"Necesito que lo liberes. Que ME liberes.\"" },
                { "§5§l✦ El Errante:",
                        "§f\"No te pido que luches por mí.\"" },
                { "§5§l✦ El Errante:",
                        "§d\"Te pido que me dejes ir.\"" },
                { "§5§l✦ El Errante §7sonríe con tristeza:",
                        "§f\"Si ganas... te daré mi armadura. Lo último que me queda.\"" },
                { "§5§l✦ El Errante:",
                        "§7§o*Extiende sus manos temblorosas hacia ti.*" },
                { "§5§l✦ El Errante:",
                        "§c\"El Eco es poderoso. Mucho más que yo ahora. Ten cuidado.\"" },
                { "§5§l✦ El Errante §7con la voz rota:",
                        "§d\"¿Aceptas... liberarme?\"" },
        };

        dialogueTask = new BukkitRunnable() {
            int step = 0;
            int charIndex = 0;
            String currentLine = "";
            boolean sendingLine = false;
            int pauseTicks = 0;

            @Override
            public void run() {
                if (!player.isOnline() || villager == null || villager.isDead()) {
                    cancel();
                    return;
                }

                // Pausa entre líneas (más lento, más emotivo)
                if (pauseTicks > 0) {
                    pauseTicks--;
                    return;
                }

                if (step >= dialogues.length) {
                    cancel();
                    showDialogueOptions();
                    return;
                }

                if (!sendingLine) {
                    player.sendMessage("");
                    player.sendMessage(dialogues[step][0]);
                    currentLine = dialogues[step][1];
                    charIndex = 0;
                    sendingLine = true;

                    // Sonido triste, suave
                    player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.3f,
                            0.4f + (float) (Math.random() * 0.3));

                    // Partículas tristes (almas ascendiendo)
                    if (villager != null) {
                        villager.getWorld().spawnParticle(Particle.SOUL,
                                villager.getLocation().add(0, 2.2, 0), 3, 0.2, 0.3, 0.2, 0.02);
                    }

                    // Pausa de 1.5s antes de mostrar el texto
                    pauseTicks = 15;
                    return;
                }

                // Efecto typewriter: avanzar caracteres
                charIndex += 2;
                if (charIndex >= currentLine.length()) {
                    player.sendMessage(currentLine);

                    // Lágrimas de partículas en momentos emotivos
                    if (step == 6 || step == 9 || step == 12 || step == 17) {
                        if (villager != null) {
                            Location vLoc = villager.getLocation().add(0, 1.8, 0);
                            vLoc.getWorld().spawnParticle(Particle.DUST, vLoc, 8, 0.1, 0.3, 0.1, 0,
                                    new Particle.DustOptions(Color.fromRGB(150, 80, 220), 0.8f));
                            vLoc.getWorld().spawnParticle(Particle.DRIPPING_WATER, vLoc, 3, 0.1, 0.1, 0.1, 0);
                        }
                        player.playSound(player.getLocation(), Sound.ENTITY_ALLAY_HURT, 0.2f, 0.5f);
                    }

                    sendingLine = false;
                    step++;

                    // Pausa más larga entre diálogos (3-4 segundos para que sea emotivo)
                    pauseTicks = 30 + (int)(Math.random() * 20);
                } else {
                    // Sonido sutil de typewriter
                    if (charIndex % 6 == 0) {
                        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.1f,
                                1.2f + (float) Math.random() * 0.5f);
                    }
                }
            }
        }.runTaskTimer(plugin, 60L, 2L); // 3s delay inicial, cada 0.1s tick
    }

    private void sendTypewriterDialogueQuest3() {
        String[][] dialogues = {
                { "§0§l✦ El Errante §7susurra:", "§f\"Has superado la sombra... y el abismo...\"" },
                { "§0§l✦ El Errante:", "§f\"Pero hay algo más. Algo que trasciende todo.\"" },
                { "§0§l✦ El Errante:", "§f\"El Vacío mismo me creó. Y ahora... quiere crearte a ti.\"" },
                { "§0§l✦ El Errante:", "§f\"No será una prueba de fuerza. Será una prueba de VOLUNTAD.\"" },
                { "§0§l✦ El Errante:",
                        "§c\"Jugaré con tu mente, tu dinero, tu inventario... ¿estás preparado?\"" },
                { "§0§l✦ El Errante:",
                        "§e\"[¡IMPORTANTE!] Activa TODAS las partículas en ajustes de vídeo. Las necesitarás.\"" },
                { "§0§l✦ El Errante:", "§c\"[¡PELIGRO!] Esta misión es EXTREMADAMENTE difícil. ¡Ten cuidado!\"" },
                { "§0§l✦ El Errante:", "§f\"¿Aceptas el desafío del Arquitecto del Vacío?\"" },
        };

        dialogueTask = new BukkitRunnable() {
            int step = 0;
            int charIndex = 0;
            String currentLine = "";
            boolean sendingLine = false;

            @Override
            public void run() {
                if (!player.isOnline() || villager == null || villager.isDead()) {
                    cancel();
                    return;
                }

                if (step >= dialogues.length) {
                    cancel();
                    showDialogueOptions();
                    return;
                }

                if (!sendingLine) {
                    player.sendMessage("");
                    player.sendMessage(dialogues[step][0]);
                    currentLine = dialogues[step][1];
                    charIndex = 0;
                    sendingLine = true;
                    player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_AMBIENT, 0.3f, 1.5f);
                }

                charIndex += 2;
                if (charIndex >= currentLine.length()) {
                    player.sendMessage(currentLine);
                    step++;
                    sendingLine = false;
                } else {
                    if (charIndex % 4 == 0) {
                        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.15f,
                                1.5f + (float) Math.random() * 0.5f);
                    }
                }
            }
        }.runTaskTimer(plugin, 40L, 2L);
    }

    private void sendTypewriterDialogue() {
        String[][] dialogues = {
                { "§5§l✦ El Errante §7susurra:", "§f\"Te he estado observando...\"" },
                { "§5§l✦ El Errante:", "§f\"Hay algo dentro de ti... un poder dormido.\"" },
                { "§5§l✦ El Errante:", "§f\"Las sombras me enviaron a buscarte.\"" },
                { "§5§l✦ El Errante:", "§f\"Puedo despertar ese poder... pero deberás probarte.\"" },
                { "§5§l✦ El Errante:",
                        "§e\"[¡IMPORTANTE!] Activa todas las partículas en tus ajustes de vídeo. Las necesitarás.\"" },
                { "§5§l✦ El Errante:", "§f\"¿Aceptas el desafío del Cazador de Sombras?\"" },
        };

        dialogueTask = new BukkitRunnable() {
            int step = 0;
            int charIndex = 0;
            String currentLine = "";
            boolean sendingLine = false;

            @Override
            public void run() {
                if (!player.isOnline() || villager == null || villager.isDead()) {
                    cancel();
                    return;
                }

                if (step >= dialogues.length) {
                    // Último paso: mostrar opciones
                    cancel();
                    showDialogueOptions();
                    return;
                }

                if (!sendingLine) {
                    // Enviar header
                    player.sendMessage("");
                    player.sendMessage(dialogues[step][0]);
                    currentLine = dialogues[step][1];
                    charIndex = 0;
                    sendingLine = true;

                    // Sonido de susurro
                    player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f,
                            0.8f + (float) (Math.random() * 0.4));
                    return;
                }

                // Efecto máquina de escribir: mostrar la línea completa de golpe
                // (enviar carácter a carácter es imposible sin packets, así que enviamos la
                // línea con delay)
                player.sendMessage(currentLine);

                // Partícula sutil
                if (villager != null) {
                    villager.getWorld().spawnParticle(Particle.SOUL,
                            villager.getLocation().add(0, 2.2, 0), 3, 0.2, 0.1, 0.2, 0.01);
                }

                sendingLine = false;
                step++;
            }
        }.runTaskTimer(plugin, 20, 50); // 1s delay inicial, 2.5s entre líneas
    }

    // ==================== MISIÓN 8: DIÁLOGO DEL HERALDO ====================

    private void sendTypewriterDialogueQuest8() {
        String[][] dialogues = {
                { "§b§l❄ El Heraldo §3habla con voz cristalina:",
                        "§f\"...Al fin nos encontramos, portador de reliquias.\"" },
                { "§b§l❄ El Heraldo §3observa tu equipo del Vacío:",
                        "§7§o*Cristales de hielo se forman en el aire a su alrededor.*" },
                { "§b§l❄ El Heraldo:",
                        "§f\"Antes de que el Vacío consumiera dimensiones... existía el Frío Primordial.\"" },
                { "§b§l❄ El Heraldo §3cierra los ojos:",
                        "§f\"Yo fui su campeón. Congelé mundos enteros en su nombre.\"" },
                { "§b§l❄ El Heraldo:",
                        "§f\"Pero cuando el Vacío llegó... incluso el hielo fue devorado.\"" },
                { "§b§l❄ El Heraldo §3te mira fijamente:",
                        "§f\"Tus reliquias cantan. Puedo oírlas. El Vacío en ellas... me despierta.\"" },
                { "§b§l❄ El Heraldo §3extiende una mano congelada:",
                        "§f\"Solo hay una forma de resolver esto.\"" },
                { "§b§l❄ El Heraldo:",
                        "§f\"Si me derrotas, el poder del Frío Primordial será tuyo.\"" },
                { "§b§l❄ El Heraldo §3sonríe con tristeza:",
                        "§f\"Y yo... al fin podré descansar después de eones de frío.\"" },
                { "§b§l❄ El Heraldo:",
                        "§e\"¿Estás listo para enfrentar el Cero Absoluto?\"" },
        };

        dialogueTask = new BukkitRunnable() {
            int step = 0;
            int charIndex = 0;
            String currentLine = "";
            boolean sendingLine = false;

            @Override
            public void run() {
                if (!player.isOnline() || villager == null || villager.isDead()) {
                    cancel();
                    return;
                }

                if (step >= dialogues.length) {
                    cancel();
                    showDialogueOptions();
                    return;
                }

                if (!sendingLine) {
                    player.sendMessage("");
                    player.sendMessage(dialogues[step][0]);
                    currentLine = dialogues[step][1];
                    charIndex = 0;
                    sendingLine = true;
                    player.playSound(player.getLocation(), Sound.BLOCK_POWDER_SNOW_STEP, 0.5f,
                            0.8f + (float) (Math.random() * 0.4));
                    return;
                }

                player.sendMessage(currentLine);

                if (villager != null) {
                    villager.getWorld().spawnParticle(Particle.SNOWFLAKE,
                            villager.getLocation().add(0, 2.2, 0), 5, 0.3, 0.2, 0.3, 0.02);
                }

                sendingLine = false;
                step++;
            }
        }.runTaskTimer(plugin, 20, 50);
    }

    // ==================== MISIÓN 9: DIÁLOGO DEL CENTINELA ====================

    private void sendTypewriterDialogueQuest9() {
        String[][] dialogues = {
                { "§e§l☆ El Centinela §6habla con voz resonante:",
                        "§f\"...Has llegado más lejos que cualquier otro, cazador.\"" },
                { "§e§l☆ El Centinela §6observa tus reliquias con reverencia:",
                        "§7§o*Su cuerpo emite una luz cálida que hace retroceder las sombras.*" },
                { "§e§l☆ El Centinela:",
                        "§f\"Yo soy lo que queda del principio. Antes del Vacío, antes del Frío...\"" },
                { "§e§l☆ El Centinela:",
                        "§f\"Existía la Luz. La primera fuerza. El Origen de todo.\"" },
                { "§e§l☆ El Centinela §6levanta la mano:",
                        "§7§o*Una estrella diminuta se forma sobre su palma, tan brillante que duele mirar.*" },
                { "§e§l☆ El Centinela:",
                        "§f\"El Abismo devora. El Hielo congela. Pero la Luz... la Luz crea.\"" },
                { "§e§l☆ El Centinela §6te mira directamente:",
                        "§f\"Tu Devoradora de Almas... la puedo sentir. Hambrienta. Oscura.\"" },
                { "§e§l☆ El Centinela:",
                        "§f\"Necesita una contraparte. Un equilibrio. Sin él... te consumirá.\"" },
                { "§e§l☆ El Centinela §6cierra el puño sobre la estrella:",
                        "§f\"Demuéstrame que eres digno de portar ambas fuerzas.\"" },
                { "§e§l☆ El Centinela §6sonríe con calidez:",
                        "§f\"Derrótame, y la Estrella del Origen será tuya.\"" },
                { "§e§l☆ El Centinela:",
                        "§e\"¿Estás listo para enfrentar la Luz Primordial?\"" },
        };

        dialogueTask = new BukkitRunnable() {
            int step = 0;
            int charIndex = 0;
            String currentLine = "";
            boolean sendingLine = false;

            @Override
            public void run() {
                if (!player.isOnline() || villager == null || villager.isDead()) {
                    cancel();
                    return;
                }

                if (step >= dialogues.length) {
                    cancel();
                    showDialogueOptions();
                    return;
                }

                if (!sendingLine) {
                    player.sendMessage("");
                    player.sendMessage(dialogues[step][0]);
                    currentLine = dialogues[step][1];
                    charIndex = 0;
                    sendingLine = true;
                    player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f,
                            1.0f + (float) (Math.random() * 0.5));
                    return;
                }

                player.sendMessage(currentLine);

                if (villager != null) {
                    villager.getWorld().spawnParticle(Particle.END_ROD,
                            villager.getLocation().add(0, 2.2, 0), 5, 0.3, 0.2, 0.3, 0.03);
                }

                sendingLine = false;
                step++;
            }
        }.runTaskTimer(plugin, 20, 50);
    }

    // ==================== MISIÓN 10: DIÁLOGO FINAL — EL FIN ====================

    private void sendTypewriterDialogueQuest10() {
        String[][] dialogues = {
                { "§5§l✦ El Errante §7habla con voz rota y temblorosa:",
                        "§f\"...Sabía que vendrías. Siempre lo supe.\"" },
                { "§5§l✦ El Errante §7tose. Partículas oscuras escapan de su cuerpo:",
                        "§7§o*Su forma parpadea. Por un instante, ves a un hombre joven. Luego, a algo roto.*" },
                { "§5§l✦ El Errante:",
                        "§f\"¿Recuerdas nuestra primera vez? Aparecí de la nada...\"" },
                { "§5§l✦ El Errante:",
                        "§f\"Tú no sabías quién era yo. Yo sí sabía quién eras tú.\"" },
                { "§5§l✦ El Errante §7mira al cielo:",
                        "§7§o*Lágrimas caen por su rostro, pero se evaporan antes de tocar el suelo.*" },
                { "§5§l✦ El Errante:",
                        "§f\"Cada misión... cada arma que te di... era un pedazo de mi alma.\"" },
                { "§5§l✦ El Errante:",
                        "§f\"La Hoja del Vacío. La Devoradora. La Elegía. La Estrella...\"" },
                { "§5§l✦ El Errante §7se lleva la mano al pecho:",
                        "§f\"Todo lo que me hacía... yo. Ya no queda nada.\"" },
                { "§5§l✦ El Errante §7te mira con ojos llenos de dolor:",
                        "§f\"La corrupción que destruyó mi mundo... me ha alcanzado.\"" },
                { "§5§l✦ El Errante:",
                        "§f\"Si no me detienes... me convertiré en lo que juré destruir.\"" },
                { "§5§l✦ El Errante §7extiende la mano hacia ti:",
                        "§7§o*Su mano tiembla. Una lágrima cae.*" },
                { "§5§l✦ El Errante:",
                        "§5§o\"Por favor... no dejes que la corrupción me use.\"" },
                { "§5§l✦ El Errante:",
                        "§5§o\"Mátame. Es lo último que te pido.\"" },
                { "§5§l✦ El Errante §7baja la cabeza:",
                        "§7§o\"...¿Lo harás?\"" },
        };

        dialogueTask = new BukkitRunnable() {
            int step = 0;
            boolean sendingLine = false;

            @Override
            public void run() {
                if (villager == null || villager.isDead() || !player.isOnline()) {
                    cancel();
                    return;
                }

                if (sendingLine) return;
                if (step >= dialogues.length) {
                    cancel();
                    showDialogueOptions();
                    return;
                }

                sendingLine = true;
                String[] lines = dialogues[step];
                player.sendMessage("");
                for (String line : lines) {
                    player.sendMessage(SmallCaps.convert(line));
                }
                player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.4f, 0.3f);

                sendingLine = false;
                step++;
            }
        }.runTaskTimer(plugin, 20, 50);
    }

    public void showDialogueOptions() {
        dialogueOptionsShown = true;
        player.sendMessage("");
        player.sendMessage(SmallCaps.convert("§8§m                                          "));
        if (isQuest10) {
            player.sendMessage(SmallCaps.convert("  §a§l[ACEPTAR] §7- Acabar con el sufrimiento del Errante"));
            player.sendMessage(SmallCaps.convert("  §c§l[RECHAZAR] §7- No puedes hacerlo (12 horas)"));
        } else if (isQuest9) {
            player.sendMessage(SmallCaps.convert("  §a§l[ACEPTAR] §7- Desafiar al Centinela del Origen"));
            player.sendMessage(SmallCaps.convert("  §c§l[RECHAZAR] §7- No estás listo (12 horas)"));
        } else if (isQuest8) {
            player.sendMessage(SmallCaps.convert("  §a§l[ACEPTAR] §7- Enfrentar al Heraldo del Hielo Eterno"));
            player.sendMessage(SmallCaps.convert("  §c§l[RECHAZAR] §7- Huir del frío (12 horas)"));
        } else if (isQuest7) {
            player.sendMessage(SmallCaps.convert("  §a§l[ACEPTAR] §7- Descender al Vientre del Vacío"));
            player.sendMessage(SmallCaps.convert("  §c§l[RECHAZAR] §7- Huir de la oscuridad (12 horas)"));
        } else if (isQuest6) {
            player.sendMessage(SmallCaps.convert("  §a§l[ACEPTAR] §7- Ayudar a Lyra a encontrar paz"));
            player.sendMessage(SmallCaps.convert("  §c§l[RECHAZAR] §7- No estás listo (Lyra vuelve en 12 horas)"));
        } else if (isQuest5) {
            player.sendMessage(SmallCaps.convert("  §a§l[ACEPTAR] §7- Ir al Jardín del Cielo"));
            player.sendMessage(SmallCaps.convert("  §c§l[RECHAZAR] §7- No estás listo (El Eco vuelve en 12 horas)"));
        } else if (isQuest4) {
            player.sendMessage(SmallCaps.convert("  §a§l[ACEPTAR] §7- Liberar al Errante"));
            player.sendMessage(SmallCaps.convert("  §c§l[RECHAZAR] §7- No estás listo (NPC vuelve en 12 horas)"));
        } else if (isQuest3) {
            player.sendMessage(SmallCaps.convert("  §a§l[ACEPTAR] §7- Acepta el desafío del Arquitecto"));
            player.sendMessage(SmallCaps.convert("  §c§l[RECHAZAR] §7- Rechaza (NPC vuelve en 12 horas)"));
        } else if (isQuest2) {
            player.sendMessage(SmallCaps.convert("  §a§l[ACEPTAR] §7- Acepta el desafío del Abismo"));
            player.sendMessage(SmallCaps.convert("  §c§l[RECHAZAR] §7- Rechaza (NPC vuelve en 12 horas)"));
        } else {
            player.sendMessage(SmallCaps.convert("  §a§l[ACEPTAR] §7- Acepta el desafío"));
            player.sendMessage(SmallCaps.convert("  §c§l[RECHAZAR] §7- Rechaza (NPC vuelve en 12 horas)"));
        }
        player.sendMessage(SmallCaps.convert("§8§m                                          "));
        player.sendMessage(SmallCaps.convert("§7Escribe §a\"aceptar\" §7o §c\"rechazar\" §7en el chat."));
        player.sendMessage("");

        manager.getPlugin().getLogger().info("[ShadowHunter] Opciones mostradas a " + player.getName());

        // Hard despawn timer: 2 minutes after dialogue options are shown
        // If the player doesn't respond with "aceptar" or "rechazar", force despawn
        startPostDialogueDespawnTimer();
    }

    // ==================== MISIÓN 7: DIÁLOGO LA SOMBRA SIN NOMBRE ====================

    private void sendTypewriterDialogueQuest7() {
        String[] dialogues = {
                "§4§l☠ La Sombra Sin Nombre §8aparece... pero no tiene forma.",
                "§8§o*El aire se congela. La temperatura desciende. Algo antiguo te observa.*",
                "§4§l☠ §8Una voz sin origen susurra directamente en tu mente:",
                "§4\"...Tú. El que mató al Devorador. Al Titán. Al Arquitecto.\"",
                "§4\"El que liberó al cobarde del Errante.\"",
                "§4\"El que escaló hasta el cielo buscando paz.\"",
                "§4\"El que hizo llorar a la niña por última vez.\"",
                "§4§l☠ §8La sombra se intensifica... puedes ver rostros en ella.",
                "§4\"¿Sabes quién soy?\"",
                "§4\"Yo estaba aquí antes que las sombras.\"",
                "§4\"Antes que el Errante perdiera su nombre.\"",
                "§4\"Antes que Lyra cayera al vacío.\"",
                "§4§l☠ §8La voz se vuelve más profunda, más oscura.",
                "§4\"Yo soy lo que queda cuando todo se olvida.\"",
                "§4\"Cuando un nombre se pierde. Cuando un recuerdo muere.\"",
                "§4\"Yo consumí al Errante. Yo devoré a Lyra.\"",
                "§4\"Y ahora... §c§l§ote quiero a ti§4§o.\"",
                "§4§l☠ §8Puedes sentir cómo la oscuridad se cierra a tu alrededor.",
                "§4\"Hay un lugar debajo del mundo. Debajo de todo.\"",
                "§4\"Lo llaman El Vientre del Vacío. Es mi hogar.\"",
                "§4\"Es un laberinto hecho de almas olvidadas.\"",
                "§4\"Los que entran no vuelven. Los que mueren no descansan.\"",
                "§4\"Vagan por los pasillos para siempre... sin nombre, sin recuerdos.\"",
                "§4§l☠ §c§oSientes una presión en el pecho. Tu visión se oscurece.",
                "§4\"Pero tú... tú eres diferente, ¿verdad?\"",
                "§4\"Has matado monstruos. Has salvado almas.\"",
                "§4\"¿Podrás sobrevivir a lo que hay abajo?\"",
                "§4\"¿Podrás encontrar los §c§l3 fragmentos de alma§4§o que me mantienen sellado?\"",
                "§4\"¿Podrás §c§l§omatarme§4§o?\"",
                "§4§l☠ §8La Sombra Sin Nombre ríe. El sonido te hiela la sangre.",
                "§4\"Desciende, cazador. Desciende al Vientre del Vacío.\"",
                "§4\"Y descubre lo que significa... ser §c§l§oolvidado§4§o.\"",
        };

        dialogueTask = new BukkitRunnable() {
            int i = 0;
            @Override
            public void run() {
                if (!inDialogue || !player.isOnline() || i >= dialogues.length) {
                    if (i >= dialogues.length) {
                        showDialogueOptions();
                    }
                    cancel();
                    return;
                }

                player.sendMessage(SmallCaps.convert(dialogues[i]));

                // Sound effects for atmosphere
                if (dialogues[i].contains("☠")) {
                    player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 0.6f, 0.3f);
                } else if (dialogues[i].contains("matarme") || dialogues[i].contains("olvidado")) {
                    player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_ROAR, 0.5f, 0.3f);
                    player.playSound(player.getLocation(), Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 0.4f, 0.5f);
                } else if (dialogues[i].contains("Lyra") || dialogues[i].contains("Errante")) {
                    player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_NEARBY_CLOSER, 0.4f, 0.5f);
                } else if (!dialogues[i].contains("§o*")) {
                    player.playSound(player.getLocation(), Sound.BLOCK_SCULK_SENSOR_CLICKING, 0.3f, 0.3f + (float)(Math.random() * 0.3));
                }

                // Particle effects
                if (villager != null && !villager.isDead()) {
                    org.bukkit.Location vLoc = villager.getLocation().add(0, 1.5, 0);
                    player.getWorld().spawnParticle(Particle.DUST, vLoc, 5, 0.3, 0.5, 0.3, 0,
                            new Particle.DustOptions(Color.fromRGB(30, 0, 0), 2.0f));
                    player.getWorld().spawnParticle(Particle.SCULK_SOUL, vLoc, 2, 0.3, 0.3, 0.3, 0.02);
                }

                i++;
            }
        }.runTaskTimer(plugin, 40L, 55L); // Slower pacing for horror effect
    }

    // ==================== MISIÓN 6: DIÁLOGO LYRA ====================

    private void sendTypewriterDialogueQuest6() {
        String[] dialogues = {
                "§b§l♡ Lyra §7aparece lentamente... translúcida, apenas visible.",
                "§7§o*No es el Errante. Es una chica joven, hecha de luz y lágrimas.*",
                "§b§l♡ Lyra §7susurra con voz quebrada:",
                "§b\"Tú... tú conociste a mi padre, ¿verdad?\"",
                "§b§l♡ Lyra:",
                "§b\"El Errante... así le llaman ahora.\"",
                "§b\"Pero para mí siempre fue solo... papá.\"",
                "§b§l♡ Lyra §7baja la mirada:",
                "§b\"Él se fue un día. Dijo que volvería pronto.\"",
                "§b\"Pronto se convirtió en semanas. Meses. Años.\"",
                "§b§l♡ Lyra §7tiembla:",
                "§b\"La oscuridad empezó a hablarme.\"",
                "§8\"Nadie te quiere. Nadie va a volver.\"",
                "§b\"Y yo... les creí.\"",
                "§b§l♡ Lyra §7con lágrimas:",
                "§b\"Una noche caminé hasta el borde del mundo.\"",
                "§b\"Miré hacia abajo. El vacío me miraba.\"",
                "§b\"Y me dejé caer.\"",
                "§7§o*Un silencio profundo invade el aire...*",
                "§7§o*Lyra te mira con ojos llenos de dolor.*",
                "§b§l♡ Lyra:",
                "§b\"No puedo descansar. La culpa me ata a este mundo.\"",
                "§b\"La culpa de mi padre... y la mía propia.\"",
                "§b§l♡ Lyra §7extiende su mano:",
                "§b\"Por favor... ayúdame a recordar quién fui.\"",
                "§b\"Camina por mis recuerdos. Consuela a las almas perdidas.\"",
                "§b\"Y destruye la culpa que me consume.\"",
                "§b§l♡ Lyra:",
                "§b\"Solo entonces podré... descansar.\"",
                "§7§o*Una lágrima cae al suelo y se convierte en una flor de luz.*"
        };

        final int[] step = {0};

        dialogueTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!inDialogue || step[0] >= dialogues.length) {
                    cancel();
                    if (inDialogue) showDialogueOptions();
                    return;
                }

                player.sendMessage(SmallCaps.convert(dialogues[step[0]]));

                // Effects per step
                if (step[0] <= 6) {
                    player.playSound(player.getLocation(), Sound.ENTITY_ALLAY_AMBIENT_WITH_ITEM, 0.5f, 0.6f);
                } else if (step[0] <= 18) {
                    player.playSound(player.getLocation(), Sound.ENTITY_ALLAY_DEATH, 0.5f, 0.4f + (step[0] * 0.02f));
                } else {
                    player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.4f, 0.5f);
                }

                // Soul particles around Lyra
                if (villager != null && !villager.isDead()) {
                    World w = villager.getWorld();
                    w.spawnParticle(Particle.SOUL, villager.getLocation().add(0, 1.5, 0),
                            5, 0.3, 0.5, 0.3, 0.02);
                    w.spawnParticle(Particle.DRIPPING_WATER, villager.getLocation().add(0, 2, 0),
                            3, 0.2, 0.1, 0.2, 0);
                }

                step[0]++;
            }
        }.runTaskTimer(plugin, 20, 50); // 1s delay, 2.5s between each line
    }

    // ==================== MISIÓN 5: DIÁLOGO EL ECO ====================

    private void sendTypewriterDialogueQuest5() {
        String[][] dialogues = {
                { "§3§l◈ El Eco §7aparece lentamente frente a ti.",
                        "§7§o*No tiene cuerpo real. Solo una silueta borrosa de alguien que fue.*" },
                { "§3§l◈ El Eco:", "§f\"No te asustes. No soy él.\"" },
                { "§3§l◈ El Eco:",
                        "§f\"Solo soy lo que queda cuando alguien se va con cosas pendientes.\"" },
                { "§3§l◈ El Eco §7hace una pausa:", "§f\"El Errante desapareció. Pero dejó algo sin entregar.\"" },
                { "§3§l◈ El Eco:", "§f\"Un arco que fabricó con sus propias manos, antes de todo esto.\"" },
                { "§3§l◈ El Eco:",
                        "§f\"Lo guardó en unas ruinas que flotan sobre las nubes.\"" },
                { "§3§l◈ El Eco §7te mira fijamente:", "§7§o*Una pausa larga.*" },
                { "§3§l◈ El Eco:", "§f\"Me pidió que lo entregara a quien llegara hasta el final.\"" },
                { "§3§l◈ El Eco:", "§f\"Eso... eres tú.\"" },
                { "§3§l◈ El Eco:",
                        "§f\"Para llegar tendrás que superar las pruebas del jardín del cielo.\"" },
                { "§3§l◈ El Eco:", "§f\"No es una prueba de fuerza. Es una prueba de fe.\"" },
                { "§3§l◈ El Eco §7susurra:", "§f\"¿Quieres ir?\"" },
        };

        dialogueTask = new BukkitRunnable() {
            int step = 0;
            int charIndex = 0;
            String currentLine = "";
            boolean sendingLine = false;

            @Override
            public void run() {
                if (!player.isOnline() || villager == null || villager.isDead()) {
                    cancel();
                    return;
                }

                if (step >= dialogues.length) {
                    cancel();
                    showDialogueOptions();
                    return;
                }

                if (!sendingLine) {
                    player.sendMessage("");
                    player.sendMessage(dialogues[step][0]);
                    currentLine = dialogues[step][1];
                    charIndex = 0;
                    sendingLine = true;
                    player.playSound(player.getLocation(),
                            Sound.ENTITY_ALLAY_AMBIENT_WITH_ITEM, 0.2f,
                            1.3f + (float) (Math.random() * 0.3));
                }

                charIndex += 2;
                if (charIndex >= currentLine.length()) {
                    player.sendMessage(currentLine);
                    if (villager != null) {
                        villager.getWorld().spawnParticle(Particle.END_ROD,
                                villager.getLocation().add(0, 2, 0),
                                3, 0.2, 0.3, 0.2, 0.02);
                    }
                    sendingLine = false;
                    step++;
                } else {
                    if (charIndex % 5 == 0) {
                        player.playSound(player.getLocation(),
                                Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.1f,
                                1.5f + (float) Math.random() * 0.4f);
                    }
                }
            }
        }.runTaskTimer(plugin, 30L, 2L);
    }

    /**
     * Diálogo alternativo para Misión 2: El Despertar del Abismo.
     */
    private void sendTypewriterDialogueQuest2() {
        String[][] dialogues = {
                { "§4§l✦ El Errante §7murmura:", "§f\"Nos volvemos a encontrar, Cazador...\"" },
                { "§4§l✦ El Errante:", "§f\"La Hoja del Vacío que portas... tiembla.\"" },
                { "§4§l✦ El Errante:", "§f\"Algo monstruoso despierta bajo la tierra. El Abismo.\"" },
                { "§4§l✦ El Errante:", "§f\"Fragmentos de su poder se esparcen por la zona.\"" },
                { "§4§l✦ El Errante:", "§f\"Necesito a alguien que los reúna... y enfrente al Titán.\"" },
                { "§4§l✦ El Errante:", "§c\"Será más peligroso que antes. Mucho más.\"" },
                { "§4§l✦ El Errante:",
                        "§e\"[¡IMPORTANTE!] Asegúrate de tener las partículas activadas, es crucial.\"" },
                { "§4§l✦ El Errante:", "§f\"\u00bfAceptas el desafío del Abismo?\"" },
        };

        dialogueTask = new BukkitRunnable() {
            int step = 0;
            boolean sendingLine = false;

            @Override
            public void run() {
                if (!player.isOnline() || villager == null || villager.isDead()) {
                    cancel();
                    return;
                }
                if (step >= dialogues.length) {
                    cancel();
                    showDialogueOptions();
                    return;
                }
                if (!sendingLine) {
                    player.sendMessage("");
                    player.sendMessage(dialogues[step][0]);
                    sendingLine = true;
                    player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f,
                            0.5f + (float) (Math.random() * 0.3));
                    return;
                }
                player.sendMessage(dialogues[step][1]);
                if (villager != null) {
                    villager.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME,
                            villager.getLocation().add(0, 2.2, 0), 5, 0.2, 0.1, 0.2, 0.02);
                }
                sendingLine = false;
                step++;
            }
        }.runTaskTimer(plugin, 20, 50);
    }

    public boolean isInDialogue() {
        return inDialogue;
    }

    public boolean isDialogueOptionsShown() {
        return dialogueOptionsShown;
    }

    /**
     * Cancela el diálogo typewriter activo sin mostrar opciones.
     * Usado cuando el jugador acepta/rechaza antes de que termine.
     */
    public void cancelDialogue() {
        if (dialogueTask != null) {
            dialogueTask.cancel();
            dialogueTask = null;
        }
        inDialogue = false;
    }

    /**
     * Salta el diálogo typewriter y muestra las opciones directamente.
     */
    public void skipDialogue() {
        if (dialogueTask != null) {
            dialogueTask.cancel();
            dialogueTask = null;
        }
        if (inDialogue) {
            showDialogueOptions();
        }
    }

    // ==================== NPC COMENTARIOS DURANTE MISIÓN ====================

    public void say(String message) {
        if (!player.isOnline())
            return;
        player.sendMessage(SmallCaps.convert("§5§l✦ El Errante: §7" + message));
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.4f, 1.0f);
    }

    public void sayDelayed(String message, long delayTicks) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> say(message), delayTicks);
    }

    /**
     * Mueve el NPC a una posición y lo deja estacionario.
     */
    public void moveToAndStay(Location loc) {
        if (followTask != null)
            followTask.cancel();
        stationaryLoc = loc.clone();

        if (villager != null && !villager.isDead()) {
            Location target = loc.clone();
            target.setDirection(player.getLocation().toVector().subtract(target.toVector()));
            villager.teleport(target);
        }
    }

    /**
     * Hace que el NPC mire al jugador periódicamente.
     */
    public void lookAtPlayer() {
        if (villager != null && !villager.isDead() && player.isOnline()) {
            Location loc = villager.getLocation();
            loc.setDirection(player.getLocation().toVector().subtract(loc.toVector()));
            villager.teleport(loc);
        }
    }

    // ==================== DESPAWN ====================

    public void despawn() {
        if (dialogueTask != null) {
            dialogueTask.cancel();
            dialogueTask = null;
        }
        if (followTask != null)
            followTask.cancel();
        if (particleTask != null)
            particleTask.cancel();
        if (nameTag != null && !nameTag.isDead())
            nameTag.remove();
        if (villager != null && !villager.isDead())
            villager.remove();
        inDialogue = false;
        villager = null;
        nameTag = null;
    }

    public void despawnWithEffect() {
        if (villager != null && !villager.isDead()) {
            Location loc = villager.getLocation();
            World world = loc.getWorld();

            // Efecto de desaparición épico
            world.spawnParticle(Particle.PORTAL, loc.add(0, 1, 0), 100, 0.5, 1, 0.5, 1);
            world.spawnParticle(Particle.SMOKE, loc, 30, 0.5, 1, 0.5, 0.1);
            world.spawnParticle(Particle.SOUL, loc, 20, 0.5, 1, 0.5, 0.05);
            world.playSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.5f);
            world.playSound(loc, Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 0.8f);

            // Mensaje final
            if (player.isOnline()) {
                player.sendMessage("");
                player.sendMessage(SmallCaps.convert("§5§l✦ El Errante §7sonríe: §f\"Has demostrado tu valía. Nos volveremos a ver...\""));
                player.sendMessage("");
            }
        }
        despawn();
    }

    private BukkitTask despawnTask;

    private void startDespawnTimer() {
        // Cancelar timer anterior si existe
        if (despawnTask != null) {
            despawnTask.cancel();
        }

        // Iniciar nuevo timer de 1 minuto
        despawnTask = new BukkitRunnable() {
            @Override
            public void run() {
                // Si el jugador no ha respondido, despawnear con cooldown
                if (!isInDialogue() && villager != null && !villager.isDead()) {
                    despawnWithCooldown();
                }
            }
        }.runTaskLater(plugin, 20L * 60); // 1 minuto
    }

    public void despawnWithCooldown() {
        if (villager == null)
            return;

        Location loc = villager.getLocation();
        World world = loc.getWorld();

        // Explosión de despawn
        world.spawnParticle(Particle.EXPLOSION, loc, 30, 1, 1, 1, 0.3);
        world.spawnParticle(Particle.SMOKE, loc, 50, 2, 2, 2, 0.1);
        world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.2f);

        // Mensaje al jugador
        if (player.isOnline()) {
            player.sendMessage("");
            player.sendMessage(SmallCaps.convert("§5§l✦ El Errante §7desaparece entre sombras..."));
            player.sendMessage(SmallCaps.convert("§7\"§fVolveré más tarde... cuando estés más preparado.§7\""));
            player.sendMessage(SmallCaps.convert("§c§lCooldown de 6 horas aplicado."));
            player.sendMessage("");
        }

        // Aplicar cooldown de 6 horas
        manager.addCooldown(player.getUniqueId(), 6 * 60 * 60 * 1000L);

        despawn();
    }

    public void cancelDespawnTimer() {
        if (despawnTask != null) {
            despawnTask.cancel();
            despawnTask = null;
        }
        if (postDialogueDespawnTask != null) {
            postDialogueDespawnTask.cancel();
            postDialogueDespawnTask = null;
        }
    }

    private BukkitTask postDialogueDespawnTask;

    private void startPostDialogueDespawnTimer() {
        // Cancel any existing post-dialogue timer
        if (postDialogueDespawnTask != null) {
            postDialogueDespawnTask.cancel();
        }

        // 2 minutes hard timer — if player doesn't respond, despawn WITHOUT cooldown penalty
        postDialogueDespawnTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (villager == null || villager.isDead()) return;

                plugin.getLogger().info("[ShadowHunter] Post-dialogue 2min timeout for " + player.getName() + " — despawning Errante");

                if (player.isOnline()) {
                    player.sendMessage("");
                    player.sendMessage(SmallCaps.convert("§5§l✦ §7El Errante se desvanece lentamente..."));
                    player.sendMessage(SmallCaps.convert("§7\"§fNo te preocupes... volveré pronto.§7\""));
                    player.sendMessage("");
                }

                // Despawn with effect but NO cooldown (so the NPC comes back naturally)
                if (villager != null && !villager.isDead()) {
                    Location loc = villager.getLocation();
                    World w = loc.getWorld();
                    if (w != null) {
                        w.spawnParticle(Particle.PORTAL, loc.clone().add(0, 1, 0), 60, 0.5, 1, 0.5, 0.5);
                        w.spawnParticle(Particle.SMOKE, loc.clone().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.05);
                        w.playSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 0.6f);
                    }
                }
                despawn();
                // Reset state so NPC can re-spawn normally
                manager.resetNPC(player.getUniqueId());
            }
        }.runTaskLater(plugin, 20L * 120); // 2 minutes = 2400 ticks
    }

    // ==================== GETTERS ====================

    public Villager getVillager() {
        return villager;
    }

    public Player getPlayer() {
        return player;
    }

    public boolean isAlive() {
        return villager != null && !villager.isDead();
    }

    /**
     * Asegura que el villager del NPC esté vivo y cerca de una ubicación.
     * Si el villager murió o fue eliminado, lo re-spawnea.
     * Útil para el boss de Quest 4 donde el NPC debe estar presente para el diálogo post-boss.
     */
    public void ensureAlive(Location location) {
        if (villager != null && !villager.isDead()) {
            // Ya está vivo, solo teleportar cerca
            if (villager.getLocation().distanceSquared(location) > 100) { // >10 bloques
                villager.teleport(location);
            }
            return;
        }

        // Villager muerto o nulo: re-spawnear
        World world = location.getWorld();
        if (world == null) return;

        plugin.getLogger().info("[ShadowHunter] Re-spawneando Errante NPC para " + player.getName() + " (villager perdido)");

        // Efecto de reaparición
        world.spawnParticle(Particle.SOUL, location.clone().add(0, 1, 0), 30, 0.5, 1, 0.5, 0.03);
        world.spawnParticle(Particle.PORTAL, location, 40, 0.5, 1, 0.5, 0.5);
        world.playSound(location, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 0.5f);

        villager = (Villager) world.spawnEntity(location, EntityType.VILLAGER);
        villager.setProfession(Villager.Profession.NITWIT);
        villager.setCustomName(SmallCaps.convert(isQuest10 ? "§5§l✦ El Errante §7§o[Última Vez]" : isQuest9 ? "§e§l☆ El Centinela" : isQuest8 ? "§b§l❄ El Heraldo" : isQuest7 ? "§4§l☠ La Sombra Sin Nombre" : isQuest6 ? "§b§l♡ Lyra" : isQuest5 ? "§3§l◈ El Eco" : "§5§l✦ El Errante"));
        villager.setCustomNameVisible(true);
        villager.setAI(false);
        villager.setInvulnerable(true);
        villager.setSilent(true);
        villager.setCollidable(false);
        villager.setRemoveWhenFarAway(false);
        villager.setPersistent(true);
        villager.addScoreboardTag("errante_npc");
        villager.addScoreboardTag("wardstone_mission_mob");
        villager.addPotionEffect(new org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.WATER_BREATHING, 99999, 0, false, false));
        equipNPC();

        // Mirar al jugador
        if (player.isOnline()) {
            Location vLoc = villager.getLocation();
            Vector toPlayer = player.getLocation().toVector().subtract(vLoc.toVector()).normalize();
            vLoc.setDirection(toPlayer);
            villager.setRotation(vLoc.getYaw(), vLoc.getPitch());
        }
    }
}
