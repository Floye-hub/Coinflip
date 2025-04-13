// CoinFlipCommands.java avec messages configurés
package com.floye.coinflip;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.floye.coinflip.utils.EconomyHandler;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class CoinFlipCommands {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("coinflip")
                .executes(CoinFlipCommands::openGui)
                .then(CommandManager.literal("create")
                        .then(CommandManager.argument("amount", IntegerArgumentType.integer(1))
                                .executes(context -> createFlip(context, IntegerArgumentType.getInteger(context, "amount"), "dollars"))
                                .then(CommandManager.argument("currency", StringArgumentType.word())
                                        .suggests((context, builder) -> suggestAllowedCurrencies(builder))
                                        .executes(context -> createFlip(
                                                context,
                                                IntegerArgumentType.getInteger(context, "amount"),
                                                StringArgumentType.getString(context, "currency")
                                        ))
                                )
                        )
                )
                .then(CommandManager.literal("cancel")
                        .executes(CoinFlipCommands::cancelFlip))
                .then(CommandManager.literal("currencies")
                        .executes(CoinFlipCommands::listCurrencies))
                .then(CommandManager.literal("reloadconfig")
                        .requires(source -> source.hasPermissionLevel(2))
                        .executes(CoinFlipCommands::reloadConfig)
                )
        );
    }

    private static CompletableFuture<Suggestions> suggestAllowedCurrencies(SuggestionsBuilder builder) {
        for (String currency : CoinFlipMod.config.getAvailableCurrencyAliases()) {
            boolean isPrimary = currency.equals(CoinFlipMod.config.defaultCurrencyAlias);
            if (isPrimary) {
                builder.suggest(currency);
            } else {
                builder.suggest(currency);
            }
        }
        return builder.buildFuture();
    }

    private static int createFlip(CommandContext<ServerCommandSource> context, int amount, String currencyAlias) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player == null) return 0;

        if (!CoinFlipMod.config.isValidCurrencyAlias(currencyAlias)) {
            String allowedCurrencies = String.join(", ", CoinFlipMod.config.getAvailableCurrencyAliases());
            String message = CoinFlipMod.config.getMessage("invalidCurrency", Map.of(
                    "currency", currencyAlias,
                    "allowed_currencies", allowedCurrencies
            ));
            player.sendMessage(Text.literal(message));
            return 0;
        }

        String fullCurrencyKey = CoinFlipMod.config.getFullCurrencyKey(currencyAlias);

        if (!EconomyHandler.isCurrencyValid(fullCurrencyKey)) {
            String message = CoinFlipMod.config.getMessage("currencyNotExist", Map.of(
                    "currency", currencyAlias
            ));
            player.sendMessage(Text.literal(message));
            return 0;
        }

        int max = CoinFlipMod.config.maxCoinFlipsPerPlayer;

        CompletableFuture<Boolean> future = CoinFlipMod.coinFlipManager.createFlip(player, amount, fullCurrencyKey);
        future.thenAccept(success -> {
            String msgKey = success ? "createSuccess" : "createFail";
            String message = CoinFlipMod.config.getMessage(msgKey, Map.of(
                    "amount", String.valueOf(amount),
                    "currency", currencyAlias,
                    "max", String.valueOf(max)
            ));
            player.sendMessage(Text.literal(message));
        });
        return Command.SINGLE_SUCCESS;
    }

    private static int cancelFlip(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player == null) return 0;

        CoinFlipMod.coinFlipManager.cancelFlip(player).thenAccept(result -> { // result est maintenant un CancelFlipResult
            if (result.success) { // Utilisation de result.success
                String message = CoinFlipMod.config.getMessage("cancelSuccess", Map.of(
                        "amount", String.valueOf(result.amount), // Utilisation de result.amount
                        "currency", result.currencyAlias // Utilisation de result.currencyAlias
                ));
                player.sendMessage(Text.literal(message));
            } else {
                String message = CoinFlipMod.config.getMessage("cancelFail", Map.of());
                player.sendMessage(Text.literal(message));
            }
        });
        return Command.SINGLE_SUCCESS;
    }

    private static int openGui(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player != null) {
            new CoinFlipGui(player).open();
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int reloadConfig(CommandContext<ServerCommandSource> context) {
        CoinFlipMod.config = CoinFlipConfig.load();
        String message = CoinFlipMod.config.getMessage("configReloaded", Map.of());
        context.getSource().sendFeedback(() -> Text.literal(message), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int listCurrencies(CommandContext<ServerCommandSource> context) {
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player == null) return 0;

        String title = CoinFlipMod.config.getMessage("availableCurrenciesTitle", Map.of());
        player.sendMessage(Text.literal(title).formatted(Formatting.GOLD));

        for (String alias : CoinFlipMod.config.getAvailableCurrencyAliases()) {
            boolean isPrimary = alias.equals(CoinFlipMod.config.defaultCurrencyAlias);
            String fullKey = CoinFlipMod.config.getFullCurrencyKey(alias);
            boolean exists = EconomyHandler.isCurrencyValid(fullKey);

            String currencyMessage = CoinFlipMod.config.getMessage(isPrimary ?
                            "primaryCurrencyFormat" : "currencyFormat",
                    Map.of("alias", alias, "exists", exists ? "✓" : "✗")
            );

            Text message = Text.literal("• ")
                    .formatted(Formatting.YELLOW)
                    .append(Text.literal(currencyMessage)
                            .formatted(isPrimary ? Formatting.GREEN : exists ? Formatting.WHITE : Formatting.RED));

            player.sendMessage(message);
        }
        return Command.SINGLE_SUCCESS;
    }
}