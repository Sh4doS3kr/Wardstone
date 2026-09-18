package com.moonlight.coreprotect.koth;

import com.moonlight.coreprotect.CoreProtectPlugin;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Sistema de partículas para delimitar visualmente la zona KOTH.
 * Usa un BukkitRunnable para renderizar partículas en los bordes
 * de la zona rectangular cada pocos ticks.
 */
public class KothParticles {

    private final CoreProtectPlugin plugin;
    private BukkitRunnable particleTask;
    private int taskId = -1;

    // Configuración visual
    private static final double PARTICLE_SPACING = 1.5; // Distancia entre partículas
    private static final int PARTICLE_Y_OFFSET = 1; // Altura base sobre el suelo
    private static final int PARTICLE_LAYERS = 3; // Capas verticales de partículas

    public KothParticles(CoreProtectPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Inicia el renderizado de partículas para la zona KOTH activa.
     * Se ejecuta cada 40 ticks (2 segundos) para no afectar rendimiento.
     */
    public void start(KothZone zone) {
        stop(); // Parar si ya estaba corriendo

        particleTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (zone == null || !zone.isActive()) {
                    cancel();
                    return;
                }

                World world = zone.getWorld();
                if (world == null)
                    return;

                // Determinar color según estado
                Color color;
                switch (zone.getState()) {
                    case CAPTURING:
                        color = Color.fromRGB(0, 255, 127); // Verde Neón
                        break;
                    case CONTESTED:
                        color = Color.fromRGB(255, 0, 0); // Rojo Intenso
                        break;
                    case CAPTURED:
                        color = Color.fromRGB(255, 215, 0); // Dorado
                        break;
                    default:
                        color = Color.fromRGB(127, 255, 212); // Aguamarina (esperando)
                        break;
                }

                Particle.DustOptions dustOptions = new Particle.DustOptions(color, 1.2f);
                Particle.DustOptions edgeOptions = new Particle.DustOptions(Color.WHITE, 0.8f);

                int x1 = zone.getX1();
                int z1 = zone.getZ1();
                int x2 = zone.getX2();
                int z2 = zone.getZ2();

                // Dibujar bordes
                for (int layer = 0; layer < 4; layer++) {
                    double yOffset = 0.5 + (layer * 1.5);

                    drawEdge(world, x1, z1, x2, z1, yOffset, dustOptions); // Norte
                    drawEdge(world, x1, z2, x2, z2, yOffset, dustOptions); // Sur
                    drawEdge(world, x1, z1, x1, z2, yOffset, dustOptions); // Oeste
                    drawEdge(world, x2, z1, x2, z2, yOffset, dustOptions); // Este
                }

                // Efecto de portal en el centro si está activo
                if (zone.isActive()) {
                    Location center = new Location(world, (x1 + x2) / 2.0, 64, (z1 + z2) / 2.0);
                    world.spawnParticle(Particle.PORTAL, center, 10, 2, 1, 2, 0.1);
                }
            }
        };

        taskId = particleTask.runTaskTimer(plugin, 0L, 20L).getTaskId(); // Más frecuente para fluidez
    }

    private void drawEdge(World world, double x1, double z1, double x2, double z2, double y,
            Particle.DustOptions scale) {
        double distance = Math.sqrt(Math.pow(x1 - x2, 2) + Math.pow(z1 - z2, 2));
        for (double d = 0; d <= distance; d += 1.0) {
            double lerp = d / distance;
            double px = x1 + (x2 - x1) * lerp;
            double pz = z1 + (z2 - z1) * lerp;

            // En un mundo vacío, buscamos el bloque más alto o usamos altura fija
            double py = world.getHighestBlockYAt((int) px, (int) pz);
            if (py < 0)
                py = 64; // Altura mínima en void

            world.spawnParticle(Particle.DUST, px + 0.5, py + y, pz + 0.5, 1, 0, 0, 0, scale);
        }
    }

    public void stop() {
        if (taskId != -1) {
            try {
                plugin.getServer().getScheduler().cancelTask(taskId);
            } catch (Exception ignored) {
            }
            taskId = -1;
        }
    }
}
