package org.alexdev.unlimitednametags.packet;

import com.github.retrooper.packetevents.PacketEvents;
import io.github.retrooper.packetevents.adventure.serializer.legacy.LegacyComponentSerializer;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

@RequiredArgsConstructor
public class KyoriManager {

    private final UnlimitedNameTags plugin;

    public void sendMessage(@NotNull CommandSender sender, @NotNull Component component) {
        if (plugin.isPaper()) {
            sender.sendMessage(component);
            return;
        }

        if(!(sender instanceof Player)) {
            sender.sendMessage(LegacyComponentSerializer.legacySection().serialize(component));
            return;
        }

        PacketEvents.getAPI().getPlayerManager().getUser(sender).sendMessage(component);
    }

}
