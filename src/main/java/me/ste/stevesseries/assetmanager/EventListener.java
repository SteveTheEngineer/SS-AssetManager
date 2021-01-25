package me.ste.stevesseries.assetmanager;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class EventListener implements Listener {
    private final AssetManager plugin;
    private final List<UUID> resourcePackInstalled = new ArrayList<>();

    public EventListener(AssetManager plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        event.getPlayer().setResourcePack(this.plugin.getResourcePackURL(), this.plugin.getResourcesHash());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        this.resourcePackInstalled.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onResourcePackStatus(PlayerResourcePackStatusEvent event) {
        if(event.getStatus() == PlayerResourcePackStatusEvent.Status.DECLINED && this.plugin.getConfig().getBoolean("force")) {
            event.getPlayer().kickPlayer("You must accept the resource pack");
        } else if(event.getStatus() == PlayerResourcePackStatusEvent.Status.FAILED_DOWNLOAD) {
            event.getPlayer().setResourcePack(this.plugin.getResourcePackURL(), this.plugin.getResourcesHash());
        } else if(event.getStatus() == PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED) {
            this.resourcePackInstalled.add(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if(!this.resourcePackInstalled.contains(event.getPlayer().getUniqueId()) && this.plugin.getConfig().getBoolean("force")) {
            event.setCancelled(true);
        }
    }
}