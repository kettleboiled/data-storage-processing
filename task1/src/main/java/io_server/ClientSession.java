package io_server;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

public class ClientSession {
    private static final int MAX_NAME_LENGTH = 1024;

    private final ByteArrayOutputStream readStream = new ByteArrayOutputStream(64);
    private String clientName;
    private ByteBuffer writeBuffer;

    public boolean readName(SocketChannel channel, ByteBuffer sharedBuffer) throws IOException {
        int bytesRead;

        while ((bytesRead = channel.read(sharedBuffer)) > 0) {
            sharedBuffer.flip();

            while (sharedBuffer.hasRemaining()) {
                byte b = sharedBuffer.get();

                if (b == 0x00) {
                    this.clientName = readStream.toString(StandardCharsets.US_ASCII);
                    if (this.clientName.isEmpty()) {
                        throw new IOException("Получено пустое имя клиента");
                    }
                    return true;
                }

                if (readStream.size() >= MAX_NAME_LENGTH) {
                    throw new IOException("Превышена максимальная длина имени (" + MAX_NAME_LENGTH + " байт)");
                }

                readStream.write(b);
            }
            sharedBuffer.clear();
        }

        if (bytesRead == -1) {
            throw new EOFException("Клиент закрыл соединение до завершения передачи имени");
        }
        return false;
    }

    public void prepareWrite(byte[] pemData) {
        this.writeBuffer = ByteBuffer.wrap(pemData);
    }


    public boolean writeResponse(SocketChannel channel) throws IOException {
        if (writeBuffer == null) {
            throw new IllegalStateException("Буфер записи не инициализирован");
        }

        channel.write(writeBuffer);
        return !writeBuffer.hasRemaining();
    }

    public String getClientName() {
        return clientName;
    }
}