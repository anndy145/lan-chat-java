import server.Server;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

/** 驗證 Server 的資料大小上限與首次協定資料期限。 */
public class ServerInputRegression {
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
            Thread thread = new Thread(() -> {
                try { server.serve(); } catch (IOException e) { throw new RuntimeException(e); }
            });
            thread.start();
            try {
                try (Socket socket = new Socket("127.0.0.1", port)) {
                    socket.setSoTimeout(4000);
                    BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                    PrintWriter output = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
                    output.println("@hello:v2:" + encode("Alice"));
                    until(input, "@joined:");
                    output.println("@say:" + encode("😀".repeat(2000)));
                    String delivered;
                    do { delivered = input.readLine(); }
                    while (delivered != null && !delivered.endsWith("😀".repeat(2000)));
                    if (delivered == null) throw new EOFException("Maximum-size message not delivered");
                    output.println("x".repeat(16 * 1024));
                    until(input, "@error:message-length");
                    output.println("x".repeat(16 * 1024 + 1));
                    while (input.readLine() != null) { }
                }
                try (Socket socket = new Socket("127.0.0.1", port)) {
                    socket.setSoTimeout(15000);
                    long start = System.nanoTime();
                    OutputStream output = socket.getOutputStream();
                    for (int i = 0; i < 5; i++) {
                        output.write('@');
                        output.flush();
                        Thread.sleep(1800);
                    }
                    if (socket.getInputStream().read() != -1) throw new AssertionError("Handshake remained open");
                    long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
                    if (elapsed < 9000 || elapsed > 14000) throw new AssertionError("Unexpected timeout: " + elapsed);
                }
                try (Socket observer = new Socket("127.0.0.1", port)) {
                    observer.setSoTimeout(3000);
                    PrintWriter output = new PrintWriter(observer.getOutputStream(), true);
                    BufferedReader input = new BufferedReader(new InputStreamReader(observer.getInputStream(), StandardCharsets.UTF_8));
                    output.println("@observe:v2");
                    until(input, "@users:");
                }
                System.out.println("PASS: line limit, UTF-8 message, total handshake deadline and later connections");
            } finally {
                server.close();
                thread.join(4000);
                if (thread.isAlive()) throw new AssertionError("Listener did not stop");
            }
        }
    }
}