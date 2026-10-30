package br.a320sim.net;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/** Roda no PC do aluno: conecta no instrutor, manda os comandos e recebe o barramento. */
public final class SimClient implements Closeable {

    private static final int CONNECT_TIMEOUT_MS = 4000;

    private final AtomicReference<Map<String, Object>> latestSnapshot = new AtomicReference<>();
    private final Connection connection;
    private volatile long lastSnapshotAt;

    private SimClient(Socket socket) throws IOException {
        connection = new Connection(socket, this::onMessage);
    }

    public static SimClient connect(String host, int port, String name) throws IOException {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
        SimClient client = new SimClient(socket);
        client.connection.send(new Message.Hello(name));
        return client;
    }

    private void onMessage(Message message) {
        if (message instanceof Message.BusSnapshot snapshot) {
            latestSnapshot.set(snapshot.values());
            lastSnapshotAt = System.currentTimeMillis();
        }
    }

    public Optional<Map<String, Object>> pollSnapshot() {
        return Optional.ofNullable(latestSnapshot.getAndSet(null));
    }

    public void sendControls(Map<String, Object> values) {
        connection.send(new Message.ControlUpdate(values));
    }

    public boolean isOpen() {
        return connection.isOpen();
    }

    /** Há quanto tempo não chega nada do instrutor (ex.: ele está escolhendo cartuchos). */
    public long millisSinceLastSnapshot() {
        return lastSnapshotAt == 0 ? Long.MAX_VALUE : System.currentTimeMillis() - lastSnapshotAt;
    }

    @Override
    public void close() {
        connection.close();
    }
}
