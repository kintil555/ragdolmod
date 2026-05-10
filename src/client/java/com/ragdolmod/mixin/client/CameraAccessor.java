package com.ragdolmod.mixin.client;

import com.ragdolmod.client.ClientRagdollCache;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/**
 * CameraAccessor
 *
 * Mixin target: {@link net.minecraft.client.render.Camera}
 *
 * Intercepts {@code getRoll()} to add the ragdoll-derived roll angle
 * on top of whatever vanilla returns (e.g., nausea effect roll).
 *
 * Also intercepts {@code getPitch()} to add the landing pitch bump.
 */
@Environment(EnvType.CLIENT)
@Mixin(Camera.class)
public abstract class CameraAccessor {

    /** Smoothed roll offset (degrees) accumulated this frame. */
    private float ragdolmod_rollOffset  = 0f;
    private float ragdolmod_pitchOffset = 0f;

    /**
     * Intercept getRoll() to add ragdoll camera roll.
     * Vanilla getRoll() returns the nausea/portal effect roll in degrees.
     * We add our physics-based roll on top.
     */
    @Inject(method = "getRoll", at = @At("RETURN"), cancellable = true)
    private void onGetRoll(CallbackInfoReturnable<Float> cir) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        UUID uuid = mc.player.getUuid();
        var state = ClientRagdollCache.get(uuid);
        if (state == null) return;
        if (!com.ragdolmod.RagdolMod.CONFIG.enabled) return;

        float ragdollRoll = (float) Math.toDegrees(state.getCameraRoll())
                            * com.ragdolmod.RagdolMod.CONFIG.cameraRollSensitivity;

        // Smooth the roll offset
        ragdolmod_rollOffset += (ragdollRoll - ragdolmod_rollOffset) * 0.25f;

        // Return vanilla roll + our roll
        cir.setReturnValue(cir.getReturnValue() + ragdolmod_rollOffset);
    }

    /**
     * Intercept getPitch() to add the landing pitch bump.
     */
    @Inject(method = "getPitch", at = @At("RETURN"), cancellable = true)
    private void onGetPitch(CallbackInfoReturnable<Float> cir) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        UUID uuid = mc.player.getUuid();
        var state = ClientRagdollCache.get(uuid);
        if (state == null) return;
        if (!com.ragdolmod.RagdolMod.CONFIG.enabled) return;

        float pitchBump = (float) Math.toDegrees(state.getCameraPitchBump());

        // Smooth pitch offset
        ragdolmod_pitchOffset += (pitchBump - ragdolmod_pitchOffset) * 0.20f;
        ragdolmod_pitchOffset *= 0.88f;

        cir.setReturnValue(cir.getReturnValue() + ragdolmod_pitchOffset);
    }
}
