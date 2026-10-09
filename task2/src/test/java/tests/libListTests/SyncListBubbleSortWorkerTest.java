package tests.libListTests;

import app.libList.SyncListBubbleSortWorker;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SyncListBubbleSortWorkerTest {

    @Test
    void testWorkerSortsSynchronizedList() throws InterruptedException {
        List<String> list = Collections.synchronizedList(new ArrayList<>());
        list.add("C");
        list.add("A");
        list.add("B");

        AtomicLong stepCounter = new AtomicLong(0);
        Thread workerThread = new Thread(new SyncListBubbleSortWorker(list, 1, 1, stepCounter));
        workerThread.setDaemon(true);
        workerThread.start();

        Thread.sleep(200);

        workerThread.interrupt();
        workerThread.join(500);

        assertEquals(3, list.size());
        assertEquals("A", list.get(0));
        assertEquals("B", list.get(1));
        assertEquals("C", list.get(2));
    }

    @Test
    void testEmptyAndSingleElementListDoesNotCrash() throws InterruptedException {
        List<String> list = Collections.synchronizedList(new ArrayList<>());
        AtomicLong stepCounter = new AtomicLong(0);

        Thread workerThread = new Thread(new SyncListBubbleSortWorker(list, 1, 1, stepCounter));

        assertDoesNotThrow(() -> {
            workerThread.setDaemon(true);
            workerThread.start();
            Thread.sleep(50);
            workerThread.interrupt();
            workerThread.join(500);
        });

        list.add("Одиночный элемент");
        Thread workerThread2 = new Thread(new SyncListBubbleSortWorker(list, 1, 1, stepCounter));
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
        List<String> list = Collections.synchronizedList(new ArrayList<>());
        String[] data = {"Яблоко", "Апельсин", "Груша", "Манго", "Банан"};
        for (String item : data) {
            list.add(item);
        }

        AtomicLong stepCounter = new AtomicLong(0);

        Thread worker1 = new Thread(new SyncListBubbleSortWorker(list, 1, 1, stepCounter));
        Thread worker2 = new Thread(new SyncListBubbleSortWorker(list, 1, 1, stepCounter));
        Thread worker3 = new Thread(new SyncListBubbleSortWorker(list, 1, 1, stepCounter));

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

        assertEquals(5, list.size());
        assertEquals("Апельсин", list.get(0));
        assertEquals("Банан", list.get(1));
        assertEquals("Груша", list.get(2));
        assertEquals("Манго", list.get(3));
        assertEquals("Яблоко", list.get(4));
    }
}