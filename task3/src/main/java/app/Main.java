package app;

import app.parsing.ResponseParser;
import app.parsing.SimpleResponseParser;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Phaser;

public class Main {

    public static void main(String[] args) {
        Config config = parseArgs(args);

        Phaser phaser = new Phaser(1);

        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        ResponseParser parser = new SimpleResponseParser();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {

            Spider spider = new Spider(httpClient, executor, phaser, config.port(), parser);

            phaser.register();
            executor.submit(() -> spider.crawl("/"));

            phaser.arriveAndAwaitAdvance();

            spider.printSortedResults();

        } catch (Exception e) {
            System.err.println("Критическая ошибка выполнения: " + e.getMessage());
            System.exit(1);
        }
    }

    static Config parseArgs(String[] args) {
        int port = 8080;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Ошибка парсинга порта. Используется порт по умолчанию: 8080");
            }
        }
        return new Config(port);
    }

    record Config(int port) {}
}