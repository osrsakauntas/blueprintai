package com.kodari.lostknowledge;

public final class BlueprintDefinition {
    public final String id;
    public final String name;
    public final String color;
    public final String rarity;
    public final String category;
    public final String description;
    public final String discoveryHint;
    public final String itemMaterial;
    public final String recipeResult;

    public BlueprintDefinition(String id, String name, String color, String rarity, String category,
                               String description, String discoveryHint, String itemMaterial, String recipeResult) {
        this.id = id;
        this.name = name;
        this.color = color;
        this.rarity = rarity;
        this.category = category;
        this.description = description;
        this.discoveryHint = discoveryHint;
        this.itemMaterial = itemMaterial;
        this.recipeResult = recipeResult;
    }
}
