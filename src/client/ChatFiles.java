package client;

import common.FileWire;
import common.ImageWire;
import common.TransferWire;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** 一般檔案的讀取與接收。收到的檔案只保留在記憶體，使用者選擇另存時才寫入磁碟。 */
final class ChatFiles {
    record Attachment(ImageWire.Destination destination, String conversation, String title, String sender,
                      String filename, byte[] bytes) implements ChatModel.Entry {}

    private final ChatTransfers transfers = new ChatTransfers(TransferWire.FILE);

    static boolean isData(String line) {
        return TransferWire.FILE.isData(line);
    }

    Attachment receive(String line) throws IOException {
        ChatTransfers.Received received = transfers.receive(line);
        if (received == null) return null;
        return new Attachment(received.destination(), received.conversation(), received.title(), received.sender(),
                received.filename(), received.bytes());
    }

    static byte[] read(Path path) throws IOException {
        if (!Files.isRegularFile(path)) throw new IOException("Only regular files can be sent.");
        try (var input = Files.newInputStream(path)) {
            byte[] bytes = input.readNBytes(FileWire.MAX_BYTES + 1);
            if (bytes.length == 0) throw new IOException("File is empty.");
            if (bytes.length > FileWire.MAX_BYTES) throw new IOException("File must be at most 20 MiB.");
            return bytes;
        }
    }

    static String size(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(java.util.Locale.ROOT, "%.1f KB", bytes / 1024.0);
        return String.format(java.util.Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024));
    }
}
