package client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;

public class Client {
    public static void main(String[] args) {
        if (args.length != 3) {
            System.err.println("Usage: java Client <server-ip> <port> <name>");
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

        connect(args[0], port, args[2]);
    }

    private static void connect(String host, int port, String name) {
        try (Socket socket = new Socket(host, port);
                BufferedReader serverInput = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                PrintWriter serverOutput = new PrintWriter(
                        new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {
            BufferedReader keyboard = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
            serverOutput.println("@hello:v2:" + Base64.getEncoder().encodeToString(name.getBytes(StandardCharsets.UTF_8)));
            System.out.println("Connected. Type a message and press Enter. Type /quit to leave.");

            AtomicBoolean leaving = new AtomicBoolean(false);
            Thread sender = new Thread(() -> sendMessages(keyboard, serverOutput, socket, leaving), "keyboard-sender");
            sender.setDaemon(true);
            sender.start();
            receiveMessages(serverInput, leaving);
        } catch (IOException exception) {
            System.err.println("Could not connect to " + host + ":" + port + ". " + exception.getMessage());
        }
    }

    private static void sendMessages(BufferedReader keyboard, PrintWriter serverOutput,
            Socket socket, AtomicBoolean leaving) {
        try {
            String line;
            while ((line = keyboard.readLine()) != null) {
                if ("/quit".equalsIgnoreCase(line.trim())) {
                    leaving.set(true);
                    serverOutput.println(line);
                    break;
                }
                if (line.startsWith("/group ")) {
                    String[] parts = line.split(" ", 3);
                    if (parts.length < 3 || parts[2].isBlank()) {
                        System.out.println("Usage: /group <group-id> <message>");
                        continue;
                    }
                    String encoded = Base64.getEncoder().encodeToString(parts[2].getBytes(StandardCharsets.UTF_8));
                    serverOutput.println("@group-send:" + parts[1] + ":" + encoded);
                } else {
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

    private static void receiveMessages(BufferedReader serverInput, AtomicBoolean leaving) {
        try {
            String message;
            while ((message = serverInput.readLine()) != null) {
                if (message.startsWith("@joined:")) {
                    continue;
                } else if (message.equals("@joined") || message.equals("@error:version")) {
                    System.out.println("Incompatible server version. Update both server and client.");
                    return;
                } else if (message.equals("@error:name")) {
                    System.out.println("Name is already in use.");
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
                    if (!encodedNames.isEmpty()) {
                        for (String encoded : encodedNames.split(",")) {
                            if (names.length() > 0)
                                names.append(", ");
                            String[] user = encoded.split("=", 2);
                            if (user.length != 2) {
                                System.out.println("Incompatible server version. Update both server and client.");
                                return;
                            }
                            names.append(new String(Base64.getDecoder().decode(user[1]), StandardCharsets.UTF_8));
                        }
                    }
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
