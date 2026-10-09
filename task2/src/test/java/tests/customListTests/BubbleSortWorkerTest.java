package tests.customListTests;

import app.customList.BubbleSortWorker;
import app.customList.ConcurrentList;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class BubbleSortWorkerTest {

    @Test
    void testWorkerSortsList() throws InterruptedException {
        ConcurrentList list = new ConcurrentList();
        list.add("C");
        list.add("A");
        list.add("B");

        AtomicLong dummyCounter = new AtomicLong(0);
        Thread workerThread = new Thread(new BubbleSortWorker(list, 1, 1, dummyCounter));
        workerThread.setDaemon(true);
        workerThread.start();

        Thread.sleep(200);

        workerThread.interrupt();
        workerThread.join(500);

        List<String> result = new ArrayList<>();
        for (String s : list) {
            result.add(s);
        }

        assertEquals(3, result.size());
        assertEquals("A", result.get(0));
        assertEquals("B", result.get(1));
        assertEquals("C", result.get(2));
    }

    @Test
    void testEmptyAndSingleElementListDoesNotCrash() throws InterruptedException {
        ConcurrentList list = new ConcurrentList();
        AtomicLong dummyCounter = new AtomicLong(0);

        Thread workerThread = new Thread(new BubbleSortWorker(list, 1, 1, dummyCounter));

        assertDoesNotThrow(() -> {
            workerThread.setDaemon(true);
            workerThread.start();
            Thread.sleep(50);
            workerThread.interrupt();
            workerThread.join(500);
        });

        list.add("dddddd");
        Thread workerThread2 = new Thread(new BubbleSortWorker(list, 1, 1, dummyCounter));
        assertDoesNotThrow(() -> {
            workerThread2.setDaemon(true);
            workerThread2.start();
            Thread.sleep(50);
            workerThread2.interrupt();
            workerThread2.join(500);
        });
    }

    @Test
    void testMultipleWorkersConcurrentSorting() throws InterruptedException {
        ConcurrentList list = new ConcurrentList();
        String[] data = {"Яблоко", "Апельсин", "Груша", "Манго", "Банан"};
        for (String item : data) {
            list.add(item);
        }

        AtomicLong dummyCounter = new AtomicLong(0);

        Thread worker1 = new Thread(new BubbleSortWorker(list, 1, 1, dummyCounter));
        Thread worker2 = new Thread(new BubbleSortWorker(list, 1, 1, dummyCounter));
        Thread worker3 = new Thread(new BubbleSortWorker(list, 1, 1, dummyCounter));

        worker1.setDaemon(true);
        worker2.setDaemon(true);
        worker3.setDaemon(true);

        worker1.start();
        worker2.start();
        worker3.start();

        Thread.sleep(500);

        worker1.interrupt();
        worker2.interrupt();
        worker3.interrupt();

        worker1.join(500);
        worker2.join(500);
        worker3.join(500);

        List<String> result = new ArrayList<>();
        for (String s : list) {
            result.add(s);
        }

        assertEquals(5, result.size());
        assertEquals("Апельсин", result.get(0));
        assertEquals("Банан", result.get(1));
        assertEquals("Груша", result.get(2));
        assertEquals("Манго", result.get(3));
        assertEquals("Яблоко", result.get(4));
    }
}