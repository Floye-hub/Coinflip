package com.floye.coinflip;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CoinFlipMod implements ModInitializer {
	public static final String MOD_ID = "coinflip";
	public static final Logger LOGGER = LoggerFactory.getLogger(CoinFlipMod.class);

	public static CoinFlipManager coinFlipManager = new CoinFlipManager();
	public static CoinFlipConfig config = CoinFlipConfig.load(); // ✅ Chargement de la config

	@Override
	public void onInitialize() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			CoinFlipCommands.register(dispatcher);
		});

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			coinFlipManager.onPlayerDisconnect(handler.getPlayer().getUuid());
		});

		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			CoinFlipManager.shutdown();
		});

		ServerLifecycleEvents.SERVER_STARTING.register(server -> {
			coinFlipManager.loadAndRefundFlips();
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			CoinFlipMod.coinFlipManager.onPlayerJoin(handler.getPlayer());
		});


		Runtime.getRuntime().addShutdownHook(new Thread(CoinFlipManager::shutdown));
	}

	public static void clearAllFlips() {
		coinFlipManager.clearAllFlips();
	}
}