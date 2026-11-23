package server;

import common.ChatProtocol;
import common.ImageChunks;
import common.ImageWire;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

/** 每條連線一次上傳，驗證完成後才轉送。 */
final class ImageUpload {
    private static final Semaphore SLOTS = new Semaphore(4);
    private static final long UPLOAD_TIMEOUT = TimeUnit.SECONDS.toNanos(60);
    private static final long QUEUE_TIMEOUT = TimeUnit.SECONDS.toNanos(5);

    record Recipient(ClientSession session, String id, String conversation, String title, long routeVersion) {}
    record Route(String title, java.util.List<Recipient> recipients) {}

    private final ClientSession sender;
    private final Server server;
    private Pending pending;

    private record Pending(String id, String senderId, ImageWire.Destination destination, String target,
                           String filename, ImageChunks chunks, Route route, long deadline, long senderVersion) {}

    ImageUpload(ClientSession sender, Server server) {
        this.sender = sender;
        this.server = server;
    }

    long deadline() {
        return pending == null ? 0 : pending.deadline();
    }

    boolean handle(String line) {
        if (line.equals(ImageWire.ENABLE)) {
            sender.receivesImages = true;
            sender.send(ImageWire.READY);
        } else if (line.equals(ImageWire.ENABLE_LOBBY)) {
            sender.receivesLobbyImages = true;
            sender.send(ImageWire.READY_LOBBY);
        } else if (line.startsWith(ImageWire.BEGIN)) {
            begin(line.substring(ImageWire.BEGIN.length()));
        } else if (line.startsWith(ImageWire.CHUNK)) {
            chunk(line.substring(ImageWire.CHUNK.length()));
        } else if (line.startsWith(ImageWire.END)) {
            finish(line.substring(ImageWire.END.length()));
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
            if (pending != null) throw new IllegalArgumentException("An image upload is already in progress.");
            if (parts.length != 6 || !parts[1].equals(sender.getUserId())) {
                throw new IllegalArgumentException("Invalid image request.");
            }
            var destination = ImageWire.Destination.fromCode(parts[2]);
            if (destination == ImageWire.Destination.LOBBY && !sender.receivesLobbyImages)
                throw new IllegalArgumentException("Lobby images are not enabled.");
            String filename = ChatProtocol.decode(parts[4]);
            if (!ImageWire.validName(filename)) throw new IllegalArgumentException("Invalid image filename.");
            ImageChunks chunks = new ImageChunks(Integer.parseInt(parts[5]));
            Route route = server.imageRoute(sender, destination, parts[3]);
            if (route == null || (destination != ImageWire.Destination.LOBBY && route.recipients().isEmpty())) {
                throw new IllegalArgumentException("No eligible recipients are online.");
            }
            if (!SLOTS.tryAcquire()) throw new IllegalArgumentException("Server is busy with image uploads. Try again.");
            pending = new Pending(id, parts[1], destination, parts[3], filename, chunks, route,
                    System.nanoTime() + UPLOAD_TIMEOUT, sender.imageRouteVersion(destination));
        } catch (IllegalArgumentException exception) {
            result(id, "Image rejected: " + exception.getMessage());
        }
    }

    private void chunk(String payload) {
        if (pending == null) return;
        String[] parts = payload.split(":", 3);
        if (!parts[0].equals(pending.id())) return;
        try {
            if (parts.length != 3) throw new IOException("Incomplete image chunk.");
            pending.chunks().append(Integer.parseInt(parts[1]), parts[2]);
        } catch (IOException | IllegalArgumentException exception) {
            result(pending.id(), "Image rejected: " + exception.getMessage());
            close();
        }
    }

    private void finish(String id) {
        Pending upload = pending;
        if (upload == null || !upload.id().equals(id)) return;
        try {
            byte[] bytes = upload.chunks().finish();
            long deadline = System.nanoTime() + UPLOAD_TIMEOUT;
            String relayId = UUID.randomUUID().toString();
            int delivered = 0;
            int notices = 0;
            for (Recipient recipient : upload.route().recipients()) {
                if (relay(upload, recipient, relayId, bytes, deadline)) {
                    if (canReceive(upload, recipient)) delivered++;
                    else notices++;
                }
            }
            if ((upload.destination() == ImageWire.Destination.LOBBY || delivered + notices > 0) && sender.receivesImages) {
                relay(upload, new Recipient(sender, upload.senderId(), upload.target(),
                        upload.route().title(), upload.senderVersion()), relayId, bytes, deadline);
            }
            result(id, "Image queued for " + delivered + " recipient(s), " + notices
                    + " notification(s) only, " + (upload.route().recipients().size() - delivered - notices)
                    + " skipped.");
        } catch (IOException exception) {
            result(id, "Image rejected: " + exception.getMessage());
        } finally {
            close();
        }
    }

    private static boolean canReceive(Pending upload, Recipient recipient) {
        return recipient.session().receivesImages
                && (upload.destination() != ImageWire.Destination.LOBBY || recipient.session().receivesLobbyImages);
    }

    private boolean relay(Pending upload, Recipient recipient, String id, byte[] bytes, long deadline) {
        if (!canReceive(upload, recipient)) {
            return queue(upload, recipient, ImageWire.NOTICE + ChatProtocol.encode(
                    "Image from " + sender.getName() + (upload.destination() != ImageWire.Destination.PRIVATE ? " in " + recipient.title() : "")
                            + ": " + upload.filename()), deadline);
        }
        String header = id + ":" + upload.destination().code()
                + ":" + recipient.conversation() + ":" + ChatProtocol.encode(recipient.title())
                + ":" + ChatProtocol.encode(recipient.session() == sender ? "You" : sender.getName())
                + ":" + ChatProtocol.encode(upload.filename()) + ":" + bytes.length;
        if (!queue(upload, recipient, ImageWire.BEGIN + header, deadline)) return false;
        int sequence = 0;
        for (int offset = 0; offset < bytes.length; offset += ImageChunks.CHUNK_BYTES) {
            int size = Math.min(ImageChunks.CHUNK_BYTES, bytes.length - offset);
            if (!queue(upload, recipient, ImageWire.CHUNK + id + ":" + sequence++ + ":"
                    + ImageChunks.encode(bytes, offset, size), deadline)) {
                abort(recipient, id);
                return false;
            }
        }
        if (queue(upload, recipient, ImageWire.END + id, deadline)) return true;
        abort(recipient, id);
        return false;
    }

    private void abort(Recipient recipient, String id) {
        recipient.session().send(ImageWire.FAIL + id);
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
        sender.send(ImageWire.RESULT + id + ":" + ChatProtocol.encode(message));
    }

    void close() {
        if (pending != null) {
            pending = null;
            SLOTS.release();
        }
    }
}
