package com.ragdolmod.client;

import com.ragdolmod.network.JumpImpulsePacket;
import com.ragdolmod.network.RagdollSyncPacket;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

/**
 * RagdolModClient – Client Entrypoint
 */
@Environment(EnvType.CLIENT)
public class RagdolModClient implements ClientModInitializer {

    private boolean prevOnGround = true;
    private int jumpPacketCooldown = 0;

    @Override
    public void onInitializeClient() {
        // ── Receive ragdoll sync from server ──────────────────────────
        ClientPlayNetworking.registerGlobalReceiver(RagdollSyncPacket.ID,
                (payload, context) -> {
                    ClientRagdollCache.update(
                            payload.playerUUID(),
                            payload.tiltAngle(), payload.swayAngle(), payload.headLagAngle(),
                            payload.cameraRoll(), payload.cameraPitchBump(),
                            payload.velocityX(), payload.velocityZ(), payload.stumbleIntensity()
                    );
                });

        // ── Detect local player jumps → send impulse packet ──────────
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;
            if (jumpPacketCooldown > 0) jumpPacketCooldown--;

            ClientPlayerEntity player = client.player;
            boolean onGround = player.isOnGround();

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

    private void sendJumpPacket(MinecraftClient client, ClientPlayerEntity player) {
        var options = client.options;
        float fwd = 0, strafe = 0;
        if (options.forwardKey.isPressed())  fwd    += 1;
        if (options.backKey.isPressed())     fwd    -= 1;
        if (options.rightKey.isPressed())    strafe += 1;
        if (options.leftKey.isPressed())     strafe -= 1;

        ClientPlayNetworking.send(new JumpImpulsePacket(fwd, strafe, player.getYaw()));
    }
}
