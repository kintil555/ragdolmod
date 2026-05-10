package com.ragdolmod.physics;

import com.ragdolmod.config.RagdollConfig;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

/**
 * PlayerRagdollState
 *
 * Attached to each player entity to track their individual ragdoll
 * physics state. Stored on the server and mirrored on the client.
 *
 * Lifecycle:
 *   Created when a player first joins.
 *   Ticked once per game tick in ServerPlayerEntityMixin.
 *   Serialized via network packet for client rendering.
 */
public class PlayerRagdollState {

    // ── Core physics engine ────────────────────────────────────────────
    public final RagdollPhysicsEngine engine;

    // ── Previous-tick values for interpolation ─────────────────────────
    public float prevTiltAngle;
    public float prevSwayAngle;
    public float prevHeadLagAngle;
    public float prevCameraRoll;
    public float prevCameraPitchBump;

    // ── Jump state tracking ────────────────────────────────────────────
    /** Was the player on the ground last tick? Used to detect landing. */
    private boolean wasOnGround = true;
    /** Vertical velocity last tick (for landing impact calculation). */
    private double prevVelocityY = 0;
    /** Accumulated time (ticks) since last jump – cooldown. */
    private int jumpCooldown = 0;

    // ── Movement tracking ──────────────────────────────────────────────
    /** Previous yaw angle, used to calculate yawDelta for head lag. */
    private float prevYaw = 0;
    /** Last known horizontal input direction (normalized). */
    private float inputDirX = 0, inputDirZ = 0;

    // ── Config reference ───────────────────────────────────────────────
    private final RagdollConfig config;

    // ─────────────────────────────────────────────────────────────────
    public PlayerRagdollState(RagdollConfig config) {
        this.config = config;
        this.engine = config.createEngine();
    }

    // ─────────────────────────────────────────────────────────────────
    // Main tick
    // ─────────────────────────────────────────────────────────────────

    /**
     * Called each server tick for this player.
     *
     * @param player  the player entity
     */
    public void tick(PlayerEntity player) {
        if (!config.enabled) return;

        // ── Save prev-tick values for interpolation ──
        prevTiltAngle     = engine.tiltAngle;
        prevSwayAngle     = engine.swayAngle;
        prevHeadLagAngle  = engine.headLagAngle;
        prevCameraRoll    = engine.cameraRoll;
        prevCameraPitchBump = engine.cameraPitchBump;

        boolean onGround = player.isOnGround();
        Vec3d   vel      = player.getVelocity();

        // ── Landing detection ──────────────────────────────────────────
        if (onGround && !wasOnGround) {
            double impactSpeed = Math.abs(prevVelocityY);
            if (impactSpeed > config.landingStumbleThreshold) {
                float stumbleStr = (float)(impactSpeed * 1.8);
                engine.triggerStumble(Math.min(stumbleStr, 1.0f));
                // Horizontal momentum is preserved through landing
                // (do not zero out velocity.x/z here)
            }
        }

        // ── Yaw delta → head lag ──────────────────────────────────────
        float currentYaw = player.getYaw();
        float yawDelta   = currentYaw - prevYaw;
        // Wrap to [-180, 180]
        while (yawDelta >  180) yawDelta -= 360;
        while (yawDelta < -180) yawDelta += 360;
        engine.notifyYawDelta(yawDelta);
        prevYaw = currentYaw;

        // ── Tick the physics engine ────────────────────────────────────
        // NOTE: Do NOT overwrite engine.velocity from MC velocity here.
        // The engine owns XZ velocity. We only read MC velocity for landing
        // detection (above) and Y-axis (managed by Minecraft gravity).
        engine.tick(onGround, config);

        // ── Apply engine XZ velocity back to Minecraft entity ─────────
        // This is the key: override MC's movement with our physics engine.
        if (!player.isSpectator() && !player.hasVehicle()) {
            player.setVelocity(engine.velocity.x, vel.y, engine.velocity.z);
        }

        // ── Cooldown tick ─────────────────────────────────────────────
        if (jumpCooldown > 0) jumpCooldown--;

        // ── Update tracking fields ────────────────────────────────────
        wasOnGround  = onGround;
        prevVelocityY = vel.y;
    }

    // ─────────────────────────────────────────────────────────────────
    // Jump impulse
    // ─────────────────────────────────────────────────────────────────

    /**
     * Called when the player initiates a jump.
     * Applies a direction-preserving momentum boost.
     *
     * @param player  the player entity
     */
    public void onJump(PlayerEntity player) {
        if (!config.enabled) return;
        if (jumpCooldown > 0) return;
        if (!player.isOnGround()) return;

        Vec3d vel = player.getVelocity();

        // Compute horizontal input direction from current engine velocity
        float hSpeedX = engine.velocity.x;
        float hSpeedZ = engine.velocity.z;
        float hMag = (float) Math.sqrt(hSpeedX * hSpeedX + hSpeedZ * hSpeedZ);

        // Base vertical impulse (always applied)
        float verticalImpulse = config.jumpImpulse;

        // Horizontal momentum boost – makes repeated jump-walking fast
        if (hMag > 0.01f) {
            float boostX = (hSpeedX / hMag) * config.jumpMomentumBoost;
            float boostZ = (hSpeedZ / hMag) * config.jumpMomentumBoost;
            engine.applyImpulse(boostX, 0, boostZ);
        }

        // Vertical impulse (applied to Minecraft natively via setVelocity)
        // We add extra vertical kick here for snappier jump feel.
        double newVY = Math.max(vel.y, 0) + verticalImpulse * 0.08;
        player.setVelocity(player.getVelocity().x, newVY, player.getVelocity().z);

        // Tilt impulse forward (body leans into the jump)
        engine.applyTiltImpulse(0.15f);

        // Stumble slightly on takeoff (unstable ragdoll body)
        engine.triggerStumble(0.1f);

        jumpCooldown = 3;
    }

    // ─────────────────────────────────────────────────────────────────
    // Walk force injection
    // ─────────────────────────────────────────────────────────────────

    /**
     * Called each tick to apply the player's WASD input as a
     * very weak force (ragdolls can barely walk without momentum).
     *
     * @param forwardInput  -1..1 W/S axis
     * @param strafeInput   -1..1 A/D axis
     * @param yaw           player yaw (degrees)
     * @param onGround      whether the player is on the ground
     */
    public void applyWalkForce(float forwardInput, float strafeInput, float yaw, boolean onGround) {
        if (!config.enabled) return;

        // Convert yaw to radians and compute movement direction in world space
        double yawRad = Math.toRadians(yaw);
        float sinYaw = (float) Math.sin(yawRad);
        float cosYaw = (float) Math.cos(yawRad);

        // Input vector (normalized if diagonal)
        float inputMag = (float) Math.sqrt(forwardInput * forwardInput + strafeInput * strafeInput);
        if (inputMag > 1.0f) {
            forwardInput /= inputMag;
            strafeInput  /= inputMag;
        }

        // World-space direction
        // Minecraft: +Z = south, +X = east; forward = -sin(yaw), cos(yaw) in world XZ
        float worldX = -sinYaw * forwardInput - cosYaw * strafeInput;
        float worldZ =  cosYaw * forwardInput - sinYaw * strafeInput;

        // Walking force is tiny – ragdolls are weak
        float forceMag = config.walkSpeedMultiplier * engine.mass * 20.0f;
        if (!onGround) {
            forceMag *= config.airControlFactor;
        }

        engine.addForce(worldX * forceMag, 0, worldZ * forceMag);

        // Slight direction-change stumble
        float dot = worldX * inputDirX + worldZ * inputDirZ;
        if (dot < -0.5f && inputMag > 0.3f) {
            // Sharp reversal – apply angular impulse
            engine.applyTiltImpulse(0.06f);
        }

        inputDirX = (inputMag > 0.01f) ? worldX : 0;
        inputDirZ = (inputMag > 0.01f) ? worldZ : 0;
    }

    // ─────────────────────────────────────────────────────────────────
    // Interpolated getters (for smooth client rendering)
    // ─────────────────────────────────────────────────────────────────

    /** Smoothly interpolate tiltAngle by partial tick factor. */
    public float getTiltAngle(float tickDelta) {
        return lerp(prevTiltAngle, engine.tiltAngle, tickDelta);
    }

    public float getSwayAngle(float tickDelta) {
        return lerp(prevSwayAngle, engine.swayAngle, tickDelta);
    }

    public float getHeadLagAngle(float tickDelta) {
        return lerp(prevHeadLagAngle, engine.headLagAngle, tickDelta);
    }

    public float getCameraRoll(float tickDelta) {
        return lerp(prevCameraRoll, engine.cameraRoll, tickDelta);
    }

    public float getCameraPitchBump(float tickDelta) {
        return lerp(prevCameraPitchBump, engine.cameraPitchBump, tickDelta);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
