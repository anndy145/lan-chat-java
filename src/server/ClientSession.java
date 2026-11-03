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
            active = false;
            name = initialObserver ? "Anonymous" : cleanName(requestedName);
            if (initialObserver) {
                server.add(this);
            } else if (!server.join(this, name)) {
                return;
            }

            String line;
            while ((line = input.readLine()) != null) {
                if ("/quit".equalsIgnoreCase(line.trim())) {
                    break;
                }
                if ("@leave".equals(line)) {
                    server.setActive(this, false);
                } else if ("@join".equals(line)) {
                    server.join(this, name);
                } else if (line.startsWith("@join:") && !isActive()) {
                    try {
                        String requested = new String(Base64.getDecoder().decode(line.substring(6)), StandardCharsets.UTF_8);
                        server.join(this, cleanName(requested));
                    } catch (IllegalArgumentException exception) {
                        // 忽略格式錯誤的加入要求。
                    }
                } else if (line.startsWith("@private:") && isActive()) {
                    String[] parts = line.substring(9).split(":", 2);
                    if (parts.length == 2) {
                        try {
                            String target = new String(Base64.getDecoder().decode(parts[0]), StandardCharsets.UTF_8);
                            String text = new String(Base64.getDecoder().decode(parts[1]), StandardCharsets.UTF_8);
                            if (!text.isBlank() && !text.contains("\n") && !text.contains("\r")) {
                                server.sendPrivate(this, target, text);
                            }
                        } catch (IllegalArgumentException exception) {
                            send("[Private] Invalid message.");
                        }
                    }
                } else if (line.startsWith("@group-create:") && isActive()) {
                    String[] parts = line.substring(14).split(":", 2);
                    try {
                        if (parts.length == 2 && !parts[1].isEmpty()) {
                            String groupName = new String(Base64.getDecoder().decode(parts[0]), StandardCharsets.UTF_8).strip();
                            String[] members = parts[1].split(",");
                            for (int i = 0; i < members.length; i++) {
                                members[i] = new String(Base64.getDecoder().decode(members[i]), StandardCharsets.UTF_8);
                            }
                            server.createGroup(this, groupName, members);
                        }
                    } catch (IllegalArgumentException exception) {
                        send("@notice:SW52YWxpZCBncm91cCByZXF1ZXN0Lg==");
                    }
                } else if (line.startsWith("@group-send:") && isActive()) {
                    String[] parts = line.substring(12).split(":", 2);
                    try {
                        if (parts.length == 2) {
                            String text = new String(Base64.getDecoder().decode(parts[1]), StandardCharsets.UTF_8);
                            if (!text.isBlank() && !text.contains("\n") && !text.contains("\r")) {
                                server.sendGroup(this, parts[0], text);
                            }
                        }
                    } catch (IllegalArgumentException exception) {
                        send("@notice:SW52YWxpZCBncm91cCBtZXNzYWdlLg==");
                    }
                } else if (isActive() && !line.isBlank()) {
                    server.sendPublic(this, line);
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

    void setName(String name) {
        this.name = name;
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

