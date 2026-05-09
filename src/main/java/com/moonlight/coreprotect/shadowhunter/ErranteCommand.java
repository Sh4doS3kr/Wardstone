package com.moonlight.coreprotect.shadowhunter;

import com.moonlight.coreprotect.CoreProtectPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.UUID;
import com.moonlight.coreprotect.util.SmallCaps;

/**
 * Comando /errante — abre un panel GUI con las misiones del Errante.
 */
public class ErranteCommand implements CommandExecutor {

    private final CoreProtectPlugin plugin;
    static final String GUI_TITLE = SmallCaps.convert("§5§l✦ El Errante — Misiones ✦");

    public ErranteCommand(CoreProtectPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(SmallCaps.convert("§cEste comando solo puede ser usado por jugadores."));
            return true;
        }

        Player player = (Player) sender;
        openErranteGUI(player);
        return true;
    }

    public void openErranteGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, 54, GUI_TITLE);
        UUID uid = player.getUniqueId();
        ShadowHunterManager manager = plugin.getShadowHunterManager();

        // Decoración con cristales morados
        ItemStack glass = createDecor(Material.PURPLE_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) {
            gui.setItem(i, glass);
        }

        // Slot 4 central superior: cabeza del Errante (info)
        String nextVisitStr = manager.getNextErranteVisitEstimate(uid);
        ItemStack info = createItem(Material.ENDER_EYE, "§5§l✦ El Errante ✦",
                "§7Un misterioso viajero que aparece",
                "§7ante los aventureros más valientes.",
                "",
                "§7El Errante te visitará cuando lo considere.",
                "§7No puedes invocarlo desde aquí.",
                "",
                "§e⏱ Próxima visita estimada:",
                "§f  " + nextVisitStr,
                "",
                "§c§l⚠ ¡Las misiones son difíciles!",
                "§cPodrías perder tus ítems si mueres.");
        gui.setItem(4, info);

        // Slot 11: Misión 1 — El Cazador de Sombras
        boolean m1Done = manager.hasCompleted(uid);
        String m1State = m1Done ? "§a✔ Completada" : "§e▶ Disponible";
        ItemStack mission1 = createItem(Material.NETHERITE_SWORD,
                "§6§l⚔ Misión 1: El Cazador de Sombras",
                "§8§m                              ",
                "",
                "§7Derrota oleadas de sombras, completa",
                "§7un ritual arcano y enfréntate al",
                "§c§lDevorador de Sombras§7.",
                "",
                "§7Recompensa: §d§lHoja del Vacío",
                "",
                "§7Estado: " + m1State,
                "",
                m1Done ? "§a✔ Ya completada" : "§e▶ Espera la visita del Errante");
        ItemMeta m1Meta = mission1.getItemMeta();
        m1Meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        mission1.setItemMeta(m1Meta);
        gui.setItem(11, mission1);

        // Slot 13: Misión 2 — El Despertar del Abismo
        boolean m2Available = m1Done;
        boolean m2Done = manager.hasCompletedQuest2(uid);
        String m2State;
        Material m2Mat;

        if (!m2Available) {
            m2State = "§c✖ Bloqueada (completa Misión 1)";
            m2Mat = Material.BARRIER;
        } else if (m2Done) {
            m2State = "§a✔ Completada";
            m2Mat = Material.NETHERITE_AXE;
        } else {
            m2State = "§e▶ Disponible";
            m2Mat = Material.NETHERITE_AXE;
        }

        ItemStack mission2 = createItem(m2Mat,
                "§4§l⚔ Misión 2: El Despertar del Abismo",
                "§8§m                              ",
                "",
                "§7Explora ruinas abisales, enfrenta",
                "§7hordas de criaturas oscuras y",
                "§7derrota al temible §4§lTitán del Abismo§7.",
                "",
                "§7Recompensa: §4§lDevoradora de Almas",
                "",
                "§7Estado: " + m2State,
                "",
                m2Available ? (m2Done ? "§a✔ Ya completada" : "§e▶ Espera la visita del Errante")
                        : "§c§lNo disponible");
        if (m2Mat != Material.BARRIER) {
            ItemMeta m2Meta = mission2.getItemMeta();
            m2Meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            mission2.setItemMeta(m2Meta);
        }
        gui.setItem(13, mission2);

        // Slot 15: Misión 3 — El Arquitecto del Vacío
        boolean m3Available = m2Done && manager.isQuest3Enabled();
        boolean m3Done = manager.hasCompletedQuest3(uid);
        String m3State;
        Material m3Mat;

        if (!m2Done) {
            m3State = "§c✖ Bloqueada (completa Misión 2)";
            m3Mat = Material.BARRIER;
        } else if (!manager.isQuest3Enabled()) {
            m3State = "§8✖ Próximamente...";
            m3Mat = Material.BARRIER;
        } else if (m3Done) {
            m3State = "§a✔ Completada";
            m3Mat = Material.NETHERITE_PICKAXE;
        } else {
            m3State = "§e▶ Disponible";
            m3Mat = Material.NETHERITE_PICKAXE;
        }

        ItemStack mission3 = createItem(m3Mat,
                "§0§l✦ Misión 3: El Arquitecto del Vacío ✦",
                "§8§m                              ",
                "",
                "§7Supera las pruebas mentales del Arquitecto,",
                "§7asciende a una arena en el cielo y",
                "§7sobrevive a un §0§lboss psicológico§7.",
                "",
                "§7Recompensa: §0§lFractura del Vacío §7(Pico OP)",
                "",
                "§7Estado: " + m3State,
                "",
                m3Available ? (m3Done ? "§a✔ Ya completada" : "§e▶ Espera la visita del Errante")
                        : "§c§lNo disponible");
        if (m3Mat != Material.BARRIER) {
            ItemMeta m3Meta = mission3.getItemMeta();
            m3Meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            mission3.setItemMeta(m3Meta);
        }
        gui.setItem(15, mission3);

        // Slot 22: Misión 4 — La Elegía del Errante
        boolean m4Available = m3Done && manager.isQuest4Enabled();
        boolean m4Done = manager.hasCompletedQuest4(uid);
        String m4State;
        Material m4Mat;

        if (!m3Done) {
            m4State = "§c✖ Bloqueada (completa Misión 3)";
            m4Mat = Material.BARRIER;
        } else if (!manager.isQuest4Enabled()) {
            m4State = "§8✖ Próximamente...";
            m4Mat = Material.BARRIER;
        } else if (m4Done) {
            m4State = "§a✔ Completada";
            m4Mat = Material.NETHERITE_CHESTPLATE;
        } else {
            m4State = "§e▶ Disponible";
            m4Mat = Material.NETHERITE_CHESTPLATE;
        }

        ItemStack mission4 = createItem(m4Mat,
                "§5§l✦ Misión 4: La Elegía del Errante ✦",
                "§8§m                              ",
                "",
                "§7El Errante revela su pasado trágico.",
                "§7Libera su eco atrapado en dolor eterno",
                "§7y enfréntate al §5§lEco Eterno del Errante§7.",
                "",
                "§7Recompensa: §5§lElegía del Errante §7(Peto OP)",
                "§7  §d(Todas las habilidades son pasivas)",
                "",
                "§7Estado: " + m4State,
                "",
                m4Available ? (m4Done ? "§a✔ Ya completada" : "§e▶ Espera la visita del Errante")
                        : "§c§lNo disponible");
        if (m4Mat != Material.BARRIER) {
            ItemMeta m4Meta = mission4.getItemMeta();
            m4Meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            mission4.setItemMeta(m4Meta);
        }
        gui.setItem(22, mission4);

        // Slot 24: Misión 5 — El Jardín del Cielo
        boolean m5Available = m4Done && manager.isQuest5Enabled();
        boolean m5Done = manager.hasCompletedQuest5(uid);
        String m5State;
        Material m5Mat;

        if (!m4Done) {
            m5State = "§c✖ Bloqueada (completa Misión 4)";
            m5Mat = Material.BARRIER;
        } else if (!manager.isQuest5Enabled()) {
            m5State = "§8✖ Próximamente...";
            m5Mat = Material.BARRIER;
        } else if (m5Done) {
            m5State = "§a✔ Completada";
            m5Mat = Material.BOW;
        } else {
            m5State = "§e▶ Disponible";
            m5Mat = Material.BOW;
        }

        ItemStack mission5 = createItem(m5Mat,
                "§d§l◈ Misión 5: El Jardín del Cielo ◈",
                "§8§m                              ",
                "",
                "§7El Eco del Errante trae un último encargo.",
                "§7Salta entre plataformas flotantes sobre",
                "§7las nubes y llega al altar del arco.",
                "",
                "§7Recompensa: §d§lÚltimo Suspiro §7(Arco OP)",
                "§7  §b(Lluvia Celestial, Paso del Viento...)",
                "",
                "§7Estado: " + m5State,
                "",
                m5Available ? (m5Done ? "§a✔ Ya completada" : "§e▶ Espera la visita del Eco")
                        : "§c§lNo disponible");
        if (m5Mat != Material.BARRIER) {
            ItemMeta m5Meta = mission5.getItemMeta();
            m5Meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            mission5.setItemMeta(m5Meta);
        }
        gui.setItem(24, mission5);

        // Slot 31: Misión 6 — Lágrimas del Vacío
        boolean m6Available = m5Done && manager.isQuest6Enabled();
        boolean m6Done = manager.hasCompletedQuest6(uid);
        String m6State;
        Material m6Mat;

        if (!m5Done) {
            m6State = "§c✖ Bloqueada (completa Misión 5)";
            m6Mat = Material.BARRIER;
        } else if (!manager.isQuest6Enabled()) {
            m6State = "§8✖ Próximamente...";
            m6Mat = Material.BARRIER;
        } else if (m6Done) {
            m6State = "§a✔ Completada";
            m6Mat = Material.NETHERITE_LEGGINGS;
        } else {
            m6State = "§e▶ Disponible";
            m6Mat = Material.NETHERITE_LEGGINGS;
        }

        ItemStack mission6 = createItem(m6Mat,
                "§b§l♡ Misión 6: Lágrimas del Vacío ♡",
                "§8§m                              ",
                "",
                "§7Lyra, la hija del Errante, aparece como espíritu.",
                "§7Camina por sus recuerdos, consuela almas perdidas",
                "§7y enfrenta a §4§lLa Culpa§7, la manifestación del dolor.",
                "",
                "§7Recompensa: §b§lLágrimas de Lyra §7(Pantalones OP)",
                "§7  §b(Paso Fantasmal, Lágrimas Sanadoras...)",
                "",
                "§7Estado: " + m6State,
                "",
                m6Available ? (m6Done ? "§a✔ Ya completada" : "§e▶ Espera la visita de Lyra")
                        : "§c§lNo disponible");
        if (m6Mat != Material.BARRIER) {
            ItemMeta m6Meta = mission6.getItemMeta();
            m6Meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            mission6.setItemMeta(m6Meta);
        }
        gui.setItem(31, mission6);

        // Slot 33: Misión 7 — El Vientre del Vacío
        boolean m7Available = m6Done && manager.isQuest7Enabled();
        boolean m7Done = manager.hasCompletedQuest7(uid);
        String m7State;
        Material m7Mat;

        if (!m6Done) {
            m7State = "§c✖ Bloqueada (completa Misión 6)";
            m7Mat = Material.BARRIER;
        } else if (!manager.isQuest7Enabled()) {
            m7State = "§8✖ Próximamente...";
            m7Mat = Material.BARRIER;
        } else if (m7Done) {
            m7State = "§a✔ Completada";
            m7Mat = Material.NETHERITE_BOOTS;
        } else {
            m7State = "§e▶ Disponible";
            m7Mat = Material.NETHERITE_BOOTS;
        }

        ItemStack mission7 = createItem(m7Mat,
                "§4§l☠ Misión 7: El Vientre del Vacío ☠",
                "§8§m                              ",
                "",
                "§7Desciende a un laberinto de oscuridad pura.",
                "§7Recoge §c§l3 fragmentos de alma§7, sobrevive",
                "§7a los §4§lOlvidados§7 y derrota a §4§lEl Olvido§7.",
                "",
                "§7Recompensa: §4§lPisada del Olvido §7(Botas OP)",
                "§7  §4(Velocidad, Sin daño por caída, Inmune a knockback)",
                "",
                "§7Estado: " + m7State,
                "",
                m7Available ? (m7Done ? "§a✔ Ya completada" : "§e▶ Espera la visita de la Sombra")
                        : "§c§lNo disponible");
        if (m7Mat != Material.BARRIER) {
            ItemMeta m7Meta = mission7.getItemMeta();
            m7Meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            mission7.setItemMeta(m7Meta);
        }
        gui.setItem(33, mission7);

        // Slot 35: Misión 8 — Heraldo del Hielo Eterno
        boolean m8Available = m7Done && manager.isQuest8Enabled();
        boolean m8Done = manager.hasCompletedQuest8(uid);
        String m8State;
        Material m8Mat;

        if (!m7Done) {
            m8State = "§c✖ Bloqueada (completa Misión 7)";
            m8Mat = Material.BARRIER;
        } else if (!manager.isQuest8Enabled()) {
            m8State = "§8✖ Próximamente...";
            m8Mat = Material.BARRIER;
        } else if (m8Done) {
            m8State = "§a✔ Completada";
            m8Mat = Material.MACE;
        } else {
            m8State = "§e▶ Disponible";
            m8Mat = Material.MACE;
        }

        ItemStack mission8 = createItem(m8Mat,
                "§b§l❄ Misión 8: Heraldo del Hielo Eterno ❄",
                "§8§m                              ",
                "",
                "§7Un ser primordial de hielo despierta.",
                "§7Sobrevive a sus §b§l3 fases§7 de poder",
                "§7gélido y derrota al §b§lHeraldo del Hielo§7.",
                "",
                "§7Recompensa: §b§lMaza del Vacío §7(Maza OP)",
                "§7  §b(Congelación, Tormenta de Hielo...)",
                "",
                "§7Estado: " + m8State,
                "",
                m8Available ? (m8Done ? "§a✔ Ya completada" : "§e▶ Espera la visita del Heraldo")
                        : "§c§lNo disponible");
        if (m8Mat != Material.BARRIER) {
            ItemMeta m8Meta = mission8.getItemMeta();
            m8Meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            mission8.setItemMeta(m8Meta);
        }
        gui.setItem(35, mission8);

        // Slot 49 (centro fila 6): Misión 9 — El Origen de la Luz
        boolean m9Available = m8Done && manager.isQuest9Enabled();
        boolean m9Done = manager.hasCompletedQuest9(uid);
        String m9State;
        Material m9Mat;

        if (!m8Done) {
            m9State = "§c✖ Bloqueada (completa Misión 8)";
            m9Mat = Material.BARRIER;
        } else if (!manager.isQuest9Enabled()) {
            m9State = "§8✖ Próximamente...";
            m9Mat = Material.BARRIER;
        } else if (m9Done) {
            m9State = "§a✔ Completada";
            m9Mat = Material.GOLDEN_AXE;
        } else {
            m9State = "§e▶ Disponible";
            m9Mat = Material.GOLDEN_AXE;
        }

        ItemStack mission9 = createItem(m9Mat,
                "§e§l☆ Misión 9: El Origen de la Luz ☆",
                "§8§m                              ",
                "",
                "§7El guardián de la Luz Primordial",
                "§7despierta. Sobrevive §e§l3 fases§7 de",
                "§7poder radiante y obtén la contraparte",
                "§7de la §5§lDevoradora de Almas§7.",
                "",
                "§7Recompensa: §e§lEstrella del Origen §7(Hacha)",
                "§7  §e(Nova Radiante, Singularidad Blanca...)",
                "",
                "§7Estado: " + m9State,
                "",
                m9Available ? (m9Done ? "§a✔ Ya completada" : "§e▶ Espera la visita del Centinela")
                        : "§c§lNo disponible");
        if (m9Mat != Material.BARRIER) {
            ItemMeta m9Meta = mission9.getItemMeta();
            m9Meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            mission9.setItemMeta(m9Meta);
        }
        gui.setItem(49, mission9);

        // Slot 50: Misión 10 — El Fin
        boolean m10Available = m9Done && manager.isQuest10Enabled();
        boolean m10Done = manager.hasCompletedQuest10(uid);
        String m10State;
        Material m10Mat;

        if (!m9Done) {
            m10State = "§c✖ Bloqueada (completa Misión 9)";
            m10Mat = Material.BARRIER;
        } else if (!manager.isQuest10Enabled()) {
            m10State = "§8✖ Próximamente...";
            m10Mat = Material.BARRIER;
        } else if (m10Done) {
            m10State = "§a✔ Completada";
            m10Mat = Material.NETHERITE_HELMET;
        } else {
            m10State = "§e▶ Disponible";
            m10Mat = Material.NETHERITE_HELMET;
        }

        ItemStack mission10 = createItem(m10Mat,
                "§5§l✦ Misión 10: El Fin ✦",
                "§8§m                              ",
                "",
                "§7La última misión. El Errante ha sido",
                "§7consumido por la corrupción que combatió",
                "§7durante milenios. Te pide que acabes",
                "§7con su sufrimiento antes de que sea tarde.",
                "",
                "§7Recompensa: §5§lCasco del Errante §7(Casco OP)",
                "§7  §5(Protección V, Espinas III, Irrompible)",
                "",
                "§7§o\"No me olvides... por favor.\"",
                "",
                "§7Estado: " + m10State,
                "",
                m10Available ? (m10Done ? "§a✔ Ya completada" : "§e▶ Espera la última visita del Errante")
                        : "§c§lNo disponible");
        if (m10Mat != Material.BARRIER) {
            ItemMeta m10Meta = mission10.getItemMeta();
            m10Meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            mission10.setItemMeta(m10Meta);
        }
        gui.setItem(50, mission10);

        player.openInventory(gui);
    }

    private ItemStack createItem(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(Arrays.asList(lore));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createDecor(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }
}
