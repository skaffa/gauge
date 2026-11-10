package dev.gauge.client.mixin;

import dev.gauge.client.GaugeClient;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Every block change the client applies (server updates and local prediction) passes through here. */
@Mixin(ClientLevel.class)
abstract class ClientLevelMixin {
	@Inject(method = "sendBlockUpdated", at = @At("HEAD"))
	private void gauge$blockChanged(BlockPos pos, BlockState oldState, BlockState newState, int flags, CallbackInfo ci) {
		GaugeClient.onBlockChanged((ClientLevel) (Object) this, pos, oldState, newState);
	}
}
