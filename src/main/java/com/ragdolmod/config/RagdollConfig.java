package com.ragdolmod.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.ragdolmod.physics.RagdollPhysicsEngine;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * RagdollConfig
 *
 * Holds all user-configurable parameters for the ragdoll physics system.
 * Values are persisted to config/ragdolmod.json via Gson.
 *
 * Hot-reload: The physics engine reads from this object each tick, so
 * changing values at runtime takes effect immediately.
 */
public class RagdollConfig {

    // ── Physics parameters ─────────────────────────────────────────────

    /**
     * Spring stiffness (k) for body stabilisation.
     * Range: 1.0 (very floppy) – 20.0 (stiff)
     * Default: 4.5
     */
    public float springStiffness = 4.5f;

    /**
     * Damping coefficient (b) for all spring oscillators.
     * Range: 0.1 (very bouncy) – 5.0 (critically damped)
     * Default: 1.8
     */
    public float dampingCoefficient = 1.8f;

    /**
     * Quadratic drag on horizontal velocity.
     * Range: 0.0 – 2.0
     * Default: 0.3
     */
    public float dragCoefficient = 0.3f;

    /**
     * Body mass (kg, arbitrary units).
     * Higher mass = more momentum, sluggish direction changes.
     * Range: 0.5 – 5.0
     * Default: 1.8
     */
    public float bodyMass = 1.8f;

    // ── Movement parameters ────────────────────────────────────────────

    /**
     * Maximum WASD walking speed multiplier.
     * 1.0 = vanilla speed. Ragdoll effect reduces this heavily.
     * Default: 0.08 (very slow, floppy walk)
     */
    public float walkSpeedMultiplier = 0.08f;

    /**
     * Jump impulse magnitude when jumping while moving.
     * Range: 0.5 – 3.0
     * Default: 1.4
     */
    public float jumpImpulse = 1.4f;

    /**
     * Horizontal momentum boost applied during a jump.
     * This is what makes jump-based locomotion feel fast.
     * Range: 0.5 – 3.0
     * Default: 1.6
     */
    public float jumpMomentumBoost = 1.6f;

    /**
     * Air control factor (0 = none, 1 = full).
     * Lower value preserves inertia in the air.
     * Default: 0.15
     */
    public float airControlFactor = 0.15f;

    // ── Visual/animation parameters ────────────────────────────────────

    /**
     * Intensity of the wobble/sway effect.
     * Range: 0.0 (disabled) – 2.0 (extreme)
     * Default: 1.0
     */
    public float wobbleIntensity = 1.0f;

    /**
     * How fast the body recovers from stumble events.
     * Range: 0.1 (very slow) – 2.0 (instant)
     * Default: 0.6
     */
    public float recoverySpeed = 0.6f;

    /**
     * Camera roll sensitivity to sway.
     * Default: 0.7
     */
    public float cameraRollSensitivity = 0.7f;

    /**
     * Landing stumble threshold (vertical fall speed in m/tick).
     * Default: 0.3
     */
    public float landingStumbleThreshold = 0.3f;

    /**
     * Enable/disable the ragdoll mod effect entirely.
     * Default: true
     */
    public boolean enabled = true;

    // ── IO helpers ─────────────────────────────────────────────────────

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "ragdolmod.json";

    /**
     * Load config from disk, creating defaults if absent.
     */
    public static RagdollConfig load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);

        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                RagdollConfig cfg = GSON.fromJson(reader, RagdollConfig.class);
                if (cfg != null) return cfg;
            } catch (IOException | com.google.gson.JsonParseException e) {
                System.err.println("[RagdolMod] Failed to read config, using defaults: " + e.getMessage());
            }
        }

        // Write default config
        RagdollConfig defaults = new RagdollConfig();
        defaults.save();
        return defaults;
    }

    /** Persist config to disk. */
    public void save() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
        try (Writer writer = Files.newBufferedWriter(path)) {
            GSON.toJson(this, writer);
        } catch (IOException e) {
            System.err.println("[RagdolMod] Failed to save config: " + e.getMessage());
        }
    }

    /**
     * Produce a {@link RagdollPhysicsEngine} pre-seeded with this config.
     */
    public RagdollPhysicsEngine createEngine() {
        return new RagdollPhysicsEngine(this);
    }
}
