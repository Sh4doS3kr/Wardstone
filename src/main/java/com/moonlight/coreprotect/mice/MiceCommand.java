package com.moonlight.coreprotect.mice;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.util.SmallCaps;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

/**
 * /ratones — GUI de crafteo de queso suizo (Swiss) para atraer ratones.
 *
 * Receta: 3 Wheat + 1 Milk Bucket → 4 Swiss (queso MythicMobs)
 *
 * El jugador mete los ingredientes en los slots marcados y pulsa el botón de craftear.
 */
public class MiceCommand implements CommandExecutor, Listener {

    private final CoreProtectPlugin plugin;

    private static final String GUI_TITLE = "§6§l🧀 §eQueso para Ratones";
    // Slots de ingredientes (en un inventario de 27 = 3 filas)
    // Layout:
    //  Row 0: [deco] [deco]  [deco]  [deco]  [deco]   [deco]  [deco]  [deco] [deco]
    //  Row 1: [deco] [wheat] [wheat] [wheat] [arrow]  [result] [deco]  [info] [deco]
    //  Row 2: [deco] [deco]  [milk]  [deco]  [craft]  [deco]   [deco]  [deco] [deco]
    private static final int SLOT_WHEAT_1 = 10;
    private static final int SLOT_WHEAT_2 = 11;
    private static final int SLOT_WHEAT_3 = 12;
    private static final int SLOT_MILK    = 20;
    private static final int SLOT_ARROW   = 13;
    private static final int SLOT_RESULT  = 14;
    private static final int SLOT_CRAFT   = 22;
    private static final int SLOT_INFO    = 16;

    private static final Set<Integer> INPUT_SLOTS = Set.of(SLOT_WHEAT_1, SLOT_WHEAT_2, SLOT_WHEAT_3, SLOT_MILK);

    // Track open GUIs
    private final Set<UUID> openGUIs = new HashSet<>();

    public MiceCommand(CoreProtectPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cSolo jugadores.");
            return true;
        }
        openGUI(player);
        return true;
    }

    private void openGUI(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, GUI_TITLE);

        // Fill decorative glass
        ItemStack deco = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, deco);
        }

        // Clear input slots (player puts items here)
        inv.setItem(SLOT_WHEAT_1, null);
        inv.setItem(SLOT_WHEAT_2, null);
        inv.setItem(SLOT_WHEAT_3, null);
        inv.setItem(SLOT_MILK, null);

        // Arrow indicator
        inv.setItem(SLOT_ARROW, createItem(Material.ARROW, "§7→ §eCraftear →"));

        // Result preview (no clickable yet)
        ItemStack preview = createItem(Material.PAPER, "§f§l🧀 §eSwiss §7x4");
        ItemMeta pm = preview.getItemMeta();
        pm.setLore(Arrays.asList(
                "§7Queso suizo para atraer ratones.",
                "§7Los ratones cercanos vendran",
                "§7corriendo hacia ti.",
                "",
                "§a¡Mete los ingredientes y craftea!"
        ));
        preview.setItemMeta(pm);
        inv.setItem(SLOT_RESULT, preview);

        // Craft button
        ItemStack craftBtn = createItem(Material.LIME_STAINED_GLASS_PANE, "§a§l¡CRAFTEAR!");
        ItemMeta cbm = craftBtn.getItemMeta();
        cbm.setLore(Arrays.asList(
                "§7Necesitas:",
                "§f 3x §eTrigo §7(Wheat)",
                "§f 1x §eCubo de Leche §7(Milk Bucket)",
                "",
                "§aClick para craftear §f4x Swiss"
        ));
        craftBtn.setItemMeta(cbm);
        inv.setItem(SLOT_CRAFT, craftBtn);

        // Info
        ItemStack info = createItem(Material.OAK_SIGN, "§b§lInfo Ratones");
        ItemMeta im = info.getItemMeta();
        im.setLore(Arrays.asList(
                "§7Los ratones aparecen de forma",
                "§7natural en el mundo survival.",
                "",
                "§eSostén queso §7en la mano para",
                "§7atraerlos. Click derecho sobre",
                "§7un ratón con queso para §aalimentarlo§7.",
                "",
                "§dDos ratones alimentados se",
                "§dreproducen y crían ratoncitos."
        ));
        info.setItemMeta(im);
        inv.setItem(SLOT_INFO, info);

        player.openInventory(inv);
        openGUIs.add(player.getUniqueId());
        player.playSound(player.getLocation(), Sound.BLOCK_BARREL_OPEN, 0.7f, 1.2f);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!openGUIs.contains(player.getUniqueId())) return;
        if (event.getView().getTitle() == null || !event.getView().getTitle().equals(GUI_TITLE)) return;

        int slot = event.getRawSlot();

        // Allow interaction with input slots (player can place/take items)
        if (INPUT_SLOTS.contains(slot)) {
            return; // allow normal click
        }

        // If clicking in player inventory, allow (shift-click will be handled)
        if (slot >= 27) {
            return; // allow picking from own inventory
        }

        // Cancel all other clicks in the GUI
        event.setCancelled(true);

        // Craft button
        if (slot == SLOT_CRAFT) {
            attemptCraft(player, event.getView().getTopInventory());
        }
    }

    private void attemptCraft(Player player, Inventory inv) {
        // Check ingredients
        int wheatCount = 0;
        int milkCount = 0;

        for (int s : new int[]{SLOT_WHEAT_1, SLOT_WHEAT_2, SLOT_WHEAT_3}) {
            ItemStack item = inv.getItem(s);
            if (item != null && item.getType() == Material.WHEAT) {
                wheatCount += item.getAmount();
            }
        }

        ItemStack milkItem = inv.getItem(SLOT_MILK);
        if (milkItem != null && milkItem.getType() == Material.MILK_BUCKET) {
            milkCount = milkItem.getAmount();
        }

        if (wheatCount < 3 || milkCount < 1) {
            player.sendMessage(SmallCaps.convert("§c§l✖ §cNecesitas 3 trigo y 1 cubo de leche."));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1f);
            return;
        }

        // Consume ingredients: 3 wheat, 1 milk bucket
        int wheatToRemove = 3;
        for (int s : new int[]{SLOT_WHEAT_1, SLOT_WHEAT_2, SLOT_WHEAT_3}) {
            if (wheatToRemove <= 0) break;
            ItemStack item = inv.getItem(s);
            if (item != null && item.getType() == Material.WHEAT) {
                int take = Math.min(item.getAmount(), wheatToRemove);
                item.setAmount(item.getAmount() - take);
                wheatToRemove -= take;
                if (item.getAmount() <= 0) inv.setItem(s, null);
            }
        }

        // Consume 1 milk bucket → return empty bucket
        ItemStack milk = inv.getItem(SLOT_MILK);
        if (milk != null) {
            milk.setAmount(milk.getAmount() - 1);
            if (milk.getAmount() <= 0) inv.setItem(SLOT_MILK, null);
        }
        // Give back empty bucket
        player.getInventory().addItem(new ItemStack(Material.BUCKET, 1));

        // Give Swiss cheese via MythicMobs command
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                "mm items give " + player.getName() + " swiss 4");

        player.sendMessage(SmallCaps.convert("§a§l✔ §f¡Has crafteado §e4x Swiss§f! Usa el queso para atraer ratones."));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.3f, 1.5f);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!openGUIs.remove(player.getUniqueId())) return;
        if (!event.getView().getTitle().equals(GUI_TITLE)) return;

        Inventory inv = event.getView().getTopInventory();
        // Return leftover items in input slots to player
        for (int s : INPUT_SLOTS) {
            ItemStack item = inv.getItem(s);
            if (item != null && item.getType() != Material.AIR) {
                HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item);
                // Drop on floor if inventory full
                for (ItemStack drop : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), drop);
                }
                inv.setItem(s, null);
            }
        }
    }

    private ItemStack createItem(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }
}
