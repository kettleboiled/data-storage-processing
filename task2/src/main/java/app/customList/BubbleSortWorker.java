package app.customList;

import java.util.concurrent.atomic.AtomicLong;

public class BubbleSortWorker implements Runnable {
    private final ConcurrentList list;
    private final long stepDelayMs;
    private final long innerStepDelayMs;
    private final AtomicLong stepCounter;

    public BubbleSortWorker(ConcurrentList list,
                            long stepDelayMs,
                            long innerStepDelayMs,
                            AtomicLong stepCounter
    ) {
        this.list = list;
        this.stepDelayMs = stepDelayMs;
        this.innerStepDelayMs = innerStepDelayMs;
        this.stepCounter = stepCounter;
    }

    @Override
    public void run() {
        Node prev = null;
        Node curr = null;
        Node next = null;
        try {
            while (!Thread.currentThread().isInterrupted()) {
                prev = list.head;
                prev.locker.lock();

                curr = prev.next;
                if (curr == null) {
                    prev.locker.unlock();
                    Thread.sleep(stepDelayMs);
                    continue;
                }

                curr.locker.lock();

                while (true) {
                    next = curr.next;
                    if (next == null) {
                        curr.locker.unlock();
                        prev.locker.unlock();
                        break;
                    }

                    next.locker.lock();

                    Thread.sleep(innerStepDelayMs);
                    stepCounter.incrementAndGet();

                    if (next.value != null && curr.value != null && curr.value.compareTo(next.value) > 0) {
                        prev.next = next;
                        curr.next = next.next;
                        next.next = curr;

                        Node oldPrev = prev;
                        prev = next;

                        oldPrev.locker.unlock();
                    } else {
                        Node oldPrev = prev;
                        prev = curr;
                        curr = next;

                        oldPrev.locker.unlock();
                    }

                    Thread.sleep(stepDelayMs);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (prev != null && prev.locker.isHeldByCurrentThread()) prev.locker.unlock();
            if (curr != null && curr.locker.isHeldByCurrentThread()) curr.locker.unlock();
            if (next != null && next.locker.isHeldByCurrentThread()) next.locker.unlock();
        }
    }
}