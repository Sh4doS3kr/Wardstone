package com.moonlight.coreprotect.minigames;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Stack;

/**
 * Construye arenas decoradas para cada minijuego.
 * Cada arena se genera dinámicamente en el mundo de minijuegos.
 */
public class ArenaBuilder {

    private static final Random RANDOM = new Random();

    // =============================================
    // TNT RUN - Plataforma de TNT con capas
    // =============================================
    public static void buildTNTRun(World w) {
        int centerX = 0, centerZ = 0;
        int radius = 25;
        int baseY = 80;

        // 3 capas de TNT con sandstone debajo, separadas por 5 bloques
        Material[] layerBlocks = {Material.RED_SAND, Material.SAND, Material.TNT};
        Material[] supportBlocks = {Material.RED_SANDSTONE, Material.SANDSTONE, Material.SMOOTH_SANDSTONE};
        
        for (int layer = 0; layer < 3; layer++) {
            int y = baseY - (layer * 6);
            Material block = layerBlocks[layer];
            Material support = supportBlocks[layer];
            
            for (int x = centerX - radius; x <= centerX + radius; x++) {
                for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                    double dist = Math.sqrt((x - centerX) * (x - centerX) + (z - centerZ) * (z - centerZ));
                    if (dist <= radius) {
                        w.getBlockAt(x, y, z).setType(block);
                        w.getBlockAt(x, y - 1, z).setType(support);
                    }
                }
            }
        }

        // Paredes de cristal decorativas
        buildGlassWalls(w, centerX, centerZ, radius + 1, baseY - 18, baseY + 5, Material.RED_STAINED_GLASS);
        
        // Decoración: pilares de obsidiana en los bordes
        for (int angle = 0; angle < 360; angle += 45) {
            double rad = Math.toRadians(angle);
            int px = centerX + (int)(radius * Math.cos(rad));
            int pz = centerZ + (int)(radius * Math.sin(rad));
            for (int y = baseY - 18; y <= baseY + 6; y++) {
                w.getBlockAt(px, y, pz).setType(Material.OBSIDIAN);
            }
            w.getBlockAt(px, baseY + 7, pz).setType(Material.REDSTONE_LAMP);
        }

        // Suelo de lava debajo (muerte)
        int lavaY = baseY - 20;
        for (int x = centerX - radius - 2; x <= centerX + radius + 2; x++) {
            for (int z = centerZ - radius - 2; z <= centerZ + radius + 2; z++) {
                w.getBlockAt(x, lavaY, z).setType(Material.LAVA);
                w.getBlockAt(x, lavaY - 1, z).setType(Material.OBSIDIAN);
            }
        }

        // Decoración de techo con glowstone
        for (int x = centerX - radius; x <= centerX + radius; x += 5) {
            for (int z = centerZ - radius; z <= centerZ + radius; z += 5) {
                double dist = Math.sqrt((x - centerX) * (x - centerX) + (z - centerZ) * (z - centerZ));
                if (dist <= radius) {
                    w.getBlockAt(x, baseY + 8, z).setType(Material.GLOWSTONE);
                }
            }
        }
    }

    public static List<Location> getTNTRunSpawns(World w) {
        List<Location> spawns = new ArrayList<>();
        int y = 81;
        for (int i = 0; i < 24; i++) {
            double angle = (2 * Math.PI / 24) * i;
            int x = (int)(15 * Math.cos(angle));
            int z = (int)(15 * Math.sin(angle));
            spawns.add(new Location(w, x + 0.5, y, z + 0.5));
        }
        return spawns;
    }

    // =============================================
    // SPLEEF - 3 capas de nieve
    // =============================================
    public static void buildSpleef(World w) {
        int centerX = 0, centerZ = 0;
        int radius = 25;
        int baseY = 90;

        // 3 capas de nieve separadas por 3 de aire
        for (int layer = 0; layer < 3; layer++) {
            int y = baseY - (layer * 4);
            for (int x = centerX - radius; x <= centerX + radius; x++) {
                for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                    double dist = Math.sqrt((x - centerX) * (x - centerX) + (z - centerZ) * (z - centerZ));
                    if (dist <= radius) {
                        w.getBlockAt(x, y, z).setType(Material.SNOW_BLOCK);
                    }
                }
            }
        }

        // Paredes de cristal azul hielo
        buildGlassWalls(w, centerX, centerZ, radius + 1, baseY - 14, baseY + 5, Material.LIGHT_BLUE_STAINED_GLASS);

        // Pilares de hielo compacto
        for (int angle = 0; angle < 360; angle += 30) {
            double rad = Math.toRadians(angle);
            int px = centerX + (int)(radius * Math.cos(rad));
            int pz = centerZ + (int)(radius * Math.sin(rad));
            for (int y = baseY - 14; y <= baseY + 6; y++) {
                w.getBlockAt(px, y, pz).setType(Material.PACKED_ICE);
            }
            w.getBlockAt(px, baseY + 7, pz).setType(Material.SEA_LANTERN);
        }

        // Suelo de agua debajo
        int waterY = baseY - 16;
        for (int x = centerX - radius - 2; x <= centerX + radius + 2; x++) {
            for (int z = centerZ - radius - 2; z <= centerZ + radius + 2; z++) {
                w.getBlockAt(x, waterY, z).setType(Material.WATER);
                w.getBlockAt(x, waterY - 1, z).setType(Material.PRISMARINE_BRICKS);
            }
        }

        // Decoración: copos (sea lanterns)
        for (int x = centerX - radius; x <= centerX + radius; x += 6) {
            for (int z = centerZ - radius; z <= centerZ + radius; z += 6) {
                double dist = Math.sqrt((x - centerX) * (x - centerX) + (z - centerZ) * (z - centerZ));
                if (dist <= radius - 2) {
                    w.getBlockAt(x, baseY + 8, z).setType(Material.SEA_LANTERN);
                }
            }
        }
    }

    public static List<Location> getSpleefSpawns(World w) {
        List<Location> spawns = new ArrayList<>();
        int y = 91;
        for (int i = 0; i < 24; i++) {
            double angle = (2 * Math.PI / 24) * i;
            int x = (int)(15 * Math.cos(angle));
            int z = (int)(15 * Math.sin(angle));
            spawns.add(new Location(w, x + 0.5, y, z + 0.5));
        }
        return spawns;
    }

    // =============================================
    // TNT TAG - Plaza Medieval Estilizada
    // =============================================
    public static void buildTNTTag(World w) {
        int cx = 0, cz = 0;
        int half = 42;
        int by = 80;

        // === SUELO: Adoquines con caminos empedrados ===
        for (int x = cx - half; x <= cx + half; x++) {
            for (int z = cz - half; z <= cz + half; z++) {
                w.getBlockAt(x, by - 2, z).setType(Material.STONE);
                w.getBlockAt(x, by - 1, z).setType(Material.DIRT);
                // Caminos principales en cruz
                boolean mainPath = (Math.abs(x) <= 2) || (Math.abs(z) <= 2);
                // Caminos secundarios
                boolean secPath = (Math.abs(x - 20) <= 1) || (Math.abs(x + 20) <= 1)
                        || (Math.abs(z - 20) <= 1) || (Math.abs(z + 20) <= 1);
                if (mainPath) {
                    w.getBlockAt(x, by, z).setType(
                        (x + z) % 2 == 0 ? Material.POLISHED_ANDESITE : Material.STONE_BRICKS);
                } else if (secPath) {
                    w.getBlockAt(x, by, z).setType(Material.COBBLESTONE);
                } else {
                    // Bloques de hierba con variación
                    int r = RANDOM.nextInt(10);
                    if (r < 6) w.getBlockAt(x, by, z).setType(Material.GRASS_BLOCK);
                    else if (r < 8) w.getBlockAt(x, by, z).setType(Material.COARSE_DIRT);
                    else w.getBlockAt(x, by, z).setType(Material.PODZOL);
                }
            }
        }

        // === MUROS EXTERIORES: Muralla medieval con almenas ===
        for (int x = cx - half; x <= cx + half; x++) {
            for (int y = by + 1; y <= by + 5; y++) {
                w.getBlockAt(x, y, cz - half).setType(Material.STONE_BRICKS);
                w.getBlockAt(x, y, cz + half).setType(Material.STONE_BRICKS);
            }
            // Almenas
            if (x % 3 == 0) {
                w.getBlockAt(x, by + 6, cz - half).setType(Material.STONE_BRICK_WALL);
                w.getBlockAt(x, by + 6, cz + half).setType(Material.STONE_BRICK_WALL);
            }
        }
        for (int z = cz - half; z <= cz + half; z++) {
            for (int y = by + 1; y <= by + 5; y++) {
                w.getBlockAt(cx - half, y, z).setType(Material.STONE_BRICKS);
                w.getBlockAt(cx + half, y, z).setType(Material.STONE_BRICKS);
            }
            if (z % 3 == 0) {
                w.getBlockAt(cx - half, by + 6, z).setType(Material.STONE_BRICK_WALL);
                w.getBlockAt(cx + half, by + 6, z).setType(Material.STONE_BRICK_WALL);
            }
        }
        // Arcos decorativos en las murallas (SIN abertura al vacío, solo decoración interna)
        for (int side = -1; side <= 1; side += 2) {
            // Arco interior falso (rebajamos 1 bloque de grosor pero dejamos pared trasera)
            for (int y = by + 1; y <= by + 4; y++) {
                for (int d = -2; d <= 2; d++) {
                    // Solo crear un arco visual interior con rejas, NO abrir al exterior
                    if (side == -1) {
                        w.getBlockAt(d, y, cz + side * half + 1).setType(Material.AIR); // hueco interior
                        w.getBlockAt(cx + side * half + 1, y, d).setType(Material.AIR);
                    } else {
                        w.getBlockAt(d, y, cz + side * half - 1).setType(Material.AIR);
                        w.getBlockAt(cx + side * half - 1, y, d).setType(Material.AIR);
                    }
                }
            }
            // Rejas decorativas en la pared
            for (int d = -2; d <= 2; d++) {
                w.getBlockAt(d, by + 3, cz + side * half).setType(Material.IRON_BARS);
                w.getBlockAt(d, by + 4, cz + side * half).setType(Material.IRON_BARS);
                w.getBlockAt(cx + side * half, by + 3, d).setType(Material.IRON_BARS);
                w.getBlockAt(cx + side * half, by + 4, d).setType(Material.IRON_BARS);
            }
        }

        // === FUENTE CENTRAL (agua 100% contenida) ===
        // Base sólida elevada
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist <= 5.5) {
                    w.getBlockAt(cx + dx, by, cz + dz).setType(Material.QUARTZ_BRICKS);
                    w.getBlockAt(cx + dx, by + 1, cz + dz).setType(Material.QUARTZ_BRICKS);
                }
                // Muro contenedor sólido de 2 bloques de alto
                if (dist > 3.5 && dist <= 5.5) {
                    w.getBlockAt(cx + dx, by + 2, cz + dz).setType(Material.QUARTZ_BRICKS);
                    w.getBlockAt(cx + dx, by + 3, cz + dz).setType(Material.QUARTZ_SLAB);
                }
                // Interior: agua a nivel by+2, contenida por muros de by+2 y by+3
                if (dist <= 3.5) {
                    w.getBlockAt(cx + dx, by + 2, cz + dz).setType(Material.WATER);
                    // Asegurar aire encima del agua
                    w.getBlockAt(cx + dx, by + 3, cz + dz).setType(Material.AIR);
                }
            }
        }
        // Pilar central ornamental (sobre el agua, sin chorros)
        w.getBlockAt(cx, by + 2, cz).setType(Material.QUARTZ_PILLAR);
        w.getBlockAt(cx, by + 3, cz).setType(Material.QUARTZ_PILLAR);
        w.getBlockAt(cx, by + 4, cz).setType(Material.QUARTZ_PILLAR);
        w.getBlockAt(cx, by + 5, cz).setType(Material.SEA_LANTERN);
        // Decoración seca alrededor del pilar (sin agua que pueda fluir)
        w.getBlockAt(cx + 1, by + 3, cz).setType(Material.QUARTZ_SLAB);
        w.getBlockAt(cx - 1, by + 3, cz).setType(Material.QUARTZ_SLAB);
        w.getBlockAt(cx, by + 3, cz + 1).setType(Material.QUARTZ_SLAB);
        w.getBlockAt(cx, by + 3, cz - 1).setType(Material.QUARTZ_SLAB);

        // === 1. TORRE DEL RELOJ (NE, alta y estrecha) ===
        int tx = 25, tz = -25;
        // Base 7x7
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int y = by + 1; y <= by + 12; y++) {
                    boolean edge = Math.abs(dx) == 3 || Math.abs(dz) == 3;
                    if (edge) {
                        w.getBlockAt(tx + dx, y, tz + dz).setType(
                            y % 4 == 0 ? Material.CHISELED_STONE_BRICKS : Material.STONE_BRICKS);
                    }
                }
                // Techo piramidal
                w.getBlockAt(tx + dx, by + 13, tz + dz).setType(Material.DARK_OAK_SLAB);
            }
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                w.getBlockAt(tx + dx, by + 14, tz + dz).setType(Material.DARK_OAK_PLANKS);
            }
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                w.getBlockAt(tx + dx, by + 15, tz + dz).setType(Material.DARK_OAK_SLAB);
            }
        }
        w.getBlockAt(tx, by + 16, tz).setType(Material.LIGHTNING_ROD);
        // Ventanas de la torre
        for (int y = by + 3; y <= by + 11; y += 3) {
            w.getBlockAt(tx + 3, y, tz).setType(Material.GRAY_STAINED_GLASS_PANE);
            w.getBlockAt(tx - 3, y, tz).setType(Material.GRAY_STAINED_GLASS_PANE);
            w.getBlockAt(tx, y, tz + 3).setType(Material.GRAY_STAINED_GLASS_PANE);
            w.getBlockAt(tx, y, tz - 3).setType(Material.GRAY_STAINED_GLASS_PANE);
        }
        // Puerta
        for (int y = by + 1; y <= by + 3; y++) {
            w.getBlockAt(tx, y, tz + 3).setType(Material.AIR);
        }
        // Escalera espiral interior de la torre
        for (int y = by + 1; y <= by + 12; y++) {
            int step = (y - by - 1) % 4;
            int sx2 = step == 0 ? -2 : step == 1 ? 0 : step == 2 ? 2 : 0;
            int sz2 = step == 0 ? 0 : step == 1 ? -2 : step == 2 ? 0 : 2;
            w.getBlockAt(tx + sx2, y, tz + sz2).setType(Material.STONE_BRICK_STAIRS);
        }
        // Reloj (redstone lamp arriba como esfera)
        w.getBlockAt(tx, by + 11, tz + 3).setType(Material.REDSTONE_LAMP);
        w.getBlockAt(tx, by + 11, tz - 3).setType(Material.REDSTONE_LAMP);
        w.getBlockAt(tx + 3, by + 11, tz).setType(Material.REDSTONE_LAMP);
        w.getBlockAt(tx - 3, by + 11, tz).setType(Material.REDSTONE_LAMP);

        // === 2. IGLESIA / CAPILLA (NW, grande y alta) ===
        int cx2 = -28, cz2 = -22;
        // Nave principal 14x10
        for (int dx = 0; dx < 14; dx++) {
            for (int dz = 0; dz < 10; dz++) {
                // Suelo
                w.getBlockAt(cx2 + dx, by, cz2 + dz).setType(
                    (dx + dz) % 2 == 0 ? Material.POLISHED_GRANITE : Material.POLISHED_DIORITE);
                // Paredes
                boolean edge = dx == 0 || dx == 13 || dz == 0 || dz == 9;
                if (edge) {
                    for (int y = by + 1; y <= by + 8; y++) {
                        w.getBlockAt(cx2 + dx, y, cz2 + dz).setType(Material.STONE_BRICKS);
                    }
                }
                // Techo a dos aguas
                w.getBlockAt(cx2 + dx, by + 9, cz2 + dz).setType(Material.DARK_OAK_SLAB);
            }
        }
        // Techo triangular
        for (int layer = 0; layer < 5; layer++) {
            for (int dx = layer; dx < 14 - layer; dx++) {
                w.getBlockAt(cx2 + dx, by + 9 + layer, cz2 + layer).setType(Material.SPRUCE_STAIRS);
                w.getBlockAt(cx2 + dx, by + 9 + layer, cz2 + 9 - layer).setType(Material.SPRUCE_STAIRS);
            }
        }
        // Ventanales de colores
        Material[] stainedGlass = {Material.RED_STAINED_GLASS_PANE, Material.BLUE_STAINED_GLASS_PANE,
            Material.YELLOW_STAINED_GLASS_PANE, Material.PURPLE_STAINED_GLASS_PANE};
        for (int dx = 3; dx <= 11; dx += 4) {
            for (int y = by + 3; y <= by + 6; y++) {
                w.getBlockAt(cx2 + dx, y, cz2).setType(stainedGlass[(dx + y) % stainedGlass.length]);
                w.getBlockAt(cx2 + dx, y, cz2 + 9).setType(stainedGlass[(dx + y) % stainedGlass.length]);
            }
        }
        // Puerta principal
        for (int y = by + 1; y <= by + 4; y++) {
            w.getBlockAt(cx2 + 6, y, cz2).setType(Material.AIR);
            w.getBlockAt(cx2 + 7, y, cz2).setType(Material.AIR);
        }
        // Escalera interior de la iglesia (lateral, sube al campanario)
        for (int step = 0; step < 8; step++) {
            w.getBlockAt(cx2 + 1, by + 1 + step, cz2 + 1 + Math.min(step, 7)).setType(Material.STONE_BRICK_STAIRS);
            for (int y2 = by + 1; y2 < by + 1 + step; y2++) {
                w.getBlockAt(cx2 + 1, y2, cz2 + 1 + Math.min(step, 7)).setType(Material.STONE_BRICKS);
            }
        }
        // Campanario
        for (int y = by + 9; y <= by + 14; y++) {
            w.getBlockAt(cx2, y, cz2).setType(Material.STONE_BRICKS);
            w.getBlockAt(cx2 + 1, y, cz2).setType(Material.STONE_BRICKS);
            w.getBlockAt(cx2, y, cz2 + 1).setType(Material.STONE_BRICKS);
            w.getBlockAt(cx2 + 1, y, cz2 + 1).setType(Material.STONE_BRICKS);
        }
        w.getBlockAt(cx2, by + 15, cz2).setType(Material.STONE_BRICK_WALL);
        w.getBlockAt(cx2, by + 13, cz2).setType(Material.BELL);

        // === 3. TABERNA (SE, ancha y acogedora) ===
        int tavX = 15, tavZ = 15;
        // Edificio 12x10, altura 6
        for (int dx = 0; dx < 12; dx++) {
            for (int dz = 0; dz < 10; dz++) {
                w.getBlockAt(tavX + dx, by, tavZ + dz).setType(Material.DARK_OAK_PLANKS);
                boolean edge = dx == 0 || dx == 11 || dz == 0 || dz == 9;
                if (edge) {
                    for (int y = by + 1; y <= by + 5; y++) {
                        w.getBlockAt(tavX + dx, y, tavZ + dz).setType(
                            (dx == 0 || dx == 11) && (dz == 0 || dz == 9) ? Material.DARK_OAK_LOG : Material.SPRUCE_PLANKS);
                    }
                }
                w.getBlockAt(tavX + dx, by + 6, tavZ + dz).setType(Material.SPRUCE_SLAB);
            }
        }
        // Segundo piso (balcón que sobresale)
        for (int dx = -1; dx < 13; dx++) {
            for (int dz = -1; dz < 11; dz++) {
                if (dx < 0 || dx >= 12 || dz < 0 || dz >= 10) {
                    w.getBlockAt(tavX + dx, by + 3, tavZ + dz).setType(Material.DARK_OAK_PLANKS);
                }
            }
        }
        for (int dx = -1; dx < 13; dx++) {
            w.getBlockAt(tavX + dx, by + 4, tavZ - 1).setType(Material.DARK_OAK_FENCE);
            w.getBlockAt(tavX + dx, by + 4, tavZ + 10).setType(Material.DARK_OAK_FENCE);
        }
        // Puerta y ventanas
        for (int y = by + 1; y <= by + 3; y++) {
            w.getBlockAt(tavX + 5, y, tavZ).setType(Material.AIR);
            w.getBlockAt(tavX + 6, y, tavZ).setType(Material.AIR);
        }
        w.getBlockAt(tavX + 3, by + 2, tavZ).setType(Material.GLASS_PANE);
        w.getBlockAt(tavX + 8, by + 2, tavZ).setType(Material.GLASS_PANE);
        // Escalera interior taberna al segundo piso (balcón)
        for (int step = 0; step < 3; step++) {
            w.getBlockAt(tavX + 10, by + 1 + step, tavZ + 2 + step).setType(Material.SPRUCE_STAIRS);
            for (int y2 = by + 1; y2 < by + 1 + step; y2++) {
                w.getBlockAt(tavX + 10, y2, tavZ + 2 + step).setType(Material.SPRUCE_PLANKS);
            }
        }
        // Interior: bar
        for (int dx = 2; dx <= 9; dx++) {
            w.getBlockAt(tavX + dx, by + 1, tavZ + 7).setType(Material.SPRUCE_SLAB);
        }
        w.getBlockAt(tavX + 2, by + 1, tavZ + 8).setType(Material.BARREL);
        w.getBlockAt(tavX + 3, by + 1, tavZ + 8).setType(Material.BARREL);
        // Letrero de taberna
        w.getBlockAt(tavX + 5, by + 5, tavZ - 1).setType(Material.SPRUCE_WALL_SIGN);
        // Chimenea
        for (int y = by + 1; y <= by + 7; y++) {
            w.getBlockAt(tavX + 11, y, tavZ + 5).setType(Material.COBBLESTONE);
        }
        w.getBlockAt(tavX + 10, by + 1, tavZ + 5).setType(Material.CAMPFIRE);

        // === 4. HERRERÍA (SW, robusta con horno) ===
        int smX = -30, smZ = 18;
        for (int dx = 0; dx < 10; dx++) {
            for (int dz = 0; dz < 8; dz++) {
                w.getBlockAt(smX + dx, by, smZ + dz).setType(Material.COBBLESTONE);
                boolean edge = dx == 0 || dx == 9 || dz == 0 || dz == 7;
                if (edge) {
                    for (int y = by + 1; y <= by + 5; y++) {
                        w.getBlockAt(smX + dx, y, smZ + dz).setType(
                            (dx == 0 || dx == 9) && (dz == 0 || dz == 7) ? Material.STONE_BRICKS : Material.COBBLESTONE);
                    }
                }
                w.getBlockAt(smX + dx, by + 6, smZ + dz).setType(Material.STONE_BRICK_SLAB);
            }
        }
        // Puerta
        for (int y = by + 1; y <= by + 3; y++) {
            w.getBlockAt(smX + 5, y, smZ).setType(Material.AIR);
        }
        // Hornos y anvil
        w.getBlockAt(smX + 2, by + 1, smZ + 6).setType(Material.BLAST_FURNACE);
        w.getBlockAt(smX + 3, by + 1, smZ + 6).setType(Material.BLAST_FURNACE);
        w.getBlockAt(smX + 4, by + 1, smZ + 6).setType(Material.SMITHING_TABLE);
        w.getBlockAt(smX + 6, by + 1, smZ + 5).setType(Material.ANVIL);
        // Chimenea de piedra negra
        for (int y = by + 1; y <= by + 8; y++) {
            w.getBlockAt(smX + 8, y, smZ + 6).setType(Material.POLISHED_BLACKSTONE_BRICKS);
        }
        w.getBlockAt(smX + 8, by + 1, smZ + 5).setType(Material.LAVA);
        w.getBlockAt(smX + 7, by + 1, smZ + 5).setType(Material.POLISHED_BLACKSTONE);
        w.getBlockAt(smX + 7, by + 2, smZ + 5).setType(Material.POLISHED_BLACKSTONE);

        // === 5. MERCADO (puesto de telas y toldos, abierto) ===
        int[][] stallPositions = {{-10, -30}, {0, -30}, {10, -30}, {-10, 32}, {0, 32}, {10, 32}};
        Material[] woolColors = {Material.RED_WOOL, Material.YELLOW_WOOL, Material.BLUE_WOOL,
            Material.GREEN_WOOL, Material.ORANGE_WOOL, Material.PURPLE_WOOL};
        for (int i = 0; i < stallPositions.length; i++) {
            int sx = stallPositions[i][0], sz = stallPositions[i][1];
            // Mesa/mostrador
            for (int dx = -2; dx <= 2; dx++) {
                w.getBlockAt(sx + dx, by + 1, sz).setType(Material.OAK_SLAB);
                w.getBlockAt(sx + dx, by + 1, sz + 1).setType(Material.OAK_SLAB);
            }
            // Postes del toldo
            w.getBlockAt(sx - 2, by + 1, sz - 1).setType(Material.OAK_FENCE);
            w.getBlockAt(sx - 2, by + 2, sz - 1).setType(Material.OAK_FENCE);
            w.getBlockAt(sx - 2, by + 3, sz - 1).setType(Material.OAK_FENCE);
            w.getBlockAt(sx + 2, by + 1, sz - 1).setType(Material.OAK_FENCE);
            w.getBlockAt(sx + 2, by + 2, sz - 1).setType(Material.OAK_FENCE);
            w.getBlockAt(sx + 2, by + 3, sz - 1).setType(Material.OAK_FENCE);
            // Toldo de lana
            for (int dx = -2; dx <= 2; dx++) {
                w.getBlockAt(sx + dx, by + 4, sz - 1).setType(woolColors[i % woolColors.length]);
                w.getBlockAt(sx + dx, by + 4, sz).setType(woolColors[i % woolColors.length]);
                w.getBlockAt(sx + dx, by + 4, sz + 1).setType(woolColors[i % woolColors.length]);
            }
        }

        // === 6. CASA GRANDE DE MADERA (W, ancha 2 plantas) ===
        int hx = -28, hz = -5;
        for (int dx = 0; dx < 14; dx++) {
            for (int dz = 0; dz < 10; dz++) {
                w.getBlockAt(hx + dx, by, hz + dz).setType(Material.OAK_PLANKS);
                boolean edge = dx == 0 || dx == 13 || dz == 0 || dz == 9;
                if (edge) {
                    for (int y = by + 1; y <= by + 8; y++) {
                        boolean corner = (dx == 0 || dx == 13) && (dz == 0 || dz == 9);
                        w.getBlockAt(hx + dx, y, hz + dz).setType(corner ? Material.OAK_LOG : Material.OAK_PLANKS);
                    }
                }
                // Entrepiso
                w.getBlockAt(hx + dx, by + 4, hz + dz).setType(Material.OAK_PLANKS);
                // Techo
                w.getBlockAt(hx + dx, by + 9, hz + dz).setType(Material.SPRUCE_SLAB);
            }
        }
        // Puerta
        for (int y = by + 1; y <= by + 3; y++) {
            w.getBlockAt(hx + 7, y, hz).setType(Material.AIR);
        }
        // Ventanas P1 y P2
        for (int y : new int[]{by + 2, by + 6}) {
            w.getBlockAt(hx + 3, y, hz).setType(Material.GLASS_PANE);
            w.getBlockAt(hx + 10, y, hz).setType(Material.GLASS_PANE);
            w.getBlockAt(hx + 3, y, hz + 9).setType(Material.GLASS_PANE);
            w.getBlockAt(hx + 10, y, hz + 9).setType(Material.GLASS_PANE);
        }
        // Escalera interior
        for (int step = 0; step < 4; step++) {
            w.getBlockAt(hx + 1, by + 1 + step, hz + 1 + step).setType(Material.OAK_STAIRS);
            for (int y = by + 1; y < by + 1 + step; y++) {
                w.getBlockAt(hx + 1, y, hz + 1 + step).setType(Material.OAK_PLANKS);
            }
        }

        // === 7. PARQUE CON ÁRBOLES (E) ===
        for (int dx = 25; dx <= 38; dx++) {
            for (int dz = -5; dz <= 10; dz++) {
                w.getBlockAt(dx, by, dz).setType(Material.GRASS_BLOCK);
                if (RANDOM.nextInt(15) == 0) {
                    Material flower = RANDOM.nextBoolean() ? Material.POPPY : Material.DANDELION;
                    w.getBlockAt(dx, by + 1, dz).setType(flower);
                }
            }
        }
        // Árboles del parque (grandes y bonitos)
        buildBigTree(w, 28, by + 1, 0);
        buildBigTree(w, 35, by + 1, 5);
        buildBigTree(w, 30, by + 1, -3);
        // Bancos
        for (int bz = -3; bz <= 7; bz += 5) {
            w.getBlockAt(32, by + 1, bz).setType(Material.OAK_STAIRS);
            w.getBlockAt(33, by + 1, bz).setType(Material.OAK_STAIRS);
        }

        // === FAROLAS por los caminos ===
        for (int x = cx - 38; x <= cx + 38; x += 10) {
            for (int z = cz - 38; z <= cz + 38; z += 10) {
                if (w.getBlockAt(x, by + 1, z).getType() == Material.AIR
                    || w.getBlockAt(x, by + 1, z).getType().name().contains("CARPET")) {
                    w.getBlockAt(x, by + 1, z).setType(Material.COBBLESTONE_WALL);
                    w.getBlockAt(x, by + 2, z).setType(Material.COBBLESTONE_WALL);
                    w.getBlockAt(x, by + 3, z).setType(Material.COBBLESTONE_WALL);
                    w.getBlockAt(x, by + 4, z).setType(Material.LANTERN);
                }
            }
        }

        // === FLORES y detalles en los bordes de los caminos ===
        for (int x = cx - half + 3; x <= cx + half - 3; x += 3) {
            for (int z = cz - half + 3; z <= cz + half - 3; z += 3) {
                if (w.getBlockAt(x, by, z).getType() == Material.GRASS_BLOCK
                    && w.getBlockAt(x, by + 1, z).getType() == Material.AIR) {
                    if (RANDOM.nextInt(8) == 0) {
                        Material[] flowers = {Material.POPPY, Material.DANDELION, Material.CORNFLOWER,
                            Material.AZURE_BLUET, Material.OXEYE_DAISY, Material.ALLIUM};
                        w.getBlockAt(x, by + 1, z).setType(flowers[RANDOM.nextInt(flowers.length)]);
                    }
                }
            }
        }
    }

    private static void buildBigTree(World w, int x, int y, int z) {
        // Tronco grueso
        for (int dy = 0; dy < 7; dy++) {
            w.getBlockAt(x, y + dy, z).setType(Material.OAK_LOG);
            if (dy < 3) {
                w.getBlockAt(x + 1, y + dy, z).setType(Material.OAK_LOG);
                w.getBlockAt(x, y + dy, z + 1).setType(Material.OAK_LOG);
            }
        }
        // Copa frondosa
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int dy = 4; dy <= 7; dy++) {
                    double dist = Math.sqrt(dx * dx + dz * dz);
                    double maxR = dy < 6 ? 3.5 : 2.0;
                    if (dist <= maxR && !(dx == 0 && dz == 0 && dy < 7)) {
                        w.getBlockAt(x + dx, y + dy, z + dz).setType(Material.OAK_LEAVES);
                    }
                }
            }
        }
        w.getBlockAt(x, y + 8, z).setType(Material.OAK_LEAVES);
    }

    public static List<Location> getTNTTagSpawns(World w) {
        List<Location> spawns = new ArrayList<>();
        int y = 81;
        // Spawns en el camino principal (zona despejada, sobre suelo sólido)
        for (int i = 0; i < 24; i++) {
            double angle = (2 * Math.PI / 24) * i;
            int x = (int)(20 * Math.cos(angle));
            int z = (int)(20 * Math.sin(angle));
            spawns.add(new Location(w, x + 0.5, y, z + 0.5));
        }
        return spawns;
    }

    // =============================================
    // BLOCK PARTY - Suelo de colores
    // =============================================
    public static void buildBlockParty(World w) {
        int centerX = 0, centerZ = 0;
        int radius = 20;
        int baseY = 80;

        // Suelo de colores aleatorios
        Material[] colors = getBlockPartyColors();
        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                double dist = Math.sqrt((x - centerX) * (x - centerX) + (z - centerZ) * (z - centerZ));
                if (dist <= radius) {
                    Material color = colors[RANDOM.nextInt(colors.length)];
                    w.getBlockAt(x, baseY, z).setType(color);
                }
            }
        }

        // Paredes de cristal
        buildGlassWalls(w, centerX, centerZ, radius + 1, baseY - 2, baseY + 10, Material.WHITE_STAINED_GLASS);

        // Pilares decorativos con banners
        for (int angle = 0; angle < 360; angle += 45) {
            double rad = Math.toRadians(angle);
            int px = centerX + (int)((radius + 1) * Math.cos(rad));
            int pz = centerZ + (int)((radius + 1) * Math.sin(rad));
            for (int y = baseY - 2; y <= baseY + 10; y++) {
                w.getBlockAt(px, y, pz).setType(Material.QUARTZ_PILLAR);
            }
            w.getBlockAt(px, baseY + 11, pz).setType(Material.GLOWSTONE);
        }

        // Techo abierto con glowstone
        for (int x = centerX - radius; x <= centerX + radius; x += 4) {
            for (int z = centerZ - radius; z <= centerZ + radius; z += 4) {
                double dist = Math.sqrt((x - centerX) * (x - centerX) + (z - centerZ) * (z - centerZ));
                if (dist <= radius) {
                    w.getBlockAt(x, baseY + 12, z).setType(Material.GLOWSTONE);
                }
            }
        }
    }

    public static Material[] getBlockPartyColors() {
        return new Material[]{
            Material.RED_CONCRETE, Material.BLUE_CONCRETE, Material.GREEN_CONCRETE,
            Material.YELLOW_CONCRETE, Material.ORANGE_CONCRETE, Material.PURPLE_CONCRETE,
            Material.LIME_CONCRETE, Material.CYAN_CONCRETE, Material.PINK_CONCRETE,
            Material.WHITE_CONCRETE
        };
    }

    public static String getColorName(Material mat) {
        switch (mat) {
            case RED_CONCRETE: return "§c§lROJO";
            case BLUE_CONCRETE: return "§9§lAZUL";
            case GREEN_CONCRETE: return "§2§lVERDE";
            case YELLOW_CONCRETE: return "§e§lAMARILLO";
            case ORANGE_CONCRETE: return "§6§lNARANJA";
            case PURPLE_CONCRETE: return "§5§lMORADO";
            case LIME_CONCRETE: return "§a§lLIMA";
            case CYAN_CONCRETE: return "§3§lCIAN";
            case PINK_CONCRETE: return "§d§lROSA";
            case WHITE_CONCRETE: return "§f§lBLANCO";
            default: return "§7???";
        }
    }

    public static List<Location> getBlockPartySpawns(World w) {
        List<Location> spawns = new ArrayList<>();
        int y = 81;
        for (int i = 0; i < 24; i++) {
            double angle = (2 * Math.PI / 24) * i;
            int x = (int)(12 * Math.cos(angle));
            int z = (int)(12 * Math.sin(angle));
            spawns.add(new Location(w, x + 0.5, y, z + 0.5));
        }
        return spawns;
    }

    // =============================================
    // OITC - Arena urbana con cobertura
    // =============================================
    public static void buildOITC(World w) {
        int centerX = 0, centerZ = 0;
        int sizeX = 30, sizeZ = 30;
        int baseY = 80;

        // Suelo de piedra
        for (int x = centerX - sizeX; x <= centerX + sizeX; x++) {
            for (int z = centerZ - sizeZ; z <= centerZ + sizeZ; z++) {
                w.getBlockAt(x, baseY, z).setType(
                    (x + z) % 3 == 0 ? Material.POLISHED_DEEPSLATE : Material.DEEPSLATE_TILES);
                w.getBlockAt(x, baseY - 1, z).setType(Material.DEEPSLATE);
            }
        }

        // Paredes altas
        buildWalls(w, centerX, centerZ, sizeX, sizeZ, baseY, baseY + 6, Material.DEEPSLATE_BRICK_WALL, Material.DEEPSLATE_BRICKS);

        // Muros de cobertura en forma de cruz
        for (int i = -20; i <= 20; i++) {
            if (Math.abs(i) > 3) {
                for (int y = baseY + 1; y <= baseY + 2; y++) {
                    w.getBlockAt(i, y, 0).setType(Material.DEEPSLATE_BRICKS);
                    w.getBlockAt(0, y, i).setType(Material.DEEPSLATE_BRICKS);
                }
            }
        }

        // Plataformas elevadas en las esquinas
        int[][] corners = {{-20, -20}, {20, -20}, {-20, 20}, {20, 20}};
        for (int[] corner : corners) {
            for (int x = corner[0] - 3; x <= corner[0] + 3; x++) {
                for (int z = corner[1] - 3; z <= corner[1] + 3; z++) {
                    w.getBlockAt(x, baseY + 3, z).setType(Material.POLISHED_DEEPSLATE);
                }
            }
            // Escaleras
            w.getBlockAt(corner[0], baseY + 1, corner[1] + (corner[1] > 0 ? -4 : 4)).setType(Material.DEEPSLATE_BRICK_STAIRS);
            w.getBlockAt(corner[0], baseY + 2, corner[1] + (corner[1] > 0 ? -3 : 3)).setType(Material.DEEPSLATE_BRICK_STAIRS);
        }

        // Pilares con lámparas
        for (int x = -20; x <= 20; x += 10) {
            for (int z = -20; z <= 20; z += 10) {
                if (x != 0 || z != 0) {
                    for (int y = baseY + 1; y <= baseY + 4; y++) {
                        w.getBlockAt(x, y, z).setType(Material.POLISHED_DEEPSLATE);
                    }
                    w.getBlockAt(x, baseY + 5, z).setType(Material.LANTERN);
                }
            }
        }
    }

    public static List<Location> getOITCSpawns(World w) {
        List<Location> spawns = new ArrayList<>();
        int y = 81;
        spawns.add(new Location(w, -20.5, y, -20.5));
        spawns.add(new Location(w, 20.5, y, -20.5));
        spawns.add(new Location(w, -20.5, y, 20.5));
        spawns.add(new Location(w, 20.5, y, 20.5));
        spawns.add(new Location(w, 0.5, y, -25.5));
        spawns.add(new Location(w, 0.5, y, 25.5));
        spawns.add(new Location(w, -25.5, y, 0.5));
        spawns.add(new Location(w, 25.5, y, 0.5));
        for (int i = 0; i < 8; i++) {
            double angle = (2 * Math.PI / 8) * i;
            int x = (int)(15 * Math.cos(angle));
            int z = (int)(15 * Math.sin(angle));
            spawns.add(new Location(w, x + 0.5, y, z + 0.5));
        }
        return spawns;
    }

    // =============================================
    // SUMO - Plataforma pequeña circular en el vacío
    // =============================================
    public static void buildSumo(World w) {
        int centerX = 0, centerZ = 0;
        int radius = 12;
        int baseY = 100;

        // Plataforma circular de slime y piedra
        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                double dist = Math.sqrt((x - centerX) * (x - centerX) + (z - centerZ) * (z - centerZ));
                if (dist <= radius) {
                    if (dist <= 3) {
                        w.getBlockAt(x, baseY, z).setType(Material.GOLD_BLOCK);
                    } else if (dist <= radius - 2) {
                        w.getBlockAt(x, baseY, z).setType(
                            (x + z) % 2 == 0 ? Material.SANDSTONE : Material.RED_SANDSTONE);
                    } else {
                        w.getBlockAt(x, baseY, z).setType(Material.RED_CONCRETE);
                    }
                }
            }
        }

        // Pilares decorativos en el borde
        for (int angle = 0; angle < 360; angle += 90) {
            double rad = Math.toRadians(angle);
            int px = centerX + (int)((radius + 1) * Math.cos(rad));
            int pz = centerZ + (int)((radius + 1) * Math.sin(rad));
            for (int y = baseY; y <= baseY + 5; y++) {
                w.getBlockAt(px, y, pz).setType(Material.IRON_BARS);
            }
            w.getBlockAt(px, baseY + 6, pz).setType(Material.LANTERN);
        }
    }

    public static List<Location> getSumoSpawns(World w) {
        List<Location> spawns = new ArrayList<>();
        int y = 101;
        for (int i = 0; i < 16; i++) {
            double angle = (2 * Math.PI / 16) * i;
            int x = (int)(8 * Math.cos(angle));
            int z = (int)(8 * Math.sin(angle));
            spawns.add(new Location(w, x + 0.5, y, z + 0.5));
        }
        return spawns;
    }

    // =============================================
    // METEOR RAIN - Arena cerrada
    // =============================================
    public static void buildMeteorRain(World w) {
        int centerX = 0, centerZ = 0;
        int sizeX = 30, sizeZ = 30;
        int baseY = 80;

        // Suelo de nether bricks con patrones
        for (int x = centerX - sizeX; x <= centerX + sizeX; x++) {
            for (int z = centerZ - sizeZ; z <= centerZ + sizeZ; z++) {
                boolean isLava = (Math.abs(x % 10) == 0 && Math.abs(z % 10) == 0);
                w.getBlockAt(x, baseY, z).setType(isLava ? Material.MAGMA_BLOCK :
                    ((x + z) % 2 == 0 ? Material.NETHER_BRICKS : Material.RED_NETHER_BRICKS));
                w.getBlockAt(x, baseY - 1, z).setType(Material.NETHERRACK);
            }
        }

        // Paredes de nether bricks
        buildWalls(w, centerX, centerZ, sizeX, sizeZ, baseY, baseY + 20, Material.NETHER_BRICK_WALL, Material.NETHER_BRICKS);

        // Antorchas decorativas en las paredes (sin cobertura)
        int[][] torchPos = {{-15, -sizeZ}, {15, -sizeZ}, {-15, sizeZ}, {15, sizeZ}, {-sizeX, -15}, {-sizeX, 15}, {sizeX, -15}, {sizeX, 15}};
        for (int[] pos : torchPos) {
            w.getBlockAt(pos[0], baseY + 3, pos[1]).setType(Material.SHROOMLIGHT);
        }
    }

    public static List<Location> getMeteorRainSpawns(World w) {
        List<Location> spawns = new ArrayList<>();
        int y = 81;
        for (int i = 0; i < 24; i++) {
            double angle = (2 * Math.PI / 24) * i;
            int x = (int)(20 * Math.cos(angle));
            int z = (int)(20 * Math.sin(angle));
            spawns.add(new Location(w, x + 0.5, y, z + 0.5));
        }
        return spawns;
    }

    // =============================================
    // SNOWBALL FIGHT - Arena nevada con cobertura
    // =============================================
    public static void buildSnowballFight(World w) {
        int size = 25;
        int baseY = 80;

        // Suelo de nieve y hielo
        for (int x = -size; x <= size; x++) {
            for (int z = -size; z <= size; z++) {
                w.getBlockAt(x, baseY - 1, z).setType(Material.PACKED_ICE);
                if ((x + z) % 3 == 0) {
                    w.getBlockAt(x, baseY, z).setType(Material.SNOW_BLOCK);
                } else {
                    w.getBlockAt(x, baseY, z).setType(Material.POWDER_SNOW_CAULDRON);
                    w.getBlockAt(x, baseY, z).setType(Material.SNOW_BLOCK);
                }
            }
        }

        // Paredes de hielo (borde)
        for (int x = -size; x <= size; x++) {
            for (int y = baseY + 1; y <= baseY + 4; y++) {
                w.getBlockAt(x, y, -size).setType(Material.BLUE_ICE);
                w.getBlockAt(x, y, size).setType(Material.BLUE_ICE);
            }
        }
        for (int z = -size; z <= size; z++) {
            for (int y = baseY + 1; y <= baseY + 4; y++) {
                w.getBlockAt(-size, y, z).setType(Material.BLUE_ICE);
                w.getBlockAt(size, y, z).setType(Material.BLUE_ICE);
            }
        }

        // Muros de nieve como cobertura (barricadas)
        int[][] walls = {
            {-10, -5, -10, 5}, {10, -5, 10, 5},     // Muros laterales
            {-5, -12, 5, -12}, {-5, 12, 5, 12},      // Muros frontales
            {-15, 0, -8, 0}, {8, 0, 15, 0},           // Muros medios
            {0, -8, 0, -3}, {0, 3, 0, 8},             // Muros centrales
        };
        for (int[] wall : walls) {
            int x1 = Math.min(wall[0], wall[2]), x2 = Math.max(wall[0], wall[2]);
            int z1 = Math.min(wall[1], wall[3]), z2 = Math.max(wall[1], wall[3]);
            for (int x = x1; x <= x2; x++) {
                for (int z = z1; z <= z2; z++) {
                    for (int y = baseY + 1; y <= baseY + 2; y++) {
                        w.getBlockAt(x, y, z).setType(Material.SNOW_BLOCK);
                    }
                }
            }
        }

        // Torres de hielo en las esquinas
        int[][] towers = {{-18, -18}, {18, -18}, {-18, 18}, {18, 18}};
        for (int[] t : towers) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int y = baseY + 1; y <= baseY + 4; y++) {
                        w.getBlockAt(t[0] + dx, y, t[1] + dz).setType(Material.PACKED_ICE);
                    }
                    w.getBlockAt(t[0] + dx, baseY + 5, t[1] + dz).setType(Material.SNOW_BLOCK);
                }
            }
            // Escalera interior
            w.getBlockAt(t[0], baseY + 1, t[1]).setType(Material.SPRUCE_STAIRS);
            w.getBlockAt(t[0], baseY + 2, t[1]).setType(Material.SPRUCE_STAIRS);
        }

        // Iglú central (cobertura principal)
        for (int angle = 0; angle < 360; angle += 10) {
            double rad = Math.toRadians(angle);
            int x = (int)(4 * Math.cos(rad));
            int z = (int)(4 * Math.sin(rad));
            for (int y = baseY + 1; y <= baseY + 3; y++) {
                w.getBlockAt(x, y, z).setType(Material.SNOW_BLOCK);
            }
        }
        // Aberturas
        for (int y = baseY + 1; y <= baseY + 2; y++) {
            w.getBlockAt(4, y, 0).setType(Material.AIR);
            w.getBlockAt(-4, y, 0).setType(Material.AIR);
            w.getBlockAt(0, y, 4).setType(Material.AIR);
            w.getBlockAt(0, y, -4).setType(Material.AIR);
        }

        // Spruce trees decorativos
        int[][] trees = {{-6, -15}, {6, -15}, {-6, 15}, {6, 15}, {-15, -10}, {15, 10}};
        for (int[] tree : trees) {
            for (int y = baseY + 1; y <= baseY + 4; y++) {
                w.getBlockAt(tree[0], y, tree[1]).setType(Material.SPRUCE_LOG);
            }
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (Math.abs(dx) + Math.abs(dz) <= 3) {
                        w.getBlockAt(tree[0] + dx, baseY + 4, tree[1] + dz).setType(Material.SPRUCE_LEAVES);
                        if (Math.abs(dx) + Math.abs(dz) <= 2) {
                            w.getBlockAt(tree[0] + dx, baseY + 5, tree[1] + dz).setType(Material.SPRUCE_LEAVES);
                        }
                    }
                }
            }
            w.getBlockAt(tree[0], baseY + 6, tree[1]).setType(Material.SPRUCE_LEAVES);
        }
    }

    public static List<Location> getSnowballFightSpawns(World w) {
        List<Location> spawns = new ArrayList<>();
        int y = 82;
        for (int i = 0; i < 16; i++) {
            double angle = (2 * Math.PI / 16) * i;
            int x = (int)(15 * Math.cos(angle));
            int z = (int)(15 * Math.sin(angle));
            spawns.add(new Location(w, x + 0.5, y, z + 0.5));
        }
        return spawns;
    }

    // =============================================
    // INFECTION - Arena con lava dinámica
    // =============================================
    public static void buildInfection(World w) {
        int centerX = 0, centerZ = 0;
        int sizeX = 35, sizeZ = 35;
        int baseY = 80;

        // Suelo de tierra y hierba
        for (int x = centerX - sizeX; x <= centerX + sizeX; x++) {
            for (int z = centerZ - sizeZ; z <= centerZ + sizeZ; z++) {
                w.getBlockAt(x, baseY, z).setType(Material.GRASS_BLOCK);
                w.getBlockAt(x, baseY - 1, z).setType(Material.DIRT);
                w.getBlockAt(x, baseY - 2, z).setType(Material.STONE);
            }
        }

        // Obstáculos de madera y piedra
        for (int i = 0; i < 15; i++) {
            int wx = RANDOM.nextInt(50) - 25;
            int wz = RANDOM.nextInt(50) - 25;
            int len = 3 + RANDOM.nextInt(4);
            boolean horizontal = RANDOM.nextBoolean();
            Material mat = RANDOM.nextBoolean() ? Material.OAK_LOG : Material.COBBLESTONE;
            for (int j = 0; j < len; j++) {
                int bx = horizontal ? wx + j : wx;
                int bz = horizontal ? wz : wz + j;
                for (int y = baseY + 1; y <= baseY + 2; y++) {
                    w.getBlockAt(bx, y, bz).setType(mat);
                }
            }
        }

        // Paredes de la arena
        buildWalls(w, centerX, centerZ, sizeX, sizeZ, baseY, baseY + 5, Material.MOSSY_COBBLESTONE_WALL, Material.MOSSY_COBBLESTONE);

        // Árboles
        for (int i = 0; i < 8; i++) {
            int tx = RANDOM.nextInt(40) - 20;
            int tz = RANDOM.nextInt(40) - 20;
            buildTree(w, tx, baseY + 1, tz);
        }

        // Antorchas de alma
        for (int x = -30; x <= 30; x += 8) {
            for (int z = -30; z <= 30; z += 8) {
                w.getBlockAt(x, baseY + 1, z).setType(Material.SOUL_LANTERN);
            }
        }
    }

    public static List<Location> getInfectionSpawns(World w) {
        List<Location> spawns = new ArrayList<>();
        int y = 81;
        for (int i = 0; i < 24; i++) {
            double angle = (2 * Math.PI / 24) * i;
            int x = (int)(25 * Math.cos(angle));
            int z = (int)(25 * Math.sin(angle));
            spawns.add(new Location(w, x + 0.5, y, z + 0.5));
        }
        return spawns;
    }

    // =============================================
    // HELPERS
    // =============================================

    private static void buildGlassWalls(World w, int cx, int cz, int radius, int yMin, int yMax, Material glass) {
        for (int angle = 0; angle < 360; angle++) {
            double rad = Math.toRadians(angle);
            int x = cx + (int)(radius * Math.cos(rad));
            int z = cz + (int)(radius * Math.sin(rad));
            for (int y = yMin; y <= yMax; y++) {
                if (w.getBlockAt(x, y, z).getType() == Material.AIR) {
                    w.getBlockAt(x, y, z).setType(glass);
                }
            }
        }
    }

    // =============================================
    // MUSICAL CHAIRS - Arena circular con sillas
    // =============================================
    public static void buildMusicalChairs(World w) {
        int centerX = 0, centerZ = 0;
        int radius = 18;
        int baseY = 80;

        // Suelo de madera con patrón decorativo
        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                double dist = Math.sqrt((x - centerX) * (x - centerX) + (z - centerZ) * (z - centerZ));
                if (dist <= radius) {
                    if (dist <= 3) {
                        w.getBlockAt(x, baseY, z).setType(Material.GOLD_BLOCK);
                    } else if ((x + z) % 2 == 0) {
                        w.getBlockAt(x, baseY, z).setType(Material.OAK_PLANKS);
                    } else {
                        w.getBlockAt(x, baseY, z).setType(Material.SPRUCE_PLANKS);
                    }
                    w.getBlockAt(x, baseY - 1, z).setType(Material.DARK_OAK_PLANKS);
                }
            }
        }

        // Paredes de cristal con colores
        Material[] glassColors = {
            Material.RED_STAINED_GLASS, Material.ORANGE_STAINED_GLASS,
            Material.YELLOW_STAINED_GLASS, Material.LIME_STAINED_GLASS,
            Material.LIGHT_BLUE_STAINED_GLASS, Material.MAGENTA_STAINED_GLASS
        };
        for (int angle = 0; angle < 360; angle++) {
            double rad = Math.toRadians(angle);
            int x = centerX + (int)((radius + 1) * Math.cos(rad));
            int z = centerZ + (int)((radius + 1) * Math.sin(rad));
            for (int y = baseY; y <= baseY + 5; y++) {
                Material glass = glassColors[(angle / 15) % glassColors.length];
                w.getBlockAt(x, y, z).setType(glass);
            }
        }

        // Jukebox central decorativa
        w.getBlockAt(centerX, baseY + 1, centerZ).setType(Material.JUKEBOX);
        w.getBlockAt(centerX, baseY + 2, centerZ).setType(Material.NOTE_BLOCK);

        // Glowstone en el techo
        for (int angle = 0; angle < 360; angle += 30) {
            double rad = Math.toRadians(angle);
            int x = centerX + (int)(8 * Math.cos(rad));
            int z = centerZ + (int)(8 * Math.sin(rad));
            w.getBlockAt(x, baseY + 6, z).setType(Material.GLOWSTONE);
        }
    }

    public static List<Location> getMusicalChairsSpawns(World w) {
        List<Location> spawns = new ArrayList<>();
        int y = 81;
        for (int i = 0; i < 24; i++) {
            double angle = (2 * Math.PI / 24) * i;
            int x = (int)(12 * Math.cos(angle));
            int z = (int)(12 * Math.sin(angle));
            spawns.add(new Location(w, x + 0.5, y, z + 0.5));
        }
        return spawns;
    }

    /**
     * Obtiene posiciones para las sillas (vagonetas) en un c\u00edrculo.
     * @param chairCount N\u00famero de sillas a generar
     */
    public static List<Location> getChairPositions(World w, int chairCount) {
        List<Location> chairs = new ArrayList<>();
        int y = 81;
        double chairRadius = 8.0;
        for (int i = 0; i < chairCount; i++) {
            double angle = (2 * Math.PI / chairCount) * i;
            double x = chairRadius * Math.cos(angle);
            double z = chairRadius * Math.sin(angle);
            chairs.add(new Location(w, x + 0.5, y, z + 0.5));
        }
        return chairs;
    }

    private static void buildWalls(World w, int cx, int cz, int sizeX, int sizeZ, int yMin, int yMax, Material wallMat, Material baseMat) {
        for (int x = cx - sizeX; x <= cx + sizeX; x++) {
            for (int y = yMin; y <= yMax; y++) {
                w.getBlockAt(x, y, cz - sizeZ).setType(y == yMin ? baseMat : wallMat);
                w.getBlockAt(x, y, cz + sizeZ).setType(y == yMin ? baseMat : wallMat);
            }
        }
        for (int z = cz - sizeZ; z <= cz + sizeZ; z++) {
            for (int y = yMin; y <= yMax; y++) {
                w.getBlockAt(cx - sizeX, y, z).setType(y == yMin ? baseMat : wallMat);
                w.getBlockAt(cx + sizeX, y, z).setType(y == yMin ? baseMat : wallMat);
            }
        }
    }

    private static void buildHouse(World w, int x, int y, int z, Material walls, Material logs) {
        int sizeX = 5, sizeZ = 5, height = 4;
        
        // Suelo
        for (int dx = 0; dx < sizeX; dx++) {
            for (int dz = 0; dz < sizeZ; dz++) {
                w.getBlockAt(x + dx, y - 1, z + dz).setType(walls);
            }
        }
        
        // Paredes
        for (int dy = 0; dy < height; dy++) {
            for (int dx = 0; dx < sizeX; dx++) {
                w.getBlockAt(x + dx, y + dy, z).setType(dx == 0 || dx == sizeX - 1 ? logs : walls);
                w.getBlockAt(x + dx, y + dy, z + sizeZ - 1).setType(dx == 0 || dx == sizeX - 1 ? logs : walls);
            }
            for (int dz = 1; dz < sizeZ - 1; dz++) {
                w.getBlockAt(x, y + dy, z + dz).setType(logs);
                w.getBlockAt(x + sizeX - 1, y + dy, z + dz).setType(logs);
            }
        }
        
        // Puerta (hueco)
        w.getBlockAt(x + sizeX / 2, y, z).setType(Material.AIR);
        w.getBlockAt(x + sizeX / 2, y + 1, z).setType(Material.AIR);
        
        // Techo plano con escaleras
        for (int dx = 0; dx < sizeX; dx++) {
            for (int dz = 0; dz < sizeZ; dz++) {
                w.getBlockAt(x + dx, y + height, z + dz).setType(Material.OAK_SLAB);
            }
        }
        
        // Ventana
        w.getBlockAt(x + sizeX / 2, y + 1, z + sizeZ - 1).setType(Material.GLASS_PANE);
        w.getBlockAt(x + sizeX / 2, y + 2, z + sizeZ - 1).setType(Material.GLASS_PANE);
    }

    private static void buildFountain(World w, int x, int y, int z) {
        // Base de piedra
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                w.getBlockAt(x + dx, y, z + dz).setType(Material.STONE_BRICKS);
            }
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                w.getBlockAt(x + dx, y + 1, z + dz).setType(Material.WATER);
            }
        }
        // Pilar central
        w.getBlockAt(x, y + 1, z).setType(Material.STONE_BRICK_WALL);
        w.getBlockAt(x, y + 2, z).setType(Material.STONE_BRICK_WALL);
        w.getBlockAt(x, y + 3, z).setType(Material.WATER);
    }

    private static void buildTree(World w, int x, int y, int z) {
        // Tronco
        for (int dy = 0; dy < 5; dy++) {
            w.getBlockAt(x, y + dy, z).setType(Material.OAK_LOG);
        }
        // Hojas
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = 3; dy <= 5; dy++) {
                    if (dx == 0 && dz == 0) continue;
                    if (Math.abs(dx) == 2 && Math.abs(dz) == 2 && dy == 5) continue;
                    w.getBlockAt(x + dx, y + dy, z + dz).setType(Material.OAK_LEAVES);
                }
            }
        }
        w.getBlockAt(x, y + 6, z).setType(Material.OAK_LEAVES);
    }

    // =============================================
    // MURDER MYSTERY - Mansión Victoriana Encantada (2 Plantas)
    // =============================================
    public static void buildMurderMystery(World w) {
        org.bukkit.Bukkit.getLogger().info("[MiniGames] Iniciando construcción de Murder Mystery arena...");
        int cx = 0, cz = 0;
        int halfW = 45, halfL = 45; // 90x90
        int f1 = 80; // Planta baja
        int f2 = f1 + 5; // Planta alta (separada 5 bloques)
        int ceilingH = 4; // Altura interior de cada planta

        // ====== ESTRUCTURA BASE: Limpiar + Cimientos ======
        org.bukkit.Bukkit.getLogger().info("[MiniGames] Construyendo estructura base...");
        for (int x = cx - halfW; x <= cx + halfW; x++) {
            for (int z = cz - halfL; z <= cz + halfL; z++) {
                w.getBlockAt(x, f1 - 1, z).setType(Material.POLISHED_BLACKSTONE);
                // P1 suelo
                Material floor1 = ((x + z) % 2 == 0) ? Material.DARK_OAK_PLANKS : Material.SPRUCE_PLANKS;
                w.getBlockAt(x, f1, z).setType(floor1);
                // P1 aire
                for (int y = f1 + 1; y <= f1 + ceilingH; y++) w.getBlockAt(x, y, z).setType(Material.AIR);
                // Entrepiso (techo P1 / suelo P2)
                w.getBlockAt(x, f2, z).setType(Material.DARK_OAK_PLANKS);
                // P2 aire
                for (int y = f2 + 1; y <= f2 + ceilingH; y++) w.getBlockAt(x, y, z).setType(Material.AIR);
                // Techo final
                w.getBlockAt(x, f2 + ceilingH + 1, z).setType(
                    (Math.abs(x) + Math.abs(z)) % 3 == 0 ? Material.DARK_OAK_SLAB : Material.SPRUCE_SLAB);
            }
        }

        // ====== PAREDES EXTERIORES (ambas plantas) ======
        for (int floor : new int[]{f1, f2}) {
            for (int x = cx - halfW; x <= cx + halfW; x++) {
                for (int y = floor + 1; y <= floor + ceilingH; y++) {
                    Material wm = (y == floor + ceilingH) ? Material.DARK_OAK_LOG : Material.STRIPPED_DARK_OAK_WOOD;
                    w.getBlockAt(x, y, cz - halfL).setType(wm);
                    w.getBlockAt(x, y, cz + halfL).setType(wm);
                }
            }
            for (int z = cz - halfL; z <= cz + halfL; z++) {
                for (int y = floor + 1; y <= floor + ceilingH; y++) {
                    Material wm = (y == floor + ceilingH) ? Material.DARK_OAK_LOG : Material.STRIPPED_DARK_OAK_WOOD;
                    w.getBlockAt(cx - halfW, y, z).setType(wm);
                    w.getBlockAt(cx + halfW, y, z).setType(wm);
                }
            }
        }

        // ====== VENTANAS EN PAREDES EXTERIORES ======
        for (int floor : new int[]{f1, f2}) {
            for (int x = cx - halfW + 5; x <= cx + halfW - 5; x += 8) {
                w.getBlockAt(x, floor + 2, cz - halfL).setType(Material.GRAY_STAINED_GLASS);
                w.getBlockAt(x, floor + 3, cz - halfL).setType(Material.GRAY_STAINED_GLASS);
                w.getBlockAt(x, floor + 2, cz + halfL).setType(Material.GRAY_STAINED_GLASS);
                w.getBlockAt(x, floor + 3, cz + halfL).setType(Material.GRAY_STAINED_GLASS);
            }
            for (int z = cz - halfL + 5; z <= cz + halfL - 5; z += 8) {
                w.getBlockAt(cx - halfW, floor + 2, z).setType(Material.GRAY_STAINED_GLASS);
                w.getBlockAt(cx - halfW, floor + 3, z).setType(Material.GRAY_STAINED_GLASS);
                w.getBlockAt(cx + halfW, floor + 2, z).setType(Material.GRAY_STAINED_GLASS);
                w.getBlockAt(cx + halfW, floor + 3, z).setType(Material.GRAY_STAINED_GLASS);
            }
        }

        // ====== PLANTA 1: HABITACIONES ======
        // Pasillo central horizontal (ancho 4, de este a oeste)
        for (int x = cx - halfW + 1; x <= cx + halfW - 1; x++) {
            for (int z = cz - 2; z <= cz + 2; z++) {
                w.getBlockAt(x, f1, z).setType(Material.POLISHED_ANDESITE);
                // Alfombra roja en el centro del pasillo
                if (z == cz) w.getBlockAt(x, f1 + 1, z).setType(Material.RED_CARPET);
            }
        }
        // Pasillo central vertical
        for (int z = cz - halfL + 1; z <= cz + halfL - 1; z++) {
            for (int x = cx - 2; x <= cx + 2; x++) {
                w.getBlockAt(x, f1, z).setType(Material.POLISHED_ANDESITE);
                if (x == cx) w.getBlockAt(x, f1 + 1, z).setType(Material.RED_CARPET);
            }
        }

        // Paredes de los pasillos P1
        buildHallWalls(w, cx - halfW + 1, cx + halfW - 1, cz - 3, f1, ceilingH, Material.STRIPPED_OAK_WOOD);
        buildHallWalls(w, cx - halfW + 1, cx + halfW - 1, cz + 3, f1, ceilingH, Material.STRIPPED_OAK_WOOD);
        buildHallWallsZ(w, cz - halfL + 1, cz + halfL - 1, cx - 3, f1, ceilingH, Material.STRIPPED_OAK_WOOD);
        buildHallWallsZ(w, cz - halfL + 1, cz + halfL - 1, cx + 3, f1, ceilingH, Material.STRIPPED_OAK_WOOD);

        // Puertas en pasillos P1 cada 12 bloques
        for (int x = cx - halfW + 8; x <= cx + halfW - 8; x += 12) {
            for (int y = f1 + 1; y <= f1 + 3; y++) {
                w.getBlockAt(x, y, cz - 3).setType(Material.AIR);
                w.getBlockAt(x, y, cz + 3).setType(Material.AIR);
            }
        }
        for (int z = cz - halfL + 8; z <= cz + halfL - 8; z += 12) {
            for (int y = f1 + 1; y <= f1 + 3; y++) {
                w.getBlockAt(cx - 3, y, z).setType(Material.AIR);
                w.getBlockAt(cx + 3, y, z).setType(Material.AIR);
            }
        }

        // ====== P1 HABITACIONES TEMÁTICAS ======
        // 1. Gran Salón (esquina NE: x=5..40, z=-40..-5)
        buildRoomFloor(w, 5, -40, 40, -5, f1, Material.POLISHED_GRANITE);
        // Chimenea gigante en la pared norte
        for (int x = 15; x <= 25; x++) {
            for (int y = f1 + 1; y <= f1 + 4; y++) {
                w.getBlockAt(x, y, -39).setType(Material.POLISHED_BLACKSTONE_BRICKS);
            }
        }
        for (int x = 17; x <= 23; x++) {
            w.getBlockAt(x, f1 + 1, -39).setType(Material.CAMPFIRE);
            w.getBlockAt(x, f1 + 2, -39).setType(Material.AIR);
        }
        // Sofás (escaleras)
        for (int x = 10; x <= 20; x += 5) {
            for (int z = -25; z <= -15; z += 5) {
                w.getBlockAt(x, f1 + 1, z).setType(Material.SPRUCE_STAIRS);
            }
        }
        // Candelabros
        for (int x = 10; x <= 35; x += 8) {
            w.getBlockAt(x, f1 + ceilingH, -22).setType(Material.IRON_BARS);
            w.getBlockAt(x, f1 + ceilingH - 1, -22).setType(Material.LANTERN);
        }

        // 2. Cocina (esquina NW: x=-40..-5, z=-40..-5)
        buildRoomFloor(w, -40, -40, -5, -5, f1, Material.SMOOTH_STONE);
        for (int x = -38; x <= -10; x += 4) {
            w.getBlockAt(x, f1 + 1, -38).setType(Material.FURNACE);
            w.getBlockAt(x + 1, f1 + 1, -38).setType(Material.SMOKER);
            w.getBlockAt(x + 2, f1 + 1, -38).setType(Material.CRAFTING_TABLE);
        }
        // Mesa de cocina central
        for (int x = -30; x <= -15; x++) {
            w.getBlockAt(x, f1 + 1, -22).setType(Material.SMOOTH_STONE_SLAB);
        }
        // Barriles y estanterías
        for (int z = -35; z <= -10; z += 5) {
            w.getBlockAt(-38, f1 + 1, z).setType(Material.BARREL);
            w.getBlockAt(-38, f1 + 2, z).setType(Material.BARREL);
        }
        w.getBlockAt(-22, f1 + 1, -35).setType(Material.CAULDRON);
        w.getBlockAt(-20, f1 + 1, -35).setType(Material.BREWING_STAND);

        // 3. Biblioteca (esquina SE: x=5..40, z=5..40)
        buildRoomFloor(w, 5, 5, 40, 40, f1, Material.DARK_OAK_PLANKS);
        // Estanterías enormes
        for (int x = 7; x <= 38; x += 4) {
            for (int z = 7; z <= 38; z++) {
                if (z % 6 < 4) {
                    for (int y = f1 + 1; y <= f1 + 3; y++) {
                        w.getBlockAt(x, y, z).setType(Material.BOOKSHELF);
                    }
                }
            }
        }
        // Mesa de lectura
        w.getBlockAt(22, f1 + 1, 22).setType(Material.LECTERN);
        w.getBlockAt(20, f1 + 1, 22).setType(Material.ENCHANTING_TABLE);
        // Sillas alrededor
        for (int dx = -1; dx <= 1; dx += 2) {
            for (int dz = -1; dz <= 1; dz += 2) {
                w.getBlockAt(22 + dx * 2, f1 + 1, 22 + dz * 2).setType(Material.DARK_OAK_STAIRS);
            }
        }

        // 4. Comedor (esquina SW: x=-40..-5, z=5..40)
        buildRoomFloor(w, -40, 5, -5, 40, f1, Material.CRIMSON_PLANKS);
        // Mesa larga
        for (int x = -35; x <= -10; x++) {
            w.getBlockAt(x, f1 + 1, 22).setType(Material.DARK_OAK_SLAB);
            if (x % 3 == 0) {
                w.getBlockAt(x, f1 + 1, 21).setType(Material.DARK_OAK_STAIRS);
                w.getBlockAt(x, f1 + 1, 23).setType(Material.DARK_OAK_STAIRS);
            }
        }
        // Candelabros sobre mesa
        for (int x = -33; x <= -12; x += 7) {
            w.getBlockAt(x, f1 + ceilingH, 22).setType(Material.IRON_BARS);
            w.getBlockAt(x, f1 + ceilingH - 1, 22).setType(Material.LANTERN);
        }
        // Vitrina con platos
        for (int z = 8; z <= 15; z++) {
            w.getBlockAt(-38, f1 + 1, z).setType(Material.DARK_OAK_SLAB);
            w.getBlockAt(-38, f1 + 2, z).setType(Material.FLOWER_POT);
        }

        // ====== ESCALERAS CENTRALES (conectan P1 y P2) ======
        // Escalera principal en el vestíbulo central
        for (int step = 0; step < 5; step++) {
            for (int x = cx - 2; x <= cx + 2; x++) {
                w.getBlockAt(x, f1 + 1 + step, cz - 6 - step).setType(Material.DARK_OAK_STAIRS);
                // Relleno debajo
                for (int y = f1 + 1; y < f1 + 1 + step; y++) {
                    w.getBlockAt(x, y, cz - 6 - step).setType(Material.DARK_OAK_PLANKS);
                }
            }
        }
        // Abrir hueco en el entrepiso para la escalera
        for (int x = cx - 3; x <= cx + 3; x++) {
            for (int z = cz - 12; z <= cz - 5; z++) {
                w.getBlockAt(x, f2, z).setType(Material.AIR);
            }
        }
        // Barandilla
        for (int z = cz - 12; z <= cz - 5; z++) {
            w.getBlockAt(cx - 3, f2 + 1, z).setType(Material.DARK_OAK_FENCE);
            w.getBlockAt(cx + 3, f2 + 1, z).setType(Material.DARK_OAK_FENCE);
        }

        // Segunda escalera trasera (esquina sur)
        for (int step = 0; step < 5; step++) {
            for (int x = cx + 30; x <= cx + 34; x++) {
                w.getBlockAt(x, f1 + 1 + step, cz + 30 + step).setType(Material.SPRUCE_STAIRS);
                for (int y = f1 + 1; y < f1 + 1 + step; y++) {
                    w.getBlockAt(x, y, cz + 30 + step).setType(Material.SPRUCE_PLANKS);
                }
            }
        }
        for (int x = cx + 29; x <= cx + 35; x++) {
            for (int z = cz + 29; z <= cz + 36; z++) {
                w.getBlockAt(x, f2, z).setType(Material.AIR);
            }
        }

        // ====== PLANTA 2: HABITACIONES ======
        // P2 pasillo central
        for (int x = cx - halfW + 1; x <= cx + halfW - 1; x++) {
            for (int z = cz - 2; z <= cz + 2; z++) {
                w.getBlockAt(x, f2, z).setType(Material.POLISHED_ANDESITE);
                if (z == cz) w.getBlockAt(x, f2 + 1, z).setType(Material.PURPLE_CARPET);
            }
        }
        // Paredes pasillo P2
        buildHallWalls(w, cx - halfW + 1, cx + halfW - 1, cz - 3, f2, ceilingH, Material.STRIPPED_SPRUCE_WOOD);
        buildHallWalls(w, cx - halfW + 1, cx + halfW - 1, cz + 3, f2, ceilingH, Material.STRIPPED_SPRUCE_WOOD);
        // Puertas P2
        for (int x = cx - halfW + 8; x <= cx + halfW - 8; x += 12) {
            for (int y = f2 + 1; y <= f2 + 3; y++) {
                w.getBlockAt(x, y, cz - 3).setType(Material.AIR);
                w.getBlockAt(x, y, cz + 3).setType(Material.AIR);
            }
        }

        // 5. Dormitorio Master (P2 NE)
        buildRoomFloor(w, 5, -40, 40, -5, f2, Material.CRIMSON_PLANKS);
        // Cama (usar bloques de lana roja como decoración)
        w.getBlockAt(20, f2 + 1, -30).setType(Material.RED_WOOL);
        w.getBlockAt(21, f2 + 1, -30).setType(Material.RED_WOOL);
        for (int dx = 18; dx <= 23; dx++) {
            for (int dz = -33; dz <= -28; dz++) {
                w.getBlockAt(dx, f2 + 1, dz).setType(Material.RED_CARPET);
            }
        }
        w.getBlockAt(15, f2 + 1, -35).setType(Material.CHEST);
        // Armor stand reemplazado por bloque decorativo
        w.getBlockAt(30, f2 + 1, -35).setType(Material.SKELETON_SKULL);
        // Tocador
        w.getBlockAt(35, f2 + 1, -10).setType(Material.CARTOGRAPHY_TABLE);

        // 6. Estudio/Oficina (P2 NW)
        buildRoomFloor(w, -40, -40, -5, -5, f2, Material.SPRUCE_PLANKS);
        // Escritorio
        for (int x = -30; x <= -20; x++) {
            w.getBlockAt(x, f2 + 1, -25).setType(Material.DARK_OAK_SLAB);
        }
        w.getBlockAt(-25, f2 + 1, -25).setType(Material.LECTERN);
        w.getBlockAt(-25, f2 + 1, -27).setType(Material.DARK_OAK_STAIRS);
        // Cuadros decorativos (bloques de lana pintados)
        for (int z = -38; z <= -30; z += 3) {
            w.getBlockAt(-39, f2 + 2, z).setType(Material.BROWN_WOOL);
        }
        // Estanterías
        for (int z = -38; z <= -10; z += 6) {
            for (int y = f2 + 1; y <= f2 + 3; y++) {
                w.getBlockAt(-8, y, z).setType(Material.BOOKSHELF);
            }
        }

        // 7. Sala de Juegos (P2 SE)
        buildRoomFloor(w, 5, 5, 40, 40, f2, Material.OAK_PLANKS);
        // Billar (mesa verde)
        for (int x = 15; x <= 30; x++) {
            for (int z = 15; z <= 25; z++) {
                w.getBlockAt(x, f2 + 1, z).setType(Material.GREEN_WOOL);
            }
        }
        // Gramófono decorativo
        w.getBlockAt(10, f2 + 1, 10).setType(Material.NOTE_BLOCK);
        w.getBlockAt(10, f2 + 2, 10).setType(Material.LIGHTNING_ROD);
        // Alfombra persa
        for (int x = 8; x <= 38; x++) {
            for (int z = 30; z <= 38; z++) {
                if ((x + z) % 2 == 0) w.getBlockAt(x, f2 + 1, z).setType(Material.ORANGE_CARPET);
            }
        }

        // 8. Baño victoriano (P2 SW)
        buildRoomFloor(w, -40, 5, -5, 40, f2, Material.WHITE_CONCRETE);
        w.getBlockAt(-30, f2 + 1, 20).setType(Material.CAULDRON);
        w.getBlockAt(-25, f2 + 1, 20).setType(Material.CAULDRON);
        w.getBlockAt(-20, f2 + 1, 15).setType(Material.BREWING_STAND);
        // Espejo (cristal)
        for (int z = 10; z <= 15; z++) {
            w.getBlockAt(-38, f2 + 2, z).setType(Material.GLASS);
            w.getBlockAt(-38, f2 + 3, z).setType(Material.GLASS);
        }

        // ====== ILUMINACIÓN TENUE Y ATMOSFÉRICA (ambas plantas) ======
        for (int floor : new int[]{f1, f2}) {
            for (int x = cx - halfW + 4; x <= cx + halfW - 4; x += 6) {
                for (int z = cz - halfL + 4; z <= cz + halfL - 4; z += 6) {
                    if (w.getBlockAt(x, floor + 1, z).getType() == Material.AIR) {
                        if (RANDOM.nextInt(3) == 0) {
                            w.getBlockAt(x, floor + ceilingH, z).setType(Material.IRON_BARS);
                            w.getBlockAt(x, floor + ceilingH - 1, z).setType(
                                RANDOM.nextBoolean() ? Material.LANTERN : Material.SOUL_LANTERN);
                        }
                    }
                }
            }
        }

        // ====== DECORACIÓN DISPERSA: telarañas, cuadros, alfombras ======
        for (int floor : new int[]{f1, f2}) {
            for (int x = cx - halfW + 2; x <= cx + halfW - 2; x += 4) {
                for (int z = cz - halfL + 2; z <= cz + halfL - 2; z += 4) {
                    if (RANDOM.nextInt(12) == 0 && w.getBlockAt(x, floor + ceilingH, z).getType() != Material.AIR) {
                        w.getBlockAt(x, floor + ceilingH - 1, z).setType(Material.COBWEB);
                    }
                    if (RANDOM.nextInt(15) == 0 && w.getBlockAt(x, floor + 1, z).getType() == Material.AIR) {
                        Material[] carpets = {Material.RED_CARPET, Material.BROWN_CARPET, Material.PURPLE_CARPET, Material.GRAY_CARPET};
                        w.getBlockAt(x, floor + 1, z).setType(carpets[RANDOM.nextInt(carpets.length)]);
                    }
                }
            }
        }

        // ====== JARDÍN EXTERIOR (borde oeste, fuera de la mansión) ======
        for (int z = cz - 20; z <= cz + 20; z++) {
            for (int x = cx - halfW - 15; x <= cx - halfW - 1; x++) {
                w.getBlockAt(x, f1, z).setType(RANDOM.nextBoolean() ? Material.GRASS_BLOCK : Material.PODZOL);
                if (RANDOM.nextInt(8) == 0) {
                    w.getBlockAt(x, f1 + 1, z).setType(Material.DEAD_BUSH);
                }
            }
        }
        // Camino de grava
        for (int z = cz - 15; z <= cz + 15; z++) {
            w.getBlockAt(cx - halfW - 5, f1, z).setType(Material.GRAVEL);
            w.getBlockAt(cx - halfW - 6, f1, z).setType(Material.GRAVEL);
        }

        // ====== SÓTANO secreto (debajo del vestíbulo) ======
        int basementY = f1 - 5;
        for (int x = cx - 10; x <= cx + 10; x++) {
            for (int z = cz + 10; z <= cz + 30; z++) {
                w.getBlockAt(x, basementY, z).setType(Material.COBBLESTONE);
                for (int y = basementY + 1; y <= basementY + 3; y++) {
                    w.getBlockAt(x, y, z).setType(Material.AIR);
                }
                w.getBlockAt(x, basementY + 4, z).setType(Material.STONE_BRICKS);
            }
        }
        // Paredes del sótano
        for (int x = cx - 10; x <= cx + 10; x++) {
            for (int y = basementY + 1; y <= basementY + 3; y++) {
                w.getBlockAt(x, y, cz + 10).setType(Material.MOSSY_STONE_BRICKS);
                w.getBlockAt(x, y, cz + 30).setType(Material.MOSSY_STONE_BRICKS);
            }
        }
        for (int z = cz + 10; z <= cz + 30; z++) {
            for (int y = basementY + 1; y <= basementY + 3; y++) {
                w.getBlockAt(cx - 10, y, z).setType(Material.MOSSY_STONE_BRICKS);
                w.getBlockAt(cx + 10, y, z).setType(Material.MOSSY_STONE_BRICKS);
            }
        }
        // Escalera de bajada al sótano
        for (int step = 0; step < 5; step++) {
            w.getBlockAt(cx, f1 - step, cz + 10 + step).setType(Material.COBBLESTONE_STAIRS);
            w.getBlockAt(cx + 1, f1 - step, cz + 10 + step).setType(Material.COBBLESTONE_STAIRS);
            // Abrir hueco en suelo P1
            if (step < 3) {
                w.getBlockAt(cx, f1, cz + 10 + step).setType(Material.AIR);
                w.getBlockAt(cx + 1, f1, cz + 10 + step).setType(Material.AIR);
            }
        }
        // Cadenas y jaulas en el sótano
        for (int x = cx - 8; x <= cx + 8; x += 4) {
            for (int y = basementY + 1; y <= basementY + 3; y++) {
                w.getBlockAt(x, y, cz + 15).setType(Material.IRON_BARS);
            }
        }
        w.getBlockAt(cx, basementY + 1, cz + 25).setType(Material.SOUL_LANTERN);
        w.getBlockAt(cx - 5, basementY + 1, cz + 20).setType(Material.SOUL_LANTERN);
        w.getBlockAt(cx + 5, basementY + 1, cz + 20).setType(Material.SOUL_LANTERN);
        
        org.bukkit.Bukkit.getLogger().info("[MiniGames] Murder Mystery arena construida exitosamente!");
    }

    private static void buildRoomFloor(World w, int x1, int z1, int x2, int z2, int floorY, Material mat) {
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                w.getBlockAt(x, floorY, z).setType(mat);
            }
        }
    }

    private static void buildHallWalls(World w, int x1, int x2, int z, int floorY, int height, Material mat) {
        for (int x = x1; x <= x2; x++) {
            for (int y = floorY + 1; y <= floorY + height; y++) {
                if (w.getBlockAt(x, y, z).getType() == Material.AIR) {
                    w.getBlockAt(x, y, z).setType(mat);
                }
            }
        }
    }

    private static void buildHallWallsZ(World w, int z1, int z2, int x, int floorY, int height, Material mat) {
        for (int z = z1; z <= z2; z++) {
            for (int y = floorY + 1; y <= floorY + height; y++) {
                if (w.getBlockAt(x, y, z).getType() == Material.AIR) {
                    w.getBlockAt(x, y, z).setType(mat);
                }
            }
        }
    }

    public static List<Location> getMurderMysterySpawns(World w) {
        List<Location> spawns = new ArrayList<>();
        int y = 81; // f1 (80) + 1 = sobre el suelo de planta baja
        // Posiciones fijas en zonas GARANTIZADAS como aire:
        // Pasillos (ancho 4, centro en x=0 o z=0) y centros de habitaciones
        // Pasillo horizontal (z=-1 a z+1, cualquier x)
        spawns.add(new Location(w, -30.5, y, 0.5));
        spawns.add(new Location(w, -20.5, y, 0.5));
        spawns.add(new Location(w, -10.5, y, 0.5));
        spawns.add(new Location(w, 10.5, y, 0.5));
        spawns.add(new Location(w, 20.5, y, 0.5));
        spawns.add(new Location(w, 30.5, y, 0.5));
        // Pasillo vertical (x=-1 a x+1, cualquier z)
        spawns.add(new Location(w, 0.5, y, -30.5));
        spawns.add(new Location(w, 0.5, y, -20.5));
        spawns.add(new Location(w, 0.5, y, -10.5));
        spawns.add(new Location(w, 0.5, y, 10.5));
        spawns.add(new Location(w, 0.5, y, 20.5));
        spawns.add(new Location(w, 0.5, y, 30.5));
        // Intersección central
        spawns.add(new Location(w, 1.5, y, 1.5));
        spawns.add(new Location(w, -1.5, y, -1.5));
        spawns.add(new Location(w, 1.5, y, -1.5));
        spawns.add(new Location(w, -1.5, y, 1.5));
        // Centros de habitaciones P1 (lejos de muebles)
        spawns.add(new Location(w, 20.5, y, -20.5));  // Gran Salón
        spawns.add(new Location(w, -20.5, y, -20.5)); // Cocina
        spawns.add(new Location(w, 20.5, y, 20.5));   // Biblioteca (centro mesa)
        spawns.add(new Location(w, -20.5, y, 20.5));  // Comedor
        // Planta 2 (f2 = 85, sobre suelo = 86)
        int y2 = 86;
        spawns.add(new Location(w, -30.5, y2, 0.5));
        spawns.add(new Location(w, -20.5, y2, 0.5));
        spawns.add(new Location(w, 10.5, y2, 0.5));
        spawns.add(new Location(w, 20.5, y2, 0.5));
        spawns.add(new Location(w, 0.5, y2, -20.5));
        spawns.add(new Location(w, 0.5, y2, 20.5));
        spawns.add(new Location(w, 0.5, y2, -30.5));
        spawns.add(new Location(w, 0.5, y2, 30.5));
        return spawns;
    }

    // =============================================
    // BEAST ESCAPE - Laberinto masivo con zonas temáticas
    // =============================================
    public static void buildBeastEscape(World w) {
        int baseY = 80;
        int halfW = 50, halfL = 50; // 100x100 arena
        int wallH = 5;

        // 1. Suelo base: piedra musgosa
        for (int x = -halfW; x <= halfW; x++) {
            for (int z = -halfL; z <= halfL; z++) {
                Material floor;
                if ((x + z) % 3 == 0) floor = Material.MOSSY_COBBLESTONE;
                else if ((x + z) % 5 == 0) floor = Material.COBBLESTONE;
                else floor = Material.STONE_BRICKS;
                w.getBlockAt(x, baseY, z).setType(floor);
                w.getBlockAt(x, baseY - 1, z).setType(Material.DEEPSLATE);
            }
        }

        // 2. Paredes exteriores de deepslate brick
        for (int x = -halfW; x <= halfW; x++) {
            for (int y = baseY + 1; y <= baseY + wallH + 2; y++) {
                w.getBlockAt(x, y, -halfL).setType(Material.DEEPSLATE_BRICKS);
                w.getBlockAt(x, y, halfL).setType(Material.DEEPSLATE_BRICKS);
            }
        }
        for (int z = -halfL; z <= halfL; z++) {
            for (int y = baseY + 1; y <= baseY + wallH + 2; y++) {
                w.getBlockAt(-halfW, y, z).setType(Material.DEEPSLATE_BRICKS);
                w.getBlockAt(halfW, y, z).setType(Material.DEEPSLATE_BRICKS);
            }
        }

        // 3. Techo parcial (deja huecos para luz dramática)
        for (int x = -halfW; x <= halfW; x++) {
            for (int z = -halfL; z <= halfL; z++) {
                if ((x + z) % 7 != 0) {
                    w.getBlockAt(x, baseY + wallH + 3, z).setType(Material.DEEPSLATE_TILES);
                }
            }
        }

        // 4. LABERINTO: Paredes interiores usando un grid procedural
        int cellSize = 5;
        int gridW = (halfW * 2) / cellSize;
        int gridL = (halfL * 2) / cellSize;
        boolean[][] visited = new boolean[gridW][gridL];
        boolean[][][] walls = new boolean[gridW][gridL][4]; // 0=N,1=E,2=S,3=W, true=wall present
        // Initialize all walls
        for (int gx = 0; gx < gridW; gx++)
            for (int gz = 0; gz < gridL; gz++)
                for (int d = 0; d < 4; d++)
                    walls[gx][gz][d] = true;

        // Recursive backtracking maze generation
        Stack<int[]> stack = new Stack<>();
        visited[0][0] = true;
        stack.push(new int[]{0, 0});
        int[][] dirs = {{0, -1, 0, 2}, {1, 0, 1, 3}, {0, 1, 2, 0}, {-1, 0, 3, 1}};

        while (!stack.isEmpty()) {
            int[] cell = stack.peek();
            List<int[]> neighbors = new ArrayList<>();
            for (int[] d : dirs) {
                int nx = cell[0] + d[0], nz = cell[1] + d[1];
                if (nx >= 0 && nx < gridW && nz >= 0 && nz < gridL && !visited[nx][nz]) {
                    neighbors.add(new int[]{nx, nz, d[2], d[3]});
                }
            }
            if (neighbors.isEmpty()) {
                stack.pop();
            } else {
                int[] next = neighbors.get(RANDOM.nextInt(neighbors.size()));
                walls[cell[0]][cell[1]][next[2]] = false;
                walls[next[0]][next[1]][next[3]] = false;
                visited[next[0]][next[1]] = true;
                stack.push(new int[]{next[0], next[1]});
            }
        }

        // Place maze walls as stone brick walls
        for (int gx = 0; gx < gridW; gx++) {
            for (int gz = 0; gz < gridL; gz++) {
                int bx = -halfW + gx * cellSize;
                int bz = -halfL + gz * cellSize;

                // North wall (z = bz)
                if (walls[gx][gz][0]) {
                    for (int dx = 0; dx <= cellSize; dx++) {
                        for (int y = baseY + 1; y <= baseY + wallH; y++) {
                            Material wm = y == baseY + wallH ? Material.DEEPSLATE_TILE_SLAB : Material.STONE_BRICKS;
                            if (RANDOM.nextInt(10) == 0) wm = Material.MOSSY_STONE_BRICKS;
                            w.getBlockAt(bx + dx, y, bz).setType(wm);
                        }
                    }
                }
                // East wall (x = bx + cellSize)
                if (walls[gx][gz][1]) {
                    for (int dz = 0; dz <= cellSize; dz++) {
                        for (int y = baseY + 1; y <= baseY + wallH; y++) {
                            Material wm = y == baseY + wallH ? Material.DEEPSLATE_TILE_SLAB : Material.STONE_BRICKS;
                            if (RANDOM.nextInt(10) == 0) wm = Material.CRACKED_STONE_BRICKS;
                            w.getBlockAt(bx + cellSize, y, bz + dz).setType(wm);
                        }
                    }
                }
            }
        }

        // 5. ZONA: Cripta central (radio 8 del centro)
        for (int x = -8; x <= 8; x++) {
            for (int z = -8; z <= 8; z++) {
                // Limpiar aire
                for (int y = baseY + 1; y <= baseY + wallH; y++) {
                    w.getBlockAt(x, y, z).setType(Material.AIR);
                }
                // Suelo de la cripta
                w.getBlockAt(x, baseY, z).setType(
                    (Math.abs(x) + Math.abs(z)) % 2 == 0 ? Material.POLISHED_DEEPSLATE : Material.DEEPSLATE_BRICKS);
                // Techo de la cripta
                w.getBlockAt(x, baseY + wallH + 1, z).setType(Material.DEEPSLATE_BRICKS);
            }
        }
        // Paredes de la cripta
        for (int x = -8; x <= 8; x++) {
            for (int y = baseY + 1; y <= baseY + wallH; y++) {
                w.getBlockAt(x, y, -8).setType(Material.CHISELED_DEEPSLATE);
                w.getBlockAt(x, y, 8).setType(Material.CHISELED_DEEPSLATE);
            }
        }
        for (int z = -8; z <= 8; z++) {
            for (int y = baseY + 1; y <= baseY + wallH; y++) {
                w.getBlockAt(-8, y, z).setType(Material.CHISELED_DEEPSLATE);
                w.getBlockAt(8, y, z).setType(Material.CHISELED_DEEPSLATE);
            }
        }
        // Entradas (4 puertas)
        int[][] cryptDoors = {{0, -8}, {0, 8}, {-8, 0}, {8, 0}};
        for (int[] d : cryptDoors) {
            for (int y = baseY + 1; y <= baseY + 3; y++) {
                w.getBlockAt(d[0], y, d[1]).setType(Material.AIR);
                w.getBlockAt(d[0] + (d[1] == 0 ? 0 : 0), y, d[1] + (d[0] == 0 ? 0 : 0)).setType(Material.AIR);
            }
        }
        // Sarcófago central
        for (int dx = -1; dx <= 1; dx++) {
            w.getBlockAt(dx, baseY + 1, 0).setType(Material.POLISHED_DEEPSLATE);
            w.getBlockAt(dx, baseY + 2, 0).setType(Material.DEEPSLATE_BRICK_SLAB);
        }
        // Velas alrededor
        int[][] candlePos = {{-3, -3}, {3, -3}, {-3, 3}, {3, 3}, {-5, 0}, {5, 0}, {0, -5}, {0, 5}};
        for (int[] cp : candlePos) {
            w.getBlockAt(cp[0], baseY + 1, cp[1]).setType(Material.SOUL_LANTERN);
        }

        // 6. ZONA: Alcantarillas (esquina suroeste, -30 a -45 en ambos ejes)
        for (int x = -45; x <= -30; x++) {
            for (int z = -45; z <= -30; z++) {
                for (int y = baseY + 1; y <= baseY + wallH; y++) {
                    w.getBlockAt(x, y, z).setType(Material.AIR);
                }
                w.getBlockAt(x, baseY, z).setType((x + z) % 3 == 0 ? Material.WATER : Material.MOSSY_COBBLESTONE);
                w.getBlockAt(x, baseY + 3, z).setType(Material.IRON_BARS);
            }
        }
        // Tubería decorativa
        for (int x = -44; x <= -31; x += 4) {
            for (int y = baseY + 1; y <= baseY + 2; y++) {
                w.getBlockAt(x, y, -37).setType(Material.IRON_BARS);
            }
        }

        // 7. ZONA: Biblioteca (esquina noreste, +30 a +45)
        for (int x = 30; x <= 45; x++) {
            for (int z = 30; z <= 45; z++) {
                for (int y = baseY + 1; y <= baseY + wallH; y++) {
                    w.getBlockAt(x, y, z).setType(Material.AIR);
                }
                w.getBlockAt(x, baseY, z).setType(Material.DARK_OAK_PLANKS);
            }
        }
        // Estanterías
        for (int x = 31; x <= 44; x += 3) {
            for (int z = 31; z <= 44; z++) {
                for (int y = baseY + 1; y <= baseY + 3; y++) {
                    w.getBlockAt(x, y, z).setType(Material.BOOKSHELF);
                }
            }
        }
        // Lecterns y mesas
        w.getBlockAt(37, baseY + 1, 37).setType(Material.LECTERN);
        w.getBlockAt(38, baseY + 1, 37).setType(Material.ENCHANTING_TABLE);

        // 8. ZONA: Jardín muerto (esquina sureste, +30 a +45 x, -30 to -45 z)
        for (int x = 30; x <= 45; x++) {
            for (int z = -45; z <= -30; z++) {
                for (int y = baseY + 1; y <= baseY + wallH + 3; y++) {
                    w.getBlockAt(x, y, z).setType(Material.AIR);
                }
                w.getBlockAt(x, baseY, z).setType(
                    RANDOM.nextInt(3) == 0 ? Material.COARSE_DIRT : Material.PODZOL);
                // Árboles muertos
                if (RANDOM.nextInt(20) == 0) {
                    for (int y = baseY + 1; y <= baseY + 3 + RANDOM.nextInt(2); y++) {
                        w.getBlockAt(x, y, z).setType(Material.DEAD_BUSH);
                        if (y > baseY + 1) w.getBlockAt(x, y, z).setType(Material.OAK_FENCE);
                    }
                }
            }
        }
        // Fuente rota
        for (int dx = 35; dx <= 40; dx++) {
            for (int dz = -40; dz <= -35; dz++) {
                w.getBlockAt(dx, baseY + 1, dz).setType(Material.CRACKED_STONE_BRICKS);
            }
        }
        w.getBlockAt(37, baseY + 1, -37).setType(Material.CAULDRON);

        // 9. ZONA: Torre de vigilancia (esquina noroeste, -30 to -45 x, +30 to +45 z)
        int towerX = -37, towerZ = 37;
        for (int y = baseY + 1; y <= baseY + 12; y++) {
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    if (Math.abs(dx) == 3 || Math.abs(dz) == 3) {
                        w.getBlockAt(towerX + dx, y, towerZ + dz).setType(
                            y <= baseY + wallH ? Material.STONE_BRICKS : Material.DARK_OAK_PLANKS);
                    } else {
                        w.getBlockAt(towerX + dx, y, towerZ + dz).setType(Material.AIR);
                    }
                }
            }
        }
        // Escalera interior espiral (slabs en vez de stairs para evitar problemas de orientación)
        for (int y = baseY + 1; y <= baseY + 11; y++) {
            int step = (y - baseY) % 4;
            int sx = step == 0 ? -2 : step == 1 ? 0 : step == 2 ? 2 : 0;
            int sz = step == 0 ? 0 : step == 1 ? -2 : step == 2 ? 0 : 2;
            w.getBlockAt(towerX + sx, y, towerZ + sz).setType(Material.SPRUCE_SLAB);
        }
        // Puerta de torre
        for (int y = baseY + 1; y <= baseY + 3; y++) {
            w.getBlockAt(towerX + 3, y, towerZ).setType(Material.AIR);
        }
        // Mirador arriba
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                w.getBlockAt(towerX + dx, baseY + 12, towerZ + dz).setType(Material.DARK_OAK_PLANKS);
            }
        }

        // 10. Iluminación tenue por todo el laberinto
        for (int x = -halfW + 3; x <= halfW - 3; x += 7) {
            for (int z = -halfL + 3; z <= halfL - 3; z += 7) {
                if (w.getBlockAt(x, baseY + 1, z).getType() == Material.AIR) {
                    w.getBlockAt(x, baseY + 1, z).setType(
                        RANDOM.nextBoolean() ? Material.SOUL_LANTERN : Material.SOUL_TORCH);
                }
            }
        }

        // 11. Telarañas y ambiente siniestro dispersas
        for (int x = -halfW + 2; x <= halfW - 2; x += 5) {
            for (int z = -halfL + 2; z <= halfL - 2; z += 5) {
                if (RANDOM.nextInt(8) == 0 && w.getBlockAt(x, baseY + wallH, z).getType() != Material.AIR) {
                    w.getBlockAt(x, baseY + wallH - 1, z).setType(Material.COBWEB);
                }
            }
        }

        // 12. Puerta de salida (bloqueada con barras al norte)
        Location exit = getBeastEscapeExitLocation(w);
        // Limpiar corredor de acceso desde el laberinto hasta la puerta (z=-45 a z=-50)
        for (int z = exit.getBlockZ(); z <= exit.getBlockZ() + 8; z++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dy = 0; dy <= 3; dy++) {
                    w.getBlockAt(exit.getBlockX() + dx, exit.getBlockY() + dy, z).setType(Material.AIR);
                }
                // Suelo sólido en el corredor
                w.getBlockAt(exit.getBlockX() + dx, exit.getBlockY() - 1, z).setType(Material.STONE_BRICKS);
            }
        }
        // Plataforma de salida al otro lado de la pared (z=-51 a z=-54)
        for (int z = exit.getBlockZ() - 4; z <= exit.getBlockZ() - 1; z++) {
            for (int dx = -3; dx <= 3; dx++) {
                w.getBlockAt(exit.getBlockX() + dx, exit.getBlockY() - 1, z).setType(Material.GOLD_BLOCK);
                for (int dy = 0; dy <= 4; dy++) {
                    w.getBlockAt(exit.getBlockX() + dx, exit.getBlockY() + dy, z).setType(Material.AIR);
                }
            }
        }
        // Barras de hierro bloqueando la puerta
        for (int dy = 0; dy < 4; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                w.getBlockAt(exit.getBlockX() + dx, exit.getBlockY() + dy, exit.getBlockZ()).setType(Material.IRON_BARS);
            }
        }
        // Marco de la puerta
        for (int dy = -1; dy < 5; dy++) {
            w.getBlockAt(exit.getBlockX() - 2, exit.getBlockY() + dy, exit.getBlockZ()).setType(Material.GILDED_BLACKSTONE);
            w.getBlockAt(exit.getBlockX() + 2, exit.getBlockY() + dy, exit.getBlockZ()).setType(Material.GILDED_BLACKSTONE);
        }
        w.getBlockAt(exit.getBlockX(), exit.getBlockY() + 5, exit.getBlockZ()).setType(Material.REDSTONE_LAMP);
        // Iluminación del corredor
        w.getBlockAt(exit.getBlockX(), exit.getBlockY() + 3, exit.getBlockZ() + 4).setType(Material.LANTERN);
        w.getBlockAt(exit.getBlockX(), exit.getBlockY() + 3, exit.getBlockZ() + 7).setType(Material.LANTERN);

        // 13. Entradas explícitas a cada zona temática (garantizar acceso desde el laberinto)
        // Alcantarillas: entradas este y norte
        for (int d = -1; d <= 1; d++) {
            for (int y = baseY + 1; y <= baseY + 3; y++) {
                w.getBlockAt(-30, y, -37 + d).setType(Material.AIR); // este
                w.getBlockAt(-37 + d, y, -30).setType(Material.AIR); // norte
            }
        }
        // Biblioteca: entradas oeste y sur
        for (int d = -1; d <= 1; d++) {
            for (int y = baseY + 1; y <= baseY + 3; y++) {
                w.getBlockAt(30, y, 37 + d).setType(Material.AIR); // oeste
                w.getBlockAt(37 + d, y, 30).setType(Material.AIR); // sur
            }
        }
        // Jardín muerto: entradas oeste y norte
        for (int d = -1; d <= 1; d++) {
            for (int y = baseY + 1; y <= baseY + 3; y++) {
                w.getBlockAt(30, y, -37 + d).setType(Material.AIR); // oeste
                w.getBlockAt(37 + d, y, -30).setType(Material.AIR); // norte
            }
        }
        // Torre: entrada extra al este (además de la puerta existente)
        for (int d = -1; d <= 1; d++) {
            for (int y = baseY + 1; y <= baseY + 3; y++) {
                w.getBlockAt(-34, y, 37 + d).setType(Material.AIR);
                w.getBlockAt(-37 + d, y, 34).setType(Material.AIR); // sur
            }
        }

        // 14. Palancas en posiciones fijas — limpiar área 5x5 para accesibilidad
        List<Location> levers = getBeastEscapeLeverLocations(w);
        for (Location ll : levers) {
            int lx = ll.getBlockX(), ly = ll.getBlockY(), lz = ll.getBlockZ();
            // Limpiar 5x5x3 de aire alrededor de la palanca
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    for (int dy = 0; dy <= 2; dy++) {
                        w.getBlockAt(lx + dx, ly + dy, lz + dz).setType(Material.AIR);
                    }
                    // Suelo sólido debajo
                    w.getBlockAt(lx + dx, ly - 1, lz + dz).setType(Material.STONE_BRICKS);
                }
            }
            // Colocar palanca sobre bloque de oro con lámpara encima
            w.getBlockAt(lx, ly - 1, lz).setType(Material.GOLD_BLOCK);
            w.getBlockAt(lx, ly, lz).setType(Material.LEVER);
            w.getBlockAt(lx, ly + 3, lz).setType(Material.REDSTONE_LAMP);
        }
    }

    public static List<Location> getBeastEscapeSpawns(World w) {
        List<Location> spawns = new ArrayList<>();
        int y = 81;
        // Supervivientes spawnean en las esquinas (lejos de la bestia en el centro)
        int[][] corners = {{-40, -40}, {40, -40}, {-40, 40}, {40, 40},
                           {-30, -40}, {30, -40}, {-40, -30}, {40, -30},
                           {-30, 40}, {30, 40}, {-40, 30}, {40, 30},
                           {-20, -40}, {20, -40}, {-20, 40}, {20, 40},
                           {-40, -20}, {40, -20}, {-40, 20}, {40, 20}};
        for (int[] c : corners) {
            spawns.add(new Location(w, c[0] + 0.5, y, c[1] + 0.5));
        }
        return spawns;
    }

    public static List<Location> getBeastEscapeLeverLocations(World w) {
        List<Location> levers = new ArrayList<>();
        int y = 81;
        // 5 palancas en posiciones fijas dentro de las zonas temáticas abiertas
        // Cada zona tiene aire garantizado (se limpia en buildBeastEscape)
        levers.add(new Location(w, -37, y, -37));   // Alcantarillas (zona abierta -45 a -30)
        levers.add(new Location(w, 35, y, 35));      // Biblioteca (zona abierta 30 a 45) - evitar estanterías
        levers.add(new Location(w, 37, y, -37));     // Jardín muerto (zona abierta, techo abierto)
        levers.add(new Location(w, -37, y, 37));     // Torre (planta baja, zona abierta interior)
        levers.add(new Location(w, 5, y, 0));        // Cripta central (zona abierta -8 a 8)
        return levers;
    }

    public static Location getBeastEscapeExitLocation(World w) {
        return new Location(w, 0, 81, -50); // Norte, en la pared exterior
    }

    // =============================================
    // RED LIGHT GREEN LIGHT - Pasillo largo
    // =============================================
    public static void buildRedLightGreenLight(World w) {
        int baseY = 80;
        int halfWidth = 15; // 30 bloques de ancho
        int startZ = 50;    // Inicio
        int endZ = -50;     // Meta (100 bloques de largo)

        // Suelo del pasillo
        for (int x = -halfWidth; x <= halfWidth; x++) {
            for (int z = endZ - 5; z <= startZ + 5; z++) {
                if (z >= endZ && z <= startZ) {
                    // Suelo principal
                    if (z % 10 == 0) {
                        w.getBlockAt(x, baseY, z).setType(Material.YELLOW_CONCRETE);
                    } else {
                        w.getBlockAt(x, baseY, z).setType(
                            (x + z) % 2 == 0 ? Material.WHITE_CONCRETE : Material.LIGHT_GRAY_CONCRETE);
                    }
                } else {
                    w.getBlockAt(x, baseY, z).setType(Material.GRAY_CONCRETE);
                }
                w.getBlockAt(x, baseY - 1, z).setType(Material.STONE);
            }
        }

        // Paredes laterales
        for (int z = endZ - 5; z <= startZ + 5; z++) {
            for (int y = baseY + 1; y <= baseY + 8; y++) {
                Material wallMat = y <= baseY + 4 ? Material.SMOOTH_STONE : Material.GLASS;
                w.getBlockAt(-halfWidth - 1, y, z).setType(wallMat);
                w.getBlockAt(halfWidth + 1, y, z).setType(wallMat);
            }
        }

        // Pared trasera (detrás del inicio)
        for (int x = -halfWidth; x <= halfWidth; x++) {
            for (int y = baseY + 1; y <= baseY + 8; y++) {
                w.getBlockAt(x, y, startZ + 5).setType(Material.IRON_BLOCK);
            }
        }

        // Línea de meta (verde)
        for (int x = -halfWidth; x <= halfWidth; x++) {
            w.getBlockAt(x, baseY, endZ).setType(Material.LIME_CONCRETE);
            w.getBlockAt(x, baseY, endZ - 1).setType(Material.LIME_CONCRETE);
            w.getBlockAt(x, baseY, endZ - 2).setType(Material.LIME_CONCRETE);
        }
        // Arco de meta
        for (int x = -halfWidth; x <= halfWidth; x++) {
            w.getBlockAt(x, baseY + 6, endZ).setType(Material.LIME_STAINED_GLASS);
        }
        for (int y = baseY + 1; y <= baseY + 6; y++) {
            w.getBlockAt(-halfWidth, y, endZ).setType(Material.GOLD_BLOCK);
            w.getBlockAt(halfWidth, y, endZ).setType(Material.GOLD_BLOCK);
        }

        // Línea de inicio (roja)
        for (int x = -halfWidth; x <= halfWidth; x++) {
            w.getBlockAt(x, baseY, startZ).setType(Material.RED_CONCRETE);
        }

        // Obstáculos decorativos en el camino (no bloquean, solo estéticos)
        for (int z = startZ - 10; z >= endZ + 10; z -= 15) {
            // Pilares
            for (int y = baseY + 1; y <= baseY + 3; y++) {
                w.getBlockAt(-8, y, z).setType(Material.QUARTZ_PILLAR);
                w.getBlockAt(8, y, z).setType(Material.QUARTZ_PILLAR);
            }
            w.getBlockAt(-8, baseY + 4, z).setType(Material.SEA_LANTERN);
            w.getBlockAt(8, baseY + 4, z).setType(Material.SEA_LANTERN);
        }

        // "Muñeca" observadora al final (torre decorativa)
        int dollX = 0, dollZ = endZ - 4;
        for (int y = baseY + 1; y <= baseY + 5; y++) {
            w.getBlockAt(dollX, y, dollZ).setType(Material.DARK_OAK_LOG);
        }
        // Cabeza
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                w.getBlockAt(dollX + dx, baseY + 5, dollZ + dz).setType(Material.ORANGE_CONCRETE);
                w.getBlockAt(dollX + dx, baseY + 6, dollZ + dz).setType(Material.ORANGE_CONCRETE);
            }
        }
        w.getBlockAt(dollX, baseY + 7, dollZ).setType(Material.ORANGE_CONCRETE);
        // Ojos
        w.getBlockAt(dollX - 1, baseY + 6, dollZ + 1).setType(Material.BLACK_CONCRETE);
        w.getBlockAt(dollX + 1, baseY + 6, dollZ + 1).setType(Material.BLACK_CONCRETE);

        // Iluminación
        for (int z = startZ; z >= endZ; z -= 8) {
            w.getBlockAt(-halfWidth, baseY + 5, z).setType(Material.SEA_LANTERN);
            w.getBlockAt(halfWidth, baseY + 5, z).setType(Material.SEA_LANTERN);
        }
    }

    public static List<Location> getRedLightGreenLightSpawns(World w) {
        List<Location> spawns = new ArrayList<>();
        int y = 81;
        int startZ = 48; // Detrás de la línea de inicio
        for (int i = 0; i < 24; i++) {
            int x = -12 + (i % 8) * 3;
            int z = startZ - (i / 8) * 2;
            spawns.add(new Location(w, x + 0.5, y, z + 0.5));
        }
        return spawns;
    }

    // =============================================
    // FARM HUNT - Granja decorada con corrales
    // =============================================
    public static void buildFarmHunt(World w) {
        int baseY = 80;
        int farmSize = 40;

        // === SUELO BASE: Tierra y hierba ===
        for (int x = -farmSize; x <= farmSize; x++) {
            for (int z = -farmSize; z <= farmSize; z++) {
                w.getBlockAt(x, baseY - 1, z).setType(Material.DIRT);
                w.getBlockAt(x, baseY, z).setType(Material.GRASS_BLOCK);
                // Capa inferior
                w.getBlockAt(x, baseY - 2, z).setType(Material.STONE);
            }
        }

        // === CAMINOS DE GRAVA ===
        // Camino principal norte-sur
        for (int z = -farmSize; z <= farmSize; z++) {
            for (int dx = -1; dx <= 1; dx++) {
                w.getBlockAt(dx, baseY, z).setType(Material.GRAVEL);
            }
        }
        // Camino este-oeste
        for (int x = -farmSize; x <= farmSize; x++) {
            for (int dz = -1; dz <= 1; dz++) {
                w.getBlockAt(x, baseY, dz).setType(Material.GRAVEL);
            }
        }
        // Bordes de camino con path
        for (int z = -farmSize; z <= farmSize; z++) {
            w.getBlockAt(-2, baseY, z).setType(Material.DIRT_PATH);
            w.getBlockAt(2, baseY, z).setType(Material.DIRT_PATH);
        }
        for (int x = -farmSize; x <= farmSize; x++) {
            w.getBlockAt(x, baseY, -2).setType(Material.DIRT_PATH);
            w.getBlockAt(x, baseY, 2).setType(Material.DIRT_PATH);
        }

        // === GRANERO PRINCIPAL (Esquina noroeste) ===
        int barnX = -20, barnZ = -20;
        buildBarn(w, barnX, baseY + 1, barnZ);

        // === CORRALES DE ANIMALES (cercas) ===
        // Corral 1: Noroeste pequeño
        buildFence(w, 10, baseY + 1, -25, 20, -15);
        // Corral 2: Suroeste
        buildFence(w, -30, baseY + 1, 10, -15, 25);
        // Corral 3: Noreste
        buildFence(w, 15, baseY + 1, -10, 30, 5);
        // Corral 4: Sureste
        buildFence(w, 10, baseY + 1, 15, 25, 30);

        // === CAMPOS DE CULTIVO ===
        buildCropField(w, -12, baseY, 8, -4, 18);
        buildCropField(w, 5, baseY, -18, 12, -10);

        // === PAJARES (Haystacks) repartidos ===
        int[][] haystacks = {{-8, -15}, {12, 8}, {-25, 5}, {20, -5}, {5, 22}, {-15, 20}, {25, 20}, {-5, -25}, {15, -20}, {-20, 12}};
        for (int[] hs : haystacks) {
            buildHaystack(w, hs[0], baseY + 1, hs[1]);
        }

        // === MOLINO DE VIENTO (centro-este) ===
        buildWindmill(w, 25, baseY + 1, -20);

        // === POZO DE AGUA (centro) ===
        buildWell(w, 0, baseY + 1, 0);

        // === ÁRBOLES FRUTALES ===
        int[][] trees = {{-30, -8}, {-28, 28}, {30, -15}, {28, 25}, {-10, 30}, {10, -30}, {-35, 0}, {35, 0}};
        for (int[] t : trees) {
            buildFarmTree(w, t[0], baseY + 1, t[1]);
        }

        // === FLORES Y DECORACIÓN ===
        Material[] flowers = {Material.DANDELION, Material.POPPY, Material.CORNFLOWER, Material.OXEYE_DAISY, Material.ALLIUM};
        for (int i = 0; i < 80; i++) {
            int fx = RANDOM.nextInt(farmSize * 2) - farmSize;
            int fz = RANDOM.nextInt(farmSize * 2) - farmSize;
            if (w.getBlockAt(fx, baseY, fz).getType() == Material.GRASS_BLOCK
                && w.getBlockAt(fx, baseY + 1, fz).getType() == Material.AIR) {
                w.getBlockAt(fx, baseY + 1, fz).setType(flowers[RANDOM.nextInt(flowers.length)]);
            }
        }

        // === HIERBA ALTA ===
        for (int i = 0; i < 120; i++) {
            int gx = RANDOM.nextInt(farmSize * 2) - farmSize;
            int gz = RANDOM.nextInt(farmSize * 2) - farmSize;
            if (w.getBlockAt(gx, baseY, gz).getType() == Material.GRASS_BLOCK
                && w.getBlockAt(gx, baseY + 1, gz).getType() == Material.AIR) {
                w.getBlockAt(gx, baseY + 1, gz).setType(RANDOM.nextBoolean() ? Material.SHORT_GRASS : Material.TALL_GRASS);
            }
        }

        // === VALLAS EXTERIORES (perímetro) ===
        for (int x = -farmSize; x <= farmSize; x++) {
            w.getBlockAt(x, baseY + 1, -farmSize).setType(Material.OAK_FENCE);
            w.getBlockAt(x, baseY + 1, farmSize).setType(Material.OAK_FENCE);
        }
        for (int z = -farmSize; z <= farmSize; z++) {
            w.getBlockAt(-farmSize, baseY + 1, z).setType(Material.OAK_FENCE);
            w.getBlockAt(farmSize, baseY + 1, z).setType(Material.OAK_FENCE);
        }

        // === ILUMINACIÓN: Antorchas y faroles ===
        int[][] lanterns = {{-10, -10}, {10, -10}, {-10, 10}, {10, 10}, {0, -15}, {0, 15}, {-15, 0}, {15, 0},
            {-25, -25}, {25, -25}, {-25, 25}, {25, 25}, {-20, 0}, {20, 0}, {0, -25}, {0, 25}};
        for (int[] l : lanterns) {
            w.getBlockAt(l[0], baseY + 1, l[1]).setType(Material.OAK_FENCE);
            w.getBlockAt(l[0], baseY + 2, l[1]).setType(Material.LANTERN);
        }

        // === CARROS DE HENO decorativos ===
        buildHayCart(w, -5, baseY + 1, -12);
        buildHayCart(w, 8, baseY + 1, 15);

        // === PAREDES INVISIBLES (barrera arriba) ===
        for (int x = -farmSize; x <= farmSize; x++) {
            for (int y = baseY + 2; y <= baseY + 5; y++) {
                w.getBlockAt(x, y, -farmSize).setType(Material.BARRIER);
                w.getBlockAt(x, y, farmSize).setType(Material.BARRIER);
            }
        }
        for (int z = -farmSize; z <= farmSize; z++) {
            for (int y = baseY + 2; y <= baseY + 5; y++) {
                w.getBlockAt(-farmSize, y, z).setType(Material.BARRIER);
                w.getBlockAt(farmSize, y, z).setType(Material.BARRIER);
            }
        }
    }

    private static void buildBarn(World w, int bx, int by, int bz) {
        int width = 12, depth = 10, height = 8;

        // Suelo del granero
        for (int x = bx; x < bx + width; x++) {
            for (int z = bz; z < bz + depth; z++) {
                w.getBlockAt(x, by - 1, z).setType(Material.SPRUCE_PLANKS);
            }
        }

        // Paredes de madera roja (acacia = aspecto rojo)
        for (int x = bx; x < bx + width; x++) {
            for (int y = by; y < by + height; y++) {
                w.getBlockAt(x, y, bz).setType(Material.ACACIA_PLANKS);
                w.getBlockAt(x, y, bz + depth - 1).setType(Material.ACACIA_PLANKS);
            }
        }
        for (int z = bz; z < bz + depth; z++) {
            for (int y = by; y < by + height; y++) {
                w.getBlockAt(bx, y, z).setType(Material.ACACIA_PLANKS);
                w.getBlockAt(bx + width - 1, y, z).setType(Material.ACACIA_PLANKS);
            }
        }

        // Interior vacío
        for (int x = bx + 1; x < bx + width - 1; x++) {
            for (int z = bz + 1; z < bz + depth - 1; z++) {
                for (int y = by; y < by + height; y++) {
                    w.getBlockAt(x, y, z).setType(Material.AIR);
                }
            }
        }

        // Pilares de esquina de roble oscuro
        for (int y = by; y < by + height; y++) {
            w.getBlockAt(bx, y, bz).setType(Material.DARK_OAK_LOG);
            w.getBlockAt(bx + width - 1, y, bz).setType(Material.DARK_OAK_LOG);
            w.getBlockAt(bx, y, bz + depth - 1).setType(Material.DARK_OAK_LOG);
            w.getBlockAt(bx + width - 1, y, bz + depth - 1).setType(Material.DARK_OAK_LOG);
        }

        // Techo triangular (a dos aguas)
        for (int i = 0; i <= width / 2; i++) {
            for (int z = bz - 1; z < bz + depth + 1; z++) {
                w.getBlockAt(bx + i, by + height + i, z).setType(Material.DARK_OAK_STAIRS);
                w.getBlockAt(bx + width - 1 - i, by + height + i, z).setType(Material.DARK_OAK_STAIRS);
            }
        }
        // Cumbrera del techo
        for (int z = bz - 1; z < bz + depth + 1; z++) {
            w.getBlockAt(bx + width / 2, by + height + width / 2, z).setType(Material.DARK_OAK_SLAB);
        }

        // Puerta del granero (hueco grande)
        for (int y = by; y < by + 4; y++) {
            for (int dx = 2; dx < 5; dx++) {
                w.getBlockAt(bx + dx, y, bz + depth - 1).setType(Material.AIR);
            }
        }

        // Balas de heno dentro
        for (int x = bx + 1; x < bx + 4; x++) {
            for (int z = bz + 1; z < bz + 3; z++) {
                w.getBlockAt(x, by, z).setType(Material.HAY_BLOCK);
                if (RANDOM.nextBoolean()) w.getBlockAt(x, by + 1, z).setType(Material.HAY_BLOCK);
            }
        }

        // Iluminación interior
        w.getBlockAt(bx + width / 2, by + height - 1, bz + depth / 2).setType(Material.LANTERN);
    }

    private static void buildFence(World w, int x1, int y, int z1, int x2, int z2) {
        // Valla de madera rodeando el corral
        for (int x = x1; x <= x2; x++) {
            w.getBlockAt(x, y, z1).setType(Material.OAK_FENCE);
            w.getBlockAt(x, y, z2).setType(Material.OAK_FENCE);
        }
        for (int z = z1; z <= z2; z++) {
            w.getBlockAt(x1, y, z).setType(Material.OAK_FENCE);
            w.getBlockAt(x2, y, z).setType(Material.OAK_FENCE);
        }
        // Puerta
        w.getBlockAt((x1 + x2) / 2, y, z2).setType(Material.OAK_FENCE_GATE);

        // Abrevadero con agua
        int wx = (x1 + x2) / 2 - 1;
        int wz = (z1 + z2) / 2;
        for (int dx = 0; dx < 3; dx++) {
            w.getBlockAt(wx + dx, y, wz).setType(Material.CAULDRON);
        }
    }

    private static void buildCropField(World w, int x1, int y, int z1, int x2, int z2) {
        Material[] crops = {Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS};
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                w.getBlockAt(x, y, z).setType(Material.FARMLAND);
                if ((x + z) % 4 == 0) {
                    w.getBlockAt(x, y, z).setType(Material.WATER);
                } else {
                    w.getBlockAt(x, y + 1, z).setType(crops[RANDOM.nextInt(crops.length)]);
                }
            }
        }
    }

    private static void buildHaystack(World w, int x, int y, int z) {
        int height = 1 + RANDOM.nextInt(3);
        for (int dy = 0; dy < height; dy++) {
            w.getBlockAt(x, y + dy, z).setType(Material.HAY_BLOCK);
            if (dy == 0 && RANDOM.nextBoolean()) {
                w.getBlockAt(x + 1, y, z).setType(Material.HAY_BLOCK);
                w.getBlockAt(x, y, z + 1).setType(Material.HAY_BLOCK);
            }
        }
    }

    private static void buildWindmill(World w, int mx, int my, int mz) {
        // Torre de piedra
        for (int y = my; y < my + 10; y++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    w.getBlockAt(mx + dx, y, mz + dz).setType(Material.COBBLESTONE);
                }
            }
        }
        // Interior hueco
        for (int y = my; y < my + 9; y++) {
            w.getBlockAt(mx, y, mz).setType(Material.AIR);
        }
        // Techo
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                w.getBlockAt(mx + dx, my + 10, mz + dz).setType(Material.DARK_OAK_SLAB);
            }
        }
        // Aspas (cruz de madera)
        for (int i = -4; i <= 4; i++) {
            w.getBlockAt(mx + i, my + 7, mz + 2).setType(Material.OAK_FENCE);
            w.getBlockAt(mx, my + 7 + i, mz + 2).setType(Material.OAK_FENCE);
        }
        // Puerta
        w.getBlockAt(mx, my, mz - 1).setType(Material.AIR);
        w.getBlockAt(mx, my + 1, mz - 1).setType(Material.AIR);
    }

    private static void buildWell(World w, int wx, int wy, int wz) {
        // Base de piedra
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                w.getBlockAt(wx + dx, wy - 1, wz + dz).setType(Material.STONE_BRICKS);
                w.getBlockAt(wx + dx, wy, wz + dz).setType(Material.COBBLESTONE_WALL);
            }
        }
        // Agua en el centro
        w.getBlockAt(wx, wy - 1, wz).setType(Material.WATER);
        w.getBlockAt(wx, wy, wz).setType(Material.WATER);

        // Pilares y techo
        w.getBlockAt(wx - 1, wy + 1, wz - 1).setType(Material.OAK_FENCE);
        w.getBlockAt(wx + 1, wy + 1, wz - 1).setType(Material.OAK_FENCE);
        w.getBlockAt(wx - 1, wy + 1, wz + 1).setType(Material.OAK_FENCE);
        w.getBlockAt(wx + 1, wy + 1, wz + 1).setType(Material.OAK_FENCE);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                w.getBlockAt(wx + dx, wy + 2, wz + dz).setType(Material.SPRUCE_SLAB);
            }
        }
    }

    private static void buildFarmTree(World w, int tx, int ty, int tz) {
        int height = 4 + RANDOM.nextInt(3);
        // Tronco
        for (int y = ty; y < ty + height; y++) {
            w.getBlockAt(tx, y, tz).setType(Material.OAK_LOG);
        }
        // Copa
        int topY = ty + height;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    if (Math.abs(dx) + Math.abs(dz) + Math.abs(dy) <= 3) {
                        if (w.getBlockAt(tx + dx, topY + dy, tz + dz).getType() == Material.AIR) {
                            w.getBlockAt(tx + dx, topY + dy, tz + dz).setType(Material.OAK_LEAVES);
                        }
                    }
                }
            }
        }
    }

    private static void buildHayCart(World w, int cx, int cy, int cz) {
        // Ruedas (troncos)
        w.getBlockAt(cx - 1, cy, cz - 1).setType(Material.SPRUCE_LOG);
        w.getBlockAt(cx + 1, cy, cz - 1).setType(Material.SPRUCE_LOG);
        w.getBlockAt(cx - 1, cy, cz + 1).setType(Material.SPRUCE_LOG);
        w.getBlockAt(cx + 1, cy, cz + 1).setType(Material.SPRUCE_LOG);
        // Plataforma
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                w.getBlockAt(cx + dx, cy + 1, cz + dz).setType(Material.SPRUCE_PLANKS);
            }
        }
        // Heno encima
        w.getBlockAt(cx, cy + 2, cz).setType(Material.HAY_BLOCK);
        w.getBlockAt(cx - 1, cy + 2, cz).setType(Material.HAY_BLOCK);
        w.getBlockAt(cx + 1, cy + 2, cz).setType(Material.HAY_BLOCK);
    }

    public static List<Location> getFarmHuntSpawns(World w) {
        List<Location> spawns = new ArrayList<>();
        int y = 81;
        // Spawns repartidos por toda la granja
        int[][] spawnPoints = {
            {-15, -10}, {-10, -20}, {-5, -15}, {0, -25}, {5, -18}, {10, -12},
            {15, -8}, {20, -15}, {-20, 5}, {-15, 15}, {-8, 10}, {-3, 20},
            {5, 12}, {10, 20}, {18, 8}, {22, 15}, {-25, -5}, {25, 5},
            {-12, 25}, {12, -25}, {-18, -18}, {18, 18}, {-5, 5}, {8, -8}
        };
        for (int[] sp : spawnPoints) {
            spawns.add(new Location(w, sp[0] + 0.5, y, sp[1] + 0.5));
        }
        return spawns;
    }
}
