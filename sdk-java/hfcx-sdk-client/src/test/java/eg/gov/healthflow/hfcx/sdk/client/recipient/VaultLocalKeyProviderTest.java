package eg.gov.healthflow.hfcx.sdk.client.recipient;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import eg.gov.healthflow.hfcx.sdk.client.testsupport.TestCerts;
import eg.gov.healthflow.hfcx.sdk.core.exception.TechnicalException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.security.interfaces.RSAPrivateKey;
import java.time.Duration;
import java.util.Base64;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VaultLocalKeyProviderTest {

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort()).build();

    private static TestCerts.GeneratedCert cert;

    @BeforeAll
    static void generateKey() throws Exception {
        cert = TestCerts.generate("vault-test", Duration.ofDays(30));
    }

    @BeforeEach
    void resetWireMock() {
        wm.resetAll();
    }

    private VaultLocalKeyProvider build() {
        return VaultLocalKeyProvider.builder()
                .vaultBaseUrl(wm.baseUrl())
                .secretMount("secret")
                .secretPath("hfcx/private-key")
                .vaultToken("hvs.test-token")
                .build();
    }

    private static String pkcs8Pem(RSAPrivateKey key) {
        StringBuilder sb = new StringBuilder("-----BEGIN PRIVATE KEY-----\n");
        sb.append(Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(key.getEncoded()));
        sb.append("\n-----END PRIVATE KEY-----\n");
        return sb.toString();
    }

    @Test
    void successfulFetchReturnsParsedKey() {
        wm.stubFor(get(urlEqualTo("/v1/secret/data/hfcx/private-key"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"data\":{\"data\":{\"value\":"
                                + JsonStrings.escape(pkcs8Pem(cert.privateKey()))
                                + "}}}")));
        VaultLocalKeyProvider provider = build();

        assertEquals(cert.privateKey().getModulus(), provider.getPrivateKey().getModulus());
        wm.verify(getRequestedFor(urlEqualTo("/v1/secret/data/hfcx/private-key"))
                .withHeader("X-Vault-Token", equalTo("hvs.test-token")));
    }

    @Test
    void namespaceHeaderIncludedWhenConfigured() {
        wm.stubFor(get(urlEqualTo("/v1/secret/data/hfcx/private-key"))
                .willReturn(aResponse().withStatus(200)
                        .withBody("{\"data\":{\"data\":{\"value\":"
                                + JsonStrings.escape(pkcs8Pem(cert.privateKey()))
                                + "}}}")));
        VaultLocalKeyProvider provider = VaultLocalKeyProvider.builder()
                .vaultBaseUrl(wm.baseUrl())
                .secretPath("hfcx/private-key")
                .vaultToken("hvs.test-token")
                .vaultNamespace("egypt-tenant")
                .build();
        provider.getPrivateKey();
        wm.verify(getRequestedFor(urlEqualTo("/v1/secret/data/hfcx/private-key"))
                .withHeader("X-Vault-Namespace", equalTo("egypt-tenant")));
    }

    @Test
    void status403MapsToTechnicalException() {
        wm.stubFor(get(urlEqualTo("/v1/secret/data/hfcx/private-key"))
                .willReturn(aResponse().withStatus(403)));
        VaultLocalKeyProvider provider = build();
        TechnicalException ex = assertThrows(TechnicalException.class, provider::getPrivateKey);
        assertEquals("ERR-T-004", ex.getCode());
    }

    @Test
    void status404MapsToTechnicalException() {
        wm.stubFor(get(urlEqualTo("/v1/secret/data/hfcx/private-key"))
                .willReturn(aResponse().withStatus(404)));
        VaultLocalKeyProvider provider = build();
        assertThrows(TechnicalException.class, provider::getPrivateKey);
    }

    @Test
    void missingValueFieldRaisesTechnicalException() {
        wm.stubFor(get(urlEqualTo("/v1/secret/data/hfcx/private-key"))
                .willReturn(aResponse().withStatus(200)
                        .withBody("{\"data\":{\"data\":{\"other\":\"x\"}}}")));
        VaultLocalKeyProvider provider = build();
        TechnicalException ex = assertThrows(TechnicalException.class, provider::getPrivateKey);
        assertEquals("ERR-T-004", ex.getCode());
    }

    @Test
    void customSecretFieldIsHonoured() {
        wm.stubFor(get(urlEqualTo("/v1/secret/data/hfcx/private-key"))
                .willReturn(aResponse().withStatus(200)
                        .withBody("{\"data\":{\"data\":{\"private_key\":"
                                + JsonStrings.escape(pkcs8Pem(cert.privateKey()))
                                + "}}}")));
        VaultLocalKeyProvider provider = VaultLocalKeyProvider.builder()
                .vaultBaseUrl(wm.baseUrl())
                .secretPath("hfcx/private-key")
                .secretField("private_key")
                .vaultToken("hvs.test-token")
                .build();
        assertEquals(cert.privateKey().getModulus(), provider.getPrivateKey().getModulus());
    }

    /** Minimal helper for embedding a multi-line string into a JSON literal. */
    static final class JsonStrings {
        static String escape(String s) {
            return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
                    .replace("\n", "\\n").replace("\r", "\\r") + "\"";
        }
    }
}
