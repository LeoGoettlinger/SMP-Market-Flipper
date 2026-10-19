package com.example.donutflipscanner.mixin;

import net.minecraft.entity.projectile.FishingBobberEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Reads the server-synchronised vanilla bite state; the client never guesses from bobber motion. */
@Mixin(FishingBobberEntity.class)
public interface FishingBobberAccessor {
    @Accessor("caughtFish")
    boolean donutflip$isCaughtFish();
}
