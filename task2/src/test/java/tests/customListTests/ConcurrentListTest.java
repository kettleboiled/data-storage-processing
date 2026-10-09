package tests.customListTests;

import app.customList.ConcurrentList;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConcurrentListTest {

    @Test
    void testAddAndIterateSequential() {
        ConcurrentList list = new ConcurrentList();
        list.add("Первый");
        list.add("Второй");
        list.add("Третий");

        List<String> result = new ArrayList<>();
        for (String s : list) {
            result.add(s);
        }

        assertEquals(3, result.size());
        assertEquals("Третий", result.get(0));
        assertEquals("Второй", result.get(1));
        assertEquals("Первый", result.get(2));
    }

    @Test
    void testIteratorOnEmptyList() {
        ConcurrentList list = new ConcurrentList();
        int count = 0;
        for (String ignored : list) {
            count++;
        }
        assertEquals(0, count);
    }

    @Test
    void testConcurrentAdd() throws InterruptedException {
        ConcurrentList list = new ConcurrentList();
        int threadCount = 10;
        int elementsPerThread = 1000;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                for (int j = 0; j < elementsPerThread; j++) {
                    list.add("Элемент");
                }
                latch.countDown();
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        int totalCount = 0;
        for (String ignored : list) {
            totalCount++;
        }

        assertEquals(threadCount * elementsPerThread, totalCount);
    }
}