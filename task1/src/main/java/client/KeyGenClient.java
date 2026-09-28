package client;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class KeyGenClient {

    private static final String CERT_BEGIN_MARKER = "-----BEGIN CERTIFICATE-----";

    public static void main(String[] args) {
        if (args.length < 3) {
            printUsage();
            System.exit(1);
        }

        String host = args[0];
        int port = Integer.parseInt(args[1]);
        String clientName = args[2];
        int delaySeconds = 0;
        boolean crash = false;

        for (int i = 3; i < args.length; i++) {
            switch (args[i]) {
                case "--delay":
                    if (i + 1 < args.length) {
                        delaySeconds = Integer.parseInt(args[++i]);
                    } else {
                        System.err.println("Ошибка: после флага --delay необходимо указать время в секундах.");
                        System.exit(1);
                    }
                    break;
                case "--crash":
                    crash = true;
                    break;
                default:
                    System.err.println("Неизвестный аргумент: " + args[i]);
                    printUsage();
                    System.exit(1);
            }
        }

        try {
            runClient(host, port, clientName, Path.of("."), delaySeconds, crash);
        } catch (Exception e) {
            System.err.println("Ошибка выполнения клиента: " + e.getMessage());
            System.exit(1);
        }
    }

    public static void runClient(String host,
                                 int port,
                                 String clientName,
                                 Path outputDir,
                                 int delaySeconds,
                                 boolean crash)
            throws IOException, InterruptedException {

        System.out.printf("Подключение к %s:%d для запроса имени '%s'...%n", host, port, clientName);

        try (Socket socket = new Socket(host, port)) {
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            byte[] nameBytes = clientName.getBytes(StandardCharsets.US_ASCII);
            out.write(nameBytes);
            out.write(0x00);
            out.flush();

            if (crash) {
                System.out.println("Режим --crash активирован. Аварийное закрытие соединения без чтения ответа.");
                socket.setSoLinger(true, 0);
                return;
            }

            if (delaySeconds > 0) {
                System.out.printf("Задержка перед чтением ответа: %d сек...%n", delaySeconds);
                Thread.sleep(delaySeconds * 1000L);
            }

            ByteArrayOutputStream responseBuffer = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                responseBuffer.write(buffer, 0, bytesRead);
            }

            String fullPemResponse = responseBuffer.toString(StandardCharsets.US_ASCII);
            if (fullPemResponse.isEmpty()) {
                throw new IOException("Сервер закрыл соединение, вернув пустой ответ.");
            }

            saveKeyAndCertificate(outputDir, clientName, fullPemResponse);
        }
    }

    private static void saveKeyAndCertificate(Path outputDir,
                                              String clientName,
                                              String fullPem)
            throws IOException {
        int certStartIndex = fullPem.indexOf(CERT_BEGIN_MARKER);
        if (certStartIndex == -1) {
            throw new IOException("В ответе сервера не найден заголовок сертификата: " + CERT_BEGIN_MARKER);
        }

        String privateKeyPem = fullPem.substring(0, certStartIndex).trim() + System.lineSeparator();
        String certificatePem = fullPem.substring(certStartIndex).trim() + System.lineSeparator();

        Path keyPath = outputDir.resolve(clientName + ".key");
        Path crtPath = outputDir.resolve(clientName + ".crt");

        Files.writeString(keyPath, privateKeyPem, StandardCharsets.US_ASCII);
        Files.writeString(crtPath, certificatePem, StandardCharsets.US_ASCII);

        System.out.println("Файлы успешно сохранены:");
        System.out.println("  Ключ:       " + keyPath.toAbsolutePath());
        System.out.println("  Сертификат: " + crtPath.toAbsolutePath());
    }

    private static void printUsage() {
        System.out.println("Использование: java KeyGenClient <host> <port> <clientName> [--delay <seconds>] [--crash]");
    }
}