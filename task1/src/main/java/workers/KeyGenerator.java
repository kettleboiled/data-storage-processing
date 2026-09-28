package workers;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.StringWriter;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.concurrent.Callable;

import static java.lang.System.currentTimeMillis;

public class KeyGenerator implements Callable<byte[]> {
    private static final int KEY_SIZE = 8192;
    private static final long VALIDITY_PERIOD_MS = 365L * 24 * 60 * 60 * 1000;
    private static final String SIGNATURE_ALGORITHM = "SHA256WithRSAEncryption";

    private final String clientName;
    private final String issuerName;
    private final PrivateKey privateKey;

    public KeyGenerator(String clientName, String issuerName, PrivateKey privateKey) {
        this.clientName = clientName;
        this.issuerName = issuerName;
        this.privateKey = privateKey;
    }

    @Override
    public byte[] call() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");

        SecureRandom secureRandom = new SecureRandom();
        keyPairGenerator.initialize(KEY_SIZE, secureRandom);

        KeyPair keyPair = keyPairGenerator.generateKeyPair();

        X500Name issuer = new X500Name(issuerName);
        X500Name subject = new X500Name("CN=" + clientName);

        BigInteger serialNumber = new BigInteger(160, new SecureRandom());

        Date notBefore = new Date(currentTimeMillis());
        Date notAfter = new Date(currentTimeMillis() + VALIDITY_PERIOD_MS);

        JcaX509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                issuer,
                serialNumber,
                notBefore,
                notAfter,
                subject,
                keyPair.getPublic()
        );

        ContentSigner signer = new JcaContentSignerBuilder(SIGNATURE_ALGORITHM)
                .setProvider("BC")
                .build(privateKey);

        X509CertificateHolder certHolder = certBuilder.build(signer);

        X509Certificate clientCert = new JcaX509CertificateConverter()
                .setProvider("BC")
                .getCertificate(certHolder);

        StringWriter stringWriter = new StringWriter();
        try (JcaPEMWriter pemWriter = new JcaPEMWriter(stringWriter)) {
            pemWriter.writeObject(keyPair.getPrivate());
            pemWriter.writeObject(clientCert);
        }

        String pemData = stringWriter.toString();
        return pemData.getBytes(StandardCharsets.US_ASCII);
    }
}
