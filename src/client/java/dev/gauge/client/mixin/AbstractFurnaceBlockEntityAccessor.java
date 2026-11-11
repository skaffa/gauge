package dev.gauge.client.mixin;

import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Burn and cook timers, read on the singleplayer server (the client only gets them while the menu is open). */
@Mixin(AbstractFurnaceBlockEntity.class)
public interface AbstractFurnaceBlockEntityAccessor {
	@Accessor("litTimeRemaining")
	int gauge$litTime();

	@Accessor("litTotalTime")
	int gauge$litTotal();

	@Accessor("cookingTimer")
	int gauge$cookTime();

	@Accessor("cookingTotalTime")
	int gauge$cookTotal();
}
