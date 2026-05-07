package eg.gov.healthflow.hfcx.sdk.client.recipient;

import eg.gov.healthflow.hfcx.sdk.core.exception.KeyUnavailableException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Objects;

/**
 * Reads an RSA private key from a PKCS#8 PEM file on disk.
 *
 * <p>The file is read on every call to {@link #getPrivateKey()} so a
 * key rotation that swaps the file on disk takes effect immediately.
 * If you want caching, wrap the provider in a memoising decorator at
 * the call site.
 */
public final class FileLocalKeyProvider implements LocalKeyProvider {

    private final Path path;

    public FileLocalKeyProvider(Path path) {
        this.path = Objects.requireNonNull(path, "path");
    }

    public FileLocalKeyProvider(String path) {
        this(Path.of(Objects.requireNonNull(path, "path")));
    }

    @Override
    public java.security.interfaces.RSAPrivateKey getPrivateKey() {
        try {
            String pem = Files.readString(path, StandardCharsets.UTF_8);
            return parsePkcs8(pem);
        } catch (IOException e) {
            throw new KeyUnavailableException(
                    "Failed to read private key file: " + path, e);
        }
    }

    private static java.security.interfaces.RSAPrivateKey parsePkcs8(String pem) {
        String stripped = pem
                .replaceAll("-----BEGIN [^-]+-----", "")
                .replaceAll("-----END [^-]+-----", "")
                .replaceAll("\\s+", "");
        try {
            byte[] der = Base64.getDecoder().decode(stripped);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return (java.security.interfaces.RSAPrivateKey)
                    kf.generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (IllegalArgumentException | NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new KeyUnavailableException(
                    "Failed to parse PKCS#8 PEM private key", e);
        }
    }
}
