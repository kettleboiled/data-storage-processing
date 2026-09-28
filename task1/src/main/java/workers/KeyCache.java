package workers;

import init.ServerConfig;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class KeyCache {

    private final ServerConfig config;
    private final WorkerPool workerPool;
    private final ConcurrentHashMap<String, CompletableFuture<byte[]>> cache = new ConcurrentHashMap<>();

    public KeyCache(ServerConfig config, WorkerPool workerPool) {
        this.config = config;
        this.workerPool = workerPool;
    }

    public CompletableFuture<byte[]> getOrGenerate(String clientName) {
        CompletableFuture<byte[]> future = cache.computeIfAbsent(clientName, this::startGeneration);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                cache.remove(clientName, future);
            }
        });

        return future;
    }

    private CompletableFuture<byte[]> startGeneration(String name) {
        KeyGenerator generatorTask = new KeyGenerator(
                name,
                config.getIssuerName(),
                config.getServerPrivateKey()
        );
        return workerPool.submit(generatorTask);
    }
}