package com.maris7.guard.nms.v1_21.playertrace;

import com.maris7.guard.playertrace.PlayerVisibilityPacketBridge;
import com.mojang.datafixers.util.Pair;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class V1_21PlayerVisibilityPacketBridge implements PlayerVisibilityPacketBridge {
    @Override
    public void sendSpawnPackets(Player viewer, Player target) {
        ServerPlayer viewerHandle = ((CraftPlayer) viewer).getHandle();
        ServerPlayer targetHandle = ((CraftPlayer) target).getHandle();
        int id = targetHandle.getId();
        viewerHandle.connection.send(new ClientboundAddEntityPacket(id, targetHandle.getUUID(), targetHandle.getX(), targetHandle.getY(), targetHandle.getZ(), targetHandle.getXRot(), targetHandle.getYRot(), EntityType.PLAYER, 0, targetHandle.getDeltaMovement(), targetHandle.getYHeadRot()));
        viewerHandle.connection.send(new ClientboundSetEntityDataPacket(id, targetHandle.getEntityData().packAll()));
        viewerHandle.connection.send(new ClientboundSetEquipmentPacket(id, equipment(targetHandle)));
        viewerHandle.connection.send(new ClientboundRotateHeadPacket(targetHandle, toPackedAngle(targetHandle.getYHeadRot())));
        viewerHandle.connection.send(ClientboundTeleportEntityPacket.teleport(id, PositionMoveRotation.of(targetHandle), Set.<Relative>of(), targetHandle.onGround()));
    }

    private List<Pair<EquipmentSlot, net.minecraft.world.item.ItemStack>> equipment(ServerPlayer targetHandle) {
        List<Pair<EquipmentSlot, net.minecraft.world.item.ItemStack>> equipment = new ArrayList<>();
        addEquipment(equipment, targetHandle, EquipmentSlot.MAINHAND);
        addEquipment(equipment, targetHandle, EquipmentSlot.OFFHAND);
        addEquipment(equipment, targetHandle, EquipmentSlot.FEET);
        addEquipment(equipment, targetHandle, EquipmentSlot.LEGS);
        addEquipment(equipment, targetHandle, EquipmentSlot.CHEST);
        addEquipment(equipment, targetHandle, EquipmentSlot.HEAD);
        return equipment;
    }

    private void addEquipment(List<Pair<EquipmentSlot, net.minecraft.world.item.ItemStack>> equipment, ServerPlayer targetHandle, EquipmentSlot slot) {
        equipment.add(Pair.of(slot, targetHandle.getItemBySlot(slot)));
    }

    private byte toPackedAngle(float angle) {
        return (byte) ((int) (angle * 256.0F / 360.0F));
    }
}
