package com.moonlight.coreprotect.effects;

import org.bukkit.Location;
import org.bukkit.Sound;

public class SoundManager {

    public static void playActivationSound(Location location) {
        location.getWorld().playSound(location, Sound.BLOCK_BEACON_ACTIVATE, 0.5f, 1.2f);
        location.getWorld().playSound(location, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.3f, 1.0f);
    }

    public static void playAmbientSound(Location location) {
        location.getWorld().playSound(location, Sound.BLOCK_BEACON_AMBIENT, 0.4f, 1.5f);
        location.getWorld().playSound(location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.2f, 0.5f);
    }

    public static void playFinalExplosion(Location location) {
        location.getWorld().playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.8f);
        location.getWorld().playSound(location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        location.getWorld().playSound(location, Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 0.5f);
        location.getWorld().playSound(location, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.3f, 2.0f);
    }

    public static void playProtectionDenied(Location location) {
        location.getWorld().playSound(location, Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
        location.getWorld().playSound(location, Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.5f);
    }

    public static void playCorePlaced(Location location) {
        location.getWorld().playSound(location, Sound.BLOCK_ANVIL_PLACE, 0.8f, 0.8f);
        location.getWorld().playSound(location, Sound.BLOCK_BEACON_ACTIVATE, 0.5f, 1.0f);
    }

    public static void playCoreRemoved(Location location) {
        location.getWorld().playSound(location, Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 1.0f);
        location.getWorld().playSound(location, Sound.ENTITY_ITEM_BREAK, 0.5f, 0.8f);
    }

    public static void playMemberAdded(Location location) {
        location.getWorld().playSound(location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
    }

    public static void playMemberRemoved(Location location) {
        location.getWorld().playSound(location, Sound.ENTITY_ITEM_BREAK, 0.5f, 1.0f);
    }

    public static void playUpgradeAmbient(Location location, int tick) {
        float pitch = 0.5f + (tick / 400.0f) * 1.5f; // Rising pitch over time
        location.getWorld().playSound(location, Sound.BLOCK_BEACON_AMBIENT, 0.8f, pitch);
        location.getWorld().playSound(location, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.5f, pitch);
        if (tick > 160) {
            location.getWorld().playSound(location, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.6f, pitch);
        }
    }

    public static void playUpgradeTransform(Location location) {
        location.getWorld().playSound(location, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.5f);
        location.getWorld().playSound(location, Sound.BLOCK_END_PORTAL_SPAWN, 0.8f, 1.2f);
        location.getWorld().playSound(location, Sound.ITEM_TRIDENT_THUNDER, 1.0f, 1.0f);
        location.getWorld().playSound(location, Sound.ENTITY_WITHER_SPAWN, 0.3f, 2.0f);
    }

    public static void playUpgradeComplete(Location location) {
        location.getWorld().playSound(location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        location.getWorld().playSound(location, Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 0.8f);
        location.getWorld().playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.2f);
        location.getWorld().playSound(location, Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.5f);
        location.getWorld().playSound(location, Sound.ENTITY_ENDER_DRAGON_DEATH, 0.3f, 2.0f);
    }

    public static void playUpgradePurchased(Location location) {
        location.getWorld().playSound(location, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        location.getWorld().playSound(location, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 1.5f);
    }

    public static void playUpgradeDenied(Location location) {
        location.getWorld().playSound(location, Sound.ENTITY_VILLAGER_NO, 0.5f, 1.0f);
        location.getWorld().playSound(location, Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.5f);
    }

    public static void playGUIOpen(Location location) {
        location.getWorld().playSound(location, Sound.BLOCK_ENDER_CHEST_OPEN, 0.5f, 1.2f);
    }

    public static void playGUIClick(Location location) {
        location.getWorld().playSound(location, Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
    }
}
