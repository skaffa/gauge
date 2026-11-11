package dev.gauge.client;

import dev.gauge.core.ContainerSnapshot;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.vehicle.ContainerEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.LecternMenu;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.BlockHitResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Remembers what containers hold by reading the menu while the player has one open.
 *
 * <p>The menu does not say which block it belongs to, so the block the player last right-clicked is
 * used, and only if the menu's container slot count matches that block's size.
 */
final class ContainerTracker {
	/** A right-click this many ticks before the screen opened still counts (server round trip). */
	private static final int OPEN_WINDOW = 60;

	private static BlockPos clicked;
	private static String clickedDim;
	private static long clickedTick;
	/** Where the open screen's contents go, or null when the open screen is not a tracked container. */
	private static List<BlockPos> bound;
	private static Screen boundScreen;

	private ContainerTracker() {
	}

	/** Container entity (minecart, chest boat) the player last right-clicked, or null. */
	private static Entity clickedEntity;
	/** Entity the open screen's contents go to, or null. */
	private static Entity boundEntity;

	static InteractionResult onUseEntity(Player player, Level level, InteractionHand hand, Entity entity, EntityHitResult hit) {
		if (level.isClientSide() && entity instanceof ContainerEntity) {
			clickedEntity = entity;
			clicked = null;
			clickedDim = GaugeClient.dimension(level);
			clickedTick = level.getGameTime();
		}
		return InteractionResult.PASS;
	}

	static InteractionResult onUse(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		if (level.isClientSide()) {
			clickedEntity = null;
			clicked = hit.getBlockPos().immutable();
			clickedDim = GaugeClient.dimension(level);
			clickedTick = level.getGameTime();
		}
		return InteractionResult.PASS;
	}

	/** Lectern the open lectern menu belongs to. */
	private static BlockPos lecternPos;

	static void tick(Minecraft mc, String dimension) {
		trackLectern(mc, dimension);
		Screen screen = mc.gui.screen();
		if (!(screen instanceof AbstractContainerScreen<?> acs) || mc.level == null) {
			bound = null;
			boundEntity = null;
			boundScreen = null;
			return;
		}
		if (screen != boundScreen) {
			boundScreen = screen;
			boundEntity = bindEntity(mc.level, acs.getMenu(), dimension);
			bound = boundEntity == null ? bind(mc.level, acs.getMenu(), dimension) : null;
		}
		if (bound == null && boundEntity == null) {
			return;
		}
		ContainerSnapshot snap = read(acs.getMenu());
		if (snap == null) {
			return;
		}
		if (boundEntity != null) {
			Gauge.rememberEntity(boundEntity.getUUID(), snap);
			return;
		}
		var map = Gauge.snapshots(dimension);
		for (BlockPos p : bound) {
			map.put(p.asLong(), snap);
		}
	}

	private static void trackLectern(Minecraft mc, String dimension) {
		if (mc.player == null || mc.level == null || !(mc.player.containerMenu instanceof LecternMenu menu)) {
			lecternPos = null;
			return;
		}
		if (lecternPos == null) {
			if (clicked == null || !dimension.equals(clickedDim) || mc.level.getGameTime() - clickedTick > OPEN_WINDOW
					|| !(mc.level.getBlockState(clicked).getBlock() instanceof LecternBlock)) {
				return;
			}
			lecternPos = clicked;
			clicked = null;
		}
		int pages = LecternPages.count(menu.getBook());
		if (pages > 0) {
			Gauge.rememberLectern(dimension, lecternPos.asLong(), Math.clamp(menu.getPage(), 0, pages - 1), pages);
		}
	}

	private static Entity bindEntity(Level level, AbstractContainerMenu menu, String dimension) {
		if (clickedEntity == null || !dimension.equals(clickedDim) || level.getGameTime() - clickedTick > OPEN_WINDOW) {
			return null;
		}
		Entity e = clickedEntity;
		clickedEntity = null;
		return e instanceof ContainerEntity c && containerSlots(menu).size() == c.getContainerSize() ? e : null;
	}

	private static List<BlockPos> bind(Level level, AbstractContainerMenu menu, String dimension) {
		if (clicked == null || !dimension.equals(clickedDim) || level.getGameTime() - clickedTick > OPEN_WINDOW) {
			return null;
		}
		BlockPos pos = clicked;
		clicked = null;
		if (!(level.getBlockEntity(pos) instanceof BaseContainerBlockEntity be)) {
			return null;
		}
		List<BlockPos> positions = new ArrayList<>();
		positions.add(pos);
		int expected = be.getContainerSize();
		BlockState s = level.getBlockState(pos);
		if (s.getBlock() instanceof ChestBlock && s.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
			BlockPos other = ChestBlock.getConnectedBlockPos(pos, s);
			if (level.getBlockEntity(other) instanceof BaseContainerBlockEntity otherBe) {
				expected += otherBe.getContainerSize();
				positions.add(other);
			}
		}
		return containerSlots(menu).size() == expected ? positions : null;
	}

	private static List<Slot> containerSlots(AbstractContainerMenu menu) {
		List<Slot> out = new ArrayList<>();
		for (Slot slot : menu.slots) {
			Container c = slot.container;
			if (!(c instanceof Inventory)) {
				out.add(slot);
			}
		}
		return out;
	}

	private static ContainerSnapshot read(AbstractContainerMenu menu) {
		List<Slot> slots = containerSlots(menu);
		if (slots.isEmpty()) {
			return null;
		}
		int[] counts = new int[slots.size()];
		int[] max = new int[slots.size()];
		for (int i = 0; i < counts.length; i++) {
			ItemStack stack = slots.get(i).getItem();
			counts[i] = stack.isEmpty() ? 0 : stack.getCount();
			max[i] = stack.isEmpty() ? 64 : Math.min(stack.getMaxStackSize(), slots.get(i).container.getMaxStackSize(stack));
		}
		return ContainerSnapshot.of(counts, max, System.currentTimeMillis());
	}
}
