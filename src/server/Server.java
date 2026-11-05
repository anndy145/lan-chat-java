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
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.UUID;
import java.time.LocalDateTime;

/** TCP LAN chat server. Run: java -cp out server.Server [port] */
public class Server implements AutoCloseable {
    private static final int DEFAULT_PORT = 5487;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final int port;
    private final Set<ClientSession> clients = ConcurrentHashMap.newKeySet();
    private final Set<Socket> sockets = ConcurrentHashMap.newKeySet();
    private final Object lifecycleLock = new Object();
    private ServerSocket listener;
    private boolean closed;
    private final Map<String, ChatGroup> groups = new HashMap<>();

    private static class ChatGroup {
        final String name;
        final Set<ClientSession> members;

        ChatGroup(String name, Set<ClientSession> members) {
            this.name = name;
            this.members = members;
        }
    }

    public Server(int port) {
        this.port = port;
    }

    public static void main(String[] args) {
        int port = parsePort(args);
        try (Server server = new Server(port)) {
            server.start();
        }
    }

    public void start() {
        try {
            bind();
            System.out.println("Chat server started on port " + port + ".");
            printLanAddresses();
            serve();
        } catch (IOException exception) {
            System.err.println("Server error: " + exception.getMessage());
        } finally {
            close();
        }
    }

    // 先綁定連接埠，呼叫者確認成功後再啟動接受連線的執行緒。
    public void bind() throws IOException {
        synchronized (lifecycleLock) {
            if (closed || listener != null)
                throw new IllegalStateException("Server already started or closed");
            listener = new ServerSocket(port);
        }
    }

    public void serve() throws IOException {
        ServerSocket serverSocket;
        synchronized (lifecycleLock) {
            if (listener == null)
                throw new IllegalStateException("Server is not bound");
            serverSocket = listener;
        }
        try {
            while (true) {
                Socket socket = serverSocket.accept();
                synchronized (lifecycleLock) {
                    if (closed) {
                        socket.close();
                        break;
                    }
                    sockets.add(socket);
                    Thread thread = new Thread(() -> {
                        try {
                            new ClientSession(socket, this).run();
                        } finally {
                            sockets.remove(socket);
                        }
                    }, "client-" + socket.getRemoteSocketAddress());
                    thread.start();
                }
            }
        } catch (IOException exception) {
            if (!serverSocket.isClosed())
                throw exception;
        }
    }

    @Override
    public void close() {
        synchronized (lifecycleLock) {
            if (closed)
                return;
            closed = true;
            try {
                if (listener != null)
                    listener.close();
            } catch (IOException ignored) {
                // 已關閉或無法關閉時仍繼續釋放其他連線。
            }
            for (Socket socket : sockets) {
                try {
                    socket.close();
                } catch (IOException ignored) {
                    // 繼續關閉其餘連線。
                }
            }
        }
    }

    public static String lanAddresses() {
        StringBuilder result = new StringBuilder();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (!networkInterface.isUp() || networkInterface.isLoopback())
                    continue;
                Enumeration<java.net.InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    java.net.InetAddress address = addresses.nextElement();
                    if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
                        if (result.length() > 0)
                            result.append(", ");
                        result.append(address.getHostAddress());
                    }
                }
            }
        } catch (SocketException ignored) {
            return "unavailable";
        }
        return result.length() == 0 ? "unavailable" : result.toString();
    }

    public synchronized void add(ClientSession session) {
        clients.add(session);
        broadcastUsers();
    }

    public synchronized boolean join(ClientSession session, String name) {
        if (clients.contains(session) && session.isActive())
            return false;
        boolean taken = clients.stream().anyMatch(client -> client != session
                && client.isActive() && client.getName().equals(name));
        if (taken) {
            session.send("@error:name");
            return false;
        }
        session.setName(name);
        session.setUserId(UUID.randomUUID().toString());
        clients.add(session);
        session.setActive(true);
        logEvent("joined", name);
        session.send("@joined:" + session.getUserId());
        broadcastUsers();
        broadcast(timestamped(name + " joined the chat."));
        return true;
    }

    public synchronized void sendPrivate(ClientSession sender, String recipientId, String text) {
        if (!sender.isActive())
            return;
        for (ClientSession recipient : clients) {
            if (recipient.isActive() && recipient.getUserId().equals(recipientId)) {
                if (recipient == sender) {
                    sendPrivateLine(sender, recipientId, recipient.getName(), "You cannot message yourself.");
                    return;
                }
                sendPrivateLine(recipient, sender.getUserId(), sender.getName(), timestamped(sender.getName() + ": " + text));
                sendPrivateLine(sender, recipient.getUserId(), recipient.getName(), timestamped("You: " + text));
                audit(sender.getName(), recipient.getName());
                return;
            }
        }
        sendPrivateLine(sender, recipientId, "", "User is no longer online. Select the user again to start a new chat.");
    }

    public synchronized void sendPublic(ClientSession sender, String text) {
        if (!sender.isActive())
            return;
        String recipients = clients.stream().filter(ClientSession::isActive)
                .filter(client -> client != sender).map(ClientSession::getName).sorted()
                .collect(Collectors.joining(", "));
        broadcast(timestamped(sender.getName() + ": " + text));
        audit(sender.getName(), "public [" + recipients + "]");
    }

    public synchronized void createGroup(ClientSession creator, String name, String[] requestedMembers) {
        if (!creator.isActive() || name.isBlank() || name.length() > 80 || requestedMembers.length == 0) {
            creator.send("@notice:" + encode("Enter a group name and select at least one online user."));
            return;
        }
        Set<ClientSession> members = new LinkedHashSet<>();
        members.add(creator);
        for (String requested : requestedMembers) {
            ClientSession match = clients.stream().filter(ClientSession::isActive)
                    .filter(client -> client.getUserId().equals(requested)).findFirst().orElse(null);
            if (match == null || match == creator) {
                creator.send("@notice:" + encode("A selected user is no longer online."));
                return;
            }
            members.add(match);
        }
        if (members.size() < 2) {
            creator.send("@notice:" + encode("Select at least one other user."));
            return;
        }
        String id = UUID.randomUUID().toString();
        groups.put(id, new ChatGroup(name, members));
        String announcement = "@group:" + id + ":" + encode(name) + ":"
                + encode("Group created by " + creator.getName() + ".");
        members.forEach(member -> member.send(announcement));
    }

    public synchronized void sendGroup(ClientSession sender, String id, String text) {
        ChatGroup group = groups.get(id);
        if (!sender.isActive() || group == null || !group.members.contains(sender)) {
            sender.send("@notice:" + encode("You are no longer a member of this group."));
            return;
        }
        String payload = "@group:" + id + ":" + encode(group.name) + ":"
                + encode(timestamped(sender.getName() + ": " + text));
        group.members.forEach(member -> member.send(payload));
        String recipients = group.members.stream().filter(member -> member != sender)
                .map(ClientSession::getName).sorted().collect(Collectors.joining(", "));
        audit(sender.getName(), "group [" + recipients + "]");
    }

    private static String encode(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private void audit(String sender, String recipients) {
        System.out.println("[" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                + "] from=" + sender + " to=" + recipients);
    }

    private void logEvent(String event, String name) {
        System.out.println("[" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                + "] " + event + " user=" + name);
    }

    private void removeFromGroups(ClientSession session) {
        groups.values().forEach(group -> group.members.remove(session));
        groups.values().removeIf(group -> group.members.isEmpty());
    }

    private void sendPrivateLine(ClientSession recipient, String peerId, String peer, String text) {
        String encodedPeer = Base64.getEncoder().encodeToString(peer.getBytes(StandardCharsets.UTF_8));
        String encodedText = Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
        recipient.send("@pm:" + peerId + ":" + encodedPeer + ":" + encodedText);
    }

    public synchronized void setActive(ClientSession session, boolean active) {
        if (clients.contains(session) && session.isActive() != active) {
            session.setActive(active);
            logEvent(active ? "joined" : "left", session.getName());
            if (!active)
                removeFromGroups(session);
            broadcastUsers();
            broadcast(timestamped(session.getName() + (active ? " joined the chat." : " left the chat.")));
        }
    }

    public synchronized void remove(ClientSession session, boolean requestedQuit) {
        if (clients.remove(session)) {
            if (session.isActive()) {
                logEvent(requestedQuit ? "left" : "disconnected", session.getName());
                broadcast(timestamped(session.getName() + " left the chat."));
            }
            removeFromGroups(session);
            broadcastUsers();
        }
    }

    // 線上名單是獨立的協定訊息；Base64 避免暱稱中的分隔符干擾解析。
    private void broadcastUsers() {
        String names = clients.stream()
                .filter(ClientSession::isActive)
                .sorted(java.util.Comparator.comparing(ClientSession::getName))
                .map(client -> client.getUserId() + "=" + encode(client.getName()))
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
