package tests.init;

import init.ServerConfig;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.util.io.pem.PemObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;

import static org.junit.jupiter.api.Assertions.*;

public class ServerConfigTest {

    @TempDir
    Path tempDir;

    private static KeyPair testKeyPair;

    @BeforeAll
    static void initKeyPair() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        testKeyPair = kpg.generateKeyPair();
    }

    @Test
    void loadsPkcs1KeyAndCustomIssuerName() throws Exception {
        Path keyFile = tempDir.resolve("pkcs1.key");
        try (JcaPEMWriter writer = new JcaPEMWriter(new FileWriter(keyFile.toFile()))) {
            writer.writeObject(testKeyPair);
        }

        Path propsFile = tempDir.resolve("server.properties");
        Files.writeString(propsFile, "issuer.name=CN=CustomTestCA\n");

        ServerConfig config = new ServerConfig(keyFile.toString(), propsFile.toString());

        assertEquals("CN=CustomTestCA", config.getIssuerName());
        assertNotNull(config.getServerPrivateKey());
        assertArrayEquals(testKeyPair.getPrivate().getEncoded(), config.getServerPrivateKey().getEncoded());
    }

    @Test
    void loadsPkcs8KeyAndUsesDefaultIssuerWhenPropertyMissing() throws Exception {
        Path keyFile = tempDir.resolve("pkcs8.key");
        try (JcaPEMWriter writer = new JcaPEMWriter(new FileWriter(keyFile.toFile()))) {
            writer.writeObject(new PemObject("PRIVATE KEY", testKeyPair.getPrivate().getEncoded()));
        }

        Path propsFile = tempDir.resolve("empty.properties");
        Files.writeString(propsFile, "# Настройки по умолчанию\n");

        ServerConfig config = new ServerConfig(keyFile.toString(), propsFile.toString());

        assertEquals("CN=KeyGenServerIssuer", config.getIssuerName());
        assertNotNull(config.getServerPrivateKey());
        assertArrayEquals(testKeyPair.getPrivate().getEncoded(), config.getServerPrivateKey().getEncoded());
    }

    @Test
    void throwsIllegalArgumentExceptionWhenPemContainsPublicKeyInsteadOfPrivate() throws Exception {
        Path invalidKeyFile = tempDir.resolve("public_only.pem");
        try (JcaPEMWriter writer = new JcaPEMWriter(new FileWriter(invalidKeyFile.toFile()))) {
            writer.writeObject(testKeyPair.getPublic());
        }

        Path propsFile = tempDir.resolve("server.properties");
        Files.writeString(propsFile, "issuer.name=CN=Test\n");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                new ServerConfig(invalidKeyFile.toString(), propsFile.toString())
        );
        assertTrue(ex.getMessage().contains("Неизвестный формат ключа в файле"));
    }

    @Test
    void throwsFileNotFoundExceptionWhenFilesDoNotExist() {
        Path existingProps = tempDir.resolve("props.properties");

        assertThrows(FileNotFoundException.class, () ->
                new ServerConfig("non_existent.key", existingProps.toString())
        );

        assertDoesNotThrow(() -> Files.writeString(existingProps, "issuer.name=CN=Test\n"));
        assertThrows(FileNotFoundException.class, () ->
                new ServerConfig(tempDir.resolve("missing.key").toString(), existingProps.toString())
        );
    }
}
