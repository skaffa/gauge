package dev.gauge.core;

/**
 * What a container held the last time the player had it open.
 *
 * <p>Vanilla servers never send container contents to clients until the container is opened, so this is
 * the only honest client-side source. It goes stale as soon as hoppers or other players move items.
 *
 * @param slots     container slots (a double chest has 54)
 * @param usedSlots slots holding anything
 * @param items     total item count
 * @param capacity  items that would fit if every slot were a full stack of what it holds (64 for empty)
 * @param fill      comparator fill fraction (0..1)
 * @param signal    comparator output (0..15)
 * @param seenAt    wall-clock millis when captured
 */
public record ContainerSnapshot(int slots, int usedSlots, int items, int capacity, double fill, int signal, long seenAt) {
	public static ContainerSnapshot of(int[] counts, int[] maxStacks, long seenAt) {
		int used = 0;
		int items = 0;
		int capacity = 0;
		for (int i = 0; i < counts.length; i++) {
			if (counts[i] > 0) {
				used++;
				items += counts[i];
				capacity += maxStacks[i];
			} else {
				capacity += 64;
			}
		}
		return new ContainerSnapshot(counts.length, used, items, capacity,
				ComparatorMath.fill(counts, maxStacks), ComparatorMath.signal(counts, maxStacks), seenAt);
	}

	public int percent() {
		// 1 item must not read as 0 %; a full container must not read 100 % before it really is
		if (fill <= 0) {
			return 0;
		}
		int p = (int) Math.floor(fill * 100);
		return Math.max(1, p);
	}

	/** "just now", "42s ago", "5m ago", "3h ago". */
	public String age(long now) {
		long s = Math.max(0, (now - seenAt) / 1000);
		if (s < 5) {
			return "just now";
		}
		if (s < 60) {
			return s + "s ago";
		}
		if (s < 3600) {
			return (s / 60) + "m ago";
		}
		return (s / 3600) + "h ago";
	}
}
