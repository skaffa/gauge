package dev.gauge.client;

import dev.gauge.core.GaugeConfig;
import dev.gauge.core.PulseTracker;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.RedstoneWireBlock;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Runs every client tick and submits labels, arrows and pulse highlights as vanilla gizmos. Gizmos are
 * collected per tick and drawn by the vanilla renderer every frame, so this works with Sodium and Iris
 * without any render hooks of its own.
 */
final class OverlayRenderer {
	static final int FAST = 0xFFFF40FF;
	static final int PULSE = 0xFF40FFFF;
	static final float TEXT = 0.26f;
	static final float SMALL = 0.2f;

	private static long[] found = new long[1024];

	private OverlayRenderer() {
	}

	static void tick(Minecraft mc, String dimension) {
		Level level = mc.level;
		GaugeConfig cfg = Gauge.config;
		if (level == null || mc.player == null || !cfg.enabled) {
			return;
		}
		long now = level.getGameTime();
		if (cfg.mode == GaugeConfig.Mode.FOCUS) {
			Entity targetEntity = targetEntity(mc);
			if (targetEntity != null && cfg.showEntities) {
				drawEntity(targetEntity, dimension, true);
			}
			BlockPos target = target(mc);
			if (target != null) {
				draw(level, target, level.getBlockState(target), dimension, now, true);
				if (cfg.showPoweredBlocks) {
					poweredBlock(level, target, new HashSet<>(), true);
				}
			}
			return;
		}

		Vec3 eye = mc.player.getEyePosition();
		Vec3 look = mc.player.getViewVector(1.0f);
		if (cfg.showEntities) {
			double r2 = (double) cfg.radius * cfg.radius;
			for (Entity e : level.getEntities((Entity) null, new AABB(eye, eye).inflate(cfg.radius), EntityInspector::interesting)) {
				if (e.distanceToSqr(eye) <= r2) {
					drawEntity(e, dimension, false);
				}
			}
		}
		int n = scan(level, eye, look, cfg.radius);
		if (n > cfg.maxLabels) {
			Arrays.sort(found, 0, n);
			n = cfg.maxLabels;
		}
		Set<Long> powered = new HashSet<>();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int k = 0; k < n; k++) {
			int idx = (int) found[k];
			long packed = packedAt(idx);
			pos.set(BlockPos.getX(packed), BlockPos.getY(packed), BlockPos.getZ(packed));
			BlockState s = level.getBlockState(pos);
			BlockPos immutable = pos.immutable();
			draw(level, immutable, s, dimension, now, false);
			if (cfg.showPoweredBlocks && s.isSignalSource()) {
				boolean dust = s.getBlock() instanceof RedstoneWireBlock;
				for (Direction d : Direction.values()) {
					// the block under every piece of dust is powered too; labelling them all is noise
					if (!(dust && d == Direction.DOWN)) {
						poweredBlock(level, immutable.relative(d), powered, false);
					}
				}
			}
		}
	}

	static BlockPos target(Minecraft mc) {
		HitResult hit = mc.hitResult;
		if (hit instanceof BlockHitResult bh && hit.getType() == HitResult.Type.BLOCK) {
			return bh.getBlockPos();
		}
		return null;
	}

	/** The container cart, chest boat or TNT under the crosshair, or null. */
	static Entity targetEntity(Minecraft mc) {
		return mc.hitResult instanceof EntityHitResult eh && EntityInspector.interesting(eh.getEntity()) ? eh.getEntity() : null;
	}

	private static void drawEntity(Entity e, String dimension, boolean focus) {
		Inspector.Info info = EntityInspector.inspect(e, dimension);
		if (info == null) {
			return;
		}
		Vec3 p = e.position().add(0, e.getBbHeight() + 0.35, 0);
		var props = Gizmos.billboardText(info.label(), p, TextGizmo.Style.forColorAndCentered(info.color()).withScale(TEXT));
		if (focus) {
			props.setAlwaysOnTop();
		}
	}

	// ---- scanning ----

	private static long[] positions = new long[1024];

	private static long packedAt(int idx) {
		return positions[idx];
	}

	/**
	 * Collects interesting blocks within the radius that are in front of the player, as (distance, index)
	 * pairs in {@link #found}. Chunk sections whose palette holds nothing interesting are skipped whole.
	 */
	private static int scan(Level level, Vec3 eye, Vec3 look, int radius) {
		int n = 0;
		double r2 = (double) radius * radius;
		int ex = (int) Math.floor(eye.x);
		int ey = (int) Math.floor(eye.y);
		int ez = (int) Math.floor(eye.z);
		int minY = Math.max(level.getMinY(), ey - radius);
		int maxY = Math.min(level.getMaxY(), ey + radius);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int cx = (ex - radius) >> 4; cx <= (ex + radius) >> 4; cx++) {
			for (int cz = (ez - radius) >> 4; cz <= (ez + radius) >> 4; cz++) {
				LevelChunk chunk = (LevelChunk) level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false);
				if (chunk == null) {
					continue;
				}
				for (int sy = minY >> 4; sy <= maxY >> 4; sy++) {
					int idx = level.getSectionIndexFromSectionY(sy);
					if (idx < 0 || idx >= chunk.getSections().length) {
						continue;
					}
					LevelChunkSection section = chunk.getSection(idx);
					if (section.hasOnlyAir() || !section.maybeHas(Inspector.INTERESTING)) {
						continue;
					}
					int x0 = Math.max(cx << 4, ex - radius);
					int x1 = Math.min((cx << 4) + 15, ex + radius);
					int y0 = Math.max(sy << 4, minY);
					int y1 = Math.min((sy << 4) + 15, maxY);
					int z0 = Math.max(cz << 4, ez - radius);
					int z1 = Math.min((cz << 4) + 15, ez + radius);
					for (int y = y0; y <= y1; y++) {
						for (int z = z0; z <= z1; z++) {
							for (int x = x0; x <= x1; x++) {
								double dx = x + 0.5 - eye.x;
								double dy = y + 0.5 - eye.y;
								double dz = z + 0.5 - eye.z;
								double d2 = dx * dx + dy * dy + dz * dz;
								if (d2 > r2) {
									continue;
								}
								// cheap view cull: skip what is behind the camera (keep the block we stand in)
								if (d2 > 4 && dx * look.x + dy * look.y + dz * look.z < -1.0) {
									continue;
								}
								BlockState s = section.getBlockState(x & 15, y & 15, z & 15);
								if (!Inspector.interesting(s)) {
									continue;
								}
								if (n == found.length) {
									found = Arrays.copyOf(found, n * 2);
									positions = Arrays.copyOf(positions, n * 2);
								}
								pos.set(x, y, z);
								positions[n] = pos.asLong();
								found[n] = ((long) Float.floatToIntBits((float) d2) << 32) | n;
								n++;
							}
						}
					}
				}
			}
		}
		return n;
	}

	// ---- drawing ----

	private static void draw(Level level, BlockPos pos, BlockState s, String dimension, long now, boolean focus) {
		GaugeConfig cfg = Gauge.config;
		Inspector.Info info = Inspector.inspect(level, pos, s, dimension);
		PulseTracker.Pulse pulse = cfg.showPulses ? Gauge.PULSES.get(pos.asLong(), now, cfg.pulseWindow) : null;
		if (info == null && pulse == null) {
			return;
		}
		boolean shown = info != null && switch (info.category()) {
			case DUST -> cfg.showDust && (info.active() || cfg.showUnpoweredDust || focus);
			case LOGIC -> cfg.showLogic;
			case CONTAINER -> cfg.showContainers;
			case SOUND -> cfg.showSound;
			case MECHANISM -> cfg.showMechanisms;
			case TRIPWIRE -> cfg.showMechanisms && (info.active() || focus);
			case ENTITY -> cfg.showEntities;
		} && !info.label().isEmpty();
		if (info != null && info.category() == Inspector.Category.TRIPWIRE && info.active() && cfg.showMechanisms) {
			Gizmos.cuboid(new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 0.15, pos.getZ() + 1),
					GizmoStyle.stroke(Inspector.RED, 2.0f));
		}
		double labelY = s.getShape(level, pos).isEmpty() ? 0.3 : Math.min(1.0, s.getShape(level, pos).max(Direction.Axis.Y)) + 0.25;
		if (s.getBlock() instanceof LecternBlock && s.getValue(LecternBlock.HAS_BOOK)) {
			// the open book is drawn above the lectern's shape; keep the label clear of its pages
			labelY = 1.45;
		}
		Vec3 center = Vec3.atCenterOf(pos);
		Vec3 top = new Vec3(center.x, pos.getY() + labelY, center.z);
		if (labelY > 1.0 && !level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()) {
			// something sits on top (a hopper on a chest): put the label in front, toward the viewer
			Vec3 eye = Minecraft.getInstance().player.getEyePosition();
			Vec3 flat = new Vec3(eye.x - center.x, 0, eye.z - center.z);
			Vec3 toward = flat.lengthSqr() < 1e-6 ? Vec3.ZERO : flat.normalize().scale(0.75);
			top = center.add(toward).add(0, 0.2, 0);
		}
		if (shown) {
			float scale = info.category() == Inspector.Category.DUST ? SMALL : TEXT;
			var props = Gizmos.billboardText(info.label(), top, TextGizmo.Style.forColorAndCentered(info.color()).withScale(scale));
			if (focus) {
				props.setAlwaysOnTop();
			}
			if (info.output() != null) {
				arrow(pos, info.output(), info.active() ? 0xE0FF5050 : 0xB0C0C0C0);
			}
		}
		if (pulse != null) {
			int color = pulse.fast() ? FAST : PULSE;
			Gizmos.cuboid(new AABB(pos).inflate(0.02), GizmoStyle.stroke(color, 2.0f));
			String t = pulse.clockPeriod() > 0 ? "clock " + pulse.clockPeriod() + "gt"
					: pulse.lastInterval() > 0 ? pulse.lastInterval() + "gt" : null;
			if (t != null) {
				double off = shown ? 0.22 : 0;
				Gizmos.billboardText(t, top.add(0, off, 0), TextGizmo.Style.forColorAndCentered(color).withScale(SMALL));
			}
		}
	}

	/** Arrow from just inside the block out through the face it outputs to. */
	private static void arrow(BlockPos pos, Direction out, int color) {
		Vec3 c = Vec3.atCenterOf(pos);
		Vec3 u = out.getUnitVec3();
		if (out.getAxis() != Direction.Axis.Y) {
			c = c.add(0, -0.3, 0); // keep it low so it does not cross the label
		}
		Gizmos.arrow(c.add(u.scale(0.1)), c.add(u.scale(0.85)), color, 2.0f);
	}

	/** Labels a solid block that receives power: S = strongly powered (powers dust beside it), W = weakly. */
	private static void poweredBlock(Level level, BlockPos pos, Set<Long> done, boolean focus) {
		if (!done.add(pos.asLong())) {
			return;
		}
		BlockState s = level.getBlockState(pos);
		if (s.isAir() || Inspector.interesting(s) || !s.isRedstoneConductor(level, pos)) {
			return;
		}
		// Strong power comes from repeaters, comparators, torches below, attached levers/buttons...
		// Dust only weakly powers a block (in code its direct signal is non-zero, but other dust ignores it).
		// Level.getBestNeighborSignal would also count strongly powered solid neighbours, which do not
		// power a solid block, so ask each neighbouring component for the signal it sends here.
		int strong = 0;
		int weak = 0;
		for (Direction d : Direction.values()) {
			BlockPos n = pos.relative(d);
			BlockState ns = level.getBlockState(n);
			if (!ns.isSignalSource()) {
				continue;
			}
			int sig = ns.getSignal(level, n, d);
			weak = Math.max(weak, sig);
			if (!(ns.getBlock() instanceof RedstoneWireBlock)) {
				strong = Math.max(strong, ns.getDirectSignal(level, n, d));
			}
		}
		if (weak <= 0 && strong <= 0) {
			return;
		}
		String label = strong > 0 ? "S" + strong : "W" + weak;
		Vec3 top = new Vec3(pos.getX() + 0.5, pos.getY() + 1.15, pos.getZ() + 0.5);
		var props = Gizmos.billboardText(label, top, TextGizmo.Style.forColorAndCentered(strong > 0 ? Inspector.ORANGE : 0xFFD0A070).withScale(SMALL));
		if (focus) {
			props.setAlwaysOnTop();
		}
	}
}
