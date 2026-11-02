import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Enumeration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** TCP LAN chat server. Run: java Server [port] */
public class Server {
    private static final int DEFAULT_PORT = 5487;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final int port;
    private final Set<ClientSession> clients = ConcurrentHashMap.newKeySet();

    public Server(int port) {
        this.port = port;
    }

    public static void main(String[] args) {
        int port = parsePort(args);
        new Server(port).start();
    }

    public void start() {
        System.out.println("Chat server started on port " + port + ".");
        printLanAddresses();

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                Socket socket = serverSocket.accept();
                ClientSession session = new ClientSession(socket, this);
                Thread thread = new Thread(session, "client-" + socket.getRemoteSocketAddress());
                thread.start();
            }
        } catch (IOException exception) {
            System.err.println("Server error: " + exception.getMessage());
        }
    }

    public void add(ClientSession session) {
        clients.add(session);
    }

    public void remove(ClientSession session) {
        clients.remove(session);
    }

    public void broadcast(String message) {
        for (ClientSession client : clients) {
            client.send(message);
        }
        System.out.println(message);
    }

    public String timestamped(String message) {
        return "[" + LocalTime.now().format(TIME_FORMAT) + "] " + message;
    }

    private static int parsePort(String[] args) {
        if (args.length > 1) {
            printUsageAndExit();
        }
        if (args.length == 0) {
            return DEFAULT_PORT;
        }
        try {
            int port = Integer.parseInt(args[0]);
            if (port < 1 || port > 65535) {
                throw new NumberFormatException();
            }
            return port;
        } catch (NumberFormatException exception) {
            System.err.println("Port must be an integer from 1 to 65535.");
            System.exit(1);
            return DEFAULT_PORT;
        }
    }

    private static void printUsageAndExit() {
        System.err.println("Usage: java Server [port]");
        System.exit(1);
    }

    private static void printLanAddresses() {
        System.out.println("Clients can connect using one of these IPv4 addresses:");
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (!networkInterface.isUp() || networkInterface.isLoopback()) {
                    continue;
                }
                Enumeration<java.net.InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    java.net.InetAddress address = addresses.nextElement();
                    if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
                        System.out.println("  " + address.getHostAddress());
                    }
                }
            }
        } catch (SocketException exception) {
            System.out.println("  (Could not detect LAN IP address.)");
        }
    }
}

/** Owns one client connection. Kept in this file so beginners compile only two files. */
class ClientSession implements Runnable {
    private final Socket socket;
    private final Server server;
    private PrintWriter output;
    private String name;

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
            name = cleanName(input.readLine());
            server.add(this);
            server.broadcast(server.timestamped(name + " joined the chat."));

            String line;
            while ((line = input.readLine()) != null) {
                if ("/quit".equalsIgnoreCase(line.trim())) {
                    break;
                }
                if (!line.isBlank()) {
                    server.broadcast(server.timestamped(name + ": " + line));
                }
            }
        } catch (IOException exception) {
            System.out.println("Connection lost: " + socket.getRemoteSocketAddress());
        } finally {
            if (name != null) {
                server.remove(this);
                server.broadcast(server.timestamped(name + " left the chat."));
            }
        }
    }

    public synchronized void send(String message) {
        if (output != null) {
            output.println(message);
        }
    }

    private static String cleanName(String requestedName) {
        if (requestedName == null || requestedName.isBlank()) {
            return "Anonymous";
        }
        return requestedName.strip().replaceAll("[\\r\\n]", "");
    }
}
