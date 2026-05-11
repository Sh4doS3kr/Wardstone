package com.moonlight.coreprotect.minigames;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.minigames.games.*;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Listener principal para todos los eventos de minijuegos.
 * Maneja interacciones específicas de cada minijuego.
 */
public class MiniGameListener implements Listener {

    private final CoreProtectPlugin plugin;

    public MiniGameListener(CoreProtectPlugin plugin) {
        this.plugin = plugin;
    }

    private MiniGameManager getManager() {
        return plugin.getMiniGameManager();
    }

    private boolean isInMinigame(Player player) {
        MiniGameManager mgr = getManager();
        if (mgr == null || !mgr.isGameActive() || mgr.getCurrentGame() == null) return false;
        return mgr.getCurrentGame().getPlayers().contains(player.getUniqueId());
    }

    private boolean isInMinigameWorld(Player player) {
        return player.getWorld().getName().equals(MiniGameWorld.getWorldName());
    }

    // ==========================================
    // PVP BLOCKER FINAL - HIGHEST priority, runs after ALL other plugins
    // Garantiza que ningún otro listener puede reactivar el PVP en minijuegos
    // ==========================================
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerHitFinal(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        if (!isInMinigameWorld((Player) event.getEntity())) return;

        // Solo actuar si el daño viene de un jugador o proyectil de jugador
        Player shooter = null;
        if (event.getDamager() instanceof Player) {
            shooter = (Player) event.getDamager();
        } else if (event.getDamager() instanceof Projectile proj) {
            if (proj.getShooter() instanceof Player p) shooter = p;
        }
        if (shooter == null) return; // Daño de entorno/FallingBlock: no tocar

        MiniGameManager mgr = getManager();
        if (mgr == null || !mgr.isGameActive() || mgr.getCurrentGame() == null) {
            // No hay juego activo pero están en el mundo: bloquear igualmente
            event.setCancelled(true);
            return;
        }

        MiniGame game = mgr.getCurrentGame();

        // SUMO, OITC, PILARES permiten daño jugador→jugador siempre
        if (game instanceof SumoGame || game instanceof OITCGame || game instanceof PillarsOfFortuneGame) {
            event.setCancelled(false); // Forzar que NO esté cancelado
            return;
        }
        // EYE OF STORM: permitir PvP solo si NO hay gracia
        if (game instanceof EyeOfStormGame eyeGame) {
            if (!eyeGame.isGraceActive()) {
                event.setCancelled(false);
            }
            return;
        }
        // SKYWARS: permitir PvP solo si NO hay gracia
        if (game instanceof SkyWarsGame skyGame) {
            if (!skyGame.isGraceActive()) {
                event.setCancelled(false);
            }
            return;
        }

        // Murder Mystery y Beast Escape tienen su propia lógica (gestionada en HIGH)
        // No interferir: dejar que el handler HIGH decida
        if (game instanceof MurderMysteryGame || game instanceof BeastEscapeGame) return;

        // Todo lo demás: CANCELAR sin excepción
        event.setCancelled(true);
    }

    // ==========================================
    // PILARES PVP GARANTÍA — MONITOR priority (último de todos)
    // Fuerza que el PvP funcione en Pilares sin importar qué otro plugin lo cancele
    // ==========================================
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onPillarsPvpGuarantee(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (!isInMinigameWorld(victim)) return;

        MiniGameManager mgr = getManager();
        if (mgr == null || !mgr.isGameActive() || mgr.getCurrentGame() == null) return;
        if (!(mgr.getCurrentGame() instanceof PillarsOfFortuneGame)
                && !(mgr.getCurrentGame() instanceof BlackHoleGame)
                && !(mgr.getCurrentGame() instanceof FakeDeathmatchGame)
                && !(mgr.getCurrentGame() instanceof EyeOfStormGame)
                && !(mgr.getCurrentGame() instanceof SkyWarsGame)) return;

        // EYE OF STORM: respetar gracia (no forzar PvP si hay gracia)
        if (mgr.getCurrentGame() instanceof EyeOfStormGame eyeGame && eyeGame.isGraceActive()) return;
        // SKYWARS: respetar gracia
        if (mgr.getCurrentGame() instanceof SkyWarsGame skyGame && skyGame.isGraceActive()) return;

        // Detectar si el daño viene de un jugador (directo o proyectil)
        Player attacker = null;
        if (event.getDamager() instanceof Player p) {
            attacker = p;
        } else if (event.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p) {
            attacker = p;
        }
        if (attacker == null) return;

        // FORZAR que el evento NO esté cancelado
        if (event.isCancelled()) {
            event.setCancelled(false);
            plugin.getLogger().warning("[PvP Guarantee] Forzando PvP: " + attacker.getName() + " -> " + victim.getName()
                + " (damager: " + event.getDamager().getType() + ", wasCancelled: true)");
        }
    }

    // ==========================================
    // PVP HANDLER - Lógica específica por juego (HIGH priority)
    // ==========================================
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) return;

        MiniGameManager mgr = getManager();
        if (mgr == null || !mgr.isGameActive() || mgr.getCurrentGame() == null) return;

        Player victim = (Player) event.getEntity();
        if (!isInMinigame(victim)) return;

        MiniGame game = mgr.getCurrentGame();

        // ---- Daño jugador → jugador ----
        if (event.getDamager() instanceof Player) {
            Player damager = (Player) event.getDamager();
            if (!isInMinigame(damager)) return;

            // TNT TAG: pasar bomba, sin daño real
            if (game instanceof TNTTagGame) {
                TNTTagGame tntTag = (TNTTagGame) game;
                if (damager.getUniqueId().equals(tntTag.getBombHolder())) {
                    tntTag.passBomb(damager.getUniqueId(), victim.getUniqueId());
                }
                event.setCancelled(true);
                return;
            }

            // OITC: lógica de espada (única fuente de PvP melee permitida)
            if (game instanceof OITCGame) {
                OITCGame oitc = (OITCGame) game;
                if (oitc.isInvincible(victim.getUniqueId())) {
                    event.setCancelled(true);
                    return;
                }
                if (victim.getHealth() - event.getFinalDamage() <= 0) {
                    event.setCancelled(true);
                    oitc.onKill(damager.getUniqueId(), victim.getUniqueId());
                }
                return; // daño permitido
            }

            // SUMO: knockback sin daño de vida
            if (game instanceof SumoGame) {
                event.setDamage(0);
                return;
            }

            // MURDER MYSTERY: PvP completo entre Asesino ↔ Sheriff
            if (game instanceof MurderMysteryGame) {
                MurderMysteryGame mm = (MurderMysteryGame) game;
                UUID damagerUUID = damager.getUniqueId();
                UUID victimUUID = victim.getUniqueId();

                // Asesino → cualquiera = insta-kill
                if (mm.isMurderer(damagerUUID)) {
                    event.setDamage(1000.0);
                    return;
                }

                // Sheriff → Asesino = insta-kill
                if (mm.isSheriff(damagerUUID) && mm.isMurderer(victimUUID)) {
                    event.setDamage(1000.0);
                    return;
                }

                // Sheriff → Inocente = sheriff muere (penalización)
                if (mm.isSheriff(damagerUUID) && mm.isInnocent(victimUUID)) {
                    event.setDamage(1000.0);
                    damager.sendMessage("§c§l✖ §cHas matado a un inocente. ¡El remordimiento te consume!");
                    Bukkit.getScheduler().runTask(plugin, () -> damager.setHealth(0));
                    return;
                }

                // Inocente → Asesino = daño normal (pueden pegarle)
                if (mm.isInnocent(damagerUUID) && mm.isMurderer(victimUUID)) {
                    return; // daño normal
                }

                // Inocente → Inocente/Sheriff = cancelar
                event.setCancelled(true);
                return;
            }

            // BEAST ESCAPE: bestia mata de un golpe, supervivientes pueden pegar a la bestia
            if (game instanceof BeastEscapeGame) {
                BeastEscapeGame beast = (BeastEscapeGame) game;
                if (beast.isBeast(damager.getUniqueId()) && beast.isSurvivor(victim.getUniqueId())) {
                    event.setDamage(1000.0); // bestia mata de un golpe
                } else if (beast.isSurvivor(damager.getUniqueId()) && beast.isBeast(victim.getUniqueId())) {
                    return; // supervivientes pueden pegar a la bestia (daño normal)
                } else {
                    event.setCancelled(true); // superviviente → superviviente = cancelar
                }
                return;
            }

            // SNOWBALL FIGHT: solo bolas de nieve hacen daño (melee cancelado)
            if (game instanceof SnowballFightGame) {
                event.setCancelled(true);
                return;
            }

            // PILARES DE LA FORTUNA: PvP completo permitido
            if (game instanceof PillarsOfFortuneGame) {
                return; // daño normal
            }

            // BLACK HOLE: PvP completo permitido
            if (game instanceof BlackHoleGame) {
                return; // daño normal
            }

            // FAKE DEATHMATCH: PvP completo permitido
            if (game instanceof FakeDeathmatchGame) {
                return; // daño normal
            }

            // EYE OF STORM: PvP permitido SOLO si no hay gracia
            if (game instanceof EyeOfStormGame eyeGame) {
                if (eyeGame.isGraceActive()) {
                    event.setCancelled(true);
                    if (event.getDamager() instanceof Player attacker) {
                        attacker.sendMessage("§e§l⏳ §7PvP desactivado durante el tiempo de gracia. ¡Lootea cofres!");
                    }
                    return;
                }
                return; // daño normal
            }

            // SKYWARS: PvP permitido SOLO si no hay gracia
            if (game instanceof SkyWarsGame skyGame) {
                if (skyGame.isGraceActive()) {
                    event.setCancelled(true);
                    if (event.getDamager() instanceof Player attacker) {
                        attacker.sendMessage("§e§l⏳ §7PvP desactivado durante la gracia. ¡Lootea tu isla!");
                    }
                    return;
                }
                return; // daño normal
            }

            // TODOS LOS DEMÁS: sin PvP
            event.setCancelled(true);
            return;
        }

        // ---- Daño proyectil → jugador (flechas, tridentes, bolas de nieve, etc.) ----
        if (event.getDamager() instanceof Projectile projectile) {
            if (!(projectile.getShooter() instanceof Player shooter)) return;
            if (!isInMinigame(shooter)) return;

            // PILARES DE LA FORTUNA: todo proyectil permitido
            if (game instanceof PillarsOfFortuneGame) {
                return;
            }

            // BLACK HOLE: todo proyectil permitido
            if (game instanceof BlackHoleGame) {
                return;
            }

            // FAKE DEATHMATCH: todo proyectil permitido
            if (game instanceof FakeDeathmatchGame) {
                return;
            }

            // EYE OF STORM: proyectil solo si no hay gracia
            if (game instanceof EyeOfStormGame eyeGame) {
                if (eyeGame.isGraceActive()) {
                    event.setCancelled(true);
                    shooter.sendMessage("§e§l⏳ §7PvP desactivado durante el tiempo de gracia.");
                    return;
                }
                return;
            }

            // SKYWARS: proyectil solo si no hay gracia
            if (game instanceof SkyWarsGame skyGame) {
                if (skyGame.isGraceActive()) {
                    event.setCancelled(true);
                    shooter.sendMessage("§e§l⏳ §7PvP desactivado durante la gracia.");
                    return;
                }
                return;
            }

            if (game instanceof OITCGame) {
                OITCGame oitc = (OITCGame) game;
                event.setCancelled(true);
                if (!oitc.isInvincible(victim.getUniqueId())) {
                    oitc.onKill(shooter.getUniqueId(), victim.getUniqueId());
                }
                return;
            }

            if (game instanceof MurderMysteryGame) {
                MurderMysteryGame mm = (MurderMysteryGame) game;
                UUID shooterUUID = shooter.getUniqueId();
                UUID victimUUID = victim.getUniqueId();

                // Asesino → cualquiera = insta-kill
                if (mm.isMurderer(shooterUUID)) {
                    event.setDamage(1000.0);
                    return;
                }

                // Sheriff/Inocente → Asesino = insta-kill
                if (mm.isMurderer(victimUUID)) {
                    event.setDamage(1000.0);
                    return;
                }

                // Sheriff → Inocente = penalización (sheriff muere)
                if (mm.isSheriff(shooterUUID) && mm.isInnocent(victimUUID)) {
                    event.setDamage(1000.0);
                    shooter.sendMessage("§c§l✖ §cHas matado a un inocente. ¡El remordimiento te consume!");
                    Bukkit.getScheduler().runTask(plugin, () -> shooter.setHealth(0));
                    return;
                }

                // Inocente → Inocente/Sheriff = cancelar
                event.setCancelled(true);
                return;
            }

            // BEAST ESCAPE: supervivientes pueden disparar a la bestia
            if (game instanceof BeastEscapeGame) {
                BeastEscapeGame beast = (BeastEscapeGame) game;
                UUID shooterUUID = shooter.getUniqueId();
                UUID victimUUID = victim.getUniqueId();
                if (beast.isBeast(shooterUUID) && beast.isSurvivor(victimUUID)) {
                    event.setDamage(1000.0);
                    return;
                }
                if (beast.isSurvivor(shooterUUID) && beast.isBeast(victimUUID)) {
                    return; // daño normal
                }
                event.setCancelled(true);
                return;
            }

            // Snowball Fight: solo bolas de nieve hacen daño
            if (game instanceof SnowballFightGame && projectile instanceof Snowball) {
                return;
            }

            // Resto: cancelar proyectiles entre jugadores
            event.setCancelled(true);
        }
    }

    // ==========================================
    // BLOCK BREAK: Solo permitido en Spleef
    // ==========================================
    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!isInMinigameWorld(event.getPlayer())) return;

        MiniGameManager mgr = getManager();
        if (mgr != null && mgr.isGameActive() && mgr.getCurrentGame() != null) {
            MiniGame game = mgr.getCurrentGame();
            // Solo Spleef permite romper bloques (nieve)
            if (game instanceof SpleefGame) {
                if (event.getBlock().getType() == Material.SNOW_BLOCK) {
                    event.setDropItems(false);
                    return;
                }
            }
            // Build Battle: permitir romper solo en tu propio plot durante fase de construcción
            if (game instanceof BuildBattleGame) {
                BuildBattleGame bb = (BuildBattleGame) game;
                if (bb.isBuildingAllowed() && bb.isInsidePlayerPlot(event.getPlayer().getUniqueId(), event.getBlock().getLocation())) {
                    event.setDropItems(false);
                    return;
                }
            }
            // Build Battle Classic: permitir romper en tu plot durante fase de construcción
            if (game instanceof BuildBattleClassicGame) {
                BuildBattleClassicGame bbc = (BuildBattleClassicGame) game;
                if (bbc.isBuildingAllowed() && bbc.isInsidePlayerPlot(event.getPlayer().getUniqueId(), event.getBlock().getLocation())) {
                    event.setDropItems(false);
                    return;
                }
            }
            // Pilares de la Fortuna: permitir romper bloques
            if (game instanceof PillarsOfFortuneGame) {
                event.setDropItems(false);
                return;
            }
            // Black Hole: permitir romper bloques
            if (game instanceof BlackHoleGame) {
                event.setDropItems(false);
                return;
            }
            // Eye of Storm: permitir romper bloques CON drops
            if (game instanceof EyeOfStormGame) {
                return;
            }
            // SkyWars: permitir romper bloques CON drops, pero NO cristal durante gracia
            if (game instanceof SkyWarsGame skyGame) {
                if (skyGame.isGraceActive() && event.getBlock().getType() == Material.GLASS) {
                    event.setCancelled(true);
                    event.getPlayer().sendMessage("§c§l✖ §7No puedes romper la jaula.");
                    return;
                }
                return;
            }
            // Fake Deathmatch: permitir romper bloques
            if (game instanceof FakeDeathmatchGame) {
                event.setDropItems(false);
                return;
            }
        }

        event.setCancelled(true);
    }

    // ==========================================
    // DEATH: Manejar muertes en minijuegos
    // ==========================================
    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (!isInMinigame(player)) return;

        MiniGameManager mgr = getManager();
        MiniGame game = mgr.getCurrentGame();

        // Eye of Storm / SkyWars: dropear items del jugador al morir
        if (game instanceof EyeOfStormGame || game instanceof SkyWarsGame) {
            event.setKeepInventory(false);
            event.setDroppedExp(0);
            event.setKeepLevel(true);
            // Los drops se generan automáticamente por Minecraft
        } else {
            event.setKeepInventory(true);
            event.getDrops().clear();
            event.setDroppedExp(0);
            event.setKeepLevel(true);
        }

        // En Murder Mystery, ocultamos mensaje de muerte en el chat y el sheriff dropea el arco
        if (game instanceof MurderMysteryGame) {
            event.setDeathMessage(null); // Anular mensaje de broadcast vanilla

            MurderMysteryGame mm = (MurderMysteryGame) game;
            if (mm.isSheriff(player.getUniqueId())) {
                mm.spawnDroppedBow(player.getLocation());
            }
        }

        // Auto-respawn rápido y manejar según juego
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            player.spigot().respawn();
            // Forzar que respawnee en el mundo de minijuegos
            org.bukkit.World minigameWorld = Bukkit.getWorld(MiniGameWorld.getWorldName());
            if (minigameWorld != null && !player.getWorld().getName().equals(MiniGameWorld.getWorldName())) {
                player.teleport(new org.bukkit.Location(minigameWorld, 0, 85, 0));
            }

            // En OITC, no eliminar, solo dar kit (respawn gestionado en onKill)
            if (game instanceof OITCGame) {
                return;
            }

            // Default: eliminar (pone spectator + TP al centro del minijuego)
            if (game.getAlivePlayers().contains(player.getUniqueId())) {
                game.eliminatePlayer(player.getUniqueId());
            }
        }, 3L);
    }

    // ==========================================
    // SNOWBALL FIGHT: Bola de nieve impacta jugador
    // ==========================================
    @EventHandler(priority = EventPriority.HIGH)
    public void onSnowballHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        if (!(event.getDamager() instanceof Snowball)) return;

        Snowball snowball = (Snowball) event.getDamager();
        if (!(snowball.getShooter() instanceof Player)) return;

        Player shooter = (Player) snowball.getShooter();
        Player victim = (Player) event.getEntity();
        if (!isInMinigame(shooter) || !isInMinigame(victim)) return;

        MiniGameManager mgr = getManager();
        if (mgr == null || mgr.getCurrentGame() == null) return;

        MiniGame game = mgr.getCurrentGame();
        if (game instanceof SnowballFightGame) {
            event.setCancelled(true); // Cancelar daño vanilla de snowball
            ((SnowballFightGame) game).onSnowballHit(shooter, victim);
        }
    }

    // ==========================================
    // PILARES: Snowball/Egg aplican daño custom (vanilla = 0)
    // ==========================================
    @EventHandler(priority = EventPriority.HIGH)
    public void onProjectileHitPillars(org.bukkit.event.entity.ProjectileHitEvent event) {
        if (!(event.getHitEntity() instanceof Player victim)) return;
        Projectile proj = event.getEntity();
        if (!(proj.getShooter() instanceof Player shooter)) return;
        if (!isInMinigame(shooter) || !isInMinigame(victim)) return;

        MiniGameManager mgr = getManager();
        if (mgr == null || !mgr.isGameActive() || mgr.getCurrentGame() == null) return;
        if (!(mgr.getCurrentGame() instanceof PillarsOfFortuneGame)
                && !(mgr.getCurrentGame() instanceof BlackHoleGame)
                && !(mgr.getCurrentGame() instanceof FakeDeathmatchGame)) return;

        // Snowball y Egg no hacen daño vanilla, aplicar daño custom
        if (proj instanceof Snowball || proj instanceof org.bukkit.entity.Egg) {
            victim.damage(3.0, shooter); // 1.5 corazones
            victim.getWorld().playSound(victim.getLocation(), org.bukkit.Sound.ENTITY_ARROW_HIT_PLAYER, 0.8f, 1.2f);
        }
    }

    // ==========================================
    // PREVENT: Drops, pickups, hunger, build en mundo minijuegos
    // ==========================================
    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (isInMinigame(event.getPlayer())) {
            // Pilares, Black Hole, Fake Deathmatch: permitir dropear items
            MiniGameManager mgr = getManager();
            if (mgr != null && mgr.isGameActive()
                    && (mgr.getCurrentGame() instanceof PillarsOfFortuneGame
                        || mgr.getCurrentGame() instanceof BlackHoleGame
                        || mgr.getCurrentGame() instanceof FakeDeathmatchGame
                        || mgr.getCurrentGame() instanceof EyeOfStormGame
                        || mgr.getCurrentGame() instanceof SkyWarsGame)) {
                return;
            }
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPickup(org.bukkit.event.entity.EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        if (!isInMinigame(player)) return;
        
        MiniGameManager mgr = getManager();
        if (mgr == null || !mgr.isGameActive() || mgr.getCurrentGame() == null) return;
        
        MiniGame game = mgr.getCurrentGame();
        if (game instanceof MurderMysteryGame) {
            MurderMysteryGame mm = (MurderMysteryGame) game;
            org.bukkit.entity.Item item = event.getItem();
            
            if (item.getItemStack().getType() == Material.GOLD_INGOT) {
                event.setCancelled(true);
                mm.pickupGold(player, item);
            } else if (item.getItemStack().getType() == Material.BOW) {
                event.setCancelled(true);
                mm.pickupSheriffBow(player, item);
            } else {
                event.setCancelled(true);
            }
        } else if (game instanceof PillarsOfFortuneGame || game instanceof BlackHoleGame || game instanceof FakeDeathmatchGame || game instanceof EyeOfStormGame || game instanceof SkyWarsGame) {
            // Pilares / Black Hole / Fake Deathmatch / Eye of Storm / SkyWars: permitir recoger items
            return;
        } else {
            // Bloquear recolección en otros minijuegos
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onHunger(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player && isInMinigame((Player) event.getEntity())) {
            // Pilares / Black Hole / Fake Deathmatch: permitir hambre
            MiniGameManager mgr = getManager();
            if (mgr != null && mgr.isGameActive()
                    && (mgr.getCurrentGame() instanceof PillarsOfFortuneGame
                        || mgr.getCurrentGame() instanceof BlackHoleGame
                        || mgr.getCurrentGame() instanceof FakeDeathmatchGame
                        || mgr.getCurrentGame() instanceof EyeOfStormGame
                        || mgr.getCurrentGame() instanceof SkyWarsGame)) {
                return;
            }
            event.setCancelled(true);
        }
    }

    // ==========================================
    // BEAST ESCAPE: Interacción con palancas
    // ==========================================
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (!isInMinigame(event.getPlayer())) return;
        MiniGameManager mgr = getManager();
        if (mgr == null || !mgr.isGameActive() || mgr.getCurrentGame() == null) return;

        MiniGame game = mgr.getCurrentGame();
        if (game instanceof BeastEscapeGame && event.getClickedBlock() != null
                && event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            if (event.getClickedBlock().getType() == Material.LEVER) {
                BeastEscapeGame beast = (BeastEscapeGame) game;
                beast.onLeverActivated(event.getPlayer(), event.getClickedBlock().getLocation());
                // No cancelar: dejar que la palanca se active visualmente
            }
        }

        // Black Hole: permitir abrir cofres
        if (game instanceof BlackHoleGame) {
            return; // No cancelar ninguna interacción
        }

        // Eye of Storm: permitir abrir cofres y toda interacción
        if (game instanceof EyeOfStormGame) {
            return;
        }

        // SkyWars: permitir abrir cofres y toda interacción
        if (game instanceof SkyWarsGame) {
            return;
        }

        // Build Battle Classic: block ender pearls, interactables, handle vote clicks
        if (game instanceof BuildBattleClassicGame) {
            BuildBattleClassicGame bbc = (BuildBattleClassicGame) game;
            Player player = event.getPlayer();
            ItemStack held = player.getInventory().getItemInMainHand();

            // Block ender pearls always
            if (held != null && held.getType() == Material.ENDER_PEARL) {
                event.setCancelled(true);
                player.sendMessage("§c§l✖ §cNo puedes usar ender pearls en Build Battle.");
                return;
            }

            // Block interactable blocks (chests, doors, buttons, etc.) — they break the game
            if (event.getClickedBlock() != null
                    && event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
                Material clicked = event.getClickedBlock().getType();
                if (isInteractableBlock(clicked)) {
                    event.setCancelled(true);
                    return;
                }
            }

            if (held != null && held.getType() != Material.AIR && held.hasItemMeta()) {
                // Theme vote phase: click papers in hotbar
                if (!bbc.isBuildingAllowed() && !bbc.isVotingPhase()) {
                    int slot = player.getInventory().getHeldItemSlot();
                    bbc.handleVoteClick(player, slot);
                    event.setCancelled(true);
                }
                // Voting phase: nether star reopens vote GUI
                if (bbc.isVotingPhase() && held.getType() == Material.NETHER_STAR) {
                    event.setCancelled(true);
                    org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> bbc.openVoteGUI(player));
                }
            }
        }
    }

    /**
     * Whether a block type is an interactable that should be blocked in Build Battle Classic.
     */
    private boolean isInteractableBlock(Material mat) {
        String name = mat.name();
        return name.contains("CHEST") || name.contains("SHULKER")
                || name.contains("FURNACE") || name.contains("SMOKER") || name.contains("BLAST_FURNACE")
                || name.contains("BREWING_STAND") || name.contains("HOPPER") || name.contains("DROPPER")
                || name.contains("DISPENSER") || name.contains("BARREL") || name.contains("ANVIL")
                || name.contains("ENCHANTING_TABLE") || name.contains("CRAFTING_TABLE")
                || name.contains("CARTOGRAPHY_TABLE") || name.contains("GRINDSTONE")
                || name.contains("LOOM") || name.contains("STONECUTTER") || name.contains("SMITHING_TABLE")
                || name.contains("BEACON") || name.contains("LECTERN") || name.contains("COMPOSTER")
                || name.contains("CAULDRON") || name.contains("CAMPFIRE")
                || mat == Material.NOTE_BLOCK || mat == Material.JUKEBOX
                || mat == Material.RESPAWN_ANCHOR || mat == Material.BELL
                || mat == Material.CAKE || mat == Material.CANDLE_CAKE
                || name.contains("BED") && !name.contains("BEDROCK");
    }

    // ==========================================
    // FARM HUNT: Cazador hace click derecho en entidad
    // ==========================================
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityInteract(org.bukkit.event.player.PlayerInteractEntityEvent event) {
        if (!isInMinigame(event.getPlayer())) return;
        MiniGameManager mgr = getManager();
        if (mgr == null || !mgr.isGameActive() || mgr.getCurrentGame() == null) return;

        MiniGame game = mgr.getCurrentGame();
        if (game instanceof FarmHuntGame) {
            FarmHuntGame fh = (FarmHuntGame) game;
            Player player = event.getPlayer();
            org.bukkit.entity.Entity target = event.getRightClicked();
            
            // Con LibsDisguises los jugadores disfrazados siguen siendo Player entities
            if (fh.isHunter(player.getUniqueId()) && target instanceof org.bukkit.entity.LivingEntity) {
                event.setCancelled(true);
                fh.onHunterGuess(player, target);
            }
        }
    }

    // ==========================================
    // FARM HUNT: Sneak para hacer sonido de animal
    // ==========================================
    @EventHandler
    public void onSneak(org.bukkit.event.player.PlayerToggleSneakEvent event) {
        if (!isInMinigame(event.getPlayer())) return;
        if (!event.isSneaking()) return;
        
        MiniGameManager mgr = getManager();
        if (mgr == null || !mgr.isGameActive() || mgr.getCurrentGame() == null) return;

        MiniGame game = mgr.getCurrentGame();
        if (game instanceof FarmHuntGame) {
            ((FarmHuntGame) game).onAnimalSound(event.getPlayer());
        }
    }

    // ==========================================
    // PREVENT: Pociones en Murder Mystery
    // ==========================================
    @EventHandler
    public void onPotionConsume(org.bukkit.event.player.PlayerItemConsumeEvent event) {
        if (!isInMinigame(event.getPlayer())) return;
        MiniGameManager mgr = getManager();
        if (mgr == null || !mgr.isGameActive() || mgr.getCurrentGame() == null) return;

        MiniGame game = mgr.getCurrentGame();
        if (game instanceof MurderMysteryGame) {
            Material type = event.getItem().getType();
            if (type == Material.POTION || type == Material.SPLASH_POTION || type == Material.LINGERING_POTION) {
                event.setCancelled(true);
                event.getPlayer().sendMessage("§c§l✖ §cNo puedes usar pociones en Murder Mystery.");
            }
        }
    }

    @EventHandler
    public void onPotionSplash(org.bukkit.event.entity.PotionSplashEvent event) {
        if (event.getEntity().getWorld().getName().equals(MiniGameWorld.getWorldName())) {
            MiniGameManager mgr = getManager();
            if (mgr != null && mgr.isGameActive() && mgr.getCurrentGame() instanceof MurderMysteryGame) {
                event.setCancelled(true);
            }
        }
    }

    // ==========================================
    // PREVENT: Teleport fuera del mundo durante minijuego
    // ==========================================
    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        MiniGameManager mgr = getManager();
        if (mgr == null) return;

        boolean participating = isInMinigame(player);
        boolean joining = mgr.isJoinPhase() && mgr.getJoinedPlayers().contains(player.getUniqueId());
        if (!participating && !joining) return;

        if (event.getTo() == null || event.getTo().getWorld() == null) return;

        // Bloquear cualquier TP fuera del mundo de minijuegos mientras está apuntado/participando.
        // Esto evita el exploit de Ender Pearl stasis (TP diferido) que puede sacar al jugador del mundo.
        if (!event.getTo().getWorld().getName().equals(MiniGameWorld.getWorldName())) {
            event.setCancelled(true);
            player.sendMessage(com.moonlight.coreprotect.util.SmallCaps.convert(
                "§c§l✖ §cNo puedes salir del minijuego. Usa §f/mg leave §csi quieres abandonar."));
            return;
        }

        // Block ender pearl teleports in Build Battle Classic
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.ENDER_PEARL && participating) {
            MiniGame game = mgr.getCurrentGame();
            if (game instanceof BuildBattleClassicGame) {
                event.setCancelled(true);
            }
        }
    }

    // ==========================================
    // QUIT: Manejar desconexión durante minijuego
    // ==========================================
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        MiniGameManager mgr = getManager();
        if (mgr == null) return;

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Si estaba en fase de unión, quitarlo
        mgr.getJoinedPlayers().remove(uuid);

        // Si estaba en un minijuego activo
        if (mgr.isGameActive() && mgr.getCurrentGame() != null) {
            MiniGame game = mgr.getCurrentGame();
            if (game.getPlayers().contains(uuid)) {
                game.getAlivePlayers().remove(uuid);
                game.getPlayers().remove(uuid);

                // Limpiar efectos visuales (glowing, scoreboard, pociones)
                player.setGlowing(false);
                player.setGameMode(org.bukkit.GameMode.SURVIVAL);
                player.setFallDistance(0);
                player.setFireTicks(0);
                player.getActivePotionEffects().forEach(e -> player.removePotionEffect(e.getType()));
                player.setAllowFlight(false);
                player.setFlying(false);
                try { player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard()); } catch (Exception ignored) {}

                // Restaurar inventario y posición del overworld
                if (mgr.getSavedInventories().containsKey(uuid)) {
                    MiniGameManager.SavedPlayerData data = mgr.getSavedInventories().get(uuid);
                    if (data != null) {
                        player.getInventory().clear();
                        player.getInventory().setContents(data.inventory);
                        player.getInventory().setArmorContents(data.armor);
                        player.getInventory().setItemInOffHand(data.offhand);
                        player.setHealth(Math.min(data.health, player.getMaxHealth()));
                        player.setFoodLevel(data.food);
                        player.setSaturation(data.saturation);
                        player.setExp(data.exp);
                        player.setLevel(data.level);
                        player.setGameMode(data.gameMode);
                    }
                    mgr.getSavedInventories().remove(uuid);
                }
                // Teleportar al overworld antes de desconectar
                org.bukkit.Location returnLoc = mgr.getReturnLocations().remove(uuid);
                if (returnLoc != null) {
                    player.teleport(returnLoc);
                } else {
                    player.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
                }

                // === Manejar salida de jugadores clave ===
                // Beast Escape: si la bestia se va, ganan los supervivientes
                if (game instanceof BeastEscapeGame) {
                    BeastEscapeGame beg = (BeastEscapeGame) game;
                    if (beg.isBeast(uuid)) {
                        beg.broadcastGame("§4§l☠ §c¡La bestia ha abandonado! §aLos supervivientes ganan.");
                        beg.endGame(null);
                        return;
                    }
                    beg.eliminatePlayerSilent(uuid);
                }
                // TNT Tag: si el portador de la bomba se va, pasar a otro
                else if (game instanceof TNTTagGame) {
                    TNTTagGame tnt = (TNTTagGame) game;
                    if (uuid.equals(tnt.getBombHolder())) {
                        tnt.onBombHolderQuit();
                    }
                }
                // Murder Mystery: si el asesino se va, ganan los inocentes
                else if (game instanceof MurderMysteryGame) {
                    MurderMysteryGame mm = (MurderMysteryGame) game;
                    if (mm.isMurderer(uuid)) {
                        mm.broadcastGame("§a§l¡El asesino ha abandonado! §fLos inocentes ganan.");
                        mm.endGame(null);
                        return;
                    }
                }
                // Farm Hunt: limpiar disfraz al salir
                else if (game instanceof FarmHuntGame) {
                    ((FarmHuntGame) game).eliminatePlayerSilent(uuid);
                }

                // Verificar condición de victoria genérica
                game.checkWinCondition();
            }
        }
    }

    // ==========================================
    // JOIN: Restaurar datos si se desconectó durante minijuego
    // ==========================================
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        MiniGameManager mgr = getManager();
        if (mgr == null) return;

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Prioridad 1: datos en memoria (jugador se desconectó y el juego aún no terminó)
        if (mgr.getSavedInventories().containsKey(uuid)) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    player.setGameMode(org.bukkit.GameMode.SURVIVAL);
                    player.setFallDistance(0);
                    player.setFireTicks(0);
                    player.getActivePotionEffects().forEach(e -> player.removePotionEffect(e.getType()));
                    player.setAllowFlight(false);
                    player.setFlying(false);

                    MiniGameManager.SavedPlayerData data = mgr.getSavedInventories().get(uuid);
                    if (data != null) {
                        player.getInventory().clear();
                        player.getInventory().setContents(data.inventory);
                        player.getInventory().setArmorContents(data.armor);
                        player.getInventory().setItemInOffHand(data.offhand);
                        player.setHealth(Math.min(data.health, player.getMaxHealth()));
                        player.setFoodLevel(data.food);
                        player.setSaturation(data.saturation);
                        player.setExp(data.exp);
                        player.setLevel(data.level);
                        player.setGameMode(data.gameMode);
                        mgr.getSavedInventories().remove(uuid);

                        org.bukkit.Location ret = mgr.getReturnLocations().remove(uuid);
                        if (ret != null) {
                            player.teleport(ret);
                        } else {
                            // Fallback: si no hay ubicación de retorno, TP al spawn del overworld
                            player.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
                        }

                        mgr.restoreFlightIfPermitted(player);
                        player.sendMessage("§a§l✔ §7Tu inventario ha sido restaurado del minijuego anterior.");
                    }
                }
            }, 20L);
            return;
        }

        // Prioridad 2: datos en disco (servidor se reinició mientras el jugador estaba offline)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                boolean restored = mgr.loadAndRestoreFromDisk(player);

                // Prioridad 3: Safety net — si el jugador sigue en el mundo de minijuegos
                // sin datos guardados (el juego terminó mientras estaba offline, etc.),
                // forzar teleport al overworld para que no se quede atrapado.
                if (!restored && player.getWorld().getName().equals(MiniGameWorld.getWorldName())) {
                    player.setGameMode(org.bukkit.GameMode.SURVIVAL);
                    player.setFallDistance(0);
                    player.setFireTicks(0);
                    player.getActivePotionEffects().forEach(e -> player.removePotionEffect(e.getType()));
                    player.setAllowFlight(false);
                    player.setFlying(false);
                    player.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
                    mgr.restoreFlightIfPermitted(player);
                    player.sendMessage("§c§l⚠ §7Estabas en el mundo de minijuegos. Teletransportado al spawn.");
                    plugin.getLogger().warning("[MiniGames] Safety TP en join: " + player.getName() + " estaba en mundo minijuegos sin datos guardados.");
                }
            }
        }, 20L);
    }

    // ==========================================
    // PREVENT: Void/Suffocation/Fall damage in minigames
    // ==========================================
    @EventHandler(priority = EventPriority.HIGH)
    public void onEnvironmentalDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        if (!isInMinigameWorld(player)) return;

        MiniGameManager mgr = getManager();
        if (mgr == null || !mgr.isGameActive() || mgr.getCurrentGame() == null) return;
        MiniGame game = mgr.getCurrentGame();

        EntityDamageEvent.DamageCause cause = event.getCause();

        // VOID: eliminar jugador
        if (cause == EntityDamageEvent.DamageCause.VOID) {
            event.setCancelled(true);
            if (game.getAlivePlayers().contains(player.getUniqueId())) {
                player.setHealth(player.getMaxHealth());
                game.eliminatePlayer(player.getUniqueId());
            }
            return;
        }

        // SUFFOCATION: cancelar y teletransportar a spawn seguro (nunca matar por esto)
        if (cause == EntityDamageEvent.DamageCause.SUFFOCATION) {
            event.setCancelled(true);
            player.setHealth(player.getMaxHealth());
            // TP a un spawn seguro
            org.bukkit.World mgWorld = org.bukkit.Bukkit.getWorld(MiniGameWorld.getWorldName());
            if (mgWorld != null) {
                player.teleport(new org.bukkit.Location(mgWorld, 0, 85, 0));
                player.setFallDistance(0);
            }
            return;
        }

        // TNT TAG: cancelar TODO daño (fuego, explosiones, caída, etc.)
        // La única eliminación es por la bomba explotando al acabar el timer
        if (game instanceof com.moonlight.coreprotect.minigames.games.TNTTagGame) {
            event.setCancelled(true);
            player.setFireTicks(0);
            return;
        }

        // FALL: cancelar durante los primeros 10 segundos del juego, si es espectador,
        // o si es Anvil Rain (la gravedad/surge/earthquake no debe matar por caída)
        if (cause == EntityDamageEvent.DamageCause.FALL) {
            if (game.getGameTime() < 10 || game.getSpectators().contains(player.getUniqueId())
                    || !game.getAlivePlayers().contains(player.getUniqueId())
                    || game instanceof com.moonlight.coreprotect.minigames.games.AnvilRainGame) {
                event.setCancelled(true);
                return;
            }
        }
    }

    // ==========================================
    // PREVENT: Block place in minigame world
    // ==========================================
    @EventHandler
    public void onBlockPlace(org.bukkit.event.block.BlockPlaceEvent event) {
        if (isInMinigameWorld(event.getPlayer())) {
            // Build Battle: permitir colocar solo en tu propio plot durante fase de construcción
            MiniGameManager mgr = getManager();
            if (mgr != null && mgr.isGameActive() && mgr.getCurrentGame() instanceof BuildBattleGame) {
                BuildBattleGame bb = (BuildBattleGame) mgr.getCurrentGame();
                if (bb.isBuildingAllowed() && bb.isInsidePlayerPlot(event.getPlayer().getUniqueId(), event.getBlock().getLocation())) {
                    // Block dangerous/portal materials
                    org.bukkit.Material placed = event.getBlock().getType();
                    if (placed == org.bukkit.Material.OBSIDIAN
                            || placed == org.bukkit.Material.END_PORTAL_FRAME
                            || placed == org.bukkit.Material.RESPAWN_ANCHOR
                            || placed == org.bukkit.Material.NETHER_PORTAL
                            || placed == org.bukkit.Material.END_PORTAL
                            || placed == org.bukkit.Material.END_GATEWAY
                            || placed == org.bukkit.Material.BEDROCK
                            || placed == org.bukkit.Material.COMMAND_BLOCK
                            || placed == org.bukkit.Material.CHAIN_COMMAND_BLOCK
                            || placed == org.bukkit.Material.REPEATING_COMMAND_BLOCK
                            || placed == org.bukkit.Material.STRUCTURE_BLOCK
                            || placed == org.bukkit.Material.TNT
                            || placed == org.bukkit.Material.FIRE
                            || placed == org.bukkit.Material.LAVA
                            || placed == org.bukkit.Material.WATER) {
                        event.setCancelled(true);
                        event.getPlayer().sendMessage("§c§l✖ §cNo puedes colocar ese bloque en Build Battle.");
                        return;
                    }
                    return;
                }
            }
            // Pilares de la Fortuna: permitir colocar bloques (con restricciones)
            if (mgr != null && mgr.isGameActive() && mgr.getCurrentGame() instanceof PillarsOfFortuneGame) {
                org.bukkit.Material placed = event.getBlock().getType();
                // Bloquear materiales peligrosos
                if (placed == org.bukkit.Material.OBSIDIAN
                        || placed == org.bukkit.Material.END_PORTAL_FRAME
                        || placed == org.bukkit.Material.RESPAWN_ANCHOR
                        || placed == org.bukkit.Material.BEDROCK
                        || placed == org.bukkit.Material.COMMAND_BLOCK
                        || placed == org.bukkit.Material.CHAIN_COMMAND_BLOCK
                        || placed == org.bukkit.Material.REPEATING_COMMAND_BLOCK
                        || placed == org.bukkit.Material.STRUCTURE_BLOCK
                        || placed == org.bukkit.Material.LAVA_BUCKET) {
                    event.setCancelled(true);
                    return;
                }
                // Limitar altura de construcción
                if (event.getBlock().getY() > 160) { // PILLAR_Y(100) + PILLAR_HEIGHT(20) + 40 margen
                    event.setCancelled(true);
                    event.getPlayer().sendMessage("§c§l✖ §cNo puedes construir tan alto.");
                    return;
                }
                return;
            }
            // Black Hole: permitir colocar bloques (con restricciones)
            if (mgr != null && mgr.isGameActive() && mgr.getCurrentGame() instanceof BlackHoleGame) {
                org.bukkit.Material placed = event.getBlock().getType();
                if (placed == org.bukkit.Material.OBSIDIAN
                        || placed == org.bukkit.Material.END_PORTAL_FRAME
                        || placed == org.bukkit.Material.RESPAWN_ANCHOR
                        || placed == org.bukkit.Material.BEDROCK
                        || placed == org.bukkit.Material.COMMAND_BLOCK
                        || placed == org.bukkit.Material.CHAIN_COMMAND_BLOCK
                        || placed == org.bukkit.Material.REPEATING_COMMAND_BLOCK
                        || placed == org.bukkit.Material.STRUCTURE_BLOCK) {
                    event.setCancelled(true);
                    return;
                }
                if (event.getBlock().getY() > 150) {
                    event.setCancelled(true);
                    event.getPlayer().sendMessage("§c§l✖ §cNo puedes construir tan alto.");
                    return;
                }
                return;
            }
            // Eye of Storm: permitir colocar bloques (con restricciones)
            if (mgr != null && mgr.isGameActive() && mgr.getCurrentGame() instanceof EyeOfStormGame) {
                org.bukkit.Material placed = event.getBlock().getType();
                if (placed == org.bukkit.Material.OBSIDIAN
                        || placed == org.bukkit.Material.END_PORTAL_FRAME
                        || placed == org.bukkit.Material.RESPAWN_ANCHOR
                        || placed == org.bukkit.Material.BEDROCK
                        || placed == org.bukkit.Material.COMMAND_BLOCK
                        || placed == org.bukkit.Material.CHAIN_COMMAND_BLOCK
                        || placed == org.bukkit.Material.REPEATING_COMMAND_BLOCK
                        || placed == org.bukkit.Material.STRUCTURE_BLOCK) {
                    event.setCancelled(true);
                    return;
                }
                if (event.getBlock().getY() > 150) {
                    event.setCancelled(true);
                    event.getPlayer().sendMessage("§c§l✖ §cNo puedes construir tan alto.");
                    return;
                }
                return;
            }
            // SkyWars: permitir colocar bloques (con restricciones)
            if (mgr != null && mgr.isGameActive() && mgr.getCurrentGame() instanceof SkyWarsGame) {
                org.bukkit.Material placed = event.getBlock().getType();
                if (placed == org.bukkit.Material.OBSIDIAN
                        || placed == org.bukkit.Material.END_PORTAL_FRAME
                        || placed == org.bukkit.Material.RESPAWN_ANCHOR
                        || placed == org.bukkit.Material.BEDROCK
                        || placed == org.bukkit.Material.COMMAND_BLOCK
                        || placed == org.bukkit.Material.CHAIN_COMMAND_BLOCK
                        || placed == org.bukkit.Material.REPEATING_COMMAND_BLOCK
                        || placed == org.bukkit.Material.STRUCTURE_BLOCK) {
                    event.setCancelled(true);
                    return;
                }
                if (event.getBlock().getY() > 150) {
                    event.setCancelled(true);
                    event.getPlayer().sendMessage("§c§l✖ §cNo puedes construir tan alto.");
                    return;
                }
                return;
            }
            // Fake Deathmatch: permitir colocar bloques (con restricciones)
            if (mgr != null && mgr.isGameActive() && mgr.getCurrentGame() instanceof FakeDeathmatchGame) {
                org.bukkit.Material placed = event.getBlock().getType();
                if (placed == org.bukkit.Material.OBSIDIAN
                        || placed == org.bukkit.Material.END_PORTAL_FRAME
                        || placed == org.bukkit.Material.RESPAWN_ANCHOR
                        || placed == org.bukkit.Material.BEDROCK
                        || placed == org.bukkit.Material.COMMAND_BLOCK
                        || placed == org.bukkit.Material.CHAIN_COMMAND_BLOCK
                        || placed == org.bukkit.Material.REPEATING_COMMAND_BLOCK
                        || placed == org.bukkit.Material.STRUCTURE_BLOCK
                        || placed == org.bukkit.Material.TNT
                        || placed == org.bukkit.Material.LAVA) {
                    event.setCancelled(true);
                    return;
                }
                if (event.getBlock().getY() > 150) {
                    event.setCancelled(true);
                    event.getPlayer().sendMessage("§c§l✖ §cNo puedes construir tan alto.");
                    return;
                }
                return;
            }
            // Build Battle Classic: permitir colocar en tu plot durante fase de construcción
            if (mgr != null && mgr.isGameActive() && mgr.getCurrentGame() instanceof BuildBattleClassicGame) {
                BuildBattleClassicGame bbc = (BuildBattleClassicGame) mgr.getCurrentGame();
                if (bbc.isBuildingAllowed() && bbc.isInsidePlayerPlot(event.getPlayer().getUniqueId(), event.getBlock().getLocation())) {
                    // Block dangerous materials
                    org.bukkit.Material placed = event.getBlock().getType();
                    if (placed == org.bukkit.Material.OBSIDIAN
                            || placed == org.bukkit.Material.END_PORTAL_FRAME
                            || placed == org.bukkit.Material.RESPAWN_ANCHOR
                            || placed == org.bukkit.Material.NETHER_PORTAL
                            || placed == org.bukkit.Material.END_PORTAL
                            || placed == org.bukkit.Material.END_GATEWAY
                            || placed == org.bukkit.Material.BEDROCK
                            || placed == org.bukkit.Material.COMMAND_BLOCK
                            || placed == org.bukkit.Material.CHAIN_COMMAND_BLOCK
                            || placed == org.bukkit.Material.REPEATING_COMMAND_BLOCK
                            || placed == org.bukkit.Material.STRUCTURE_BLOCK
                            || placed == org.bukkit.Material.TNT
                            || placed == org.bukkit.Material.FIRE
                            || placed == org.bukkit.Material.LAVA
                            || placed == org.bukkit.Material.WATER) {
                        event.setCancelled(true);
                        event.getPlayer().sendMessage("§c§l✖ §cNo puedes colocar ese bloque.");
                        return;
                    }
                    return;
                }
            }
            event.setCancelled(true);
        }
    }

    // ==========================================
    // PREVENT: Inventory click in minigames
    // ==========================================
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        if (!isInMinigame(player)) return;
        // Build Battle: permitir mover items del inventario durante construcción
        MiniGameManager mgr = getManager();
        if (mgr != null && mgr.isGameActive() && mgr.getCurrentGame() instanceof BuildBattleGame) {
            BuildBattleGame bb = (BuildBattleGame) mgr.getCurrentGame();
            if (bb.isBuildingAllowed()) {
                return;
            }
        }
        // Pilares de la Fortuna: permitir mover items
        if (mgr != null && mgr.isGameActive() && mgr.getCurrentGame() instanceof PillarsOfFortuneGame) {
            return;
        }
        // Black Hole: permitir mover items (cofres incluidos)
        if (mgr != null && mgr.isGameActive() && mgr.getCurrentGame() instanceof BlackHoleGame) {
            return;
        }
        // Fake Deathmatch: permitir mover items
        if (mgr != null && mgr.isGameActive() && mgr.getCurrentGame() instanceof FakeDeathmatchGame) {
            return;
        }
        // Eye of Storm: permitir mover items (cofres incluidos)
        if (mgr != null && mgr.isGameActive() && mgr.getCurrentGame() instanceof EyeOfStormGame) {
            return;
        }
        // SkyWars: permitir mover items (cofres incluidos)
        if (mgr != null && mgr.isGameActive() && mgr.getCurrentGame() instanceof SkyWarsGame) {
            return;
        }
        // Build Battle Classic: permitir mover items durante construcción + handle vote GUI
        if (mgr != null && mgr.isGameActive() && mgr.getCurrentGame() instanceof BuildBattleClassicGame) {
            BuildBattleClassicGame bbc = (BuildBattleClassicGame) mgr.getCurrentGame();
            if (bbc.isBuildingAllowed()) {
                return;
            }
            // Handle vote GUI clicks
            if (bbc.isVotingPhase() && event.getView().getTitle().equals("§b§l⭐ Vota la Construcción ⭐")) {
                event.setCancelled(true);
                bbc.handleVoteGUIClick(player, event.getRawSlot());
                return;
            }
        }
        // Bloquear mover items en todos los demás minijuegos
        event.setCancelled(true);
    }

    // ==========================================
    // PREVENT: Mob spawning in minigame world
    // ==========================================
    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (event.getEntity().getWorld().getName().equals(MiniGameWorld.getWorldName())) {
            if (event.getSpawnReason() != CreatureSpawnEvent.SpawnReason.CUSTOM) {
                event.setCancelled(true);
            }
        }
    }

    // ==========================================
    // FARM HUNT: Prevenir daño a mobs disfraz y señuelos
    // ==========================================
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityDamage(org.bukkit.event.entity.EntityDamageEvent event) {
        if (!(event.getEntity().getWorld().getName().equals(MiniGameWorld.getWorldName()))) return;
        if (event.getEntity() instanceof Player) return;
        
        MiniGameManager mgr = getManager();
        if (mgr == null || !mgr.isGameActive() || mgr.getCurrentGame() == null) return;
        
        if (mgr.getCurrentGame() instanceof FarmHuntGame) {
            // Cancelar todo daño a entidades no-jugador en Farm Hunt
            event.setCancelled(true);
        }
    }

    // ==========================================
    // PREVENT: Commands in minigames (except /mg)
    // ==========================================
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!isInMinigame(player)) return;

        String command = event.getMessage().toLowerCase();
        
        // Permitir solo comandos de minijuegos
        if (command.startsWith("/mg") || command.startsWith("/minigame") || 
            command.startsWith("/minijuego") || command.startsWith("/minijuegos")) {
            return;
        }

        // Bloquear todos los demás comandos
        event.setCancelled(true);
        player.sendMessage(com.moonlight.coreprotect.util.SmallCaps.convert(
            "§c§l✖ §cNo puedes usar comandos durante un minijuego."));
    }

    // ==========================================
    // PREVENT: Fly during minigames
    // ==========================================
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerToggleFlight(org.bukkit.event.player.PlayerToggleFlightEvent event) {
        if (!isInMinigame(event.getPlayer())) return;
        
        // Build Battle / Build Battle Classic: permitir vuelo
        MiniGameManager mgr = getManager();
        if (mgr != null && mgr.isGameActive() &&
                (mgr.getCurrentGame() instanceof BuildBattleGame || mgr.getCurrentGame() instanceof BuildBattleClassicGame)) {
            return;
        }
        
        // Prevenir que se active el vuelo en minijuegos
        if (event.isFlying()) {
            event.setCancelled(true);
            event.getPlayer().setAllowFlight(false);
            event.getPlayer().setFlying(false);
            event.getPlayer().sendMessage("§c§l✖ §cNo puedes volar durante un minijuego.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerCommandFly(org.bukkit.event.player.PlayerCommandPreprocessEvent event) {
        if (!isInMinigame(event.getPlayer())) return;
        
        // Build Battle / Build Battle Classic: permitir comandos de vuelo
        MiniGameManager mgr = getManager();
        if (mgr != null && mgr.isGameActive() &&
                (mgr.getCurrentGame() instanceof BuildBattleGame || mgr.getCurrentGame() instanceof BuildBattleClassicGame)) {
            return;
        }
        
        String command = event.getMessage().toLowerCase();
        
        // Bloquear comandos de vuelo
        if (command.startsWith("/fly") || command.startsWith("/flight")) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§c§l✖ §cNo puedes usar comandos de vuelo durante un minijuego.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPotionEffect(org.bukkit.event.entity.EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        if (!isInMinigame(player)) return;

        MiniGameManager mgr = getManager();
        if (mgr == null || mgr.getCurrentGame() == null) return;

        // Si es Murder Mystery, no permitir que se quiten efectos (invisibilidad, etc.)
        if (mgr.getCurrentGame() instanceof MurderMysteryGame) {
            if (event.getAction() == org.bukkit.event.entity.EntityPotionEffectEvent.Action.REMOVED ||
                event.getAction() == org.bukkit.event.entity.EntityPotionEffectEvent.Action.CLEARED) {
                event.setCancelled(true);
            }
        }
    }
}
