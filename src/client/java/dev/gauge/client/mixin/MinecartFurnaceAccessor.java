package dev.gauge.client.mixin;

import net.minecraft.world.entity.vehicle.minecart.MinecartFurnace;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Furnace minecart fuel: the synced has-fuel flag on the client, the tick count on the singleplayer server. */
@Mixin(MinecartFurnace.class)
public interface MinecartFurnaceAccessor {
	@Accessor("fuel")
	int gauge$fuel();

	@Invoker("hasFuel")
	boolean gauge$hasFuel();
}
