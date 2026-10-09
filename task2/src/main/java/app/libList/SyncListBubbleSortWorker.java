package app.libList;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class SyncListBubbleSortWorker implements Runnable {
    private final List<String> list;
    private final long stepDelayMs;
    private final long innerStepDelayMs;
    private final AtomicLong stepCounter;

    public SyncListBubbleSortWorker(List<String> list,
                                    long stepDelayMs,
                                    long innerStepDelayMs,
                                    AtomicLong stepCounter) {
        this.list = list;
        this.stepDelayMs = stepDelayMs;
        this.innerStepDelayMs = innerStepDelayMs;
        this.stepCounter = stepCounter;
    }

    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                int size = list.size();
                for (int i = 0; i < size - 1; i++) {
                    synchronized (list) {
                        if (i < list.size() - 1) {
                            String a = list.get(i);
                            String b = list.get(i + 1);

                            Thread.sleep(innerStepDelayMs);

                            stepCounter.incrementAndGet();

                            if (a != null && b != null && a.compareTo(b) > 0) {
                                list.set(i, b);
                                list.set(i + 1, a);
                            }
                        }
                    }
                    Thread.sleep(stepDelayMs);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}