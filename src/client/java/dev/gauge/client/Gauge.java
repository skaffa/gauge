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
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Shared client state: settings, pulse history and remembered container contents. */
public final class Gauge {
	public static final String MOD_ID = "gauge";
	public static final Logger LOG = LoggerFactory.getLogger("Gauge");
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("gauge.json");

	public static GaugeConfig config = new GaugeConfig();
	public static final PulseTracker PULSES = new PulseTracker();
	/** Container snapshots per dimension, keyed by {@code BlockPos.asLong()}. Kept for the session only. */
	private static final Map<String, Map<Long, ContainerSnapshot>> SNAPSHOTS = new HashMap<>();

	private Gauge() {
	}

	static void load() {
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

	static int forgetSnapshots() {
		int n = SNAPSHOTS.values().stream().mapToInt(Map::size).sum();
		SNAPSHOTS.clear();
		return n;
	}

	static void reset() {
		SNAPSHOTS.clear();
		PULSES.clear();
	}

	public static void say(String msg) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			mc.player.sendOverlayMessage(Component.literal("Gauge: " + msg).withStyle(ChatFormatting.GOLD));
		}
	}
}
