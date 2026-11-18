package server;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** 限制每行 UTF-8 資料的位元組數，並支援第一行的完整等待期限。 */
final class BoundedLineReader {
    static final int MAX_LINE_BYTES = 16 * 1024;
    private final Socket socket;
    private final BufferedInputStream input;

    BoundedLineReader(Socket socket) throws IOException {
        this.socket = socket;
        this.input = new BufferedInputStream(socket.getInputStream());
    }

    String readLine(long deadlineNanos) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        while (true) {
            if (deadlineNanos != 0) {
                long remaining = deadlineNanos - System.nanoTime();
                if (remaining <= 0) throw new SocketTimeoutException("Handshake timed out");
                long millis = Math.max(1, TimeUnit.NANOSECONDS.toMillis(remaining));
                socket.setSoTimeout((int) Math.min(Integer.MAX_VALUE, millis));
            }
            int value = input.read();
            if (value == -1) {
                if (bytes.size() == 0) return null;
                break;
            }
            if (value == '\n') break;
            // CRLF 的 CR 不算在內容大小內；若後面不是 LF，下一個位元組會觸發上限。
            if (bytes.size() >= MAX_LINE_BYTES && !(bytes.size() == MAX_LINE_BYTES && value == '\r')) {
                throw new IOException("Line exceeds maximum size");
            }
            bytes.write(value);
        }
        byte[] data = bytes.toByteArray();
        int length = data.length;
        if (length > 0 && data[length - 1] == '\r') length--;
        return new String(data, 0, length, StandardCharsets.UTF_8);
    }
}