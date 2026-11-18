import server.Server;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

/** 模擬收件者停止讀取，確認其他連線仍可使用。 */
public class SlowClientRegression {
    private static String encode(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    private static String until(BufferedReader input, String prefix) throws IOException {
        String line;
        while ((line = input.readLine()) != null) if (line.startsWith(prefix)) return line;
        throw new EOFException("Missing " + prefix);
    }

    public static void main(String[] args) throws Exception {
        int port;
        try (ServerSocket reserved = new ServerSocket(0)) { port = reserved.getLocalPort(); }
        try (Server server = new Server(port)) {
            server.bind();
            Thread listener = new Thread(() -> {
                try { server.serve(); } catch (IOException e) { throw new RuntimeException(e); }
            });
            listener.start();
            try (Socket slow = new Socket(); Socket healthy = new Socket("127.0.0.1", port)) {
                slow.setReceiveBufferSize(1024);
                slow.connect(new InetSocketAddress("127.0.0.1", port));
                slow.setSoTimeout(5000);
                BufferedReader slowInput = new BufferedReader(new InputStreamReader(slow.getInputStream(), StandardCharsets.UTF_8));
                PrintWriter slowOutput = new PrintWriter(new OutputStreamWriter(slow.getOutputStream(), StandardCharsets.UTF_8), true);
                slowOutput.println("@hello:v2:" + encode("Slow"));
                String slowId = until(slowInput, "@joined:").substring(8);

                healthy.setSoTimeout(5000);
                BufferedReader input = new BufferedReader(new InputStreamReader(healthy.getInputStream(), StandardCharsets.UTF_8));
                PrintWriter output = new PrintWriter(new OutputStreamWriter(healthy.getOutputStream(), StandardCharsets.UTF_8), true);
                output.println("@hello:v2:" + encode("Healthy"));
                until(input, "@joined:");

                String payload = encode("字".repeat(2000));
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
                boolean disconnected = false;
                for (int i = 0; i < 5000 && System.nanoTime() < deadline; i++) {
                    output.println("@private:" + slowId + ":" + payload);
                    String reply = until(input, "@pm:" + slowId + ":");
                    String body = new String(Base64.getDecoder().decode(reply.split(":", 4)[3]), StandardCharsets.UTF_8);
                    if (body.contains("no longer online")) {
                        disconnected = true;
                        break;
                    }
                    if (!body.contains("You: ")) throw new AssertionError("Missing private echo");
                }
                if (!disconnected) throw new AssertionError("Slow client was not disconnected");

                output.println("@say:" + encode("still working"));
                String line;
                do { line = input.readLine(); } while (line != null && !line.contains("Healthy: still working"));
                if (line == null) throw new AssertionError("Healthy client stopped receiving");
                for (int i = 0; i < 20; i++) output.println("@say:" + encode("order-" + i));
                for (int i = 0; i < 20; i++) {
                    String ordered = input.readLine();
                    if (ordered == null || !ordered.endsWith("Healthy: order-" + i))
                        throw new AssertionError("Messages out of order: " + ordered);
                }
                String slowWriter = "client-writer-" + slow.getLocalSocketAddress();
                String healthyWriter = "client-writer-" + healthy.getLocalSocketAddress();
                server.close();
                long stopDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
                boolean running;
                do {
                    running = Thread.getAllStackTraces().keySet().stream()
                            .anyMatch(t -> t.isAlive() && (t.getName().equals(slowWriter)
                                    || t.getName().equals(healthyWriter)));
                    if (running) Thread.sleep(20);
                } while (running && System.nanoTime() < stopDeadline);
                if (running) throw new AssertionError("Client writer did not stop");
                System.out.println("PASS: slow client disconnected; healthy client remained responsive; ordering and shutdown");
            } finally {
                server.close();
                listener.join(4000);
                if (listener.isAlive()) throw new AssertionError("Server did not stop");
            }
        }
    }
}