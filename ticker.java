///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 25+
//DEPS dev.tamboui:tamboui-tui:0.4.0
//DEPS dev.tamboui:tamboui-toolkit:0.4.0
//DEPS dev.tamboui:tamboui-widgets:0.4.0
//DEPS dev.tamboui:tamboui-panama-backend:0.4.0
//DEPS org.apache.fory:fory-json:1.6.0
//DEPS com.steeplesoft:ticker:0.1

import com.steeplesoft.ticker.TickerApp;

void main(String... args) throws Exception {
    TickerApp.main(args);
}
