package tests.io_server;

import io_server.ClientSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.EOFException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

public class ClientSessionTest {

    private ServerSocketChannel serverChannel;
    private SocketChannel clientSide;
    private SocketChannel serverSide;
    private final ByteBuffer sharedBuffer = ByteBuffer.allocateDirect(1024);

    @BeforeEach
    void setUpChannelPair() throws IOException {
        serverChannel = ServerSocketChannel.open();
        serverChannel.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));

        clientSide = SocketChannel.open(serverChannel.getLocalAddress());
        serverSide = serverChannel.accept();
        serverSide.configureBlocking(false);
    }

    @AfterEach
    void closeChannels() throws IOException {
        if (clientSide != null) clientSide.close();
        if (serverSide != null) serverSide.close();
        if (serverChannel != null) serverChannel.close();
    }

    @Test
    void readsFragmentedNameAcrossMultipleCalls() throws Exception {
        ClientSession session = new ClientSession();
        clientSide.write(ByteBuffer.wrap("ali".getBytes(StandardCharsets.US_ASCII)));
        boolean completedFirst = false;
        for (int i = 0; i < 50; i++) {
            if (session.readName(serverSide, sharedBuffer)) {
                completedFirst = true;
                break;
            }
            if (session.getClientName() != null || sharedBuffer.position() > 0) {
                break;
            }
            Thread.sleep(10);
        }
        assertFalse(completedFirst, "Имя еще не завершено нулевым байтом, метод должен вернуть false");
        assertNull(session.getClientName());

        clientSide.write(ByteBuffer.wrap(new byte[]{'c', 'e', 0x00}));

        boolean completedSecond = false;
        for (int i = 0; i < 50; i++) {
            if (session.readName(serverSide, sharedBuffer)) {
                completedSecond = true;
                break;
            }
            Thread.sleep(10);
        }

        assertTrue(completedSecond, "После получения 0x00 метод должен вернуть true");
        assertEquals("alice", session.getClientName());
    }

    @Test
    void throwsIOExceptionOnEmptyName() throws IOException {
        ClientSession session = new ClientSession();

        clientSide.write(ByteBuffer.wrap(new byte[]{0x00}));

        IOException ex = assertThrows(IOException.class, () ->
                session.readName(serverSide, sharedBuffer)
        );
        assertTrue(ex.getMessage().contains("пустое имя"));
    }

    @Test
    void throwsIOExceptionWhenNameExceedsMaxLength() throws IOException {
        ClientSession session = new ClientSession();

        byte[] oversized = new byte[1025];
        for (int i = 0; i < oversized.length; i++) {
            oversized[i] = 'a';
        }
        clientSide.write(ByteBuffer.wrap(oversized));

        IOException ex = assertThrows(IOException.class, () ->
                session.readName(serverSide, sharedBuffer)
        );
        assertTrue(ex.getMessage().contains("Превышена максимальная длина имени"));
    }

    @Test
    void throwsEOFExceptionWhenClientDisconnectsBeforeNullByte() throws IOException {
        ClientSession session = new ClientSession();

        clientSide.write(ByteBuffer.wrap("incomplete".getBytes(StandardCharsets.US_ASCII)));
        clientSide.close(); // клиент закрывает соединение без 0x00

        assertThrows(EOFException.class, () ->
                session.readName(serverSide, sharedBuffer)
        );
    }

    @Test
    void writesFullResponseAndRejectsUninitializedBuffer() throws IOException {
        ClientSession session = new ClientSession();

        assertThrows(IllegalStateException.class, () ->
                session.writeResponse(serverSide)
        );

        byte[] payload = "-----BEGIN CERTIFICATE-----\nTEST\n-----END CERTIFICATE-----"
                .getBytes(StandardCharsets.US_ASCII);
        session.prepareWrite(payload);

        assertTrue(session.writeResponse(serverSide));

        ByteBuffer received = ByteBuffer.allocate(payload.length);
        while (received.hasRemaining()) {
            if (clientSide.read(received) == -1) break;
        }
        assertArrayEquals(payload, received.array());
    }
}