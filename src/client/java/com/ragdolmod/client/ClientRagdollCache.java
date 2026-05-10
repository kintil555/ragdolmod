package com.ragdolmod.client;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ClientRagdollCache
 *
 * Stores the most recently received ragdoll sync data for each player UUID.
 * Accessed by rendering mixins to apply visual ragdoll effects.
 *
 * Data arrives via {@link com.ragdolmod.network.RagdollSyncPacket} at ~25 Hz.
 * The renderer interpolates between the last two known states for smoothness.
 */
public class ClientRagdollCache {

    /** Immutable snapshot of one player's ragdoll state at a point in time. */
    public record Snapshot(
            float tiltAngle,
            float swayAngle,
            float headLagAngle,
            float cameraRoll,
            float cameraPitchBump,
            float velocityX,
            float velocityZ,
            float stumbleIntensity,
            long  receivedTime         // System.nanoTime() when packet arrived
    ) {}

    /** Previous snapshot (for interpolation). */
    public record InterpolatedState(Snapshot prev, Snapshot current) {
        /** Linear interpolate between prev and current based on time elapsed. */
        public float getTiltAngle() {
            return lerp(prev.tiltAngle(), current.tiltAngle(), alpha());
        }
        public float getSwayAngle() {
            return lerp(prev.swayAngle(), current.swayAngle(), alpha());
        }
        public float getHeadLagAngle() {
            return lerp(prev.headLagAngle(), current.headLagAngle(), alpha());
        }
        public float getCameraRoll() {
            return lerp(prev.cameraRoll(), current.cameraRoll(), alpha());
        }
        public float getCameraPitchBump() {
            return lerp(prev.cameraPitchBump(), current.cameraPitchBump(), alpha());
        }

        private float alpha() {
            if (prev == current) return 1.0f;
            long interval = current.receivedTime() - prev.receivedTime();
            if (interval <= 0) return 1.0f;
            long elapsed = System.nanoTime() - current.receivedTime();
            return Math.min(1.0f, (float) elapsed / interval);
        }

        private static float lerp(float a, float b, float t) {
            return a + (b - a) * t;
        }
    }

    // ─────────────────────────────────────────────────────────────────
    private static final Map<UUID, Snapshot> PREV    = new ConcurrentHashMap<>();
    private static final Map<UUID, Snapshot> CURRENT = new ConcurrentHashMap<>();

    /** Update state when a new sync packet arrives. */
    public static void update(UUID uuid,
                               float tilt, float sway, float headLag,
                               float camRoll, float camPitch,
                               float vx, float vz, float stumble) {
        Snapshot snap = new Snapshot(tilt, sway, headLag, camRoll, camPitch,
                                     vx, vz, stumble, System.nanoTime());
        Snapshot existing = CURRENT.get(uuid);
        if (existing != null) {
            PREV.put(uuid, existing);
        } else {
            PREV.put(uuid, snap);
        }
        CURRENT.put(uuid, snap);
    }

    /**
     * Retrieve interpolated state for a player.
     * Returns null if no data has been received yet.
     */
    public static InterpolatedState get(UUID uuid) {
        Snapshot cur  = CURRENT.get(uuid);
        if (cur == null) return null;
        Snapshot prev = PREV.getOrDefault(uuid, cur);
        return new InterpolatedState(prev, cur);
    }

    /** Remove state when player disconnects / leaves render range. */
    public static void remove(UUID uuid) {
        CURRENT.remove(uuid);
        PREV.remove(uuid);
    }
}
