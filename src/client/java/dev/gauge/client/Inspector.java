package dev.gauge.client;

import dev.gauge.core.ContainerSnapshot;
import dev.gauge.core.NoteMath;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.CrafterBlock;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.RedstoneWireBlock;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.ComparatorMode;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Turns a block into what Gauge shows for it: a short in-world label, an output arrow and HUD lines.
 * Everything comes from client-side block states, except container contents (remembered from the last
 * time the player opened the container) and comparator output (estimated, the server never sends it).
 */
final class Inspector {
	enum Category {
		DUST, LOGIC, CONTAINER, SOUND, MECHANISM,
		/** Tripwire string: labelled in radius mode only while tripped. */
		TRIPWIRE, ENTITY
	}

	/**
	 * @param label  short in-world text
	 * @param color  ARGB label colour
	 * @param output direction the component outputs to, or null
	 * @param active powered / triggered / unlocked
	 */
	record Info(Category category, String name, String label, int color, Direction output, boolean active, List<String> lines) {
	}

	static final int GRAY = 0xFFA0A0A0;
	static final int OFF = 0xFF707070;
	static final int RED = 0xFFFF4040;
	static final int ORANGE = 0xFFFFA040;
	static final int YELLOW = 0xFFFFE060;
	static final int GREEN = 0xFF60E060;
	static final int BLUE = 0xFF60C0FF;

	/** Cheap palette test used to skip whole chunk sections. */
	static final Predicate<BlockState> INTERESTING = Inspector::interesting;

	private Inspector() {
	}

	static boolean interesting(BlockState s) {
		if (s.isAir()) {
			return false;
		}
		Block b = s.getBlock();
		return s.isSignalSource() || s.hasAnalogOutputSignal() || b instanceof RedstoneWireBlock || b instanceof HopperBlock
				|| b instanceof DispenserBlock || b instanceof CrafterBlock || b instanceof RedstoneLampBlock
				|| b instanceof NoteBlock || b instanceof JukeboxBlock || Mechanisms.interesting(b);
	}

	/** Colour of a 0..15 signal: grey at 0, dark to bright red as it rises. */
	static int powerColor(int p) {
		if (p <= 0) {
			return OFF;
		}
		int r = 0xFF;
		int g = 0x20 + p * 9;
		int b = 0x20 + p * 6;
		return 0xFF000000 | r << 16 | g << 8 | b;
	}

	/** Returns null for blocks Gauge has nothing to say about. */
	static Info inspect(Level level, BlockPos pos, BlockState s, String dimension) {
		Block b = s.getBlock();
		String name = b.getName().getString();
		List<String> lines = new ArrayList<>();

		if (b instanceof RedstoneWireBlock) {
			int p = s.getValue(RedstoneWireBlock.POWER);
			lines.add("Power: " + p);
			return new Info(Category.DUST, name, Integer.toString(p), powerColor(p), null, p > 0, lines);
		}
		if (b instanceof RepeaterBlock) {
			int delay = s.getValue(RepeaterBlock.DELAY);
			boolean on = s.getValue(DiodeBlock.POWERED);
			boolean locked = s.getValue(RepeaterBlock.LOCKED);
			Direction out = s.getValue(DiodeBlock.FACING).getOpposite();
			lines.add("Delay: " + delay + " redstone tick" + (delay == 1 ? "" : "s") + " (" + delay * 2 + " game ticks)");
			lines.add("Output: " + (on ? "15" : "0") + " -> " + dir(out));
			if (locked) {
				lines.add("Locked by a side repeater/comparator");
			}
			String label = delay + "t" + (locked ? " L" : "");
			return new Info(Category.LOGIC, name, label, locked ? BLUE : on ? RED : GRAY, out, on, lines);
		}
		if (b instanceof ComparatorBlock) {
			boolean subtract = s.getValue(ComparatorBlock.MODE) == ComparatorMode.SUBTRACT;
			boolean on = s.getValue(DiodeBlock.POWERED);
			Direction out = s.getValue(DiodeBlock.FACING).getOpposite();
			ServerPeek.Peek peek = ServerPeek.get(dimension, pos.asLong());
			boolean exact = peek != null && peek.comparatorOut() >= 0;
			int est = exact ? peek.comparatorOut() : estimateComparator(level, pos, s, dimension);
			String value = est < 0 ? (on ? "on" : "off") : exact ? Integer.toString(est) : "~" + est;
			lines.add("Mode: " + (subtract ? "subtract" : "compare"));
			lines.add("Output: " + value + (est >= 0 && !exact ? " (estimated)" : "") + " -> " + dir(out));
			if (est < 0) {
				lines.add("Input unknown: open the container behind it once");
			}
			String label = (subtract ? "sub " : "cmp ") + value;
			return new Info(Category.LOGIC, name, label, on ? RED : GRAY, out, on, lines);
		}
		if (b instanceof ObserverBlock) {
			boolean on = s.getValue(ObserverBlock.POWERED);
			Direction face = s.getValue(ObserverBlock.FACING);
			lines.add("Watching: " + dir(face));
			lines.add("Pulses out: " + dir(face.getOpposite()));
			return new Info(Category.LOGIC, name, on ? "obs 15" : "obs", on ? RED : GRAY, face.getOpposite(), on, lines);
		}
		if (b instanceof HopperBlock) {
			Direction out = s.getValue(HopperBlock.FACING);
			boolean enabled = s.getValue(HopperBlock.ENABLED);
			lines.add("Transfers: " + dir(out) + (enabled ? "" : " (LOCKED by redstone)"));
			return container(level, pos, s, dimension, name, out, enabled, enabled ? null : "locked", lines);
		}
		if (b instanceof DispenserBlock) {
			Direction out = s.getValue(DispenserBlock.FACING);
			boolean triggered = s.getValue(DispenserBlock.TRIGGERED);
			lines.add("Facing: " + dir(out) + (triggered ? " (powered)" : ""));
			return container(level, pos, s, dimension, name, out, triggered, null, lines);
		}

		Info mech = Mechanisms.inspect(level, pos, s, dimension, name, lines);
		if (mech != null || b instanceof TntBlock) {
			return mech;
		}

		if (b instanceof NoteBlock) {
			return noteBlock(s, name, lines);
		}
		if (b instanceof JukeboxBlock) {
			return jukebox(level, pos, s, dimension, name, lines);
		}
		if (b instanceof LecternBlock) {
			return lectern(pos, s, dimension, name, lines);
		}

		BlockEntity be = level.getBlockEntity(pos);
		if (be instanceof BaseContainerBlockEntity) {
			return container(level, pos, s, dimension, name, null, false, null, lines);
		}

		if (s.isSignalSource()) {
			int p = ownSignal(level, pos, s);
			int strong = 0;
			for (Direction d : Direction.values()) {
				strong = Math.max(strong, s.getDirectSignal(level, pos, d));
			}
			lines.add("Output: " + p + (strong > 0 ? " (strong " + strong + ")" : ""));
			return new Info(Category.LOGIC, name, Integer.toString(p), powerColor(p), null, p > 0, lines);
		}
		if (s.hasAnalogOutputSignal()) {
			int a = analogValue(level, pos, s, dimension);
			lines.add("Comparator reads: " + (a < 0 ? "unknown (server-side data)" : a));
			return new Info(Category.CONTAINER, name, "cmp " + (a < 0 ? "?" : a), a < 0 ? GRAY : YELLOW, null, a > 0, lines);
		}
		if (b instanceof RedstoneLampBlock) {
			boolean lit = s.getValue(BlockStateProperties.LIT);
			lines.add(lit ? "Lit" : "Off");
			return new Info(Category.LOGIC, name, lit ? "on" : "off", lit ? YELLOW : GRAY, null, lit, lines);
		}
		if (b instanceof CrafterBlock) {
			boolean triggered = s.getValue(CrafterBlock.TRIGGERED);
			lines.add(triggered ? "Powered" : "Unpowered");
			return container(level, pos, s, dimension, name, null, triggered, null, lines);
		}
		return null;
	}

	private static Info noteBlock(BlockState s, String name, List<String> lines) {
		NoteBlockInstrument inst = s.getValue(NoteBlock.INSTRUMENT);
		int step = s.getValue(NoteBlock.NOTE);
		boolean on = s.getValue(NoteBlock.POWERED);
		String id = inst.getSerializedName();
		String instName = id.replace('_', ' ');
		lines.add("Instrument: " + instName + (inst.worksAboveNoteBlock() ? " (head on top)" : " (block below)"));
		if (!inst.isTunable()) {
			lines.add("Fixed sound: tuning has no effect");
			return new Info(Category.SOUND, name, instName, GRAY, null, on, lines);
		}
		String note = NoteMath.name(step, id);
		lines.add("Note: " + note + "  (step " + step + "/24 = clicks from F#)");
		double hz = NoteMath.frequency(step, id);
		lines.add(String.format(Locale.ROOT, "Pitch: x%.3f", NoteMath.pitch(step))
				+ (hz > 0 ? String.format(Locale.ROOT, "  ~%.1f Hz", hz) : ""));
		lines.add("Next click: " + NoteMath.name((step + 1) % NoteMath.STEPS, id));
		if (on) {
			lines.add("Powered");
		}
		String label = NoteMath.lowOctave(id) < 0 ? note + " " + step : note;
		return new Info(Category.SOUND, name, label, NoteMath.color(step), null, on, lines);
	}

	private static Info jukebox(Level level, BlockPos pos, BlockState s, String dimension, String name, List<String> lines) {
		if (!s.getValue(JukeboxBlock.HAS_RECORD)) {
			lines.add("Empty");
			return new Info(Category.SOUND, name, "empty", GRAY, null, false, lines);
		}
		ServerPeek.Peek peek = ServerPeek.get(dimension, pos.asLong());
		JukeboxTracker.Playing playing = peek != null && peek.song() != null
				? new JukeboxTracker.Playing(peek.song(), level.getGameTime() - peek.songTicks())
				: JukeboxTracker.get(dimension, pos.asLong());
		if (playing == null && peek != null) {
			lines.add("Has a disc; not playing");
			return new Info(Category.SOUND, name, "disc", GRAY, null, false, lines);
		}
		if (playing == null) {
			lines.add("Has a disc; song unknown");
			lines.add("(it started before you came near, or has ended)");
			return new Info(Category.SOUND, name, "disc ?", GRAY, null, true, lines);
		}
		long elapsed = playing.elapsed(level.getGameTime());
		int length = playing.song().lengthInTicks();
		boolean done = playing.song().hasFinished(elapsed);
		int pct = length <= 0 ? 100 : (int) Math.min(100, elapsed * 100 / length);
		lines.add(playing.song().description().getString());
		lines.add(done ? "Finished (" + NoteMath.time(length) + ")"
				: NoteMath.time(elapsed) + " / " + NoteMath.time(length) + "  (" + pct + "%)");
		lines.add("Comparator: " + playing.song().comparatorOutput());
		String label = done ? "done" : "\u266A " + NoteMath.time(elapsed) + "/" + NoteMath.time(length);
		return new Info(Category.SOUND, name, label, done ? GRAY : 0xFF80FFB0, null, !done, lines);
	}

	private static Info lectern(BlockPos pos, BlockState s, String dimension, String name, List<String> lines) {
		if (!s.getValue(LecternBlock.HAS_BOOK)) {
			lines.add("No book. Comparator: 0");
			return new Info(Category.CONTAINER, name, "no book", GRAY, null, false, lines);
		}
		int[] pp = lecternPage(dimension, pos.asLong());
		boolean pulse = s.getValue(LecternBlock.POWERED);
		if (pp == null) {
			lines.add("Page unknown: open it once to read it");
			lines.add("Pulses 15 for 2 game ticks when a page turns");
			return new Info(Category.CONTAINER, name, "book ?", GRAY, null, pulse, lines);
		}
		int signal = LecternPages.signal(pp[0], pp[1]);
		lines.add("Page " + (pp[0] + 1) + "/" + pp[1] + "   Comparator: " + signal);
		lines.add(pp[2] == 1 ? "Live (singleplayer)" : "Last seen when you read it");
		lines.add("Pulses 15 for 2 game ticks when a page turns");
		return new Info(Category.CONTAINER, name, "p" + (pp[0] + 1) + "/" + pp[1] + " | " + signal, ORANGE, null, pulse, lines);
	}

	/** {page, pages, live 1/0} from the singleplayer server or the last time the player read it; else null. */
	private static int[] lecternPage(String dimension, long pos) {
		ServerPeek.Peek peek = ServerPeek.get(dimension, pos);
		if (peek != null && peek.lecternPage() >= 0) {
			return new int[] {peek.lecternPage(), peek.lecternPages(), 1};
		}
		int[] seen = Gauge.lectern(dimension, pos);
		return seen == null ? null : new int[] {seen[0], seen[1], 0};
	}

	/**
	 * Comparator reading of a block, or -1 when it depends on server-side data the client lacks.
	 * Blocks without a block entity (composter, cauldron, cake...) are exact on the client.
	 */
	static int analogValue(Level level, BlockPos pos, BlockState s, String dimension) {
		ServerPeek.Peek peek = ServerPeek.get(dimension, pos.asLong());
		if (peek != null && peek.analog() >= 0) {
			return peek.analog();
		}
		Block b = s.getBlock();
		BlockEntity be = level.getBlockEntity(pos);
		if (be instanceof BaseContainerBlockEntity) {
			ContainerSnapshot snap = Gauge.snapshot(dimension, pos.asLong());
			return snap == null ? -1 : snap.signal();
		}
		if (b instanceof LecternBlock) {
			if (!s.getValue(LecternBlock.HAS_BOOK)) {
				return 0;
			}
			int[] pp = lecternPage(dimension, pos.asLong());
			return pp == null ? -1 : LecternPages.signal(pp[0], pp[1]);
		}
		if (b instanceof JukeboxBlock) {
			if (!s.getValue(JukeboxBlock.HAS_RECORD)) {
				return 0;
			}
			JukeboxTracker.Playing playing = JukeboxTracker.get(dimension, pos.asLong());
			return playing == null ? -1 : playing.song().comparatorOutput();
		}
		if (be == null || b instanceof BeehiveBlock) {
			return s.getAnalogOutputSignal(level, pos, Direction.NORTH);
		}
		return -1;
	}

	static Info container(Level level, BlockPos pos, BlockState s, String dimension, String name, Direction out,
			boolean active, String extra, List<String> lines) {
		ServerPeek.Peek peek = ServerPeek.get(dimension, pos.asLong());
		boolean live = peek != null && peek.container() != null;
		ContainerSnapshot snap = live ? peek.container() : Gauge.snapshot(dimension, pos.asLong());
		String label;
		int color;
		if (snap == null) {
			label = "?";
			color = GRAY;
			lines.add("Contents unknown: open it once to read it");
		} else {
			label = snap.percent() + "% | " + snap.signal();
			color = snap.signal() >= 15 ? RED : snap.signal() == 0 ? GRAY : GREEN;
			lines.add("Items: " + snap.items() + "/" + snap.capacity() + " (" + snap.usedSlots() + "/" + snap.slots() + " slots)");
			lines.add("Fill: " + snap.percent() + "%   Comparator: " + snap.signal());
			lines.add(live ? "Live (singleplayer)" : "Seen " + snap.age(System.currentTimeMillis()));
		}
		if (s.getBlock() instanceof ChestBlock && s.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
			lines.add("Double chest");
		}
		if (s.getBlock() instanceof AbstractFurnaceBlock) {
			boolean lit = s.getValue(AbstractFurnaceBlock.LIT);
			if (peek != null && peek.furnaceFuel() >= 0) {
				if (lit && peek.furnaceFuel() <= 0) {
					lines.add("Lit without fuel burning (set by a command or another mod)");
					extra = "lit!";
				} else if (peek.furnaceFuel() > 0) {
					lines.add("Burning: " + EntityInspector.seconds(peek.furnaceFuel()) + " of fuel left (of "
							+ EntityInspector.seconds(peek.furnaceFuelTotal()) + ")");
					extra = "lit";
				} else {
					lines.add("Not lit");
				}
				if (peek.cookTotal() > 0 && peek.cook() > 0) {
					lines.add("Cooking: " + peek.cook() * 100 / peek.cookTotal() + "%");
				}
			} else {
				lines.add(lit ? "Lit (fuel left is server-side; exact in singleplayer)" : "Not lit");
				extra = lit ? "lit" : null;
			}
		}
		if (extra != null) {
			label = label + " " + extra;
		}
		return new Info(Category.CONTAINER, name, label, color, out, active, lines);
	}

	/** Strongest weak signal the block emits in any direction (what dust next to it would see). */
	static int ownSignal(Level level, BlockPos pos, BlockState s) {
		int p = 0;
		for (Direction d : Direction.values()) {
			p = Math.max(p, s.getSignal(level, pos, d));
		}
		return p;
	}

	/**
	 * The server keeps comparator output in its block entity and never syncs it, so recompute it from the
	 * inputs the client can see. Returns -1 when it cannot be worked out (rear container never opened).
	 */
	static int estimateComparator(Level level, BlockPos pos, BlockState s, String dimension) {
		Direction facing = s.getValue(DiodeBlock.FACING);
		BlockPos rearPos = pos.relative(facing);
		int rear = rearInput(level, rearPos, facing, dimension);
		if (rear < 0) {
			return -1;
		}
		int side = 0;
		for (Direction d : new Direction[] {facing.getClockWise(), facing.getCounterClockWise()}) {
			side = Math.max(side, level.getControlInputSignal(pos.relative(d), d, true));
		}
		boolean subtract = s.getValue(ComparatorBlock.MODE) == ComparatorMode.SUBTRACT;
		return subtract ? Math.max(rear - side, 0) : rear >= side ? rear : 0;
	}

	private static int rearInput(Level level, BlockPos rearPos, Direction facing, String dimension) {
		BlockState rs = level.getBlockState(rearPos);
		int signal = level.getSignal(rearPos, facing);
		if (rs.getBlock() instanceof RedstoneWireBlock) {
			signal = Math.max(signal, rs.getValue(RedstoneWireBlock.POWER));
		}
		if (rs.hasAnalogOutputSignal()) {
			int a = analog(level, rearPos, rs, facing, dimension);
			return a < 0 ? -1 : Math.max(signal, a);
		}
		if (rs.isRedstoneConductor(level, rearPos) && signal < 15) {
			// comparators read containers through one solid block
			BlockPos behind = rearPos.relative(facing);
			BlockState bs = level.getBlockState(behind);
			if (bs.hasAnalogOutputSignal()) {
				int a = analog(level, behind, bs, facing, dimension);
				return a < 0 ? -1 : Math.max(signal, a);
			}
		}
		return signal;
	}

	private static int analog(Level level, BlockPos pos, BlockState s, Direction facing, String dimension) {
		return analogValue(level, pos, s, dimension);
	}

	static String dir(Direction d) {
		return d.getName().toUpperCase(Locale.ROOT);
	}
}
