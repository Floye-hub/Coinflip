package com.floye.coinflip;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import eu.pb4.sgui.api.GuiHelpers;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class CoinFlipAnimationGui extends SimpleGui {
    private final ServerPlayerEntity player;
    private final CoinFlipManager.CoinFlip flip;
    private final boolean isWinner;
    private int animationStep = 0;
    private boolean isAnimationRunning = true;
    private ScheduledExecutorService executor;

    public CoinFlipAnimationGui(ServerPlayerEntity player, CoinFlipManager.CoinFlip flip, boolean isWinner) {
        super(ScreenHandlerType.GENERIC_9X3, player, false);
        CoinFlipMod.LOGGER.info("CoinFlipAnimationGui CONSTRUCTEUR DÉMARRÉ pour joueur {}", player.getName().getString());
        this.player = player;
        this.flip = flip;
        this.isWinner = isWinner;

        CoinFlipMod.coinFlipManager.addActiveAnimation(player.getUuid(), this);

        this.setTitle(Text.literal(CoinFlipMod.config.gui.animationTitle)
                .formatted(CoinFlipMod.config.getColorFormatting(CoinFlipMod.config.gui.primaryColor)));
        initializeGui();
        this.open();
        CoinFlipMod.LOGGER.info("CoinFlipAnimationGui CONSTRUCTEUR FINI et initializeGui() terminé pour joueur {}", player.getName().getString());
    }

    private void initializeGui() {
        for (int i = 0; i < this.getSize(); i++) {
            this.clearSlot(i);
        }
        startCoinFlipAnimation();
    }

    private void startCoinFlipAnimation() {
        isAnimationRunning = true;
        animationStep = 0;
        executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(() -> {
            if (!isAnimationRunning) return;
            player.getServer().execute(() -> updateAnimation());
            animationStep++;
            if (animationStep >= 20) {
                isAnimationRunning = false;
                player.getServer().execute(() -> showResult());
                executor.shutdown();
            }
        }, 0, 200, TimeUnit.MILLISECONDS);
    }

    private void updateAnimation() {
        // Vider tous les slots
        for (int i = 0; i < this.getSize(); i++) {
            this.clearSlot(i);
        }

        // Jouer le son de flip
        if (isAnimationRunning) {
            Identifier soundId = Identifier.of(CoinFlipMod.config.gui.flipSound);
            SoundEvent soundEvent = SoundEvent.of(soundId);
            player.getWorld().playSound(
                    null,
                    player.getBlockPos(),
                    soundEvent,
                    SoundCategory.PLAYERS,
                    CoinFlipMod.config.gui.flipSoundVolume,
                    CoinFlipMod.config.gui.flipSoundPitch
            );

        }

        // Choix couleurs alternées
        Item glassColor = (animationStep % 2 == 0) ? Items.LIME_STAINED_GLASS_PANE : Items.RED_STAINED_GLASS_PANE;
        Item woolColor = (animationStep % 2 == 0) ? Items.LIME_WOOL : Items.RED_WOOL;

        // Remplir la rangée du milieu (slots 9 à 17)
        for (int slot = 9; slot <= 17; slot++) {
            if (slot == 13) { // slot central
                this.setSlot(slot, new GuiElementBuilder(woolColor).setName(Text.literal("")));
            } else {
                this.setSlot(slot, new GuiElementBuilder(glassColor).setName(Text.literal("")));
            }
        }

        GuiHelpers.sendPlayerScreenHandler(player);
    }

    private void showResult() {
        // Nettoyer toute la GUI
        for (int i = 0; i < this.getSize(); i++) {
            this.clearSlot(i);
        }

        double pot = flip.getAmount() * 2;
        String currencyAlias = CoinFlipMod.config.getCurrencyAliasFromKey(flip.currency);

        double taxPercent = CoinFlipMod.config.getTaxPercentageForCurrency(currencyAlias);
        double taxAmount = pot * (taxPercent / 100.0);
        double winnerAmount = pot - taxAmount;

        String message;

        if (isWinner) {
            String displayAmount = String.format("%.2f", winnerAmount);
            String taxDisplay = String.format("%.2f", taxAmount);

            message = String.format("You won %s %s (tax: %s, %.2f%%)",
                    displayAmount,
                    CoinFlipMod.config.getCurrencyAliasFromKey(flip.currency),
                    taxDisplay,
                    taxPercent);
        } else {
            String displayAmount = String.format("%.2f", flip.getAmount());
            message = String.format("You lost %s %s",
                    displayAmount,
                    CoinFlipMod.config.getCurrencyAliasFromKey(flip.currency));
        }

        // Jouer le son de victoire/défaite
        String soundKey = isWinner ? CoinFlipMod.config.gui.winSound : CoinFlipMod.config.gui.loseSound;
        float volume = isWinner ? CoinFlipMod.config.gui.winSoundVolume : CoinFlipMod.config.gui.loseSoundVolume;
        float pitch = isWinner ? CoinFlipMod.config.gui.winSoundPitch : CoinFlipMod.config.gui.loseSoundPitch;

        Identifier soundId = Identifier.of(soundKey);
        SoundEvent soundEvent = SoundEvent.of(soundId);
        player.getWorld().playSound(
                null,
                player.getBlockPos(),
                soundEvent,
                SoundCategory.PLAYERS,
                CoinFlipMod.config.gui.flipSoundVolume,
                CoinFlipMod.config.gui.flipSoundPitch
        );


        // Couleurs en fonction du résultat
        Item glassColor = isWinner ? Items.LIME_STAINED_GLASS_PANE : Items.RED_STAINED_GLASS_PANE;
        Item woolColor = isWinner ? Items.LIME_WOOL : Items.RED_WOOL;
        Formatting color = isWinner ? Formatting.GREEN : Formatting.RED;

        // Afficher vitres + laine sur la ligne du milieu
        for (int slot = 9; slot <= 17; slot++) {
            if (slot == 13) { // laine au centre
                this.setSlot(slot, new GuiElementBuilder(woolColor)
                        .setName(Text.literal(message).formatted(color)));
            } else {
                this.setSlot(slot, new GuiElementBuilder(glassColor)
                        .setName(Text.literal("")));
            }
        }

        GuiHelpers.sendPlayerScreenHandler(player);
        CoinFlipMod.coinFlipManager.removeActiveAnimation(player.getUuid());

        // Envoi aussi le message dans le chat
        player.sendMessage(Text.literal(message).formatted(color), false);
    }

    @Override
    public void onClose() {
        super.onClose();

        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
        if (!player.isRemoved()) {
            CoinFlipMod.coinFlipManager.removeActiveAnimation(player.getUuid());
        }
    }
}