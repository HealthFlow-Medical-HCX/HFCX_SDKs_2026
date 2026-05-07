package eg.gov.healthflow.hfcx.sdk.client.testsupport;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.StringWriter;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.Base64;
import java.util.Date;

/** Test-only helpers for generating self-signed RSA certs without committing PEM files. */
public final class TestCerts {

    private TestCerts() {}

    public record GeneratedCert(RSAPublicKey publicKey, RSAPrivateKey privateKey,
                                X509Certificate certificate, String pem) {}

    /** Generate a 2048-bit RSA self-signed cert with the given subject and lifetime. */
    public static GeneratedCert generate(String subjectCn, Duration validity) throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair pair = kpg.generateKeyPair();

        X500Name issuer = new X500Name("CN=" + subjectCn);
        BigInteger serial = BigInteger.valueOf(System.currentTimeMillis());
        Date notBefore = new Date();
        Date notAfter = Date.from(notBefore.toInstant().plus(validity));

        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                issuer, serial, notBefore, notAfter, issuer, pair.getPublic());
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(pair.getPrivate());
        X509Certificate cert = new JcaX509CertificateConverter()
                .getCertificate(builder.build(signer));

        StringWriter out = new StringWriter();
        out.write("-----BEGIN CERTIFICATE-----\n");
        out.write(Base64.getMimeEncoder(64, "\n".getBytes())
                .encodeToString(cert.getEncoded()));
        out.write("\n-----END CERTIFICATE-----\n");

        return new GeneratedCert(
                (RSAPublicKey) pair.getPublic(),
                (RSAPrivateKey) pair.getPrivate(),
                cert,
                out.toString());
    }
}
