package client;

import common.ChatProtocol;

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
        if (!name.equals("--observe") && (name.isEmpty() || name.codePointCount(0, name.length()) > ChatProtocol.MAX_NAME_LENGTH)) {
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
            serverOutput.println(ChatProtocol.OBSERVE);
            if (!name.equals("--observe")) {
                state.mode = Mode.JOINING;
                serverOutput.println(ChatProtocol.JOIN + encode(name));
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
                if (!handleInput(line, serverOutput, leaving, state)) break;
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

    // 指令與一般聊天文字分流；未知指令不會意外公開。
    private static boolean handleInput(String line, PrintWriter output, AtomicBoolean leaving, ChatState state) {
        if (!line.startsWith("/") || line.startsWith("//")) {
            synchronized (state) {
                if (state.mode != Mode.ACTIVE) {
                    System.out.println("You are observing. Type /join <name> before sending messages.");
                    return true;
                }
            }
            sendPublicMessage(line.startsWith("//") ? line.substring(1) : line, output);
            return true;
        }

        int end = 0;
        while (end < line.length() && !Character.isWhitespace(line.charAt(end))) end++;
        String command = line.substring(0, end).toLowerCase(java.util.Locale.ROOT);
        String arguments = line.substring(end).stripLeading();
        if (command.equals("/quit") && arguments.isEmpty()) {
            leaving.set(true);
            output.println("/quit");
            return false;
        }
        if (command.equals("/help") && arguments.isEmpty()) { printHelp(); return true; }
        if (command.equals("/users") && arguments.isEmpty()) { printUsers(state); return true; }
        if (command.equals("/join")) { joinChat(arguments, output, state); return true; }
        if (command.equals("/leave") && arguments.isEmpty()) { leaveChat(output, state); return true; }

        synchronized (state) {
            if (state.mode != Mode.ACTIVE) {
                System.out.println("You are observing. Type /join <name> before sending messages.");
                return true;
            }
        }
        if (command.equals("/msg")) sendPrivateMessage("/msg " + arguments, output, state);
        else if (command.equals("/group")) sendGroupMessage(arguments, output);
        else System.out.println("Unknown command or invalid arguments. Type /help for usage.");
        return true;
    }

    private static void printUsers(ChatState state) {
        synchronized (state) {
            if (!state.usersReceived) {
                System.out.println("Online user list has not been received yet.");
                return;
            }
            String names = state.onlineUsers.keySet().stream().sorted()
                    .collect(java.util.stream.Collectors.joining(", "));
            System.out.println("線上使用者：" + (names.isEmpty() ? "(none)" : names));
        }
    }

    private static void joinChat(String name, PrintWriter output, ChatState state) {
        synchronized (state) {
            if (state.mode != Mode.OBSERVING) {
                System.out.println("Already joined or joining the chat.");
            } else if (name.isBlank() || name.codePointCount(0, name.length()) > ChatProtocol.MAX_NAME_LENGTH) {
                System.out.println("Usage: /join <name> (1 to 32 characters)");
            } else {
                state.mode = Mode.JOINING;
                output.println(ChatProtocol.JOIN + encode(name.strip()));
            }
        }
    }

    private static void leaveChat(PrintWriter output, ChatState state) {
        synchronized (state) {
            if (state.mode != Mode.ACTIVE) {
                System.out.println("You are not currently in the chat.");
            } else {
                state.mode = Mode.OBSERVING;
                state.ownId = null;
                output.println(ChatProtocol.LEAVE);
                System.out.println("You left the chat. Still observing online users.");
            }
        }
    }

    private static void sendGroupMessage(String arguments, PrintWriter output) {
        String[] parts = arguments.split("\\s+", 2);
        if (parts.length < 2 || parts[1].isBlank()) {
            System.out.println("Usage: /group <group-id> <message>");
        } else if (parts[1].codePointCount(0, parts[1].length()) > ChatProtocol.MAX_MESSAGE_LENGTH) {
            System.out.println("Message must be at most 2000 characters.");
        } else {
            output.println(ChatProtocol.GROUP_SEND + parts[0] + ":" + encode(parts[1]));
        }
    }

    private static void sendPublicMessage(String message, PrintWriter output) {
        if (message.codePointCount(0, message.length()) > ChatProtocol.MAX_MESSAGE_LENGTH) {
            System.out.println("Message must be at most 2000 characters.");
        } else {
            output.println(ChatProtocol.SAY + encode(message));
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
        System.out.println("  Other text               Send to the lobby (chat mode; use // for a leading /).");
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
        if (text.codePointCount(0, text.length()) > ChatProtocol.MAX_MESSAGE_LENGTH) {
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
        output.println(ChatProtocol.PRIVATE + id + ":" + encode(text));
    }

    private static String encode(String text) {
        return ChatProtocol.encode(text);
    }

    private static void receiveMessages(BufferedReader serverInput, AtomicBoolean leaving, ChatState state) {
        try {
            String message;
            while ((message = serverInput.readLine()) != null) {
                if (message.startsWith(ChatProtocol.JOINED)) {
                    synchronized (state) { state.mode = Mode.ACTIVE; state.ownId = message.substring(8); }
                    System.out.println("Joined chat. Type /leave to observe or /quit to exit.");
                    continue;
                } else if (message.equals("@joined") || message.equals(ChatProtocol.ERROR_VERSION)) {
                    System.out.println("Incompatible server version. Update both server and client.");
                    return;
                } else if (message.equals(ChatProtocol.ERROR_NAME_LENGTH)) {
                    synchronized (state) { state.mode = Mode.OBSERVING; }
                    System.out.println("Name must contain 1 to 32 characters. Type /join <name> to try again.");
                } else if (message.equals(ChatProtocol.ERROR_MESSAGE_LENGTH)) {
                    System.out.println("Message must be at most 2000 characters.");
                } else if (message.equals(ChatProtocol.ERROR_NAME)) {
                    synchronized (state) { state.mode = Mode.OBSERVING; }
                    System.out.println("Name is already in use. Type /join <name> to try again.");
                } else if (message.startsWith(ChatProtocol.NOTICE)) {
                    try {
                        System.out.println(
                                new String(Base64.getDecoder().decode(message.substring(8)), StandardCharsets.UTF_8));
                    } catch (IllegalArgumentException exception) {
                        System.out.println("Invalid notification received.");
                    }
                } else if (message.startsWith(ChatProtocol.GROUP_MESSAGE)) {
                    String[] parts = message.substring(7).split(":", 3);
                    if (parts.length == 3) {
                        try {
                            String name = ChatProtocol.decode(parts[1]);
                            String text = ChatProtocol.decode(parts[2]);
                            System.out.println("[Group " + name + " / " + parts[0] + "] " + text);
                        } catch (IllegalArgumentException exception) {
                            System.out.println("Invalid group message received.");
                        }
                    }
                } else if (message.startsWith(ChatProtocol.PRIVATE_MESSAGE)) {
                    String[] parts = message.substring(4).split(":", 3);
                    if (parts.length == 3) {
                        try {
                            String peer = ChatProtocol.decode(parts[1]);
                            String text = ChatProtocol.decode(parts[2]);
                            System.out.println("[Private with " + peer + "] " + text);
                        } catch (IllegalArgumentException exception) {
                            System.out.println("Invalid private message received.");
                        }
                    }
                } else if (message.startsWith(ChatProtocol.USERS)) {
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
