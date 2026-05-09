package com.moonlight.coreprotect.combos;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.util.SmallCaps;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;

public class CandleSwordCommand implements CommandExecutor {

    private final CoreProtectPlugin plugin;

    public CandleSwordCommand(CoreProtectPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(SmallCaps.convert("§cSolo jugadores pueden usar este comando."));
            return true;
        }

        Player player = (Player) sender;
        
        // Solo operadores pueden usar el comando
        if (!player.isOp()) {
            player.sendMessage(SmallCaps.convert("§c§l✖ §cSolo los operadores pueden usar este comando."));
            return true;
        }

        // Dar la vela de combos
        ItemStack candleSword = createCandleSword();
        player.getInventory().addItem(candleSword);
        
        player.sendMessage(SmallCaps.convert("§6§l✓ §6¡Vela de Combos recibida!"));
        player.sendMessage(SmallCaps.convert("§eUsa secuencias de clicks para ejecutar combos especiales."));
        player.sendMessage(SmallCaps.convert("§7Ejemplo: §fIzquierda, Izquierda, Derecha §7para combos rápidos."));
        
        return true;
    }

    private ItemStack createCandleSword() {
        ItemStack candle = new ItemStack(Material.RED_CANDLE);
        ItemMeta meta = candle.getItemMeta();

        // Nombre y lore con todos los combos
        meta.setDisplayName(SmallCaps.convert("§6§l✦ Vela Combos ✦"));
        
        List<String> lore = new ArrayList<>();
        lore.add("§8§m                              ");
        lore.add("§6§l⚔ Vela de Combos Secuenciales");
        lore.add("§8§m                              ");
        lore.add("");
        lore.add("§e§l✦ COMBOS RÁPIDOS (1.5s):");
        lore.add("§7• §fA-L-R §7= §aExplosión Veloz §8(3s) §7- Explosión instantánea");
        lore.add("§7• §fL-A-L §7= §bTorbellino §8(4s) §7- Tornado que empuja");
        lore.add("§7• §fR-A-R §7= §cImpacto Doble §8(3s) §7- Golpe crítico duplicado");
        lore.add("");
        lore.add("§e§l✦ COMBOS MEDIOS (2.0s):");
        lore.add("§7• §fA-L-A-R §7= §dFuria Tétrada §8(5s) §7- Cruz con fuego");
        lore.add("§7• §fL-R-A-L §7= §aSanto Grial §8(6s) §7- Curación y protección");
        lore.add("§7• §fR-L-A-R §7= §cCruz Infernal §8(5s) §7- Daño en línea recta");
        lore.add("");
        lore.add("§e§l✦ COMBOS LARGOS (2.5s):");
        lore.add("§7• §fA-L-R-A-L-R §7= §6Tsunami de Fuego §8(8s) §7- Onda de lava");
        lore.add("§7• §fL-A-R-L-A-R §7= §eTempestad Divina §8(10s) §7- Tormenta eléctrica");
        lore.add("§7• §fR-L-A-R-L-A §7= §bDanza Espectral §8(9s) §7- Espíritus aliados");
        lore.add("");
        lore.add("§6§l✦ COMBO MAESTRO (3.0s):");
        lore.add("§7• §fA-L-R-A-L-R-A §7= §cApocalipsis §8(15s) §7- Destrucción total");
        lore.add("");
        lore.add("§7§o§fL = Izquierdo | R = Derecho | A = Ambos a la vez");
        lore.add("§7§o§6¡Secuencias únicas - practica la memoria muscular!");
        lore.add("§8§m                              ");

        meta.setLore(lore);

        // Añadir encantamientos a la vela usando un sistema personalizado
        try {
            // Usar reflection para añadir encantamientos a items que normalmente no pueden tenerlos
            meta.addEnchant(Enchantment.SHARPNESS, 6, true);
            meta.addEnchant(Enchantment.FIRE_ASPECT, 2, true);
            meta.addEnchant(Enchantment.UNBREAKING, 3, true);
            meta.addEnchant(Enchantment.LOOTING, 3, true);
            meta.addEnchant(Enchantment.MENDING, 1, true);
            meta.addEnchant(Enchantment.SWEEPING_EDGE, 3, true);
            
            plugin.getLogger().info("[CandleSword] Encantamientos añadidos a la vela correctamente");
        } catch (Exception e) {
            plugin.getLogger().warning("[CandleSword] Error añadiendo encantamientos: " + e.getMessage());
        }

        // Flags y Persistent Data
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        
        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(CandleComboListener.getCandleSwordKey(plugin), PersistentDataType.BYTE, (byte) 1);

        candle.setItemMeta(meta);
        return candle;
    }
}
