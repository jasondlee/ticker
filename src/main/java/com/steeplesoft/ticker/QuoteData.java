package com.steeplesoft.ticker;

import org.apache.fory.json.annotation.JsonProperty;

public record QuoteData(
        @JsonProperty("symbol")
        String symbol,
        @JsonProperty("shortName")
        String name,
        @JsonProperty("regularMarketPrice")
        double currentPrice,
        @JsonProperty("regularMarketPreviousClose")
        double last,
        @JsonProperty("regularMarketChange")
        double change,
        @JsonProperty("regularMarketChangePercent")
        double percentChange,
        @JsonProperty("regularMarketOpen")
        double openPrice,
        @JsonProperty("regularMarketDayLow")
        double low,
        @JsonProperty("regularMarketDayHigh")
        double high,
        @JsonProperty("fiftyTwoWeekLow")
        double low52,
        @JsonProperty("fiftyTwoWeekHigh")
        double high52,
        @JsonProperty("regularMarketVolume")
        double volume,
        @JsonProperty("averageDailyVolume10Day")
        double averageVolume,
        @JsonProperty("trailingPE")
        double peRatio,
        @JsonProperty("dividendRate")
        double dividend,
        @JsonProperty("dividendYield")
        double yield,
        @JsonProperty("marketCap")
        double marketCap
) {
}
