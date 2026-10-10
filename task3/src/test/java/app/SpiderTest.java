package app;

import app.parsing.ResponseParser;
import app.parsing.SimpleResponseParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Phaser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SpiderTest {

    @Mock
    private HttpClient mockHttpClient;
    @Mock
    private HttpResponse<String> mockResponse;

    private ResponseParser parser;
    private ExecutorService executor;
    private Phaser phaser;
    private Spider spider;

    @BeforeEach
    void setUp() {
        parser = new SimpleResponseParser();
        executor = Executors.newVirtualThreadPerTaskExecutor();
        phaser = new Phaser(1);
        spider = new Spider(mockHttpClient, executor, phaser, 8080, parser);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void testSuccessfulCrawlAndSorting() throws Exception {
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn("{\"message\":\"B_Node\", \"successors\":[\"/path1\", \"/path2\"]}")
                .thenReturn("{\"message\":\"A_Node\", \"successors\":[]}")
                .thenReturn("{\"message\":\"C_Node\", \"successors\":[]}");

        phaser.register();
        executor.submit(() -> spider.crawl("/"));

        phaser.arriveAndAwaitAdvance();

        verify(mockHttpClient, times(3)).send(any(), any());

        List<String> actualMessages = new ArrayList<>(spider.getMessages());
        Collections.sort(actualMessages);

        List<String> expectedMessages = List.of("A_Node", "B_Node", "C_Node");
        assertEquals(expectedMessages, actualMessages);
    }

    @Test
    void testVisitedPathsDeduplication() throws Exception {
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);
        when(mockResponse.statusCode()).thenReturn(200);

        when(mockResponse.body()).thenReturn("{\"message\":\"Loop\", \"successors\":[\"/loop\"]}");

        phaser.register();
        executor.submit(() -> spider.crawl("/loop"));
        phaser.arriveAndAwaitAdvance();

        verify(mockHttpClient, times(1)).send(any(), any());
    }

    @Test
    void testErrorResponseHandling() throws Exception {
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);
        when(mockResponse.statusCode()).thenReturn(500);

        phaser.register();
        executor.submit(() -> spider.crawl("/error"));
        phaser.arriveAndAwaitAdvance();

        verify(mockHttpClient, times(1)).send(any(), any());
    }
}
