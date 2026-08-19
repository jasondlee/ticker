package com.steeplesoft.ticker;

import static dev.tamboui.toolkit.Toolkit.panel;
import static dev.tamboui.toolkit.Toolkit.table;
import static dev.tamboui.toolkit.Toolkit.text;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

import dev.tamboui.layout.Constraint;
import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.toolkit.app.ToolkitApp;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.elements.TableElement;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.Event;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.table.Cell;
import dev.tamboui.widgets.table.Row;
import dev.tamboui.widgets.table.TableState;

public class MarketTracker extends ToolkitApp {
    private final YahooFinanceClient client = new YahooFinanceClient();
    private final TableState tableState = new TableState();
    private List<QuoteData> markets = List.of();
    private List<QuoteData> quotes = List.of();

    static void main() throws Exception {
        new MarketTracker().run();
    }

    @Override
    protected void onStart() {
        runner().eventRouter().addGlobalHandler(this::handleEvent);
        tableState.selectFirst();

        CompletableFuture.runAsync(this::fetchQuotes);
    }

    private void fetchQuotes() {
        try {
            var response = client.getQuotes(List.of("^DJI", "^IXIC", "^GSPC", "ORCL", "IBM", "NVDA", "SPCX"));
            Predicate<QuoteData> predicate = (QuoteData q) -> !q.symbol().startsWith("^") && !q.symbol().contains("=");
            markets = response.stream().filter(q -> !predicate.test(q)).toList();
            quotes = response.stream().filter(predicate).toList();
        } catch (IOException | URISyntaxException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected Element render() {
        return panel(
                markets(),
                stocks(),
                text("Press 'q' to quit").dim()
        )
                .borderless();
    }

    private EventResult handleEvent(Event event) {
        if (!(event instanceof KeyEvent)) {
            return EventResult.UNHANDLED;
        }
        KeyEvent key = (KeyEvent) event;
        if (key.isDown()) {
            tableState.selectNext(quotes.size());
            return EventResult.HANDLED;
        }
        if (key.isUp()) {
            tableState.selectPrevious();
            return EventResult.HANDLED;
        }
//        if (key.isCharIgnoreCase('t')) {
//            themeIndex = (themeIndex + 1) % THEMES.length;
//            applyTheme();
//            return EventResult.HANDLED;
//        }
        if (key.isCharIgnoreCase('q')) {
            quit();
            return EventResult.HANDLED;
        }
        return EventResult.UNHANDLED;
    }

    protected Element markets() {
        return panel("Markets",
                text("1"),
                text("2"))
                .padding(0);
    }

    protected Element stocks() {
        return panel("Stocks", stockTable()).fill(20);
    }

    private TableElement stockTable() {
        List<String> headers = List.of(
                "Ticker",
                "Current",
                "Change",
                "Change%",
                "Open",
                "Low",
                "High",
                "52wk Low",
                "52wk High",
                "Volume",
                "AvgVolume",
                "P/E",
                "Dividend",
                "Yield",
                "MktCap"
        );
        TableElement table = table()
                .header(Row.from(
                        headers.stream().map(s -> Cell.from(s).style(Style.EMPTY.bold())).toList()
                ).style(Style.EMPTY.fg(Color.YELLOW)))
                .widths(
                        Constraint.length(6), // Symbol
                        Constraint.length(8), // Current
                        Constraint.length(8), // Change
                        Constraint.length(8), // Change %
                        Constraint.length(9), // Open
                        Constraint.length(9), // Low
                        Constraint.length(9), // High
                        Constraint.length(9), // 52wk Low
                        Constraint.length(9), // 52wk High
                        Constraint.length(8), // Volume
                        Constraint.length(10), // Avg Volume
                        Constraint.length(6), // P/E
                        Constraint.length(9), // Dividend
                        Constraint.length(6), // Yield
                        Constraint.fill()
                )
                .rows(rowsFromQuote())
                .highlightStyle(Style.EMPTY.bg(Color.BLUE).fg(Color.WHITE).bold())
                .highlightSymbol("▶ ")
                .state(tableState);
        return table;
    }

    private List<Row> rowsFromQuote() {
        return quotes.stream()
                .filter(q -> !q.symbol().startsWith("^") && !q.symbol().contains("="))
                .map(q -> Row.from(
                        Cell.from(q.symbol()).style(Style.EMPTY.bold()),
                        dollarCell(q.currentPrice()),
                        amountChangeCell(q.change()),
                        percentageCell(q.percentChange()),
                        dollarCell(q.openPrice()),
                        dollarCell(q.low()),
                        dollarCell(q.high()),
                        dollarCell(q.low52()),
                        dollarCell(q.high52()),
                        Cell.from(String.format("%,.2fM", q.volume() / 1_000_000)),
                        Cell.from(String.format("%,.2fM", q.averageVolume() / 1_000_000)),
                        Cell.from(String.format("%,.2f", q.peRatio())),
                        dollarCell(q.dividend()),
                        Cell.from(String.format("%,.2f%%", q.yield())),
                        Cell.from(String.format("%,.2fB", (q.marketCap() / 1_000_000_000)))
                )).toList();
    }

    private Cell percentageCell(double value) {
        var style = value > 0 ? Style.EMPTY.fg(Color.GREEN) : Style.EMPTY.bold().fg(Color.RED);
        return Cell.from(String.format("%,.2f%%", value)).style(style);
    }

    private Cell dollarCell(double value) {
        return Cell.from(String.format("$%,.2f", value));
    }

    private Cell amountChangeCell(double value) {
        var style = value > 0 ? Style.EMPTY.fg(Color.GREEN) : Style.EMPTY.bold().fg(Color.RED);
        return Cell.from(String.format("$%,.2f", value)).style(style);
    }
}
