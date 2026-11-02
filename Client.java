import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * TCP LAN chat client. Run: java Client <server-ip> <port> <name>
 */
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
             BufferedReader serverInput = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter serverOutput = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
             BufferedReader keyboard = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            serverOutput.println(name);
            System.out.println("Connected. Type a message and press Enter. Type /quit to leave.");

            Thread receiver = new Thread(() -> receiveMessages(serverInput), "server-receiver");
            receiver.setDaemon(true);
            receiver.start();

            String line;
            while ((line = keyboard.readLine()) != null) {
                serverOutput.println(line);
                if ("/quit".equalsIgnoreCase(line.trim())) {
                    break;
                }
            }
        } catch (IOException exception) {
            System.err.println("Could not connect to " + host + ":" + port + ". " + exception.getMessage());
        }
    }

    private static void receiveMessages(BufferedReader serverInput) {
        try {
            String message;
            while ((message = serverInput.readLine()) != null) {
                System.out.println(message);
            }
            System.out.println("Server disconnected.");
        } catch (IOException exception) {
            System.out.println("Connection closed.");
        }
    }
}
