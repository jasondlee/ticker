package com.steeplesoft.ticker;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

import dev.tamboui.widgets.input.TextInputState;
import dev.tamboui.widgets.table.TableState;

public class MarketTrackerController {
    private final TableState tableState = new TableState();
    private final TextInputState inputState = new TextInputState();
    private final YahooFinanceClient client = new YahooFinanceClient();
    private final List<String> tickerSymbols = new ArrayList<>();
    private List<QuoteData> markets = List.of();
    private List<QuoteData> stocks = List.of();
    private MarketTrackerController.DialogType currentDialog = MarketTrackerController.DialogType.NONE;

    public MarketTrackerController() {
        tableState.selectFirst();
        loadConfig();

        CompletableFuture.runAsync(this::refreshQuotes);
    }

    public void refreshQuotes() {
        List<QuoteData> response = null;
        try {
            response = client.getQuotes(tickerSymbols);
        } catch (IOException | InterruptedException | URISyntaxException e) {
            throw new RuntimeException(e);
        }

        Predicate<QuoteData> predicate = (QuoteData q) -> !q.symbol().startsWith("^") && !q.symbol().contains("=");
        markets = response.stream().filter(q -> !predicate.test(q)).toList();
        stocks = response.stream().filter(predicate).toList();
    }

    public List<QuoteData> markets() {
        return markets;
    }

    public List<QuoteData> stocks() {
        return stocks;
    }

    public TableState tableState() {
        return tableState;
    }

    /**
     * Returns the text input state used for input dialogs.
     *
     * @return the TextInputState instance backing input dialogs
     */
    public TextInputState inputState() {
        return inputState;
    }

    /**
     * Returns the current dialog type.
     *
     * @return current DialogType
     */
    public DialogType currentDialog() {
        return currentDialog;
    }

    public void promptAddStock() {
        currentDialog = DialogType.ADD_STOCK;
    }

    public void addStock() {
        if (inputState.length() > 0) {
            tickerSymbols.add(inputState.text().trim().toUpperCase());
            try {
                saveConfig();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            refreshQuotes();
        }
        dismissDialog();
    }

    public void removeStock(int symbol) {
        tickerSymbols.remove(stocks.get(symbol).symbol());
        try {
            saveConfig();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        refreshQuotes();
    }

    public void dismissDialog() {
        currentDialog = MarketTrackerController.DialogType.NONE;
        inputState.clear();
    }

    private void loadConfig() {
        try {
            File config = new File(Path.of(System.getProperty("user.home")).toFile(), ".mtop");
            if (config.exists()) {
                List<String> strings = Files.readAllLines(config.toPath());
                strings.stream().filter(s -> s.startsWith("symbols="))
                    .findFirst()
                    .ifPresent(s -> {
                        tickerSymbols.addAll(List.of(s.substring(8).split(",")));
                    });
            }

            if (tickerSymbols.isEmpty()) {
                tickerSymbols.addAll(List.of("^DJI", "^GSPC", "^IXIC", "IBM"));
                saveConfig();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void saveConfig() throws IOException {
        File config = new File(Path.of(System.getProperty("user.home")).toFile(), ".mtop");
        if (!config.exists()) {
            if (!config.createNewFile()) {
                throw new IOException("Could not create config file");
            }
        }
        tickerSymbols.sort(Comparator.naturalOrder());
        Files.writeString(config.toPath(), "symbols=" + String.join(",", tickerSymbols));
    }

    public enum DialogType {
        /**
         * No dialog is currently shown.
         */
        NONE,
        /**
         * The "add stock" input dialog is shown.
         */
        ADD_STOCK
    }
}
