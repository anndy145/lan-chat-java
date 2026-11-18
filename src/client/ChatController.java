package client;

import common.ChatProtocol;
import client.ChatModel.OnlineUser;
import client.ChatWindow.PrivateChat;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import server.Server;

/** 處理使用者操作與連線事件。所有狀態與介面更新都在 Swing 執行緒執行。 */
final class ChatController {
    private final ChatWindow view;
    private final ChatModel model;

    ChatController(ChatWindow view, ChatModel model) {
        this.view = view;
        this.model = model;
    }

    void connectAction() {
        if (model.observing && model.output != null) {
            String name = view.nameField.getText().strip();
            if (name.isEmpty()) {
                JOptionPane.showMessageDialog(view, "Please enter a name.");
            } else if (name.codePointCount(0, name.length()) > ChatProtocol.MAX_NAME_LENGTH) {
                JOptionPane.showMessageDialog(view, "Name must be at most 32 characters.");
            } else {
                model.output.println(ChatProtocol.JOIN + ChatProtocol.encode(name));
                model.joining = true;
                view.setConnected(false);
            }
        } else connect(false);
    }

    void viewUsersAction() {
        if (model.observing) {
            closeConnection();
            view.appendMessage("Disconnected.");
        } else connect(true);
    }

    void hostAction() {
        if (model.temporaryServer != null) stopTemporaryServer();
        else startTemporaryServer();
    }

    void startTemporaryServer() {
        int port;
        try {
            port = Integer.parseInt(view.portField.getText().strip());
            if (port < 1 || port > 65535)
                throw new NumberFormatException();
        } catch (NumberFormatException exception) {
            JOptionPane.showMessageDialog(view, "Port must be a number from 1 to 65535.");
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
                    view.hostField.setText("127.0.0.1");
                    view.setConnected(false);
                    view.appendMessage("Temporary server started on port " + port + ". LAN IP: " + addresses);
                });
                server.serve();
                SwingUtilities.invokeLater(() -> {
                    if (generation == model.serverGeneration && model.temporaryServer == server) {
                        model.temporaryServer = null;
                        view.setConnected(model.socket != null && !model.observing);
                        view.appendMessage("Temporary server stopped.");
                    }
                });
            } catch (IOException exception) {
                SwingUtilities.invokeLater(() -> {
                    if (generation != model.serverGeneration || !view.isDisplayable())
                        return;
                    model.temporaryServer = null;
                    model.startingServer = false;
                    view.setConnected(model.socket != null && !model.observing);
                    view.appendMessage("Temporary server failed: " + exception.getMessage());
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
            view.appendMessage("Temporary server stopped.");
        }
        view.setConnected(model.socket != null && !model.observing);
    }

    void connect(boolean observeOnly) {
        String host = view.hostField.getText().strip();
        String name = view.nameField.getText().strip();
        if (host.isEmpty()) {
            JOptionPane.showMessageDialog(view, "Please enter a server IP or hostname.");
            view.hostField.requestFocusInWindow();
            return;
        }
        int port;
        try {
            port = Integer.parseInt(view.portField.getText().strip());
        } catch (NumberFormatException exception) {
            JOptionPane.showMessageDialog(view, "Port must be a number from 1 to 65535.");
            view.portField.requestFocusInWindow();
            return;
        }
        if (port < 1 || port > 65535) {
            JOptionPane.showMessageDialog(view, "Port must be between 1 and 65535.");
            view.portField.requestFocusInWindow();
            return;
        }
        if (!observeOnly && name.isEmpty()) {
            JOptionPane.showMessageDialog(view, "Please enter a name.");
            view.nameField.requestFocusInWindow();
            return;
        }

        if (!observeOnly && name.codePointCount(0, name.length()) > ChatProtocol.MAX_NAME_LENGTH) {
            JOptionPane.showMessageDialog(view, "Name must be at most 32 characters.");
            view.nameField.requestFocusInWindow();
            return;
        }

        model.connecting = true;
        view.clearPrivateChats();
        model.activeName = null;
        model.activeId = null;
        long generation = ++model.connectionGeneration;
        view.setConnected(false);
        view.appendMessage("Connecting...");

        // 網路連線可能需要等待，放在背景執行緒以免視窗卡住。
        Thread connectionThread = new Thread(() -> {
            Socket newSocket = new Socket();
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
                    view.appendMessage(observeOnly ? "Connected. You can view online users." : "Joining chat...");
                });
                }, received -> {
                    SwingUtilities.invokeLater(() -> {
                        if (model.socket == newSocket) {
                            if (received.startsWith(ChatProtocol.JOINED)) {
                                model.activeId = received.substring(8);
                                model.joining = false;
                                model.observing = false;
                                if (model.activeName != null && !model.activeName.equals(view.nameField.getText().strip())) {
                                    view.clearPrivateChats();
                                }
                                model.activeName = view.nameField.getText().strip();
                                view.setConnected(true);
                                view.appendMessage("You joined the chat.");
                            } else if (received.equals(ChatProtocol.ERROR_NAME_LENGTH)) {
                                model.joining = false;
                                model.observing = true;
                                view.setConnected(false);
                                JOptionPane.showMessageDialog(view, "Name must be at most 32 characters.");
                            } else if (received.equals(ChatProtocol.ERROR_MESSAGE_LENGTH)) {
                                JOptionPane.showMessageDialog(view, "Message must be at most 2000 characters.");
                            } else if (received.equals(ChatProtocol.ERROR_NAME)) {
                                model.joining = false;
                                model.observing = true;
                                view.setConnected(false);
                                view.appendMessage("That name is already in use. Choose another name.");
                            } else if (received.equals(ChatProtocol.ERROR_VERSION) || received.equals("@joined")) {
                                view.appendMessage("Incompatible server version. Update both server and client.");
                                closeConnection();
                            } else if (received.startsWith(ChatProtocol.USERS)) {
                                updateOnlineUsers(received.substring(7));
                            } else if (received.startsWith(ChatProtocol.PRIVATE_MESSAGE) && !model.observing) {
                                receivePrivate(received);
                            } else if (received.startsWith(ChatProtocol.GROUP_MESSAGE) && !model.observing) {
                                receiveGroup(received);
                            } else if (received.startsWith(ChatProtocol.GROUP_ERROR)) {
                                try {
                                    JOptionPane.showMessageDialog(view, new String(Base64.getDecoder().decode(
                                            received.substring(13)), StandardCharsets.UTF_8));
                                } catch (IllegalArgumentException exception) {
                                    view.appendMessage("Received an invalid notification.");
                                }
                            } else if (received.startsWith(ChatProtocol.NOTICE)) {
                                try {
                                    view.appendMessage(new String(Base64.getDecoder().decode(received.substring(8)),
                                            StandardCharsets.UTF_8));
                                } catch (IllegalArgumentException exception) {
                                    view.appendMessage("Received an invalid notification.");
                                }
                            } else if (!model.observing) {
                                view.appendMessage(received);
                            }
                        }
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

    void sendMessage() {
        String message = view.inputField.getText();
        if (model.output == null || message.isBlank()) {
            return;
        }
        if (message.codePointCount(0, message.length()) > ChatProtocol.MAX_MESSAGE_LENGTH) {
            JOptionPane.showMessageDialog(view, "Message must be at most 2000 characters.");
            return;
        }
        // 伺服器會把訊息廣播回來，因此此處不重複加到聊天區。
        view.inputField.setText("");
        if ("/quit".equalsIgnoreCase(message.strip())) {
            leaveChat();
        } else if (view.chats.getSelectedIndex() == 0) {
            model.output.println(ChatProtocol.SAY + encode(message));
        } else if (view.groupChats.containsValue(view.chats.getSelectedComponent())) {
            String groupId = view.groupChats.entrySet().stream()
                    .filter(entry -> entry.getValue() == view.chats.getSelectedComponent())
                    .map(Map.Entry::getKey).findFirst().orElseThrow();
            model.output.println(ChatProtocol.GROUP_SEND + groupId + ":" + encode(message));
        } else {
            String targetId = view.privateChats.entrySet().stream()
                    .filter(entry -> entry.getValue().pane == view.chats.getSelectedComponent())
                    .map(Map.Entry::getKey).findFirst().orElseThrow();
            model.output.println(ChatProtocol.PRIVATE + targetId + ":" + encode(message));
        }
    }

    void openSelectedChat() {
        List<OnlineUser> selected = view.usersList.getSelectedValuesList();
        if (selected.size() != 1) {
            JOptionPane.showMessageDialog(view, "Select exactly one online user.");
            return;
        }
        openPrivateChat(selected.get(0));
    }

    void openPrivateChat(OnlineUser target) {
        if (target.id.equals(model.activeId)) {
            JOptionPane.showMessageDialog(view, "Select another user.");
            return;
        }
        PrivateChat chat = view.privateChat(target.id, target.name);
        view.chats.setSelectedComponent(chat.pane);
        view.inputField.requestFocusInWindow();
    }

    void createGroup() {
        List<String> members = view.usersList.getSelectedValuesList().stream()
                .filter(user -> !user.id.equals(model.activeId)).map(user -> user.id).collect(Collectors.toList());
        if (members.isEmpty()) {
            JOptionPane.showMessageDialog(view, "Select at least one other online user. Hold Ctrl to select more.");
            return;
        }
        String name = JOptionPane.showInputDialog(view, "Group name:");
        if (name == null)
            return;
        name = name.strip();
        if (name.isEmpty() || name.length() > 80 || name.contains("\n") || name.contains("\r")) {
            JOptionPane.showMessageDialog(view, "Enter a group name of up to 80 characters.");
            return;
        }
        model.output.println(ChatProtocol.GROUP_CREATE + encode(name) + ":"
                + String.join(",", members));
    }

    void addGroupMembers() {
        String groupId = view.groupChats.entrySet().stream()
                .filter(entry -> entry.getValue() == view.chats.getSelectedComponent())
                .map(Map.Entry::getKey).findFirst().orElse(null);
        if (groupId == null) {
            JOptionPane.showMessageDialog(view, "Select a group tab first.");
            return;
        }
        List<String> members = view.usersList.getSelectedValuesList().stream()
                .filter(user -> !user.id.equals(model.activeId)).map(user -> user.id).collect(Collectors.toList());
        if (members.isEmpty()) {
            JOptionPane.showMessageDialog(view, "Select at least one other online user. Hold Ctrl to select more.");
            return;
        }
        model.output.println(ChatProtocol.GROUP_ADD + groupId + ":" + String.join(",", members));
    }

    private static String encode(String text) {
        return ChatProtocol.encode(text);
    }

    private void receiveGroup(String message) {
        String[] parts = message.substring(ChatProtocol.GROUP_MESSAGE.length()).split(":", 3);
        if (parts.length != 3) return;
        try {
            view.showGroupMessage(parts[0], ChatProtocol.decode(parts[1]), ChatProtocol.decode(parts[2]));
        } catch (IllegalArgumentException exception) {
            view.appendMessage("Received an invalid group message.");
        }
    }

    private void receivePrivate(String message) {
        String[] parts = message.substring(ChatProtocol.PRIVATE_MESSAGE.length()).split(":", 3);
        if (parts.length != 3) return;
        try {
            view.showPrivateMessage(parts[0], ChatProtocol.decode(parts[1]), ChatProtocol.decode(parts[2]));
        } catch (IllegalArgumentException exception) {
            view.appendMessage("Received an invalid private message.");
        }
    }

    // 離開聊天但保留連線，以繼續接收線上名單。
    void leaveChat() {
        if (model.output == null) {
            return;
        }
        model.observing = true;
        model.activeId = null;
        model.output.println(ChatProtocol.LEAVE);
        view.clearGroupChats();
        view.setConnected(false);
        view.inputField.setText("");
        view.appendMessage("You left the chat.");
    }

    void disconnected(Socket closedSocket, String message) {
        if (model.socket == closedSocket || (model.socket == null && model.connecting)) {
            model.socket = null;
            model.output = null;
            model.connecting = false;
            model.joining = false;
            model.observing = false;
            model.activeId = null;
            model.onlineUsers.clear();
            view.showOnlineUsers(model.onlineUsers);
            view.setConnected(false);
            if (view.isDisplayable()) {
                view.appendMessage(message);
            }
        }
    }

    void closeConnection() {
        model.connectionGeneration++;
        Socket oldSocket = model.socket;
        model.socket = null;
        model.output = null;
        model.connecting = false;
        model.joining = false;
        model.observing = false;
        model.activeId = null;
        model.onlineUsers.clear();
        view.showOnlineUsers(model.onlineUsers);
        view.setConnected(false);
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
            view.appendMessage("Received an invalid online user list.");
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
