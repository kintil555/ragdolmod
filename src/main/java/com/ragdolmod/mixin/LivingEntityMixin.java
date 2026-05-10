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
     * scale down any velocity the vanilla code just applied.
     *
     * We clamp horizontal velocity so that vanilla WASD barely moves the player.
     * The actual movement comes from the physics engine's impulse system.
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

        // The physics engine has already written the correct XZ velocity
        // back to the entity in PlayerRagdollState.tick().
        // Here we ensure any vanilla movement residual beyond that is suppressed.
        // We do NOT touch Y velocity (gravity/jump managed by Minecraft).
        var vel = player.getVelocity();
        double maxAllowedXZ = 0.8; // generous cap – engine handles real limiting
        double xzSpd = Math.sqrt(vel.x * vel.x + vel.z * vel.z);

        if (xzSpd > maxAllowedXZ) {
            double scale = maxAllowedXZ / xzSpd;
            player.setVelocity(vel.x * scale, vel.y, vel.z * scale);
        }
    }
}
