package com.moonlight.coreprotect.kits;

import com.moonlight.coreprotect.CoreProtectPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import com.moonlight.coreprotect.util.SmallCaps;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * /kits - GUI con kits: Starter, Luna, Nova, Eclipse, Moonlord
 * Starter: gratis, 2h cooldown
 * Luna/Nova/Eclipse/Moonlord: 12h cooldown
 * Permisos: essentials.kits.luna, essentials.kits.nova, essentials.kits.eclipse, essentials.kits.moonlord
 * Eclipse/Moonlord: habilidades pasivas mientras llevas la armadura
 * Click izquierdo = reclamar, Click derecho = ver contenido
 */
public class KitsCommand implements CommandExecutor, Listener {

    static final String GUI_TITLE = SmallCaps.convert("§5§l✦ Kits Premium ✦");
    static final String PREVIEW_PREFIX = SmallCaps.convert("§8Contenido: ");
    private static final long STARTER_CD = 2L * 60L * 60L * 1000L;  // 2h
    private static final long PREMIUM_CD = 12L * 60L * 60L * 1000L; // 12h
    private static final long DEMON_CD = 100L * 60L * 60L * 1000L; // 100h

    private final CoreProtectPlugin plugin;
    private final File cooldownFile;
    private final Map<String, Map<UUID, Long>> cooldowns = new HashMap<>();

    public KitsCommand(CoreProtectPlugin plugin) {
        this.plugin = plugin;
        this.cooldownFile = new File(plugin.getDataFolder(), "kit_cooldowns.yml");
        for (String k : new String[]{"starter", "luna", "nova", "eclipse", "moonlord", "demon"})
            cooldowns.put(k, new HashMap<>());
        loadCooldowns();

    }

    // ==================== COOLDOWNS ====================

    private void loadCooldowns() {
        if (!cooldownFile.exists()) return;
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(cooldownFile);
        for (String kit : cooldowns.keySet()) {
            if (cfg.isConfigurationSection(kit)) {
                for (String uid : cfg.getConfigurationSection(kit).getKeys(false)) {
                    try {
                        cooldowns.get(kit).put(UUID.fromString(uid), cfg.getLong(kit + "." + uid));
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        }
    }

    private void saveCooldowns() {
        FileConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<String, Map<UUID, Long>> entry : cooldowns.entrySet()) {
            for (Map.Entry<UUID, Long> cd : entry.getValue().entrySet()) {
                cfg.set(entry.getKey() + "." + cd.getKey().toString(), cd.getValue());
            }
        }
        try { cfg.save(cooldownFile); } catch (IOException e) {
            plugin.getLogger().severe("Error guardando kit_cooldowns.yml: " + e.getMessage());
        }
    }

    private long getCooldownMs(String kit) {
        if (kit.equals("starter")) return STARTER_CD;
        if (kit.equals("demon")) return DEMON_CD;
        return PREMIUM_CD;
    }

    private boolean isOnCooldown(String kit, UUID uid) {
        Long last = cooldowns.get(kit).get(uid);
        if (last == null) return false;
        return System.currentTimeMillis() - last < getCooldownMs(kit);
    }

    private String getCooldownRemaining(String kit, UUID uid) {
        Long last = cooldowns.get(kit).get(uid);
        if (last == null) return "Disponible";
        long remaining = getCooldownMs(kit) - (System.currentTimeMillis() - last);
        if (remaining <= 0) return "Disponible";
        long hours = remaining / (60 * 60 * 1000);
        long minutes = (remaining % (60 * 60 * 1000)) / (60 * 1000);
        return hours + "h " + minutes + "m";
    }

    // ==================== COMMAND ====================

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(SmallCaps.convert("§cSolo jugadores pueden usar este comando."));
            return true;
        }
        openGUI((Player) sender);
        return true;
    }

    // ==================== MAIN GUI ====================

    private void openGUI(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, GUI_TITLE);
        UUID uid = player.getUniqueId();

        // Borders: magenta outer + purple accents
        ItemStack border = item(Material.MAGENTA_STAINED_GLASS_PANE, " ");
        ItemStack accent = item(Material.PURPLE_STAINED_GLASS_PANE, " ");
        ItemStack dark   = item(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) inv.setItem(i, dark);
        // Top & bottom rows
        for (int i = 0; i < 9; i++) { inv.setItem(i, border); inv.setItem(45 + i, border); }
        // Side columns
        for (int row = 1; row <= 4; row++) { inv.setItem(row * 9, accent); inv.setItem(row * 9 + 8, accent); }
        // Clear inner area
        for (int row = 1; row <= 4; row++)
            for (int col = 1; col <= 7; col++)
                inv.setItem(row * 9 + col, null);

        // Title
        inv.setItem(4, item(Material.NETHER_STAR, "§5§l✦ Kits Premium ✦",
                "§7Selecciona tu kit.", "",
                "§a◀ Click izquierdo §7= Reclamar",
                "§e▶ Click derecho §7= Ver contenido",
                "", "§7Cooldown para obtener kits: §e12h §7(Starter: §a2h§7, Demon: §4100h§7)"));
        // Decorative corners
        inv.setItem(0, accent); inv.setItem(8, accent);
        inv.setItem(45, accent); inv.setItem(53, accent);

        // Row 1 center: Starter (slot 13)
        boolean starterCD = isOnCooldown("starter", uid);
        inv.setItem(13, kitItem(Material.LEATHER_CHESTPLATE, "§a§lKit Starter",
                true, starterCD, getCooldownRemaining("starter", uid), "starter",
                "§7Kit básico para empezar.", "§7¡Gratis para todos!", "",
                "§a✔ §7Cooldown para obtener: §a2 horas"));

        // Row 2: Luna (20) + Demon (22 centro) + Nova (24)
        boolean hasLuna = player.hasPermission("essentials.kits.luna");
        boolean lunaCD = isOnCooldown("luna", uid);
        inv.setItem(20, kitItem(Material.DIAMOND_CHESTPLATE, "§f§lKit Luna",
                hasLuna, lunaCD, getCooldownRemaining("luna", uid), "luna",
                "§7Armadura de diamante encantada,",
                "§7espada, pico, hacha, god apples.",
                "§7+Speed I al reclamar.", "",
                "§7Cooldown para obtener: §e12 horas"));
        
        // Kit Demon en el centro con icono de calavera
        boolean hasDemon = player.hasPermission("essentials.kits.demon");
        boolean demonCD = isOnCooldown("demon", uid);
        inv.setItem(22, kitItem(Material.WITHER_SKELETON_SKULL, "§4§lKit Demon",
                hasDemon, demonCD, getCooldownRemaining("demon", uid), "demon",
                "§7Set completo Netherite + habilidades,",
                "§7espada Sharp V, pico Fortune III,",
                "§7god apples, totem, elytra.", "",
                "§4§l⚡ HABILIDADES PASIVAS:",
                "§4☄ Aura Infernal §7(Daño + Fuego)",
                "§4§l⚡ HABILIDAD ACTIVA: §7Shift + Click Derecho",
                "§4⚡ Devoradora de Almas §7(Daño AoE + Vida)",
                "§7Cooldown para obtener kit: §e100 horas"));
        
        boolean hasNova = player.hasPermission("essentials.kits.nova");
        boolean novaCD = isOnCooldown("nova", uid);
        inv.setItem(24, kitItem(Material.NETHERITE_SWORD, "§b§lKit Nova",
                hasNova, novaCD, getCooldownRemaining("nova", uid), "nova",
                "§7Armadura netherite encantada,",
                "§7espada Sharp V, pico Fortune III,",
                "§7god apples, totem.",
                "§7+Speed I +Resistance I al reclamar.", "",
                "§7Cooldown para obtener: §e12 horas"));

        // Row 3: Eclipse (29) + Moonlord (33)
        boolean hasEclipse = player.hasPermission("essentials.kits.eclipse");
        boolean eclipseCD = isOnCooldown("eclipse", uid);
        inv.setItem(29, kitItem(Material.BLAZE_POWDER, "§6§lKit Eclipse",
                hasEclipse, eclipseCD, getCooldownRemaining("eclipse", uid), "eclipse",
                "§7Netherite max enchant + Mending,",
                "§7totem x2, elytra, god apples.", "",
                "§6§l⚡ HABILIDAD: §7Shift + Click Derecho:",
                "§6☀ Llamarada Solar",
                "§7 • Explosión de fuego en área",
                "§7 • Ciega y quema enemigos",
                "§7 • Cooldown: §e30s", "",
                "§7Cooldown para obtener: §e12 horas"));
        inv.setItem(31, accent); // center divider
        // Moonlord justo debajo de Nova (24 -> 33)
        boolean hasMoonlord = player.hasPermission("essentials.kits.moonlord");
        boolean moonlordCD = isOnCooldown("moonlord", uid);
        inv.setItem(33, kitItem(Material.NETHERITE_CHESTPLATE, "§d§lKit Moonlord",
                hasMoonlord, moonlordCD, getCooldownRemaining("moonlord", uid), "moonlord",
                "§7Netherite §d§lGOD§7 enchant,",
                "§7totem x3, elytra, arco OP, tridente,",
                "§7god apples, cohetes.", "",
                "§d§l⚡ HABILIDAD: §7Shift + Click Derecho:",
                "§d☽ Impacto Lunar",
                "§7 • Teletransporte detrás del enemigo",
                "§7 • Backstab + Invisibilidad 4s",
                "§7 • Cooldown: §e35s", "",
                "§7Cooldown para obtener: §e12 horas"));
        
        inv.setItem(49, item(Material.BOOK, "§e§lInfo",
                "§a◀ Click izquierdo §7= Reclamar kit",
                "§e▶ Click derecho §7= Ver contenido del kit", "",
                "§7Los kits §6Eclipse, §dMoonlord §7y §4Demon",
                "§7tienen habilidades activas.",
                "§7Usa §eShift + Click Derecho §7con espada.",
                "",
                "§7§4Kit Demon §7no tiene preview."));

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 0.6f, 1.2f);
    }

    // ==================== PREVIEW GUI ====================

    private void openPreview(Player player, String kitName) {
        String title = PREVIEW_PREFIX + kitDisplayName(kitName);
        List<ItemStack> items = getKitItems(kitName);
        int size = Math.max(27, ((items.size() / 9) + 2) * 9);
        if (size > 54) size = 54;
        Inventory inv = Bukkit.createInventory(null, size, title);

        for (int i = 0; i < items.size() && i < size - 9; i++) {
            inv.setItem(i, items.get(i));
        }

        // Back button
        inv.setItem(size - 5, item(Material.ARROW, "§c§l← Volver", "§7Click para volver al menú de kits."));

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1.2f);
    }

    // ==================== CLICK HANDLER ====================

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();

        // Main GUI
        if (title.equals(GUI_TITLE)) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player)) return;
            Player player = (Player) event.getWhoClicked();
            int slot = event.getRawSlot();
            String kit = slotToKit(slot);
            if (kit == null) return;

            if (event.getClick() == ClickType.RIGHT || event.getClick() == ClickType.SHIFT_RIGHT) {
                // Right click = preview
                openPreview(player, kit);
            } else {
                // Left click = claim
                String perm = kit.equals("starter") ? null : "essentials.kits." + kit;
                claimKit(player, kit, perm);
            }
            return;
        }

        // Preview GUI
        if (title.startsWith(PREVIEW_PREFIX)) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player)) return;
            Player player = (Player) event.getWhoClicked();
            ItemStack clicked = event.getCurrentItem();
            if (clicked != null && clicked.getType() == Material.ARROW) {
                openGUI(player);
            }
        }
    }

    private String slotToKit(int slot) {
        switch (slot) {
            case 13: return "starter";
            case 20: return "luna";
            case 22: return "demon";
            case 24: return "nova";
            case 29: return "eclipse";
            case 33: return "moonlord"; // Moonlord justo debajo de Nova
            default: return null;
        }
    }

    private String kitDisplayName(String kit) {
        switch (kit) {
            case "starter": return "Starter";
            case "luna": return "Luna";
            case "demon": return "Demon";
            case "nova": return "Nova";
            case "eclipse": return "Eclipse";
            case "moonlord": return "Moonlord";
            default: return kit;
        }
    }

    // ==================== CLAIM ====================

    private void claimKit(Player player, String kitName, String permission) {
        UUID uid = player.getUniqueId();
        if (permission != null && !player.hasPermission(permission)) {
            player.sendMessage(SmallCaps.convert("§c§l✖ §cNo tienes el rango necesario para este kit."));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
            return;
        }
        // Ops bypass cooldown
        if (!player.isOp() && isOnCooldown(kitName, uid)) {
            player.sendMessage(SmallCaps.convert("§c§l✖ §cDebes esperar §e" + getCooldownRemaining(kitName, uid) + " §cpara reclamar este kit."));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
            return;
        }

        // Check if inventory has space
        if (!hasInventorySpace(player, kitName)) {
            player.sendMessage(SmallCaps.convert("§c§l✖ §cTu inventario está lleno. Libera espacio para recibir el kit."));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
            return;
        }

        if (!player.isOp()) {
            cooldowns.get(kitName).put(uid, System.currentTimeMillis());
            saveCooldowns();
        }
        player.closeInventory();

        switch (kitName) {
            case "starter": giveStarterKit(player); break;
            case "luna": giveLunaKit(player); break;
            case "nova": giveNovaKit(player); break;
            case "eclipse": giveEclipseKit(player); break;
            case "moonlord": giveMoonlordKit(player); break;
            case "demon": giveDemonKit(player); break;
        }
    }

    private boolean hasInventorySpace(Player player, String kitName) {
        int requiredSlots = switch (kitName) {
            case "starter" -> 13;
            case "luna" -> 11;
            case "nova" -> 13;
            case "eclipse" -> 15;
            case "moonlord" -> 17;
            case "demon" -> 18;
            default -> 10;
        };

        int emptySlots = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType() == Material.AIR) {
                emptySlots++;
            }
        }

        return emptySlots >= requiredSlots;
    }

    // ==================== KIT STARTER (Hierro) ====================
    private void giveStarterKit(Player player) {
        player.getInventory().addItem(
            new ItemStack(Material.IRON_SWORD),
            new ItemStack(Material.IRON_PICKAXE),
            new ItemStack(Material.IRON_AXE),
            new ItemStack(Material.IRON_SHOVEL),
            new ItemStack(Material.IRON_HELMET),
            new ItemStack(Material.IRON_CHESTPLATE),
            new ItemStack(Material.IRON_LEGGINGS),
            new ItemStack(Material.IRON_BOOTS),
            new ItemStack(Material.SHIELD),
            new ItemStack(Material.COOKED_BEEF, 32),
            new ItemStack(Material.OAK_LOG, 32),
            new ItemStack(Material.TORCH, 16),
            new ItemStack(Material.BREAD, 16)
        );
        player.sendMessage(SmallCaps.convert("§a§l✓ §aKit Starter recibido."));
    }

    // ==================== KIT LUNA (Diamante) ====================
    private void giveLunaKit(Player player) {
        player.getInventory().addItem(
            enchant(new ItemStack(Material.DIAMOND_HELMET), "§f§lCasco Luna",
                Map.of(Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3)),
            enchant(new ItemStack(Material.DIAMOND_CHESTPLATE), "§f§lPeto Luna",
                Map.of(Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3)),
            enchant(new ItemStack(Material.DIAMOND_LEGGINGS), "§f§lPantalones Luna",
                Map.of(Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3)),
            enchant(new ItemStack(Material.DIAMOND_BOOTS), "§f§lBotas Luna",
                Map.of(Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3, Enchantment.FEATHER_FALLING, 4)),
            enchant(new ItemStack(Material.DIAMOND_SWORD), "§f§lEspada Luna",
                Map.of(Enchantment.SHARPNESS, 5, Enchantment.UNBREAKING, 3, Enchantment.FIRE_ASPECT, 1)),
            enchant(new ItemStack(Material.DIAMOND_PICKAXE), "§f§lPico Luna",
                Map.of(Enchantment.EFFICIENCY, 5, Enchantment.UNBREAKING, 3, Enchantment.FORTUNE, 3)),
            enchant(new ItemStack(Material.DIAMOND_AXE), "§f§lHacha Luna",
                Map.of(Enchantment.EFFICIENCY, 5, Enchantment.UNBREAKING, 3)),
            new ItemStack(Material.GOLDEN_APPLE, 16),
            new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 4),
            new ItemStack(Material.EXPERIENCE_BOTTLE, 32),
            new ItemStack(Material.COOKED_BEEF, 64)
        );
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 6000, 0, false, false, true));
    }

    // ==================== KIT NOVA (Netherite) ====================
    private void giveNovaKit(Player player) {
        player.getInventory().addItem(
            enchant(new ItemStack(Material.NETHERITE_HELMET), "§b§lCasco Nova",
                Map.of(Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3, Enchantment.RESPIRATION, 3)),
            enchant(new ItemStack(Material.NETHERITE_CHESTPLATE), "§b§lPeto Nova",
                Map.of(Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3)),
            enchant(new ItemStack(Material.NETHERITE_LEGGINGS), "§b§lPantalones Nova",
                Map.of(Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3)),
            enchant(new ItemStack(Material.NETHERITE_BOOTS), "§b§lBotas Nova",
                Map.of(Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3, Enchantment.FEATHER_FALLING, 4)),
            enchant(new ItemStack(Material.NETHERITE_SWORD), "§b§lEspada Nova",
                Map.of(Enchantment.SHARPNESS, 5, Enchantment.UNBREAKING, 3, Enchantment.FIRE_ASPECT, 2, Enchantment.LOOTING, 3)),
            enchant(new ItemStack(Material.NETHERITE_PICKAXE), "§b§lPico Nova",
                Map.of(Enchantment.EFFICIENCY, 5, Enchantment.UNBREAKING, 3, Enchantment.FORTUNE, 3)),
            enchant(new ItemStack(Material.NETHERITE_AXE), "§b§lHacha Nova",
                Map.of(Enchantment.EFFICIENCY, 5, Enchantment.UNBREAKING, 3, Enchantment.SHARPNESS, 5)),
            new ItemStack(Material.GOLDEN_APPLE, 32),
            new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 8),
            new ItemStack(Material.EXPERIENCE_BOTTLE, 64),
            new ItemStack(Material.TOTEM_OF_UNDYING, 1),
            new ItemStack(Material.COOKED_BEEF, 64)
        );
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 6000, 0, false, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 6000, 0, false, false, true));
    }

    // ==================== KIT ECLIPSE (Netherite MAX + Pasiva) ====================
    private void giveEclipseKit(Player player) {
        player.getInventory().addItem(
            enchant(new ItemStack(Material.NETHERITE_HELMET), "§6§lCasco Eclipse",
                Map.of(Enchantment.PROTECTION, 5, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1, Enchantment.RESPIRATION, 3, Enchantment.AQUA_AFFINITY, 1)),
            enchant(new ItemStack(Material.NETHERITE_CHESTPLATE), "§6§lPeto Eclipse",
                Map.of(Enchantment.PROTECTION, 5, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1)),
            enchant(new ItemStack(Material.NETHERITE_LEGGINGS), "§6§lPantalones Eclipse",
                Map.of(Enchantment.PROTECTION, 5, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1, Enchantment.SWIFT_SNEAK, 3)),
            enchant(new ItemStack(Material.NETHERITE_BOOTS), "§6§lBotas Eclipse",
                Map.of(Enchantment.PROTECTION, 5, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1, Enchantment.FEATHER_FALLING, 5, Enchantment.DEPTH_STRIDER, 3)),
            enchWithAbility(new ItemStack(Material.NETHERITE_SWORD), "§6§lEspada Eclipse",
                Map.of(Enchantment.SHARPNESS, 5, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1, Enchantment.FIRE_ASPECT, 2, Enchantment.SWEEPING_EDGE, 3, Enchantment.LOOTING, 3),
                "§6§l⚡ HABILIDAD: §7Shift + Click Derecho", "§7 • Llamarada Solar: §6Explosión de fuego AoE", "§7Cooldown: §e30s"),
            enchant(new ItemStack(Material.NETHERITE_PICKAXE), "§6§lPico Eclipse",
                Map.of(Enchantment.EFFICIENCY, 5, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1, Enchantment.FORTUNE, 3)),
            enchant(new ItemStack(Material.NETHERITE_AXE), "§6§lHacha Eclipse",
                Map.of(Enchantment.EFFICIENCY, 5, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1, Enchantment.SHARPNESS, 5)),
            enchant(new ItemStack(Material.BOW), "§6§lArco Eclipse",
                Map.of(Enchantment.POWER, 5, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1, Enchantment.FLAME, 1)),
            enchant(new ItemStack(Material.MACE), "§6§lMazo Eclipse",
                Map.of(Enchantment.DENSITY, 6, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1, Enchantment.KNOCKBACK, 2, Enchantment.FIRE_ASPECT, 2)),
            new ItemStack(Material.TOTEM_OF_UNDYING, 2),
            new ItemStack(Material.GOLDEN_APPLE, 64),
            new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 8),
            new ItemStack(Material.EXPERIENCE_BOTTLE, 64),
            new ItemStack(Material.ARROW, 64)
        );
        // Elytra con Unbreaking
        ItemStack elytra = new ItemStack(Material.ELYTRA);
        ItemMeta em = elytra.getItemMeta();
        em.setDisplayName("§6§lElytra Eclipse");
        em.addEnchant(Enchantment.UNBREAKING, 3, true);
        elytra.setItemMeta(em);
        player.getInventory().addItem(elytra, new ItemStack(Material.FIREWORK_ROCKET, 32));
    }

    // ==================== KIT MOONLORD (GOD + Pasiva) ====================
    private void giveMoonlordKit(Player player) {
        player.getInventory().addItem(
            enchant(new ItemStack(Material.NETHERITE_HELMET), "§d§lCasco Moonlord",
                Map.of(Enchantment.PROTECTION, 6, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1, Enchantment.RESPIRATION, 3, Enchantment.AQUA_AFFINITY, 1)),
            enchant(new ItemStack(Material.NETHERITE_CHESTPLATE), "§d§lPeto Moonlord",
                Map.of(Enchantment.PROTECTION, 6, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1, Enchantment.THORNS, 3)),
            enchant(new ItemStack(Material.NETHERITE_LEGGINGS), "§d§lPantalones Moonlord",
                Map.of(Enchantment.PROTECTION, 6, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1, Enchantment.SWIFT_SNEAK, 3)),
            enchant(new ItemStack(Material.NETHERITE_BOOTS), "§d§lBotas Moonlord",
                Map.of(Enchantment.PROTECTION, 6, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1, Enchantment.FEATHER_FALLING, 6, Enchantment.DEPTH_STRIDER, 3, Enchantment.SOUL_SPEED, 3)),
            enchWithAbility(new ItemStack(Material.NETHERITE_SWORD), "§d§lEspada Moonlord",
                Map.of(Enchantment.SHARPNESS, 6, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1, Enchantment.FIRE_ASPECT, 2, Enchantment.SWEEPING_EDGE, 3, Enchantment.LOOTING, 3),
                "§d§l⚡ HABILIDAD: §7Shift + Click Derecho", "§7 • Impacto Lunar: §dTeletransporte + Backstab", "§7Cooldown: §e35s"),
            enchant(new ItemStack(Material.NETHERITE_PICKAXE), "§d§lPico Moonlord",
                Map.of(Enchantment.EFFICIENCY, 6, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1, Enchantment.FORTUNE, 3)),
            enchant(new ItemStack(Material.NETHERITE_AXE), "§d§lHacha Moonlord",
                Map.of(Enchantment.EFFICIENCY, 6, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1, Enchantment.SHARPNESS, 6)),
            enchant(new ItemStack(Material.BOW), "§d§lArco Moonlord",
                Map.of(Enchantment.POWER, 6, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1, Enchantment.FLAME, 1, Enchantment.INFINITY, 1)),
            enchant(new ItemStack(Material.TRIDENT), "§d§lTridente Moonlord",
                Map.of(Enchantment.LOYALTY, 3, Enchantment.RIPTIDE, 3, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1)),
            enchant(new ItemStack(Material.MACE), "§d§lMazo Moonlord",
                Map.of(Enchantment.DENSITY, 7, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1, Enchantment.SWEEPING_EDGE, 3, Enchantment.LOOTING, 3)),
            new ItemStack(Material.TOTEM_OF_UNDYING, 3),
            new ItemStack(Material.GOLDEN_APPLE, 64),
            new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 16),
            new ItemStack(Material.EXPERIENCE_BOTTLE, 64),
            new ItemStack(Material.ARROW, 1)
        );
        // Elytra con Mending
        ItemStack elytra = new ItemStack(Material.ELYTRA);
        ItemMeta em = elytra.getItemMeta();
        em.setDisplayName("§d§lElytra Moonlord");
        em.addEnchant(Enchantment.UNBREAKING, 3, true);
        em.addEnchant(Enchantment.MENDING, 1, true);
        elytra.setItemMeta(em);
        player.getInventory().addItem(elytra, new ItemStack(Material.FIREWORK_ROCKET, 64));
    }

    // ==================== KIT DEMON (Items exactos como preview + Title) ====================
    private void giveDemonKit(Player player) {
        // Dar los items exactos como el preview
        player.getInventory().addItem(
            createDemonHelmet(),
            createDemonChestplate(),
            createDemonLeggings(),
            createDemonBoots(),
            createDemonSword(),
            createDemonPickaxe(),
            createDemonAxe(),
            createDemonShovel(),
            createDemonHoe(),
            
            // Items extra exactos del comando
            new ItemStack(Material.EXPERIENCE_BOTTLE, 64),
            new ItemStack(Material.EXPERIENCE_BOTTLE, 64),
            new ItemStack(Material.EXPERIENCE_BOTTLE, 64),
            new ItemStack(Material.EXPERIENCE_BOTTLE, 64),
            new ItemStack(Material.ENDER_PEARL, 16),
            new ItemStack(Material.ENDER_PEARL, 16),
            new ItemStack(Material.GOLDEN_APPLE, 64),
            new ItemStack(Material.GOLDEN_APPLE, 64),
            new ItemStack(Material.AMETHYST_BLOCK, 32),
            new ItemStack(Material.DIAMOND_BLOCK, 32),
            new ItemStack(Material.EMERALD_BLOCK, 42),
            new ItemStack(Material.LAPIS_BLOCK, 32),
            new ItemStack(Material.GOLD_BLOCK, 32),
            new ItemStack(Material.ENDER_PEARL, 16),
            new ItemStack(Material.EXPERIENCE_BOTTLE, 64),
            new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 32)
        );

        // Title y mensajes del Kit Demon
        player.sendTitle(SmallCaps.convert("§4§l☠ Kit Demon ☠"), SmallCaps.convert("§c¡El poder del abismo es tuyo!"), 10, 60, 20);
        player.sendMessage("");
        player.sendMessage(SmallCaps.convert("§4§l☠ §c§lKit Demon §4§l☠ §7— Set recibido."));
        player.sendMessage(SmallCaps.convert("§c  ▸ Furia Demoníaca §8(Espada)§7: Dash igneo (Shift+ClickDcho)."));
        player.sendMessage(SmallCaps.convert("§c  ▸ Alma Ardiente §8(1p)§7: Inmunidad al fuego, absorber llamas cura."));
        player.sendMessage(SmallCaps.convert("§c  ▸ Sed de Sangre §8(2p)§7: Robavida 20% + Celeridad al matar."));
        player.sendMessage(SmallCaps.convert("§c  ▸ Marca Demoniaca §8(2p)§7: Marca objetivos. +35% daño."));
        player.sendMessage(SmallCaps.convert("§c  ▸ Aura Infernal §8(3p)§7: Marchit. II + daño a cercanos."));
        player.sendMessage(SmallCaps.convert("§c  ▸ Pacto Demoníaco §8(4p)§7: Al 20% HP: Fuerza III + Regen III."));
        player.sendMessage(SmallCaps.convert("§c  ▸ Poder del Abismo: §7Fuerza II (Peto) y Velocidad II (Botas)."));
        player.sendMessage("");
        if (player.isOp()) player.sendMessage(SmallCaps.convert("§8[OP] Sin cooldown."));
    }

    // ==================== GET KIT ITEMS (for preview) ====================

    private List<ItemStack> getKitItems(String kit) {
        List<ItemStack> items = new ArrayList<>();
        switch (kit) {
            case "starter":
                items.addAll(Arrays.asList(
                    new ItemStack(Material.IRON_SWORD), new ItemStack(Material.IRON_PICKAXE),
                    new ItemStack(Material.IRON_AXE), new ItemStack(Material.IRON_SHOVEL),
                    new ItemStack(Material.IRON_HELMET), new ItemStack(Material.IRON_CHESTPLATE),
                    new ItemStack(Material.IRON_LEGGINGS), new ItemStack(Material.IRON_BOOTS),
                    new ItemStack(Material.SHIELD), new ItemStack(Material.COOKED_BEEF, 32),
                    new ItemStack(Material.OAK_LOG, 32), new ItemStack(Material.TORCH, 16),
                    new ItemStack(Material.BREAD, 16)
                ));
                break;
            case "luna":
                items.addAll(Arrays.asList(
                    enchant(new ItemStack(Material.DIAMOND_HELMET), "§f§lCasco Luna", Map.of(Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3)),
                    enchant(new ItemStack(Material.DIAMOND_CHESTPLATE), "§f§lPeto Luna", Map.of(Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3)),
                    enchant(new ItemStack(Material.DIAMOND_LEGGINGS), "§f§lPantalones Luna", Map.of(Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3)),
                    enchant(new ItemStack(Material.DIAMOND_BOOTS), "§f§lBotas Luna", Map.of(Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3, Enchantment.FEATHER_FALLING, 4)),
                    enchant(new ItemStack(Material.DIAMOND_SWORD), "§f§lEspada Luna", Map.of(Enchantment.SHARPNESS, 5, Enchantment.UNBREAKING, 3, Enchantment.FIRE_ASPECT, 1)),
                    enchant(new ItemStack(Material.DIAMOND_PICKAXE), "§f§lPico Luna", Map.of(Enchantment.EFFICIENCY, 5, Enchantment.UNBREAKING, 3, Enchantment.FORTUNE, 3)),
                    enchant(new ItemStack(Material.DIAMOND_AXE), "§f§lHacha Luna", Map.of(Enchantment.EFFICIENCY, 5, Enchantment.UNBREAKING, 3)),
                    new ItemStack(Material.GOLDEN_APPLE, 16), new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 4),
                    new ItemStack(Material.EXPERIENCE_BOTTLE, 32), new ItemStack(Material.COOKED_BEEF, 64),
                    item(Material.POTION, "§e+Speed I §7(5 min)")
                ));
                break;
            case "nova":
                items.addAll(Arrays.asList(
                    enchant(new ItemStack(Material.NETHERITE_HELMET), "§b§lCasco Nova", Map.of(Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3, Enchantment.RESPIRATION, 3)),
                    enchant(new ItemStack(Material.NETHERITE_CHESTPLATE), "§b§lPeto Nova", Map.of(Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3)),
                    enchant(new ItemStack(Material.NETHERITE_LEGGINGS), "§b§lPantalones Nova", Map.of(Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3)),
                    enchant(new ItemStack(Material.NETHERITE_BOOTS), "§b§lBotas Nova", Map.of(Enchantment.PROTECTION, 4, Enchantment.UNBREAKING, 3, Enchantment.FEATHER_FALLING, 4)),
                    enchant(new ItemStack(Material.NETHERITE_SWORD), "§b§lEspada Nova", Map.of(Enchantment.SHARPNESS, 5, Enchantment.UNBREAKING, 3, Enchantment.FIRE_ASPECT, 2, Enchantment.LOOTING, 3)),
                    enchant(new ItemStack(Material.NETHERITE_PICKAXE), "§b§lPico Nova", Map.of(Enchantment.EFFICIENCY, 5, Enchantment.UNBREAKING, 3, Enchantment.FORTUNE, 3)),
                    enchant(new ItemStack(Material.NETHERITE_AXE), "§b§lHacha Nova", Map.of(Enchantment.EFFICIENCY, 5, Enchantment.UNBREAKING, 3, Enchantment.SHARPNESS, 5)),
                    new ItemStack(Material.GOLDEN_APPLE, 32), new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 8),
                    new ItemStack(Material.EXPERIENCE_BOTTLE, 64), new ItemStack(Material.TOTEM_OF_UNDYING, 1),
                    item(Material.POTION, "§e+Speed I +Resistance I §7(5 min)")
                ));
                break;
            case "eclipse":
                items.addAll(Arrays.asList(
                    enchant(new ItemStack(Material.NETHERITE_HELMET), "§6§lCasco Eclipse", Map.of(Enchantment.PROTECTION, 5, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1)),
                    enchant(new ItemStack(Material.NETHERITE_CHESTPLATE), "§6§lPeto Eclipse", Map.of(Enchantment.PROTECTION, 5, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1)),
                    enchant(new ItemStack(Material.NETHERITE_LEGGINGS), "§6§lPantalones Eclipse", Map.of(Enchantment.PROTECTION, 5, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1)),
                    enchant(new ItemStack(Material.NETHERITE_BOOTS), "§6§lBotas Eclipse", Map.of(Enchantment.PROTECTION, 5, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1, Enchantment.FEATHER_FALLING, 5)),
                    enchWithAbility(new ItemStack(Material.NETHERITE_SWORD), "§6§lEspada Eclipse", Map.of(Enchantment.SHARPNESS, 5, Enchantment.FIRE_ASPECT, 2, Enchantment.SWEEPING_EDGE, 3, Enchantment.LOOTING, 3), "§6§l⚡ HABILIDAD: §7Shift + Click Derecho", "§7 • Llamarada Solar: §6Explosión de fuego AoE", "§7Cooldown: §e30s"),
                    enchant(new ItemStack(Material.BOW), "§6§lArco Eclipse", Map.of(Enchantment.POWER, 5, Enchantment.FLAME, 1)),
                    enchant(new ItemStack(Material.MACE), "§6§lMazo Eclipse", Map.of(Enchantment.DENSITY, 6, Enchantment.KNOCKBACK, 2, Enchantment.FIRE_ASPECT, 2)),
                    new ItemStack(Material.TOTEM_OF_UNDYING, 2), new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 8),
                    new ItemStack(Material.ELYTRA), new ItemStack(Material.FIREWORK_ROCKET, 32),
                    item(Material.BLAZE_POWDER, "§6§l⚡ Llamarada Solar", "§7Shift + Click Derecho con espada:", "§7Explosión de fuego AoE + Ceguera", "§7Cooldown: §e30s")
                ));
                break;
            case "moonlord":
                items.addAll(Arrays.asList(
                    enchant(new ItemStack(Material.NETHERITE_HELMET), "§d§lCasco Moonlord", Map.of(Enchantment.PROTECTION, 6, Enchantment.UNBREAKING, 3, Enchantment.MENDING, 1)),
                    enchant(new ItemStack(Material.NETHERITE_CHESTPLATE), "§d§lPeto Moonlord", Map.of(Enchantment.PROTECTION, 6, Enchantment.THORNS, 3)),
                    enchant(new ItemStack(Material.NETHERITE_LEGGINGS), "§d§lPantalones Moonlord", Map.of(Enchantment.PROTECTION, 6, Enchantment.MENDING, 1)),
                    enchant(new ItemStack(Material.NETHERITE_BOOTS), "§d§lBotas Moonlord", Map.of(Enchantment.PROTECTION, 6, Enchantment.FEATHER_FALLING, 6, Enchantment.SOUL_SPEED, 3)),
                    enchWithAbility(new ItemStack(Material.NETHERITE_SWORD), "§d§lEspada Moonlord", Map.of(Enchantment.SHARPNESS, 6, Enchantment.FIRE_ASPECT, 2, Enchantment.SWEEPING_EDGE, 3, Enchantment.LOOTING, 3), "§d§l⚡ HABILIDAD: §7Shift + Click Derecho", "§7 • Impacto Lunar: §dTeletransporte + Backstab", "§7Cooldown: §e35s"),
                    enchant(new ItemStack(Material.BOW), "§d§lArco Moonlord", Map.of(Enchantment.POWER, 6, Enchantment.FLAME, 1, Enchantment.INFINITY, 1)),
                    enchant(new ItemStack(Material.TRIDENT), "§d§lTridente Moonlord", Map.of(Enchantment.LOYALTY, 3, Enchantment.RIPTIDE, 3)),
                    enchant(new ItemStack(Material.MACE), "§d§lMazo Moonlord", Map.of(Enchantment.DENSITY, 7, Enchantment.SWEEPING_EDGE, 3, Enchantment.LOOTING, 3)),
                    new ItemStack(Material.TOTEM_OF_UNDYING, 3), new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 16),
                    new ItemStack(Material.ELYTRA), new ItemStack(Material.FIREWORK_ROCKET, 64),
                    item(Material.NETHER_STAR, "§d§l⚡ Impacto Lunar", "§7Shift + Click Derecho con espada:", "§7Teletransporte + Backstab + Invisibilidad", "§7Cooldown: §e35s")
                ));
                break;
            case "demon":
                // Items exactos del comando /kit demon
                items.add(createDemonHelmet());
                items.add(createDemonChestplate());
                items.add(createDemonLeggings());
                items.add(createDemonBoots());
                items.add(createDemonSword());
                items.add(createDemonPickaxe());
                items.add(createDemonAxe());
                items.add(createDemonShovel());
                items.add(createDemonHoe());
                
                // Items extra exactos del comando
                items.add(new ItemStack(Material.EXPERIENCE_BOTTLE, 64));
                items.add(new ItemStack(Material.EXPERIENCE_BOTTLE, 64));
                items.add(new ItemStack(Material.EXPERIENCE_BOTTLE, 64));
                items.add(new ItemStack(Material.EXPERIENCE_BOTTLE, 64));
                items.add(new ItemStack(Material.ENDER_PEARL, 16));
                items.add(new ItemStack(Material.ENDER_PEARL, 16));
                items.add(new ItemStack(Material.GOLDEN_APPLE, 64));
                items.add(new ItemStack(Material.GOLDEN_APPLE, 64));
                items.add(new ItemStack(Material.AMETHYST_BLOCK, 32));
                items.add(new ItemStack(Material.DIAMOND_BLOCK, 32));
                items.add(new ItemStack(Material.EMERALD_BLOCK, 42));
                items.add(new ItemStack(Material.LAPIS_BLOCK, 32));
                items.add(new ItemStack(Material.GOLD_BLOCK, 32));
                items.add(new ItemStack(Material.ENDER_PEARL, 16));
                items.add(new ItemStack(Material.EXPERIENCE_BOTTLE, 64));
                items.add(new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 32));
                break;
        }
        return items;
    }

    // ==================== MÉTODOS PARA CREAR ITEMS DEL KIT DEMON (EXACTOS COMO /KIT DEMON) ====================

    private ItemStack createDemonHelmet() {
        ItemStack helmet = new ItemStack(Material.NETHERITE_HELMET);
        ItemMeta hMeta = helmet.getItemMeta();
        hMeta.setDisplayName(sc("§4§k|§r §c§lCorona del Abismo §4§k|"));
        hMeta.setLore(getDemonSetLore());
        hMeta.addEnchant(Enchantment.PROTECTION, 7, true);
        hMeta.addEnchant(Enchantment.UNBREAKING, 4, true);
        hMeta.addEnchant(Enchantment.MENDING, 1, true);
        hMeta.addEnchant(Enchantment.RESPIRATION, 4, true);
        hMeta.addEnchant(Enchantment.AQUA_AFFINITY, 4, true);
        hMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        hMeta.getPersistentDataContainer().set(com.moonlight.coreprotect.kits.KitDemon.getDemonKey(plugin), org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
        helmet.setItemMeta(hMeta);
        return helmet;
    }

    private ItemStack createDemonChestplate() {
        ItemStack chestplate = new ItemStack(Material.NETHERITE_CHESTPLATE);
        ItemMeta cMeta = chestplate.getItemMeta();
        cMeta.setDisplayName(sc("§4§k|§r §c§lCoraza del Diablo §4§k|"));
        cMeta.setLore(getDemonSetLore());
        cMeta.addEnchant(Enchantment.PROTECTION, 7, true);
        cMeta.addEnchant(Enchantment.UNBREAKING, 4, true);
        cMeta.addEnchant(Enchantment.MENDING, 1, true);
        cMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        cMeta.getPersistentDataContainer().set(com.moonlight.coreprotect.kits.KitDemon.getDemonKey(plugin), org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
        chestplate.setItemMeta(cMeta);
        return chestplate;
    }

    private ItemStack createDemonLeggings() {
        ItemStack leggings = new ItemStack(Material.NETHERITE_LEGGINGS);
        ItemMeta lMeta = leggings.getItemMeta();
        lMeta.setDisplayName(sc("§4§k|§r §c§lGrebas Infernales §4§k|"));
        lMeta.setLore(getDemonSetLore());
        lMeta.addEnchant(Enchantment.PROTECTION, 7, true);
        lMeta.addEnchant(Enchantment.UNBREAKING, 4, true);
        lMeta.addEnchant(Enchantment.MENDING, 1, true);
        lMeta.addEnchant(Enchantment.SWIFT_SNEAK, 4, true);
        lMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        lMeta.getPersistentDataContainer().set(com.moonlight.coreprotect.kits.KitDemon.getDemonKey(plugin), org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
        leggings.setItemMeta(lMeta);
        return leggings;
    }

    private ItemStack createDemonBoots() {
        ItemStack boots = new ItemStack(Material.NETHERITE_BOOTS);
        ItemMeta bMeta = boots.getItemMeta();
        bMeta.setDisplayName(sc("§4§k|§r §c§lBotas de la Condena §4§k|"));
        bMeta.setLore(getDemonSetLore());
        bMeta.addEnchant(Enchantment.PROTECTION, 7, true);
        bMeta.addEnchant(Enchantment.UNBREAKING, 4, true);
        bMeta.addEnchant(Enchantment.MENDING, 1, true);
        bMeta.addEnchant(Enchantment.FEATHER_FALLING, 5, true);
        bMeta.addEnchant(Enchantment.DEPTH_STRIDER, 4, true);
        bMeta.addEnchant(Enchantment.SOUL_SPEED, 4, true);
        bMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        bMeta.getPersistentDataContainer().set(com.moonlight.coreprotect.kits.KitDemon.getDemonKey(plugin), org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
        boots.setItemMeta(bMeta);
        return boots;
    }

    private ItemStack createDemonSword() {
        ItemStack sword = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta swordMeta = sword.getItemMeta();
        swordMeta.setDisplayName(sc("§4§k|§r §c§lColmillo Demoníaco §4§k|"));
        swordMeta.setLore(getDemonSetLore());
        swordMeta.addEnchant(Enchantment.SHARPNESS, 7, true);
        swordMeta.addEnchant(Enchantment.FIRE_ASPECT, 4, true);
        swordMeta.addEnchant(Enchantment.LOOTING, 4, true);
        swordMeta.addEnchant(Enchantment.UNBREAKING, 4, true);
        swordMeta.addEnchant(Enchantment.MENDING, 1, true);
        swordMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        swordMeta.getPersistentDataContainer().set(com.moonlight.coreprotect.kits.KitDemon.getDemonKey(plugin), org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
        sword.setItemMeta(swordMeta);
        return sword;
    }

    private ItemStack createDemonPickaxe() {
        ItemStack pickaxe = new ItemStack(Material.NETHERITE_PICKAXE);
        ItemMeta meta = pickaxe.getItemMeta();
        meta.setDisplayName(sc("§4§lPico Demoníaco"));
        meta.addEnchant(Enchantment.EFFICIENCY, 7, true);
        meta.addEnchant(Enchantment.FORTUNE, 4, true);
        meta.addEnchant(Enchantment.UNBREAKING, 4, true);
        meta.addEnchant(Enchantment.MENDING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(com.moonlight.coreprotect.kits.KitDemon.getDemonKey(plugin), org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
        pickaxe.setItemMeta(meta);
        return pickaxe;
    }

    private ItemStack createDemonAxe() {
        ItemStack axe = new ItemStack(Material.NETHERITE_AXE);
        ItemMeta meta = axe.getItemMeta();
        meta.setDisplayName(sc("§4§lHacha Demoníaca"));
        meta.addEnchant(Enchantment.EFFICIENCY, 7, true);
        meta.addEnchant(Enchantment.FORTUNE, 4, true);
        meta.addEnchant(Enchantment.UNBREAKING, 4, true);
        meta.addEnchant(Enchantment.MENDING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(com.moonlight.coreprotect.kits.KitDemon.getDemonKey(plugin), org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
        axe.setItemMeta(meta);
        return axe;
    }

    private ItemStack createDemonShovel() {
        ItemStack shovel = new ItemStack(Material.NETHERITE_SHOVEL);
        ItemMeta meta = shovel.getItemMeta();
        meta.setDisplayName(sc("§4§lPala Demoníaca"));
        meta.addEnchant(Enchantment.EFFICIENCY, 7, true);
        meta.addEnchant(Enchantment.SILK_TOUCH, 1, true);
        meta.addEnchant(Enchantment.UNBREAKING, 4, true);
        meta.addEnchant(Enchantment.MENDING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(com.moonlight.coreprotect.kits.KitDemon.getDemonKey(plugin), org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
        shovel.setItemMeta(meta);
        return shovel;
    }

    private ItemStack createDemonHoe() {
        ItemStack hoe = new ItemStack(Material.NETHERITE_HOE);
        ItemMeta meta = hoe.getItemMeta();
        meta.setDisplayName(sc("§4§lAzada Demoníaca"));
        meta.addEnchant(Enchantment.EFFICIENCY, 7, true);
        meta.addEnchant(Enchantment.FORTUNE, 4, true);
        meta.addEnchant(Enchantment.UNBREAKING, 4, true);
        meta.addEnchant(Enchantment.MENDING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(com.moonlight.coreprotect.kits.KitDemon.getDemonKey(plugin), org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
        hoe.setItemMeta(meta);
        return hoe;
    }

    private List<String> getDemonSetLore() {
        return java.util.Arrays.asList(
                "§8§m                              ",
                "§4§l✦ Set Demoniaco §4§l✦",
                "§8§m                              ",
                "§c▸ Poder del Abismo",
                "§7  Espada: Furia Demoníaca (Dash Igneo)",
                "§7  Peto: Otorga §cFuerza II§7 permanentemente.",
                "§7  Botas: Otorga §bVelocidad II§7 permanentemente.",
                "",
                "§c▸ Alma Ardiente §8(1 pieza)",
                "§7  Inmunidad al fuego. Absorber",
                "§7  llamas restaura vida.",
                "",
                "§c▸ Sed de Sangre §8(2 piezas)",
                "§7  Robavida §c20%§7. §eCeleridad I §7al matar.",
                "",
                "§c▸ Marca Demoniaca §8(2 piezas)",
                "§7  Golpear marca al objetivo §c8s§7.",
                "§7  §c+35% §7daño contra marcados. CD §e15s§7.",
                "",
                "§c▸ Aura Infernal §8(3 piezas)",
                "§7  Anillo de fuego: §8Marchit. II §7+",
                "§7  daño a enemigos en §c4 bloques§7.",
                "",
                "§c▸ Pacto Demoníaco §8(4 piezas)",
                "§7  Al §c20% HP§7: §cFuerza III §7+ §aRegen III",
                "§7  + empuje demoníaco. CD §e60s§7.",
                "",
                "§8§m                              ",
                "§4§o\"El demonio que llevas dentro despierta.\""
        );
    }

    private ItemStack createDemonElytra() {
        ItemStack elytra = new ItemStack(Material.ELYTRA);
        ItemMeta em = elytra.getItemMeta();
        em.setDisplayName(sc("§4§lElytra Demon"));
        em.addEnchant(Enchantment.UNBREAKING, 3, true);
        em.addEnchant(Enchantment.MENDING, 1, true);
        elytra.setItemMeta(em);
        return elytra;
    }

    private static String sc(String s) { return SmallCaps.convert(s); }

    private ItemStack enchant(ItemStack item, String name, Map<Enchantment, Integer> enchants) {
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(sc(name));
        for (Map.Entry<Enchantment, Integer> e : enchants.entrySet()) {
            meta.addEnchant(e.getKey(), e.getValue(), true);
        }
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack item(Material material, String name, String... lore) {
        ItemStack i = new ItemStack(material);
        ItemMeta m = i.getItemMeta();
        m.setDisplayName(sc(name));
        if (lore.length > 0) {
            List<String> conv = new ArrayList<>();
            for (String l : lore) conv.add(sc(l));
            m.setLore(conv);
        }
        m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        i.setItemMeta(m);
        return i;
    }

    private ItemStack kitItem(Material material, String name, boolean hasPermission, boolean onCooldown,
                              String cooldownTime, String kitId, String... extraLore) {
        ItemStack i = new ItemStack(material);
        ItemMeta m = i.getItemMeta();
        m.setDisplayName(sc(name));
        List<String> lore = new ArrayList<>();
        for (String l : extraLore) lore.add(sc(l));
        lore.add("");
        if (!hasPermission) {
            lore.add(sc("§c✖ No tienes este rango"));
        } else if (onCooldown) {
            lore.add(sc("§c⏳ Cooldown: §e" + cooldownTime));
        } else {
            lore.add(sc("§a◀ Click izquierdo para reclamar"));
        }
        lore.add(sc("§e▶ Click derecho para ver contenido"));
        m.setLore(lore);
        m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        if (hasPermission && !onCooldown) {
            m.addEnchant(Enchantment.UNBREAKING, 1, true);
        }
        i.setItemMeta(m);
        return i;
    }

    private ItemStack enchWithAbility(ItemStack item, String name, Map<Enchantment, Integer> enchants, String abilityName, String abilityDesc, String cooldown) {
        ItemMeta m = item.getItemMeta();
        m.setDisplayName(sc(name));
        for (Map.Entry<Enchantment, Integer> e : enchants.entrySet()) m.addEnchant(e.getKey(), e.getValue(), true);
        
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(sc(abilityName));
        lore.add(sc(abilityDesc));
        lore.add(sc(cooldown));
        m.setLore(lore);
        
        item.setItemMeta(m);
        return item;
    }
}
