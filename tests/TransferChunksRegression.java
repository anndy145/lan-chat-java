import common.TransferChunks;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import javax.imageio.ImageIO;

public class TransferChunksRegression {
    public static void main(String[] args) throws Exception {
        BufferedImage image = new BufferedImage(120, 120, BufferedImage.TYPE_INT_RGB);
        java.util.Random random = new java.util.Random(42);
        for (int y = 0; y < image.getHeight(); y++)
            for (int x = 0; x < image.getWidth(); x++) image.setRGB(x, y, random.nextInt());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        byte[] original = out.toByteArray();
        TransferChunks chunks = new TransferChunks(original.length);
        int sequence = 0;
        for (int offset = 0; offset < original.length; offset += TransferChunks.CHUNK_BYTES) {
            int size = Math.min(TransferChunks.CHUNK_BYTES, original.length - offset);
            chunks.append(sequence++, TransferChunks.encode(original, offset, size));
        }
        if (!Arrays.equals(original, chunks.finish())) throw new AssertionError("Round trip failed");
        TransferChunks invalid = new TransferChunks(original.length);
        try { invalid.append(1, "YQ=="); throw new AssertionError("Out-of-order chunk accepted"); }
        catch (IOException expected) {}
        try { invalid.append(0, "%%%"); throw new AssertionError("Invalid encoding accepted"); }
        catch (IOException expected) {}
        invalid.append(0, "YQ==");
        try { invalid.finish(); throw new AssertionError("Incomplete image accepted"); }
        catch (IOException expected) {}
        System.out.println("PASS: multi-chunk image round trip and invalid input");
    }
}