package com.moonlight.coreprotect.util;

import org.bukkit.GameRule;
import org.bukkit.World;

public final class GameRuleUtil {

    private GameRuleUtil() {}

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void set(World world, String rule, boolean value) {
        GameRule gameRule = GameRule.getByName(rule);
        if (gameRule != null) {
            world.setGameRule(gameRule, value);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Boolean get(World world, String rule) {
        GameRule gameRule = GameRule.getByName(rule);
        if (gameRule != null) {
            return (Boolean) world.getGameRuleValue(gameRule);
        }
        return null;
    }
}
