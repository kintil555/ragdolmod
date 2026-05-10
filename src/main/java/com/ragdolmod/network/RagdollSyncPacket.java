package com.ragdolmod.network;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
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
public class RagdollSyncPacket {

    public static final Identifier ID =
            Identifier.of("ragdolmod", "ragdoll_sync");

    // ─────────────────────────────────────────────────────────────────
    // Data fields (decoded on client)
    // ─────────────────────────────────────────────────────────────────

    public final UUID  playerUUID;
    public final float tiltAngle;
    public final float swayAngle;
    public final float headLagAngle;
    public final float cameraRoll;
    public final float cameraPitchBump;
    public final float velocityX;
    public final float velocityZ;
    public final float stumbleIntensity;

    // ─────────────────────────────────────────────────────────────────
    // Constructor (for building outgoing packets)
    // ─────────────────────────────────────────────────────────────────

    public RagdollSyncPacket(UUID playerUUID,
                              float tiltAngle,
                              float swayAngle,
                              float headLagAngle,
                              float cameraRoll,
                              float cameraPitchBump,
                              float velocityX,
                              float velocityZ,
                              float stumbleIntensity) {
        this.playerUUID       = playerUUID;
        this.tiltAngle        = tiltAngle;
        this.swayAngle        = swayAngle;
        this.headLagAngle     = headLagAngle;
        this.cameraRoll       = cameraRoll;
        this.cameraPitchBump  = cameraPitchBump;
        this.velocityX        = velocityX;
        this.velocityZ        = velocityZ;
        this.stumbleIntensity = stumbleIntensity;
    }

    // ─────────────────────────────────────────────────────────────────
    // Decode from incoming buffer (client side)
    // ─────────────────────────────────────────────────────────────────

    public static RagdollSyncPacket decode(PacketByteBuf buf) {
        UUID  uuid      = buf.readUuid();
        float tilt      = buf.readFloat();
        float sway      = buf.readFloat();
        float headLag   = buf.readFloat();
        float camRoll   = buf.readFloat();
        float camPitch  = buf.readFloat();
        float vx        = buf.readFloat();
        float vz        = buf.readFloat();
        float stumble   = buf.readFloat();
        return new RagdollSyncPacket(uuid, tilt, sway, headLag, camRoll, camPitch, vx, vz, stumble);
    }

    // ─────────────────────────────────────────────────────────────────
    // Encode to buffer (server side)
    // ─────────────────────────────────────────────────────────────────

    public PacketByteBuf encode() {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeUuid(playerUUID);
        buf.writeFloat(tiltAngle);
        buf.writeFloat(swayAngle);
        buf.writeFloat(headLagAngle);
        buf.writeFloat(cameraRoll);
        buf.writeFloat(cameraPitchBump);
        buf.writeFloat(velocityX);
        buf.writeFloat(velocityZ);
        buf.writeFloat(stumbleIntensity);
        return buf;
    }

    // ─────────────────────────────────────────────────────────────────
    // Server → nearby clients broadcast helper
    // ─────────────────────────────────────────────────────────────────

    /**
     * Send this packet to all players within 64 blocks of {@code origin}
     * (excluding the origin player themselves if selfExclude=true).
     *
     * @param origin       the player whose state is being synced
     * @param selfExclude  whether to skip sending to origin themselves
     */
    public void sendToTracking(ServerPlayerEntity origin, boolean selfExclude) {
        PacketByteBuf buf = encode();
        for (ServerPlayerEntity nearby : origin.getServerWorld().getPlayers()) {
            if (selfExclude && nearby == origin) continue;
            if (nearby.squaredDistanceTo(origin) > 64 * 64) continue;
            ServerPlayNetworking.send(nearby, ID, buf);
        }
    }

    /**
     * Send this packet to the player themselves (for their own camera/animation).
     */
    public void sendToSelf(ServerPlayerEntity player) {
        ServerPlayNetworking.send(player, ID, encode());
    }
}
