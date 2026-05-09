package com.moonlight.coreprotect.bossarena;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * Interfaz común para todos los bosses del sistema Boss Arena.
 */
public interface Boss {

    /**
     * Prepara la arena ANTES de la cuenta atras. Los bosses con mundo propio
     * deben crear el mundo, construir la arena, y actualizar las locations aqui.
     * Se llama antes de que los jugadores puedan hacer /boss.
     */
    default void prepareArena() {}

    void spawn();

    void cleanup();

    void onDamage(EntityDamageByEntityEvent event);

    boolean isActive();

    boolean isBossEntity(Entity entity);

    boolean isSummonedMob(Entity entity);

    Player getLastHitter();

    String getBossName();

    BossType getBossType();
}
