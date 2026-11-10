package dev.gauge.client;

import com.mojang.blaze3d.platform.InputConstants;

import dev.gauge.core.GaugeConfig;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

/** Client entrypoint: keys, events, the per-tick overlay and the {@code /gauge} command. */
public final class GaugeClient implements ClientModInitializer {
	private static KeyMapping toggleKey;
	private static KeyMapping modeKey;
	private static KeyMapping screenKey;
	private static boolean openScreen;

	@Override
	public void onInitializeClient() {
		Gauge.load();
		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(Gauge.MOD_ID, "main"));
		toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.gauge.toggle", InputConstants.KEY_N, category));
		modeKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.gauge.mode", InputConstants.KEY_Z, category));
		screenKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.gauge.screen", InputConstants.UNKNOWN.getValue(), category));

		ClientTickEvents.END_CLIENT_TICK.register(GaugeClient::tick);
		UseBlockCallback.EVENT.register(ContainerTracker::onUse);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> Gauge.reset());
		HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, Identifier.fromNamespaceAndPath(Gauge.MOD_ID, "target"), GaugeHud::extract);
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> dispatcher.register(literal("gauge")
				.executes(c -> {
					openScreen = true;
					return 1;
				})
				.then(literal("forget").executes(c -> {
					int n = Gauge.forgetSnapshots();
					c.getSource().sendFeedback(Component.literal("Gauge: forgot " + n + " remembered container" + (n == 1 ? "" : "s")));
					return 1;
				}))));
		Gauge.LOG.info("Gauge loaded");
	}

	private static void tick(Minecraft mc) {
		DevShots.tick(mc);
		while (screenKey.consumeClick()) {
			openScreen = true;
		}
		if (openScreen) {
			openScreen = false;
			mc.gui.setScreen(new GaugeScreen(null));
		}
		if (mc.level == null || mc.player == null) {
			return;
		}
		GaugeConfig cfg = Gauge.config;
		while (toggleKey.consumeClick()) {
			cfg.enabled = !cfg.enabled;
			Gauge.save();
			Gauge.say(cfg.enabled ? "overlay on" : "overlay off");
		}
		while (modeKey.consumeClick()) {
			cfg.mode = cfg.mode.next();
			cfg.enabled = true;
			Gauge.save();
			Gauge.say(cfg.mode == GaugeConfig.Mode.FOCUS ? "focus mode (crosshair only)" : "radius mode (" + cfg.radius + " blocks)");
		}
		String dim = dimension(mc.level);
		ContainerTracker.tick(mc, dim);
		long now = mc.level.getGameTime();
		if (now % 100 == 0) {
			Gauge.PULSES.prune(now, 400);
		}
		OverlayRenderer.tick(mc, dim);
	}

	static String dimension(Level level) {
		return level.dimension().identifier().toString();
	}

	/** Called from the {@code ClientLevel} mixin for every applied block change. */
	public static void onBlockChanged(ClientLevel level, BlockPos pos, BlockState oldState, BlockState newState) {
		// a state change of the same block (dust power, repeater on/off, hopper lock...) is a pulse;
		// placing or breaking blocks is not
		if (oldState == newState || oldState.getBlock() != newState.getBlock() || !Inspector.interesting(newState)) {
			return;
		}
		Gauge.PULSES.record(pos.asLong(), level.getGameTime());
	}
}
