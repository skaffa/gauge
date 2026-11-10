package dev.gauge.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CoreTest {
	private static int[] filled(int slots, int count) {
		int[] a = new int[slots];
		a[0] = count;
		return a;
	}

	private static int[] all(int slots, int v) {
		int[] a = new int[slots];
		java.util.Arrays.fill(a, v);
		return a;
	}

	@Test
	void emptyContainerIsZero() {
		assertEquals(0, ComparatorMath.signal(new int[27], all(27, 64)));
	}

	@Test
	void oneItemIsOne() {
		assertEquals(1, ComparatorMath.signal(filled(27, 1), all(27, 64)));
		assertEquals(1, ComparatorMath.signal(filled(5, 1), all(5, 64)));
	}

	@Test
	void fullIsFifteen() {
		assertEquals(15, ComparatorMath.signal(all(27, 64), all(27, 64)));
		assertEquals(15, ComparatorMath.signal(all(5, 16), all(5, 16)));
	}

	@Test
	void knownHopperValues() {
		// well-known table: hopper (5 slots) needs 23 items for signal 2, 46 for 3
		assertEquals(2, ComparatorMath.signalFor(23, 5, 64));
		assertEquals(1, ComparatorMath.signalFor(22, 5, 64));
		assertEquals(2, ComparatorMath.signalFor(45, 5, 64));
		assertEquals(3, ComparatorMath.signalFor(46, 5, 64));
		assertEquals(23, ComparatorMath.itemsForSignal(2, 5, 64));
		assertEquals(46, ComparatorMath.itemsForSignal(3, 5, 64));
	}

	@Test
	void knownChestValues() {
		// single chest: 124 items for signal 2, 1728 (full) for 15
		assertEquals(124, ComparatorMath.itemsForSignal(2, 27, 64));
		assertEquals(1728, ComparatorMath.itemsForSignal(15, 27, 64));
		assertEquals(14, ComparatorMath.signalFor(1727, 27, 64));
	}

	@Test
	void unstackablesCountAsFullSlots() {
		// one sword in a hopper slot = a full slot = 1/5 fill
		int[] counts = filled(5, 1);
		int[] max = all(5, 64);
		max[0] = 1;
		assertEquals(0.2, ComparatorMath.fill(counts, max), 1e-9);
		assertEquals(3, ComparatorMath.signal(counts, max));
	}

	@Test
	void snapshotSummary() {
		int[] counts = {64, 32, 0, 0, 0};
		int[] max = {64, 64, 64, 64, 64};
		ContainerSnapshot s = ContainerSnapshot.of(counts, max, 0);
		assertEquals(2, s.usedSlots());
		assertEquals(96, s.items());
		assertEquals(320, s.capacity());
		assertEquals(30, s.percent());
		assertEquals(5, s.signal());
		assertEquals(1, ContainerSnapshot.of(filled(27, 1), all(27, 64), 0).percent());
		assertEquals("42s ago", s.age(42_000));
	}

	@Test
	void pulseClockDetected() {
		PulseTracker t = new PulseTracker();
		// 2 ticks on, 3 ticks off, repeating
		long tick = 100;
		for (int i = 0; i < 6; i++) {
			t.record(1L, tick);
			tick += (i % 2 == 0) ? 2 : 3;
		}
		PulseTracker.Pulse p = t.get(1L, tick, 40);
		assertNotNull(p);
		assertEquals(5, p.clockPeriod());
		assertTrue(p.fast());
	}

	@Test
	void singlePulseHasNoPeriod() {
		PulseTracker t = new PulseTracker();
		t.record(7L, 10);
		t.record(7L, 14);
		PulseTracker.Pulse p = t.get(7L, 20, 40);
		assertEquals(4, p.lastInterval());
		assertEquals(-1, p.clockPeriod());
		assertFalse(p.fast());
		assertNull(t.get(7L, 100, 40), "quiet blocks expire");
		t.prune(100, 40);
		assertEquals(0, t.size());
	}

	@Test
	void sameTickUpdatesCountOnce() {
		PulseTracker t = new PulseTracker();
		t.record(3L, 5);
		t.record(3L, 5);
		assertEquals(1, t.get(3L, 5, 40).changes());
	}

	@Test
	void historyWrapsAround() {
		PulseTracker t = new PulseTracker();
		for (int i = 0; i < 50; i++) {
			t.record(9L, i * 4L);
		}
		PulseTracker.Pulse p = t.get(9L, 196, 40);
		assertEquals(8, p.clockPeriod());
		assertEquals(4, p.shortestInterval());
	}

	@Test
	void configRoundTripAndClamp(@TempDir Path dir) throws Exception {
		Path f = dir.resolve("gauge.json");
		GaugeConfig c = new GaugeConfig();
		c.radius = 99;
		c.mode = GaugeConfig.Mode.FOCUS;
		c.clamp().save(f);
		GaugeConfig back = GaugeConfig.load(f);
		assertEquals(GaugeConfig.MAX_RADIUS, back.radius);
		assertEquals(GaugeConfig.Mode.FOCUS, back.mode);
		Files.writeString(f, "{not json");
		assertEquals(10, GaugeConfig.load(f).radius);
	}
}
