package com.floye.coinflip;

import com.floye.coinflip.utils.EconomyHandler;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

public class CoinFlipManager {
    public static class CoinFlip {
        public final UUID creator;
        public UUID participant;
        public final int amount;
        public final String currency;
        public final UUID id;

        public CoinFlip(UUID creator, int amount, String currency) {
            this.creator = creator;
            this.amount = amount;
            this.currency = currency;
            this.participant = null;
            this.id = UUID.randomUUID();
        }

        public UUID getCreator() {
            return creator;
        }

        public UUID getJoiner() {
            return participant;
        }

        public double getAmount() {
            return amount;
        }

        public UUID getId() {
            return id;
        }
    }

    public static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final Map<UUID, List<CoinFlip>> activeFlips = new HashMap<>();
    private final Map<UUID, List<CoinFlip>> pendingRefunds = new HashMap<>();
    private final Map<UUID, CoinFlipAnimationGui> activeAnimations = new ConcurrentHashMap<>();
    private static final Path SAVE_DIR = FabricLoader.getInstance().getConfigDir().resolve("coinflip");
    private static final Path SAVE_PATH = SAVE_DIR.resolve("coinflip_data.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public void saveFlips() {
        try {
            List<CoinFlip> allFlips = getActiveFlips().stream().toList();
            String json = GSON.toJson(allFlips);
            Files.writeString(SAVE_PATH, json);
        } catch (IOException e) {
            CoinFlipMod.LOGGER.error("Erreur lors de la sauvegarde des CoinFlips", e);
        }
    }

    public void loadAndRefundFlips() {
        if (!Files.exists(SAVE_PATH)) return;

        try {
            String json = Files.readString(SAVE_PATH);
            CoinFlip[] flips = GSON.fromJson(json, CoinFlip[].class);

            if (flips != null) {
                for (CoinFlip flip : flips) {
                    if (flip.participant == null) {
                        pendingRefunds.computeIfAbsent(flip.creator, uuid -> new ArrayList<>()).add(flip);
                    }
                }
            }

            Files.deleteIfExists(SAVE_PATH);
        } catch (IOException e) {
            CoinFlipMod.LOGGER.error("Erreur lors du chargement des CoinFlips", e);
        }
    }

    public Collection<CoinFlip> getActiveFlips() {
        List<CoinFlip> allFlips = new ArrayList<>();
        for (List<CoinFlip> flips : activeFlips.values()) {
            allFlips.addAll(flips);
        }
        return allFlips;
    }

    public boolean hasActiveAnimation(UUID playerId) {
        boolean hasAnimation = activeAnimations.containsKey(playerId);
        CoinFlipMod.LOGGER.debug("Vérification animation pour joueur {}: {}", playerId, hasAnimation); // Log debug
        return hasAnimation;
    }

    public void addActiveAnimation(UUID playerId, CoinFlipAnimationGui animationGui) {
        activeAnimations.put(playerId, animationGui);
        CoinFlipMod.LOGGER.debug("Animation DÉMARRÉE pour joueur {}", playerId); // Log debug
    }

    public void removeActiveAnimation(UUID playerId) {
        activeAnimations.remove(playerId);
        CoinFlipMod.LOGGER.debug("Animation TERMINÉE pour joueur {}", playerId); // Log debug
    }

    public CompletableFuture<Boolean> createFlip(ServerPlayerEntity creator, int amount, String currencyKey) {
        if (amount <= 0) return CompletableFuture.completedFuture(false);

        UUID creatorId = creator.getUuid();
        List<CoinFlip> playerFlips = activeFlips.getOrDefault(creatorId, new ArrayList<>());

        if (playerFlips.size() >= CoinFlipMod.config.maxCoinFlipsPerPlayer) {
            String message = CoinFlipMod.config.getMessage("alreadyMaxFlips", Map.of(
                    "max", String.valueOf(CoinFlipMod.config.maxCoinFlipsPerPlayer)
            ));
            creator.sendMessage(Text.literal(message));
            return CompletableFuture.completedFuture(false);
        }

        return EconomyHandler.getAccount(creatorId, currencyKey)
                .thenApply(creatorAcc -> {
                    if (creatorAcc != null && EconomyHandler.getBalance(creatorAcc) >= amount) {
                        if (EconomyHandler.remove(creatorAcc, amount)) {
                            CoinFlip newFlip = new CoinFlip(creatorId, amount, currencyKey);
                            playerFlips.add(newFlip);
                            activeFlips.put(creatorId, playerFlips);

                            scheduler.schedule(() -> {
                                if (newFlip.participant == null) {
                                    cancelFlipAfterTimeout(newFlip);
                                }
                            }, CoinFlipMod.config.flipTimeoutMinutes, TimeUnit.MINUTES);

                            CoinFlipMod.LOGGER.info("creation coinflip");

                            broadcastFlipCreation(creator, newFlip);
                            saveFlips();
                            return true;
                        }
                    }
                    return false;
                });
    }

    private void broadcastFlipCreation(ServerPlayerEntity creator, CoinFlip flip) {
        CoinFlipMod.LOGGER.info("Début de broadcastFlipCreation");
        String playerName = creator.getName().getString();
        CoinFlipMod.LOGGER.info("1");
        String amountString = String.format("%.2f",(double)flip.amount);
        CoinFlipMod.LOGGER.info("2");
        String currencyAlias = CoinFlipMod.config.getCurrencyAliasFromKey(flip.currency);

        String message = CoinFlipMod.config.getMessage("broadcastFlipCreation", Map.of(
                "player", playerName,
                "amount", amountString,
                "currency", currencyAlias
        ));
        CoinFlipMod.LOGGER.info("Message généré: {}", message);

        MinecraftServer server = creator.getServer();
        CoinFlipMod.LOGGER.info("Serveur: {}", server);
        if (server != null) {
            CoinFlipMod.LOGGER.info("Envoi du message à tous les joueurs");
            server.getPlayerManager().getPlayerList().forEach(player -> {
                player.sendMessage(Text.literal(message));
            });
        } else {
            CoinFlipMod.LOGGER.info("Le serveur est null, impossible d'envoyer le message");
        }
    }

    private void cancelFlipAfterTimeout(CoinFlip flip) {
        List<CoinFlip> playerFlips = activeFlips.get(flip.creator);
        if (playerFlips != null && playerFlips.contains(flip) && flip.participant == null) {
            EconomyHandler.getAccount(flip.creator, flip.currency)
                    .thenAccept(acc -> {
                        if (acc != null) {
                            EconomyHandler.add(acc, flip.amount);
                        }
                    });

            MinecraftServer server = (MinecraftServer) FabricLoader.getInstance().getGameInstance();
            ServerPlayerEntity creator = server.getPlayerManager().getPlayer(flip.creator);
            if (creator != null) {
                String message = CoinFlipMod.config.getMessage("flipTimeout", Map.of(
                        "minutes", String.valueOf(CoinFlipMod.config.flipTimeoutMinutes)
                ));
                creator.sendMessage(Text.literal(message));
            }

            playerFlips.remove(flip);
            if (playerFlips.isEmpty()) {
                activeFlips.remove(flip.creator);
            }
            saveFlips();
        }
    }

    public CompletableFuture<Boolean> joinFlip(ServerPlayerEntity joiner, UUID flipCreator, UUID flipId) {
        UUID joinerId = joiner.getUuid();



        List<CoinFlip> flips = activeFlips.get(flipCreator);
        if (flips == null || flips.isEmpty()) {
            return CompletableFuture.completedFuture(false);
        }

        CoinFlip flip = flips.stream()
                .filter(f -> f.participant == null && f.id.equals(flipId))
                .findFirst()
                .orElse(null);

        if (flip == null) {
            return CompletableFuture.completedFuture(false);
        }

        return EconomyHandler.getAccount(joinerId, flip.currency)
                .thenCompose(joinerAcc -> {
                    if (joinerAcc == null || EconomyHandler.getBalance(joinerAcc) < flip.amount) {
                        return CompletableFuture.completedFuture(false);
                    }

                    if (EconomyHandler.remove(joinerAcc, flip.amount)) {
                        flip.participant = joinerId;
                        resolveFlip(flip);
                        saveFlips();
                        return CompletableFuture.completedFuture(true);
                    }
                    return CompletableFuture.completedFuture(false);
                });
    }

    private void resolveFlip(CoinFlip flip) {
        UUID winnerUuid = new Random().nextBoolean() ? flip.creator : flip.participant;
        UUID loserUuid = winnerUuid.equals(flip.creator) ? flip.participant : flip.creator;

        MinecraftServer server = (MinecraftServer) FabricLoader.getInstance().getGameInstance();
        ServerPlayerEntity winnerPlayer = server.getPlayerManager().getPlayer(winnerUuid);
        ServerPlayerEntity loserPlayer = server.getPlayerManager().getPlayer(loserUuid);

        CoinFlipMod.LOGGER.info("resolveFlip() - Winner: {}, Loser: {}", winnerUuid, loserUuid);

        if (winnerPlayer != null) {
            addActiveAnimation(winnerPlayer.getUuid(), new CoinFlipAnimationGui(winnerPlayer, flip, true));
        }
        if (loserPlayer != null) {
            addActiveAnimation(loserPlayer.getUuid(), new CoinFlipAnimationGui(loserPlayer, flip, false));
        }

        // --- Paiement avec taxe ---
        double pot = flip.getAmount() * 2;
        String currencyAlias = CoinFlipMod.config.getCurrencyAliasFromKey(flip.currency);

        double taxPercent = CoinFlipMod.config.getTaxPercentageForCurrency(currencyAlias);
        double taxAmount = pot * (taxPercent / 100.0);
        double amountWon = pot - taxAmount;

        String currencyKey = flip.currency;

        EconomyHandler.getAccount(winnerUuid, currencyKey).thenAccept(account -> {
            boolean success = EconomyHandler.add(account, amountWon);

        });

        removeFlip(flip);
    }

    public void removeFlip(CoinFlip flip) {
        List<CoinFlip> playerFlips = activeFlips.get(flip.creator);
        if (playerFlips != null) {
            playerFlips.remove(flip);
            if (playerFlips.isEmpty()) {
                activeFlips.remove(flip.creator);
            }
        }
        saveFlips();
    }

    public void onPlayerDisconnect(UUID playerUuid) {
        List<CoinFlip> flips = activeFlips.get(playerUuid);
        if (flips != null) {
            for (CoinFlip flip : flips) {
                if (flip.participant == null) {
                    EconomyHandler.getAccount(flip.creator, flip.currency)
                            .thenAccept(account -> {
                                if (account != null) {
                                    EconomyHandler.add(account, flip.amount);
                                }
                            });
                }
            }
            activeFlips.remove(playerUuid);
            saveFlips();
        }
        removeActiveAnimation(playerUuid);
    }

    public void clearAllFlips() {
        activeFlips.clear(); // Clears the map of active flips
        saveFlips(); // Optionally save the cleared state (if you want to persist this)

        // Optionally, you might want to handle ongoing animations when clearing all flips:
        activeAnimations.values().forEach(CoinFlipAnimationGui::close); // Close all animation GUIs
        activeAnimations.clear(); // Clear the map of active animations


    }

    public CompletableFuture<CancelFlipResult> cancelFlip(ServerPlayerEntity player) {
        UUID creatorId = player.getUuid();
        List<CoinFlip> playerFlips = activeFlips.get(creatorId);

        if (playerFlips == null || playerFlips.isEmpty()) {
            return CompletableFuture.completedFuture(new CancelFlipResult(false, 0, null)); // Aucun flip actif
        }

        CoinFlip flipToCancel = null;
        for (CoinFlip flip : playerFlips) {
            if (flip.participant == null) {
                flipToCancel = flip;
                break; // Prend le premier flip non rejoint trouvé
            }
        }

        if (flipToCancel == null) {
            return CompletableFuture.completedFuture(new CancelFlipResult(false, 0, null)); // Aucun flip non rejoint à annuler
        }

        CoinFlip finalFlipToCancel = flipToCancel; // Pour utilisation dans lambda
        return EconomyHandler.getAccount(creatorId, flipToCancel.currency)
                .thenApply(account -> {
                    if (account != null) {
                        EconomyHandler.add(account, finalFlipToCancel.amount);

                        List<CoinFlip> currentFlips = activeFlips.get(creatorId);
                        if (currentFlips != null) {
                            currentFlips.remove(finalFlipToCancel);
                            if (currentFlips.isEmpty()) {
                                activeFlips.remove(creatorId);
                            }
                        }
                        saveFlips();
                        String currencyAlias = CoinFlipMod.config.getCurrencyAliasFromKey(finalFlipToCancel.currency);
                        return new CancelFlipResult(true, finalFlipToCancel.amount, currencyAlias);
                    } else {
                        return new CancelFlipResult(false, 0, null); // Erreur de compte (peu probable ici, mais au cas où)
                    }
                });
    }

    // Classe interne pour représenter le résultat de l'annulation
    public static class CancelFlipResult {
        public final boolean success;
        public final int amount;
        public final String currencyAlias;

        public CancelFlipResult(boolean success, int amount, String currencyAlias) {
            this.success = success;
            this.amount = amount;
            this.currencyAlias = currencyAlias;
        }
    }

    public void onPlayerJoin(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();

        if (pendingRefunds.containsKey(uuid)) {
            List<CoinFlip> flips = pendingRefunds.remove(uuid);

            for (CoinFlip flip : flips) {
                EconomyHandler.getAccount(uuid, flip.currency).thenAccept(account -> {
                    if (account != null) {
                        EconomyHandler.add(account, flip.amount);
                    }
                });
            }

            if (!flips.isEmpty()) {
                String msg = CoinFlipMod.config.getMessage("refundMessage", Map.of());
                player.sendMessage(Text.literal(msg));
            }
        }
    }

    public static void shutdown() {
        scheduler.shutdown();
        CoinFlipMod.coinFlipManager.saveFlips();
    }
}