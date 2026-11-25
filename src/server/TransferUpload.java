package server;

import common.ChatProtocol;
import common.FileWire;
import common.TransferChunks;
import common.ImageWire;
import common.TransferWire;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

/** 每條連線每種類型同時一次上傳（圖片或一般檔案），驗證完成後才轉送。 */
final class TransferUpload {
    private static final long QUEUE_TIMEOUT = TimeUnit.SECONDS.toNanos(5);

    /** 圖片會驗證格式並轉成縮圖；一般檔案只檢查大小，Client 不會自動開啟。 */
    enum Kind {
        IMAGE(TransferWire.IMAGE, new Semaphore(4), TimeUnit.SECONDS.toNanos(60)),
        FILE(TransferWire.FILE, new Semaphore(2), TimeUnit.SECONDS.toNanos(120));

        final TransferWire wire;
        final Semaphore slots;
        final long timeout;

        Kind(TransferWire wire, Semaphore slots, long timeout) {
            this.wire = wire;
            this.slots = slots;
            this.timeout = timeout;
        }
    }

    record Recipient(ClientSession session, String id, String conversation, String title, long routeVersion) {}
    record Route(String title, java.util.List<Recipient> recipients) {}

    private final ClientSession sender;
    private final Server server;
    private final Kind kind;
    private final TransferWire wire;
    private Pending pending;

    private record Pending(String id, String senderId, ImageWire.Destination destination, String target,
                           String filename, TransferChunks chunks, Route route, long deadline, long senderVersion) {}

    TransferUpload(ClientSession sender, Server server, Kind kind) {
        this.sender = sender;
        this.server = server;
        this.kind = kind;
        this.wire = kind.wire;
    }

    long deadline() {
        return pending == null ? 0 : pending.deadline();
    }

    boolean handle(String line) {
        if (kind == Kind.IMAGE && line.equals(ImageWire.ENABLE)) {
            sender.receivesImages = true;
            sender.send(ImageWire.READY);
        } else if (kind == Kind.IMAGE && line.equals(ImageWire.ENABLE_LOBBY)) {
            sender.receivesLobbyImages = true;
            sender.send(ImageWire.READY_LOBBY);
        } else if (kind == Kind.FILE && line.equals(FileWire.ENABLE)) {
            sender.receivesFiles = true;
            sender.send(FileWire.READY);
        } else if (line.startsWith(wire.begin())) {
            begin(line.substring(wire.begin().length()));
        } else if (line.startsWith(wire.chunk())) {
            chunk(line.substring(wire.chunk().length()));
        } else if (line.startsWith(wire.end())) {
            finish(line.substring(wire.end().length()));
        } else {
            return false;
        }
        return true;
    }

    private void begin(String payload) {
        String[] parts = payload.split(":", 6);
        String id = parts[0];
        try {
            if (!UUID.fromString(id).toString().equals(id)) return;
            if (pending != null) throw new IllegalArgumentException("An upload is already in progress.");
            if (parts.length != 6 || !parts[1].equals(sender.getUserId())) {
                throw new IllegalArgumentException("Invalid request.");
            }
            var destination = ImageWire.Destination.fromCode(parts[2]);
            if (kind == Kind.IMAGE && destination == ImageWire.Destination.LOBBY && !sender.receivesLobbyImages)
                throw new IllegalArgumentException("Lobby images are not enabled.");
            String filename = ChatProtocol.decode(parts[4]);
            if (!ImageWire.validName(filename)) throw new IllegalArgumentException("Invalid filename.");
            TransferChunks chunks = new TransferChunks(Integer.parseInt(parts[5]), wire.maxBytes());
            Route route = server.imageRoute(sender, destination, parts[3]);
            if (route == null || (destination != ImageWire.Destination.LOBBY && route.recipients().isEmpty())) {
                throw new IllegalArgumentException("No eligible recipients are online.");
            }
            if (!kind.slots.tryAcquire())
                throw new IllegalArgumentException("Server is busy with other uploads. Try again.");
            pending = new Pending(id, parts[1], destination, parts[3], filename, chunks, route,
                    System.nanoTime() + kind.timeout, sender.imageRouteVersion(destination));
        } catch (IllegalArgumentException exception) {
            result(id, wire.label() + " rejected: " + exception.getMessage());
        }
    }

    private void chunk(String payload) {
        if (pending == null) return;
        String[] parts = payload.split(":", 3);
        if (!parts[0].equals(pending.id())) return;
        try {
            if (parts.length != 3) throw new IOException("Incomplete chunk.");
            pending.chunks().append(Integer.parseInt(parts[1]), parts[2]);
        } catch (IOException | IllegalArgumentException exception) {
            result(pending.id(), wire.label() + " rejected: " + exception.getMessage());
            close();
        }
    }

    private void finish(String id) {
        Pending upload = pending;
        if (upload == null || !upload.id().equals(id)) return;
        try {
            byte[] bytes = kind == Kind.IMAGE ? upload.chunks().finish() : upload.chunks().finishBytes();
            long deadline = System.nanoTime() + kind.timeout;
            String relayId = UUID.randomUUID().toString();
            int delivered = 0;
            int notices = 0;
            for (Recipient recipient : upload.route().recipients()) {
                if (relay(upload, recipient, relayId, bytes, deadline)) {
                    if (canReceive(upload, recipient)) delivered++;
                    else notices++;
                }
            }
            boolean senderReceives = kind == Kind.IMAGE ? sender.receivesImages : sender.receivesFiles;
            if ((upload.destination() == ImageWire.Destination.LOBBY || delivered + notices > 0) && senderReceives) {
                relay(upload, new Recipient(sender, upload.senderId(), upload.target(),
                        upload.route().title(), upload.senderVersion()), relayId, bytes, deadline);
            }
            result(id, wire.label() + " queued for " + delivered + " recipient(s), " + notices
                    + " notification(s) only, " + (upload.route().recipients().size() - delivered - notices)
                    + " skipped.");
        } catch (IOException exception) {
            result(id, wire.label() + " rejected: " + exception.getMessage());
        } finally {
            close();
        }
    }

    private boolean canReceive(Pending upload, Recipient recipient) {
        ClientSession session = recipient.session();
        if (kind == Kind.FILE) return session.receivesFiles;
        return session.receivesImages
                && (upload.destination() != ImageWire.Destination.LOBBY || session.receivesLobbyImages);
    }

    private boolean relay(Pending upload, Recipient recipient, String id, byte[] bytes, long deadline) {
        if (!canReceive(upload, recipient)) {
            // 舊版或命令列 Client 都看得懂圖片提示，因此一般檔案也使用同一種提示。
            return queue(upload, recipient, ImageWire.NOTICE + ChatProtocol.encode(
                    wire.label() + " from " + sender.getName()
                            + (upload.destination() != ImageWire.Destination.PRIVATE ? " in " + recipient.title() : "")
                            + ": " + upload.filename()), deadline);
        }
        String header = id + ":" + upload.destination().code()
                + ":" + recipient.conversation() + ":" + ChatProtocol.encode(recipient.title())
                + ":" + ChatProtocol.encode(recipient.session() == sender ? "You" : sender.getName())
                + ":" + ChatProtocol.encode(upload.filename()) + ":" + bytes.length;
        if (!queue(upload, recipient, wire.begin() + header, deadline)) return false;
        int sequence = 0;
        for (int offset = 0; offset < bytes.length; offset += TransferChunks.CHUNK_BYTES) {
            int size = Math.min(TransferChunks.CHUNK_BYTES, bytes.length - offset);
            if (!queue(upload, recipient, wire.chunk() + id + ":" + sequence++ + ":"
                    + TransferChunks.encode(bytes, offset, size), deadline)) {
                abort(recipient, id);
                return false;
            }
        }
        if (queue(upload, recipient, wire.end() + id, deadline)) return true;
        abort(recipient, id);
        return false;
    }

    private void abort(Recipient recipient, String id) {
        recipient.session().send(wire.fail() + id);
    }

    private boolean queue(Pending upload, Recipient recipient, String line, long deadline) {
        byte[] frame = (line + "\n").getBytes(StandardCharsets.UTF_8);
        long remaining = Math.min(deadline - System.nanoTime(), QUEUE_TIMEOUT);
        long until = System.nanoTime() + remaining;
        while (System.nanoTime() - until < 0) {
            int queued = server.queueImage(sender, upload.senderId(), upload.senderVersion(), upload.destination(), upload.target(), recipient, frame);
            if (queued != 0) return queued > 0;
            // 等候佇列空間時不持有 Server 的鎖。
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
        }
        return false;
    }

    private void result(String id, String message) {
        sender.send(wire.result() + id + ":" + ChatProtocol.encode(message));
    }

    void close() {
        if (pending != null) {
            pending = null;
            kind.slots.release();
        }
    }
}
