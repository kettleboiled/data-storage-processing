package tests.workers;

import init.ServerConfig;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import workers.KeyCache;
import workers.WorkerPool;

import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

public class KeyCacheTest {

    @TempDir
    static Path tempDir;

    private static ServerConfig validConfig;
    private WorkerPool workerPool;
    private KeyCache keyCache;

    @BeforeAll
    static void initConfig() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair serverKp = kpg.generateKeyPair();

        Path keyFile = tempDir.resolve("test-server.key");
        try (JcaPEMWriter writer = new JcaPEMWriter(new FileWriter(keyFile.toFile()))) {
            writer.writeObject(serverKp.getPrivate());
        }

        Path propsFile = tempDir.resolve("test-server.properties");
        Files.writeString(propsFile, "issuer.name=CN=CacheTestIssuer\n");

        validConfig = new ServerConfig(keyFile.toString(), propsFile.toString());
    }

    @AfterEach
    void tearDown() {
        if (workerPool != null) {
            workerPool.shutdown();
        }
    }

    @Test
    void testConstructorRejectsNonPositiveThreadCount() {
        assertThrows(IllegalArgumentException.class, ()
                -> new WorkerPool(0));
        assertThrows(IllegalArgumentException.class, ()
                -> new WorkerPool(-4));
    }

    @Test
    void testConcurrentRequestsReturnSameFutureAndIdenticalKeys() throws Exception {
        workerPool = new WorkerPool(2);
        keyCache = new KeyCache(validConfig, workerPool);
        String clientName = "concurrent_alice";

        int concurrentClients = 10;
        ExecutorService clientSimulatorPool = Executors.newFixedThreadPool(concurrentClients);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<CompletableFuture<byte[]>>> tasks = new ArrayList<>();

        for (int i = 0; i < concurrentClients; i++) {
            tasks.add(clientSimulatorPool.submit(() -> {
                startLatch.await();
                return keyCache.getOrGenerate(clientName);
            }));
        }

        startLatch.countDown();
        CompletableFuture<byte[]> firstFuture = tasks.get(0).get(5, TimeUnit.SECONDS);
        for (Future<CompletableFuture<byte[]>> task : tasks) {
            CompletableFuture<byte[]> currentFuture = task.get(5, TimeUnit.SECONDS);
            assertSame(firstFuture, currentFuture,
                    "Повторные запросы должны получать тот же объект CompletableFuture");
        }

        clientSimulatorPool.shutdown();

        byte[] generatedBytes = firstFuture.get(90, TimeUnit.SECONDS);
        assertNotNull(generatedBytes);
        assertTrue(generatedBytes.length > 1000);

        long startTime = System.nanoTime();
        CompletableFuture<byte[]> cachedFuture = keyCache.getOrGenerate(clientName);
        byte[] cachedBytes = cachedFuture.get(1, TimeUnit.SECONDS);
        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);

        assertTrue(durationMs < 50,
                "Повторный запрос из кэша должен выполняться мгновенно");
        assertArrayEquals(generatedBytes, cachedBytes,
                "Массив байтов из кэша должен полностью совпадать с исходным");
    }
}