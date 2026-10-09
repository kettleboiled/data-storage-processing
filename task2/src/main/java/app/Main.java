package app;

import app.customList.BubbleSortWorker;
import app.customList.ConcurrentList;
import app.libList.SyncListBubbleSortWorker;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class Main {

    public static void main(String[] args) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            System.out.println("1 - Интерактивный ввод и собственная реализация");
            System.out.println("2 - Сравнительный анализ");
            System.out.println("Выберите режим работы (1 или 2):");

            String mode = reader.readLine();

            if ("2".equals(mode != null ? mode.trim() : "")) {
                runBenchmarkMode();
            } else {
                runInteractiveMode(reader);
            }
        } catch (IOException e) {
            System.err.println("Ошибка ввода-вывода: " + e.getMessage());
        } catch (InterruptedException e) {
            System.err.println("Поток был прерван: " + e.getMessage());
            Thread.currentThread().interrupt();
        }
    }

    private static void runInteractiveMode(BufferedReader reader) throws IOException {
        ConcurrentList list = new ConcurrentList();

        int threadCount = 3;
        long stepDelayMs = 500;
        long innerStepDelayMs = 100;

        System.out.print("Введите количество потоков (по умолчанию 3): ");
        String input = reader.readLine();
        if (input != null && !input.trim().isEmpty()) {
            threadCount = Integer.parseInt(input.trim());
        }

        System.out.print("Введите задержку между шагами в мс (по умолчанию 500): ");
        input = reader.readLine();
        if (input != null && !input.trim().isEmpty()) {
            stepDelayMs = Long.parseLong(input.trim());
        }

        System.out.print("Введите задержку внутри шага в мс (по умолчанию 100): ");
        input = reader.readLine();
        if (input != null && !input.trim().isEmpty()) {
            innerStepDelayMs = Long.parseLong(input.trim());
        }

        System.out.println("Конфигурация применена. Запуск потоков...");

        AtomicLong dummyCounter = new AtomicLong(0);

        List<Thread> workers = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            Thread worker = new Thread(new BubbleSortWorker(list, stepDelayMs, innerStepDelayMs, dummyCounter));
            worker.setDaemon(true);
            worker.start();
            workers.add(worker);
        }

        System.out.println("Вводите строки (пустая строка для вывода списка): ");
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isEmpty()) {
                System.out.println("--- Текущее состояние списка ---");
                for (String item : list) {
                    System.out.println(item);
                }
                System.out.println("--------------------------------");
            } else {
                if (line.length() > 80) {
                    int length = line.length();
                    for (int i = 0; i < length; i += 80) {
                        String chunk = line.substring(i, Math.min(length, i + 80));
                        list.add(chunk);
                    }
                } else {
                    list.add(line);
                }
            }
        }
    }

    private static void runBenchmarkMode() throws InterruptedException {
        int threadCount = 5;
        long stepDelayMs = 10;
        long innerStepDelayMs = 50;
        int runTimeMs = 10000;

        System.out.println("\n--- Запуск сравнительного анализа на " + runTimeMs / 1000 + " секунд ---");

        List<String> initialData = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            initialData.add("Item_" + String.format("%03d", (int)(Math.random() * 1000)));
        }

        ConcurrentList customList = new ConcurrentList();
        List<String> syncList = Collections.synchronizedList(new ArrayList<>());

        for (String item : initialData) {
            customList.add(item);
            syncList.add(0, item);
        }

        AtomicLong customCounter = new AtomicLong(0);
        List<Thread> customWorkers = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            Thread t = new Thread(new BubbleSortWorker(customList, stepDelayMs, innerStepDelayMs, customCounter));
            t.start();
            customWorkers.add(t);
        }

        AtomicLong syncCounter = new AtomicLong(0);
        List<Thread> syncWorkers = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            Thread t = new Thread(new SyncListBubbleSortWorker(syncList, stepDelayMs, innerStepDelayMs, syncCounter));
            t.start();
            syncWorkers.add(t);
        }

        Thread.sleep(runTimeMs);

        for (Thread t : customWorkers) t.interrupt();
        for (Thread t : syncWorkers) t.interrupt();

        for (Thread t : customWorkers) t.join();
        for (Thread t : syncWorkers) t.join();

        System.out.println("\n--- Результаты ---");
        System.out.println("Собственная реализация (Fine-Grained Locking): " + customCounter.get() + " шагов.");
        System.out.println("Библиотечная реализация (Coarse-Grained Locking): " + syncCounter.get() + " шагов.");
    }
}