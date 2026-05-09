package com.moonlight.coreprotect.commands;

import com.moonlight.coreprotect.CoreProtectPlugin;
import com.moonlight.coreprotect.util.GameRuleUtil;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.entity.SpawnCategory;

public class MobDiagCommand implements CommandExecutor {

    private final CoreProtectPlugin plugin;

    public MobDiagCommand(CoreProtectPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage("§cSolo operadores pueden usar este comando.");
            return true;
        }

        sender.sendMessage("§6§l=== Diagnóstico de Spawn de Mobs ===");

        for (World world : plugin.getServer().getWorlds()) {
            sender.sendMessage("");
            sender.sendMessage("§e§lMundo: §f" + world.getName());
            sender.sendMessage("  §7Dificultad: §f" + world.getDifficulty().name());

            Boolean doMobSpawning = GameRuleUtil.get(world, "doMobSpawning");
            sender.sendMessage("  §7doMobSpawning: " + (Boolean.TRUE.equals(doMobSpawning) ? "§atrue" : "§cfalse"));

            try {
                int monsterLimit = world.getSpawnLimit(SpawnCategory.MONSTER);
                sender.sendMessage("  §7Monster spawn limit: §f" + monsterLimit + " §7(default: 70)");
            } catch (Exception e) {
                sender.sendMessage("  §7Monster spawn limit: §c(error al leer)");
            }

            try {
                int animalLimit = world.getSpawnLimit(SpawnCategory.ANIMAL);
                sender.sendMessage("  §7Animal spawn limit: §f" + animalLimit);
            } catch (Exception ignored) {}

            @SuppressWarnings({"unchecked", "rawtypes"})
            org.bukkit.GameRule rtsRule = org.bukkit.GameRule.getByName("randomTickSpeed");
            Integer randomTickSpeed = rtsRule != null ? (Integer) world.getGameRuleValue(rtsRule) : null;
            sender.sendMessage("  §7randomTickSpeed: §f" + (randomTickSpeed != null ? randomTickSpeed : "?"));

            // Count hostile entities currently in world
            long hostileCount = world.getEntities().stream()
                    .filter(e -> e instanceof org.bukkit.entity.Monster)
                    .count();
            sender.sendMessage("  §7Mobs hostiles actuales: §f" + hostileCount);
        }

        // TPSMonitor status
        try {
            boolean safeMode = plugin.getTpsMonitor() != null && plugin.getTpsMonitor().isSafeMode();
            sender.sendMessage("");
            sender.sendMessage("§e§lTPSMonitor safe mode: " + (safeMode ? "§c§lACTIVO (reduce spawns!)" : "§aInactivo"));
        } catch (Exception e) {
            sender.sendMessage("§e§lTPSMonitor: §7no disponible");
        }

        // Player-specific info
        if (sender instanceof Player) {
            Player p = (Player) sender;
            var region = plugin.getProtectionManager().getRegionAt(p.getLocation());
            sender.sendMessage("");
            if (region != null) {
                sender.sendMessage("§e§lTu ubicación: §cDentro de región protegida");
                sender.sendMessage("  §7noMobSpawn: " + (region.isNoMobSpawn() ? "§cSí (bloquea hostiles)" : "§aNo"));
            } else if (plugin.getProtectionManager().isSpawnCore(p.getLocation())) {
                sender.sendMessage("§e§lTu ubicación: §cDentro del spawn core (zombies eliminados)");
            } else {
                sender.sendMessage("§e§lTu ubicación: §aTierra libre (mobs deberían spawnear)");
            }
        }

        sender.sendMessage("");
        sender.sendMessage("§6§l=== Fin del diagnóstico ===");
        return true;
    }
}
