package com.kodari.lostknowledge.listener;

import com.kodari.lostknowledge.BlueprintDefinition;
import com.kodari.lostknowledge.BlueprintService;
import com.kodari.lostknowledge.LostKnowledgePlugin;
import com.kodari.lostknowledge.gui.BlueprintMenu;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class BlueprintListener implements Listener {
    private final LostKnowledgePlugin plugin;
    private final BlueprintService service;
    private final BlueprintMenu menu;

    public BlueprintListener(LostKnowledgePlugin plugin, BlueprintService service, BlueprintMenu menu) {
        this.plugin = plugin;
        this.service = service;
        this.menu = menu;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        Block clicked = event.getClickedBlock();
        Material tableMaterial = plugin.getResearchTableMaterial();
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && clicked != null && tableMaterial != null
                && clicked.getType() == tableMaterial) {
            event.setCancelled(true);
            menu.open(player, true);
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (service.handleItemUse(player, hand)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ItemStack result = event.getRecipe().getResult();
        String materialName = result.getType().name();
        if (!service.hasUnlockedRecipe(player, materialName)) {
            event.setCancelled(true);
            for (BlueprintDefinition blueprint : service.getBlueprints()) {
                if (blueprint.recipeResult.equalsIgnoreCase(materialName)) {
                    player.sendMessage(plugin.message("locked-recipe", "name", blueprint.name));
                    break;
                }
            }
        }
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        menu.handleClick(event);
    }
}
