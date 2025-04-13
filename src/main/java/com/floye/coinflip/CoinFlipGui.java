package com.floye.coinflip;

import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.SimpleGui;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CoinFlipGui extends SimpleGui {
    private final ServerPlayerEntity player;
    private int page = 0;

    public CoinFlipGui(ServerPlayerEntity player) {
        super(ScreenHandlerType.GENERIC_9X6, player, false);
        this.player = player;


        this.setTitle(Text.literal(CoinFlipMod.config.gui.mainTitle)
                .formatted(CoinFlipMod.config.getColorFormatting(CoinFlipMod.config.gui.primaryColor)));
        initializeGui();
    }

    private void initializeGui() {
        for (int i = 0; i < this.getSize(); i++) {
            this.clearSlot(i);
        }

        List<CoinFlipManager.CoinFlip> flips = new ArrayList<>(CoinFlipMod.coinFlipManager.getActiveFlips());
        int itemsPerPage = CoinFlipMod.config.gui.itemsPerPage;

        for (int i = 0; i < itemsPerPage; i++) {
            int index = page * itemsPerPage + i;
            if (index >= flips.size()) break;

            CoinFlipManager.CoinFlip flip = flips.get(index);
            ServerPlayerEntity creator = player.getServer().getPlayerManager().getPlayer(flip.creator);
            String currencyAlias = CoinFlipMod.config.getCurrencyAliasFromKey(flip.currency);

            // Formater le nom de l'entrée
            String flipEntry = CoinFlipMod.config.gui.flipEntryFormat
                    .replace("%player%", creator != null ? creator.getName().getString() : "Joueur Inconnu")
                    .replace("%amount%", String.valueOf(flip.amount))
                    .replace("%currency%", currencyAlias);

            // Créer le lore
            List<Text> lore = new ArrayList<>();
            lore.add(Text.literal(" "));
            lore.add(Text.literal(CoinFlipMod.config.messages.clickToJoin)
                    .formatted(CoinFlipMod.config.getColorFormatting(CoinFlipMod.config.gui.successColor)));

            GuiElementBuilder builder = new GuiElementBuilder(
                    CoinFlipMod.config.gui.showPlayerHeads ? Items.PLAYER_HEAD : Items.GOLD_NUGGET)
                    .setName(Text.literal(flipEntry));

            if (CoinFlipMod.config.gui.showPlayerHeads && creator != null) {
                builder.setSkullOwner(creator.getGameProfile(), player.getServer()); // Correction ici : ajout de player.getServer()
            }

            builder.setCallback((slot, type, action) -> {
                handleFlipClick(flip, currencyAlias);
            });

            this.setSlot(i, builder);

            this.setSlot(i, builder);
        }

        // Boutons de navigation
        if (page > 0) {
            this.setSlot(itemsPerPage + 1, new GuiElementBuilder(Items.ARROW)
                    .setName(Text.literal(CoinFlipMod.config.gui.prevPageButton)
                            .formatted(CoinFlipMod.config.getColorFormatting(CoinFlipMod.config.gui.secondaryColor)))
                    .setCallback((slot, type, action) -> {
                        page--;
                        initializeGui();
                    }));
        }

        if ((page + 1) * itemsPerPage < flips.size()) {
            this.setSlot(itemsPerPage + 7, new GuiElementBuilder(Items.ARROW)
                    .setName(Text.literal(CoinFlipMod.config.gui.nextPageButton)
                            .formatted(CoinFlipMod.config.getColorFormatting(CoinFlipMod.config.gui.secondaryColor)))
                    .setCallback((slot, type, action) -> {
                        page++;
                        initializeGui();
                    }));
        }
    }

    private void handleFlipClick(CoinFlipManager.CoinFlip flip, String currencyAlias) {
        UUID creatorId = flip.getCreator();

        // 1. Bloquer si c'est son propre flip
        if (player.getUuid().equals(creatorId)) {
            player.sendMessage(Text.literal(CoinFlipMod.config.messages.joinOwnFlip)
                    .formatted(CoinFlipMod.config.getColorFormatting(CoinFlipMod.config.gui.errorColor)));
            return;
        }

        // 2. Bloquer si le créateur de ce flip a déjà une animation en cours
        if (CoinFlipMod.coinFlipManager.hasActiveAnimation(creatorId)) {
            player.sendMessage(Text.literal("§cVous ne pouvez pas rejoindre ce CoinFlip car le créateur est déjà en train de jouer un autre CoinFlip.")
                    .formatted(Formatting.RED));
            return;
        }

        // 3. Sinon, tenter de rejoindre
        CoinFlipMod.coinFlipManager.joinFlip(player, creatorId, flip.getId()).thenAccept(success -> {
            if (!success) {
                this.close();
                player.sendMessage(Text.literal(
                                CoinFlipMod.config.messages.notEnoughMoney.replace("%currency%", currencyAlias))
                        .formatted(CoinFlipMod.config.getColorFormatting(CoinFlipMod.config.gui.errorColor)));
            }
        });
    }
}
