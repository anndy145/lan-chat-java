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
import javax.swing.SwingUtilities;

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
    private final DefaultListModel<String> onlineUsers = new DefaultListModel<>();

    // 連線狀態和介面元件只在 Swing 介面執行緒中讀寫。
    private Socket socket;
    private PrintWriter output;
    private boolean connecting;
    private boolean observing;
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
        JPanel inputPanel = new JPanel(new BorderLayout(5, 0));
        inputPanel.add(inputField, BorderLayout.CENTER);
        JPanel buttonsPanel = new JPanel();
        buttonsPanel.add(sendButton);
        buttonsPanel.add(leaveButton);
        inputPanel.add(buttonsPanel, BorderLayout.EAST);
        add(connectionPanel, BorderLayout.NORTH);
        add(new JScrollPane(chatArea), BorderLayout.CENTER);
        JPanel usersPanel = new JPanel(new BorderLayout());
        JPanel usersHeader = new JPanel(new BorderLayout());
        usersHeader.add(new JLabel("Online users"), BorderLayout.NORTH);
        usersHeader.add(viewUsersButton, BorderLayout.SOUTH);
        usersPanel.add(usersHeader, BorderLayout.NORTH);
        JScrollPane usersScroll = new JScrollPane(new JList<>(onlineUsers));
        usersScroll.setPreferredSize(new java.awt.Dimension(140, 0));
        usersPanel.add(usersScroll, BorderLayout.CENTER);
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
                observing = false;
                setConnected(true);
                appendMessage("You joined the chat.");
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
                writer.println(observeOnly ? "@observe" : name);

                // Swing 元件只能在介面執行緒更新。
                SwingUtilities.invokeLater(() -> {
                    if (!isDisplayable() || generation != connectionGeneration) {
                        closeSocket(newSocket);
                        return;
                    }
                    socket = newSocket;
                    output = writer;
                    connecting = false;
                    observing = observeOnly;
                    setConnected(!observeOnly);
                    appendMessage(observeOnly ? "Connected. You can view online users." : "Connected.");
                });

                String message;
                while ((message = input.readLine()) != null) {
                    String received = message;
                    SwingUtilities.invokeLater(() -> {
                        if (socket == newSocket) {
                            if (received.startsWith("@users:")) {
                                updateOnlineUsers(received.substring(7));
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
        } else {
            output.println(message);
        }
    }

    // 離開聊天但保留連線，以繼續接收線上名單。
    private void leaveChat() {
        if (output == null) {
            return;
        }
        observing = true;
        output.println("@leave");
        setConnected(false);
        inputField.setText("");
        appendMessage("You left the chat.");
    }

    private void disconnected(Socket closedSocket, String message) {
        if (socket == closedSocket || (socket == null && connecting)) {
            socket = null;
            output = null;
            connecting = false;
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
        observing = false;
        onlineUsers.clear();
        setConnected(false);
        closeSocket(oldSocket);
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
        nameField.setEnabled(!connected && !connecting);
        connectButton.setText(observing ? "Join chat" : "Connect");
        connectButton.setEnabled(!connected && !connecting);
        viewUsersButton.setText(observing ? "Disconnect" : "View online users");
        viewUsersButton.setEnabled(!connected && !connecting);
        inputField.setEnabled(connected);
        sendButton.setEnabled(connected);
        leaveButton.setEnabled(connected);
    }

    private void appendMessage(String message) {
        chatArea.append(message + "\n");
        chatArea.setCaretPosition(chatArea.getDocument().getLength());
    }

    // 伺服器每次傳送完整名單，以最新名單取代舊內容。
    private void updateOnlineUsers(String encodedNames) {
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
    }

    public static void main(String[] args) {
        // 從 Swing 的介面執行緒建立和顯示視窗。
        SwingUtilities.invokeLater(() -> new ChatWindow().setVisible(true));
    }
}
