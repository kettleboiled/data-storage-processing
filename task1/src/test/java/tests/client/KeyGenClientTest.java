package tests.client;

import client.KeyGenClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(30)
public class KeyGenClientTest {

    @TempDir
    Path tempDir;

    private ExecutorService executor;
    private final List<ServerSocket> servers = new ArrayList<>();

    private static final String FAKE_KEY = "-----BEGIN PRIVATE KEY-----\nKEY_DATA\n-----END PRIVATE KEY-----";
    private static final String FAKE_CERT = "-----BEGIN CERTIFICATE-----\nCERT_DATA\n-----END CERTIFICATE-----";
    private static final String FULL_PEM = FAKE_KEY + "\n" + FAKE_CERT + "\n";

    @BeforeEach
    void setUp() {
        executor = Executors.newCachedThreadPool();
    }

    @AfterEach
    void tearDown() throws IOException {
        for (ServerSocket server : servers) {
            server.close();
        }
        executor.shutdownNow();
    }

    private interface ServerHandler {
        void handle(Socket socket) throws Exception;
    }

    private int serveOnce(ServerHandler handler) throws IOException {
        ServerSocket server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        servers.add(server);
        executor.submit(() -> {
            try (Socket socket = server.accept()) {
                socket.setSoTimeout(10_000);
                handler.handle(socket);
            } catch (Exception ignored) {
            }
            return null;
        });
        return server.getLocalPort();
    }

    private static byte[] readUntilNul(InputStream in) throws IOException {
        ByteArrayOutputStream received = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) > 0) {
            received.write(b);
        }
        received.write(0);
        return received.toByteArray();
    }

    @Test
    void savesKeyAndCertificateAsPem() throws Exception {
        int port = serveOnce(socket -> {
            assertArrayEquals(new byte[]{'a', 'l', 'i', 'c', 'e', 0},
                    readUntilNul(socket.getInputStream()));
            OutputStream out = socket.getOutputStream();
            out.write(FULL_PEM.getBytes(StandardCharsets.US_ASCII));
            out.flush();
        });

        KeyGenClient
                .runClient("127.0.0.1", port, "alice", tempDir, 0, false);

        assertEquals(FAKE_KEY, Files.readString(tempDir.resolve("alice.key")).trim());
        assertEquals(FAKE_CERT, Files.readString(tempDir.resolve("alice.crt")).trim());
    }

    @Test
    void crashSendsNameAndCreatesNoFiles() throws Exception {
        byte[][] receivedBox = new byte[1][];
        int port = serveOnce(socket -> {
            receivedBox[0] = readUntilNul(socket.getInputStream());
            Thread.sleep(3_000);
        });

        KeyGenClient
                .runClient("127.0.0.1", port, "bob", tempDir, 0, true);

        for (int i = 0; i < 50 && receivedBox[0] == null; i++) {
            Thread.sleep(20);
        }
        assertArrayEquals(new byte[]{'b', 'o', 'b', 0}, receivedBox[0]);
        assertFalse(Files.exists(tempDir.resolve("bob.key")));
        assertFalse(Files.exists(tempDir.resolve("bob.crt")));
    }

    @Test
    void delayWaitsBeforeReading() throws Exception {
        int port = serveOnce(socket -> {
            readUntilNul(socket.getInputStream());
            OutputStream out = socket.getOutputStream();
            out.write(FULL_PEM.getBytes(StandardCharsets.US_ASCII));
            out.flush();
        });

        long startMs = System.currentTimeMillis();
        KeyGenClient.runClient("127.0.0.1", port, "slow", tempDir, 1, false);
        long elapsedMs = System.currentTimeMillis() - startMs;

        assertTrue(elapsedMs >= 950, "Клиент должен подождать не менее ~1 секунды");
        assertTrue(Files.exists(tempDir.resolve("slow.key")));
        assertTrue(Files.exists(tempDir.resolve("slow.crt")));
    }

    @Test
    void emptyResponseThrowsIOException() throws Exception {
        int port = serveOnce(socket -> {
            readUntilNul(socket.getInputStream());
        });

        assertThrows(IOException.class, () ->
                KeyGenClient
                        .runClient("127.0.0.1", port, "empty", tempDir, 0, false));
        assertFalse(Files.exists(tempDir.resolve("empty.key")));
    }

    @Test
    void malformedResponseWithoutCertHeaderThrowsIOException() throws Exception {
        int port = serveOnce(socket -> {
            readUntilNul(socket.getInputStream());
            OutputStream out = socket.getOutputStream();
            out.write(FAKE_KEY.getBytes(StandardCharsets.US_ASCII)); // Только ключ, без сертификата
            out.flush();
        });

        assertThrows(IOException.class, () ->
                KeyGenClient
                        .runClient("127.0.0.1", port, "broken", tempDir, 0, false));
        assertFalse(Files.exists(tempDir.resolve("broken.key")));
    }
}