package com.ragdolmod.mixin.client;

import com.ragdolmod.client.ClientRagdollCache;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
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
 * Injects into renderWorld to pre-compute smoothed camera roll/pitch
 * offsets each frame for ragdoll physics.
 *
 * Uses intermediary method selector (method_3188) with remap=false
 * to avoid refmap resolution issues on MC 1.21.1.
 */
@Environment(EnvType.CLIENT)
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Shadow @Final MinecraftClient client;

    private float ragdoll_smoothRoll  = 0f;
    private float ragdoll_smoothPitch = 0f;

    @Inject(
        method = "method_3188",
        at = @At("HEAD"),
        remap = false
    )
    private void onRenderWorldHead(RenderTickCounter tickCounter, CallbackInfo ci) {
        if (client.player == null) return;

        UUID uuid = client.player.getUuid();
        var state = ClientRagdollCache.get(uuid);
        if (state == null) return;

        float targetRoll  = (float) Math.toDegrees(state.getCameraRoll())
                            * getRollSensitivity();
        float targetPitch = (float) Math.toDegrees(state.getCameraPitchBump());

        float lerpRate = 0.2f;
        ragdoll_smoothRoll  += (targetRoll  - ragdoll_smoothRoll)  * lerpRate;
        ragdoll_smoothPitch += (targetPitch - ragdoll_smoothPitch) * lerpRate;

        ragdoll_smoothRoll  *= 0.92f;
        ragdoll_smoothPitch *= 0.90f;
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
