package br.a320sim.net;

import java.io.Closeable;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/** Roda no PC do instrutor: aceita um aluno por vez e troca mensagens com ele. */
public final class SimServer implements Closeable {

    public static final int DEFAULT_PORT = 7320;

    private final ServerSocket serverSocket;
    private final AtomicReference<Map<String, Object>> latestControls = new AtomicReference<>();
    private volatile Connection student;
    private volatile String studentName = "";

    public SimServer(int port) throws IOException {
        serverSocket = new ServerSocket(port);
        Thread acceptor = new Thread(this::acceptLoop, "net-accept");
        acceptor.setDaemon(true);
        acceptor.start();
    }

    private void acceptLoop() {
        while (!serverSocket.isClosed()) {
            try {
                Socket socket = serverSocket.accept();
                if (hasStudent()) {
                    socket.close();
                    continue;
                }
                studentName = "";
                student = new Connection(socket, this::onMessage);
            } catch (IOException e) {
                return;
            }
        }
    }

    private void onMessage(Message message) {
        switch (message) {
            case Message.Hello hello -> studentName = hello.name();
            case Message.ControlUpdate update -> latestControls.set(update.values());
            case Message.BusSnapshot ignored -> {
            }
        }
    }

    /** Os comandos mais recentes do aluno, se chegou algo novo desde a última consulta. */
    public Optional<Map<String, Object>> pollControls() {
        return Optional.ofNullable(latestControls.getAndSet(null));
    }

    public void send(Message message) {
        Connection connection = student;
        if (connection != null) {
            connection.send(message);
        }
    }

    public boolean hasStudent() {
        Connection connection = student;
        return connection != null && connection.isOpen();
    }

    public String studentDescription() {
        Connection connection = student;
        return connection == null ? "" : studentName + " (" + connection.remoteAddress() + ")";
    }

    public int port() {
        return serverSocket.getLocalPort();
    }

    /** Endereços IPv4 desta máquina que o aluno pode usar para conectar (rede local ou VPN). */
    public static List<String> localAddresses() {
        List<String> addresses = new ArrayList<>();
        try {
            for (NetworkInterface nic : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!nic.isUp() || nic.isLoopback()) {
                    continue;
                }
                for (InetAddress address : Collections.list(nic.getInetAddresses())) {
                    if (address instanceof Inet4Address) {
                        addresses.add(address.getHostAddress());
                    }
                }
            }
        } catch (SocketException e) {
            // sem rede: lista vazia
        }
        return addresses;
    }

    @Override
    public void close() {
        try {
            serverSocket.close();
        } catch (IOException ignored) {
            // já estava fechado
        }
        Connection connection = student;
        if (connection != null) {
            connection.close();
        }
    }
}
