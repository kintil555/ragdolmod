package com.ragdolmod.mixin.client;

import com.ragdolmod.client.ClientRagdollCache;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * ClientPlayerEntityMixin
 *
 * Client-side player mixin.
 * Applies ragdoll perturbations to the local player's viewpoint:
 *  - Body tilt affects camera pitch
 *  - Sway affects camera roll (visible in first-person view)
 *  - Head lag affects yaw offset
 *
 * These modifications are purely visual and do not affect hitboxes
 * or server-side player position.
 */
@Environment(EnvType.CLIENT)
@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixin {

    /**
     * We hook into the tick to propagate camera distortions.
     * The actual camera rotation modification is done in {@link GameRendererMixin}.
     * This tick hook pre-fetches and caches the state for that frame.
     */
    @Inject(method = "tick", at = @At("RETURN"))
    private void onTickReturn(CallbackInfo ci) {
        ClientPlayerEntity self = (ClientPlayerEntity)(Object)this;
        // State is read by GameRendererMixin during rendering.
        // Nothing to do here currently; hook retained for future use.
    }
}
