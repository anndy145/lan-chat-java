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
    final List<Entry> lobbyEntries = new ArrayList<>();
    final Map<String, List<Entry>> groupHistory = new LinkedHashMap<>();

    private <T extends Entry> long retainedBytes(Class<T> type, java.util.function.ToLongFunction<T> sizeOf) {
        return java.util.stream.Stream.concat(java.util.stream.Stream.of(lobbyEntries),
                java.util.stream.Stream.concat(privateHistory.values().stream(), groupHistory.values().stream()))
                .flatMap(List::stream).filter(type::isInstance).map(type::cast).mapToLong(sizeOf).sum();
    }

    private void addEntry(common.ImageWire.Destination destination, String conversation, Entry entry) {
        switch (destination) {
            case LOBBY -> lobbyEntries.add(entry);
            case GROUP -> groupHistory.computeIfAbsent(conversation, key -> new ArrayList<>()).add(entry);
            case PRIVATE -> privateHistory.computeIfAbsent(conversation, key -> new ArrayList<>()).add(entry);
        }
    }

    boolean addPicture(ChatImages.Picture picture) {
        long size = picture.bytes().length + 240L * 240 * 4;
        if (retainedBytes(ChatImages.Picture.class, item -> item.bytes().length + 240L * 240 * 4) + size > 32L * 1024 * 1024) {
            return false;
        }
        addEntry(picture.destination(), picture.conversation(), picture);
        return true;
    }

    // 收到的檔案保留在記憶體供另存，總量上限避免長時間使用耗盡記憶體。
    boolean addAttachment(ChatFiles.Attachment attachment) {
        if (retainedBytes(ChatFiles.Attachment.class, item -> item.bytes().length) + attachment.bytes().length > 128L * 1024 * 1024) {
            return false;
        }
        addEntry(attachment.destination(), attachment.conversation(), attachment);
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
