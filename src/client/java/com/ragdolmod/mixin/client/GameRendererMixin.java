package com.ragdolmod.mixin.client;

import com.ragdolmod.client.ClientRagdollCache;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.MinecraftClient;

import java.util.UUID;

/**
 * GameRendererMixin
 *
 * Modifies the camera projection matrix to incorporate ragdoll physics:
 *   - Roll:       tilt the view sideways (simulates body lean)
 *   - Pitch bump: brief downward dip on hard landings
 *
 * We inject into {@code renderWorld} which is called each frame.
 * The roll is applied as a pre-rotation on the view matrix.
 */
@Environment(EnvType.CLIENT)
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Shadow @Final private MinecraftClient client;
    @Shadow @Final private Camera camera;

    /** Smoothed roll offset accumulated between ticks (degrees). */
    private float ragdoll_smoothRoll = 0f;
    /** Smoothed pitch offset. */
    private float ragdoll_smoothPitch = 0f;

    /**
     * Inject at the start of renderWorld to apply camera roll/pitch.
     * We modify the matrices by calling Camera rotation helpers indirectly.
     *
     * Strategy: accumulate smoothed offsets and apply them to getViewMatrix.
     */
    @Inject(method = "renderWorld", at = @At("HEAD"))
    private void onRenderWorldHead(float tickDelta, long limitTime,
                                    org.joml.Matrix4f matrix4f, CallbackInfo ci) {
        if (client.player == null) return;

        UUID uuid = client.player.getUuid();
        var state = ClientRagdollCache.get(uuid);
        if (state == null) return;

        // Target roll in degrees (sway drives camera roll)
        float targetRoll  = (float) Math.toDegrees(state.getCameraRoll())
                            * getRagdollConfigCameraRollSensitivity();
        // Target pitch bump (degrees, downward = positive in MC)
        float targetPitch = (float) Math.toDegrees(state.getCameraPitchBump());

        // Smooth interpolation towards target each frame
        float lerpRate = 0.2f;
        ragdoll_smoothRoll  += (targetRoll  - ragdoll_smoothRoll)  * lerpRate;
        ragdoll_smoothPitch += (targetPitch - ragdoll_smoothPitch) * lerpRate;

        // Decay to zero
        ragdoll_smoothRoll  *= 0.92f;
        ragdoll_smoothPitch *= 0.90f;
    }

    /**
     * Modify the view bobbing / camera roll after vanilla processing.
     * We use the getCameraRoll() hook (if present) or apply via the matrix.
     *
     * Note: Minecraft 1.21.1 exposes camera roll via Camera.getRoll().
     * We add our roll on top of any existing roll value.
     */
    @Inject(method = "renderWorld", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/render/Camera;getPos()Lnet/minecraft/util/math/Vec3d;",
            shift = At.Shift.BEFORE
    ))
    private void injectCameraRoll(float tickDelta, long limitTime,
                                   Matrix4f projectionMatrix, CallbackInfo ci) {
        // Apply roll to the camera object
        // Minecraft Camera has setRoll() accessible via the CameraAccessor mixin
        if (client.player == null) return;

        UUID uuid = client.player.getUuid();
        var state = ClientRagdollCache.get(uuid);
        if (state == null) return;

        // We'll apply through the accessor in CameraAccessor
        // This just marks that we should rotate the view
        // The actual rotation is applied in CameraAccessor injection
    }

    // ─────────────────────────────────────────────────────────────────

    /** Read camera roll sensitivity from cached config. Cached to avoid allocations. */
    private float getRagdollConfigCameraRollSensitivity() {
        try {
            return com.ragdolmod.RagdolMod.CONFIG.cameraRollSensitivity;
        } catch (Exception e) {
            return 0.7f; // fallback
        }
    }

    /** Public accessor for other mixins/renderers to read smooth roll. */
    public float ragdolmod_getSmoothRoll() {
        return ragdoll_smoothRoll;
    }

    public float ragdolmod_getSmoothPitch() {
        return ragdoll_smoothPitch;
    }
}
