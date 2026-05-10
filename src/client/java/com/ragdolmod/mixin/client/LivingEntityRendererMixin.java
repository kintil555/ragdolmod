package com.ragdolmod.mixin.client;

import com.ragdolmod.client.ClientRagdollCache;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * LivingEntityRendererMixin
 *
 * Injects into setupTransforms (method_4058) to apply ragdoll physics
 * offsets to the MatrixStack via pure matrix math — no bone rotation,
 * no model API, just Verlet-integrated angles from the physics engine.
 *
 * Transforms applied (all in radians from physics engine):
 *   1. Translate pivot up to centre-of-mass height
 *   2. Rotate Z by swayAngle   → body tips left/right (spring oscillation)
 *   3. Rotate X by tiltAngle   → body pitches forward/back (momentum lean)
 *   4. Translate pivot back down to feet
 *   5. Drop CoM by (1 - cos(sway)) * comHeight → body sinks when tipping
 *
 * Confirmed intermediary from yarn 1.21.1+build.3:
 *   setupTransforms → method_4058
 *   Descriptor: (Lnet/minecraft/class_1309;Lnet/minecraft/class_4587;FFFF)V
 */
@Environment(EnvType.CLIENT)
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {

    @Inject(
        method = "method_4058(Lnet/minecraft/class_1309;Lnet/minecraft/class_4587;FFFF)V",
        at = @At("RETURN"),
        remap = false
    )
    private void onSetupTransforms(
            LivingEntity entity,
            MatrixStack matrices,
            float animationProgress,
            float bodyYaw,
            float tickDelta,
            float scale,
            CallbackInfo ci
    ) {
        if (!(entity instanceof AbstractClientPlayerEntity player)) return;
        if (!com.ragdolmod.RagdolMod.CONFIG.enabled) return;

        UUID uuid = player.getUuid();
        ClientRagdollCache.InterpolatedState state = ClientRagdollCache.get(uuid);
        if (state == null) return;

        float tilt = state.getTiltAngle();
        float sway = state.getSwayAngle();

        if (Math.abs(tilt) < 0.001f && Math.abs(sway) < 0.001f) return;

        // Pivot point: roughly mid-torso so tilting looks natural
        final float COM = 0.9f;

        // Lift to CoM
        matrices.translate(0.0, COM, 0.0);

        // Sway: rotate around Z axis (body tips left/right)
        if (Math.abs(sway) > 0.001f) {
            float h = sway * 0.5f;
            matrices.multiply(new Quaternionf(0f, 0f, (float)Math.sin(h), (float)Math.cos(h)));
        }

        // Tilt: rotate around X axis (body pitches forward/back)
        if (Math.abs(tilt) > 0.001f) {
            float h = tilt * 0.5f;
            matrices.multiply(new Quaternionf((float)Math.sin(h), 0f, 0f, (float)Math.cos(h)));
        }

        // Lower back to feet
        matrices.translate(0.0, -COM, 0.0);

        // CoM vertical drop from lateral sway: Δy = COM*(1 - cos(sway))
        // Body sinks slightly when tipping — gives ragdoll "weight" feel
        double yDrop = COM * (1.0 - Math.cos(sway));
        if (yDrop > 0.001) {
            matrices.translate(0.0, -yDrop, 0.0);
        }
    }
}
