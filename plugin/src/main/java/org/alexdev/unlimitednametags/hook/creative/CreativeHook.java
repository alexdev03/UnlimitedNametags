package org.alexdev.unlimitednametags.hook.creative;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.google.common.collect.Maps;
import net.kyori.adventure.key.Key;
import org.alexdev.unlimitednametags.hook.HelmetDebugContext;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import team.unnamed.creative.ResourcePack;
import team.unnamed.creative.base.Vector3Float;
import team.unnamed.creative.model.ItemOverride;
import team.unnamed.creative.model.ItemTransform;
import team.unnamed.creative.model.Model;

import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

public interface CreativeHook {

    Logger HELMET_CREATIVE_DBG = Logger.getLogger("UnlimitedNameTags");

    double MULTIPLIER = 1.1;

    void loadTexture();

    @Nullable
    ResourcePack getResourcePack();

    @NotNull
    Map<Key, Map<Integer, Model>> getCmdCache();

    default double getHigh(@NotNull ItemStack helmet) {
        if (!helmet.hasItemMeta()) {
            return 0;
        }
        final ItemMeta meta = helmet.getItemMeta();
        final boolean hasCmd = meta.hasCustomModelData();
        // Without CMD, still allow 1.21.3+ equippable models or item_model (matches findModel tail).
        final boolean mayUseEquippable = !PacketEvents.getAPI().getServerManager().getVersion().isOlderThan(ServerVersion.V_1_21_3)
                && meta.hasEquippable()
                && meta.getEquippable().getModel() != null;
        final boolean mayUseItemModel = meta.hasItemModel() && meta.getItemModel() != null;
        if (!hasCmd && !mayUseEquippable && !mayUseItemModel) {
            if (HelmetDebugContext.isVerbose()) {
                HELMET_CREATIVE_DBG.info("[UNT helmet dbg] CreativeHook.getHigh(stack): bail early hasCmd=" + hasCmd
                        + " mayUseEquippable=" + mayUseEquippable + " mayUseItemModel=" + mayUseItemModel);
            }
            return 0;
        }

        final Optional<Model> optionalModel = findModel(helmet);
        final double out = optionalModel.map(this::getHighFromModel).orElse(0.0);
        if (HelmetDebugContext.isVerbose()) {
            HELMET_CREATIVE_DBG.info("[UNT helmet dbg] CreativeHook.getHigh(stack): modelResolved=" + optionalModel.isPresent()
                    + " metric=" + out);
        }
        return out;
    }

    default double getHigh(@NotNull Player player) {
        final ItemStack helmet = player.getInventory().getHelmet();
        if (helmet == null) {
            return 0;
        }

        return getHigh(helmet);
    }

    @SuppressWarnings("UnstableApiUsage")
    default Optional<Model> findModel(@NotNull ItemStack item) {
        final ResourcePack pack = getResourcePack();
        if (pack == null) {
            if (HelmetDebugContext.isVerbose()) {
                HELMET_CREATIVE_DBG.info("[UNT helmet dbg] CreativeHook.findModel: getResourcePack() is null");
            }
            return Optional.empty();
        }
        final Map<Integer, Model> cmdCache = getCmdCache().computeIfAbsent(item.getType().getKey(), k -> Maps.newConcurrentMap());

        final ItemMeta itemMeta = item.getItemMeta();
        if (itemMeta.hasCustomModelData()) {
            final int customModelData = itemMeta.getCustomModelData();
            final String asString = Integer.toString(customModelData);
            if (cmdCache.containsKey(customModelData)) {
                return Optional.of(cmdCache.get(customModelData));
            }

            var key = new NamespacedKey(item.getType().getKey().namespace(), "item/" + item.getType().getKey().value());
            var modelIn = pack.model(key);

            if (modelIn != null) {
                final Optional<ItemOverride> optionalOverride = findItemOverride(modelIn, customModelData, asString);
                if (optionalOverride.isPresent()) {
                    final Model model = pack.model(optionalOverride.get().model());
                    if (model != null) {
                        cmdCache.put(customModelData, model);
                        return Optional.of(model);
                    }
                }
            }

            //Fallback
            Optional<ItemOverride> optionalOverride = Optional.empty();
            for (Model model : pack.models()) {
                optionalOverride = findItemOverride(model, customModelData, asString);
                if (optionalOverride.isPresent()) {
                    break;
                }
            }

            return optionalOverride.flatMap(override -> {
                final Model model = pack.model(override.model());
                if (model == null) {
                    return Optional.empty();
                }
                cmdCache.put(customModelData, model);
                return Optional.of(model);
            });
        }

        if (!PacketEvents.getAPI().getServerManager().getVersion().isOlderThan(ServerVersion.V_1_21_3)
                && itemMeta.hasEquippable()) {
            final NamespacedKey model = itemMeta.getEquippable().getModel();
            if (model != null) {
                final Model resolved = pack.model(model);
                if (resolved != null) {
                    return Optional.of(resolved);
                }
                if (HelmetDebugContext.isVerbose()) {
                    HELMET_CREATIVE_DBG.info("[UNT helmet dbg] CreativeHook.findModel: equippable.model=" + model + " not in pack");
                }
            }
        }

        if (itemMeta.hasItemModel()) {
            final NamespacedKey itemModelKey = itemMeta.getItemModel();
            if (itemModelKey != null) {
                final Model im = pack.model(itemModelKey);
                if (im != null) {
                    return Optional.of(im);
                }
                if (HelmetDebugContext.isVerbose()) {
                    HELMET_CREATIVE_DBG.info("[UNT helmet dbg] CreativeHook.findModel: item_model=" + itemModelKey + " not in pack");
                }
            }
        }

        if (HelmetDebugContext.isVerbose()) {
            HELMET_CREATIVE_DBG.info("[UNT helmet dbg] CreativeHook.findModel: giving up (see prior lines)");
        }
        return Optional.empty();
    }

    default Optional<ItemOverride> findItemOverride(@NotNull Model model, int customModelData, @NotNull String asString) {
        return model.overrides().stream()
                .filter(e -> e.predicate().stream().anyMatch(p ->
                        p.name().equalsIgnoreCase("custom_model_data") &&
                                (p.value().equals(customModelData) || p.value().toString().equals(asString))))
                .findFirst();
    }

    default double getHighFromModel(@NotNull Model model) {
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

}
