package eg.gov.healthflow.hfcx.sdk.client.recipient;

import eg.gov.healthflow.hfcx.sdk.client.testsupport.TestCerts;
import eg.gov.healthflow.hfcx.sdk.core.exception.TechnicalException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.interfaces.RSAPrivateKey;
import java.time.Duration;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FileLocalKeyProviderTest {

    @Test
    void readsRoundTrippablePkcs8Pem(@TempDir Path tmp) throws Exception {
        TestCerts.GeneratedCert generated = TestCerts.generate("test", Duration.ofDays(30));
        Path keyFile = tmp.resolve("key.pem");
        Files.writeString(keyFile, pkcs8Pem(generated.privateKey()));

        FileLocalKeyProvider provider = new FileLocalKeyProvider(keyFile);
        RSAPrivateKey key = provider.getPrivateKey();

        assertNotNull(key);
        assertEquals(generated.privateKey().getModulus(), key.getModulus());
    }

    @Test
    void rereadsFileEachCallSoRotationsTakeEffect(@TempDir Path tmp) throws Exception {
        TestCerts.GeneratedCert first = TestCerts.generate("first", Duration.ofDays(30));
        TestCerts.GeneratedCert second = TestCerts.generate("second", Duration.ofDays(30));
        Path keyFile = tmp.resolve("key.pem");
        Files.writeString(keyFile, pkcs8Pem(first.privateKey()));
        FileLocalKeyProvider provider = new FileLocalKeyProvider(keyFile);

        assertEquals(first.privateKey().getModulus(), provider.getPrivateKey().getModulus());
        Files.writeString(keyFile, pkcs8Pem(second.privateKey()));
        assertEquals(second.privateKey().getModulus(), provider.getPrivateKey().getModulus());
    }

    @Test
    void missingFileRaisesTechnicalException() {
        FileLocalKeyProvider provider = new FileLocalKeyProvider("/no/such/file.pem");
        TechnicalException ex = assertThrows(TechnicalException.class, provider::getPrivateKey);
        assertEquals("ERR-T-004", ex.getCode());
    }

    @Test
    void malformedPemRaisesTechnicalException(@TempDir Path tmp) throws Exception {
        Path keyFile = tmp.resolve("key.pem");
        Files.writeString(keyFile, "not a real pem");
        FileLocalKeyProvider provider = new FileLocalKeyProvider(keyFile);
        TechnicalException ex = assertThrows(TechnicalException.class, provider::getPrivateKey);
        assertEquals("ERR-T-004", ex.getCode());
    }

    private static String pkcs8Pem(RSAPrivateKey key) {
        StringBuilder sb = new StringBuilder("-----BEGIN PRIVATE KEY-----\n");
        sb.append(Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(key.getEncoded()));
        sb.append("\n-----END PRIVATE KEY-----\n");
        return sb.toString();
    }
}
