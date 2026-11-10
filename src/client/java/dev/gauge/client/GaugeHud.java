package dev.gauge.client;

import dev.gauge.core.PulseTracker;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/** Small panel beside the crosshair describing the targeted component. */
final class GaugeHud {
	private GaugeHud() {
	}

	static void extract(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (!Gauge.config.enabled || !Gauge.config.showHud || mc.level == null || mc.player == null || mc.gui.screen() != null) {
			return;
		}
		BlockPos pos = OverlayRenderer.target(mc);
		if (pos == null) {
			return;
		}
		BlockState s = mc.level.getBlockState(pos);
		String dim = GaugeClient.dimension(mc.level);
		Inspector.Info info = Inspector.interesting(s) ? Inspector.inspect(mc.level, pos, s, dim) : null;
		PulseTracker.Pulse pulse = Gauge.PULSES.get(pos.asLong(), mc.level.getGameTime(), Gauge.config.pulseWindow);
		if (info == null && pulse == null) {
			return;
		}
		List<String> lines = new ArrayList<>();
		String title = info != null ? info.name() : s.getBlock().getName().getString();
		if (info != null) {
			lines.addAll(info.lines());
		}
		if (pulse != null) {
			if (pulse.clockPeriod() > 0) {
				lines.add("Clock: period " + pulse.clockPeriod() + " game ticks");
			} else if (pulse.lastInterval() > 0) {
				lines.add("Last change " + pulse.lastInterval() + " game ticks after the one before");
			} else {
				lines.add("Changed just now");
			}
			if (pulse.fast()) {
				lines.add("Fast pulses (<= 2 game ticks)");
			}
		}

		Font font = mc.font;
		int w = font.width(title);
		for (String l : lines) {
			w = Math.max(w, font.width(l));
		}
		int x = g.guiWidth() / 2 + 14;
		int y = g.guiHeight() / 2 + 6;
		int h = 12 + lines.size() * 10;
		if (x + w + 6 > g.guiWidth()) {
			x = Math.max(2, g.guiWidth() - w - 8);
		}
		g.fill(x - 3, y - 3, x + w + 3, y + h - 1, 0xA0101010);
		g.text(font, title, x, y, info != null ? info.color() : 0xFFFFFFFF, true);
		int ly = y + 12;
		for (String l : lines) {
			g.text(font, l, x, ly, 0xFFE0E0E0, true);
			ly += 10;
		}
	}
}
