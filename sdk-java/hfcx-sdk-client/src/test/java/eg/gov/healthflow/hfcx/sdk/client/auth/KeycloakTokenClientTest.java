package eg.gov.healthflow.hfcx.sdk.client.auth;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import eg.gov.healthflow.hfcx.sdk.core.exception.AuthenticationException;
import eg.gov.healthflow.hfcx.sdk.core.exception.TechnicalException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeycloakTokenClientTest {

    private static final String TOKEN_PATH = "/auth/realms/hcx/protocol/openid-connect/token";

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private MutableClock clock;
    private RecordingSleeper sleeper;

    @BeforeEach
    void setUp() {
        wm.resetAll();
        clock = new MutableClock(Instant.parse("2026-05-07T12:00:00Z"));
        sleeper = new RecordingSleeper();
    }

    private KeycloakTokenClient.Builder clientBuilder() {
        return KeycloakTokenClient.builder()
                .tokenEndpoint(wm.baseUrl() + TOKEN_PATH)
                .clientId("hfcx-sdk-test")
                .clientSecret("super-secret")
                .clock(clock)
                .sleeper(sleeper)
                // Three zero delays so the retry-policy shape is preserved
                // (max attempts = retries + 1) but tests don't sleep.
                .retryDelays(List.of(Duration.ZERO, Duration.ZERO, Duration.ZERO));
    }

    private static String tokenResponse(String accessToken, int expiresIn) {
        return "{\"access_token\":\"" + accessToken + "\","
                + "\"expires_in\":" + expiresIn + ","
                + "\"token_type\":\"Bearer\"}";
    }

    @Test
    void happyPathReturnsAccessTokenAndPostsClientCredentials() {
        wm.stubFor(post(urlEqualTo(TOKEN_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(tokenResponse("token-1", 300))));

        KeycloakTokenClient client = clientBuilder().build();

        assertEquals("token-1", client.getToken());
        wm.verify(postRequestedFor(urlEqualTo(TOKEN_PATH))
                .withHeader("Content-Type", equalTo("application/x-www-form-urlencoded")));
    }

    @Test
    void cachedTokenSkipsHttpCallWhileFresh() {
        wm.stubFor(post(urlEqualTo(TOKEN_PATH))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody(tokenResponse("token-cached", 300))));

        KeycloakTokenClient client = clientBuilder().build();

        client.getToken();
        client.getToken();
        client.getToken();

        wm.verify(1, postRequestedFor(urlEqualTo(TOKEN_PATH)));
    }

    @Test
    void tokenWithinRefreshLeadTimeTriggersFreshFetch() {
        // First response valid for 100s, second response valid for 100s.
        wm.stubFor(post(urlEqualTo(TOKEN_PATH))
                .inScenario("refresh")
                .whenScenarioStateIs("Started")
                .willReturn(aResponse().withStatus(200).withBody(tokenResponse("token-A", 100)))
                .willSetStateTo("after-first"));
        wm.stubFor(post(urlEqualTo(TOKEN_PATH))
                .inScenario("refresh")
                .whenScenarioStateIs("after-first")
                .willReturn(aResponse().withStatus(200).withBody(tokenResponse("token-B", 100))));

        KeycloakTokenClient client = clientBuilder()
                .refreshLeadTime(Duration.ofSeconds(60))
                .build();

        assertEquals("token-A", client.getToken());
        // Advance to 41s before expiry — past the 60s lead time, must refresh.
        clock.advance(Duration.ofSeconds(60));
        assertEquals("token-B", client.getToken());
        wm.verify(2, postRequestedFor(urlEqualTo(TOKEN_PATH)));
    }

    @Test
    void status401MapsToAuthenticationExceptionAndDoesNotRetry() {
        wm.stubFor(post(urlEqualTo(TOKEN_PATH))
                .willReturn(aResponse().withStatus(401).withBody("{\"error\":\"invalid_client\"}")));

        KeycloakTokenClient client = clientBuilder().build();

        AuthenticationException ex = assertThrows(AuthenticationException.class, client::getToken);
        assertEquals("ERR-T-002", ex.getCode());
        // 401 is a credential problem, not a transport blip — must NOT retry.
        wm.verify(1, postRequestedFor(urlEqualTo(TOKEN_PATH)));
        assertEquals(0, sleeper.totalSleepMillis());
    }

    @Test
    void status503RetriesThenSucceeds() {
        wm.stubFor(post(urlEqualTo(TOKEN_PATH))
                .inScenario("retry")
                .whenScenarioStateIs("Started")
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("one"));
        wm.stubFor(post(urlEqualTo(TOKEN_PATH))
                .inScenario("retry")
                .whenScenarioStateIs("one")
                .willReturn(aResponse().withStatus(503))
                .willSetStateTo("two"));
        wm.stubFor(post(urlEqualTo(TOKEN_PATH))
                .inScenario("retry")
                .whenScenarioStateIs("two")
                .willReturn(aResponse().withStatus(200).withBody(tokenResponse("token-recovered", 300))));

        KeycloakTokenClient client = clientBuilder().build();

        assertEquals("token-recovered", client.getToken());
        wm.verify(3, postRequestedFor(urlEqualTo(TOKEN_PATH)));
        // Two retries used → two recorded backoffs.
        assertEquals(2, sleeper.invocationCount());
    }

    @Test
    void status503ExhaustingRetriesRaisesTechnicalException() {
        wm.stubFor(post(urlEqualTo(TOKEN_PATH))
                .willReturn(aResponse().withStatus(503)));

        KeycloakTokenClient client = clientBuilder().build();

        TechnicalException ex = assertThrows(TechnicalException.class, client::getToken);
        assertEquals("ERR-T-001", ex.getCode());
        assertTrue(ex.getMessage().contains("503"));
        // 1 initial + 3 retries = 4 attempts.
        wm.verify(4, postRequestedFor(urlEqualTo(TOKEN_PATH)));
        assertEquals(3, sleeper.invocationCount());
    }

    @Test
    void connectionRefusedRaisesTechnicalExceptionAfterRetries() {
        // Build a client pointed at a port that nothing is listening on.
        // Use the WireMock port + 1 and stop WireMock for that port — easier
        // to just point at an unbound localhost port.
        int unboundPort = 1; // privileged port nothing should be bound to
        KeycloakTokenClient client = KeycloakTokenClient.builder()
                .tokenEndpoint("http://127.0.0.1:" + unboundPort + TOKEN_PATH)
                .clientId("hfcx-sdk-test")
                .clientSecret("super-secret")
                .clock(clock)
                .sleeper(sleeper)
                .requestTimeout(Duration.ofMillis(250))
                .retryDelays(List.of(Duration.ZERO, Duration.ZERO, Duration.ZERO))
                .build();

        TechnicalException ex = assertThrows(TechnicalException.class, client::getToken);
        assertEquals("ERR-T-001", ex.getCode());
        assertEquals(3, sleeper.invocationCount(), "should retry the configured number of times");
    }

    @Test
    void concurrentGetTokenCallsTriggerExactlyOneFetch() throws Exception {
        wm.stubFor(post(urlEqualTo(TOKEN_PATH))
                .willReturn(aResponse()
                        .withFixedDelay(50)
                        .withStatus(200)
                        .withBody(tokenResponse("token-concurrent", 300))));

        KeycloakTokenClient client = clientBuilder().build();

        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<String>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return client.getToken();
                }));
            }
            start.countDown();

            for (Future<String> f : results) {
                assertEquals("token-concurrent", f.get(5, TimeUnit.SECONDS));
            }
        } finally {
            pool.shutdownNow();
        }
        // Double-checked locking must collapse 16 concurrent calls to 1 HTTP fetch.
        wm.verify(1, postRequestedFor(urlEqualTo(TOKEN_PATH)));
    }

    /**
     * Static structural check: the only field that holds a token is
     * {@code volatile} and lives in heap memory. There is no
     * {@code transient}-bypass field, no {@code Path}-typed field, no
     * file-handle cache. Combined with the code-review requirement in
     * {@code CONTRIBUTING.md}, this makes "token never persisted to disk"
     * a property the build can enforce.
     */
    @Test
    void tokenIsHeldInMemoryOnly() throws Exception {
        Field[] fields = KeycloakTokenClient.class.getDeclaredFields();
        boolean foundCacheField = false;
        for (Field f : fields) {
            String typeName = f.getType().getName();
            assertTrue(!typeName.startsWith("java.io.File")
                            && !typeName.startsWith("java.nio.file.")
                            && !typeName.contains("Path"),
                    "KeycloakTokenClient must not hold filesystem references; found: " + f);
            if (f.getName().equals("cached")) {
                foundCacheField = true;
                assertTrue(java.lang.reflect.Modifier.isVolatile(f.getModifiers()),
                        "cache field must be volatile");
            }
        }
        assertTrue(foundCacheField, "expected a 'cached' field for the in-memory token");
    }

    @Test
    void invalidateForcesNextCallToRefetch() {
        wm.stubFor(post(urlEqualTo(TOKEN_PATH))
                .inScenario("invalidate")
                .whenScenarioStateIs("Started")
                .willReturn(aResponse().withStatus(200).withBody(tokenResponse("first", 300)))
                .willSetStateTo("second"));
        wm.stubFor(post(urlEqualTo(TOKEN_PATH))
                .inScenario("invalidate")
                .whenScenarioStateIs("second")
                .willReturn(aResponse().withStatus(200).withBody(tokenResponse("second", 300))));

        KeycloakTokenClient client = clientBuilder().build();

        assertEquals("first", client.getToken());
        client.invalidate();
        assertEquals("second", client.getToken());
        wm.verify(2, postRequestedFor(urlEqualTo(TOKEN_PATH)));
    }

    @Test
    void missingAccessTokenInResponseRaisesTechnicalException() {
        wm.stubFor(post(urlEqualTo(TOKEN_PATH))
                .willReturn(aResponse().withStatus(200).withBody("{\"error\":\"oops\"}")));

        KeycloakTokenClient client = clientBuilder().build();

        TechnicalException ex = assertThrows(TechnicalException.class, client::getToken);
        assertEquals("ERR-T-001", ex.getCode());
        assertTrue(ex.getMessage().toLowerCase().contains("access_token"));
    }

    @Test
    void builderRejectsMissingRequiredFields() {
        assertThrows(NullPointerException.class, () -> KeycloakTokenClient.builder().build());
        assertThrows(NullPointerException.class, () -> KeycloakTokenClient.builder()
                .tokenEndpoint("http://x")
                .build());
        assertThrows(NullPointerException.class, () -> KeycloakTokenClient.builder()
                .tokenEndpoint("http://x")
                .clientId("y")
                .build());
    }

    /** Test clock that lets cases advance time without sleeping. */
    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant initial) {
            this.now = initial;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    /** Records sleep calls without actually sleeping. */
    private static final class RecordingSleeper implements KeycloakTokenClient.Sleeper {
        private final AtomicInteger calls = new AtomicInteger();
        private long totalMillis = 0L;

        @Override
        public void sleep(long millis) {
            calls.incrementAndGet();
            totalMillis += millis;
        }

        int invocationCount() {
            return calls.get();
        }

        long totalSleepMillis() {
            return totalMillis;
        }
    }
}
