package org.alexdev.unlimitednametags.events;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientEntityAction;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPassengers;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams;
import io.github.retrooper.packetevents.util.GeyserUtil;
import net.kyori.adventure.text.Component;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.alexdev.unlimitednametags.hook.FloodgateHook;
import org.alexdev.unlimitednametags.hook.GeyserHook;
import org.alexdev.unlimitednametags.hook.ViaVersionHook;
import org.alexdev.unlimitednametags.packet.PacketNameTag;
import org.alexdev.unlimitednametags.placeholders.PAPIManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Optional;
import java.util.regex.Pattern;

public class PacketEventsListener extends PacketListenerAbstract {

    private static final Pattern RELATIONAL_PLACEHOLDER = Pattern.compile("%rel_.*?%");

    private final UnlimitedNameTags plugin;

    public PacketEventsListener(UnlimitedNameTags plugin) {
        this.plugin = plugin;
    }

    public void onEnable() {
        PacketEvents.getAPI().getEventManager().registerListener(this);
    }

    public void onPacketSend(@NotNull PacketSendEvent event) {
        if (event.getPacketType() == PacketType.Play.Server.TEAMS) {
            handleTeams(event);
        } else if (event.getPacketType() == PacketType.Play.Server.SET_PASSENGERS) {
            handlePassengers(event);
        } else if (event.getPacketType() == PacketType.Play.Server.ENTITY_METADATA) {
            handleMetaData(event);
        }
    }


    @Override
    public void onPacketReceive(@NotNull PacketReceiveEvent event) {
        if (event.getPacketType() == PacketType.Play.Client.ENTITY_ACTION) {
            handleUseEntity(event);
        }
//        else if (event.getPacketType() == PacketType.Play.Client.ENTITY_ACTION) {
//            handlePlayerCommand(event);
//        }
    }

    private void handlePlayerCommand(PacketReceiveEvent event) {
        System.out.println("Player command");
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        final WrapperPlayClientEntityAction packet = new WrapperPlayClientEntityAction(event);
        System.out.println(packet.getAction());
        if (packet.getAction() == WrapperPlayClientEntityAction.Action.START_FLYING_WITH_ELYTRA) {
            System.out.println("Flying with elytra");
            if (!plugin.getConfigManager().getSettings().isShowCurrentNameTag()) {
                return;
            }

            plugin.getNametagManager().getPacketDisplayText(player).ifPresent(packetNameTag -> {
                packetNameTag.hideForOwner();

                plugin.getTaskScheduler().runTaskLaterAsynchronously(packetNameTag::showForOwner, 5);
            });
        }
    }

    private void handleUseEntity(PacketReceiveEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }

        final WrapperPlayClientEntityAction packet = new WrapperPlayClientEntityAction(event);
        final Optional<? extends Player> player = plugin.getPlayerListener().getPlayerFromEntityId(packet.getEntityId());
        if (player.isEmpty()) {
            return;
        }
        switch (packet.getAction()) {
            case START_SNEAKING -> plugin.getNametagManager().updateSneaking(player.get(), true);
            case STOP_SNEAKING -> plugin.getNametagManager().updateSneaking(player.get(), false);
        }
    }

    private void handlePassengers(@NotNull PacketSendEvent event) {
        final WrapperPlayServerSetPassengers packet = new WrapperPlayServerSetPassengers(event);
        final Optional<? extends Player> player = plugin.getPlayerListener().getPlayerFromEntityId(packet.getEntityId());
        if (player.isEmpty()) {
            return;
        }

        final Optional<PacketNameTag> optionalPacketDisplayText = plugin.getNametagManager().getPacketDisplayText(player.get());
        if (optionalPacketDisplayText.isEmpty()) {
            return;
        }

        plugin.getPacketManager().setPassengers(player.get(), Arrays.stream(packet.getPassengers()).boxed().toList());
    }

    private void handleTeams(@NotNull PacketSendEvent event) {
        if (!plugin.getConfigManager().getSettings().isDisableDefaultNameTag()) {
            return;
        }

        final Player player = Bukkit.getPlayer(event.getUser().getUUID());
        if(player == null) {
            return;
        }

        if(plugin.getHook(ViaVersionHook.class).map(h -> h.hasNotTextDisplays(player)).orElse(false)) {
            return;
        }

        final WrapperPlayServerTeams packet = new WrapperPlayServerTeams(event);
        if (packet.getTeamMode() == WrapperPlayServerTeams.TeamMode.CREATE || packet.getTeamMode() == WrapperPlayServerTeams.TeamMode.UPDATE) {
            packet.getTeamInfo().ifPresent(t -> t.setTagVisibility(WrapperPlayServerTeams.NameTagVisibility.NEVER));
            event.markForReEncode(true);
        }
    }

    private void handleMetaData(@NotNull PacketSendEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        //handle metadata for : bedrock players && client with version 1.20.1 or lower
        final WrapperPlayServerEntityMetadata packet = new WrapperPlayServerEntityMetadata(event);
        final Optional<PacketNameTag> textDisplay = plugin.getNametagManager().getPacketDisplayText(packet.getEntityId());
        if (textDisplay.isEmpty()) {
            return;
        }

        checkRelationalPlaceholders(player, event, packet, textDisplay.get());
    }

    private boolean checkOldVersion(@NotNull Player player, @NotNull PacketSendEvent event,
                                 @NotNull WrapperPlayServerEntityMetadata packet) {
        final boolean changeY = isBedrockPlayer(player);
        if (!changeY) {
            return false;
        }

        final Optional<EntityData> entityData = packet.getEntityMetadata().stream()
                .filter(e -> e.getType().equals(EntityDataTypes.ADV_COMPONENT))
                .findFirst();

        if (entityData.isEmpty()) {
            return false;
        }

//        final Component space = Component.text("\t ");
//
//        final Component targetComponent = (Component) entityData.get().getValue();
//        Component finalComponent = Component.empty();
//        for (int i = 0; i < 10; i++) {
//            finalComponent = finalComponent.append(space);
//        }
//        entityData.get().setValue(targetComponent.append(finalComponent));
//        event.markForReEncode(true);
//        System.out.println("Added 10 lines to " + player.getName());
        return true;



//        for (final EntityData eData : packet.getEntityMetadata()) {
//            if (eData.getIndex() == 11) {
//                final Vector3f old = (Vector3f) eData.getValue();
//                final Vector3f newV = new Vector3f(old.getX(), old.getY() + 0.45f, old.getZ());
//                System.out.println("Increasing y for packet to " + player.getName());
//                eData.setValue(newV);
//                event.markForReEncode(true);
//                return;
//            }
//        }
    }

    private void checkRelationalPlaceholders(@NotNull Player player, @NotNull PacketSendEvent event,
                                             @NotNull WrapperPlayServerEntityMetadata packet, @NotNull PacketNameTag packetNameTag) {
        final Player owner = packetNameTag.getOwner();
        if (owner == null) {
            return;
        }

        final PAPIManager papiManager = plugin.getPlaceholderManager().getPapiManager();
        if (!papiManager.isPAPIEnabled()) {
            return;
        }

        if(packetNameTag.getLines().stream().noneMatch(l -> RELATIONAL_PLACEHOLDER.matcher(l).find())) {
            return;
        }

        final Optional<EntityData> relationalData = packet.getEntityMetadata().stream()
                .filter(e -> e.getType().equals(EntityDataTypes.ADV_COMPONENT))
                .findFirst();

        if (relationalData.isEmpty()) {
            return;
        }

        final Component relationalComponent = plugin.getPlaceholderManager().applyRelationalPlaceholders(player, owner, packetNameTag.getLines());
        relationalData.get().setValue(relationalComponent);
        event.markForReEncode(true);
    }

    public boolean isBedrockPlayer(Player player) {
        if (plugin.getHook(GeyserHook.class).map(h -> h.isBedrockPlayer(player)).orElse(false)) {
            return true;
        }

        if (plugin.getHook(FloodgateHook.class).map(h -> h.isFloodgatePlayer(player)).orElse(false)) {
            return true;
        }

        return GeyserUtil.isGeyserPlayer(player.getUniqueId());
    }

    public void onDisable() {
        PacketEvents.getAPI().getEventManager().unregisterListener(this);
    }
}
