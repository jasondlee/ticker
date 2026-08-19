package com.steeplesoft.ticker;

import java.util.List;

public class YahooResponse {
    public QuoteResponse quoteResponse;

    public static class QuoteResponse {
        public List<QuoteData> result;
        public String error;
    }
}
