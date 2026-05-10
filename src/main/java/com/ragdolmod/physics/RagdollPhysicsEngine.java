package com.ragdolmod.physics;

/**
 * RagdollPhysicsEngine
 *
 * Core physics simulation for the ragdoll movement system.
 * Uses Verlet integration combined with spring-damper forces
 * to produce realistic, unstable, floppy movement.
 *
 * Physics equations implemented:
 *   Spring force:    F = -k*x - b*v
 *   Momentum:        p = m*v
 *   Verlet step:     x(t+dt) = 2x(t) - x(t-dt) + a(t)*dt^2
 *   Drag:            F_drag = -c_d * v * |v|
 *   Inertia torque:  τ = I * α
 */
public class RagdollPhysicsEngine {

    // ── Spring constants ───────────────────────────────────────────────────
    /** Spring stiffness for body stabilisation (k). Higher = stiffer. */
    public float springStiffness;
    /** Damping coefficient (b). Prevents perpetual oscillation. */
    public float dampingCoefficient;
    /** Drag coefficient applied to horizontal velocity. */
    public float dragCoefficient;

    // ── Body state ─────────────────────────────────────────────────────────
    /** Current world velocity of the ragdoll body (m/tick). */
    public Vec3f velocity;
    /** Accumulated external forces applied this tick. */
    private Vec3f accumulatedForce;

    /** Body tilt angle (radians) relative to vertical axis. */
    public float tiltAngle;
    /** Angular velocity of body tilt (rad/tick). */
    public float tiltVelocity;
    /** Angular velocity of body yaw (sway left-right). */
    public float swayVelocity;
    /** Current sway offset angle (radians). */
    public float swayAngle;

    /** Head lag behind body yaw (radians). */
    public float headLagAngle;
    /** Angular velocity of head lag. */
    public float headLagVelocity;

    /** Camera roll due to momentum (radians). */
    public float cameraRoll;
    /** Camera pitch perturbation from landing/stumble. */
    public float cameraPitchBump;

    /** Body mass (arbitrary unit, affects momentum feel). */
    public float mass;

    // ── Stumble state ──────────────────────────────────────────────────────
    /** Stumble intensity (0..1). Decays each tick. */
    public float stumbleIntensity;
    /** Pseudo-random phase for per-axis stumble wobble. */
    private float stumblePhaseX;
    private float stumblePhaseZ;

    // ── Timing ────────────────────────────────────────────────────────────
    private static final float DT = 0.05f;   // 1 tick = 50ms = 0.05s
    private static final float DT_SQ = DT * DT;

    // ── Constructor ───────────────────────────────────────────────────────
    public RagdollPhysicsEngine(RagdollConfig cfg) {
        this.springStiffness    = cfg.springStiffness;
        this.dampingCoefficient = cfg.dampingCoefficient;
        this.dragCoefficient    = cfg.dragCoefficient;
        this.mass               = cfg.bodyMass;

        this.velocity         = new Vec3f(0, 0, 0);
        this.accumulatedForce = new Vec3f(0, 0, 0);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Apply an impulse directly to the body (e.g., jump launch).
     * Impulse = change in momentum → Δv = impulse / mass
     *
     * @param x X component of impulse vector
     * @param y Y component (vertical)
     * @param z Z component
     */
    public void applyImpulse(float x, float y, float z) {
        float invMass = 1.0f / mass;
        velocity.x += x * invMass;
        velocity.y += y * invMass;
        velocity.z += z * invMass;
    }

    /**
     * Accumulate a continuous force for this tick.
     * Force is consumed and reset at the end of {@link #tick}.
     *
     * @param x X force component
     * @param y Y force component
     * @param z Z force component
     */
    public void addForce(float x, float y, float z) {
        accumulatedForce.x += x;
        accumulatedForce.y += y;
        accumulatedForce.z += z;
    }

    /**
     * Trigger a stumble event (e.g., hard landing).
     *
     * @param intensity 0..1 severity of stumble
     */
    public void triggerStumble(float intensity) {
        stumbleIntensity = Math.min(1.0f, stumbleIntensity + intensity);
        stumblePhaseX = (float)(Math.random() * Math.PI * 2);
        stumblePhaseZ = (float)(Math.random() * Math.PI * 2);
        cameraPitchBump += intensity * 0.12f;   // slight forward pitch dip
    }

    /**
     * Apply a tilt impulse to the body (angular impulse).
     * Used when the player changes direction suddenly.
     *
     * @param angleDelta radians of angular impulse
     */
    public void applyTiltImpulse(float angleDelta) {
        // Moment of inertia I ≈ mass * radius^2; radius ≈ 0.3 for a player
        float momentOfInertia = mass * 0.09f;
        tiltVelocity += angleDelta / momentOfInertia;
    }

    /**
     * Main physics tick. Call once per game tick.
     *
     * @param onGround whether the player is on the ground
     * @param cfg      live config reference (allows hot-reloading)
     */
    public void tick(boolean onGround, RagdollConfig cfg) {
        // Update cached spring/damping params from config
        springStiffness    = cfg.springStiffness;
        dampingCoefficient = cfg.dampingCoefficient;
        dragCoefficient    = cfg.dragCoefficient;

        integrateVelocity(onGround);
        integrateTilt(onGround, cfg);
        integrateSway(cfg);
        integrateHeadLag();
        integrateCamera();
        decayStumble(cfg);

        // Reset accumulated force for next tick
        accumulatedForce.set(0, 0, 0);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Internal integration steps
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Integrate linear velocity using accumulated forces + drag.
     *
     * Newton second law applied per tick:
     *   a = F_total / m
     *   v(t+dt) = v(t) + a*dt
     *
     * Drag is quadratic:
     *   F_drag = -c_d * v * |v|
     */
    private void integrateVelocity(boolean onGround) {
        // Compute drag on horizontal plane only
        float speedSq = velocity.x * velocity.x + velocity.z * velocity.z;
        float dragMag = dragCoefficient * speedSq;

        if (speedSq > 1e-6f) {
            float speed = (float) Math.sqrt(speedSq);
            float dragX = -(velocity.x / speed) * dragMag;
            float dragZ = -(velocity.z / speed) * dragMag;
            accumulatedForce.x += dragX;
            accumulatedForce.z += dragZ;
        }

        // Ground friction: linear damping when grounded
        if (onGround) {
            float groundFriction = 0.82f;  // reduce horizontal vel by this factor
            velocity.x *= groundFriction;
            velocity.z *= groundFriction;
        }

        // a = F / m
        float invMass = 1.0f / mass;
        velocity.x += accumulatedForce.x * invMass * DT;
        velocity.z += accumulatedForce.z * invMass * DT;
        // Y-axis velocity is managed by Minecraft's own gravity; we only track XZ here.

        // Clamp to sane max speed (prevents physics explosions)
        float maxSpeed = 2.0f;
        float spd = (float) Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        if (spd > maxSpeed) {
            float scale = maxSpeed / spd;
            velocity.x *= scale;
            velocity.z *= scale;
        }
    }

    /**
     * Spring-damper simulation for body tilt.
     *
     * The body tries to return to upright (tiltAngle = 0) via:
     *   F_spring = -k * tiltAngle  (restoring force)
     *   F_damp   = -b * tiltVelocity (damping)
     *   α = (F_spring + F_damp) / I
     *   I ≈ mass * 0.15 (approximate torso moment of inertia)
     */
    private void integrateTilt(boolean onGround, RagdollConfig cfg) {
        float I = mass * 0.15f;  // moment of inertia

        // Forward tilt driven by XZ speed
        float horizontalSpeed = (float) Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
        float targetTilt = horizontalSpeed * 0.35f * cfg.wobbleIntensity;
        // Clamp target tilt
        targetTilt = Math.min(targetTilt, 0.5f);

        // Spring force towards target tilt
        float tiltDisp   = tiltAngle - targetTilt;
        float springForce = -springStiffness * tiltDisp;
        float dampForce   = -dampingCoefficient * tiltVelocity;

        float alpha = (springForce + dampForce) / I;
        tiltVelocity += alpha * DT;
        tiltAngle    += tiltVelocity * DT;

        // Clamp to physical limits (can't tilt past ±50°)
        float maxTilt = 0.87f; // ~50 degrees in radians
        if (Math.abs(tiltAngle) > maxTilt) {
            tiltAngle = Math.signum(tiltAngle) * maxTilt;
            tiltVelocity *= -0.3f;  // bounce slightly
        }
    }

    /**
     * Lateral sway oscillation - wobble while moving.
     * Simulates the unsteady, floppy sideways movement.
     *
     * F = -k*sway - b*swayVelocity + perturbation
     */
    private void integrateSway(RagdollConfig cfg) {
        float horizontalSpeed = (float) Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);

        // Perturbation force proportional to movement speed (simulates gait instability)
        float perturbation = 0;
        if (horizontalSpeed > 0.01f) {
            // Sway frequency grows with speed, producing faster wobble at higher velocity
            float swayFreq = 3.0f + horizontalSpeed * 2.0f;
            perturbation = (float)(Math.sin(System.currentTimeMillis() * 0.001 * swayFreq))
                           * horizontalSpeed * 0.15f * cfg.wobbleIntensity;
        }

        float I = mass * 0.1f;
        float springForce = -springStiffness * 0.7f * swayAngle;
        float dampForce   = -dampingCoefficient * swayVelocity;

        float alpha = (springForce + dampForce + perturbation) / I;
        swayVelocity += alpha * DT;
        swayAngle    += swayVelocity * DT;

        // Clamp sway
        float maxSway = 0.35f;
        if (Math.abs(swayAngle) > maxSway) {
            swayAngle = Math.signum(swayAngle) * maxSway;
            swayVelocity *= -0.4f;
        }
    }

    /**
     * Head lag behind body yaw rotation.
     * The head has its own rotational inertia and lags behind
     * body yaw changes - spring pulls it back to centre.
     */
    private void integrateHeadLag() {
        float I = mass * 0.03f;  // Head has small moment of inertia
        float springF = -springStiffness * 0.5f * headLagAngle;
        float dampF   = -dampingCoefficient * headLagVelocity;

        float alpha = (springF + dampF) / I;
        headLagVelocity += alpha * DT;
        headLagAngle    += headLagVelocity * DT;

        // Clamp head lag to ±30°
        float maxLag = 0.52f;
        if (Math.abs(headLagAngle) > maxLag) {
            headLagAngle = Math.signum(headLagAngle) * maxLag;
            headLagVelocity *= -0.2f;
        }
    }

    /**
     * Camera roll and pitch bump integration.
     * Camera roll mirrors swayAngle with damping.
     * Camera pitch bump decays exponentially.
     */
    private void integrateCamera() {
        // Camera roll follows sway with some lag
        float rollTarget = swayAngle * 0.6f;
        cameraRoll += (rollTarget - cameraRoll) * 0.15f;

        // Pitch bump decays exponentially (τ ≈ 0.3s → factor ≈ e^(-dt/τ) per tick)
        cameraPitchBump *= 0.85f;
        if (Math.abs(cameraPitchBump) < 0.001f) cameraPitchBump = 0;
    }

    /**
     * Decay stumble intensity over time.
     * Stumble manifests as random angular jitter applied to tilt/sway.
     */
    private void decayStumble(RagdollConfig cfg) {
        if (stumbleIntensity <= 0) return;

        // Apply stumble as random impulse to tilt and sway
        float jitterX = (float)(Math.sin(stumblePhaseX) * stumbleIntensity * 0.08f * cfg.wobbleIntensity);
        float jitterZ = (float)(Math.sin(stumblePhaseZ) * stumbleIntensity * 0.08f * cfg.wobbleIntensity);
        tiltVelocity += jitterX;
        swayVelocity += jitterZ;

        // Phase advances faster when intensity is high
        stumblePhaseX += 0.8f + stumbleIntensity * 0.5f;
        stumblePhaseZ += 0.7f + stumbleIntensity * 0.6f;

        // Decay stumble intensity
        stumbleIntensity *= (1.0f - cfg.recoverySpeed * 0.05f);
        if (stumbleIntensity < 0.001f) stumbleIntensity = 0;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Utility: notify head of yaw change (for lag calculation)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Register a yaw delta so the head can lag behind.
     * Call each tick with (newYaw - prevYaw).
     *
     * @param yawDelta change in body yaw (degrees)
     */
    public void notifyYawDelta(float yawDelta) {
        // Convert degrees to radians
        float rad = yawDelta * (float)(Math.PI / 180.0);
        // Apply as angular impulse to head (opposite sign → lag behind)
        headLagVelocity -= rad * 0.6f;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Inline vector helper
    // ─────────────────────────────────────────────────────────────────────

    public static final class Vec3f {
        public float x, y, z;

        public Vec3f(float x, float y, float z) { this.x = x; this.y = y; this.z = z; }

        public void set(float x, float y, float z) { this.x = x; this.y = y; this.z = z; }

        public float length() {
            return (float) Math.sqrt(x * x + y * y + z * z);
        }

        @Override
        public String toString() {
            return String.format("Vec3f(%.4f, %.4f, %.4f)", x, y, z);
        }
    }
}
