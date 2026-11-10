package dev.gauge.client;

import dev.gauge.core.ContainerSnapshot;

import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.item.JukeboxSongPlayer;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ComparatorBlockEntity;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * In singleplayer the server runs in this same process, so Gauge can read the real block entities
 * instead of guessing: container contents, comparator output, the jukebox song and lectern page.
 *
 * <p>Reads run as a task on the server thread (block entities are not safe to touch from the render
 * thread) and publish an immutable map the client reads one tick later. On a multiplayer server this
 * does nothing and Gauge falls back to what the client can see.
 */
final class ServerPeek {
	/** Exact server-side facts about one block. Fields that do not apply are -1 / null. */
	record Peek(int analog, int comparatorOut, ContainerSnapshot container, JukeboxSong song, long songTicks,
			int lecternPage, int lecternPages, int lecternSignal) {
	}

	private static final Set<Long> REQUESTED = new HashSet<>();
	private static volatile Map<Long, Peek> latest = Map.of();
	private static volatile String latestDim = "";
	private static volatile boolean pending;

	private ServerPeek() {
	}

	static boolean active() {
		return Minecraft.getInstance().hasSingleplayerServer();
	}

	/** Asks for this block to be read on the next server pass; returns the last result, if any. */
	static Peek get(String dimension, long pos) {
		if (!active()) {
			return null;
		}
		REQUESTED.add(pos);
		return dimension.equals(latestDim) ? latest.get(pos) : null;
	}

	/** Once per client tick: hands the requested positions to the server thread. */
	static void tick(Minecraft mc) {
		IntegratedServer server = mc.getSingleplayerServer();
		if (server == null || mc.level == null || REQUESTED.isEmpty() || pending) {
			REQUESTED.clear();
			return;
		}
		var dimKey = mc.level.dimension();
		String dim = GaugeClient.dimension(mc.level);
		long[] positions = REQUESTED.stream().mapToLong(Long::longValue).toArray();
		REQUESTED.clear();
		pending = true;
		server.execute(() -> {
			try {
				ServerLevel level = server.getLevel(dimKey);
				Map<Long, Peek> out = new HashMap<>();
				if (level != null) {
					for (long p : positions) {
						Peek peek = read(level, BlockPos.of(p));
						if (peek != null) {
							out.put(p, peek);
						}
					}
				}
				latest = Map.copyOf(out);
				latestDim = dim;
			} catch (RuntimeException e) {
				Gauge.LOG.debug("server peek failed", e);
			} finally {
				pending = false;
			}
		});
	}

	static void clear() {
		latest = Map.of();
		latestDim = "";
		REQUESTED.clear();
	}

	private static Peek read(ServerLevel level, BlockPos pos) {
		if (!level.isLoaded(pos)) {
			return null;
		}
		BlockState s = level.getBlockState(pos);
		BlockEntity be = level.getBlockEntity(pos);
		int analog = s.hasAnalogOutputSignal() ? s.getAnalogOutputSignal(level, pos, Direction.NORTH) : -1;
		int cmp = be instanceof ComparatorBlockEntity c ? c.getOutputSignal() : -1;
		ContainerSnapshot snap = null;
		if (be instanceof BaseContainerBlockEntity) {
			// read through the block so double chests count both halves, like a comparator does
			Container c = s.getBlock() instanceof net.minecraft.world.level.block.ChestBlock chest
					? net.minecraft.world.level.block.ChestBlock.getContainer(chest, s, level, pos, true)
					: (Container) be;
			if (c != null) {
				snap = snapshot(c);
			}
		}
		JukeboxSong song = null;
		long ticks = -1;
		if (be instanceof JukeboxBlockEntity jb) {
			JukeboxSongPlayer player = jb.getSongPlayer();
			if (player.isPlaying()) {
				song = player.getSong();
				ticks = player.getTicksSinceSongStarted();
			}
		}
		int page = -1;
		int pages = -1;
		int lecternSignal = -1;
		if (be instanceof LecternBlockEntity lb && lb.hasBook()) {
			page = lb.getPage();
			pages = LecternPages.count(lb.getBook());
			lecternSignal = lb.getRedstoneSignal();
		}
		return new Peek(analog, cmp, snap, song, ticks, page, pages, lecternSignal);
	}

	private static ContainerSnapshot snapshot(Container c) {
		int n = c.getContainerSize();
		int[] counts = new int[n];
		int[] max = new int[n];
		for (int i = 0; i < n; i++) {
			ItemStack stack = c.getItem(i);
			counts[i] = stack.isEmpty() ? 0 : stack.getCount();
			max[i] = stack.isEmpty() ? 64 : Math.min(stack.getMaxStackSize(), c.getMaxStackSize(stack));
		}
		return ContainerSnapshot.of(counts, max, System.currentTimeMillis());
	}
}
