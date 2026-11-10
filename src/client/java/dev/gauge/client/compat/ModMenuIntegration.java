package dev.gauge.client.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

import dev.gauge.client.GaugeScreen;

/** Adds a "Configure" button for Gauge in Mod Menu. Only loaded when Mod Menu is installed. */
public final class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return GaugeScreen::new;
	}
}
