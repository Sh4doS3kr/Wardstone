package com.moonlight.coreprotect.listeners;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.essence.EssenceManager;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Warden;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;

public class BossEssenceListener implements Listener {
    
    private final CoreProtectPlugin plugin;
    private final EssenceManager essenceManager;
    
    public BossEssenceListener(CoreProtectPlugin plugin) {
        this.plugin = plugin;
        this.essenceManager = plugin.getEssenceManager();
    }
    
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBossDeath(EntityDeathEvent event) {
        if (event.getEntity().getKiller() == null) {
            return;
        }
        
        Player killer = event.getEntity().getKiller();
        EntityType entityType = event.getEntity().getType();
        
        int essences = 0;
        String bossName = "";
        
        if (entityType == EntityType.ENDER_DRAGON) {
            essences = 2;
            bossName = "Ender Dragon";
        } else if (entityType == EntityType.WARDEN) {
            NamespacedKey key = new NamespacedKey(plugin, "warden_kills");
            int kills = killer.getPersistentDataContainer().getOrDefault(key, PersistentDataType.INTEGER, 0);
            kills++;
            
            if (kills >= 2) {
                essences = 1;
                bossName = "Warden (2 kills)";
                killer.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, 0);
            } else {
                killer.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, kills);
                killer.sendMessage("§d§l✦ §aHas matado a 1 Warden. §7¡Mata otro para poder conseguir §d+1 Esencia§7!");
                killer.playSound(killer.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
            }
        }
        
        if (essences > 0) {
            essenceManager.addEssences(killer.getUniqueId(), essences);
            killer.sendMessage("§d§l✦ §aHas recibido §d+" + essences + " Esencias §7por matar al §c" + bossName + "§7!");
            killer.playSound(killer.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
        }
    }
}
