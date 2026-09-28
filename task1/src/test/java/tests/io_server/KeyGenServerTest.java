package tests.io_server;

import io_server.KeyGenServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import workers.KeyCache;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.Selector;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(15)
public class KeyGenServerTest {

    private int port;
    private KeyGenServer server;
    private ExecutorService serverThread;
    private StubKeyCache stubCache;

    private static class StubKeyCache extends KeyCache {
        private volatile Function<String, CompletableFuture<byte[]>> behavior = name ->
                CompletableFuture.supplyAsync(() -> {
                    try {
                        Thread.sleep(20);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return ("PEM_FOR_" + name).getBytes(StandardCharsets.US_ASCII);
                });

        StubKeyCache() {
            super(null, null);
        }

        void setBehavior(Function<String, CompletableFuture<byte[]>> behavior) {
            this.behavior = behavior;
        }

        @Override
        public CompletableFuture<byte[]> getOrGenerate(String clientName) {
            return behavior.apply(clientName);
        }
    }

    @BeforeEach
    void startServer() throws Exception {
        try (ServerSocket ss = new ServerSocket(0)) {
            port = ss.getLocalPort();
        }

        stubCache = new StubKeyCache();
        server = new KeyGenServer(port, stubCache);
        serverThread = Executors.newSingleThreadExecutor();
        serverThread.submit(() -> {
            try {
                server.start();
            } catch (IOException ignored) {
            }
        });

        waitForServerReady();
    }

    @AfterEach
    void stopServer() throws Exception {
        Field runningField = KeyGenServer.class.getDeclaredField("running");
        runningField.setAccessible(true);
        runningField.set(server, false);

        Field selectorField = KeyGenServer.class.getDeclaredField("selector");
        selectorField.setAccessible(true);
        Selector sel = (Selector) selectorField.get(server);
        if (sel != null) {
            sel.wakeup();
        }

        serverThread.shutdownNow();
    }

    private void waitForServerReady() throws Exception {
        Field selectorField = KeyGenServer.class.getDeclaredField("selector");
        selectorField.setAccessible(true);
        for (int i = 0; i < 100; i++) {
            if (selectorField.get(server) != null) {
                Thread.sleep(20);
                return;
            }
            Thread.sleep(20);
        }
    }

    private String requestKey(String name) throws IOException {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(5_000);
            OutputStream out = socket.getOutputStream();
            out.write(name.getBytes(StandardCharsets.US_ASCII));
            out.write(0x00);
            out.flush();

            InputStream in = socket.getInputStream();
            return new String(in.readAllBytes(), StandardCharsets.US_ASCII);
        }
    }

    @Test
    void servesSingleClientRequestSuccessfully() throws IOException {
        String response = requestKey("alice");
        assertEquals("PEM_FOR_alice", response);
    }

    @Test
    void handlesFragmentedNameDelivery() throws Exception {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(5_000);
            OutputStream out = socket.getOutputStream();

            out.write("bo".getBytes(StandardCharsets.US_ASCII));
            out.flush();
            Thread.sleep(50);

            out.write(new byte[]{'b', 0x00});
            out.flush();

            String response = new String(socket.getInputStream()
                    .readAllBytes(), StandardCharsets.US_ASCII);
            assertEquals("PEM_FOR_bob", response);
        }
    }

    @Test
    void survivesCrashingClientAndContinuesServingNextClients() throws Exception {
        try (Socket crashingSocket = new Socket("127.0.0.1", port)) {
            OutputStream out = crashingSocket.getOutputStream();
            out.write("crash_user".getBytes(StandardCharsets.US_ASCII));
            out.write(0x00);
            out.flush();
            crashingSocket.setSoLinger(true, 0);
        }

        String normalResponse = requestKey("healthy_user");
        assertEquals("PEM_FOR_healthy_user", normalResponse);
    }

    @Test
    void closesClientConnectionCleanlyWhenKeyGenerationFails() throws IOException {
        stubCache.setBehavior(name -> CompletableFuture
                .failedFuture(new RuntimeException("Simulated crypto error")));

        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(5_000);
            OutputStream out = socket.getOutputStream();
            out.write("fail_user".getBytes(StandardCharsets.US_ASCII));
            out.write(0x00);
            out.flush();

            byte[] received = socket.getInputStream().readAllBytes();
            assertEquals(0, received.length);
        }

        stubCache.setBehavior(name -> CompletableFuture
                .completedFuture("RECOVERED".getBytes(StandardCharsets.US_ASCII)));
        assertEquals("RECOVERED", requestKey("next_user"));
    }
}