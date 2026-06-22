package com.senseei.launcher.backup.adapter;

import com.senseei.launcher.backup.port.RconClient;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Source RCON over TCP — the binary protocol the old rcon.py spoke: little-endian
 * length-prefixed packets, authenticate then run one command. Localhost only.
 */
public final class SourceRconClient implements RconClient {

    private static final int TYPE_AUTH = 3;
    private static final int TYPE_EXEC = 2;
    private static final int TIMEOUT_MS = 8000;

    @Override
    public String execute(int port, String password, String command) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), TIMEOUT_MS);
            socket.setSoTimeout(TIMEOUT_MS);
            OutputStream out = socket.getOutputStream();
            DataInputStream in = new DataInputStream(socket.getInputStream());

            send(out, 1, TYPE_AUTH, password);
            if (receive(in).id() == -1) {
                throw new RuntimeException("RCON auth failed");
            }
            send(out, 2, TYPE_EXEC, command);
            return receive(in).body().strip();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void send(OutputStream out, int id, int type, String body) throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buf = ByteBuffer.allocate(14 + payload.length).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(10 + payload.length);   // length covers id + type + body + two nulls
        buf.putInt(id);
        buf.putInt(type);
        buf.put(payload).put((byte) 0).put((byte) 0);
        out.write(buf.array());
        out.flush();
    }

    private static Packet receive(DataInputStream in) throws IOException {
        int len = Integer.reverseBytes(in.readInt());
        byte[] data = new byte[len];
        in.readFully(data);
        int id = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).getInt();
        String body = new String(data, 8, len - 10, StandardCharsets.UTF_8);
        return new Packet(id, body);
    }

    private record Packet(int id, String body) {
    }
}
