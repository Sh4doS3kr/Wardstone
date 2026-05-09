package com.moonlight.coreprotect.minigames.games;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.minigames.*;
import com.moonlight.coreprotect.util.ActionBarUtil;
import org.bukkit.*;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * PILARES DE LA FORTUNA (Pillars of Fortune):
 * Cada jugador aparece en su propio pilar flotante alto en el vacío.
 * Cada cierto tiempo, TODOS reciben items aleatorios (armas, bloques, armadura, pociones, etc.).
 * Los jugadores deben usar los items para:
 *   - Construir puentes hacia otros pilares
 *   - Atacar a otros jugadores
 *   - Defenderse
 * Si caes al vacío → ELIMINADO.
 * Último en pie gana.
 *
 * Progresión:
 * - Ronda 1 (0-30s): Items básicos cada 10s (bloques, comida, espada de madera)
 * - Ronda 2 (30-60s): Items mejores cada 8s (arcos, armadura de hierro, pociones)
 * - Ronda 3 (60-120s): Items épicos cada 7s (diamante, TNT, perlas de ender)
 * - Ronda 4 (120s+): Items caóticos cada 5s (netherite, spawn eggs, totems)
 *
 * Eventos especiales:
 * - LLUVIA DE BLOQUES: Todos reciben stacks de bloques
 * - EQUIPAMIENTO COMPLETO: Set de armadura aleatorio para todos
 * - POCIONES LOCAS: Todos reciben pociones aleatorias (buenas y malas)
 * - BONANZA: Items legendarios para todos
 */
public class PillarsOfFortuneGame extends MiniGame {

    private static final int PILLAR_Y = 100;
    private static final int PILLAR_HEIGHT = 20;
    private static final int PILLAR_SPACING = 20;
    private static final int VOID_KILL_Y = 50;

    // Tormenta: empieza a los 180s, radio inicial = 40, se cierra en 180s hasta 2
    private static final int STORM_START = 180;
    private static final double STORM_INITIAL_RADIUS = 40.0;
    private static final double STORM_MIN_RADIUS = 2.0;
    private static final int STORM_SHRINK_DURATION = 180; // segundos para cerrar completamente
    private static final double STORM_DAMAGE = 4.0; // corazones por tick fuera de la zona

    private BossBar statusBar;
    private final Random random = new Random();
    private int lootRound = 0;
    private int nextLootAt = 3; // primera entrega a los 3s
    private boolean gameStarted = false;
    private boolean stormActive = false;
    private double stormRadius = STORM_INITIAL_RADIUS;
    private double lastPlacedRadius = -1; // radio de la última pared de cristal colocada
    private int stormParticleTaskId = -1;

    // Pillar locations per player (UUID -> pillar center top)
    private final Map<UUID, Location> pillarLocations = new LinkedHashMap<>();

    public PillarsOfFortuneGame(CoreProtectPlugin plugin, MiniGameManager manager) {
        super(plugin, manager, MiniGameType.PILLARS_OF_FORTUNE);
    }

    // ═══════════════════════════════════════════
    //  ARENA: Pilares individuales en el vacío
    // ═══════════════════════════════════════════

    @Override
    public void buildArena(World world) {
        // Sin suelo — vacío puro debajo de los pilares. Caer = muerte.
    }

    private void buildPillar(World world, int cx, int cz, Material baseMat) {
        int baseY = PILLAR_Y;
        // Pilar de 1 bloque de ancho
        for (int y = baseY; y < baseY + PILLAR_HEIGHT; y++) {
            world.getBlockAt(cx, y, cz).setType(baseMat);
        }
        // Bloque superior donde el jugador se para
        world.getBlockAt(cx, baseY + PILLAR_HEIGHT, cz).setType(baseMat);
    }

    @Override
    public List<Location> getSpawnLocations(World world) {
        List<Location> spawns = new ArrayList<>();
        int maxPlayers = 15;

        // Materiales variados para los pilares (1 material por pilar)
        Material[] pillarMaterials = {
            Material.STONE_BRICKS, Material.DEEPSLATE_BRICKS, Material.SANDSTONE,
            Material.PRISMARINE_BRICKS, Material.PURPUR_BLOCK, Material.RED_NETHER_BRICKS,
            Material.QUARTZ_BLOCK, Material.MOSSY_STONE_BRICKS, Material.BRICKS,
            Material.POLISHED_BLACKSTONE, Material.COPPER_BLOCK, Material.DIORITE,
            Material.ANDESITE, Material.POLISHED_GRANITE, Material.BLACKSTONE
        };

        // Distribuir pilares en círculo
        double radius = maxPlayers <= 8 ? 18.0 : 22.0;
        for (int i = 0; i < maxPlayers; i++) {
            double angle = (2 * Math.PI / maxPlayers) * i;
            int cx = (int) (radius * Math.cos(angle));
            int cz = (int) (radius * Math.sin(angle));

            buildPillar(world, cx, cz, pillarMaterials[i % pillarMaterials.length]);

            Location spawn = new Location(world, cx + 0.5, PILLAR_Y + PILLAR_HEIGHT + 1, cz + 0.5);
            spawn.setYaw((float) Math.toDegrees(-angle + Math.PI)); // Mirar hacia el centro
            spawns.add(spawn);
        }

        return spawns;
    }

    // ═══════════════════════════════════════════
    //  PRE-COUNTDOWN: Congelar jugadores
    // ═══════════════════════════════════════════

    @Override
    protected void onPreCountdown() {
        // Asociar cada jugador con su pilar
        List<UUID> playerList = new ArrayList<>(players);
        World world = Bukkit.getWorld(MiniGameWorld.getWorldName());
        if (world == null) return;

        List<Location> spawns = getSpawnLocations(world);
        for (int i = 0; i < playerList.size(); i++) {
            pillarLocations.put(playerList.get(i), spawns.get(i % spawns.size()));
        }
    }

    // ═══════════════════════════════════════════
    //  GAME START
    // ═══════════════════════════════════════════

    @Override
    public void startGameLogic() {
        gameStarted = true;

        statusBar = Bukkit.createBossBar("§5§l✦ PILARES DE LA FORTUNA §5§l✦", BarColor.PURPLE, BarStyle.SEGMENTED_10);
        statusBar.setProgress(1.0);
        statusBar.setVisible(true);
        for (UUID uuid : alivePlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) statusBar.addPlayer(p);
        }

        broadcastGame("");
        broadcastGame("§5§l✦ §d§lPILARES DE LA FORTUNA §5§l✦");
        broadcastGame("§7Recibirás §eitems aleatorios §7cada pocos segundos.");
        broadcastGame("§7Usa bloques para §aconstruir puentes §7hacia otros pilares.");
        broadcastGame("§7Usa armas para §celiminar §7a tus rivales.");
        broadcastGame("§7¡Si caes al vacío, estás §c§lFUERA§7!");
        broadcastGame("");

        // Inventario vacío al inicio — todo depende de la suerte
        for (UUID uuid : alivePlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) continue;
            p.getInventory().clear();
        }
    }

    // ═══════════════════════════════════════════
    //  TICK (cada segundo)
    // ═══════════════════════════════════════════

    @Override
    public void onTick() {
        if (!gameStarted) return;

        // Verificar caídas al vacío
        for (UUID uuid : new ArrayList<>(alivePlayers)) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline()) continue;
            if (p.getLocation().getY() < VOID_KILL_Y) {
                eliminatePlayer(uuid);
            }
        }

        // === TORMENTA ===
        if (gameTime == STORM_START) {
            stormActive = true;
            stormRadius = STORM_INITIAL_RADIUS;
            broadcastGame("");
            broadcastGame("§4§l⚠ §c§lTORMENTA DE SANGRE §4§l⚠");
            broadcastGame("§7Una tormenta mortal se acerca. §c¡Corre hacia el centro!");
            broadcastGame("");
            titleAlive("§4§lTORMENTA", "§c¡Sal de la zona roja o morirás!");
            soundAll(Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 0.5f);
            if (statusBar != null) {
                statusBar.setColor(BarColor.RED);
            }
            startStormBorder();
        }

        if (stormActive) {
            // Reducir radio gradualmente
            int stormTime = gameTime - STORM_START;
            double shrinkFraction = Math.min(1.0, (double) stormTime / STORM_SHRINK_DURATION);
            stormRadius = STORM_INITIAL_RADIUS - (STORM_INITIAL_RADIUS - STORM_MIN_RADIUS) * shrinkFraction;

            // Dañar jugadores fuera de la zona
            for (UUID uuid : new ArrayList<>(alivePlayers)) {
                Player p = Bukkit.getPlayer(uuid);
                if (p == null || !p.isOnline()) continue;
                double dist = Math.sqrt(p.getLocation().getX() * p.getLocation().getX()
                        + p.getLocation().getZ() * p.getLocation().getZ());
                if (dist > stormRadius) {
                    p.damage(STORM_DAMAGE);
                    p.playSound(p.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 0.5f, 0.5f);
                    if (p.getHealth() <= 0) {
                        eliminatePlayer(uuid);
                    }
                }
            }
        }

        // Actualizar bossbar
        if (statusBar != null) {
            int phase = getCurrentPhase();
            String phaseName = getPhaseNameColored(phase);
            if (stormActive) {
                double progress = Math.max(0, (stormRadius - STORM_MIN_RADIUS) / (STORM_INITIAL_RADIUS - STORM_MIN_RADIUS));
                statusBar.setProgress(progress);
                statusBar.setTitle("§4§l⚠ TORMENTA §8| §fRadio: §c" + String.format("%.0f", stormRadius) + " §8| §fVivos: §a" + alivePlayers.size());
            } else {
                int timeToStorm = STORM_START - gameTime;
                statusBar.setProgress(Math.max(0, (double) timeToStorm / STORM_START));
                statusBar.setTitle("§5§l✦ " + phaseName + " §8| §fVivos: §a" + alivePlayers.size() + " §8| §fTormenta en §c" + formatTime(timeToStorm));
            }
        }

        // Entrega de 1 item aleatorio
        if (gameTime >= nextLootAt) {
            lootRound++;
            deliverLoot();
            nextLootAt = gameTime + getLootInterval();
        }

        // Info periódica
        if (gameTime % 30 == 0 && gameTime > 0 && !stormActive) {
            broadcastGame("§5§l✦ §fTiempo: §e" + formatTime(gameTime) + " §7| §fVivos: §a" + alivePlayers.size() + " §7| §fRonda: §d" + lootRound);
        }

        // ActionBar
        int phase2 = getCurrentPhase();
        for (UUID uuid : alivePlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                int nextIn = nextLootAt - gameTime;
                if (stormActive) {
                    double dist = Math.sqrt(p.getLocation().getX() * p.getLocation().getX()
                            + p.getLocation().getZ() * p.getLocation().getZ());
                    String safeColor = dist <= stormRadius ? "§a" : "§c";
                    ActionBarUtil.send(p, "§4§l⚠ §cRadio: §f" + String.format("%.0f", stormRadius)
                            + " §8| " + safeColor + "Tu dist: " + String.format("%.0f", dist)
                            + " §8| §eItems en §f" + nextIn + "s");
                } else {
                    ActionBarUtil.send(p, "§5§l✦ §7Fase " + phase2 + " §8| §eItems en §f" + nextIn + "s §8| §aVivos: §f" + alivePlayers.size());
                }
            }
        }
    }

    // ═══════════════════════════════════════════
    //  LOOT DELIVERY
    // ═══════════════════════════════════════════

    private int getCurrentPhase() {
        if (gameTime < 30) return 1;
        if (gameTime < 60) return 2;
        if (gameTime < 120) return 3;
        return 4;
    }

    private String getPhaseNameColored(int phase) {
        return switch (phase) {
            case 1 -> "§aFase 1: Inicio";
            case 2 -> "§eFase 2: Escalada";
            case 3 -> "§6Fase 3: Épica";
            case 4 -> "§cFase 4: Caos";
            default -> "§5Pilares de la Fortuna";
        };
    }

    private int getLootInterval() {
        int phase = getCurrentPhase();
        return switch (phase) {
            case 1 -> 8;
            case 2 -> 6;
            case 3 -> 5;
            default -> 4;
        };
    }

    // Items que NO deben aparecer nunca en el loot
    private static final Set<Material> ITEM_BLACKLIST = Set.of(
        Material.BEDROCK, Material.COMMAND_BLOCK, Material.CHAIN_COMMAND_BLOCK,
        Material.REPEATING_COMMAND_BLOCK, Material.STRUCTURE_BLOCK, Material.BARRIER,
        Material.END_PORTAL_FRAME, Material.RESPAWN_ANCHOR, Material.OBSIDIAN,
        Material.SPAWNER, Material.DRAGON_EGG, Material.NETHER_STAR,
        Material.ELYTRA, Material.FIREWORK_ROCKET, Material.NETHERITE_BLOCK,
        Material.BEACON, Material.ENCHANTING_TABLE, Material.ANVIL,
        Material.ENDER_CHEST, Material.SHULKER_BOX, Material.EXPERIENCE_BOTTLE
    );

    private void deliverLoot() {
        soundAll(Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.5f);

        for (UUID uuid : alivePlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline()) continue;

            // 1 item aleatorio por ronda (clásico)
            ItemStack item = getRandomItem();
            HashMap<Integer, ItemStack> overflow = p.getInventory().addItem(item);
            for (ItemStack drop : overflow.values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), drop);
            }

            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        }
    }

    private ItemStack getRandomItem() {
        ItemStack[] pool = {
            // Bloques (alta probabilidad — aparecen mucho)
            new ItemStack(Material.OAK_PLANKS, 1),
            new ItemStack(Material.OAK_PLANKS, 1),
            new ItemStack(Material.OAK_PLANKS, 1),
            new ItemStack(Material.COBBLESTONE, 1),
            new ItemStack(Material.COBBLESTONE, 1),
            new ItemStack(Material.COBBLESTONE, 1),
            new ItemStack(Material.DIRT, 1),
            new ItemStack(Material.DIRT, 1),
            new ItemStack(Material.STONE, 1),
            new ItemStack(Material.STONE, 1),
            new ItemStack(Material.SANDSTONE, 1),
            new ItemStack(Material.NETHERRACK, 1),
            new ItemStack(Material.END_STONE, 1),
            new ItemStack(Material.BRICKS, 1),
            new ItemStack(Material.BIRCH_PLANKS, 1),
            new ItemStack(Material.SPRUCE_PLANKS, 1),
            new ItemStack(Material.DARK_OAK_PLANKS, 1),
            new ItemStack(Material.GLASS, 1),
            new ItemStack(Material.TERRACOTTA, 1),
            new ItemStack(Material.SMOOTH_STONE, 1),
            new ItemStack(Material.DEEPSLATE, 1),
            new ItemStack(Material.MOSSY_COBBLESTONE, 1),
            // Armas
            new ItemStack(Material.WOODEN_SWORD, 1),
            new ItemStack(Material.WOODEN_SWORD, 1),
            new ItemStack(Material.STONE_SWORD, 1),
            new ItemStack(Material.STONE_SWORD, 1),
            new ItemStack(Material.IRON_SWORD, 1),
            new ItemStack(Material.DIAMOND_SWORD, 1),
            new ItemStack(Material.WOODEN_AXE, 1),
            new ItemStack(Material.STONE_AXE, 1),
            new ItemStack(Material.IRON_AXE, 1),
            new ItemStack(Material.BOW, 1),
            new ItemStack(Material.BOW, 1),
            new ItemStack(Material.CROSSBOW, 1),
            new ItemStack(Material.ARROW, 1),
            new ItemStack(Material.ARROW, 1),
            new ItemStack(Material.ARROW, 1),
            new ItemStack(Material.TRIDENT, 1),
            new ItemStack(Material.FISHING_ROD, 1),
            // Armadura (rara)
            new ItemStack(Material.LEATHER_BOOTS, 1),
            new ItemStack(Material.IRON_HELMET, 1),
            new ItemStack(Material.IRON_BOOTS, 1),
            // Comida
            new ItemStack(Material.COOKED_BEEF, 1),
            new ItemStack(Material.COOKED_BEEF, 1),
            new ItemStack(Material.COOKED_PORKCHOP, 1),
            new ItemStack(Material.BREAD, 1),
            new ItemStack(Material.GOLDEN_APPLE, 1),
            // Herramientas / utilidad
            new ItemStack(Material.SHIELD, 1),
            new ItemStack(Material.ENDER_PEARL, 1),
            new ItemStack(Material.TNT, 1),
            new ItemStack(Material.TNT, 1),
            new ItemStack(Material.FLINT_AND_STEEL, 1),
            new ItemStack(Material.SNOWBALL, 1),
            new ItemStack(Material.SNOWBALL, 1),
            new ItemStack(Material.EGG, 1),
            new ItemStack(Material.EGG, 1),
            new ItemStack(Material.LAVA_BUCKET, 1),
            new ItemStack(Material.WATER_BUCKET, 1),
            new ItemStack(Material.COBWEB, 1),
            new ItemStack(Material.LADDER, 1),
            new ItemStack(Material.SCAFFOLDING, 1),
            // Pociones
            makePotion(Material.SPLASH_POTION, PotionEffectType.INSTANT_DAMAGE, 0, 1),
            makePotion(Material.POTION, PotionEffectType.SPEED, 0, 400),
            makePotion(Material.POTION, PotionEffectType.STRENGTH, 0, 400),
            makePotion(Material.POTION, PotionEffectType.REGENERATION, 0, 200),
            makePotion(Material.SPLASH_POTION, PotionEffectType.POISON, 0, 100),
            makePotion(Material.SPLASH_POTION, PotionEffectType.SLOWNESS, 0, 200),
            makePotion(Material.POTION, PotionEffectType.FIRE_RESISTANCE, 0, 400),
            makePotion(Material.POTION, PotionEffectType.JUMP_BOOST, 1, 400),
            // Raros
            new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 1),
            new ItemStack(Material.TOTEM_OF_UNDYING, 1),
            new ItemStack(Material.NETHERITE_SWORD, 1)
        };
        ItemStack result;
        do {
            result = pool[random.nextInt(pool.length)].clone();
        } while (ITEM_BLACKLIST.contains(result.getType()));
        return result;
    }

    // ═══════════════════════════════════════════
    //  TORMENTA: WorldBorder visual (zero-lag, red wall nativo del cliente)
    // ═══════════════════════════════════════════

    private void startStormBorder() {
        World world = Bukkit.getWorld(MiniGameWorld.getWorldName());
        if (world == null) return;

        // Usar el WorldBorder del mundo para mostrar la zona segura
        org.bukkit.WorldBorder border = world.getWorldBorder();
        border.setCenter(0, 0);
        border.setSize(STORM_INITIAL_RADIUS * 2); // diámetro
        border.setWarningDistance(5);
        border.setWarningTime(0);
        border.setDamageAmount(0); // Daño lo manejamos nosotros (circular)
        border.setDamageBuffer(0);

        // Iniciar el shrink suave del border
        border.setSize(STORM_MIN_RADIUS * 2, STORM_SHRINK_DURATION);

        // Task para sincronizar stormRadius con el border actual (para damage circular)
        stormParticleTaskId = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!gameStarted || !stormActive) return;
            // Sincronizar el radio visual con nuestro cálculo
            World w = Bukkit.getWorld(MiniGameWorld.getWorldName());
            if (w != null) {
                // Actualizar radius basado en el border actual
                double currentDiameter = w.getWorldBorder().getSize();
                stormRadius = currentDiameter / 2.0;
            }
        }, 20L, 20L).getTaskId();
    }

    private void resetStormBorder() {
        World world = Bukkit.getWorld(MiniGameWorld.getWorldName());
        if (world == null) return;
        org.bukkit.WorldBorder border = world.getWorldBorder();
        border.reset();
    }

    // ═══════════════════════════════════════════
    //  CLEANUP
    // ═══════════════════════════════════════════

    @Override
    public void onCleanup() {
        gameStarted = false;
        stormActive = false;
        if (stormParticleTaskId != -1) {
            Bukkit.getScheduler().cancelTask(stormParticleTaskId);
            stormParticleTaskId = -1;
        }
        // Resetear el WorldBorder
        resetStormBorder();
        lastPlacedRadius = -1;
        if (statusBar != null) {
            statusBar.removeAll();
            statusBar.setVisible(false);
            statusBar = null;
        }
        pillarLocations.clear();
    }

    // ═══════════════════════════════════════════
    //  UTILITY METHODS
    // ═══════════════════════════════════════════

    private ItemStack makePotion(Material potionType, PotionEffectType effect, int amplifier, int durationTicks) {
        ItemStack potion = new ItemStack(potionType);
        PotionMeta meta = (PotionMeta) potion.getItemMeta();
        if (meta != null) {
            meta.addCustomEffect(new PotionEffect(effect, durationTicks, amplifier), true);
            String name = getPotionName(effect, amplifier);
            meta.setDisplayName(name);
            potion.setItemMeta(meta);
        }
        return potion;
    }

    private String getPotionName(PotionEffectType effect, int amplifier) {
        String level = amplifier > 0 ? " II" : "";
        if (effect.equals(PotionEffectType.INSTANT_DAMAGE)) return "§c§lPoción de Daño" + level;
        if (effect.equals(PotionEffectType.SLOWNESS)) return "§9Poción de Lentitud" + level;
        if (effect.equals(PotionEffectType.POISON)) return "§2Poción de Veneno" + level;
        if (effect.equals(PotionEffectType.SPEED)) return "§bPoción de Velocidad" + level;
        if (effect.equals(PotionEffectType.STRENGTH)) return "§4Poción de Fuerza" + level;
        if (effect.equals(PotionEffectType.REGENERATION)) return "§dPoción de Regeneración" + level;
        if (effect.equals(PotionEffectType.FIRE_RESISTANCE)) return "§6Poción de Resistencia al Fuego";
        if (effect.equals(PotionEffectType.WEAKNESS)) return "§8Poción de Debilidad";
        if (effect.equals(PotionEffectType.INVISIBILITY)) return "§7Poción de Invisibilidad";
        return "§5Poción Misteriosa";
    }

    private void titleAlive(String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        for (UUID uuid : alivePlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.sendTitle(title, subtitle, fadeIn, stay, fadeOut);
            }
        }
    }

    private String formatTime(int seconds) {
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    /**
     * Permite al listener saber si este juego permite PvP, block place/break, drops, pickups.
     */
    public boolean isPvpGame() { return true; }
    public boolean allowsBlockPlace() { return true; }
    public boolean allowsBlockBreak() { return true; }
    public boolean allowsDrops() { return true; }
    public boolean allowsPickups() { return true; }
    public boolean allowsInventory() { return true; }
    public boolean allowsHunger() { return true; }
}
