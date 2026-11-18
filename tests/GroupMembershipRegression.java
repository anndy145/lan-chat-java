import server.Server;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** 驗證群組名單、主動退出與斷線通知。 */
public class GroupMembershipRegression {
    private static String encode(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String text) {
        return new String(Base64.getDecoder().decode(text), StandardCharsets.UTF_8);
    }

    private static final class Peer implements AutoCloseable {
        final Socket socket;
        final BufferedReader input;
        final PrintWriter output;

        Peer(int port, String name) throws IOException {
            socket = new Socket("127.0.0.1", port);
            socket.setSoTimeout(6000);
            input = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            output = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
            output.println("@hello:v2:" + encode(name));
        }

        String until(String prefix) throws IOException {
            String line;
            while ((line = input.readLine()) != null) if (line.startsWith(prefix)) return line;
            throw new EOFException("Missing " + prefix);
        }

        @Override public void close() throws IOException { socket.close(); }
    }

    private static String groupText(String line) {
        return decode(line.split(":", 4)[3]);
    }

    public static void main(String[] args) throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) { port = reservation.getLocalPort(); }
        try (Server server = new Server(port)) {
            server.bind();
            Thread listener = new Thread(() -> {
                try { server.serve(); } catch (IOException e) { throw new RuntimeException(e); }
            });
            listener.start();
            try (Peer alice = new Peer(port, "Alice"); Peer bob = new Peer(port, "Bob");
                 Peer charlie = new Peer(port, "Charlie")) {
                alice.until("@joined:");
                String bobId = bob.until("@joined:").substring(8);
                charlie.until("@joined:");
                alice.output.println("@group-create:" + encode("Team") + ":" + bobId);
                String group = alice.until("@group:").split(":", 3)[1];
                bob.until("@group:" + group + ":");

                String art = "\u2800\u28FF\u28F7\n\u2800\u2811\u2800";
                String windowsArt = art.replace("\n", "\r\n");
                alice.output.println("@say:" + encode(windowsArt));
                if (!decode(bob.until("@notice:").substring(8)).endsWith("Alice: " + art))
                    throw new AssertionError("Lobby message lost multiline layout");

                alice.output.println("@private:" + bobId + ":" + encode(windowsArt));
                if (!groupText(bob.until("@pm:")).endsWith("Alice: " + art))
                    throw new AssertionError("Private message lost multiline layout");

                alice.output.println("@group-send:" + group + ":" + encode(windowsArt));
                if (!groupText(bob.until("@group:" + group + ":")).endsWith("Alice: " + art)
                        || !groupText(alice.until("@group:" + group + ":")).endsWith("Alice: " + art))
                    throw new AssertionError("Group message lost multiline layout");


                alice.output.println("@group-members-request:" + group);
                String[] list = alice.until("@group-members:" + group + ":").split(":", 4);
                if (!decode(list[2]).equals("Team") || !list[3].equals(encode("Alice") + "," + encode("Bob")))
                    throw new AssertionError("Incorrect members");

                charlie.output.println("@group-members-request:" + group);
                if (!decode(charlie.until("@group-error:").substring(13)).contains("no longer a member"))
                    throw new AssertionError("Non-member accessed member list");

                bob.output.println("@group-leave:" + group);
                bob.until("@group-left:" + group);
                if (!groupText(alice.until("@group:" + group + ":")).contains("Bob left the group"))
                    throw new AssertionError("Missing leave notification");
                bob.output.println("@group-send:" + group + ":" + encode("not allowed"));
                if (!decode(bob.until("@notice:").substring(8)).contains("no longer a member"))
                    throw new AssertionError("Former member could send");

                alice.output.println("@group-send:" + group + ":" + encode("private group message"));
                alice.output.println("@say:" + encode("barrier"));
                String observed;
                do {
                    observed = bob.input.readLine();
                    if (observed == null) throw new EOFException("Bob disconnected");
                    if (observed.startsWith("@group:" + group + ":"))
                        throw new AssertionError("Former member received a group message");
                } while (!observed.contains("Alice: barrier"));

                alice.output.println("@group-members-request:" + group);
                String remaining = alice.until("@group-members:" + group + ":").split(":", 4)[3];
                if (!remaining.equals(encode("Alice"))) throw new AssertionError("Left member still listed");

                alice.output.println("@group-add:" + group + ":" + bobId);
                alice.until("@group:" + group + ":");
                bob.until("@group:" + group + ":");
                bob.close();
                if (!groupText(alice.until("@group:" + group + ":")).contains("Bob left the group"))
                    throw new AssertionError("Missing disconnect notification");

                alice.output.println("@group-leave:" + group);
                alice.until("@group-left:" + group);
                alice.output.println("@group-members-request:" + group);
                alice.until("@group-error:");
                System.out.println("PASS: group member access, leave, rejoin and disconnect");
            } finally {
                server.close();
                listener.join(4000);
                if (listener.isAlive()) throw new AssertionError("Listener did not stop");
            }
        }
    }
}