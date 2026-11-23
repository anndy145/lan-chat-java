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

    /** 將剪貼簿圖片編碼成符合上傳限制的 PNG；過大時改用 JPEG 並視需要縮小。 */
    public static Encoded encodeForUpload(java.awt.Image source) throws IOException {
        int width = source.getWidth(null);
        int height = source.getHeight(null);
        if (width <= 0 || height <= 0) throw new IOException("Invalid image.");
        double scale = Math.min(1.0, Math.sqrt((double) MAX_PIXELS / ((double) width * height)));
        BufferedImage argb = draw(source, Math.max(1, (int) (width * scale)), Math.max(1, (int) (height * scale)),
                BufferedImage.TYPE_INT_ARGB);
        byte[] png = write(argb, "png", 0f);
        if (png.length <= MAX_BYTES) return new Encoded(png, "png");
        BufferedImage rgb = draw(argb, argb.getWidth(), argb.getHeight(), BufferedImage.TYPE_INT_RGB);
        while (true) {
            for (float quality : new float[] {0.9f, 0.75f, 0.6f}) {
                byte[] jpeg = write(rgb, "jpeg", quality);
                if (jpeg.length <= MAX_BYTES) return new Encoded(jpeg, "jpg");
            }
            if (rgb.getWidth() < 64 || rgb.getHeight() < 64) throw new IOException("Image is too large.");
            rgb = draw(rgb, (int) (rgb.getWidth() * 0.7), (int) (rgb.getHeight() * 0.7), BufferedImage.TYPE_INT_RGB);
        }
    }

    /** 編碼結果與建議的副檔名。 */
    public record Encoded(byte[] bytes, String extension) {}

    private static BufferedImage draw(java.awt.Image source, int width, int height, int type) {
        BufferedImage result = new BufferedImage(width, height, type);
        java.awt.Graphics2D graphics = result.createGraphics();
        try {
            if (type == BufferedImage.TYPE_INT_RGB) {
                // JPEG 沒有透明度，透明區域以白色填滿。
                graphics.setColor(java.awt.Color.WHITE);
                graphics.fillRect(0, 0, width, height);
            }
            graphics.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                    java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return result;
    }

    private static byte[] write(BufferedImage image, String format, float quality) throws IOException {
        javax.imageio.ImageWriter writer = ImageIO.getImageWritersByFormatName(format).next();
        var output = new java.io.ByteArrayOutputStream();
        try (var stream = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(stream);
            javax.imageio.ImageWriteParam parameters = writer.getDefaultWriteParam();
            if (quality > 0 && parameters.canWriteCompressed()) {
                parameters.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
                parameters.setCompressionQuality(quality);
            }
            writer.write(null, new javax.imageio.IIOImage(image, null, null), parameters);
        } finally {
            writer.dispose();
        }
        return output.toByteArray();
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