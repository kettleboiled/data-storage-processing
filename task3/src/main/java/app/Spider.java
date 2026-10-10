package app;

import app.parsing.ParsedResponse;
import app.parsing.ResponseParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Phaser;

public class Spider {
    private final HttpClient httpClient;
    private final ExecutorService executor;
    private final Phaser phaser;
    private final String baseUrl;
    private final ResponseParser parser;

    private final Set<String> visitedPaths = ConcurrentHashMap.newKeySet();
    private final ConcurrentLinkedQueue<String> messages = new ConcurrentLinkedQueue<>();

    public Spider(HttpClient httpClient,
                  ExecutorService executor,
                  Phaser phaser, int port,
                  ResponseParser parser) {
        this.httpClient = httpClient;
        this.executor = executor;
        this.phaser = phaser;
        this.baseUrl = "http://localhost:" + port;
        this.parser = parser;
    }

    public void crawl(String path) {
        try {
            if (!visitedPaths.add(path)) {
                return;
            }

            String fullPath = path.startsWith("/") ? path : "/" + path;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + fullPath))
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                System.out.println("Успещно прочитан путь: " + fullPath);
                processResponse(response.body());
            }

        } catch (Exception e) {
            System.err.println("Ошибка при обработке пути " + path + ": " + e.getMessage());
        } finally {
            phaser.arriveAndDeregister();
        }
    }

    private void processResponse(String jsonBody) {
        ParsedResponse data = parser.parse(jsonBody);

        if (data.message() != null) {
            messages.add(data.message());
        }

        if (data.successors() != null) {
            for (String nextPath : data.successors()) {
                if (!visitedPaths.contains(nextPath)) {
                    phaser.register();
                    executor.submit(() -> crawl(nextPath));
                }
            }
        }
    }

    public void printSortedResults() {
        List<String> sortedMessages = new ArrayList<>(messages);
        Collections.sort(sortedMessages);

        for (String msg : sortedMessages) {
            System.out.println(msg);
        }
    }

    public ConcurrentLinkedQueue<String> getMessages() {
        return this.messages;
    }
}