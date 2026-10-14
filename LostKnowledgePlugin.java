package com.kodari.lostknowledge;

import com.cryptomorin.xseries.XMaterial;
import com.kodari.lostknowledge.command.BlueprintCommand;
import com.kodari.lostknowledge.gui.BlueprintMenu;
import com.kodari.lostknowledge.listener.BlueprintListener;
import com.kodari.lostknowledge.loot.LootTableManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

public final class LostKnowledgePlugin extends JavaPlugin {
    private FileConfiguration messages;
    private BlueprintService blueprintService;
    private LootTableManager lootTableManager;
    private BlueprintMenu blueprintMenu;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResourceIfMissing("blueprints.yml");
        updateBlueprintDefinitionsIfNeeded();
        saveResourceIfMissing("loot-tables.yml");
        saveResourceIfMissing("messages.yml");
        loadMessages();

        blueprintService = new BlueprintService(this);
        blueprintService.reload();
        lootTableManager = new LootTableManager(this, blueprintService);
        lootTableManager.reload();
        blueprintMenu = new BlueprintMenu(blueprintService);

        Bukkit.getPluginManager().registerEvents(new BlueprintListener(this, blueprintService, blueprintMenu), this);
        Bukkit.getPluginManager().registerEvents(lootTableManager, this);

        BlueprintCommand commandExecutor = new BlueprintCommand(this, blueprintService, blueprintMenu, lootTableManager);
        if (getCommand("blueprint") != null) {
            getCommand("blueprint").setExecutor(commandExecutor);
            getCommand("blueprint").setTabCompleter(commandExecutor);
        }
    }

    public void reloadPluginData() {
        reloadConfig();
        loadMessages();
        blueprintService.reload();
        lootTableManager.reload();
    }

    public BlueprintService getBlueprintService() {
        return blueprintService;
    }

    public LootTableManager getLootTableManager() {
        return lootTableManager;
    }

    public BlueprintMenu getBlueprintMenu() {
        return blueprintMenu;
    }

    public Material getResearchTableMaterial() {
        String materialName = getConfig().getString("research-table.material", "SMITHING_TABLE");
        return XMaterial.matchXMaterial(materialName).map(XMaterial::parseMaterial).orElse(null);
    }

    public int getResearchCommandDistance() {
        return Math.max(1, getConfig().getInt("research-table.command-distance", 5));
    }

    public String message(String key, String... replacements) {
        String prefix = messages.getString("prefix", "");
        String fallback = key.equals("blueprint-outside-race-territory")
                ? "%prefix%Blueprintą gali panaudoti tik savo rasės teritorijoje."
                : key;
        String text = messages.getString(key, fallback).replace("%prefix%", prefix);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            text = text.replace("%" + replacements[i] + "%", replacements[i + 1]);
        }
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    public void giveItem(org.bukkit.entity.Player player, org.bukkit.inventory.ItemStack item) {
        if (item == null) {
            return;
        }
        player.getInventory().addItem(item).values().forEach(overflow ->
                player.getWorld().dropItemNaturally(player.getLocation(), overflow));
    }

    private void saveResourceIfMissing(String resource) {
        File file = new File(getDataFolder(), resource);
        if (!file.exists()) {
            saveResource(resource, false);
        }
    }

    private void updateBlueprintDefinitionsIfNeeded() {
        File file = new File(getDataFolder(), "blueprints.yml");
        FileConfiguration configuration = YamlConfiguration.loadConfiguration(file);
        if (configuration.getInt("blueprint-format", 0) < 3) {
            saveResource("blueprints.yml", true);
            saveResource("loot-tables.yml", true);
        }
    }

    private void loadMessages() {
        File file = new File(getDataFolder(), "messages.yml");
        messages = YamlConfiguration.loadConfiguration(file);
    }
}
