package com.steeplesoft.ticker;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Layout;
import dev.tamboui.layout.Position;
import dev.tamboui.layout.Rect;
import dev.tamboui.layout.Size;
import dev.tamboui.style.Color;
import dev.tamboui.style.Style;
import dev.tamboui.terminal.Frame;
import dev.tamboui.text.Line;
import dev.tamboui.text.Span;
import dev.tamboui.text.Text;
import dev.tamboui.tui.TuiConfig;
import dev.tamboui.tui.TuiRunner;
import dev.tamboui.tui.event.Event;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.Clear;
import dev.tamboui.widgets.block.Block;
import dev.tamboui.widgets.block.BorderType;
import dev.tamboui.widgets.block.Borders;
import dev.tamboui.widgets.input.TextInput;
import dev.tamboui.widgets.input.TextInputState;
import dev.tamboui.widgets.paragraph.Paragraph;
import dev.tamboui.widgets.table.Cell;
import dev.tamboui.widgets.table.Row;
import dev.tamboui.widgets.table.Table;
import dev.tamboui.widgets.table.TableState;

/**
 * A terminal market ticker built with TamboUI's immediate-mode API.
 * <p>
 * The UI is redrawn each frame by {@link #render(Frame)}; {@link TuiRunner} owns the terminal
 * lifecycle and event loop while this class provides the renderer and event handler. State that is
 * shown on screen ({@link #quotes}, {@link #markets}, {@link #currentDialogType}) is only mutated on
 * the render thread, so no synchronization is required: the background quote fetch publishes its
 * results back via {@link TuiRunner#runOnRenderThread(Runnable)}.
 */
public class MarketTracker {
    private final YahooFinanceClient client = new YahooFinanceClient();
    private final TableState tableState = new TableState();
    private final TextInputState inputState = new TextInputState();
    private List<QuoteData> markets = List.of();
    private List<QuoteData> quotes = List.of();
    private DialogType currentDialogType = DialogType.NONE;

    static void main() throws Exception {
        new MarketTracker().run();
    }

    private void run() throws Exception {
        // noTick(): redraw only when something changes (a key event, a resize, or the quote fetch
        // completing) rather than on a fixed timer.
        TuiConfig config = TuiConfig.builder().noTick().build();
        try (TuiRunner runner = TuiRunner.create(config)) {
            tableState.selectFirst();
            CompletableFuture.runAsync(() -> fetchQuotes(runner));
            runner.run(this::handleEvent, this::render);
        }
    }

    private void fetchQuotes(TuiRunner runner) {
        try {
            var response = client.getQuotes(List.of("^DJI", "^IXIC", "^GSPC", "ORCL", "IBM", "NVDA", "SPCX"));
            Predicate<QuoteData> predicate = (QuoteData q) -> !q.symbol().startsWith("^") && !q.symbol().contains("=");
            var fetchedMarkets = response.stream().filter(q -> !predicate.test(q)).toList();
            var fetchedQuotes = response.stream().filter(predicate).toList();
            // Publish the results on the render thread, then force an immediate redraw so the freshly
            // fetched data appears without waiting for the next input event.
            runner.runOnRenderThread(() -> {
                markets = fetchedMarkets;
                quotes = fetchedQuotes;
                tableState.selectFirst();
                runner.draw(this::render);
            });
        } catch (IOException | URISyntaxException | InterruptedException e) {
            // Surface the failure through TuiRunner's error display instead of dying silently on the
            // background thread.
            runner.runOnRenderThread(() -> {
                throw new RuntimeException(e);
            });
        }
    }

    private void render(Frame frame) {
        Rect area = frame.area();
        List<Rect> rows = Layout.vertical()
                .constraints(
                        Constraint.length(5), // Markets panel: top/bottom border + three index rows
                        Constraint.fill(),    // Stocks table
                        Constraint.length(1)  // Footer
                )
                .split(area);

        renderMarkets(frame, rows.get(0));
        renderStocks(frame, rows.get(1));
        frame.renderWidget(
                Paragraph.builder().text("Press 'q' to quit").style(Style.EMPTY.dim()).build(),
                rows.get(2));

        if (currentDialogType == DialogType.ADD_STOCK) {
            renderAddStockDialog(frame, area);
        }
    }

    private boolean handleEvent(Event event, TuiRunner runner) {
        if (!(event instanceof KeyEvent key)) {
            return false;
        }
        if (currentDialogType == DialogType.ADD_STOCK) {
            return handleDialogKey(key);
        }
        if (key.isDown()) {
            tableState.selectNext(quotes.size());
            return true;
        }
        if (key.isUp()) {
            tableState.selectPrevious();
            return true;
        }
        if (key.isCharIgnoreCase('+')) {
            currentDialogType = DialogType.ADD_STOCK;
            return true;
        }
        if (key.isCharIgnoreCase('q')) {
            runner.quit();
            return false;
        }
        return false;
    }

    private boolean handleDialogKey(KeyEvent key) {
        if (key.isCancel() || key.code() == KeyCode.ESCAPE) {
            dismissDialog();
            return true;
        }
        if (key.isConfirm() || key.code() == KeyCode.ENTER) {
            addStock();
            dismissDialog();
            return true;
        }
        if (key.isDeleteBackward() || key.code() == KeyCode.BACKSPACE) {
            inputState.deleteBackward();
            return true;
        }
        if (key.isLeft()) {
            inputState.moveCursorLeft();
            return true;
        }
        if (key.isRight()) {
            inputState.moveCursorRight();
            return true;
        }
        if (key.code() == KeyCode.CHAR) {
            inputState.insert(key.string());
            return true;
        }
        return false;
    }

    private void renderMarkets(Frame frame, Rect area) {
        Block block = Block.builder().title("Markets").borders(Borders.ALL).build();
        List<Line> lines = markets.stream()
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
                .block(block)
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
                .block(Block.builder().title("Stocks").borders(Borders.ALL).build())
                .build();
        frame.renderStatefulWidget(table, area, tableState);
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

    private void renderAddStockDialog(Frame frame, Rect area) {
        int width = Math.min(50, Math.max(20, area.width() - 2));
        int height = 5;
        int x = area.x() + (area.width() - width) / 2;
        int y = area.y() + (area.height() - height) / 2;
        Rect dialogArea = Rect.of(new Position(x, y), new Size(width, height));

        // Clear whatever is underneath so the dialog reads as a modal overlay.
        frame.renderWidget(Clear.INSTANCE, dialogArea);

        Block block = Block.builder()
                .title("Add Stock")
                .borders(Borders.ALL)
                .borderType(BorderType.ROUNDED)
                .borderColor(Color.CYAN)
                .build();
        frame.renderWidget(block, dialogArea);

        Rect inner = block.inner(dialogArea);
        if (inner.isEmpty()) {
            return;
        }
        List<Rect> rows = Layout.vertical()
                .constraints(
                        Constraint.length(1), // Prompt
                        Constraint.length(1), // Input
                        Constraint.length(1)  // Hint
                )
                .split(inner);

        frame.renderWidget(Paragraph.from("Enter ticker symbol"), rows.get(0));

        TextInput input = TextInput.builder()
                .cursorStyle(Style.EMPTY.fg(Color.CYAN).reversed())
                .build();
        input.renderWithCursor(rows.get(1), frame.buffer(), inputState, frame);

        frame.renderWidget(
                Paragraph.builder().text("[Enter] Confirm  [Esc] Cancel").style(Style.EMPTY.dim()).build(),
                rows.get(2));
    }

    private void addStock() {
        // TODO: add the entered symbol (inputState.text()) to the tracked quotes and refetch.
    }

    private void dismissDialog() {
        currentDialogType = DialogType.NONE;
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
