package co.surumene.whatawonderfulchicken;

import co.surumene.whatawonderfulchicken.command.CommandService;
import co.surumene.whatawonderfulchicken.config.ConfigService;
import co.surumene.whatawonderfulchicken.config.MessageService;
import co.surumene.whatawonderfulchicken.display.DisplayService;
import co.surumene.whatawonderfulchicken.gui.InventoryService;
import co.surumene.whatawonderfulchicken.listener.InteractionListener;
import co.surumene.whatawonderfulchicken.listener.InventoryListener;
import co.surumene.whatawonderfulchicken.listener.WorldListener;
import co.surumene.whatawonderfulchicken.service.WonderfulChickenService;
import co.surumene.whatawonderfulchicken.service.WonderfulChickenStore;
import co.surumene.whatawonderfulchicken.task.FollowController;
import co.surumene.whatawonderfulchicken.task.IntegrityController;
import co.surumene.whatawonderfulchicken.task.RidingController;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class WhatAWonderfulChickenPlugin extends JavaPlugin {
    private ConfigService configService;
    private MessageService messageService;
    private WonderfulChickenStore store;
    private WonderfulChickenService chickens;
    private DisplayService displays;
    private InventoryService inventories;
    private RidingController riding;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        configService = new ConfigService(this);
        ConfigService.ValidationResult validation = configService.validate(getConfig());
        if (!validation.valid()) {
            getLogger().severe("Invalid configuration: " + String.join("; ", validation.errors()));
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        messageService = new MessageService(this);
        store = new WonderfulChickenStore(this, configService);
        chickens = new WonderfulChickenService(this, configService, store);
        displays = new DisplayService(this, chickens, store);
        inventories = new InventoryService(this, chickens, store, configService, messageService, displays);
        riding = new RidingController(chickens, store, configService);

        CommandService commands = new CommandService(this, chickens, store, configService, messageService, displays);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(commands.build(), "What a Wonderful Chicken administration", List.of("whatawonderfulchicken")));

        getServer().getPluginManager().registerEvents(new WorldListener(chickens, store, displays, inventories), this);
        getServer().getPluginManager().registerEvents(new InteractionListener(this, chickens, store, configService, messageService, inventories, displays), this);
        getServer().getPluginManager().registerEvents(new InventoryListener(inventories), this);

        chickens.scanLoadedWorlds();
        displays.rebuildAllLoaded();

        Bukkit.getScheduler().runTaskTimer(this, riding, 1L, 1L);
        Bukkit.getScheduler().runTaskTimer(this, new FollowController(chickens, store, configService), 5L, 5L);
        Bukkit.getScheduler().runTaskTimer(this, new IntegrityController(chickens, displays), 1L, 1L);

        getLogger().info(messageService.text(Bukkit.getConsoleSender(), "plugin.enabled"));
    }

    @Override
    public void onDisable() {
        if (riding != null) {
            Bukkit.getOnlinePlayers().forEach(riding::restoreHud);
        }
    }
}