package com.steeplesoft.ticker;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.Provider;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import org.apache.fory.json.ForyJson;
import org.conscrypt.Conscrypt;

/**
 * Talks to Yahoo Finance's undocumented quote API. Yahoo requires a two-step handshake before it will serve quotes:
 * first fetch a session cookie (the {@code A1} cookie) by loading the finance home page as a browser would, then
 * exchange that cookie for a "crumb" token. The crumb and the cookie must both accompany every quote request or Yahoo
 * answers with HTTP 429.
 * <p>
 * A top-level navigation request primes the cookie, and the {@code A1} cookie is then sent explicitly as a
 * {@code Cookie} header (rather than relying on automatic cookie management) on the crumb and quote calls. Sending the
 * value verbatim also sidesteps {@link java.net.CookieManager}'s RFC 2965 {@code $Version} formatting, which Yahoo does
 * not accept.
 * <p>
 * Yahoo's edge additionally fingerprints the TLS ClientHello and answers HTTP 429 to the JDK's JSSE fingerprint. To get
 * past this, we route TLS through Conscrypt (BoringSSL), whose ClientHello resembles Chrome and is accepted.
 * <p>
 * HTTP is handled by the JDK's {@link HttpClient}; the Conscrypt {@link SSLContext} is handed to it so the handshake
 * bytes come from Conscrypt's {@code SSLEngine}.
 */
public class YahooFinanceClient implements AutoCloseable {
    static final String CRUMB_URL = "https://query1.finance.yahoo.com/v1/test/getcrumb";
    static final String URL = "https://query1.finance.yahoo.com/v7/finance/quote?crumb=%s&symbols=%s";

    // Loaded as a browser navigation to obtain the session (A1) cookie.
    private static final String COOKIE_URL = "https://finance.yahoo.com/";
    // Appended to the quote URL only, mirroring the query parameters a browser sends.
    private static final String QUERY_SUFFIX =
            "&range=1d&interval=5m&indicators=close&includeTimestamps=false&includePrePost=false"
                    + "&corsDomain=finance.yahoo.com&.tsrc=finance";

    // Yahoo rejects requests without a browser-like User-Agent. I've tried a fake one, but it seemed to reject that,
    // so we'll just masquerade as Firefox.
    private final String USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:154.0) Gecko/20100101 Firefox/154.0";

    private static final int HTTP_OK = 200;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private final HttpClient httpClient;
    private final ForyJson fory = ForyJson.builder().build();
    private final Object crumbLock = new Object();
    private volatile String crumb;
    // The "A1=<value>; " Cookie header value obtained from the priming request.
    private volatile String cookieHeader;

    public YahooFinanceClient() {
        // Route TLS through Conscrypt so Yahoo's edge accepts the ClientHello fingerprint. NORMAL redirect
        // following mirrors the priming navigation a browser performs.
        httpClient = HttpClient.newBuilder()
                .sslContext(createConscryptSslContext())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
    }

    /**
     * Builds an {@link SSLContext} backed by Conscrypt (BoringSSL). The provider is used only for this client's
     * context rather than being installed JVM-wide. Conscrypt's own {@link TrustManagerFactory} is required: the JDK's
     * default trust manager cannot handle Conscrypt's {@code GENERIC} auth type and fails the handshake.
     */
    private static SSLContext createConscryptSslContext() {
        try {
            Provider conscrypt = Conscrypt.newProvider();
            SSLContext sslContext = SSLContext.getInstance("TLS", conscrypt);
            TrustManagerFactory trustManagerFactory =
                    TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm(), conscrypt);
            trustManagerFactory.init((KeyStore) null);
            sslContext.init(null, trustManagerFactory.getTrustManagers(), null);
            return sslContext;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to initialize Conscrypt TLS", e);
        }
    }

    public List<QuoteData> getQuotes(List<String> symbols) throws IOException, InterruptedException, URISyntaxException {
        String uri = String.format(URL, getCrumb(),
                URLEncoder.encode(String.join(",", symbols), StandardCharsets.UTF_8)) + QUERY_SUFFIX;
        HttpResponse<String> response = httpClient.send(makeRequest(uri), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != HTTP_OK) {
            throw new IOException("Unexpected response status: " + response.statusCode());
        }
        var yahooResponse = fory.fromJson(response.body(), YahooResponse.class);
        if (yahooResponse.quoteResponse.error == null) {
            return yahooResponse.quoteResponse.result;
        } else {
            throw new RuntimeException("Unexpected error response: " + yahooResponse.quoteResponse.error);
        }
    }

    protected String getCrumb() {
        if (crumb == null) {
            synchronized (crumbLock) {
                if (crumb == null) {
                    // The cookie must be obtained before the crumb, and both are reused for quotes.
                    cookieHeader = fetchCookies();
                    crumb = fetchCrumb();
                }
            }
        }
        return crumb;
    }

    /**
     * Loads the Yahoo Finance home page as a browser navigation so Yahoo issues the session cookie,
     * then returns the {@code A1} cookie formatted as a {@code Cookie} header value. The response
     * body is irrelevant; only the {@code Set-Cookie} headers it returns matter.
     */
    private String fetchCookies() {
        try {
            HttpResponse<Void> response = httpClient.send(makeRequest(COOKIE_URL), HttpResponse.BodyHandlers.discarding());
            for (String setCookie : response.headers().allValues("Set-Cookie")) {
                for (HttpCookie cookie : HttpCookie.parse(setCookie)) {
                    if ("A1".equals(cookie.getName())) {
                        return "A1=" + cookie.getValue() + "; ";
                    }
                }
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Failed to obtain the session cookie", e);
        }

        throw new RuntimeException("Failed to obtain the session cookie");
    }

    private String fetchCrumb() {
        try {
            HttpResponse<String> response = httpClient.send(makeRequest(CRUMB_URL), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != HTTP_OK) {
                throw new IOException("Unexpected response status: " + response.statusCode());
            }
            return response.body();
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Failed to fetch crumb", e);
        }
    }

    private HttpRequest makeRequest(String uri) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(uri))
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .header("Accept",
                        "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,"
                                + "*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
                .header("User-Agent", USER_AGENT);
        if (cookieHeader != null) {
            builder.header("Cookie", cookieHeader);
        }
        return builder.build();
    }

    @Override
    public void close() {
        httpClient.close();
    }
}
