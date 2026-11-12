package client;

import common.ChatProtocol;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/** 開啟連線並接收訊息；Swing 元件更新由呼叫者處理。 */
final class ChatConnection {
    private static final int CONNECT_TIMEOUT_MS = 5487;

    private ChatConnection() {}

    static void receive(Socket socket, String host, int port, boolean observeOnly, String name,
                        Consumer<PrintWriter> connected, Consumer<String> message) throws IOException {
        socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
        BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        PrintWriter output = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
        output.println(ChatProtocol.OBSERVE);
        if (!observeOnly) output.println(ChatProtocol.JOIN + ChatProtocol.encode(name));
        connected.accept(output);
        String line;
        while ((line = input.readLine()) != null) message.accept(line);
    }
}