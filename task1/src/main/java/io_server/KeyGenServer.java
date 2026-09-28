package io_server;

import init.ServerConfig;
import workers.KeyCache;
import workers.WorkerPool;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class KeyGenServer {

    private final int port;
    private final KeyCache cacheManager;

    private final Queue<Runnable> selectorTasks = new ConcurrentLinkedQueue<>();
    private final ByteBuffer sharedReadBuffer = ByteBuffer.allocateDirect(1024);

    private volatile boolean running = true;
    private Selector selector;

    public KeyGenServer(int port, KeyCache cacheManager) {
        this.port = port;
        this.cacheManager = cacheManager;
    }

    public void start() throws IOException {
        try (Selector sel = Selector.open();
             ServerSocketChannel serverChannel = ServerSocketChannel.open()) {

            this.selector = sel;

            serverChannel.bind(new InetSocketAddress(port));
            serverChannel.configureBlocking(false);
            serverChannel.register(selector, SelectionKey.OP_ACCEPT);

            System.out.println("Сервер запущен на порту " + port);

            while (running) {
                selector.select();
                processSelectorTasks();

                Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
                while (keys.hasNext()) {
                    SelectionKey key = keys.next();
                    keys.remove();
                    dispatch(key, serverChannel);
                }
            }
        }
    }

    public void stop() {
        this.running = false;
        if (this.selector != null) {
            this.selector.wakeup();
        }
    }

    private void dispatch(SelectionKey key,
                          ServerSocketChannel serverChannel) {
        if (!key.isValid()) {
            return;
        }

        try {
            if (key.isAcceptable()) {
                handleAccept(serverChannel);
            } else if (key.isReadable()) {
                handleRead(key);
            } else if (key.isWritable()) {
                handleWrite(key);
            }
        } catch (CancelledKeyException | IOException e) {
            System.err.println("Разрыв соединения с клиентом: " + e.getMessage());
            closeClient(key);
        }
    }

    private void processSelectorTasks() {
        Runnable task;
        while ((task = selectorTasks.poll()) != null) {
            try {
                task.run();
            } catch (Exception e) {
                System.err.println("Ошибка выполнения задачи в потоке Selector: " + e.getMessage());
            }
        }
    }

    private void handleAccept(ServerSocketChannel serverChannel) throws IOException {
        SocketChannel clientChannel = serverChannel.accept();
        if (clientChannel == null) {
            return;
        }
        clientChannel.configureBlocking(false);

        SelectionKey key = clientChannel.register(selector, SelectionKey.OP_READ);
        key.attach(new ClientSession());
    }

    private void handleRead(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        ClientSession session = (ClientSession) key.attachment();

        sharedReadBuffer.clear();
        if (!session.readName(channel, sharedReadBuffer)) {
            return;
        }

        key.interestOps(0);
        String clientName = session.getClientName();
        System.out.println("Получен запрос для имени: " + clientName);

        cacheManager.getOrGenerate(clientName)
                .whenComplete((pemBytes, ex) ->
                        scheduleResponse(key, session, clientName, pemBytes, ex));
    }

    private void scheduleResponse(SelectionKey key,
                                  ClientSession session,
                                  String clientName,
                                  byte[] pemBytes,
                                  Throwable ex) {
        selectorTasks.add(() -> {
            if (!key.isValid()) {
                return;
            }
            if (ex != null) {
                System.err.println("Ошибка генерации для "
                        + clientName + ": " + ex.getMessage());
                closeClient(key);
            } else {
                session.prepareWrite(pemBytes);
                key.interestOps(SelectionKey.OP_WRITE);
            }
        });
        selector.wakeup();
    }

    private void handleWrite(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        ClientSession session = (ClientSession) key.attachment();

        if (session.writeResponse(channel)) {
            System.out.println("Ответ успешно отправлен клиенту: "
                    + session.getClientName());
            closeClient(key);
        }
    }

    private void closeClient(SelectionKey key) {
        try {
            key.cancel();
            key.channel().close();
        } catch (IOException ignored) {
        }
    }

    public static void main(String[] args) {
        if (args.length < 4) {
            System.err.println("Использование: java KeyGenServer <port> <threads> <server.key> <server.properties>");
            System.exit(1);
        }

        try {
            int port = Integer.parseInt(args[0]);
            int threads = Integer.parseInt(args[1]);
            String keyPath = args[2];
            String propsPath = args[3];

            ServerConfig config = new ServerConfig(keyPath, propsPath);
            try (WorkerPool workerPool = new WorkerPool(threads)) {
                KeyCache cacheManager = new KeyCache(config, workerPool);
                KeyGenServer server = new KeyGenServer(port, cacheManager);
                Runtime.getRuntime().addShutdownHook(new Thread(server::stop));

                server.start();
            }
        } catch (Exception e) {
            System.err.println("Критическая ошибка запуска сервера: " + e.getMessage());
            System.exit(1);
        }
    }
}