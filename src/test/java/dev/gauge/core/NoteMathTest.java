package dev.gauge.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NoteMathTest {
	@Test
	void harpNames() {
		assertEquals("F#3", NoteMath.name(0, "harp"));
		assertEquals("B3", NoteMath.name(5, "harp"));
		assertEquals("C4", NoteMath.name(6, "harp"));
		assertEquals("F#4", NoteMath.name(12, "harp"));
		assertEquals("C5", NoteMath.name(18, "harp"));
		assertEquals("F#5", NoteMath.name(24, "harp"));
	}

	@Test
	void instrumentOctaves() {
		assertEquals("F#1", NoteMath.name(0, "bass"));
		assertEquals("F#7", NoteMath.name(24, "bell"));
		assertEquals("A", NoteMath.name(3, "basedrum"), "drums have no octave");
	}

	@Test
	void frequencies() {
		// harp step 3 is A3 = 220 Hz, flute step 3 is A4 = 440 Hz
		assertEquals(220.0, NoteMath.frequency(3, "harp"), 1e-6);
		assertEquals(440.0, NoteMath.frequency(3, "flute"), 1e-6);
		assertEquals(-1, NoteMath.frequency(3, "snare"));
	}

	@Test
	void pitchAndClicks() {
		assertEquals(0.5, NoteMath.pitch(0), 1e-9);
		assertEquals(1.0, NoteMath.pitch(12), 1e-9);
		assertEquals(2.0, NoteMath.pitch(24), 1e-9);
		assertEquals(3, NoteMath.clicks(4, 7));
		assertEquals(22, NoteMath.clicks(7, 4));
	}

	@Test
	void particleColors() {
		// step 0 is green-ish yellow, 12 is pinkish; just check they differ and are opaque
		assertEquals(0xFF, NoteMath.color(0) >>> 24);
		assertNotEquals(NoteMath.color(0), NoteMath.color(12));
	}

	@Test
	void timeFormat() {
		assertEquals("0:00", NoteMath.time(0));
		assertEquals("1:05", NoteMath.time(65 * 20));
		assertEquals("2:28", NoteMath.time(2968));
	}
}
