package com.ragdolmod.mixin.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;

/**
 * GameRendererMixin
 *
 * Reserved for future camera matrix modifications.
 * Camera roll and pitch are currently handled entirely by CameraAccessor.
 */
@Environment(EnvType.CLIENT)
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    // No injections needed - CameraAccessor handles getRoll() and getPitch() directly.
}
