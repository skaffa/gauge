package dev.gauge.core;

/**
 * Vanilla's comparator formula for containers, without Minecraft classes.
 *
 * <p>Each slot contributes {@code count / maxStackSize}; the sum is divided by the slot count to get the
 * fill fraction {@code f}. The signal is {@code floor(f * 14) + (f > 0 ? 1 : 0)}, so a single item gives 1
 * and only a completely full container gives 15.
 */
public final class ComparatorMath {
	private ComparatorMath() {
	}

	/**
	 * Fill fraction (0..1) as the comparator sees it.
	 *
	 * @param counts    item count per slot (0 = empty)
	 * @param maxStacks max stack size of the item in each slot (ignored for empty slots)
	 */
	public static double fill(int[] counts, int[] maxStacks) {
		if (counts.length == 0) {
			return 0;
		}
		double sum = 0;
		for (int i = 0; i < counts.length; i++) {
			if (counts[i] > 0) {
				sum += counts[i] / (double) Math.max(1, maxStacks[i]);
			}
		}
		return Math.min(1.0, sum / counts.length);
	}

	/** Comparator output (0..15) for a fill fraction. Uses float math like vanilla. */
	public static int signal(double fill) {
		float f = (float) fill;
		return (int) Math.floor(f * 14.0f) + (f > 0.0f ? 1 : 0);
	}

	public static int signal(int[] counts, int[] maxStacks) {
		if (counts.length == 0) {
			return 0;
		}
		// vanilla accumulates in float, divides, then lerps; mirror it exactly
		float sum = 0;
		for (int i = 0; i < counts.length; i++) {
			if (counts[i] > 0) {
				sum += counts[i] / (float) Math.max(1, maxStacks[i]);
			}
		}
		return signal(sum / counts.length);
	}

	/**
	 * Fewest items (of one stackable kind, max stack {@code maxStack}) that must be in a container of
	 * {@code slots} slots for the comparator to reach {@code signal}. Handy for item-counting clocks.
	 */
	public static int itemsForSignal(int signal, int slots, int maxStack) {
		if (signal <= 0) {
			return 0;
		}
		if (signal == 1) {
			return 1;
		}
		// need floor(items / (slots * maxStack) * 14) >= signal - 1
		int capacity = slots * maxStack;
		int items = (int) Math.ceil((signal - 1) * capacity / 14.0);
		// guard against float rounding at the boundary
		while (signalFor(items, slots, maxStack) < signal) {
			items++;
		}
		while (items > 1 && signalFor(items - 1, slots, maxStack) >= signal) {
			items--;
		}
		return items;
	}

	/** Signal for {@code items} items of one kind spread over full stacks in {@code slots} slots. */
	public static int signalFor(int items, int slots, int maxStack) {
		int[] counts = new int[slots];
		int[] max = new int[slots];
		int left = items;
		for (int i = 0; i < slots; i++) {
			max[i] = maxStack;
			counts[i] = Math.min(maxStack, Math.max(0, left));
			left -= counts[i];
		}
		return signal(counts, max);
	}
}
