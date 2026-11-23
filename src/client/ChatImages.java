package client;

import common.ImageFiles;
import common.ImageWire;
import common.TransferWire;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;

final class ChatImages {
    record Picture(ImageWire.Destination destination, String conversation, String title, String sender,
                   String filename, byte[] bytes, BufferedImage thumbnail) implements ChatModel.Entry {}

    private final ChatTransfers transfers = new ChatTransfers(TransferWire.IMAGE);

    static boolean isData(String line) {
        return TransferWire.IMAGE.isData(line);
    }

    Picture receive(String line) throws IOException {
        ChatTransfers.Received received = transfers.receive(line);
        if (received == null) return null;
        return new Picture(received.destination(), received.conversation(), received.title(), received.sender(),
                received.filename(), received.bytes(), scaled(ImageFiles.decode(received.bytes()), 240));
    }

    static boolean hasImageName(String name) {
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg");
    }

    static BufferedImage scaled(BufferedImage image, int maximum) {
        double scale = Math.min(1.0, (double) maximum / Math.max(image.getWidth(), image.getHeight()));
        BufferedImage result = new BufferedImage(Math.max(1, (int) (image.getWidth() * scale)),
                Math.max(1, (int) (image.getHeight() * scale)), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = result.createGraphics();
        try {
            graphics.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                    java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.drawImage(image, 0, 0, result.getWidth(), result.getHeight(), null);
        } finally {
            graphics.dispose();
        }
        return result;
    }
}
