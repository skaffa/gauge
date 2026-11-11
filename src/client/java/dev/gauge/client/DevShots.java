package dev.gauge.client;

import dev.gauge.core.GaugeConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Development aid, off by default. With {@code -Dgauge.devshots=x,y,z;x,y,z...} (a container to open, then blocks to aim at) it waits
 * after joining, saves screenshots of radius mode, opens and closes the container, then focus mode, and
 * quits. Used with a scripted test server; see README.
 */
final class DevShots {
	private static final String PROP = System.getProperty("gauge.devshots", "");
	private static int ticks = -1;

	private DevShots() {
	}

	static void tick(Minecraft mc) {
		if (PROP.isEmpty() || mc.level == null || mc.player == null) {
			return;
		}
		ticks++;
		// a click elsewhere on the desktop must not pause the world mid-run
		mc.options.pauseOnLostFocus = false;
		if (mc.gui.screen() instanceof net.minecraft.client.gui.screens.PauseScreen) {
			mc.gui.setScreen(null);
		}
		List<BlockPos> targets = targets();
		if (targets.isEmpty()) {
			return;
		}
		BlockPos chest = targets.get(0);
		int extra = ticks < 500 ? -1 : (ticks - 500) / 30 + 1;
		if (ticks >= 300) {
			// the dev window may catch real mouse movement; keep looking at the rig
			Vec3 look = extra >= 1 && extra < targets.size()
					? OVERVIEW.contains(extra) ? Vec3.atCenterOf(targets.get(extra)) : topOf(mc, targets.get(extra))
					: ticks >= 460 ? Vec3.atCenterOf(chest) : Vec3.atCenterOf(chest).add(0, -0.5, 3.0);
			lookAt(mc, look);
		}
		switch (ticks) {
			// singleplayer test worlds: stand where the multiplayer test puts the player
			case 250 -> teleport(mc, 5.5, -60, 1.5);
			case 300 -> {
				Gauge.config.mode = GaugeConfig.Mode.RADIUS;
				Gauge.config.enabled = true;
			}
			case 340 -> shot(mc, "1-radius");
			case 350 -> openContainer(mc, chest);
			case 400 -> shot(mc, "2-open");
			case 410 -> mc.player.closeContainer();
			case 450 -> shot(mc, "3-after");
			case 460 -> Gauge.config.mode = GaugeConfig.Mode.FOCUS;
			case 480 -> shot(mc, "4-focus");
			default -> {
			}
		}
		if (extra >= 1 && extra < targets.size() && (ticks - 500) % 30 == 1) {
			// singleplayer: step up to the target so it is within reach of the crosshair
			BlockPos t = targets.get(extra);
			boolean overview = OVERVIEW.contains(extra);
			// "r" targets: step back and up, radius mode, to see every label around it
			Gauge.config.mode = overview ? GaugeConfig.Mode.RADIUS : GaugeConfig.Mode.FOCUS;
			// overviews: hover (creative flight) and hide the hand and hotbar, like F1
			mc.player.getAbilities().flying = overview;
			if (mc.gui.hud.isHidden() != overview) {
				mc.gui.hud.toggle();
			}
			teleport(mc, t.getX() + 0.5, t.getY() + (overview ? 1.2 : 0), t.getZ() + (overview ? 3.5 : 2.5));
		}
		if (extra >= 1 && extra < targets.size() && (ticks - 500) % 30 == 25) {
			shot(mc, "5-target" + extra);
		}
		if (extra >= targets.size()) {
			Gauge.config.mode = GaugeConfig.Mode.RADIUS;
			PROP_DONE[0] = true;
			mc.stop();
		}
	}

	private static final boolean[] PROP_DONE = new boolean[1];
	/** Indexes of targets written with an "r" prefix: radius-mode overview shots. */
	private static final java.util.Set<Integer> OVERVIEW = new java.util.HashSet<>();

	/** First entry: the container to open. The rest: blocks to aim at in focus mode, one shot each. */
	private static List<BlockPos> targets() {
		List<BlockPos> out = new ArrayList<>();
		if (PROP_DONE[0]) {
			return out;
		}
		for (String t : PROP.split(";")) {
			t = t.trim();
			if (t.startsWith("r")) {
				OVERVIEW.add(out.size());
				t = t.substring(1);
			}
			String[] p = t.split(",");
			if (p.length == 3) {
				out.add(new BlockPos(Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim()), Integer.parseInt(p[2].trim())));
			}
		}
		return out;
	}

	/** Just under the top of the block's shape, so the crosshair lands on it and not on its neighbours. */
	private static Vec3 topOf(Minecraft mc, BlockPos pos) {
		var shape = mc.level.getBlockState(pos).getShape(mc.level, pos);
		double top = shape.isEmpty() ? 0.5 : shape.max(net.minecraft.core.Direction.Axis.Y) - 0.03;
		return new Vec3(pos.getX() + 0.5, pos.getY() + top, pos.getZ() + 0.5);
	}

	/** Singleplayer only (multiplayer tests position the player over RCON). */
	private static void teleport(Minecraft mc, double x, double y, double z) {
		var server = mc.getSingleplayerServer();
		var id = mc.player.getUUID();
		if (server != null) {
			server.execute(() -> {
				var sp = server.getPlayerList().getPlayer(id);
				if (sp != null) {
					sp.teleportTo(x, y, z);
				}
			});
		}
	}

	private static void lookAt(Minecraft mc, Vec3 target) {
		Vec3 d = target.subtract(mc.player.getEyePosition());
		double flat = Math.sqrt(d.x * d.x + d.z * d.z);
		mc.player.setYRot((float) Math.toDegrees(Math.atan2(-d.x, d.z)));
		mc.player.setXRot((float) -Math.toDegrees(Math.atan2(d.y, flat)));
	}

	private static void openContainer(Minecraft mc, BlockPos pos) {
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
		mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
	}

	private static void shot(Minecraft mc, String name) {
		Gauge.LOG.info("dev shot {} at {} rot {} {}", name, mc.player.position(), mc.player.getYRot(), mc.player.getXRot());
		Screenshot.grab(mc.gameDirectory, "gauge-" + name + ".png", mc.gameRenderer.mainRenderTarget(), 1,
				msg -> Gauge.LOG.info("dev screenshot {}: {}", name, msg.getString()));
	}
}
