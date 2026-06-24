package me.tofaa.entitylib.spigot;

import com.github.retrooper.packetevents.PacketEventsAPI;
import io.github.retrooper.packetevents.bstats.bukkit.Metrics;
import io.github.retrooper.packetevents.bstats.charts.SimplePie;
import me.tofaa.entitylib.APIConfig;
import me.tofaa.entitylib.EntityLib;
import me.tofaa.entitylib.EntityIdProvider;
import me.tofaa.entitylib.EntityUuidProvider;
import me.tofaa.entitylib.UserLocaleProvider;
import me.tofaa.entitylib.common.AbstractPlatform;
import me.tofaa.entitylib.event.EventHandler;
import org.alexdev.unlimitednametags.packet.CustomSpigotEntityProvider;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.util.logging.Logger;

public final class UntSpigotEntityLibPlatform extends SpigotEntityLibPlatform {

    private SpigotEntityLibAPI api;
    private UserLocaleProvider userLocaleProvider = new SpigotPlayerLocaleProvider();

    public UntSpigotEntityLibPlatform(@NotNull JavaPlugin plugin) {
        super(plugin);
    }

    @Override
    public void setupApi(@NotNull APIConfig settings) {
        setupBaseProviders();
        this.logger = settings.shouldUsePlatformLogger() ? handle.getLogger() : Logger.getLogger("EntityLib");
        this.api = new SpigotEntityLibAPI(this, settings);
        this.setEntityIdProvider(new CustomSpigotEntityProvider());
        this.api.onLoad();
        this.api.onEnable();
        if (settings.shouldUseBstats()) {
            final PacketEventsAPI<Plugin> packetEvents = (PacketEventsAPI<Plugin>) api.getPacketEvents();
            final Metrics metrics = new Metrics(packetEvents.getPlugin(), 21916);
            metrics.addCustomChart(new SimplePie("entitylib-version", () -> EntityLib.getVersion().toString()));
        }
    }

    @Override
    public SpigotEntityLibAPI getAPI() {
        return api;
    }

    @Override
    public String getName() {
        return "Spigot";
    }

    @Override
    public @NotNull UserLocaleProvider getUserLocaleProvider() {
        return userLocaleProvider;
    }

    @Override
    public void setUserLocaleProvider(@NotNull final UserLocaleProvider provider) {
        this.userLocaleProvider = provider;
    }

    private void setupBaseProviders() {
        setAbstractPlatformField("eventHandler", EventHandler.create());
        this.setEntityIdProvider(new EntityIdProvider.DefaultEntityIdProvider());
        this.setEntityUuidProvider(new EntityUuidProvider.DefaultEntityUuidProvider());
    }

    private void setAbstractPlatformField(@NotNull String name, @NotNull Object value) {
        try {
            final Field field = AbstractPlatform.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(this, value);
        } catch (final ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to initialize EntityLib platform field " + name, exception);
        }
    }
}
