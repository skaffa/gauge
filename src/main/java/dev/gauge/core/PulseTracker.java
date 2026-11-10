package dev.gauge.core;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Remembers when each block last changed state (in game ticks) and derives pulse timing from it.
 *
 * <p>The client only sees the state the server sends at the end of each tick, so pulses shorter than a
 * tick (0-tick pulses) never show up here. Everything from 1 game tick upward does.
 */
public final class PulseTracker {
	/** Changes kept per block. Enough to recognise an on/off clock pattern. */
	static final int HISTORY = 8;

	private final Map<Long, Entry> entries = new HashMap<>();

	/** Timing summary for one block. */
	public record Pulse(int changes, long lastChange, int lastInterval, int clockPeriod, int shortestInterval) {
		/** True when the block toggles at least every two game ticks: rapid pulses, burnout risk. */
		public boolean fast() {
			return shortestInterval > 0 && shortestInterval <= 2;
		}
	}

	private static final class Entry {
		final long[] ticks = new long[HISTORY];
		int count;

		void add(long tick) {
			if (count > 0 && ticks[(count - 1) % HISTORY] == tick) {
				return; // several updates in one tick count once
			}
			ticks[count % HISTORY] = tick;
			count++;
		}

		long last() {
			return ticks[(count - 1) % HISTORY];
		}

		/** i = 0 is the newest interval. */
		int interval(int i) {
			int newer = count - 1 - i;
			int older = newer - 1;
			if (older < 0 || count - older > HISTORY) {
				return -1;
			}
			return (int) (ticks[newer % HISTORY] - ticks[older % HISTORY]);
		}
	}

	public void record(long pos, long tick) {
		entries.computeIfAbsent(pos, k -> new Entry()).add(tick);
	}

	/** Timing for a block that changed within {@code window} ticks of {@code now}, else null. */
	public Pulse get(long pos, long now, int window) {
		Entry e = entries.get(pos);
		if (e == null || now - e.last() > window) {
			return null;
		}
		int last = e.interval(0);
		int shortest = Integer.MAX_VALUE;
		for (int i = 0; i < HISTORY - 1; i++) {
			int iv = e.interval(i);
			if (iv < 0) {
				break;
			}
			// only intervals that are themselves recent count as "current" behaviour
			shortest = Math.min(shortest, iv);
		}
		int period = clockPeriod(e);
		return new Pulse(e.count, e.last(), last, period, shortest == Integer.MAX_VALUE ? -1 : shortest);
	}

	/**
	 * A clock alternates on and off with fixed durations, so intervals repeat as a, b, a, b.
	 * Returns a + b when the last four intervals follow that pattern, else -1.
	 */
	private static int clockPeriod(Entry e) {
		int a = e.interval(0);
		int b = e.interval(1);
		int c = e.interval(2);
		int d = e.interval(3);
		if (a <= 0 || b <= 0 || c <= 0 || d <= 0) {
			return -1;
		}
		return a == c && b == d ? a + b : -1;
	}

	/** Drops blocks that have been quiet for longer than {@code maxAge} ticks. */
	public void prune(long now, int maxAge) {
		Iterator<Entry> it = entries.values().iterator();
		while (it.hasNext()) {
			if (now - it.next().last() > maxAge) {
				it.remove();
			}
		}
	}

	public int size() {
		return entries.size();
	}

	public void clear() {
		entries.clear();
	}
}
