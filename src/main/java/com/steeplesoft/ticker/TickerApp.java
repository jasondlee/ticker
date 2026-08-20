package com.steeplesoft.ticker;

import java.time.Duration;

import dev.tamboui.toolkit.app.ToolkitRunner;
import dev.tamboui.tui.TuiConfig;

public class TickerApp {
    public static void main(String... args) throws Exception {
        TickerController controller = new TickerController();
        TickerView view = new TickerView(controller);
        TuiConfig config = TuiConfig.builder()
                .tickRate(Duration.ofMillis(50))
                .build();
        try (ToolkitRunner runner = ToolkitRunner.create(config)) {
            runner.run(() -> view);
        }
    }
}
