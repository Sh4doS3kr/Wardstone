package com.moonlight.coreprotect.gui;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.core.ProtectedRegion;
import com.moonlight.coreprotect.util.SmallCaps;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * GUI de Configuración del Core — Huge Update.
 * Permite:
 * - Renombrar el territorio
 * - Cambiar modo de entrada (abierto / solo miembros / ban list)
 * - Establecer mensaje de bienvenida
 * - Transferir propiedad
 * - Ver estadísticas del core
 * - Eliminar el core
 */
public class CoreSettingsGUI {

    public static final String GUI_TITLE = SmallCaps.convert(ChatColor.DARK_GRAY + "" + ChatColor.BOLD + "✦ Configuración ✦");
    private static final int GUI_SIZE = 54;

    private final CoreProtectPlugin plugin;

    public CoreSettingsGUI(CoreProtectPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, ProtectedRegion region) {
        Inventory inv = Bukkit.createInventory(null, GUI_SIZE, GUI_TITLE);

        // === BORDERS ===
        ItemStack blackGlass = createGlass(Material.BLACK_STAINED_GLASS_PANE);
        ItemStack orangeGlass = createGlass(Material.ORANGE_STAINED_GLASS_PANE);
        ItemStack grayGlass = createGlass(Material.GRAY_STAINED_GLASS_PANE);

        for (int i = 0; i < 9; i++) inv.setItem(i, blackGlass);
        inv.setItem(1, orangeGlass);
        inv.setItem(4, orangeGlass);
        inv.setItem(7, orangeGlass);
        for (int row = 1; row < 5; row++) {
            inv.setItem(row * 9, blackGlass);
            inv.setItem(row * 9 + 8, blackGlass);
        }
        for (int i = 45; i < 54; i++) inv.setItem(i, blackGlass);
        for (int i = 9; i < 45; i++) {
            if (inv.getItem(i) == null) inv.setItem(i, grayGlass);
        }

        boolean isOwner = region.getOwner().equals(player.getUniqueId());

        // === CORE NAME (slot 10) ===
        String displayName = region.getCoreName() != null ? region.getCoreName() : "Sin nombre";
        ItemStack nameItem = new ItemStack(Material.NAME_TAG);
        ItemMeta nameMeta = nameItem.getItemMeta();
        nameMeta.setDisplayName(SmallCaps.convert(ChatColor.GOLD + "" + ChatColor.BOLD + "✎ NOMBRE DEL TERRITORIO"));
        List<String> nameLore = new ArrayList<>();
        nameLore.add("");
        nameLore.add(ChatColor.GRAY + "Nombre actual: " + ChatColor.WHITE + displayName);
        nameLore.add("");
        nameLore.add(ChatColor.GRAY + "Cambia el nombre de tu");
        nameLore.add(ChatColor.GRAY + "territorio protegido.");
        nameLore.add("");
        if (isOwner) {
            nameLore.add(ChatColor.YELLOW + "▶ Click para renombrar");
            nameLore.add(ChatColor.DARK_GRAY + "(Escribe en el chat)");
        } else {
            nameLore.add(ChatColor.RED + "Solo el dueño puede renombrar");
        }
        nameMeta.setLore(nameLore);
        nameItem.setItemMeta(nameMeta);
        inv.setItem(10, nameItem);

        // === ENTRY MODE (slot 12) ===
        int mode = region.getEntryMode();
        String modeLabel;
        Material modeMat;
        String modeDesc;
        switch (mode) {
            case 1:
                modeLabel = "§c§lSOLO MIEMBROS";
                modeMat = Material.IRON_DOOR;
                modeDesc = "Solo miembros pueden entrar";
                break;
            case 2:
                modeLabel = "§6§lLISTA NEGRA";
                modeMat = Material.OAK_FENCE_GATE;
                modeDesc = "Los baneados no pueden entrar";
                break;
            default:
                modeLabel = "§a§lABIERTO";
                modeMat = Material.OAK_DOOR;
                modeDesc = "Cualquiera puede entrar";
                break;
        }
        ItemStack modeItem = new ItemStack(modeMat);
        ItemMeta modeMeta = modeItem.getItemMeta();
        modeMeta.setDisplayName(SmallCaps.convert(ChatColor.AQUA + "" + ChatColor.BOLD + "🚪 MODO DE ENTRADA"));
        List<String> modeLore = new ArrayList<>();
        modeLore.add("");
        modeLore.add(ChatColor.GRAY + "Modo actual: " + modeLabel);
        modeLore.add(ChatColor.GRAY + modeDesc);
        modeLore.add("");
        modeLore.add(ChatColor.DARK_GRAY + "▸ " + (mode == 0 ? ChatColor.GREEN + "● " : ChatColor.GRAY + "○ ") + ChatColor.GRAY + "Abierto");
        modeLore.add(ChatColor.DARK_GRAY + "▸ " + (mode == 1 ? ChatColor.GREEN + "● " : ChatColor.GRAY + "○ ") + ChatColor.GRAY + "Solo miembros");
        modeLore.add(ChatColor.DARK_GRAY + "▸ " + (mode == 2 ? ChatColor.GREEN + "● " : ChatColor.GRAY + "○ ") + ChatColor.GRAY + "Lista negra");
        modeLore.add("");
        if (isOwner) {
            modeLore.add(ChatColor.YELLOW + "▶ Click para cambiar modo");
        } else {
            modeLore.add(ChatColor.RED + "Solo el dueño puede cambiar");
        }
        modeMeta.setLore(modeLore);
        modeItem.setItemMeta(modeMeta);
        inv.setItem(12, modeItem);

        // === WELCOME MESSAGE (slot 14) ===
        String welcomeMsg = region.getWelcomeMessage() != null ? region.getWelcomeMessage() : "Ninguno";
        ItemStack welcomeItem = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta welcomeMeta = welcomeItem.getItemMeta();
        welcomeMeta.setDisplayName(SmallCaps.convert(ChatColor.GREEN + "" + ChatColor.BOLD + "✉ MENSAJE DE BIENVENIDA"));
        List<String> welcomeLore = new ArrayList<>();
        welcomeLore.add("");
        welcomeLore.add(ChatColor.GRAY + "Mensaje actual:");
        welcomeLore.add(ChatColor.WHITE + "  \"" + (welcomeMsg.length() > 24 ? welcomeMsg.substring(0, 24) + "..." : welcomeMsg) + "\"");
        welcomeLore.add("");
        welcomeLore.add(ChatColor.GRAY + "Se muestra a jugadores");
        welcomeLore.add(ChatColor.GRAY + "cuando entran a tu zona.");
        welcomeLore.add("");
        if (isOwner) {
            welcomeLore.add(ChatColor.YELLOW + "▶ Click para cambiar");
            welcomeLore.add(ChatColor.RED + "▶ Shift+Click para borrar");
        }
        welcomeMeta.setLore(welcomeLore);
        welcomeItem.setItemMeta(welcomeMeta);
        inv.setItem(14, welcomeItem);

        // === TRANSFER OWNERSHIP (slot 16) ===
        if (isOwner) {
            ItemStack transferItem = new ItemStack(Material.GOLD_INGOT);
            ItemMeta transferMeta = transferItem.getItemMeta();
            transferMeta.setDisplayName(SmallCaps.convert(ChatColor.RED + "" + ChatColor.BOLD + "⚠ TRANSFERIR PROPIEDAD"));
            List<String> transferLore = new ArrayList<>();
            transferLore.add("");
            transferLore.add(ChatColor.GRAY + "Transfiere este territorio");
            transferLore.add(ChatColor.GRAY + "a otro jugador.");
            transferLore.add("");
            transferLore.add(ChatColor.RED + "¡Acción irreversible!");
            transferLore.add(ChatColor.RED + "Perderás el control total.");
            transferLore.add("");
            transferLore.add(ChatColor.YELLOW + "▶ Click para seleccionar");
            transferMeta.setLore(transferLore);
            transferItem.setItemMeta(transferMeta);
            inv.setItem(16, transferItem);
        }

        // === BANNED PLAYERS (slot 20) ===
        ItemStack banItem = new ItemStack(Material.BARRIER);
        ItemMeta banMeta = banItem.getItemMeta();
        banMeta.setDisplayName(SmallCaps.convert(ChatColor.RED + "" + ChatColor.BOLD + "🚫 JUGADORES BANEADOS"));
        List<String> banLore = new ArrayList<>();
        banLore.add("");
        banLore.add(ChatColor.GRAY + "Baneados: " + ChatColor.WHITE + region.getBannedPlayers().size());
        banLore.add("");
        if (!region.getBannedPlayers().isEmpty()) {
            for (UUID banned : region.getBannedPlayers()) {
                String name = Bukkit.getOfflinePlayer(banned).getName();
                banLore.add(ChatColor.DARK_GRAY + "  ▸ " + ChatColor.RED + (name != null ? name : "???"));
            }
            banLore.add("");
        }
        banLore.add(ChatColor.YELLOW + "▶ Click para gestionar");
        banMeta.setLore(banLore);
        banItem.setItemMeta(banMeta);
        inv.setItem(20, banItem);

        // === CORE STATS (slot 22) ===
        ItemStack statsItem = new ItemStack(Material.PAPER);
        ItemMeta statsMeta = statsItem.getItemMeta();
        statsMeta.setDisplayName(SmallCaps.convert(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "📊 ESTADÍSTICAS"));
        List<String> statsLore = new ArrayList<>();
        statsLore.add("");
        long daysOld = (System.currentTimeMillis() - region.getCreatedAt()) / (1000L * 60 * 60 * 24);
        statsLore.add(ChatColor.DARK_GRAY + "▸ " + ChatColor.GRAY + "Creado hace: " + ChatColor.WHITE + daysOld + " días");
        statsLore.add(ChatColor.DARK_GRAY + "▸ " + ChatColor.GRAY + "Nivel: " + ChatColor.WHITE + region.getLevel() + "/30");
        statsLore.add(ChatColor.DARK_GRAY + "▸ " + ChatColor.GRAY + "Área: " + ChatColor.WHITE + region.getEffectiveSize() + "x" + region.getEffectiveSize());
        statsLore.add(ChatColor.DARK_GRAY + "▸ " + ChatColor.GRAY + "Mejoras: " + ChatColor.WHITE + region.getActiveUpgradeCount() + "/" + region.getTotalUpgradeCount());
        statsLore.add(ChatColor.DARK_GRAY + "▸ " + ChatColor.GRAY + "Miembros: " + ChatColor.WHITE + region.getMembers().size());
        statsLore.add(ChatColor.DARK_GRAY + "▸ " + ChatColor.GRAY + "Baneados: " + ChatColor.WHITE + region.getBannedPlayers().size());
        statsLore.add(ChatColor.DARK_GRAY + "▸ " + ChatColor.GRAY + "Prestigio: " + ChatColor.WHITE + region.getPrestige());
        String ownerName = Bukkit.getOfflinePlayer(region.getOwner()).getName();
        statsLore.add(ChatColor.DARK_GRAY + "▸ " + ChatColor.GRAY + "Dueño: " + ChatColor.GOLD + (ownerName != null ? ownerName : "???"));
        statsLore.add("");
        statsLore.add(ChatColor.DARK_GRAY + "Coordenadas: " + region.getCoreX() + ", " + region.getCoreY() + ", " + region.getCoreZ());
        statsMeta.setLore(statsLore);
        statsItem.setItemMeta(statsMeta);
        inv.setItem(22, statsItem);

        // === DELETE CORE (slot 24) ===
        if (isOwner) {
            ItemStack deleteItem = new ItemStack(Material.TNT);
            ItemMeta deleteMeta = deleteItem.getItemMeta();
            deleteMeta.setDisplayName(SmallCaps.convert(ChatColor.DARK_RED + "" + ChatColor.BOLD + "💀 ELIMINAR NÚCLEO"));
            List<String> deleteLore = new ArrayList<>();
            deleteLore.add("");
            deleteLore.add(ChatColor.RED + "Elimina este territorio");
            deleteLore.add(ChatColor.RED + "permanentemente.");
            deleteLore.add("");
            deleteLore.add(ChatColor.DARK_RED + "¡ACCIÓN IRREVERSIBLE!");
            deleteLore.add(ChatColor.DARK_RED + "Se pierde todo.");
            deleteLore.add("");
            deleteLore.add(ChatColor.RED + "▶ Shift+Click para eliminar");
            deleteMeta.setLore(deleteLore);
            deleteItem.setItemMeta(deleteMeta);
            inv.setItem(24, deleteItem);
        }

        // === ADVANCED FLAGS (slot 30) ===
        if (isOwner) {
            ItemStack flagsItem = new ItemStack(Material.REPEATER);
            ItemMeta flagsMeta = flagsItem.getItemMeta();
            flagsMeta.setDisplayName(SmallCaps.convert(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "⚙ AJUSTES AVANZADOS"));
            List<String> flagsLore = new ArrayList<>();
            flagsLore.add("");
            flagsLore.add(ChatColor.GRAY + "Controla las protecciones");
            flagsLore.add(ChatColor.GRAY + "de tu territorio:");
            flagsLore.add("");
            flagsLore.add(ChatColor.DARK_GRAY + "▸ " + (region.isAllowPvP() ? ChatColor.GREEN + "PvP" : ChatColor.RED + "PvP"));
            flagsLore.add(ChatColor.DARK_GRAY + "▸ " + (region.isAllowExplosions() ? ChatColor.GREEN + "Explosiones" : ChatColor.RED + "Explosiones"));
            flagsLore.add(ChatColor.DARK_GRAY + "▸ " + (region.isAllowMobSpawn() ? ChatColor.GREEN + "Mobs" : ChatColor.RED + "Mobs"));
            flagsLore.add(ChatColor.DARK_GRAY + "▸ " + (region.isAllowFallDamage() ? ChatColor.GREEN + "Caída" : ChatColor.RED + "Caída"));
            flagsLore.add(ChatColor.DARK_GRAY + "▸ " + (region.isAllowHunger() ? ChatColor.GREEN + "Hambre" : ChatColor.RED + "Hambre"));
            flagsLore.add(ChatColor.DARK_GRAY + "▸ " + (region.isAllowFireSpread() ? ChatColor.GREEN + "Fuego" : ChatColor.RED + "Fuego"));
            flagsLore.add(ChatColor.DARK_GRAY + "▸ " + (region.isAllowAbilities() ? ChatColor.GREEN + "Habilidades" : ChatColor.RED + "Habilidades"));
            flagsLore.add("");
            flagsLore.add(ChatColor.YELLOW + "▶ Click para configurar");
            flagsMeta.setLore(flagsLore);
            flagsItem.setItemMeta(flagsMeta);
            inv.setItem(30, flagsItem);
        }

        // === BACK BUTTON (slot 49) ===
        ItemStack backBtn = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backBtn.getItemMeta();
        backMeta.setDisplayName(SmallCaps.convert(ChatColor.YELLOW + "" + ChatColor.BOLD + "◀ VOLVER"));
        backMeta.setLore(Arrays.asList("", ChatColor.GRAY + "Volver al panel principal"));
        backBtn.setItemMeta(backMeta);
        inv.setItem(49, backBtn);

        player.openInventory(inv);
    }

    private ItemStack createGlass(Material material) {
        ItemStack glass = new ItemStack(material);
        ItemMeta meta = glass.getItemMeta();
        meta.setDisplayName(SmallCaps.convert(" "));
        glass.setItemMeta(meta);
        return glass;
    }

    public static boolean isSettingsGUI(String title) {
        return title.contains(SmallCaps.convert("Configuración"));
    }
}
