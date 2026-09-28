package init;

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;

import java.io.FileReader;
import java.io.IOException;
import java.security.PrivateKey;
import java.util.Properties;

import static java.security.Security.addProvider;


public class ServerConfig {
    private final PrivateKey privateKey;
    private final String issuerName;

    public ServerConfig(String keyFilePath, String propertiesFilePath) throws Exception {
        addProvider(new BouncyCastleProvider());

        Properties props = new Properties();
        try (FileReader propsReader = new FileReader(propertiesFilePath)) {
            props.load(propsReader);
            this.issuerName = props.getProperty("issuer.name", "CN=KeyGenServerIssuer");
        }

        this.privateKey = loadKey(keyFilePath);
    }

    private PrivateKey loadKey(String filePath) throws IOException{
        try (PEMParser pemParser = new PEMParser(new FileReader(filePath))) {
            Object object = pemParser.readObject();
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider("BC");

            if (object instanceof PEMKeyPair) {
                return converter.getPrivateKey(((PEMKeyPair) object).getPrivateKeyInfo());
            } else if (object instanceof PrivateKeyInfo) {
                return converter.getPrivateKey((PrivateKeyInfo) object);
            } else {
                throw new IllegalArgumentException("Неизвестный формат ключа в файле: " + object.getClass().getName());
            }
        }
    }

    public PrivateKey getServerPrivateKey() {
        return privateKey;
    }

    public String getIssuerName() {
        return issuerName;
    }


}
