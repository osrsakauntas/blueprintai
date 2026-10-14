package com.kodari.lostknowledge.gui;

import com.kodari.lostknowledge.BlueprintDefinition;
import com.kodari.lostknowledge.BlueprintService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class BlueprintMenu {
    private final BlueprintService service;

    public BlueprintMenu(BlueprintService service) {
        this.service = service;
    }

    public void open(Player player, boolean researchTable) {
        BlueprintMenuHolder holder = new BlueprintMenuHolder();
        holder.setResearchTable(researchTable);
        String title = researchTable ? "&8Tyrimų stalas" : "&8Brėžinių kolekcija";
        Inventory inventory = Bukkit.createInventory(holder, 54, ChatColor.translateAlternateColorCodes('&', title));
        holder.setInventory(inventory);

        List<BlueprintDefinition> blueprints = new ArrayList<>();
        blueprints.addAll(service.getBlueprints());
        blueprints.sort(Comparator.comparingInt((BlueprintDefinition blueprint) ->
                        blueprint.id.startsWith("iron_") ? 0 : blueprint.id.startsWith("diamond_") ? 1 : 2)
                .thenComparing(blueprint -> blueprint.id));
        int slot = 0;
        for (BlueprintDefinition blueprint : blueprints) {
            if (slot >= 45) {
                break;
            }
            holder.addBlueprintButton(slot, blueprint.id);
            inventory.setItem(slot++, service.createCollectionItem(blueprint, service.getUnlocked(player).contains(blueprint.id)));
        }

        int learned = 0;
        for (BlueprintDefinition blueprint : service.getBlueprints()) {
            if (service.hasUnlocked(player, blueprint.id)) {
                learned++;
            }
        }
        inventory.setItem(49, service.createMenuItem("BOOK", "&bTavo pažanga",
                List.of("&7Atrasti brėžiniai: &f" + learned + "/" + service.getBlueprints().size(),
                        "&7Atrakinti receptai gaminami įprastai.")));

        int researchSlot = 45;
        for (String group : service.getResearchableFragmentGroups()) {
            if (researchSlot == 49) {
                researchSlot++;
            }
            if (researchSlot > 53) {
                break;
            }
            holder.addResearchButton(researchSlot, group);
            inventory.setItem(researchSlot++, service.createFragmentResearchItem(group, player));
        }
        player.openInventory(inventory);
    }

    public void handleClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof BlueprintMenuHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
            return;
        }
        String researchBlueprintId = holder.getResearchBlueprintButton(event.getRawSlot());
        if (researchBlueprintId != null) {
            service.researchFragments(player, holder.getResearchSelectionGroup(), researchBlueprintId);
            openResearchSelection(player, holder.isResearchTable(), holder.getResearchSelectionGroup());
            return;
        }
        if (holder.getResearchSelectionGroup() != null && event.getRawSlot() == 49) {
            open(player, holder.isResearchTable());
            return;
        }
        String blueprintId = holder.getBlueprintButton(event.getRawSlot());
        if (blueprintId != null && !service.hasUnlocked(player, blueprintId)) {
            service.sendBlueprintHint(player, blueprintId);
            return;
        }
        String group = holder.getResearchGroup(event.getRawSlot());
        if (group != null) {
            openResearchSelection(player, holder.isResearchTable(), group);
        }
    }

    private void openResearchSelection(Player player, boolean researchTable, String group) {
        BlueprintMenuHolder holder = new BlueprintMenuHolder();
        holder.setResearchTable(researchTable);
        holder.setResearchSelectionGroup(group);
        Inventory inventory = Bukkit.createInventory(holder, 54,
                ChatColor.translateAlternateColorCodes('&', "&8Pasirink brėžinį: " + service.getFragmentName(group)));
        holder.setInventory(inventory);

        int slot = 0;
        for (String id : service.getResearchableBlueprintIds(player, group)) {
            BlueprintDefinition blueprint = service.getBlueprint(id);
            if (blueprint != null && slot < 45) {
                holder.addResearchBlueprintButton(slot, id);
                inventory.setItem(slot++, service.createResearchSelectionItem(blueprint));
            }
        }
        if (slot == 0) {
            inventory.setItem(22, service.createMenuItem("GRAY_DYE", "&7Naujų brėžinių nebėra",
                    List.of("&7Šioje grupėje visus brėžinius jau atradai arba turi.")));
        }
        inventory.setItem(49, service.createMenuItem("ARROW", "&cGrįžti", List.of("&7Grįžti į brėžinių meniu.")));
        inventory.setItem(50, service.createFragmentResearchItem(group, player));
        player.openInventory(inventory);
    }

}
