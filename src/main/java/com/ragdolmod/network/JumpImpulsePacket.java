package com.ragdolmod.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * JumpImpulsePacket
 *
 * Client → Server packet sent when the player initiates a jump.
 * Carries the player's horizontal input at jump time so the server
 * can apply the correct momentum impulse.
 *
 * Packet layout:
 *   float  forwardInput   (-1..1)
 *   float  strafeInput    (-1..1)
 *   float  yaw            (degrees)
 */
public record JumpImpulsePacket(float forwardInput, float strafeInput, float yaw)
        implements CustomPayload {

    public static final CustomPayload.Id<JumpImpulsePacket> ID =
            new CustomPayload.Id<>(Identifier.of("ragdolmod", "jump_impulse"));

    public static final PacketCodec<PacketByteBuf, JumpImpulsePacket> CODEC =
            PacketCodec.of(
                    (pkt, buf) -> {
                        buf.writeFloat(pkt.forwardInput());
                        buf.writeFloat(pkt.strafeInput());
                        buf.writeFloat(pkt.yaw());
                    },
                    buf -> new JumpImpulsePacket(buf.readFloat(), buf.readFloat(), buf.readFloat())
            );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
