package dev.gauge.client;

import dev.gauge.client.Inspector.Category;
import dev.gauge.client.Inspector.Info;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.vehicle.ContainerEntity;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DetectorRailBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.TripWireBlock;
import net.minecraft.world.level.block.TripWireHookBlock;
import net.minecraft.world.level.block.entity.BellBlockEntity;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Locale;

/** Blocks that move, open or react: doors, pistons, rails, bells, tripwires, TNT and beehives. */
final class Mechanisms {
	private Mechanisms() {
	}

	static boolean interesting(Block b) {
		return b instanceof DoorBlock || b instanceof TrapDoorBlock || b instanceof FenceGateBlock || b instanceof BellBlock
				|| b instanceof TripWireBlock || b instanceof PistonBaseBlock || b instanceof PoweredRailBlock
				|| b instanceof TntBlock || b instanceof BeehiveBlock;
	}

	/** Returns null when the block is not one of these. */
	static Info inspect(Level level, BlockPos pos, BlockState s, String dimension, String name, List<String> lines) {
		Block b = s.getBlock();
		if (b instanceof DoorBlock) {
			// the label goes on the lower half only; the upper half still fills the HUD panel
			boolean upper = s.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER;
			return openable(name, s.getValue(DoorBlock.OPEN), s.getValue(DoorBlock.POWERED), upper, lines);
		}
		if (b instanceof TrapDoorBlock) {
			return openable(name, s.getValue(TrapDoorBlock.OPEN), s.getValue(TrapDoorBlock.POWERED), false, lines);
		}
		if (b instanceof FenceGateBlock) {
			return openable(name, s.getValue(FenceGateBlock.OPEN), s.getValue(FenceGateBlock.POWERED), false, lines);
		}
		if (b instanceof PistonBaseBlock) {
			return piston(level, pos, s, name, lines);
		}
		if (b instanceof PoweredRailBlock) {
			boolean on = s.getValue(PoweredRailBlock.POWERED);
			boolean activator = s.is(Blocks.ACTIVATOR_RAIL);
			lines.add(on ? "Powered" : "Unpowered");
			if (activator) {
				lines.add(on ? "Locks hopper carts, primes TNT carts, ejects riders" : "Unlocks hopper carts that pass");
			} else {
				lines.add(on ? "Speeds carts up" : "Brakes carts");
				lines.add("Power spreads up to 8 rails along the line");
			}
			return new Info(Category.MECHANISM, name, on ? "on" : "off", on ? Inspector.RED : Inspector.GRAY, null, on, lines);
		}
		if (b instanceof DetectorRailBlock) {
			return detectorRail(level, pos, s, dimension, name, lines);
		}
		if (b instanceof TripWireBlock) {
			boolean tripped = s.getValue(TripWireBlock.POWERED);
			boolean attached = s.getValue(TripWireBlock.ATTACHED);
			boolean disarmed = s.getValue(TripWireBlock.DISARMED);
			lines.add(tripped ? "Tripped: something is on it" : "Not tripped");
			lines.add(attached ? "Connected to hooks" : "Not connected to two hooks");
			if (disarmed) {
				lines.add("Disarmed: cut with shears, breaking it will not trigger");
			}
			String label = tripped ? "tripped" : !attached ? "loose" : disarmed ? "disarmed" : "armed";
			return new Info(Category.TRIPWIRE, name, label, tripped ? Inspector.RED : attached ? Inspector.GREEN : Inspector.GRAY,
					null, tripped, lines);
		}
		if (b instanceof TripWireHookBlock) {
			boolean on = s.getValue(TripWireHookBlock.POWERED);
			boolean attached = s.getValue(TripWireHookBlock.ATTACHED);
			Direction facing = s.getValue(TripWireHookBlock.FACING);
			lines.add("Output: " + (on ? "15 (strong into the block behind)" : "0"));
			lines.add(attached ? "Wire connected" : "No complete wire (needs a hook at both ends, max 40 strings)");
			String label = on ? "15" : attached ? "armed" : "no wire";
			return new Info(Category.LOGIC, name, label, on ? Inspector.RED : attached ? Inspector.GREEN : Inspector.GRAY,
					on ? facing.getOpposite() : null, on, lines);
		}
		if (b instanceof BellBlock) {
			boolean powered = s.getValue(BellBlock.POWERED);
			boolean ringing = level.getBlockEntity(pos) instanceof BellBlockEntity be && be.shaking;
			if (ringing) {
				BellBlockEntity be = (BellBlockEntity) level.getBlockEntity(pos);
				lines.add(String.format(Locale.ROOT, "Ringing (%.1fs ago)", be.ticks / 20.0));
			} else {
				lines.add("Not ringing");
			}
			lines.add(powered ? "Powered (rings once when power turns on)" : "Unpowered");
			String label = ringing ? "ringing" : powered ? "powered" : "idle";
			return new Info(Category.MECHANISM, name, label, ringing ? Inspector.YELLOW : powered ? Inspector.RED : Inspector.GRAY,
					null, ringing || powered, lines);
		}
		if (b instanceof TntBlock) {
			if (!s.getValue(TntBlock.UNSTABLE)) {
				return null;
			}
			lines.add("Unstable: ignites when broken");
			return new Info(Category.MECHANISM, name, "unstable", Inspector.RED, null, true, lines);
		}
		if (b instanceof BeehiveBlock) {
			return beehive(pos, s, dimension, name, lines);
		}
		return null;
	}

	private static Info openable(String name, boolean open, boolean powered, boolean upper, List<String> lines) {
		lines.add(open ? "Open" : "Closed");
		lines.add(powered ? "Powered" : "Unpowered");
		boolean mismatch = open != powered;
		if (mismatch && powered) {
			lines.add("Powered but closed: toggled by hand since the power came on");
		}
		String label = upper ? "" : (open ? "open" : "closed") + (powered ? " P" : "");
		int color = mismatch && powered ? Inspector.ORANGE : open ? Inspector.GREEN : Inspector.GRAY;
		return new Info(Category.MECHANISM, name, label, color, null, open, lines);
	}

	private static Info piston(Level level, BlockPos pos, BlockState s, String name, List<String> lines) {
		Direction facing = s.getValue(PistonBaseBlock.FACING);
		boolean extended = s.getValue(PistonBaseBlock.EXTENDED);
		boolean sticky = s.is(Blocks.STICKY_PISTON);
		boolean direct = directSignal(level, pos, facing);
		boolean qc = !direct && quasiSignal(level, pos);
		boolean powered = direct || qc;
		lines.add((extended ? "Extended" : "Retracted") + ", facing " + Inspector.dir(facing));
		lines.add(direct ? "Powered" : qc ? "Powered by quasi-connectivity (the block above it is powered)" : "Unpowered");
		String label = sticky ? "sticky " : "";
		int color;
		if (powered && !extended) {
			lines.add("Powered but retracted: waiting for a block update (BUD),");
			lines.add("or the push is blocked (12+ blocks, immovable block)");
			label += "ret!";
			color = Inspector.ORANGE;
		} else if (!powered && extended) {
			lines.add("Unpowered but extended: waiting for a block update (BUD)");
			label += "ext!";
			color = Inspector.ORANGE;
		} else {
			label += extended ? "ext" : "ret";
			color = extended ? Inspector.GREEN : Inspector.GRAY;
		}
		return new Info(Category.MECHANISM, name, label, color, extended ? facing : null, powered, lines);
	}

	/** Vanilla's piston check for power from its own neighbours, minus the face it pushes from. */
	static boolean directSignal(Level level, BlockPos pos, Direction facing) {
		for (Direction d : Direction.values()) {
			if (d != facing && level.hasSignal(pos.relative(d), d)) {
				return true;
			}
		}
		return false;
	}

	/** The rest of vanilla's piston check: power at its own position from below, and around the block above. */
	static boolean quasiSignal(Level level, BlockPos pos) {
		if (level.hasSignal(pos, Direction.DOWN)) {
			return true;
		}
		BlockPos up = pos.above();
		for (Direction d : Direction.values()) {
			if (d != Direction.DOWN && level.hasSignal(up.relative(d), d)) {
				return true;
			}
		}
		return false;
	}

	private static Info detectorRail(Level level, BlockPos pos, BlockState s, String dimension, String name, List<String> lines) {
		boolean on = s.getValue(DetectorRailBlock.POWERED);
		lines.add("Output: " + (on ? "15 (cart on it)" : "0"));
		int cmp = -1;
		boolean exact = false;
		ServerPeek.Peek peek = ServerPeek.get(dimension, pos.asLong());
		if (peek != null && peek.analog() >= 0) {
			cmp = peek.analog();
			exact = true;
		} else if (on) {
			// same box vanilla searches
			AABB box = new AABB(pos.getX() + 0.2, pos.getY(), pos.getZ() + 0.2, pos.getX() + 0.8, pos.getY() + 0.8, pos.getZ() + 0.8);
			List<AbstractMinecart> carts = level.getEntitiesOfClass(AbstractMinecart.class, box);
			for (AbstractMinecart cart : carts) {
				if (cart instanceof ContainerEntity) {
					var snap = EntityInspector.snapshot(dimension, cart);
					cmp = snap == null ? -1 : snap.signal();
					break;
				}
				cmp = 0;
			}
		} else {
			cmp = 0;
		}
		lines.add("Comparator: " + (cmp < 0 ? "unknown (open the cart once)" : Integer.toString(cmp)));
		String label = (on ? "15" : "0") + (on && cmp != 0 ? " | " + (cmp < 0 ? "?" : cmp) : "");
		return new Info(Category.LOGIC, name, label, on ? Inspector.RED : Inspector.GRAY, null, on, lines);
	}

	private static Info beehive(BlockPos pos, BlockState s, String dimension, String name, List<String> lines) {
		int honey = s.getValue(BeehiveBlock.HONEY_LEVEL);
		ServerPeek.Peek peek = ServerPeek.get(dimension, pos.asLong());
		int bees = peek == null ? -1 : peek.bees();
		lines.add("Honey: " + honey + "/5" + (honey >= 5 ? " (ready: shears or bottle)" : "") + "   Comparator: " + honey);
		lines.add(bees < 0 ? "Bees inside: unknown (server-side; exact in singleplayer)" : "Bees inside: " + bees + "/3");
		if (peek != null && peek.sedated()) {
			lines.add("Smoked: campfire below, bees stay calm");
		}
		String label = "honey " + honey + "/5" + (bees >= 0 ? " | " + bees + " bee" + (bees == 1 ? "" : "s") : "");
		return new Info(Category.CONTAINER, name, label, honey >= 5 ? Inspector.ORANGE : Inspector.YELLOW, null, honey > 0, lines);
	}
}
