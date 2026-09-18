package com.moonlight.coreprotect.combos;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.util.SmallCaps;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.bukkit.event.block.Action;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CandleComboListener implements Listener {

    private final CoreProtectPlugin plugin;
    private final Map<UUID, ComboSequence> playerCombos = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastClickTime = new ConcurrentHashMap<>();

    public CandleComboListener(CoreProtectPlugin plugin) {
        this.plugin = plugin;
    }

    public static NamespacedKey getCandleSwordKey(Plugin plugin) {
        return new NamespacedKey(plugin, "candle_sword");
    }

    // Mapa para tracking de clicks dobles
    private final Map<UUID, Long> lastLeftClick = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastRightClick = new ConcurrentHashMap<>();

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (!isCandleSword(item)) return;
        
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        
        // Detectar clicks izquierdo y derecho (incluso si están cancelados)
        boolean isLeftClick = false;
        boolean isRightClick = false;
        boolean isDoubleClick = false;
        
        Action action = event.getAction();
        
        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            isLeftClick = true;
            // Verificar si hay un click derecho reciente (dentro de 50ms = simultáneo)
            Long lastRight = lastRightClick.get(playerId);
            if (lastRight != null && (currentTime - lastRight) < 50) {
                isDoubleClick = true;
                isLeftClick = false; // Es un doble click, no un click izquierdo
            }
            lastLeftClick.put(playerId, currentTime);
        } else if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            isRightClick = true;
            // Verificar si hay un click izquierdo reciente (dentro de 50ms = simultáneo)
            Long lastLeft = lastLeftClick.get(playerId);
            if (lastLeft != null && (currentTime - lastLeft) < 50) {
                isDoubleClick = true;
                isRightClick = false; // Es un doble click, no un click derecho
            }
            lastRightClick.put(playerId, currentTime);
        } else {
            return;
        }

        // Debug para verificar que se detecta
        String clickType = isDoubleClick ? "DOBLE (A)" : (isLeftClick ? "IZQUIERDO (L)" : "DERECHO (R)");
        plugin.getLogger().info("[CandleCombo] Click detectado: " + clickType + " para " + player.getName());

        // Registrar el click
        registerClick(player, isDoubleClick, isLeftClick);
        
        // Cancelar el evento para evitar acciones por defecto
        event.setCancelled(true);
        event.setUseInteractedBlock(Event.Result.DENY);
    }

    private void registerClick(Player player, boolean isDoubleClick, boolean isLeftClick) {
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();

        // Limpiar combo si pasó mucho tiempo
        if (playerCombos.containsKey(playerId)) {
            ComboSequence sequence = playerCombos.get(playerId);
            if (currentTime - sequence.getLastClickTime() > 3000) { // 3 segundos máximo
                playerCombos.remove(playerId);
                lastClickTime.remove(playerId);
                player.sendTitle("", "", 0, 0, 0); // Limpiar title
            }
        }

        // Añadir click a la secuencia
        ComboSequence sequence = playerCombos.computeIfAbsent(playerId, k -> new ComboSequence());
        sequence.addClick(isDoubleClick, isLeftClick, currentTime);
        lastClickTime.put(playerId, currentTime);

        // Mostrar subtitle con la secuencia actual
        showComboSubtitle(player, sequence);

        // Verificar si completó algún combo
        checkCombos(player, sequence);
    }

    private void checkCombos(Player player, ComboSequence sequence) {
        String pattern = sequence.getPattern();
        
        // COMBOS ÚNICOS - Solo match exacto, no parciales
        // COMBOS RÁPIDOS (1.5s) - Secuencias únicas
        if (pattern.equals("A-L-R") && sequence.getTimeWindow() <= 1500) {
            executeCombo(player, "Explosión Veloz", "Explosión instantánea con fuego", 3, 4.0, 5.0);
            resetCombo(player);
            return;
        }
        else if (pattern.equals("L-A-L") && sequence.getTimeWindow() <= 1500) {
            executeCombo(player, "Torbellino", "Tornado que empuja enemigos", 4, 3.0, 6.0);
            resetCombo(player);
            return;
        }
        else if (pattern.equals("R-A-R") && sequence.getTimeWindow() <= 1500) {
            executeCombo(player, "Impacto Doble", "Golpe crítico duplicado", 3, 6.0, 4.0);
            resetCombo(player);
            return;
        }
        // COMBOS MEDIOS (2.0s) - Secuencias únicas
        else if (pattern.equals("A-L-A-R") && sequence.getTimeWindow() <= 2000) {
            executeCombo(player, "Furia Tétrada", "Ataque en forma de cruz con fuego", 5, 7.0, 8.0);
            resetCombo(player);
            return;
        }
        else if (pattern.equals("L-R-A-L") && sequence.getTimeWindow() <= 2000) {
            executeCombo(player, "Santo Grial", "Curación y protección temporal", 6, 2.0, 9.0);
            resetCombo(player);
            return;
        }
        else if (pattern.equals("R-L-A-R") && sequence.getTimeWindow() <= 2000) {
            executeCombo(player, "Cruz Infernal", "Daño masivo en línea recta", 5, 8.0, 6.0);
            resetCombo(player);
            return;
        }
        // COMBOS LARGOS (2.5s) - Secuencias únicas
        else if (pattern.equals("A-L-R-A-L-R") && sequence.getTimeWindow() <= 2500) {
            executeCombo(player, "Tsunami de Fuego", "Onda expansiva de lava", 8, 12.0, 15.0);
            resetCombo(player);
            return;
        }
        else if (pattern.equals("L-A-R-L-A-R") && sequence.getTimeWindow() <= 2500) {
            executeCombo(player, "Tempestad Divina", "Tormenta eléctrica controlada", 10, 10.0, 18.0);
            resetCombo(player);
            return;
        }
        else if (pattern.equals("R-L-A-R-L-A") && sequence.getTimeWindow() <= 2500) {
            executeCombo(player, "Danza Espectral", "Invocación de espíritus aliados", 9, 9.0, 16.0);
            resetCombo(player);
            return;
        }
        // COMBO MAESTRO (3.0s) - El más difícil
        else if (pattern.equals("A-L-R-A-L-R-A") && sequence.getTimeWindow() <= 3000) {
            executeCombo(player, "Apocalipsis", "Destrucción total del área", 15, 20.0, 25.0);
            resetCombo(player);
            return;
        }
        // Si la secuencia es muy larga o inválida, mostrar error
        else if (sequence.getClicks().size() > 7 || !couldBeValidSequence(pattern)) {
            showErrorSubtitle(player, sequence);
            resetCombo(player);
        }
    }

    private void showComboSubtitle(Player player, ComboSequence sequence) {
        String pattern = sequence.getPattern();
        String displayPattern = pattern.replace("-", " ");
        
        // Verificar si la secuencia actual podría llevar a algún combo válido
        if (couldBeValidSequence(pattern)) {
            // Verde si va bien
            player.sendTitle("", "§a" + displayPattern, 0, 20, 5);
        } else {
            // Rojo si es inválido
            player.sendTitle("", "§c" + displayPattern, 0, 20, 5);
        }
    }

    private void showErrorSubtitle(Player player, ComboSequence sequence) {
        String pattern = sequence.getPattern();
        String displayPattern = pattern.replace("-", " ");
        
        // Mostrar en rojo con indicación de error
        player.sendTitle("", "§c✗ " + displayPattern + " - ¡ERROR!", 0, 30, 10);
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
        
        // Limpiar subtitle después de un tiempo
        new BukkitRunnable() {
            @Override
            public void run() {
                player.sendTitle("", "", 0, 0, 0); // Limpiar title completamente
            }
        }.runTaskLater(plugin, 60L); // 3 segundos después
    }

    private boolean couldBeValidSequence(String pattern) {
        // Secuencias válidas posibles con el nuevo sistema A
        String[] validPatterns = {
            // COMBOS RÁPIDOS (1.5s)
            "A", "A-L", "A-L-R",  // Explosión Veloz
            "L", "L-A", "L-A-L",  // Torbellino
            "R", "R-A", "R-A-R",  // Impacto Doble
            // COMBOS MEDIOS (2.0s)
            "A-L-A", "A-L-A-R",  // Furia Tétrada
            "L", "L-R", "L-R-A", "L-R-A-L",  // Santo Grial
            "R", "R-L", "R-L-A", "R-L-A-R",  // Cruz Infernal
            // COMBOS LARGOS (2.5s)
            "A-L-R", "A-L-R-A", "A-L-R-A-L", "A-L-R-A-L-R",  // Tsunami de Fuego
            "L-A", "L-A-R", "L-A-R-L", "L-A-R-L-A", "L-A-R-L-A-R",  // Tempestad Divina
            "R-L", "R-L-A", "R-L-A-R", "R-L-A-R-L", "R-L-A-R-L-A",  // Danza Espectral
            // COMBO MAESTRO (3.0s)
            "A-L-R-A", "A-L-R-A-L", "A-L-R-A-L-R", "A-L-R-A-L-R-A"  // Apocalipsis
        };
        
        for (String valid : validPatterns) {
            if (pattern.equals(valid)) {
                return true;
            }
        }
        return false;
    }

    private void executeCombo(Player player, String comboName, String description, int duration, double damage, double radius) {
        Location loc = player.getLocation();
        World world = loc.getWorld();

        // Mensajes y efectos
        player.sendMessage(SmallCaps.convert("§6§l⚡ §6¡" + comboName + " ejecutado!"));
        player.sendMessage(SmallCaps.convert("§7" + description));
        player.sendTitle(SmallCaps.convert("§6§l⚔ " + comboName), SmallCaps.convert("§7" + description), 5, 20, 10);
        
        // Limpiar subtitle después de un tiempo
        new BukkitRunnable() {
            @Override
            public void run() {
                player.sendTitle("", "", 0, 0, 0); // Limpiar title completamente
            }
        }.runTaskLater(plugin, 60L); // 3 segundos después

        // Efectos visuales según el combo
        switch (comboName) {
            case "Explosión Veloz":
                world.createExplosion(loc, 2.0f, false, false);
                world.spawnParticle(Particle.FLAME, loc, 50, 2, 2, 2, 0.5);
                world.spawnParticle(Particle.EXPLOSION, loc, 10, 1, 1, 1, 0.1);
                break;
            case "Torbellino":
                createTornado(world, loc, duration);
                world.spawnParticle(Particle.SMOKE, loc, 100, 3, 3, 3, 0.3);
                break;
            case "Impacto Doble":
                world.spawnParticle(Particle.CRIT, loc, 80, 2, 2, 2, 0.4);
                player.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 1.5f);
                break;
            case "Furia Tétrada":
                createFuryEffect(world, loc, duration);
                break;
            case "Santo Grial":
                createHolyGrailEffect(world, loc, duration);
                break;
            case "Cruz Infernal":
                createCrossEffect(world, loc, duration);
                break;
            case "Tsunami de Fuego":
                createFireTsunami(world, loc, duration);
                break;
            case "Tempestad Divina":
                createDivineStorm(world, loc, duration);
                break;
            case "Danza Espectral":
                createSpectralDance(world, loc, duration);
                break;
        }

        // Aplicar daño a enemigos cercanos
        applyNearbyDamage(player, loc, damage, radius);
    }

    private void applyNearbyDamage(Player caster, Location center, double damage, double radius) {
        center.getWorld().getNearbyEntities(center, radius, radius, radius).forEach(entity -> {
            if (entity instanceof LivingEntity && entity != caster) {
                LivingEntity target = (LivingEntity) entity;
                target.damage(damage, caster);
                
                // Efectos adicionales
                target.setFireTicks(60); // 3 segundos de fuego
                Vector knockback = target.getLocation().toVector().subtract(center.toVector()).normalize().multiply(2);
                target.setVelocity(knockback);
            }
        });
    }

    // Efectos especiales para cada combo
    private void createTornado(World world, Location center, int duration) {
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks >= duration * 20) {
                    cancel();
                    return;
                }
                
                for (int i = 0; i < 5; i++) {
                    double angle = (ticks * 0.2 + i * 72) * Math.PI / 180;
                    double x = center.getX() + Math.cos(angle) * 2;
                    double z = center.getZ() + Math.sin(angle) * 2;
                    Location particleLoc = new Location(world, x, center.getY() + ticks * 0.1, z);
                    world.spawnParticle(Particle.SMOKE, particleLoc, 5, 0.1, 0.1, 0.1, 0);
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    private void createFuryEffect(World world, Location center, int duration) {
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks >= duration * 20) {
                    cancel();
                    return;
                }
                
                double angle = ticks * 0.3;
                for (int i = 0; i < 4; i++) {
                    double x = center.getX() + Math.cos(angle + i * Math.PI/2) * 3;
                    double z = center.getZ() + Math.sin(angle + i * Math.PI/2) * 3;
                    Location particleLoc = new Location(world, x, center.getY(), z);
                    world.spawnParticle(Particle.DUST, particleLoc, 10, 
                        new Particle.DustOptions(Color.RED, 1.0f));
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    private void createHolyGrailEffect(World world, Location center, int duration) {
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks >= duration * 20) {
                    cancel();
                    return;
                }
                
                world.spawnParticle(Particle.END_ROD, center, 20, 2, 2, 2, 0.2);
                world.spawnParticle(Particle.TOTEM_OF_UNDYING, center, 5, 1, 1, 1, 0.1);
                ticks++;
            }
        }.runTaskTimer(plugin, 0, 4);
    }

    private void createCrossEffect(World world, Location center, int duration) {
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks >= duration * 20) {
                    cancel();
                    return;
                }
                
                // Líneas horizontales y verticales
                for (int i = -4; i <= 4; i++) {
                    Location h1 = center.clone().add(i, 0, 0);
                    Location h2 = center.clone().add(0, 0, i);
                    world.spawnParticle(Particle.FLAME, h1, 3, 0.1, 0.1, 0.1, 0);
                    world.spawnParticle(Particle.FLAME, h2, 3, 0.1, 0.1, 0.1, 0);
                }
                ticks += 2;
            }
        }.runTaskTimer(plugin, 0, 2);
    }

    private void createFireTsunami(World world, Location center, int duration) {
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks >= duration * 20) {
                    cancel();
                    return;
                }
                
                double radius = ticks * 0.5;
                for (int i = 0; i < 360; i += 10) {
                    double angle = i * Math.PI / 180;
                    double x = center.getX() + Math.cos(angle) * radius;
                    double z = center.getZ() + Math.sin(angle) * radius;
                    Location particleLoc = new Location(world, x, center.getY() + Math.random() * 2, z);
                    world.spawnParticle(Particle.FLAME, particleLoc, 5, 0.2, 0.2, 0.2, 0);
                    world.spawnParticle(Particle.LAVA, particleLoc, 1, 0.1, 0.1, 0.1, 0);
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    private void createDivineStorm(World world, Location center, int duration) {
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks >= duration * 20) {
                    cancel();
                    return;
                }
                
                // Rayos aleatorios
                for (int i = 0; i < 3; i++) {
                    double x = center.getX() + (Math.random() - 0.5) * 10;
                    double z = center.getZ() + (Math.random() - 0.5) * 10;
                    Location strikeLoc = new Location(world, x, center.getY(), z);
                    world.strikeLightningEffect(strikeLoc);
                    world.spawnParticle(Particle.ELECTRIC_SPARK, strikeLoc, 20, 2, 2, 2, 0.3);
                }
                
                world.spawnParticle(Particle.HAPPY_VILLAGER, center, 10, 3, 3, 3, 0.2);
                ticks += 5;
            }
        }.runTaskTimer(plugin, 0, 5);
    }

    private void createSpectralDance(World world, Location center, int duration) {
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks >= duration * 20) {
                    cancel();
                    return;
                }
                
                // Espíritus danzantes
                for (int i = 0; i < 5; i++) {
                    double angle = (ticks * 0.1 + i * 72) * Math.PI / 180;
                    double x = center.getX() + Math.cos(angle) * 4;
                    double z = center.getZ() + Math.sin(angle) * 4;
                    double y = center.getY() + Math.sin(ticks * 0.2 + i) * 2;
                    Location spiritLoc = new Location(world, x, y, z);
                    
                    world.spawnParticle(Particle.SOUL, spiritLoc, 8, 0.3, 0.3, 0.3, 0);
                    world.spawnParticle(Particle.END_ROD, spiritLoc, 3, 0.1, 0.1, 0.1, 0);
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0, 1);
    }

    private void resetCombo(Player player) {
        playerCombos.remove(player.getUniqueId());
        lastClickTime.remove(player.getUniqueId());
    }

    private boolean isCandleSword(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer()
                .has(getCandleSwordKey(plugin), PersistentDataType.BYTE);
    }

    // Clase para manejar secuencias de combos
    private static class ComboSequence {
        private final List<Character> clicks = new ArrayList<>();
        private long lastClickTime;

        public void addClick(boolean isDoubleClick, boolean isLeftClick, long time) {
            if (isDoubleClick) {
                clicks.add('A'); // Ambos clicks a la vez
            } else {
                clicks.add(isLeftClick ? 'L' : 'R');
            }
            lastClickTime = time;
        }

        public String getPattern() {
            StringBuilder pattern = new StringBuilder();
            for (Character click : clicks) {
                if (pattern.length() > 0) pattern.append("-");
                pattern.append(click);
            }
            return pattern.toString();
        }

        public long getTimeWindow() {
            if (clicks.isEmpty()) return 0;
            // El tiempo transcurrido es aproximadamente clicks.size() * tiempo entre clicks
            // Para simplificar, asumimos que cada click está separado por ~200ms
            return clicks.size() * 200; // Tiempo estimado en milisegundos
        }

        public List<Character> getClicks() {
            return new ArrayList<>(clicks);
        }

        public long getLastClickTime() {
            return lastClickTime;
        }
    }
}
