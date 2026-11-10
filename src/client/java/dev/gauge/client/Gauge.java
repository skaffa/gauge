package dev.gauge.client;

import dev.gauge.core.ContainerSnapshot;
import dev.gauge.core.GaugeConfig;
import dev.gauge.core.PulseTracker;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Shared client state: settings, pulse history and remembered container contents. */
public final class Gauge {
	public static final String MOD_ID = "gauge";
	public static final Logger LOG = LoggerFactory.getLogger("Gauge");
	/** All Gauge files live in their own folder: {@code config/gauge/}. */
	private static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID);
	private static final Path FILE = DIR.resolve("config.json");
	/** Where 0.1.0 dev builds kept the settings; moved into {@link #DIR} on first load. */
	private static final Path OLD_FILE = FabricLoader.getInstance().getConfigDir().resolve("gauge.json");

	public static GaugeConfig config = new GaugeConfig();
	public static final PulseTracker PULSES = new PulseTracker();
	/** Container snapshots per dimension, keyed by {@code BlockPos.asLong()}. Kept for the session only. */
	private static final Map<String, Map<Long, ContainerSnapshot>> SNAPSHOTS = new HashMap<>();

	/** Lectern {page, pages} per dimension, from the last time the player read it. */
	private static final Map<String, Map<Long, int[]>> LECTERNS = new HashMap<>();

	private Gauge() {
	}

	static void load() {
		if (!Files.exists(FILE) && Files.isRegularFile(OLD_FILE)) {
			try {
				Files.createDirectories(DIR);
				Files.move(OLD_FILE, FILE);
			} catch (IOException e) {
				LOG.warn("Could not move {} to {}", OLD_FILE, FILE, e);
			}
		}
		config = GaugeConfig.load(FILE).clamp();
	}

	public static void save() {
		try {
			config.clamp().save(FILE);
		} catch (IOException e) {
			LOG.warn("Could not save {}", FILE, e);
		}
	}

	static Map<Long, ContainerSnapshot> snapshots(String dimension) {
		return SNAPSHOTS.computeIfAbsent(dimension, k -> new HashMap<>());
	}

	static ContainerSnapshot snapshot(String dimension, long pos) {
		Map<Long, ContainerSnapshot> m = SNAPSHOTS.get(dimension);
		return m == null ? null : m.get(pos);
	}

	static void rememberLectern(String dimension, long pos, int page, int pages) {
		LECTERNS.computeIfAbsent(dimension, k -> new HashMap<>()).put(pos, new int[] {page, pages});
	}

	static int[] lectern(String dimension, long pos) {
		Map<Long, int[]> m = LECTERNS.get(dimension);
		return m == null ? null : m.get(pos);
	}

	static int forgetSnapshots() {
		int n = SNAPSHOTS.values().stream().mapToInt(Map::size).sum() + LECTERNS.values().stream().mapToInt(Map::size).sum();
		SNAPSHOTS.clear();
		LECTERNS.clear();
		return n;
	}

	static void reset() {
		SNAPSHOTS.clear();
		LECTERNS.clear();
		PULSES.clear();
	}

	public static void say(String msg) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			mc.player.sendOverlayMessage(Component.literal("Gauge: " + msg).withStyle(ChatFormatting.GOLD));
		}
	}
}
