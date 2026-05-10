package com.ragdolmod.mixin.client;

import com.ragdolmod.client.ClientRagdollCache;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
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
 * Applies camera roll each frame by hooking into renderWorld.
 *
 * FIX: Changed from remap=false + intermediary "method_3188" to
 * remap=true + yarn name "renderWorld". The old approach silently
 * failed in MC 1.21.1 because the intermediary name had changed,
 * meaning the mixin never injected at all.
 */
@Environment(EnvType.CLIENT)
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Shadow @Final MinecraftClient client;

    /** Smoothed roll offset in radians. */
    private float ragdoll_smoothRoll  = 0f;
    private float ragdoll_smoothPitch = 0f;

    /**
     * Inject at HEAD of renderWorld using remap=true so Loom resolves
     * the yarn name to the correct intermediary/srg at build time.
     */
    @Inject(
        method = "renderWorld(Lnet/minecraft/client/render/RenderTickCounter;)V",
        at = @At("HEAD"),
        remap = true
    )
    private void onRenderWorldHead(RenderTickCounter tickCounter, CallbackInfo ci) {
        if (client.player == null) {
            ragdoll_smoothRoll  *= 0.85f;
            ragdoll_smoothPitch *= 0.85f;
            return;
        }

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

        ragdoll_smoothRoll  += (targetRoll  - ragdoll_smoothRoll)  * 0.2f;
        ragdoll_smoothPitch += (targetPitch - ragdoll_smoothPitch) * 0.2f;
        ragdoll_smoothRoll  *= 0.92f;
        ragdoll_smoothPitch *= 0.90f;

        // Apply roll to camera rotation quaternion directly.
        // rotateLocalZ tilts the view around the forward axis (roll).
        if (Math.abs(ragdoll_smoothRoll) > 0.001f) {
            var camera = client.gameRenderer.getCamera();
            if (camera != null && camera.isReady()) {
                Quaternionf rot = camera.getRotation();
                rot.rotateLocalZ(ragdoll_smoothRoll);
            }
        }
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
