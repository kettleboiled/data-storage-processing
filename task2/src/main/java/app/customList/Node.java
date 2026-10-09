package app.customList;

import java.util.concurrent.locks.ReentrantLock;

public class Node {
    public final String value;
    public Node next;
    public final ReentrantLock locker;

    public Node(String value) {
        this.value = value;
        this.locker = new ReentrantLock();
    }
}
