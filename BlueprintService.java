package com.kodari.lostknowledge;

import com.cryptomorin.xseries.XMaterial;
import com.cryptomorin.xseries.XEnchantment;
import com.cryptomorin.xseries.XItemFlag;
import com.cryptomorin.xseries.XSound;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class BlueprintService {
    private final LostKnowledgePlugin plugin;
    private final RacePluginIntegration raceIntegration;
    private final NamespacedKey itemTypeKey;
    private final NamespacedKey itemIdKey;
    private final Map<String, BlueprintDefinition> blueprints = new HashMap<>();
    private final Map<UUID, Set<String>> unlocked = new HashMap<>();
    private final Map<String, Set<String>> raceUnlocked = new HashMap<>();
    private FileConfiguration definitionConfig;
    private FileConfiguration progressConfig;
    private File progressFile;

    public BlueprintService(LostKnowledgePlugin plugin) {
        this.plugin = plugin;
        raceIntegration = new RacePluginIntegration(plugin);
        itemTypeKey = new NamespacedKey(plugin, "item_type");
        itemIdKey = new NamespacedKey(plugin, "item_id");
    }

    public void reload() {
        definitionConfig = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "blueprints.yml"));
        blueprints.clear();
        ConfigurationSection section = definitionConfig.getConfigurationSection("blueprints");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection blueprint = section.getConfigurationSection(id);
                if (blueprint == null) {
                    continue;
                }
                blueprints.put(id.toLowerCase(), new BlueprintDefinition(
                        id.toLowerCase(),
                        blueprint.getString("name", id),
                        blueprint.getString("color", "&f"),
                        blueprint.getString("rarity", "COMMON"),
                        blueprint.getString("category", "RESEARCH"),
                        blueprint.getString("description", ""),
                        blueprint.getString("discovery-hint", defaultDiscoveryHint(id)),
                        blueprint.getString("item-material", "PAPER"),
                        blueprint.getString("recipe-result", "")
                ));
            }
        }
        loadProgress();
    }

    public BlueprintDefinition getBlueprint(String id) {
        return id == null ? null : blueprints.get(id.toLowerCase());
    }

    public Collection<BlueprintDefinition> getBlueprints() {
        return Collections.unmodifiableCollection(blueprints.values());
    }

    public List<String> getBlueprintIds() {
        List<String> ids = new ArrayList<>(blueprints.keySet());
        Collections.sort(ids);
        return ids;
    }

    public boolean hasUnlocked(Player player, String id) {
        String normalizedId = id.toLowerCase();
        String raceId = raceIntegration.getRaceId(player);
        return (raceId != null && raceUnlocked.getOrDefault(raceId, Collections.emptySet()).contains(normalizedId))
                || unlocked.getOrDefault(player.getUniqueId(), Collections.emptySet()).contains(normalizedId);
    }

    public Set<String> getUnlocked(Player player) {
        Set<String> playerBlueprints = new HashSet<>(unlocked.getOrDefault(player.getUniqueId(), Collections.emptySet()));
        String raceId = raceIntegration.getRaceId(player);
        if (raceId != null) {
            playerBlueprints.addAll(raceUnlocked.getOrDefault(raceId, Collections.emptySet()));
        }
        return Collections.unmodifiableSet(playerBlueprints);
    }

    public boolean unlock(Player player, String id) {
        if (getBlueprint(id) == null) {
            return false;
        }
        String raceId = raceIntegration.getRaceId(player);
        if (raceId == null) {
            return unlock(player.getUniqueId(), id);
        }
        Set<String> raceBlueprints = raceUnlocked.computeIfAbsent(raceId, ignored -> new HashSet<>());
        if (!raceBlueprints.add(id.toLowerCase())) {
            return false;
        }
        saveProgress();
        return true;
    }

    public boolean unlock(UUID playerId, String id) {
        if (getBlueprint(id) == null) {
            return false;
        }
        Set<String> playerBlueprints = unlocked.computeIfAbsent(playerId, ignored -> new HashSet<>());
        if (!playerBlueprints.add(id.toLowerCase())) {
            return false;
        }
        saveProgress();
        return true;
    }

    public boolean hasUnlockedRecipe(Player player, String materialName) {
        boolean hasConfiguredRecipe = false;
        for (BlueprintDefinition blueprint : blueprints.values()) {
            if (!blueprint.recipeResult.equalsIgnoreCase(materialName)) {
                continue;
            }
            hasConfiguredRecipe = true;
            if (hasUnlocked(player, blueprint.id)) {
                return true;
            }
        }
        return !hasConfiguredRecipe;
    }

    public ItemStack createBlueprintItem(String id) {
        BlueprintDefinition blueprint = getBlueprint(id);
        if (blueprint == null) {
            return null;
        }
        List<String> lore = new ArrayList<>();
        lore.add(color("&7" + blueprint.rarity + " &8• &7" + displayCategory(blueprint.category)));
        lore.add(color("&7" + blueprint.description));
        lore.add(color("&8Spustelėk dešiniuoju pelės mygtuku, kad išmoktum receptą."));
        lore.add(color("&8Receptas atrakinamas visam laikui."));
        return taggedItem(blueprintDisplayMaterial(blueprint.itemMaterial), color(blueprint.color + blueprint.name), lore,
                "blueprint", blueprint.id);
    }

    public ItemStack createRewardItem(String type, String id) {
        if (type == null || id == null) {
            return null;
        }
        return switch (type.toLowerCase()) {
            case "blueprint" -> createBlueprintItem(id);
            case "cache" -> createCacheItem(id);
            case "fragment" -> createFragmentItem(id);
            default -> null;
        };
    }

    public ItemStack createCacheItem(String tier) {
        ConfigurationSection cache = definitionConfig.getConfigurationSection("caches." + tier.toLowerCase());
        if (cache == null) {
            return null;
        }
        String name = cache.getString("name", tier + " brėžinių skrynia");
        return taggedItem(cache.getString("material", "CHEST"), color("&b" + name),
                List.of(color("&7Spustelėk dešiniuoju pelės mygtuku, kad rastum naują brėžinį.")), "cache", tier.toLowerCase());
    }

    public ItemStack createFragmentItem(String group) {
        ConfigurationSection fragment = definitionConfig.getConfigurationSection("fragments." + group.toLowerCase());
        if (fragment == null) {
            return null;
        }
        String name = fragment.getString("name", "Tyrimų fragmentas");
        return taggedItem(fragment.getString("material", "PAPER"), color("&d" + name),
                List.of(color("&7Prarastų žinių tyrimų fragmentas.")), "fragment", group.toLowerCase());
    }

    public ItemStack createCollectionItem(BlueprintDefinition blueprint, boolean learned) {
        String title = learned ? "&a" + blueprint.name : "&7Nežinomas receptas";
        List<String> lore = learned
                ? List.of(color("&7" + blueprint.rarity + " &8• &7" + displayCategory(blueprint.category)),
                        color("&aReceptas atrakintas"))
                : List.of(color("&7Kategorija: &f" + displayCategory(blueprint.category)),
                        color("&ePaspausk, kad sužinotum, kur ieškoti."));
        ItemStack item = taggedItem(blueprintDisplayMaterial("WRITABLE_BOOK"), color(title), lore,
                "collection", blueprint.id);
        if (learned && item != null && item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            XEnchantment.matchXEnchantment("UNBREAKING")
                    .ifPresent(enchantment -> meta.addEnchant(enchantment.getEnchant(), 1, true));
            XItemFlag.of("HIDE_ENCHANTS").ifPresent(flag -> flag.set(meta));
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack createResearchSelectionItem(BlueprintDefinition blueprint) {
        return taggedItem(blueprintDisplayMaterial(blueprint.itemMaterial), color(blueprint.color + blueprint.name),
                List.of(color("&7" + blueprint.rarity + " • " + displayCategory(blueprint.category)),
                        color("&7" + blueprint.discoveryHint), color("&ePaspausk, kad iškeistum fragmentus.")),
                "research-blueprint", blueprint.id);
    }

    private String blueprintDisplayMaterial(String fallback) {
        return XMaterial.matchXMaterial("FLOWER_BANNER_PATTERN")
                .map(XMaterial::parseMaterial)
                .map(Material::name)
                .orElse(fallback);
    }

    public ItemStack createMenuItem(String material, String title, List<String> lore) {
        List<String> coloredLore = new ArrayList<>();
        for (String line : lore) {
            coloredLore.add(color(line));
        }
        return taggedItem(material, color(title), coloredLore, "menu", "menu");
    }

    public String displayCategory(String category) {
        if (category == null) {
            return "Kiti receptai";
        }
        return switch (category.toUpperCase()) {
            case "TOOLS", "ĮRANKIAI" -> "Įrankiai";
            case "ARMOR_WEAPONS", "ARMOR", "ARMOUR", "WEAPONS", "GINKLAI", "ŠARVAI" -> "Šarvai ir ginklai";
            case "UTILITY", "UTILITIES", "NAUDINGIEJI DAIKTAI" -> "Naudingieji daiktai";
            case "ADVANCED", "PAŽANGŪS", "RESEARCH" -> "Pažangūs receptai";
            default -> category;
        };
    }

    public void sendBlueprintHint(Player player, String id) {
        BlueprintDefinition blueprint = getBlueprint(id);
        if (blueprint != null) {
            player.sendMessage(plugin.message("blueprint-hint", "name", blueprint.name, "hint", blueprint.discoveryHint));
        }
    }

    public ItemStack createFragmentResearchItem(String group, Player player) {
        ConfigurationSection fragment = definitionConfig.getConfigurationSection("fragments." + group);
        int required = fragment == null ? 1 : Math.max(1, fragment.getInt("required", 3));
        String name = fragment == null ? group : fragment.getString("name", group);
        int count = countFragments(player, group);
        return taggedItem(fragment == null ? "PAPER" : fragment.getString("material", "PAPER"),
                color("&dTyrimas: " + name),
                List.of(color("&7Fragmentai: &f" + count + "/" + required), color("&eSpustelėk, kad pasirinktum brėžinį.")),
                "research", group);
    }

    public boolean handleItemUse(Player player, ItemStack item) {
        String type = getItemValue(item, itemTypeKey);
        String id = getItemValue(item, itemIdKey);
        if (type == null || id == null) {
            return false;
        }
        if (type.equals("blueprint")) {
            BlueprintDefinition blueprint = getBlueprint(id);
            if (blueprint == null) {
                return false;
            }
            if (!raceIntegration.isInRaceTerritory(player, player.getLocation())) {
                player.sendMessage(plugin.message("blueprint-outside-race-territory"));
                return true;
            }
            consumeMainHand(player);
            if (unlock(player, id)) {
                player.sendMessage(plugin.message("blueprint-unlocked", "name", blueprint.name));
                XSound.matchXSound("ENTITY_PLAYER_LEVELUP").ifPresent(sound -> sound.play(player));
            } else {
                String fragmentId = duplicateFragmentId(blueprint);
                plugin.giveItem(player, createFragmentItem(fragmentId));
                player.sendMessage(plugin.message("duplicate-blueprint", "name", blueprint.name,
                        "fragment", getFragmentName(fragmentId)));
            }
            return true;
        }
        if (type.equals("cache")) {
            ConfigurationSection cache = definitionConfig.getConfigurationSection("caches." + id);
            if (cache == null) {
                return false;
            }
            consumeMainHand(player);
            openCache(player, id, cache);
            return true;
        }
        return false;
    }

    public List<String> getResearchableFragmentGroups() {
        List<String> groups = new ArrayList<>();
        ConfigurationSection fragments = definitionConfig.getConfigurationSection("fragments");
        if (fragments == null) {
            return groups;
        }
        for (String group : fragments.getKeys(false)) {
            ConfigurationSection fragment = fragments.getConfigurationSection(group);
            if (fragment != null && !fragment.getStringList("blueprints").isEmpty()) {
                groups.add(group);
            }
        }
        Collections.sort(groups);
        return groups;
    }

    public boolean researchFragments(Player player, String group) {
        List<String> eligible = getResearchableBlueprintIds(player, group);
        if (eligible.isEmpty()) {
            player.sendMessage(plugin.message("research-no-results"));
            return false;
        }
        return researchFragments(player, group, eligible.get(ThreadLocalRandom.current().nextInt(eligible.size())));
    }

    public List<String> getResearchableBlueprintIds(Player player, String group) {
        ConfigurationSection fragment = definitionConfig.getConfigurationSection("fragments." + group);
        if (fragment == null) {
            return List.of();
        }
        List<String> eligible = new ArrayList<>();
        for (String id : fragment.getStringList("blueprints")) {
            String normalizedId = id.toLowerCase();
            if (getBlueprint(normalizedId) != null && !hasUnlocked(player, normalizedId)
                    && !hasBlueprintItem(player, normalizedId) && !eligible.contains(normalizedId)) {
                eligible.add(normalizedId);
            }
        }
        Collections.sort(eligible);
        return eligible;
    }

    public boolean researchFragments(Player player, String group, String blueprintId) {
        ConfigurationSection fragment = definitionConfig.getConfigurationSection("fragments." + group);
        if (fragment == null) {
            return false;
        }
        List<String> eligible = getResearchableBlueprintIds(player, group);
        String selectedId = blueprintId == null ? "" : blueprintId.toLowerCase();
        if (eligible.isEmpty() || !eligible.contains(selectedId)) {
            player.sendMessage(plugin.message("research-no-results"));
            return false;
        }
        int required = Math.max(1, fragment.getInt("required", 3));
        int count = countFragments(player, group);
        if (count < required) {
            player.sendMessage(plugin.message("research-not-enough", "required", String.valueOf(required),
                    "fragment", fragment.getString("name", group), "count", String.valueOf(count)));
            return false;
        }
        removeFragments(player, group, required);
        ItemStack item = createBlueprintItem(selectedId);
        plugin.giveItem(player, item);
        BlueprintDefinition blueprint = getBlueprint(selectedId);
        player.sendMessage(plugin.message("research-success", "name", blueprint == null ? selectedId : blueprint.name));
        return true;
    }

    public int countFragments(Player player, String group) {
        int amount = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if ("fragment".equals(getItemValue(item, itemTypeKey)) && group.equals(getItemValue(item, itemIdKey))) {
                amount += item.getAmount();
            }
        }
        return amount;
    }

    public String getFragmentName(String group) {
        ConfigurationSection fragment = definitionConfig.getConfigurationSection("fragments." + group);
        return fragment == null ? group : fragment.getString("name", group);
    }

    private void openCache(Player player, String tier, ConfigurationSection cache) {
        List<String> eligible = new ArrayList<>();
        for (String id : cache.getStringList("blueprints")) {
            String normalizedId = id.toLowerCase();
            if (getBlueprint(normalizedId) != null && !hasUnlocked(player, normalizedId)
                    && !hasBlueprintItem(player, normalizedId) && !eligible.contains(normalizedId)) {
                eligible.add(normalizedId);
            }
        }
        if (eligible.isEmpty()) {
            String fragmentId = cache.getString("fallback-fragment", "trade_token");
            ItemStack fallback = createFragmentItem(fragmentId);
            plugin.giveItem(player, fallback);
            player.sendMessage(plugin.message("cache-empty", "fragment", getFragmentName(fragmentId)));
            return;
        }
        String id = eligible.get(ThreadLocalRandom.current().nextInt(eligible.size()));
        ItemStack reward = createBlueprintItem(id);
        plugin.giveItem(player, reward);
        BlueprintDefinition blueprint = getBlueprint(id);
        player.sendMessage(plugin.message("cache-opened", "name", blueprint == null ? id : blueprint.name));
    }

    private ItemStack taggedItem(String materialName, String displayName, List<String> lore, String type, String id) {
        Material material = XMaterial.matchXMaterial(materialName).map(XMaterial::parseMaterial).orElse(null);
        if (material == null) {
            return null;
        }
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        meta.setDisplayName(displayName);
        meta.setLore(lore);
        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(itemTypeKey, PersistentDataType.STRING, type);
        data.set(itemIdKey, PersistentDataType.STRING, id);
        item.setItemMeta(meta);
        return item;
    }

    private String getItemValue(ItemStack item, NamespacedKey key) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    private void consumeMainHand(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getAmount() <= 1) {
            player.getInventory().setItemInMainHand(null);
        } else {
            hand.setAmount(hand.getAmount() - 1);
        }
    }

    private void removeFragments(Player player, String group, int amount) {
        ItemStack[] contents = player.getInventory().getStorageContents();
        int remaining = amount;
        for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
            ItemStack item = contents[slot];
            if (!"fragment".equals(getItemValue(item, itemTypeKey)) || !group.equals(getItemValue(item, itemIdKey))) {
                continue;
            }
            int removed = Math.min(remaining, item.getAmount());
            remaining -= removed;
            if (item.getAmount() == removed) {
                contents[slot] = null;
            } else {
                item.setAmount(item.getAmount() - removed);
            }
        }
        player.getInventory().setStorageContents(contents);
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    private boolean hasBlueprintItem(Player player, String id) {
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if ("blueprint".equals(getItemValue(item, itemTypeKey))
                    && id.equalsIgnoreCase(getItemValue(item, itemIdKey))) {
                return true;
            }
        }
        return false;
    }

    private String duplicateFragmentId(BlueprintDefinition blueprint) {
        String defaultFragment = blueprint.rarity.equalsIgnoreCase("RETAS") ? "ancient" : "trade_token";
        return definitionConfig.getString("blueprints." + blueprint.id + ".duplicate-fragment", defaultFragment);
    }

    private String defaultDiscoveryHint(String id) {
        if (id.startsWith("iron_")) {
            return "Dažnai randamas kaimų kalvių, požemių ir apleistų kasyklų skryniose.";
        }
        if (id.startsWith("diamond_")) {
            return "Ieškok retų skrynių šventyklose, tvirtovėse ir kitose pavojingose struktūrose.";
        }
        return "Tyrinėk struktūrų skrynias ir ieškok retų radinių.";
    }

    private void loadProgress() {
        unlocked.clear();
        raceUnlocked.clear();
        progressFile = new File(plugin.getDataFolder(), "players.yml");
        progressConfig = YamlConfiguration.loadConfiguration(progressFile);
        ConfigurationSection races = progressConfig.getConfigurationSection("races");
        if (races != null) {
            for (String raceId : races.getKeys(false)) {
                Set<String> raceBlueprints = new HashSet<>();
                for (String id : races.getStringList(raceId + ".unlocked")) {
                    if (blueprints.containsKey(id.toLowerCase())) {
                        raceBlueprints.add(id.toLowerCase());
                    }
                }
                raceUnlocked.put(raceId.toLowerCase(), raceBlueprints);
            }
        }
        for (String uuidText : progressConfig.getKeys(false)) {
            if (uuidText.equals("races")) {
                continue;
            }
            try {
                UUID uuid = UUID.fromString(uuidText);
                Set<String> playerBlueprints = new HashSet<>();
                for (String id : progressConfig.getStringList(uuidText + ".unlocked")) {
                    if (blueprints.containsKey(id.toLowerCase())) {
                        playerBlueprints.add(id.toLowerCase());
                    }
                }
                unlocked.put(uuid, playerBlueprints);
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Ignoring invalid player UUID in players.yml: " + uuidText);
            }
        }
    }

    private void saveProgress() {
        for (Map.Entry<UUID, Set<String>> entry : unlocked.entrySet()) {
            progressConfig.set(entry.getKey() + ".unlocked", new ArrayList<>(entry.getValue()));
        }
        for (Map.Entry<String, Set<String>> entry : raceUnlocked.entrySet()) {
            progressConfig.set("races." + entry.getKey() + ".unlocked", new ArrayList<>(entry.getValue()));
        }
        try {
            progressConfig.save(progressFile);
        } catch (IOException exception) {
            plugin.getLogger().severe("Could not save blueprint progress: " + exception.getMessage());
        }
    }
}
