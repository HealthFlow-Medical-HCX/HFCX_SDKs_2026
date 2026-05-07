package eg.gov.healthflow.hfcx.sdk.client.registry;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import eg.gov.healthflow.hfcx.sdk.core.exception.BusinessException;
import eg.gov.healthflow.hfcx.sdk.core.exception.ParticipantNotFoundException;
import eg.gov.healthflow.hfcx.sdk.core.exception.TechnicalException;
import eg.gov.healthflow.hfcx.sdk.core.exception.TransportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Resolves an HFCX participant code to the recipient's encryption public
 * key by querying the platform's Sunbird-RC participant registry, with
 * a Caffeine-backed cache.
 *
 * <h2>Caching</h2>
 *
 * Each entry's TTL is set to the X.509 cert's {@code notAfter} minus one
 * hour, so the SDK refreshes the key before the gateway starts rejecting
 * it as expired. The cache holds at most {@value #DEFAULT_MAX_ENTRIES}
 * entries; eviction is by access order. Hit / miss / load counts are
 * logged at INFO level on a periodic basis controlled by
 * {@link Builder#statsLogInterval(Duration)} (default: every 60 seconds
 * with at least one cache event since the last log).
 *
 * <h2>Errors</h2>
 *
 * <ul>
 *   <li>Registry HTTP 404 / "participant not found" → {@link BusinessException}
 *       with code {@code ERR-B-NF}.</li>
 *   <li>Registry HTTP 5xx after retry exhaustion, network failure, or
 *       cert-fetch failure → {@link TechnicalException} with code
 *       {@code ERR-T-001}.</li>
 *   <li>Cert PEM parsing failure or non-RSA cert → {@link TechnicalException}
 *       with code {@code ERR-T-001}.</li>
 * </ul>
 */
public final class RegistryClient implements RecipientCertResolver {

    private static final Logger log = LoggerFactory.getLogger(RegistryClient.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final int DEFAULT_MAX_ENTRIES = 10_000;
    private static final Duration DEFAULT_PRE_EXPIRY_BUFFER = Duration.ofHours(1);
    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration DEFAULT_STATS_LOG_INTERVAL = Duration.ofSeconds(60);

    /** Wire-format error code for "participant not found in the registry". */
    public static final String CODE_PARTICIPANT_NOT_FOUND = ParticipantNotFoundException.CODE;

    private final URI registryBaseUrl;
    private final HttpClient httpClient;
    private final Duration requestTimeout;
    private final Duration preExpiryBuffer;
    private final Cache<String, ParticipantCert> cache;
    private final long statsLogIntervalNanos;
    private final AtomicLong lastStatsLogNanos = new AtomicLong(System.nanoTime());

    private RegistryClient(Builder b) {
        this.registryBaseUrl = URI.create(Objects.requireNonNull(
                b.registryBaseUrl, "registryBaseUrl is required"));
        this.httpClient = b.httpClient != null ? b.httpClient
                : HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        this.requestTimeout = b.requestTimeout != null ? b.requestTimeout : DEFAULT_REQUEST_TIMEOUT;
        this.preExpiryBuffer = b.preExpiryBuffer != null ? b.preExpiryBuffer : DEFAULT_PRE_EXPIRY_BUFFER;
        int maxEntries = b.maxEntries != null ? b.maxEntries : DEFAULT_MAX_ENTRIES;
        Duration statsLogInterval = b.statsLogInterval != null
                ? b.statsLogInterval : DEFAULT_STATS_LOG_INTERVAL;
        this.statsLogIntervalNanos = statsLogInterval.toNanos();
        this.cache = Caffeine.newBuilder()
                .maximumSize(maxEntries)
                .expireAfter(new CertExpiry(this.preExpiryBuffer))
                .recordStats()
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * @return the recipient's encryption certificate, possibly served from cache.
     * @throws BusinessException ({@link #CODE_PARTICIPANT_NOT_FOUND}) if the
     *     registry has no entry for {@code participantCode}.
     * @throws TechnicalException for transport failures, cert-fetch failures,
     *     or PEM parse errors.
     */
    public ParticipantCert getRecipientCert(String participantCode) {
        Objects.requireNonNull(participantCode, "participantCode");
        ParticipantCert cert = cache.get(participantCode, this::fetch);
        maybeLogStats();
        return cert;
    }

    /** Drops the cache; the next lookup re-fetches. */
    public void invalidateAll() {
        cache.invalidateAll();
    }

    /** Drops a single entry. */
    public void invalidate(String participantCode) {
        cache.invalidate(participantCode);
    }

    private ParticipantCert fetch(String participantCode) {
        log.debug("registry: fetching participant '{}'", participantCode);

        URI registryEndpoint = registryBaseUrl.resolve("/api/v1/Participant/search");
        String body = "{\"filters\":{\"participant_code\":{\"eq\":\""
                + participantCode.replace("\"", "\\\"") + "\"}}}";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(registryEndpoint)
                .timeout(requestTimeout)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = sendOrThrow(request, "registry search");
        int status = response.statusCode();

        if (status == 404) {
            throw new ParticipantNotFoundException(
                    "Participant '" + participantCode + "' not found in registry");
        }
        if (status < 200 || status >= 300) {
            throw new TransportException(
                    "Registry search returned HTTP " + status);
        }

        String certUrl = extractEncryptionCertUrl(participantCode, response.body());

        // The encryption_cert URL is typically a static asset hosted by the
        // registry or a CDN. Issue a GET, parse the PEM, extract the RSA
        // public key and notAfter.
        HttpRequest certRequest = HttpRequest.newBuilder()
                .uri(URI.create(certUrl))
                .timeout(requestTimeout)
                .GET()
                .build();
        HttpResponse<String> certResponse = sendOrThrow(certRequest, "encryption_cert fetch");
        if (certResponse.statusCode() != 200) {
            throw new TransportException(
                    "encryption_cert fetch returned HTTP " + certResponse.statusCode()
                            + " for " + certUrl);
        }
        return parseCert(participantCode, certResponse.body());
    }

    private HttpResponse<String> sendOrThrow(HttpRequest request, String description) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (java.io.IOException e) {
            throw new TransportException(
                    description + " failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TransportException(
                    description + " interrupted", e);
        }
    }

    private static String extractEncryptionCertUrl(String participantCode, String responseBody) {
        try {
            JsonNode root = JSON.readTree(responseBody);
            JsonNode entries = root.isArray() ? root : root.get("entity");
            if (entries == null || !entries.isArray() || entries.isEmpty()) {
                throw new ParticipantNotFoundException(
                        "Registry response had no entry for '" + participantCode + "'");
            }
            JsonNode first = entries.get(0);
            JsonNode cert = first.get("encryption_cert");
            if (cert == null || cert.isNull() || cert.asText().isEmpty()) {
                throw new TransportException(
                        "Registry entry for '" + participantCode + "' has no encryption_cert");
            }
            return cert.asText();
        } catch (JsonProcessingException e) {
            throw new TransportException(
                    "Registry response for '" + participantCode + "' was not valid JSON", e);
        }
    }

    private static ParticipantCert parseCert(String participantCode, String pemOrDer) {
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate cert = (X509Certificate) cf.generateCertificate(
                    new ByteArrayInputStream(pemOrDer.getBytes(StandardCharsets.UTF_8)));
            if (!(cert.getPublicKey() instanceof RSAPublicKey rsaPublicKey)) {
                throw new TransportException(
                        "encryption_cert for '" + participantCode + "' is not RSA: "
                                + cert.getPublicKey().getAlgorithm());
            }
            Instant notAfter = cert.getNotAfter().toInstant();
            return new ParticipantCert(participantCode, rsaPublicKey, notAfter);
        } catch (CertificateException e) {
            throw new TransportException(
                    "Failed to parse encryption_cert PEM for '" + participantCode + "'", e);
        }
    }

    private void maybeLogStats() {
        long now = System.nanoTime();
        long last = lastStatsLogNanos.get();
        if (now - last < statsLogIntervalNanos) {
            return;
        }
        if (!lastStatsLogNanos.compareAndSet(last, now)) {
            return;
        }
        CacheStats s = cache.stats();
        log.info("registry cache stats: hits={} misses={} hitRate={} evictions={} loads={}",
                s.hitCount(), s.missCount(), formatRate(s.hitRate()),
                s.evictionCount(), s.loadCount());
    }

    private static String formatRate(double rate) {
        return Double.isNaN(rate) ? "n/a" : String.format("%.2f", rate);
    }

    /** Variable-TTL based on each cert's {@code notAfter}. */
    private static final class CertExpiry implements Expiry<String, ParticipantCert> {

        private final Duration preExpiryBuffer;

        CertExpiry(Duration preExpiryBuffer) {
            this.preExpiryBuffer = preExpiryBuffer;
        }

        @Override
        public long expireAfterCreate(String key, ParticipantCert value, long currentTime) {
            return ttlFor(value);
        }

        @Override
        public long expireAfterUpdate(String key, ParticipantCert value, long currentTime, long currentDuration) {
            return ttlFor(value);
        }

        @Override
        public long expireAfterRead(String key, ParticipantCert value, long currentTime, long currentDuration) {
            return currentDuration;
        }

        private long ttlFor(ParticipantCert value) {
            Duration ttl = Duration.between(Instant.now(), value.notAfter()).minus(preExpiryBuffer);
            if (ttl.isNegative() || ttl.isZero()) {
                return 0L;
            }
            return TimeUnit.NANOSECONDS.convert(ttl.toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    public static final class Builder {
        private String registryBaseUrl;
        private HttpClient httpClient;
        private Duration requestTimeout;
        private Duration preExpiryBuffer;
        private Integer maxEntries;
        private Duration statsLogInterval;

        public Builder registryBaseUrl(String registryBaseUrl) {
            this.registryBaseUrl = registryBaseUrl;
            return this;
        }

        public Builder httpClient(HttpClient httpClient) {
            this.httpClient = httpClient;
            return this;
        }

        public Builder requestTimeout(Duration requestTimeout) {
            this.requestTimeout = requestTimeout;
            return this;
        }

        /** How long before {@code notAfter} to expire each cache entry. Default 1 hour. */
        public Builder preExpiryBuffer(Duration preExpiryBuffer) {
            this.preExpiryBuffer = preExpiryBuffer;
            return this;
        }

        public Builder maxEntries(int maxEntries) {
            this.maxEntries = maxEntries;
            return this;
        }

        /** Minimum interval between cache-stats INFO log lines. Default 60s. */
        public Builder statsLogInterval(Duration interval) {
            this.statsLogInterval = interval;
            return this;
        }

        public RegistryClient build() {
            return new RegistryClient(this);
        }
    }
}
