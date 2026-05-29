package org.alexdev.unlimitednametags.api;

import me.tofaa.entitylib.meta.display.AbstractDisplayMeta;
import net.kyori.adventure.text.Component;
import org.alexdev.unlimitednametags.config.DisplayAnimation;
import org.alexdev.unlimitednametags.config.Settings;
import org.alexdev.unlimitednametags.hook.hat.HatHook;
import org.alexdev.unlimitednametags.vanish.VanishIntegration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Paper/Bukkit {@link UNTAPI} with {@link Player} convenience methods.
 * Retrieve via {@link #getInstance()}.
 */
@SuppressWarnings("unused")
public final class UNTPaperAPI extends UNTAPI {

    private final UnlimitedNameTagsInstancePaper paperPlugin;

    private UNTPaperAPI(@NotNull UnlimitedNameTagsInstancePaper plugin) {
        super(plugin);
        this.paperPlugin = plugin;
    }

    @NotNull
    public static UNTPaperAPI getInstance() {
        return (UNTPaperAPI) UNTAPI.getInstance();
    }

    @ApiStatus.Internal
    public static void register(@NotNull UnlimitedNameTagsInstancePaper plugin) {
        setInstance(new UNTPaperAPI(plugin));
    }

    @ApiStatus.Internal
    public static void unregister() {
        setInstance(null);
    }

    @NotNull
    public UnlimitedNameTagsInstancePaper paperPlugin() {
        return paperPlugin;
    }

    @NotNull
    private UntNametagManagerPaper nametagManagerPaper() {
        return paperPlugin.getNametagManager();
    }

    public void vanishPlayer(@NotNull Player player) {
        vanishPlayer(player.getUniqueId());
    }

    public void unVanishPlayer(@NotNull Player player) {
        unVanishPlayer(player.getUniqueId());
    }

    public void hideNametag(@NotNull Player player) {
        hideNametag(player.getUniqueId());
    }

    public void showNametag(@NotNull Player player) {
        showNametag(player.getUniqueId());
    }

    @NotNull
    public Collection<? extends UntNametagDisplay> getPacketDisplayText(@NotNull Player player) {
        return nametagManagerPaper().getPacketDisplayText(player);
    }

    public void setNametagOverride(@NotNull Player player, @NotNull Settings.NameTag nameTag) {
        setNametagOverride(player.getUniqueId(), nameTag);
    }

    public void modifyNametagProperty(
            @NotNull Player player,
            @NotNull Function<Settings.NameTag, Settings.NameTag> modifier) {
        modifyNametagProperty(player.getUniqueId(), modifier);
    }

    public void removeNametagOverride(@NotNull Player player) {
        removeNametagOverride(player.getUniqueId());
    }

    public boolean hasNametagOverride(@NotNull Player player) {
        return hasNametagOverride(player.getUniqueId());
    }

    @NotNull
    public Optional<Settings.NameTag> getNametagOverride(@NotNull Player player) {
        return getNametagOverride(player.getUniqueId());
    }

    @NotNull
    public Settings.NameTag getEffectiveNametag(@NotNull Player player) {
        return getEffectiveNametag(player.getUniqueId());
    }

    @NotNull
    public Settings.NameTag getConfigNametag(@NotNull Player player) {
        return getConfigNametag(player.getUniqueId());
    }

    public void setShiftSystemBlocked(@NotNull Player player, boolean blocked) {
        setShiftSystemBlocked(player.getUniqueId(), blocked);
    }

    public boolean isShiftSystemBlocked(@NotNull Player player) {
        return isShiftSystemBlocked(player.getUniqueId());
    }

    public void forceRefresh(@NotNull Player player) {
        forceRefresh(player.getUniqueId());
    }

    public void forceRefresh(@NotNull Player player, boolean force) {
        forceRefresh(player.getUniqueId(), force);
    }

    public void setNametagScale(@NotNull Player player, float scale) {
        setNametagScale(player.getUniqueId(), scale);
    }

    public void setNametagBackground(@NotNull Player player, @NotNull Settings.Background background) {
        setNametagBackground(player.getUniqueId(), background);
    }

    public void setNametagDisplayGroups(@NotNull Player player, @NotNull List<Settings.DisplayGroup> displayGroups) {
        setNametagDisplayGroups(player.getUniqueId(), displayGroups);
    }

    public void setNametagDisplayGroupAnimation(
            @NotNull Player player,
            int displayGroupIndex,
            @Nullable DisplayAnimation animation) {
        final Settings.NameTag current = paperPlugin.getNametagManager().getEffectiveNametag(player);
        final List<Settings.DisplayGroup> groups = new ArrayList<>(current.displayGroups());
        if (displayGroupIndex < 0 || displayGroupIndex >= groups.size()) {
            throw new IllegalArgumentException(
                    "displayGroupIndex " + displayGroupIndex + " out of range (size " + groups.size() + ")");
        }
        groups.set(displayGroupIndex, groups.get(displayGroupIndex).withAnimation(animation));
        final Settings.NameTag modified = new Settings.NameTag(current.permission(), List.copyOf(groups));
        paperPlugin.getNametagManager().setNametagOverride(player, modified);
        paperPlugin.getNametagManager().refresh(player, true);
    }

    public void clearNametagDisplayGroupAnimation(@NotNull Player player, int displayGroupIndex) {
        setNametagDisplayGroupAnimation(player, displayGroupIndex, null);
    }

    public void registerNametagCustomAnimation(@NotNull String id, @NotNull NametagCustomAnimationHandler handler) {
        paperPlugin.registerNametagCustomAnimation(id, handler);
    }

    public boolean unregisterNametagCustomAnimation(@NotNull String id) {
        return paperPlugin.unregisterNametagCustomAnimation(id);
    }

    @Nullable
    public NametagCustomAnimationHandler getNametagCustomAnimationHandler(@NotNull String id) {
        return paperPlugin.getNametagCustomAnimationHandler(id);
    }

    /**
     * @deprecated Renamed to {@link #setNametagDisplayGroups(Player, List)}.
     */
    @Deprecated(forRemoval = true, since = "2.0.0")
    public void setNametagLines(@NotNull Player player, @NotNull List<Settings.DisplayGroup> displayGroups) {
        setNametagDisplayGroups(player, displayGroups);
    }

    public void setNametagBillboard(@NotNull Player player, @NotNull AbstractDisplayMeta.BillboardConstraints billboard) {
        nametagManagerPaper().getPacketDisplayText(player).forEach(packetNameTag -> {
            packetNameTag.setBillboard(billboard);
            packetNameTag.refresh();
        });
    }

    public void setNametagShadowed(@NotNull Player player, boolean shadowed) {
        modifyNametagProperty(player, tag -> tag.withDisplayGroups(
                group -> group.withBackground(group.effectiveBackground().withShadowed(shadowed))
        ));
    }

    public void setNametagSeeThrough(@NotNull Player player, boolean seeThrough) {
        modifyNametagProperty(player, tag -> tag.withDisplayGroups(
                group -> group.withBackground(group.effectiveBackground().withSeeThrough(seeThrough))
        ));
    }

    public void setForcedNametag(@NotNull Player player, @NotNull Component component) {
        nametagManagerPaper().getPacketDisplayText(player).stream().findFirst().ifPresent(packetNameTag -> {
            packetNameTag.setForcedNameTag(component);
            packetNameTag.refresh();
        });
    }

    public void setForcedNametag(@NotNull Player player, @NotNull Player viewer, @NotNull Component component) {
        nametagManagerPaper().getPacketDisplayText(player).stream().findFirst().ifPresent(packetNameTag -> {
            packetNameTag.setForcedNameTag(viewer.getUniqueId(), component);
            packetNameTag.refreshForPlayer(viewer);
        });
    }

    public void clearForcedNametag(@NotNull Player player) {
        nametagManagerPaper().getPacketDisplayText(player).stream().findFirst().ifPresent(packetNameTag -> {
            packetNameTag.clearForcedNameTag();
            packetNameTag.refresh();
        });
    }

    public void clearForcedNametag(@NotNull Player player, @NotNull Player viewer) {
        nametagManagerPaper().getPacketDisplayText(player).stream().findFirst().ifPresent(packetNameTag -> {
            packetNameTag.clearForcedNameTag(viewer.getUniqueId());
            packetNameTag.refreshForPlayer(viewer);
        });
    }
}
