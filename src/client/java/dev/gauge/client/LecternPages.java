package dev.gauge.client;

import dev.gauge.client.mixin.LecternBlockEntityAccessor;

import net.minecraft.world.item.ItemStack;

/** Page count of a book the way the lectern counts it (written and writable books). */
final class LecternPages {
	private LecternPages() {
	}

	static int count(ItemStack book) {
		return LecternBlockEntityAccessor.gauge$pageCount(book);
	}

	/** Lectern comparator output: floor(page / (pages - 1) * 14) + 1, or 15 for a one-page book. */
	static int signal(int page, int pages) {
		float f = pages > 1 ? page / (pages - 1.0f) : 1.0f;
		return (int) Math.floor(f * 14.0f) + 1;
	}
}
