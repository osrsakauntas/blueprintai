package com.kodari.lostknowledge.command;

import com.kodari.lostknowledge.BlueprintDefinition;
import com.kodari.lostknowledge.BlueprintService;
import com.kodari.lostknowledge.LostKnowledgePlugin;
import com.kodari.lostknowledge.gui.BlueprintMenu;
import com.kodari.lostknowledge.loot.LootTableManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class BlueprintCommand implements CommandExecutor, TabCompleter {
    private final LostKnowledgePlugin plugin;
    private final BlueprintService service;
    private final BlueprintMenu menu;
    private final LootTableManager lootTableManager;

    public BlueprintCommand(LostKnowledgePlugin plugin, BlueprintService service, BlueprintMenu menu,
                            LootTableManager lootTableManager) {
        this.plugin = plugin;
        this.service = service;
        this.menu = menu;
        this.lootTableManager = lootTableManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("menu")) {
            if (sender instanceof Player player) {
                menu.open(player, false);
                player.sendMessage(plugin.message("collection-open"));
            } else {
                sender.sendMessage(plugin.message("player-only"));
            }
            return true;
        }
        if (args[0].equalsIgnoreCase("research")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(plugin.message("player-only"));
                return true;
            }
            if (!isNearResearchTable(player)) {
                player.sendMessage(plugin.message("research-table-nearby"));
                return true;
            }
            menu.open(player, true);
            player.sendMessage(plugin.message("research-table-open"));
            return true;
        }
        if (args[0].equalsIgnoreCase("list")) {
            sender.sendMessage(plugin.message("blueprint-list", "ids", String.join(", ", service.getBlueprintIds())));
            return true;
        }
        if (args[0].equalsIgnoreCase("give") && args.length >= 3) {
            if (!requireAdmin(sender)) {
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[1]);
            BlueprintDefinition blueprint = service.getBlueprint(args[2]);
            if (target == null) {
                sender.sendMessage(plugin.message("player-not-found"));
            } else if (blueprint == null) {
                sender.sendMessage(plugin.message("unknown-blueprint", "id", args[2]));
            } else {
                plugin.giveItem(target, service.createBlueprintItem(blueprint.id));
                sender.sendMessage(plugin.message("blueprint-given", "name", blueprint.name, "player", target.getName()));
                target.sendMessage(plugin.message("blueprint-received", "name", blueprint.name));
            }
            return true;
        }
        if (args[0].equalsIgnoreCase("unlock") && args.length >= 3) {
            if (!requireAdmin(sender)) {
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[1]);
            BlueprintDefinition blueprint = service.getBlueprint(args[2]);
            if (target == null) {
                sender.sendMessage(plugin.message("player-not-found"));
            } else if (blueprint == null) {
                sender.sendMessage(plugin.message("unknown-blueprint", "id", args[2]));
            } else if (service.unlock(target, blueprint.id)) {
                sender.sendMessage(plugin.message("unlocked-by-admin", "name", blueprint.name, "player", target.getName()));
                target.sendMessage(plugin.message("blueprint-unlocked", "name", blueprint.name));
            } else {
                sender.sendMessage(plugin.message("already-unlocked", "name", blueprint.name));
            }
            return true;
        }
        if (args[0].equalsIgnoreCase("admin")) {
            if (!requireAdmin(sender)) {
                return true;
            }
            if (args.length >= 2 && args[1].equalsIgnoreCase("reload")) {
                plugin.reloadPluginData();
                sender.sendMessage(plugin.message("reloaded"));
                return true;
            }
            if (args.length >= 3 && args[1].equalsIgnoreCase("testloot")) {
                ConfigurationSection structure = lootTableManager.getStructure(args[2]);
                if (structure == null) {
                    sender.sendMessage(plugin.message("unknown-structure", "id", args[2]));
                    return true;
                }
                ConfigurationSection rewards = structure.getConfigurationSection("rewards");
                List<String> rewardNames = new ArrayList<>();
                if (rewards != null) {
                    for (String id : rewards.getKeys(false)) {
                        ConfigurationSection reward = rewards.getConfigurationSection(id);
                        if (reward != null) {
                            rewardNames.add(id + " (svoris " + reward.getInt("weight", 0) + ")");
                        }
                    }
                }
                sender.sendMessage(plugin.message("testloot-result", "id", args[2],
                        "chance", String.valueOf(structure.getDouble("blueprint-chance", 0)),
                        "rewards", String.join(", ", rewardNames)));
                return true;
            }
        }
        sender.sendMessage(plugin.message("usage"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return partial(args[0], List.of("menu", "research", "give", "unlock", "list", "admin"));
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("unlock"))) {
            return partial(args[1], Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()));
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("unlock"))) {
            return partial(args[2], service.getBlueprintIds());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("admin")) {
            return partial(args[1], List.of("reload", "testloot"));
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("testloot")) {
            return partial(args[2], lootTableManager.getStructureIds());
        }
        return Collections.emptyList();
    }

    private boolean requireAdmin(CommandSender sender) {
        if (sender.hasPermission("lostknowledge.admin")) {
            return true;
        }
        sender.sendMessage(plugin.message("no-permission"));
        return false;
    }

    private boolean isNearResearchTable(Player player) {
        Material tableMaterial = plugin.getResearchTableMaterial();
        if (tableMaterial == null) {
            return false;
        }
        int radius = plugin.getResearchCommandDistance();
        int baseX = player.getLocation().getBlockX();
        int baseY = player.getLocation().getBlockY();
        int baseZ = player.getLocation().getBlockZ();
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    if (player.getWorld().getBlockAt(baseX + x, baseY + y, baseZ + z).getType() == tableMaterial) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private List<String> partial(String input, List<String> options) {
        String prefix = input.toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(prefix))
                .collect(Collectors.toList());
    }
}
