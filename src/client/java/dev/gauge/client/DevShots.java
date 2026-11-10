package dev.gauge.client;

import dev.gauge.core.GaugeConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Development aid, off by default. With {@code -Dgauge.devshots=x,y,z} (a container to open) it waits
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
		BlockPos chest = chest();
		if (chest != null && ticks >= 300) {
			// the dev window may catch real mouse movement; keep looking at the rig
			lookAt(mc, ticks >= 460 ? Vec3.atCenterOf(chest) : Vec3.atCenterOf(chest).add(0, -0.5, 3.0));
		}
		switch (ticks) {
			case 300 -> {
				Gauge.config.mode = GaugeConfig.Mode.RADIUS;
				Gauge.config.enabled = true;
			}
			case 340 -> shot(mc, "1-radius");
			case 350 -> openContainer(mc);
			case 400 -> shot(mc, "2-open");
			case 410 -> mc.player.closeContainer();
			case 450 -> shot(mc, "3-after");
			case 460 -> Gauge.config.mode = GaugeConfig.Mode.FOCUS;
			case 480 -> shot(mc, "4-focus");
			case 500 -> {
				Gauge.config.mode = GaugeConfig.Mode.RADIUS;
				mc.stop();
			}
			default -> {
			}
		}
	}

	private static BlockPos chest() {
		String[] p = PROP.split(",");
		if (p.length != 3) {
			return null;
		}
		return new BlockPos(Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim()), Integer.parseInt(p[2].trim()));
	}

	private static void lookAt(Minecraft mc, Vec3 target) {
		Vec3 d = target.subtract(mc.player.getEyePosition());
		double flat = Math.sqrt(d.x * d.x + d.z * d.z);
		mc.player.setYRot((float) Math.toDegrees(Math.atan2(-d.x, d.z)));
		mc.player.setXRot((float) -Math.toDegrees(Math.atan2(d.y, flat)));
	}

	private static void openContainer(Minecraft mc) {
		BlockPos pos = chest();
		if (pos == null) {
			return;
		}
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
		mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
	}

	private static void shot(Minecraft mc, String name) {
		Gauge.LOG.info("dev shot {} at {} rot {} {}", name, mc.player.position(), mc.player.getYRot(), mc.player.getXRot());
		Screenshot.grab(mc.gameDirectory, "gauge-" + name + ".png", mc.gameRenderer.mainRenderTarget(), 1,
				msg -> Gauge.LOG.info("dev screenshot {}: {}", name, msg.getString()));
	}
}
