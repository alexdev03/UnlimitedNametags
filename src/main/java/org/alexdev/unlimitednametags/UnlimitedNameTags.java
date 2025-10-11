package org.alexdev.unlimitednametags;

import com.alessiodp.libby.BukkitLibraryManager;
import com.alessiodp.libby.Library;
import com.github.Anon8281.universalScheduler.UniversalScheduler;
import com.github.Anon8281.universalScheduler.scheduling.schedulers.TaskScheduler;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jonahseguin.drink.CommandService;
import com.jonahseguin.drink.Drink;
import lombok.Getter;
import org.alexdev.unlimitednametags.api.UNTAPI;
import org.alexdev.unlimitednametags.commands.MainCommand;
import org.alexdev.unlimitednametags.config.ConfigManager;
import org.alexdev.unlimitednametags.hook.*;
import org.alexdev.unlimitednametags.hook.hat.HatHook;
import org.alexdev.unlimitednametags.listeners.*;
import org.alexdev.unlimitednametags.metrics.Metrics;
import org.alexdev.unlimitednametags.nametags.ConditionalManager;
import org.alexdev.unlimitednametags.nametags.NameTagManager;
import org.alexdev.unlimitednametags.packet.KyoriManager;
import org.alexdev.unlimitednametags.packet.PacketManager;
import org.alexdev.unlimitednametags.placeholders.PlaceholderManager;
import org.alexdev.unlimitednametags.vanish.VanishManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Getter
public final class UnlimitedNameTags extends JavaPlugin {

    private boolean isPaper;
    private ConfigManager configManager;
    private NameTagManager nametagManager;
    private PlaceholderManager placeholderManager;
    private VanishManager vanishManager;
    private PacketEventsListener packetEventsListener;
    private PacketManager packetManager;
    private PlayerListener playerListener;
    private TrackerManager trackerManager;
    private Map<Class<? extends Hook>, Hook> hooks;
    private List<HatHook> hatHooks;
    private TaskScheduler taskScheduler;
    private KyoriManager kyoriManager;
    private ConditionalManager conditionalManager;

    @Override
    public void onLoad() {
        hooks = Maps.newHashMap();
        hatHooks = Lists.newCopyOnWriteArrayList();
    }

    @Override
    public void onEnable() {
        isPaper = isPaperSupported();
        loadLibraries().join();
        kyoriManager = new KyoriManager(this);

        taskScheduler = UniversalScheduler.getScheduler(this);
        configManager = new ConfigManager(this);
        if (!loadConfig()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        trackerManager = new TrackerManager(this);
        nametagManager = new NameTagManager(this);
        placeholderManager = new PlaceholderManager(this);
        vanishManager = new VanishManager(this);
        packetManager = new PacketManager(this);
        conditionalManager = new ConditionalManager(this);


        loadCommands();
        loadListeners();
        loadHooks();
        loadStats();

        UNTAPI.register(this);
        getLogger().info("API registered");
        getLogger().info("UnlimitedNameTags has been enabled!");
    }

    private boolean loadConfig() {
        final Optional<Throwable> error = configManager.loadConfigs();
        if (error.isPresent()) {
            getLogger().log(java.util.logging.Level.SEVERE, "Failed to load configuration", error.get());
            return false;
        }
        return true;
    }

    private CompletableFuture<Void> loadLibraries() {

        final BukkitLibraryManager bukkitLibraryManager = new BukkitLibraryManager(this);
        bukkitLibraryManager.addRepository("https://maven-central.storage-download.googleapis.com/maven2");
        bukkitLibraryManager.addMavenCentral();

        final List<Library> libraries = Lists.newArrayList(

        );

        if (!isPaper) {
            libraries.add(Library.builder()
                    .groupId("net{}kyori")
                    .artifactId("adventure-text-minimessage")
                    .version("4.17.0")
                    .relocate("net{}]kyori{}adventure{}text{}serializer", "io{}github{}retrooper{}packetevents{}adventure{}serializer")
                    .build());
        }

        if (false && Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")) {
            libraries.add(Library.builder()
                    .groupId("team.unnamed")
                    .artifactId("creative-server")
                    .version("1.8.3-SNAPSHOT")
                    .build());
            libraries.add(Library.builder()
                    .groupId("team.unnamed")
                    .artifactId("creative-serializer-minecraft")
                    .version("1.8.3-SNAPSHOT")
                    .build());
            libraries.add(Library.builder()
                    .groupId("team.unnamed")
                    .artifactId("creative-api")
                    .version("1.8.3-SNAPSHOT")
                    .build());
        }

        return CompletableFuture.runAsync(() -> libraries.forEach(bukkitLibraryManager::loadLibrary));
    }

    private void loadListeners() {
        playerListener = new PlayerListener(this);
        Bukkit.getPluginManager().registerEvents(playerListener, this);
        Bukkit.getPluginManager().registerEvents(new OtherListener(this), this);


        if (isPaper) {
            getLogger().info("Paper found, using Paper's tracker");
            Bukkit.getPluginManager().registerEvents(new PaperTrackerListener(this), this);
        } else {
            if (!isCorrectSpigotVersion()) {
                getLogger().severe("Unsupported Spigot version, please use 1.20.2 or higher");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }

            getLogger().info("Paper not found, using Spigot's tracker");
            Bukkit.getPluginManager().registerEvents(new SpigotTrackerListener(this), this);
        }

        getLogger().info("PacketEvents found, hooking into it");
        packetEventsListener = new PacketEventsListener(this);
        packetEventsListener.onEnable();
    }

    private boolean isCorrectSpigotVersion() {
        final String version = Bukkit.getServer().getBukkitVersion().split("-")[0];
        final String[] split = version.split("\\.");
        if (split.length < 2) {
            return false;
        }

        final int major = Integer.parseInt(split[1]);
        final int minor = Integer.parseInt(split[2]);

        if (major < 20) {
            return false;
        } else return major != 20 || minor >= 2;
    }

    private boolean isPaperSupported() {
        try {
            Class.forName("io.papermc.paper.event.player.PlayerTrackEntityEvent");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private void loadHooks() {
        if (Bukkit.getPluginManager().isPluginEnabled("TypeWriter")) {
            hooks.put(TypeWriterListener.class, new TypeWriterListener(this));
            getLogger().info("TypeWriter found, hooking into it");
        }

        if (Bukkit.getPluginManager().isPluginEnabled("MiniPlaceholders")) {
            hooks.put(MiniPlaceholdersHook.class, new MiniPlaceholdersHook(this));
            getLogger().info("MiniPlaceholders found, hooking into it");
        }

        if (Bukkit.getPluginManager().isPluginEnabled("Nexo")) {
            getLogger().info("Nexo found, hooking into it");
            final NexoHook hook = new NexoHook(this);
            hatHooks.add(hook);
            hooks.put(NexoHook.class, hook);
        }

        if (Bukkit.getPluginManager().isPluginEnabled("Oraxen")) {
            getLogger().info("Oraxen found, hooking into it");
            final OraxenHook hook = new OraxenHook(this);
            hatHooks.add(hook);
            hooks.put(OraxenHook.class, hook);
        }

        if (false && Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")) {
            getLogger().info("ItemsAdder found, hooking into it");
            final ItemsAdderHook hook = new ItemsAdderHook(this);
            hatHooks.add(hook);
            hooks.put(ItemsAdderHook.class, hook);
        }


        if (Bukkit.getPluginManager().isPluginEnabled("ViaVersion")) {
            final ViaVersionHook hook = new ViaVersionHook(this);
            hooks.put(ViaVersionHook.class, hook);
            getLogger().info("ViaVersion found, hooking into it");
        }

        if (Bukkit.getPluginManager().isPluginEnabled("geyser")) {
            final GeyserHook hook = new GeyserHook(this);
            hooks.put(GeyserHook.class, hook);
            getLogger().info("Geyser found, hooking into it");
        }

        if (Bukkit.getPluginManager().isPluginEnabled("floodgate")) {
            final FloodgateHook hook = new FloodgateHook(this);
            hooks.put(FloodgateHook.class, hook);
            getLogger().info("Floodgate found, hooking into it");
        }

        if (Bukkit.getPluginManager().isPluginEnabled("LibsDisguises")) {
            final LibsDisguisesHook hook = new LibsDisguisesHook(this);
            hooks.put(LibsDisguisesHook.class, hook);
            getLogger().info("LibsDisguises found, hooking into it");
        }

        if (Bukkit.getPluginManager().isPluginEnabled("FeatherServerAPI")) {
            final FeatherClientHook hook = new FeatherClientHook(this);
            hooks.put(FeatherClientHook.class, hook);
            getLogger().info("FeatherServerAPI found, hooking into it");
        }

        if (Bukkit.getPluginManager().isPluginEnabled("HMCCosmetics")) {
            final HMCCosmeticsHook hook = new HMCCosmeticsHook(this);
            hatHooks.add(hook);
            hooks.put(HMCCosmeticsHook.class, hook);
            getLogger().info("HMCCosmetics found, hooking into it");
        }

//        if (Bukkit.getPluginManager().isPluginEnabled("LabyModServerAPI")) {
//            final LabyModHook hook = new LabyModHook(this);
//            hooks.put(LabyModHook.class, hook);
//            getLogger().info("LabyModServerAPI found, hooking into it");
//        }

        hooks.values().forEach(Hook::onEnable);
    }

    private void loadCommands() {
        final CommandService drink = Drink.get(this);

        drink.register(new MainCommand(this), "unt", "unlimitednametags");
        drink.registerCommands();
    }

    private void loadStats() {
        final org.alexdev.unlimitednametags.metrics.Metrics metrics = new org.alexdev.unlimitednametags.metrics.Metrics(this, 23081);
        metrics.addCustomChart(new Metrics.SimplePie("paper", () -> String.valueOf(isPaper)));
        metrics.addCustomChart(new Metrics.AdvancedPie("nametags", () -> {
            final Map<String, Integer> map = Maps.newHashMap();
            configManager.getSettings().getNameTags().forEach((key, value) -> {
                final int count = (int) nametagManager.getNameTags().values().stream()
                        .filter(n -> n.getNameTag().equals(value))
                        .count();
                map.put(key, count);
            });
            return map;
        }));
        metrics.addCustomChart(new Metrics.SimplePie("compiled", () -> String.valueOf(configManager.isCompiled())));
        metrics.addCustomChart(new Metrics.SimplePie("formatter", () -> configManager.getSettings().getFormat().getName()));
        metrics.addCustomChart(new Metrics.SimplePie("default_billboard", () -> configManager.getSettings().getDefaultBillboard().name()));
        metrics.addCustomChart(new Metrics.AdvancedPie("hooks", () -> {
            final Map<String, Integer> map = Maps.newHashMap();
            hooks.values().forEach(hook -> map.put(hook.getClass().getSimpleName(), 1));
            return map;
        }));
    }

    public <H extends Hook> Optional<H> getHook(@NotNull Class<H> hookType) {
        return Optional.ofNullable(hooks.get(hookType)).map(hookType::cast);
    }

    @Override
    public void onDisable() {
        UNTAPI.unregister();

        hooks.values().forEach(Hook::onDisable);

        trackerManager.onDisable();
        packetEventsListener.onDisable();
        nametagManager.removeAll();
        placeholderManager.close();
        packetManager.close();
        taskScheduler.cancelTasks();
    }

}
