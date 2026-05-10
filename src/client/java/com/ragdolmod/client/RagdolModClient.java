package com.ragdolmod.client;

import com.ragdolmod.network.JumpImpulsePacket;
import com.ragdolmod.network.RagdollSyncPacket;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * RagdolModClient – Client Entrypoint
 *
 * Handles:
 *  1. Receiving RagdollSyncPacket and updating ClientRagdollCache.
 *  2. Detecting local player jumps and sending JumpImpulsePacket to server.
 *  3. Cleaning up cache on disconnect.
 */
@Environment(EnvType.CLIENT)
public class RagdolModClient implements ClientModInitializer {

    /** Track previous onGround state to detect jump initiation. */
    private boolean prevOnGround = true;
    /** Small debounce to avoid sending duplicate jump packets. */
    private int jumpPacketCooldown = 0;

    @Override
    public void onInitializeClient() {
        // ── Receive ragdoll sync from server ──────────────────────────
        ClientPlayNetworking.registerGlobalReceiver(RagdollSyncPacket.ID,
                (client, handler, buf, responseSender) -> {
                    RagdollSyncPacket pkt = RagdollSyncPacket.decode(buf);
                    // Update cache on render thread is safe since ConcurrentHashMap is used
                    ClientRagdollCache.update(
                            pkt.playerUUID,
                            pkt.tiltAngle, pkt.swayAngle, pkt.headLagAngle,
                            pkt.cameraRoll, pkt.cameraPitchBump,
                            pkt.velocityX, pkt.velocityZ, pkt.stumbleIntensity
                    );
                });

        // ── Detect local player jumps → send impulse packet ──────────
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;
            if (jumpPacketCooldown > 0) jumpPacketCooldown--;

            ClientPlayerEntity player = client.player;
            boolean onGround = player.isOnGround();

            // Detect jump: was on ground, now in air, and space is pressed
            if (prevOnGround && !onGround && jumpPacketCooldown == 0) {
                sendJumpPacket(client, player);
                jumpPacketCooldown = 4;
            }

            prevOnGround = onGround;
        });

        // ── Clean cache on disconnect ─────────────────────────────────
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            if (client.player != null) {
                ClientRagdollCache.remove(client.player.getUuid());
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────

    private void sendJumpPacket(MinecraftClient client, ClientPlayerEntity player) {
        // Read WASD input at jump moment for accurate momentum direction
        var options = client.options;
        float fwd    = 0, strafe = 0;
        if (options.forwardKey.isPressed())  fwd    += 1;
        if (options.backKey.isPressed())     fwd    -= 1;
        if (options.rightKey.isPressed())    strafe += 1;
        if (options.leftKey.isPressed())     strafe -= 1;

        JumpImpulsePacket pkt = new JumpImpulsePacket(fwd, strafe, player.getYaw());
        ClientPlayNetworking.send(JumpImpulsePacket.ID, pkt.encode());
    }
}
