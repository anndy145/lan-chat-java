package server;

import common.ChatProtocol;

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
    private String userId;
    private volatile boolean active = true;

    ClientSession(Socket socket, Server server) {
        this.socket = socket;
        this.server = server;
    }

    @Override
    public void run() {
        boolean requestedQuit = false;
        try (Socket ignored = socket;
             BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {
            output = writer;
            String requestedName = input.readLine();
            if (requestedName == null) {
                return;
            }
            boolean initialObserver = ChatProtocol.OBSERVE.equals(requestedName);
            if (!initialObserver && !requestedName.startsWith("@hello:v2:")) {
                send(ChatProtocol.ERROR_VERSION);
                return;
            }
            active = false;
            try {
                name = initialObserver ? "Anonymous" : cleanName(new String(
                        Base64.getDecoder().decode(requestedName.substring(10)), StandardCharsets.UTF_8));
            } catch (IllegalArgumentException exception) {
                send(ChatProtocol.ERROR_VERSION);
                return;
            }
            if (initialObserver) {
                server.add(this);
            } else if (!server.join(this, name)) {
                return;
            }

            String line;
            while ((line = input.readLine()) != null) {
                if ("/quit".equalsIgnoreCase(line.trim())) {
                    requestedQuit = true;
                    break;
                }
                if (ChatProtocol.LEAVE.equals(line)) {
                    server.setActive(this, false);
                } else if ("@join".equals(line)) {
                    server.join(this, name);
                } else if (line.startsWith(ChatProtocol.JOIN) && !isActive()) {
                    try {
                        String requested = new String(Base64.getDecoder().decode(line.substring(ChatProtocol.JOIN.length())), StandardCharsets.UTF_8);
                        server.join(this, cleanName(requested));
                    } catch (IllegalArgumentException exception) {
                        // 忽略格式錯誤的加入要求。
                    }
                } else if (line.startsWith(ChatProtocol.SAY) && isActive()) {
                    try {
                        String text = new String(Base64.getDecoder().decode(line.substring(ChatProtocol.SAY.length())), StandardCharsets.UTF_8);
                        if (!text.isBlank() && !text.contains("\n") && !text.contains("\r")) {
                            server.sendPublic(this, text);
                        }
                    } catch (IllegalArgumentException exception) {
                        send("@notice:SW52YWxpZCBtZXNzYWdlLg==");
                    }
                } else if (line.startsWith(ChatProtocol.PRIVATE) && isActive()) {
                    String[] parts = line.substring(ChatProtocol.PRIVATE.length()).split(":", 2);
                    if (parts.length == 2) {
                        try {
                            String target = parts[0];
                            String text = ChatProtocol.decode(parts[1]);
                            if (!text.isBlank() && !text.contains("\n") && !text.contains("\r")) {
                                server.sendPrivate(this, target, text);
                            }
                        } catch (IllegalArgumentException exception) {
                            send("[Private] Invalid message.");
                        }
                    }
                } else if (line.startsWith(ChatProtocol.GROUP_CREATE) && isActive()) {
                    String[] parts = line.substring(ChatProtocol.GROUP_CREATE.length()).split(":", 2);
                    try {
                        if (parts.length == 2 && !parts[1].isEmpty()) {
                            String groupName = new String(Base64.getDecoder().decode(parts[0]), StandardCharsets.UTF_8).strip();
                            String[] members = parts[1].split(",");
                            server.createGroup(this, groupName, members);
                        }
                    } catch (IllegalArgumentException exception) {
                        send("@notice:SW52YWxpZCBncm91cCByZXF1ZXN0Lg==");
                    }
                } else if (line.startsWith(ChatProtocol.GROUP_ADD) && isActive()) {
                    String[] parts = line.substring(ChatProtocol.GROUP_ADD.length()).split(":", 2);
                    if (parts.length == 2 && !parts[1].isEmpty()) {
                        server.addGroupMembers(this, parts[0], parts[1].split(",", -1));
                    } else {
                        send("@notice:SW52YWxpZCBncm91cCByZXF1ZXN0Lg==");
                    }
                } else if (line.startsWith(ChatProtocol.GROUP_SEND) && isActive()) {
                    String[] parts = line.substring(ChatProtocol.GROUP_SEND.length()).split(":", 2);
                    try {
                        if (parts.length == 2) {
                            String text = ChatProtocol.decode(parts[1]);
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
            // 關閉連線時由 Server 統一記錄狀態，避免重複訊息。
        } finally {
            if (name != null) {
                server.remove(this, requestedQuit);
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

    String getUserId() {
        return userId;
    }

    void setUserId(String userId) {
        this.userId = userId;
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

