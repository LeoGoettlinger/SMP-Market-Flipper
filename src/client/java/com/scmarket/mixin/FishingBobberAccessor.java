package com.scmarket.mixin;

import net.minecraft.world.entity.projectile.FishingHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Reads the server-synchronised vanilla bite state; the client never guesses from bobber motion. */
@Mixin(FishingHook.class)
public interface FishingBobberAccessor {
    @Accessor("biting")
    boolean scmarket$isCaughtFish();
}
