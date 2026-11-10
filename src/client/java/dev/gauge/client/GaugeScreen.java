package dev.gauge.client;

import dev.gauge.core.GaugeConfig;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** All Gauge settings on one screen. Changes apply and save immediately. */
public final class GaugeScreen extends Screen {
	private static final int WHITE = 0xFFFFFFFF;
	private static final int GRAY = 0xFFA8A8A8;
	private static final int ROW = 21;
	private static final int H = 20;

	private final Screen parent;
	private final List<Label> labels = new ArrayList<>();
	private int colW;

	private record Label(String text, int x, int y, int color) {
	}

	public GaugeScreen(Screen parent) {
		super(Component.literal("Gauge"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		build();
	}

	private void build() {
		clearWidgets();
		labels.clear();
		GaugeConfig c = Gauge.config;
		colW = Math.min(200, (width - 36) / 2);
		int left = width / 2 - colW - 8;
		int right = width / 2 + 8;
		int top = 30;

		int y = top;
		y = toggle(left, y, "Overlay", () -> c.enabled, v -> c.enabled = v, "Key N toggles it in game");
		labels.add(new Label("Mode", left, y + 6, GRAY));
		addRenderableWidget(Button.builder(Component.literal(modeName(c.mode)), b -> {
			c.mode = c.mode.next();
			changed();
			b.setMessage(Component.literal(modeName(c.mode)));
		}).bounds(left + colW / 2, y, colW / 2, H).tooltip(Tooltip.create(Component.literal(
				"Focus: only the block under the crosshair. Radius: everything nearby. Key Z switches."))).build());
		y += ROW;
		labels.add(new Label("Radius", left, y + 6, GRAY));
		int bw = colW / 2;
		int step = bw / 4;
		addRenderableWidget(Button.builder(Component.literal("-"), b -> {
			c.radius = Math.max(GaugeConfig.MIN_RADIUS, c.radius - 1);
			changed();
		}).bounds(left + colW - bw, y, step, H).build());
		addRenderableWidget(Button.builder(Component.literal("+"), b -> {
			c.radius = Math.min(GaugeConfig.MAX_RADIUS, c.radius + 1);
			changed();
		}).bounds(left + colW - step, y, step, H).build());
		y += ROW;
		y = toggle(left, y, "Target panel", () -> c.showHud, v -> c.showHud = v, "Details of the targeted block beside the crosshair");

		y = top;
		y = toggle(right, y, "Redstone dust", () -> c.showDust, v -> c.showDust = v, "Power level 0-15 on dust");
		y = toggle(right, y, "  unpowered dust", () -> c.showUnpoweredDust, v -> c.showUnpoweredDust = v, "Also label dust at power 0");
		y = toggle(right, y, "Logic blocks", () -> c.showLogic, v -> c.showLogic = v,
				"Repeaters, comparators, observers, torches, levers, buttons, lamps");
		y = toggle(right, y, "Powered blocks", () -> c.showPoweredBlocks, v -> c.showPoweredBlocks = v,
				"Solid blocks receiving power: S = strong, W = weak");
		y = toggle(right, y, "Containers", () -> c.showContainers, v -> c.showContainers = v,
				"Fill % and comparator output of containers you have opened, plus composters, cauldrons...");
		toggle(right, y, "Pulse highlight", () -> c.showPulses, v -> c.showPulses = v,
				"Outline blocks that just changed: cyan, magenta when toggling every 1-2 game ticks");

		addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
				.bounds(width / 2 - 100, height - 28, 200, H).build());
	}

	private static String modeName(GaugeConfig.Mode m) {
		return m == GaugeConfig.Mode.FOCUS ? "Focus" : "Radius";
	}

	private int toggle(int x, int y, String label, BooleanSupplier get, Consumer<Boolean> set, String tip) {
		labels.add(new Label(label, x, y + 6, GRAY));
		int bw = colW / 2;
		addRenderableWidget(Button.builder(onOff(get.getAsBoolean()), b -> {
			set.accept(!get.getAsBoolean());
			changed();
			b.setMessage(onOff(get.getAsBoolean()));
		}).bounds(x + colW - bw, y, bw, H).tooltip(Tooltip.create(Component.literal(tip))).build());
		return y + ROW;
	}

	private static Component onOff(boolean v) {
		return Component.literal(v ? "On" : "Off");
	}

	private static void changed() {
		Gauge.save();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
		super.extractRenderState(g, mx, my, delta);
		g.centeredText(font, title, width / 2, 10, WHITE);
		for (Label l : labels) {
			g.text(font, Component.literal(l.text()), l.x(), l.y(), l.color());
		}
		// radius value sits between its - and + buttons
		int left = width / 2 - colW - 8;
		int bw = colW / 2;
		String r = Integer.toString(Gauge.config.radius);
		g.centeredText(font, Component.literal(r), left + colW - bw / 2, 30 + 2 * ROW + 6, WHITE);
	}

	@Override
	public void onClose() {
		Gauge.save();
		minecraft.gui.setScreen(parent);
	}
}
