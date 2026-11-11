package dev.gauge.client;

import dev.gauge.client.Inspector.Category;
import dev.gauge.client.Inspector.Info;
import dev.gauge.client.mixin.MinecartFurnaceAccessor;
import dev.gauge.core.ContainerSnapshot;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.vehicle.ContainerEntity;
import net.minecraft.world.entity.vehicle.boat.AbstractChestBoat;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecartContainer;
import net.minecraft.world.entity.vehicle.minecart.MinecartFurnace;
import net.minecraft.world.entity.vehicle.minecart.MinecartHopper;
import net.minecraft.world.entity.vehicle.minecart.MinecartTNT;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Labels for minecarts, chest boats and lit TNT. */
final class EntityInspector {
	private EntityInspector() {
	}

	static boolean interesting(Entity e) {
		return e instanceof PrimedTnt || e instanceof MinecartTNT || e instanceof MinecartFurnace
				|| e instanceof AbstractMinecartContainer || e instanceof AbstractChestBoat;
	}

	/** Contents of a container entity: exact in singleplayer, else from the last time the player opened it. */
	static ContainerSnapshot snapshot(String dimension, Entity e) {
		ServerPeek.EntityPeek peek = ServerPeek.get(dimension, e);
		if (peek != null && peek.container() != null) {
			return peek.container();
		}
		return Gauge.entitySnapshot(e.getUUID());
	}

	static Info inspect(Entity e, String dimension) {
		String name = e.getName().getString();
		List<String> lines = new ArrayList<>();
		if (e instanceof PrimedTnt tnt) {
			int fuse = tnt.getFuse();
			lines.add("Fuse: " + fuse + " game ticks (" + seconds(fuse) + ")");
			return new Info(Category.ENTITY, name, seconds(fuse), Inspector.RED, null, true, lines);
		}
		if (e instanceof MinecartTNT cart) {
			if (cart.isPrimed()) {
				int fuse = cart.getFuse();
				lines.add("Primed. Fuse: " + fuse + " game ticks (" + seconds(fuse) + ")");
				return new Info(Category.ENTITY, name, seconds(fuse), Inspector.RED, null, true, lines);
			}
			lines.add("Not primed");
			lines.add("Primes on a powered activator rail, fire, or a hard hit");
			return new Info(Category.ENTITY, name, "TNT", Inspector.GRAY, null, false, lines);
		}
		if (e instanceof MinecartFurnace furnace) {
			ServerPeek.EntityPeek peek = ServerPeek.get(dimension, e);
			boolean fuel = ((MinecartFurnaceAccessor) furnace).gauge$hasFuel();
			String label;
			if (peek != null && peek.fuel() >= 0) {
				int ticks = peek.fuel();
				lines.add(ticks > 0 ? "Fuel: " + ticks + " game ticks (" + seconds(ticks) + ")" : "No fuel");
				label = ticks > 0 ? "fuel " + seconds(ticks) : "no fuel";
				fuel = ticks > 0;
			} else {
				lines.add(fuel ? "Has fuel (time left is server-side)" : "No fuel");
				label = fuel ? "fuel" : "no fuel";
			}
			lines.add("Each coal or charcoal adds 3600 game ticks (3 min)");
			return new Info(Category.ENTITY, name, label, fuel ? Inspector.ORANGE : Inspector.GRAY, null, fuel, lines);
		}
		if (e instanceof ContainerEntity) {
			ContainerSnapshot snap = snapshot(dimension, e);
			ServerPeek.EntityPeek peek = ServerPeek.get(dimension, e);
			String label;
			int color;
			if (snap == null) {
				label = "?";
				color = Inspector.GRAY;
				lines.add("Contents unknown: open it once to read it");
			} else {
				label = snap.percent() + "% | " + snap.signal();
				color = snap.signal() >= 15 ? Inspector.RED : snap.signal() == 0 ? Inspector.GRAY : Inspector.GREEN;
				lines.add("Items: " + snap.items() + "/" + snap.capacity() + " (" + snap.usedSlots() + "/" + snap.slots() + " slots)");
				lines.add("Fill: " + snap.percent() + "%" + (e instanceof AbstractMinecartContainer ? "   Detector rail comparator: " + snap.signal() : ""));
				lines.add(peek != null && peek.container() != null ? "Live (singleplayer)" : "Seen " + snap.age(System.currentTimeMillis()));
			}
			boolean active = true;
			if (e instanceof MinecartHopper) {
				int enabled = peek != null ? peek.hopperEnabled() : railLock(e);
				if (enabled == 0) {
					label += " locked";
					active = false;
					lines.add("Locked (a powered activator rail locks it, an unpowered one unlocks it)");
				} else if (enabled == 1) {
					lines.add("Unlocked: pulls items from above and from the block it rides over");
				} else {
					lines.add("Lock state unknown (set by the last activator rail it passed)");
				}
			}
			return new Info(Category.ENTITY, name, label, color, null, active, lines);
		}
		return null;
	}

	/** 0 when the hopper cart sits on a powered activator rail (locked), 1 on an unpowered one, -1 elsewhere. */
	private static int railLock(Entity e) {
		BlockPos pos = e.blockPosition();
		for (BlockPos p : new BlockPos[] {pos, pos.below()}) {
			BlockState s = e.level().getBlockState(p);
			if (s.is(Blocks.ACTIVATOR_RAIL)) {
				return s.getValue(PoweredRailBlock.POWERED) ? 0 : 1;
			}
		}
		return -1;
	}

	static String seconds(int ticks) {
		return String.format(Locale.ROOT, "%.1fs", ticks / 20.0);
	}
}
