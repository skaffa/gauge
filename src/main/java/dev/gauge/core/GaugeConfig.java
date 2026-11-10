package dev.gauge.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Everything Gauge saves in {@code config/gauge/config.json}. */
public final class GaugeConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public static final int MIN_RADIUS = 2;
	public static final int MAX_RADIUS = 32;

	public enum Mode {
		/** Only the block under the crosshair. */
		FOCUS,
		/** Every component within {@link #radius}. */
		RADIUS;

		public Mode next() {
			return this == FOCUS ? RADIUS : FOCUS;
		}
	}

	public boolean enabled = true;
	public Mode mode = Mode.RADIUS;
	public int radius = 10;
	/** Hard cap on labels per tick, nearest first. */
	public int maxLabels = 300;

	public boolean showDust = true;
	/** Show dust labels at power 0 too. Off keeps big unpowered lines quiet. */
	public boolean showUnpoweredDust = false;
	public boolean showLogic = true;
	public boolean showPoweredBlocks = true;
	public boolean showContainers = true;
	public boolean showPulses = true;
	/** Note block tuning and jukebox songs. */
	public boolean showSound = true;
	public boolean showHud = true;
	/** Ticks a block stays highlighted after its last state change. */
	public int pulseWindow = 40;

	public GaugeConfig clamp() {
		radius = Math.clamp(radius, MIN_RADIUS, MAX_RADIUS);
		maxLabels = Math.clamp(maxLabels, 20, 2000);
		pulseWindow = Math.clamp(pulseWindow, 5, 400);
		if (mode == null) {
			mode = Mode.RADIUS;
		}
		return this;
	}

	public static GaugeConfig load(Path file) {
		if (!Files.exists(file)) {
			return new GaugeConfig();
		}
		try {
			GaugeConfig c = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), GaugeConfig.class);
			return c == null ? new GaugeConfig() : c.clamp();
		} catch (IOException | JsonParseException e) {
			return new GaugeConfig();
		}
	}

	public void save(Path file) throws IOException {
		Files.createDirectories(file.getParent());
		Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
		Files.writeString(tmp, GSON.toJson(this), StandardCharsets.UTF_8);
		Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
	}
}
