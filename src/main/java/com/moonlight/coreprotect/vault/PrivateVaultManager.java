package com.moonlight.coreprotect.vault;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.block.ShulkerBox;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.moonlight.coreprotect.util.SmallCaps;
import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.essence.EssenceManager;
import com.moonlight.coreprotect.prestige.PrestigeManager;

/**
 * Gestiona los Private Vaults de los jugadores.
 * Cada vault es un inventario de 54 slots (doble cofre).
 *
 * Sistema de slots:
 * - Vault 1: Gratis para todos (base)
 * - Vault 2: Rango Luna
 * - Vault 3: Rango Nova
 * - Vault 4: Rango Eclipse
 * - Vault 5: Rango Moonlord
 * - Vault 6: Comprable con esencias + Prestigio 10
 * - Vault 7: Comprable con esencias + Prestigio 20
 * - Vault 8: Comprable con esencias + Prestigio 30
 * - Vault 9: Comprable con esencias + Prestigio 40
 * - Vault 10: Comprable con esencias + Prestigio 50
 */
public class PrivateVaultManager {

    private final CoreProtectPlugin plugin;
    private final File dataFolder;
    private final Map<UUID, Integer> purchasedSlots = new HashMap<>();
    private final File purchasedSlotsFile;
    // Fecha límite para jugadores que tienen items en vaults bloqueados
    private final Map<UUID, Long> vaultDeadlines = new HashMap<>();
    private final File deadlinesFile;
    
    static final String VAULT_TITLE_PREFIX = SmallCaps.convert("§8§l✦ §5§lVault Privado #");
    static final String SELECTOR_TITLE = SmallCaps.convert("§8§l✦ §5§lPrivate Vaults §8§l✦");
    
    private static final int ESSENCE_COST_PER_SLOT = 50; // Coste por slot extra
    private static final int MAX_PURCHASABLE_SLOTS = 5; // Vaults 6-10

    // Prestigio requerido para COMPRAR vaults de esencias (index = vaultNumber)
    // Vaults 2-5 NO usan prestigio, usan rango
    private static final int[] VAULT_PRESTIGE_REQ = {0, 0, 0, 0, 0, 0, 10, 20, 30, 40, 50};

    // Nombres de rango por vault (2-5)
    private static final String[] RANK_NAMES = {null, null, "Luna", "Nova", "Eclipse", "Moonlord"};
    private static final String[] RANK_PERMS = {null, null, "essentials.kits.luna", "essentials.kits.nova", "essentials.kits.eclipse", "essentials.kits.moonlord"};
    private static final String[] RANK_COLORS = {null, null, "§f", "§b", "§6", "§d"};

    private static final long DEADLINE_DURATION_MS = 7L * 24 * 60 * 60 * 1000; // 7 días

    public PrivateVaultManager(CoreProtectPlugin plugin) {
        this.plugin = plugin;
        this.dataFolder = new File(plugin.getDataFolder(), "vaults");
        this.purchasedSlotsFile = new File(plugin.getDataFolder(), "purchased_vault_slots.yml");
        this.deadlinesFile = new File(plugin.getDataFolder(), "vault_deadlines.yml");
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        loadPurchasedSlots();
        loadDeadlines();
    }

    /**
     * Abre un vault para el jugador.
     * 
     * @param player      El jugador
     * @param vaultNumber Número del vault (1-based)
     * @return true si se abrió correctamente
     */
    public boolean openVault(Player player, int vaultNumber) {
        // --- Vaults de RANGO (2-5): verificar permiso de rango ---
        if (vaultNumber >= 2 && vaultNumber <= 5) {
            if (!hasRankForVault(player, vaultNumber)) {
                boolean hasItems = vaultHasItems(player.getUniqueId(), vaultNumber);
                if (hasItems) {
                    long deadline = getOrCreateDeadline(player.getUniqueId());
                    String deadlineStr = formatDeadline(deadline);
                    player.sendMessage("");
                    player.sendMessage(SmallCaps.convert("§c§l⚠ §eAVISO: §fNecesitas rango " + RANK_COLORS[vaultNumber] + RANK_NAMES[vaultNumber] + " §fpara usar este vault."));
                    player.sendMessage(SmallCaps.convert("§7Retira tus items antes del §c§l" + deadlineStr + "§7."));
                    player.sendMessage(SmallCaps.convert("§7Después de esa fecha, el contenido será §c§lELIMINADO§7."));
                    player.sendMessage("");
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
                    Inventory vault = loadVault(player.getUniqueId(), vaultNumber);
                    player.openInventory(vault);
                    return true;
                } else {
                    player.sendMessage(SmallCaps.convert("§c§l✖ §cNecesitas rango " + RANK_COLORS[vaultNumber] + RANK_NAMES[vaultNumber] + " §cpara el Vault #" + vaultNumber + "."));
                    player.sendMessage(SmallCaps.convert("§7Compra tu rango en §e/tienda §7para acceder."));
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
                    return false;
                }
            }
        }

        // --- Vaults de ESENCIAS+PRESTIGIO (6-10): verificar compra Y prestigio ---
        if (vaultNumber >= 6 && vaultNumber <= 10) {
            int purchased = getPurchasedSlots(player.getUniqueId());
            int essenceVaultIndex = vaultNumber - 5; // vault 6=1, 7=2, ..., 10=5
            boolean bought = purchased >= essenceVaultIndex;
            int reqPrestige = getRequiredPrestige(vaultNumber);
            PrestigeManager pm2 = plugin.getPrestigeManager();
            int playerPrestige = pm2 != null ? pm2.getPrestige(player.getUniqueId()) : 0;
            boolean hasPrestige = playerPrestige >= reqPrestige;

            // Si no cumple algún requisito (no comprado o no tiene prestigio)
            if (!bought || !hasPrestige) {
                boolean hasItems = vaultHasItems(player.getUniqueId(), vaultNumber);
                if (hasItems) {
                    long deadline = getOrCreateDeadline(player.getUniqueId());
                    String deadlineStr = formatDeadline(deadline);
                    String reason = !hasPrestige ? "§dPrestigio " + reqPrestige : "comprarlo con esencias";
                    player.sendMessage("");
                    player.sendMessage(SmallCaps.convert("§c§l⚠ §eAVISO: §fNecesitas " + reason + " §fpara usar este vault."));
                    player.sendMessage(SmallCaps.convert("§7Retira tus items antes del §c§l" + deadlineStr + "§7."));
                    player.sendMessage(SmallCaps.convert("§7Después de esa fecha, el contenido será §c§lELIMINADO§7."));
                    player.sendMessage("");
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
                    Inventory vault = loadVault(player.getUniqueId(), vaultNumber);
                    player.openInventory(vault);
                    return true;
                } else {
                    if (!bought) {
                        player.sendMessage(SmallCaps.convert("§c§l✖ §cNo has comprado el Vault #" + vaultNumber + "."));
                    } else {
                        player.sendMessage(SmallCaps.convert("§c§l✖ §cNecesitas §dPrestigio " + reqPrestige + " §cpara el Vault #" + vaultNumber + "."));
                    }
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
                    return false;
                }
            }
        }

        int maxVaults = getMaxVaults(player);
        if (vaultNumber < 1 || vaultNumber > maxVaults) {
            player.sendMessage(SmallCaps.convert("§c§l✖ §cNo tienes acceso al vault #" + vaultNumber + "."));
            player.sendMessage(SmallCaps.convert("§7Tienes acceso a §e" + maxVaults + " §7vault(s)."));
            return false;
        }

        Inventory vault = loadVault(player.getUniqueId(), vaultNumber);
        player.openInventory(vault);
        return true;
    }

    /**
     * Comprueba si el jugador tiene el rango necesario para un vault (2-5).
     */
    private boolean hasRankForVault(Player player, int vaultNumber) {
        if (vaultNumber < 2 || vaultNumber > 5) return true;
        // Moonlord tiene acceso a todos, Eclipse a 2-4, Nova a 2-3, Luna a 2
        if (player.hasPermission("essentials.kits.moonlord")) return true;
        if (player.hasPermission("essentials.kits.eclipse") && vaultNumber <= 4) return true;
        if (player.hasPermission("essentials.kits.nova") && vaultNumber <= 3) return true;
        if (player.hasPermission("essentials.kits.luna") && vaultNumber <= 2) return true;
        return false;
    }

    /**
     * Abre el menú selector de vaults para el jugador (estilo /ah).
     */
    // Slot mapping por sección — usado también en el listener
    // Fila 2: BASE (vault 1)
    static final int BASE_SLOT = 11;
    // Fila 3: RANGO (vaults 2-5)
    static final int[] RANK_SLOTS = {20, 21, 22, 23}; // vaults 2-5
    // Fila 4: ESENCIAS+PRESTIGIO (vaults 6-10)
    static final int[] ESSENCE_SLOTS = {29, 30, 31, 32, 33}; // vaults 6-10

    /**
     * Obtiene el número de vault a partir del slot en el GUI, o -1 si no es vault.
     */
    public int getVaultNumberFromSlot(int slot) {
        if (slot == BASE_SLOT) return 1;
        for (int i = 0; i < RANK_SLOTS.length; i++) {
            if (RANK_SLOTS[i] == slot) return i + 2; // vaults 2-5
        }
        for (int i = 0; i < ESSENCE_SLOTS.length; i++) {
            if (ESSENCE_SLOTS[i] == slot) return i + 6; // vaults 6-10
        }
        return -1;
    }

    public void openVaultSelector(Player player) {
        Inventory gui = Bukkit.createInventory(null, 54, SELECTOR_TITLE);
        int maxVaults = getMaxVaults(player);
        int purchasedCount = getPurchasedSlots(player.getUniqueId());
        int rankSlots = getRankExtraSlots(player);
        EssenceManager essenceMgr = plugin.getEssenceManager();
        int essences = essenceMgr.getEssences(player.getUniqueId());
        PrestigeManager pm = plugin.getPrestigeManager();
        int playerPrestige = pm != null ? pm.getPrestige(player.getUniqueId()) : 0;

        // === Bordes decorativos ===
        ItemStack border = createDecor(Material.BLACK_STAINED_GLASS_PANE, "§r");
        for (int i = 0; i < 9; i++) gui.setItem(i, border);
        for (int i = 45; i < 54; i++) gui.setItem(i, border);
        gui.setItem(9, border); gui.setItem(18, border); gui.setItem(27, border); gui.setItem(36, border);
        gui.setItem(17, border); gui.setItem(26, border); gui.setItem(35, border); gui.setItem(44, border);

        // === Header: Info ===
        gui.setItem(4, createItem(Material.ENDER_CHEST,
                "§5§l✦ Private Vaults",
                "§7Tus espacios de almacenamiento seguros.",
                "",
                "§e▸ Vaults disponibles: §f" + maxVaults,
                "§7  · Base: §a1 gratis",
                "§7  · Rango: §d+" + rankSlots + " slots",
                "§7  · Comprados: §b+" + purchasedCount + " slots",
                "",
                "§b▸ Tus esencias: §f" + essences,
                "§d▸ Tu prestigio: §f" + playerPrestige));

        // ══════════════════════════════════════
        // FILA 2: BASE (Vault 1)
        // ══════════════════════════════════════
        gui.setItem(10, createSectionLabel(Material.LIME_STAINED_GLASS_PANE,
                "§a§l✦ BASE", "§7Vault gratuito."));
        gui.setItem(BASE_SLOT, createVaultItem(1, true, "§aGratis (Base)"));

        // ══════════════════════════════════════
        // FILA 3: RANGO (Vaults 2-5)
        // ══════════════════════════════════════
        gui.setItem(19, createSectionLabel(Material.PURPLE_STAINED_GLASS_PANE,
                "§d§l✦ RANGO", "§7Requieren rango VIP."));
        for (int i = 0; i < 4; i++) {
            int vaultNum = i + 2;
            int guiSlot = RANK_SLOTS[i];
            boolean hasRank = hasRankForVault(player, vaultNum);
            boolean hasItems = vaultHasItems(player.getUniqueId(), vaultNum);

            if (hasRank) {
                gui.setItem(guiSlot, createVaultItem(vaultNum, true, RANK_COLORS[vaultNum] + "Rango " + RANK_NAMES[vaultNum]));
            } else {
                gui.setItem(guiSlot, createRankLockedVaultItem(vaultNum, hasItems, player.getUniqueId()));
            }
        }

        // ══════════════════════════════════════
        // FILA 4: ESENCIAS + PRESTIGIO (Vaults 6-10)
        // ══════════════════════════════════════
        gui.setItem(28, createSectionLabel(Material.CYAN_STAINED_GLASS_PANE,
                "§b§l✦ ESENCIAS + PRESTIGIO",
                "§7Comprables con esencias.",
                "§7Requieren nivel de prestigio.",
                "§b▸ Coste: §f" + ESSENCE_COST_PER_SLOT + " Esencias/slot"));

        for (int i = 0; i < 5; i++) {
            int vaultNum = i + 6; // vaults 6-10
            int guiSlot = ESSENCE_SLOTS[i];
            int reqPrestige = getRequiredPrestige(vaultNum);
            int essenceVaultIndex = vaultNum - 5;
            boolean bought = purchasedCount >= essenceVaultIndex;
            boolean hasPrestige = playerPrestige >= reqPrestige;
            String glyph = "<glyph:rank" + reqPrestige + ">";

            if (bought && hasPrestige) {
                // Totalmente desbloqueado
                gui.setItem(guiSlot, createVaultItem(vaultNum, true, "§b" + ESSENCE_COST_PER_SLOT + "e + " + glyph + " §dP" + reqPrestige));
            } else if (bought && !hasPrestige) {
                // Comprado pero sin prestigio → bloqueado con warning
                boolean hasItems = vaultHasItems(player.getUniqueId(), vaultNum);
                gui.setItem(guiSlot, createEssenceLockedVaultItem(vaultNum, reqPrestige, hasItems, player.getUniqueId(), true));
            } else {
                // No comprado → mostrar como comprable
                gui.setItem(guiSlot, createPrestigePurchasableItem(vaultNum, reqPrestige, playerPrestige, essences));
            }
        }

        // === Fila 5: Info ===
        gui.setItem(40, createItem(Material.BOOK,
                "§e§l¿ Cómo conseguir más vaults?",
                "§a§l▸ BASE",
                "§7  · Vault #1 gratis para todos",
                "",
                "§d§l▸ RANGO",
                "§7  · §fLuna: §aVault #2",
                "§7  · §bNova: §aVault #3",
                "§7  · §6Eclipse: §aVault #4",
                "§7  · §dMoonlord: §aVault #5",
                "",
                "§b§l▸ ESENCIAS + PRESTIGIO",
                "§7  · §b" + ESSENCE_COST_PER_SLOT + "e §7+ <glyph:rank10> §6P10: §aVault #6",
                "§7  · §b" + ESSENCE_COST_PER_SLOT + "e §7+ <glyph:rank20> §fP20: §aVault #7",
                "§7  · §b" + ESSENCE_COST_PER_SLOT + "e §7+ <glyph:rank30> §eP30: §aVault #8",
                "§7  · §b" + ESSENCE_COST_PER_SLOT + "e §7+ <glyph:rank40> §9P40: §aVault #9",
                "§7  · §b" + ESSENCE_COST_PER_SLOT + "e §7+ <glyph:rank50> §aP50: §aVault #10"));

        // Warning si tiene deadline activa
        Long deadline = vaultDeadlines.get(player.getUniqueId());
        if (deadline != null && deadline > System.currentTimeMillis()) {
            gui.setItem(38, createItem(Material.CLOCK,
                    "§c§l⚠ AVISO IMPORTANTE",
                    "§7Tienes items en vaults bloqueados.",
                    "§7Retíralos o serán eliminados.",
                    "",
                    "§cFecha límite: §f" + formatDeadline(deadline),
                    "§7Usa §f/pv §7para retirarlos."));
        }

        player.openInventory(gui);
        player.playSound(player.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 0.7f, 1.2f);
    }

    private ItemStack createVaultItem(int num, boolean unlocked, String source) {
        ItemStack item = new ItemStack(Material.CHEST);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(SmallCaps.convert("§a§l✔ Vault #" + num));
        List<String> lore = new ArrayList<>();
        lore.add(SmallCaps.convert("§7Tu espacio seguro personal."));
        lore.add("");
        lore.add(SmallCaps.convert("§7Origen: " + source));
        lore.add("");
        lore.add(SmallCaps.convert("§a§l➜ Click para abrir"));
        meta.setLore(lore);
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Item para vault bloqueado por RANGO (vaults 2-5).
     */
    private ItemStack createRankLockedVaultItem(int num, boolean hasItems, UUID uuid) {
        ItemStack item = new ItemStack(hasItems ? Material.CHEST : Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        String rankColor = RANK_COLORS[num];
        String rankName = RANK_NAMES[num];
        meta.setDisplayName(SmallCaps.convert(rankColor + "§l✦ Vault #" + num + " §8(§c" + rankName + "§8)"));
        List<String> lore = new ArrayList<>();
        lore.add(SmallCaps.convert("§7Requiere rango " + rankColor + rankName + " §7para usar."));
        lore.add("");
        if (hasItems) {
            long deadline = getOrCreateDeadline(uuid);
            String deadlineStr = formatDeadline(deadline);
            long remaining = deadline - System.currentTimeMillis();
            String timeLeft = formatTimeLeft(remaining);
            lore.add(SmallCaps.convert("§c§l⚠ §eTienes items dentro!"));
            lore.add(SmallCaps.convert("§7Retíralos antes del §c§l" + deadlineStr));
            lore.add(SmallCaps.convert("§7Tiempo restante: §c" + timeLeft));
            lore.add(SmallCaps.convert("§7Después serán §c§lELIMINADOS§7."));
            lore.add("");
            lore.add(SmallCaps.convert("§e§l➜ Click izq §7para abrir y retirar"));
            lore.add(SmallCaps.convert("§d§l➜ Click der §7para empaquetar en §dShulker Boxes"));
        } else {
            lore.add(SmallCaps.convert("§7Compra el rango " + rankColor + rankName + " §7en §e/tienda§7."));
            lore.add("");
            lore.add(SmallCaps.convert("§c§lBLOQUEADO"));
        }
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Item para vault comprable con esencias + prestigio (vaults 6-10).
     */
    private ItemStack createPrestigePurchasableItem(int num, int reqPrestige, int playerPrestige, int essences) {
        boolean hasPrestige = playerPrestige >= reqPrestige;
        boolean hasEssences = essences >= ESSENCE_COST_PER_SLOT;
        boolean canBuy = hasPrestige && hasEssences;
        String glyph = "<glyph:rank" + reqPrestige + "> ";
        String tierColor = getPrestigeTierColor(reqPrestige);

        ItemStack item = new ItemStack(canBuy ? Material.LIME_STAINED_GLASS_PANE : Material.ORANGE_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(SmallCaps.convert("§e§l▸ " + glyph + "Vault #" + num + " §8(§eCOMPRABLE§8)"));
        List<String> lore = new ArrayList<>();
        lore.add(SmallCaps.convert("§7Desbloquea con esencias y prestigio."));
        lore.add("");
        lore.add(SmallCaps.convert("§7Requisitos:"));
        lore.add(SmallCaps.convert((hasPrestige ? "§a  ✔ " : "§c  ✖ ") + glyph + tierColor + "Prestigio " + reqPrestige));
        lore.add(SmallCaps.convert((hasEssences ? "§a  ✔ " : "§c  ✖ ") + "§b" + ESSENCE_COST_PER_SLOT + " Esencias"));
        lore.add("");
        if (canBuy) {
            lore.add(SmallCaps.convert("§a§l➜ Click para comprar"));
        } else {
            if (!hasPrestige) lore.add(SmallCaps.convert("§c§l✖ Falta prestigio §7(§e/prestigio§7)"));
            if (!hasEssences) lore.add(SmallCaps.convert("§c§l✖ Faltan esencias §7(tienes §b" + essences + "§7)"));
        }
        meta.setLore(lore);
        if (canBuy) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Item para vault de esencias comprado pero sin prestigio (bloqueado con warning).
     */
    private ItemStack createEssenceLockedVaultItem(int num, int reqPrestige, boolean hasItems, UUID uuid, boolean bought) {
        String glyph = "<glyph:rank" + reqPrestige + ">";
        String tierColor = getPrestigeTierColor(reqPrestige);
        ItemStack item = new ItemStack(hasItems ? Material.CHEST : Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(SmallCaps.convert("§c§l✦ Vault #" + num + " §8(§c" + glyph + " P" + reqPrestige + "§8)"));
        List<String> lore = new ArrayList<>();
        lore.add(SmallCaps.convert("§7Requiere " + glyph + " " + tierColor + "Prestigio " + reqPrestige + " §7para usar."));
        if (bought) lore.add(SmallCaps.convert("§a✔ Comprado con esencias"));
        lore.add("");
        if (hasItems) {
            long deadline = getOrCreateDeadline(uuid);
            String deadlineStr = formatDeadline(deadline);
            long remaining = deadline - System.currentTimeMillis();
            String timeLeft = formatTimeLeft(remaining);
            lore.add(SmallCaps.convert("§c§l⚠ §eTienes items dentro!"));
            lore.add(SmallCaps.convert("§7Se bloqueará en §c§l" + timeLeft));
            lore.add(SmallCaps.convert("§7Fecha límite: §c§l" + deadlineStr));
            lore.add(SmallCaps.convert("§7Retira tus items o serán §c§lELIMINADOS§7."));
            lore.add("");
            lore.add(SmallCaps.convert("§e§l➜ Click izq §7para abrir y retirar"));
            lore.add(SmallCaps.convert("§d§l➜ Click der §7para empaquetar en §dShulker Boxes"));
        } else {
            lore.add(SmallCaps.convert("§7Sube tu prestigio con §e/prestigio§7."));
            lore.add("");
            lore.add(SmallCaps.convert("§c§lBLOQUEADO"));
        }
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createLockedVaultItem(int num) {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(SmallCaps.convert("§c§l✖ Vault #" + num + " §8(§cBLOQUEADO§8)"));
        List<String> lore = new ArrayList<>();
        lore.add(SmallCaps.convert("§7No tienes acceso a este espacio."));
        lore.add("");
        lore.add(SmallCaps.convert("§c§lBLOQUEADO"));
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createSectionLabel(Material mat, String name, String... loreLines) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        if (loreLines.length > 0) {
            meta.setLore(Arrays.asList(loreLines));
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createItem(org.bukkit.Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(java.util.Arrays.asList(lore));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createDecor(org.bukkit.Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Calcula cuántos vaults puede tener el jugador (base + rango + comprados).
     */
    public int getMaxVaults(Player player) {
        int base = 1; // Vault 1 gratis
        int rank = getRankExtraSlots(player);
        int purchased = getPurchasedSlots(player.getUniqueId());
        return base + rank + purchased;
    }

    /**
     * Obtiene slots extra por rango (vaults 2-5).
     * Luna=+1, Nova=+2, Eclipse=+3, Moonlord=+4
     */
    public int getRankExtraSlots(Player player) {
        if (player.hasPermission("essentials.kits.moonlord")) return 4;
        if (player.hasPermission("essentials.kits.eclipse")) return 3;
        if (player.hasPermission("essentials.kits.nova")) return 2;
        if (player.hasPermission("essentials.kits.luna")) return 1;
        return 0;
    }

    /**
     * Obtiene el prestigio requerido para un vault.
     */
    public int getRequiredPrestige(int vaultNumber) {
        if (vaultNumber >= 0 && vaultNumber < VAULT_PRESTIGE_REQ.length) {
            return VAULT_PRESTIGE_REQ[vaultNumber];
        }
        return 0;
    }

    private String getPrestigeTierColor(int prestige) {
        if (prestige <= 10) return "§6";
        if (prestige <= 20) return "§f";
        if (prestige <= 30) return "§e";
        if (prestige <= 40) return "§9";
        if (prestige <= 50) return "§a";
        return "§b";
    }

    /**
     * Obtiene slots comprados por el jugador.
     */
    public int getPurchasedSlots(UUID uuid) {
        return purchasedSlots.getOrDefault(uuid, 0);
    }

    /**
     * Compra un slot extra con esencias + prestigio (Vaults 6-10).
     */
    public boolean purchaseSlot(Player player) {
        UUID uuid = player.getUniqueId();
        int current = getPurchasedSlots(uuid);
        
        if (current >= MAX_PURCHASABLE_SLOTS) {
            player.sendMessage(SmallCaps.convert("§c§l✖ §cYa has comprado el máximo de slots (§e" + MAX_PURCHASABLE_SLOTS + "§c)."));
            return false;
        }

        // Verificar prestigio requerido para el siguiente vault
        int nextVaultNum = 6 + current; // vault 6, 7, 8, 9, 10
        int reqPrestige = getRequiredPrestige(nextVaultNum);
        PrestigeManager pm = plugin.getPrestigeManager();
        int playerPrestige = pm != null ? pm.getPrestige(uuid) : 0;
        if (playerPrestige < reqPrestige) {
            player.sendMessage(SmallCaps.convert("§c§l✖ §cNecesitas §dPrestigio " + reqPrestige + " §cpara comprar el Vault #" + nextVaultNum + "."));
            player.sendMessage(SmallCaps.convert("§7Tu prestigio: §e" + playerPrestige + "§7. Sube con §e/prestigio§7."));
            return false;
        }

        EssenceManager essenceMgr = plugin.getEssenceManager();
        if (!essenceMgr.hasEssences(uuid, ESSENCE_COST_PER_SLOT)) {
            player.sendMessage(SmallCaps.convert("§c§l✖ §cNecesitas §b" + ESSENCE_COST_PER_SLOT + " Esencias §cpara comprar un slot."));
            player.sendMessage(SmallCaps.convert("§7Tienes: §b" + essenceMgr.getEssences(uuid) + " Esencias"));
            return false;
        }

        essenceMgr.removeEssences(uuid, ESSENCE_COST_PER_SLOT);
        essenceMgr.saveData();
        purchasedSlots.put(uuid, current + 1);
        savePurchasedSlots();

        player.sendMessage(SmallCaps.convert("§3§l✦ §a¡Vault #" + nextVaultNum + " desbloqueado!"));
        player.sendMessage(SmallCaps.convert("§7Has gastado §b" + ESSENCE_COST_PER_SLOT + " Esencias§7."));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
        return true;
    }

    /**
     * Carga slots comprados desde archivo.
     */
    private void loadPurchasedSlots() {
        if (!purchasedSlotsFile.exists()) return;
        FileConfiguration config = YamlConfiguration.loadConfiguration(purchasedSlotsFile);
        for (String key : config.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                int slots = config.getInt(key);
                purchasedSlots.put(uuid, slots);
            } catch (Exception ignored) {}
        }
    }

    /**
     * Guarda slots comprados a archivo.
     */
    private void savePurchasedSlots() {
        FileConfiguration config = new YamlConfiguration();
        for (Map.Entry<UUID, Integer> entry : purchasedSlots.entrySet()) {
            config.set(entry.getKey().toString(), entry.getValue());
        }
        try {
            config.save(purchasedSlotsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Error guardando purchased_vault_slots.yml: " + e.getMessage());
        }
    }

    // ===========================
    // DEADLINE SYSTEM
    // ===========================

    private void loadDeadlines() {
        if (!deadlinesFile.exists()) return;
        FileConfiguration config = YamlConfiguration.loadConfiguration(deadlinesFile);
        for (String key : config.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                long deadline = config.getLong(key);
                vaultDeadlines.put(uuid, deadline);
            } catch (Exception ignored) {}
        }
    }

    private void saveDeadlines() {
        FileConfiguration config = new YamlConfiguration();
        for (Map.Entry<UUID, Long> entry : vaultDeadlines.entrySet()) {
            config.set(entry.getKey().toString(), entry.getValue());
        }
        try {
            config.save(deadlinesFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Error guardando vault_deadlines.yml: " + e.getMessage());
        }
    }

    public long getOrCreateDeadline(UUID uuid) {
        Long existing = vaultDeadlines.get(uuid);
        if (existing != null) return existing;
        long deadline = System.currentTimeMillis() + DEADLINE_DURATION_MS;
        vaultDeadlines.put(uuid, deadline);
        saveDeadlines();
        return deadline;
    }

    /**
     * Verifica si un vault tiene items almacenados.
     */
    public boolean vaultHasItems(UUID uuid, int vaultNumber) {
        File file = getPlayerFile(uuid);
        if (!file.exists()) return false;
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        String path = "vault." + vaultNumber;
        if (!config.contains(path)) return false;
        for (int i = 0; i < 54; i++) {
            if (config.contains(path + "." + i)) return true;
        }
        return false;
    }

    /**
     * Verifica todos los jugadores y elimina items de vaults con deadline expirada.
     */
    public void checkExpiredDeadlines() {
        long now = System.currentTimeMillis();
        List<UUID> expired = new ArrayList<>();
        for (Map.Entry<UUID, Long> entry : vaultDeadlines.entrySet()) {
            if (entry.getValue() <= now) {
                expired.add(entry.getKey());
            }
        }
        for (UUID uuid : expired) {
            org.bukkit.OfflinePlayer offp = Bukkit.getOfflinePlayer(uuid);
            Player online = offp.isOnline() ? offp.getPlayer() : null;
            // Verificar vaults de rango (2-5)
            for (int v = 2; v <= 5; v++) {
                if (online != null && !hasRankForVault(online, v) && vaultHasItems(uuid, v)) {
                    clearVault(uuid, v);
                    plugin.getLogger().info("[PV] Vault #" + v + " de " + uuid + " vaciado por deadline expirada (rango).");
                } else if (online == null && vaultHasItems(uuid, v)) {
                    clearVault(uuid, v);
                    plugin.getLogger().info("[PV] Vault #" + v + " de " + uuid + " vaciado por deadline expirada (offline).");
                }
            }
            // Verificar vaults de esencias (6-10) — requieren prestigio
            PrestigeManager pm = plugin.getPrestigeManager();
            int prestige = pm != null ? pm.getPrestige(uuid) : 0;
            for (int v = 6; v <= 10; v++) {
                int req = getRequiredPrestige(v);
                if (prestige < req && vaultHasItems(uuid, v)) {
                    clearVault(uuid, v);
                    plugin.getLogger().info("[PV] Vault #" + v + " de " + uuid + " vaciado por deadline expirada (prestigio).");
                }
            }
            vaultDeadlines.remove(uuid);
        }
        if (!expired.isEmpty()) saveDeadlines();
    }

    /**
     * Elimina todos los items de un vault.
     */
    private void clearVault(UUID uuid, int vaultNumber) {
        File file = getPlayerFile(uuid);
        if (!file.exists()) return;
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        config.set("vault." + vaultNumber, null);
        try { config.save(file); } catch (IOException ignored) {}
    }

    /**
     * Comprueba si un jugador tiene vaults bloqueados con items (rango o prestigio).
     */
    public boolean hasVaultsAtRisk(UUID uuid) {
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) return false;
        // Verificar vaults de rango (2-5)
        for (int v = 2; v <= 5; v++) {
            if (!hasRankForVault(player, v) && vaultHasItems(uuid, v)) return true;
        }
        // Verificar vaults de esencias (6-10) — requieren prestigio
        PrestigeManager pm = plugin.getPrestigeManager();
        int prestige = pm != null ? pm.getPrestige(uuid) : 0;
        for (int v = 6; v <= 10; v++) {
            int req = getRequiredPrestige(v);
            if (prestige < req && vaultHasItems(uuid, v)) return true;
        }
        return false;
    }

    /**
     * Obtiene la deadline de un jugador, o -1 si no tiene.
     */
    public long getDeadline(UUID uuid) {
        return vaultDeadlines.getOrDefault(uuid, -1L);
    }

    private String formatDeadline(long epochMs) {
        java.time.LocalDateTime dt = java.time.Instant.ofEpochMilli(epochMs)
                .atZone(java.time.ZoneId.of("Europe/Madrid")).toLocalDateTime();
        return String.format("%02d/%02d/%d %02d:%02d",
                dt.getDayOfMonth(), dt.getMonthValue(), dt.getYear(),
                dt.getHour(), dt.getMinute());
    }

    private String formatTimeLeft(long ms) {
        if (ms <= 0) return "§c§lEXPIRADO";
        long days = ms / (24 * 60 * 60 * 1000);
        long hours = (ms % (24 * 60 * 60 * 1000)) / (60 * 60 * 1000);
        if (days > 0) return days + "d " + hours + "h";
        long minutes = (ms % (60 * 60 * 1000)) / (60 * 1000);
        return hours + "h " + minutes + "m";
    }

    /**
     * Carga un vault desde el archivo YAML del jugador.
     */
    public Inventory loadVault(UUID uuid, int vaultNumber) {
        String title = VAULT_TITLE_PREFIX + vaultNumber + " §5§l✦";
        Inventory vault = Bukkit.createInventory(null, 54, title);

        File file = getPlayerFile(uuid);
        if (!file.exists())
            return vault;

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        String path = "vault." + vaultNumber;

        if (config.contains(path)) {
            for (int i = 0; i < 54; i++) {
                if (config.contains(path + "." + i)) {
                    ItemStack item = config.getItemStack(path + "." + i);
                    if (item != null) {
                        vault.setItem(i, item);
                    }
                }
            }
        }

        return vault;
    }

    /**
     * Guarda un vault al archivo YAML del jugador.
     */
    public void saveVault(UUID uuid, int vaultNumber, Inventory vault) {
        File file = getPlayerFile(uuid);
        FileConfiguration config;

        if (file.exists()) {
            config = YamlConfiguration.loadConfiguration(file);
        } else {
            config = new YamlConfiguration();
        }

        String path = "vault." + vaultNumber;

        // Limpiar sección anterior
        config.set(path, null);

        for (int i = 0; i < vault.getSize(); i++) {
            ItemStack item = vault.getItem(i);
            if (item != null) {
                config.set(path + "." + i, item);
            }
        }

        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("[PV] Error guardando vault de " + uuid + ": " + e.getMessage());
        }
    }

    /**
     * Extrae el número de vault del título del inventario.
     * 
     * @return el número de vault, o -1 si no es un vault válido
     */
    public int getVaultNumberFromTitle(String title) {
        if (title == null || !title.startsWith(VAULT_TITLE_PREFIX))
            return -1;
        try {
            // Extraer número entre el prefix y el suffix
            String rest = title.substring(VAULT_TITLE_PREFIX.length());
            int endIdx = rest.indexOf(' ');
            if (endIdx == -1)
                endIdx = rest.length();
            return Integer.parseInt(rest.substring(0, endIdx));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private File getPlayerFile(UUID uuid) {
        return new File(dataFolder, uuid.toString() + ".yml");
    }

    /**
     * Comprueba si un vault esta bloqueado para el jugador (no cumple requisitos).
     * Solo retorna true para vaults 2-10 que no tienen rango/prestigio/compra.
     */
    public boolean isVaultBlocked(Player player, int vaultNumber) {
        if (vaultNumber < 2 || vaultNumber > 10) return false;
        UUID uuid = player.getUniqueId();

        if (vaultNumber >= 2 && vaultNumber <= 5) {
            return !hasRankForVault(player, vaultNumber);
        }

        // Vaults 6-10
        int purchased = getPurchasedSlots(uuid);
        int essenceVaultIndex = vaultNumber - 5;
        boolean bought = purchased >= essenceVaultIndex;
        int reqPrestige = getRequiredPrestige(vaultNumber);
        PrestigeManager pm = plugin.getPrestigeManager();
        int playerPrestige = pm != null ? pm.getPrestige(uuid) : 0;
        boolean hasPrestige = playerPrestige >= reqPrestige;
        return !bought || !hasPrestige;
    }

    /**
     * Empaqueta los items de un vault bloqueado en shulker boxes y los entrega al jugador.
     * El vault se vacia despues de empaquetar exitosamente.
     */
    public void packVaultToShulkers(Player player, int vaultNumber) {
        UUID uuid = player.getUniqueId();
        if (!isVaultBlocked(player, vaultNumber)) {
            player.sendMessage(SmallCaps.convert("§c§l✖ §cEste vault no esta bloqueado."));
            return;
        }

        // Cargar items del vault
        Inventory vault = loadVault(uuid, vaultNumber);
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < vault.getSize(); i++) {
            ItemStack it = vault.getItem(i);
            if (it != null && it.getType() != Material.AIR) {
                items.add(it);
            }
        }

        if (items.isEmpty()) {
            player.sendMessage(SmallCaps.convert("§c§l✖ §cEste vault no tiene items para empaquetar."));
            return;
        }

        // Filtrar items no empaquetables (shulker boxes ya existentes con contenido)
        // y empaquetar de 27 en 27 en shulkers
        List<ItemStack> shulkers = new ArrayList<>();
        List<ItemStack> overflow = new ArrayList<>();
        int idx = 0;
        int shulkerIdx = 1;
        while (idx < items.size()) {
            ItemStack shulkerItem = new ItemStack(Material.SHULKER_BOX);
            ItemMeta sMeta = shulkerItem.getItemMeta();
            if (!(sMeta instanceof BlockStateMeta)) {
                // No deberia pasar, pero por seguridad
                overflow.addAll(items.subList(idx, items.size()));
                break;
            }
            BlockStateMeta bsm = (BlockStateMeta) sMeta;
            ShulkerBox sb = (ShulkerBox) bsm.getBlockState();
            int placed = 0;
            while (placed < 27 && idx < items.size()) {
                ItemStack it = items.get(idx);
                // No anidar shulkers con items dentro: si es un shulker con contenido, ponerlo aparte
                if (it.getType().name().endsWith("SHULKER_BOX")) {
                    ItemMeta im = it.getItemMeta();
                    if (im instanceof BlockStateMeta) {
                        BlockStateMeta innerBsm = (BlockStateMeta) im;
                        if (innerBsm.getBlockState() instanceof ShulkerBox) {
                            ShulkerBox innerSb = (ShulkerBox) innerBsm.getBlockState();
                            boolean hasContent = false;
                            for (ItemStack inner : innerSb.getInventory().getContents()) {
                                if (inner != null && inner.getType() != Material.AIR) { hasContent = true; break; }
                            }
                            if (hasContent) {
                                overflow.add(it);
                                idx++;
                                continue;
                            }
                        }
                    }
                }
                sb.getInventory().setItem(placed, it);
                placed++;
                idx++;
            }
            bsm.setBlockState(sb);
            bsm.setDisplayName(SmallCaps.convert("§5§l✦ Vault #" + vaultNumber + " §8- §fParte " + shulkerIdx));
            List<String> lore = new ArrayList<>();
            lore.add(SmallCaps.convert("§7Items rescatados de tu Vault #" + vaultNumber));
            lore.add(SmallCaps.convert("§7Coloca y rompe el shulker para recuperar."));
            bsm.setLore(lore);
            shulkerItem.setItemMeta(bsm);
            shulkers.add(shulkerItem);
            shulkerIdx++;
        }

        // Vaciar el vault
        clearVault(uuid, vaultNumber);

        // Entregar shulkers + overflow al jugador o dropearlos
        int givenShulkers = shulkers.size();
        for (ItemStack sh : shulkers) {
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(sh);
            for (ItemStack lo : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), lo);
            }
        }
        for (ItemStack it : overflow) {
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(it);
            for (ItemStack lo : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), lo);
            }
        }

        player.sendMessage("");
        player.sendMessage(SmallCaps.convert("§a§l✔ §aItems del Vault #" + vaultNumber + " empaquetados!"));
        player.sendMessage(SmallCaps.convert("§7Has recibido §d" + givenShulkers + " shulker box" + (givenShulkers == 1 ? "" : "es") + "§7 con tus items."));
        if (!overflow.isEmpty()) {
            player.sendMessage(SmallCaps.convert("§7§o(§e" + overflow.size() + "§7§o items no empaquetables se entregaron sueltos)"));
        }
        player.sendMessage("");
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.2f);
    }
}
