package dev.gauge.client.mixin;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.LecternBlockEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Exposes vanilla's own page counting so Gauge's lectern maths matches it exactly. */
@Mixin(LecternBlockEntity.class)
public interface LecternBlockEntityAccessor {
	@Invoker("getPageCount")
	static int gauge$pageCount(ItemStack book) {
		throw new AssertionError();
	}
}
