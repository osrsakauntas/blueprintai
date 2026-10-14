package com.kodari.lostknowledge;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Locale;

public final class RacePluginIntegration {
    private static final String RACE_PLUGIN_NAME = "RaceBorderProgression";
    private final LostKnowledgePlugin plugin;

    public RacePluginIntegration(LostKnowledgePlugin plugin) {
        this.plugin = plugin;
    }

    public String getRaceId(Player player) {
        Plugin racePlugin = getRacePlugin();
        if (racePlugin == null) {
            return null;
        }
        try {
            Object race = getRace(racePlugin, player);
            if (race == null) {
                return null;
            }
            Object raceId = race.getClass().getMethod("getId").invoke(race);
            return raceId instanceof String ? ((String) raceId).toLowerCase(Locale.ROOT) : null;
        } catch (ReflectiveOperationException | LinkageError exception) {
            plugin.getLogger().warning("Could not read player race from RaceBorderProgression: " + exception.getMessage());
            return null;
        }
    }

    public boolean isInRaceTerritory(Player player, Location location) {
        Plugin racePlugin = getRacePlugin();
        if (racePlugin == null || location == null || location.getWorld() == null) {
            return false;
        }
        try {
            Object race = getRace(racePlugin, player);
            if (race == null) {
                return false;
            }
            Object raceWorld = race.getClass().getMethod("getWorld").invoke(race);
            if (!(raceWorld instanceof String) || !location.getWorld().getName().equals(raceWorld)) {
                return false;
            }
            Object borderManager = racePlugin.getClass().getMethod("getBorderManager").invoke(racePlugin);
            if (borderManager == null) {
                return false;
            }
            return Boolean.TRUE.equals(borderManager.getClass()
                    .getMethod("isInside", race.getClass(), Location.class)
                    .invoke(borderManager, race, location));
        } catch (ReflectiveOperationException | LinkageError exception) {
            plugin.getLogger().warning("Could not check player race territory from RaceBorderProgression: "
                    + exception.getMessage());
            return false;
        }
    }

    private Plugin getRacePlugin() {
        Plugin racePlugin = Bukkit.getPluginManager().getPlugin(RACE_PLUGIN_NAME);
        return racePlugin != null && racePlugin.isEnabled() ? racePlugin : null;
    }

    private Object getRace(Plugin racePlugin, Player player) throws ReflectiveOperationException {
        Object raceManager = racePlugin.getClass().getMethod("getRaceManager").invoke(racePlugin);
        return raceManager == null ? null
                : raceManager.getClass().getMethod("getRace", Player.class).invoke(raceManager, player);
    }
}