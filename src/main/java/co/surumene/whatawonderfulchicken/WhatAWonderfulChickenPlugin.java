package co.surumene.whatawonderfulchicken;

import co.surumene.whatawonderfulchicken.command.CommandService;
import co.surumene.whatawonderfulchicken.compat.BedrockCompatibility;
import co.surumene.whatawonderfulchicken.compat.GeyserBedrockCompatibility;
import co.surumene.whatawonderfulchicken.config.ConfigService;
import co.surumene.whatawonderfulchicken.config.MessageService;
import co.surumene.whatawonderfulchicken.display.DisplayService;
import co.surumene.whatawonderfulchicken.genome.WonderfulChickenGenomeProfile;
import co.surumene.whatawonderfulchicken.genome.WonderfulChickenGenomeSettings;
import co.surumene.whatawonderfulchicken.genome.lifecycle.ProfileRegistryGateway;
import co.surumene.whatawonderfulchicken.genome.lifecycle.WglProfileRegistryGateway;
import co.surumene.whatawonderfulchicken.gui.InventoryService;
import co.surumene.whatawonderfulchicken.listener.InteractionListener;
import co.surumene.whatawonderfulchicken.listener.InventoryListener;
import co.surumene.whatawonderfulchicken.listener.WorldListener;
import co.surumene.whatawonderfulchicken.service.WonderfulChickenService;
import co.surumene.whatawonderfulchicken.service.WonderfulChickenStore;
import co.surumene.whatawonderfulchicken.task.FollowController;
import co.surumene.whatawonderfulchicken.task.IntegrityController;
import co.surumene.whatawonderfulchicken.task.RidingController;
import co.surumene.whatawonderfulchicken.task.TraitController;
import co.surumene.whatawonderfulchicken.runtime.BiologicalClock;
import co.surumene.whatawonderfulchicken.runtime.BiologicalClockListener;
import co.surumene.whatawonderfulchicken.runtime.YamlBiologicalClockStateStore;
import org.bukkit.World;
import java.io.File;
import co.surumene.wgl.plugin.WonderfulGenomeLibService;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class WhatAWonderfulChickenPlugin extends JavaPlugin {
    private WonderfulGenomeLibService genomeLib;
    private ProfileRegistryGateway profileRegistry;
    private WonderfulChickenGenomeProfile genomeProfile;
    private ConfigService configService;
    private MessageService messageService;
    private WonderfulChickenStore store;
    private WonderfulChickenService chickens;
    private DisplayService displays;
    private InventoryService inventories;
    private RidingController riding;
    private TraitController traits;
    private BedrockCompatibility bedrock;
    private BiologicalClock biologicalClock;

    @Override
    public void onEnable() {
        genomeLib = Bukkit.getServicesManager().load(WonderfulGenomeLibService.class);
        if (genomeLib == null) {
            throw new IllegalStateException("WonderfulGenomeLib service is unavailable");
        }

        saveDefaultConfig();
        configService = new ConfigService(this);
        ConfigService.ValidationResult validation = configService.validate(getConfig());
        if (!validation.valid()) {
            getLogger().severe("Invalid configuration: " + String.join("; ", validation.errors()));
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        configService.persistMissingDefaults();

        World clockWorld = requireClockWorld(configService.ageClockWorld());
        biologicalClock = BiologicalClock.start(
                clockWorld.getName(), clockWorld::getFullTime,
                new YamlBiologicalClockStateStore(
                        new File(getDataFolder(), "biological-clock.yml"), getLogger()));

        profileRegistry = new WglProfileRegistryGateway(genomeLib, this);
        genomeProfile = new WonderfulChickenGenomeProfile(
                WonderfulChickenGenomeSettings.defaults(),
                genomeLib.engine().geneSequenceCodec());
        profileRegistry.register(genomeProfile);

        messageService = new MessageService(this);
        store = new WonderfulChickenStore(this, configService);
        chickens = new WonderfulChickenService(this, configService, store);
        bedrock = createBedrockCompatibility();
        displays = new DisplayService(this, chickens, store);
        inventories = new InventoryService(this, chickens, store, configService, messageService, displays);
        riding = new RidingController(chickens, store, configService, bedrock);
        traits = new TraitController(chickens, store, configService);

        CommandService commands = new CommandService(this, chickens, store, configService, messageService, displays);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(commands.build(), "What a Wonderful Chicken administration", List.of("whatawonderfulchicken")));

        WorldListener worldListener = new WorldListener(this, chickens, store, displays, inventories, configService);
        getServer().getPluginManager().registerEvents(worldListener, this);
        getServer().getPluginManager().registerEvents(
                new BiologicalClockListener(biologicalClock, getLogger()), this);
        getServer().getPluginManager().registerEvents(new InteractionListener(this, chickens, store, configService, messageService, inventories, displays, riding), this);
        getServer().getPluginManager().registerEvents(new InventoryListener(inventories), this);

        chickens.scanLoadedWorlds();
        displays.rebuildAllLoaded();

        Bukkit.getScheduler().runTaskTimer(this, riding, 1L, 1L);
        Bukkit.getScheduler().runTaskTimer(this, traits, 10L, 10L);
        Bukkit.getScheduler().runTaskTimer(this, new FollowController(this, chickens, store, configService), 5L, 5L);
        Bukkit.getScheduler().runTaskTimer(this, new IntegrityController(chickens, displays, worldListener), 1L, 1L);

        getLogger().info(messageService.text(Bukkit.getConsoleSender(), "plugin.enabled"));
    }

    @Override
    public void onDisable() {
        if (inventories != null) inventories.closeAll();
        if (riding != null) riding.shutdown();
        if (traits != null) traits.shutdown();
        if (displays != null) displays.removeAll();
        if (bedrock != null) bedrock.shutdown();
        if (profileRegistry != null) profileRegistry.unregisterOwner();
        if (biologicalClock != null) {
            try {
                biologicalClock.persist();
            } catch (RuntimeException error) {
                getLogger().warning("Could not persist biological clock state: " + error.getMessage());
            }
            biologicalClock = null;
        }
        genomeProfile = null;
        profileRegistry = null;
        genomeLib = null;
    }

    public WonderfulGenomeLibService genomeLib() {
        WonderfulGenomeLibService service = genomeLib;
        if (service == null) {
            throw new IllegalStateException("WonderfulGenomeLib service is not available");
        }
        return service;
    }

    public WonderfulChickenGenomeProfile genomeProfile() {
        WonderfulChickenGenomeProfile profile = genomeProfile;
        if (profile == null) {
            throw new IllegalStateException("Wonderful Chicken genome profile is not available");
        }
        return profile;
    }

    public BiologicalClock biologicalClock() {
        if (biologicalClock == null) {
            throw new IllegalStateException("WWC biological clock is unavailable");
        }
        return biologicalClock;
    }

    public void reconfigureBiologicalClock() {
        World next = requireClockWorld(configService.ageClockWorld());
        if (!biologicalClock().worldName().equals(next.getName())) {
            biologicalClock().reconfigure(next.getName(), next::getFullTime);
        }
    }

    private World requireClockWorld(String name) {
        World world = Bukkit.getWorld(name);
        if (world == null) {
            throw new IllegalArgumentException("WWC biological clock world is unavailable: " + name);
        }
        return world;
    }

    private BedrockCompatibility createBedrockCompatibility() {
        if (getServer().getPluginManager().getPlugin("Geyser-Spigot") == null) {
            return BedrockCompatibility.disabled();
        }
        try {
            return GeyserBedrockCompatibility.create(this);
        } catch (LinkageError ex) {
            getLogger().warning("Installed Geyser is too old for WWC Bedrock seat compatibility: " + ex.getMessage());
            return BedrockCompatibility.disabled();
        }
    }
}
