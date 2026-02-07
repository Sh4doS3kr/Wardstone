package com.moonlight.coreprotect.gui;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.core.ProtectedRegion;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class CoreMembersGUI {

    public static final String GUI_TITLE = ChatColor.DARK_GRAY + "" + ChatColor.BOLD + "✦ Miembros ✦";
    public static final String INVITE_TITLE = ChatColor.DARK_GRAY + "" + ChatColor.BOLD + "✦ Invitar Jugador ✦";
    private static final int GUI_SIZE = 54;

    private final CoreProtectPlugin plugin;

    public CoreMembersGUI(CoreProtectPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, ProtectedRegion region) {
        Inventory inv = Bukkit.createInventory(null, GUI_SIZE, GUI_TITLE);

        // === BORDERS ===
        ItemStack blackGlass = createGlass(Material.BLACK_STAINED_GLASS_PANE);
        ItemStack blueGlass = createGlass(Material.LIGHT_BLUE_STAINED_GLASS_PANE);
        ItemStack grayGlass = createGlass(Material.GRAY_STAINED_GLASS_PANE);

        // Top row
        for (int i = 0; i < 9; i++) inv.setItem(i, blackGlass);
        inv.setItem(1, blueGlass);
        inv.setItem(4, blueGlass);
        inv.setItem(7, blueGlass);

        // Side borders
        for (int row = 1; row < 5; row++) {
            inv.setItem(row * 9, blackGlass);
            inv.setItem(row * 9 + 8, blackGlass);
        }

        // Bottom row
        for (int i = 45; i < 54; i++) inv.setItem(i, blackGlass);

        // Fill empty with gray glass
        for (int i = 9; i < 45; i++) {
            if (inv.getItem(i) == null) inv.setItem(i, grayGlass);
        }

        // === CURRENT MEMBERS (slots 10-16, 19-25, 28-34) ===
        int[] memberSlots = {
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34
        };

        List<UUID> members = region.getMembers();
        boolean isOwner = region.getOwner().equals(player.getUniqueId());

        for (int i = 0; i < memberSlots.length && i < members.size(); i++) {
            UUID memberId = members.get(i);
            OfflinePlayer member = Bukkit.getOfflinePlayer(memberId);
            String memberName = member.getName() != null ? member.getName() : "???";

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta skullMeta = (SkullMeta) head.getItemMeta();
            skullMeta.setOwningPlayer(member);
            skullMeta.setDisplayName(ChatColor.AQUA + memberName);

            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add(ChatColor.GRAY + "Estado: " + (member.isOnline() ? ChatColor.GREEN + "Conectado" : ChatColor.RED + "Desconectado"));
            lore.add("");
            if (isOwner) {
                lore.add(ChatColor.RED + "▶ Click para expulsar");
            }
            skullMeta.setLore(lore);
            head.setItemMeta(skullMeta);

            inv.setItem(memberSlots[i], head);
        }

        // === INVITE BUTTON (slot 40) ===
        if (isOwner) {
            ItemStack inviteBtn = new ItemStack(Material.EMERALD);
            ItemMeta invMeta = inviteBtn.getItemMeta();
            invMeta.setDisplayName(ChatColor.GREEN + "" + ChatColor.BOLD + "➕ INVITAR JUGADOR");
            invMeta.setLore(Arrays.asList(
                    "",
                    ChatColor.GRAY + "Selecciona un jugador online",
                    ChatColor.GRAY + "para agregarlo a tu zona.",
                    "",
                    ChatColor.YELLOW + "▶ Click para ver jugadores"));
            inviteBtn.setItemMeta(invMeta);
            inv.setItem(40, inviteBtn);
        }

        // === BACK BUTTON (slot 49) ===
        ItemStack backBtn = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backBtn.getItemMeta();
        backMeta.setDisplayName(ChatColor.YELLOW + "" + ChatColor.BOLD + "◀ VOLVER");
        backMeta.setLore(Arrays.asList("", ChatColor.GRAY + "Volver al panel principal"));
        backBtn.setItemMeta(backMeta);
        inv.setItem(49, backBtn);

        player.openInventory(inv);
    }

    public void openInviteMenu(Player player, ProtectedRegion region) {
        Inventory inv = Bukkit.createInventory(null, GUI_SIZE, INVITE_TITLE);

        // === BORDERS ===
        ItemStack blackGlass = createGlass(Material.BLACK_STAINED_GLASS_PANE);
        ItemStack greenGlass = createGlass(Material.LIME_STAINED_GLASS_PANE);
        ItemStack grayGlass = createGlass(Material.GRAY_STAINED_GLASS_PANE);

        // Top row
        for (int i = 0; i < 9; i++) inv.setItem(i, blackGlass);
        inv.setItem(2, greenGlass);
        inv.setItem(4, greenGlass);
        inv.setItem(6, greenGlass);

        // Side borders + bottom
        for (int row = 1; row < 5; row++) {
            inv.setItem(row * 9, blackGlass);
            inv.setItem(row * 9 + 8, blackGlass);
        }
        for (int i = 45; i < 54; i++) inv.setItem(i, blackGlass);

        // Fill empty
        for (int i = 9; i < 45; i++) {
            if (inv.getItem(i) == null) inv.setItem(i, grayGlass);
        }

        // === ONLINE PLAYERS (excluding owner and current members) ===
        int[] slots = {
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34,
                37, 38, 39, 40, 41, 42, 43
        };

        int slotIdx = 0;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (slotIdx >= slots.length) break;
            if (online.getUniqueId().equals(region.getOwner())) continue;
            if (region.isMember(online.getUniqueId())) continue;

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta skullMeta = (SkullMeta) head.getItemMeta();
            skullMeta.setOwningPlayer(online);
            skullMeta.setDisplayName(ChatColor.GREEN + online.getName());
            skullMeta.setLore(Arrays.asList(
                    "",
                    ChatColor.GRAY + "Click para invitar a",
                    ChatColor.GRAY + "tu zona protegida.",
                    "",
                    ChatColor.YELLOW + "▶ Click para agregar"));
            head.setItemMeta(skullMeta);

            inv.setItem(slots[slotIdx], head);
            slotIdx++;
        }

        if (slotIdx == 0) {
            ItemStack noPlayers = new ItemStack(Material.BARRIER);
            ItemMeta npMeta = noPlayers.getItemMeta();
            npMeta.setDisplayName(ChatColor.RED + "No hay jugadores disponibles");
            npMeta.setLore(Arrays.asList("", ChatColor.GRAY + "Todos los jugadores online", ChatColor.GRAY + "ya son miembros o no hay nadie."));
            noPlayers.setItemMeta(npMeta);
            inv.setItem(22, noPlayers);
        }

        // === BACK BUTTON (slot 49) ===
        ItemStack backBtn = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backBtn.getItemMeta();
        backMeta.setDisplayName(ChatColor.YELLOW + "" + ChatColor.BOLD + "◀ VOLVER");
        backMeta.setLore(Arrays.asList("", ChatColor.GRAY + "Volver a miembros"));
        backBtn.setItemMeta(backMeta);
        inv.setItem(49, backBtn);

        player.openInventory(inv);
    }

    private ItemStack createGlass(Material material) {
        ItemStack glass = new ItemStack(material);
        ItemMeta meta = glass.getItemMeta();
        meta.setDisplayName(" ");
        glass.setItemMeta(meta);
        return glass;
    }

    public static boolean isMembersGUI(String title) {
        return title.contains("Miembros") && !title.contains("Invitar");
    }

    public static boolean isInviteGUI(String title) {
        return title.contains("Invitar Jugador");
    }
}
