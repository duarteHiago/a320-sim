package br.a320sim.net;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * Um lado de uma conexão TCP: envia mensagens (uma por linha) e entrega as recebidas
 * a quem estiver ouvindo, numa thread própria.
 */
public final class Connection implements Closeable {

    private final Socket socket;
    private final BufferedWriter out;
    private final MessageCodec codec = new MessageCodec();
    private volatile boolean open = true;

    public Connection(Socket socket, Consumer<Message> onMessage) throws IOException {
        this.socket = socket;
        socket.setTcpNoDelay(true);
        out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        Thread reader = new Thread(() -> readLoop(in, onMessage), "net-reader");
        reader.setDaemon(true);
        reader.start();
    }

    private void readLoop(BufferedReader in, Consumer<Message> onMessage) {
        try {
            String line;
            while (open && (line = in.readLine()) != null) {
                onMessage.accept(codec.decode(line));
            }
        } catch (IOException | RuntimeException e) {
            // conexão caiu ou chegou lixo: encerra
        } finally {
            close();
        }
    }

    public synchronized void send(Message message) {
        if (!open) {
            return;
        }
        try {
            out.write(codec.encode(message));
            out.write('\n');
            out.flush();
        } catch (IOException e) {
            close();
        }
    }

    public boolean isOpen() {
        return open;
    }

    public String remoteAddress() {
        return socket.getInetAddress().getHostAddress();
    }

    @Override
    public void close() {
        open = false;
        try {
            socket.close();
        } catch (IOException ignored) {
            // já estava fechada
        }
    }
}
