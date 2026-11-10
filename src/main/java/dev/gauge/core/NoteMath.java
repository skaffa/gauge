package dev.gauge.core;

import java.util.Map;

/**
 * Note block pitch maths, without Minecraft classes.
 *
 * <p>A note block has 25 steps (0..24, one right-click each), two octaves from F# to F#. Step 12 plays the
 * sound at its recorded pitch. Which octave that is depends on the instrument (the block underneath).
 */
public final class NoteMath {
	public static final int STEPS = 25;
	private static final String[] NAMES = {"F#", "G", "G#", "A", "A#", "B", "C", "C#", "D", "D#", "E", "F"};

	/** Octave of step 0 per tunable instrument (Minecraft wiki ranges). Missing = octave unknown. */
	private static final Map<String, Integer> LOW_OCTAVE = Map.ofEntries(
			Map.entry("bass", 1), Map.entry("didgeridoo", 1),
			Map.entry("guitar", 2),
			Map.entry("harp", 3), Map.entry("banjo", 3), Map.entry("bit", 3), Map.entry("iron_xylophone", 3), Map.entry("pling", 3),
			Map.entry("cow_bell", 4), Map.entry("flute", 4),
			Map.entry("bell", 5), Map.entry("chime", 5), Map.entry("xylophone", 5));

	private NoteMath() {
	}

	/** Pitch name without octave, e.g. "F#" for step 0 and 12. */
	public static String pitchClass(int step) {
		return NAMES[Math.floorMod(step, 12)];
	}

	/** Octave of step 0 for an instrument, or -1 when Gauge does not know it (drums, new instruments). */
	public static int lowOctave(String instrument) {
		return LOW_OCTAVE.getOrDefault(instrument, -1);
	}

	/** Scientific pitch name, e.g. harp step 6 = "C4". Without a known octave just the pitch class. */
	public static String name(int step, String instrument) {
		int low = lowOctave(instrument);
		if (low < 0) {
			return pitchClass(step);
		}
		// the octave number goes up at C, which is step 6 and 18
		return pitchClass(step) + (low + (step + 6) / 12);
	}

	/** Playback speed of the sample: 2^((step - 12) / 12), 0.5 .. 2.0. Same as vanilla. */
	public static double pitch(int step) {
		return Math.pow(2.0, (step - 12) / 12.0);
	}

	/** Frequency in Hz (A4 = 440), or -1 when the octave is unknown. */
	public static double frequency(int step, String instrument) {
		int low = lowOctave(instrument);
		if (low < 0) {
			return -1;
		}
		int midi = 12 * (low + 1) + 6 + step;
		return 440.0 * Math.pow(2.0, (midi - 69) / 12.0);
	}

	/** Right-clicks needed to go from {@code from} to {@code to} (it wraps from 24 back to 0). */
	public static int clicks(int from, int to) {
		return Math.floorMod(to - from, STEPS);
	}

	/** The colour vanilla gives the note particle for this step (a hue wheel over 0..24), as ARGB. */
	public static int color(int step) {
		double f = step / 24.0;
		int r = channel(f);
		int g = channel(f + 1.0 / 3.0);
		int b = channel(f + 2.0 / 3.0);
		return 0xFF000000 | r << 16 | g << 8 | b;
	}

	private static int channel(double f) {
		double v = Math.max(0.0, Math.sin(f * Math.PI * 2) * 0.65 + 0.35);
		return (int) Math.round(Math.min(1.0, v) * 255);
	}

	/** Ticks as "m:ss". */
	public static String time(long ticks) {
		long s = Math.max(0, ticks) / 20;
		return (s / 60) + ":" + (s % 60 < 10 ? "0" : "") + (s % 60);
	}
}
