package server;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Enumeration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

/** TCP LAN chat server. Run: java -cp out server.Server [port] */
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

    public synchronized void add(ClientSession session) {
        clients.add(session);
        broadcastUsers();
    }

    public synchronized void setActive(ClientSession session, boolean active) {
        if (clients.contains(session) && session.isActive() != active) {
            session.setActive(active);
            broadcastUsers();
            broadcast(timestamped(session.getName() + (active ? " joined the chat." : " left the chat.")));
        }
    }

    public synchronized void remove(ClientSession session) {
        if (clients.remove(session)) {
            broadcastUsers();
        }
    }

    // 線上名單是獨立的協定訊息；Base64 避免暱稱中的分隔符干擾解析。
    private void broadcastUsers() {
        String names = clients.stream()
                .filter(ClientSession::isActive)
                .map(ClientSession::getName)
                .sorted()
                .map(name -> Base64.getEncoder().encodeToString(name.getBytes(StandardCharsets.UTF_8)))
                .collect(Collectors.joining(","));
        String message = "@users:" + names;
        for (ClientSession client : clients) {
            client.send(message);
        }
    }

    public synchronized void broadcast(String message) {
        for (ClientSession client : clients) {
            if (client.isActive()) {
                client.send(message);
            }
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

