package com.maris7.guard.nms.v1_21.raytraceantixray;

import com.maris7.guard.raytraceantixray.RayTracePacketBridge;
import io.netty.channel.Channel;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

public final class V1_21RayTracePacketBridge implements RayTracePacketBridge {
    @Override
    public boolean sendPacketImmediately(Player player, Object packet) {
        ServerGamePacketListenerImpl connection = ((CraftPlayer) player).getHandle().connection;

        if (connection == null || connection.isDisconnected()) {
            return false;
        }

        Channel channel = connection.connection.channel;

        if (channel == null || !channel.isOpen()) {
            return false;
        }

        if (channel.eventLoop().inEventLoop()) {
            channel.writeAndFlush(packet);
        } else {
            channel.eventLoop().execute(() -> {
                if (channel.isOpen()) {
                    channel.writeAndFlush(packet);
                }
            });
        }
        return true;
    }
}
