package dev.gauge.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.item.JukeboxSong;

import java.util.HashMap;
import java.util.Map;

/**
 * Remembers which song each jukebox started and when. The server tells nearby clients when a song
 * starts and stops (level events), but never what a jukebox holds, so a song that started before the
 * player came near stays unknown.
 */
public final class JukeboxTracker {
	record Playing(JukeboxSong song, long startTick) {
		long elapsed(long now) {
			return Math.max(0, now - startTick);
		}
	}

	private static final Map<String, Map<Long, Playing>> PLAYING = new HashMap<>();

	private JukeboxTracker() {
	}

	/** Called from the {@code LevelEventHandler} mixin. */
	public static void started(Holder<JukeboxSong> song, BlockPos pos) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return;
		}
		PLAYING.computeIfAbsent(GaugeClient.dimension(mc.level), k -> new HashMap<>())
				.put(pos.asLong(), new Playing(song.value(), mc.level.getGameTime()));
	}

	/** Called from the {@code LevelEventHandler} mixin. */
	public static void stopped(BlockPos pos) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return;
		}
		Map<Long, Playing> m = PLAYING.get(GaugeClient.dimension(mc.level));
		if (m != null) {
			m.remove(pos.asLong());
		}
	}

	static Playing get(String dimension, long pos) {
		Map<Long, Playing> m = PLAYING.get(dimension);
		return m == null ? null : m.get(pos);
	}

	static void clear() {
		PLAYING.clear();
	}
}
