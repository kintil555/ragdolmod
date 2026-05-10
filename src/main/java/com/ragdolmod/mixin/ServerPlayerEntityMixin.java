package com.ragdolmod.mixin;

import com.ragdolmod.RagdolMod;
import com.ragdolmod.physics.PlayerRagdollState;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * ServerPlayerEntityMixin
 *
 * Hooks into the server player's tick cycle to apply walk forces
 * through the physics engine before vanilla movement is processed.
 *
 * We inject at the START of tick() so the engine's force accumulation
 * happens before {@code LivingEntity.travel()} is called.
 */
@Mixin(ServerPlayerEntity.class)
public abstract class ServerPlayerEntityMixin {

    /**
     * Inject at the beginning of the server player's per-tick update.
     * This ensures walk forces are applied before Minecraft's own physics run.
     */
    @Inject(method = "tick", at = @At("HEAD"))
    private void onTickHead(CallbackInfo ci) {
        ServerPlayerEntity self = (ServerPlayerEntity)(Object)this;
        if (!RagdolMod.CONFIG.enabled) return;

        PlayerRagdollState state = RagdolMod.getState(self.getUuid());
        if (state == null) return;

        // Walk force injection is handled in RagdolMod.onServerTick()
        // through tickPlayerMovement(). Nothing extra needed here,
        // but this hook is kept for future injection points (e.g., stumble on hit).
    }

    /**
     * Inject after the player's velocity is updated by vanilla.
     * This is where we apply the ragdoll engine override and sync back.
     */
    @Inject(method = "tick", at = @At("TAIL"))
    private void onTickTail(CallbackInfo ci) {
        // Physics tick and sync happen in RagdolMod.onServerTick() via
        // ServerTickEvents.END_SERVER_TICK to guarantee ordering after
        // all per-player ticks complete.
    }
}
