package common;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Locale;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/** 圖片大小、實際格式與像素數驗證。 */
public final class ImageFiles {
    public static final int MAX_BYTES = 5 * 1024 * 1024;
    public static final long MAX_PIXELS = 20_000_000L;

    private ImageFiles() {}

    public static byte[] read(Path path) throws IOException {
        try (var input = Files.newInputStream(path)) {
            byte[] bytes = input.readNBytes(MAX_BYTES + 1);
            validate(bytes);
            return bytes;
        }
    }

    public static void validate(byte[] bytes) throws IOException {
        decode(bytes);
    }

    public static BufferedImage decode(byte[] bytes) throws IOException {
        if (bytes.length == 0 || bytes.length > MAX_BYTES) {
            throw new IOException("Image must be between 1 byte and 5 MiB.");
        }
        try (ImageInputStream input = new javax.imageio.stream.MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) throw new IOException("Cannot read image.");
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("Only PNG and JPEG images are supported.");
            ImageReader reader = readers.next();
            try {
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!format.equals("png") && !format.equals("jpeg") && !format.equals("jpg")) {
                    throw new IOException("Only PNG and JPEG images are supported.");
                }
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > MAX_PIXELS) {
                    throw new IOException("Image dimensions are too large.");
                }
                BufferedImage decoded = reader.read(0);
                if (decoded == null) throw new IOException("Invalid image.");
                return decoded;
            } finally {
                reader.dispose();
            }
        } catch (RuntimeException exception) {
            throw new IOException("Invalid image.", exception);
        }
    }
}