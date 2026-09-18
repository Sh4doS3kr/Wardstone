package com.moonlight.coreprotect.crafting;

import com.moonlight.coreprotect.CoreProtectPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Receta para craftear Bocadillo Cubee (Cubee Snack)
 * Ingredientes:
 * - 4 Semillas de trigo (forma de +)
 * - 1 Patata (centro)
 *
 * Al craftear, el resultado real es el item de MythicMobs "Cubee_Snack".
 */
public class CubeeSnackRecipe implements Listener {

    private static final NamespacedKey RECIPE_KEY = new NamespacedKey(
            CoreProtectPlugin.getInstance(), "cubee_snack");

    private static final String MYTHIC_ITEM_ID = "Cubee_Snack";

    /**
     * Registra la receta y el listener en el servidor
     */
    public static void register() {
        // Eliminar receta existente si existe (para evitar duplicados en reload)
        unregister();

        ItemStack result = createPlaceholderSnack();

        ShapedRecipe recipe = new ShapedRecipe(RECIPE_KEY, result);
        recipe.shape(
                " S ",
                "SPS",
                " S "
        );
        recipe.setIngredient('S', Material.WHEAT_SEEDS);
        recipe.setIngredient('P', Material.POTATO);

        Bukkit.addRecipe(recipe);

        // Registrar listener para interceptar el crafteo
        CubeeSnackRecipe listener = new CubeeSnackRecipe();
        Bukkit.getPluginManager().registerEvents(listener, CoreProtectPlugin.getInstance());

        CoreProtectPlugin.getInstance().getLogger().info("[Recipes] Receta de Bocadillo Cubee registrada.");
    }

    /**
     * Placeholder visual para la mesa de crafteo (se reemplaza al craftear)
     */
    private static ItemStack createPlaceholderSnack() {
        ItemStack item = new ItemStack(Material.POTATO);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§bBocadillo Cubee!");
            meta.setLore(java.util.Arrays.asList(
                    "§7Click derecho en un Cubee Dizzy (mareado)",
                    "§7para intentar domarlo.",
                    "§eProbabilidad de éxito: 5.25%",
                    "§7Se consume al usarlo."
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Cuando se prepara el crafteo, reemplazar el resultado con el item real de MythicMobs
     */
    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        if (event.getRecipe() == null) return;
        if (!(event.getRecipe() instanceof ShapedRecipe shaped)) return;
        if (!shaped.getKey().equals(RECIPE_KEY)) return;

        // Intentar obtener el item real de MythicMobs
        ItemStack mythicItem = getMythicItem(MYTHIC_ITEM_ID);
        if (mythicItem != null) {
            event.getInventory().setResult(mythicItem);
        }
    }

    /**
     * Cuando se completa el crafteo, cancelar el evento vanilla y gestionar manualmente
     * para evitar dupe de ingredientes.
     */
    @EventHandler
    public void onCraft(CraftItemEvent event) {
        if (event.getRecipe() == null) return;
        if (!(event.getRecipe() instanceof ShapedRecipe shaped)) return;
        if (!shaped.getKey().equals(RECIPE_KEY)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // Cancelar el crafteo vanilla para evitar dupes
        event.setCancelled(true);

        // Consumir ingredientes manualmente (1 de cada slot ocupado)
        CraftingInventory inv = event.getInventory();
        ItemStack[] matrix = inv.getMatrix();
        for (int i = 0; i < matrix.length; i++) {
            ItemStack slot = matrix[i];
            if (slot != null && slot.getType() != Material.AIR) {
                if (slot.getAmount() > 1) {
                    slot.setAmount(slot.getAmount() - 1);
                } else {
                    matrix[i] = null;
                }
            }
        }
        inv.setMatrix(matrix);
        inv.setResult(new ItemStack(Material.AIR));

        // Dar el item real de MythicMobs al jugador
        Bukkit.getScheduler().runTask(CoreProtectPlugin.getInstance(), () -> {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "mm items give " + player.getName() + " " + MYTHIC_ITEM_ID);
        });
    }

    /**
     * Obtiene un ItemStack de MythicMobs via reflexion.
     * Devuelve null si MythicMobs no esta disponible.
     */
    private static ItemStack getMythicItem(String itemId) {
        try {
            Class<?> mb = Class.forName("io.lumine.mythic.bukkit.MythicBukkit");
            Object inst = mb.getMethod("inst").invoke(null);
            Object itemManager = mb.getMethod("getItemManager").invoke(inst);

            // Optional<MythicItem> getItem(String)
            Object opt = itemManager.getClass().getMethod("getItem", String.class).invoke(itemManager, itemId);
            if (opt instanceof java.util.Optional<?> o && o.isPresent()) {
                Object mythicItem = o.get();
                // MythicItem#generateItemStack(int amount)
                Object result = mythicItem.getClass().getMethod("generateItemStack", int.class).invoke(mythicItem, 1);
                if (result instanceof ItemStack) {
                    return (ItemStack) result;
                }
            }
        } catch (Exception e) {
            // Silenciar — se usara fallback con comando
        }
        return null;
    }

    /**
     * Elimina la receta del servidor (para reload)
     */
    public static void unregister() {
        Bukkit.removeRecipe(RECIPE_KEY);
    }
}
