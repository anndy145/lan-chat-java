package server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** 處理單一 Client 的連線。 */
class ClientSession implements Runnable {
    private final Socket socket;
    private final Server server;
    private PrintWriter output;
    private String name;
    private volatile boolean active = true;

    ClientSession(Socket socket, Server server) {
        this.socket = socket;
        this.server = server;
    }

    @Override
    public void run() {
        try (Socket ignored = socket;
             BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {
            output = writer;
            String requestedName = input.readLine();
            if (requestedName == null) {
                return;
            }
            boolean initialObserver = "@observe".equals(requestedName);
            active = !initialObserver;
            name = initialObserver ? "Anonymous" : cleanName(requestedName);
            server.add(this);
            if (active) {
                server.broadcast(server.timestamped(name + " joined the chat."));
            }

            String line;
            while ((line = input.readLine()) != null) {
                if ("/quit".equalsIgnoreCase(line.trim())) {
                    break;
                }
                if ("@leave".equals(line)) {
                    server.setActive(this, false);
                } else if ("@join".equals(line)) {
                    server.setActive(this, true);
                } else if (line.startsWith("@join:") && !isActive()) {
                    try {
                        String requested = new String(Base64.getDecoder().decode(line.substring(6)), StandardCharsets.UTF_8);
                        name = cleanName(requested);
                        server.setActive(this, true);
                    } catch (IllegalArgumentException exception) {
                        // 忽略格式錯誤的加入要求。
                    }
                } else if (isActive() && !line.isBlank()) {
                    server.broadcast(server.timestamped(name + ": " + line));
                }
            }
        } catch (IOException exception) {
            System.out.println("Connection lost: " + socket.getRemoteSocketAddress());
        } finally {
            if (name != null) {
                boolean wasActive = isActive();
                server.remove(this);
                if (wasActive) {
                    server.broadcast(server.timestamped(name + " left the chat."));
                }
            }
        }
    }

    public synchronized void send(String message) {
        if (output != null) {
            output.println(message);
        }
    }

    String getName() {
        return name;
    }

    boolean isActive() {
        return active;
    }

    void setActive(boolean active) {
        this.active = active;
    }

    private static String cleanName(String requestedName) {
        if (requestedName == null || requestedName.isBlank()) {
            return "Anonymous";
        }
        return requestedName.strip().replaceAll("[\\r\\n]", "");
    }
}

