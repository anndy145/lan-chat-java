package client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class Client {
    public static void main(String[] args) {
        if (args.length != 3) {
            System.err.println("Usage: java Client <server-ip> <port> <name|--observe>");
            System.exit(1);
        }

        int port;
        try {
            port = Integer.parseInt(args[1]);
            if (port < 1 || port > 65535) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException exception) {
            System.err.println("Port must be an integer from 1 to 65535.");
            return;
        }

        String name = args[2].strip();
        if (!name.equals("--observe") && (name.isEmpty() || name.codePointCount(0, name.length()) > 32)) {
            System.err.println("Name must contain 1 to 32 characters.");
            return;
        }
        connect(args[0], port, name);
    }

    private enum Mode { OBSERVING, JOINING, ACTIVE }

    private static class ChatState {
        Mode mode = Mode.OBSERVING;
        String ownId;
        Map<String, String> onlineUsers = new HashMap<>();
        boolean usersReceived;
    }

    private static void connect(String host, int port, String name) {
        try (Socket socket = new Socket(host, port);
                BufferedReader serverInput = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                PrintWriter serverOutput = new PrintWriter(
                        new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {
            BufferedReader keyboard = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
            ChatState state = new ChatState();
            serverOutput.println("@observe:v2");
            if (!name.equals("--observe")) {
                state.mode = Mode.JOINING;
                serverOutput.println("@join:" + encode(name));
            } else {
                System.out.println("Observing online users. Type /join <name> to join or /quit to exit.");
            }

            AtomicBoolean leaving = new AtomicBoolean(false);
            Thread sender = new Thread(() -> sendMessages(keyboard, serverOutput, socket, leaving, state), "keyboard-sender");
            sender.setDaemon(true);
            sender.start();
            receiveMessages(serverInput, leaving, state);
        } catch (IOException exception) {
            System.err.println("Could not connect to " + host + ":" + port + ". " + exception.getMessage());
        }
    }

    private static void sendMessages(BufferedReader keyboard, PrintWriter serverOutput,
            Socket socket, AtomicBoolean leaving, ChatState state) {
        try {
            String line;
            while ((line = keyboard.readLine()) != null) {
                if ("/quit".equalsIgnoreCase(line.trim())) {
                    leaving.set(true);
                    serverOutput.println(line);
                    break;
                }
                if (line.trim().equalsIgnoreCase("/help")) {
                    printHelp();
                    continue;
                }
                synchronized (state) {
                    if (line.trim().equalsIgnoreCase("/users")) {
                        if (!state.usersReceived) {
                            System.out.println("Online user list has not been received yet.");
                        } else {
                            String names = state.onlineUsers.keySet().stream().sorted()
                                    .collect(java.util.stream.Collectors.joining(", "));
                            System.out.println("線上使用者：" + (names.isEmpty() ? "(none)" : names));
                        }
                        continue;
                    }
                    if (line.equalsIgnoreCase("/leave")) {
                        if (state.mode != Mode.ACTIVE) {
                            System.out.println("You are not currently in the chat.");
                        } else {
                            state.mode = Mode.OBSERVING;
                            state.ownId = null;
                            serverOutput.println("@leave");
                            System.out.println("You left the chat. Still observing online users.");
                        }
                        continue;
                    }
                    if (line.equals("/join") || line.startsWith("/join ")) {
                        if (state.mode != Mode.OBSERVING) {
                            System.out.println("Already joined or joining the chat.");
                            continue;
                        }
                        String requested = line.substring(5).strip();
                        if (requested.isEmpty() || requested.codePointCount(0, requested.length()) > 32) {
                            System.out.println("Usage: /join <name> (1 to 32 characters)");
                            continue;
                        }
                        state.mode = Mode.JOINING;
                        serverOutput.println("@join:" + encode(requested));
                        continue;
                    }
                    if (state.mode != Mode.ACTIVE) {
                        System.out.println("You are observing. Type /join <name> before sending messages.");
                        continue;
                    }
                }
                if (line.equals("/msg") || line.startsWith("/msg ")) {
                    sendPrivateMessage(line, serverOutput, state);
                } else if (line.startsWith("/group ")) {
                    String[] parts = line.split(" ", 3);
                    if (parts.length < 3 || parts[2].isBlank()) {
                        System.out.println("Usage: /group <group-id> <message>");
                        continue;
                    }
                    if (parts[2].codePointCount(0, parts[2].length()) > 2000) {
                        System.out.println("Message must be at most 2000 characters.");
                        continue;
                    }
                    String encoded = Base64.getEncoder().encodeToString(parts[2].getBytes(StandardCharsets.UTF_8));
                    serverOutput.println("@group-send:" + parts[1] + ":" + encoded);
                } else {
                    if (line.codePointCount(0, line.length()) > 2000) {
                        System.out.println("Message must be at most 2000 characters.");
                        continue;
                    }
                    String encoded = Base64.getEncoder().encodeToString(line.getBytes(StandardCharsets.UTF_8));
                    serverOutput.println("@say:" + encoded);
                }
            }
        } catch (IOException exception) {
            System.err.println("Could not read keyboard input: " + exception.getMessage());
        } finally {
            leaving.set(true);
            try {
                socket.close();
            } catch (IOException exception) {
                System.err.println("Could not close connection: " + exception.getMessage());
            }
        }
    }

    private static void printHelp() {
        System.out.println("Commands:");
        System.out.println("  /help                    Show this help.");
        System.out.println("  /users                   Show online users.");
        System.out.println("  /join <name>             Join chat from observer mode.");
        System.out.println("  /leave                   Leave chat and continue observing.");
        System.out.println("  /msg <name> <message>    Send a private message (chat mode).");
        System.out.println("                           Quote names with spaces: /msg \"Andy Chen\" Hello there");
        System.out.println("  /group <id> <message>    Send to a group you joined (chat mode).");
        System.out.println("  /quit                    Disconnect and exit.");
        System.out.println("  Other text               Send to the lobby (chat mode).");
    }

    // 引號中的 \ 和 \" 分別代表反斜線和雙引號。
    private static void sendPrivateMessage(String line, PrintWriter output, ChatState state) {
        String args = line.substring(4).stripLeading();
        String name;
        int end;
        if (args.startsWith("\"")) {
            StringBuilder parsed = new StringBuilder();
            int index = 1;
            boolean closed = false;
            while (index < args.length()) {
                char ch = args.charAt(index++);
                if (ch == '"') { closed = true; break; }
                if (ch == '\\') {
                    if (index >= args.length() || (args.charAt(index) != '\\' && args.charAt(index) != '"')) {
                        System.out.println("Invalid escape in name. Use \\\\ or \\\".");
                        return;
                    }
                    ch = args.charAt(index++);
                }
                parsed.append(ch);
            }
            if (!closed || index >= args.length() || !Character.isWhitespace(args.charAt(index))) {
                System.out.println("Usage: /msg <name> <message> (use quotes for names with spaces)");
                return;
            }
            name = parsed.toString();
            end = index;
        } else {
            end = 0;
            while (end < args.length() && !Character.isWhitespace(args.charAt(end))) end++;
            name = args.substring(0, end);
        }
        String text = args.substring(end).stripLeading();
        if (name.isEmpty() || text.isBlank()) {
            System.out.println("Usage: /msg <name> <message> (use quotes for names with spaces)");
            return;
        }
        if (text.codePointCount(0, text.length()) > 2000) {
            System.out.println("Message must be at most 2000 characters.");
            return;
        }
        String id;
        synchronized (state) {
            id = state.onlineUsers.get(name);
            if (id == null) {
                System.out.println("User is not online.");
                return;
            }
            if (id.equals(state.ownId)) {
                System.out.println("You cannot message yourself.");
                return;
            }
        }
        output.println("@private:" + id + ":" + encode(text));
    }

    private static String encode(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    private static void receiveMessages(BufferedReader serverInput, AtomicBoolean leaving, ChatState state) {
        try {
            String message;
            while ((message = serverInput.readLine()) != null) {
                if (message.startsWith("@joined:")) {
                    synchronized (state) { state.mode = Mode.ACTIVE; state.ownId = message.substring(8); }
                    System.out.println("Joined chat. Type /leave to observe or /quit to exit.");
                    continue;
                } else if (message.equals("@joined") || message.equals("@error:version")) {
                    System.out.println("Incompatible server version. Update both server and client.");
                    return;
                } else if (message.equals("@error:name-length")) {
                    synchronized (state) { state.mode = Mode.OBSERVING; }
                    System.out.println("Name must contain 1 to 32 characters. Type /join <name> to try again.");
                } else if (message.equals("@error:message-length")) {
                    System.out.println("Message must be at most 2000 characters.");
                } else if (message.equals("@error:name")) {
                    synchronized (state) { state.mode = Mode.OBSERVING; }
                    System.out.println("Name is already in use. Type /join <name> to try again.");
                } else if (message.startsWith("@notice:")) {
                    try {
                        System.out.println(
                                new String(Base64.getDecoder().decode(message.substring(8)), StandardCharsets.UTF_8));
                    } catch (IllegalArgumentException exception) {
                        System.out.println("Invalid notification received.");
                    }
                } else if (message.startsWith("@group:")) {
                    String[] parts = message.substring(7).split(":", 3);
                    if (parts.length == 3) {
                        try {
                            String name = new String(Base64.getDecoder().decode(parts[1]), StandardCharsets.UTF_8);
                            String text = new String(Base64.getDecoder().decode(parts[2]), StandardCharsets.UTF_8);
                            System.out.println("[Group " + name + " / " + parts[0] + "] " + text);
                        } catch (IllegalArgumentException exception) {
                            System.out.println("Invalid group message received.");
                        }
                    }
                } else if (message.startsWith("@pm:")) {
                    String[] parts = message.substring(4).split(":", 3);
                    if (parts.length == 3) {
                        try {
                            String peer = new String(Base64.getDecoder().decode(parts[1]), StandardCharsets.UTF_8);
                            String text = new String(Base64.getDecoder().decode(parts[2]), StandardCharsets.UTF_8);
                            System.out.println("[Private with " + peer + "] " + text);
                        } catch (IllegalArgumentException exception) {
                            System.out.println("Invalid private message received.");
                        }
                    }
                } else if (message.startsWith("@users:")) {
                    String encodedNames = message.substring(7);
                    StringBuilder names = new StringBuilder();
                    Map<String, String> users = new HashMap<>();
                    if (!encodedNames.isEmpty()) {
                        for (String encoded : encodedNames.split(",")) {
                            if (names.length() > 0)
                                names.append(", ");
                            String[] user = encoded.split("=", 2);
                            if (user.length != 2) {
                                System.out.println("Incompatible server version. Update both server and client.");
                                return;
                            }
                            try {
                                UUID.fromString(user[0]);
                                String name = new String(Base64.getDecoder().decode(user[1]), StandardCharsets.UTF_8);
                                names.append(name);
                                users.put(name, user[0]);
                            } catch (IllegalArgumentException exception) {
                                System.out.println("Invalid online user list received.");
                                return;
                            }
                        }
                    }
                    synchronized (state) { state.onlineUsers = users; state.usersReceived = true; }
                    System.out.println("線上使用者：" + names);
                } else {
                    System.out.println(message);
                }
            }
        } catch (IOException exception) {
            // A socket close can interrupt readLine, including when the user leaves.
        }
        if (!leaving.get()) {
            System.out.println("Server disconnected. Client closing.");
        }
    }
}
