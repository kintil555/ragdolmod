package com.ragdolmod;

import com.ragdolmod.config.RagdollConfig;
import com.ragdolmod.network.JumpImpulsePacket;
import com.ragdolmod.network.RagdollSyncPacket;
import com.ragdolmod.physics.PlayerRagdollState;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RagdolMod – Main Entrypoint (Server Side)
 *
 * Registers:
 *  - Config loading
 *  - Per-player ragdoll state storage
 *  - Server tick event for physics update
 *  - Networking: receive JumpImpulsePacket, broadcast RagdollSyncPacket
 *  - Player join/leave lifecycle cleanup
 */
public class RagdolMod implements ModInitializer {

    public static final String MOD_ID = "ragdolmod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** Global config loaded from disk at startup. */
    public static RagdollConfig CONFIG;

    /**
     * Per-player physics state map.
     * Key: player UUID.
     * Thread-safe: accessed from server tick thread only (Minecraft is single-threaded per world).
     */
    private static final Map<UUID, PlayerRagdollState> PLAYER_STATES = new ConcurrentHashMap<>();

    /** Sync packet interval: every N ticks (2 = 25 Hz). */
    private static final int SYNC_INTERVAL_TICKS = 2;
    private int tickCounter = 0;

    // ─────────────────────────────────────────────────────────────────
    // ModInitializer
    // ─────────────────────────────────────────────────────────────────

    @Override
    public void onInitialize() {
        LOGGER.info("[RagdolMod] Initializing physics ragdoll system...");

        // Load configuration
        CONFIG = RagdollConfig.load();
        LOGGER.info("[RagdolMod] Config loaded. Wobble: {}, JumpImpulse: {}",
                CONFIG.wobbleIntensity, CONFIG.jumpImpulse);

        // ── Networking: receive jump impulse from client ──────────────
        ServerPlayNetworking.registerGlobalReceiver(JumpImpulsePacket.ID,
                (server, player, handler, buf, responseSender) -> {
                    JumpImpulsePacket pkt = JumpImpulsePacket.decode(buf);
                    // Execute on server thread
                    server.execute(() -> handleJumpPacket(player, pkt));
                });

        // ── Player join: create ragdoll state ─────────────────────────
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            UUID uuid = handler.getPlayer().getUuid();
            PLAYER_STATES.put(uuid, new PlayerRagdollState(CONFIG));
            LOGGER.debug("[RagdolMod] Ragdoll state created for player {}", uuid);
        });

        // ── Player leave: remove ragdoll state ────────────────────────
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID uuid = handler.getPlayer().getUuid();
            PLAYER_STATES.remove(uuid);
            LOGGER.debug("[RagdolMod] Ragdoll state removed for player {}", uuid);
        });

        // ── Server tick: update all players + sync ────────────────────
        ServerTickEvents.END_SERVER_TICK.register(this::onServerTick);

        LOGGER.info("[RagdolMod] Ready. Physics will activate for all players.");
    }

    // ─────────────────────────────────────────────────────────────────
    // Server tick handler
    // ─────────────────────────────────────────────────────────────────

    private void onServerTick(MinecraftServer server) {
        tickCounter++;

        for (var world : server.getWorlds()) {
            for (ServerPlayerEntity player : world.getPlayers()) {
                UUID uuid = player.getUuid();
                PlayerRagdollState state = PLAYER_STATES.computeIfAbsent(
                        uuid, k -> new PlayerRagdollState(CONFIG));

                // Inject walk force from player's current input
                // Minecraft stores current input in player.input (via mixin injection)
                // We read velocity and infer movement direction here
                tickPlayerMovement(player, state);

                // Tick physics engine
                state.tick(player);

                // Sync state to clients at reduced frequency
                if (tickCounter % SYNC_INTERVAL_TICKS == 0) {
                    RagdollSyncPacket syncPkt = buildSyncPacket(player, state);
                    syncPkt.sendToSelf(player);
                    syncPkt.sendToTracking(player, true);
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Walk force application
    // ─────────────────────────────────────────────────────────────────

    /**
     * Applies the slow ragdoll walking force each tick.
     * We use current Minecraft velocity to infer movement intent,
     * then apply it through the physics engine (which drastically slows it).
     */
    private void tickPlayerMovement(ServerPlayerEntity player, PlayerRagdollState state) {
        // Read Minecraft's own velocity as proxy for WASD input
        var vel = player.getVelocity();

        // Compute approximate forward/strafe from velocity vs yaw
        float yawRad = (float) Math.toRadians(player.getYaw());
        float sinYaw = (float) Math.sin(yawRad);
        float cosYaw = (float) Math.cos(yawRad);

        // Project world velocity onto player local axes to get forward/strafe
        // forward = dot(vel_xz, (-sinYaw, cosYaw))
        // strafe  = dot(vel_xz, (-cosYaw, -sinYaw))
        float fwd    = (float)(-sinYaw * vel.x + cosYaw * vel.z);
        float strafe = (float)(-cosYaw * vel.x - sinYaw * vel.z);

        // Clamp to [-1, 1]
        fwd    = Math.max(-1, Math.min(1, fwd * 8));
        strafe = Math.max(-1, Math.min(1, strafe * 8));

        state.applyWalkForce(fwd, strafe, player.getYaw(), player.isOnGround());
    }

    // ─────────────────────────────────────────────────────────────────
    // Jump packet handler
    // ─────────────────────────────────────────────────────────────────

    private void handleJumpPacket(ServerPlayerEntity player, JumpImpulsePacket pkt) {
        PlayerRagdollState state = PLAYER_STATES.get(player.getUuid());
        if (state == null) return;
        state.onJump(player);
        LOGGER.debug("[RagdolMod] Jump impulse applied for {}", player.getName().getString());
    }

    // ─────────────────────────────────────────────────────────────────
    // Sync packet builder
    // ─────────────────────────────────────────────────────────────────

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

    // ─────────────────────────────────────────────────────────────────
    // Public accessors
    // ─────────────────────────────────────────────────────────────────

    /** Retrieve a player's ragdoll state by UUID (may return null if not loaded). */
    public static PlayerRagdollState getState(UUID uuid) {
        return PLAYER_STATES.get(uuid);
    }
}
