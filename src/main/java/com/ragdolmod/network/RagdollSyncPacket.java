package com.ragdolmod.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * RagdollSyncPacket
 *
 * Server → Client packet that broadcasts a player's ragdoll physics state.
 * Sent every 2 ticks (25 Hz) for efficiency while remaining smooth.
 *
 * Packet layout (binary, big-endian):
 *   UUID    playerUUID    (2 longs / 16 bytes)
 *   float   tiltAngle
 *   float   swayAngle
 *   float   headLagAngle
 *   float   cameraRoll
 *   float   cameraPitchBump
 *   float   velocityX
 *   float   velocityZ
 *   float   stumbleIntensity
 */
public record RagdollSyncPacket(
        UUID playerUUID,
        float tiltAngle,
        float swayAngle,
        float headLagAngle,
        float cameraRoll,
        float cameraPitchBump,
        float velocityX,
        float velocityZ,
        float stumbleIntensity
) implements CustomPayload {

    public static final CustomPayload.Id<RagdollSyncPacket> ID =
            new CustomPayload.Id<>(Identifier.of("ragdolmod", "ragdoll_sync"));

    public static final PacketCodec<PacketByteBuf, RagdollSyncPacket> CODEC =
            PacketCodec.of(
                    (pkt, buf) -> {
                        buf.writeUuid(pkt.playerUUID());
                        buf.writeFloat(pkt.tiltAngle());
                        buf.writeFloat(pkt.swayAngle());
                        buf.writeFloat(pkt.headLagAngle());
                        buf.writeFloat(pkt.cameraRoll());
                        buf.writeFloat(pkt.cameraPitchBump());
                        buf.writeFloat(pkt.velocityX());
                        buf.writeFloat(pkt.velocityZ());
                        buf.writeFloat(pkt.stumbleIntensity());
                    },
                    buf -> new RagdollSyncPacket(
                            buf.readUuid(),
                            buf.readFloat(), buf.readFloat(), buf.readFloat(),
                            buf.readFloat(), buf.readFloat(),
                            buf.readFloat(), buf.readFloat(), buf.readFloat()
                    )
            );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }

    // ─────────────────────────────────────────────────────────────────
    // Server → nearby clients broadcast helper
    // ─────────────────────────────────────────────────────────────────

    /**
     * Send this packet to all players within 64 blocks of {@code origin}.
     *
     * @param origin       the player whose state is being synced
     * @param selfExclude  whether to skip sending to origin themselves
     */
    public void sendToTracking(ServerPlayerEntity origin, boolean selfExclude) {
        for (ServerPlayerEntity nearby : origin.getServerWorld().getPlayers()) {
            if (selfExclude && nearby == origin) continue;
            if (nearby.squaredDistanceTo(origin) > 64 * 64) continue;
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(nearby, this);
        }
    }

    /**
     * Send this packet to the player themselves (for their own camera/animation).
     */
    public void sendToSelf(ServerPlayerEntity player) {
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, this);
    }
}
