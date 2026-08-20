package com.steeplesoft.ticker;

import java.time.Duration;

import dev.tamboui.toolkit.app.ToolkitRunner;
import dev.tamboui.tui.TuiConfig;

public class MarketTracker {

    static void main() throws Exception {
        MarketTrackerController controller = new MarketTrackerController();
        MarketTrackerView view = new MarketTrackerView(controller);
        // noTick(): redraw only when something changes (a key event, a resize, or the quote fetch
        // completing) rather than on a fixed timer.
        TuiConfig config = TuiConfig.builder()
//                .noTick()
                .tickRate(Duration.ofMillis(50))
                .build();
        try (ToolkitRunner runner = ToolkitRunner.create(config)) {
            runner.run(() -> view);
        }
    }


}
