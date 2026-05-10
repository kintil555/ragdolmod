package com.ragdolmod.mixin.client;

import com.ragdolmod.client.ClientRagdollCache;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.MinecraftClient;

import java.util.UUID;

/**
 * GameRendererMixin
 *
 * In MC 1.21.1, Camera does not have a getRoll() method.
 * Camera roll is instead applied by rotating the camera's rotation quaternion
 * around the Z axis (view/forward axis) after the camera update.
 */
@Environment(EnvType.CLIENT)
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Shadow @Final MinecraftClient client;

    /** Smoothed roll offset in radians. */
    private float ragdoll_smoothRoll  = 0f;
    private float ragdoll_smoothPitch = 0f;

    /**
     * Inject at the start of renderWorld to pre-compute smooth roll/pitch each frame.
     * Uses intermediary name (method_3188) with remap=false for MC 1.21.1 compatibility.
     */
    @Inject(
        method = "method_3188",
        at = @At("HEAD"),
        remap = false
    )
    private void onRenderWorldHead(RenderTickCounter tickCounter, CallbackInfo ci) {
        if (client.player == null) return;

        UUID uuid = client.player.getUuid();
        var state = ClientRagdollCache.get(uuid);
        if (state == null) {
            ragdoll_smoothRoll  *= 0.85f;
            ragdoll_smoothPitch *= 0.85f;
            return;
        }

        if (!com.ragdolmod.RagdolMod.CONFIG.enabled) return;

        float targetRoll  = state.getCameraRoll()  * getRollSensitivity();
        float targetPitch = state.getCameraPitchBump();

        float lerpRate = 0.2f;
        ragdoll_smoothRoll  += (targetRoll  - ragdoll_smoothRoll)  * lerpRate;
        ragdoll_smoothPitch += (targetPitch - ragdoll_smoothPitch) * lerpRate;

        ragdoll_smoothRoll  *= 0.92f;
        ragdoll_smoothPitch *= 0.90f;
    }

    /**
     * Inject after Camera#update() to apply roll via the rotation quaternion.
     * In 1.21.1 there is no getRoll() on Camera, so we directly mutate
     * the rotation quaternion returned by Camera#getRotation().
     */
    @Inject(
        method = "method_3188",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/render/Camera;update(Lnet/minecraft/world/BlockView;Lnet/minecraft/entity/Entity;ZZF)V",
            shift = At.Shift.AFTER,
            remap = true
        ),
        remap = false
    )
    private void onAfterCameraUpdate(RenderTickCounter tickCounter, CallbackInfo ci) {
        if (Math.abs(ragdoll_smoothRoll) < 0.001f) return;
        if (client.player == null) return;

        Camera camera = client.gameRenderer.getCamera();
        if (!camera.isReady()) return;

        // Apply roll by rotating the camera quaternion around the local Z (forward) axis.
        Quaternionf rotation = camera.getRotation();
        rotation.rotateLocalZ(ragdoll_smoothRoll);
    }

    private float getRollSensitivity() {
        try {
            return com.ragdolmod.RagdolMod.CONFIG.cameraRollSensitivity;
        } catch (Exception e) {
            return 0.7f;
        }
    }

    public float ragdolmod_getSmoothRoll()  { return ragdoll_smoothRoll;  }
    public float ragdolmod_getSmoothPitch() { return ragdoll_smoothPitch; }
}
