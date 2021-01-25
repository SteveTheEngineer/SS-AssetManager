package me.ste.stevesseries.assetmanager;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.io.ByteStreams;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public final class AssetManager extends JavaPlugin {
    private HttpServer httpServer;
    private String resourcePackURL = null;
    private byte[] resourcesHash = null;
    private byte[] resourcePack = null;
    private Path dataFolder = this.getDataFolder().toPath();
    private Map<String, byte[]> assetMap = new HashMap<>();
    private ListMultimap<Material, CustomModelDataAllocation> customModelDataAllocations = ArrayListMultimap.create();
    private Map<Material, Integer> newCustomModelDataIds = new HashMap<>();

    @Override
    public void onEnable() {
        Path configFile = dataFolder.resolve("config.yml");

        if(!Files.isDirectory(this.dataFolder)) {
            try {
                Files.createDirectory(this.dataFolder);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if(!Files.isRegularFile(configFile)) {
            this.saveDefaultConfig();
        }
        this.reloadConfig();

        this.resourcePackURL = String.format("%s/resources.zip", this.getConfig().getString("webserver.url"));


        this.getLogger().info("Loading plugin assets...");
        for(Plugin plugin : this.getServer().getPluginManager().getPlugins()) {
            if(plugin instanceof JavaPlugin) {
                try {
                    Method m = JavaPlugin.class.getDeclaredMethod("getFile");
                    m.setAccessible(true);
                    Path file = ((File) m.invoke(plugin)).toPath();
                    JarFile jarFile = new JarFile(file.toString());
                    final boolean[] assetsFound = {false};
                    jarFile.stream().forEach(entry -> {
                        if(entry.getName().startsWith("assets/") && !assetsFound[0]) {
                            this.getLogger().info(String.format("Loading assets of plugin %s", plugin.getDescription().getFullName()));
                            assetsFound[0] = true;
                        }
                        if(entry.getName().startsWith("assets/") && !entry.isDirectory()) {
                            try {
                                this.assetMap.put(entry.getName().substring(7), ByteStreams.toByteArray(Objects.requireNonNull(plugin.getResource(entry.getName()))));
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    });
                } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | IOException e) {
                    e.printStackTrace();
                }
            }
        }

        this.getLogger().info("Loading resource packs from the resourcepacks folder...");
        Path resourcePacks = Paths.get("resourcepacks/");
        try {
            if(!Files.isDirectory(resourcePacks)) {
                Files.createDirectory(resourcePacks);
            }
            Files.list(resourcePacks).forEach(packf -> {
                if(Files.isRegularFile(packf) && packf.toString().endsWith(".zip")) {
                    this.getLogger().info(String.format("Loading resource pack %s", resourcePacks.relativize(packf).toString()));
                    try {
                        ZipFile zipFile = new ZipFile(packf.toString());
                        zipFile.stream().forEach(entry -> {
                            if(entry.getName().startsWith("assets/") && !entry.isDirectory()) {
                                try {
                                    this.assetMap.put(entry.getName().substring(7), ByteStreams.toByteArray(zipFile.getInputStream(entry)));
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                        });
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else if(Files.isDirectory(packf)) {
                    this.getLogger().info(String.format("Loading resource pack %s", resourcePacks.relativize(packf).toString()));
                    Path assets = packf.resolve("assets");
                    if(Files.isDirectory(assets)) {
                        try {
                            Files.walk(assets).forEach(asset -> {
                                try {
                                    if(Files.isRegularFile(asset)) {
                                        this.assetMap.put(assets.relativize(asset).toString(), Files.readAllBytes(asset));
                                    }
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            });
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                } else {
                    this.getLogger().severe(String.format("%s is not a valid resource pack", packf.toString()));
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }

        Path worldFolder = this.getServer().getWorlds().get(0).getWorldFolder().toPath();
        Path worldResources = worldFolder.resolve("resources.zip");
        if(Files.isRegularFile(worldResources)) {
            this.getLogger().info("Loading world resources...");
            try {
                ZipFile zipFile = new ZipFile(worldResources.toString());
                zipFile.stream().forEach(entry -> {
                    if(entry.getName().startsWith("assets/") && !entry.isDirectory()) {
                        try {
                            this.assetMap.put(entry.getName().substring(7), ByteStreams.toByteArray(zipFile.getInputStream(entry)));
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                });
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        this.getLogger().info("Finished loading assets");

        try {
            this.httpServer = HttpServer.create(new InetSocketAddress(this.getServer().getIp(), this.getConfig().getInt("webserver.port")), 0);
            this.httpServer.createContext("/resources.zip", httpExchange -> {
                if(httpExchange.getRequestMethod().equals("GET")) {
                    Headers headers = httpExchange.getResponseHeaders();
                    headers.add("Content-Type", "application/zip");

                    httpExchange.sendResponseHeaders(200, this.resourcePack.length);
                    OutputStream outputStream = httpExchange.getResponseBody();
                    outputStream.write(this.resourcePack);
                    outputStream.close();
                }
            });
            this.httpServer.setExecutor(Executors.newFixedThreadPool(10));
            this.httpServer.start();
        } catch (IOException e) {
            e.printStackTrace();
        }

        ConfigurationSection newCustomModelDataIdsSection = this.getConfig().getConfigurationSection("newCustomModelDataIds");
        for(String key : newCustomModelDataIdsSection.getKeys(false)) {
            newCustomModelDataIds.put(Material.valueOf(key), newCustomModelDataIdsSection.getInt(key));
        }
        ConfigurationSection customModelDataAllocationsSection = this.getConfig().getConfigurationSection("customModelDataAllocations");
        for(String key : customModelDataAllocationsSection.getKeys(false)) {
            ConfigurationSection ms = customModelDataAllocationsSection.getConfigurationSection(key);
            for(String key2 : ms.getKeys(false)) {
                ConfigurationSection cmdaSection = ms.getConfigurationSection(key2);
                String[] key2Parts = key2.split(":");
                Material material = Material.valueOf(key);
                this.customModelDataAllocations.put(material, new CustomModelDataAllocation(new NamespacedKey(key2Parts[0], key2Parts[1]), material, cmdaSection.getString("model"), cmdaSection.getInt("id")));
            }
        }

        this.getServer().getPluginManager().registerEvents(new EventListener(this), this);

        recreateResourcePack();
    }

    @Override
    public void onDisable() {
        Path configFile = dataFolder.resolve("config.yml");

        if(!Files.isDirectory(this.dataFolder)) {
            try {
                Files.createDirectory(this.dataFolder);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if(!Files.isRegularFile(configFile)) {
            this.saveDefaultConfig();
        }
        this.reloadConfig();

        if(this.httpServer != null) {
            this.httpServer.stop(0);
            this.httpServer = null;
        }

        ConfigurationSection newCustomModelDataIdsSection = this.getConfig().createSection("newCustomModelDataIds");
        for(Map.Entry<Material, Integer> entry : this.newCustomModelDataIds.entrySet()) {
            newCustomModelDataIdsSection.set(entry.getKey().name(), entry.getValue());
        }
        ConfigurationSection customModelDataAllocationsSection = this.getConfig().createSection("customModelDataAllocations");
        for(Map.Entry<Material, Collection<CustomModelDataAllocation>> s : this.customModelDataAllocations.asMap().entrySet()) {
            ConfigurationSection ms = customModelDataAllocationsSection.createSection(s.getKey().name());
            for(CustomModelDataAllocation customModelDataAllocation : s.getValue()) {
                ConfigurationSection cmdaSection = ms.createSection(customModelDataAllocation.getKey().toString());
                cmdaSection.set("model", customModelDataAllocation.getModel());
                cmdaSection.set("id", customModelDataAllocation.getData());
            }
        }
        this.saveConfig();
    }

    /**
     * @return Resource pack download URL
     */
    public String getResourcePackURL() {
        return this.resourcePackURL;
    }

    /**
     * @return Resource pack SHA-1 hash
     */
    public byte[] getResourcesHash() {
        return this.resourcesHash;
    }

    /**
     * Allocate a custom model data. Persists after restarts/reloads.
     *
     * @param key Allocation ID
     * @param material Custom model item
     * @param modelId Custom model ID
     * @return Custom model data allocation
     */
    public CustomModelDataAllocation allocateCustomModelData(NamespacedKey key, Material material, String modelId) {
        for(CustomModelDataAllocation customModelDataAllocation : this.customModelDataAllocations.get(material)) {
            if(customModelDataAllocation.getKey().equals(key)) {
                return customModelDataAllocation;
            }
        }
        int newId = 1;
        if(this.newCustomModelDataIds.containsKey(material)) {
            newId = this.newCustomModelDataIds.get(material);
        }
        if(newId <= 16777216) {
            CustomModelDataAllocation customModelDataAllocation = new CustomModelDataAllocation(key, material, modelId, newId);
            this.customModelDataAllocations.put(material, customModelDataAllocation);
            this.newCustomModelDataIds.put(material, newId + 1);
            return customModelDataAllocation;
        } else {
            return null;
        }
    }

    /**
     * Recreate the resource pack. Must be called after any changes to the assets.
     */
    public void recreateResourcePack() {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        ZipOutputStream zipOutputStream = new ZipOutputStream(byteArrayOutputStream);

        JsonObject object = new JsonObject();
        JsonObject pack = new JsonObject();

        pack.addProperty("pack_format", this.getConfig().getInt("pack.format"));
        pack.addProperty("description", ChatColor.translateAlternateColorCodes('&', Objects.requireNonNull(this.getConfig().getString("pack.description"))));

        object.add("pack", pack);
        
        try {
            zipOutputStream.putNextEntry(new ZipEntry("pack.mcmeta"));
            zipOutputStream.write(object.toString().getBytes(StandardCharsets.UTF_8));
            zipOutputStream.closeEntry();
            
            Path icon = this.dataFolder.resolve("icon.png");
            if(Files.isRegularFile(icon)) {
                zipOutputStream.putNextEntry(new ZipEntry("pack.png"));
                zipOutputStream.write(Files.readAllBytes(icon));
                zipOutputStream.closeEntry();
            }

            zipOutputStream.putNextEntry(new ZipEntry("assets/"));
            zipOutputStream.closeEntry();

            for(Map.Entry<String, byte[]> assetEntry : this.assetMap.entrySet()) {
                if(assetEntry.getKey().startsWith("minecraft/models/item/")) {
                    String name = assetEntry.getKey().substring(12, assetEntry.getKey().length() - 5);
                    for(Material mat : this.customModelDataAllocations.keySet()) {
                        if(name.equals(mat.name().toLowerCase())) {
                            continue;
                        }
                    }
                }
                zipOutputStream.putNextEntry(new ZipEntry("assets/" + assetEntry.getKey()));
                zipOutputStream.write(assetEntry.getValue());
                zipOutputStream.closeEntry();
            }

            for(Map.Entry<Material, Collection<CustomModelDataAllocation>> entry : customModelDataAllocations.asMap().entrySet()) {
                JsonObject model = new JsonObject();
                JsonObject textures = new JsonObject();
                JsonArray overrides = new JsonArray();

                for(CustomModelDataAllocation allocation : entry.getValue()) {
                    JsonObject override = new JsonObject();
                    JsonObject predicate = new JsonObject();

                    predicate.addProperty("custom_model_data", allocation.getData());

                    override.add("predicate", predicate);
                    override.addProperty("model", allocation.getModel());

                    overrides.add(override);
                }

                textures.addProperty("layer0", String.format("minecraft:item/%s", entry.getKey().name().toLowerCase()));

                model.addProperty("parent", "minecraft:item/generated");
                model.add("textures", textures);
                model.add("overrides", overrides);;

                zipOutputStream.putNextEntry(new ZipEntry(String.format("assets/minecraft/models/item/%s.json", entry.getKey().name().toLowerCase())));
                zipOutputStream.write(model.toString().getBytes(StandardCharsets.UTF_8));
                zipOutputStream.closeEntry();
            }

            zipOutputStream.close();
            MessageDigest md = MessageDigest.getInstance("SHA1");
            md.update(byteArrayOutputStream.toByteArray());
            this.resourcesHash = md.digest();
            this.resourcePack = byteArrayOutputStream.toByteArray();
        } catch (IOException | NoSuchAlgorithmException e) {
            e.printStackTrace();
        }

        for(Player player : this.getServer().getOnlinePlayers()) {
            player.setResourcePack(this.resourcePackURL, this.resourcesHash);
        }
    }

    /**
     * @return Asset map, path -> data
     */
    public Map<String, byte[]> getAssetMap() {
        return this.assetMap;
    }

    /**
     * @return Asset manager instance
     */
    public static AssetManager getInstance() {
        return AssetManager.getPlugin(AssetManager.class);
    }
}