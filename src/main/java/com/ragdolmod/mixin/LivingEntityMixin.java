package com.ragdolmod.mixin;

import com.ragdolmod.RagdolMod;
import com.ragdolmod.physics.PlayerRagdollState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * LivingEntityMixin
 *
 * Intercepts the vanilla movement velocity application for players
 * to suppress the normal WASD speed and replace it with our
 * physics-based ragdoll velocity from the engine.
 *
 * The key injection point is {@code travel()} which is where
 * Minecraft applies input-based movement.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    /**
     * Inject into the end of {@code travel(Vec3d moveInput)} to
     * replace the vanilla-applied velocity with the physics engine's velocity.
     *
     * After vanilla travel() runs, the player's XZ velocity reflects normal
     * Minecraft movement. We discard that and write the physics engine's
     * XZ velocity instead, so ragdoll physics fully controls movement.
     */
    @Inject(method = "travel", at = @At("RETURN"))
    private void onTravelReturn(net.minecraft.util.math.Vec3d moveInput, CallbackInfo ci) {
        LivingEntity self = (LivingEntity)(Object)this;

        // Only apply to players
        if (!(self instanceof PlayerEntity player)) return;
        // Skip spectators and mounted players
        if (player.isSpectator() || player.hasVehicle()) return;
        // Skip if mod is disabled
        if (!RagdolMod.CONFIG.enabled) return;

        PlayerRagdollState state = RagdolMod.getState(player.getUuid());
        if (state == null) return;

        // Replace vanilla XZ velocity with the engine's physics velocity.
        // Y velocity is preserved (gravity and jumping are managed by Minecraft).
        var vel = player.getVelocity();
        player.setVelocity(
            state.engine.velocity.x,
            vel.y,
            state.engine.velocity.z
        );
    }
}
