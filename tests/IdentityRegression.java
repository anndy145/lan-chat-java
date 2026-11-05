import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import server.Server;

/** 使用本機 TCP 連線驗證暱稱重用時的私訊對象。 */
public class IdentityRegression {
    private static String encode(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    private static String messageText(String line) {
        return new String(Base64.getDecoder().decode(line.split(":", 4)[3]), StandardCharsets.UTF_8);
    }

    private static final class Peer implements AutoCloseable {
        final Socket socket;
        final BufferedReader input;
        final PrintWriter output;

        Peer(int port, String name) throws IOException {
            socket = new Socket("127.0.0.1", port);
            socket.setSoTimeout(4000);
            input = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            output = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
            output.println("@hello:v2:" + encode(name));
        }

        String until(String prefix) throws IOException {
            String line;
            while ((line = input.readLine()) != null) {
                if (line.startsWith(prefix)) return line;
            }
            throw new IOException("Connection closed before " + prefix);
        }

        @Override public void close() throws IOException { socket.close(); }
    }

    public static void main(String[] args) throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) { port = reservation.getLocalPort(); }
        try (Server server = new Server(port)) {
            server.bind();
            Thread listener = new Thread(() -> {
                try { server.serve(); } catch (IOException exception) { throw new RuntimeException(exception); }
            });
            listener.start();
            try (Peer alice = new Peer(port, "Alice"); Peer bob = new Peer(port, "Bob")) {
                String aliceId = alice.until("@joined:").substring(8);
                String oldBob = bob.until("@joined:").substring(8);
                while (!alice.until("@users:").contains(oldBob)) { }

                alice.output.println("@private:" + oldBob + ":" + encode("first"));
                if (!messageText(bob.until("@pm:" + aliceId + ":")).endsWith("Alice: first")) {
                    throw new AssertionError("Private message did not reach original Bob");
                }
                alice.until("@pm:" + oldBob + ":");

                bob.output.println("@leave");
                while (alice.until("@users:").contains(oldBob)) { }
                bob.output.println("@join:" + encode("Bob"));
                String newBob = bob.until("@joined:").substring(8);
                if (oldBob.equals(newBob)) throw new AssertionError("ID reused after leave");
                while (!alice.until("@users:").contains(newBob)) { }
                alice.output.println("@private:" + oldBob + ":" + encode("must-not-arrive"));
                if (!messageText(alice.until("@pm:" + oldBob + ":")).contains("no longer online")) {
                    throw new AssertionError("Missing stale-recipient error");
                }
                alice.output.println("@say:" + encode("barrier"));
                String line;
                do {
                    line = bob.input.readLine();
                    if (line == null) throw new IOException("Bob disconnected");
                    if (line.startsWith("@pm:")) throw new AssertionError("Stale private message reached Bob");
                } while (!line.contains("Alice: barrier"));

                alice.output.println("@private:" + newBob + ":" + encode("second"));
                if (!messageText(bob.until("@pm:" + aliceId + ":")).endsWith("Alice: second")) {
                    throw new AssertionError("New private message did not reach Bob");
                }
                alice.until("@pm:" + newBob + ":");
                alice.output.println("@group-create:" + encode("Team") + ":" + newBob);
                String groupId = alice.until("@group:").split(":", 3)[1];
                bob.until("@group:" + groupId + ":");
                alice.output.println("@group-send:" + groupId + ":" + encode("group-test"));
                bob.until("@group:" + groupId + ":");

                bob.output.println("@leave");
                while (alice.until("@users:").contains(newBob)) { }
                try (Peer replacement = new Peer(port, "Bob")) {
                    String replacementId = replacement.until("@joined:").substring(8);
                    if (newBob.equals(replacementId)) throw new AssertionError("ID reused by another connection");
                    alice.output.println("@private:" + newBob + ":" + encode("wrong-recipient"));
                    if (!messageText(alice.until("@pm:" + newBob + ":")).contains("no longer online")) {
                        throw new AssertionError("Missing stale-recipient error");
                    }
                    alice.output.println("@say:" + encode("replacement-barrier"));
                    do {
                        line = replacement.input.readLine();
                        if (line == null) throw new IOException("Replacement disconnected");
                        if (line.startsWith("@pm:")) throw new AssertionError("Private message reached replacement");
                    } while (!line.contains("replacement-barrier"));
                    alice.output.println("@private:" + replacementId + ":" + encode("new-recipient"));
                    replacement.until("@pm:" + aliceId + ":");
                }

                try (Socket old = new Socket("127.0.0.1", port)) {
                    old.setSoTimeout(4000);
                    PrintWriter oldOut = new PrintWriter(old.getOutputStream(), true);
                    BufferedReader oldIn = new BufferedReader(new InputStreamReader(old.getInputStream(), StandardCharsets.UTF_8));
                    oldOut.println("@observe");
                    if (!"@error:version".equals(oldIn.readLine())) throw new AssertionError("Old protocol accepted");
                }
                System.out.println("PASS: rejoin and replacement IDs, private isolation, group routing, legacy rejection");
            } finally {
                server.close();
                listener.join(4000);
                if (listener.isAlive()) throw new AssertionError("Listener did not stop");
            }
        }
    }
}
