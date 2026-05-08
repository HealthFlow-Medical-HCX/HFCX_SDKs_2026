package eg.gov.healthflow.hfcx.sdk.client.crossfixtures;

import eg.gov.healthflow.hfcx.sdk.core.crypto.JweEncryption;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.cert.CertificateException;
import java.security.spec.X509EncodedKeySpec;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;

/**
 * One-shot helper that regenerates {@code java-produced.jwe} for the
 * cross-SDK round-trip fixture under
 * {@code sdk-python/tests/fixtures/cross-sdk/}.
 *
 * <p>Invocation (from the {@code sdk-java/} directory):
 *
 * <pre>{@code
 * ./mvnw -B -ntp -pl hfcx-sdk-client \
 *     test-compile exec:java \
 *     -Dexec.mainClass=eg.gov.healthflow.hfcx.sdk.client.crossfixtures.RegenerateCrossSdkJwe \
 *     -Dexec.classpathScope=test
 * }</pre>
 *
 * <p>The fixture directory is resolved relative to this file's source
 * tree at run time; both SDKs read the same on-disk artifacts.
 */
public final class RegenerateCrossSdkJwe {

    private RegenerateCrossSdkJwe() {}

    public static void main(String[] args) throws IOException, CertificateException {
        Path fixtureDir = locateFixtureDir();
        Path publicKeyPem = fixtureDir.resolve("public-key.pem");
        Path plaintext = fixtureDir.resolve("plaintext.json");
        Path output = fixtureDir.resolve("java-produced.jwe");

        if (!Files.exists(publicKeyPem)) {
            throw new IllegalStateException("Missing " + publicKeyPem);
        }
        if (!Files.exists(plaintext)) {
            throw new IllegalStateException("Missing " + plaintext);
        }

        RSAPublicKey publicKey = parsePublicKey(Files.readString(publicKeyPem, StandardCharsets.UTF_8));
        String payload = Files.readString(plaintext, StandardCharsets.UTF_8);
        String jwe = JweEncryption.encryptUtf8(payload, publicKey);
        Files.writeString(output, jwe, StandardCharsets.UTF_8);

        System.out.println("Wrote " + output + " (" + jwe.length() + " chars, "
                + jwe.split("\\.").length + " segments)");
    }

    /**
     * Walks up from this class's source directory to find the
     * monorepo-relative {@code sdk-python/tests/fixtures/cross-sdk/}
     * path. Avoids hardcoding an absolute path so the helper works
     * from any working directory.
     */
    private static Path locateFixtureDir() {
        Path candidate = Path.of("").toAbsolutePath();
        while (candidate != null) {
            Path fixtures = candidate.resolve("sdk-python/tests/fixtures/cross-sdk");
            if (Files.isDirectory(fixtures)) {
                return fixtures;
            }
            candidate = candidate.getParent();
        }
        throw new IllegalStateException(
                "Could not locate sdk-python/tests/fixtures/cross-sdk/ relative to "
                        + Path.of("").toAbsolutePath());
    }

    private static RSAPublicKey parsePublicKey(String pem) {
        String stripped = pem
                .replaceAll("-----BEGIN [^-]+-----", "")
                .replaceAll("-----END [^-]+-----", "")
                .replaceAll("\\s+", "");
        try {
            byte[] der = Base64.getDecoder().decode(stripped);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return (RSAPublicKey) kf.generatePublic(new X509EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse public-key.pem", e);
        }
    }
}
