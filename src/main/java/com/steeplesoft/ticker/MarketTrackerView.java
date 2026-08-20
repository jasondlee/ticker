package com.steeplesoft.ticker;

import static dev.tamboui.toolkit.Toolkit.dialog;
import static dev.tamboui.toolkit.Toolkit.text;
import static dev.tamboui.toolkit.Toolkit.textInput;

import java.util.List;

import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Layout;
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
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.paragraph.Paragraph;
import dev.tamboui.widgets.table.Cell;
import dev.tamboui.widgets.table.Row;
import dev.tamboui.widgets.table.Table;

public class MarketTrackerView implements Element {
    private final MarketTrackerController controller;

    public MarketTrackerView(MarketTrackerController controller) {
        this.controller = controller;
    }

    @Override
    public void render(Frame frame, Rect area, RenderContext context) {
        List<Rect> rows = Layout.vertical()
                .constraints(
                        Constraint.length(5), // Markets panel: top/bottom border + three index rows
                        Constraint.fill(),    // Stocks table
                        Constraint.length(1)  // Footer
                )
                .split(area);

        renderMarkets(frame, rows.get(0));
        if (!controller.stocks().isEmpty()) {
            renderStocks(frame, rows.get(1));
            if (controller.tableState().selected() > controller.stocks().size() - 1) {
                controller.tableState().select(controller.stocks().size() - 1);
            }
        } else {
            Block block = Block.builder().title("Stocks").borders(Borders.ALL).build();
            Paragraph panel = Paragraph.builder()
                    .block(block)
                    .text(Text.from("Loading..."))
                    .build();
            frame.renderWidget(panel, rows.get(1));
        }
        frame.renderWidget(
                Paragraph.builder().text("[Up/Down] Navigate [+] Add stock [-] Remove stock [r] Refresh quotes [q] Quit").style(Style.EMPTY.dim()).build(),
                rows.get(2));

        if (controller.currentDialog() == MarketTrackerController.DialogType.ADD_STOCK) {
            createInputDialog("Add Stock", "Enter stock symbol:", controller::addStock)
                .render(frame, area, context);
        }
    }

    @Override
    public Size preferredSize(int availableWidth, int availableHeight, RenderContext context) {
        return Size.UNKNOWN;
    }

    @Override
    public EventResult handleKeyEvent(KeyEvent event, boolean focused) {
        if (controller.currentDialog() == MarketTrackerController.DialogType.ADD_STOCK) {
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

    private void renderMarkets(Frame frame, Rect area) {
        List<Line> lines = controller.markets().stream()
                .map(m -> {
                    Style changeStyle = m.percentChange() >= 0
                            ? Style.EMPTY.fg(Color.GREEN)
                            : Style.EMPTY.bold().fg(Color.RED);
                    String label = m.name() != null ? m.name() : m.symbol();
                    return Line.from(
                            new Span(String.format("%-14s ", label), Style.EMPTY.bold()),
                            Span.raw(String.format("%,.2f ", m.currentPrice())),
                            new Span(String.format("%+,.2f%%", m.percentChange()), changeStyle)
                    );
                })
                .toList();
        Paragraph panel = Paragraph.builder()
                .block(Block.builder().title("Markets").borders(Borders.ALL).build())
                .text(Text.from(lines))
                .build();
        frame.renderWidget(panel, area);
    }

    private void renderStocks(Frame frame, Rect area) {
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
        Table table = Table.builder()
                .header(Row.from(headers.stream().map(s -> Cell.from(s).style(Style.EMPTY.bold())).toList())
                        .style(Style.EMPTY.fg(Color.YELLOW)))
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
                .block(Block.builder().title("Stocks").borders(Borders.ALL).build())
                .build();
        frame.renderStatefulWidget(table, area, controller.tableState());
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
}
