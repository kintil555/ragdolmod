package com.ragdolmod.mixin.client;

import com.ragdolmod.client.ClientRagdollCache;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/**
 * CameraAccessor
 *
 * Mixin target: {@link Camera}
 *
 * Intercepts getPitch() (intermediary: method_19329) to add ragdoll pitch bump.
 *
 * Uses remap=false + intermediary method name to avoid refmap resolution issues
 * when the mod jar is built without a proper refmap.
 *
 * getRoll() does NOT exist in MC 1.21.1 Camera — roll is handled in GameRendererMixin.
 */
@Environment(EnvType.CLIENT)
@Mixin(Camera.class)
public abstract class CameraAccessor {

    private float ragdolmod_pitchOffset = 0f;

    /**
     * Intercept getPitch() to add the landing pitch bump.
     * method_19329 = getPitch() in intermediary for MC 1.21.1
     */
    @Inject(method = "method_19329", at = @At("RETURN"), cancellable = true, remap = false)
    private void onGetPitch(CallbackInfoReturnable<Float> cir) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        UUID uuid = mc.player.getUuid();
        var state = ClientRagdollCache.get(uuid);
        if (state == null) return;
        if (!com.ragdolmod.RagdolMod.CONFIG.enabled) return;

        float pitchBump = (float) Math.toDegrees(state.getCameraPitchBump());

        ragdolmod_pitchOffset += (pitchBump - ragdolmod_pitchOffset) * 0.20f;
        ragdolmod_pitchOffset *= 0.88f;

        cir.setReturnValue(cir.getReturnValue() + ragdolmod_pitchOffset);
    }
}
