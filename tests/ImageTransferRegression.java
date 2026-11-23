import common.ChatProtocol;
import common.ImageChunks;
import common.ImageWire;
import server.Server;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Random;
import java.util.UUID;
import javax.imageio.ImageIO;

public class ImageTransferRegression {
    private static final class Peer extends Socket {
        final BufferedReader input;
        final PrintWriter output;
        final String id;

        Peer(int port, String name, boolean images) throws IOException {
            super("127.0.0.1", port);
            setSoTimeout(7000);
            input = new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
            output = new PrintWriter(getOutputStream(), true, StandardCharsets.UTF_8);
            output.println("@observe:v2");
            if (images) {
                output.println(ImageWire.ENABLE);
                until(ImageWire.READY);
            }
            output.println(ChatProtocol.JOIN + ChatProtocol.encode(name));
            id = until(ChatProtocol.JOINED).substring(ChatProtocol.JOINED.length());
        }

        String until(String prefix) throws IOException {
            String line;
            while ((line = input.readLine()) != null) if (line.startsWith(prefix)) return line;
            throw new EOFException(prefix);
        }

        String begin(String kind, String target, int size) {
            String transfer = UUID.randomUUID().toString();
            output.println(ImageWire.BEGIN + transfer + ":" + id + ":" + kind + ":" + target
                    + ":" + ChatProtocol.encode("照片.png") + ":" + size);
            return transfer;
        }

        void finish(String transfer, byte[] bytes) {
            int sequence = 0;
            for (int offset = 0; offset < bytes.length; offset += ImageChunks.CHUNK_BYTES) {
                output.println(ImageWire.CHUNK + transfer + ":" + sequence++ + ":" + ImageChunks.encode(
                        bytes, offset, Math.min(ImageChunks.CHUNK_BYTES, bytes.length - offset)));
            }
            output.println(ImageWire.END + transfer);
        }

        void expectImage(String kind, String conversation, byte[] original) throws IOException {
            String[] header = until(ImageWire.BEGIN).substring(ImageWire.BEGIN.length()).split(":");
            if (!header[1].equals(kind) || !header[2].equals(conversation)
                    || !ChatProtocol.decode(header[5]).equals("照片.png")) throw new AssertionError("Wrong metadata");
            ImageChunks chunks = new ImageChunks(Integer.parseInt(header[6]));
            String line;
            while ((line = input.readLine()) != null && !line.equals(ImageWire.END + header[0])) {
                if (!line.startsWith(ImageWire.CHUNK + header[0] + ":")) continue;
                String[] parts = line.split(":", 4);
                chunks.append(Integer.parseInt(parts[2]), parts[3]);
            }
            if (line == null || !Arrays.equals(chunks.finish(), original)) throw new AssertionError("Image changed");
        }
    }

    public static void main(String[] args) throws Exception {
        BufferedImage image = new BufferedImage(1150, 1150, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(42);
        for (int y = 0; y < image.getHeight(); y++)
            for (int x = 0; x < image.getWidth(); x++) image.setRGB(x, y, random.nextInt());
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        byte[] png = bytes.toByteArray();

        int port;
        try (ServerSocket reservation = new ServerSocket(0)) { port = reservation.getLocalPort(); }
        try (Server server = new Server(port)) {
            server.bind();
            Thread listener = new Thread(() -> {
                try { server.serve(); } catch (IOException e) { throw new UncheckedIOException(e); }
            });
            listener.start();
            try (Peer alice = new Peer(port, "Alice", true);
                 Peer bob = new Peer(port, "Bob", true);
                 Peer cli = new Peer(port, "CLI", false)) {
                alice.finish(alice.begin("p", bob.id, png.length), png);
                bob.expectImage("p", alice.id, png);
                alice.expectImage("p", bob.id, png);
                alice.until(ImageWire.RESULT);

                alice.output.println(ImageWire.ENABLE_LOBBY);
                alice.until(ImageWire.READY_LOBBY);
                bob.output.println(ImageWire.ENABLE_LOBBY);
                bob.until(ImageWire.READY_LOBBY);
                alice.finish(alice.begin("l", ImageWire.LOBBY_TARGET, png.length), png);
                bob.expectImage("l", ImageWire.LOBBY_TARGET, png);
                alice.expectImage("l", ImageWire.LOBBY_TARGET, png);
                String lobbyNotice = ChatProtocol.decode(cli.until(ImageWire.NOTICE).substring(ImageWire.NOTICE.length()));
                if (!lobbyNotice.contains("Alice in Lobby")) throw new AssertionError(lobbyNotice);
                alice.until(ImageWire.RESULT);


                alice.output.println(ChatProtocol.GROUP_CREATE + ChatProtocol.encode("Team") + ":" + bob.id + "," + cli.id);
                String group = alice.until(ChatProtocol.GROUP_MESSAGE).split(":", 3)[1];
                String transfer = alice.begin("g", group, png.length);
                // A reply to a later command proves the server has read BEGIN.
                alice.output.println(ChatProtocol.GROUP_MEMBERS_REQUEST + group);
                alice.until(ChatProtocol.GROUP_MEMBERS);
                bob.output.println(ChatProtocol.GROUP_LEAVE + group);
                bob.until(ChatProtocol.GROUP_LEFT);
                alice.finish(transfer, png);
                String notice = ChatProtocol.decode(cli.until(ImageWire.NOTICE).substring(ImageWire.NOTICE.length()));
                if (!notice.contains("Alice in Team: 照片.png")) throw new AssertionError(notice);
                alice.expectImage("g", group, png);
                String result = alice.until(ImageWire.RESULT);
                if (!ChatProtocol.decode(result.split(":", 3)[2]).contains("1 skipped")) throw new AssertionError(result);

                bob.output.println(ChatProtocol.SAY + ChatProtocol.encode("barrier"));
                String line;
                do {
                    line = bob.input.readLine();
                    if (line == null || line.startsWith(ImageWire.BEGIN)) throw new AssertionError("Image leaked");
                } while (!line.endsWith("Bob: barrier"));
                transfer = bob.begin("g", group, png.length);
                if (!bob.until(ImageWire.RESULT).startsWith(ImageWire.RESULT + transfer + ":"))
                    throw new AssertionError("Missing permission rejection");

                transfer = alice.begin("p", bob.id, png.length);
                alice.output.println(ImageWire.CHUNK + transfer + ":1:" + ImageChunks.encode(png, 0, 100));
                if (!ChatProtocol.decode(alice.until(ImageWire.RESULT).split(":", 3)[2]).contains("Invalid chunk order"))
                    throw new AssertionError("Out-of-order upload accepted");
                alice.output.println(ChatProtocol.GROUP_ADD + group + ":" + bob.id);
                bob.until(ChatProtocol.GROUP_MESSAGE + group + ":");
                alice.finish(alice.begin("g", group, png.length), png);
                bob.expectImage("g", group, png);
                alice.expectImage("g", group, png);
                alice.until(ImageWire.RESULT);

                try (Peer interrupted = new Peer(port, "Interrupted", true)) {
                    String partial = interrupted.begin("p", bob.id, png.length);
                    interrupted.output.println(ImageWire.CHUNK + partial + ":0:" + ImageChunks.encode(png, 0, 8192));
                }
                bob.output.println(ChatProtocol.SAY + ChatProtocol.encode("after disconnect"));
                do {
                    line = bob.input.readLine();
                    if (line == null || line.startsWith(ImageWire.BEGIN)) throw new AssertionError("Partial image relayed");
                } while (!line.endsWith("Bob: after disconnect"));
                System.out.println("PASS: private and group image bytes, departed member, CLI notice, invalid sequence and disconnect");
            } finally {
                server.close();
                listener.join(4000);
                if (listener.isAlive()) throw new AssertionError("Server did not stop");
            }
        }
    }
}
