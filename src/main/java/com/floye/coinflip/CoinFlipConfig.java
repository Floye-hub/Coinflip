package com.floye.coinflip;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Formatting;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class CoinFlipConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_DIR = FabricLoader.getInstance().getConfigDir().resolve("coinflip");
    private static final Path CONFIG_PATH = CONFIG_DIR.resolve("setting.json");

    // Paramètres généraux
    public String defaultCurrencyAlias = "dollars";
    public int maxCoinFlipsPerPlayer = 2;
    public double taxPercentage = 5.0;
    public int flipTimeoutMinutes = 5;

    // Configuration des devises
    public Map<String, String> currencyAliases = new HashMap<>() {{
        put("dollars", "impactor:dollars");
        put("credit", "impactor:credit");
    }};

    // Configuration de l'interface graphique
    public GuiConfig gui = new GuiConfig();

    // Messages
    public Messages messages = new Messages();

    public static class GuiConfig {
        // Titles
        public String mainTitle = "Available CoinFlips";
        public String animationTitle = "CoinFlip Result";

        // Buttons and UI elements
        public String nextPageButton = "Next Page →";
        public String prevPageButton = "← Previous Page";
        public String backButton = "Back";

        // Display formats
        public String flipEntryFormat = "%player% - %amount% %currency%";

        // Colors
        public String primaryColor = "GOLD";
        public String secondaryColor = "YELLOW";
        public String successColor = "GREEN";
        public String errorColor = "RED";

        // Display options
        public boolean showPlayerHeads = true;
        public int itemsPerPage = 50;

        // Sons
        public String flipSound = "minecraft:block.note_block.pling";
        public float flipSoundVolume = 0.5f;
        public float flipSoundPitch = 1.0f;

        public String winSound = "minecraft:entity.player.levelup";
        public float winSoundVolume = 1.0f;
        public float winSoundPitch = 1.0f;

        public String loseSound = "minecraft:entity.villager.no";
        public float loseSoundVolume = 1.0f;
        public float loseSoundPitch = 0.8f;
    }

    public static class Messages {
        // Command messages
        public String createSuccess = "§aCoinFlip of %amount% %currency% successfully created!";
        public String createFail = "§cFailed to create CoinFlip";
        public String cancelSuccess = "§aCoinFlip canceled! %amount% %currency% has been refunded";
        public String cancelFail = "§cNo active CoinFlip";
        public String joinSuccess = "§aYou have joined %player%'s CoinFlip";
        public String joinOwnFlip = "§cYou cannot join your own CoinFlip!";
        public String joinFail = "§cFailed to join the CoinFlip";
        public String notEnoughMoney = "§cYou don't have enough money (%currency%) to join this CoinFlip";
        public String alreadyMaxFlips = "§cYou have already reached the limit of %max% active CoinFlips.";
        public String playerNotFound = "§cPlayer not found!";
        public String playerJoined = "§a%player% has joined your CoinFlip!";
        public String win = "§aYou won %amount% %currency%!";
        public String lose = "§cYou lost %amount% %currency%!";
        public String refundMessage = "§aYou have been refunded for your unresolved CoinFlips.";
        public String invalidCurrency = "§cInvalid currency: %currency%. Allowed currencies: %allowed_currencies%";
        public String taxApplied = "§7Tax applied: %tax% %currency% (%percentage%%)";
        public String clickToJoin = "§aClick to join";
        public String configReloaded = "§aCoinFlip configuration successfully reloaded!";
        public String availableCurrenciesTitle = "§6=== Available Currencies ===";
        public String primaryCurrencyFormat = "§a%alias% §7(default)";
        public String currencyFormat = "§f%alias%";
        public String currencyNotExist = "§cThe currency %currency% is configured but does not exist in the economic system";
        public String flipTimeout = "§cYour CoinFlip was canceled after %minutes% minutes without a participant";
        public String broadcastFlipCreation = "§6A new CoinFlip has been created by %player% for %amount% %currency%!";
    }

    public static CoinFlipConfig load() {
        try {
            if (!Files.exists(CONFIG_DIR)) {
                Files.createDirectories(CONFIG_DIR);
            }

            if (Files.exists(CONFIG_PATH)) {
                String json = Files.readString(CONFIG_PATH);
                return GSON.fromJson(json, CoinFlipConfig.class);
            } else {
                CoinFlipConfig config = new CoinFlipConfig();
                Files.writeString(CONFIG_PATH, GSON.toJson(config));
                return config;
            }
        } catch (IOException e) {
            CoinFlipMod.LOGGER.error("Erreur lors du chargement de la configuration CoinFlip", e);
            return new CoinFlipConfig();
        }
    }

    public String getMessage(String key, Map<String, String> placeholders) {
        String raw = switch (key) {
            case "createSuccess" -> messages.createSuccess;
            case "createFail" -> messages.createFail;
            case "cancelSuccess" -> messages.cancelSuccess;
            case "cancelFail" -> messages.cancelFail;
            case "joinSuccess" -> messages.joinSuccess;
            case "joinFail" -> messages.joinFail;
            case "joinOwnFlip" -> messages.joinOwnFlip;
            case "notEnoughMoney" -> messages.notEnoughMoney;
            case "alreadyMaxFlips" -> messages.alreadyMaxFlips;
            case "playerNotFound" -> messages.playerNotFound;
            case "playerJoined" -> messages.playerJoined;
            case "win" -> messages.win;
            case "lose" -> messages.lose;
            case "refundMessage" -> messages.refundMessage;
            case "invalidCurrency" -> messages.invalidCurrency;
            case "taxApplied" -> messages.taxApplied;
            case "clickToJoin" -> messages.clickToJoin;
            case "configReloaded" -> messages.configReloaded;
            case "availableCurrenciesTitle" -> messages.availableCurrenciesTitle;
            case "primaryCurrencyFormat" -> messages.primaryCurrencyFormat;
            case "currencyFormat" -> messages.currencyFormat;
            case "currencyNotExist" -> messages.currencyNotExist;
            case "flipTimeout" -> messages.flipTimeout;
            case "broadcastFlipCreation" -> messages.broadcastFlipCreation;
            default -> "";
        };

        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            raw = raw.replace("%" + entry.getKey() + "%", entry.getValue());
        }

        return raw;
    }

    public Formatting getColorFormatting(String colorName) {
        try {
            return Formatting.valueOf(colorName.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Formatting.WHITE;
        }
    }

    public boolean isValidCurrencyAlias(String alias) {
        return currencyAliases.containsKey(alias);
    }

    public String getFullCurrencyKey(String alias) {
        return currencyAliases.getOrDefault(alias, "impactor:dollars");
    }

    public List<String> getAvailableCurrencyAliases() {
        return new ArrayList<>(currencyAliases.keySet());
    }

    public String getCurrencyAliasFromKey(String fullKey) {
        for (Map.Entry<String, String> entry : currencyAliases.entrySet()) {
            if (entry.getValue().equals(fullKey)) {
                return entry.getKey();
            }
        }
        return fullKey;
    }
}