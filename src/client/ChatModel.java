package client;

import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import server.Server;

/** GUI Client 的連線與使用者狀態，只在 Swing 執行緒讀寫。 */
final class ChatModel {
    final List<OnlineUser> onlineUsers = new ArrayList<>();
    final StringBuilder lobbyHistory = new StringBuilder();
    interface Entry {}
    record Text(String value) implements Entry {}
    final Map<String, List<Entry>> privateHistory = new LinkedHashMap<>();
    final Map<String, List<Entry>> groupHistory = new LinkedHashMap<>();

    boolean addPicture(ChatImages.Picture picture) {
        long retained = java.util.stream.Stream.concat(privateHistory.values().stream(), groupHistory.values().stream())
                .flatMap(List::stream).filter(ChatImages.Picture.class::isInstance)
                .map(ChatImages.Picture.class::cast).mapToLong(item -> item.bytes().length + 240L * 240 * 4).sum();
        if (retained + picture.bytes().length + 240L * 240 * 4 > 32L * 1024 * 1024) return false;
        var history = picture.group() ? groupHistory : privateHistory;
        history.computeIfAbsent(picture.conversation(), key -> new ArrayList<>()).add(picture);
        return true;
    }

    String addLobbyMessage(String text) {
        return lobbyHistory.append(text).append('\n').toString();
    }

    String addPrivateMessage(String id, String text) {
        privateHistory.computeIfAbsent(id, key -> new ArrayList<>()).add(new Text(text));
        return text;
    }

    String addGroupMessage(String id, String text) {
        groupHistory.computeIfAbsent(id, key -> new ArrayList<>()).add(new Text(text));
        return text;
    }

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
