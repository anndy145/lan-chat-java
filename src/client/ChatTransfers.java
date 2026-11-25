package client;

import common.ChatProtocol;
import common.TransferChunks;
import common.ImageWire;
import common.TransferWire;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.function.IntConsumer;

/** 圖片與一般檔案共用的分塊傳送與接收；不負責畫面更新。 */
final class ChatTransfers {
    record Received(ImageWire.Destination destination, String conversation, String title, String sender,
                    String filename, byte[] bytes) {}

    private record Incoming(String[] header, TransferChunks body) {}

    private final TransferWire wire;
    private final Map<String, Incoming> incoming = new HashMap<>();

    ChatTransfers(TransferWire wire) {
        this.wire = wire;
    }

    /** 依序送出 BEGIN、所有區塊與 END；progress 收到 0 到 100 的百分比。 */
    static void send(PrintWriter output, TransferWire wire, byte[] bytes, String name, String id, String sender,
                     ImageWire.Destination destination, String target, IntConsumer progress) throws IOException {
        if (!ImageWire.validName(name)) throw new IOException("Invalid filename.");
        if (bytes.length == 0 || bytes.length > wire.maxBytes()) throw new IOException("Invalid size.");
        output.println(wire.begin() + id + ":" + sender + ":" + destination.code()
                + ":" + target + ":" + ChatProtocol.encode(name) + ":" + bytes.length);
        int sequence = 0;
        for (int offset = 0; offset < bytes.length; offset += TransferChunks.CHUNK_BYTES) {
            int size = Math.min(TransferChunks.CHUNK_BYTES, bytes.length - offset);
            output.println(wire.chunk() + id + ":" + sequence++ + ":" + TransferChunks.encode(bytes, offset, size));
            if (output.checkError()) throw new IOException("Connection closed.");
            progress.accept((int) ((offset + (long) size) * 100 / bytes.length));
        }
        output.println(wire.end() + id);
        if (output.checkError()) throw new IOException("Connection closed.");
    }

    boolean isData(String line) {
        return wire.isData(line);
    }

    Received receive(String line) throws IOException {
        int separator = line.indexOf(':');
        String[] fields = line.substring(separator + 1).split(":", 7);
        String id = fields[0];
        try {
            if (line.startsWith(wire.begin())) {
                if (fields.length != 7 || incoming.size() >= 4) throw new IOException("Invalid transfer header.");
                incoming.put(id, new Incoming(fields, new TransferChunks(Integer.parseInt(fields[6]), wire.maxBytes())));
            } else if (line.startsWith(wire.chunk())) {
                Incoming transfer = incoming.get(id);
                if (transfer != null) {
                    if (fields.length != 3) throw new IOException("Invalid transfer chunk.");
                    transfer.body().append(Integer.parseInt(fields[1]), fields[2]);
                }
            } else if (line.startsWith(wire.end())) {
                Incoming transfer = incoming.remove(id);
                if (transfer != null) {
                    String[] header = transfer.header();
                    byte[] bytes = transfer.body().finishBytes();
                    String filename = ChatProtocol.decode(header[5]);
                    if (!ImageWire.validName(filename)) throw new IOException("Invalid filename.");
                    return new Received(ImageWire.Destination.fromCode(header[1]), header[2],
                            ChatProtocol.decode(header[3]), ChatProtocol.decode(header[4]), filename, bytes);
                }
            } else {
                incoming.remove(id);
                throw new IOException(wire.label() + " transfer interrupted.");
            }
            return null;
        } catch (IllegalArgumentException exception) {
            incoming.remove(id);
            throw new IOException("Invalid " + wire.label().toLowerCase(java.util.Locale.ROOT) + " data.", exception);
        } catch (IOException exception) {
            incoming.remove(id);
            throw exception;
        }
    }
}
