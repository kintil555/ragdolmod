package com.ragdolmod.mixin.client;

import com.ragdolmod.client.ClientRagdollCache;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * CameraAccessor
 *
 * Mixin target: {@link net.minecraft.client.render.Camera}
 *
 * NOTE: getRoll() does NOT exist in Minecraft 1.21.1's Camera class.
 * Camera roll is instead applied via the rotation quaternion in GameRendererMixin.
 *
 * This mixin intercepts {@code getPitch()} to add the landing pitch bump.
 */
@Environment(EnvType.CLIENT)
@Mixin(Camera.class)
public abstract class CameraAccessor {

    private float ragdolmod_pitchOffset = 0f;

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
