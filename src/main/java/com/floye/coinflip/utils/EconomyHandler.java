package com.floye.coinflip.utils;

import net.impactdev.impactor.api.economy.EconomyService;
import net.impactdev.impactor.api.economy.accounts.Account;
import net.impactdev.impactor.api.economy.currency.Currency;
import net.impactdev.impactor.api.economy.transactions.EconomyTransaction;
import net.impactdev.impactor.api.economy.transactions.details.EconomyResultType;
import net.kyori.adventure.key.InvalidKeyException;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class EconomyHandler {

    // Obtenir un compte pour une devise spécifique
    public static CompletableFuture<Account> getAccount(UUID playerId, String currencyKey) {
        EconomyService service = EconomyService.instance();
        Optional<Currency> currency = getCurrency(currencyKey);

        if (currency.isPresent()) {
            return service.account(currency.get(), playerId);
        } else {
            // Fallback sur la devise par défaut si la devise spécifiée n'existe pas
            return service.account(playerId);
        }
    }

    // Obtenir le solde d'un compte
    public static double getBalance(Account account) {
        return account.balance().doubleValue();
    }

    // Retirer de l'argent d'un compte
    public static boolean remove(Account account, double amount) {
        EconomyTransaction transaction = account.withdraw(BigDecimal.valueOf(amount));
        return transaction.result() == EconomyResultType.SUCCESS;
    }

    // Ajouter de l'argent à un compte
    public static boolean add(Account account, double amount) {
        EconomyTransaction transaction = account.deposit(BigDecimal.valueOf(amount));
        return transaction.result() == EconomyResultType.SUCCESS;
    }

    // Vérifier si une devise est valide
    public static boolean isCurrencyValid(String currencyKey) {
        try {
            return getCurrency(currencyKey).isPresent();
        } catch (Exception e) {
            return false;
        }
    }

    // Obtenir la devise principale
    public static Currency getPrimaryCurrency() {
        return EconomyService.instance().currencies().primary();
    }

    // Obtenir une devise par sa clé
    public static Optional<Currency> getCurrency(String currencyKey) {
        try {
            return EconomyService.instance().currencies().currency(Key.key(currencyKey));
        } catch (InvalidKeyException e) {
            return Optional.empty();
        }
    }

    // Formater un montant avec une devise
    public static String formatAmount(Currency currency, BigDecimal amount) {
        Component formatted = currency.format(amount);
        return formatted.toString();
    }

    // Obtenir une devise ou la devise par défaut
    public static Currency getCurrencyOrDefault(String currencyKey) {
        if (currencyKey == null || currencyKey.isEmpty()) {
            return getPrimaryCurrency();
        }

        try {
            Optional<Currency> currency = getCurrency(currencyKey);
            return currency.orElse(getPrimaryCurrency());
        } catch (Exception e) {
            return getPrimaryCurrency();
        }
    }
}