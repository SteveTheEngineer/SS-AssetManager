package me.ste.stevesseries.assetmanager;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;

public class CustomModelDataAllocation {
    private NamespacedKey key;
    private Material item;
    private String model;
    private int data;

    public CustomModelDataAllocation(NamespacedKey key, Material item, String model, int data) {
        this.key = key;
        this.item = item;
        this.model = model;
        this.data = data;
    }

    /**
     * @return Custom model allocation namespaced key
     */
    public NamespacedKey getKey() {
        return this.key;
    }

    /**
     * @return Custom model item
     */
    public Material getItem() {
        return this.item;
    }

    /**
     * @return Custom model ID
     */
    public String getModel() {
        return this.model;
    }

    /**
     * @return Allocated custom model data
     */
    public int getData() {
        return this.data;
    }
}