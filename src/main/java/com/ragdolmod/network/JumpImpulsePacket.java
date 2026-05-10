package com.ragdolmod.network;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
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
public class JumpImpulsePacket {

    public static final Identifier ID =
            Identifier.of("ragdolmod", "jump_impulse");

    public final float forwardInput;
    public final float strafeInput;
    public final float yaw;

    public JumpImpulsePacket(float forwardInput, float strafeInput, float yaw) {
        this.forwardInput = forwardInput;
        this.strafeInput  = strafeInput;
        this.yaw          = yaw;
    }

    public static JumpImpulsePacket decode(PacketByteBuf buf) {
        return new JumpImpulsePacket(buf.readFloat(), buf.readFloat(), buf.readFloat());
    }

    public PacketByteBuf encode() {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeFloat(forwardInput);
        buf.writeFloat(strafeInput);
        buf.writeFloat(yaw);
        return buf;
    }
}
