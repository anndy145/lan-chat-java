package client;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.ListSelectionModel;
import java.util.List;
import java.util.stream.Collectors;

public class ChatWindow extends JFrame {
    private final JTextField hostField = new JTextField("127.0.0.1");
    private final JTextField portField = new JTextField("5487");
    private final JTextField nameField = new JTextField();
    private final JButton connectButton = new JButton("Connect");
    private final JButton viewUsersButton = new JButton("View online users");
    private final JTextArea chatArea = new JTextArea();
    private final JTextField inputField = new JTextField();
    private final JButton sendButton = new JButton("Send");
    private final JButton leaveButton = new JButton("Leave");
    private final JButton privateButton = new JButton("Open private chat");
    private final JButton createGroupButton = new JButton("Create group");
    private final DefaultListModel<String> onlineUsers = new DefaultListModel<>();
    private final JList<String> usersList = new JList<>(onlineUsers);
    private final JTabbedPane chats = new JTabbedPane();
    private final Map<String, JTextArea> privateChats = new LinkedHashMap<>();
    private final Map<String, JScrollPane> groupChats = new LinkedHashMap<>();
    private String activeName;

    // 連線狀態和介面元件只在 Swing 介面執行緒中讀寫。
    private Socket socket;
    private PrintWriter output;
    private boolean connecting;
    private boolean observing;
    private boolean joining;
    private long connectionGeneration;

    public ChatWindow() {
        super("Chat Room");
        setSize(600, 450);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        // 上方放連線資料；中間顯示訊息，下方讓使用者輸入。
        JPanel connectionPanel = new JPanel(new GridLayout(2, 4, 5, 5));
        connectionPanel.add(new JLabel("Server IP"));
        connectionPanel.add(new JLabel("Port"));
        connectionPanel.add(new JLabel("Name"));
        connectionPanel.add(new JLabel(""));
        connectionPanel.add(hostField);
        connectionPanel.add(portField);
        connectionPanel.add(nameField);
        connectionPanel.add(connectButton);

        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        chats.addTab("Group", new JScrollPane(chatArea));
        JPanel inputPanel = new JPanel(new BorderLayout(5, 0));
        inputPanel.add(inputField, BorderLayout.CENTER);
        JPanel buttonsPanel = new JPanel();
        buttonsPanel.add(sendButton);
        buttonsPanel.add(leaveButton);
        inputPanel.add(buttonsPanel, BorderLayout.EAST);
        add(connectionPanel, BorderLayout.NORTH);
        add(chats, BorderLayout.CENTER);
        JPanel usersPanel = new JPanel(new BorderLayout());
        JPanel usersHeader = new JPanel(new BorderLayout());
        usersHeader.add(new JLabel("Online users"), BorderLayout.NORTH);
        usersHeader.add(viewUsersButton, BorderLayout.SOUTH);
        usersPanel.add(usersHeader, BorderLayout.NORTH);
        JScrollPane usersScroll = new JScrollPane(usersList);
        usersList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        usersScroll.setPreferredSize(new java.awt.Dimension(140, 0));
        usersPanel.add(usersScroll, BorderLayout.CENTER);
        JPanel userButtons = new JPanel(new GridLayout(2, 1));
        userButtons.add(privateButton);
        userButtons.add(createGroupButton);
        usersPanel.add(userButtons, BorderLayout.SOUTH);
        add(usersPanel, BorderLayout.EAST);
        add(inputPanel, BorderLayout.SOUTH);
        setConnected(false);

        connectButton.addActionListener(event -> {
            if (observing && output != null) {
                String name = nameField.getText().strip();
                if (name.isEmpty()) {
                    JOptionPane.showMessageDialog(this, "Please enter a name.");
                    return;
                }
                output.println("@join:" + Base64.getEncoder().encodeToString(name.getBytes(StandardCharsets.UTF_8)));
                joining = true;
                setConnected(false);
            } else {
                connect(false);
            }
        });
        viewUsersButton.addActionListener(event -> {
            if (observing) {
                closeConnection();
                appendMessage("Disconnected. You can change the server IP and port.");
            } else {
                connect(true);
            }
        });
        sendButton.addActionListener(event -> sendMessage());
        privateButton.addActionListener(event -> openSelectedChat());
        createGroupButton.addActionListener(event -> createGroup());
        leaveButton.addActionListener(event -> leaveChat());
        inputField.addActionListener(event -> sendMessage());
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
                closeConnection();
            }
        });
    }

    private void connect(boolean observeOnly) {
        String host = hostField.getText().strip();
        String name = nameField.getText().strip();
        int port;
        try {
            port = Integer.parseInt(portField.getText().strip());
            if (port < 1 || port > 65535 || host.isEmpty() || (!observeOnly && name.isEmpty())) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException exception) {
            JOptionPane.showMessageDialog(this, observeOnly
                    ? "Enter a server IP and a port from 1 to 65535."
                    : "Enter a server IP, a name, and a port from 1 to 65535.");
            return;
        }

        connecting = true;
        clearPrivateChats();
        activeName = null;
        long generation = ++connectionGeneration;
        setConnected(false);
        appendMessage("Connecting...");

        // 網路連線可能需要等待，放在背景執行緒以免視窗卡住。
        Thread connectionThread = new Thread(() -> {
            Socket newSocket = new Socket();
            try {
                newSocket.connect(new InetSocketAddress(host, port), 5487);
                BufferedReader input = new BufferedReader(
                        new InputStreamReader(newSocket.getInputStream(), StandardCharsets.UTF_8));
                PrintWriter writer = new PrintWriter(
                        new OutputStreamWriter(newSocket.getOutputStream(), StandardCharsets.UTF_8), true);
                writer.println("@observe");
                if (!observeOnly) {
                    writer.println("@join:" + Base64.getEncoder().encodeToString(name.getBytes(StandardCharsets.UTF_8)));
                }

                // Swing 元件只能在介面執行緒更新。
                SwingUtilities.invokeLater(() -> {
                    if (!isDisplayable() || generation != connectionGeneration) {
                        closeSocket(newSocket);
                        return;
                    }
                    socket = newSocket;
                    output = writer;
                    connecting = false;
                    observing = true;
                    joining = !observeOnly;
                    setConnected(false);
                    appendMessage(observeOnly ? "Connected. You can view online users." : "Joining chat...");
                });

                String message;
                while ((message = input.readLine()) != null) {
                    String received = message;
                    SwingUtilities.invokeLater(() -> {
                        if (socket == newSocket) {
                            if (received.equals("@joined")) {
                                joining = false;
                                observing = false;
                                if (activeName != null && !activeName.equals(nameField.getText().strip())) {
                                    clearPrivateChats();
                                }
                                activeName = nameField.getText().strip();
                                setConnected(true);
                                appendMessage("You joined the chat.");
                            } else if (received.equals("@error:name")) {
                                joining = false;
                                observing = true;
                                setConnected(false);
                                appendMessage("That name is already in use. Choose another name.");
                            } else if (received.startsWith("@users:")) {
                                updateOnlineUsers(received.substring(7));
                            } else if (received.startsWith("@pm:") && !observing) {
                                receivePrivate(received);
                            } else if (received.startsWith("@group:") && !observing) {
                                receiveGroup(received);
                            } else if (received.startsWith("@notice:")) {
                                try {
                                    appendMessage(new String(Base64.getDecoder().decode(received.substring(8)), StandardCharsets.UTF_8));
                                } catch (IllegalArgumentException exception) {
                                    appendMessage("Received an invalid notification.");
                                }
                            } else if (!observing) {
                                appendMessage(received);
                            }
                        }
                    });
                }
                SwingUtilities.invokeLater(() -> {
                    if (generation == connectionGeneration) disconnected(newSocket, "Disconnected from the server.");
                });
            } catch (IOException exception) {
                SwingUtilities.invokeLater(() -> {
                    if (generation == connectionGeneration) disconnected(newSocket, "Connection closed or failed: " + exception.getMessage());
                });
            } finally {
                closeSocket(newSocket);
            }
        }, "chat-connection");
        connectionThread.setDaemon(true);
        connectionThread.start();
    }

    private void sendMessage() {
        String message = inputField.getText();
        if (output == null || message.isBlank()) {
            return;
        }
        // 伺服器會把訊息廣播回來，因此此處不重複加到聊天區。
        inputField.setText("");
        if ("/quit".equalsIgnoreCase(message.strip())) {
            leaveChat();
        } else if (chats.getSelectedIndex() == 0) {
            output.println(message);
        } else if (groupChats.containsValue(chats.getSelectedComponent())) {
            String groupId = groupChats.entrySet().stream()
                    .filter(entry -> entry.getValue() == chats.getSelectedComponent())
                    .map(Map.Entry::getKey).findFirst().orElseThrow();
            output.println("@group-send:" + groupId + ":" + encode(message));
        } else {
            String target = chats.getTitleAt(chats.getSelectedIndex());
            String encodedTarget = Base64.getEncoder().encodeToString(target.getBytes(StandardCharsets.UTF_8));
            String encodedMessage = Base64.getEncoder().encodeToString(message.getBytes(StandardCharsets.UTF_8));
            output.println("@private:" + encodedTarget + ":" + encodedMessage);
        }
    }

    private void openSelectedChat() {
        List<String> selected = usersList.getSelectedValuesList();
        if (selected.size() != 1) {
            JOptionPane.showMessageDialog(this, "Select exactly one online user.");
            return;
        }
        String target = selected.get(0);
        if (target.equalsIgnoreCase(activeName)) {
            JOptionPane.showMessageDialog(this, "Select another user.");
            return;
        }
        JTextArea area = privateChat(target);
        chats.setSelectedComponent(area.getParent().getParent());
    }

    private void createGroup() {
        List<String> members = usersList.getSelectedValuesList().stream()
                .filter(name -> !name.equalsIgnoreCase(activeName)).collect(Collectors.toList());
        if (members.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Select at least one other online user. Hold Ctrl to select more.");
            return;
        }
        String name = JOptionPane.showInputDialog(this, "Group name:");
        if (name == null) return;
        name = name.strip();
        if (name.isEmpty() || name.length() > 80 || name.contains("\n") || name.contains("\r")) {
            JOptionPane.showMessageDialog(this, "Enter a group name of up to 80 characters.");
            return;
        }
        output.println("@group-create:" + encode(name) + ":"
                + members.stream().map(ChatWindow::encode).collect(Collectors.joining(",")));
    }

    private static String encode(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    private void receiveGroup(String message) {
        String[] parts = message.substring(7).split(":", 3);
        if (parts.length != 3) return;
        try {
            String name = new String(Base64.getDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            String text = new String(Base64.getDecoder().decode(parts[2]), StandardCharsets.UTF_8);
            JScrollPane pane = groupChats.get(parts[0]);
            if (pane == null) {
                JTextArea area = new JTextArea();
                area.setEditable(false);
                area.setLineWrap(true);
                area.setWrapStyleWord(true);
                pane = new JScrollPane(area);
                groupChats.put(parts[0], pane);
                chats.addTab("Group: " + name, pane);
            }
            JTextArea area = (JTextArea) pane.getViewport().getView();
            area.append(text + "\n");
            area.setCaretPosition(area.getDocument().getLength());
        } catch (IllegalArgumentException exception) {
            appendMessage("Received an invalid group message.");
        }
    }

    // 每位對象有獨立的文字區；收到私訊時也會自動建立分頁。
    private JTextArea privateChat(String peer) {
        return privateChats.computeIfAbsent(peer, name -> {
            JTextArea area = new JTextArea();
            area.setEditable(false);
            area.setLineWrap(true);
            area.setWrapStyleWord(true);
            chats.addTab(name, new JScrollPane(area));
            return area;
        });
    }

    private void receivePrivate(String message) {
        String[] parts = message.substring(4).split(":", 2);
        if (parts.length != 2) return;
        try {
            String peer = new String(Base64.getDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            String text = new String(Base64.getDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            JTextArea area = privateChat(peer);
            area.append(text + "\n");
            area.setCaretPosition(area.getDocument().getLength());
        } catch (IllegalArgumentException exception) {
            appendMessage("Received an invalid private message.");
        }
    }

    // 離開聊天但保留連線，以繼續接收線上名單。
    private void leaveChat() {
        if (output == null) {
            return;
        }
        observing = true;
        output.println("@leave");
        clearGroupChats();
        setConnected(false);
        inputField.setText("");
        appendMessage("You left the chat.");
    }

    private void disconnected(Socket closedSocket, String message) {
        if (socket == closedSocket || (socket == null && connecting)) {
            socket = null;
            output = null;
            connecting = false;
            joining = false;
            observing = false;
            onlineUsers.clear();
            setConnected(false);
            if (isDisplayable()) {
                appendMessage(message);
            }
        }
    }

    private void closeConnection() {
        connectionGeneration++;
        Socket oldSocket = socket;
        socket = null;
        output = null;
        connecting = false;
        joining = false;
        observing = false;
        onlineUsers.clear();
        setConnected(false);
        closeSocket(oldSocket);
    }

    private void clearPrivateChats() {
        while (chats.getTabCount() > 1) chats.removeTabAt(1);
        privateChats.clear();
        groupChats.clear();
    }

    private void clearGroupChats() {
        for (JScrollPane pane : groupChats.values()) chats.remove(pane);
        groupChats.clear();
    }

    private static void closeSocket(Socket socket) {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // 連線已關閉時，不需要再次處理。
            }
        }
    }

    private void setConnected(boolean connected) {
        hostField.setEnabled(!connected && !observing && !connecting);
        portField.setEnabled(!connected && !observing && !connecting);
        nameField.setEnabled(!connected && !connecting && !joining);
        connectButton.setText(observing ? "Join chat" : "Connect");
        connectButton.setEnabled(!connected && !connecting && !joining);
        viewUsersButton.setText(observing ? "Disconnect" : "View online users");
        viewUsersButton.setEnabled(!connected && !connecting && !joining);
        inputField.setEnabled(connected);
        sendButton.setEnabled(connected);
        privateButton.setEnabled(connected);
        createGroupButton.setEnabled(connected);
        leaveButton.setEnabled(connected);
    }

    private void appendMessage(String message) {
        chatArea.append(message + "\n");
        chatArea.setCaretPosition(chatArea.getDocument().getLength());
    }

    // 伺服器每次傳送完整名單，以最新名單取代舊內容。
    private void updateOnlineUsers(String encodedNames) {
        List<String> selected = usersList.getSelectedValuesList();
        onlineUsers.clear();
        if (!encodedNames.isEmpty()) {
            for (String encoded : encodedNames.split(",")) {
                try {
                    onlineUsers.addElement(new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8));
                } catch (IllegalArgumentException exception) {
                    appendMessage("Received an invalid online user list.");
                    onlineUsers.clear();
                    return;
                }
            }
        }
        int[] indexes = selected.stream().mapToInt(onlineUsers::indexOf).filter(index -> index >= 0).toArray();
        usersList.setSelectedIndices(indexes);
    }

    public static void main(String[] args) {
        // 從 Swing 的介面執行緒建立和顯示視窗。
        SwingUtilities.invokeLater(() -> new ChatWindow().setVisible(true));
    }
}
