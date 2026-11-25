package client;

import common.ChatProtocol;
import common.FileWire;
import common.ImageFiles;
import common.TransferWire;
import java.nio.file.Files;
import java.nio.file.Path;
import common.ImageWire;
import client.ChatModel.OnlineUser;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.swing.SwingUtilities;
import server.Server;

/** 處理使用者操作與連線事件。所有狀態與介面更新都在 Swing 執行緒執行。 */
final class ChatController {
    private final java.util.ArrayDeque<Outgoing> outgoing = new java.util.ArrayDeque<>();
    private String uploadId;
    private javax.swing.Timer uploadTimer;
    private final ChatWindow view;
    private final ChatModel model;

    ChatController(ChatWindow view, ChatModel model) {
        this.view = view;
        this.model = model;
    }

    void connectAction() {
        if (model.observing && model.output != null) {
            String name = view.name();
            if (!validateName(name, false)) return;
            model.output.println(ChatProtocol.JOIN + ChatProtocol.encode(name));
            model.joining = true;
            view.setConnected(false);
        } else connect(false);
    }

    private boolean validateName(String name, boolean focusOnError) {
        String error = null;
        if (name.isEmpty()) {
            error = "Please enter a name.";
        } else if (name.codePointCount(0, name.length()) > ChatProtocol.MAX_NAME_LENGTH) {
            error = "Name must be at most 32 characters.";
        }
        if (error == null) return true;
        view.showMessage(error);
        if (focusOnError) view.focusName();
        return false;
    }

    void viewUsersAction() {
        if (model.observing) {
            closeConnection();
            appendMessage("Disconnected.");
        } else connect(true);
    }

    void hostAction() {
        if (model.temporaryServer != null) stopTemporaryServer();
        else startTemporaryServer();
    }

    void startTemporaryServer() {
        int port;
        try {
            port = Integer.parseInt(view.port());
            if (port < 1 || port > 65535)
                throw new NumberFormatException();
        } catch (NumberFormatException exception) {
            view.showMessage("Port must be a number from 1 to 65535.");
            return;
        }
        model.startingServer = true;
        long generation = ++model.serverGeneration;
        view.setConnected(false);
        Thread worker = new Thread(() -> {
            Server server = new Server(port);
            try {
                server.bind();
                String addresses = Server.lanAddresses();
                SwingUtilities.invokeLater(() -> {
                    if (!view.isDisplayable() || generation != model.serverGeneration) {
                        server.close();
                        return;
                    }
                    model.temporaryServer = server;
                    model.startingServer = false;
                    view.useLocalHost();
                    view.setConnected(false);
                    appendMessage("Temp server started on port " + port + ". LAN IP: " + addresses);
                });
                server.serve();
                SwingUtilities.invokeLater(() -> {
                    if (generation == model.serverGeneration && model.temporaryServer == server) {
                        model.temporaryServer = null;
                        view.setConnected(model.socket != null && !model.observing);
                        appendMessage("Temp server stopped.");
                    }
                });
            } catch (IOException exception) {
                SwingUtilities.invokeLater(() -> {
                    if (generation != model.serverGeneration || !view.isDisplayable())
                        return;
                    model.temporaryServer = null;
                    model.startingServer = false;
                    view.setConnected(model.socket != null && !model.observing);
                    appendMessage("Temp server failed: " + exception.getMessage());
                });
            } finally {
                server.close();
            }
        }, "temporary-server");
        worker.setDaemon(true);
        worker.start();
    }

    void stopTemporaryServer() {
        model.serverGeneration++;
        model.startingServer = false;
        Server server = model.temporaryServer;
        model.temporaryServer = null;
        if (server != null) {
            server.close();
            appendMessage("Temp server stopped.");
        }
        view.setConnected(model.socket != null && !model.observing);
    }

    void connect(boolean observeOnly) {
        String host = view.host();
        String name = view.name();
        if (host.isEmpty()) {
            view.showMessage("Please enter a server IP or hostname.");
            view.focusHost();
            return;
        }
        int port;
        try {
            port = Integer.parseInt(view.port());
        } catch (NumberFormatException exception) {
            view.showMessage("Port must be a number from 1 to 65535.");
            view.focusPort();
            return;
        }
        if (port < 1 || port > 65535) {
            view.showMessage("Port must be between 1 and 65535.");
            view.focusPort();
            return;
        }
        if (!observeOnly && !validateName(name, true)) return;

        model.connecting = true;
        clearConversationHistory();
        model.activeName = null;
        model.activeId = null;
        long generation = ++model.connectionGeneration;
        view.setConnected(false);
        appendMessage("Connecting...");

        // 網路連線可能需要等待，放在背景執行緒以免視窗卡住。
        Thread connectionThread = new Thread(() -> {
            Socket newSocket = new Socket();
            ChatImages images = new ChatImages();
            ChatFiles files = new ChatFiles();
            try {
                ChatConnection.receive(newSocket, host, port, observeOnly, name, writer -> {
                // Swing 元件只能在介面執行緒更新。
                SwingUtilities.invokeLater(() -> {
                    if (!view.isDisplayable() || generation != model.connectionGeneration) {
                        closeSocket(newSocket);
                        return;
                    }
                    model.socket = newSocket;
                    model.output = writer;
                    model.connecting = false;
                    model.observing = true;
                    model.joining = !observeOnly;
                    view.setConnected(false);
                    appendMessage(observeOnly ? "Connected. You can view online users." : "Joining chat...");
                });
                }, received -> {
                    if (ChatFiles.isData(received)) {
                        receiveTransfer(newSocket, () -> files.receive(received),
                                attachment -> model.addAttachment(attachment),
                                view::showAttachment,
                                "File history is full. Restart the client to receive more files.");
                        return;
                    }
                    if (ChatImages.isData(received)) {
                        receiveTransfer(newSocket, () -> images.receive(received),
                                picture -> model.addPicture(picture),
                                view::showPicture,
                                "Image history is full. Restart the client to clear it.");
                        return;
                    }
                    SwingUtilities.invokeLater(() -> {
                        if (model.socket == newSocket) handleServerLine(received);
                    });
                });
                SwingUtilities.invokeLater(() -> {
                    if (generation == model.connectionGeneration)
                        disconnected(newSocket, "Disconnected from the server.");
                });
            } catch (IOException exception) {
                SwingUtilities.invokeLater(() -> {
                    if (generation == model.connectionGeneration)
                        disconnected(newSocket, "Connection closed or failed: " + exception.getMessage());
                });
            } finally {
                closeSocket(newSocket);
            }
        }, "chat-connection");
        connectionThread.setDaemon(true);
        connectionThread.start();
    }

    // 傳送佇列：圖片按鈕、檔案按鈕、拖曳與貼上都依序上傳，目標在加入佇列時決定。
    private enum Mode { IMAGE, FILE, AUTO }

    private record Target(ImageWire.Destination destination, String id, String title, boolean images, boolean files) {}

    private record Outgoing(Path path, java.awt.Image pasted, String name, Mode mode, Target target) {}

    private record Payload(TransferWire wire, byte[] bytes, String name) {}

    private static final int MAX_QUEUED_UPLOADS = 20;

    private Target currentTarget() {
        String group = view.selectedGroupId();
        boolean lobby = view.isLobbySelected();
        var destination = lobby ? ImageWire.Destination.LOBBY
                : group != null ? ImageWire.Destination.GROUP : ImageWire.Destination.PRIVATE;
        String target = lobby ? ImageWire.LOBBY_TARGET : group == null ? view.selectedPrivateId() : group;
        if (target == null || model.output == null || model.activeId == null || model.observing) return null;
        return new Target(destination, target, view.selectedChatTitle(), view.imagesAllowedHere(), view.filesAllowed());
    }

    void sendPicture() {
        Target target = currentTarget();
        if (target == null || !target.images()) return;
        Path file = view.choosePicture();
        if (file != null) enqueue(List.of(new Outgoing(file, null, file.getFileName().toString(), Mode.IMAGE, target)));
    }

    void sendFile() {
        Target target = currentTarget();
        if (target == null || !target.files()) return;
        List<Path> files = view.chooseFiles();
        enqueue(files.stream().map(path -> new Outgoing(path, null, path.getFileName().toString(), Mode.FILE, target))
                .toList());
    }

    /** 拖曳或貼上的檔案：PNG/JPEG 以圖片顯示，其他檔案以附件傳送。 */
    void sendDroppedFiles(List<Path> paths) {
        Target target = currentTarget();
        if (target == null || paths.isEmpty()) return;
        List<Path> regular = paths.stream().filter(Files::isRegularFile).toList();
        if (regular.size() != paths.size()) appendMessage("Folders cannot be sent. Select files instead.");
        if (regular.isEmpty()) return;
        if (!target.files() && regular.stream().anyMatch(path -> !ChatImages.hasImageName(path.getFileName().toString()))) {
            view.showMessage("This server only supports images. Update the server to send other files.");
            return;
        }
        if (!view.confirmFiles(regular, target.title())) return;
        if (!target.equals(currentTarget())) return;
        enqueue(regular.stream().map(path -> new Outgoing(path, null, path.getFileName().toString(), Mode.AUTO, target))
                .toList());
    }

    void sendPastedImage(java.awt.Image image) {
        Target target = currentTarget();
        if (target == null || !target.images()) return;
        if (!view.confirmPastedImage(image, target.title())) return;
        if (!target.equals(currentTarget())) return;
        String name = "pasted-image-" + java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        enqueue(List.of(new Outgoing(null, image, name, Mode.IMAGE, target)));
    }

    private void enqueue(List<Outgoing> items) {
        if (items.isEmpty()) return;
        if (outgoing.size() + items.size() > MAX_QUEUED_UPLOADS) {
            view.showMessage("Too many files are waiting. Send at most " + MAX_QUEUED_UPLOADS + " at a time.");
            return;
        }
        outgoing.addAll(items);
        startNextUpload();
    }

    private void startNextUpload() {
        if (uploadId != null) return;
        Outgoing item = outgoing.poll();
        var writer = model.output;
        String sender = model.activeId;
        if (item == null || writer == null || sender == null) {
            outgoing.clear();
            view.setTransferStatus(null);
            return;
        }
        String id = java.util.UUID.randomUUID().toString();
        uploadId = id;
        view.setTransferStatus("Preparing " + item.name() + "..." + queuedSuffix());
        uploadTimer = new javax.swing.Timer(250_000, event -> finishUpload(id, item.name() + ": transfer timed out."));
        uploadTimer.setRepeats(false);
        uploadTimer.start();
        Thread.ofVirtual().start(() -> {
            try {
                Payload payload = prepare(item);
                int[] shown = {-1};
                ChatTransfers.send(writer, payload.wire(), payload.bytes(), payload.name(), id, sender,
                        item.target().destination(), item.target().id(), percent -> {
                            // 每 5% 更新一次，避免大量排入 Swing 事件。
                            if (percent / 5 == shown[0]) return;
                            shown[0] = percent / 5;
                            SwingUtilities.invokeLater(() -> {
                                if (id.equals(uploadId)) view.setTransferStatus("Sending " + payload.name()
                                        + " (" + ChatFiles.size(payload.bytes().length) + ") " + percent + "%" + queuedSuffix());
                            });
                        });
                SwingUtilities.invokeLater(() -> {
                    if (id.equals(uploadId)) view.setTransferStatus("Delivering " + payload.name() + "..." + queuedSuffix());
                });
            } catch (IOException exception) {
                SwingUtilities.invokeLater(() -> finishUpload(id, item.name() + ": " + exception.getMessage()));
            }
        });
    }

    private String queuedSuffix() {
        return outgoing.isEmpty() ? "" : "  (" + outgoing.size() + " more waiting)";
    }

    // 在背景執行緒讀取並決定傳送方式，避免大型檔案讓視窗停頓。
    private static Payload prepare(Outgoing item) throws IOException {
        Target target = item.target();
        if (item.pasted() != null) {
            if (!target.images()) throw new IOException("Images are not supported here.");
            ImageFiles.Encoded encoded = ImageFiles.encodeForUpload(item.pasted());
            ImageFiles.validate(encoded.bytes());
            return new Payload(TransferWire.IMAGE, encoded.bytes(), item.name() + "." + encoded.extension());
        }
        if (item.mode() == Mode.IMAGE) {
            if (!target.images()) throw new IOException("Images are not supported here.");
            return new Payload(TransferWire.IMAGE, ImageFiles.read(item.path()), item.name());
        }
        IOException imageError = null;
        if (item.mode() == Mode.AUTO && target.images() && ChatImages.hasImageName(item.name())) {
            try {
                return new Payload(TransferWire.IMAGE, ImageFiles.read(item.path()), item.name());
            } catch (IOException exception) {
                // 無法當作圖片時改用一般檔案傳送，例如超過圖片上限的大型照片。
                imageError = exception;
            }
        }
        if (!target.files()) {
            throw imageError != null ? imageError
                    : new IOException("This server only supports images. Update the server to send other files.");
        }
        return new Payload(TransferWire.FILE, ChatFiles.read(item.path()), item.name());
    }

    private void showTransferResult(String line) {
        String prefix = line.startsWith(FileWire.RESULT) ? FileWire.RESULT : ImageWire.RESULT;
        String[] result = line.substring(prefix.length()).split(":", 2);
        if (result.length == 2) finishUpload(result[0], ChatProtocol.decode(result[1]));
    }

    private void finishUpload(String id, String message) {
        if (!id.equals(uploadId)) return;
        uploadId = null;
        if (uploadTimer != null) uploadTimer.stop();
        uploadTimer = null;
        if (!message.startsWith("Image queued for ") && !message.startsWith("File queued for ")) appendMessage(message);
        startNextUpload();
    }

    private void resetUploads() {
        outgoing.clear();
        uploadId = null;
        if (uploadTimer != null) uploadTimer.stop();
        uploadTimer = null;
        view.setTransferStatus(null);
    }

    void sendMessage() {
        String message = view.message();
        if (model.output == null || message.isBlank()) {
            return;
        }
        if (message.codePointCount(0, message.length()) > ChatProtocol.MAX_MESSAGE_LENGTH) {
            view.showMessage("Message must be at most 2000 characters.");
            return;
        }
        // 伺服器會把訊息廣播回來，因此此處不重複加到聊天區。
        view.clearInput();
        if ("/quit".equalsIgnoreCase(message.strip())) {
            leaveChat();
        } else if (view.isLobbySelected()) {
            model.output.println(ChatProtocol.SAY + ChatProtocol.encode(message));
        } else if (view.selectedGroupId() != null) {
            model.output.println(ChatProtocol.GROUP_SEND + view.selectedGroupId() + ":" + ChatProtocol.encode(message));
        } else {
            String targetId = view.selectedPrivateId();
            if (targetId != null) model.output.println(ChatProtocol.PRIVATE + targetId + ":" + ChatProtocol.encode(message));
        }
    }

    void openSelectedChat() {
        List<OnlineUser> selected = view.selectedUsers();
        if (selected.size() != 1) {
            view.showMessage("Select exactly one online user.");
            return;
        }
        openPrivateChat(selected.get(0));
    }

    void openPrivateChat(OnlineUser target) {
        if (target.id.equals(model.activeId)) {
            view.showMessage("Select another user.");
            return;
        }
        model.privateHistory.computeIfAbsent(target.id, key -> new ArrayList<>());
        view.selectPrivateChat(target);
    }

    void createGroup() {
        List<String> members = view.selectedUsers().stream()
                .filter(user -> !user.id.equals(model.activeId)).map(user -> user.id).collect(Collectors.toList());
        if (members.isEmpty()) {
            view.showMessage("Select at least one other online user. Hold Ctrl to select more.");
            return;
        }
        String name = view.askGroupName();
        if (name == null)
            return;
        name = name.strip();
        if (name.isEmpty() || name.length() > 80 || name.contains("\n") || name.contains("\r")) {
            view.showMessage("Enter a group name of up to 80 characters.");
            return;
        }
        model.output.println(ChatProtocol.GROUP_CREATE + ChatProtocol.encode(name) + ":"
                + String.join(",", members));
    }

    void addGroupMembers() {
        String groupId = view.selectedGroupId();
        if (groupId == null) {
            view.showMessage("Select a group tab first.");
            return;
        }
        List<String> members = view.selectedUsers().stream()
                .filter(user -> !user.id.equals(model.activeId)).map(user -> user.id).collect(Collectors.toList());
        if (members.isEmpty()) {
            view.showMessage("Select at least one other online user. Hold Ctrl to select more.");
            return;
        }
        model.output.println(ChatProtocol.GROUP_ADD + groupId + ":" + String.join(",", members));
    }

    private String selectedGroupId() {
        return view.selectedGroupId();
    }

    void showGroupMembers() {
        String id = selectedGroupId();
        if (id == null) {
            view.showMessage("Select a group tab first.");
        } else if (model.output != null) {
            model.output.println(ChatProtocol.GROUP_MEMBERS_REQUEST + id);
        }
    }

    void leaveSelectedGroup() {
        String id = selectedGroupId();
        if (id == null) {
            view.showMessage("Select a group tab first.");
        } else if (model.output != null) {
            model.output.println(ChatProtocol.GROUP_LEAVE + id);
        }
    }

    private void receiveGroupMembers(String message) {
        String[] parts = message.substring(ChatProtocol.GROUP_MEMBERS.length()).split(":", 3);
        if (parts.length != 3 || !view.hasGroup(parts[0])) return;
        try {
            String title = ChatProtocol.decode(parts[1]);
            List<String> names = new java.util.ArrayList<>();
            if (!parts[2].isEmpty()) {
                for (String encoded : parts[2].split(",")) names.add(ChatProtocol.decode(encoded));
            }
            view.showMessage(String.join("\n", names),
                    "Group members: " + title);
        } catch (IllegalArgumentException exception) {
            appendMessage("Received an invalid group member list.");
        }
    }

    private void appendMessage(String message) {
        view.appendMessage(model.addLobbyMessage(message));
    }

    private void receiveGroup(String message) {
        String[] parts = message.substring(ChatProtocol.GROUP_MESSAGE.length()).split(":", 3);
        if (parts.length != 3) return;
        try {
            String name = ChatProtocol.decode(parts[1]);
            String text = ChatProtocol.decode(parts[2]);
            view.showGroupMessage(parts[0], name, model.addGroupMessage(parts[0], text));
        } catch (IllegalArgumentException exception) {
            appendMessage("Received an invalid group message.");
        }
    }

    private void receivePrivate(String message) {
        String[] parts = message.substring(ChatProtocol.PRIVATE_MESSAGE.length()).split(":", 3);
        if (parts.length != 3) return;
        try {
            String peer = ChatProtocol.decode(parts[1]);
            String text = ChatProtocol.decode(parts[2]);
            if (peer.isEmpty() && !view.hasPrivateChat(parts[0])) {
                appendMessage(text);
            } else {
                view.showPrivateMessage(parts[0], peer, model.addPrivateMessage(parts[0], text));
            }
        } catch (IllegalArgumentException exception) {
            appendMessage("Received an invalid private message.");
        }
    }

    // 離開聊天但保留連線，以繼續接收線上名單。
    void leaveChat() {
        if (model.output == null) {
            return;
        }
        model.observing = true;
        model.activeId = null;
        resetUploads();
        model.output.println(ChatProtocol.LEAVE);
        model.groupHistory.clear();
        view.clearGroupChats();
        view.setConnected(false);
        view.clearInput();
        appendMessage("You left the chat.");
    }

    private void handleServerLine(String received) {
        if (received.equals(ImageWire.READY)) {
            view.enableImages(true);
        } else if (received.equals(ImageWire.READY_LOBBY)) {
            view.enableLobbyImages(true);
        } else if (received.equals(FileWire.READY)) {
            view.enableFiles(true);
        } else if (received.startsWith(ImageWire.RESULT) || received.startsWith(FileWire.RESULT)) {
            showTransferResult(received);
        } else if (received.startsWith(ImageWire.NOTICE)) {
            appendMessage(ChatProtocol.decode(received.substring(ImageWire.NOTICE.length())));
        } else if (received.startsWith(ChatProtocol.JOINED)) {
            model.activeId = received.substring(ChatProtocol.JOINED.length());
            model.joining = false;
            model.observing = false;
            if (model.activeName != null && !model.activeName.equals(view.name())) {
                clearConversationHistory();
            }
            model.activeName = view.name();
            view.setConnected(true);
            appendMessage("You joined the chat.");
        } else if (received.equals(ChatProtocol.ERROR_NAME_LENGTH)) {
            rejectJoin("Name must be at most 32 characters.", true);
        } else if (received.equals(ChatProtocol.ERROR_MESSAGE_LENGTH)) {
            view.showMessage("Message must be at most 2000 characters.");
        } else if (received.equals(ChatProtocol.ERROR_NAME)) {
            rejectJoin("That name is already in use. Choose another name.", false);
        } else if (received.equals(ChatProtocol.ERROR_VERSION) || received.equals("@joined")) {
            appendMessage("Incompatible server version. Update both server and client.");
            closeConnection();
        } else if (received.startsWith(ChatProtocol.USERS)) {
            updateOnlineUsers(received.substring(ChatProtocol.USERS.length()));
        } else if (received.startsWith(ChatProtocol.PRIVATE_MESSAGE) && !model.observing) {
            receivePrivate(received);
        } else if (received.startsWith(ChatProtocol.GROUP_MESSAGE) && !model.observing) {
            receiveGroup(received);
        } else if (received.startsWith(ChatProtocol.GROUP_MEMBERS) && !model.observing) {
            receiveGroupMembers(received);
        } else if (received.startsWith(ChatProtocol.GROUP_LEFT)) {
            String id = received.substring(ChatProtocol.GROUP_LEFT.length());
            model.groupHistory.remove(id);
            view.removeGroupChat(id);
            appendMessage("You left the group.");
        } else if (received.startsWith(ChatProtocol.GROUP_ERROR)) {
            decodeNotice(received.substring(ChatProtocol.GROUP_ERROR.length()), true);
        } else if (received.startsWith(ChatProtocol.NOTICE)) {
            decodeNotice(received.substring(ChatProtocol.NOTICE.length()), false);
        } else if (!model.observing) {
            appendMessage(received);
        }
    }

    private void rejectJoin(String message, boolean dialog) {
        model.joining = false;
        model.observing = true;
        view.setConnected(false);
        if (dialog) view.showMessage(message);
        else appendMessage(message);
    }

    private void decodeNotice(String encoded, boolean dialog) {
        try {
            String text = ChatProtocol.decode(encoded);
            if (dialog) view.showMessage(text);
            else appendMessage(text);
        } catch (IllegalArgumentException exception) {
            appendMessage("Received an invalid notification.");
        }
    }
    @FunctionalInterface
    private interface TransferParser<T> {
        T parse() throws IOException;
    }

    private <T> void receiveTransfer(Socket expectedSocket, TransferParser<T> parser,
                                     java.util.function.Predicate<T> store,
                                     java.util.function.Consumer<T> display, String fullMessage) {
        try {
            T item = parser.parse();
            if (item != null) SwingUtilities.invokeLater(() -> {
                if (model.socket != expectedSocket || model.observing) return;
                if (store.test(item)) display.accept(item);
                else appendMessage(fullMessage);
            });
        } catch (IOException exception) {
            SwingUtilities.invokeLater(() -> {
                if (model.socket == expectedSocket) appendMessage(exception.getMessage());
            });
        }
    }

    private void clearConversationHistory() {
        model.privateHistory.clear();
        model.groupHistory.clear();
        view.clearPrivateChats();
    }

    private void resetConnectionState() {
        model.socket = null;
        resetUploads();
        view.enableImages(false);
        view.enableFiles(false);
        model.output = null;
        model.connecting = false;
        model.joining = false;
        model.observing = false;
        model.activeId = null;
        model.onlineUsers.clear();
        view.showOnlineUsers(model.onlineUsers);
        view.setConnected(false);
    }

    void disconnected(Socket closedSocket, String message) {
        if (model.socket == closedSocket || (model.socket == null && model.connecting)) {
            resetConnectionState();
            if (view.isDisplayable()) {
                appendMessage(message);
            }
        }
    }

    void closeConnection() {
        model.connectionGeneration++;
        Socket oldSocket = model.socket;
        resetConnectionState();
        closeSocket(oldSocket);
    }

    private void updateOnlineUsers(String encodedNames) {
        List<OnlineUser> users = new ArrayList<>();
        try {
            if (!encodedNames.isEmpty()) {
                for (String encoded : encodedNames.split(",")) {
                    String[] parts = encoded.split("=", 2);
                    if (parts.length != 2) throw new IllegalArgumentException();
                    java.util.UUID.fromString(parts[0]);
                    users.add(new OnlineUser(parts[0], ChatProtocol.decode(parts[1])));
                }
            }
        } catch (IllegalArgumentException exception) {
            users.clear();
            appendMessage("Received an invalid online user list.");
        }
        model.onlineUsers.clear();
        model.onlineUsers.addAll(users);
        view.showOnlineUsers(model.onlineUsers);
    }
    private static void closeSocket(Socket socket) {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

}
