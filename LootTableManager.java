package com.kodari.lostknowledge.loot;

import com.kodari.lostknowledge.BlueprintService;
import com.kodari.lostknowledge.LostKnowledgePlugin;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.loot.LootTable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class LootTableManager implements Listener {
    private final LostKnowledgePlugin plugin;
    private final BlueprintService blueprintService;
    private FileConfiguration lootConfig;

    public LootTableManager(LostKnowledgePlugin plugin, BlueprintService blueprintService) {
        this.plugin = plugin;
        this.blueprintService = blueprintService;
    }

    public void reload() {
        lootConfig = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "loot-tables.yml"));
    }

    public ConfigurationSection getStructure(String id) {
        return lootConfig.getConfigurationSection("structures." + id.toLowerCase());
    }

    public List<String> getStructureIds() {
        ConfigurationSection structures = lootConfig.getConfigurationSection("structures");
        return structures == null ? List.of() : new ArrayList<>(structures.getKeys(false));
    }

    @EventHandler
    public void onLootGenerate(LootGenerateEvent event) {
        String structureId = findStructure(event.getLootTable().getKey());
        if (structureId == null) {
            return;
        }
        ConfigurationSection structure = getStructure(structureId);
        if (structure == null) {
            return;
        }
        double chance = Math.max(0.0, Math.min(100.0, structure.getDouble("blueprint-chance", 0.0)));
        if (ThreadLocalRandom.current().nextDouble(100.0) >= chance) {
            return;
        }
        ItemStack reward = chooseReward(structure.getConfigurationSection("rewards"));
        if (reward != null) {
            event.getLoot().add(reward);
        }
    }

    private String findStructure(NamespacedKey tableKey) {
        String tablePath = tableKey.getKey();
        ConfigurationSection structures = lootConfig.getConfigurationSection("structures");
        if (structures == null) {
            return null;
        }
        for (String id : structures.getKeys(false)) {
            ConfigurationSection structure = structures.getConfigurationSection(id);
            if (structure != null && structure.getStringList("loot-tables").contains(tablePath)) {
                return id;
            }
        }
        return null;
    }

    private ItemStack chooseReward(ConfigurationSection rewards) {
        if (rewards == null) {
            return null;
        }
        int totalWeight = 0;
        List<String> rewardIds = new ArrayList<>();
        for (String rewardId : rewards.getKeys(false)) {
            ConfigurationSection reward = rewards.getConfigurationSection(rewardId);
            if (reward == null) {
                continue;
            }
            int weight = Math.max(0, reward.getInt("weight", 0));
            if (weight > 0) {
                totalWeight += weight;
                rewardIds.add(rewardId);
            }
        }
        if (totalWeight <= 0) {
            return null;
        }
        int roll = ThreadLocalRandom.current().nextInt(totalWeight);
        for (String rewardId : rewardIds) {
            ConfigurationSection reward = rewards.getConfigurationSection(rewardId);
            int weight = Math.max(0, reward == null ? 0 : reward.getInt("weight", 0));
            roll -= weight;
            if (roll < 0 && reward != null) {
                return blueprintService.createRewardItem(reward.getString("type"), reward.getString("id"));
            }
        }
        return null;
    }
}
