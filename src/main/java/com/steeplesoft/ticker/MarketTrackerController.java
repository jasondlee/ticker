package com.steeplesoft.ticker;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.function.Predicate;

import dev.tamboui.widgets.input.TextInputState;
import dev.tamboui.widgets.table.TableState;

public class MarketTrackerController {
    private final TableState tableState = new TableState();
    private final TextInputState inputState = new TextInputState();
    private final YahooFinanceClient client = new YahooFinanceClient();
    private List<QuoteData> markets = List.of();
    private List<QuoteData> quotes = List.of();
    private MarketTrackerController.DialogType currentDialog = MarketTrackerController.DialogType.NONE;

    public MarketTrackerController() {
        tableState.selectFirst();
    }

    public void refreshQuotes() {
        List<QuoteData> response = null;
        try {
            response = client.getQuotes(List.of("^DJI", "^IXIC", "^GSPC", "ORCL", "IBM", "NVDA", "SPCX"));
        } catch (IOException | InterruptedException | URISyntaxException e) {
            throw new RuntimeException(e);
        }

        Predicate<QuoteData> predicate = (QuoteData q) -> !q.symbol().startsWith("^") && !q.symbol().contains("=");
        markets = response.stream().filter(q -> !predicate.test(q)).toList();
        quotes = response.stream().filter(predicate).toList();
    }

    public List<QuoteData> getMarkets() {
        return markets;
    }

    public List<QuoteData> getQuotes() {
        return quotes;
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

    public void dismissDialog() {
        currentDialog = MarketTrackerController.DialogType.NONE;
        inputState.clear();
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
