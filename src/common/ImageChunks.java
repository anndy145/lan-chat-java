package common;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

/** 圖片區塊的大小與順序驗證。此類別不負責網路傳輸或授權。 */
public final class ImageChunks {
    public static final int CHUNK_BYTES = 8 * 1024;
    private final int expectedBytes;
    private final ByteArrayOutputStream data;
    private int nextSequence;

    public ImageChunks(int expectedBytes) {
        if (expectedBytes <= 0 || expectedBytes > ImageFiles.MAX_BYTES) {
            throw new IllegalArgumentException("Invalid image size");
        }
        this.expectedBytes = expectedBytes;
        this.data = new ByteArrayOutputStream(Math.min(expectedBytes, CHUNK_BYTES));
    }

    public static String encode(byte[] source, int offset, int length) {
        if (length <= 0 || length > CHUNK_BYTES || offset < 0 || offset > source.length - length) {
            throw new IllegalArgumentException("Invalid chunk range");
        }
        return Base64.getEncoder().encodeToString(java.util.Arrays.copyOfRange(source, offset, offset + length));
    }

    public void append(int sequence, String encoded) throws IOException {
        if (sequence != nextSequence) throw new IOException("Invalid chunk order");
        if (encoded.length() > ((CHUNK_BYTES + 2) / 3) * 4) throw new IOException("Chunk too large");
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException exception) {
            throw new IOException("Invalid chunk encoding", exception);
        }
        if (bytes.length == 0 || bytes.length > CHUNK_BYTES || bytes.length > expectedBytes - data.size()) {
            throw new IOException("Invalid chunk size");
        }
        data.write(bytes, 0, bytes.length);
        nextSequence++;
    }

    public byte[] finish() throws IOException {
        byte[] bytes = finishBytes();
        ImageFiles.validate(bytes);
        return bytes;
    }

    public byte[] finishBytes() throws IOException {
        if (data.size() != expectedBytes) throw new IOException("Incomplete image");
        return data.toByteArray();
    }
}