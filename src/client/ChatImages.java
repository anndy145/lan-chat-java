package client;

import common.ChatProtocol;
import common.ImageChunks;
import common.ImageFiles;
import common.ImageWire;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

final class ChatImages {
    record Picture(ImageWire.Destination destination, String conversation, String title, String sender,
                   String filename, byte[] bytes, BufferedImage thumbnail) implements ChatModel.Entry {}
    private record Incoming(String[] header, ImageChunks body) {}
    private final Map<String, Incoming> incoming = new HashMap<>();

    static void send(PrintWriter output, Path path, String id, String sender, ImageWire.Destination destination, String target)
            throws IOException {
        byte[] bytes = ImageFiles.read(path);
        String name = path.getFileName().toString();
        if (!ImageWire.validName(name)) throw new IOException("Invalid image filename.");
        output.println(ImageWire.BEGIN + id + ":" + sender + ":" + destination.code()
                + ":" + target + ":" + ChatProtocol.encode(name) + ":" + bytes.length);
        int sequence = 0;
        for (int offset = 0; offset < bytes.length; offset += ImageChunks.CHUNK_BYTES) {
            output.println(ImageWire.CHUNK + id + ":" + sequence++ + ":" + ImageChunks.encode(
                    bytes, offset, Math.min(ImageChunks.CHUNK_BYTES, bytes.length - offset)));
            if (output.checkError()) throw new IOException("Connection closed.");
        }
        output.println(ImageWire.END + id);
        if (output.checkError()) throw new IOException("Connection closed.");
    }

    static boolean isData(String line) {
        return line.startsWith(ImageWire.BEGIN) || line.startsWith(ImageWire.CHUNK)
                || line.startsWith(ImageWire.END) || line.startsWith(ImageWire.FAIL);
    }

    Picture receive(String line) throws IOException {
        int separator = line.indexOf(':');
        String[] fields = line.substring(separator + 1).split(":", 7);
        String id = fields[0];
        try {
            if (line.startsWith(ImageWire.BEGIN)) {
                if (fields.length != 7 || incoming.size() >= 4) throw new IOException("Invalid image header.");
                incoming.put(id, new Incoming(fields, new ImageChunks(Integer.parseInt(fields[6]))));
            } else if (line.startsWith(ImageWire.CHUNK)) {
                Incoming transfer = incoming.get(id);
                if (transfer != null) {
                    if (fields.length != 3) throw new IOException("Invalid image chunk.");
                    transfer.body().append(Integer.parseInt(fields[1]), fields[2]);
                }
            } else if (line.startsWith(ImageWire.END)) {
                Incoming transfer = incoming.remove(id);
                if (transfer != null) {
                    String[] header = transfer.header();
                    byte[] bytes = transfer.body().finishBytes();
                    String filename = ChatProtocol.decode(header[5]);
                    if (!ImageWire.validName(filename)) throw new IOException("Invalid image filename.");
                    return new Picture(ImageWire.Destination.fromCode(header[1]), header[2], ChatProtocol.decode(header[3]),
                            ChatProtocol.decode(header[4]), filename, bytes, scaled(ImageFiles.decode(bytes), 240));
                }
            } else {
                incoming.remove(id);
                throw new IOException("Image transfer interrupted.");
            }
            return null;
        } catch (IllegalArgumentException exception) {
            incoming.remove(id);
            throw new IOException("Invalid image data.", exception);
        } catch (IOException exception) {
            incoming.remove(id);
            throw exception;
        }
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
