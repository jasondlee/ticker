package com.steeplesoft.ticker;

import java.io.IOException;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.fory.json.ForyJson;

public class YahooFinanceClient {
    final static String CRUMB_URL = "https://query1.finance.yahoo.com/v1/test/getcrumb";
    final static String URL = "https://query1.finance.yahoo.com/v7/finance/quote?crumb=%s&symbols=%s";

    // Yahoo rejects requests without a browser-like User-Agent.
    private final static String USER_AGENT = "Mozilla/5.0 (X11; Fedora; Linux x86_64; rv:153.0) Gecko/20100101 Firefox/153.0";
    public static final CookieManager COOKIE_MANAGER = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
//    private final static String USER_AGENT = "Steeplesoft/1.0 (Java) MarketTracker/1.0";

    private final HttpClient httpClient;
    private final ForyJson fory = ForyJson.builder().build();
    private volatile String crumb;// = "OIcqe4QHTdf";

    public YahooFinanceClient() {
        System.setProperty("jdk.httpclient.allowRestrictedHeaders", "Host,Connection");
        CookieHandler.setDefault(COOKIE_MANAGER);
        httpClient = HttpClient.newBuilder()
                .cookieHandler(COOKIE_MANAGER)
                .build();
    }

    public List<QuoteData> getQuotes(List<String> symbols) throws IOException, InterruptedException, URISyntaxException {
        String uri = String.format(URL, getCrumb(), URLEncoder.encode(String.join(",", symbols),
                StandardCharsets.UTF_8));
        HttpResponse<String> response = httpClient.send(getRequestBuilder(uri).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Unexpected response status: " + response.statusCode());
        }
        String body = response.body();
        Files.writeString(Path.of(System.currentTimeMillis() + ".json"), body);
        var yahooResponse = fory.fromJson(
                body,
                YahooResponse.class);
        if (yahooResponse.quoteResponse.error == null) {
            return yahooResponse.quoteResponse.result;
        } else {
            throw new RuntimeException("Unexpected error response: " + yahooResponse.quoteResponse.error);
        }
    }

    public List<QuoteData> getQuotes2(List<String> symbols) throws IOException, InterruptedException, URISyntaxException {
        var yahooResponse = fory.fromJson(
                Files.readString(Path.of(getClass().getClassLoader().getResource("test.json").toURI())),
                YahooResponse.class);
        if (yahooResponse.quoteResponse.error == null) {
            return yahooResponse.quoteResponse.result;
        } else {
            throw new RuntimeException("Unexpected error response: " + yahooResponse.quoteResponse.error);
        }
    }

    protected String getCrumb() {
        if (crumb == null) {
            synchronized (CRUMB_URL) {
                if (crumb == null) {
                    crumb = fetchCrumb();
                }
            }
        }
        return crumb;
    }

    private String fetchCrumb() {
        prime();

        try {
            HttpResponse<String> response = httpClient.send(getRequestBuilder(CRUMB_URL).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 429) {
                throw new RuntimeException(response.headers().toString());
            }
            if (response.statusCode() != 200) {
                throw new IOException("Unexpected response status: " + response.statusCode());
            }
            return response.body();
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Failed to fetch crumb", e);
        }
    }

    /**
     * Sends a priming request so the CookieManager captures Yahoo's session cookies.
     * The endpoint typically responds with a non-200 status while still setting the cookies we
     * need, so the response status and body are intentionally ignored.
     */
    private void prime() {
        // Hitting this endpoint yields the session cookies Yahoo requires before it will issue a crumb.
        try {
            httpClient.send(getRequestBuilder("https://finance.yahoo.com?a=1").GET().build(),
                    HttpResponse.BodyHandlers.discarding());
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Failed to prime session", e);
        }
    }

    private HttpRequest.Builder getRequestBuilder(String uri) {
        return HttpRequest.newBuilder()
                .uri(URI.create(uri //))
                       + "&range=1d&interval=5m&indicators=close&includeTimestamps=false&includePrePost=false&corsDomain=finance.yahoo.com&.tsrc=finance"))
                .header("Accept", "*/*")
                .header("Accept-Language", "en-US,en;q=0.5")
//                .header("Connection", "keep-alive")
                .header("Content-Type", "application/json")
                .header("Host", "query1.finance.yahoo.com")
                .header("Origin", "https://finance.yahoo.com")
                .header("Referer", "https://finance.yahoo.com")
                .header("Sec-Fetch-Dest", "empty")
                .header("Sec-Fetch-Mode", "cors")
                .header("Sec-Fetch-Site", "same-site")
                .header("TE", "trailers")
                .header("User-Agent", USER_AGENT);
    }
}
