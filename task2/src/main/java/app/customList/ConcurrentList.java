package app.customList;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ConcurrentList implements Iterable<String> {
    public final Node head;

    public ConcurrentList() {
        this.head = new Node(null);
    }

    public void add(String value) {
        Node newNode = new Node(value);

        head.locker.lock();
        try {
            newNode.next = head.next;
            head.next = newNode;
        } finally {
            head.locker.unlock();
        }

    }

    @Override
    public Iterator<String> iterator() {
        List<String> currentStateList = new ArrayList<>();
        Node current = head;

        current.locker.lock();
        try {
            Node nextNode = current.next;
            while (nextNode != null) {
                nextNode.locker.lock();
                try {
                    currentStateList.add(nextNode.value);
                } finally {
                    current.locker.unlock();
                    current = nextNode;
                    nextNode = current.next;
                }
            }
        } finally {
            current.locker.unlock();
        }

        return currentStateList.iterator();
    }
}
