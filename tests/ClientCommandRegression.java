import server.Server;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.*;

/** 使用真正的命令列 Client 驗證指令分流與私訊。 */
public class ClientCommandRegression {
    private static final BlockingQueue<String> lines = new LinkedBlockingQueue<>();

    private static void expect(String text) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(6);
        while (true) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) throw new AssertionError("Missing output: " + text);
            String line = lines.poll(remaining, TimeUnit.NANOSECONDS);
            if (line == null) throw new AssertionError("Missing output: " + text);
            if (line.contains(text)) return;
        }
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
            try (Socket peer = new Socket("127.0.0.1", port)) {
                peer.setSoTimeout(6000);
                BufferedReader input = new BufferedReader(new InputStreamReader(peer.getInputStream(), StandardCharsets.UTF_8));
                PrintWriter output = new PrintWriter(new OutputStreamWriter(peer.getOutputStream(), StandardCharsets.UTF_8), true);
                output.println("@hello:v2:" + Base64.getEncoder().encodeToString("Andy Chen".getBytes(StandardCharsets.UTF_8)));
                while (!input.readLine().startsWith("@joined:")) {}

                Process client = new ProcessBuilder(System.getProperty("java.home") + "/bin/java",
                        "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8",
                        "-cp", System.getProperty("java.class.path"),
                        "client.Client", "127.0.0.1", Integer.toString(port), "--observe")
                        .redirectErrorStream(true).start();
                Thread reader = new Thread(() -> {
                    try (BufferedReader stdout = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = stdout.readLine()) != null) lines.offer(line);
                    } catch (IOException ignored) {}
                });
                reader.setDaemon(true);
                reader.start();
                try (PrintWriter commands = new PrintWriter(new OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8), true)) {
                    expect("Observing online users");
                    expect("線上使用者：");
                    commands.println("/users"); expect("Andy Chen");
                    commands.println("/help"); expect("Commands:"); expect("Other text");
                    commands.println("/join Alice"); expect("Joined chat"); expect("Alice, Andy Chen");
                    commands.println("/msg\t\"Andy Chen\" hello world");
                    String line;
                    do { line = input.readLine(); } while (line != null && !line.startsWith("@pm:"));
                    if (line == null) throw new AssertionError("Private message not received");
                    String text = new String(Base64.getDecoder().decode(line.split(":", 4)[3]), StandardCharsets.UTF_8);
                    if (!text.endsWith("Alice: hello world")) throw new AssertionError("Wrong message: " + text);
                    expect("You: hello world");
                    commands.println("/msgBob secret"); expect("Unknown command");
                    commands.println("/group"); expect("Usage: /group");
                    commands.println("//hello");
                    do { line = input.readLine(); } while (line != null && !line.contains("Alice: /hello"));
                    if (line == null) throw new AssertionError("Escaped public message missing");
                    commands.println("barrier");
                    do {
                        line = input.readLine();
                        if (line == null) throw new AssertionError("Peer disconnected");
                        if (line.contains("/msgBob") || line.contains("Alice: /group"))
                            throw new AssertionError("A malformed command reached the lobby");
                    } while (!line.contains("Alice: barrier"));
                    commands.println("/leave"); expect("Still observing");
                    commands.println("/quit");
                    if (!client.waitFor(6, TimeUnit.SECONDS) || client.exitValue() != 0)
                        throw new AssertionError("Client did not exit cleanly");
                    System.out.println("PASS: command parsing, private messages, observer and lobby isolation");
                } finally {
                    client.destroyForcibly();
                }
            } finally {
                server.close();
                listener.join(4000);
            }
        }
    }
}