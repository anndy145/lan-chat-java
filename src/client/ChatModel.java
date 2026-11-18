package client;

import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import server.Server;

/** GUI Client 的連線與使用者狀態，只在 Swing 執行緒讀寫。 */
final class ChatModel {
    final List<OnlineUser> onlineUsers = new ArrayList<>();

    static final class OnlineUser {
        final String id;
        final String name;

        OnlineUser(String id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof OnlineUser && id.equals(((OnlineUser) other).id);
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }
    }

    String activeName;
    String activeId;
    Socket socket;
    PrintWriter output;
    boolean connecting;
    boolean observing;
    boolean joining;
    long connectionGeneration;
    Server temporaryServer;
    boolean startingServer;
    long serverGeneration;
}
