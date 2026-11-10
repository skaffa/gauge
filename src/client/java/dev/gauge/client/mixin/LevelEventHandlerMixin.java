package dev.gauge.client.mixin;

import dev.gauge.client.JukeboxTracker;

import net.minecraft.client.renderer.LevelEventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.item.JukeboxSong;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Jukebox start/stop level events: the only song information a client ever receives. */
@Mixin(LevelEventHandler.class)
abstract class LevelEventHandlerMixin {
	// TAIL: playJukeboxSong first calls stopJukeboxSong for the same position, which would undo a HEAD record
	@Inject(method = "playJukeboxSong", at = @At("TAIL"))
	private void gauge$songStarted(Holder<JukeboxSong> song, BlockPos pos, CallbackInfo ci) {
		JukeboxTracker.started(song, pos);
	}

	@Inject(method = "stopJukeboxSong", at = @At("HEAD"))
	private void gauge$songStopped(BlockPos pos, CallbackInfo ci) {
		JukeboxTracker.stopped(pos);
	}
}
