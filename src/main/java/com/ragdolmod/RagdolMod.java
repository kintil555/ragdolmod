package com.ragdolmod;

import com.ragdolmod.config.RagdollConfig;
import com.ragdolmod.network.JumpImpulsePacket;
import com.ragdolmod.network.RagdollSyncPacket;
import com.ragdolmod.physics.PlayerRagdollState;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RagdolMod – Main Entrypoint (Server Side)
 */
public class RagdolMod implements ModInitializer {

    public static final String MOD_ID = "ragdolmod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static RagdollConfig CONFIG;

    private static final Map<UUID, PlayerRagdollState> PLAYER_STATES = new ConcurrentHashMap<>();

    private static final int SYNC_INTERVAL_TICKS = 2;
    private int tickCounter = 0;

    @Override
    public void onInitialize() {
        LOGGER.info("[RagdolMod] Initializing physics ragdoll system...");

        CONFIG = RagdollConfig.load();
        LOGGER.info("[RagdolMod] Config loaded. Wobble: {}, JumpImpulse: {}",
                CONFIG.wobbleIntensity, CONFIG.jumpImpulse);

        // ── Register payload types ────────────────────────────────────
        PayloadTypeRegistry.playC2S().register(JumpImpulsePacket.ID, JumpImpulsePacket.CODEC);
        PayloadTypeRegistry.playS2C().register(RagdollSyncPacket.ID, RagdollSyncPacket.CODEC);

        // ── Networking: receive jump impulse from client ──────────────
        ServerPlayNetworking.registerGlobalReceiver(JumpImpulsePacket.ID,
                (payload, context) -> {
                    context.server().execute(() -> handleJumpPacket(context.player(), payload));
                });

        // ── Player join ───────────────────────────────────────────────
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            UUID uuid = handler.getPlayer().getUuid();
            PLAYER_STATES.put(uuid, new PlayerRagdollState(CONFIG));
            LOGGER.debug("[RagdolMod] Ragdoll state created for player {}", uuid);
        });

        // ── Player leave ──────────────────────────────────────────────
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID uuid = handler.getPlayer().getUuid();
            PLAYER_STATES.remove(uuid);
            LOGGER.debug("[RagdolMod] Ragdoll state removed for player {}", uuid);
        });

        // ── Server tick ───────────────────────────────────────────────
        ServerTickEvents.END_SERVER_TICK.register(this::onServerTick);

        LOGGER.info("[RagdolMod] Ready. Physics will activate for all players.");
    }

    private void onServerTick(MinecraftServer server) {
        tickCounter++;

        for (var world : server.getWorlds()) {
            for (ServerPlayerEntity player : world.getPlayers()) {
                UUID uuid = player.getUuid();
                PlayerRagdollState state = PLAYER_STATES.computeIfAbsent(
                        uuid, k -> new PlayerRagdollState(CONFIG));

                tickPlayerMovement(player, state);
                state.tick(player);

                if (tickCounter % SYNC_INTERVAL_TICKS == 0) {
                    RagdollSyncPacket syncPkt = buildSyncPacket(player, state);
                    syncPkt.sendToSelf(player);
                    syncPkt.sendToTracking(player, true);
                }
            }
        }
    }

    private void tickPlayerMovement(ServerPlayerEntity player, PlayerRagdollState state) {
        var vel = player.getVelocity();

        float yawRad = (float) Math.toRadians(player.getYaw());
        float sinYaw = (float) Math.sin(yawRad);
        float cosYaw = (float) Math.cos(yawRad);

        float fwd    = (float)(-sinYaw * vel.x + cosYaw * vel.z);
        float strafe = (float)(-cosYaw * vel.x - sinYaw * vel.z);

        fwd    = Math.max(-1, Math.min(1, fwd * 8));
        strafe = Math.max(-1, Math.min(1, strafe * 8));

        state.applyWalkForce(fwd, strafe, player.getYaw(), player.isOnGround());
    }

    private void handleJumpPacket(ServerPlayerEntity player, JumpImpulsePacket pkt) {
        PlayerRagdollState state = PLAYER_STATES.get(player.getUuid());
        if (state == null) return;
        state.onJump(player);
        LOGGER.debug("[RagdolMod] Jump impulse applied for {}", player.getName().getString());
    }

    private RagdollSyncPacket buildSyncPacket(ServerPlayerEntity player, PlayerRagdollState state) {
        return new RagdollSyncPacket(
                player.getUuid(),
                state.engine.tiltAngle,
                state.engine.swayAngle,
                state.engine.headLagAngle,
                state.engine.cameraRoll,
                state.engine.cameraPitchBump,
                state.engine.velocity.x,
                state.engine.velocity.z,
                state.engine.stumbleIntensity
        );
    }

    public static PlayerRagdollState getState(UUID uuid) {
        return PLAYER_STATES.get(uuid);
    }
}
