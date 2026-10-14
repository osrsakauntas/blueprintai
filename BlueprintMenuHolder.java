package com.kodari.lostknowledge.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;

public final class BlueprintMenuHolder implements InventoryHolder {
    private Inventory inventory;
    private final Map<Integer, String> researchButtons = new HashMap<>();
    private final Map<Integer, String> blueprintButtons = new HashMap<>();
    private final Map<Integer, String> researchBlueprintButtons = new HashMap<>();
    private boolean researchTable;
    private String researchSelectionGroup;

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public void addResearchButton(int slot, String group) {
        researchButtons.put(slot, group);
    }

    public String getResearchGroup(int slot) {
        return researchButtons.get(slot);
    }

    public void addBlueprintButton(int slot, String blueprintId) {
        blueprintButtons.put(slot, blueprintId);
    }

    public String getBlueprintButton(int slot) {
        return blueprintButtons.get(slot);
    }

    public void addResearchBlueprintButton(int slot, String blueprintId) {
        researchBlueprintButtons.put(slot, blueprintId);
    }

    public String getResearchBlueprintButton(int slot) {
        return researchBlueprintButtons.get(slot);
    }

    public boolean isResearchTable() {
        return researchTable;
    }

    public void setResearchTable(boolean researchTable) {
        this.researchTable = researchTable;
    }

    public String getResearchSelectionGroup() {
        return researchSelectionGroup;
    }

    public void setResearchSelectionGroup(String researchSelectionGroup) {
        this.researchSelectionGroup = researchSelectionGroup;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
