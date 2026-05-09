package com.moonlight.coreprotect.minigames.games;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.minigames.*;
import com.moonlight.coreprotect.util.ActionBarUtil;
import me.libraryaddict.disguise.DisguiseAPI;
import me.libraryaddict.disguise.disguisetypes.DisguiseType;
import me.libraryaddict.disguise.disguisetypes.MobDisguise;
import org.bukkit.*;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.*;

/**
 * Caza en la Granja (Farm Hunt) — Usa LibsDisguises para disfrazar jugadores.
 * Los jugadores-animal SE CONVIERTEN en el mob a nivel de paquetes: primera persona,
 * movimiento natural, sin colisión, sin lag, sin nametag.
 * Los cazadores ven animales y deben adivinar cuáles son jugadores reales.
 */
public class FarmHuntGame extends MiniGame {

    private final Set<UUID> hunters = new LinkedHashSet<>();
    private final Set<UUID> animals = new LinkedHashSet<>();
    private final Map<UUID, DisguiseType> disguiseTypes = new HashMap<>();
    private final Map<UUID, Integer> hunterAttempts = new HashMap<>();
    private final Map<UUID, Long> lastClickTime = new HashMap<>();
    private final Set<Entity> decoyAnimals = new HashSet<>();

    private static final int MAX_ATTEMPTS = 4;
    private static final int GAME_DURATION = 180;
    private static final int HUNTER_RELEASE_DELAY = 10;
    private boolean huntersReleased = false;

    private BossBar timerBar;
    private Scoreboard scoreboard;
    private Team hunterTeam;
    private Team animalTeam;

    // Tipos de animales disponibles para disfraz (LibsDisguises DisguiseType)
    private static final DisguiseType[] FARM_DISGUISES = {
        DisguiseType.SHEEP, DisguiseType.COW, DisguiseType.PIG, DisguiseType.CHICKEN,
        DisguiseType.HORSE, DisguiseType.DONKEY, DisguiseType.RABBIT, DisguiseType.GOAT,
        DisguiseType.CAT, DisguiseType.WOLF
    };

    // Tipos de animales decorativos (señuelos reales con AI)
    private static final EntityType[] DECOY_TYPES = {
        EntityType.SHEEP, EntityType.COW, EntityType.PIG, EntityType.CHICKEN,
        EntityType.SHEEP, EntityType.COW, EntityType.PIG, EntityType.CHICKEN,
        EntityType.HORSE, EntityType.DONKEY, EntityType.RABBIT, EntityType.GOAT
    };

    private static final Random RANDOM = new Random();

    public FarmHuntGame(CoreProtectPlugin plugin, MiniGameManager manager) {
        super(plugin, manager, MiniGameType.FARM_HUNT);
    }

    @Override
    public void buildArena(World world) {
        ArenaBuilder.buildFarmHunt(world);
    }

    @Override
    public List<Location> getSpawnLocations(World world) {
        return ArenaBuilder.getFarmHuntSpawns(world);
    }

    @Override
    public void startGameLogic() {
        // Verificar que LibsDisguises está disponible
        if (!Bukkit.getPluginManager().isPluginEnabled("LibsDisguises")) {
            broadcastGame("§c§l✖ Error: LibsDisguises no está instalado. No se puede iniciar Farm Hunt.");
            endGame(null);
            return;
        }

        List<UUID> playerList = new ArrayList<>(alivePlayers);
        Collections.shuffle(playerList);

        int numHunters = Math.max(1, playerList.size() / 4);

        for (int i = 0; i < playerList.size(); i++) {
            if (i < numHunters) {
                hunters.add(playerList.get(i));
            } else {
                animals.add(playerList.get(i));
            }
        }

        World w = Bukkit.getWorld(MiniGameWorld.getWorldName());
        if (w == null) return;

        // Scoreboard: ocultar nametags
        scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        hunterTeam = scoreboard.registerNewTeam("fh_hunters");
        hunterTeam.setColor(ChatColor.RED);
        hunterTeam.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);

        animalTeam = scoreboard.registerNewTeam("fh_animals");
        animalTeam.setColor(ChatColor.GREEN);
        animalTeam.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        animalTeam.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);

        hunterTeam.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);

        // Configurar cazadores
        for (UUID uuid : hunters) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) setupHunter(p, true);
        }

        // Configurar animales (disfraz con LibsDisguises)
        for (UUID uuid : animals) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) setupAnimal(p);
        }

        // Setear scoreboard a todos y ocultar de tab list
        for (UUID uuid : alivePlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.setScoreboard(scoreboard);
                // Ocultar nombre en tab list (lista de jugadores)
                p.setPlayerListName("");
            }
        }

        // Señuelos reales con AI para llenar la granja
        spawnDecoyAnimals(w);

        // BossBar
        timerBar = Bukkit.createBossBar("§6§l\uD83C\uDF3E CAZA EN LA GRANJA §8| §e§lTIEMPO", BarColor.YELLOW, BarStyle.SOLID);
        timerBar.setVisible(true);
        for (UUID uuid : alivePlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) timerBar.addPlayer(p);
        }

        broadcastGame("");
        broadcastGame("§6§l\uD83C\uDF3E ══════════════════════════ \uD83C\uDF3E");
        broadcastGame("§e§l      CAZA EN LA GRANJA");
        broadcastGame("§7  Cazadores: §c" + hunters.size() + " §8| §7Animales: §a" + animals.size());
        broadcastGame("§7  Los cazadores tienen §c4 intentos §7para encontrar");
        broadcastGame("§7  a los jugadores disfrazados entre los animales.");
        broadcastGame("§a  ¡Los animales tienen " + HUNTER_RELEASE_DELAY + "s de ventaja!");
        broadcastGame("§6§l\uD83C\uDF3E ══════════════════════════ \uD83C\uDF3E");
        broadcastGame("");
    }

    // ===================================
    // SETUP ROLES
    // ===================================

    private void setupHunter(Player p, boolean isInitial) {
        // Quitar disfraz de LibsDisguises si tenía
        if (DisguiseAPI.isDisguised(p)) {
            DisguiseAPI.undisguiseToAll(p);
        }

        p.getInventory().clear();
        p.sendTitle("§c§l\uD83D\uDD0D CAZADOR", "§7¡Encuentra a los impostores!", 10, 70, 20);
        p.sendMessage("§c§l\uD83D\uDD0D CAZADOR §7- Haz click derecho en los animales para identificar impostores.");
        p.sendMessage("§7Tienes §e" + MAX_ATTEMPTS + " intentos§7. ¡No los desperdicies!");

        ItemStack wand = new ItemStack(Material.CARROT_ON_A_STICK);
        ItemMeta meta = wand.getItemMeta();
        meta.setDisplayName("§c§l\uD83D\uDD0D Detector de Impostores");
        meta.setLore(Arrays.asList(
            "§7Click derecho en un animal",
            "§7para ver si es un jugador.",
            "§eIntentos restantes: §c" + MAX_ATTEMPTS
        ));
        wand.setItemMeta(meta);
        p.getInventory().setItem(0, wand);

        hunterAttempts.put(p.getUniqueId(), MAX_ATTEMPTS);
        hunterTeam.addEntry(p.getName());

        // Limpiar efectos previos
        p.removePotionEffect(PotionEffectType.INVISIBILITY);
        p.removePotionEffect(PotionEffectType.SLOWNESS);
        p.removePotionEffect(PotionEffectType.SPEED);
        p.removePotionEffect(PotionEffectType.BLINDNESS);

        if (isInitial) {
            // Al inicio: congelados Y ciegos durante 10 segundos
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, HUNTER_RELEASE_DELAY * 20 + 20, 255, false, false, true));
            p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, HUNTER_RELEASE_DELAY * 20 + 20, 1, false, false, true));
        } else {
            // Convertido: speed inmediata
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 999999, 0, false, false, true));
        }
    }

    private void setupAnimal(Player p) {
        p.getInventory().clear();
        p.getInventory().setArmorContents(new ItemStack[4]);
        p.getInventory().setItemInOffHand(null);

        // Elegir animal aleatorio
        DisguiseType dType = FARM_DISGUISES[RANDOM.nextInt(FARM_DISGUISES.length)];
        disguiseTypes.put(p.getUniqueId(), dType);

        String animalName = getDisguiseDisplayName(dType);
        String emoji = getDisguiseEmoji(dType);
        p.sendTitle("§a§l" + emoji + " " + animalName.toUpperCase(), "§7¡Actúa como un animal!", 10, 70, 20);
        p.sendMessage("§a§l" + emoji + " ERES UN " + animalName.toUpperCase() + " §7- ¡Mézclate con los demás animales!");
        p.sendMessage("§7Muévete como un animal real. Pulsa §eshift §7para hacer sonido.");
        p.sendMessage("§e§l⚠ IMPORTANTE: §7Tú te ves como humano, pero §c§llos cazadores te ven como " + animalName.toUpperCase() + "!");
        p.sendMessage("§e§l⚠ §7No te asustes si te ves normal - §aes parte del juego§7. ¡Ellos te ven como animal!");

        // Ocultar nametag
        animalTeam.addEntry(p.getName());

        // DISFRAZ CON LIBSDISGUISES — aplicar con delay para evitar problemas de timing
        // Delay de 5 ticks (0.25s) asegura que el jugador esté completamente cargado
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline() || !running) return;
            
            MobDisguise disguise = new MobDisguise(dType);
            disguise.setViewSelfDisguise(false);     // NO verse a sí mismo - evita colisión con el disfraz
            disguise.setReplaceSounds(true);         // Sonidos del animal al caminar/daño
            disguise.setHearSelfDisguise(true);      // Escuchar sus propios sonidos
            disguise.getWatcher().setCustomNameVisible(false); // Sin nombre flotante
            disguise.setModifyBoundingBox(false);    // No modificar hitbox
            DisguiseAPI.disguiseToAll(p, disguise);
            
            // Recordatorio adicional después de aplicar disfraz
            p.sendMessage("§e§l⚠ RECUERDA: §7Los cazadores te ven como un §a" + animalName + "§7, aunque tú te veas normal.");
        }, 5L);
        
        // Recordatorio periódico durante el juego (cada 30 segundos)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline() || !running || !animals.contains(p.getUniqueId())) return;
            p.sendMessage("§e⚠ §7Recordatorio: Los cazadores te ven como §a" + animalName + "§7 - ¡Actúa como tal!");
        }, 600L); // 30 segundos
    }

    private void spawnDecoyAnimals(World w) {
        int numDecoys = Math.max(30, alivePlayers.size() * 5);

        for (int i = 0; i < numDecoys; i++) {
            EntityType type = DECOY_TYPES[RANDOM.nextInt(DECOY_TYPES.length)];
            int x = RANDOM.nextInt(60) - 30;
            int z = RANDOM.nextInt(60) - 30;
            int y = 81;

            for (int dy = 0; dy < 10; dy++) {
                if (w.getBlockAt(x, y + dy, z).getType() == Material.AIR
                    && w.getBlockAt(x, y + dy - 1, z).getType().isSolid()) {
                    y = y + dy;
                    break;
                }
            }

            Location loc = new Location(w, x + 0.5, y, z + 0.5);
            Entity decoy = w.spawnEntity(loc, type);
            decoy.setInvulnerable(true);
            if (decoy instanceof LivingEntity living) {
                living.setRemoveWhenFarAway(false);
            }
            if (decoy instanceof Ageable ageable) {
                ageable.setAdult();
            }
            decoyAnimals.add(decoy);
        }
    }

    // ===================================
    // GAME TICK (cada 1 segundo)
    // ===================================

    @Override
    public void onTick() {
        if (!running) return;

        // Liberar cazadores después del delay
        if (!huntersReleased && gameTime >= HUNTER_RELEASE_DELAY) {
            huntersReleased = true;
            for (UUID uuid : hunters) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null && p.isOnline()) {
                    p.removePotionEffect(PotionEffectType.SLOWNESS);
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 999999, 0, false, false, true));
                    p.sendTitle("§c§l¡A CAZAR!", "§7¡Encuentra a los impostores!", 5, 40, 10);
                    p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.5f);
                }
            }
            broadcastGame("§c§l\uD83D\uDD0D ¡Los cazadores han sido liberados!");
        }

        // Action bars
        for (UUID uuid : alivePlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline()) continue;

            if (hunters.contains(uuid)) {
                int att = hunterAttempts.getOrDefault(uuid, 0);
                ActionBarUtil.send(p, "§c§l\uD83D\uDD0D CAZADOR §8| §eIntentos: §c" + att + "/" + MAX_ATTEMPTS +
                    " §8| §7Animales restantes: §a" + animals.size());
            } else if (animals.contains(uuid)) {
                DisguiseType dt = disguiseTypes.getOrDefault(uuid, DisguiseType.SHEEP);
                ActionBarUtil.send(p, "§a§l" + getDisguiseEmoji(dt) + " " + getDisguiseDisplayName(dt).toUpperCase() +
                    " §8| §7Cazadores: §c" + hunters.size() + " §8| §7¡Mézclate!");
            }
        }

        // Timer
        int remaining = GAME_DURATION - gameTime;
        if (timerBar != null) {
            double progress = Math.max(0.0, Math.min(1.0, (double) remaining / GAME_DURATION));
            timerBar.setProgress(progress);
            String timeStr = String.format("%02d:%02d", remaining / 60, remaining % 60);
            timerBar.setTitle("§6§l\uD83C\uDF3E CAZA EN LA GRANJA §8| §e" + timeStr +
                " §8| §cCazadores: " + hunters.size() + " §8| §aAnimales: " + animals.size());
            if (remaining <= 30) timerBar.setColor(BarColor.RED);
            else if (remaining <= 60) timerBar.setColor(BarColor.PINK);
        }

        if (remaining == 60 || remaining == 30 || remaining == 10) {
            broadcastGame("§e§l⏳ §fTiempo restante: §c" + remaining + "s");
            if (remaining <= 10) soundAll(Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
        }

        if (remaining <= 0) {
            broadcastGame("§a§l\uD83C\uDF3E ¡SE ACABÓ EL TIEMPO! §fLos animales sobrevivieron y ganan.");
            animalsWin();
            return;
        }

        if (animals.isEmpty()) {
            broadcastGame("§c§l\uD83D\uDD0D ¡TODOS LOS IMPOSTORES HAN SIDO ENCONTRADOS! §fLos cazadores ganan.");
            huntersWin();
            return;
        }

        boolean anyHunterHasAttempts = false;
        for (UUID uuid : hunters) {
            if (hunterAttempts.getOrDefault(uuid, 0) > 0) {
                anyHunterHasAttempts = true;
                break;
            }
        }
        if (!anyHunterHasAttempts) {
            broadcastGame("§a§l\uD83C\uDF3E ¡Los cazadores se quedaron sin intentos! §fLos animales ganan.");
            animalsWin();
        }
    }

    // ===================================
    // HUNTER GUESS LOGIC
    // ===================================

    /**
     * Llamado cuando un cazador hace click derecho en una entidad.
     * Con LibsDisguises: si el target es un Player disfrazado → ACIERTO.
     * Si es un mob real (señuelo) → FALLO.
     */
    public void onHunterGuess(Player hunter, Entity target) {
        if (!running || !hunters.contains(hunter.getUniqueId())) return;
        if (!huntersReleased) {
            hunter.sendMessage("§c§l✖ §c¡Espera a que termine la cuenta atrás!");
            return;
        }

        // Prevenir doble-click (evento se dispara dos veces por mano principal/secundaria)
        UUID hunterUUID = hunter.getUniqueId();
        long now = System.currentTimeMillis();
        long lastClick = lastClickTime.getOrDefault(hunterUUID, 0L);
        if (now - lastClick < 500) {
            return; // Ignorar clicks dentro de 500ms
        }
        lastClickTime.put(hunterUUID, now);

        int attempts = hunterAttempts.getOrDefault(hunterUUID, 0);
        if (attempts <= 0) {
            hunter.sendMessage("§c§l✖ §cNo te quedan intentos.");
            return;
        }

        // Con LibsDisguises el jugador disfrazado sigue siendo un Player
        if (target instanceof Player targetPlayer) {
            UUID targetUUID = targetPlayer.getUniqueId();

            // Verificar si es un animal disfrazado
            if (animals.contains(targetUUID)) {
                onPlayerCaught(hunter, targetPlayer, targetUUID);
                return;
            }
            // Si es otro cazador, ignorar
            if (hunters.contains(targetUUID)) {
                hunter.sendMessage("§7Ese es otro cazador.");
                return;
            }
        }

        // FALLO — es un mob real (señuelo)
        attempts--;
        hunterAttempts.put(hunter.getUniqueId(), attempts);

        hunter.sendMessage("§c§l✖ §7¡Era un animal real! Intentos restantes: §e" + attempts);
        hunter.playSound(hunter.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);

        World w = target.getWorld();
        w.spawnParticle(Particle.SMOKE, target.getLocation().add(0, 1, 0), 10, 0.3, 0.3, 0.3, 0.02);

        updateDetectorLore(hunter, attempts);

        if (attempts <= 0) {
            hunter.sendMessage("§c§l✖ §c¡Te has quedado sin intentos! Ahora solo puedes observar.");
            hunter.sendTitle("§c§lSIN INTENTOS", "§7No puedes adivinar más", 5, 40, 10);
        }
    }

    private void onPlayerCaught(Player hunter, Player caught, UUID caughtUUID) {
        if (caught == null || !caught.isOnline()) return;

        // Quitar disfraz de LibsDisguises
        if (DisguiseAPI.isDisguised(caught)) {
            DisguiseAPI.undisguiseToAll(caught);
        }

        animals.remove(caughtUUID);
        animalTeam.removeEntry(caught.getName());

        DisguiseType dt = disguiseTypes.getOrDefault(caughtUUID, DisguiseType.SHEEP);
        String animalName = getDisguiseDisplayName(dt);

        broadcastGame("§c§l\uD83D\uDD0D ¡" + hunter.getName() + " §fha descubierto a §e" + caught.getName() +
            " §f(" + getDisguiseEmoji(dt) + " " + animalName + ")!");
        soundAll(Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);

        World w = caught.getWorld();
        w.spawnParticle(Particle.EXPLOSION, caught.getLocation().add(0, 1, 0), 5, 0.5, 0.5, 0.5, 0.1);
        caught.playSound(caught.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.7f, 1.5f);

        // Convertir en cazador
        hunters.add(caughtUUID);
        setupHunter(caught, false);

        caught.sendTitle("§c§l¡TE PILLARON!", "§7Ahora eres CAZADOR", 10, 50, 10);
        caught.sendMessage("§c§l\uD83D\uDD0D §7¡Te han descubierto! Ahora eres cazador. ¡Encuentra a los demás!");

        hunter.sendMessage("§a§l✔ §a¡Correcto! §e" + caught.getName() + " §7era un " + animalName + ".");
        hunter.playSound(hunter.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);

        if (animals.isEmpty()) {
            broadcastGame("§c§l\uD83D\uDD0D ¡TODOS LOS IMPOSTORES HAN SIDO ENCONTRADOS! §fLos cazadores ganan.");
            huntersWin();
        }
    }

    private void updateDetectorLore(Player hunter, int attempts) {
        ItemStack item = hunter.getInventory().getItem(0);
        if (item != null && item.getType() == Material.CARROT_ON_A_STICK) {
            ItemMeta meta = item.getItemMeta();
            meta.setLore(Arrays.asList(
                "§7Click derecho en un animal",
                "§7para ver si es un jugador.",
                "§eIntentos restantes: §c" + attempts
            ));
            item.setItemMeta(meta);
        }
    }

    // ===================================
    // ANIMAL SOUND (shift)
    // ===================================

    public void onAnimalSound(Player player) {
        if (!animals.contains(player.getUniqueId())) return;
        DisguiseType dt = disguiseTypes.getOrDefault(player.getUniqueId(), DisguiseType.SHEEP);
        Sound sound = getDisguiseSound(dt);
        if (sound != null) {
            player.getWorld().playSound(player.getLocation(), sound, 1.0f, 0.8f + RANDOM.nextFloat() * 0.4f);
        }
    }

    // ===================================
    // WIN CONDITIONS
    // ===================================

    private void animalsWin() {
        List<UUID> winners = new ArrayList<>(animals);
        endGame(null);
        giveDelayedRewards(winners, 140L);
    }

    private void huntersWin() {
        if (hunters.size() == 1) {
            UUID hunterUUID = hunters.iterator().next();
            Player winner = Bukkit.getPlayer(hunterUUID);
            endGame(winner);
        } else {
            endGame(null);
            List<UUID> hunterList = new ArrayList<>(hunters);
            giveDelayedRewards(hunterList, 140L);
        }
    }

    // ===================================
    // ELIMINATE / QUIT
    // ===================================

    @Override
    public void eliminatePlayer(UUID uuid) {
        if (animals.contains(uuid)) {
            Player p = Bukkit.getPlayer(uuid);
            // if (p != null && DisguiseAPI.isDisguised(p)) {
            //     DisguiseAPI.undisguiseToAll(p);
            // }
            animals.remove(uuid);
        }
        hunters.remove(uuid);
        super.eliminatePlayer(uuid);
    }

    public void eliminatePlayerSilent(UUID uuid) {
        Player p = Bukkit.getPlayer(uuid);
        // if (p != null && DisguiseAPI.isDisguised(p)) {
        //     DisguiseAPI.undisguiseToAll(p);
        // }
        animals.remove(uuid);
        hunters.remove(uuid);
    }

    @Override
    public void checkWinCondition() {
        // Custom win logic in onTick
    }

    // ===================================
    // CLEANUP
    // ===================================

    @Override
    public void onCleanup() {
        // Quitar todos los disfraces de LibsDisguises
        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                // if (DisguiseAPI.isDisguised(p)) {
                //     DisguiseAPI.undisguiseToAll(p);
                // }
                p.removePotionEffect(PotionEffectType.BLINDNESS);
                p.removePotionEffect(PotionEffectType.INVISIBILITY);
                p.removePotionEffect(PotionEffectType.SLOWNESS);
                p.removePotionEffect(PotionEffectType.SPEED);
                p.setGlowing(false);
                p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
                // Restaurar nombre en tab list
                p.setPlayerListName(p.getName());
            }
        }

        // Limpiar animales decorativos
        for (Entity decoy : decoyAnimals) {
            if (decoy != null && !decoy.isDead()) decoy.remove();
        }
        decoyAnimals.clear();

        if (timerBar != null) {
            timerBar.removeAll();
            timerBar.setVisible(false);
        }

        if (hunterTeam != null) {
            try { hunterTeam.unregister(); } catch (Exception ignored) {}
        }
        if (animalTeam != null) {
            try { animalTeam.unregister(); } catch (Exception ignored) {}
        }

        hunters.clear();
        animals.clear();
        hunterAttempts.clear();
        disguiseTypes.clear();
        lastClickTime.clear();
    }

    // ===================================
    // API PÚBLICA
    // ===================================

    public boolean isHunter(UUID uuid) { return hunters.contains(uuid); }
    public boolean isAnimal(UUID uuid) { return animals.contains(uuid); }
    public Set<Entity> getDecoyAnimals() { return decoyAnimals; }

    // ===================================
    // UTILIDADES — DisguiseType helpers
    // ===================================

    private String getDisguiseDisplayName(DisguiseType type) {
        return switch (type) {
            case SHEEP -> "Oveja";
            case COW -> "Vaca";
            case PIG -> "Cerdo";
            case CHICKEN -> "Pollo";
            case HORSE -> "Caballo";
            case DONKEY -> "Burro";
            case RABBIT -> "Conejo";
            case GOAT -> "Cabra";
            case CAT -> "Gato";
            case WOLF -> "Lobo";
            default -> "Animal";
        };
    }

    private String getDisguiseEmoji(DisguiseType type) {
        return switch (type) {
            case SHEEP -> "\uD83D\uDC11";
            case COW -> "\uD83D\uDC04";
            case PIG -> "\uD83D\uDC37";
            case CHICKEN -> "\uD83D\uDC14";
            case HORSE -> "\uD83D\uDC34";
            case DONKEY -> "\uD83E\uDECF";
            case RABBIT -> "\uD83D\uDC07";
            case GOAT -> "\uD83D\uDC10";
            case CAT -> "\uD83D\uDC31";
            case WOLF -> "\uD83D\uDC3A";
            default -> "\uD83D\uDC3E";
        };
    }

    private Sound getDisguiseSound(DisguiseType type) {
        return switch (type) {
            case SHEEP -> Sound.ENTITY_SHEEP_AMBIENT;
            case COW -> Sound.ENTITY_COW_AMBIENT;
            case PIG -> Sound.ENTITY_PIG_AMBIENT;
            case CHICKEN -> Sound.ENTITY_CHICKEN_AMBIENT;
            case HORSE -> Sound.ENTITY_HORSE_AMBIENT;
            case DONKEY -> Sound.ENTITY_DONKEY_AMBIENT;
            case RABBIT -> Sound.ENTITY_RABBIT_AMBIENT;
            case GOAT -> Sound.ENTITY_GOAT_SCREAMING_AMBIENT;
            case CAT -> Sound.ENTITY_CAT_AMBIENT;
            case WOLF -> Sound.ENTITY_WOLF_AMBIENT;
            default -> null;
        };
    }
}
