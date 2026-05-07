package eg.gov.healthflow.hfcx.sdk.client.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import eg.gov.healthflow.hfcx.sdk.core.exception.AuthenticationException;
import eg.gov.healthflow.hfcx.sdk.core.exception.TechnicalException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Keycloak (or any OIDC-compliant) bearer-token client used by
 * {@code HfcxClient} to authenticate to the HFCX gateway.
 *
 * <h2>Caching</h2>
 *
 * Tokens are cached in a single {@code volatile} reference and refreshed
 * when within {@link Builder#refreshLeadTime(Duration)} of expiry
 * (default 60 seconds — matches the cross-SDK invariant). The cache
 * is per-instance; multiple {@code HfcxClient} instances that share a
 * single {@code KeycloakTokenClient} share its cache, which is the
 * intended pattern for a single client-id.
 *
 * <h2>Retry policy on 5xx</h2>
 *
 * Up to three retry attempts with exponential backoff
 * ({@code 1s / 2s / 4s} by default; override via
 * {@link Builder#retryDelays(List)}). A {@code 401} response is NOT
 * retried — it indicates the configured client credentials are invalid
 * and surfaces immediately as {@link AuthenticationException}.
 *
 * <h2>Persistence</h2>
 *
 * Tokens are NEVER written to disk. The single in-memory reference is
 * dropped on JVM exit. Verified by code review and by
 * {@code KeycloakTokenClientTest#tokenIsHeldInMemoryOnly}.
 */
public final class KeycloakTokenClient {

    private static final Logger log = LoggerFactory.getLogger(KeycloakTokenClient.class);

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final List<Duration> DEFAULT_RETRY_DELAYS = List.of(
            Duration.ofSeconds(1),
            Duration.ofSeconds(2),
            Duration.ofSeconds(4));

    private static final Duration DEFAULT_REFRESH_LEAD_TIME = Duration.ofSeconds(60);
    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final URI tokenEndpoint;
    private final String clientId;
    private final String clientSecret;
    private final HttpClient httpClient;
    private final Clock clock;
    private final Duration refreshLeadTime;
    private final Duration requestTimeout;
    private final List<Duration> retryDelays;
    private final Sleeper sleeper;

    /**
     * Single-slot cache. {@code volatile} so a fresh value written under the
     * lock is immediately visible to lock-free reads on other threads.
     */
    private volatile CachedToken cached;

    /** Mutex used by the slow-path refresh; never held during HTTP I/O of a cache hit. */
    private final Object refreshLock = new Object();

    private KeycloakTokenClient(Builder b) {
        this.tokenEndpoint = URI.create(Objects.requireNonNull(
                b.tokenEndpoint, "tokenEndpoint is required"));
        this.clientId = Objects.requireNonNull(b.clientId, "clientId is required");
        this.clientSecret = Objects.requireNonNull(b.clientSecret, "clientSecret is required");
        this.httpClient = b.httpClient != null ? b.httpClient
                : HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(5))
                        .build();
        this.clock = b.clock != null ? b.clock : Clock.systemUTC();
        this.refreshLeadTime = b.refreshLeadTime != null ? b.refreshLeadTime : DEFAULT_REFRESH_LEAD_TIME;
        this.requestTimeout = b.requestTimeout != null ? b.requestTimeout : DEFAULT_REQUEST_TIMEOUT;
        this.retryDelays = b.retryDelays != null ? List.copyOf(b.retryDelays) : DEFAULT_RETRY_DELAYS;
        this.sleeper = b.sleeper != null ? b.sleeper : Thread::sleep;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * @return a valid bearer token (cached if still fresh, otherwise fetched).
     * @throws AuthenticationException if Keycloak rejects the configured credentials with 401.
     * @throws TechnicalException for transport failures, 5xx after retry exhaustion, or any non-200/401 status.
     */
    public String getToken() {
        CachedToken snapshot = cached;
        if (snapshot != null && !needsRefresh(snapshot)) {
            return snapshot.accessToken;
        }
        synchronized (refreshLock) {
            // Double-checked: another thread may have refreshed while we waited.
            snapshot = cached;
            if (snapshot != null && !needsRefresh(snapshot)) {
                return snapshot.accessToken;
            }
            CachedToken fresh = fetchNewToken();
            cached = fresh;
            return fresh.accessToken;
        }
    }

    /**
     * Drops the cached token. The next {@link #getToken()} call will fetch a
     * fresh one. Useful when an upstream call returned 401 and the caller
     * suspects the cached token is no longer valid.
     */
    public void invalidate() {
        cached = null;
    }

    private boolean needsRefresh(CachedToken token) {
        Instant refreshAt = token.expiresAt.minus(refreshLeadTime);
        return !clock.instant().isBefore(refreshAt);
    }

    private CachedToken fetchNewToken() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(tokenEndpoint)
                .timeout(requestTimeout)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(formBody()))
                .build();

        for (int attempt = 0; ; attempt++) {
            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();

                if (status == 200) {
                    return parseTokenResponse(response.body());
                }
                if (status == 401) {
                    // 401 is a credential problem, not a transport blip — never retry.
                    throw new AuthenticationException(
                            "Keycloak rejected client_id '" + clientId + "' with HTTP 401");
                }
                if (status >= 500 && status < 600) {
                    if (attempt < retryDelays.size()) {
                        Duration backoff = retryDelays.get(attempt);
                        log.warn("Keycloak returned HTTP {} (attempt {}/{}); retrying in {}",
                                status, attempt + 1, retryDelays.size() + 1, backoff);
                        sleepFor(backoff);
                        continue;
                    }
                    throw new TechnicalException("ERR-T-001",
                            "Keycloak returned HTTP " + status
                                    + " after " + (retryDelays.size() + 1) + " attempts");
                }
                throw new TechnicalException("ERR-T-001",
                        "Keycloak returned unexpected HTTP " + status + ": "
                                + truncate(response.body(), 200));
            } catch (java.io.IOException e) {
                if (attempt < retryDelays.size()) {
                    Duration backoff = retryDelays.get(attempt);
                    log.warn("Keycloak request failed ({}); retrying in {}",
                            e.getClass().getSimpleName(), backoff);
                    sleepFor(backoff);
                    continue;
                }
                throw new TechnicalException("ERR-T-001",
                        "Keycloak request failed after " + (retryDelays.size() + 1) + " attempts", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new TechnicalException("ERR-T-001",
                        "Keycloak request interrupted", e);
            }
        }
    }

    private String formBody() {
        return "grant_type=client_credentials"
                + "&client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
                + "&client_secret=" + URLEncoder.encode(clientSecret, StandardCharsets.UTF_8);
    }

    private CachedToken parseTokenResponse(String body) {
        try {
            JsonNode node = JSON.readTree(body);
            JsonNode access = node.get("access_token");
            JsonNode expires = node.get("expires_in");
            if (access == null || access.isNull() || access.asText().isEmpty()) {
                throw new TechnicalException("ERR-T-001",
                        "Keycloak response missing 'access_token': " + truncate(body, 200));
            }
            long expiresInSeconds = expires != null && expires.isIntegralNumber()
                    ? expires.asLong()
                    : 60L;
            return new CachedToken(access.asText(), clock.instant().plusSeconds(expiresInSeconds));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new TechnicalException("ERR-T-001",
                    "Keycloak response was not valid JSON: " + truncate(body, 200), e);
        }
    }

    private void sleepFor(Duration d) {
        try {
            sleeper.sleep(d.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TechnicalException("ERR-T-001",
                    "Interrupted while waiting to retry Keycloak request", e);
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return "null";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    /** Hook so tests can substitute a no-op sleeper without changing wall-clock semantics. */
    @FunctionalInterface
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    /** Immutable in-memory snapshot. Never serialized, never written to disk. */
    private static final class CachedToken {
        final String accessToken;
        final Instant expiresAt;

        CachedToken(String accessToken, Instant expiresAt) {
            this.accessToken = accessToken;
            this.expiresAt = expiresAt;
        }
    }

    public static final class Builder {
        private String tokenEndpoint;
        private String clientId;
        private String clientSecret;
        private HttpClient httpClient;
        private Clock clock;
        private Duration refreshLeadTime;
        private Duration requestTimeout;
        private List<Duration> retryDelays;
        private Sleeper sleeper;

        public Builder tokenEndpoint(String url) {
            this.tokenEndpoint = url;
            return this;
        }

        public Builder clientId(String id) {
            this.clientId = id;
            return this;
        }

        public Builder clientSecret(String secret) {
            this.clientSecret = secret;
            return this;
        }

        /** Override the default {@link HttpClient} (e.g. for custom timeouts or proxy). */
        public Builder httpClient(HttpClient httpClient) {
            this.httpClient = httpClient;
            return this;
        }

        /** Override the clock; used by tests to advance time without sleeping. */
        public Builder clock(Clock clock) {
            this.clock = clock;
            return this;
        }

        /**
         * How far before expiry the SDK proactively refreshes. Default 60s,
         * matching the cross-SDK invariant.
         */
        public Builder refreshLeadTime(Duration d) {
            this.refreshLeadTime = d;
            return this;
        }

        /** Per-request timeout. Default 10s. */
        public Builder requestTimeout(Duration d) {
            this.requestTimeout = d;
            return this;
        }

        /**
         * Retry-backoff delays applied between attempts on 5xx or transport
         * errors. Default {@code [1s, 2s, 4s]} = up to three retries =
         * four total attempts. Tests typically pass three zero durations.
         */
        public Builder retryDelays(List<Duration> delays) {
            this.retryDelays = delays;
            return this;
        }

        /** Internal — tests inject a no-op sleeper to avoid wall-clock waits. */
        Builder sleeper(Sleeper sleeper) {
            this.sleeper = sleeper;
            return this;
        }

        public KeycloakTokenClient build() {
            return new KeycloakTokenClient(this);
        }
    }
}
