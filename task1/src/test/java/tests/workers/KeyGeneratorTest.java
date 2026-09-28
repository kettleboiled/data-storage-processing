package tests.workers;

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import workers.KeyGenerator;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

import static org.junit.jupiter.api.Assertions.*;

public class KeyGeneratorTest {

    private static KeyPair serverKeyPair;
    private static final String ISSUER_NAME = "CN=TestServerIssuer";

    @BeforeAll
    static void setUpClass() throws Exception {
        Security.addProvider(new BouncyCastleProvider());
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        serverKeyPair = kpg.generateKeyPair();
    }

    @Test
    void testCallGeneratesValid8192KeyAndSignedCertificate() throws Exception {
        String clientName = "alice_test";
        KeyGenerator generator = new KeyGenerator(clientName, ISSUER_NAME, serverKeyPair.getPrivate());

        byte[] pemBytes = generator.call();
        assertNotNull(pemBytes);
        assertTrue(pemBytes.length > 0);

        String pemString = new String(pemBytes, StandardCharsets.US_ASCII);
        assertTrue(pemString.contains("-----BEGIN RSA PRIVATE KEY-----")
                || pemString.contains("-----BEGIN PRIVATE KEY-----"));
        assertTrue(pemString.contains("-----BEGIN CERTIFICATE-----"));

        PrivateKey extractedPrivateKey = null;
        X509Certificate extractedCert = null;

        try (PEMParser parser = new PEMParser(new StringReader(pemString))) {
            JcaPEMKeyConverter keyConverter = new JcaPEMKeyConverter().setProvider("BC");
            JcaX509CertificateConverter certConverter = new JcaX509CertificateConverter().setProvider("BC");

            Object obj;
            while ((obj = parser.readObject()) != null) {
                if (obj instanceof PEMKeyPair pemKeyPair) {
                    extractedPrivateKey = keyConverter.getPrivateKey(pemKeyPair.getPrivateKeyInfo());
                } else if (obj instanceof PrivateKeyInfo privateKeyInfo) {
                    extractedPrivateKey = keyConverter.getPrivateKey(privateKeyInfo);
                } else if (obj instanceof X509CertificateHolder certHolder) {
                    extractedCert = certConverter.getCertificate(certHolder);
                }
            }
        }

        assertNotNull(extractedPrivateKey,
                "Приватный ключ должен присутствовать в PEM");
        assertInstanceOf(RSAPrivateKey.class, extractedPrivateKey);
        RSAPrivateKey rsaPrivateKey = (RSAPrivateKey) extractedPrivateKey;
        assertEquals(8192, rsaPrivateKey.getModulus().bitLength(),
                "Размер ключа должен быть ровно 8192 бита");

        assertNotNull(extractedCert, "Сертификат должен присутствовать в PEM");
        extractedCert.checkValidity();

        assertEquals("CN=" + clientName, extractedCert.getSubjectX500Principal().getName());
        assertEquals(ISSUER_NAME, extractedCert.getIssuerX500Principal().getName());

        RSAPublicKey certPublicKey = (RSAPublicKey) extractedCert.getPublicKey();
        assertEquals(8192, certPublicKey.getModulus().bitLength());
        assertEquals(rsaPrivateKey.getModulus(), certPublicKey.getModulus(),
                "Модули приватного и публичного ключей должны совпадать");

        extractedCert.verify(serverKeyPair.getPublic(), "BC");
    }

    @Test
    void testInvalidIssuerThrowsException() {
        KeyGenerator generator = new KeyGenerator("bob",
                "InvalidIssuerFormatWithoutEquals", serverKeyPair.getPrivate());
        assertThrows(IllegalArgumentException.class, generator::call);
    }
}