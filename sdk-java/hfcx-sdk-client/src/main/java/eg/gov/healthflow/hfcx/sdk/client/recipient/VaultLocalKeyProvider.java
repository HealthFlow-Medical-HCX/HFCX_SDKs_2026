package eg.gov.healthflow.hfcx.sdk.client.recipient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import eg.gov.healthflow.hfcx.sdk.core.exception.TechnicalException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.Objects;

/**
 * Reads the recipient's PKCS#8 PEM private key from a HashiCorp Vault
 * KV v2 secret.
 *
 * <p>Expects the secret stored under {@code <mount>/data/<path>} to have
 * a field whose value is the PKCS#8 PEM:
 *
 * <pre>{@code
 *   $ vault kv put secret/hfcx/private-key value=@key.pem
 * }</pre>
 *
 * <h2>Scope of this implementation</h2>
 *
 * Sprint J5 ships a deliberately small Vault client: token-auth only,
 * single GET per call. Production deployments often want AppRole auth,
 * Vault Agent sidecar templating, namespaces, or token renewal — these
 * are NOT covered. Configure those at the deployment layer (Vault Agent
 * usually solves them all) or implement {@link LocalKeyProvider}
 * directly.
 *
 * <p>Like {@link FileLocalKeyProvider}, this provider does not cache the
 * fetched key — every call to {@link #getPrivateKey()} hits Vault.
 * Wrap with a memoising decorator if you want caching.
 */
public final class VaultLocalKeyProvider implements LocalKeyProvider {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final URI vaultBaseUrl;
    private final String secretMount;
    private final String secretPath;
    private final String secretField;
    private final String vaultToken;
    private final String vaultNamespace;
    private final HttpClient httpClient;
    private final Duration requestTimeout;

    private VaultLocalKeyProvider(Builder b) {
        this.vaultBaseUrl = URI.create(Objects.requireNonNull(
                b.vaultBaseUrl, "vaultBaseUrl is required"));
        this.secretMount = Objects.requireNonNull(b.secretMount, "secretMount is required");
        this.secretPath = Objects.requireNonNull(b.secretPath, "secretPath is required");
        this.secretField = b.secretField != null ? b.secretField : "value";
        this.vaultToken = Objects.requireNonNull(b.vaultToken, "vaultToken is required");
        this.vaultNamespace = b.vaultNamespace;
        this.httpClient = b.httpClient != null ? b.httpClient
                : HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        this.requestTimeout = b.requestTimeout != null ? b.requestTimeout : Duration.ofSeconds(10);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public RSAPrivateKey getPrivateKey() {
        URI endpoint = vaultBaseUrl.resolve(
                "/v1/" + secretMount + "/data/" + secretPath);
        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                .uri(endpoint)
                .timeout(requestTimeout)
                .header("X-Vault-Token", vaultToken)
                .header("Accept", "application/json")
                .GET();
        if (vaultNamespace != null) {
            reqBuilder.header("X-Vault-Namespace", vaultNamespace);
        }

        HttpResponse<String> response;
        try {
            response = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new TechnicalException("ERR-T-001",
                    "Vault request failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TechnicalException("ERR-T-001", "Vault request interrupted", e);
        }
        if (response.statusCode() == 403) {
            throw new TechnicalException("ERR-T-001",
                    "Vault token rejected (HTTP 403) for " + endpoint);
        }
        if (response.statusCode() == 404) {
            throw new TechnicalException("ERR-T-001",
                    "Vault secret not found at " + endpoint);
        }
        if (response.statusCode() != 200) {
            throw new TechnicalException("ERR-T-001",
                    "Vault returned HTTP " + response.statusCode() + " for " + endpoint);
        }
        return parsePem(extractField(response.body()));
    }

    private String extractField(String body) {
        try {
            JsonNode root = JSON.readTree(body);
            JsonNode data = root.path("data").path("data").path(secretField);
            if (data.isMissingNode() || data.isNull() || data.asText().isEmpty()) {
                throw new TechnicalException("ERR-T-001",
                        "Vault response missing field 'data.data." + secretField + "'");
            }
            return data.asText();
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new TechnicalException("ERR-T-001",
                    "Vault response was not valid JSON", e);
        }
    }

    private static RSAPrivateKey parsePem(String pem) {
        String stripped = pem
                .replaceAll("-----BEGIN [^-]+-----", "")
                .replaceAll("-----END [^-]+-----", "")
                .replaceAll("\\s+", "");
        try {
            byte[] der = Base64.getDecoder().decode(stripped);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return (RSAPrivateKey) kf.generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (IllegalArgumentException | NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new TechnicalException("ERR-T-001",
                    "Failed to parse PKCS#8 PEM from Vault secret", e);
        }
    }

    public static final class Builder {
        private String vaultBaseUrl;
        private String secretMount;
        private String secretPath;
        private String secretField;
        private String vaultToken;
        private String vaultNamespace;
        private HttpClient httpClient;
        private Duration requestTimeout;

        public Builder vaultBaseUrl(String vaultBaseUrl) {
            this.vaultBaseUrl = vaultBaseUrl;
            return this;
        }

        /** Default {@code "secret"}. */
        public Builder secretMount(String secretMount) {
            this.secretMount = secretMount;
            return this;
        }

        public Builder secretPath(String secretPath) {
            this.secretPath = secretPath;
            return this;
        }

        /** Default {@code "value"}. */
        public Builder secretField(String secretField) {
            this.secretField = secretField;
            return this;
        }

        public Builder vaultToken(String vaultToken) {
            this.vaultToken = vaultToken;
            return this;
        }

        public Builder vaultNamespace(String vaultNamespace) {
            this.vaultNamespace = vaultNamespace;
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

        public VaultLocalKeyProvider build() {
            if (secretMount == null) secretMount = "secret";
            return new VaultLocalKeyProvider(this);
        }
    }
}
