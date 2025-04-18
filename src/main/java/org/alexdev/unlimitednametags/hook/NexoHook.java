package org.alexdev.unlimitednametags.hook;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.google.common.collect.Maps;
import com.nexomc.nexo.api.NexoItems;
import com.nexomc.nexo.api.NexoPack;
import com.nexomc.nexo.api.events.NexoItemsLoadedEvent;
import com.nexomc.nexo.items.ItemBuilder;
import com.nexomc.nexo.items.NexoMeta;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.alexdev.unlimitednametags.hook.hat.HatHook;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import team.unnamed.creative.ResourcePack;
import team.unnamed.creative.base.Vector3Float;
import team.unnamed.creative.model.ItemOverride;
import team.unnamed.creative.model.ItemTransform;
import team.unnamed.creative.model.Model;

import java.util.Map;
import java.util.Optional;

@SuppressWarnings("DuplicatedCode")
public class NexoHook extends Hook implements Listener, HatHook {

    private final double MULTIPLIER = 1.1;

    private final Map<Integer, Model> cmdCache;
    private ResourcePack pack;

    public NexoHook(@NotNull UnlimitedNameTags plugin) {
        super(plugin);
        this.cmdCache = Maps.newConcurrentMap();
        loadTexture();
    }

    private void loadTexture() {
        pack = NexoPack.resourcePack();
    }

    public double getHigh(@NotNull Player player) {
        final ItemStack helmet = player.getInventory().getHelmet();
        if (helmet == null) {
            return 0;
        }

        if (!helmet.hasItemMeta() || !helmet.getItemMeta().hasCustomModelData()) {
            return 0;
        }

        final Optional<Model> optionalModel = findModel(helmet);
        return optionalModel.map(this::getHighFromModel).orElse(0.0);
    }

    @SuppressWarnings("UnstableApiUsage")
    private Optional<Model> findModel(@NotNull ItemStack item) {
        final Optional<ItemBuilder> optionalItemBuilder = Optional.ofNullable(NexoItems.builderFromItem(item));
        if (optionalItemBuilder.isPresent()) {
            final Optional<Model> optionalModel = optionalItemBuilder.map(ItemBuilder::getNexoMeta)
                    .map(NexoMeta::getModel)
                    .map(key -> pack.model(key));
            if (optionalModel.isPresent()) {
                return optionalModel;
            }
        }

        final ItemMeta itemMeta = item.getItemMeta();
        if (itemMeta.hasCustomModelData()) {
            final int customModelData = itemMeta.getCustomModelData();
            if (cmdCache.containsKey(customModelData)) {
                return Optional.of(cmdCache.get(customModelData));
            }

            final Optional<ItemOverride> optionalOverride = pack.models().stream()
                    .flatMap(m -> m.overrides()
                            .stream())
                    .filter(e -> e.predicate().stream().anyMatch(p ->
                            p.name().equalsIgnoreCase("custom_model_data") &&
                                    p.value().equals(itemMeta.getCustomModelData())))
                    .findFirst();
            return optionalOverride.map(override -> {
                final Model model = pack.model(override.model());
                cmdCache.put(customModelData, model);
                return model;
            });
        }

        if (PacketEvents.getAPI().getServerManager().getVersion().isOlderThan(ServerVersion.V_1_21_3)) {
            return Optional.empty();
        }

        if (itemMeta.hasEquippable()) {
            final NamespacedKey model = itemMeta.getEquippable().getModel();
            if (model == null) {
                return Optional.empty();
            }
            return Optional.ofNullable(pack.model(model));
        }
        return Optional.empty();
    }

    private double getHighFromModel(@NotNull Model model) {
        double highest = 0;
        for (int i = 0; i < model.elements().size(); i++) {
            final double to = model.elements().get(i).to().y();
            if (to > highest) {
                highest = to;
            }
        }

        if (!model.display().containsKey(ItemTransform.Type.HEAD)) {
            return -1;
        }

        final ItemTransform itemTransform = model.display().get(ItemTransform.Type.HEAD);
        final Vector3Float scale = itemTransform.scale();
        highest *= scale.y();
        highest *= MULTIPLIER;

        final double translation = itemTransform.translation().y();
        return highest + translation;
    }

    @EventHandler
    public void onEnable(NexoItemsLoadedEvent event) {
        cmdCache.clear();
        pack = NexoPack.resourcePack();
        plugin.getLogger().info("Nexo items loaded, clearing cache");
    }

    @Override
    public void onEnable() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void onDisable() {

    }
}
