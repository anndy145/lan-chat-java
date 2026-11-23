package server;

import common.ChatProtocol;
import common.ImageWire;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/** 處理單一 Client 的連線。 */
class ClientSession implements Runnable {
    private final Socket socket;
    private final Server server;
    volatile boolean receivesImages;
    volatile boolean receivesLobbyImages;
    volatile boolean receivesFiles;
    long activeVersion;
    long groupDepartureVersion;
    boolean isClosing() { return closing.get(); }
    private static final int MAX_PENDING_MESSAGES = 128;
    private static final int MAX_PENDING_BYTES = 1024 * 1024;
    private static final byte[] STOP = new byte[0];
    private final ArrayBlockingQueue<byte[]> outbound = new ArrayBlockingQueue<>(MAX_PENDING_MESSAGES);
    private final Object outboundLock = new Object();
    private final AtomicBoolean closing = new AtomicBoolean();
    private int pendingBytes;
    private Thread writerThread;
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
        boolean rejected = false;
        TransferUpload images = new TransferUpload(this, server, TransferUpload.Kind.IMAGE);
        TransferUpload files = new TransferUpload(this, server, TransferUpload.Kind.FILE);
        try {
            startWriter();
            BoundedLineReader input = new BoundedLineReader(socket);
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
            String requestedName = input.readLine(deadline);
            socket.setSoTimeout(0);
            if (requestedName == null) {
                return;
            }
            boolean initialObserver = ChatProtocol.OBSERVE.equals(requestedName);
            if (!initialObserver && !requestedName.startsWith("@hello:v2:")) {
                send(ChatProtocol.ERROR_VERSION);
                rejected = true;
                return;
            }
            active = false;
            try {
                name = initialObserver ? "Anonymous" : cleanName(new String(
                        Base64.getDecoder().decode(requestedName.substring(10)), StandardCharsets.UTF_8));
            } catch (IllegalArgumentException exception) {
                send(ChatProtocol.ERROR_VERSION);
                rejected = true;
                return;
            }
            if (initialObserver) {
                server.add(this);
            } else if (!server.join(this, name)) {
                rejected = true;
                return;
            }

            String line;
            while (true) {
                long uploadDeadline = earliest(images.deadline(), files.deadline());
                if (uploadDeadline == 0 && socket.getSoTimeout() != 0) socket.setSoTimeout(0);
                line = input.readLine(uploadDeadline);
                if (line == null) break;
                if ("/quit".equalsIgnoreCase(line.trim())) {
                    requestedQuit = true;
                    break;
                }
                if (images.handle(line) || files.handle(line)) continue;
                if (ChatProtocol.LEAVE.equals(line)) {
                    images.close();
            files.close();
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
                        String text = decodeMessage(line.substring(ChatProtocol.SAY.length()));
                        if (!text.isBlank()) {
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
                            String text = decodeMessage(parts[1]);
                            if (!text.isBlank()) {
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
                } else if (line.startsWith(ChatProtocol.GROUP_MEMBERS_REQUEST) && isActive()) {
                    server.sendGroupMembers(this, line.substring(ChatProtocol.GROUP_MEMBERS_REQUEST.length()));
                } else if (line.startsWith(ChatProtocol.GROUP_LEAVE) && isActive()) {
                    server.leaveGroup(this, line.substring(ChatProtocol.GROUP_LEAVE.length()));
                } else if (line.startsWith(ChatProtocol.GROUP_SEND) && isActive()) {
                    String[] parts = line.substring(ChatProtocol.GROUP_SEND.length()).split(":", 2);
                    try {
                        if (parts.length == 2) {
                            String text = decodeMessage(parts[1]);
                            if (!text.isBlank()) {
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
            images.close();
            files.close();
            if (rejected) drainRejection();
            closeSocket();
            if (writerThread != null) writerThread.interrupt();
            if (name != null) server.remove(this, requestedQuit);
        }
    }

    // 此方法可能在 Server 的鎖內呼叫，只加入佇列，不執行 Socket 寫入。
    public void send(String message) {
        byte[] bytes = (message + "\n").getBytes(StandardCharsets.UTF_8);
        boolean overflow = false;
        synchronized (outboundLock) {
            if (closing.get()) return;
            if (bytes.length > MAX_PENDING_BYTES - pendingBytes || !outbound.offer(bytes)) {
                overflow = true;
            } else {
                pendingBytes += bytes.length;
            }
        }
        if (overflow) requestClose();
    }

    // 圖片轉送使用有期限的重試，佇列滿時保留一般聊天的處理能力。
    boolean offerImage(byte[] bytes) {
        synchronized (outboundLock) {
            if (closing.get() || bytes.length > MAX_PENDING_BYTES - 128 * 1024 - pendingBytes
                    || outbound.remainingCapacity() <= 16 || !outbound.offer(bytes)) {
                return false;
            }
            pendingBytes += bytes.length;
            return true;
        }
    }

    private void startWriter() throws IOException {
        OutputStream output = socket.getOutputStream();
        writerThread = new Thread(() -> {
            try {
                while (true) {
                    byte[] bytes = outbound.take();
                    if (bytes == STOP) return;
                    output.write(bytes);
                    output.flush();
                    synchronized (outboundLock) {
                        pendingBytes -= bytes.length;
                    }
                }
            } catch (IOException exception) {
                closeSocket();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        }, "client-writer-" + socket.getRemoteSocketAddress());
        writerThread.setDaemon(true);
        writerThread.start();
    }

    // 首次握手遭拒時，讓錯誤訊息有機會送達，但不無限等待慢速連線。
    private void drainRejection() {
        if (writerThread == null || !outbound.offer(STOP)) return;
        try {
            writerThread.join(1000);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private void requestClose() {
        if (closing.compareAndSet(false, true)) {
            Thread closer = new Thread(this::closeSocket, "slow-client-close");
            closer.setDaemon(true);
            closer.start();
        }
    }

    private void closeSocket() {
        closing.set(true);
        try {
            socket.close();
        } catch (IOException ignored) {
            // 連線可能已經關閉。
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

    long imageRouteVersion(ImageWire.Destination destination) {
        return destination == ImageWire.Destination.LOBBY ? activeVersion : groupDepartureVersion;
    }

    boolean isActive() {
        return active;
    }

    void setActive(boolean active) {
        if (this.active != active) activeVersion++;
        this.active = active;
    }

    private static long earliest(long first, long second) {
        if (first == 0) return second;
        if (second == 0) return first;
        return first - second <= 0 ? first : second;
    }

    private static String decodeMessage(String encoded) {
        return ChatProtocol.decode(encoded).replace("\r\n", "\n").replace('\r', '\n');
    }

    private static String cleanName(String requestedName) {
        if (requestedName == null || requestedName.isBlank()) {
            return "Anonymous";
        }
        return requestedName.strip().replaceAll("[\\r\\n]", "");
    }
}

