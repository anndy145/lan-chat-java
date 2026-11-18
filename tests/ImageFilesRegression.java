import common.ImageFiles;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

/** 確認圖片限制依實際資料判斷，不依賴副檔名。 */
public class ImageFilesRegression {
    private static void rejects(byte[] data) throws Exception {
        try {
            ImageFiles.validate(data);
            throw new AssertionError("Invalid image was accepted");
        } catch (IOException expected) {
            // 預期拒絕無效或過大的圖片。
        }
    }

    public static void main(String[] args) throws Exception {
        BufferedImage image = new BufferedImage(3, 2, BufferedImage.TYPE_INT_RGB);
        for (String format : new String[]{"png", "jpeg"}) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            if (!ImageIO.write(image, format, bytes)) throw new AssertionError("Missing " + format + " writer");
            ImageFiles.validate(bytes.toByteArray());
        }
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", png)) throw new AssertionError("Missing PNG writer");
        byte[] largeDimensions = png.toByteArray();
        java.nio.ByteBuffer header = java.nio.ByteBuffer.wrap(largeDimensions);
        header.putInt(16, 5000);
        header.putInt(20, 4001);
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update(largeDimensions, 12, 17);
        header.putInt(29, (int) crc.getValue());
        rejects(largeDimensions);

        ByteArrayOutputStream gif = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "gif", gif)) throw new AssertionError("Missing GIF writer");
        rejects(gif.toByteArray());
        rejects(new byte[0]);
        rejects("not an image".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        rejects(new byte[ImageFiles.MAX_BYTES + 1]);
        System.out.println("PASS: PNG, JPEG, GIF rejection, pixel and file size limits");
    }
}