package com.steeplesoft.ticker;

import static dev.tamboui.toolkit.Toolkit.dialog;
import static dev.tamboui.toolkit.Toolkit.dock;
import static dev.tamboui.toolkit.Toolkit.flow;
import static dev.tamboui.toolkit.Toolkit.panel;
import static dev.tamboui.toolkit.Toolkit.richText;
import static dev.tamboui.toolkit.Toolkit.table;
import static dev.tamboui.toolkit.Toolkit.text;
import static dev.tamboui.toolkit.Toolkit.textInput;

import java.util.List;

import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.element.RenderContext;
import dev.tamboui.toolkit.element.Size;
import dev.tamboui.toolkit.elements.DialogElement;
import dev.tamboui.toolkit.elements.RichTextElement;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.table.Cell;
import dev.tamboui.widgets.table.Row;

public class TickerView implements Element {
    public static final String FORMAT_CURRENCY = "$%,.2f";
    public static final String FORMAT_PERCENT = "%,4.2f%%";
    public static final String FORMAT_VOLUME = "%,.2fM";

    public static final int WIDTH_CURRENCY = 13;
    public static final int WIDTH_PERCENT = 8;
    public static final int WIDTH_VOLUME = 11;

    private final TickerController controller;
    private final List<String> stockTableHeaders = List.of(
            "Ticker",
            "Current Price",
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

    public TickerView(TickerController controller) {
        this.controller = controller;
    }

    @Override
    public Size preferredSize(int availableWidth, int availableHeight, RenderContext context) {
        return Size.UNKNOWN;
    }

    @Override
    public void render(Frame frame, Rect area, RenderContext context) {
        Element ui = dock()
                .top(header(), Constraint.length(3))
                .center(stocks())
                .bottom(footer(), Constraint.length(1));
        ui.render(frame, area, context);

        if (controller.currentDialog() == TickerController.DialogType.ADD_STOCK) {
            createInputDialog("Add Stock", "Enter stock symbol:", controller::addStock)
                    .render(frame, area, context);
        }
    }

    @Override
    public EventResult handleKeyEvent(KeyEvent event, boolean focused) {
        if (controller.currentDialog() == TickerController.DialogType.ADD_STOCK) {
            return handleDialogKey(event);
        }
        if (event.isDown()) {
            controller.tableState().selectNext(controller.stocks().size());
            return EventResult.HANDLED;
        }
        if (event.isUp()) {
            controller.tableState().selectPrevious();
            return EventResult.HANDLED;
        }
        if (event.isCharIgnoreCase('+')) {
            controller.promptAddStock();
            return EventResult.HANDLED;
        }
        if (event.isChar('-')) {
            controller.removeStock(controller.tableState().selected());
            return EventResult.HANDLED;
        }
//        if (key.isCharIgnoreCase('q')) {
//            runner.quit();
//            return false;
//        }
        if (event.isCharIgnoreCase('r')) {
            controller.refreshQuotes();
            return EventResult.HANDLED;
        }
        return EventResult.UNHANDLED;
    }

    protected Element header() {
        List<RichTextElement> lines = controller.markets().stream()
                .map(m -> richText(
                        Text.from(
                                Line.from(
                                        Span.styled(m.name() != null ? m.name() : m.symbol(), Style.EMPTY.fg(Color.YELLOW).bold()),
                                        Span.raw(String.format(" %,.2f", m.currentPrice())),
                                        Span.styled(String.format(" %+,.2f%%", m.percentChange()), posNegStyle(m.percentChange()))
                                )
                        )))
                .toList();
        return panel(flow(lines)
                .spacing(1))
                .title("Markets");
    }

    protected Element stocks() {
        return table()
                .header(Row.from(stockTableHeaders.stream().map(s -> Cell.from(s).style(Style.EMPTY.bold())).toList())
                        .style(Style.EMPTY.fg(Color.YELLOW)))
                .widths(
                        Constraint.length(
                                Math.max(8,
                                        controller.stocks().stream().map(s -> s.symbol().length()).max(Integer::compare).orElse(8))),
                        Constraint.length(WIDTH_CURRENCY), // Current
                        Constraint.length(WIDTH_CURRENCY), // Change
                        Constraint.length(10), // Change %
                        Constraint.length(WIDTH_CURRENCY), // Open
                        Constraint.length(WIDTH_CURRENCY), // Low
                        Constraint.length(WIDTH_CURRENCY), // High
                        Constraint.length(WIDTH_CURRENCY), // 52 wk Low
                        Constraint.length(WIDTH_CURRENCY), // 52 wk High
                        Constraint.length(WIDTH_VOLUME), // Volume
                        Constraint.length(WIDTH_VOLUME), // Avg Volume
                        Constraint.length(7), // P/E
                        Constraint.length(WIDTH_CURRENCY/2), // Dividend
                        Constraint.length(7), // Yield
                        Constraint.fill()
                )
                .rows(rowsFromQuote())
                .highlightStyle(Style.EMPTY.bg(Color.BLUE).fg(Color.WHITE).bold())
                .highlightSymbol("▶ ")
                .title("Stocks")
                .state(controller.tableState());
    }

    protected Element footer() {
        return text("[Up/Down] Navigate [+] Add stock [-] Remove stock [r] Refresh quotes [q] Quit").dim();
    }

    private EventResult handleDialogKey(KeyEvent key) {
        if (key.isCancel() || key.code() == KeyCode.ESCAPE) {
            controller.dismissDialog();
            return EventResult.HANDLED;
        }
        if (key.isConfirm() || key.code() == KeyCode.ENTER) {
            controller.addStock();
            return EventResult.HANDLED;
        }
        if (key.isDeleteBackward() || key.code() == KeyCode.BACKSPACE) {
            controller.inputState().deleteBackward();
            return EventResult.HANDLED;
        }
        if (key.isLeft()) {
            controller.inputState().moveCursorLeft();
            return EventResult.HANDLED;
        }
        if (key.isRight()) {
            controller.inputState().moveCursorRight();
            return EventResult.HANDLED;
        }
        if (key.code() == KeyCode.CHAR) {
            controller.inputState().insert(key.string());
            return EventResult.HANDLED;
        }
        return EventResult.UNHANDLED;
    }

    private List<Row> rowsFromQuote() {
        return controller.stocks().stream()
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
                        Cell.from(pad(String.format(FORMAT_VOLUME, q.volume() / 1_000_000), WIDTH_VOLUME)),
                        Cell.from(pad(String.format(FORMAT_VOLUME, q.averageVolume() / 1_000_000), WIDTH_VOLUME)),
                        Cell.from(pad(String.format("%,.2f", q.peRatio()), 6)),
                        Cell.from(pad(String.format(FORMAT_CURRENCY, q.dividend()), WIDTH_CURRENCY/2)),
                        Cell.from(pad(String.format(FORMAT_PERCENT, q.yield()), 7)),
                        Cell.from(pad(marketCap(q), 7))
                )).toList();
    }

    private Cell percentageCell(double value) {
        return Cell.from(pad(String.format(FORMAT_PERCENT, value), WIDTH_PERCENT))
                .style(posNegStyle(value));
    }

    private Cell dollarCell(double value) {
        return Cell.from(pad(String.format(FORMAT_CURRENCY, value), WIDTH_CURRENCY));
    }

    private Cell amountChangeCell(double value) {
        return Cell.from(pad(String.format(FORMAT_CURRENCY, value), WIDTH_CURRENCY)).style(posNegStyle(value));
    }

    private String marketCap(QuoteData quote) {
        double marketCap = quote.marketCap() / 1_000_000; // Market cap in millions
        if (marketCap > 1_000_000) {
            return String.format("%,.2fT", (marketCap / 1_000_000));
        }
        if (marketCap > 1_000) {
            return String.format("%,.2fB", (marketCap / 1_000));
        }
        return String.format(FORMAT_VOLUME, marketCap);
    }

    private DialogElement createInputDialog(String title, String prompt, Runnable onConfirm) {
        return dialog(title,
                text(prompt),
                textInput(controller.inputState()).cursorStyle(dev.tamboui.style.Style.EMPTY.fg(Color.CYAN).reversed()),
                text("[Enter] Confirm  [Esc] Cancel").dim()
        ).rounded()
                .borderColor(Color.CYAN)
                .width(Math.max(50, prompt.length() + 4))
                .onConfirm(onConfirm)
                .onCancel(controller::dismissDialog);
    }

    private String pad(String source, int width) {
        return String.format("%%%ds".formatted(width), source);
    }

    private Style posNegStyle(double value) {
        return value > 0 ? Style.EMPTY.fg(Color.GREEN) : Style.EMPTY.bold().fg(Color.RED);
    }
}
